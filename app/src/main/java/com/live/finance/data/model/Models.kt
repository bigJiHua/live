package com.live.finance.data.model

/** 登录用户（对应 GET /auth/me data）。 */
data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val avatar: String = "",
) {
    val displayName: String get() = username.ifEmpty { email.ifEmpty { "用户" } }
}

/**
 * 流水行 —— 对应 GET /account 返回的 data.list[*]（后端已 JOIN 出 category_name / card_alias / card_last4）。
 * direction：0 支出、1 收入、2 转账/其它（与 web isExpense 判定一致）。
 */
data class FlowRow(
    val id: String,
    val direction: Int,
    val amount: Double,
    val currency: String = "CNY",
    val exchangeRate: Double = 1.0,
    val payMethod: String = "",
    /** 后端 `pay_type`（"转账"/"其他支出"/"储蓄卡"…）。web 的转账/提现/冲正配对**以它为准**（`item.pay_type || item.category_name`）。 */
    val payType: String = "",
    val accountType: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val transDate: String = "",          // yyyy-MM-dd
    val createTime: String = "",         // yyyy-MM-dd HH:mm:ss
    val remark: String = "",
    val cardId: String = "",
    val cardAlias: String = "",
    val cardLast4: String = "",
    val reversedId: String = "",
    val transferGroupId: String = "",
) {
    val isIncome: Boolean get() = direction == 1
    val isExpense: Boolean get() = direction == 0 || direction == 2
    val isTransfer: Boolean get() = direction == 2 || transferGroupId.isNotEmpty()

    /** "HH:mm"（createTime 为后端毫秒时间戳字符串）。 */
    val time: String get() = com.live.finance.core.TimeFmt.hm(createTime)

    /** 银行卡简称「别名 后四位」，用于横向可滑的一行。 */
    val bankLabel: String get() =
        listOf(cardAlias, cardLast4).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { payMethod }

    /** 展示日（优先 trans_date）。 */
    val day: String get() = transDate.ifEmpty { createTime.take(10) }
}

/** 转账明细行（对应 GET /account/transfer/list 行，表 account_transfer）。 */
data class TransferRow(
    val id: String,
    val fromCardId: String = "",
    val toCardId: String = "",
    val amount: Double = 0.0,
    val transDate: String = "",
    val remark: String = "",
)
