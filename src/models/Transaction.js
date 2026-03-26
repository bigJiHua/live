const db = require('../config/db');

/**
 * 账务流水模型
 */
class Transaction {
  // 查找所有流水（支持分页和过滤）
  static async findAll(filters, page = 1, limit = 20) {
    const offset = (page - 1) * limit;

    let whereClause = 'WHERE 1=1';
    const params = [];

    if (filters.userId) {
      whereClause += ' AND user_id = ?';
      params.push(filters.userId);
    }

    if (filters.type) {
      whereClause += ' AND type = ?';
      params.push(filters.type);
    }

    if (filters.categoryId) {
      whereClause += ' AND category_id = ?';
      params.push(filters.categoryId);
    }

    if (filters.startDate) {
      whereClause += ' AND date >= ?';
      params.push(filters.startDate);
    }

    if (filters.endDate) {
      whereClause += ' AND date <= ?';
      params.push(filters.endDate);
    }

    // 获取总数
    const countQuery = `SELECT COUNT(*) as total FROM transactions ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    // 获取数据
    const query = `
      SELECT t.*, c.name as category_name, c.color as category_color, c.icon as category_icon
      FROM transactions t
      LEFT JOIN categories c ON t.category_id = c.id
      ${whereClause}
      ORDER BY t.date DESC, t.created_at DESC
      LIMIT ? OFFSET ?
    `;
    const [rows] = await db.execute(query, [...params, limit, offset]);

    return {
      rows,
      count: total,
    };
  }

  // 通过 ID 查找流水
  static async findById(id, userId) {
    const query = `
      SELECT t.*, c.name as category_name, c.color as category_color, c.icon as category_icon
      FROM transactions t
      LEFT JOIN categories c ON t.category_id = c.id
      WHERE t.id = ? AND t.user_id = ?
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  // 创建流水
  static async create({ userId, amount, type, categoryId, description, date }) {
    const query = `
      INSERT INTO transactions (user_id, amount, type, category_id, description, date, created_at)
      VALUES (?, ?, ?, ?, ?, ?, NOW())
    `;
    const [result] = await db.execute(query, [
      userId,
      amount,
      type,
      categoryId,
      description,
      date,
    ]);
    return this.findById(result.insertId, userId);
  }

  // 更新流水
  static async update(id, userId, { amount, type, categoryId, description, date }) {
    const query = `
      UPDATE transactions
      SET amount = ?, type = ?, category_id = ?, description = ?, date = ?
      WHERE id = ? AND user_id = ?
    `;
    await db.execute(query, [amount, type, categoryId, description, date, id, userId]);
    return this.findById(id, userId);
  }

  // 删除流水
  static async delete(id, userId) {
    const query = 'DELETE FROM transactions WHERE id = ? AND user_id = ?';
    const [result] = await db.execute(query, [id, userId]);
    return result.affectedRows > 0;
  }

  // 获取统计信息
  static async getStats(userId, { startDate, endDate }) {
    let whereClause = 'WHERE user_id = ?';
    const params = [userId];

    if (startDate) {
      whereClause += ' AND date >= ?';
      params.push(startDate);
    }

    if (endDate) {
      whereClause += ' AND date <= ?';
      params.push(endDate);
    }

    const query = `
      SELECT
        type,
        SUM(amount) as total,
        COUNT(*) as count
      FROM transactions
      ${whereClause}
      GROUP BY type
    `;
    const [rows] = await db.execute(query, params);

    const stats = {
      income: 0,
      expense: 0,
      balance: 0,
    };

    rows.forEach((row) => {
      if (row.type === 'income') {
        stats.income = parseFloat(row.total) || 0;
      } else if (row.type === 'expense') {
        stats.expense = parseFloat(row.total) || 0;
      }
    });

    stats.balance = stats.income - stats.expense;

    return stats;
  }
}

module.exports = Transaction;
