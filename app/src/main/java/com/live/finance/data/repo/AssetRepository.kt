package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.AssetDetails
import com.live.finance.data.model.AssetHome
import com.live.finance.data.model.AssetItem
import com.live.finance.data.model.AssetRegister
import com.live.finance.data.model.FlowRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface AssetRepository {
    suspend fun list(startDate: String? = null, endDate: String? = null): ApiResult<List<AssetRegister>>
    suspend fun detail(id: String): ApiResult<AssetRegister>
    suspend fun home(): ApiResult<AssetHome>

    /** POST /asset/register（body.data 三总额 + asset_details + remark）。 */
    suspend fun create(record: AssetRegister): ApiResult<AssetRegister>

    /** PUT /asset/register/:id（同构 body）。 */
    suspend fun update(record: AssetRegister): ApiResult<AssetRegister>

    /** DELETE /asset/register/:id（后端 pinLockGuard，8303 由 ApiClient.pinGate 全局承接）。 */
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeAssetRepository : AssetRepository {
    private val seed = mutableListOf(
        AssetRegister(
            "ar3", 258000.00, 12000.00, 246000.00, "2026-09-14", "手动资产登记",
            "2026-09-14 10:00:00", "1789000000000", "1789000000000",
            AssetDetails(
                balance = listOf(
                    AssetItem("bl_1", "wechat", "", 8200.0),
                    AssetItem("bl_2", "bank", "招行活期", 120000.0),
                ),
                offshore = listOf(AssetItem("of_1", "OtherHKD", "", 50000.0, currency = "HKD")),
                debt = listOf(AssetItem("db_1", "CreditCard", "", 12000.0)),
                exchangeRates = mapOf("HKD" to "91.20", "USD" to "712.30", "GBP" to "", "EUR" to ""),
            ),
        ),
        AssetRegister("ar2", 245000.00, 15000.00, 230000.00, "2026-08-14", "", "2026-08-14 10:00:00", "1786000000000", "1786000000000"),
        AssetRegister("ar1", 231000.00, 18000.00, 213000.00, "2026-07-14", "", "2026-07-14 10:00:00", "1783000000000", "1783000000000"),
    )

    override suspend fun list(startDate: String?, endDate: String?): ApiResult<List<AssetRegister>> =
        ApiResult.Ok(seed.toList(), "")

    override suspend fun detail(id: String): ApiResult<AssetRegister> =
        seed.firstOrNull { it.id == id }?.let { ApiResult.Ok(it, "") }
            ?: ApiResult.Fail("记录不存在", 404)

    override suspend fun create(record: AssetRegister): ApiResult<AssetRegister> {
        val now = System.currentTimeMillis().toString()
        val created = record.copy(
            id = "ar${now.takeLast(9)}",
            registerDate = record.registerDate.ifBlank { TODAY_FMT.format(Date()) },
            registerTime = DATETIME_FMT.format(Date()),
            createTime = now,
            updateTime = now,
        )
        seed.add(0, created)
        return ApiResult.Ok(created, "")
    }

    override suspend fun update(record: AssetRegister): ApiResult<AssetRegister> {
        val idx = seed.indexOfFirst { it.id == record.id }
        if (idx < 0) return ApiResult.Fail("记录不存在", 404)
        val now = System.currentTimeMillis().toString()
        val updated = record.copy(updateTime = now)
        seed[idx] = updated
        return ApiResult.Ok(updated, "")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        val removed = seed.removeAll { it.id == id }
        return if (removed) ApiResult.Ok(Unit, "") else ApiResult.Fail("记录不存在", 404)
    }

    override suspend fun home(): ApiResult<AssetHome> =
        ApiResult.Ok(
            AssetHome(
                totalAsset = 258000.00, creditDebt = 12000.00, totalBalance = 246000.00,
                debitCardCount = 3, creditCardCount = 2, monthlySurplus = 8600.0,
                monthIncome = 22000.0, todayIncome = 0.0, todayExpense = 328.5,
                recent = listOf(
                    FlowRow("r1", 0, 1288.0, categoryName = "数码", payMethod = "信用卡", transDate = "2026-09-13", createTime = "1757769600000", cardAlias = "招商银行", cardLast4 = "6621"),
                    FlowRow("r2", 1, 12000.0, categoryName = "工资", payMethod = "储蓄卡", transDate = "2026-09-10", createTime = "1757488800000", cardAlias = "中国银行", cardLast4 = "1234"),
                ),
            ), "",
        )

    private companion object {
        val TODAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val DATETIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}

class RemoteAssetRepository(private val client: ApiClient) : AssetRepository {
    override suspend fun list(startDate: String?, endDate: String?): ApiResult<List<AssetRegister>> {
        val q = buildMap { startDate?.let { put("startDate", it) }; endDate?.let { put("endDate", it) } }
        return client.get("/asset/register/list", q).toResult().map { data ->
            toArr(data).map { parse(it.asJsonObject) }
        }
    }

    override suspend fun detail(id: String): ApiResult<AssetRegister> =
        client.get("/asset/register/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun create(record: AssetRegister): ApiResult<AssetRegister> =
        client.post("/asset/register", buildBody(record)).toResult().map {
            parse((it as? JsonObject) ?: JsonObject())
        }

    override suspend fun update(record: AssetRegister): ApiResult<AssetRegister> =
        client.put("/asset/register/${record.id}", buildBody(record)).toResult().map {
            parse((it as? JsonObject) ?: JsonObject())
        }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/asset/register/$id").toResult().map { }

    override suspend fun home(): ApiResult<AssetHome> =
        client.get("/asset/home").toResult().map { data ->
            val o = data as? JsonObject ?: return@map AssetHome()
            val recent = (o.get("largeTransactions") as? JsonArray)?.map {
                val r = it.asJsonObject
                FlowRow(
                    id = str(r, "id"), direction = int(r, "direction"), amount = dbl(r, "amount"),
                    currency = str(r, "currency").ifEmpty { "CNY" },
                    payMethod = str(r, "pay_method"), categoryName = str(r, "category_name").ifEmpty { str(r, "pay_type") },
                    transDate = str(r, "trans_date").take(10), createTime = str(r, "create_time"),
                    accountType = str(r, "account_type"), cardAlias = str(r, "card_alias"), cardLast4 = str(r, "card_last4"),
                )
            } ?: emptyList()
            AssetHome(
                totalAsset = dbl(o, "total_asset"),
                creditDebt = dbl(o, "credit_debt"),
                totalBalance = dbl(o, "total_balance"),
                debitCardCount = int(o, "debitCardCount"),
                creditCardCount = int(o, "creditCardCount"),
                monthlySurplus = dbl(o, "monthBalance"),
                monthIncome = dbl(o, "monthIncome"),
                todayIncome = dbl(o, "todayIncome"),
                todayExpense = dbl(o, "todayExpense"),
                recent = recent,
            )
        }

    // ========== 序列化 ==========

    private fun buildBody(r: AssetRegister): JsonObject = JsonObject().apply {
        addProperty("total_asset", round2(r.totalAsset))
        addProperty("credit_debt", round2(r.creditDebt))
        addProperty("total_balance", round2(r.totalBalance))
        add("asset_details", buildDetails(r.details ?: AssetDetails()))
        addProperty("remark", r.remark.ifBlank { "手动资产登记" })
    }

    private fun buildDetails(d: AssetDetails): JsonObject = JsonObject().apply {
        add("balance", JsonArray().also { arr -> d.balance.forEach { arr.add(itemJson(it)) } })
        add("offshore", JsonArray().also { arr -> d.offshore.forEach { arr.add(itemJson(it)) } })
        add("debt", JsonArray().also { arr -> d.debt.forEach { arr.add(itemJson(it)) } })
        add("exchangeRates", JsonObject().also { o ->
            d.exchangeRates.forEach { (k, v) -> o.addProperty(k, v) }
        })
    }

    private fun itemJson(i: AssetItem): JsonObject = JsonObject().apply {
        if (i.id.isNotEmpty()) addProperty("id", i.id)
        addProperty("type", i.type)
        addProperty("customName", i.customName)
        addProperty("amount", i.amount)
        addProperty("remark", i.remark)
        addProperty("currency", i.currency)
    }

    // ========== 反序列化 ==========

    private fun parse(o: JsonObject) = AssetRegister(
        id = str(o, "id"),
        totalAsset = dbl(o, "total_asset"),
        creditDebt = dbl(o, "credit_debt"),
        totalBalance = dbl(o, "total_balance"),
        registerDate = str(o, "register_date").take(10),
        remark = str(o, "remark"),
        registerTime = str(o, "register_time"),
        createTime = str(o, "create_time"),
        updateTime = str(o, "update_time"),
        details = (o.get("asset_details") as? JsonObject)?.let(::parseDetails),
    )

    /**
     * 兼容两种历史形态：
     *  - 现网（web 提交）：balance/offshore/debt 为 **数组**；
     *  - 早期：对象 map（key=类型 → 行内可能缺 type，需用 key 回填）。
     */
    private fun parseDetails(o: JsonObject): AssetDetails = AssetDetails(
        balance = parseItems(o.get("balance")),
        offshore = parseItems(o.get("offshore")),
        debt = parseItems(o.get("debt")),
        exchangeRates = (o.get("exchangeRates") as? JsonObject)?.entrySet()?.associate { (k, v) ->
            k to if (v.isJsonNull) "" else v.asString
        } ?: emptyMap(),
    )

    private fun parseItems(el: JsonElement?): List<AssetItem> = when (el) {
        is JsonArray -> el.mapNotNull { it as? JsonObject }.map { parseItem(it, "") }
        is JsonObject -> el.entrySet().mapNotNull { (key, v) ->
            (v as? JsonObject)?.let { parseItem(it, key) }
        }
        else -> emptyList()
    }

    private fun parseItem(o: JsonObject, fallbackType: String) = AssetItem(
        id = str(o, "id"),
        type = str(o, "type").ifEmpty { fallbackType },
        customName = str(o, "customName"),
        amount = dbl(o, "amount"),
        remark = str(o, "remark"),
        currency = str(o, "currency").ifEmpty { "CNY" },
    )

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: data.getAsJsonArray("data") ?: JsonArray()
        else -> JsonArray()
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private fun dbl(o: JsonObject, k: String): Double =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    private fun int(o: JsonObject, k: String): Int =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
    private fun str(o: JsonObject, k: String): String =
        if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
}
