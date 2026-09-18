package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Repay

/**
 * 信用卡还款仓储（基础路径 /card/repay）。
 *
 * 契约（见 api/src/modules/card/model/repay.js）：
 * - `POST /card/repay` body `{cardId,billId,repayAmount,repayMethod,repayTime,[repayMethodCardId],[billMonth],[remark]}`
 * - `POST /card/repay/merge`（信报合一合并还款）body `{poolId,repayMethod,repayTime,[repayMethodCardId],[remark]}`
 *   —— 金额由后端按池内全部卡待还总额结清，前端不传金额。
 * - `repayMethod` 仅 `balance`/`bank_card`/`cash`（`card` 已废弃，传了报错）。
 * - 撤销：`POST /account/credit/:accountId/reverse/repay`（按来源流水 account_id，非 repay id）。
 */
interface RepayRepository {
    suspend fun list(cardId: String? = null, billMonth: String? = null, billId: String? = null): ApiResult<List<Repay>>
    suspend fun detail(id: String): ApiResult<Repay>

    suspend fun create(
        cardId: String,
        billId: String?,
        repayAmount: Double,
        repayMethod: String,
        repayTime: String,
        repayMethodCardId: String? = null,
        billMonth: String? = null,
        remark: String = "",
    ): ApiResult<Unit>

    suspend fun merge(
        poolId: String,
        repayMethod: String,
        repayTime: String,
        repayMethodCardId: String? = null,
        remark: String = "",
    ): ApiResult<Unit>

    suspend fun update(id: String, repayAmount: Double, repayMethod: String, repayTime: String, remark: String): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>

    /** 还款撤销：按来源支出流水 accountId 冲正。 */
    suspend fun reverse(accountId: String): ApiResult<Unit>
}

class FakeRepayRepository : RepayRepository {
    private val seed = mutableListOf(
        Repay("r1", "c3", "bl1", "2026-09", 3200.0, "balance", "", "2026-08-25", "", "acc_1", "龙卡", "9012", 4800.0, 4800.0),
        Repay("r2", "c4", "bl2", "2026-08", 1500.0, "bank_card", "", "2026-08-01", "", "acc_2", "", "3456", 1500.0, 0.0),
    )

    override suspend fun list(cardId: String?, billMonth: String?, billId: String?): ApiResult<List<Repay>> =
        ApiResult.Ok(seed.filter {
            (cardId == null || it.cardId == cardId) &&
                (billMonth == null || it.billMonth == billMonth) &&
                (billId == null || it.billId == billId)
        }, "")

    override suspend fun detail(id: String): ApiResult<Repay> {
        val r = seed.firstOrNull { it.id == id } ?: return ApiResult.Fail("还款记录不存在", 404)
        return ApiResult.Ok(r, "")
    }

    override suspend fun create(
        cardId: String, billId: String?, repayAmount: Double, repayMethod: String,
        repayTime: String, repayMethodCardId: String?, billMonth: String?, remark: String,
    ): ApiResult<Unit> {
        seed.add(0, Repay("r${System.currentTimeMillis()}", cardId, billId ?: "", billMonth ?: "",
            repayAmount, repayMethod, repayMethodCardId ?: "", repayTime, remark, "acc_new", "", "", 0.0, 0.0))
        return ApiResult.Ok(Unit, "还款成功")
    }

    override suspend fun merge(poolId: String, repayMethod: String, repayTime: String, repayMethodCardId: String?, remark: String): ApiResult<Unit> =
        ApiResult.Ok(Unit, "合并还款成功")

    override suspend fun update(id: String, repayAmount: Double, repayMethod: String, repayTime: String, remark: String): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i < 0) return ApiResult.Fail("还款记录不存在", 404)
        seed[i] = seed[i].copy(amount = repayAmount, method = repayMethod, time = repayTime, remark = remark)
        return ApiResult.Ok(Unit, "已更新")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }

    override suspend fun reverse(accountId: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已撤销")
}

class RemoteRepayRepository(private val client: ApiClient) : RepayRepository {
    override suspend fun list(cardId: String?, billMonth: String?, billId: String?): ApiResult<List<Repay>> {
        val q = buildMap<String, String> {
            cardId?.let { put("cardId", it) }
            billMonth?.let { put("billMonth", it) }
            billId?.let { put("billId", it) }
        }
        return client.get("/card/repay", q).toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }
    }

    override suspend fun detail(id: String): ApiResult<Repay> =
        client.get("/card/repay/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun create(
        cardId: String, billId: String?, repayAmount: Double, repayMethod: String,
        repayTime: String, repayMethodCardId: String?, billMonth: String?, remark: String,
    ): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("cardId", cardId)
            billId?.let { addProperty("billId", it) }
            addProperty("repayAmount", repayAmount)
            addProperty("repayMethod", repayMethod)
            addProperty("repayTime", repayTime)
            repayMethodCardId?.let { addProperty("repayMethodCardId", it) }
            billMonth?.let { addProperty("billMonth", it) }
            if (remark.isNotBlank()) addProperty("remark", remark)
        }
        return client.post("/card/repay", body).toResult().map { }
    }

    override suspend fun merge(poolId: String, repayMethod: String, repayTime: String, repayMethodCardId: String?, remark: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("poolId", poolId)
            addProperty("repayMethod", repayMethod)
            addProperty("repayTime", repayTime)
            repayMethodCardId?.let { addProperty("repayMethodCardId", it) }
            if (remark.isNotBlank()) addProperty("remark", remark)
        }
        return client.post("/card/repay/merge", body).toResult().map { }
    }

    override suspend fun update(id: String, repayAmount: Double, repayMethod: String, repayTime: String, remark: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("repayAmount", repayAmount)
            addProperty("repayMethod", repayMethod)
            addProperty("repayTime", repayTime)
            addProperty("remark", remark)
        }
        return client.put("/card/repay/$id", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/repay/$id").toResult().map { }

    override suspend fun reverse(accountId: String): ApiResult<Unit> =
        client.post("/account/credit/$accountId/reverse/repay", JsonObject()).toResult().map { }

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
        else -> JsonArray()
    }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun d(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)

    private fun parse(o: JsonObject) = Repay(
        id = str(o, "id"),
        cardId = str(o, "card_id"),
        billId = str(o, "bill_id"),
        billMonth = str(o, "bill_month"),
        amount = d(o, "repay_amount"),
        method = str(o, "repay_method"),
        repayCardId = str(o, "repay_card_id"),
        time = str(o, "repay_time"),
        remark = str(o, "remark"),
        accountId = str(o, "account_id"),
        cardAlias = str(o, "card_alias"),
        cardLast4 = str(o, "card_last4"),
        billAmount = d(o, "bill_amount"),
        billNeedRepay = d(o, "bill_need_repay"),
        repayCardAlias = str(o, "repay_card_alias"),
        repayCardLast4 = str(o, "repay_card_last4"),
    )
}
