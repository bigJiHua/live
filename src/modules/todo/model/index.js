const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

/**
 * 待办事项模型 - 对应数据库 todo 表
 * 支持日程、生日、纪念日、倒数日
 */
class Todo {
  static tableName = 'todo';

  /**
   * 创建待办事项
   */
  static async create({ userId, content, eventType, happenDate, priority, needRemind, remindDays, remindTime, isRecurring, remark }) {
    const now = String(Date.now());

    // 业务规则：remark 不能超过 50 个字符
    if (remark && remark.length > 50) {
      throw new Error('备注不能超过50个字符');
    }

    // 业务规则：一天最多添加 10 个事件
    if (happenDate) {
      const [countResult] = await db.execute(
        `SELECT COUNT(*) as total FROM ${this.tableName} WHERE user_id = ? AND happen_date = ? AND is_deleted = 0`,
        [userId, happenDate]
      );
      if (countResult[0].total >= 10) {
        throw new Error('该日期已添加10个事件，达到每日上限');
      }
    }

    // 计算提醒时间：happen_date - remind_days 天
    let finalRemindTime = remindTime || null;
    if (needRemind && happenDate && remindDays && !remindTime) {
      const happenDateObj = new Date(happenDate);
      const remindTimestamp = happenDateObj.getTime() - parseInt(remindDays) * 24 * 60 * 60 * 1000;
      finalRemindTime = String(remindTimestamp);
    }

    const id = idUtils.billId();
    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, content, event_type, happen_date, status, priority, need_remind, remind_time, is_recurring, remark, create_time, update_time, is_deleted)
      VALUES (?, ?, ?, ?, ?, '待完成', ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      userId,
      content,
      eventType || null,
      happenDate || null,
      priority || 2,
      needRemind ? 1 : 0,
      finalRemindTime,
      isRecurring ? 1 : 0,
      remark || null,
      now,
      now
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
   * 获取用户的待办列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE user_id = ? AND is_deleted = 0';
    const params = [userId];

    if (filters.status) {
      whereClause += ' AND status = ?';
      params.push(filters.status);
    }

    if (filters.eventType) {
      whereClause += ' AND event_type = ?';
      params.push(filters.eventType);
    }

    if (filters.startDate) {
      whereClause += ' AND happen_date >= ?';
      params.push(filters.startDate);
    }

    if (filters.endDate) {
      whereClause += ' AND happen_date <= ?';
      params.push(filters.endDate);
    }

    if (filters.keyword) {
      whereClause += ' AND content LIKE ?';
      params.push(`%${filters.keyword}%`);
    }

    // 按日期排序
    const orderClause = 'ORDER BY happen_date ASC, priority ASC, create_time DESC';

    const query = `
      SELECT * FROM ${this.tableName} 
      ${whereClause} ${orderClause}
    `;
    const [rows] = await db.execute(query, params);

    return rows;
  }

  /**
   * 按月查询待办（返回日历格式数据）
   * 返回结构：{ date: 'YYYY-MM-DD', list: [...] }
   */
  static async findByMonth(userId, year, month) {
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const endDate = `${year}-${String(month).padStart(2, '0')}-31`;

    const query = `
      SELECT * FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
        AND happen_date >= ? AND happen_date <= ?
      ORDER BY happen_date ASC, priority ASC, create_time ASC
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    // 按日期分组
    const grouped = {};
    for (const row of rows) {
      const date = row.happen_date;
      if (!grouped[date]) {
        grouped[date] = [];
      }
      grouped[date].push(row);
    }

    // 转换为数组格式，便于日历展示
    const calendarData = Object.keys(grouped).sort().map(date => ({
      date,
      list: grouped[date],
      count: grouped[date].length,
      hasCompleted: grouped[date].some(item => item.status === '已完成'),
      hasOverdue: grouped[date].some(item => item.status === '逾期')
    }));

    return calendarData;
  }

  /**
   * 按年查询待办（返回年度统计数据）
   */
  static async findByYear(userId, year) {
    const startDate = `${year}-01-01`;
    const endDate = `${year}-12-31`;

    const query = `
      SELECT happen_date, status, COUNT(*) as count
      FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
        AND happen_date >= ? AND happen_date <= ?
      GROUP BY happen_date, status
      ORDER BY happen_date ASC
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    // 转换为 { date: 'YYYY-MM-DD', status: 'xxx', count: n } 结构
    return rows.map(row => ({
      date: row.happen_date,
      status: row.status,
      count: row.count
    }));
  }

