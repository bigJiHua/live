package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.InstallmentRow
import com.live.finance.data.model.Recurring
import com.live.finance.data.model.RecurringCatStat
import com.live.finance.data.model.RecurringMonth
import com.live.finance.data.model.RecurringSummary

interface RecurringRepository {
    /** 固定事件/固定支出列表。month 指定则按该月过滤（固定支出页）；excludeInstallment 排除分期（固定事件页）。 */
    suspend fun list(month: String? = null, excludeInstallment: Boolean = false): ApiResult<List<Recurring>>
    /** 按月汇总：GET /recurring/summary?month= */
    suspend fun summary(month: String): ApiResult<RecurringSummary>
    /** 新增：POST /recurring（body 由屏幕组包，含 name/amount/cycle/day_of_cycle/month_of_cycle/category_id/account_id/end_date/remark/is_active）。 */
    suspend fun create(body: JsonObject): ApiResult<Unit>
    /** 编辑：PUT /recurring/:id */
    suspend fun update(id: String, body: JsonObject): ApiResult<Unit>
    /** 删除：DELETE /recurring/:id */
    suspend fun delete(id: String): ApiResult<Unit>
    /** 月状态（入账/跳过/改额）：PUT /recurring/:id/month-status {month,status,amount?} */
    suspend fun updateMonthStatus(id: String, month: String, status: String, amount: Double? = null): ApiResult<Unit>

    // —— 分期相关（独立特性，保留兼容 ——
    suspend fun installments(): ApiResult<List<Recurring>>
    suspend fun enter(id: String, month: String): ApiResult<Unit>
    suspend fun abort(id: String, month: String): ApiResult<Unit>
    suspend fun installmentRows(): ApiResult<List<InstallmentRow>>
    suspend fun createInstallment(body: JsonObject): ApiResult<Unit>
    suspend fun abortMonths(id: String, months: List<String>): ApiResult<Unit>
}

class FakeRecurringRepository : RecurringRepository {
    private val seed = mutableListOf(
        Recurring(
            "rc1", "房租", 3000.0, categoryName = "居住", accountName = "招商银行", accountLast4 = "6621",
            cycle = "month", dayOfCycle = 1, endDate = "2027-12-31", remark = "房东银行卡", isActive = true,
            monthRecords = mapOf(
                "2026-09" to RecurringMonth(status = "entered", planDate = "2026-09-01", amount = 3000.0, actualAmount = 3000.0),
                "2026-10" to RecurringMonth(status = "pending", planDate = "2026-10-01", amount = 3000.0),
            ),
        ),
        Recurring(
            "rc2", "宽带", 100.0, categoryName = "网络", accountName = "微信零钱",
            cycle = "month", dayOfCycle = 15, isActive = true,
            monthRecords = mapOf("2026-09" to RecurringMonth(status = "entered", planDate = "2026-09-15", amount = 100.0, actualAmount = 100.0)),
        ),
        Recurring(
            "rc3", "基金定投", 500.0, categoryName = "理财", accountName = "支付宝",
            cycle = "week", dayOfCycle = 3, isActive = true,
            monthRecords = mapOf("2026-09" to RecurringMonth(status = "entered", planDate = "2026-09-16", amount = 500.0, actualAmount = 500.0)),
        ),
        Recurring(
            "rc4", "车险", 1200.0, categoryName = "保险", accountName = "储蓄卡",
            cycle = "year", monthOfCycle = 6, isActive = false, remark = "已停",
        ),
    )

    override suspend fun list(month: String?, excludeInstallment: Boolean): ApiResult<List<Recurring>> =
        ApiResult.Ok(seed.toList(), "")

    override suspend fun summary(month: String): ApiResult<RecurringSummary> {
        val rows = seed.filter { it.isActive }
        val totalAmount = rows.sumOf { it.amount }
        val pending = 0
        val stats = rows.groupBy { it.categoryName }.map { (cn, rs) ->
            RecurringCatStat(categoryName = cn, count = rs.size, amount = rs.sumOf { it.amount })
        }
        return ApiResult.Ok(RecurringSummary(totalAmount = totalAmount, total = rows.size, pending = pending, categoryStats = stats), "")
    }

