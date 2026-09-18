package com.live.finance.data.model

/** 资产明细单项（境内 balance / 境外 offshore / 负债 debt 通用，对应 web asset_details 内的行）。 */
data class AssetItem(
    val id: String = "",
    val type: String = "",
    val customName: String = "",
    val amount: Double = 0.0,
    val remark: String = "",
    /** 仅境外项使用（HKD/USD/…），境内与负债默认 CNY。 */
    val currency: String = "CNY",
)

/**
 * 登记快照里的资产明细（`asset_details` JSON 列）：
 * ```
 * { balance: [AssetItem], offshore: [AssetItem], debt: [AssetItem], exchangeRates: { HKD: "91.2", … } }
 * ```
 * 汇率语义 = 每 100 外币等值人民币（与记账页一致）。
 */
data class AssetDetails(
    val balance: List<AssetItem> = emptyList(),
    val offshore: List<AssetItem> = emptyList(),
    val debt: List<AssetItem> = emptyList(),
    val exchangeRates: Map<String, String> = emptyMap(),
)

/** 资产登记记录（对应 GET /asset/register/list 行；表 asset_register）。 */
data class AssetRegister(
    val id: String,
    val totalAsset: Double = 0.0,
    val creditDebt: Double = 0.0,
    val totalBalance: Double = 0.0,
    val registerDate: String = "",
    val remark: String = "",
    /** "YYYY-MM-DD HH:mm:ss"（后端本地时间拼接）。 */
    val registerTime: String = "",
    /** 毫秒时间戳字符串（同 moment.create_time 口径）。 */
    val createTime: String = "",
    val updateTime: String = "",
    val details: AssetDetails? = null,
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
