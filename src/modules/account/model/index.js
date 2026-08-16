const db = require('../../../common/config/db');

// 近月收支笔数缓存：避免每次刷新都做全量聚合查询（按 用户 隔离，TTL 60s）
const FLOW_STATS_CACHE_TTL = 60 * 1000;
const flowStatsCache = new Map(); // key: `${userId}:${months}` -> { ts, data }

/**
 * 账务流水模型 - 对应数据库 account 表（公共只读层）
 * 
 * 本文件仅保留只读查询、统计、备注修改方法，供通用控制器和 debit/credit 模块调用。
 * 写入方法（create/update/reverse）已拆分到：
 *   - model/debit.js  借记卡写入
 *   - model/credit.js 信用卡写入
 * 两者独立实现，不共用写入方法，避免数据交叉出错。
 */
class Account {
  static tableName = 'account';

  /**
   * 获取收支列表（支持分页和过滤）
   */
  static async findAll(userId, filters = {}, page = 1, limit = 20) {
    const offset = (page - 1) * limit;

    let whereClause = 'WHERE a.user_id = ? AND a.is_deleted = 0';
    const params = [userId];

    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }
    if (filters.categoryId) {
      whereClause += ' AND a.category_id = ?';
      params.push(filters.categoryId);
    }
    if (filters.payMethod) {
      whereClause += ' AND a.pay_method = ?';
      params.push(filters.payMethod);
    }
    if (filters.startDate) {
      whereClause += ' AND a.trans_date >= ?';
      params.push(filters.startDate);
    }
    if (filters.endDate) {
      whereClause += ' AND a.trans_date <= ?';
      params.push(filters.endDate);
    }

    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      ${whereClause}
      ORDER BY a.trans_date DESC, a.create_time DESC
      LIMIT ? OFFSET ?
    `;
    const queryParams = [...params, String(limit), String(offset)]
    const [rows] = await db.execute(query, queryParams);

    return { rows, total };
  }

  /**
   * 根据ID查找单条记录
   */
  static async findById(id, userId) {
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4,
        rb.remark as reversed_remark
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      LEFT JOIN ${this.tableName} rb ON a.reversed_id = rb.id
      WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 根据卡片ID获取收支流水（默认本月）
   */
  static async findByCardId(cardId, userId, filters = {}, page = 1, limit = 20) {
    const offset = (page - 1) * limit;

    let whereClause = 'WHERE a.card_id = ? AND a.user_id = ? AND a.is_deleted = 0';
    const params = [cardId, userId];

    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }

    let startDate = filters.startDate;
    let endDate = filters.endDate;
    if (!startDate && !endDate) {
      const now = new Date();
      const year = now.getFullYear();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      startDate = `${year}-${month}-01`;
      const lastDay = new Date(year, now.getMonth() + 1, 0).getDate();
      endDate = `${year}-${month}-${lastDay}`;
    }
    if (startDate) {
      whereClause += ' AND a.trans_date >= ?';
      params.push(startDate);
    }
    if (endDate) {
      whereClause += ' AND a.trans_date <= ?';
      params.push(endDate);
    }

    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      ${whereClause}
      ORDER BY a.trans_date DESC, a.create_time DESC
      LIMIT ? OFFSET ?
    `;
    const [rows] = await db.execute(query, [...params, String(limit), String(offset)]);

