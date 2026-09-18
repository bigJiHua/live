package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Foreign
import com.live.finance.data.model.ForeignHistory

/**
 * 外币消费登记/对账仓储（基础路径 /card/bill/foreign）。
 *
 * 汇率口径：**每 100 外币等值人民币**（rmb = foreignAmount * rate / 100）。
 * 业务：信用卡外币消费时后端自动登记 pending；用户对账后写实际汇率/人民币。
 * `pending`（未对账）不计入账单金额，但按登记汇率占用可用额度。
 */
interface ForeignRepository {
    /** 用户维度全部待对账（专用登记页首 tab）。 */
    suspend fun pending(): ApiResult<List<Foreign>>

    /** 用户维度全部登记记录（含已对账）。 */
    suspend fun list(): ApiResult<List<Foreign>>

    /** 历史外币消费流水（扫描账本，含未登记）。 */
    suspend fun history(cardId: String? = null, startDate: String? = null, endDate: String? = null): ApiResult<List<ForeignHistory>>

    /** 补登记未登记的历史外币消费流水。 */
    suspend fun register(accountId: String): ApiResult<Unit>

    /** 对账：PUT /card/bill/foreign/{id}/reconcile {actualRate, actualRmb?, settleDate?, remark}。 */
    suspend fun reconcile(id: String, actualRate: Double, actualRmb: Double, settleDate: String, remark: String): ApiResult<Unit>

    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeForeignRepository : ForeignRepository {
    private val seed = mutableListOf(
        Foreign("fg1", "c4", "a1", "USD", 120.0, 715.0, 858.0, 0.0, 0.0, "", "pending", "", "2026-09-10 12:00:00"),
        Foreign("fg2", "c3", "a2", "JPY", 15000.0, 4.82, 723.0, 4.85, 727.5, "2026-09-06", "reconciled", "", "2026-09-05 18:00:00"),
    )

    override suspend fun pending(): ApiResult<List<Foreign>> = ApiResult.Ok(seed.filter { it.isPending }, "")

    override suspend fun list(): ApiResult<List<Foreign>> = ApiResult.Ok(seed.toList(), "")

    override suspend fun history(cardId: String?, startDate: String?, endDate: String?): ApiResult<List<ForeignHistory>> =
        ApiResult.Ok(
            listOf(
                ForeignHistory("a1", "c4", "USD", 120.0, 715.0, "2026-09-10", "fg1", "pending", 715.0, 858.0, 0.0, ""),
                ForeignHistory("a3", "c3", "HKD", 800.0, 92.0, "2026-09-02", "", "", 0.0, 0.0, 0.0, ""),
            ), ""
        )

    override suspend fun register(accountId: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已登记")

    override suspend fun reconcile(id: String, actualRate: Double, actualRmb: Double, settleDate: String, remark: String): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(
            status = "reconciled", actualRate = actualRate,
            actualRmb = if (actualRmb > 0) actualRmb else seed[i].foreignAmount * actualRate / 100.0,
            settleDate = settleDate, remark = remark,
        )
        return ApiResult.Ok(Unit, "已对账")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }
}

class RemoteForeignRepository(private val client: ApiClient) : ForeignRepository {
    override suspend fun pending(): ApiResult<List<Foreign>> = fetchList("/card/bill/foreign/pending")

    override suspend fun list(): ApiResult<List<Foreign>> = fetchList("/card/bill/foreign/list")

    private suspend fun fetchList(path: String): ApiResult<List<Foreign>> =
        client.get(path).toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }

    override suspend fun history(cardId: String?, startDate: String?, endDate: String?): ApiResult<List<ForeignHistory>> {
        val q = buildMap<String, String> {
            cardId?.let { put("cardId", it) }
            startDate?.let { put("startDate", it) }
            endDate?.let { put("endDate", it) }
        }
        return client.get("/card/bill/foreign/history", q).toResult().map { data ->
            toArr(data).map { el ->
                val o = el.asJsonObject
                ForeignHistory(
                    accountId = str(o, "account_id"),
                    cardId = str(o, "card_id"),
                    currency = str(o, "currency"),
                    amount = d(o, "amount"),
                    exchangeRate = d(o, "exchange_rate"),
                    transDate = str(o, "trans_date").take(10),
                    regId = str(o, "reg_id"),
                    regStatus = str(o, "reg_status"),
                    registeredRate = d(o, "registered_rate"),
                    registeredRmb = d(o, "registered_rmb"),
                    actualRmb = d(o, "actual_rmb"),
                    settleDate = str(o, "settle_date").take(10),
                )
            }
        }
    }

    override suspend fun register(accountId: String): ApiResult<Unit> {
        val body = JsonObject().apply { addProperty("accountId", accountId) }
        return client.post("/card/bill/foreign/register", body).toResult().map { }
    }

    override suspend fun reconcile(id: String, actualRate: Double, actualRmb: Double, settleDate: String, remark: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("actualRate", actualRate)
            if (actualRmb > 0) addProperty("actualRmb", actualRmb)  // 留空由后端按 外币*汇率/100 反算
            if (settleDate.isNotBlank()) addProperty("settleDate", settleDate)
            addProperty("remark", remark)
        }
        return client.put("/card/bill/foreign/$id/reconcile", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/bill/foreign/$id").toResult().map { }

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
        else -> JsonArray()
    }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)

    private fun parse(o: JsonObject) = Foreign(
        id = str(o, "id"),
        cardId = str(o, "card_id"),
        accountId = str(o, "account_id"),
        currency = str(o, "currency"),
        foreignAmount = d(o, "foreign_amount"),
        registeredRate = d(o, "registered_rate"),
        registeredRmb = d(o, "registered_rmb"),
        actualRate = d(o, "actual_rate"),
        actualRmb = d(o, "actual_rmb"),
        settleDate = str(o, "settle_date").take(10),
        status = str(o, "status").ifEmpty { "pending" },
        remark = str(o, "remark"),
        createTime = str(o, "create_time"),
    )
}
