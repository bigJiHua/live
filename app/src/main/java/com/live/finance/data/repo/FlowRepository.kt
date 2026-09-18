package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.CatStat
import com.live.finance.data.model.FlowRow
import com.live.finance.data.model.TransferRow
import com.live.finance.data.model.MonthStats

/** 新增一笔的入参（对齐后端 AccountRules.create 必填字段）。 */
data class NewFlow(
    val direction: Int,            // 0 支出 / 1 收入
    val amount: Double,
    val categoryId: String,
    val categoryName: String,      // → payType
    val payMethod: String,
    val cardId: String,
    val transDate: String,         // yyyy-MM-dd
    val currency: String = "CNY",
    val exchangeRate: Double = 1.0,
    val remark: String = "",
    /**
     * 转账组 ID（自转/提现的两笔流水共用）。后端在 `direction=1 + payType=转账` 时凭它回查支出方并写 `account_transfer`
     * （见 `api/src/modules/account/model/debit.js`）。非转账场景留空。
     */
    val transferGroupId: String = "",
    /** 所选账户是否信用卡：决定 POST /account/credit 还是 /account/debit（与 direction 无关）。 */
    val isCredit: Boolean = false,
)

interface FlowRepository {
    suspend fun list(
        year: Int,
        month: Int,
        direction: Int? = null,
        page: Int = 1,
        limit: Int = 50,
    ): ApiResult<List<FlowRow>>

    /** 某张卡的收支流水（GET /account/list?cardId=），可按账单周期过滤（web bill/Ledger 用）。 */
    suspend fun listByCard(
        cardId: String,
        page: Int = 1,
        limit: Int = 50,
        startDate: String? = null,
        endDate: String? = null,
        direction: Int? = null,
    ): ApiResult<List<FlowRow>>

    suspend fun detail(id: String): ApiResult<FlowRow>

    /** 区间查询（`GET /account?startDate&endDate`）：web 下拉刷新「今天」与自定义区间用。 */
    suspend fun listRange(
        startDate: String,
        endDate: String,
        direction: Int? = null,
        page: Int = 1,
        limit: Int = 100,
    ): ApiResult<List<FlowRow>>

    /** 月度收支 + 分类占比（GET /account/stats/month）。 */
    suspend fun monthStats(year: Int, month: Int): ApiResult<MonthStats>

    /**
     * 各卡近 N 月「支出/收入笔数」（GET /account/stats/cards-flow?months=）。
     * 返回 cardId → (expenseCount, incomeCount)；后端对全部借记/信用卡给默认 0 行。
     */
    suspend fun cardsFlowStats(months: Int = 6): ApiResult<Map<String, Pair<Int, Int>>>

    /** 转账明细（GET /account/transfer/list，后端自动配对 account_transfer 表）。返回 (total, rows)。 */
    suspend fun transferList(page: Int = 1, limit: Int = 20): ApiResult<Pair<Int, List<TransferRow>>>

    /** 仅改备注（`PATCH /account/:id/remark {remark}`）；回传后端 message（web `showToast` 用）。 */
    suspend fun updateRemark(id: String, remark: String): ApiResult<String>

    /** 改分类（`PUT /account/credit/:id` 或 `/account/debit/:id`，body `{categoryId}`，按 `account_type` 分流）。 */
    suspend fun updateCategory(id: String, categoryId: String, isCredit: Boolean): ApiResult<Unit>

    /** 借记卡冲正（`POST /account/debit/:id/reverse`）。 */
    suspend fun reverseDebit(id: String): ApiResult<String>

    /** 转账冲正（自转/提现撤销，`POST /account/debit/:id/reverse/transfer`）。 */
    suspend fun reverseTransfer(id: String): ApiResult<String>

    /** 信用卡消费冲正（`POST /account/credit/:id/reverse/expense`）。 */
    suspend fun reverseCreditExpense(id: String): ApiResult<String>

    /** 支出走 POST /account/debit，收入走 POST /account/credit。 */
    suspend fun create(p: NewFlow): ApiResult<Unit>

