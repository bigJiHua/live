package com.live.finance.data.model

/** 外币消费登记（对应 GET /card/bill/foreign/list）。 */
data class Foreign(
    val id: String,
    val cardId: String = "",
    val currency: String = "",
    val foreignAmount: Double = 0.0,
    val registeredRate: Double = 0.0,
    val registeredRmb: Double = 0.0,
    val status: String = "pending",
    val createTime: String = "",
) {
    val statusLabel: String get() = when (status) {
        "pending" -> "待对账"; "reconciled" -> "已对账"; else -> status
    }
}
