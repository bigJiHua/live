package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Job
import com.live.finance.data.model.SalaryDay
import com.live.finance.data.model.SalaryFormal
import com.live.finance.data.model.SalaryMonth
import com.live.finance.data.model.SalaryMonthDay
import com.live.finance.data.model.SalaryParttime
import com.live.finance.data.model.NewTodo
import com.live.finance.data.model.Todo
import com.live.finance.data.model.TodoCalendarDay
import com.live.finance.data.model.TodoCalendarMonth

/**
 * 待办域（对齐 web `utils/api/todo.js` + `utils/api/recurring.js` 的月状态接口）：
 *  - `GET    /todo/list?happen_date=`            当日待办（后端会额外注入 `source=card_bill` 的还款提醒）
 *  - `GET    /todo/reminders?year=&month=[&days=]` 提醒（首页传 days=3；日历页按月）
 *  - `GET    /todo/calendar/month?year=&month=`   日历月视图（`days[].{date,count,hasOverdue,list[]}`）
 *  - `POST   /todo` / `PUT /todo/:id` / `DELETE /todo/:id`（删除受 **pinLockGuard** 保护 → 8303 由 pinGate 自动补令牌重发）
 *  - `PUT    /recurring/:id/month-status`         固定支出当月状态（标记已处理/待处理）
 */
interface TodoRepository {
    suspend fun list(): ApiResult<List<Todo>>

    /** 某日待办（web `getTodoList({happen_date})`）。 */
    suspend fun listByDate(date: String): ApiResult<List<Todo>>

    /** 提醒列表。`days` 非空 = 首页今日±N 天窗口；为空 = 按 [year]/[month] 整月。 */
    suspend fun reminders(year: Int, month: Int, days: Int? = null): ApiResult<List<Todo>>

    suspend fun calendarMonth(year: Int, month: Int): ApiResult<TodoCalendarMonth>

    suspend fun create(p: NewTodo): ApiResult<Unit>
    suspend fun update(id: String, p: NewTodo): ApiResult<Unit>

    /** 仅改状态（web `updateTodo(id, {status})`）。 */
    suspend fun updateStatus(id: String, status: String): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>

    /** 固定支出当月状态：`month` = `yyyy-MM`，`status` = `done` / `pending`。 */
    suspend fun updateRecurringMonthStatus(recurringId: String, month: String, status: String, amount: Double): ApiResult<Unit>
}

class FakeTodoRepository : TodoRepository {
    private val seed = mutableListOf(
        Todo("t1", "交房租", "账单", "2026-09-15", "待完成", "1", true),
        Todo("t2", "体检预约", "健康", "2026-09-20", "待完成", "2"),
        Todo("t3", "信用卡还款", "账单", "2026-09-25", "已完成", "1", true),
        Todo("t4", "生日", "生日", todayPlus(3), "待完成", "2", true),
    )

    override suspend fun list(): ApiResult<List<Todo>> = ApiResult.Ok(seed.toList(), "")
    override suspend fun listByDate(date: String): ApiResult<List<Todo>> =
        ApiResult.Ok(seed.filter { it.happenDate == date }, "")

    override suspend fun reminders(year: Int, month: Int, days: Int?): ApiResult<List<Todo>> =
        ApiResult.Ok(seed.filter { it.status == "待完成" && it.happenDate.startsWith("%04d-%02d".format(year, month)) }, "")

    override suspend fun calendarMonth(year: Int, month: Int): ApiResult<TodoCalendarMonth> {
        val prefix = "%04d-%02d".format(year, month)
        val days = seed.filter { it.happenDate.startsWith(prefix) }
            .groupBy { it.happenDate }
            .map { (d, list) -> TodoCalendarDay(date = d, count = list.size, list = list) }
        return ApiResult.Ok(TodoCalendarMonth(year = year, month = month, days = days), "")
    }

    override suspend fun create(p: NewTodo): ApiResult<Unit> {
        seed.add(
            Todo(
                id = "t${System.currentTimeMillis()}", content = p.content, eventType = p.eventType,
                happenDate = p.happenDate, priority = p.priority.toString(), needRemind = p.needRemind,
                remark = p.remark, isRecurring = p.isRecurring, remindDays = p.remindDays,
            ),
        )
        return ApiResult.Ok(Unit, "创建成功")
    }

