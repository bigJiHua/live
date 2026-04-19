const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");
const WorkJob = require('./job');

/**
 * 每日工资明细模型 - 对应数据库 work_salary 表
 * 
 * 正式工：月薪制，日薪 = base_salary / base_work_days
 * 兼职工：时薪制，日薪 = hourly_wage * work_hours
 * 
 * 注意：subsidy_meal/subsidy_traffic/subsidy_post 是每月固定补贴，不按日计算
 *       它们应该在发薪日（pay_day）单独发放，不计入每日工资
 */
class WorkSalary {
  static tableName = 'work_salary';

  /**
   * 计算正式工当日工资
   * 注：subsidy_meal/subsidy_traffic/subsidy_post 是每月固定补贴，不按日计算
   */
  static calculateFormalSalary(job, workDate, overrides = {}) {
    if (job.status !== 1) return null;
    if (job.join_date && workDate < job.join_date) return null;
    if (job.leave_date && workDate > job.leave_date) return null;

    const baseSalary = parseFloat(job.base_salary) || 0;
    const baseWorkDays = parseFloat(job.base_work_days) || 22;
    const daySalary = Math.round((baseSalary / baseWorkDays) * 100) / 100;

    // 每日补贴为空（meal/traffic/post 是每月补贴，不按日计算）
    const subsidy = 0;

    const social = parseFloat(job.social) || 0;
    const dailySocial = Math.round((social / baseWorkDays) * 100) / 100;

    const fund = parseFloat(job.fund) || 0;
    const dailyFund = Math.round((fund / baseWorkDays) * 100) / 100;

    const taxRate = parseFloat(job.tax_rate) || 0;
    const tax = Math.round((daySalary + subsidy) * taxRate / 100 * 100) / 100;

    const cut = overrides.cut !== undefined ? overrides.cut : 0;
    const income = Math.round((daySalary + subsidy - dailySocial - dailyFund - tax - cut) * 100) / 100;

    return {
      job_id: job.id,
      job_type: 'formal',
      company: job.company,
      job_color: job.job_color || '#07c160',
      work_date: workDate,
      work_date_dt: workDate,
      work_hours: 0,
      day_salary: daySalary,
      subsidy,
      social: dailySocial,
      fund: dailyFund,
      tax,
      cut,
      income,
      status: 1,
      remark: null,
      is_calculated: true
    };
  }

  /**
   * 计算兼职工当日工资
   * 注：subsidy_meal/subsidy_traffic/subsidy_post 是每月固定补贴，不按日计算
   */
  static calculateParttimeSalary(job, workDate, workHours, overrides = {}) {
    if (job.join_date && workDate < job.join_date) return null;
    if (job.leave_date && workDate > job.leave_date) return null;
    if (!workHours || parseFloat(workHours) <= 0) return null;

    const hourlyWage = parseFloat(job.hourly_wage) || 0;
    const daySalary = Math.round(hourlyWage * parseFloat(workHours) * 100) / 100;

    // 每日补贴为空（meal/traffic/post 是每月补贴，不按日计算）
    const subsidy = 0;

    const cut = overrides.cut !== undefined ? overrides.cut : 0;
    const income = Math.round((daySalary + subsidy - cut) * 100) / 100;

    return {
      job_id: job.id,
      job_type: 'parttime',
      company: job.company,
      job_color: job.job_color || '#07c160',
      work_date: workDate,
      work_date_dt: workDate,
      work_hours: parseFloat(workHours),
      day_salary: daySalary,
      subsidy,
      social: 0,
      fund: 0,
      tax: 0,
      cut,
      income,
      status: 1,
      remark: null,
      is_calculated: true
    };
  }

  /**
   * 获取某一天所有工资（正式 + 所有兼职）
   */
  static async getDaySalary(userId, workDate, workHoursMap = {}) {
    const result = {
      formal: null,
      parttimes: [],
      total_income: 0
    };

    // 正式工
    const formalJobs = await WorkJob.findActiveFormal(userId, workDate);
    if (formalJobs.length > 0) {
      const formal = formalJobs[0];
      const salary = this.calculateFormalSalary(formal, workDate);

      if (salary) {
        const [saved] = await db.execute(
          `SELECT * FROM ${this.tableName} WHERE job_id = ? AND work_date = ? AND is_deleted = 0`,
          [formal.id, workDate]
        );

        if (saved.length > 0) {
          result.formal = { ...saved[0], is_calculated: false };
          result.total_income += parseFloat(saved[0].income) || 0;
        } else {
          result.formal = salary;
          result.total_income += salary.income;
        }
      }
    }

    // 兼职列表
    const parttimeJobs = await WorkJob.findActiveParttimes(userId, workDate);
    for (const job of parttimeJobs) {
      const workHours = workHoursMap[job.id] || null;

      const [saved] = await db.execute(
        `SELECT * FROM ${this.tableName} WHERE job_id = ? AND work_date = ? AND is_deleted = 0`,
        [job.id, workDate]
      );

      let salary;
      if (saved.length > 0) {
        salary = { 
          ...saved[0], 
          hourly_wage: job.hourly_wage || 0,
          is_calculated: false 
        };
        result.total_income += parseFloat(saved[0].income) || 0;
      } else if (workHours) {
        salary = this.calculateParttimeSalary(job, workDate, workHours);
        if (salary) {
          salary.hourly_wage = job.hourly_wage || 0;
          result.total_income += salary.income;
        }
      } else {
        salary = {
          job_id: job.id,
          job_type: 'parttime',
          company: job.company,
          job_color: job.job_color || '#07c160',
          hourly_wage: job.hourly_wage || 0,
          work_date: workDate,
          work_date_dt: workDate,
          day_salary: 0,
          subsidy: 0,
          income: 0,
          work_hours: 0,
          need_hours: true,
          is_calculated: true
        };
      }

      if (salary) {
        result.parttimes.push(salary);
      }
    }

    result.total_income = Math.round(result.total_income * 100) / 100;
    return result;
  }

