package com.live.finance.data.model

/** 银行卡/账户（对应 GET /card 行，后端已 JOIN bank_name）。 */
data class Card(
    val id: String,
    val cardType: String = "debit",      // debit / credit
    val bankId: String = "",
    val bankName: String = "",
    val alias: String = "",
    val last4: String = "",
    val cardBin: String = "",
    val cardOrg: String = "",
    val cardLevel: String = "",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    val currency: String = "CNY",
    val openDate: String = "",
    val expireDate: String = "",
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val creditLimit: Double = 0.0,
) {
    val isCredit: Boolean get() = cardType == "credit"
    /** 「别名 或 银行名(尾号xxxx)」——对齐前端 alias || 银行名(尾号) 兜底。 */
    val displayName: String
        get() = alias.ifBlank {
            val b = bankName.ifBlank { "未命名卡" }
            if (last4.isNotBlank()) "$b($last4)" else b
        }
}
