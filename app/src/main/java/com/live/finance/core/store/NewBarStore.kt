package com.live.finance.core.store

import android.content.Context
import com.live.finance.core.glass.NewBarSpec

/**
 * 新版独立底部 Tab（NewLiquidGlassTabBar）参数的持久层（2026-10-27）。
 * 与 [GlassStore] 同款 SharedPreferences 方案：读写都是同步 Float/Int，
 * AppGraph.init() 直接 load()。旧键残留在 prefs 里无害（不再读）。
 */
class NewBarStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): NewBarSpec {
        val d = NewBarSpec()
        return NewBarSpec(
            blurPx = prefs.getFloat(KEY_BLUR_PX, d.blurPx),
            tintDark = prefs.getFloat(KEY_TINT_DARK, d.tintDark),
            tintLight = prefs.getFloat(KEY_TINT_LIGHT, d.tintLight),
            refraction = prefs.getFloat(KEY_REFRACTION, d.refraction),
            dispersion = prefs.getFloat(KEY_DISPERSION, d.dispersion),
            fresnel = prefs.getFloat(KEY_FRESNEL, d.fresnel),
            lensCenter = prefs.getFloat(KEY_LENS_CENTER, d.lensCenter),
            lensEdge = prefs.getFloat(KEY_LENS_EDGE, d.lensEdge),
            lensBandDp = prefs.getFloat(KEY_LENS_BAND_DP, d.lensBandDp),
            capsuleDark = prefs.getFloat(KEY_CAPSULE_DARK, d.capsuleDark),
            capsuleLight = prefs.getFloat(KEY_CAPSULE_LIGHT, d.capsuleLight),
            shadowAlpha = prefs.getInt(KEY_SHADOW_ALPHA, d.shadowAlpha),
        ).clamped()
    }

    fun save(spec: NewBarSpec) {
        val s = spec.clamped()
        prefs.edit()
            .putFloat(KEY_BLUR_PX, s.blurPx)
            .putFloat(KEY_TINT_DARK, s.tintDark)
            .putFloat(KEY_TINT_LIGHT, s.tintLight)
            .putFloat(KEY_REFRACTION, s.refraction)
            .putFloat(KEY_DISPERSION, s.dispersion)
            .putFloat(KEY_FRESNEL, s.fresnel)
            .putFloat(KEY_LENS_CENTER, s.lensCenter)
            .putFloat(KEY_LENS_EDGE, s.lensEdge)
            .putFloat(KEY_LENS_BAND_DP, s.lensBandDp)
            .putFloat(KEY_CAPSULE_DARK, s.capsuleDark)
            .putFloat(KEY_CAPSULE_LIGHT, s.capsuleLight)
            .putInt(KEY_SHADOW_ALPHA, s.shadowAlpha)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "finance_new_bar"
        private const val KEY_BLUR_PX = "blur_px"
        private const val KEY_TINT_DARK = "tint_dark"
        private const val KEY_TINT_LIGHT = "tint_light"
        private const val KEY_REFRACTION = "refraction"
        private const val KEY_DISPERSION = "dispersion"
        private const val KEY_FRESNEL = "fresnel"
        private const val KEY_LENS_CENTER = "lens_center"
        private const val KEY_LENS_EDGE = "lens_edge"
        private const val KEY_LENS_BAND_DP = "lens_band_dp"
        private const val KEY_CAPSULE_DARK = "capsule_dark"
        private const val KEY_CAPSULE_LIGHT = "capsule_light"
        private const val KEY_SHADOW_ALPHA = "shadow_alpha"
    }
}
