const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 账户清算中心
 * 支持多币种，按 card_id + currency 存储余额
 */
class AccountSettlement {
  /**
   * 计算余额（从 account 表汇总，换算成人民币价值）
   * CNY（人民币）：金额 * 1（1:1 不变）
   * 外币：金额 * 汇率 / 100（前端传来的是"100外币兑人民币价格"）
   * 注意：跳过 reversed_id 不为空的流水（已被冲正，不应计入余额）
   */
  static async calculateBalance(cardId, userId) {
    const query = `
      SELECT 
        currency,
        direction,
        amount,
        exchange_rate
      FROM account
      WHERE card_id = ? AND user_id = ? AND is_deleted = 0 AND reversed_id IS NULL
    `;
    const [rows] = await db.execute(query, [cardId, userId]);
    
    let income = 0;
    let expense = 0;
    
    for (const row of rows) {
      const amount = parseFloat(row.amount) || 0;
      const rate = parseFloat(row.exchange_rate) || 1;
      
      // 换算成人民币：CNY 直接用，外币用 金额 * 汇率 / 100
      let cnyAmount = amount;
      if (row.currency !== 'CNY') {
        cnyAmount = Math.round(amount * rate / 100 * 100) / 100;
      }
      
      if (row.direction === 1) {
        income += cnyAmount;
      } else {
        expense += cnyAmount;
      }
    }
    
    return { 
      income: Math.round(income * 100) / 100, 
      expense: Math.round(expense * 100) / 100, 
      balance: Math.round((income - expense) * 100) / 100, 
      totalCount: rows.length 
    };
  }

