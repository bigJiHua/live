const dayjs = require('dayjs');
const utc = require('dayjs/plugin/utc');
const timezone = require('dayjs/plugin/timezone');
dayjs.extend(utc);
dayjs.extend(timezone);
const Todo = require('../model');
const RecurringExpense = require('../../recurring/model');
const CardBill = require('../../card/model/bill');

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
      const { status, event_type, start_date, end_date, keyword, happen_date } = req.query;

      const filters = {};
      if (status) filters.status = status;
      if (event_type) filters.eventType = event_type;
      if (start_date) filters.startDate = start_date;
      if (end_date) filters.endDate = end_date;
      if (keyword) filters.keyword = keyword;
      if (happen_date) { filters.startDate = happen_date; filters.endDate = happen_date; }

      const rows = await Todo.findAll(userId, filters);

      // 注入该日的信用卡还款提醒（source=card_bill，V2：账单日+还款日双事件口径）
      let result = rows;
      if (happen_date) {
        const cardRows = await CardBill.getRepaymentRemindersV2(userId, { happenDate: happen_date });
        result = [...rows, ...cardRows];
      }

      return res.json({ status: 200, message: '查询成功', data: result });
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
      const recurringEvents = await RecurringExpense.getCalendarEvents(userId, parseInt(year), parseInt(month));
      const cardEvents = await CardBill.getRepaymentRemindersV2(userId, { year: parseInt(year), month: parseInt(month) });

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
        const fixedList = recurringEvents.filter(item => item.happen_date === dateStr);
        const cardList = cardEvents.filter(item => item.happen_date === dateStr);
        const list = [
          ...(dayData ? dayData.list : []),
          ...fixedList,
          ...cardList
        ];
        calendar.days.push({
          day,
          date: dateStr,
          list,
          count: list.length,
          hasCompleted: list.some(item => item.status === '已完成'),
          hasOverdue: list.some(item => item.status === '逾期')
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
      const { year, month, days, scope } = req.query;
      // ⚠️ 统一用「北京时间」取当前年月，避免 UTC 服务器下 now.getFullYear()/getMonth() 取错月导致数据丢失
      const bjNow = dayjs().tz('Asia/Shanghai');
      const now = new Date();
      const y = year ? parseInt(year, 10) : bjNow.get('year');
      const m = month ? parseInt(month, 10) : bjNow.get('month') + 1;
      const daysAround = days != null ? parseInt(days, 10) : undefined;
      // 横幅按月加载「未完成」待办：普通待办/信用卡/recurring 全部按当前查看月过滤（切到哪月显示哪月）。
      // 首页提醒传 days（今日±N天窗口）：普通待办按窗口过滤，信用卡/recurring 仍按月（窗口外的不展示）。
      let todos;
      if (daysAround != null) {
        // 首页提醒：优先今日±N天窗口；窗口内无「有效」数据则回退「当月1号~今日」的未完成待办
        // 注意：content==='1' 为飞机模式占位行，不计入有效数据，避免误判有数据而跳过回退
        const inWindow = await Todo.findOpenReminders(userId, { daysAround });
        const validInWindow = inWindow.filter(r => r.content && r.content !== "1");
        todos = validInWindow.length
          ? inWindow
          : await Todo.findOpenReminders(userId, { pastOnly: true, year: y, month: m });
      } else {
        todos = await Todo.findOpenReminders(userId, { year: y, month: m });
      }
      const [recurring, cardBills] = await Promise.all([
        RecurringExpense.getUpcomingRemindersCurrentMonth(userId, {
          month: y && m ? `${y}-${String(m).padStart(2, "0")}` : undefined,
        }),
        CardBill.getRepaymentRemindersV2(userId, { year: y, month: m })
      ]);
      const rows = [...todos, ...recurring, ...cardBills].sort((a, b) => {
        const left = a.happen_date || '';
        const right = b.happen_date || '';
        return left.localeCompare(right);
      });

      return res.json({ status: 200, message: '查询成功', data: rows });
    } catch (error) {
      console.error('获取提醒列表错误:', error);
      return res.status(500).json({ status: 500, message: error.message || '查询失败' });
    }
  }
}

module.exports = new TodoController();
