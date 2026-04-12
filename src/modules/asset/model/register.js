const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');

/**
 * 总资产登记表 - 用户手动核算登记
 */
class AssetRegister {
  static tableName = 'asset_register';

  /**
   * 用户登记资产
   */
  static async create({ userId, totalAsset, creditDebt, totalBalance, assetDetails, remark }) {
    const id = idUtils.billId();
    const now = String(Date.now());
    const today = now.substring(0, 10);
    const registerTime = new Date().toLocaleString('zh-CN', { hour12: false });

    await db.execute(
      `INSERT INTO ${this.tableName} 
       (id, user_id, total_asset, credit_debt, total_balance, asset_details, register_date, register_time, remark, create_time, update_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
      [id, userId, totalAsset, creditDebt, totalBalance, JSON.stringify(assetDetails), today, registerTime, remark || null, now, now]
    );

    return this.findById(id, userId);
  }

  /**
   * 根据ID查询
   */
  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  /**
   * 获取用户所有登记记录
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE user_id = ? AND is_deleted = 0';
    const params = [userId];

    if (filters.startDate) {
      whereClause += ' AND register_date >= ?';
      params.push(filters.startDate);
    }
    if (filters.endDate) {
      whereClause += ' AND register_date <= ?';
      params.push(filters.endDate);
    }

    const query = `
      SELECT * FROM ${this.tableName}
      ${whereClause}
      ORDER BY register_date DESC, create_time DESC
    `;

    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 获取最新登记记录
   */
  static async getLatest(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} 
       WHERE user_id = ? AND is_deleted = 0
       ORDER BY register_date DESC, create_time DESC LIMIT 1`,
      [userId]
    );
    return rows[0] || null;
  }

  /**
   * 更新登记记录
   */
  static async update(id, userId, updates) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    const allowedFields = ['total_asset', 'credit_debt', 'total_balance', 'asset_details', 'remark'];

    Object.keys(updates).forEach(key => {
      if (allowedFields.includes(key) && updates[key] !== undefined) {
        fields.push(`${key} = ?`);
        if (key === 'asset_details') {
          params.push(JSON.stringify(updates[key]));
        } else {
          params.push(updates[key]);
        }
      }
    });

    if (fields.length === 0) return this.findById(id, userId);

    fields.push('update_time = ?');
    params.push(now);
    params.push(id, userId);

    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      params
    );

    return this.findById(id, userId);
  }

  /**
   * 删除登记记录
   */
  static async delete(id, userId) {
    const now = String(Date.now());
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [now, id, userId]
    );
    return result.affectedRows > 0;
  }
}

module.exports = AssetRegister;
