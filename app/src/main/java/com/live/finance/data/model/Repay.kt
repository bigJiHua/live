package com.live.finance.data.model

/** 还款记录（对应 GET /card/repay 行）。 */
data class Repay(
    val id: String,
    val cardId: String = "",
    val billMonth: String = "",
    val amount: Double = 0.0,
    val method: String = "",
    val time: String = "",
) {
    val dateLabel: String get() = com.live.finance.core.TimeFmt.date(time)
    val hhmm: String get() = com.live.finance.core.TimeFmt.hm(time)
}
