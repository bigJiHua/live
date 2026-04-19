const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

/**
 * 工作信息模型 - 对应数据库 work_job 表
 * 1个用户 = 1个正式工 + N个兼职工
 * 
 * 表结构：
 * - 只有 create_at（TIMESTAMP，自动设置）
 * - 没有 update_time 字段
 */
class WorkJob {
  static tableName = 'work_job';

  /**
   * 创建工作信息
   */
  static async create({ userId, jobType, company, jobColor, status, joinDate, leaveDate, payDay,
    baseSalary, baseWorkDays, hourlyWage,
    subsidyMeal, subsidyTraffic, subsidyPost, social, fund, taxRate, remark }) {
    
    // 正式工限制：1个用户只能有1个正式工作
    if (jobType === 'formal') {
      const [existing] = await db.execute(
        `SELECT id FROM ${this.tableName} WHERE user_id = ? AND job_type = 'formal' AND is_deleted = 0`,
        [userId]
      );
      if (existing.length > 0) {
        throw new Error('正式工只能有一条，如需更换请先删除旧的');
      }
    }

    // 兼职工必填时薪
    if (jobType === 'parttime') {
      if (!hourlyWage || parseFloat(hourlyWage) <= 0) {
        throw new Error('兼职工必须填写时薪');
      }
    }

    // 正式工必填月薪
    if (jobType === 'formal') {
      if (!baseSalary || parseFloat(baseSalary) <= 0) {
        throw new Error('正式工必须填写月基本工资');
      }
      if (!baseWorkDays || parseInt(baseWorkDays) <= 0) {
        throw new Error('正式工必须填写月应出勤天数');
      }
    }

    // 校验日期：离职日期 > 入职日期
    if (joinDate && leaveDate && leaveDate <= joinDate) {
      throw new Error('离职日期必须大于入职日期');
    }

    // remark 限制50字符
    if (remark && remark.length > 50) {
      throw new Error('备注不能超过50个字符');
    }

    const id = idUtils.billId();
    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, job_type, company, job_color, status, join_date, leave_date, pay_day,
       base_salary, base_work_days, hourly_wage,
       subsidy_meal, subsidy_traffic, subsidy_post, 
       social, fund, tax_rate, remark, is_deleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id, userId, jobType, company || '', jobColor || '#07c160',
      status !== undefined ? (status ? 1 : 0) : 1,
      joinDate || null, leaveDate || null, payDay || 15,
      baseSalary || 0, baseWorkDays || 22, hourlyWage || 0,
      subsidyMeal || 0, subsidyTraffic || 0, subsidyPost || 0,
      social || 0, fund || 0, taxRate || 0, remark || null
    ]);

    return this.findById(id, userId);
  }

  /**
   * 根据 ID 查询
   */
  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  /**
   * 获取用户所有工作列表
   */
  static async findAll(userId) {
    const query = `
      SELECT * FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
      ORDER BY job_type ASC
    `;
    const [rows] = await db.execute(query, [userId]);
    return rows;
  }

  /**
   * 获取用户所有在职正式工
   */
  static async findActiveFormal(userId, workDate) {
    const query = `
      SELECT * FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
        AND job_type = 'formal' 
        AND status = 1
        AND join_date <= ?
        AND (leave_date IS NULL OR leave_date >= ?)
    `;
    const [rows] = await db.execute(query, [userId, workDate, workDate]);
    return rows;
  }

  /**
   * 获取用户在某天有效的兼职列表
   */
  static async findActiveParttimes(userId, workDate) {
    const query = `
      SELECT * FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
        AND job_type = 'parttime'
        AND join_date <= ?
        AND (leave_date IS NULL OR leave_date >= ?)
      ORDER BY company ASC
    `;
    const [rows] = await db.execute(query, [userId, workDate, workDate]);
    return rows;
  }

  /**
   * 更新工作信息
   */
  static async update(id, userId, updates) {
    const fields = [];
    const params = [];

    const allowedFields = ['company', 'job_color', 'status', 'join_date', 'leave_date', 'pay_day',
      'base_salary', 'base_work_days', 'hourly_wage',
      'subsidy_meal', 'subsidy_traffic', 'subsidy_post', 
      'social', 'fund', 'tax_rate', 'remark'];

    Object.keys(updates).forEach(key => {
      if (allowedFields.includes(key) && updates[key] !== undefined) {
        // 金额字段校验不能为负
        const amountFields = ['base_salary', 'base_work_days', 'hourly_wage', 
          'subsidy_meal', 'subsidy_traffic', 'subsidy_post', 'social', 'fund', 'pay_day'];
        if (amountFields.includes(key) && updates[key] < 0) {
          throw new Error(`${key} 不能为负数`);
        }
        fields.push(`${key} = ?`);
        params.push(updates[key]);
      }
    });

    if (fields.length === 0) return this.findById(id, userId);

    // 校验日期
    const existing = await this.findById(id, userId);
    if (!existing) {
      throw new Error('工作记录不存在');
    }

    if (updates.leave_date !== undefined || updates.join_date !== undefined) {
      const joinDate = updates.join_date !== undefined ? updates.join_date : existing.join_date;
      const leaveDate = updates.leave_date !== undefined ? updates.leave_date : existing.leave_date;
      if (joinDate && leaveDate && leaveDate <= joinDate) {
        throw new Error('离职日期必须大于入职日期');
      }
    }

    if (updates.remark !== undefined && updates.remark && updates.remark.length > 50) {
      throw new Error('备注不能超过50个字符');
    }

    params.push(id, userId);

    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      params
    );

    // 自动化：如果是正式工且更新了离职日期，自动删除离职日之后到月底的薪酬记录
    const newLeaveDate = updates.leave_date !== undefined ? updates.leave_date : existing.leave_date;
    if (existing.job_type === 'formal' && updates.leave_date) {
      const today = new Date().toISOString().split('T')[0];
      // 如果新离职日期 <= 今天，清理未来薪酬
      if (newLeaveDate <= today) {
        const [year, month] = newLeaveDate.split('-').slice(0, 2);
        const endOfMonth = `${year}-${month}-${new Date(year, month, 0).getDate()}`;
        await db.execute(
          `DELETE FROM work_salary WHERE user_id = ? AND job_id = ? AND work_date > ? AND work_date <= ? AND job_type = 'formal'`,
          [userId, id, newLeaveDate, endOfMonth]
        );
      }
    }

    return this.findById(id, userId);
  }

  /**
   * 软删除工作
   */
  static async delete(id, userId) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1 WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }
}

module.exports = WorkJob;
