package com.live.finance.data.model

/** 同银行共享额度池（对应 GET /card/pool 行）。 */
data class Pool(
    val id: String,
    val bankName: String = "",
    val totalCreditLimit: Double = 0.0,
    val totalTempLimit: Double = 0.0,
    val currency: String = "CNY",
    val remark: String = "",
)
