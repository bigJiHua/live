const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 账户余额模型 - 对应数据库 account_balance 表
 * 用于记录每张卡/账户的实时余额
 * 
 * 支持两种账户类型：
 * 1. 实体卡片：card_id 关联 card_base 表
 * 2. 虚拟账户：xxxx(现金)、yyyy(余额=微信+支付宝)
 */
class AccountBalance {
  static tableName = 'account_balance';

  // 虚拟账户类型
  static VIRTUAL_TYPES = {
    CASH: 'xxxx',      // 现金
    BALANCE: 'yyyy',   // 余额（微信+支付宝）
  };

  // 虚拟账户显示名称
  static VIRTUAL_NAMES = {
    'xxxx': { name: '现金', alias: '现金账户', card_type: 'cash' },
    'yyyy': { name: '余额', alias: '余额账户(微信+支付宝)', card_type: 'digital' },
  };

  /**
   * 获取用户所有账户余额（包括虚拟账户）
   * 虚拟账户: xxxx(现金), yyyy(余额)
   * 实体卡: 关联 card_base 表
   */
  static async findAll(userId) {
    const query = `
      SELECT ab.*, 
             cb.alias as card_alias, 
             cb.last4_no as card_last4, 
             cb.card_type,
             cb.alias as alias
      FROM ${this.tableName} ab
      LEFT JOIN card_base cb ON ab.card_id = cb.id
      WHERE ab.user_id = ? AND ab.is_deleted = 0
      ORDER BY 
        CASE WHEN ab.card_id IN ('xxxx', 'yyyy') THEN 0 ELSE 1 END,
        ab.card_id
    `;
    const [rows] = await db.execute(query, [userId]);
    
    // 补充虚拟账户名称
    return rows.map(row => {
      if (row.card_id === 'xxxx' || row.card_id === 'yyyy') {
        const virtualInfo = this.VIRTUAL_NAMES[row.card_id] || {};
        return {
          ...row,
          alias: row.alias || virtualInfo.alias || row.card_id,
          card_type: row.card_type || virtualInfo.card_type || 'virtual',
        };
      }
      return row;
    });
  }

  /**
   * 获取单个账户余额
   * @param {string} cardId - 卡片ID
   * @param {string} userId - 用户ID
   * @param {string} currency - 币种，默认 CNY
   */
  static async findByCardId(cardId, userId, currency = 'CNY') {
    const query = `
      SELECT ab.*, 
             cb.alias as card_alias, 
             cb.last4_no as card_last4, 
             cb.card_type
      FROM ${this.tableName} ab
      LEFT JOIN card_base cb ON ab.card_id = cb.id
      WHERE ab.card_id = ? AND ab.user_id = ? AND ab.currency = ? AND ab.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [cardId, userId, currency]);
    return rows[0] || null;
  }

  /**
   * 获取虚拟账户余额
   */
  static async findVirtualAccount(cardId, userId) {
    return this.findByCardId(cardId, userId);
  }

  /**
   * 创建或更新账户余额
   * @param {string} cardId - 卡片ID
   * @param {string} userId - 用户ID
   * @param {number} balance - 余额
   * @param {string} currency - 币种，默认 CNY
   */
  static async upsert(cardId, userId, balance, currency = 'CNY') {
    const now = String(Date.now());
    const id = idUtils.billId();

    // 先查询是否存在（按币种查询）
    const existing = await this.findByCardId(cardId, userId, currency);

    if (existing) {
      // 更新
      const query = `
        UPDATE ${this.tableName}
        SET balance = ?, currency = ?, update_time = ?
        WHERE card_id = ? AND user_id = ? AND currency = ?
      `;
      await db.execute(query, [balance, currency, now, cardId, userId, currency]);
    } else {
      // 创建
      const query = `
        INSERT INTO ${this.tableName} (id, user_id, card_id, balance, currency, update_time, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, 0)
      `;
      await db.execute(query, [id, userId, cardId, balance, currency, now]);
    }

    return this.findByCardId(cardId, userId, currency);
  }

  /**
   * 初始化用户的虚拟账户（如果不存在，支持多币种）
   * @param {string} userId - 用户ID
   * @param {string} currency - 币种，默认 CNY
   */
  static async initVirtualAccounts(userId, currency = 'CNY') {
    const results = [];
    for (const [type, cardId] of Object.entries(this.VIRTUAL_TYPES)) {
      const existing = await this.findByCardId(cardId, userId, currency);
      if (!existing) {
        const result = await this.upsert(cardId, userId, 0, currency);
        results.push(result);
      }
    }
    return results;
  }

  /**
   * 根据收支记录更新余额
   * @param {Object} record - 收支记录
   */
  static async syncFromAccount(record) {
    const { card_id, user_id, direction, amount, account_type, currency } = record;
    const currencyCode = currency || 'CNY';
    
    // 跳过虚拟账户的收支同步（虚拟账户需要单独维护）
    if (card_id === 'xxxx' || card_id === 'yyyy') {
      return null;
    }
    
    const current = await this.findByCardId(card_id, user_id, currencyCode);
    const currentBalance = parseFloat(current?.balance || 0);
    const amountNum = parseFloat(amount);

    let newBalance;
    if (account_type === 'credit') {
      // 信用卡：余额为负数（负债）
      if (direction === 0) {
        newBalance = currentBalance - amountNum;
      } else {
        newBalance = currentBalance + amountNum;
      }
    } else {
      // 储蓄卡/现金：余额为正
      if (direction === 0) {
        newBalance = currentBalance - amountNum;
      } else {
        newBalance = currentBalance + amountNum;
      }
    }

    return this.upsert(card_id, user_id, newBalance, currencyCode);
  }

  /**
   * 手动更新虚拟账户余额
   * @param {string} cardId - 卡片ID
   * @param {string} userId - 用户ID
   * @param {number} balance - 余额
   * @param {string} currency - 币种，默认 CNY
   */
  static async updateVirtualBalance(cardId, userId, balance, currency = 'CNY') {
    return this.upsert(cardId, userId, balance, currency);
  }

  /**
   * 删除账户余额（软删除）
   * @param {string} cardId - 卡片ID
   * @param {string} userId - 用户ID
   * @param {string} currency - 币种，默认 CNY
   */
  static async delete(cardId, userId, currency = 'CNY') {
    const now = String(Date.now());
    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1, update_time = ?
      WHERE card_id = ? AND user_id = ? AND currency = ?
    `;
    const [result] = await db.execute(query, [now, cardId, userId, currency]);
    return result.affectedRows > 0;
  }
}

module.exports = AccountBalance;
