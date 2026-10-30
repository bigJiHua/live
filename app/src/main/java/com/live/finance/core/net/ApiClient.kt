package com.live.finance.core.net

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.BuildConfig
import com.live.finance.core.AppConfig
import com.live.finance.core.crypto.AesCbc
import com.live.finance.core.store.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * 传输层：把一次业务调用按 web 协议发出去，并解析明文信封。
 *
 * 规则（与 web 拦截器等价）：
 *  - 每个请求带安全 headers + Bearer(token)。
 *  - 写操作(post/put/delete)：随机 IV + AES-256-CBC 加密
 *      plaintext = { fingerprint, path, data:<业务体> } → { "_p": ivHex+base64 }，
 *      并加 X-FP-ID: fp；命中 skipEncryptionUrls / 握手路径则明文透传。
 *  - GET 读：仅 headers + query，明文。
 *  - 响应明文 JSON → Envelope。
 */
class ApiClient(
    private val device: DeviceProfile,
    private val session: SessionStore,
    private val clientContext: ClientContext,
) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .apply {
            // 🔒 安全整改 P0-4（2026-10-08）：可选的证书锁定（Pinning）
            //
            // 默认**关闭**，因为 Let's Encrypt 证书约 90 天轮换，硬编码指纹会导致
            // 证书更新后全网用户 APP 同时失效且必须发版才能恢复。
            // 需要时在 gradle.properties 填 API_PINNED_SHA256（仅限自签名/固定 CA 证书）。
            //
            // 未启用 Pinning 时，防中间人主要靠 network_security_config.xml：
            //   · cleartextTrafficPermitted="false" ⇒ 无法降级到 http
            //   · 只信任 system CA ⇒ 用户装的抓包证书无效
            if (com.live.finance.BuildConfig.ENABLE_PINNING) {
                val host = com.live.finance.BuildConfig.API_HOST
                val pin = com.live.finance.BuildConfig.API_PINNED_SHA256
                if (host.isNotBlank() && pin.isNotBlank()) {
                    certificatePinner(
                        okhttp3.CertificatePinner.Builder()
                            .add(host, pin)
                            .build()
                    )
                } else {
                    android.util.Log.w(
                        "ApiClient",
                        "ENABLE_PINNING=true 但 API_HOST/API_PINNED_SHA256 为空 —— 已跳过证书锁定",
                    )
                }
            }
        }
        .build()

    val handshake = HandshakeManager(http, device)

    /**
     * 命中 8303 时的验证回调（由 AppGraph 接到 PinCoordinator）；返回票据则带票重发原请求，null 表示取消。
     * 第二参数为 `skipCooldown`：冷却静默重发后仍是 8303 时用它强制回到弹窗流程（对齐 web `requestPinVerify`）。
     */
    var pinGate: (suspend (com.google.gson.JsonElement?, Boolean) -> com.live.finance.core.PinTicket?)? = null

    /**
     * 会话被终止的统一回调（由 AppGraph 接到 `sessionBlocked` 状态，UI 侧执行 web `forceLogin` 的那套：
     * toast + 清 token + 回登录页，带防重入）。两类来源：
     *  - **401**（HTTP 或业务体 `status:401`）= web `response.js` 的 `forceLogin(res.message)`；
     *  - **8304**（PIN 连续错 3 次锁定 / 系统硬锁）= web `pin.js` 的 `onPinLocked → forceLogin`。
     * 后端 8304 的 HTTP 状态是 200、只在业务体带 `code:8304`，故必须在这里拦。
     */
    var onSessionBlocked: ((String) -> Unit)? = null

    /**
     * 全局网络活动计数（2026-10-27 批次A2）：>0 = 有请求在飞。
     *
     * 消费方：GlobalNavBar 顶栏「一键锁定」左侧的活动指示器——用户由此察觉「系统正在/刚在后台更新数据」。
     * 覆盖 [request]/[uploadRaw]/[upload]/[download] 全部真实网络出口；attempt() 内部重发
     * （8401 重发 / 带票重发）算同一次业务请求不重复计；SWR 后台静默刷新（批次B）也计入，
     * 这是「静默更新可感知」的落点。8303 的 PIN 弹窗等待期一并计入（实现从简：弹窗期间请求未决）。
     */
    val inFlight = MutableStateFlow(0)

    /** 包住一次真实网络出口的计数壳：进入 +1，finally -1（成功/失败/异常/取消都归位）。 */
    private inline fun <T> counted(block: () -> T): T {
        inFlight.update { it + 1 }
        try {
            return block()
        } finally {
            inFlight.update { it - 1 }
        }
    }

    // ── SWR 后台静默刷新（2026-10-27 批次B）──
    // STALE 命中时的后台刷新跑在这里：独立 SupervisorJob ⇒ 与调用方协程解耦（页面离开取消不影响），
    // 会话终止/切站点时用 cancelBackgroundRefresh() 取消全部在飞。
    private val swrJob = SupervisorJob()
    private val swrScope = CoroutineScope(swrJob + Dispatchers.IO + CoroutineName("api-swr"))

    /** 会话终止/切站点时调用：取消全部在飞的后台刷新（其结果已无意义，且不得在清缓存后复活旧数据）。 */
    fun cancelBackgroundRefresh() {
        swrJob.cancelChildren()
    }

    /**
     * STALE 命中触发的后台静默刷新。single-flight：同 key 同时只允许一个在飞（[ApiGetCache.markRefreshing]）。
     * 一律 silent：401/8303/8304 只吞不弹——下次用户前台操作拿到同样的失败会走正常 forceLogin/PIN 流程，
     * 保证最终一定知道会话失效，只是不打断当前的无感刷新。
     */
    private fun launchStaleRefresh(cacheKey: String, path: String, query: Map<String, String>?) {
        if (!ApiGetCache.markRefreshing(cacheKey)) return // 已有同 key 在飞：复用那一次即可
        swrScope.launch {
            try {
                val env = execute(
                    method = "get", lower = "get", path = path, query = query,
                    body = null, encrypted = false, routeTag = "", silent = true, cacheKey = cacheKey,
                )
                if (env.isSuccess) {
                    // execute 内部已把新信封 put 进缓存，这里只广播（页面可选监听做原地更新）
                    ApiGetCache.broadcast(cacheKey)
                    if (BuildConfig.DEBUG) Log.d(TAG, "SWR REFRESHED $path")
                } else {
                    ApiGetCache.extendGrace(cacheKey, SWR_GRACE_MS)
                    if (BuildConfig.DEBUG) Log.d(TAG, "SWR KEEP-STALE $path code=${env.code} msg=${env.message}")
                }
            } catch (e: CancellationException) {
                throw e // 会话终止/切站点取消：正常路径，finally 会释放 single-flight 占位
            } catch (e: Exception) {
                ApiGetCache.extendGrace(cacheKey, SWR_GRACE_MS)
                if (BuildConfig.DEBUG) Log.d(TAG, "SWR ERROR $path: ${e.message}")
            } finally {
                ApiGetCache.unmarkRefreshing(cacheKey)
            }
        }
    }

    suspend fun get(path: String, query: Map<String, String>? = null) =
        request("get", path, query, null, encrypted = false)

    suspend fun post(path: String, body: JsonElement? = null) =
        request("post", path, null, body, encrypted = true)

    suspend fun put(path: String, body: JsonElement? = null) =
        request("put", path, null, body, encrypted = true)

    /** PATCH（如 `PATCH /account/:id/remark`）；`request` 走 `else -> method(...)` 分支，任意 method 都支持。 */
    suspend fun patch(path: String, body: JsonElement? = null) =
        request("patch", path, null, body, encrypted = true)

    suspend fun delete(path: String, body: JsonElement? = null) =
        request("delete", path, null, body, encrypted = true)

    /** 通用 multipart 上传（不加密，带安全头+Bearer）。field 为文件字段名，form 为其它文本字段。 */
    suspend fun uploadRaw(
        path: String, field: String, fileName: String, mime: String, bytes: ByteArray,
        form: Map<String, String> = emptyMap(),
    ): ApiResult<JsonObject?> {
        // 🛡 站点守卫（2026-10-17）：未接入/地址非法时不进 OkHttp（否则抛 IllegalArgumentException）
        val target = siteUrlOrNull(path) ?: return ApiResult.Network(noSiteEnvelope().message)
        // 🔄 P2-18（2026-10-08）：8401 一次性自愈。
        //   请求构建提成 sendOnce()：每次调用都重新 device.headers() ⇒ 全新 nonce；
        //   被判重放（HTTP 400 + code:8401）时重发一次，与 request() 的 attempt() 同语义。
        fun sendOnce(): Envelope {
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(field, fileName, bytes.toRequestBody(mime.toMediaType()))
            form.forEach { (k, v) -> mb.addFormDataPart(k, v) }
            val builder = Request.Builder().url(target).post(mb.build())
            device.headers().forEach { (k, v) -> builder.header(k, v) }
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            return http.newCall(builder.build()).execute().use { resp ->
                parseEnvelope(resp.code, resp.body?.string() ?: "")
            }
        }
        return counted {
        withContext(Dispatchers.IO) {
            try {
                var env = sendOnce()
                if (env.code == AppConfig.Pin.NONCE_REPLAY) {
                    env = sendOnce()
                }
                when (val r = env.toResult()) {
                    is ApiResult.Ok -> {
                        // 上传走独立 OkHttp，不经 request() 的写后清缓存；这里补上，
                        // 否则刚传的图会被 GET /upload/list 的 5 分钟缓存挡住。
                        ApiGetCache.clear()
                        ApiResult.Ok(r.data as? JsonObject, r.message)
                    }
                    is ApiResult.Fail -> ApiResult.Fail(r.message)
                    // 远端 5xx 也要保持「灰」，别被下面的 else 降级成红的「上传失败」
                    is ApiResult.Network -> ApiResult.Network(r.message)
                    else -> ApiResult.Fail("上传失败")
                }
            } catch (e: java.io.IOException) {
                ApiResult.Network(Envelope.transport(e).message)
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "上传异常")
            }
        }
        }
    }

    /** 二进制下载（导出/备份），返回原始字节；带安全头+Bearer，不加密不解码。 */
    suspend fun download(path: String, query: Map<String, String>? = null): ApiResult<ByteArray> {
        // 🛡 站点守卫（2026-10-17）：未接入/地址非法时不进 OkHttp
        val target = siteUrlOrNull(path) ?: return ApiResult.Network(noSiteEnvelope().message)
        // 🔄 P2-18（2026-10-08）：8401 一次性自愈。
        //   sendOnce() 每次重建请求 ⇒ 全新 nonce。8401 是 HTTP 400 + JSON body，
        //   而成功响应是二进制（不能按文本读，String 解码会损坏字节）——
        //   故先 bytes() 全量读入（与旧实现 resp.body.bytes() 的内存行为一致），
        //   仅当「HTTP 不成功 且 Content-Type 是 json」时才尝试解析 code:8401。
        fun sendOnce(): Triple<Int, ByteArray?, String> {
            val builder = Request.Builder().url(target).get()
            device.headers().forEach { (k, v) -> builder.header(k, v) }
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            return http.newCall(builder.build()).execute().use { resp ->
                Triple(resp.code, resp.body?.bytes(), resp.header("Content-Type") ?: "")
            }
        }
        fun isNonceReplayBody(ct: String, body: ByteArray?): Boolean {
            if (!ct.contains("json") || body == null) return false
            val obj = runCatching { gson.fromJson(String(body), JsonObject::class.java) }.getOrNull()
            return obj?.get("code")?.takeIf { !it.isJsonNull }?.asInt == AppConfig.Pin.NONCE_REPLAY
        }
        return counted {
        withContext(Dispatchers.IO) {
            try {
                var (code, body, ct) = sendOnce()
                if (code !in 200..299 && isNonceReplayBody(ct, body)) {
                    val retry = sendOnce()
                    code = retry.first
                    body = retry.second
                    // retry.third（Content-Type）此处不再需要：8401 判定只做一次
                }
                when {
                    code in 200..299 && body != null -> ApiResult.Ok(body, "")
                    // 远端 5xx ⇒ 灰（对齐 web 的 kind='server'），不是红的「下载失败」
                    code >= 500 -> ApiResult.Network("服务器开小差了，请稍后重试")
                    else -> ApiResult.Fail("下载失败 $code")
                }
            } catch (e: java.io.IOException) {
                ApiResult.Network(Envelope.transport(e).message)
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "下载异常")
            }
        }
        }
    }

    /**
     * 文件上传（multipart，对应 fileRequest：同 /api/v1 前缀、带安全头+Bearer、不加密、60s）。
     * POST /upload/single，form: file + busType + busId + remark。返回附件可访问 URL（或空）。
     */
    suspend fun upload(bytes: ByteArray, fileName: String, mime: String, busType: String, busId: String, remark: String): ApiResult<String> {
        // 🛡 站点守卫（2026-10-17）：未接入/地址非法时不进 OkHttp
        val target = siteUrlOrNull("/upload/single") ?: return ApiResult.Network(noSiteEnvelope().message)
        // 🔄 P2-18（2026-10-08）：与 uploadRaw 同款 8401 一次性自愈
        fun sendOnce(): Envelope {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
                .addFormDataPart("busType", busType)
                .apply { if (busId.isNotEmpty()) addFormDataPart("busId", busId) }
                .apply { if (remark.isNotEmpty()) addFormDataPart("remark", remark) }
                .build()
            val builder = Request.Builder().url(target).post(body)
            device.headers().forEach { (k, v) -> builder.header(k, v) }
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            return http.newCall(builder.build()).execute().use { resp ->
                parseEnvelope(resp.code, resp.body?.string() ?: "")
            }
        }
        return counted {
        withContext(Dispatchers.IO) {
            try {
                var env = sendOnce()
                if (env.code == AppConfig.Pin.NONCE_REPLAY) {
                    env = sendOnce()
                }
                when (val r = env.toResult()) {
                    is ApiResult.Ok -> {
                        // 同 uploadRaw：上传成功即让 GET 快照失效，资源库/头像等立即可见。
                        ApiGetCache.clear()
                        val o = (r.data as? JsonObject)
                        val path = o?.get("url")?.takeIf { !it.isJsonNull }?.asString
                            ?: o?.get("file_path")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        ApiResult.Ok(path, r.message)
                    }
                    // 远端 5xx / 传输失败 ⇒ 保持「灰」
                    is ApiResult.Network -> ApiResult.Network(r.message)
                    else -> ApiResult.Fail(if (r is ApiResult.Fail) r.message else "上传失败")
                }
            } catch (e: java.io.IOException) {
                ApiResult.Network(Envelope.transport(e).message)
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "上传异常")
            }
        }
        }
    }

    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String>? = null,
        body: JsonElement? = null,
        encrypted: Boolean = false,
        routeTag: String = "",
        silent: Boolean = false,
    ): Envelope {
        val lower = method.lowercase()
        // 批次B 铁律：silent 只允许 GET（SWR 后台刷新专用）——写操作的 8303 PIN 弹窗是安全语义，必须保留。
        require(!silent || lower == "get") { "silent 仅允许 GET；写操作必须走前台 PIN/会话语义" }

        // ── GET 进程内 TTL 缓存 + SWR（2026-10-22 缓存 / 2026-10-27 SWR）──
        // 命中新鲜缓存直接返回，不发请求；过期（STALE）先返回旧快照（调用方零等待），
        // 同时触发一次 single-flight 的后台静默刷新；未命中照常走网络。
        // 只对「已接入站点」的 GET 生效：未接入时后面本来就会返回 noSiteEnvelope。
        val cacheKey = if (lower == "get" && AppConfig.hasSite && isCacheableGet(path)) {
            getCacheKey(path, query)
        } else {
            null
        }
        if (cacheKey != null) {
            val hit = ApiGetCache.get(cacheKey)
            if (hit != null) {
                val age = System.currentTimeMillis() - hit.at
                if (age >= 0L && age < hit.ttlMs) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "HIT GET $path age=${age}ms/${hit.ttlMs}ms cache=${ApiGetCache.sizeBytes()}B")
                    }
                    return hit.envelope
                }
                // STALE（批次B SWR）：旧快照秒回，后台拉新。这正是「数据静默更新、整体不刷新」的核心路径。
                if (BuildConfig.DEBUG) Log.d(TAG, "STALE GET $path age=${age}ms/${hit.ttlMs}ms ⇒ SWR")
                launchStaleRefresh(cacheKey, path, query)
                return hit.envelope
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "MISS GET $path")
            }
        }

        return execute(method, lower, path, query, body, encrypted, routeTag, silent, cacheKey)
    }

    /**
     * 真实网络执行段（2026-10-27 批次B 自 request 拆出）：前台请求与 SWR 后台刷新共用一条链路，
     * 8401 重发 / KEY_ERR 重握手 / 8303 带票重发等自愈逻辑天然共享。
     *
     * silent=true（仅 GET，见 [request] 的 require）时的语义分歧：
     *  - 8303：不弹 PIN，直接把信封交回（SWR 调用方按失败处理 → 旧快照延长宽限）；
     *  - 401/8304：只清缓存，**不调 onSessionBlocked**——后台刷新永不把用户踢下线，
     *    下次前台请求拿到同样的失败会走正常 forceLogin 流程兜底。
     */
    private suspend fun execute(
        method: String,
        lower: String,
        path: String,
        query: Map<String, String>?,
        body: JsonElement?,
        encrypted: Boolean,
        routeTag: String,
        silent: Boolean,
        cacheKey: String?,
    ): Envelope {
        val isHandshake = path.contains(AppConfig.HANDSHAKE_PATH)
        val isSkip = AppConfig.SKIP_ENCRYPT_URLS.any { path.contains(it) }
        val needEncrypt = encrypted && !isHandshake && !isSkip

        // 🛡 站点守卫（2026-10-17）：构建期已无默认地址，未接入时 baseUrl 为空。
        //   `AppConfig.url()` 会拼出相对路径，`toHttpUrl()` 直接抛 IllegalArgumentException ——
        //   该异常原先在 try/catch 之外，会穿透到调用方协程 ⇒ 登录按钮永远转圈。
        //   这里统一转成「没拿到响应」的灰色信封（transportFailed = true），由 UI 弹提示。
        val url = siteUrlOrNull(path, query) ?: return noSiteEnvelope()

        suspend fun attempt(routeTicket: com.live.finance.core.PinTicket? = null): Envelope {
            val reqBuilder = Request.Builder().url(url)
            // ⚠ 每次 attempt 都重新取一次安全头（内含全新的 x-client-timestamp / x-client-nonce）。
            //   所有重发路径（KEY_ERR 重握手、8303 带票重发）都走这里，因此**天然不会复用 nonce**。
            //   若将来有人把 headers 提到 attempt 外面缓存复用，会导致时间戳超窗 + 被判重放(8401)。
            device.headers().forEach { (k, v) -> reqBuilder.header(k, v) }
            session.token?.let { reqBuilder.header("Authorization", "Bearer $it") }
            // 风险路由（pinLockGuard）验证通过后重发：带上一次性令牌头
            routeTicket?.token?.takeIf { it.isNotEmpty() }?.let { reqBuilder.header(routeTicket.headerName, it) }
            if (needEncrypt) reqBuilder.header("X-FP-ID", device.fp)

            val requestBody: RequestBody? = buildBody(lower, body, needEncrypt, routeTag)
            when (lower) {
                "get" -> reqBuilder.get()
                "post" -> reqBuilder.post(requestBody ?: EMPTY_BODY)
                "put" -> reqBuilder.put(requestBody ?: EMPTY_BODY)
                "delete" -> if (requestBody != null) reqBuilder.delete(requestBody) else reqBuilder.delete()
                else -> reqBuilder.method(lower.uppercase(), requestBody)
            }
            return withContext(Dispatchers.IO) {
                try {
                    http.newCall(reqBuilder.build()).execute().use { resp ->
                        parseEnvelope(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: java.io.IOException) {
                    // 本地连不上 / DNS / 超时 / TLS ⇒ 造「没拿到响应」的信封
                    // ⇒ `toResult()` 给 `ApiResult.Network` ⇒ 弹窗**灰**（对齐 web 的 kind='network'）。
                    //
                    // ⚠ 只捕 `IOException`，别写 `catch (e: Exception)`：
                    //   协程取消抛的 `CancellationException` 不是 IOException，这样不会被吞掉；
                    //   页面离开导致的取消仍能正常传播。
                    Envelope.transport(e)
                }
            }
        }

        // 计入全局网络活动（批次A2）：从首个请求到 8401 重发/带票重发完成都算一次在飞。
        return counted {
        var env = attempt()
        // nonce 复用（8401）：请求被判重放。对齐 web `nonceRetry.js` / webview-APP 同名模块。
        //
        // 触发场景：本端 `attempt()` 每次都会重建 Request 并重新调用 `device.headers()`
        // （其中 nonce 是新生成的），所以**正常流程不会复用 nonce**；唯一的来源是
        // OkHttp 的 `retryOnConnectionFailure` 自动重试（如 keep-alive 连接已失效，
        // 重试用新连接但复用同一份请求头）。
        //
        // `attempt()` 重发时会再取一次全新 headers，因此直接重发一次即可自愈。
        // 用 if 而非 while ⇒ 最多重试一次，不会死循环（与 web 的 `__nonceRetried` 等价）。
        if (env.code == AppConfig.Pin.NONCE_REPLAY) {
            env = attempt()
        }
        // 加密写命中「安全隧道未建立 / KEY_ERR(400)」→ 旧 key 失效，重握手一次再发
        // （`!env.isPinVerify`：8302/8303/8304 的响应体也带 status:401，那是 PIN 语义不是密钥失效，
        //   否则锁定态下每个写请求都会白白重握手一次）
        if (needEncrypt && !env.isPinVerify &&
            (env.status == 401 || (env.status == 400 && env.message.contains("KEY_ERR")))
        ) {
            handshake.invalidate()
            env = attempt()
        }
        // 命中 8303：弹 PIN，验证通过后带上票据重发原请求一次
        // （route-verify 自身除外 —— 它自己就返回 PIN 码，再拦会递归；对齐 web handlePinResponse）
        //
        // ⚠ /auth/lock-system 也要除外（对齐 web `handlePinResponse` 的 `/lock-system` 分支）：
        // 它返回的 8303 语义是「已锁定」，不是「需要验证」。若进队列，验证通过后带票重发 lock-system，
        // 后端此时已因 markSuccess 把 lock 记录置为 pin_status=1，查不到未解锁记录 ⇒ 又插一条
        // ⇒ 用户明明输对了 PIN，系统却被重新锁上。锁定后的弹窗改由 GlobalNavBar 调
        // `PinCoordinator.requestUnlock()` 主动弹（等价 web 的 showPinDialog）。
        if (env.isPinVerify && !silent &&
            !path.contains("/security/pin/route-verify") &&
            !path.contains("/lock-system")
        ) {
            var ticket = pinGate?.invoke(env.data, false)
            if (ticket != null) {
                env = attempt(routeTicket = ticket)
                // 冷却窗口里"免弹窗直接重发"后仍然是 8303 ⇒ 服务端并不认这次免验（系统已锁定 /
                // 后端已清 verifiedMap）⇒ 不能把 8303 当结果交给调用方，必须回到正常弹窗流程。
                if (env.isPinVerify) {
                    ticket = pinGate?.invoke(env.data, true)
                    if (ticket != null) env = attempt(routeTicket = ticket)
                }
            }
        }
        // 会话被终止 → 统一交给 onSessionBlocked（web 的 forceLogin / onPinLocked）
        // ⚠ 判序同 Envelope.toResult：只有 **8304** 才登出；8302/8303 的响应体也带 `status:401`
        //   （中间件 respond() 所致），若按 status 判会把「取消验证 / PIN 输错」误判成会话失效而踢回登录页。
        val pinCode = env.code
        when {
            pinCode == AppConfig.Pin.LOCKED -> {
                ApiGetCache.clear()
                if (!silent) {
                    swrJob.cancelChildren()
                    onSessionBlocked?.invoke(env.message.ifEmpty { "PIN 已锁定，请重新登录" })
                }
                // silent（批次B）：后台刷新遇会话终止只吞不弹——旧快照已被 clear，下次前台请求兜底。
            }
            pinCode == null && (env.httpStatus == 401 || env.status == 401) -> {
                ApiGetCache.clear()
                if (!silent) {
                    swrJob.cancelChildren()
                    onSessionBlocked?.invoke(env.message.ifEmpty { "登录已失效" })
                }
            }
        }

        if (cacheKey != null && env.isSuccess) {
            val ttl = ttlMsFor(path)
            ApiGetCache.put(cacheKey, env, ttl)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "PUT GET $path ttl=${ttl}ms cache=${ApiGetCache.sizeBytes()}B")
            }
        } else if (lower != "get" && env.isSuccess && !isReadOnlyPost(path)) {
            // 写成功 ⇒ 所有 GET 快照失效。宁可多清一次，也不能让用户看到刚改完的旧数据。
            // 同时取消在飞的后台刷新：它们可能在写提交前发出、把写之前的旧数据塞回缓存（复活旧值）。
            ApiGetCache.clear()
            swrJob.cancelChildren()
            if (BuildConfig.DEBUG) Log.d(TAG, "CLEAR after ${method.uppercase()} $path")
        } else if (lower == "get" && env.isSuccess && isSideEffectGet(path)) {
            // 少数 GET 会写库（如分期清单会自动入账）⇒ 不缓存，且成功后清掉别的 GET 快照。
            ApiGetCache.clear()
            swrJob.cancelChildren()
            if (BuildConfig.DEBUG) Log.d(TAG, "CLEAR after side-effect GET $path")
        }
        env
        }
    }

    /**
     * 缓存 key：站点 + 当前会话 + path + 已排序 query。
     *
     * 站点/会话都进 key ⇒ 切站或换账号后旧条目自然不可达；登出/登录时还会显式清一次。
     */
    private fun getCacheKey(path: String, query: Map<String, String>?): String {
        // 用完整 token（内存 key，不写日志）而不是 hashCode：避免理论上 32 位哈希碰撞
        // 让两个账号命中同一份财务数据。
        val token = session.token ?: "anon"
        val q = query?.entries?.sortedBy { it.key }
            ?.joinToString("&") { "${it.key}=${it.value}" }
            .orEmpty()
        return "${AppConfig.baseUrl}|$token|$path|$q"
    }

    /** TTL 分级：字典/地理信息长；卡片/池/用户/安全状态中；首页摘要与账单短。 */
    private fun ttlMsFor(path: String): Long = when {
        path.startsWith("/category") || path.startsWith("/geo") -> 30 * 60_000L
        path == "/card" || path == "/card/pool" || path == "/pool" ||
            path == "/auth/me" || path == "/security/pin/check" -> 10 * 60_000L
        // 首页摘要三接口（批次B 起纳入 SWR，60s 档）：STALE 时先给旧快照再后台静默刷新。
        // 2026-10-22 的「首页空白回归」根因是无快照白屏，SWR 的「永远先给旧数据」正是正解。
        path == "/asset/home" || path == "/todo/reminders" || path == "/work/salary/day" -> 60_000L
        // 账单 GET 在「列表为空」时会触发后端重算，且账期变化比流水频繁 ⇒ 只给 60s。
        path == "/card/bill" -> 60_000L
        else -> 5 * 60_000L
    }

    /**
     * 哪些 GET 可以进缓存。
     *
     * - `/data-manager` 前缀：导出是 GET 启动任务、进度也是 GET 轮询，缓存会让「刷新状态」永远停在旧进度；
     * - `/recurring/installments`：GET 会自动入账（写库），不能读快照，见 [isSideEffectGet]；
     * - `/share` 前缀：公开分享内容可能被撤回，不适合留快照；
     * - `/geo/ip`：IP 定位是实时查询（客户端公网 IP 会变），且失败(空 address)的 200 响应缓存后会
     *   阻止重试 ⇒ 秒批"IP定位失败"。
     *
     * ⚠ 首页三接口（/asset/home、/todo/reminders、/work/salary/day）曾因 2026-10-22 首页空白回归
     *   被撤出缓存；2026-10-27 批次B 引入 SWR（STALE 先给旧快照再后台刷新）后重新纳入——
     *   空白根因已由 SWR 语义消除。
     */
    private fun isCacheableGet(path: String): Boolean =
        !path.startsWith("/data-manager") &&
            !path.startsWith("/recurring/installments") &&
            !path.startsWith("/share") &&
            path != "/geo/ip"

    /** 有写副作用的 GET：不缓存，且在成功后清空其他 GET 快照。 */
    private fun isSideEffectGet(path: String): Boolean =
        path.startsWith("/recurring/installments")

    /** 用 POST 承载的只读接口：成功也不该清 GET 缓存。 */
    private fun isReadOnlyPost(path: String): Boolean =
        path.startsWith("/moment/batch") ||
            path.startsWith("/share/password") ||
            path.startsWith("/security/pin/verify") ||
            path.startsWith("/security/pin/route-verify") ||
            path.startsWith("/security/risk-report") ||
            path.startsWith("/user/email/send-code") ||
            path.startsWith("/data-manager/validate")

    private companion object {
        const val TAG = "ApiCache"

        /** 刷新失败后旧快照的宽限时长：期间再次 STALE 命中不重新触发刷新，防后端故障时每次进页都打网络。 */
        const val SWR_GRACE_MS = 60_000L
    }

    private suspend fun buildBody(
        method: String,
        body: JsonElement?,
        needEncrypt: Boolean,
        routeTag: String,
    ): RequestBody? {
        if (method == "get") return null
        if (!needEncrypt) {
            return (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA)
        }
        val key = handshake.aesKey() ?: return (body?.toString() ?: "{}").toRequestBody(JSON_MEDIA)
        val plain = JsonObject().apply {
            addProperty("fingerprint", device.fp)
            addProperty("path", routeTag)
            add("data", body ?: JsonObject())
            // 对齐 web 拦截器的 {...deviceData}：后端解密后从 req.body 顶层读 loginIp 等写登录日志
            for ((k, v) in clientContext.data().entrySet()) add(k, v)
        }
        val payload = JsonObject().apply {
            addProperty("_p", AesCbc.encrypt(plain.toString(), key))
        }
        return payload.toString().toRequestBody(JSON_MEDIA)
    }

    /**
     * 构造完整请求 URL；**站点未接入或地址非法时返回 null**（调用方转 [noSiteEnvelope]）。
     *
     * Why 单独抽出来（2026-10-17）：`AppConfig.url(path).toHttpUrl()` 在 baseUrl 为空 /
     * 含空格 / 只有 scheme 时会抛 `IllegalArgumentException`。此前它写在 try 之外，
     * 异常穿透到 `scope.launch`，`loading` 永远不归位 ⇒ 登录按钮无限转圈且无任何提示。
     */
    private fun siteUrlOrNull(path: String, query: Map<String, String>? = null): HttpUrl? {
        if (!AppConfig.hasSite) return null
        return runCatching {
            val b = AppConfig.url(path).toHttpUrl().newBuilder()
            query?.forEach { (k, v) -> b.addQueryParameter(k, v) }
            b.build()
        }.getOrNull()
    }

    /** 「未接入站点」/「地址非法」的合成信封：UI 按网络类灰色提示，业务层不会误判成 401。 */
    private fun noSiteEnvelope(): Envelope = Envelope(
        httpStatus = -1,
        status = -1,
        code = null,
        message = if (!AppConfig.hasSite) "尚未接入站点，请先设置站点地址" else "站点地址格式不正确，请重新设置",
        data = null,
        isMessage = false,
        transportFailed = true,
    )

    private fun parseEnvelope(httpStatus: Int, text: String): Envelope {
        val obj = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
        if (obj == null) {
            return Envelope(httpStatus, httpStatus, null, text.take(80), null, false)
        }
        val status = obj.get("status")?.asInt ?: 0
        val code = obj.get("code")?.takeIf { !it.isJsonNull }?.asInt
        val message = obj.get("message")?.asString ?: ""
        val data = obj.get("data")?.takeIf { !it.isJsonNull }
        val isMessage = obj.get("ismessage")?.asBoolean ?: false
        return Envelope(httpStatus, status, code, message, data, isMessage, obj)
    }

    private val EMPTY_BODY = "".toRequestBody(JSON_MEDIA)
}
