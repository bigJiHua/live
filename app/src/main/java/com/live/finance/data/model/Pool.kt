package com.live.finance.data.model

/**
 * 同银行共享额度池（对应 GET /card/pool 行，表 card_credit_pool）。
 * 后端 findByUser 额外补 `card_count`(张数) 与 `cards`(池内卡 id 列表)。
 */
data class Pool(
    val id: String,
    val bankId: String = "",
    val bankName: String = "",
    val totalCreditLimit: Double = 0.0,
    val totalTempLimit: Double = 0.0,
    /** 是否开启「信报合一」（开启后池内可一键合并还款）。 */
    val creditReportMerged: Boolean = false,
    val currency: String = "CNY",
    val remark: String = "",
    val cardCount: Int = 0,
    val cardIds: List<String> = emptyList(),
) {
    /** 池标题（web：池名 || 银行名 || `共享额度池`）。 */
    val title: String get() = bankName.ifBlank { "共享额度池" }
}
