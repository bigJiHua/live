package com.live.finance.data.model

/**
 * 还款记录（对应 GET /card/repay 行；后端 JOIN card_base(本卡/来源卡) + card_bill 带出
 * card_alias/card_last4/bill_amount/bill_need_repay/repay_card_alias/repay_card_last4）。
 *
 * repay_method 枚举（后端 #execRepayTx 校验）：`balance`(余额) / `bank_card`(银行卡) / `cash`(现金)。
 * ⚠ 后端已废弃 `card`(本卡还款) —— 传入会报「不支持"本卡还款"方式」。
 */
data class Repay(
    val id: String,
    val cardId: String = "",
    val billId: String = "",
    val billMonth: String = "",
    val amount: Double = 0.0,          // repay_amount
    val method: String = "",           // repay_method
    val repayCardId: String = "",
    val time: String = "",             // repay_time
    val remark: String = "",
    val accountId: String = "",        // 关联的来源支出流水 account.id（撤销用）
    val cardAlias: String = "",
    val cardLast4: String = "",
    val billAmount: Double = 0.0,
    val billNeedRepay: Double = 0.0,
    val repayCardAlias: String = "",
    val repayCardLast4: String = "",
) {
    val dateLabel: String get() = com.live.finance.core.TimeFmt.date(time)
    val hhmm: String get() = com.live.finance.core.TimeFmt.hm(time)

    /** 卡片展示名（web：card_alias || `****${card_last4}`）。 */
    val cardLabel: String
        get() = cardAlias.ifBlank { if (cardLast4.isNotBlank()) "****$cardLast4" else "信用卡" }

    /** 还款方式中文（对齐 web 的 cash/bank_card/balance 文案）。 */
    val methodLabel: String
        get() = when (method) {
            "cash" -> "现金"
            "bank_card" -> "借记卡"
            "balance" -> "余额"
            "card" -> "本卡"
            else -> method.ifBlank { "—" }
        }

    /** 来源卡展示（bank_card 时才有，web repay/Detail 展示）。 */
    val repayCardLabel: String
        get() = repayCardAlias.ifBlank { if (repayCardLast4.isNotBlank()) "****$repayCardLast4" else "" }
}