    override suspend fun update(id: String, p: NewTodo): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) {
            seed[i] = seed[i].copy(
                content = p.content, eventType = p.eventType, priority = p.priority.toString(),
                needRemind = p.needRemind, remark = p.remark, isRecurring = p.isRecurring, remindDays = p.remindDays,
            )
        }
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "删除成功")
    }

    override suspend fun updateStatus(id: String, status: String): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(status = status)
        return ApiResult.Ok(Unit, "操作成功")
    }

    override suspend fun updateRecurringMonthStatus(recurringId: String, month: String, status: String, amount: Double): ApiResult<Unit> =
        ApiResult.Ok(Unit, "操作成功")

    private fun todayPlus(days: Long): String = java.time.LocalDate.now().plusDays(days).toString()
}

class RemoteTodoRepository(private val client: ApiClient) : TodoRepository {
    override suspend fun list(): ApiResult<List<Todo>> =
        client.get("/todo/list").toResult().map { data -> parseRows(data) }

    override suspend fun listByDate(date: String): ApiResult<List<Todo>> =
        client.get("/todo/list", mapOf("happen_date" to date)).toResult().map { data -> parseRows(data) }

    override suspend fun reminders(year: Int, month: Int, days: Int?): ApiResult<List<Todo>> {
        val q = buildMap {
            put("year", year.toString())
            put("month", month.toString())
            days?.let { put("days", it.toString()) }
        }
        return client.get("/todo/reminders", q).toResult().map { data -> parseRows(data) }
    }

    override suspend fun calendarMonth(year: Int, month: Int): ApiResult<TodoCalendarMonth> =
        client.get("/todo/calendar/month", mapOf("year" to year.toString(), "month" to month.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject ?: return@map TodoCalendarMonth()
                TodoCalendarMonth(
                    year = int(o, "year"),
                    month = int(o, "month"),
                    days = (o.get("days") as? JsonArray)?.mapNotNull { el ->
                        val d = el as? JsonObject ?: return@mapNotNull null
                        TodoCalendarDay(
                            date = str(d, "date").take(10),
                            count = int(d, "count"),
                            hasOverdue = bool(d, "hasOverdue") || bool(d, "has_overdue"),
                            list = (d.get("list") as? JsonArray)?.mapNotNull { parseTodo(it) } ?: emptyList(),
                        )
                    } ?: emptyList(),
                )
            }

    override suspend fun create(p: NewTodo): ApiResult<Unit> =
        client.post("/todo", todoBody(p)).toResult().map { }

    override suspend fun update(id: String, p: NewTodo): ApiResult<Unit> =
        client.put("/todo/$id", todoBody(p)).toResult().map { }

    override suspend fun updateStatus(id: String, status: String): ApiResult<Unit> =
        client.put("/todo/$id", JsonObject().apply { addProperty("status", status) }).toResult().map { }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/todo/$id").toResult().map { }

    override suspend fun updateRecurringMonthStatus(recurringId: String, month: String, status: String, amount: Double): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("month", month)
            addProperty("status", status)
            addProperty("amount", amount)
        }
        return client.put("/recurring/$recurringId/month-status", body).toResult().map { }
    }

    /** 新增/编辑入参（web：`need_remind`/`is_recurring` 提交 1/0）。 */
    private fun todoBody(p: NewTodo) = JsonObject().apply {
        addProperty("content", p.content)
        addProperty("happen_date", p.happenDate)
        addProperty("event_type", p.eventType)
        addProperty("priority", p.priority)
        addProperty("is_recurring", if (p.isRecurring) 1 else 0)
        addProperty("need_remind", if (p.needRemind) 1 else 0)
        addProperty("remind_days", p.remindDays)
        addProperty("remark", p.remark)
    }

    private fun parseRows(data: JsonElement?): List<Todo> =
        (when (data) {
            is JsonArray -> data
            is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
            else -> JsonArray()
        }).mapNotNull { parseTodo(it) }

    /** 单行 → [Todo]（普通待办 / recurring / card_bill 三种来源共用同一形状）。 */
    private fun parseTodo(el: JsonElement): Todo? {
        val o = el as? JsonObject ?: return null
        return Todo(
            id = str(o, "id"),
            content = str(o, "content"),
            eventType = str(o, "event_type"),
            happenDate = str(o, "happen_date").take(10),
            status = str(o, "status").ifEmpty { "待完成" },
            priority = str(o, "priority"),
            needRemind = bool(o, "need_remind"),
            source = str(o, "source"),
            categoryId = str(o, "category_id"),
            amount = dbl(o, "amount"),
            remark = str(o, "remark"),
            isRecurring = bool(o, "is_recurring"),
            recurringId = str(o, "recurring_id"),
            monthStatus = str(o, "month_status"),
            monthOverdue = bool(o, "month_overdue"),
            isVoid = bool(o, "is_void"),
            isOverdue = bool(o, "is_overdue"),
            billId = str(o, "bill_id"),
            cycle = str(o, "cycle"),
            remindDays = int(o, "remind_days"),
            remindTime = str(o, "remind_time"),
        )
    }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dbl(o: JsonObject, k: String) =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
    private fun int(o: JsonObject, k: String) = dbl(o, k).toInt()
    private fun bool(o: JsonObject, k: String): Boolean =
        runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString == "1" || o.get(k).asBoolean else false }
            .getOrDefault(false)
}

