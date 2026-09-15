package com.live.finance.data.model

/** 登录日志（对应 GET /auth/login-logs 行）。 */
data class LoginLog(
    val id: String,
    val type: String = "",
    val ip: String = "",
    val location: String = "",
    val isp: String = "",
    val os: String = "",
    val browser: String = "",
    val device: String = "",
    val status: Int = 1,
    val error: String = "",
    val loginTime: String = "",
) {
    val success: Boolean get() = status == 1
}
