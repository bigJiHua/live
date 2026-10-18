package com.live.finance.data.model

/**
 * 定位结果（对应后端 `GET /geo/regeo` 与 `GET /geo/ip` 的 data）。
 * web 真相源：`web/src/utils/geo/` 下的 browser.js / amap.js / ip.js 三路统一成 {address, lat, lng}。
 *
 * 注：精确定位（纯 GPS）**不走后端**，地址是 `"lat, lng"` 坐标串，见 `core/loc/GpsLocation.kt`。
 */
data class GeoPlace(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** `amap` / `nominatim` / `none`（后端 regeo）；`amap` / `none`（后端 ip）。 */
    val source: String = "",
)