    /** 删除流水 DELETE /account/:id（软删除，pinLockGuard 保护）。 */
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeFlowRepository : FlowRepository {
    private val seed = mutableListOf(
        FlowRow("f1", 0, 128.50, payMethod = "微信", categoryName = "餐饮", transDate = "2026-09-14", createTime = "2026-09-14 12:30:00", cardAlias = "微信零钱", cardLast4 = ""),
        FlowRow("f2", 0, 36.00, payMethod = "支付宝", categoryName = "交通", transDate = "2026-09-14", createTime = "2026-09-14 09:12:00", cardAlias = "招商银行", cardLast4 = "6621"),
        FlowRow("f3", 1, 12000.00, categoryName = "工资", transDate = "2026-09-10", createTime = "2026-09-10 10:00:00", cardAlias = "中国银行", cardLast4 = "1234"),
        FlowRow("f4", 0, 259.90, categoryName = "购物", transDate = "2026-09-10", createTime = "2026-09-10 20:41:00", cardAlias = "中国建设银行龙卡储蓄卡", cardLast4 = "9012"),
        FlowRow("f5", 2, 5000.00, categoryName = "转账", transDate = "2026-09-08", createTime = "2026-09-08 15:00:00", transferGroupId = "g1", cardAlias = "工商银行储蓄卡", cardLast4 = "0088"),
        FlowRow("f6", 1, 5000.00, categoryName = "转账", transDate = "2026-09-08", createTime = "2026-09-08 15:00:00", transferGroupId = "g1", cardAlias = "招商银行", cardLast4 = "6621"),
    )
    override suspend fun list(year: Int, month: Int, direction: Int?, page: Int, limit: Int): ApiResult<List<FlowRow>> =
        ApiResult.Ok(seed, "")

    override suspend fun listByCard(
        cardId: String,
        page: Int,
        limit: Int,
        startDate: String?,
        endDate: String?,
        direction: Int?,
    ): ApiResult<List<FlowRow>> =
        ApiResult.Ok(seed.filter { (it.cardId == cardId || cardId.isEmpty()) && (startDate == null || it.day >= startDate) && (endDate == null || it.day <= endDate) && (direction == null || it.direction == direction) }, "")

    override suspend fun listRange(
        startDate: String,
        endDate: String,
        direction: Int?,
        page: Int,
        limit: Int,
    ): ApiResult<List<FlowRow>> =
        ApiResult.Ok(seed.filter { it.day in startDate..endDate && (direction == null || it.direction == direction) }, "")

    override suspend fun detail(id: String): ApiResult<FlowRow> =
        ApiResult.Ok(seed.first { it.id == id }, "")

    override suspend fun updateRemark(id: String, remark: String): ApiResult<String> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(remark = remark)
        return ApiResult.Ok("操作成功", "操作成功")
    }

    override suspend fun updateCategory(id: String, categoryId: String, isCredit: Boolean): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(categoryId = categoryId)
        return ApiResult.Ok(Unit, "分类已更新")
    }

    override suspend fun reverseDebit(id: String): ApiResult<String> = ApiResult.Ok("冲正成功", "冲正成功")
    override suspend fun reverseTransfer(id: String): ApiResult<String> = ApiResult.Ok("冲正成功", "冲正成功")
    override suspend fun reverseCreditExpense(id: String): ApiResult<String> = ApiResult.Ok("冲正成功", "冲正成功")

    override suspend fun create(p: NewFlow): ApiResult<Unit> {
        seed.add(0, FlowRow(
            id = "f${System.currentTimeMillis()}",
            direction = p.direction,
            amount = p.amount,
            currency = p.currency,
            payMethod = p.payMethod,
            categoryName = p.categoryName,
            transDate = p.transDate,
            createTime = "${p.transDate} ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}",
            remark = p.remark,
            cardAlias = p.payMethod,
        ))
        return ApiResult.Ok(Unit, "登记成功")
    }

    override suspend fun monthStats(year: Int, month: Int): ApiResult<MonthStats> = ApiResult.Ok(
        MonthStats(
            income = 12000.0, expense = 16800.40, incomeCount = 1, expenseCount = 5,
            expenseBreakdown = listOf(
                CatStat("购物", 12000.0, 2), CatStat("餐饮", 293.40, 2), CatStat("交通", 36.00, 1),
            ),
            incomeBreakdown = listOf(CatStat("工资", 12000.0, 1)),
        ), ""
    )

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }

    override suspend fun cardsFlowStats(months: Int): ApiResult<Map<String, Pair<Int, Int>>> =
        ApiResult.Ok(mapOf(
            "c1" to (3 to 1),
            "c2" to (0 to 0),
        ), "")

    override suspend fun transferList(page: Int, limit: Int): ApiResult<Pair<Int, List<TransferRow>>> {
        val all = seed.filter { it.isTransfer }
        val from = (page - 1) * limit
        val rows = all.drop(from).take(limit).map {
            TransferRow(it.id, fromCardId = it.cardId, amount = it.amount, transDate = it.transDate, remark = "转账")
        }
        return ApiResult.Ok(all.size to rows, "")
    }
}

