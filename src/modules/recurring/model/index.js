const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");

class RecurringExpense {
  static tableName = "bus_recurring";

  static today() {
    return new Date().toISOString().substring(0, 10);
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
    const item = this.attachMonthInfo(row, month);
    const statusMap = {
      done: "已完成",
      skipped: "已取消",
      pending: "待完成",
    };

    return {
      id: `recurring_${item.id}_${month}`,
      source: "recurring",
      recurring_id: item.id,
      content: item.name,
      event_type: "fixed_expense",
      happen_date: item.happen_date,
      status: statusMap[item.month_status] || "待完成",
      priority: 2,
      need_remind: 1,
      is_recurring: 1,
      amount: item.month_amount,
      category_id: item.category_id,
      category_name: item.category_name || "",
      remark: item.remark || "",
      month_status: item.month_status,
      is_fixed_expense: true,
    };
  }

  static async findAll(userId, { month, includeInactive = false } = {}) {
    const params = [userId];
    let where = "WHERE r.user_id = ? AND r.is_deleted = 0";
    if (!includeInactive) {
      where += " AND r.is_active = 1";
    }

    const [rows] = await db.execute(
      `SELECT r.*, c.name AS category_name, cb.alias AS account_name, cb.last4_no AS account_last4
       FROM ${this.tableName} r
       LEFT JOIN bus_category c ON r.category_id = c.id AND c.is_deleted = 0
       LEFT JOIN card_base cb ON r.account_id = cb.id AND cb.is_deleted = 0
       ${where}
       ORDER BY r.is_active DESC, r.day_of_cycle ASC, r.create_time DESC`,
      params
    );

    if (!month) return rows.map(row => ({ ...row, month_records: this.parseMonthRecords(row.month_records) }));
    const safeMonth = this.normalizeMonth(month);
    return rows.map(row => this.attachMonthInfo(row, safeMonth));
  }

  static async findById(id, userId) {
    const [rows] = await db.execute(
      `SELECT r.*, c.name AS category_name, cb.alias AS account_name, cb.last4_no AS account_last4
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

  static async create({ userId, name, amount, categoryId, accountId, cycle, dayOfCycle, remindDays, remark, isActive }) {
    if (!name || !String(name).trim()) throw new Error("固定支出名称不能为空");
    if (!amount || Number(amount) <= 0) throw new Error("金额必须大于0");

    const finalCycle = cycle || "month";
    if (!["month"].includes(finalCycle)) {
      throw new Error("当前仅支持按月固定支出");
    }

    const day = Math.max(1, Math.min(31, Number(dayOfCycle || 1)));
    const id = idUtils.billId();
    const now = this.now();
    const nextDate = this.getNextDate(day);

    await db.execute(
      `INSERT INTO ${this.tableName}
       (id, user_id, name, amount, category_id, account_id, cycle, day_of_cycle,
        next_date, remind_days, remark, month_records, is_active, create_time, update_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
      [
        id,
        userId,
        String(name).trim(),
        amount,
        categoryId || null,
        accountId || null,
        finalCycle,
        day,
        nextDate,
        Number(remindDays || 0),
        remark || null,
        "{}",
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
      remindDays: "remind_days",
      remark: "remark",
      isActive: "is_active",
    };

    if (updates.name !== undefined && !String(updates.name).trim()) {
      throw new Error("固定支出名称不能为空");
    }
    if (updates.amount !== undefined && Number(updates.amount) <= 0) {
      throw new Error("金额必须大于0");
    }
    if (updates.cycle !== undefined && updates.cycle !== "month") {
      throw new Error("当前仅支持按月固定支出");
    }

    const fields = [];
    const params = [];
    Object.entries(map).forEach(([key, column]) => {
      if (updates[key] !== undefined) {
        fields.push(`${column} = ?`);
        if (key === "dayOfCycle") {
          params.push(Math.max(1, Math.min(31, Number(updates[key] || 1))));
        } else if (key === "remindDays") {
          params.push(Number(updates[key] || 0));
        } else if (key === "isActive") {
          params.push(updates[key] ? 1 : 0);
        } else if (key === "categoryId" || key === "accountId") {
          params.push(updates[key] || null);
        } else {
          params.push(updates[key]);
        }
      }
    });

    if (updates.dayOfCycle !== undefined) {
      fields.push("next_date = ?");
      params.push(this.getNextDate(Math.max(1, Math.min(31, Number(updates.dayOfCycle || 1)))));
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

    await db.execute(
      `UPDATE ${this.tableName} SET month_records = ?, update_time = ? WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [JSON.stringify(records), this.now(), id, userId]
    );

    const row = await this.findById(id, userId);
    return this.attachMonthInfo(row, safeMonth);
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
      const key = item.category_id || "uncategorized";
      const current = map.get(key) || {
        category_id: item.category_id || "",
        category_name: item.category_name || "未分类",
        amount: 0,
        count: 0,
      };
      current.amount += Number(item.month_amount || 0);
      current.count += 1;
      map.set(key, current);
    });
    return Array.from(map.values()).sort((a, b) => b.amount - a.amount);
  }

  static async getCalendarEvents(userId, year, monthNo) {
    const month = `${year}-${String(monthNo).padStart(2, "0")}`;
    const rows = await this.findAll(userId, { month, includeInactive: false });
    return rows.map(row => this.toCalendarEvent(row, month));
  }

  static async getUpcomingReminders(userId, scope = "default") {
    const today = new Date();
    const fromDays = scope === "all" ? 0 : 3;
    const toDays = scope === "all" ? 30 : 10;
    const start = new Date(today.getTime() + fromDays * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
    const end = new Date(today.getTime() + toDays * 24 * 60 * 60 * 1000).toISOString().substring(0, 10);
    const months = new Set([start.substring(0, 7), end.substring(0, 7)]);
    const result = [];

    for (const month of months) {
      const rows = await this.findAll(userId, { month, includeInactive: false });
      rows.forEach(row => {
        const event = this.toCalendarEvent(row, month);
        if (event.happen_date >= start && event.happen_date <= end && event.month_status !== "done" && event.month_status !== "skipped") {
          result.push(event);
        }
      });
    }

    return result.sort((a, b) => a.happen_date.localeCompare(b.happen_date));
  }
}

module.exports = RecurringExpense;
