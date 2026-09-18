package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Fund
import com.live.finance.data.model.FundHistory
import com.live.finance.data.model.FundHistoryPage
import com.live.finance.data.model.FundHistoryRange
import com.live.finance.data.model.FundMonthlyHistory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface FundRepository {
    suspend fun list(): ApiResult<List<Fund>>
    suspend fun get(id: String): ApiResult<Fund>
    suspend fun create(body: JsonObject): ApiResult<Unit>
    suspend fun update(id: String, body: JsonObject): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>

    suspend fun historyMonthly(id: String, year: Int, month: Int): ApiResult<FundMonthlyHistory>
    suspend fun history(id: String, params: Map<String, String>): ApiResult<FundHistoryPage>
    suspend fun addHistory(fundId: String, body: JsonObject): ApiResult<Unit>
    suspend fun updateHistory(historyId: String, body: JsonObject): ApiResult<Unit>
    suspend fun deleteHistory(historyId: String): ApiResult<Unit>
}

private fun str(o: JsonObject, k: String): String =
    if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""

private fun d(o: JsonObject, k: String): Double =
    runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)

private fun parseFund(o: JsonObject): Fund = Fund(
    id = str(o, "id"),
    fundName = str(o, "fund_name"),
    fundCompany = str(o, "fund_company"),
    sellOrg = str(o, "sell_org"),
    tradeAccount = str(o, "trade_account"),
    fundAccount = str(o, "fund_account"),
    share = d(o, "share"),
    invest = d(o, "invest"),
    marketVal = d(o, "market_val"),
    netValue = d(o, "net_value"),
    rate = str(o, "rate"),
    buyDate = str(o, "buy_date").take(10),
    baseInvest = d(o, "base_invest"),
    baseNetValue = d(o, "base_net_value"),
    baseMarketVal = d(o, "base_market_val"),
    capitalDelta = d(o, "capital_delta"),
)

private fun parseHistory(o: JsonObject): FundHistory = FundHistory(
    id = str(o, "id"),
    fundId = str(o, "fund_id"),
    netValue = d(o, "net_value"),
    marketVal = d(o, "market_val"),
    recordDate = str(o, "record_date").take(10),
    createTime = str(o, "create_time"),
)

class FakeFundRepository : FundRepository {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val funds = mutableListOf(
        Fund(
            "f1", "易方达蓝筹精选", fundCompany = "易方达基金", sellOrg = "天天基金",
            share = 3200.0, invest = 8000.0, marketVal = 9120.0, netValue = 1120.0, rate = "14.00%",
            buyDate = LocalDate.now().minusDays(44).format(fmt), baseInvest = 8000.0,
        ),
        Fund(
            "f2", "沪深300ETF联接", fundCompany = "华夏基金", sellOrg = "支付宝",
            share = 5000.0, invest = 12000.0, marketVal = 11400.0, netValue = -600.0, rate = "-5.00%",
            buyDate = LocalDate.now().minusDays(44).format(fmt), baseInvest = 12000.0,
        ),
    )
    private val histories = mutableMapOf<String, MutableList<FundHistory>>()

    init { funds.forEach { seed(it.id, it.buyDate) } }

    private fun seed(id: String, buyDate: String) {
        if (histories.containsKey(id)) return
        val start = runCatching { LocalDate.parse(buyDate, fmt) }.getOrDefault(LocalDate.now().minusDays(44))
        val today = LocalDate.now()
        val list = mutableListOf<FundHistory>()
        var i = 0
        var d = start
        while (!d.isAfter(today)) {
            val nv = (((i * 7) % 13) - 6) * 0.13
            val mv = if (i % 15 == 0) 1000.0 else 0.0
            list.add(FundHistory("h_${id}_$i", id, nv, mv, d.format(fmt), "${d}T08:00:00.000"))
            d = d.plusDays(1)
            i++
        }
        histories[id] = list
    }

    private fun findListByHistoryId(hid: String): MutableList<FundHistory>? =
        histories.values.firstOrNull { it.any { h -> h.id == hid } }