/** 真实实现：GET /account（后端已 JOIN 分类名/卡别名/后四位）。 */
class RemoteFlowRepository(private val client: ApiClient) : FlowRepository {
    override suspend fun list(
        year: Int,
        month: Int,
        direction: Int?,
        page: Int,
        limit: Int,
    ): ApiResult<List<FlowRow>> {
        val mm = month.toString().padStart(2, '0')
        return listRange("$year-$mm-01", "$year-$mm-${daysInMonth(year, month)}", direction, page, limit)
    }

    override suspend fun detail(id: String): ApiResult<FlowRow> =
        client.get("/account/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun listByCard(
        cardId: String,
        page: Int,
        limit: Int,
        startDate: String?,
        endDate: String?,
        direction: Int?,
    ): ApiResult<List<FlowRow>> {
        val q = linkedMapOf("cardId" to cardId, "page" to page.toString(), "limit" to limit.toString())
        if (!startDate.isNullOrBlank()) q["startDate"] = startDate
        if (!endDate.isNullOrBlank()) q["endDate"] = endDate
        if (direction != null) q["direction"] = direction.toString()
        return client.get("/account/list", q)
            .toResult().map { data -> parseList(data) }
    }

    override suspend fun listRange(
        startDate: String,
        endDate: String,
        direction: Int?,
        page: Int,
        limit: Int,
    ): ApiResult<List<FlowRow>> {
        val query = mutableMapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
            "startDate" to startDate,
            "endDate" to endDate,
        )
        direction?.let { query["direction"] = it.toString() }
        return client.get("/account", query).toResult().map { data -> parseList(data) }
    }

    override suspend fun updateRemark(id: String, remark: String): ApiResult<String> {
        val body = JsonObject().apply { addProperty("remark", remark) }
        return client.patch("/account/$id/remark", body).toResult().map { data ->
            (data as? JsonObject)?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: ""
        }
    }

    override suspend fun updateCategory(id: String, categoryId: String, isCredit: Boolean): ApiResult<Unit> {
        val body = JsonObject().apply { addProperty("categoryId", categoryId) }
        val path = if (isCredit) "/account/credit/$id" else "/account/debit/$id"
        return client.put(path, body).toResult().map { }
    }

    override suspend fun reverseDebit(id: String): ApiResult<String> =
        client.post("/account/debit/$id/reverse").toResult().map { messageOf(it) }

    override suspend fun reverseTransfer(id: String): ApiResult<String> =
        client.post("/account/debit/$id/reverse/transfer").toResult().map { messageOf(it) }

    override suspend fun reverseCreditExpense(id: String): ApiResult<String> =
        client.post("/account/credit/$id/reverse/expense").toResult().map { messageOf(it) }

    /** 冲正响应：`res.message`（web `showToast(res.message || "冲正成功")`）。 */
    private fun messageOf(data: com.google.gson.JsonElement?): String =
        (data as? JsonObject)?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: ""

    private fun parseList(data: com.google.gson.JsonElement?): List<FlowRow> =
        when (data) {
            is JsonArray -> data.map { parse(it.asJsonObject) }
            is JsonObject -> (data.getAsJsonArray("list") ?: JsonArray()).map { parse(it.asJsonObject) }
            else -> emptyList()
        }

    override suspend fun create(p: NewFlow): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("direction", p.direction)
            addProperty("amount", p.amount)
            addProperty("currency", p.currency)
            addProperty("exchangeRate", p.exchangeRate)
            addProperty("transDate", p.transDate)
            addProperty("categoryId", p.categoryId)
            addProperty("payType", p.categoryName)
            addProperty("payMethod", p.payMethod)
            addProperty("cardId", p.cardId)
            addProperty("remark", p.remark)
            // 转账组：后端凭 transferGroupId 关联两笔流水（account_transfer），非转账不发
            if (p.transferGroupId.isNotBlank()) addProperty("transferGroupId", p.transferGroupId)
        }
        val path = if (p.isCredit) "/account/credit" else "/account/debit"
        return client.post(path, body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/account/$id").toResult().map { }

    override suspend fun monthStats(year: Int, month: Int): ApiResult<MonthStats> =
        client.get("/account/stats/month", mapOf("year" to year.toString(), "month" to month.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject ?: return@map MonthStats()
                val cb = o.getAsJsonObject("categoryBreakdown")
                MonthStats(
                    income = dbl(o, "income"), expense = dbl(o, "expense"),
                    incomeCount = dbl(o, "incomeCount").toInt(), expenseCount = dbl(o, "expenseCount").toInt(),
                    expenseBreakdown = catList(cb?.getAsJsonArray("expense")),
                    incomeBreakdown = catList(cb?.getAsJsonArray("income")),
                )
            }

    override suspend fun cardsFlowStats(months: Int): ApiResult<Map<String, Pair<Int, Int>>> =
        client.get("/account/stats/cards-flow", mapOf("months" to months.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject
                val arr = o?.getAsJsonArray("list")
                    ?: (data as? JsonArray)
                    ?: JsonArray()
                arr.associate { el ->
                    val row = el.asJsonObject
                    str(row, "cardId") to (int(row, "expenseCount") to int(row, "incomeCount"))
                }
            }

    override suspend fun transferList(page: Int, limit: Int): ApiResult<Pair<Int, List<TransferRow>>> =
        client.get("/account/transfer/list", mapOf("page" to page.toString(), "limit" to limit.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject ?: return@map 0 to emptyList()
                val rows = (o.getAsJsonArray("list") ?: JsonArray()).mapNotNull { el ->
                    (el as? JsonObject)?.let { r ->
                        TransferRow(
                            id = str(r, "id"),
                            fromCardId = str(r, "from_card_id"),
                            toCardId = str(r, "to_card_id"),
                            amount = dbl(r, "amount"),
                            transDate = str(r, "trans_date").take(10),
                            remark = str(r, "remark"),
                        )
                    }
                }
                dbl(o, "total").toInt() to rows
            }

    private fun catList(arr: JsonArray?): List<CatStat> =
        arr?.map { el -> val o = el.asJsonObject
            CatStat(str(o, "categoryName"), dbl(o, "total"), dbl(o, "count").toInt())
        } ?: emptyList()

    private fun str(o: JsonObject, vararg keys: String): String =
        keys.firstOrNull { o.has(it) && !o.get(it).isJsonNull }?.let { o.get(it).asString } ?: ""

    private fun dbl(o: JsonObject, key: String): Double =
        runCatching { if (o.has(key) && !o.get(key).isJsonNull) o.get(key).asDouble else 0.0 }.getOrDefault(0.0)

    private fun int(o: JsonObject, key: String): Int =
        runCatching { if (o.has(key) && !o.get(key).isJsonNull) o.get(key).asInt else 0 }.getOrDefault(0)

    private fun parse(o: JsonObject) = FlowRow(
        id = str(o, "id"),
        direction = if (o.has("direction") && !o.get("direction").isJsonNull) o.get("direction").asInt else 0,
        amount = dbl(o, "amount"),
        currency = str(o, "currency").ifEmpty { "CNY" },
        exchangeRate = if (o.has("exchange_rate") && !o.get("exchange_rate").isJsonNull) o.get("exchange_rate").asDouble else 1.0,
        payMethod = str(o, "pay_method"),
        payType = str(o, "pay_type"),
        accountType = str(o, "account_type"),
        categoryId = str(o, "category_id"),
        categoryName = str(o, "category_name", "pay_type").ifEmpty { "未知" },
        transDate = str(o, "trans_date").take(10),
        createTime = str(o, "create_time"),
        remark = str(o, "remark"),
        cardId = str(o, "card_id"),
        cardAlias = str(o, "card_alias"),
        cardLast4 = str(o, "card_last4"),
        reversedId = str(o, "reversed_id"),
        transferGroupId = str(o, "transfer_group_id"),
    )
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
