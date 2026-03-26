const db = require('../config/db');

/**
 * 分类模型
 */
class Category {
  // 查找所有分类
  static async findAll(filters) {
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

    const query = `
      SELECT * FROM categories
      ${whereClause}
      ORDER BY type ASC, name ASC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  // 通过 ID 查找分类
  static async findById(id, userId) {
    const query = 'SELECT * FROM categories WHERE id = ? AND user_id = ?';
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  // 创建分类
  static async create({ userId, name, type, color, icon }) {
    const query = `
      INSERT INTO categories (user_id, name, type, color, icon, created_at)
      VALUES (?, ?, ?, ?, ?, NOW())
    `;
    const [result] = await db.execute(query, [userId, name, type, color, icon]);
    return this.findById(result.insertId, userId);
  }

  // 更新分类
  static async update(id, userId, { name, type, color, icon }) {
    const query = `
      UPDATE categories
      SET name = ?, type = ?, color = ?, icon = ?
      WHERE id = ? AND user_id = ?
    `;
    await db.execute(query, [name, type, color, icon, id, userId]);
    return this.findById(id, userId);
  }

  // 删除分类
  static async delete(id, userId) {
    // 检查是否有关联的交易
    const [transactions] = await db.execute(
      'SELECT COUNT(*) as count FROM transactions WHERE category_id = ?',
      [id]
    );

    if (transactions[0].count > 0) {
      throw new Error('该分类下有交易记录，无法删除');
    }

    const query = 'DELETE FROM categories WHERE id = ? AND user_id = ?';
    const [result] = await db.execute(query, [id, userId]);
    return result.affectedRows > 0;
  }

  // 创建默认分类
  static async createDefaultCategories(userId) {
    const defaultIncomeCategories = [
      { name: '工资', type: 'income', color: '#52c41a', icon: '💰' },
      { name: '奖金', type: 'income', color: '#52c41a', icon: '🎁' },
      { name: '投资收益', type: 'income', color: '#52c41a', icon: '📈' },
      { name: '兼职', type: 'income', color: '#52c41a', icon: '💼' },
      { name: '其他收入', type: 'income', color: '#52c41a', icon: '💵' },
    ];

    const defaultExpenseCategories = [
      { name: '餐饮', type: 'expense', color: '#ff4d4f', icon: '🍔' },
      { name: '交通', type: 'expense', color: '#ff4d4f', icon: '🚗' },
      { name: '购物', type: 'expense', color: '#ff4d4f', icon: '🛒' },
      { name: '娱乐', type: 'expense', color: '#ff4d4f', icon: '🎮' },
      { name: '住房', type: 'expense', color: '#ff4d4f', icon: '🏠' },
      { name: '医疗', type: 'expense', color: '#ff4d4f', icon: '💊' },
      { name: '教育', type: 'expense', color: '#ff4d4f', icon: '📚' },
      { name: '其他支出', type: 'expense', color: '#ff4d4f', icon: '📦' },
    ];

    const allCategories = [...defaultIncomeCategories, ...defaultExpenseCategories];

    for (const category of allCategories) {
      await this.create({ userId, ...category });
    }

    return allCategories;
  }
}

module.exports = Category;
