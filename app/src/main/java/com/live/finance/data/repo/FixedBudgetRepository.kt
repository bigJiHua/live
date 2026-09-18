package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Budget
import com.live.finance.data.model.BudgetDetails
import com.live.finance.data.model.Dish
import com.live.finance.data.model.EatDetails
import com.live.finance.data.model.FixedAsset
import com.live.finance.data.model.NewFixedAsset
import com.live.finance.data.model.RateItem
import com.live.finance.data.model.ShopItem
import com.live.finance.data.model.ShoppingDetails
import com.live.finance.data.model.TravelDay
import com.live.finance.data.model.TravelDetails
import com.live.finance.data.model.TravelItem

interface FixedAssetRepository {
    /** 资产列表（后端 `GET /fixedAsset/list` 会自动触发折旧巡检）。 */
    suspend fun list(): ApiResult<List<FixedAsset>>

    /** 回收站：`GET /fixedAsset/recycle-bin`。 */
    suspend fun recycleBin(): ApiResult<List<FixedAsset>>

    /** 单个资产：`GET /fixedAsset/:id`。 */
    suspend fun detail(id: String): ApiResult<FixedAsset?>

    /** 新增：`POST /fixedAsset/`。 */
    suspend fun create(p: NewFixedAsset): ApiResult<Unit>

    /** 编辑：`PUT /fixedAsset/:id`。 */
    suspend fun update(id: String, p: NewFixedAsset): ApiResult<Unit>

    /** 状态流转：`PUT /fixedAsset/:id/status {status}`（using/scrapped/sold/lost）。 */
    suspend fun changeStatus(id: String, status: String): ApiResult<Unit>

    /** 软删除（进回收站）：`DELETE /fixedAsset/:id`。 */
    suspend fun remove(id: String): ApiResult<Unit>

    /** 从回收站恢复：`PUT /fixedAsset/restore/:id`。 */
    suspend fun restore(id: String): ApiResult<Unit>

    /** 永久删除：`DELETE /fixedAsset/permanent/:id`。 */
    suspend fun permanentDelete(id: String): ApiResult<Unit>
}

class FakeFixedAssetRepository : FixedAssetRepository {
    private val rows = mutableListOf(
        FixedAsset(
            "fa1", "MacBook Pro 14", "电脑", buyPrice = 18999.0, nowVal = 12000.0, useYears = 3.0,
            residualRate = 5.0, buyDate = "2023-05-01", monthsUsed = 28,
            monthDeprec = 501.8, totalDeprec = 6999.0,
        ),
        FixedAsset(
            "fa2", "家用轿车", "其他", buyPrice = 150000.0, nowVal = 98000.0, useYears = 8.0,
            residualRate = 5.0, buyDate = "2020-01-01", monthsUsed = 80,
            monthDeprec = 1484.4, totalDeprec = 52000.0,
        ),
        FixedAsset(
            "fa3", "旧手机 iPhone 11", "手机", buyPrice = 5999.0, nowVal = 0.0, useYears = 2.0,
            buyDate = "2021-03-01", status = "sold", scrapDate = "2023-06-01",
            monthDeprec = 237.4, totalDeprec = 5999.0, deprecFinished = true,
        ),
    )
    private val trash = mutableListOf<FixedAsset>()

    override suspend fun list(): ApiResult<List<FixedAsset>> = ApiResult.Ok(rows.toList(), "")
    override suspend fun recycleBin(): ApiResult<List<FixedAsset>> = ApiResult.Ok(trash.toList(), "")
    override suspend fun detail(id: String): ApiResult<FixedAsset?> =
        ApiResult.Ok((rows + trash).firstOrNull { it.id == id }, "")

    override suspend fun create(p: NewFixedAsset): ApiResult<Unit> {
        rows.add(
            FixedAsset(
                id = "fa_${System.currentTimeMillis()}", info = p.info, tag = p.tag, imgUrl = p.imgUrl,
                buyPrice = p.buyPrice, nowVal = p.buyPrice, useYears = p.useYears,
                residualRate = p.residualRate, buyDate = p.buyDate, secondhandPrice = p.secondhandPrice ?: 0.0,
                monthDeprec = if (p.useYears > 0) p.buyPrice * (1 - p.residualRate / 100) / (p.useYears * 12) else 0.0,
            ),
        )
        return ApiResult.Ok(Unit, "创建成功")
    }

