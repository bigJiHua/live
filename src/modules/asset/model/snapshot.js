const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');

/**
 * 资产快照表 - 系统自动定时记录资产变化
 */
class AssetSnapshot {
  static tableName = 'asset_snapshot';

  /**
   * 计算当前资产数据
   */
  static async calculateCurrentAssets(userId) {
    // 计算现金类资产总和（account_balance，排除信用卡）
    const [balanceRows] = await db.execute(
      `SELECT COALESCE(SUM(balance), 0) as total 
       FROM account_balance ab
       LEFT JOIN card_base c ON ab.card_id = c.id
       WHERE ab.user_id = ? AND ab.is_deleted = 0 AND c.card_type != 'credit'`,
      [userId]
    );
    const totalBalance = parseFloat(balanceRows[0]?.total) || 0;

    // 计算信用卡总欠款
    const [debtRows] = await db.execute(
      `SELECT COALESCE(SUM(cb.need_repay), 0) as total 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditDebt = parseFloat(debtRows[0]?.total) || 0;

    // 计算信用卡溢缴款
    const [overflowRows] = await db.execute(
      `SELECT COALESCE(SUM(cb.avail_limit - c.credit_limit - c.temp_limit), 0) as overflow 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'
         AND (cb.avail_limit - c.credit_limit - c.temp_limit) > 0`,
      [userId]
    );
    const creditOverflow = parseFloat(overflowRows[0]?.overflow) || 0;

    // 总资产 = 现金 + 信用卡溢缴款
    const totalAsset = totalBalance + Math.max(0, creditOverflow);

    // 最终余额 = 总资产 - 信用卡欠款
    const finalBalance = totalAsset - creditDebt;

    return { totalAsset, creditDebt, finalBalance };
  }

  /**
   * 系统自动保存快照（收支变化时触发）
   * 每日最多1条，仅数值变化时记录
   */
  static async autoSaveSnapshot(userId) {
    try {
      const now = String(Date.now());
      const today = now.substring(0, 10);

      // 1. 检查今日是否已有记录
      const [todayRecords] = await db.execute(
        `SELECT * FROM ${this.tableName} 
         WHERE user_id = ? AND record_date = ? AND is_deleted = 0
         ORDER BY create_time DESC LIMIT 1`,
        [userId, today]
      );

      if (todayRecords.length > 0) {
        return todayRecords[0];
      }

      // 2. 计算当前资产
      const assets = await this.calculateCurrentAssets(userId);

      // 3. 对比昨日数值（允许误差0.01）
      const [yesterdayRecords] = await db.execute(
        `SELECT * FROM ${this.tableName} 
         WHERE user_id = ? AND record_date < ? AND is_deleted = 0
         ORDER BY record_date DESC LIMIT 1`,
        [userId, today]
      );

      if (yesterdayRecords.length > 0) {
        const last = yesterdayRecords[0];
        const diff1 = Math.abs(parseFloat(last.total_asset) - assets.totalAsset);
        const diff2 = Math.abs(parseFloat(last.credit_debt) - assets.creditDebt);
        const diff3 = Math.abs(parseFloat(last.total_balance) - assets.finalBalance);

        if (diff1 < 0.01 && diff2 < 0.01 && diff3 < 0.01) {
          console.log(`[资产快照] 资产数值未变化，跳过`);
          return last;
        }
      }

      // 4. 写入快照
      return await this.saveSnapshot(userId, assets.totalAsset, assets.creditDebt, assets.finalBalance);

    } catch (error) {
      console.error(`[资产快照] 自动快照失败:`, error.message);
      return null;
    }
  }

  /**
   * 保存快照（仅当与上次记录不同时）
   */
  static async saveSnapshot(userId, totalAsset, creditDebt, totalBalance) {
    const now = String(Date.now());
    const today = now.substring(0, 10);

    // 查询今天最后一条记录
    const [existing] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND record_date = ? AND is_deleted = 0
       ORDER BY create_time DESC LIMIT 1`,
      [userId, today]
    );

    // 如果今天的值与上次相同，不重复记录
    if (existing.length > 0) {
      const last = existing[0];
      if (Math.abs(parseFloat(last.total_asset) - totalAsset) < 0.01 &&
          Math.abs(parseFloat(last.credit_debt) - creditDebt) < 0.01 &&
          Math.abs(parseFloat(last.total_balance) - totalBalance) < 0.01) {
        console.log(`[资产快照] 今日数值未变，跳过记录`);
        return last;
      }
    }

    // 写入新快照
    const id = idUtils.billId();
    const recordTime = new Date().toLocaleString('zh-CN', { hour12: false });

    await db.execute(
      `INSERT INTO ${this.tableName} 
       (id, user_id, total_asset, credit_debt, total_balance, record_date, record_time, create_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)`,
      [id, userId, totalAsset, creditDebt, totalBalance, today, recordTime, now]
    );

    console.log(`[资产快照] 记录成功，日期=${today}，总资产=${totalAsset}，信用卡欠款=${creditDebt}，余额=${totalBalance}`);

    return { id, total_asset: totalAsset, credit_debt: creditDebt, total_balance: totalBalance };
  }

