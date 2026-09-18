package com.live.finance.core.net

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.AppConfig
import com.live.finance.core.crypto.AesCbc
import com.live.finance.core.store.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(AppConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    val handshake = HandshakeManager(http, device)

    /** 命中 8303 时的验证回调（由 AppGraph 接到 PinCoordinator）；返回票据则带票重发原请求，null 表示取消。 */
    var pinGate: (suspend (com.google.gson.JsonElement?) -> com.live.finance.core.PinTicket?)? = null

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
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(field, fileName, bytes.toRequestBody(mime.toMediaType()))
        form.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        val builder = Request.Builder().url(AppConfig.url(path)).post(mb.build())
        device.headers().forEach { (k, v) -> builder.header(k, v) }
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { resp ->
                    val env = parseEnvelope(resp.code, resp.body?.string() ?: "")
                    when (val r = env.toResult()) {
                        is ApiResult.Ok -> ApiResult.Ok(r.data as? JsonObject, r.message)
                        is ApiResult.Fail -> ApiResult.Fail(r.message)
                        else -> ApiResult.Fail("上传失败")
                    }
                }
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "上传异常")
            }
        }
    }

    /** 二进制下载（导出/备份），返回原始字节；带安全头+Bearer，不加密不解码。 */
    suspend fun download(path: String, query: Map<String, String>? = null): ApiResult<ByteArray> {
        val urlBuilder = AppConfig.url(path).toHttpUrl().newBuilder()
        query?.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val builder = Request.Builder().url(urlBuilder.build()).get()
        device.headers().forEach { (k, v) -> builder.header(k, v) }
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { resp ->
                    if (resp.code in 200..299 && resp.body != null) ApiResult.Ok(resp.body!!.bytes(), "")
                    else ApiResult.Fail("下载失败 ${resp.code}")
                }
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "下载异常")
            }
        }
    }

    /**
     * 文件上传（multipart，对应 fileRequest：同 /api/v1 前缀、带安全头+Bearer、不加密、60s）。
     * POST /upload/single，form: file + busType + busId + remark。返回附件可访问 URL（或空）。
     */
    suspend fun upload(bytes: ByteArray, fileName: String, mime: String, busType: String, busId: String, remark: String): ApiResult<String> {
        val url = AppConfig.url("/upload/single")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .addFormDataPart("busType", busType)
            .apply { if (busId.isNotEmpty()) addFormDataPart("busId", busId) }
            .apply { if (remark.isNotEmpty()) addFormDataPart("remark", remark) }
            .build()
        val builder = Request.Builder().url(url).post(body)
        device.headers().forEach { (k, v) -> builder.header(k, v) }
        session.token?.let { builder.header("Authorization", "Bearer $it") }
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { resp ->
                    val env = parseEnvelope(resp.code, resp.body?.string() ?: "")
                    when (val r = env.toResult()) {
                        is ApiResult.Ok -> {
                            val o = (r.data as? JsonObject)
                            val path = o?.get("url")?.takeIf { !it.isJsonNull }?.asString
                                ?: o?.get("file_path")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            ApiResult.Ok(path, r.message)
                        }
                        else -> ApiResult.Fail(if (r is ApiResult.Fail) r.message else "上传失败")
                    }
                }
            } catch (e: Exception) {
                ApiResult.Fail(e.message ?: "上传异常")
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
    ): Envelope {
        val lower = method.lowercase()
        val isHandshake = path.contains(AppConfig.HANDSHAKE_PATH)
        val isSkip = AppConfig.SKIP_ENCRYPT_URLS.any { path.contains(it) }
        val needEncrypt = encrypted && !isHandshake && !isSkip

        val urlBuilder = AppConfig.url(path).toHttpUrl().newBuilder()
        query?.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()

        suspend fun attempt(routeTicket: com.live.finance.core.PinTicket? = null): Envelope {
            val reqBuilder = Request.Builder().url(url)
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
                http.newCall(reqBuilder.build()).execute().use { resp ->
                    parseEnvelope(resp.code, resp.body?.string() ?: "")
                }
            }
        }

        var env = attempt()
        // 加密写命中「安全隧道未建立 / KEY_ERR(400)」→ 旧 key 失效，重握手一次再发
        if (needEncrypt && (env.status == 401 || (env.status == 400 && env.message.contains("KEY_ERR")))) {
            handshake.invalidate()
            env = attempt()
        }
        // 命中 8303：弹 PIN，验证通过后带上票据重发原请求一次
        if (env.isPinVerify) {
            val ticket = pinGate?.invoke(env.data)
            if (ticket != null) env = attempt(routeTicket = ticket)
        }
        return env
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
        }
        val payload = JsonObject().apply {
            addProperty("_p", AesCbc.encrypt(plain.toString(), key))
        }
        return payload.toString().toRequestBody(JSON_MEDIA)
    }

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
