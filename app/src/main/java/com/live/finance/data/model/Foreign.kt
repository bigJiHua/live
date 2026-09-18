package com.live.finance.data.model

/**
 * 外币消费登记（对应 GET /card/bill/foreign/list 与 /pending，表 card_foreign_register）。
 * 汇率口径与全局一致：**每 100 外币等值人民币**（registeredRmb = foreignAmount * registeredRate / 100）。
 */
data class Foreign(
    val id: String,
    val cardId: String = "",
    val accountId: String = "",
    val currency: String = "",
    val foreignAmount: Double = 0.0,
    val registeredRate: Double = 0.0,
    val registeredRmb: Double = 0.0,
    val actualRate: Double = 0.0,
    val actualRmb: Double = 0.0,
    val settleDate: String = "",
    val status: String = "pending",     // pending(待对账) / reconciled(已对账)
    val remark: String = "",
    val createTime: String = "",
) {
    val statusLabel: String get() = if (status == "reconciled") "已对账" else "待对账"
    val isPending: Boolean get() = status != "reconciled"
}

/**
 * 历史外币消费流水（对应 GET /card/bill/foreign/history，扫描 account 账本 + LEFT JOIN 登记表）。
 * 字段名与登记表不同：`amount` 是原币金额，`exchange_rate` 是登记汇率，`reg_id` 为空表示「未登记」。
 */
data class ForeignHistory(
    val accountId: String,
    val cardId: String = "",
    val currency: String = "",
    val amount: Double = 0.0,
    val exchangeRate: Double = 0.0,
    val transDate: String = "",
    val regId: String = "",
    val regStatus: String = "",
    val registeredRate: Double = 0.0,
    val registeredRmb: Double = 0.0,
    val actualRmb: Double = 0.0,
    val settleDate: String = "",
) {
    /** 未登记（无登记记录）→ 可「登记」；否则显示已登记/已对账。 */
    val registered: Boolean get() = regId.isNotBlank()
    val statusLabel: String get() = when {
        !registered -> "未登记"
        regStatus == "reconciled" -> "已对账"
        else -> "待对账"
    }
}
