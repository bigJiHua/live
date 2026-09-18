package com.live.finance.data.model

/** 账户余额（对应 GET /accountBalance 行；虚拟账户 card_id = xxxx现金 / yyyy余额）。 */
data class Balance(
    val cardId: String,
    val balance: Double = 0.0,
    val alias: String = "",
    val cardAlias: String = "",
    val cardLast4: String = "",
    val currency: String = "CNY",
    /** 后端 card_type：虚拟行 cash/digital/virtual，实体行 debit/credit。 */
    val cardType: String = "",
) {
    val isVirtual: Boolean get() = cardId == "xxxx" || cardId == "yyyy"
    val label: String get() = when (cardId) {
        "xxxx" -> "现金"
        "yyyy" -> "余额"
        else -> cardAlias.ifBlank { alias.ifBlank { if (cardLast4.isNotBlank()) "银行卡($cardLast4)" else "未命名账户" } }
    }
}
