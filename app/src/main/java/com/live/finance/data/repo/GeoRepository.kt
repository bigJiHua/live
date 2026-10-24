package com.live.finance.data.repo

import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.gson
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.GeoPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 地理位置（后端代理，对应 web `GET /api/v1/geo/regeo` 与 `GET /api/v1/geo/ip`）。
 *
 * 需求拍板（2026-09-18）：**原生不接高德 SDK**，高德逆地理用 api 侧已接好的 `/geo/regeo`；
 * 纯 GPS 坐标由 `core/loc/GpsLocation.kt` 本地取，不经后端（对齐 web `utils/geo/browser.js`）。
 */
interface GeoRepository {
    /** 逆地理：GPS 坐标 → 地址（后端高德优先，失败回退 Nominatim）。 */
    suspend fun reverseGeocode(lng: Double, lat: Double): ApiResult<GeoPlace>
    /** IP 定位 — 直调 ip.sb / ipinfo.io（对齐 web `utils/geo/ip.js`，不走后端）。 */
    suspend fun ipLocation(): ApiResult<GeoPlace>
}

class FakeGeoRepository : GeoRepository {
    override suspend fun reverseGeocode(lng: Double, lat: Double): ApiResult<GeoPlace> =
        ApiResult.Ok(GeoPlace(address = "浙江省杭州市西湖区", lat = lat, lng = lng, source = "amap"), "查询成功")

    override suspend fun ipLocation(): ApiResult<GeoPlace> =
        ApiResult.Ok(GeoPlace(address = "浙江 杭州", lat = 30.2741, lng = 120.1551, source = "amap"), "查询成功")
}

class RemoteGeoRepository(private val client: ApiClient) : GeoRepository {
    override suspend fun reverseGeocode(lng: Double, lat: Double): ApiResult<GeoPlace> =
        client.get("/geo/regeo", mapOf("lng" to lng.toString(), "lat" to lat.toString()))
            .toResult()
            .map { data ->
                val o = data as? JsonObject ?: JsonObject()
                GeoPlace(
                    address = if (o.has("address") && !o.get("address").isJsonNull) o.get("address").asString else "",
                    lat = lat,
                    lng = lng,
                    source = if (o.has("source") && !o.get("source").isJsonNull) o.get("source").asString else "",
                )
            }

    /** IP 定位 — 直调 ip.sb / ipinfo.io（对齐 web `utils/geo/ip.js`，不走后端）。
     *  直调才能拿到设备的真实公网 IP；走后端 /geo/ip 拿的是 req.ip（内网/反代 IP），永远失败。 */
    override suspend fun ipLocation(): ApiResult<GeoPlace> = withContext(Dispatchers.IO) {
        // 1. ip.sb（设备出口 IP = 用户公网 IP）
        try {
            val req = Request.Builder().url("https://api.ip.sb/geoip")
                .header("Accept", "application/json").get().build()
            client.http.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    val o = runCatching { gson.fromJson(resp.body?.string() ?: "", JsonObject::class.java) }.getOrNull()
                    if (o != null) {
                        val country = o.gs("country"); val region = o.gs("region"); val city = o.gs("city")
                        if (country.isNotBlank() && region.isNotBlank() && city.isNotBlank()) {
                            return@withContext ApiResult.Ok(GeoPlace("$region $city", o.gd("latitude"), o.gd("longitude"), "ip.sb"), "查询成功")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. ipinfo.io（免费限额）
        try {
            val req = Request.Builder().url("https://ipinfo.io").get().build()
            client.http.newCall(req).execute().use { resp ->
                if (resp.code in 200..299) {
                    val o = runCatching { gson.fromJson(resp.body?.string() ?: "", JsonObject::class.java) }.getOrNull()
                    if (o != null) {
                        val region = o.gs("region"); val city = o.gs("city")
                        val address = listOf(region, city).filter { it.isNotBlank() }.joinToString(" ")
                        if (address.isNotBlank()) {
                            var lat = 0.0; var lng = 0.0
                            o.get("loc")?.takeIf { !it.isJsonNull }?.asString?.split(",")?.let {
                                if (it.size >= 2) { lat = it[0].trim().toDoubleOrNull() ?: 0.0; lng = it[1].trim().toDoubleOrNull() ?: 0.0 }
                            }
                            return@withContext ApiResult.Ok(GeoPlace(address, lat, lng, "ipinfo.io"), "查询成功")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        ApiResult.Fail("IP定位失败")
    }

    private fun JsonObject.gs(k: String): String = if (has(k) && !get(k).isJsonNull) get(k).asString else ""
    private fun JsonObject.gd(k: String): Double = runCatching { if (has(k) && !get(k).isJsonNull) get(k).asDouble else 0.0 }.getOrDefault(0.0)
}