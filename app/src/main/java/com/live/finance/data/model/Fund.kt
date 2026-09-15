package com.live.finance.data.model

/** 理财/基金（对应 GET /fund/list 行，含后端计算列 invest/market_val/rate）。 */
data class Fund(
    val id: String,
    val fundName: String = "",
    val fundAccount: String = "",
    val share: Double = 0.0,
    val invest: Double = 0.0,          // 当前投入成本
    val marketVal: Double = 0.0,       // 当前市值
    val netValue: Double = 0.0,        // 累计收益
    val rate: String = "",             // 收益率，形如 "12.34%"
    val buyDate: String = "",
) {
    val isProfit: Boolean get() = netValue >= 0
}
