package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Pool

/**
 * 同银行共享额度池仓储（基础路径 /card/pool，表 card_credit_pool）。
 *
 * ⚠ 卡片归池必须走 `POST /card/pool/assign`（后端校验同银行 + 事务化 + 两侧/全池重算）；
 *   直接改 card 的 sharePoolId 会被后端忽略（Card.update 已从 fieldMap 移除）。
 * ⚠ 合并还款要求池 `creditReportMerged = true`。
 */
interface PoolRepository {
    suspend fun list(): ApiResult<List<Pool>>

    suspend fun create(
        bankId: String,
        bankName: String,
        totalCreditLimit: Double,
        totalTempLimit: Double,
        creditReportMerged: Boolean,
        currency: String = "CNY",
        remark: String = "",
    ): ApiResult<Unit>

    suspend fun update(
        id: String,
        bankId: String,
        bankName: String,
        totalCreditLimit: Double,
        totalTempLimit: Double,
        creditReportMerged: Boolean,
        currency: String = "CNY",
        remark: String = "",
    ): ApiResult<Unit>

    suspend fun delete(id: String): ApiResult<Unit>

    /** 卡片归入/移出池（poolId 为空 = 移出池）。 */
    suspend fun assign(cardId: String, poolId: String?): ApiResult<Unit>
}

class FakePoolRepository : PoolRepository {
    private val seed = mutableListOf(
        Pool("p1", "bk_cmb", "招商银行", 80000.0, 10000.0, true, "CNY", "", 2, listOf("c2")),
        Pool("p2", "bk_boc", "中国银行", 50000.0, 0.0, false, "CNY", "", 0, emptyList()),
    )

    override suspend fun list(): ApiResult<List<Pool>> = ApiResult.Ok(seed.toList(), "")

    override suspend fun create(
        bankId: String, bankName: String, totalCreditLimit: Double,
        totalTempLimit: Double, creditReportMerged: Boolean, currency: String, remark: String,
    ): ApiResult<Unit> {
        seed.add(0, Pool("p${System.currentTimeMillis()}", bankId, bankName, totalCreditLimit, totalTempLimit,
            creditReportMerged, currency, remark))
        return ApiResult.Ok(Unit, "已创建")
    }

    override suspend fun update(
        id: String, bankId: String, bankName: String, totalCreditLimit: Double,
        totalTempLimit: Double, creditReportMerged: Boolean, currency: String, remark: String,
    ): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i < 0) return ApiResult.Fail("共享池不存在", 404)
        seed[i] = seed[i].copy(bankId = bankId, bankName = bankName, totalCreditLimit = totalCreditLimit,
            totalTempLimit = totalTempLimit, creditReportMerged = creditReportMerged, currency = currency, remark = remark)
        return ApiResult.Ok(Unit, "已更新")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }

    override suspend fun assign(cardId: String, poolId: String?): ApiResult<Unit> = ApiResult.Ok(Unit, "已保存")
}

class RemotePoolRepository(private val client: ApiClient) : PoolRepository {
    override suspend fun list(): ApiResult<List<Pool>> =
        client.get("/card/pool").toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }

    override suspend fun create(
        bankId: String, bankName: String, totalCreditLimit: Double,
        totalTempLimit: Double, creditReportMerged: Boolean, currency: String, remark: String,
    ): ApiResult<Unit> = client.post("/card/pool", poolBody(bankId, bankName, totalCreditLimit, totalTempLimit, creditReportMerged, currency, remark)).toResult().map { }

    override suspend fun update(
        id: String, bankId: String, bankName: String, totalCreditLimit: Double,
        totalTempLimit: Double, creditReportMerged: Boolean, currency: String, remark: String,
    ): ApiResult<Unit> = client.put("/card/pool/$id", poolBody(bankId, bankName, totalCreditLimit, totalTempLimit, creditReportMerged, currency, remark)).toResult().map { }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/pool/$id").toResult().map { }

    override suspend fun assign(cardId: String, poolId: String?): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("cardId", cardId)
            if (poolId.isNullOrBlank()) add("poolId", com.google.gson.JsonNull.INSTANCE) else addProperty("poolId", poolId)
        }
        return client.post("/card/pool/assign", body).toResult().map { }
    }

    private fun poolBody(
        bankId: String, bankName: String, totalCreditLimit: Double,
        totalTempLimit: Double, creditReportMerged: Boolean, currency: String, remark: String,
    ) = JsonObject().apply {
        addProperty("bankId", bankId)
        addProperty("bankName", bankName)
        addProperty("totalCreditLimit", totalCreditLimit)
        addProperty("totalTempLimit", totalTempLimit)
        addProperty("creditReportMerged", if (creditReportMerged) 1 else 0)
        addProperty("currency", currency)
        addProperty("remark", remark)
    }

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
        else -> JsonArray()
    }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)

    private fun parse(o: JsonObject) = Pool(
        id = str(o, "id"),
        bankId = str(o, "bank_id"),
        bankName = str(o, "bank_name"),
        totalCreditLimit = d(o, "total_credit_limit"),
        totalTempLimit = d(o, "total_temp_limit"),
        creditReportMerged = d(o, "credit_report_merged") > 0,
        currency = str(o, "currency").ifEmpty { "CNY" },
        remark = str(o, "remark"),
        cardCount = d(o, "card_count").toInt(),
        cardIds = (o.get("cards") as? JsonArray)?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList(),
    )
}
