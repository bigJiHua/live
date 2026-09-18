package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Bill

/**
 * 信用卡账单仓储（基础路径 /card/bill）。
 *
 * ⚠ 后端 `CardBill.update` 只允许 `remindSwitch` / `remindDays` 两个字段（其余由 CreditCore 重算）。
 * ⚠ 额度一律由后端 CreditCore 计算（唯一真相源），前端不可写。
 */
interface BillRepository {
    suspend fun list(cardId: String? = null, billMonth: String? = null): ApiResult<List<Bill>>
    suspend fun detail(id: String): ApiResult<Bill>
    /** POST /card/bill/card/:cardId/rebuild —— 重算该卡全部账单快照。 */
    suspend fun rebuild(cardId: String): ApiResult<Unit>
    /** PUT /card/bill/:id —— 仅提醒开关/提前天数。 */
    suspend fun updateRemind(id: String, remindSwitch: Boolean, remindDays: Int): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeBillRepository : BillRepository {
    private val seed = mutableListOf(
        Bill("bl1", "c3", "2026-09", 5, 25, creditLimit = 50000.0, availLimit = 45200.0, usedLimit = 4800.0,
            billAmount = 4800.0, needRepay = 4800.0, repaid = 0.0, minRepay = 480.0,
            cardAlias = "龙卡", cardLast4 = "9012", billStartDate = "2026-08-06", billEndDate = "2026-09-05"),
        Bill("bl2", "c4", "2026-08", 10, 1, creditLimit = 20000.0, availLimit = 18500.0, usedLimit = 1500.0,
            billAmount = 1500.0, needRepay = 0.0, repaid = 1500.0, minRepay = 150.0,
            cardAlias = "", cardLast4 = "3456", billStartDate = "2026-07-11", billEndDate = "2026-08-10"),
    )

    override suspend fun list(cardId: String?, billMonth: String?): ApiResult<List<Bill>> = ApiResult.Ok(
        seed.filter { (cardId == null || it.cardId == cardId) && (billMonth == null || it.billMonth == billMonth) }, ""
    )

    override suspend fun detail(id: String): ApiResult<Bill> {
        val b = seed.firstOrNull { it.id == id } ?: return ApiResult.Fail("账单不存在", 404)
        return ApiResult.Ok(b, "")
    }

    override suspend fun rebuild(cardId: String): ApiResult<Unit> = ApiResult.Ok(Unit, "账单已刷新")

    override suspend fun updateRemind(id: String, remindSwitch: Boolean, remindDays: Int): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(remindSwitch = remindSwitch, remindDays = remindDays)
        return ApiResult.Ok(Unit, "已保存")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }
}

class RemoteBillRepository(private val client: ApiClient) : BillRepository {
    override suspend fun list(cardId: String?, billMonth: String?): ApiResult<List<Bill>> {
        val q = buildMap<String, String> {
            cardId?.let { put("cardId", it) }
            billMonth?.let { put("billMonth", it) }
        }
        return client.get("/card/bill", q).toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }
    }

    override suspend fun detail(id: String): ApiResult<Bill> =
        client.get("/card/bill/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun rebuild(cardId: String): ApiResult<Unit> =
        client.post("/card/bill/card/$cardId/rebuild", JsonObject()).toResult().map { }

    override suspend fun updateRemind(id: String, remindSwitch: Boolean, remindDays: Int): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("remindSwitch", remindSwitch)
            addProperty("remindDays", remindDays)
        }
        return client.put("/card/bill/$id", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/bill/$id").toResult().map { }

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
        else -> JsonArray()
    }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    private fun bool(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt != 0 else false }.getOrDefault(false)

    private fun parse(o: JsonObject) = Bill(
        id = str(o, "id"),
        cardId = str(o, "card_id"),
        billMonth = str(o, "bill_month"),
        billDay = d(o, "bill_day").toInt(),
        repayDay = d(o, "repay_day").toInt(),
        creditLimit = d(o, "credit_limit"),
        availLimit = d(o, "avail_limit"),
        usedLimit = d(o, "used_limit"),
        tempLimit = d(o, "temp_limit"),
        billStartDate = str(o, "bill_start_date").take(10),
        billEndDate = str(o, "bill_end_date").take(10),
        billAmount = d(o, "bill_amount"),
        minRepay = d(o, "min_repay"),
        repaid = d(o, "repaid"),
        needRepay = d(o, "need_repay"),
        points = d(o, "points"),
        pointsRate = d(o, "points_rate").takeIf { it > 0 } ?: 1.0,
        remindSwitch = if (o.has("remind_switch")) bool(o, "remind_switch") else true,
        remindDays = d(o, "remind_days").toInt().takeIf { it > 0 } ?: 3,
        cardAlias = str(o, "card_alias"),
        cardLast4 = str(o, "card_last4"),
        currency = str(o, "currency").ifEmpty { "CNY" },
        annualFee = d(o, "annual_fee"),
        feeFreeRule = str(o, "fee_free_rule"),
        isOverdue = d(o, "is_overdue_calc") > 0,
        overdueDays = d(o, "overdue_days_calc").toInt(),
        repayStatus = str(o, "repay_status"),
        pointsExpire = str(o, "points_expire").take(10),
    )
}