    return { rows, total };
  }

  /**
   * 创建账务记录已拆分：
   * - 借记卡/转账/还款支出流水 → model/debit.js DebitAccount.create
   * - 信用卡消费 → model/credit.js CreditAccount.create
   */

  /**
   * 更新账务记录已拆分：
   * - 借记卡流水 → model/debit.js DebitAccount.update
   * - 信用卡消费 → model/credit.js CreditAccount.update
   */

  /**
   * 仅修改备注
   */
  static async updateRemark(id, userId, remark) {
    const now = String(Date.now());

    const query = `
      UPDATE ${this.tableName}
      SET remark = ?, update_time = ?
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;

    const [result] = await db.execute(query, [remark, now, id, userId]);

    if (result.affectedRows === 0) {
      return null;
    }

    return this.findById(id, userId);
  }

  /**
   * 冲正方法已拆分：
   * - 借记卡冲正 → model/debit.js DebitAccount.reverseDebitById
   * - 转账冲正 → model/debit.js DebitAccount.reverseTransferById
   * - 信用卡消费冲正 → model/credit.js CreditAccount.reverseCreditExpenseById
   * - 信用卡还款冲正 → model/credit.js CreditAccount.reverseCreditRepayById
   */

  /**
   * 根据 transfer_group_id 查找同组转账记录
   */
  static async findByTransferGroup(transferGroupId, userId) {
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id
      FROM ${this.tableName} a
      WHERE a.transfer_group_id = ? AND a.user_id = ? AND a.is_deleted = 0
      ORDER BY a.direction ASC
    `;
    const [rows] = await db.execute(query, [transferGroupId, userId]);
    return rows;
  }

  /**
   * 获取转账明细列表（含自动同步）
   */
  static async getTransferList(userId, page = 1, limit = 20, yearMonth) {
    const now = String(Date.now());

    // 1. 查出所有未配对的转账记录（无 transfer_group_id），不限量自动配对
    const [unpairedRows] = await db.execute(`
      SELECT id, card_id, direction, amount, trans_date, remark
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 AND pay_type = '转账' AND transfer_group_id IS NULL
      ORDER BY create_time DESC
    `, [userId]);

    const pairMap = {};
    for (const row of unpairedRows) {
      const key = `${Number(row.amount).toFixed(2)}_${row.trans_date}`;
      if (!pairMap[key]) pairMap[key] = [];
      pairMap[key].push(row);
    }

    for (const key of Object.keys(pairMap)) {
      const rows = pairMap[key];
      const expenses = rows.filter((r) => r.direction === 0);
      const incomes = rows.filter((r) => r.direction === 1);
      if (expenses.length !== 1 || incomes.length !== 1) {
        console.warn(`[转账同步] 跳过歧义配对 key=${key}，支出=${expenses.length}，收入=${incomes.length}`);
        continue;
      }

      const expense = expenses[0];
      const income = incomes[0];
      if (expense.card_id === income.card_id) {
        console.warn(`[转账同步] 跳过同卡配对 key=${key}，card=${expense.card_id}`);
        continue;
      }

      const groupId = expense.id;
      await db.execute(
        `UPDATE ${this.tableName} SET transfer_group_id = ? WHERE id IN (?, ?) AND user_id = ?`,
        [groupId, expense.id, income.id, userId]
      );
      try {
        await db.execute(`
          INSERT INTO account_transfer (id, user_id, from_card_id, to_card_id, amount, trans_date, remark, create_time, is_deleted)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
          ON DUPLICATE KEY UPDATE is_deleted = 0, from_card_id = VALUES(from_card_id), to_card_id = VALUES(to_card_id)
        `, [
          groupId, userId, expense.card_id, income.card_id,
          expense.amount, expense.trans_date, expense.remark || '转账', now
        ]);
        console.log(`[转账同步] 自动配对 account_transfer: ${groupId}`);
      } catch (e) {
        console.warn(`[转账同步] 配对写入失败 ${groupId}:`, e.message);
      }
    }

    // 2. 处理已有 transfer_group_id 但未同步到 account_transfer 的记录
    const [groupedRows] = await db.execute(`
      SELECT DISTINCT transfer_group_id
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 AND pay_type = '转账' AND transfer_group_id IS NOT NULL
    `, [userId]);
    const allGroupIds = groupedRows.map((r) => r.transfer_group_id);

    if (allGroupIds.length > 0) {
      const placeholders = allGroupIds.map(() => '?').join(',');
      const [existingTransfers] = await db.execute(
        `SELECT id FROM account_transfer WHERE id IN (${placeholders}) AND user_id = ? AND is_deleted = 0`,
        [...allGroupIds, userId]
      );
      const existingIds = new Set(existingTransfers.map((r) => r.id));

      for (const groupId of allGroupIds) {
        if (existingIds.has(groupId)) continue;

        const [reversedCheck] = await db.execute(
          `SELECT COUNT(*) as cnt FROM ${this.tableName}
           WHERE transfer_group_id = ? AND user_id = ? AND is_deleted = 1 AND reversed_id IS NOT NULL`,
          [groupId, userId]
        );
        if (reversedCheck[0].cnt > 0) continue;

        const [pairRows] = await db.execute(
          `SELECT id, card_id, direction, amount, trans_date, remark FROM ${this.tableName}
           WHERE transfer_group_id = ? AND user_id = ? AND is_deleted = 0
           ORDER BY direction ASC`,
          [groupId, userId]
        );
        const expense = pairRows.find((r) => r.direction === 0);
        const income = pairRows.find((r) => r.direction === 1);
        if (!expense || !income) continue;

        try {
          await db.execute(`
            INSERT INTO account_transfer (id, user_id, from_card_id, to_card_id, amount, trans_date, remark, create_time, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE is_deleted = 0, from_card_id = VALUES(from_card_id), to_card_id = VALUES(to_card_id)
          `, [
            groupId, userId, expense.card_id, income.card_id,
            expense.amount, expense.trans_date, expense.remark || '转账', now
          ]);
          console.log(`[转账同步] 自动补全 account_transfer: ${groupId}`);
        } catch (e) {
          console.warn(`[转账同步] 写入失败 ${groupId}:`, e.message);
        }
      }
    }

    // 3. 统计总数（从 account_transfer 表）
    let countSql = 'SELECT COUNT(*) as total FROM account_transfer WHERE user_id = ? AND is_deleted = 0';
    const countParams = [userId];
    if (yearMonth) {
      countSql += ' AND (trans_date LIKE ? OR trans_date REGEXP ?)';
      countParams.push(yearMonth + '%', '^[0-9]+$');
    }
    const [countResult] = await db.execute(countSql, countParams);
    const total = countResult[0].total;

    // 4. 分页获取最终数据
    const offset = (page - 1) * limit;
    let listSql = 'SELECT * FROM account_transfer WHERE user_id = ? AND is_deleted = 0';
    const listParams = [userId];
    if (yearMonth) {
      listSql += ' AND (trans_date LIKE ? OR trans_date REGEXP ?)';
      listParams.push(yearMonth + '%', '^[0-9]+$');
    }
    listSql += ' ORDER BY trans_date DESC, create_time DESC LIMIT ? OFFSET ?';
    listParams.push(limit, offset);
    const [finalList] = await db.execute(listSql, listParams);

    // 5. 标准化 trans_date：时间戳转 YYYY-MM-DD
    const normalized = finalList.map((row) => {
      const td = row.trans_date;
      if (td && !/^\d{4}-\d{2}-\d{2}/.test(td)) {
        const ts = Number(td);
        if (!isNaN(ts) && ts > 1000000000) {
          const d = new Date(ts > 1000000000000 ? ts : ts * 1000);
          const y = d.getFullYear();
          const m = String(d.getMonth() + 1).padStart(2, '0');
          const day = String(d.getDate()).padStart(2, '0');
          row.trans_date = `${y}-${m}-${day}`;
        }
      }
      return row;
    });

    return { list: normalized, total };
  }

  /**
   * 获取本月收支统计
   */
  static async getMonthStats(userId, year, month) {
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 查询时计算人民币价值：CNY 直接用，外币用 amount * exchange_rate / 100
    // 排除信用卡还款（CATEGORY_REPAY），因为它不算支出而是账单还款
    const query = `
      SELECT 
        direction,
        SUM(CASE WHEN currency = 'CNY' THEN amount ELSE ROUND(amount * exchange_rate / 100, 2) END) as total,
        COUNT(*) as count
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 
        AND trans_date >= ? AND trans_date < ?
        AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
      GROUP BY direction
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    let income = 0;
    let expense = 0;
    let incomeCount = 0;
    let expenseCount = 0;

    rows.forEach(row => {
      if (row.direction === 1) {
        income = parseFloat(row.total) || 0;
        incomeCount = row.count;
      } else if (row.direction === 0) {
        expense = parseFloat(row.total) || 0;
        expenseCount = row.count;
      }
    });

    return {
      year,
      month,
      income,
      expense,
      balance: income - expense,
      incomeCount,
      expenseCount
    };
  }

  /**
   * 获取全量统计（总资产、卡片数量、信用卡欠款等）
   */
  static async getAllStats(userId, year, month) {
    const now = new Date();
    const targetYear = parseInt(year) || now.getFullYear();
    const targetMonth = parseInt(month) || now.getMonth() + 1;

    const startDate = `${targetYear}-${String(targetMonth).padStart(2, '0')}-01`;
    const nextMonth = targetMonth === 12 ? 1 : targetMonth + 1;
    const nextYear = targetMonth === 12 ? targetYear + 1 : targetYear;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 1. 计算总资产 = 所有借记卡/余额账户余额总和 + 信用卡溢缴款
    const [balanceRows] = await db.execute(
      `SELECT COALESCE(SUM(balance), 0) as total 
       FROM account_balance 
       WHERE user_id = ? AND is_deleted = 0`,
      [userId]
    );
    const totalBalance = parseFloat(balanceRows[0]?.total) || 0;

    // 信用卡溢缴款 / 代还金额：统一 CreditCore.aggregate 口径（H10 审计），
    // 避免共享池每张卡存池额度导致溢缴重复计算、负债口径不一致
    const CreditCore = require('../../card/core/CreditCore');
    const agg = await CreditCore.aggregate(userId);
    const creditOverflow = agg.totalOverflow || 0;
    const totalAsset = totalBalance + Math.max(0, creditOverflow);

    // 2. 银行卡数量（排除虚拟账户）
    const [bankCardRows] = await db.execute(
      `SELECT card_type, COUNT(*) as count 
       FROM card_base 
       WHERE user_id = ? AND is_deleted = 0 AND id NOT IN ('xxxx', 'yyyy')
       GROUP BY card_type`,
      [userId]
    );
    let bankCardCount = 0;
    let creditCardCount = 0;
    bankCardRows.forEach(row => {
      if (row.card_type === 'credit') {
        creditCardCount = row.count;
      } else {
        bankCardCount += row.count;
      }
    });

    // 3. 信用卡代还金额（CreditCore 统一口径，覆盖全部账单月）
    const creditCardDebt = agg.totalDebt || 0;

    // 4. 本月收支统计
    const [monthStatsRows] = await db.execute(
      `SELECT 
        direction,
        SUM(CASE WHEN currency = 'CNY' THEN amount ELSE ROUND(amount * exchange_rate / 100, 2) END) as total
       FROM ${this.tableName}
       WHERE user_id = ? AND is_deleted = 0 
         AND trans_date >= ? AND trans_date < ?
         AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
       GROUP BY direction`,
      [userId, startDate, endDate]
    );

    let monthIncome = 0;
    let monthExpense = 0;
    monthStatsRows.forEach(row => {
      if (row.direction === 1) {
        monthIncome = parseFloat(row.total) || 0;
      } else if (row.direction === 0) {
        monthExpense = parseFloat(row.total) || 0;
      }
    });
    const monthBalance = monthIncome - monthExpense;

    return {
      totalAsset,
      bankCardCount,
      creditCardCount,
      creditCardDebt,
      monthIncome,
      monthExpense,
      monthBalance
    };
  }

  /**
   * 按分类统计收支（人民币价值）
   */
  static async getStatsByCategory(userId, year, month) {
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    const query = `
      SELECT 
        a.direction,
        a.category_id,
        c.name as category_name,
        SUM(CASE WHEN a.currency = 'CNY' THEN a.amount ELSE ROUND(a.amount * a.exchange_rate / 100, 2) END) as total,
        COUNT(*) as count
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      WHERE a.user_id = ? AND a.is_deleted = 0 
        AND a.trans_date >= ? AND a.trans_date < ?
      GROUP BY a.direction, a.category_id
      ORDER BY a.direction, total DESC
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    const income = [];
    const expense = [];

    rows.forEach(row => {
      const item = {
        categoryId: row.category_id,
        categoryName: row.category_name || '未分类',
        total: parseFloat(row.total) || 0,
        count: row.count
      };
      if (row.direction === 1) {
        income.push(item);
      } else {
        expense.push(item);
      }
    });

    return { income, expense };
  }

  /**
   * 获取所有银行卡近 N 个月「支出/收入笔数」（按卡聚合）。
   * 转账已在 account 表以方向记录（支出 direction=0 / 收入 direction=1），自然计入出入，无需特殊处理。
   * 颗粒度严格到每张卡：返回所有非虚拟银行卡（借记+信用）的计数，无动账的卡默认 0 0。
   * @param {string} userId
   * @param {number} months 默认 6
   * @returns {Promise<{start:string, end:string, list:Array<{cardId:string, expenseCount:number, incomeCount:number}>}>}
   */
  static async getCardsFlowStats(userId, months = 6) {
    // 1 分钟内命中缓存直接返回，避免大量聚合查询
    const cacheKey = `${userId}:${months}`;
    const cached = flowStatsCache.get(cacheKey);
    if (cached && Date.now() - cached.ts < FLOW_STATS_CACHE_TTL) {
      return cached.data;
    }

    const now = new Date();
    // 近 N 个月 = 本月 + 往前 (N-1) 个月，起始取该月 1 号，结束取今天（含）
    const start = new Date(now.getFullYear(), now.getMonth() - (months - 1), 1);
    const pad = (n) => String(n).padStart(2, '0');
    const startDate = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-01`;
    const endDate = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

    // 1. 取所有非虚拟银行卡 id（借记 + 信用），保证覆盖页面展示的每一张卡
    const [cards] = await db.execute(
      `SELECT id FROM card_base
       WHERE user_id = ? AND is_deleted = 0
         AND id NOT IN ('xxxx', 'yyyy')
         AND card_type IN ('debit', 'credit')`,
      [userId]
    );
    const cardIds = cards.map((c) => c.id);

    // 2. 初始化结果，所有卡默认 0 0
    const resultMap = {};
    cardIds.forEach((id) => {
      resultMap[id] = { cardId: id, expenseCount: 0, incomeCount: 0 };
    });

    // 3. 按卡 + 方向聚合近 N 个月的笔数
    if (cardIds.length > 0) {
      const placeholders = cardIds.map(() => '?').join(',');
      const [rows] = await db.execute(
        `SELECT card_id, direction, COUNT(*) as cnt
         FROM ${this.tableName}
         WHERE user_id = ? AND is_deleted = 0
           AND trans_date >= ? AND trans_date <= ?
           AND card_id IN (${placeholders})
         GROUP BY card_id, direction`,
        [userId, startDate, endDate, ...cardIds]
      );
      rows.forEach((r) => {
        const entry = resultMap[r.card_id];
        if (!entry) return;
        if (r.direction === 0) entry.expenseCount = r.cnt;
        else if (r.direction === 1) entry.incomeCount = r.cnt;
      });
    }

    const result = { start: startDate, end: endDate, list: Object.values(resultMap) };
    flowStatsCache.set(cacheKey, { ts: Date.now(), data: result });
    return result;
  }
}

module.exports = Account;
