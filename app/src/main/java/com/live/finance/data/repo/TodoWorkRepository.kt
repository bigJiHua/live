package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Job
import com.live.finance.data.model.Todo

interface TodoRepository {
    suspend fun list(): ApiResult<List<Todo>>
    suspend fun reminders(year: Int, month: Int): ApiResult<List<Todo>>
}

class FakeTodoRepository : TodoRepository {
    private val seed = listOf(
        Todo("t1", "交房租", "账单", "2026-09-15", "待完成", "高", true),
        Todo("t2", "体检预约", "健康", "2026-09-20", "待完成", "中"),
        Todo("t3", "信用卡还款", "账单", "2026-09-25", "已完成", "高", true),
    )
    override suspend fun list(): ApiResult<List<Todo>> = ApiResult.Ok(seed, "")
    override suspend fun reminders(year: Int, month: Int): ApiResult<List<Todo>> =
        ApiResult.Ok(seed.filter { it.status == "待完成" }, "")
}

class RemoteTodoRepository(private val client: ApiClient) : TodoRepository {
    override suspend fun list(): ApiResult<List<Todo>> =
        client.get("/todo/list").toResult().map { data -> parseRows(data) }

    override suspend fun reminders(year: Int, month: Int): ApiResult<List<Todo>> =
        client.get("/todo/reminders", mapOf("days" to "3", "year" to year.toString(), "month" to month.toString()))
            .toResult().map { data -> parseRows(data) }

    private fun parseRows(data: JsonElement?): List<Todo> =
        (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
            .map { el -> val o = el.asJsonObject; Todo(
                id = str(o, "id"), content = str(o, "content"), eventType = str(o, "event_type"),
                happenDate = str(o, "happen_date").take(10), status = str(o, "status").ifEmpty { "待完成" },
                priority = str(o, "priority"),
                needRemind = runCatching { o.has("need_remind") && o.get("need_remind").asInt == 1 }.getOrDefault(false),
            ) }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
}

interface WorkRepository {
    suspend fun jobList(): ApiResult<List<Job>>
}

class FakeWorkRepository : WorkRepository {
    private val seed = listOf(
        Job("j1", "formal", "某某科技有限公司", "在职", "2022-03-01", "", 10, 18000.0, 103.4),
        Job("j2", "parttime", "兼职工作室", "离职", "2023-01-01", "2024-06-30", 15, 3000.0, 60.0),
    )
    override suspend fun jobList(): ApiResult<List<Job>> = ApiResult.Ok(seed, "")
}

class RemoteWorkRepository(private val client: ApiClient) : WorkRepository {
    override suspend fun jobList(): ApiResult<List<Job>> =
        client.get("/work/job/list").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Job(
                    id = str(o, "id"), jobType = str(o, "job_type"), company = str(o, "company"), status = str(o, "status"),
                    joinDate = str(o, "join_date").take(10), leaveDate = str(o, "leave_date").take(10),
                    payDay = dd(o, "pay_day").toInt(), baseSalary = dd(o, "base_salary"), hourlyWage = dd(o, "hourly_wage"),
                ) }
        }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
    private fun dd(o: JsonObject, k: String) = runCatching { if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asDouble else 0.0 }.getOrDefault(0.0)
}
