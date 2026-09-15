package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Foreign

interface ForeignRepository {
    suspend fun list(): ApiResult<List<Foreign>>
    /** 对账：PUT /card/bill/foreign/{id}/reconcile {actualRate, actualRmb, settleDate, remark}。 */
    suspend fun reconcile(id: String, actualRate: Double, actualRmb: Double, settleDate: String, remark: String): ApiResult<Unit>
}

class FakeForeignRepository : ForeignRepository {
    override suspend fun list(): ApiResult<List<Foreign>> = ApiResult.Ok(
        listOf(
            Foreign("fg1", "c4", "USD", 120.0, 715.0, 858.0, "pending", "2026-09-10 12:00:00"),
            Foreign("fg2", "c3", "JPY", 15000.0, 4.82, 723.0, "reconciled", "2026-09-05 18:00:00"),
        ), ""
    )
    override suspend fun reconcile(id: String, actualRate: Double, actualRmb: Double, settleDate: String, remark: String): ApiResult<Unit> =
        ApiResult.Ok(Unit, "已对账")
}

class RemoteForeignRepository(private val client: ApiClient) : ForeignRepository {
    override suspend fun list(): ApiResult<List<Foreign>> =
        client.get("/card/bill/foreign/list").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Foreign(
                    id = str(o, "id"), cardId = str(o, "card_id"), currency = str(o, "currency"),
                    foreignAmount = d(o, "foreign_amount"), registeredRate = d(o, "registered_rate"),
                    registeredRmb = d(o, "registered_rmb"), status = str(o, "status").ifEmpty { "pending" },
                    createTime = str(o, "create_time"),
                ) }
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
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
