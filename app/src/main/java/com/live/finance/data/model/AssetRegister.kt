package com.live.finance.data.model

/** 资产登记记录（对应 GET /asset/register/list 行）。 */
data class AssetRegister(
    val id: String,
    val totalAsset: Double = 0.0,
    val creditDebt: Double = 0.0,
    val totalBalance: Double = 0.0,
    val registerDate: String = "",
    val remark: String = "",
)

/** 首页资产总览（GET /asset/home → 最新快照 + 聚合）。 */
data class AssetHome(
    val totalAsset: Double = 0.0,
    val creditDebt: Double = 0.0,
    val totalBalance: Double = 0.0,
    val debitCardCount: Int = 0,
    val creditCardCount: Int = 0,
    val monthlySurplus: Double = 0.0,   // monthBalance
    val monthIncome: Double = 0.0,
    val todayIncome: Double = 0.0,
    val todayExpense: Double = 0.0,
    val recent: List<FlowRow> = emptyList(),   // largeTransactions
)
