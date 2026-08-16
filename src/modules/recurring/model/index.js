const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");
const dayjs = require("dayjs");
const utc = require("dayjs/plugin/utc");
const timezone = require("dayjs/plugin/timezone");
dayjs.extend(utc);
dayjs.extend(timezone);

class RecurringExpense {
  static tableName = "bus_recurring";

  static today() {
    // 北京时间"今天"，避免 UTC 服务器下 toISOString 取错日
    return dayjs().tz("Asia/Shanghai").format("YYYY-MM-DD");
  }

  static now() {
    return String(Date.now());
  }

  static parseMonthRecords(raw) {
    if (!raw) return {};
    if (typeof raw === "object") return raw;
    try {
      return JSON.parse(raw);
    } catch {
      return {};
    }
  }

  static normalizeMonth(month) {
    if (/^\d{4}-\d{2}$/.test(month || "")) return month;
    throw new Error("月份格式应为 YYYY-MM");
  }

  static daysInMonth(month) {
    const [year, monthNo] = month.split("-").map(Number);
    return new Date(year, monthNo, 0).getDate();
  }

  static getMonthDate(row, month) {
    const day = Math.max(1, Number(row.day_of_cycle || 1));
    const safeDay = Math.min(day, this.daysInMonth(month));
    return `${month}-${String(safeDay).padStart(2, "0")}`;
  }

  static getMonthRecord(row, month) {
    const records = this.parseMonthRecords(row.month_records);
    const record = records[month] || {};
    return {
      month,
      status: record.status || "pending",
      amount: record.amount !== undefined ? record.amount : row.amount,
      remind_time: record.remind_time || null,
      done_time: record.done_time || null,
      remark: record.remark || "",
    };
  }

  static attachMonthInfo(row, month) {
    const record = this.getMonthRecord(row, month);
    const happenDate = this.getMonthDate(row, month);
    return {
      ...row,
      amount: Number(row.amount || 0),
      month,
      happen_date: happenDate,
      month_status: record.status,
      month_amount: Number(record.amount || row.amount || 0),
      month_record: record,
      month_records: this.parseMonthRecords(row.month_records),
      is_done: record.status === "done",
      is_skipped: record.status === "skipped",
      is_due: happenDate <= this.today(),
    };
  }

  static toCalendarEvent(row, month) {
    // 分期：仅 repeat_count 范围内生成事件
    if (row.repeat_count) {
      const records = this.parseMonthRecords(row.month_records);
      const sortedMonths = Object.keys(records).sort();
      const idx = sortedMonths.indexOf(month);
      if (idx === -1 || idx >= row.repeat_count) return null;
    }

    const item = this.attachMonthInfo(row, month);
    const statusMap = { done: "已完成", skipped: "已取消", pending: "待完成" };

    // 分期：追加第N/M期
    let content = item.name;
    if (row.repeat_count) {
      const records = this.parseMonthRecords(row.month_records);
      const sortedMonths = Object.keys(records).sort();
      const periodIdx = sortedMonths.indexOf(month);
      if (periodIdx >= 0) {
        content = `${item.name} 第${periodIdx + 1}/${row.repeat_count}期`;
      }
    }

    return {
      id: `recurring_${item.id}_${month}`,
      source: "recurring",
      recurring_id: item.id,
      content,
      event_type: "fixed_expense",
      happen_date: item.happen_date,
      status: statusMap[item.month_status] || "待完成",
      priority: 2,
      need_remind: 1,
      is_recurring: item.cycle === "year" ? 1 : 0,
      cycle: item.cycle || "month",
      amount: item.month_amount,
      category_id: item.category_id,
      category_name: item.category_name || "",
      remark: item.remark || "",
      month_status: item.month_status,
      is_fixed_expense: true,
    };
  }

