package com.live.finance.core.store

import android.content.Context
import com.live.finance.core.glass.BarGlassSpec

/**
 * 底部悬浮岛玻璃参数的持久层（2026-10-19）。
 *
 * 为什么用 SharedPreferences 而不是 DataStore：
 *  这里是一组纯 Float，读写都是同步的 —— `AppGraph` 在 `init()` 里直接 `load()` 即可，
 *  不需要 suspend/Flow（ThemeStore 用 DataStore 是因为它有旧键迁移与多类型默认值逻辑）。
 *
 * 语义分工：
 *  - **实时生效**：实验室每次调参只更新 `AppGraph.barGlass` 的内存 State，不写盘；
 *  - **保存设定**：点「保存设定」才调用 [save]，之后重启 App 仍用这套值。
 */
class GlassStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): BarGlassSpec {
        val d = BarGlassSpec()
        val spec = BarGlassSpec(
            alphaLight = prefs.getFloat(KEY_ALPHA_LIGHT, d.alphaLight),
            alphaDark = prefs.getFloat(KEY_ALPHA_DARK, d.alphaDark),
            blurDp = prefs.getFloat(KEY_BLUR_DP, d.blurDp),
            saturation = prefs.getFloat(KEY_SATURATION, d.saturation),
            highlight = prefs.getFloat(KEY_HIGHLIGHT, d.highlight),
            specular = prefs.getFloat(KEY_SPECULAR, d.specular),
            capsuleLight = prefs.getFloat(KEY_CAPSULE_LIGHT, d.capsuleLight),
            capsuleDark = prefs.getFloat(KEY_CAPSULE_DARK, d.capsuleDark),
            collapseOnScroll = prefs.getBoolean(KEY_COLLAPSE_ON_SCROLL, d.collapseOnScroll),
        ).clamped()

        // 一次性迁移（2026-10-19 二次）：负责人「BG 背景还有透视」⇒ 旧存档里 blur 2dp 太清、
        // rim_width 8dp 还会画折射环；统一改成「模糊 20dp + 无环」，之后可在实验室里再调。
        if (!prefs.getBoolean(KEY_FROST_V2, false)) {
            val migrated = spec.copy(
                blurDp = maxOf(spec.blurDp, 20f),
            ).clamped()
            save(migrated)
            prefs.edit().putBoolean(KEY_FROST_V2, true).apply()
            return migrated
        }

        return spec
    }

    fun save(spec: BarGlassSpec) {
        val s = spec.clamped()
        prefs.edit()
            .putFloat(KEY_ALPHA_LIGHT, s.alphaLight)
            .putFloat(KEY_ALPHA_DARK, s.alphaDark)
            .putFloat(KEY_BLUR_DP, s.blurDp)
            .putFloat(KEY_SATURATION, s.saturation)
            .putFloat(KEY_HIGHLIGHT, s.highlight)
            .putFloat(KEY_SPECULAR, s.specular)
            .putFloat(KEY_CAPSULE_LIGHT, s.capsuleLight)
            .putFloat(KEY_CAPSULE_DARK, s.capsuleDark)
            .putBoolean(KEY_COLLAPSE_ON_SCROLL, s.collapseOnScroll)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "finance_glass"
        private const val KEY_ALPHA_LIGHT = "alpha_light"
        private const val KEY_ALPHA_DARK = "alpha_dark"
        private const val KEY_BLUR_DP = "blur_dp"
        private const val KEY_SATURATION = "saturation"
        private const val KEY_HIGHLIGHT = "highlight"
        private const val KEY_SPECULAR = "specular"
        private const val KEY_CAPSULE_LIGHT = "capsule_light"
        private const val KEY_CAPSULE_DARK = "capsule_dark"
        /** 是否因滚动收起底栏（默认 true）。 */
        private const val KEY_COLLAPSE_ON_SCROLL = "collapse_on_scroll"
        /** 「去透视」迁移标记：模糊提到 20dp、折射环宽归零（一次性；见 [load]）。 */
        private const val KEY_FROST_V2 = "frost_v2"
    }
}