    override suspend fun update(id: String, p: NewFixedAsset): ApiResult<Unit> {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) {
            rows[i] = rows[i].copy(
                info = p.info, tag = p.tag, imgUrl = p.imgUrl, buyPrice = p.buyPrice,
                useYears = p.useYears, residualRate = p.residualRate, buyDate = p.buyDate,
                secondhandPrice = p.secondhandPrice ?: 0.0,
            )
        }
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun changeStatus(id: String, status: String): ApiResult<Unit> {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = rows[i].copy(status = status)
        return ApiResult.Ok(Unit, "已更新")
    }

    override suspend fun remove(id: String): ApiResult<Unit> {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) trash.add(rows.removeAt(i))
        return ApiResult.Ok(Unit, "已移入回收站")
    }

    override suspend fun restore(id: String): ApiResult<Unit> {
        val i = trash.indexOfFirst { it.id == id }
        if (i >= 0) rows.add(trash.removeAt(i))
        return ApiResult.Ok(Unit, "已恢复")
    }

    override suspend fun permanentDelete(id: String): ApiResult<Unit> {
        trash.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已永久删除")
    }
}

class RemoteFixedAssetRepository(private val client: ApiClient) : FixedAssetRepository {
    override suspend fun list(): ApiResult<List<FixedAsset>> =
        client.get("/fixedAsset/list").toResult().map { data -> arr(data).map { parseAsset(it.asJsonObject) } }

    override suspend fun recycleBin(): ApiResult<List<FixedAsset>> =
        client.get("/fixedAsset/recycle-bin").toResult().map { data -> arr(data).map { parseAsset(it.asJsonObject) } }

    override suspend fun detail(id: String): ApiResult<FixedAsset?> =
        client.get("/fixedAsset/$id").toResult().map { data ->
            (data as? JsonObject)?.let { parseAsset(it) }
        }

    override suspend fun create(p: NewFixedAsset): ApiResult<Unit> =
        client.post("/fixedAsset/", createBody(p)).toResult().map { }

    override suspend fun update(id: String, p: NewFixedAsset): ApiResult<Unit> =
        client.put("/fixedAsset/$id", updateBody(p)).toResult().map { }

    override suspend fun changeStatus(id: String, status: String): ApiResult<Unit> =
        client.put("/fixedAsset/$id/status", JsonObject().apply { addProperty("status", status) }).toResult().map { }

    override suspend fun remove(id: String): ApiResult<Unit> =
        client.delete("/fixedAsset/$id").toResult().map { }

    override suspend fun restore(id: String): ApiResult<Unit> =
        client.put("/fixedAsset/restore/$id", JsonObject()).toResult().map { }

    override suspend fun permanentDelete(id: String): ApiResult<Unit> =
        client.delete("/fixedAsset/permanent/$id").toResult().map { }

    /** web `AssetForm.submit`（新增）的 body：带 `buy_date`；`secondhand_price` 空值传 **null**，不是 0。 */
    private fun createBody(p: NewFixedAsset): JsonObject = JsonObject().apply {
        addProperty("info", p.info)
        addProperty("tag", p.tag)
        addProperty("img_url", p.imgUrl)
        addProperty("buy_price", p.buyPrice)
        addProperty("buy_date", p.buyDate)
        addProperty("use_years", p.useYears)
        addProperty("residual_rate", p.residualRate)
        addNullable("secondhand_price", p.secondhandPrice)
    }