    override suspend fun create(body: JsonObject): ApiResult<Unit> {
        seed.add(
            Recurring(
                id = "rc${seed.size + 1}", name = str(body, "name"), amount = dbl(body, "amount"),
                cycle = str(body, "cycle"), dayOfCycle = int(body, "day_of_cycle"), monthOfCycle = int(body, "month_of_cycle"),
                categoryId = str(body, "category_id"), categoryName = str(body, "category_name"),
                accountId = str(body, "account_id"), accountName = str(body, "account_name"),
                endDate = str(body, "end_date"), remark = str(body, "remark"), isActive = body.get("is_active")?.asBoolean != false,
            ),
        )
        return ApiResult.Ok(Unit, "已保存")
    }

    override suspend fun update(id: String, body: JsonObject): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) {
            val old = seed[i]
            seed[i] = old.copy(
                name = str(body, "name").ifEmpty { old.name }, amount = if (body.has("amount")) dbl(body, "amount") else old.amount,
                cycle = str(body, "cycle").ifEmpty { old.cycle }, dayOfCycle = if (body.has("day_of_cycle")) int(body, "day_of_cycle") else old.dayOfCycle,
                monthOfCycle = if (body.has("month_of_cycle")) int(body, "month_of_cycle") else old.monthOfCycle,
                categoryId = str(body, "category_id").ifEmpty { old.categoryId }, categoryName = str(body, "category_name").ifEmpty { old.categoryName },
                accountId = str(body, "account_id").ifEmpty { old.accountId }, accountName = str(body, "account_name").ifEmpty { old.accountName },
                endDate = str(body, "end_date").ifEmpty { old.endDate }, remark = str(body, "remark").ifEmpty { old.remark },
                isActive = body.get("is_active")?.asBoolean ?: old.isActive,
            )
        }
        return ApiResult.Ok(Unit, "已更新")
    }

    override suspend fun delete(id: String): ApiResult<Unit> { seed.removeIf { it.id == id }; return ApiResult.Ok(Unit, "已删除") }

    override suspend fun updateMonthStatus(id: String, month: String, status: String, amount: Double?): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) {
            val old = seed[i]
            val mr = old.monthRecords.toMutableMap()
            val cur = mr[month] ?: RecurringMonth()
            mr[month] = cur.copy(status = status, planDate = cur.planDate.ifEmpty { month + "-01" }, amount = amount ?: cur.amount, actualAmount = if (status == "entered") (amount ?: cur.amount) else cur.actualAmount)
            seed[i] = old.copy(monthRecords = mr, monthStatus = status, monthAmount = amount ?: old.monthAmount)
        }
        return ApiResult.Ok(Unit, "已更新")
    }

    // 分期兼容（桩）
    override suspend fun installments(): ApiResult<List<Recurring>> = ApiResult.Ok(
        listOf(
            Recurring("in1", "手机分期", 4999.0, categoryName = "分期", accountName = "招商银行", accountLast4 = "6621", cycle = "month", totalPeriods = 12, enteredPeriods = 5),
            Recurring("in2", "电脑分期", 8999.0, categoryName = "分期", accountName = "花呗", cycle = "month", totalPeriods = 6, enteredPeriods = 6),
        ), ""
    )
    override suspend fun enter(id: String, month: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已入账")
    override suspend fun abort(id: String, month: String): ApiResult<Unit> = ApiResult.Ok(Unit, "已中止")
    override suspend fun installmentRows(): ApiResult<List<InstallmentRow>> = ApiResult.Ok(emptyList(), "")
    override suspend fun createInstallment(body: JsonObject): ApiResult<Unit> = ApiResult.Ok(Unit, "分期创建成功")
    override suspend fun abortMonths(id: String, months: List<String>): ApiResult<Unit> = ApiResult.Ok(Unit, "已中止${months.size}期")
}

class RemoteRecurringRepository(private val client: ApiClient) : RecurringRepository {
    override suspend fun list(month: String?, excludeInstallment: Boolean): ApiResult<List<Recurring>> {
        val q = buildMap<String, String> {
            put("includeInactive", "1")
            month?.let { put("month", it) }
            if (excludeInstallment) put("excludeInstallment", "1")
        }
        return client.get("/recurring/list", q).toResult().map { data -> parseRows(data) }
    }

    override suspend fun summary(month: String): ApiResult<RecurringSummary> =
        client.get("/recurring/summary", mapOf("month" to month)).toResult().map { data ->
            val o = data as? JsonObject ?: JsonObject()
            RecurringSummary(
                totalAmount = dbl(o, "totalAmount"),
                total = int(o, "total"),
                pending = int(o, "pending"),
                categoryStats = (o.getAsJsonArray("categoryStats") ?: JsonArray()).map { e ->
                    val s = e.asJsonObject
                    RecurringCatStat(str(s, "category_id"), str(s, "category_name"), int(s, "count"), dbl(s, "amount"))
                },
            )
        }