    override suspend fun list(): ApiResult<List<Fund>> = ApiResult.Ok(funds.toList(), "")
    override suspend fun get(id: String): ApiResult<Fund> {
        val f = funds.firstOrNull { it.id == id }
        return if (f != null) ApiResult.Ok(f, "") else ApiResult.Fail("基金不存在")
    }

    override suspend fun create(body: JsonObject): ApiResult<Unit> {
        val id = "f_${System.currentTimeMillis()}"
        val buy = body["buyDate"]?.asString ?: ""
        funds.add(
            Fund(
                id, str(body, "fundName"), fundCompany = str(body, "fundCompany"),
                sellOrg = str(body, "sellOrg"), fundAccount = str(body, "fundAccount"),
                share = d(body, "share"), invest = d(body, "invest"),
                marketVal = d(body, "marketVal"), netValue = d(body, "netValue"),
                rate = str(body, "rate"), buyDate = buy.take(10), baseInvest = d(body, "invest"),
            ),
        )
        seed(id, buy.take(10))
        return ApiResult.Ok(Unit, "")
    }

    override suspend fun update(id: String, body: JsonObject): ApiResult<Unit> {
        val idx = funds.indexOfFirst { it.id == id }
        if (idx < 0) return ApiResult.Fail("基金不存在")
        val cur = funds[idx]
        funds[idx] = cur.copy(
            fundName = if (body.has("fundName")) str(body, "fundName") else cur.fundName,
            fundCompany = if (body.has("fundCompany")) str(body, "fundCompany") else cur.fundCompany,
            sellOrg = if (body.has("sellOrg")) str(body, "sellOrg") else cur.sellOrg,
            fundAccount = if (body.has("fundAccount")) str(body, "fundAccount") else cur.fundAccount,
            share = if (body.has("share")) d(body, "share") else cur.share,
            invest = if (body.has("invest")) d(body, "invest") else cur.invest,
            marketVal = if (body.has("marketVal")) d(body, "marketVal") else cur.marketVal,
            netValue = if (body.has("netValue")) d(body, "netValue") else cur.netValue,
            rate = if (body.has("rate")) str(body, "rate") else cur.rate,
            buyDate = if (body.has("buyDate")) str(body, "buyDate").take(10) else cur.buyDate,
            baseInvest = if (body.has("invest")) d(body, "invest") else cur.baseInvest,
        )
        return ApiResult.Ok(Unit, "")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        funds.removeIf { it.id == id }
        histories.remove(id)
        return ApiResult.Ok(Unit, "")
    }

    override suspend fun historyMonthly(id: String, year: Int, month: Int): ApiResult<FundMonthlyHistory> {
        val prefix = String.format("%04d-%02d", year, month)
        val recs = histories[id].orEmpty().filter { it.recordDate.startsWith(prefix) }
        val latest = histories[id].orEmpty().maxByOrNull { it.recordDate }?.recordDate ?: ""
        return ApiResult.Ok(FundMonthlyHistory(recs, latest), "")
    }

    override suspend fun history(id: String, params: Map<String, String>): ApiResult<FundHistoryPage> {
        val all = histories[id].orEmpty().sortedBy { it.recordDate }
        val start = params["startDate"]
        val end = params["endDate"]
        val filtered = all.filter { (start == null || it.recordDate >= start) && (end == null || it.recordDate <= end) }
        val limit = params["limit"]?.toIntOrNull() ?: 400
        val taken = if (filtered.size > limit) filtered.takeLast(limit) else filtered
        return ApiResult.Ok(FundHistoryPage(taken, FundHistoryRange(start, end)), "")
    }

    override suspend fun addHistory(fundId: String, body: JsonObject): ApiResult<Unit> {
        val date = body["recordDate"]?.asString ?: return ApiResult.Fail("日期不能为空")
        val nv = body["netValue"]?.asString?.toDoubleOrNull() ?: 0.0
        val mv = body["marketVal"]?.asString?.toDoubleOrNull() ?: 0.0
        val list = histories.getOrPut(fundId) { mutableListOf() }
        list.removeIf { it.recordDate == date }
        list.add(FundHistory("h_${fundId}_${System.currentTimeMillis()}", fundId, nv, mv, date.take(10), "${date.take(10)}T08:00:00.000"))
        return ApiResult.Ok(Unit, "")
    }