    /**
     * web `Edit.vue`（编辑页）的 body：**不含 `buy_date`**（后端可改字段里没有它），
     * 额外带 `now_val`（可手动调整账面价值，空 → null）。
     */
    private fun updateBody(p: NewFixedAsset): JsonObject = JsonObject().apply {
        addProperty("info", p.info)
        addProperty("tag", p.tag)
        addProperty("img_url", p.imgUrl)
        addProperty("buy_price", p.buyPrice)
        addProperty("use_years", p.useYears)
        addProperty("residual_rate", p.residualRate)
        addNullable("secondhand_price", p.secondhandPrice)
        addNullable("now_val", p.nowVal)
    }

    private fun JsonObject.addNullable(key: String, v: Double?) {
        if (v == null) add(key, JsonNull.INSTANCE) else addProperty(key, v)
    }

    private fun parseAsset(o: JsonObject) = FixedAsset(
        id = str(o, "id"), info = str(o, "info"), tag = str(o, "tag"), imgUrl = str(o, "img_url"),
        buyPrice = dbl(o, "buy_price"), nowVal = dbl(o, "now_val"), secondhandPrice = dbl(o, "secondhand_price"),
        useYears = dbl(o, "use_years"), residualRate = dbl(o, "residual_rate"),
        residualVal = dbl(o, "residual_val"),
        buyDate = str(o, "buy_date").take(10), scrapDate = str(o, "scrap_date").take(10),
        status = str(o, "status").ifEmpty { "using" },
        monthsUsed = dbl(o, "months_used").toInt(), yearsUsed = dbl(o, "years_used"),
        depreciableAmount = dbl(o, "depreciable_amount"),
        monthDeprec = dbl(o, "month_deprec"), totalDeprec = dbl(o, "total_deprec"),
        lastDeprecDate = str(o, "last_deprec_date").take(10),
        deprecFinished = int01(o, "deprec_finished") == 1,
    )
}

interface BudgetRepository {
    suspend fun list(): ApiResult<List<Budget>>
    suspend fun detail(id: String): ApiResult<Budget>

    /** POST /budget/（title/budget_type/route/cycle/plan_date/budget_amount/budget_details[/total_expense]）。 */
    suspend fun create(b: Budget): ApiResult<Budget>

    /** PUT /budget/:id（同构 body）。 */
    suspend fun update(b: Budget): ApiResult<Budget>

    /** DELETE /budget/:id（硬删除，pinLockGuard，8303 由 ApiClient 全局承接）。 */
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeBudgetRepository : BudgetRepository {
    private val seed = mutableListOf(
        Budget("b1", "9月餐饮预算", "吃", 2000.0, 1450.0, "月", "2026-09-01",
            details = EatDetails(
                dishes = listOf(
                    Dish("火锅", 258.0, "1", "周五"),
                    Dish("奶茶", 18.0, "5", ""),
                ),
                actualTotal = 348.0, notes = "控制奶茶次数",
            )),
        Budget("b2", "618 购物清单", "买", 3000.0, 3200.0, "月", "2026-06-18",
            details = ShoppingDetails(
                items = listOf(
                    ShopItem("iPhone 16", "电子产品", 7999.0, "1", "想要", "2026-06-18", 8099.0, "京东自营", ""),
                    ShopItem("数据线", "配件", 39.0, "3", "必买", "2026-06-18", 117.0, "淘宝", "Type-C"),
                ),
                estimatedTotal = 8116.0, actualTotal = 3290.0, purchasedCount = 2,
            )),
        Budget("b3", "香港 3 天 2 晚", "行", 5000.0, 0.0, "月", "2026-10-01", route = "深圳 → 香港",
            details = TravelDetails(
                days = listOf(
                    TravelDay("2026-10-01", listOf(
                        TravelItem("行", "高铁票", 215.0, "CNY", 215.0),
                        TravelItem("吃", "晚餐", 180.0, "HKD", 165.6),
                    )),
                    TravelDay("2026-10-02", listOf(
                        TravelItem("买", "化妆品", 520.0, "HKD", 478.4),
                    )),
                ),
                exchangeRates = listOf(RateItem("HKD", "0.92"), RateItem("USD", "7.25"), RateItem("EUR", "7.80"), RateItem("GBP", "9.00")),
            )),
    )
    override suspend fun list(): ApiResult<List<Budget>> = ApiResult.Ok(seed.toList(), "")
    override suspend fun detail(id: String): ApiResult<Budget> =
        seed.firstOrNull { it.id == id }?.let { ApiResult.Ok(it, "") }
            ?: ApiResult.Fail("预算不存在", 404)
    override suspend fun create(b: Budget): ApiResult<Budget> {
        val created = b.copy(id = "b${System.currentTimeMillis().toString().takeLast(8)}")
        seed.add(0, created)
        return ApiResult.Ok(created, "")
    }
    override suspend fun update(b: Budget): ApiResult<Budget> {
        val i = seed.indexOfFirst { it.id == b.id }
        if (i < 0) return ApiResult.Fail("预算不存在", 404)
        seed[i] = b
        return ApiResult.Ok(b, "")
    }
    override suspend fun delete(id: String): ApiResult<Unit> {
        return if (seed.removeAll { it.id == id }) ApiResult.Ok(Unit, "") else ApiResult.Fail("预算不存在", 404)
    }
}

class RemoteBudgetRepository(private val client: ApiClient) : BudgetRepository {
    override suspend fun list(): ApiResult<List<Budget>> =
        client.get("/budget/list").toResult().map { data ->
            arr(data).map { el -> parse(el.asJsonObject) }
        }

