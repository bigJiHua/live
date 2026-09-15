package com.live.finance.data.model

/** 周期记账 / 固定事件（对应 GET /recurring/list 行，后端已 JOIN category_name/account_name）。 */
data class Recurring(
    val id: String,
    val name: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val categoryName: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val accountLast4: String = "",
    val cycle: String = "",          // 周期类型：monthly/weekly/...
    val dayOfCycle: Int = 0,
    val monthOfCycle: Int = 0,
    val status: String = "",
    val totalPeriods: Int = 0,       // 分期总期数
    val enteredPeriods: Int = 0,     // 已入账/已完成期数
) {
    val accountLabel: String get() = accountName.ifEmpty { "—" }
}
