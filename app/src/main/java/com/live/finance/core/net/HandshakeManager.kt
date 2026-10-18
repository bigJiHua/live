package com.live.finance.core.net

import com.live.finance.core.AppConfig
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 会话级 AES Key 管理，对应 web `utils/request/handshake.js`：
 *  GET /auth/handshake?fp= → { status:200, key(64hex), scene, rsaPublicKey }
 *  key 后端有效期 2h（device_crypto 按 fingerprint+scene 存）。这里同样带过期，过期或 401/解密失败时重取。
 *  并发用 Mutex 锁，避免重复握手（web 用 handshakePromise）。
 */
class HandshakeManager(
    private val client: OkHttpClient,
    private val device: DeviceProfile,
) {
    private val mutex = Mutex()

    @Volatile private var aesKey: String? = null
    @Volatile private var rsaPublicKey: String? = null
    @Volatile private var expireAt: Long = 0L

    fun hasKey(): Boolean = aesKey != null && System.currentTimeMillis() < expireAt

    fun currentKeyOrNull(): String? = if (hasKey()) aesKey else null

    suspend fun aesKey(): String? = ensure().getOrNull()

    suspend fun rsa(): String? = try { ensure(); rsaPublicKey } catch (_: Exception) { null }

    /** 强制失效当前 key（401/解密失败时调用）。 */
    fun invalidate() { aesKey = null; expireAt = 0L }

    private suspend fun ensure(): Result<String> = mutex.withLock {
        if (hasKey()) return@withLock Result.success(aesKey!!)
        runCatching {
            withContext(Dispatchers.IO) {
                // 🔄 P2-18（2026-10-08）：握手通道 8401 一次性自愈。
                //   sendOnce() 每次重建请求 ⇒ 全新 nonce（device.headers() 的约定）。
                //   被判重放时重发一次；仍失败才 error（对齐 web handshake.js 的 axiosWithNonceRetry）。
                fun sendOnce(): JsonObject {
                    // 🛡 2026-10-17：构建期无默认站点；未接入时 AppConfig.url() 会拼出相对路径，
                    //   Request.Builder().url(...) 抛 IllegalArgumentException。
                    //   这里显式失败（外层 runCatching 会吞掉并 invalidate），避免无意义的重试。
                    if (!AppConfig.hasSite) error("尚未接入站点")
                    val url = AppConfig.url(AppConfig.HANDSHAKE_PATH) + "?fp=" + device.fp
                    val req = Request.Builder().url(url).get().apply {
                        device.headers().forEach { (k, v) -> header(k, v) }
                    }.build()
                    return client.newCall(req).execute().use { resp ->
                        val text = resp.body?.string() ?: ""
                        runCatching { gson.fromJson(text, JsonObject::class.java) }
                            .getOrNull() ?: JsonObject()
                    }
                }
                var obj = sendOnce()
                if (obj.get("code")?.takeIf { !it.isJsonNull }?.asInt == AppConfig.Pin.NONCE_REPLAY) {
                    obj = sendOnce()
                }
                if (obj.get("status")?.asInt == 200) {
                    val k = obj.get("key")?.asString ?: error("握手未返回 key")
                    aesKey = k
                    rsaPublicKey = obj.get("rsaPublicKey")?.asString
                    expireAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
                    k
                } else {
                    error("Handshake Failed: ${obj.get("message")?.asString}")
                }
            }
        }.onFailure { invalidate() }
    }
}