  /**
   * 更新待办事项
   */
  static async update(id, userId, updates) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    // 如果有 remind_days，需要先获取 happen_date 计算 remind_time
    if (updates.remind_days !== undefined) {
      const existing = await this.findById(id, userId);
      if (!existing) {
        throw new Error('记录不存在');
      }
      const happenDate = updates.happen_date || existing.happen_date;
      const needRemind = updates.need_remind !== undefined ? updates.need_remind : existing.need_remind;
      
      if (needRemind && happenDate && updates.remind_days) {
        const happenDateObj = new Date(happenDate);
        const remindTimestamp = happenDateObj.getTime() - parseInt(updates.remind_days) * 24 * 60 * 60 * 1000;
        updates.remind_time = String(remindTimestamp);
      }
    }

    const allowedFields = ['content', 'event_type', 'happen_date', 'status', 'priority', 'need_remind', 'remind_time', 'is_recurring', 'remark'];

    Object.keys(updates).forEach(key => {
      if (allowedFields.includes(key) && updates[key] !== undefined) {
        fields.push(`${key} = ?`);
        if (key === 'remark' && updates[key] && updates[key].length > 50) {
          throw new Error('备注不能超过50个字符');
        }
        params.push(updates[key]);
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
   * 软删除待办事项
   */
  static async delete(id, userId) {
    const now = String(Date.now());
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [now, id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 批量更新状态
   */
  static async batchUpdateStatus(userId, ids, status) {
    const now = String(Date.now());
    const placeholders = ids.map(() => '?').join(',');
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET status = ?, update_time = ? WHERE id IN (${placeholders}) AND user_id = ? AND is_deleted = 0`,
      [status, now, ...ids, userId]
    );
    return result.affectedRows;
  }

  /**
   * 获取逾期待办（自动更新状态）
   */
  static async getOverdueItems(userId) {
    const today = new Date().toISOString().substring(0, 10);
    
    // 先更新逾期状态
    await db.execute(
      `UPDATE ${this.tableName} SET status = '逾期', update_time = ? WHERE user_id = ? AND is_deleted = 0 AND status = '待完成' AND happen_date < ?`,
      [String(Date.now()), userId, today]
    );

    // 查询逾期项目
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 0 AND status = '逾期' ORDER BY happen_date ASC`,
      [userId]
    );

    return rows;
  }

  /**
   * 获取即将提醒的事项
   * @param userId 用户ID
   * @param scope: 'default'(3-10天) | 'all'(30天周期)
   */
  static async getUpcomingReminders(userId, scope = 'default') {
    const today = new Date();

    let fromDays = 3;  // 默认从第3天开始
    let toDays = 10;    // 默认到第10天

    if (scope === 'all') {
      // 30天周期：今天到下月今天
      fromDays = 0;
      toDays = 30;
    }

    const fromDate = new Date(today.getTime() + fromDays * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
    const toDate = new Date(today.getTime() + toDays * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);

    const query = `
      SELECT * FROM ${this.tableName} 
      WHERE user_id = ? AND is_deleted = 0 
        AND need_remind = 1 
        AND happen_date >= ? AND happen_date <= ?
        AND status = '待完成'
      ORDER BY happen_date ASC, priority ASC
    `;
    const [rows] = await db.execute(query, [userId, fromDate, toDate]);

    return rows;
  }
}

module.exports = Todo;
