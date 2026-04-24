const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const AccountSettlement = require('../service/settlement');
const CardBill = require('../../card/model/bill');
const AssetSnapshot = require('../../asset/model/snapshot');

/**
 * 账务流水模型 - 对应数据库 account 表
 * 
 * 业务规则：
 * 1. 消费时自动同步关联信用卡账单
 * 2. 收支变化时自动记录资产快照
 * 2. 还款时（direction=1, pay_type=还款）自动冲减账单
 * 3. 余额由 AccountSettlement 统一计算
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

    // 过滤：收支类型
    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }

    // 过滤：分类ID
    if (filters.categoryId) {
      whereClause += ' AND a.category_id = ?';
      params.push(filters.categoryId);
    }

    // 过滤：支付方式
    if (filters.payMethod) {
      whereClause += ' AND a.pay_method = ?';
      params.push(filters.payMethod);
    }

    // 过滤：日期范围
    if (filters.startDate) {
      whereClause += ' AND a.trans_date >= ?';
      params.push(filters.startDate);
    }
    if (filters.endDate) {
      whereClause += ' AND a.trans_date <= ?';
      params.push(filters.endDate);
    }

    // 获取总数
    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    // 获取数据（关联分类和卡片信息）
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id,
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
   * 根据ID查找单条记录
   */
  static async findById(id, userId) {
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id,
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

    // 过滤：收支类型
    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }

    // 过滤：日期范围（默认当月）
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

    // 获取总数
    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    // 获取数据（关联分类和卡片信息）
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id,
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
   * 创建账务记录
   * 使用清算中心统一验证
   */
  static async create({ userId, direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    // 从 card_base 获取真实的 card_type
    let finalAccountType = '';
    if (cardId) {
      const [cardRows] = await db.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0',
        [cardId]
      );
      if (cardRows[0]?.card_type) {
        finalAccountType = cardRows[0].card_type;
      }
    }

    // ===== 信用卡不能收入 =====
    if (finalAccountType === 'credit' && direction === 1) {
      throw new Error('信用卡不能登记收入，还款请使用储蓄卡');
    }

    // ===== 清算中心验证 =====
    if (cardId) {
      const settlementResult = await AccountSettlement.validate({
        card_id: cardId,
        user_id: userId,
        direction: direction,
        amount: amount,
        exchange_rate: exchangeRate || 1,
        currency: currency || 'CNY',
        account_type: finalAccountType
      });

      if (!settlementResult.valid) {
        throw new Error(settlementResult.message);
      }
    }

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, account_type, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      userId,
      direction,
      categoryId,
      payType,
      payMethod,
      finalAccountType,
      amount,
      currency || 'CNY',
      exchangeRate || 1,
      transDate || now.substring(0, 10),
      remark || '',
      cardId,
      now,
      now
    ]);

    // ===== 清算中心同步余额（从收支表实时计算）=====
    if (cardId) {
      await AccountSettlement.syncBalanceSnapshot(cardId, userId);

      // ===== 信用卡消费实时更新账单 =====
      // 只有信用卡 + 支出 才更新账单
      if (finalAccountType === 'credit' && direction === 0) {
        await CardBill.syncFromExpense(cardId, userId, amount, transDate);
      }
    }

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return this.findById(id, userId);
  }

  /**
   * 更新账务记录
   */
  static async update(id, userId, { direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId }) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    if (direction !== undefined) {
      fields.push('direction = ?');
      params.push(direction);
    }
    if (categoryId !== undefined) {
      fields.push('category_id = ?');
      params.push(categoryId);
    }
    if (payType !== undefined) {
      fields.push('pay_type = ?');
      params.push(payType);
    }
    if (payMethod !== undefined) {
      fields.push('pay_method = ?');
      params.push(payMethod);
    }
    if (amount !== undefined) {
      fields.push('amount = ?');
      params.push(amount);
    }
    if (currency !== undefined) {
      fields.push('currency = ?');
      params.push(currency);
    }
    if (exchangeRate !== undefined) {
      fields.push('exchange_rate = ?');
      params.push(exchangeRate);
    }
    if (transDate !== undefined) {
      fields.push('trans_date = ?');
      // 确保日期格式为 YYYY-MM-DD
      const dateStr = String(transDate);
      params.push(dateStr.includes('-') ? dateStr.substring(0, 10) : dateStr);
    }
    if (remark !== undefined) {
      fields.push('remark = ?');
      params.push(remark);
    }
    if (cardId !== undefined) {
      fields.push('card_id = ?');
      params.push(cardId);
    }

    if (fields.length === 0) {
      return this.findById(id, userId);
    }

    fields.push('update_time = ?');
    params.push(now);
    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName} 
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;
    await db.execute(query, params);
    
    // 同步余额（如果修改了金额、方向或卡片）
    if (cardId || amount !== undefined || direction !== undefined) {
      const updated = await this.findById(id, userId);
      if (updated?.card_id) {
        await AccountSettlement.syncBalanceSnapshot(updated.card_id, userId);
      }
    }
    
    return updated;
  }

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
   * 借记卡冲正
   * 原支出 -> 冲正收入，原收入 -> 冲正支出
   */
  static async reverseDebitById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');

    const reverseDirection = original.direction === 0 ? 1 : 0;

    // 1. 创建冲正流水
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, reverseDirection, original.category_id,
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `冲正：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(`[借记卡冲正] ${original.card_alias || original.card_id} ${original.direction === 0 ? '支出' : '收入'} ${original.amount} -> 冲正${reverseDirection === 1 ? '收入' : '支出'}，原流水已标记删除`);

    return this.findById(id, userId);
  }

  /**
   * 信用卡消费冲正
   * 原支出 -> 冲正收入 + 恢复账单额度
   */
  static async reverseCreditExpenseById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');

    // 检查该卡是否有未撤销的还款记录
    // TODO 暂时拦截
    const [repayRows] = await db.execute(
      `SELECT id FROM card_repay WHERE card_id = ? AND is_deleted = 0 LIMIT 1`,
      [original.card_id]
    );
    if (repayRows.length > 0) {
      throw new Error('该卡有还款记录，请先撤销还款后再冲正此笔交易');
    }

    // 1. 创建冲正流水
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, 1, original.category_id,
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `冲正：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    // 恢复账单额度
    await CardBill.rollbackExpense(original.card_id, userId, original.amount, original.trans_date);
    
    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(`[信用卡消费冲正] ${original.card_alias || original.card_id} 支出${original.amount} -> 冲正收入 + 恢复账单额度，原流水已标记删除`);

    return this.findById(id, userId);
  }

  /**
   * 信用卡还款撤销冲正
   * 原还款流水 -> 冲正收入（恢复余额）+ 软删除card_repay + 重建账单
   */
  static async reverseCreditRepayById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');
    if (original.category_id !== 'CATEGORY_REPAY') {
      throw new Error('该接口仅支持信用卡还款流水');
    }

    // 1. 创建冲正流水（收入方向，恢复余额）
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, 1, original.category_id,  // direction=1 收入，恢复余额
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `还款撤销：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    // 3. 查询并软删除 card_repay 记录
    const [repayRows] = await db.execute(
      'SELECT * FROM card_repay WHERE account_id = ? AND is_deleted = 0 LIMIT 1',
      [id]
    );

    if (repayRows.length === 0) {
      throw new Error('未找到关联的还款记录');
    }

    const repayRecord = repayRows[0];
    const creditCardId = repayRecord.card_id; // 真正的信用卡ID

    await db.execute(
      'UPDATE card_repay SET is_deleted = 1, update_time = ? WHERE id = ?',
      [now, repayRecord.id]
    );

    console.log(`[信用卡还款撤销] card_repay ${repayRecord.id} 已软删除，卡片=${creditCardId}`);

    // 4. 重建账单（确保账单数据一致性）
    await CardBill.rebuildBillFromAccount(creditCardId, userId);

    await AccountSettlement.syncBalanceSnapshot(creditCardId, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return this.findById(id, userId);
  }

  /**
   * 获取本月收支统计
   */
  static async getMonthStats(userId, year, month) {
    // 计算本月的开始和结束日期
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
  static async getAllStats(userId) {
    const now = new Date();
    const year = parseInt(req?.query?.year) || now.getFullYear();
    const month = parseInt(req?.query?.month) || now.getMonth() + 1;

    // 计算本月日期范围
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 1. 计算总资产 = 所有借记卡/余额账户余额总和 + 信用卡溢缴款
    // 从 account_balance 汇总（排除信用卡）
    const [balanceRows] = await db.execute(
      `SELECT COALESCE(SUM(balance), 0) as total 
       FROM account_balance 
       WHERE user_id = ? AND is_deleted = 0`,
      [userId]
    );
    const totalBalance = parseFloat(balanceRows[0]?.total) || 0;

    // 从 card_bill 统计信用卡溢缴款（溢缴款 = 可用额度 - 额度）
    const [creditBillRows] = await db.execute(
      `SELECT COALESCE(SUM(avail_limit - credit_limit - temp_limit), 0) as overflow 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditOverflow = parseFloat(creditBillRows[0]?.overflow) || 0;
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

    // 3. 信用卡代还金额（所有未还账单汇总）
    const [repayRows] = await db.execute(
      `SELECT COALESCE(SUM(need_repay), 0) as total 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditCardDebt = parseFloat(repayRows[0]?.total) || 0;

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
      totalAsset,       // 总资产
      bankCardCount,    // 银行卡数量
      creditCardCount,  // 信用卡数量
      creditCardDebt,   // 信用卡代还金额（未还账单）
      monthIncome,      // 本月收入
      monthExpense,     // 本月支出
      monthBalance      // 本月结余
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

    // 查询时计算人民币价值
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
}

module.exports = Account;
