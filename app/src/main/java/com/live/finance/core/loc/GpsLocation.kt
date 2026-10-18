package com.live.finance.core.loc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** 一次 GPS 取点结果。`reason` 取值对齐 web `utils/geo/permission.js`。 */
data class GpsFix(
    val granted: Boolean,
    val lat: Double,
    val lng: Double,
    /** granted=false 时：`denied` / `unsupported` / `unavailable` / `timeout`。 */
    val reason: String = "",
) {
    /** web `formatCoord` = `${lat.toFixed(6)}, ${lng.toFixed(6)}`。 */
    val address: String get() = String.format(Locale.US, "%.6f, %.6f", lat, lng)
}

/**
 * 请求一次 GPS —— 逐字对齐 web `utils/geo/permission.js#requestGpsOnce()` 的取数语义：
 * `enableHighAccuracy=false`（用网络/被动定位，省电且不易 timeout）、`timeout=8000`、
 * `maximumAge=30000`（30 秒内的缓存点直接复用）、**只请求一次、不重试**。
 *
 * 实现走 `androidx.core.location.LocationManagerCompat.getCurrentLocation`，
 * minSdk 24 全版本可用，**不依赖 Google Play Services**。
 */
suspend fun requestGpsOnce(ctx: Context): GpsFix = withContext(Dispatchers.Main) {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return@withContext GpsFix(false, 0.0, 0.0, "denied")

    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext GpsFix(false, 0.0, 0.0, "unsupported")

    // enableHighAccuracy=false：优先网络定位，其次 GPS，最后被动
    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val provider = providers.firstOrNull() ?: return@withContext GpsFix(false, 0.0, 0.0, "unavailable")

    // maximumAge: 30000 —— 缓存点足够新就直接用
    val now = System.currentTimeMillis()
    for (p in providers) {
        val last = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (now - last.time <= 30_000L) return@withContext GpsFix(true, last.latitude, last.longitude)
    }

    val fix = withTimeoutOrNull(8_000L) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { runCatching { signal.cancel() } }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    lm, provider, signal, ContextCompat.getMainExecutor(ctx),
                ) { loc ->
                    if (!cont.isActive) return@getCurrentLocation
                    cont.resume(
                        if (loc == null) GpsFix(false, 0.0, 0.0, "unavailable")
                        else GpsFix(true, loc.latitude, loc.longitude),
                    )
                }
            }.onFailure {
                if (cont.isActive) cont.resume(GpsFix(false, 0.0, 0.0, "unavailable"))
            }
        }
    }
    fix ?: GpsFix(false, 0.0, 0.0, "timeout")
}