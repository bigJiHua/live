package com.live.finance.data.model

/** 固定资产（对应 GET /fixedAsset/list 行；字段名为库列原名）。 */
data class FixedAsset(
    val id: String,
    val info: String = "",                 // 资产名称
    val tag: String = "",                  // 品类
    val imgUrl: String = "",
    val buyPrice: Double = 0.0,
    val nowVal: Double = 0.0,              // 当前账面价值
    val secondhandPrice: Double = 0.0,
    val useYears: Int = 0,
    val residualVal: Double = 0.0,
    val buyDate: String = "",
    val status: String = "using",
    val monthsUsed: Int = 0,
    val yearsUsed: Double = 0.0,
    val depreciableAmount: Double = 0.0,
) {
    val statusLabel: String get() = when (status) {
        "using" -> "在用"; "scrapped" -> "已报废"; "sold" -> "已出售"; "lost" -> "已遗失"; else -> status
    }
}

/** 预算（对应 GET /budget/list 行）。 */
data class Budget(
    val id: String,
    val title: String = "",
    val budgetType: String = "",
    val budgetAmount: Double = 0.0,
    val usedAmount: Double = 0.0,
    val cycle: String = "",
    val planDate: String = "",
    val isOverBudget: Boolean = false,
) {
    val remain: Double get() = budgetAmount - usedAmount
    /** 进度 0..1（按金额，封顶 1）。 */
    val ratio: Float get() = if (budgetAmount <= 0) 0f else (usedAmount / budgetAmount).toFloat().coerceIn(0f, 1f)
}
