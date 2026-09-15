package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Recurring

interface RecurringRepository {
    suspend fun list(): ApiResult<List<Recurring>>
    suspend fun installments(): ApiResult<List<Recurring>>
    /** 入账某期：POST /recurring/{id}/enter {month:yyyy-MM}。 */
    suspend fun enter(id: String, month: String): ApiResult<Unit>
    /** 中止分期：POST /recurring/{id}/abort {month}。 */
    suspend fun abort(id: String, month: String): ApiResult<Unit>
}

class FakeRecurringRepository : RecurringRepository {
    private val seed = listOf(
        Recurring("rc1", "房租", 3000.0, categoryName = "居住", accountName = "招商银行", accountLast4 = "6621", cycle = "monthly", dayOfCycle = 1),
        Recurring("rc2", "宽带", 100.0, categoryName = "网络", accountName = "微信零钱", cycle = "monthly", dayOfCycle = 15),
        Recurring("rc3", "基金定投", 500.0, categoryName = "理财", accountName = "支付宝", cycle = "weekly", dayOfCycle = 3),
    )
    override suspend fun list(): ApiResult<List<Recurring>> = ApiResult.Ok(seed, "")
    override suspend fun installments(): ApiResult<List<Recurring>> = ApiResult.Ok(
        listOf(
            Recurring("in1", "手机分期", 4999.0, categoryName = "分期", accountName = "招商银行", accountLast4 = "6621", cycle = "monthly", totalPeriods = 12, enteredPeriods = 5),
            Recurring("in2", "电脑分期", 8999.0, categoryName = "分期", accountName = "花呗", cycle = "monthly", totalPeriods = 6, enteredPeriods = 6),
        ), ""
    )

    override suspend fun enter(id: String, month: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已入账")
    override suspend fun abort(id: String, month: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已中止")
}

class RemoteRecurringRepository(private val client: ApiClient) : RecurringRepository {
    override suspend fun list(): ApiResult<List<Recurring>> =
        client.get("/recurring/list").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Recurring(
                    id = str(o, "id"), name = str(o, "name"), amount = dbl(o, "amount"),
                    categoryId = str(o, "category_id"), categoryName = str(o, "category_name"),
                    accountId = str(o, "account_id"), accountName = str(o, "account_name"), accountLast4 = str(o, "account_last4"),
                    cycle = str(o, "cycle"), dayOfCycle = dbl(o, "day_of_cycle").toInt(), monthOfCycle = dbl(o, "month_of_cycle").toInt(),
                    status = str(o, "status"),
                    totalPeriods = records(o).first, enteredPeriods = records(o).second,
                ) }
        }

    override suspend fun installments(): ApiResult<List<Recurring>> =
        client.get("/recurring/installments").toResult().map { data -> parseRows(data) }

    override suspend fun enter(id: String, month: String): ApiResult<Unit> =
        client.post("/recurring/$id/enter", JsonObject().apply { addProperty("month", month) }).toResult().map { }

    override suspend fun abort(id: String, month: String): ApiResult<Unit> =
        client.post("/recurring/$id/abort", JsonObject().apply { addProperty("month", month) }).toResult().map { }

    private fun parseRows(data: JsonElement?): List<Recurring> =
        (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
            .map { el ->
                val o = el.asJsonObject
                val (total, entered) = records(o)
                Recurring(
                    id = str(o, "id"), name = str(o, "name"), amount = dbl(o, "amount"),
                    categoryName = str(o, "category_name"), accountName = str(o, "account_name"),
                    accountLast4 = str(o, "account_last4"), cycle = str(o, "cycle"),
                    dayOfCycle = dbl(o, "day_of_cycle").toInt(), status = str(o, "status"),
                    totalPeriods = total, enteredPeriods = entered,
                )
            }

    /** month_records 里统计总期数与已入账/已完成期数。 */
    private fun records(o: JsonObject): Pair<Int, Int> {
        val arr = if (o.has("month_records") && o.get("month_records").isJsonArray) o.getAsJsonArray("month_records") else JsonArray()
        var entered = 0
        for (el in arr) {
            val st = if (el.isJsonObject && el.asJsonObject.has("status") && !el.asJsonObject.get("status").isJsonNull) el.asJsonObject.get("status").asString else ""
            if (st == "entered" || st == "done") entered++
        }
        return Pair(arr.size(), entered)
    }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