// 文件级 JSON 解析辅助（FakeWorkRepository / RemoteWorkRepository 共用）
private fun str(o: JsonObject, k: String, def: String = "") = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else def
private fun dd(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
private fun toD(e: JsonElement?): Double =
    runCatching { if (e != null && !e.isJsonNull && e.isJsonPrimitive) e.asString.toDouble() else 0.0 }.getOrDefault(0.0)
private fun obj(o: JsonObject, k: String): JsonObject? {
    val e = o.get(k); return if (e != null && !e.isJsonNull && e.isJsonObject) e.asJsonObject else null
}
private fun arr(o: JsonObject, k: String): JsonArray? {
    val e = o.get(k); return if (e != null && !e.isJsonNull && e.isJsonArray) e.asJsonArray else null
}

interface WorkRepository {
    suspend fun jobList(): ApiResult<List<Job>>
    /** 今日预估薪酬（GET /work/salary/day?work_date=，返回正式+兼职当日实收总和）。 */
    suspend fun daySalary(date: String): ApiResult<Double>
    /** 新增工作信息（POST /work/job）。 */
    suspend fun saveJob(body: JsonObject): ApiResult<Unit>
    /** 更新工作信息（PUT /work/job/:id）。 */
    suspend fun updateJob(id: String, body: JsonObject): ApiResult<Unit>
    /** 删除工作信息（DELETE /work/job/:id）。 */
    suspend fun deleteJob(id: String): ApiResult<Unit>
    /** 月度薪酬统计（GET /work/salary/month?year=&month=）。 */
    suspend fun salaryMonth(year: Int, month: Int): ApiResult<SalaryMonth>
    /** 当日薪酬明细（GET /work/salary/day?work_date=）。 */
    suspend fun salaryDay(date: String): ApiResult<SalaryDay>
    /** 保存/提交当日薪酬（POST /work/salary）。 */
    suspend fun saveSalaryDay(body: JsonObject): ApiResult<Unit>
    /** 删除当日薪酬（DELETE /work/salary）。 */
    suspend fun deleteSalaryDay(date: String): ApiResult<Unit>
}

class FakeWorkRepository : WorkRepository {
    private val jobs = mutableListOf(
        Job("j1", "formal", "某某科技有限公司", "在职", "2022-03-01", "", 10, 18000.0, 0.0,
            baseWorkDays = 22, subsidyMeal = 500.0, subsidyTraffic = 200.0, subsidyPost = 300.0,
            social = 1600.0, fund = 800.0, taxRate = 0.1),
        Job("j2", "parttime", "周末兼职工作室", "在职", "2023-01-01", "", 15, 0.0, 60.0),
    )

    override suspend fun jobList(): ApiResult<List<Job>> = ApiResult.Ok(jobs.toList(), "")

    override suspend fun daySalary(date: String): ApiResult<Double> = ApiResult.Ok(818.18 + 180.0, "")

    override suspend fun saveJob(body: JsonObject): ApiResult<Unit> {
        val id = str(body, "id")
        val job = parseJob(body)
        if (id.isNotEmpty()) {
            val idx = jobs.indexOfFirst { it.id == id }
            if (idx >= 0) jobs[idx] = job.copy(id = id) else jobs.add(job.copy(id = "j${jobs.size + 1}"))
        } else {
            jobs.add(job.copy(id = "j${jobs.size + 1}"))
        }
        return ApiResult.Ok(Unit, "保存成功")
    }

    override suspend fun updateJob(id: String, body: JsonObject): ApiResult<Unit> {
        val idx = jobs.indexOfFirst { it.id == id }
        if (idx >= 0) jobs[idx] = parseJob(body).copy(id = id)
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun deleteJob(id: String): ApiResult<Unit> {
        jobs.removeIf { it.id == id }
        return ApiResult.Ok(Unit, "删除成功")
    }

    override suspend fun salaryMonth(year: Int, month: Int): ApiResult<SalaryMonth> {
        val daily = if (month == 9) listOf(
            SalaryMonthDay("2026-09-01", 744.0, listOf(180.0, 90.0)),
            SalaryMonthDay("2026-09-03", 744.0, emptyList()),
            SalaryMonthDay("2026-09-05", 0.0, listOf(120.0)),
            SalaryMonthDay("2026-09-08", 744.0, listOf(240.0)),
        ) else emptyList()
        val formalTotal = daily.sumOf { it.formalIncome }
        val parttimeTotal = daily.sumOf { it.parttimeTotal }
        return ApiResult.Ok(
            SalaryMonth(
                formalTotal = "%.2f".format(formalTotal),
                parttimeTotal = "%.2f".format(parttimeTotal),
                totalIncome = "%.2f".format(formalTotal + parttimeTotal),
                dailyList = daily,
            ), ""
        )
    }

    override suspend fun salaryDay(date: String): ApiResult<SalaryDay> {
        val formal = SalaryFormal(
            jobId = "j1", id = "sf1", company = "某某科技有限公司", status = 1,
            daySalary = 744.0, social = 1600.0 / 22, fund = 800.0 / 22,
            tax = 74.4, cut = 0.0, income = 744.0 - 1600.0 / 22 - 800.0 / 22 - 74.4,
        )
        val parttimes = listOf(
            SalaryParttime(jobId = "j2", id = "sp1", company = "周末兼职工作室", status = 1,
                hourlyWage = 60.0, workHours = 3.0, daySalary = 180.0, subsidy = 0.0, cut = 0.0, income = 180.0),
        )
        return ApiResult.Ok(
            SalaryDay(formal = formal, parttimes = parttimes,
                totalIncome = "%.2f".format(formal.income + 180.0)), ""
        )
    }

    override suspend fun saveSalaryDay(body: JsonObject): ApiResult<Unit> = ApiResult.Ok(Unit, "保存成功")
    override suspend fun deleteSalaryDay(date: String): ApiResult<Unit> = ApiResult.Ok(Unit, "删除成功")

    private fun parseJob(o: JsonObject) = Job(
        id = str(o, "id"), jobType = str(o, "job_type", "formal"), company = str(o, "company"),
        status = str(o, "status", "在职"),
        joinDate = str(o, "join_date").take(10), leaveDate = str(o, "leave_date").take(10),
        payDay = dd(o, "pay_day").toInt(), baseSalary = dd(o, "base_salary"), hourlyWage = dd(o, "hourly_wage"),
        baseWorkDays = dd(o, "base_work_days").toInt().let { if (it <= 0) 22 else it },
        subsidyMeal = dd(o, "subsidy_meal"), subsidyTraffic = dd(o, "subsidy_traffic"),
        subsidyPost = dd(o, "subsidy_post"), social = dd(o, "social"), fund = dd(o, "fund"),
        taxRate = dd(o, "tax_rate"),
    )
}

class RemoteWorkRepository(private val client: ApiClient) : WorkRepository {
    override suspend fun jobList(): ApiResult<List<Job>> =
        client.get("/work/job/list").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> parseJob(el.asJsonObject) }
        }

    override suspend fun daySalary(date: String): ApiResult<Double> =
        client.get("/work/salary/day", mapOf("work_date" to date)).toResult().map { data ->
            val o = data as? JsonObject ?: return@map 0.0
            var total = 0.0
            obj(o, "formal")?.get("income")?.takeIf { !it.isJsonNull }?.let { total += toD(it) }
            arr(o, "parttimes")?.forEach { total += toD(it.asJsonObject.get("income")) }
            total
        }

    override suspend fun saveJob(body: JsonObject) = client.post("/work/job", body).toResult().map {}
    override suspend fun updateJob(id: String, body: JsonObject) = client.put("/work/job/$id", body).toResult().map {}
    override suspend fun deleteJob(id: String) = client.delete("/work/job/$id").toResult().map {}

    override suspend fun salaryMonth(year: Int, month: Int): ApiResult<SalaryMonth> =
        client.get("/work/salary/month", mapOf("year" to year.toString(), "month" to month.toString()))
            .toResult().map { parseSalaryMonth(it) }

    override suspend fun salaryDay(date: String): ApiResult<SalaryDay> =
        client.get("/work/salary/day", mapOf("work_date" to date)).toResult().map { parseSalaryDay(it) }

    override suspend fun saveSalaryDay(body: JsonObject) = client.post("/work/salary", body).toResult().map {}
    override suspend fun deleteSalaryDay(date: String) =
        client.delete("/work/salary", JsonObject().apply { addProperty("work_date", date) }).toResult().map {}

    private fun parseJob(o: JsonObject) = Job(
        id = str(o, "id"), jobType = str(o, "job_type", "formal"), company = str(o, "company"),
        status = str(o, "status", "在职"),
        joinDate = str(o, "join_date").take(10), leaveDate = str(o, "leave_date").take(10),
        payDay = dd(o, "pay_day").toInt(), baseSalary = dd(o, "base_salary"), hourlyWage = dd(o, "hourly_wage"),
        baseWorkDays = dd(o, "base_work_days").toInt().let { if (it <= 0) 22 else it },
        subsidyMeal = dd(o, "subsidy_meal"), subsidyTraffic = dd(o, "subsidy_traffic"),
        subsidyPost = dd(o, "subsidy_post"), social = dd(o, "social"), fund = dd(o, "fund"),
        taxRate = dd(o, "tax_rate"),
    )

    private fun parseSalaryMonth(root: JsonElement?): SalaryMonth {
        val o = root as? JsonObject ?: return SalaryMonth()
        val daily = arr(o, "daily_list")?.map { d ->
            val jo = d.asJsonObject
            val formalIncome = obj(jo, "formal")?.let { toD(it.get("income")) } ?: 0.0
            val parttimes = arr(jo, "parttimes")?.map { toD(it.asJsonObject.get("income")) } ?: emptyList()
            SalaryMonthDay(date = str(jo, "date"), formalIncome = formalIncome, parttimeIncomes = parttimes)
        } ?: emptyList()
        return SalaryMonth(
            formalTotal = str(o, "formal_total", "0.00"),
            parttimeTotal = str(o, "parttime_total", "0.00"),
            totalIncome = str(o, "total_income", "0.00"),
            dailyList = daily,
        )
    }

    private fun parseSalaryDay(root: JsonElement?): SalaryDay {
        val o = root as? JsonObject ?: return SalaryDay()
        val formal = obj(o, "formal")?.let { f ->
            SalaryFormal(
                jobId = str(f, "job_id"), id = str(f, "id"), company = str(f, "company"),
                status = dd(f, "status").toInt(), daySalary = dd(f, "day_salary"),
                social = dd(f, "social"), fund = dd(f, "fund"),
                tax = dd(f, "tax"), cut = dd(f, "cut"), income = dd(f, "income"),
            )
        }
        val parttimes = arr(o, "parttimes")?.map { p ->
            val pp = p.asJsonObject
            SalaryParttime(
                jobId = str(pp, "job_id"), id = str(pp, "id"), company = str(pp, "company"),
                status = dd(pp, "status").toInt(), hourlyWage = dd(pp, "hourly_wage"),
                workHours = dd(pp, "work_hours"), daySalary = dd(pp, "day_salary"),
                subsidy = dd(pp, "subsidy"), cut = dd(pp, "cut"), income = dd(pp, "income"),
            )
        } ?: emptyList()
        return SalaryDay(formal = formal, parttimes = parttimes, totalIncome = str(o, "total_income", "0.00"))
    }

}