    override suspend fun create(body: JsonObject): ApiResult<Unit> = client.post("/recurring", body).toResult().map { }
    override suspend fun update(id: String, body: JsonObject): ApiResult<Unit> = client.put("/recurring/$id", body).toResult().map { }
    override suspend fun delete(id: String): ApiResult<Unit> = client.delete("/recurring/$id").toResult().map { }
    override suspend fun updateMonthStatus(id: String, month: String, status: String, amount: Double?): ApiResult<Unit> {
        val b = JsonObject().apply { addProperty("month", month); addProperty("status", status); amount?.let { addProperty("amount", it) } }
        return client.put("/recurring/$id/month-status", b).toResult().map { }
    }

    override suspend fun installments(): ApiResult<List<Recurring>> =
        client.get("/recurring/installments").toResult().map { data -> parseRows(data) }
    override suspend fun enter(id: String, month: String): ApiResult<Unit> =
        client.post("/recurring/$id/enter", JsonObject().apply { addProperty("month", month) }).toResult().map { }
    override suspend fun abort(id: String, month: String): ApiResult<Unit> =
        client.post("/recurring/$id/abort", JsonObject().apply { addProperty("month", month) }).toResult().map { }
    override suspend fun installmentRows(): ApiResult<List<InstallmentRow>> =
        client.get("/recurring/installments").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> InstallmentRow.fromJson(el.asJsonObject) }
        }
    override suspend fun createInstallment(body: JsonObject): ApiResult<Unit> = client.post("/recurring", body).toResult().map { }
    override suspend fun abortMonths(id: String, months: List<String>): ApiResult<Unit> =
        client.post("/recurring/$id/abort", JsonObject().apply { add("months", JsonArray().apply { months.forEach { add(it) } }) }).toResult().map { }

    private fun parseRows(data: JsonElement?): List<Recurring> =
        (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
            .map { el ->
                val o = el.asJsonObject
                val (total, entered) = records(o)
                Recurring(
                    id = str(o, "id"), name = str(o, "name"), amount = dbl(o, "amount"),
                    categoryId = str(o, "category_id"), categoryName = str(o, "category_name"),
                    accountId = str(o, "account_id"), accountName = str(o, "account_name"), accountLast4 = str(o, "account_last4"),
                    cycle = str(o, "cycle"), dayOfCycle = dbl(o, "day_of_cycle").toInt(), monthOfCycle = dbl(o, "month_of_cycle").toInt(),
                    endDate = str(o, "end_date"), remark = str(o, "remark"), isActive = o.get("is_active")?.asInt != 0,
                    status = str(o, "status"), totalPeriods = total, enteredPeriods = entered,
                    monthStatus = str(o, "month_status"), monthAmount = dbl(o, "month_amount"), happenDate = str(o, "happen_date"),
                    monthRecords = monthRecords(o),
                )
            }

    /** month_records：JSON 对象，键为 yyyy-MM → {status, plan_date/occur_date, amount, actual_amount}。 */
    private fun monthRecords(o: JsonObject): Map<String, RecurringMonth> {
        val res = mutableMapOf<String, RecurringMonth>()
        val mr = o.get("month_records")
        if (mr != null && mr.isJsonObject) {
            for ((k, v) in mr.asJsonObject.entrySet()) {
                if (v.isJsonObject) {
                    val m = v.asJsonObject
                    res[k] = RecurringMonth(
                        status = str(m, "status"), planDate = str(m, "plan_date"), occurDate = str(m, "occur_date"),
                        amount = dbl(m, "amount"), actualAmount = dbl(m, "actual_amount"),
                    )
                }
            }
        }
        return res
    }

    private fun records(o: JsonObject): Pair<Int, Int> {
        val arr = if (o.has("month_records") && o.get("month_records").isJsonArray) o.getAsJsonArray("month_records") else JsonArray()
        var entered = 0
        for (el in arr) {
            val st = if (el.isJsonObject && el.asJsonObject.has("status") && !el.asJsonObject.get("status").isJsonNull) el.asJsonObject.get("status").asString else ""
            if (st == "entered" || st == "done") entered++
        }
        return Pair(arr.size(), entered)
    }
}

private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
private fun dbl(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
private fun int(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asInt else 0 }.getOrDefault(0)
