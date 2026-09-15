package com.live.finance.core.net

import android.os.Build
import com.live.finance.core.store.SessionStore

/**
 * 构造与 web 端逐字段一致的安全请求头。
 * web 来源：ua-parser-js + device-hash；原生用 Build.* + 稳定指纹 fp 等价替代。
 */
class DeviceProfile(private val session: SessionStore) {

    val fp: String get() = session.fingerprint

    /**
     * 稳定的自定义 UA。后端 securityCheck 会校验「真实 User-Agent 头 == x-user-agent-custom」，
     * 故 ApiClient / HandshakeManager 必须把这个同一串既作为 x-user-agent-custom，
     * 也显式覆盖为请求的真实 User-Agent 头（覆盖 OkHttp 默认的 okhttp/x.y.z）。
     */
    val userAgent: String =
        "Dalvik/${System.getProperty("java.vm.version")} (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID})"

    fun headers(): Map<String, String> = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "User-Agent" to userAgent,
        "x-client-timestamp" to System.currentTimeMillis().toString(),
        "x-fingerprint-hash" to fp,
        "x-user-agent-custom" to userAgent,
        "x-device-model" to Build.MODEL,
        "x-os-name" to "Android",
        "x-device-type" to "mobile",
        "x-scene" to SCENE,
    )

    companion object { const val SCENE = "login" }
}
