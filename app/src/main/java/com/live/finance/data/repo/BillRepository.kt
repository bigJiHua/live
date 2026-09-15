package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Bill

interface BillRepository {
    suspend fun list(cardId: String? = null): ApiResult<List<Bill>>
}

class FakeBillRepository : BillRepository {
    private val seed = listOf(
        Bill("bl1", "c3", "2026-09", 5, 25, 4800.0, 4800.0, 0.0, 480.0, "unpaid"),
        Bill("bl2", "c4", "2026-08", 10, 1, 1500.0, 0.0, 1500.0, 150.0, "paid"),
    )
    override suspend fun list(cardId: String?): ApiResult<List<Bill>> =
        ApiResult.Ok(seed.filter { cardId == null || it.cardId == cardId }, "")
}

class RemoteBillRepository(private val client: ApiClient) : BillRepository {
    override suspend fun list(cardId: String?): ApiResult<List<Bill>> {
        val q = buildMap { cardId?.let { put("cardId", it) } }
        return client.get("/card/bill", q).toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Bill(
                    id = str(o, "id"), cardId = str(o, "card_id"), billMonth = str(o, "bill_month"),
                    billDay = d(o, "bill_day").toInt(), repayDay = d(o, "repay_day").toInt(),
                    billAmount = d(o, "bill_amount"), needRepay = d(o, "need_repay"), repaid = d(o, "repaid"),
                    minRepay = d(o, "min_repay"), status = str(o, "status"),
                    cardAlias = str(o, "card_alias"), cardLast4 = str(o, "card_last4"),
                    annualFee = d(o, "annual_fee"), feeFreeRule = str(o, "fee_free_rule"),
                    isOverdue = d(o, "is_overdue_calc") > 0, overdueDays = d(o, "overdue_days_calc").toInt(),
                ) }
        }
    }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
