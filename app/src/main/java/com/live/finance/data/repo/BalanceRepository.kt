package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Balance

interface BalanceRepository {
    suspend fun list(): ApiResult<List<Balance>>
    /** 更新某账户余额快照：POST /accountBalance/upsert {card_id, balance}。 */
    suspend fun upsert(cardId: String, balance: Double): ApiResult<Unit>
}

class FakeBalanceRepository : BalanceRepository {
    private val seed = mutableListOf(
        Balance("xxxx", 860.0, cardType = "cash"),
        Balance("yyyy", 12580.50, cardType = "digital"),
        Balance("c1", 45200.00, cardAlias = "工资卡", cardLast4 = "0088", cardType = "debit"),
        Balance("c2", 6800.00, cardAlias = "招商储蓄卡", cardLast4 = "6621", cardType = "debit"),
        Balance("c3", -3200.00, cardAlias = "龙卡", cardLast4 = "9012", cardType = "credit"),
    )
    override suspend fun list(): ApiResult<List<Balance>> = ApiResult.Ok(seed, "")
    override suspend fun upsert(cardId: String, balance: Double): ApiResult<Unit> {
        val idx = seed.indexOfFirst { it.cardId == cardId }
        if (idx >= 0) seed[idx] = seed[idx].copy(balance = balance) else seed.add(Balance(cardId, balance))
        return ApiResult.Ok(Unit, "已更新")
    }
}

class RemoteBalanceRepository(private val client: ApiClient) : BalanceRepository {
    override suspend fun list(): ApiResult<List<Balance>> =
        client.get("/accountBalance").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Balance(
                    cardId = str(o, "card_id"), balance = dbl(o, "balance"), alias = str(o, "alias"),
                    cardAlias = str(o, "card_alias"), cardLast4 = str(o, "card_last4"), currency = str(o, "currency").ifEmpty { "CNY" },
                    cardType = str(o, "card_type"),
                ) }
        }

    override suspend fun upsert(cardId: String, balance: Double): ApiResult<Unit> =
        client.post("/accountBalance/upsert", JsonObject().apply {
            addProperty("card_id", cardId); addProperty("balance", balance)
        }).toResult().map { }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