  /**
   * 保存某天工资
   */
  static async saveDaySalary({ userId, jobId, workDate, workHours, cut, subsidy, status, remark }) {
    const job = await WorkJob.findById(jobId, userId);
    if (!job) {
      throw new Error('工作记录不存在');
    }

    if (remark && remark.length > 50) {
      throw new Error('备注不能超过50个字符');
    }

    const overrides = {};
    if (cut !== undefined) overrides.cut = cut;
    // subsidy 覆盖：传入的值替换 job 表中的补贴汇总
    if (subsidy !== undefined) {
      overrides.totalSubsidy = parseFloat(subsidy) || 0;
    }

    let salary;
    if (job.job_type === 'formal') {
      salary = this.calculateFormalSalary(job, workDate, overrides);
      if (!salary) {
        throw new Error('该正式工在指定日期不计薪（未入职或已离职）');
      }
      // 如果传了 subsidy 覆盖
      if (subsidy !== undefined) {
        salary.subsidy = parseFloat(subsidy) || 0;
        const baseSalary = parseFloat(job.base_salary) || 0;
        const baseWorkDays = parseFloat(job.base_work_days) || 22;
        const daySalary = Math.round((baseSalary / baseWorkDays) * 100) / 100;
        const dailySocial = Math.round(((parseFloat(job.social) || 0) / baseWorkDays) * 100) / 100;
        const dailyFund = Math.round(((parseFloat(job.fund) || 0) / baseWorkDays) * 100) / 100;
        const taxRate = parseFloat(job.tax_rate) || 0;
        const tax = Math.round((daySalary + salary.subsidy) * taxRate / 100 * 100) / 100;
        const cut = overrides.cut !== undefined ? overrides.cut : 0;
        salary.income = Math.round((daySalary + salary.subsidy - dailySocial - dailyFund - tax - cut) * 100) / 100;
      }
    } else {
      if (!workHours || parseFloat(workHours) <= 0) {
        throw new Error('兼职工必须填写当日工作小时数');
      }
      salary = this.calculateParttimeSalary(job, workDate, workHours, overrides);
      if (!salary) {
        throw new Error('该兼职在指定日期不计薪（未开始或已结束）');
      }
      // 如果传了 subsidy 覆盖
      if (subsidy !== undefined) {
        salary.subsidy = parseFloat(subsidy) || 0;
        const hourlyWage = parseFloat(job.hourly_wage) || 0;
        salary.day_salary = Math.round(hourlyWage * parseFloat(workHours) * 100) / 100;
        const cut = overrides.cut !== undefined ? overrides.cut : 0;
        salary.income = Math.round((salary.day_salary + salary.subsidy - cut) * 100) / 100;
      }
    }

    if (status !== undefined) salary.status = status;
    if (remark !== undefined) salary.remark = remark;

    // 检查是否已存在
    const [existing] = await db.execute(
      `SELECT id FROM ${this.tableName} WHERE job_id = ? AND work_date = ? AND is_deleted = 0`,
      [jobId, workDate]
    );

    let id;
    if (existing.length > 0) {
      id = existing[0].id;
      const query = `
        UPDATE ${this.tableName} SET
          day_salary = ?, subsidy = ?,
          social = ?, fund = ?, tax = ?, cut = ?, income = ?, work_hours = ?, status = ?, remark = ?
        WHERE id = ?
      `;
      await db.execute(query, [
        salary.day_salary, salary.subsidy,
        salary.social, salary.fund, salary.tax, salary.cut, salary.income, salary.work_hours, salary.status, salary.remark, id
      ]);
    } else {
      id = idUtils.billId();
      const query = `
        INSERT INTO ${this.tableName} 
        (id, user_id, job_id, job_type, work_date, work_date_dt, work_hours, day_salary, subsidy, 
         social, fund, tax, cut, income, status, remark, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
      `;
      await db.execute(query, [
        id, userId, jobId, job.job_type, workDate, workDate, salary.work_hours,
        salary.day_salary, salary.subsidy,
        salary.social, salary.fund, salary.tax, salary.cut, salary.income, salary.status, salary.remark
      ]);
    }

    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE id = ?`,
      [id]
    );
    return rows[0];
  }

  /**
   * 补充本月缺失的正式工日薪数据（静默执行，不影响原有逻辑）
   * 范围：当月1日 ~ 执行插入的日期
   */
  static async fillMissingFormalSalary(userId, workDate) {
    // 解析年月
    const [year, month] = workDate.split('-').slice(0, 2).map(Number);
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;

    // 获取本月所有正式工
    const formalJobs = await WorkJob.findActiveFormal(userId, workDate);
    if (formalJobs.length === 0) return;
    const formalJob = formalJobs[0];

    // 获取本月已有的正式工日薪记录（只查到插入的日期）
    const [existing] = await db.execute(
      `SELECT work_date FROM ${this.tableName} 
       WHERE user_id = ? AND job_id = ? AND work_date >= ? AND work_date <= ? AND job_type = 'formal'`,
      [userId, formalJob.id, startDate, workDate]
    );

    const existingDates = new Set(existing.map(r => r.work_date));

    // 从1号到插入日期，逐日检查并补充
    const insertDay = parseInt(workDate.split('-')[2], 10);
    for (let day = 1; day <= insertDay; day++) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      if (existingDates.has(dateStr)) continue;
      
      // 检查入职/离职范围
      if (formalJob.join_date && dateStr < formalJob.join_date) continue;
      if (formalJob.leave_date && dateStr > formalJob.leave_date) continue;
      
      const salary = this.calculateFormalSalary(formalJob, dateStr);
      if (salary) {
        const id = idUtils.billId();
        await db.execute(
          `INSERT INTO ${this.tableName} 
           (id, user_id, job_id, job_type, work_date, work_date_dt, work_hours, day_salary, subsidy, 
            social, fund, tax, cut, income, status, remark, is_deleted)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
          [id, userId, formalJob.id, salary.job_type, dateStr, dateStr, salary.work_hours,
           salary.day_salary, salary.subsidy, salary.social, salary.fund, salary.tax, salary.cut,
           salary.income, salary.status, salary.remark]
        );
      }
    }
  }

  /**
   * 按月统计工资
   */
  static async getMonthSalary(userId, year, month) {
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const endDate = `${year}-${String(month).padStart(2, '0')}-31`;

    const query = `
      SELECT ws.*, wj.company, wj.job_type, wj.job_color
      FROM ${this.tableName} ws
      LEFT JOIN ${WorkJob.tableName} wj ON ws.job_id = wj.id
      WHERE ws.user_id = ? AND ws.work_date >= ? AND ws.work_date <= ? AND ws.is_deleted = 0
      ORDER BY ws.work_date_dt ASC, ws.job_type ASC
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    const dailyStats = {};
    let formalTotal = 0;
    let parttimeTotal = 0;

    for (const row of rows) {
      const date = row.work_date;
      if (!dailyStats[date]) {
        dailyStats[date] = { formal: null, parttimes: [], day_total: 0 };
      }

      if (row.job_type === 'formal') {
        dailyStats[date].formal = row;
        formalTotal += parseFloat(row.income) || 0;
      } else {
        dailyStats[date].parttimes.push(row);
        parttimeTotal += parseFloat(row.income) || 0;
      }
      dailyStats[date].day_total += parseFloat(row.income) || 0;
    }

    const dailyList = Object.keys(dailyStats).sort().map(date => ({
      date,
      formal: dailyStats[date].formal,
      parttimes: dailyStats[date].parttimes,
      day_total: Math.round(dailyStats[date].day_total * 100) / 100
    }));

    return {
      year,
      month,
      formal_total: Math.round(formalTotal * 100) / 100,
      parttime_total: Math.round(parttimeTotal * 100) / 100,
      total_income: Math.round((formalTotal + parttimeTotal) * 100) / 100,
      daily_list: dailyList
    };
  }

  /**
   * 删除指定日期的所有工资记录（硬删除）
   */
  static async delete(userId, workDate) {
    const [result] = await db.execute(
      `DELETE FROM ${this.tableName} WHERE work_date = ? AND user_id = ?`,
      [workDate, userId]
    );
    return result.affectedRows;
  }

  /**
   * 批量更新工资状态
   */
  static async batchUpdateStatus(userId, ids, status) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET status = ? WHERE id IN (${ids.map(() => '?').join(',')}) AND user_id = ? AND is_deleted = 0`,
      [status, ...ids, userId]
    );
    return result.affectedRows;
  }
}

module.exports = WorkSalary;
