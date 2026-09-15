package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Fund

interface FundRepository {
    suspend fun list(): ApiResult<List<Fund>>
}

class FakeFundRepository : FundRepository {
    override suspend fun list(): ApiResult<List<Fund>> = ApiResult.Ok(
        listOf(
            Fund("f1", "易方达蓝筹精选", "天天基金", share = 3200.0, invest = 8000.0, marketVal = 9120.0, netValue = 1120.0, rate = "14.00%", buyDate = "2024-03-01"),
            Fund("f2", "沪深300ETF联接", "支付宝", share = 5000.0, invest = 12000.0, marketVal = 11400.0, netValue = -600.0, rate = "-5.00%", buyDate = "2023-11-10"),
        ), ""
    )
}

class RemoteFundRepository(private val client: ApiClient) : FundRepository {
    override suspend fun list(): ApiResult<List<Fund>> =
        client.get("/fund/list").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Fund(
                    id = str(o, "id"), fundName = str(o, "fund_name"), fundAccount = str(o, "fund_account"),
                    share = d(o, "share"), invest = d(o, "invest"), marketVal = d(o, "market_val"),
                    netValue = d(o, "net_value"), rate = str(o, "rate"), buyDate = str(o, "buy_date").take(10),
                ) }
        }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
