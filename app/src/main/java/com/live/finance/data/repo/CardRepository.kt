package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Card

/** 新增/编辑卡片入参（对齐后端 CardRules）。credit 专属字段仅信用卡需要。 */
data class NewCard(
    val cardType: String,
    val bankId: String,
    val last4No: String,
    val cardBin: String,
    val openDate: String,
    val expireDate: String,
    val alias: String = "",
    val cardLevel: String = "普卡",
    val cardOrg: String = "银联",
    val mainSub: String = "主卡",
    val currency: String = "CNY",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val creditLimit: Double = 0.0,
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val annualFee: Double = 0.0,
    val feeFreeRule: String = "",
    val sourceFrom: String = "手动",
)

interface CardRepository {
    suspend fun list(cardType: String? = null): ApiResult<List<Card>>
    suspend fun detail(id: String): ApiResult<Card>
    suspend fun createNew(p: NewCard): ApiResult<Card>
    suspend fun update(id: String, p: NewCard): ApiResult<Unit>
    /** 删除卡片 DELETE /card/:id（pinLockGuard，8303 会走 PIN 拦截重发）。 */
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeCardRepository : CardRepository {
    private val seed = mutableListOf(
        Card("c1", "debit", bankId = "bk_icbc", bankName = "中国工商银行", alias = "工资卡", last4 = "0088", cardOrg = "银联", cardLevel = "普卡", isDefault = true),
        Card("c2", "debit", bankId = "bk_cmb", bankName = "招商银行", last4 = "6621", cardOrg = "银联", cardLevel = "金卡"),
        Card("c3", "credit", bankId = "bk_ccb", bankName = "中国建设银行", alias = "龙卡", last4 = "9012", cardOrg = "银联", cardLevel = "白金卡", billDay = 5, repayDay = 25),
        Card("c4", "credit", bankId = "bk_boc", bankName = "中国银行very-long-bank-name-test", last4 = "3456", cardOrg = "VISA", cardLevel = "普卡", billDay = 10, repayDay = 1),
        Card("c5", "debit", bankId = "bk_xxx", bankName = "已隐藏的卡", alias = "隐藏账户", last4 = "0000", cardOrg = "银联", isHide = true),
    )
    override suspend fun list(cardType: String?): ApiResult<List<Card>> =
        ApiResult.Ok(seed.filter { cardType == null || it.cardType == cardType }, "")

    override suspend fun detail(id: String): ApiResult<Card> {
        val c = seed.firstOrNull { it.id == id } ?: return ApiResult.Fail("卡片不存在", 404)
        return ApiResult.Ok(c, "")
    }

    override suspend fun createNew(p: NewCard): ApiResult<Card> {
        val c = Card(
            id = "c${System.currentTimeMillis()}", cardType = p.cardType, bankId = p.bankId, bankName = p.bankId,
            alias = p.alias, last4 = p.last4No, cardBin = p.cardBin, cardOrg = p.cardOrg, cardLevel = p.cardLevel,
            status = p.status, isDefault = p.isDefault, currency = p.currency, openDate = p.openDate, expireDate = p.expireDate,
            billDay = p.billDay, repayDay = p.repayDay, creditLimit = p.creditLimit,
        )
        seed.add(0, c)
        return ApiResult.Ok(c, "创建成功")
    }

    override suspend fun update(id: String, p: NewCard): ApiResult<Unit> {
        val idx = seed.indexOfFirst { it.id == id }
        if (idx < 0) return ApiResult.Fail("卡片不存在", 404)
        seed[idx] = seed[idx].copy(
            cardType = p.cardType, bankId = p.bankId, alias = p.alias, last4 = p.last4No, cardBin = p.cardBin,
            cardOrg = p.cardOrg, cardLevel = p.cardLevel, status = p.status, isDefault = p.isDefault,
            billDay = p.billDay, repayDay = p.repayDay, creditLimit = p.creditLimit,
        )
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }
}

class RemoteCardRepository(private val client: ApiClient) : CardRepository {
    override suspend fun list(cardType: String?): ApiResult<List<Card>> {
        val q = buildMap { cardType?.let { put("cardType", it) }; put("isHide", "0") }
        return client.get("/card", q).toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }
    }

    override suspend fun detail(id: String): ApiResult<Card> =
        client.get("/card/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun createNew(p: NewCard): ApiResult<Card> =
        client.post("/card", body(p)).toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun update(id: String, p: NewCard): ApiResult<Unit> =
        client.put("/card/$id", body(p)).toResult().map { }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/$id").toResult().map { }

    private fun body(p: NewCard): JsonObject = JsonObject().apply {
        addProperty("cardType", p.cardType)
        addProperty("bankId", p.bankId)
        addProperty("last4No", p.last4No)
        addProperty("cardBin", p.cardBin)
        addProperty("openDate", p.openDate)
        addProperty("expireDate", p.expireDate)
        addProperty("alias", p.alias)
        addProperty("cardLevel", p.cardLevel)
        addProperty("cardOrg", p.cardOrg)
        addProperty("mainSub", p.mainSub)
        addProperty("currency", p.currency)
        addProperty("status", p.status)
        addProperty("isDefault", if (p.isDefault) 1 else 0)
        addProperty("sourceFrom", p.sourceFrom)
        if (p.cardType == "credit") {
            addProperty("creditLimit", p.creditLimit)
            addProperty("billDay", p.billDay)
            addProperty("repayDay", p.repayDay)
            addProperty("annualFee", p.annualFee)
            addProperty("feeFreeRule", p.feeFreeRule)
        }
    }

    private fun toArr(data: JsonElement?): JsonArray = when (data) {
        is JsonArray -> data
        is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
        else -> JsonArray()
    }

    private fun str(o: JsonObject, k: String): String =
        if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dbl(o: JsonObject, k: String): Double =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    private fun int(o: JsonObject, k: String): Int = dbl(o, k).toInt()
    private fun bool(o: JsonObject, k: String): Boolean =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt != 0 else false }.getOrDefault(false)

    private fun parse(o: JsonObject) = Card(
        id = str(o, "id"),
        cardType = str(o, "card_type").ifEmpty { "debit" },
        bankId = str(o, "bank_id"),
        bankName = str(o, "bank_name"),
        alias = str(o, "alias"),
        last4 = str(o, "last4_no"),
        cardBin = str(o, "card_bin"),
        cardOrg = str(o, "card_org"),
        cardLevel = str(o, "card_level"),
        status = str(o, "status").ifEmpty { "正常" },
        isDefault = bool(o, "is_default"),
        isHide = bool(o, "is_hide"),
        currency = str(o, "currency").ifEmpty { "CNY" },
        openDate = str(o, "open_date").take(10),
        expireDate = str(o, "expire_date").take(10),
        billDay = int(o, "bill_day"),
        repayDay = int(o, "repay_day"),
        creditLimit = dbl(o, "credit_limit"),
    )
}
