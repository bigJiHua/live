package com.live.finance.data.model

/** 分类统计项。 */
data class CatStat(val name: String, val total: Double, val count: Int)

/** 月度统计（对应 GET /account/stats/month）。 */
data class MonthStats(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val expenseBreakdown: List<CatStat> = emptyList(),
    val incomeBreakdown: List<CatStat> = emptyList(),
) {
    val balance: Double get() = income - expense
}
