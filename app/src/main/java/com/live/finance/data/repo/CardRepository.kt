package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Card

/**
 * 新增/编辑卡片入参（对齐后端 CardRules：字段名 camelCase，见 card/model/index.js 的 create/update fieldMap）。
 *
 * ⚠ 后端不可改：`cardType` 只允许与原值相同（credit ⇄ debit 切换会抛错）；
 *    `sharePoolId` 必须走 `PUT /card/pool/assign`（PoolRepository.assign），不能直写。
 */
data class NewCard(
    val cardType: String,
    val bankId: String,
    val last4No: String,
    val cardBin: String,
    val openDate: String,
    val expireDate: String,
    val alias: String = "",
    val cardLevel: String = "普卡",
    val mainSub: String = "主卡",
    val cardOrg: String = "银联",
    val cardLength: String = "19",
    val cardImg: String = "",
    val currency: String = "CNY",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    val sort: Int = 99,
    val tag: String = "",
    val remark: String = "",
    val color: String = "#0052cc",
    val creditLimit: Double = 0.0,
    val tempLimit: Double = 0.0,
    val pointsRate: Double = 1.0,
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val annualFee: Double = 0.0,
    val feeFreeRule: String = "",
    val sourceFrom: String = "手动",
    /**
     * web `card/Add.vue` 语义：可选字段为「空」时**不提交**（`cardLevel/mainSub/cardOrg/cardLength/
     * alias/currency/status/tag/remark/color/sort` 走 truthy 判断）；
     * `card/Edit.vue` 与 `credit/AddFull.vue` 则整体提交（空串照传）。
     */
    val omitBlankOptional: Boolean = false,
    /** web `card/Edit.vue`：编辑页**不提交** `creditLimit/tempLimit/pointsRate`（额度由「额度与共享池」页负责）。 */
    val omitLimitFields: Boolean = false,
) {
    /** 转后端 JSON（对齐 web `card/Add.vue`、`card/Edit.vue`、`credit/AddFull.vue` 的提交字段集）。 */
    fun toJson(includeCardType: Boolean = true): JsonObject = JsonObject().apply {
        fun s(k: String, v: String) { addProperty(k, v) }
        fun sOpt(k: String, v: String) { if (!omitBlankOptional || v.isNotBlank()) addProperty(k, v) }
        fun d(k: String, v: Double) { addProperty(k, v) }
        fun i(k: String, v: Int) { addProperty(k, v) }
        if (includeCardType) s("cardType", cardType)
        s("bankId", bankId); s("last4No", last4No); s("cardBin", cardBin)
        s("openDate", openDate); s("expireDate", expireDate)
        sOpt("alias", alias); sOpt("cardLevel", cardLevel); sOpt("mainSub", mainSub)
        sOpt("cardOrg", cardOrg); sOpt("cardLength", cardLength); s("cardImg", cardImg)
        sOpt("currency", currency); sOpt("status", status)
        addProperty("isDefault", isDefault)
        addProperty("isHide", isHide)
        i("sort", sort)
        sOpt("tag", tag); sOpt("remark", remark); sOpt("color", color)
        // 账单日/还款日/年费/免年费：信用卡必发；借记卡的「编辑」页也会带上 0/''（Add 页不带）
        if (cardType == "credit" || !omitBlankOptional) {
            i("billDay", billDay); i("repayDay", repayDay)
            d("annualFee", annualFee); s("feeFreeRule", feeFreeRule)
        }
        // 额度三件套仅「信用卡创建/全功能录入」提交；`card/Edit.vue` 交由额度管理页负责，不带
        if (cardType == "credit" && !omitLimitFields) {
            d("creditLimit", creditLimit); d("tempLimit", tempLimit); d("pointsRate", pointsRate)
        }
        s("sourceFrom", sourceFrom)
    }
}

interface CardRepository {
    /** GET /card?cardType=&isHide=0（isHide=0 只取未隐藏卡）。 */
    suspend fun list(cardType: String? = null): ApiResult<List<Card>>

    suspend fun detail(id: String): ApiResult<Card>
    suspend fun createNew(p: NewCard): ApiResult<Card>
    suspend fun update(id: String, p: NewCard): ApiResult<Unit>

    /** PUT /card/sort `{items:[{id,sort}]}`：整列 1-N 重排后一次提交（拖拽排序）。 */
    suspend fun updateSortBatch(items: List<Pair<String, Int>>): ApiResult<Unit>