  /**
   * 获取最新快照（附加实时计算数据）
   * 如果没有快照，自动立即创建一条
   */
  static async getLatest(userId) {
    // 获取最新快照记录
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND is_deleted = 0
       ORDER BY create_time DESC LIMIT 1`,
      [userId]
    );
    
    let snapshot = rows[0] || null;

    // 如果没有快照，立即创建一条
    if (!snapshot) {
      console.log(`[资产快照] 无快照记录，立即创建`);
      snapshot = await this.createSnapshotNow(userId);
    }

    // 计算实时数据
    const realtime = await this.calculateRealtimeStats(userId);

    // 获取近期大额流水
    const largeTransactions = await this.getRecentLargeTransactions(userId);

    return {
      ...snapshot,
      ...realtime,
      largeTransactions
    };
  }

  /**
   * 立即创建一条快照
   */
  static async createSnapshotNow(userId) {
    const assets = await this.calculateCurrentAssets(userId);
    return await this.saveSnapshot(userId, assets.totalAsset, assets.creditDebt, assets.finalBalance);
  }

  /**
   * 计算实时统计数据
   */
  static async calculateRealtimeStats(userId) {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const today = now.toISOString().substring(0, 10);
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const monthEndDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 1. 借记卡数量（排除虚拟账户 xxxx, yyyy）
    const [debitRows] = await db.execute(
      `SELECT COUNT(*) as count FROM card_base 
       WHERE user_id = ? AND is_deleted = 0 
       AND card_type != 'credit' AND id NOT IN ('xxxx', 'yyyy')`,
      [userId]
    );
    const debitCardCount = debitRows[0]?.count || 0;

    // 2. 信用卡数量（排除虚拟账户）
    const [creditRows] = await db.execute(
      `SELECT COUNT(*) as count FROM card_base 
       WHERE user_id = ? AND is_deleted = 0 
       AND card_type = 'credit' AND id NOT IN ('xxxx', 'yyyy')`,
      [userId]
    );
    const creditCardCount = creditRows[0]?.count || 0;

    // 3. 信用卡代还金额（所有未还账单汇总）
    const [debtRows] = await db.execute(
      `SELECT COALESCE(SUM(cb.need_repay), 0) as total 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditDebt = parseFloat(debtRows[0]?.total) || 0;

    // 4. 本月统计（收入 - 支出，排除信用卡还款）
    const [monthRows] = await db.execute(
      `SELECT direction, SUM(amount) as total
       FROM account
       WHERE user_id = ? AND is_deleted = 0 
         AND trans_date >= ? AND trans_date < ?
         AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
       GROUP BY direction`,
      [userId, startDate, monthEndDate]
    );

    let monthIncome = 0;
    let monthExpense = 0;
    monthRows.forEach(row => {
      if (row.direction === 1) {
        monthIncome = parseFloat(row.total) || 0;
      } else if (row.direction === 0) {
        monthExpense = parseFloat(row.total) || 0;
      }
    });
    const monthBalance = monthIncome - monthExpense;

    // 5. 今日收支
    const [todayRows] = await db.execute(
      `SELECT direction, SUM(amount) as total
       FROM account
       WHERE user_id = ? AND is_deleted = 0 
         AND trans_date = ?
         AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
       GROUP BY direction`,
      [userId, today]
    );

    let todayIncome = 0;
    let todayExpense = 0;
    todayRows.forEach(row => {
      if (row.direction === 1) {
        todayIncome = parseFloat(row.total) || 0;
      } else if (row.direction === 0) {
        todayExpense = parseFloat(row.total) || 0;
      }
    });

    return {
      debitCardCount,
      creditCardCount,
      creditDebt,
      monthIncome,
      monthExpense,
      monthBalance,
      todayIncome,
      todayExpense
    };
  }

  /**
   * 获取一周内金额最大的流水（收支 + 信用卡还款，取前10名）
   */
  static async getRecentLargeTransactions(userId, limit = 10) {
    // 计算一周前日期
    const now = new Date();
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const weekAgoStr = weekAgo.toISOString().substring(0, 10);

    // 1. 获取一周内收支流水
    const [accountRows] = await db.execute(
      `SELECT id, card_id, direction, amount, category_id, pay_type, trans_date, remark, create_time
       FROM account
       WHERE user_id = ? AND is_deleted = 0 AND trans_date >= ?
       ORDER BY amount DESC LIMIT ?`,
      [userId, weekAgoStr, limit]
    );

    // 2. 获取一周内信用卡还款记录
    const [repayRows] = await db.execute(
      `SELECT id, card_id, repay_amount as amount, repay_time as trans_date, repay_method as pay_type, remark, create_time
       FROM card_repay
       WHERE user_id = ? AND is_deleted = 0 AND repay_time >= ?
       ORDER BY repay_amount DESC LIMIT ?`,
      [userId, weekAgoStr, limit]
    );

    // 3. 合并并按金额降序排列，取前 limit 条
    const allRows = [
      ...accountRows.map(r => ({ ...r, type: 'account' })),
      ...repayRows.map(r => ({ ...r, type: 'repay' }))
    ];

    allRows.sort((a, b) => parseFloat(b.amount) - parseFloat(a.amount));
    return allRows.slice(0, limit);
  }

  /**
   * 获取最近N条快照
   */
  static async getRecent(userId, limit = 30) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND is_deleted = 0
       ORDER BY record_date DESC LIMIT ?`,
      [userId, limit]
    );
    return rows;
  }

  /**
   * 获取指定日期范围的快照
   */
  static async getByDateRange(userId, startDate, endDate) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND is_deleted = 0 
         AND record_date >= ? AND record_date <= ?
       ORDER BY record_date ASC`,
      [userId, startDate, endDate]
    );
    return rows;
  }
}

module.exports = AssetSnapshot;