  static async findAll(userId, { month, includeInactive = false, installmentOnly = false, excludeInstallment = false } = {}) {
    const params = [userId];
    let where = "WHERE r.user_id = ? AND r.is_deleted = 0";
    if (!includeInactive) {
      where += " AND r.is_active = 1";
    }
    if (installmentOnly) {
      where += " AND (c.name = '分期' OR r.account_id LIKE '%\"type\":\"installment\"%')";
    } else if (excludeInstallment) {
      where += " AND (c.name IS NULL OR (c.name != '分期' AND (r.account_id NOT LIKE '%\"type\":\"installment\"%' OR r.account_id IS NULL)))";
    }

    const [rows] = await db.execute(
      `SELECT r.*, c.name AS category_name, cb.alias AS account_name, cb.last4_no AS account_last4, cb.card_type
       FROM ${this.tableName} r
       LEFT JOIN bus_category c ON r.category_id = c.id AND c.is_deleted = 0
       LEFT JOIN card_base cb ON r.account_id = cb.id AND cb.is_deleted = 0
       ${where}
       ORDER BY r.is_active DESC, r.day_of_cycle ASC, r.create_time DESC`,
      params
    );

    // 按年周期过滤：只返回匹配月份的项
    let filtered = rows;
    if (month) {
      const safeMonth = this.normalizeMonth(month);
      const [, monthNum] = safeMonth.split("-").map(Number);
      filtered = rows.filter(row => {
        // 截止日期（end_date）判断：
        // 1) 真实已过期（截止日早于今天）→ 隐藏（覆盖所有周期类型）
        // 2) 被查看的月份已超过截止日期所在月份 → 隐藏
        //    （例如 end_date=2026-10-31，查看 2026-11 时不应再出现该事件）
        if (row.end_date) {
          if (row.end_date < this.today()) return false;
          const endMonth = row.end_date.substring(0, 7);
          if (endMonth < safeMonth) return false;
        }
        if (row.cycle === "year" && row.month_of_cycle) {
          return Number(row.month_of_cycle) === monthNum;
        }
        return true;
      });
    }

    if (!month) return filtered.map(row => ({ ...row, month_records: this.parseMonthRecords(row.month_records) }));
    return filtered.map(row => this.attachMonthInfo(row, month));
  }

  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT r.*, c.name AS category_name, cb.alias AS account_name, cb.last4_no AS account_last4, cb.card_type
       FROM ${this.tableName} r
       LEFT JOIN bus_category c ON r.category_id = c.id AND c.is_deleted = 0
       LEFT JOIN card_base cb ON r.account_id = cb.id AND cb.is_deleted = 0
       WHERE r.id = ? AND r.user_id = ? AND r.is_deleted = 0`,
      [id, userId]
    );
    const row = rows[0];
    if (!row) return null;
    return { ...row, month_records: this.parseMonthRecords(row.month_records) };
  }

  static async create({ userId, name, amount, categoryId, accountId, cycle, dayOfCycle, monthOfCycle, remindDays, remark, isActive, endDate, repeatCount, notifyChannel, monthRecords }) {
    if (!name || !String(name).trim()) throw new Error("固定支出名称不能为空");
    const numAmount = Number(amount);
    if (isNaN(numAmount) || numAmount < 0) throw new Error("金额不能为负数");

    const finalCycle = cycle || "month";
    if (!["month", "year"].includes(finalCycle)) {
      throw new Error("周期仅支持 month / year");
    }
    if (finalCycle === "year" && (!monthOfCycle || Number(monthOfCycle) < 1 || Number(monthOfCycle) > 12)) {
      throw new Error("年度周期需指定 month_of_cycle (1-12)");
    }

    const day = Math.max(1, Math.min(31, Number(dayOfCycle || 1)));
    const monthVal = monthOfCycle ? Math.max(1, Math.min(12, Number(monthOfCycle))) : null;
    const id = idUtils.billId();
    const now = this.now();
    const nextDate = finalCycle === "year"
      ? this.getNextDateYear(day, monthVal)
      : this.getNextDate(day);

    const initialRecords = (monthRecords && typeof monthRecords === 'object') ? JSON.stringify(monthRecords) : "{}";

    await db.execute(
      `INSERT INTO ${this.tableName}
       (id, user_id, name, amount, category_id, account_id, cycle, day_of_cycle, month_of_cycle,
        next_date, remind_days, remark, month_records, end_date, repeat_count, notify_channel,
        is_active, create_time, update_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
      [
        id,
        userId,
        String(name).trim(),
        numAmount,
        categoryId || null,
        accountId || null,
        finalCycle,
        day,
        monthVal,
        nextDate,
        Number(remindDays || 0),
        remark || null,
        initialRecords,
        endDate || null,
        repeatCount ? Number(repeatCount) : null,
        notifyChannel || "app",
        isActive === false || isActive === 0 ? 0 : 1,
        now,
        now,
      ]
    );

