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
            LoginLog(
                "l4", "login", "203.0.113.9", "中国-上海", "电信", "Mozilla/5.0 (Android 14)",
                "Android 14", "Chrome 121", "Pixel 7", 1, "", "fp-dev-1", ts(30), ts(30),
            ),
            LoginLog(
                "l3", "refresh", "192.168.0.103", "未知", "未知", "Mozilla/5.0 (Macintosh)",
                "macOS 14", "Safari 17", "MacBook Pro", 1, "", "fp-dev-1", ts(90), ts(90),
            ),
            LoginLog(
                "l2", "login", "198.51.100.7", "中国-广东-深圳", "移动", "Mozilla/5.0 (iPhone)",
                "iOS 17", "Safari", "iPhone 15", 1, "", "fp-dev-2", ts(140), ts(140),
            ),
            LoginLog(
                "l1", "failed", "10.0.0.5", "未知", "未知", "curl/8.0", "Linux", "curl", "-", 0,
                "密码错误", "fp-dev-1", ts(600), ts(600),
            ),
        ), ""
    )

    private fun ts(minutesAgo: Long) = (System.currentTimeMillis() - minutesAgo * 60_000L).toString()
}

class RemoteLoginLogRepository(private val client: ApiClient) : LoginLogRepository {
    override suspend fun list(): ApiResult<List<LoginLog>> =
        client.get("/auth/login-logs").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; LoginLog(
                    id = str(o, "id"),
                    type = str(o, "type"),
                    loginIp = str(o, "login_ip"),
                    loginLocation = str(o, "login_location"),
                    loginIsp = str(o, "login_isp"),
                    userAgent = str(o, "user_agent"),
                    osInfo = str(o, "os_info"),
                    browserInfo = str(o, "browser_info"),
                    deviceModel = str(o, "device_model"),
                    status = runCatching { if (o.has("status") && !o.get("status").isJsonNull) o.get("status").asInt else 1 }.getOrDefault(1),
                    errorMessage = str(o, "error_message"),
                    fingerprint = str(o, "fingerprint"),
                    loginTime = str(o, "login_time"),
                    createTime = str(o, "create_time"),
                ) }
        }

    private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
}
