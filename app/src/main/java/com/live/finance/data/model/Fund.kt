package com.live.finance.data.model

/**
 * 理财/基金（对应 GET /fund/list 聚合行）。
 * 后端 `aggregateSelect` 计算列：
 *  - invest  = 当前本金（初始本金 + 累计增持本金）
 *  - market_val = 当前市值（当前本金 + 累计收益）
 *  - net_value = 累计收益（= profit_delta）
 *  - rate  = 收益率字符串，如 "12.34%"
 *  - base_invest / base_net_value / base_market_val = 编辑时的「初始」基准值
 *  - capital_delta = 累计增持本金
 */
data class Fund(
    val id: String,
    val fundName: String = "",
    val fundCompany: String = "",
    val sellOrg: String = "",
    val tradeAccount: String = "",
    val fundAccount: String = "",
    val share: Double = 0.0,
    val invest: Double = 0.0,          // 当前本金
    val marketVal: Double = 0.0,       // 当前市值
    val netValue: Double = 0.0,        // 累计收益
    val rate: String = "",             // 收益率，形如 "12.34%"
    val buyDate: String = "",
    val baseInvest: Double = 0.0,      // 初始本金
    val baseNetValue: Double = 0.0,
    val baseMarketVal: Double = 0.0,
    val capitalDelta: Double = 0.0,    // 累计增持本金
) {
    val isProfit: Boolean get() = netValue >= 0
}

/** 净值历史记录（对应 bus_fund_history 行）。netValue=当日收益，marketVal=当日增持本金。 */
data class FundHistory(
    val id: String,
    val fundId: String = "",
    val netValue: Double = 0.0,        // 当日收益
    val marketVal: Double = 0.0,       // 当日增持本金
    val recordDate: String = "",
    val createTime: String = "",
)

/** GET /fund/:id/history/monthly 响应。 */
data class FundMonthlyHistory(
    val records: List<FundHistory>,
    val latestRecordDate: String = "",
    val beforeProfit: Double = 0.0,
    val beforeCapital: Double = 0.0,
)

/** GET /fund/:id/history 响应（range 区间汇总）。 */
data class FundHistoryRange(
    val startDate: String? = null,
    val endDate: String? = null,
    val profitBefore: Double = 0.0,
    val capitalBefore: Double = 0.0,
)

data class FundHistoryPage(
    val list: List<FundHistory>,
    val range: FundHistoryRange,
)