    override suspend fun detail(id: String): ApiResult<Budget> =
        client.get("/budget/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun create(b: Budget): ApiResult<Budget> =
        client.post("/budget/", buildBody(b)).toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun update(b: Budget): ApiResult<Budget> =
        client.put("/budget/${b.id}", buildBody(b)).toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/budget/$id").toResult().map { }

    // ===== 序列化（字段名与 web 提交体逐字一致）=====
    private fun buildBody(b: Budget): JsonObject = JsonObject().apply {
        addProperty("title", b.title)
        addProperty("budget_type", b.budgetType)
        addProperty("cycle", b.cycle.ifEmpty { "月" })
        addProperty("plan_date", b.planDate)
        addProperty("budget_amount", b.budgetAmount)
        if (b.route.isNotEmpty()) addProperty("route", b.route)
        when (val d = b.details) {
            is TravelDetails -> {
                add("budget_details", JsonObject().apply {
                    add("days", JsonArray().also { arr ->
                        d.days.forEach { day ->
                            arr.add(JsonObject().apply {
                                addProperty("date", day.date)
                                add("items", JsonArray().also { ia ->
                                    day.items.forEach { it0 ->
                                        ia.add(JsonObject().apply {
                                            addProperty("type", it0.type)
                                            addProperty("description", it0.description)
                                            addProperty("amount", it0.amount)
                                            addProperty("currency", it0.currency)
                                            addProperty("cny_amount", round2(it0.cnyAmount))
                                        })
                                    }
                                })
                            })
                        }
                    })
                    add("exchange_rates", JsonArray().also { ra ->
                        d.exchangeRates.forEach { ra.add(JsonObject().apply {
                            addProperty("currency", it.currency); addProperty("value", it.value)
                        }) }
                    })
                    addProperty("notes", d.notes)
                })
                addProperty("total_expense", round2(d.totalCny))
            }
            is ShoppingDetails -> {
                add("budget_details", JsonObject().apply {
                    add("items", JsonArray().also { arr ->
                        d.items.forEach { s ->
                            arr.add(JsonObject().apply {
                                addProperty("name", s.name); addProperty("category", s.category)
                                addProperty("price", s.price); addProperty("quantity", s.quantity)
                                addProperty("priority", s.priority); addProperty("purchase_date", s.purchaseDate)
                                addProperty("actual_price", s.actualPrice); addProperty("shop", s.shop)
                                addProperty("notes", s.notes)
                            })
                        }
                    })
                    addProperty("estimated_total", round2(d.estimatedTotal))
                    addProperty("actual_total", round2(d.actualTotal))
                    addProperty("purchased_count", d.purchasedCount)
                    addProperty("notes", d.notes)
                })
            }
            is EatDetails -> {
                add("budget_details", JsonObject().apply {
                    add("dishes", JsonArray().also { arr ->
                        d.dishes.forEach { dish ->
                            arr.add(JsonObject().apply {
                                addProperty("name", dish.name); addProperty("price", dish.price)
                                addProperty("quantity", dish.quantity); addProperty("notes", dish.notes)
                            })
                        }
                    })
                    addProperty("actual_total", round2(d.actualTotal))
                    addProperty("notes", d.notes)
                })
            }
            else -> {}
        }
    }

    // ===== 反序列化（budget_details 兼容三形态，按 budget_type 分流）=====
    private fun parse(o: JsonObject) = Budget(
        id = str(o, "id"),
        title = str(o, "title"),
        budgetType = str(o, "budget_type"),
        budgetAmount = dbl(o, "budget_amount"),
        usedAmount = dbl(o, "used_amount"),
        cycle = str(o, "cycle"),
        planDate = str(o, "plan_date").take(10),
        isOverBudget = int01(o, "is_over_budget") == 1,
        route = str(o, "route"),
        details = parseDetails(str(o, "budget_type"), o.get("budget_details")),
        totalExpense = dbl(o, "total_expense"),
    )

    private fun parseDetails(type: String, el: JsonElement?): BudgetDetails? {
        val d = el as? JsonObject ?: return null
        return when (type) {
            "行" -> TravelDetails(
                days = (d.getAsJsonArray("days") ?: JsonArray()).mapNotNull { dayEl ->
                    (dayEl as? JsonObject)?.let { day ->
                        TravelDay(
                            date = str(day, "date"),
                            items = (day.getAsJsonArray("items") ?: JsonArray()).mapNotNull { itemEl ->
                                (itemEl as? JsonObject)?.let { item ->
                                    TravelItem(
                                        type = str(item, "type").ifEmpty { "吃" },
                                        description = str(item, "description"),
                                        amount = dbl(item, "amount"),
                                        currency = str(item, "currency").ifEmpty { "CNY" },
                                        cnyAmount = dbl(item, "cny_amount"),
                                    )
                                }
                            },
                        )
                    }
                },
                exchangeRates = (d.getAsJsonArray("exchange_rates") ?: JsonArray()).mapNotNull { r ->
                    (r as? JsonObject)?.let { RateItem(str(it, "currency"), str(it, "value")) }
                },
                notes = str(d, "notes"),
            )
            "买" -> ShoppingDetails(
                items = (d.getAsJsonArray("items") ?: JsonArray()).mapNotNull { itemEl ->
                    (itemEl as? JsonObject)?.let { s ->
                        ShopItem(
                            name = str(s, "name"), category = str(s, "category"),
                            price = dbl(s, "price"), quantity = str(s, "quantity").ifEmpty { "1" },
                            priority = str(s, "priority"), purchaseDate = str(s, "purchase_date").take(10),
                            actualPrice = dbl(s, "actual_price"), shop = str(s, "shop"), notes = str(s, "notes"),
                        )
                    }
                },
                estimatedTotal = dbl(d, "estimated_total"),
                actualTotal = dbl(d, "actual_total"),
                purchasedCount = dbl(d, "purchased_count").toInt(),
                notes = str(d, "notes"),
            )
            "吃" -> EatDetails(
                dishes = (d.getAsJsonArray("dishes") ?: JsonArray()).mapNotNull { dishEl ->
                    (dishEl as? JsonObject)?.let { dish ->
                        Dish(
                            name = str(dish, "name"), price = dbl(dish, "price"),
                            quantity = str(dish, "quantity").ifEmpty { "1" }, notes = str(dish, "notes"),
                        )
                    }
                },
                actualTotal = dbl(d, "actual_total"),
                notes = str(d, "notes"),
            )
            else -> null
        }
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}

// ---- 共用宽松解析助手（本域专用）----
private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
private fun int01(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
private fun arr(data: JsonElement?): JsonArray = when (data) {
    is JsonArray -> data
    is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
    else -> JsonArray()
}
