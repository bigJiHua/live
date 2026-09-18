package com.live.finance.data.model

/**
 * 周期记账 / 固定事件 / 固定支出（对应 GET /recurring/list 行，后端已 JOIN category_name/account_name）。
 * web 真相源：views/Finance/events/Index.vue（固定事件）与 views/Finance/recurring/List.vue（固定支出）。
 * cycle 取值：month / year（web 表单 value；历史数据可能存 monthly/yearly，cycleLabel 兼容两种）。
 */
data class Recurring(
    val id: String,
    val name: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val categoryName: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val accountLast4: String = "",
    val cycle: String = "",          // month / year
    val dayOfCycle: Int = 0,
    val monthOfCycle: Int = 0,
    val endDate: String = "",
    val remark: String = "",
    val isActive: Boolean = true,
    val status: String = "",
    val totalPeriods: Int = 0,       // 分期总期数
    val enteredPeriods: Int = 0,     // 已入账/已完成期数
    /** 选中月份的期状态（固定支出页用）：pending/entered/done/skipped */
    val monthStatus: String = "",
    val monthAmount: Double = 0.0,
    val happenDate: String = "",     // 选中月份的实际发生日
    /** 全量期次明细（固定事件页展开用），键为 yyyy-MM */
    val monthRecords: Map<String, RecurringMonth> = emptyMap(),
) {
    val accountLabel: String get() = accountName.ifEmpty { "—" }
    val cycleLabel: String get() = when (cycle) {
        "month", "monthly" -> if (dayOfCycle > 0) "每月${dayOfCycle}号" else "每月"
        "year", "yearly" -> if (monthOfCycle > 0) "每年${monthOfCycle}月" else "每年"
        "week", "weekly" -> "每周${dayOfCycle}号"
        else -> cycle.ifBlank { "周期" }
    }
}

/** 单期明细（month_records 内一条）。 */
data class RecurringMonth(
    val status: String = "",         // pending / entered / done / skipped
    val planDate: String = "",       // 计划日
    val occurDate: String = "",      // 实际发生日
    val amount: Double = 0.0,
    val actualAmount: Double = 0.0,
) {
    val done: Boolean get() = status == "entered" || status == "done"
    val skipped: Boolean get() = status == "skipped"
}

/** GET /recurring/summary 返回。 */
data class RecurringSummary(
    val totalAmount: Double = 0.0,
    val total: Int = 0,
    val pending: Int = 0,
    val categoryStats: List<RecurringCatStat> = emptyList(),
)

data class RecurringCatStat(
    val categoryId: String = "",
    val categoryName: String = "",
    val count: Int = 0,
    val amount: Double = 0.0,
)