    override suspend fun updateHistory(historyId: String, body: JsonObject): ApiResult<Unit> {
        val list = findListByHistoryId(historyId) ?: return ApiResult.Fail("记录不存在")
        val idx = list.indexOfFirst { it.id == historyId }
        if (idx < 0) return ApiResult.Fail("记录不存在")
        val cur = list[idx]
        list[idx] = cur.copy(
            netValue = if (body.has("netValue")) body["netValue"]?.asString?.toDoubleOrNull() ?: cur.netValue else cur.netValue,
            marketVal = if (body.has("marketVal")) body["marketVal"]?.asString?.toDoubleOrNull() ?: cur.marketVal else cur.marketVal,
        )
        return ApiResult.Ok(Unit, "")
    }

    override suspend fun deleteHistory(historyId: String): ApiResult<Unit> {
        val list = findListByHistoryId(historyId) ?: return ApiResult.Fail("记录不存在")
        list.removeIf { it.id == historyId }
        return ApiResult.Ok(Unit, "")
    }
}

class RemoteFundRepository(private val client: ApiClient) : FundRepository {
    override suspend fun list(): ApiResult<List<Fund>> =
        client.get("/fund/list").toResult().map { data ->
            (when (data) {
                is JsonArray -> data
                is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
                else -> JsonArray()
            }).map { el -> parseFund(el.asJsonObject) }
        }

    override suspend fun get(id: String): ApiResult<Fund> =
        client.get("/fund/$id").toResult().map { data -> parseFund(data as JsonObject) }

    override suspend fun create(body: JsonObject): ApiResult<Unit> =
        client.post("/fund", body).toResult().map {}

    override suspend fun update(id: String, body: JsonObject): ApiResult<Unit> =
        client.put("/fund/$id", body).toResult().map {}

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/fund/$id").toResult().map {}

    override suspend fun historyMonthly(id: String, year: Int, month: Int): ApiResult<FundMonthlyHistory> =
        client.get("/fund/$id/history/monthly", mapOf("year" to year.toString(), "month" to month.toString()))
            .toResult().map { data ->
                val o = data as JsonObject
                val recs = (o.getAsJsonArray("records") ?: JsonArray()).map { parseHistory(it.asJsonObject) }
                FundMonthlyHistory(
                    records = recs,
                    latestRecordDate = str(o, "latestRecordDate"),
                    beforeProfit = d(o, "beforeProfit"),
                    beforeCapital = d(o, "beforeCapital"),
                )
            }

    override suspend fun history(id: String, params: Map<String, String>): ApiResult<FundHistoryPage> =
        client.get("/fund/$id/history", params).toResult().map { data ->
            val o = data as JsonObject
            val recs = (o.getAsJsonArray("list") ?: JsonArray()).map { parseHistory(it.asJsonObject) }
            val r = o.getAsJsonObject("range")
            FundHistoryPage(
                list = recs,
                range = if (r != null) FundHistoryRange(
                    startDate = if (r.has("startDate") && !r.get("startDate").isJsonNull) r.get("startDate").asString else null,
                    endDate = if (r.has("endDate") && !r.get("endDate").isJsonNull) r.get("endDate").asString else null,
                    profitBefore = d(r, "profitBefore"),
                    capitalBefore = d(r, "capitalBefore"),
                ) else FundHistoryRange(),
            )
        }

    override suspend fun addHistory(fundId: String, body: JsonObject): ApiResult<Unit> =
        client.post("/fund/$fundId/history", body).toResult().map {}

    override suspend fun updateHistory(historyId: String, body: JsonObject): ApiResult<Unit> =
        client.put("/fund/history/$historyId", body).toResult().map {}

    override suspend fun deleteHistory(historyId: String): ApiResult<Unit> =
        client.delete("/fund/history/$historyId").toResult().map {}
}
