const db = require('../config/db');
const idUtils = require('../utils/idUtils');
const AccountSettlement = require('../services/AccountSettlement');
const CardBill = require('./CardBill');

/**
 * 账务流水模型 - 对应数据库 account 表
 * 
 * 业务规则：
 * 1. 消费时自动同步关联信用卡账单
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
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time,
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
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建账务记录
   * 使用清算中心统一验证
   */
  static async create({ userId, direction, categoryId, payType, payMethod, accountType, amount, currency, exchangeRate, transDate, remark, cardId }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    // 从 card_base 获取真实的 card_type
    let finalAccountType = 'debit';
    if (cardId) {
      const [cardRows] = await db.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0',
        [cardId]
      );
      if (cardRows[0]?.card_type) {
        finalAccountType = cardRows[0].card_type;
      }
    }

    // ===== 清算中心验证 =====
    if (cardId) {
      const settlementResult = await AccountSettlement.validate({
        card_id: cardId,
        user_id: userId,
        direction: direction,
        amount: amount,
        exchange_rate: exchangeRate || 1,
        account_type: finalAccountType
      });

      if (!settlementResult.valid) {
        throw new Error(settlementResult.message);
      }
    }

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      userId,
      direction,
      categoryId,
      payType,
      payMethod,
      amount,
      currency || 'CNY',
      exchangeRate || 1,
      transDate || now,
      remark || '普通支出',
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
      params.push(transDate);
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
   * 删除账务记录（软删除）
   */
  static async delete(id, userId) {
    const now = String(Date.now());
    
    // 先查询完整记录
    const [rows] = await db.execute(
      'SELECT card_id, direction, amount, trans_date FROM account WHERE id = ? AND user_id = ? AND is_deleted = 0',
      [id, userId]
    );
    const record = rows[0];
    
    const query = `
      UPDATE ${this.tableName} 
      SET is_deleted = 1, update_time = ?
      WHERE id = ? AND user_id = ?
    `;
    const [result] = await db.execute(query, [now, id, userId]);
    
    // 删除成功后同步余额和账单
    if (result.affectedRows > 0 && record?.card_id) {
      await AccountSettlement.syncBalanceSnapshot(record.card_id, userId);
      
      // 信用卡消费回滚：删除支出流水时恢复账单额度
      if (record.direction === 0) {
        await CardBill.rollbackExpense(record.card_id, userId, record.amount, record.trans_date);
      }
    }
    
    return result.affectedRows > 0;
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
    const query = `
      SELECT 
        direction,
        SUM(CASE WHEN currency = 'CNY' THEN amount ELSE ROUND(amount * exchange_rate / 100, 2) END) as total,
        COUNT(*) as count
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 
        AND trans_date >= ? AND trans_date < ?
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
