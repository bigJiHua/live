package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.CatStat
import com.live.finance.data.model.FlowRow
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

    /** 某张卡的收支流水（GET /account/list?cardId=）。 */
    suspend fun listByCard(cardId: String, page: Int = 1, limit: Int = 50): ApiResult<List<FlowRow>>

    suspend fun detail(id: String): ApiResult<FlowRow>

    /** 月度收支 + 分类占比（GET /account/stats/month）。 */
    suspend fun monthStats(year: Int, month: Int): ApiResult<MonthStats>

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

    override suspend fun listByCard(cardId: String, page: Int, limit: Int): ApiResult<List<FlowRow>> =
        ApiResult.Ok(seed.filter { it.cardId == cardId || cardId.isEmpty() }, "")

    override suspend fun detail(id: String): ApiResult<FlowRow> =
        ApiResult.Ok(seed.first { it.id == id }, "")

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
        val query = mutableMapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
            "startDate" to "$year-$mm-01",
            "endDate" to "$year-$mm-${daysInMonth(year, month)}",
        )
        direction?.let { query["direction"] = it.toString() }
        return client.get("/account", query).toResult().map { data ->
            val o = data as? JsonObject
            val arr: JsonArray = o?.getAsJsonArray("list") ?: JsonArray()
            arr.map { parse(it.asJsonObject) }
        }
    }

    override suspend fun detail(id: String): ApiResult<FlowRow> =
        client.get("/account/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun listByCard(cardId: String, page: Int, limit: Int): ApiResult<List<FlowRow>> =
        client.get("/account/list", mapOf("cardId" to cardId, "page" to page.toString(), "limit" to limit.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject
                val arr: JsonArray = o?.getAsJsonArray("list") ?: JsonArray()
                arr.map { parse(it.asJsonObject) }
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

    private fun catList(arr: JsonArray?): List<CatStat> =
        arr?.map { el -> val o = el.asJsonObject
            CatStat(str(o, "categoryName"), dbl(o, "total"), dbl(o, "count").toInt())
        } ?: emptyList()

    private fun str(o: JsonObject, vararg keys: String): String =
        keys.firstOrNull { o.has(it) && !o.get(it).isJsonNull }?.let { o.get(it).asString } ?: ""

    private fun dbl(o: JsonObject, key: String): Double =
        runCatching { if (o.has(key) && !o.get(key).isJsonNull) o.get(key).asDouble else 0.0 }.getOrDefault(0.0)

    private fun parse(o: JsonObject) = FlowRow(
        id = str(o, "id"),
        direction = if (o.has("direction") && !o.get("direction").isJsonNull) o.get("direction").asInt else 0,
        amount = dbl(o, "amount"),
        currency = str(o, "currency").ifEmpty { "CNY" },
        exchangeRate = if (o.has("exchange_rate") && !o.get("exchange_rate").isJsonNull) o.get("exchange_rate").asDouble else 1.0,
        payMethod = str(o, "pay_method"),
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
