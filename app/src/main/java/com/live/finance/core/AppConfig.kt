package com.live.finance.core

import com.live.finance.BuildConfig

/**
 * 全局配置。真机联调时只需改这里（BASE_URL / USE_FAKE）。
 * 契约来自 web/src/utils/request/{config,interceptors}，逐字段对齐。
 */
object AppConfig {
    /** 后端基址：以 / 结尾，路径含 /api/v1。由 BuildConfig 按变体注入（prod=局域网/线上，fake=占位）。 */
    @Volatile
    var baseUrl: String = BuildConfig.BASE_URL

    /** true = 全部走本地假数据（Wave-0 保证工程可直接启动）；false = 走真实后端。
     *  由 Gradle productFlavor 决定：Build Variants 选 fake → true，选 prod → false。 */
    @Volatile
    var useFake: Boolean = BuildConfig.USE_FAKE

    const val API_PREFIX = "/api/v1"
    const val TIMEOUT_MS = 15_000L

    // ---- 与 web config.js 对齐的协议常量 ----
    const val HANDSHAKE_PATH = "/auth/handshake"
    const val TOKEN_KEY = "finance_token"
    const val AES_KEY_PREFIX = "aes_"
    val ENCRYPTED_METHODS = setOf("post", "put", "delete")
    val SKIP_ENCRYPT_URLS = setOf("/auth/lock-system", "/data-manager/import/sql")

    // ---- 业务信封状态码（与后端 res.say 约定一致） ----
    const val ST_OK = 200
    const val ST_ACCEPT = 202
    const val ST_UNAUTH = 401
    const val ST_LOCKED = 423
    const val ST_RATE = 429

    // ---- PIN 安全校验码（对应 web utils/request/pin.js） ----
    object Pin {
        const val NEED_VERIFY = 8303
        const val ERROR = 8302
        const val LOCKED = 8304
        const val SUCCESS = 8301
    }

    /** 拼接完整 URL。path 可带或不带前导斜杠。 */
    fun url(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        val base = baseUrl.trimEnd('/')
        return if (p.startsWith(API_PREFIX)) "$base$p" else "$base$API_PREFIX$p"
    }

    /** 附件静态访问前缀（对应 web FILE_BASE_URL = host + /api/public）。 */
    val publicBaseUrl: String
        get() {
            val base = baseUrl.trimEnd('/')
            val schemeIdx = base.indexOf("://")
            val hostStart = if (schemeIdx >= 0) base.indexOf('/', schemeIdx + 3) else 0
            val origin = if (hostStart > 0) base.substring(0, hostStart) else base
            return "$origin/api/public"
        }

    fun fullFileUrl(path: String): String =
        if (path.startsWith("http")) path else "$publicBaseUrl${if (path.startsWith("/")) path else "/$path"}"
}
