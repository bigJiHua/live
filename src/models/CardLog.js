const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 卡片操作日志模型 - 对应数据库 card_log 表
 */
class CardLog {
  static tableName = 'card_log';

  /**
   * 获取卡片日志列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE cl.user_id = ?';
    const params = [userId];

    if (filters.cardId) {
      whereClause += ' AND cl.card_id = ?';
      params.push(filters.cardId);
    }

    if (filters.operateType) {
      whereClause += ' AND cl.operate_type = ?';
      params.push(filters.operateType);
    }

    const query = `
      SELECT cl.*, c.alias as card_alias, c.last4_no as card_last4
      FROM ${this.tableName} cl
      LEFT JOIN card_base c ON cl.card_id = c.id
      ${whereClause}
      ORDER BY cl.operate_time DESC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 创建日志
   */
  static async create({ userId, cardId, operateType, operateTime, operateIp }) {
    const id = idUtils.billId();

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, operate_type, operate_time, operate_ip, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      cardId,
      userId,
      operateType,
      operateTime || String(Date.now()),
      operateIp || ''
    ]);

    return { id, cardId, userId, operateType };
  }

  /**
   * 记录卡片操作
   */
  static async log(cardId, userId, operateType, operateIp) {
    return this.create({
      cardId,
      userId,
      operateType,
      operateTime: String(Date.now()),
      operateIp
    });
  }
}

module.exports = CardLog;