    return this.findById(id, userId);
  }

  static getNextDate(dayOfCycle) {
    const now = new Date();
    const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    let date = this.getMonthDate({ day_of_cycle: dayOfCycle }, currentMonth);
    if (date < this.today()) {
      const next = new Date(now.getFullYear(), now.getMonth() + 1, 1);
      const nextMonth = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
      date = this.getMonthDate({ day_of_cycle: dayOfCycle }, nextMonth);
    }
    return date;
  }

  static getNextDateYear(dayOfCycle, monthOfCycle) {
    const now = new Date();
    const year = now.getFullYear();
    const day = Math.max(1, Math.min(31, Number(dayOfCycle || 1)));
    const month = Number(monthOfCycle || 1);
    const thisYearDate = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    if (thisYearDate >= this.today()) {
      return thisYearDate;
    }
    return `${year + 1}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  static async update(id, userId, updates) {
    const existing = await this.findById(id, userId);
    if (!existing) throw new Error("固定支出不存在");

    const map = {
      name: "name",
      amount: "amount",
      categoryId: "category_id",
      accountId: "account_id",
      cycle: "cycle",
      dayOfCycle: "day_of_cycle",
      monthOfCycle: "month_of_cycle",
      remindDays: "remind_days",
      remark: "remark",
      isActive: "is_active",
      endDate: "end_date",
      repeatCount: "repeat_count",
      notifyChannel: "notify_channel",
    };

    if (updates.name !== undefined && !String(updates.name).trim()) {
      throw new Error("固定支出名称不能为空");
    }
    if (updates.amount !== undefined && Number(updates.amount) < 0) {
      throw new Error("金额不能为负数");
    }

    // 合并 cycle：优先用传入值，否则用现有值
    const resolvedCycle = updates.cycle !== undefined ? updates.cycle : existing.cycle;
    if (!["month", "year"].includes(resolvedCycle)) {
      throw new Error("周期仅支持 month / year");
    }

    const resolvedMonthOfCycle = updates.monthOfCycle !== undefined ? updates.monthOfCycle : existing.month_of_cycle;
    if (resolvedCycle === "year" && (!resolvedMonthOfCycle || Number(resolvedMonthOfCycle) < 1 || Number(resolvedMonthOfCycle) > 12)) {
      throw new Error("年度周期需指定 month_of_cycle (1-12)");
    }

    const fields = [];
    const params = [];
    Object.entries(map).forEach(([key, column]) => {
      if (updates[key] !== undefined) {
        fields.push(`${column} = ?`);
        if (key === "dayOfCycle") {
          params.push(Math.max(1, Math.min(31, Number(updates[key] || 1))));
        } else if (key === "monthOfCycle") {
          params.push(Math.max(1, Math.min(12, Number(updates[key] || 1))));
        } else if (key === "remindDays") {
          params.push(Number(updates[key] || 0));
        } else if (key === "repeatCount") {
          params.push(updates[key] ? Number(updates[key]) : null);
        } else if (key === "isActive") {
          params.push(updates[key] ? 1 : 0);
        } else if (key === "categoryId" || key === "accountId" || key === "endDate" || key === "notifyChannel") {
          params.push(updates[key] || null);
        } else {
          params.push(updates[key]);
        }
      }
    });

    // 当 day_of_cycle 或 month_of_cycle 或 cycle 变更时，重算 next_date
    if (updates.dayOfCycle !== undefined || updates.monthOfCycle !== undefined || updates.cycle !== undefined) {
      const dayVal = updates.dayOfCycle !== undefined ? Math.max(1, Math.min(31, Number(updates.dayOfCycle || 1))) : Number(existing.day_of_cycle || 1);
      const monthVal = updates.monthOfCycle !== undefined
        ? Math.max(1, Math.min(12, Number(updates.monthOfCycle || 1)))
        : Number(existing.month_of_cycle || 0);
      if (resolvedCycle === "year") {
        fields.push("next_date = ?");
        params.push(this.getNextDateYear(dayVal, monthVal || undefined));
      } else {
        fields.push("next_date = ?");
        params.push(this.getNextDate(dayVal));
      }
    }

    if (fields.length === 0) return existing;
    fields.push("update_time = ?");
    params.push(this.now(), id, userId);

    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(", ")} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      params
    );

    return this.findById(id, userId);
  }

  static async delete(id, userId) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [this.now(), id, userId]
    );
    return result.affectedRows > 0;
  }

  static async updateMonthStatus(id, userId, { month, status, amount, remark }) {
    const safeMonth = this.normalizeMonth(month);
    if (!["pending", "done", "skipped"].includes(status)) {
      throw new Error("状态只能是 pending/done/skipped");
    }

    const existing = await this.findById(id, userId);
    if (!existing) throw new Error("固定支出不存在");

    // 分期限定：只能操作 month_records 中已有月份，防止超范围插入
    if (existing.repeat_count) {
      const existingRecords = this.parseMonthRecords(existing.month_records);
      const validMonths = Object.keys(existingRecords);
      if (!validMonths.includes(safeMonth)) {
        throw new Error(`分期仅限已登记月份操作，${safeMonth} 不在范围内`);
      }
    }

    const records = this.parseMonthRecords(existing.month_records);
    const current = records[safeMonth] || {};
    records[safeMonth] = {
      ...current,
      status,
      amount: amount !== undefined ? amount : (current.amount !== undefined ? current.amount : existing.amount),
      remark: remark !== undefined ? remark : (current.remark || ""),
      remind_time: current.remind_time || null,
      done_time: status === "done" ? this.now() : null,
    };

    // 检查是否已满 repeat_count（分期完成）
    let shouldDeactivate = false;
    if (status === "done" && existing.repeat_count) {
      const doneCount = Object.values(records).filter(r => r.status === "done").length;
      if (doneCount >= Number(existing.repeat_count)) {
        shouldDeactivate = true;
      }
    }

    await db.execute(
      `UPDATE ${this.tableName} SET month_records = ?, is_active = ?, update_time = ? WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [JSON.stringify(records), shouldDeactivate ? 0 : (existing.is_active || 1), this.now(), id, userId]
    );

    const row = await this.findById(id, userId);
    const result = this.attachMonthInfo(row, safeMonth);
    result.auto_deactivated = shouldDeactivate;
    return result;
  }

  static async getMonthSummary(userId, month) {
    const rows = await this.findAll(userId, { month, includeInactive: false });
    const active = rows.filter(item => !item.is_skipped);
    const done = active.filter(item => item.is_done);
    const totalAmount = active.reduce((sum, item) => sum + Number(item.month_amount || 0), 0);
    const doneAmount = done.reduce((sum, item) => sum + Number(item.month_amount || 0), 0);
    return {
      month,
      total: active.length,
      done: done.length,
      pending: active.length - done.length,
      totalAmount,
      doneAmount,
      pendingAmount: totalAmount - doneAmount,
      categoryStats: this.getCategoryStats(active),
    };
  }

  static getCategoryStats(rows) {
    const map = new Map();
    rows.forEach(item => {
      const amountVal = Number(item.month_amount || 0);
      const isEvent = !item.category_id && amountVal === 0;
      const key = isEvent ? "event_reminder" : (item.category_id || "uncategorized");
      const current = map.get(key) || {
        category_id: isEvent ? "" : (item.category_id || ""),
        category_name: isEvent ? "事件提醒" : (item.category_name || "未分类"),
        amount: 0,
        count: 0,
      };
      current.amount += amountVal;
      current.count += 1;
      map.set(key, current);
    });
    return Array.from(map.values()).sort((a, b) => b.amount - a.amount);
  }

  static async getCalendarEvents(userId, year, monthNo) {
    const month = `${year}-${String(monthNo).padStart(2, "0")}`;
    const rows = await this.findAll(userId, { month, includeInactive: false });
    return rows.map(row => this.toCalendarEvent(row, month)).filter(Boolean);
  }

  static async getUpcomingReminders(userId, scope = "default") {
    const bjToday = dayjs().tz("Asia/Shanghai");
    const fromDays = scope === "all" ? 0 : 3;
    const toDays = scope === "all" ? 30 : 10;
    const start = bjToday.add(fromDays, "day").format("YYYY-MM-DD");
    const end = bjToday.add(toDays, "day").format("YYYY-MM-DD");
    const months = new Set([start.substring(0, 7), end.substring(0, 7)]);
    const result = [];

    for (const month of months) {
      const rows = await this.findAll(userId, { month, includeInactive: false });
      rows.forEach(row => {
        const event = this.toCalendarEvent(row, month);
        if (!event) return;
        if (event.happen_date >= start && event.happen_date <= end && event.month_status !== "done" && event.month_status !== "skipped") {
          result.push(event);
        }
      });
    }

    return result.sort((a, b) => a.happen_date.localeCompare(b.happen_date));
  }

  /**
   * 【日历提醒专用】获取当前自然月的固定支出/事件提醒（V2）。
   * ⚠️ 不复用 getUpcomingReminders 的 30 天滚动窗口——它会跨月把下个月的
   *    recurring 数据也带进当前月（例：8月视图出现 9月的固定支出）。
   * 本函数只返回「发生日期落在当前自然月」且未完成/未跳过的记录。
   * @param {string} userId
   * @param {object} opts { month? }  默认当前自然月
   * @returns {Promise<Array>}
   */
  static async getUpcomingRemindersCurrentMonth(userId, opts = {}) {
    const month = opts.month || dayjs().tz('Asia/Shanghai').format('YYYY-MM');
    const rows = await this.findAll(userId, { month, includeInactive: false });
    const result = [];
    rows.forEach((row) => {
      const event = this.toCalendarEvent(row, month);
      if (!event) return;
      if (event.happen_date >= `${month}-01` && event.happen_date <= `${month}-31`) {
        if (event.month_status !== "done" && event.month_status !== "skipped") {
          result.push(event);
        }
      }
    });
    return result.sort((a, b) => a.happen_date.localeCompare(b.happen_date));
  }
}

module.exports = RecurringExpense;