    /** 删除卡片 DELETE /card/:id（pinLockGuard，8303 会走 PIN 拦截重发）。 */
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeCardRepository : CardRepository {
    private val seed = mutableListOf(
        Card("c1", "debit", bankId = "bk_icbc", bankName = "中国工商银行", alias = "工资卡", last4 = "0088", cardBin = "622202", cardOrg = "银联", cardLevel = "普卡", cardLength = "19", color = "#c7000b", isDefault = true, openDate = "2021-03-01", expireDate = "2031-03-01"),
        Card("c2", "debit", bankId = "bk_cmb", bankName = "招商银行", last4 = "6621", cardBin = "621483", cardOrg = "银联", cardLevel = "金卡", cardLength = "19", color = "#c8102e", openDate = "2022-06-15", expireDate = "2032-06-15"),
        Card("c3", "credit", bankId = "bk_ccb", bankName = "中国建设银行", alias = "龙卡", last4 = "9012", cardBin = "436742", cardOrg = "银联", cardLevel = "白金卡", cardLength = "16", color = "#00378f", billDay = 5, repayDay = 25, creditLimit = 50000.0, tempLimit = 0.0, pointsRate = 1.0, openDate = "2020-09-01", expireDate = "2028-09-01"),
        Card("c4", "credit", bankId = "bk_boc", bankName = "中国银行", last4 = "3456", cardBin = "524865", cardOrg = "VISA", cardLevel = "普卡", cardLength = "16", color = "#8c1c13", billDay = 10, repayDay = 1, creditLimit = 20000.0, tempLimit = 5000.0, pointsRate = 1.5, openDate = "2023-01-20", expireDate = "2028-01-20"),
        Card("c5", "debit", bankId = "bk_xxx", bankName = "已隐藏的卡", alias = "隐藏账户", last4 = "0000", cardOrg = "银联", isHide = true),
    )

    override suspend fun list(cardType: String?): ApiResult<List<Card>> =
        ApiResult.Ok(seed.filter { !it.isHide && (cardType == null || it.cardType == cardType) }, "")

    override suspend fun detail(id: String): ApiResult<Card> {
        val c = seed.firstOrNull { it.id == id } ?: return ApiResult.Fail("卡片不存在", 404)
        return ApiResult.Ok(c, "")
    }

    override suspend fun createNew(p: NewCard): ApiResult<Card> {
        val c = Card(
            id = "c${System.currentTimeMillis()}", cardType = p.cardType, bankId = p.bankId, bankName = p.bankId,
            alias = p.alias, last4 = p.last4No, cardBin = p.cardBin, cardOrg = p.cardOrg, cardLevel = p.cardLevel,
            mainSub = p.mainSub, cardLength = p.cardLength, cardImg = p.cardImg, color = p.color,
            status = p.status, isDefault = p.isDefault, isHide = p.isHide, currency = p.currency,
            openDate = p.openDate, expireDate = p.expireDate, tag = p.tag, remark = p.remark,
            billDay = p.billDay, repayDay = p.repayDay, creditLimit = p.creditLimit, tempLimit = p.tempLimit,
            pointsRate = p.pointsRate, annualFee = p.annualFee, feeFreeRule = p.feeFreeRule,
        )
        seed.add(0, c)
        return ApiResult.Ok(c, "创建成功")
    }

    override suspend fun update(id: String, p: NewCard): ApiResult<Unit> {
        val idx = seed.indexOfFirst { it.id == id }
        if (idx < 0) return ApiResult.Fail("卡片不存在", 404)
        seed[idx] = seed[idx].copy(
            bankId = p.bankId, alias = p.alias, last4 = p.last4No, cardBin = p.cardBin, cardOrg = p.cardOrg,
            cardLevel = p.cardLevel, mainSub = p.mainSub, cardLength = p.cardLength, cardImg = p.cardImg,
            color = p.color, status = p.status, isDefault = p.isDefault, isHide = p.isHide, currency = p.currency,
            openDate = p.openDate, expireDate = p.expireDate, tag = p.tag, remark = p.remark,
            billDay = p.billDay, repayDay = p.repayDay, creditLimit = p.creditLimit, tempLimit = p.tempLimit,
            pointsRate = p.pointsRate, annualFee = p.annualFee, feeFreeRule = p.feeFreeRule,
        )
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun updateSortBatch(items: List<Pair<String, Int>>): ApiResult<Unit> {
        items.forEach { (id, sort) ->
            val idx = seed.indexOfFirst { it.id == id }
            if (idx >= 0) seed[idx] = seed[idx].copy(sort = sort)
        }
        return ApiResult.Ok(Unit, "排序已保存")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "已删除")
    }
}

class RemoteCardRepository(private val client: ApiClient) : CardRepository {
    override suspend fun list(cardType: String?): ApiResult<List<Card>> {
        val q = buildMap<String, String> {
            cardType?.let { put("cardType", it) }
            put("isHide", "0")
        }
        return client.get("/card", q).toResult().map { data -> toArr(data).map { parse(it.asJsonObject) } }
    }

    override suspend fun detail(id: String): ApiResult<Card> =
        client.get("/card/$id").toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun createNew(p: NewCard): ApiResult<Card> =
        client.post("/card", p.toJson()).toResult().map { parse((it as? JsonObject) ?: JsonObject()) }

    override suspend fun update(id: String, p: NewCard): ApiResult<Unit> =
        client.put("/card/$id", p.toJson()).toResult().map { }

    override suspend fun updateSortBatch(items: List<Pair<String, Int>>): ApiResult<Unit> {
        val arr = JsonArray()
        items.forEach { (id, sort) ->
            arr.add(JsonObject().apply { addProperty("id", id); addProperty("sort", sort) })
        }
        val body = JsonObject().apply { add("items", arr) }
        return client.put("/card/sort", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/card/$id").toResult().map { }

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
        mainSub = str(o, "main_sub").ifEmpty { "主卡" },
        cardLength = str(o, "card_length").ifEmpty { "19" },
        cardImg = str(o, "card_img"),
        color = str(o, "color"),
        currency = str(o, "currency").ifEmpty { "CNY" },
        status = str(o, "status").ifEmpty { "正常" },
        isDefault = bool(o, "is_default"),
        isHide = bool(o, "is_hide"),
        sort = int(o, "sort").takeIf { it > 0 } ?: 99,
        tag = str(o, "tag"),
        remark = str(o, "remark"),
        sourceFrom = str(o, "source_from"),
        openDate = str(o, "open_date").take(10),
        expireDate = str(o, "expire_date").take(10),
        billDay = int(o, "bill_day"),
        repayDay = int(o, "repay_day"),
        creditLimit = dbl(o, "credit_limit"),
        tempLimit = dbl(o, "temp_limit"),
        pointsRate = dbl(o, "points_rate").takeIf { it > 0 } ?: 1.0,
        sharePoolId = str(o, "share_pool_id"),
        annualFee = dbl(o, "annual_fee"),
        feeFreeRule = str(o, "fee_free_rule"),
    )
}
