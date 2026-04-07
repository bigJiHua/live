const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 分类模型 - 对应数据库 bus_category 表
 */
class Category {
  static tableName = 'bus_category';

  /**
   * 获取某类型分类的最大排序值
   */
  static async getMaxSort(userId, type) {
    const query = `
      SELECT MAX(sort) as maxSort 
      FROM ${this.tableName} 
      WHERE user_id = ? AND type = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [userId, type]);
    return rows[0]?.maxSort || 0;
  }

  /**
   * 获取分类列表（支持按类型筛选）
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE user_id = ? AND is_deleted = 0';
    const params = [userId];

    if (filters.type) {
      whereClause += ' AND type = ?';
      params.push(filters.type);
    }

    const query = `
      SELECT * FROM ${this.tableName}
      ${whereClause}
      ORDER BY sort ASC, name ASC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 根据ID查找分类
   */
  static async findById(id, userId) {
    const query = `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建分类（自动分配sort）
   */
  static async create({ userId, name, type, iconUrl, remark }) {
    const id = idUtils.billId();

    // 自动获取该类型分类的最大 sort 值 +1
    const maxSort = await this.getMaxSort(userId, type);
    const sort = maxSort + 1;

    // 构建字段和值，undefined 时让数据库使用默认值
    const fields = ['id', 'user_id', 'name', 'type', 'sort', 'is_deleted'];
    const values = [id, userId, name, type, sort, 0];

    // icon_url: undefined 时不传，让数据库使用默认值
    if (iconUrl !== undefined) {
      fields.push('icon_url');
      values.push(iconUrl);
    }

    // remark: undefined 时不传，让数据库使用默认值
    if (remark !== undefined) {
      fields.push('remark');
      values.push(remark);
    }

    const placeholders = fields.map(() => '?').join(', ');
    const query = `INSERT INTO ${this.tableName} (${fields.join(', ')}) VALUES (${placeholders})`;

    await db.execute(query, values);

    return this.findById(id, userId);
  }

  /**
   * 更新分类
   */
  static async update(id, userId, { name, iconUrl, remark }) {
    const fields = [];
    const params = [];

    if (name !== undefined) {
      fields.push('name = ?');
      params.push(name);
    }
    if (iconUrl !== undefined) {
      fields.push('icon_url = ?');
      params.push(iconUrl);
    }
    if (remark !== undefined) {
      fields.push('remark = ?');
      params.push(remark);
    }

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
   * 删除分类（软删除）
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

  /**
   * 批量创建默认分类
   */
  static async createDefaults(userId) {
    const defaults = [
      // 收入分类
      { name: '工资', type: 'income', remark: '每月固定收入' },
      { name: '奖金', type: 'income', remark: '额外奖励' },
      { name: '投资收益', type: 'income', remark: '理财分红等' },
      { name: '兼职', type: 'income', remark: '副业收入' },
      { name: '退款', type: 'income', remark: '退款返还' },
      { name: '其他收入', type: 'income' },

      // 支出分类
      { name: '餐饮', type: 'expense', remark: '早中晚餐' },
      { name: '交通', type: 'expense', remark: '出行费用' },
      { name: '购物', type: 'expense', remark: '日用品/服饰' },
      { name: '娱乐', type: 'expense', remark: '游戏/电影/旅游' },
      { name: '住房', type: 'expense', remark: '房租/房贷/物业' },
      { name: '医疗', type: 'expense', remark: '药品/看病' },
      { name: '教育', type: 'expense', remark: '培训/书籍' },
      { name: '通讯', type: 'expense', remark: '话费/网费' },
      { name: '水电煤', type: 'expense', remark: '生活必需' },
      { name: '其他支出', type: 'expense' },
    ];

    for (const item of defaults) {
      await this.create({ userId, ...item });
    }
  }
}

module.exports = Category;
