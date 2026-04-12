const Todo = require('../model');

/**
 * 待办事项控制器
 */
class TodoController {
  /**
   * 创建待办
   */
  async create(req, res) {
    try {
      const userId = req.userId;
      const { content, event_type, happen_date, priority, need_remind, remind_days, remind_time, is_recurring, remark } = req.body.data;

      if (!content || content.trim() === '') {
        return res.status(400).json({ status: 400, message: '事件内容不能为空' });
      }

      if (happen_date && !/^\d{4}-\d{2}-\d{2}$/.test(happen_date)) {
        return res.status(400).json({ status: 400, message: '日期格式不正确，应为 YYYY-MM-DD' });
      }

      const result = await Todo.create({
        userId,
        content: content.trim(),
        eventType: event_type,
        happenDate: happen_date,
        priority,
        needRemind: need_remind,
        remindDays: remind_days,
        remindTime: remind_time,
        isRecurring: is_recurring,
        remark
      });

      return res.json({ status: 200, message: '创建成功', data: result });
    } catch (error) {
      console.error('创建待办错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '创建失败' });
    }
  }

  /**
   * 获取待办列表
   */
  async list(req, res) {
    try {
      const userId = req.userId;
      const { status, event_type, start_date, end_date, keyword } = req.query;

      const filters = {};
      if (status) filters.status = status;
      if (event_type) filters.eventType = event_type;
      if (start_date) filters.startDate = start_date;
      if (end_date) filters.endDate = end_date;
      if (keyword) filters.keyword = keyword;

      const rows = await Todo.findAll(userId, filters);

      return res.json({ status: 200, message: '查询成功', data: rows });
    } catch (error) {
      console.error('获取待办列表错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 按月获取日历数据
   */
  async calendarMonth(req, res) {
    try {
      const userId = req.userId;
      const { year, month } = req.query;

      if (!year || !month) {
        return res.status(400).json({ status: 400, message: 'year 和 month 参数必填' });
      }

      const data = await Todo.findByMonth(userId, parseInt(year), parseInt(month));

      // 转换为日历网格格式（补齐该月所有日期）
      const firstDay = new Date(parseInt(year), parseInt(month) - 1, 1);
      const lastDay = new Date(parseInt(year), parseInt(month), 0);
      const daysInMonth = lastDay.getDate();
      const startWeekday = firstDay.getDay();

      const calendar = {
        year: parseInt(year),
        month: parseInt(month),
        days: []
      };

      // 补齐月初空白
      for (let i = 0; i < startWeekday; i++) {
        calendar.days.push({ day: null, list: [] });
      }

      // 填充日期
      for (let day = 1; day <= daysInMonth; day++) {
        const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        const dayData = data.find(item => item.date === dateStr);
        calendar.days.push({
          day,
          date: dateStr,
          list: dayData ? dayData.list : [],
          count: dayData ? dayData.count : 0,
          hasCompleted: dayData ? dayData.hasCompleted : false,
          hasOverdue: dayData ? dayData.hasOverdue : false
        });
      }

      return res.json({ status: 200, message: '查询成功', data: calendar });
    } catch (error) {
      console.error('获取日历数据错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 获取详情
   */
  async detail(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const result = await Todo.findById(id, userId);
      if (!result) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      return res.json({ status: 200, message: '查询成功', data: result });
    } catch (error) {
      console.error('获取详情错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }

  /**
   * 更新待办
   */
  async update(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;
      const { content, event_type, happen_date, status, priority, need_remind, remind_days, remind_time, is_recurring, remark } = req.body.data;

      // 检查是否存在
      const existing = await Todo.findById(id, userId);
      if (!existing) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      if (content !== undefined && content.trim() === '') {
        return res.status(400).json({ status: 400, message: '事件内容不能为空' });
      }

      const updates = {};
      if (content !== undefined) updates.content = content.trim();
      if (event_type !== undefined) updates.event_type = event_type;
      if (happen_date !== undefined) updates.happen_date = happen_date;
      if (status !== undefined) updates.status = status;
      if (priority !== undefined) updates.priority = priority;
      if (need_remind !== undefined) updates.need_remind = need_remind ? 1 : 0;
      if (remind_days !== undefined) updates.remind_days = remind_days;
      if (remind_time !== undefined) updates.remind_time = remind_time;
      if (is_recurring !== undefined) updates.is_recurring = is_recurring ? 1 : 0;
      if (remark !== undefined) updates.remark = remark;

      const result = await Todo.update(id, userId, updates);

      return res.json({ status: 200, message: '更新成功', data: result });
    } catch (error) {
      console.error('更新待办错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '更新失败' });
    }
  }

  /**
   * 删除待办
   */
  async delete(req, res) {
    try {
      const userId = req.userId;
      const { id } = req.params;

      const result = await Todo.delete(id, userId);
      if (!result) {
        return res.status(404).json({ status: 404, message: '记录不存在' });
      }

      return res.json({ status: 200, message: '删除成功' });
    } catch (error) {
      console.error('删除待办错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '删除失败' });
    }
  }

  /**
   * 获取即将提醒的待办
   * @param scope: 'default'(3-10天) | 'all'(30天周期)
   */
  async reminders(req, res) {
    try {
      const userId = req.userId;
      const { scope } = req.query;
      const rows = await Todo.getUpcomingReminders(userId, scope);

      return res.json({ status: 200, message: '查询成功', data: rows });
    } catch (error) {
      console.error('获取提醒列表错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }
}

module.exports = new TodoController();
