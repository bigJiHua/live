package com.live.finance.core.net

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.live.finance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 复刻 web `utils/request/client.js` 的 collectClientData：加密写请求的明文信封里
 * 平铺一层设备/网络上下文（web 拦截器的 `{...deviceData}`）。后端解密后从 req.body
 * 顶层读这些字段写登录日志（model/log.js）—— 缺失时落 '0.0.0.0'/'未知'/空串。
 *
 * IP/归属地/ISP 对齐 web `utils/network.js`：客户端直调 api.ip.sb/geoip（5s 超时），
 * 失败回退 ipinfo.io，再失败 ip="unknown"（与 web 同口径）；结果缓存 30 分钟。
 * web 的 connectionType/loginLat/loginLng 后端 user_log 表不落库，这里不带。
 */
class ClientContext(private val device: DeviceProfile) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()

    @Volatile
    private var cached: JsonObject? = null

    @Volatile
    private var cachedAt = 0L

    suspend fun data(): JsonObject {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < CACHE_MS }?.let { return it }
        return mutex.withLock {
            val now2 = System.currentTimeMillis()
            cached?.takeIf { now2 - cachedAt < CACHE_MS }?.let { return it }
            // ⚠ buildBody 在调用方（登录页 = Main）协程里执行；这里的 OkHttp execute 是阻塞调用，
            //    不切 IO 会抛 NetworkOnMainThreadException，被 fetchJson 的 runCatching 吞掉
            //    ⇒ loginLocation 永远为空（登录日志地点「未知」）。必须切 IO。
            val external = withContext(Dispatchers.IO) { fetchExternal() }
            staticFields().entrySet().forEach { external.add(it.key, it.value) }
            external.also { cached = it; cachedAt = now2 }
        }
    }

    /** 对齐 web fetchFromIpSb：ip.sb → ipinfo.io 回退，都失败 ip="unknown"。 */
    private fun fetchExternal(): JsonObject {
        val o = JsonObject()
        val n = runCatching { fetchJson("https://api.ip.sb/geoip") }.getOrNull()
        val m = runCatching { fetchJson("https://ipinfo.io") }.getOrNull()
        val ip: String
        val country: String
        val region: String
        val city: String
        val isp: String
        if (n != null) {
            ip = n.str("ip"); country = n.str("country"); region = n.str("region")
            city = n.str("city"); isp = n.str("isp")
        } else if (m != null) {
            ip = m.str("ip"); country = m.str("country"); region = m.str("region")
            city = m.str("city")
            isp = m.str("org").replace(Regex("^AS\\d+\\s*"), "")
        } else {
            ip = "unknown"; country = ""; region = ""; city = ""; isp = ""
        }
        o.addProperty("loginIp", ip.ifBlank { "unknown" })
        o.addProperty("loginLocation", listOf(country, region, city).filter { it.isNotBlank() }.joinToString("-"))
        o.addProperty("loginIsp", isp)
        return o
    }

    private fun fetchJson(url: String): JsonObject? = runCatching {
        val req = okhttp3.Request.Builder().url(url)
            .header("Accept", "application/json")
            .get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JsonParser.parseString(resp.body?.string()).asJsonObject
        }
    }.getOrNull()

    private fun staticFields(): JsonObject = JsonObject().apply {
        addProperty("userAgent", device.userAgent)
        addProperty("osInfo", "Android ${Build.VERSION.RELEASE}")
        // 原生无浏览器实体：负责人拍板填 App 版本号（2026-09-21）
        addProperty("browserInfo", "App ${BuildConfig.VERSION_NAME}")
        addProperty("deviceModel", Build.MODEL)
        addProperty("deviceType", deviceType())
        addProperty("fingerprint", device.fp)
        val dm = Resources.getSystem().displayMetrics
        addProperty("viewport", "${(dm.widthPixels / dm.density).toInt()}x${(dm.heightPixels / dm.density).toInt()}")
        addProperty("pixelRatio", dm.density)
        val lang = Locale.getDefault().toLanguageTag()
        addProperty("language", lang)
        addProperty("languages", lang)
    }

    /** web `ua.device.type`（mobile/tablet/desktop）的等价判定：大屏算 tablet。 */
    private fun deviceType(): String {
        val size = Resources.getSystem().configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return if (size >= Configuration.SCREENLAYOUT_SIZE_LARGE) "tablet" else "mobile"
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    companion object {
        private const val CACHE_MS = 30 * 60 * 1000L
    }
}
