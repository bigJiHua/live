const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

/**
 * 预算模型
 * 
 * budget_details JSON 结构示例：
 * {
 *   "category": "吃",
 *   "target": "餐饮支出",
 *   "notes": "每月餐饮控制在2000以内"
 * }
 */
class Budget {
  static tableName = 'budget';

  /**
   * 获取当前日期字符串
   */
  static getCurrentDate() {
    return new Date().toISOString().split('T')[0];
  }

  /**
   * 创建预算
   */
  static async create({ userId, title, route, budgetType, budgetAmount, budgetDetails, cycle, planDate }) {
    if (!title) throw new Error('预算标题不能为空');
    if (!budgetType) throw new Error('预算类型不能为空');
    if (!budgetAmount || parseFloat(budgetAmount) <= 0) throw new Error('预算金额必须大于0');
    if (!cycle) throw new Error('周期不能为空');
    if (!planDate) throw new Error('预计日期不能为空');

    const validCycles = ['月', '季', '年'];
    if (!validCycles.includes(cycle)) {
      throw new Error('周期只能是 月/季/年');
    }

    const validTypes = ['吃', '买', '行'];
    if (!validTypes.includes(budgetType)) {
      throw new Error('预算类型只能是 吃/买/行');
    }

    const id = idUtils.billId();
    const createTime = this.getCurrentDate();
    const detailsStr = typeof budgetDetails === 'object' 
      ? JSON.stringify(budgetDetails) 
      : '{}';

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, title, route, budget_type, budget_amount, used_amount, budget_details, 
       cycle, plan_date, is_over_budget, is_excute, create_time, update_time, is_deleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id, userId, title, route || null, budgetType, budgetAmount, 0, detailsStr,
      cycle, planDate, 0, 0, createTime, createTime
    ]);

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
    if (rows[0]) {
      rows[0].budget_details = this.parseDetails(rows[0].budget_details);
    }
    return rows[0] || null;
  }

  /**
   * 获取用户所有预算
   */
  static async findAll(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 0 ORDER BY create_time DESC`,
      [userId]
    );
    return rows.map(row => ({
      ...row,
      budget_details: this.parseDetails(row.budget_details)
    }));
  }

  /**
   * 解析 budget_details JSON
   */
  static parseDetails(details) {
    if (!details) return {};
    if (typeof details === 'object') return details;
    try {
      return JSON.parse(details);
    } catch {
      return {};
    }
  }

  /**
   * 更新预算
   */
  static async update(id, userId, updates) {
    const existing = await this.findById(id, userId);
    if (!existing) throw new Error('预算记录不存在');

    const fields = [];
    const params = [];

    Object.keys(updates).forEach(key => {
      if (updates[key] !== undefined) {
        fields.push(`${key} = ?`);
        if (key === 'budget_details' && typeof updates[key] === 'object') {
          params.push(JSON.stringify(updates[key]));
        } else {
          params.push(updates[key]);
        }
      }
    });

    if (fields.length === 0) return existing;

    fields.push('update_time = ?');
    params.push(this.getCurrentDate());
    params.push(id, userId);

    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ?`,
      params
    );

    return this.findById(id, userId);
  }

  /**
   * 永久删除预算
   */
  static async permanentDelete(id, userId) {
    const [result] = await db.execute(
      `DELETE FROM ${this.tableName} WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 获取预算统计
   */
  static async getStats(userId) {
    const [rows] = await db.execute(
      `SELECT budget_type, SUM(budget_amount) as total_budget, SUM(used_amount) as total_used
       FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 0
       GROUP BY budget_type`,
      [userId]
    );

    return rows.map(row => ({
      budget_type: row.budget_type,
      total_budget: parseFloat(row.total_budget) || 0,
      total_used: parseFloat(row.total_used) || 0,
      remaining: Math.round((parseFloat(row.total_budget) - parseFloat(row.total_used)) * 100) / 100,
      usage_rate: parseFloat(row.total_budget) > 0 
        ? Math.round(parseFloat(row.total_used) / parseFloat(row.total_budget) * 10000) / 100 
        : 0
    }));
  }
}

module.exports = Budget;