  /**
   * 获取余额快照
   */
  static async getBalanceSnapshot(cardId, userId) {
    const query = `
      SELECT * FROM account_balance 
      WHERE card_id = ? AND user_id = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [cardId, userId]);
    return rows[0] || null;
  }

  /**
   * 获取用户所有账户余额
   */
  static async getAllBalances(userId) {
    const query = `
      SELECT card_id,
             SUM(CASE WHEN direction = 1 THEN 
               CASE WHEN currency = 'CNY' THEN amount 
               ELSE ROUND(amount * exchange_rate / 100, 2) END 
             ELSE 0 END) as income,
             SUM(CASE WHEN direction = 0 THEN 
               CASE WHEN currency = 'CNY' THEN amount 
               ELSE ROUND(amount * exchange_rate / 100, 2) END 
             ELSE 0 END) as expense,
             COUNT(*) as total_count
      FROM account
      WHERE user_id = ? AND is_deleted = 0 AND card_id IS NOT NULL
      GROUP BY card_id
    `;
    const [rows] = await db.execute(query, [userId]);
    
    return rows.map(row => ({
      card_id: row.card_id,
      income: parseFloat(row.income || 0),
      expense: parseFloat(row.expense || 0),
      balance: parseFloat(row.income || 0) - parseFloat(row.expense || 0),
      total_count: parseInt(row.total_count || 0),
    }));
  }

  /**
   * 验证交易
   * - 信用卡（credit）：验证额度不能超限
   * - 虚拟账户（xxxx=现金，yyyy=余额）：需要验证余额
   * - 储蓄卡（debit）：需要验证余额
   */
  static async validate(record) {
    const { card_id, user_id, direction, amount, exchange_rate, currency } = record;
    
    // 转换后的金额（人民币）
    const amountNum = parseFloat(amount) || 0;
    let amountInCNY = amountNum;
    if (currency !== 'CNY') {
      const rate = parseFloat(exchange_rate) || 1;
      amountInCNY = Math.round(amountNum * rate / 100 * 100) / 100;
    }

    // 虚拟账户（xxxx=现金，yyyy=余额）
    if (card_id === 'xxxx' || card_id === 'yyyy') {
      const balanceInfo = await this.calculateBalance(card_id, user_id);
      const currentBalance = balanceInfo.balance;
      
      // 收入总是允许
      if (direction === 1) {
        return { valid: true, message: '收入允许', currentBalance };
      }
      // 无收支记录不能支出
      if (balanceInfo.totalCount === 0) {
        return { valid: false, message: '该账户尚无收支记录，请先登记一笔收入', currentBalance };
      }
      // 余额不足不能支出
      if (currentBalance < amountInCNY) {
        return { valid: false, message: `余额不足，当前 ${currentBalance}，需要 ${amountInCNY}`, currentBalance };
      }
      return { valid: true, message: '验证通过', currentBalance };
    }

    // 查询实体卡片类型
    const [cardRows] = await db.execute(
      'SELECT card_type, credit_limit, temp_limit FROM card_base WHERE id = ? AND is_deleted = 0',
      [card_id]
    );
    const cardType = cardRows[0]?.card_type;
    const creditLimit = parseFloat(cardRows[0]?.credit_limit) || 0;
    const tempLimit = parseFloat(cardRows[0]?.temp_limit) || 0;

    // 信用卡：验证额度不能超限（额度从 card_base 表获取）
    if (cardType === 'credit') {
      const totalLimit = creditLimit + tempLimit;
      
      // 计算已用额度 = 消费 - 还款
      const balanceInfo = await this.calculateBalance(card_id, user_id);
      const usedLimit = balanceInfo.expense - balanceInfo.income; // 已消费 - 已还款
      const availLimit = totalLimit - usedLimit;
      
      // 收入（还款）总是允许
      if (direction === 1) {
        return { valid: true, message: '信用卡还款允许', currentBalance: availLimit };
      }
      
      // 检查是否超额度
      if (usedLimit + amountInCNY > totalLimit) {
        return { 
          valid: false, 
          message: `超过信用卡额度，额度 ${totalLimit}，已用 ${usedLimit}，本次 ${amountInCNY}`,
          currentBalance: availLimit
        };
      }
      
      return { valid: true, message: '验证通过', currentBalance: availLimit };
    }

    // 储蓄卡
    const balanceInfo = await this.calculateBalance(card_id, user_id);
    const currentBalance = balanceInfo.balance;

    // 收入总是允许
    if (direction === 1) {
      return { valid: true, message: '收入允许', currentBalance };
    }

    // 储蓄卡：必须有收支记录才能支出
    if (balanceInfo.totalCount === 0) {
      return { valid: false, message: '该账户尚无收支记录，请先登记一笔收入', currentBalance };
    }

    // 储蓄卡：余额不足不能支出
    if (currentBalance < amountInCNY) {
      return { valid: false, message: `余额不足，当前 ${currentBalance}，需要 ${amountInCNY}`, currentBalance };
    }

    return { valid: true, message: '验证通过', currentBalance };
  }

  /**
   * 同步余额快照
   * 同时更新信用卡 card_bill 的已用额度和可用额度
   */
  static async syncBalanceSnapshot(cardId, userId) {
    const balanceInfo = await this.calculateBalance(cardId, userId);
    const now = String(Date.now());
    const id = idUtils.billId();
    
    // 使用 INSERT ... ON DUPLICATE KEY UPDATE 防止重复
    await db.execute(
      `INSERT INTO account_balance (id, user_id, card_id, balance, update_time, is_deleted) 
       VALUES (?, ?, ?, ?, ?, 0)
       ON DUPLICATE KEY UPDATE
         balance = VALUES(balance),
         update_time = VALUES(update_time),
         is_deleted = 0`,
      [id, userId, cardId, balanceInfo.balance, now]
    );

    return this.getBalanceSnapshot(cardId, userId);
  }

  /**
   * 初始化卡片余额
   */
  static async initCardBalance(cardId, userId) {
    const existing = await this.getBalanceSnapshot(cardId, userId);
    if (!existing) {
      await this.syncBalanceSnapshot(cardId, userId);
    }
    return this.getBalanceSnapshot(cardId, userId);
  }

  /**
   * 重建所有余额
   */
  static async rebuildAllBalances(userId) {
    const balances = await this.getAllBalances(userId);
    for (const item of balances) {
      await this.syncBalanceSnapshot(item.card_id, userId);
    }
    return balances;
  }
}

module.exports = AccountSettlement;
