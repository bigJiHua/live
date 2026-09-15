package com.live.finance.data.model

/** 信用卡账单（对应 GET /card/bill 行，后端已 JOIN 卡别名/尾号/年费 + 计算逾期）。 */
data class Bill(
    val id: String,
    val cardId: String = "",
    val billMonth: String = "",
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val billAmount: Double = 0.0,
    val needRepay: Double = 0.0,
    val repaid: Double = 0.0,
    val minRepay: Double = 0.0,
    val status: String = "",
    val cardAlias: String = "",
    val cardLast4: String = "",
    val annualFee: Double = 0.0,
    val feeFreeRule: String = "",
    val isOverdue: Boolean = false,
    val overdueDays: Int = 0,
) {
    val cardLabel: String get() = cardAlias.ifBlank { if (cardLast4.isNotBlank()) "银行卡($cardLast4)" else "信用卡" }
    val statusLabel: String get() = when {
        isOverdue -> "逾期${overdueDays}天"
        needRepay > 0 -> "待还款"
        else -> "已还清"
    }
    val overdue: Boolean get() = isOverdue
}
