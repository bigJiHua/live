package com.live.finance.core.net

import android.os.Build
import com.live.finance.core.AppConfig
import com.live.finance.core.store.SessionStore
import java.util.UUID

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

    /**
     * 构造本次请求的安全头。
     *
     * ⚠ **本方法每次调用都会生成全新的 `x-client-timestamp` 与 `x-client-nonce`**，
     * 因此调用方必须「一次请求调一次」，不要缓存 Map 复用 —— 复用会导致
     * ① 时间戳超出后端 ±2 分钟窗口；② nonce 复用被判定为重放（后端回 `code:8401`）。
     * 现有 `ApiClient.attempt()` 每次重发都重建 Request 并重新调用本方法，天然满足该约束。
     */
    fun headers(): Map<String, String> = buildMap {
        put("X-Requested-With", "XMLHttpRequest")
        put("User-Agent", userAgent)
        put("x-client-timestamp", System.currentTimeMillis().toString())
        // 一次性随机数（防重放）：对齐 web `securityHeaders.createClientNonce()`
        put("x-client-nonce", UUID.randomUUID().toString().replace("-", ""))
        put("x-fingerprint-hash", fp)
        put("x-user-agent-custom", userAgent)
        put("x-device-model", Build.MODEL)
        put("x-os-name", "Android")
        put("x-device-type", "mobile")
        put("x-scene", SCENE)
        // 🔒 P1-28（2026-10-08）：自签名 SHA-256 上报（API 端白名单比对，拦截重打包 APP）。
        //   null（计算失败）⇒ 不带该头：API loose 模式放行，strict 模式会拒 —— 两种都是显式语义。
        AppConfig.appSignSha256?.let { put("x-app-sign-sha256", it) }
    }

    companion object { const val SCENE = "login" }
}
