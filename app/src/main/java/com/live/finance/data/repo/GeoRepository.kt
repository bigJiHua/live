package com.live.finance.data.repo

import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.GeoPlace

/**
 * 地理位置（后端代理，对应 web `GET /api/v1/geo/regeo` 与 `GET /api/v1/geo/ip`）。
 *
 * 需求拍板（2026-09-18）：**原生不接高德 SDK**，高德逆地理用 api 侧已接好的 `/geo/regeo`；
 * 纯 GPS 坐标由 `core/loc/GpsLocation.kt` 本地取，不经后端（对齐 web `utils/geo/browser.js`）。
 */
interface GeoRepository {
    /** 逆地理：GPS 坐标 → 地址（后端高德优先，失败回退 Nominatim）。 */
    suspend fun reverseGeocode(lng: Double, lat: Double): ApiResult<GeoPlace>
    /** IP 定位（后端高德 IP 定位，取客户端公网 IP）。 */
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

    override suspend fun ipLocation(): ApiResult<GeoPlace> =
        client.get("/geo/ip").toResult().map { data ->
            val o = data as? JsonObject ?: JsonObject()
            GeoPlace(
                address = if (o.has("address") && !o.get("address").isJsonNull) o.get("address").asString else "",
                lat = runCatching { if (o.has("lat") && !o.get("lat").isJsonNull) o.get("lat").asDouble else 0.0 }.getOrDefault(0.0),
                lng = runCatching { if (o.has("lng") && !o.get("lng").isJsonNull) o.get("lng").asDouble else 0.0 }.getOrDefault(0.0),
                source = if (o.has("source") && !o.get("source").isJsonNull) o.get("source").asString else "",
            )
        }
}