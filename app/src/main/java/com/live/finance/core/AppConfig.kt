package com.live.finance.core

import com.live.finance.BuildConfig

/**
 * 全局配置。真机联调时只需改这里（BASE_URL / USE_FAKE）。
 * 契约来自 web/src/utils/request/{config,interceptors}，逐字段对齐。
 */
object AppConfig {
    /**
     * 后端基址（**运行时由用户输入**，如 `https://money.example.com`）。
     *
     * 🚫 2026-10-17 起构建期不再注入任何默认值：初始为空串。
     * 空串时 [url] 会拼出相对路径，网络层必须先用 [hasSite] 拦下并给用户明确提示
     * （见 ApiClient 的「未接入站点」守卫），不要直接丢给 OkHttp（会抛 IllegalArgumentException）。
     */
    @Volatile
    var baseUrl: String = ""

    /** true = 已接入站点（用户保存过合法地址）。登录页在 false 时禁止发起登录。 */
    val hasSite: Boolean
        get() = baseUrl.isNotBlank() && baseUrl.startsWith("http", ignoreCase = true)

    /** true = 全部走本地假数据（Wave-0 保证工程可直接启动）；false = 走真实后端。
     *  由 Gradle productFlavor 决定：Build Variants 选 fake → true，选 prod → false。 */
    @Volatile
    var useFake: Boolean = BuildConfig.USE_FAKE

    /**
     * **站点**基址（区别于 [baseUrl] 这个 API 基址）—— 用于拼接分享链接。
     *
     * 🚫 2026-10-17 起默认空串：不再有 `http://localhost` 占位。
     * 由用户在「我的 → 设置 → API 地址 / 分享 URL」填写并持久化到 SiteStore；
     * 未填写时分享入口直接阻断（见 DiaryDetailScreen）。
     */
    @Volatile
    var siteUrl: String = ""

    /** 拼接站点绝对地址（用于分享链接）。未配置站点时返回空串（调用方须判空）。 */
    fun siteUrl(path: String): String {
        if (siteUrl.isBlank()) return ""
        val p = if (path.startsWith("/")) path else "/$path"
        return siteUrl.trimEnd('/') + p
    }

    /**
     * 自身 APK 签名证书的 SHA-256（hex 小写，64 字符）（安全整改 P1-28，2026-10-08）。
     *
     * 由 `App.onCreate` 启动时用 PackageManager 计算并赋值；null = 计算失败
     * （异常环境/非 APK 运行）⇒ DeviceProfile 不上报该头，API 端 loose 模式放行。
     *
     * 用途：API 端 `appSignatureGuard` 与 `.env` 的 `APP_SIGN_SHA256` 白名单比对，
     * 拦截「重打包换签」的假 APP。防不住官方密钥泄露与 root 内存 hook（见 Plan R 登记）。
     */
    @Volatile
    var appSignSha256: String? = null

    /** 从 PackageManager 计算自身签名 SHA-256；失败返回 null（不抛异常）。 */
    fun computeSelfSignature(ctx: android.content.Context): String? = try {
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        // API 28+ 用 GET_SIGNING_CERTIFICATES（signingInfo）；更老版本回退 GET_SIGNATURES
        val sha = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners
            if (signers.isNullOrEmpty()) null
            else sha256Hex(signers[0].toByteArray())
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
            val sigs = info.signatures
            if (sigs.isNullOrEmpty()) null else sha256Hex(sigs[0].toByteArray())
        }
        sha?.lowercase()
    } catch (_: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    const val API_PREFIX = "/api/v1"
    const val TIMEOUT_MS = 15_000L

    // ---- 与 web config.js 对齐的协议常量 ----
    const val HANDSHAKE_PATH = "/auth/handshake"
    const val TOKEN_KEY = "finance_token"
    const val AES_KEY_PREFIX = "aes_"
    val ENCRYPTED_METHODS = setOf("post", "put", "delete")
    // ⚠ 加白名单的依据是「后端怎么读 body」：`/share/password` 的 controller 直接读 `req.body`（明文），
    // 不像其它接口走 `req.body.data` 解密 —— 走 ApiClient 默认的加密信封会被当成无效参数（400 参数不完整）。
    val SKIP_ENCRYPT_URLS = setOf("/auth/lock-system", "/data-manager/import/sql", "/share/password")

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

        /**
         * 请求被判「重放」（x-client-nonce 复用），对应后端 `securityCheck.js` 的
         * `CODE_NONCE_REPLAY`。**独立码**，不与其它 400 混用 ⇒ 只有它可以自动重发。
         * 对齐 web `utils/request/nonceRetry.js` 的 `CODE_NONCE_REPLAY`。
         */
        const val NONCE_REPLAY = 8401
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
