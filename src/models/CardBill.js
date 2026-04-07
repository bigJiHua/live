const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 卡片账单模型 - 对应数据库 card_bill 表
 */
class CardBill {
  static tableName = 'card_bill';

  /**
   * 获取卡片账单列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE cb.user_id = ? AND cb.is_deleted = 0';
    const params = [userId];

    if (filters.cardId) {
      whereClause += ' AND cb.card_id = ?';
      params.push(filters.cardId);
    }

    const query = `
      SELECT cb.*, c.alias as card_alias, c.last4_no as card_last4
      FROM ${this.tableName} cb
      LEFT JOIN card_base c ON cb.card_id = c.id
      ${whereClause}
      ORDER BY cb.update_time DESC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 根据卡片ID获取最新账单
   */
  static async findLatestByCardId(cardId) {
    const query = `
      SELECT * FROM ${this.tableName}
      WHERE card_id = ? AND is_deleted = 0
      ORDER BY update_time DESC
      LIMIT 1
    `;
    const [rows] = await db.execute(query, [cardId]);
    return rows[0] || null;
  }

  /**
   * 根据ID查找账单
   */
  static async findById(id, userId) {
    const query = `
      SELECT cb.*, c.alias as card_alias, c.last4_no as card_last4
      FROM ${this.tableName} cb
      LEFT JOIN card_base c ON cb.card_id = c.id
      WHERE cb.id = ? AND cb.user_id = ? AND cb.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建账单
   */
  static async create({
    userId,
    cardId,
    creditLimit,
    availLimit,
    usedLimit,
    tempLimit,
    billStartDate,
    billEndDate,
    billAmount,
    minRepay,
    repaid,
    needRepay,
    points,
    pointsExpire,
    repayStatus,
    isOverdue,
    overdueDays,
    remindSwitch,
    remindDays
  }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, credit_limit, avail_limit, used_limit,
        temp_limit, bill_start_date, bill_end_date, bill_amount,
        min_repay, repaid, need_repay, points, points_expire,
        repay_status, is_overdue, overdue_days, remind_switch, remind_days,
        update_time, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      cardId,
      userId,
      creditLimit || null,
      availLimit || null,
      usedLimit || null,
      tempLimit || null,
      billStartDate || null,
      billEndDate || null,
      billAmount || null,
      minRepay || null,
      repaid || null,
      needRepay || null,
      points || null,
      pointsExpire || null,
      repayStatus || null,
      isOverdue ? 1 : 0,
      overdueDays || 0,
      remindSwitch ? 1 : 0,
      remindDays || 0,
      now
    ]);

    return this.findById(id, userId);
  }

  /**
   * 更新账单
   */
  static async update(id, userId, updates) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    const fieldMap = {
      creditLimit: 'credit_limit',
      availLimit: 'avail_limit',
      usedLimit: 'used_limit',
      tempLimit: 'temp_limit',
      billStartDate: 'bill_start_date',
      billEndDate: 'bill_end_date',
      billAmount: 'bill_amount',
      minRepay: 'min_repay',
      repaid: 'repaid',
      needRepay: 'need_repay',
      points: 'points',
      pointsExpire: 'points_expire',
      repayStatus: 'repay_status',
      isOverdue: 'is_overdue',
      overdueDays: 'overdue_days',
      remindSwitch: 'remind_switch',
      remindDays: 'remind_days'
    };

    Object.keys(updates).forEach(key => {
      if (fieldMap[key] !== undefined) {
        let value = updates[key];
        if (key === 'isOverdue' || key === 'remindSwitch') {
          value = value ? 1 : 0;
        }
        fields.push(`${fieldMap[key]} = ?`);
        params.push(value);
      }
    });

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
    return this.findById(id, userId);
  }

  /**
   * 删除账单（软删除）
   */
  static async delete(id, userId) {
    const now = String(Date.now());
    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1, update_time = ?
      WHERE id = ? AND user_id = ?
    `;
    const [result] = await db.execute(query, [now, id, userId]);
    return result.affectedRows > 0;
  }
}

module.exports = CardBill;
