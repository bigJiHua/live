package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Pool

interface PoolRepository {
    suspend fun list(): ApiResult<List<Pool>>
}

class FakePoolRepository : PoolRepository {
    override suspend fun list(): ApiResult<List<Pool>> = ApiResult.Ok(
        listOf(
            Pool("p1", "招商银行", 80000.0, 10000.0),
            Pool("p2", "中国银行", 50000.0, 0.0),
        ), ""
    )
}

class RemotePoolRepository(private val client: ApiClient) : PoolRepository {
    override suspend fun list(): ApiResult<List<Pool>> =
        client.get("/card/pool").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Pool(
                    id = str(o, "id"), bankName = str(o, "bank_name"),
                    totalCreditLimit = d(o, "total_credit_limit"), totalTempLimit = d(o, "total_temp_limit"),
                    currency = str(o, "currency").ifEmpty { "CNY" }, remark = str(o, "remark"),
                ) }
        }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
