package com.live.finance.data.model

/**
 * 登录日志（对应 `GET /auth/login-logs` 行）。
 *
 * 后端实际 SELECT 的列（`api/src/modules/auth/controller/index.js`）：
 * `id, type, login_ip, login_location, login_isp, user_agent, os_info, browser_info,
 * device_model, status, error_message, login_time, create_time`。
 * ⚠ 表里虽有 `fingerprint` 列，但**该查询没有 SELECT 它** → 线上恒为空串；
 *   web `LoginLog.vue` 的「新设备登录」判定因此**永不触发**（照抄 web 行为，字段仍解析备用）。
 * ⚠ `login_time/create_time` 是**毫秒时间戳字符串**。
 */
data class LoginLog(
    val id: String,
    val type: String = "",              // login / logout / refresh / failed
    val loginIp: String = "",
    val loginLocation: String = "",
    val loginIsp: String = "",
    val userAgent: String = "",
    val osInfo: String = "",
    val browserInfo: String = "",
    val deviceModel: String = "",
    val status: Int = 1,                // 1 成功 / 0 失败
    val errorMessage: String = "",
    val fingerprint: String = "",       // 后端当前不返回，恒空
    val loginTime: String = "",
    val createTime: String = "",
) {
    /** web 判定失败用的是 `log.status === 0`（不是 `!== 1`）。 */
    val failed: Boolean get() = status == 0

    /** web `log.login_time || log.create_time`。 */
    val timeValue: String get() = loginTime.ifBlank { createTime }

    /** web `typeLabel`。 */
    val typeLabel: String get() = when (type) {
        "login" -> "登录"; "logout" -> "登出"; "refresh" -> "刷新"; "failed" -> "失败"; else -> type
    }
}
