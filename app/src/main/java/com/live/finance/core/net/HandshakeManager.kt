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
                val url = AppConfig.url(AppConfig.HANDSHAKE_PATH) + "?fp=" + device.fp
                val req = Request.Builder().url(url).get().apply {
                    device.headers().forEach { (k, v) -> header(k, v) }
                }.build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    val obj = runCatching { gson.fromJson(text, JsonObject::class.java) }
                        .getOrNull() ?: JsonObject()
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
            }
        }.onFailure { invalidate() }
    }
}
