package com.live.finance.data.model

/**
 * 固定资产（对应 `GET /fixedAsset/list` 行；字段名为库列原名）。
 *
 * 新增/编辑入参见 [NewFixedAsset]；web `List.vue` 用到的行字段还有
 * `month_deprec`（月折旧）/`total_deprec`（累计折旧）/`deprec_finished`（折旧完毕）/`scrap_date`（处置日）。
 */
data class FixedAsset(
    val id: String,
    val info: String = "",                 // 资产名称
    val tag: String = "",                  // 品类
    val imgUrl: String = "",
    val buyPrice: Double = 0.0,
    val nowVal: Double = 0.0,              // 当前账面价值
    val secondhandPrice: Double = 0.0,
    val useYears: Double = 0.0,            // ⚠ 后端是小数年限（web `step="0.1"`）
    val residualRate: Double = 0.0,        // 残值率（0~100）
    val residualVal: Double = 0.0,
    val buyDate: String = "",
    val scrapDate: String = "",            // 报废/出售/遗失日期
    val status: String = "using",
    val monthsUsed: Int = 0,
    val yearsUsed: Double = 0.0,
    val depreciableAmount: Double = 0.0,
    val monthDeprec: Double = 0.0,         // 月折旧额
    val totalDeprec: Double = 0.0,         // 累计折旧
    val lastDeprecDate: String = "",       // 上次折旧日期（详情页展示）
    val deprecFinished: Boolean = false,   // 折旧完毕（后端 1/0）
) {
    /** web `getStatusText`。 */
    val statusLabel: String get() = when (status) {
        "using" -> "使用中"; "scrapped" -> "已报废"; "sold" -> "已出售"; "lost" -> "已遗失"; else -> status
    }

    /** web `getStatusType` → `app-tag` 的 type。 */
    val statusTagType: String get() = when (status) {
        "using" -> "success"; "scrapped" -> "danger"; "sold" -> "primary"; "lost" -> "warning"; else -> "default"
    }

    /** 已归档（web `archived` 页签口径）。 */
    val archived: Boolean get() = status == "scrapped" || status == "sold" || status == "lost"

    /** 列表「当前价值」：web `item.now_val || item.buy_price`。 */
    val currentValue: Double get() = if (nowVal != 0.0) nowVal else buyPrice
}

/**
 * 固定资产新增/编辑入参（web `AssetForm.vue` 的 submit body）。
 * ⚠ 后端字段名是**库列原名**（`img_url`/`buy_price`/`residual_rate`/`secondhand_price`）。
 * ⚠ 编辑页（web `Edit.vue`）额外带 `now_val`，且**不含 `buy_date`**（后端可改字段里没有 buy_date）。
 */
data class NewFixedAsset(
    val info: String,
    val tag: String,
    val imgUrl: String = "",
    val buyPrice: Double = 0.0,
    val buyDate: String = "",
    val useYears: Double = 0.0,
    val residualRate: Double = 5.0,
    val secondhandPrice: Double? = null,
    /** 仅编辑页用：当前账面价值（可手动调整；空 → null）。 */
    val nowVal: Double? = null,
)

/**
 * 预算（对应 GET /budget/list、GET /budget/:id 行）。
 * budget_type：行=出行 / 买=购物 / 吃=餐饮（web List/Detail 的 tag 口径）。
 */
data class Budget(
    val id: String,
    val title: String = "",
    val budgetType: String = "",
    val budgetAmount: Double = 0.0,
    val usedAmount: Double = 0.0,
    val cycle: String = "",
    val planDate: String = "",
    val isOverBudget: Boolean = false,
    /** 路线（行 类型；web List 卡片的 location 行）。 */
    val route: String = "",
    /** 明细 JSON（三种类型结构各异，见 [BudgetDetails]）。 */
    val details: BudgetDetails? = null,
    /** 出行类型提交时前端算好的折合总额（web Travel submit 的 total_expense）。 */
    val totalExpense: Double = 0.0,
) {
    val remain: Double get() = budgetAmount - usedAmount
    /** 进度 0..1（按金额，封顶 1）。 */
    val ratio: Float get() = if (budgetAmount <= 0) 0f else (usedAmount / budgetAmount).toFloat().coerceIn(0f, 1f)
}

// ========== budget_details 三形态（web List.calcExpense / Detail.vue 解析口径） ==========

sealed interface BudgetDetails

/** 出行明细：days[].items[] + exchange_rates（1外币=?CNY）。 */
data class TravelDetails(
    val days: List<TravelDay> = emptyList(),
    val exchangeRates: List<RateItem> = emptyList(),
    val notes: String = "",
) : BudgetDetails {
    /** 单日折 CNY 合计（CNY 直接取 amount，外币取 cny_amount）。 */
    fun dayTotal(day: TravelDay): Double =
        day.items.sumOf { if (it.currency == "CNY") it.amount else it.cnyAmount }
    /** 全程折 CNY 总支出（web calcExpense『行』分支）。 */
    val totalCny: Double get() = days.sumOf { dayTotal(it) }
    /** 按币种汇总（web getTotalCurrencyDetail）。 */
    fun currencyTotals(): List<Pair<String, Double>> =
        days.flatMap { it.items }
            .filter { it.amount > 0 }
            .groupBy { it.currency }
            .map { (k, v) -> k to v.sumOf { it.amount } }
}

data class TravelDay(val date: String = "", val items: List<TravelItem> = emptyList())

data class TravelItem(
    val type: String = "吃",
    val description: String = "",
    val amount: Double = 0.0,
    val currency: String = "CNY",
    val cnyAmount: Double = 0.0,
)

data class RateItem(val currency: String, val value: String)

/** 购物明细：items[] + 三汇总（web Shopping submit body）。 */
data class ShoppingDetails(
    val items: List<ShopItem> = emptyList(),
    val estimatedTotal: Double = 0.0,
    val actualTotal: Double = 0.0,
    val purchasedCount: Int = 0,
    val notes: String = "",
) : BudgetDetails

data class ShopItem(
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val quantity: String = "1",
    val priority: String = "",
    val purchaseDate: String = "",
    val actualPrice: Double = 0.0,
    val shop: String = "",
    val notes: String = "",
)

/** 餐饮明细：dishes[]（价格×数量=小计）。 */
data class EatDetails(
    val dishes: List<Dish> = emptyList(),
    val actualTotal: Double = 0.0,
    val notes: String = "",
) : BudgetDetails {
    fun dishSubtotal(d: Dish): Double = d.price * (d.quantity.toDoubleOrNull() ?: 1.0)
}

data class Dish(
    val name: String = "",
    val price: Double = 0.0,
    val quantity: String = "1",
    val notes: String = "",
)
