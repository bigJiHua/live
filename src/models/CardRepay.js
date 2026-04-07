const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 卡片还款记录模型 - 对应数据库 card_repay 表
 */
class CardRepay {
  static tableName = 'card_repay';

  /**
   * 获取还款记录列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE cr.user_id = ? AND cr.is_deleted = 0';
    const params = [userId];

    if (filters.cardId) {
      whereClause += ' AND cr.card_id = ?';
      params.push(filters.cardId);
    }

    if (filters.billId) {
      whereClause += ' AND cr.bill_id = ?';
      params.push(filters.billId);
    }

    const query = `
      SELECT cr.*, c.alias as card_alias, c.last4_no as card_last4,
             cb.bill_amount, cb.need_repay as bill_need_repay
      FROM ${this.tableName} cr
      LEFT JOIN card_base c ON cr.card_id = c.id
      LEFT JOIN card_bill cb ON cr.bill_id = cb.id
      ${whereClause}
      ORDER BY cr.repay_time DESC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 根据ID查找记录
   */
  static async findById(id, userId) {
    const query = `
      SELECT cr.*, c.alias as card_alias, c.last4_no as card_last4,
             cb.bill_amount, cb.need_repay as bill_need_repay
      FROM ${this.tableName} cr
      LEFT JOIN card_base c ON cr.card_id = c.id
      LEFT JOIN card_bill cb ON cr.bill_id = cb.id
      WHERE cr.id = ? AND cr.user_id = ? AND cr.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建还款记录
   */
  static async create({
    userId,
    cardId,
    billId,
    repayAmount,
    repayMethod,
    repayTime,
    remark
  }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, bill_id, repay_amount,
        repay_method, repay_time, remark, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      cardId,
      userId,
      billId || null,
      repayAmount,
      repayMethod || null,
      repayTime || now,
      remark || null
    ]);

    return this.findById(id, userId);
  }

  /**
   * 更新还款记录
   */
  static async update(id, userId, updates) {
    const fields = [];
    const params = [];

    const fieldMap = {
      repayAmount: 'repay_amount',
      repayMethod: 'repay_method',
      repayTime: 'repay_time',
      remark: 'remark'
    };

    Object.keys(updates).forEach(key => {
      if (fieldMap[key] !== undefined) {
        fields.push(`${fieldMap[key]} = ?`);
        params.push(updates[key]);
      }
    });

    if (fields.length === 0) {
      return this.findById(id, userId);
    }

    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName}
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;
    await db.execute(query, params);
    return this.findById(id, userId);
  }

  /**
   * 删除还款记录（软删除）
   */
  static async delete(id, userId) {
    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1
      WHERE id = ? AND user_id = ?
    `;
    const [result] = await db.execute(query, [id, userId]);
    return result.affectedRows > 0;
  }
}

module.exports = CardRepay;
