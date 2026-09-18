package com.live.finance.data.model

/**
 * 待办（对应 `GET /todo/list` 行；`/todo/calendar/month` 的 `days[].list` 与 `/todo/reminders` 同构）。
 *
 * 字段按 web 实际消费面补齐（`views/Todo/Calendar.vue` + `components/calendar/CalendarGrid.vue`）：
 * 除普通待办外，同一数组里还会混入**固定支出/分期事件**（`source=recurring`，`category_id=installment`）
 * 与**信用卡还款提醒**（`source=card_bill`，只读）。
 */
data class Todo(
    val id: String,
    val content: String = "",
    val eventType: String = "",
    val happenDate: String = "",
    val status: String = "待完成",
    val priority: String = "",
    val needRemind: Boolean = false,
    // ── 日历页需要 ──
    /** `recurring`=固定支出/分期事件、`card_bill`=信用卡还款提醒、空=普通待办 */
    val source: String = "",
    /** `installment` 表示消费分期期次 */
    val categoryId: String = "",
    val amount: Double = 0.0,
    val remark: String = "",
    val isRecurring: Boolean = false,
    val recurringId: String = "",
    /** 固定支出当月状态：pending / entering / entered / done */
    val monthStatus: String = "",
    val monthOverdue: Boolean = false,
    /** 分期是否已作废（超期） */
    val isVoid: Boolean = false,
    val isOverdue: Boolean = false,
    /** `source=card_bill` 时的账单 id（用于「查看账单」） */
    val billId: String = "",
    /** 固定支出周期：`month` / `year`（web 用它判断是否显示「每年」标签） */
    val cycle: String = "",
    val remindDays: Int = 0,
    /** 毫秒时间戳字符串（后端按 remind_days 算出的提醒开始日） */
    val remindTime: String = "",
)

/** 日历某天（`GET /todo/calendar/month` → `data.days[]`）。 */
data class TodoCalendarDay(
    val date: String = "",
    val count: Int = 0,
    val hasOverdue: Boolean = false,
    val list: List<Todo> = emptyList(),
)

/** 日历月（`GET /todo/calendar/month` → `data`）。 */
data class TodoCalendarMonth(
    val year: Int = 0,
    val month: Int = 0,
    val days: List<TodoCalendarDay> = emptyList(),
)

/** 新增/编辑待办入参（web `createTodo` / `updateTodo` 的 body）。`priority` 为 1/2/3（高/中/低）。 */
data class NewTodo(
    val content: String,
    val happenDate: String,
    val eventType: String = "schedule",
    val priority: Int = 2,
    val isRecurring: Boolean = false,
    val needRemind: Boolean = false,
    val remindDays: Int = 0,
    val remark: String = "",
)

/** 工作信息（对应 GET /work/job/list 行）。 */
data class Job(
    val id: String,
    val jobType: String = "", // 'formal' 正式工 | 'parttime' 兼职
    val company: String = "",
    val status: String = "", // '在职' | '离职'
    val joinDate: String = "", // 入职日期 YYYY-MM-DD
    val leaveDate: String = "", // 离职日期 YYYY-MM-DD，空=未离职
    val payDay: Int = 0, // 发薪日（每月几号）
    val baseSalary: Double = 0.0, // 月基本工资
    val hourlyWage: Double = 0.0, // 时薪（兼职）
    // ===== 以下字段对应 web 工作设置完整表单（JobSetting.vue）=====
    val baseWorkDays: Int = 22, // 月计薪天数（日薪 = 月基本工资 / 月计薪天数）
    val subsidyMeal: Double = 0.0, // 餐补
    val subsidyTraffic: Double = 0.0, // 交通补
    val subsidyPost: Double = 0.0, // 岗位补
    val social: Double = 0.0, // 社保个人缴纳部分
    val fund: Double = 0.0, // 公积金个人缴纳部分
    val taxRate: Double = 0.0, // 个税税率（0~1）
)
