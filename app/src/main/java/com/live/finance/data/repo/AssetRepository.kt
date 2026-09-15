package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.AssetHome
import com.live.finance.data.model.AssetRegister
import com.live.finance.data.model.FlowRow

interface AssetRepository {
    suspend fun list(startDate: String? = null, endDate: String? = null): ApiResult<List<AssetRegister>>
    suspend fun detail(id: String): ApiResult<AssetRegister>
    suspend fun home(): ApiResult<AssetHome>
}

class FakeAssetRepository : AssetRepository {
    private val seed = listOf(
        AssetRegister("ar3", 258000.00, 12000.00, 246000.00, "2026-09-14", "手动资产登记"),
        AssetRegister("ar2", 245000.00, 15000.00, 230000.00, "2026-08-14", ""),
        AssetRegister("ar1", 231000.00, 18000.00, 213000.00, "2026-07-14", ""),
    )
    override suspend fun list(startDate: String?, endDate: String?): ApiResult<List<AssetRegister>> =
        ApiResult.Ok(seed, "")
    override suspend fun detail(id: String): ApiResult<AssetRegister> =
        ApiResult.Ok(seed.first { it.id == id }, "")

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

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: data.getAsJsonArray("data") ?: JsonArray()
        else -> JsonArray()
    }

    private fun dbl(o: JsonObject, k: String): Double =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    private fun int(o: JsonObject, k: String): Int =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
    private fun str(o: JsonObject, k: String): String =
        if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""

    private fun parse(o: JsonObject) = AssetRegister(
        id = str(o, "id"),
        totalAsset = dbl(o, "total_asset"),
        creditDebt = dbl(o, "credit_debt"),
        totalBalance = dbl(o, "total_balance"),
        registerDate = str(o, "register_date").take(10),
        remark = str(o, "remark"),
    )
}
