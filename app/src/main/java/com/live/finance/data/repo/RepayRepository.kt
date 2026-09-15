package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Repay

interface RepayRepository {
    suspend fun list(cardId: String? = null): ApiResult<List<Repay>>
}

class FakeRepayRepository : RepayRepository {
    private val seed = listOf(
        Repay("r1", "c3", "2026-08", 3200.0, "余额", "2026-08-25 10:00:00"),
        Repay("r2", "c4", "2026-08", 1500.0, "储蓄卡", "2026-08-01 18:30:00"),
    )
    override suspend fun list(cardId: String?): ApiResult<List<Repay>> =
        ApiResult.Ok(seed.filter { cardId == null || it.cardId == cardId }, "")
}

class RemoteRepayRepository(private val client: ApiClient) : RepayRepository {
    override suspend fun list(cardId: String?): ApiResult<List<Repay>> {
        val q = buildMap { cardId?.let { put("cardId", it) } }
        return client.get("/card/repay", q).toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Repay(
                    id = str(o, "id"), cardId = str(o, "card_id"), billMonth = str(o, "bill_month"),
                    amount = dbl(o, "repay_amount"), method = str(o, "repay_method"), time = str(o, "repay_time"),
                ) }
        }
    }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
