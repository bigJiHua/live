package com.live.finance.data.model

import com.google.gson.JsonObject

/** 分期单条期次状态（web InstallmentList 的 getMonthRecords 口径）。 */
data class InstallmentMonth(
    val month: String,          // yyyy-MM（期次键）
    val rawStatus: String,      // 后端原始 status（pending/entering/entered/void…）
    val status: String,         // effectiveStatus ?: rawStatus（done/entered/overdue 判定用）
    val overdue: Boolean,
    val planDate: String,       // 计划入账日 yyyy-MM-dd
    val amount: Double,
) {
    /** web tagText 原文口径。 */
    val tagText: String
        get() = when {
            rawStatus == "entering" -> "入账中"
            rawStatus == "void" -> "超过期限"
            status == "done" -> "已还"
            overdue -> "逾期未还"
            status == "entered" -> "已入账"
            else -> "待入账"
        }
}

/** `account_id` 里的 JSON 元信息（创建分期时由前端打包写入）。 */
data class InstallmentAccount(
    val cardId: String = "",
    val cardName: String = "",
    val billingDay: Int = 0,
    val startMonth: String = "",
    val enterMode: String = "next",
    val firstEnterDate: String = "",
    val originalAmount: Double = 0.0,
    val fee: Double = 0.0,
    val totalPeriods: Int = 0,
)

/**
 * 解析分期 `account_id` 字段（web `parseAccount`：JSON.parse 失败回 null）。
 * 兼容三种形态：已是对象 / JSON 字符串 / 旧版纯账户名。
 */
fun parseInstallmentAccount(raw: String?): InstallmentAccount {
    if (raw.isNullOrBlank()) return InstallmentAccount()
    val o: JsonObject = runCatching {
        com.google.gson.JsonParser.parseString(raw).let { el ->
            if (el.isJsonObject) el.asJsonObject else null
        } ?: return InstallmentAccount()
    }.getOrNull() ?: return InstallmentAccount()
    fun s(k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    fun d(k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    fun i(k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
    return InstallmentAccount(
        cardId = s("card_id"),
        cardName = s("card_name"),
        billingDay = i("billing_day"),
        startMonth = s("start_month"),
        enterMode = s("enter_mode").ifBlank { "next" },
        firstEnterDate = s("first_enter_date"),
        originalAmount = d("original_amount"),
        fee = d("fee"),
        totalPeriods = i("total_periods"),
    )
}

/** 分期列表条目（GET /recurring/installments 的行）。 */
data class InstallmentRow(
    val id: String,
    val name: String,
    val amount: Double,          // 每期标准金额
    val accountIdJson: String,   // account_id 原始串（JSON 元信息）
    val isActive: Int,           // 1 进行中 / 0 已结束（web 灰显「已结束」）
    val repeatCount: Int,
    val months: List<InstallmentMonth>,
) {
    val account: InstallmentAccount get() = parseInstallmentAccount(accountIdJson)

    companion object {
        fun fromJson(o: JsonObject): InstallmentRow {
            fun s(k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
            fun d(k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
            fun i(k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 1 }.getOrDefault(1)
            val records = LinkedHashMap<String, InstallmentMonth>()
            if (o.has("month_records") && o.get("month_records").isJsonObject) {
                val obj = o.getAsJsonObject("month_records")
                for (entry in obj.entrySet()) {
                    val m = entry.value
                    if (m == null || !m.isJsonObject) continue
                    val r = m.asJsonObject
                    fun rs(k: String) = if (r.has(k) && !r.get(k).isJsonNull) r.get(k).asString else ""
                    fun rd(k: String) = runCatching { if (r.has(k) && !r.get(k).isJsonNull) r.get(k).asDouble else 0.0 }.getOrDefault(0.0)
                    fun rb(k: String) = r.has(k) && !r.get(k).isJsonNull && r.get(k).asBoolean
                    val raw = rs("status").ifBlank { "pending" }
                    records[entry.key] = InstallmentMonth(
                        month = entry.key,
                        rawStatus = raw,
                        status = rs("effectiveStatus").ifBlank { raw },
                        overdue = rb("overdue"),
                        planDate = rs("plan_date"),
                        amount = if (r.has("amount") && !r.get("amount").isJsonNull) rd("amount") else 0.0,
                    )
                }
            }
            return InstallmentRow(
                id = s("id"),
                name = s("name"),
                amount = d("amount"),
                accountIdJson = s("account_id"),
                isActive = i("is_active"),
                repeatCount = i("repeat_count"),
                months = records.values.toList(),
            )
        }
    }
}
