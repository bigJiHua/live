package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.LoginLog

interface LoginLogRepository {
    suspend fun list(): ApiResult<List<LoginLog>>
}

class FakeLoginLogRepository : LoginLogRepository {
    override suspend fun list(): ApiResult<List<LoginLog>> = ApiResult.Ok(
        listOf(
            LoginLog("l1", "login", "192.168.0.103", "本机", "", "Android 14", "Chrome", "Pixel 7", 1, "", "2026-09-14 09:20:00"),
            LoginLog("l2", "login", "10.0.0.5", "未知", "", "iOS 17", "Safari", "iPhone", 0, "密码错误", "2026-09-13 22:11:00"),
        ), ""
    )
}

class RemoteLoginLogRepository(private val client: ApiClient) : LoginLogRepository {
    override suspend fun list(): ApiResult<List<LoginLog>> =
        client.get("/auth/login-logs").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; LoginLog(
                    id = str(o, "id"), type = str(o, "type"), ip = str(o, "login_ip"), location = str(o, "login_location"),
                    isp = str(o, "login_isp"), os = str(o, "os_info"), browser = str(o, "browser_info"), device = str(o, "device_model"),
                    status = runCatching { if (o.has("status") && !o.get("status").isJsonNull) o.get("status").asInt else 1 }.getOrDefault(1),
                    error = str(o, "error_message"), loginTime = str(o, "login_time").ifEmpty { str(o, "create_time") },
                ) }
        }
    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
}
