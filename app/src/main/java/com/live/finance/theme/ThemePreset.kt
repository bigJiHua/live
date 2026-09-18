package com.live.finance.theme

import androidx.compose.ui.graphics.Color

/** 色系分组，设置页按「绿→蓝→红→金→中性」排序。对应 web `useUiTheme.js` 的 THEME_HUE。 */
enum class ThemeHue { Green, Blue, Red, Gold, Neutral }

/** 预设的底色三档（对应 web preset.bg）。 */
data class ThemeBg(val primary: Color, val secondary: Color, val tertiary: Color)

/** 预设的文字四档（对应 web preset.text）。 */
data class ThemeText(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val placeholder: Color,
)

/** 预设的状态四色（对应 web preset.status / preset.statusText）。 */
data class ThemeStatus(val success: Color, val warning: Color, val danger: Color, val info: Color)

/**
 * 一套 UI 主题预设 —— 逐字段对齐 web `web/src/composables/useUiTheme.js` 的 `THEME_PRESETS`。
 *
 * `bg / text / border / status / statusText / buttonPrimaryText` 是**可选局部覆盖**，
 * 缺省时由 [resolveTokens] 按「浅色 / 深色」取 web 的默认值（STATUS_COMP/STATUS_TEXT、dark 灰阶等）。
 */
data class ThemePreset(
    val key: String,
    val name: String,
    /** 对应 web preset.mode === 'dark'（预设自带深浅，系统默认时才按系统深浅取 key） */
    val dark: Boolean,
    val primary: Color,
    /** --theme-primary-grad */
    val grad: Color,
    /** 设置页色块渐变两端（web preset.preview 的 linear-gradient(135deg, start, end)） */
    val previewStart: Color,
    val previewEnd: Color,
    val bg: ThemeBg? = null,
    val text: ThemeText? = null,
    val border: Color? = null,
    val status: ThemeStatus? = null,
    val statusText: ThemeStatus? = null,
    val buttonPrimaryText: Color? = null,
) {
    /**
     * 对应 web 的 `monochrome`：默认三套浅色主题（绿/蓝/靛）保留五颜六色语义图标，
     * 其余（深色 / 特殊色）统一成主色（web `html[data-theme-mono="1"]` 规则）。
     */
    val monochrome: Boolean get() = key !in FIXED_ICON_KEYS

    val hue: ThemeHue get() = HUE[key] ?: ThemeHue.Neutral

    companion object {
        /** web `COLOR_ICON_THEMES` */
        val FIXED_ICON_KEYS = setOf("green", "blue", "indigo")

        private val HUE: Map<String, ThemeHue> = mapOf(
            "green" to ThemeHue.Green,
            "emerald" to ThemeHue.Green,
            "wise" to ThemeHue.Green,
            "blue" to ThemeHue.Blue,
            "indigo" to ThemeHue.Blue,
            "navygold" to ThemeHue.Blue,
            "burgundy" to ThemeHue.Red,
            "blackgold" to ThemeHue.Gold,
            "mono" to ThemeHue.Neutral,
            "titanium" to ThemeHue.Neutral,
        )
    }
}

/** 预设清单（顺序与 web `THEME_PRESETS` 一致）。 */
object ThemePresets {
    /** 「系统默认」：跟随系统深浅在 [DEFAULT_DARK] / [DEFAULT_LIGHT] 之间切换 */
    const val CHOICE_SYSTEM = "system"
    const val DEFAULT_LIGHT = "indigo"
    const val DEFAULT_DARK = "blackgold"

    fun systemKey(systemDark: Boolean): String = if (systemDark) DEFAULT_DARK else DEFAULT_LIGHT

    val ALL: List<ThemePreset> = listOf(
        // ── 白底配色 ──
        ThemePreset(
            key = "green", name = "绿", dark = false,
            primary = Color(0xFF07C160), grad = Color(0xFF06AD56),
            previewStart = Color(0xFF07C160), previewEnd = Color(0xFF06AD56),
        ),
        ThemePreset(
            key = "blue", name = "蓝", dark = false,
            primary = Color(0xFF1989FA), grad = Color(0xFF1976D2),
            previewStart = Color(0xFF1989FA), previewEnd = Color(0xFF1976D2),
        ),
        ThemePreset(
            key = "indigo", name = "靛", dark = false,
            primary = Color(0xFF3A66E0), grad = Color(0xFF2A4FB8),
            previewStart = Color(0xFF3A66E0), previewEnd = Color(0xFF2A4FB8),
        ),
        ThemePreset(
            key = "mono", name = "素", dark = false,
            primary = Color(0xFF1A1A1A), grad = Color(0xFF000000),
            previewStart = Color(0xFF1A1A1A), previewEnd = Color(0xFF000000),
            bg = ThemeBg(Color(0xFFF4F4F5), Color(0xFFFFFFFF), Color(0xFFECECEC)),
            text = ThemeText(Color(0xFF1A1A1A), Color(0xFF595959), Color(0xFF8C8C8C), Color(0xFFBFBFBF)),
            border = Color(0xFFE0E0E0),
            status = ThemeStatus(Color(0xFF1A1A1A), Color(0xFF8C8C8C), Color(0xFFC0392B), Color(0xFF8C8C8C)),
            statusText = ThemeStatus(Color(0xFF1A1A1A), Color(0xFF8C8C8C), Color(0xFFC0392B), Color(0xFF8C8C8C)),
        ),
        // ── 黑底配色 ──
        ThemePreset(
            key = "blackgold", name = "黑金", dark = true,
            primary = Color(0xFFC9A86A), grad = Color(0xFFA8863F),
            previewStart = Color(0xFFC9A86A), previewEnd = Color(0xFFA8863F),
        ),
        ThemePreset(
            key = "navygold", name = "藏青金", dark = true,
            primary = Color(0xFFC9A86A), grad = Color(0xFFA8863F),
            previewStart = Color(0xFF0C1626), previewEnd = Color(0xFFC9A86A),
            bg = ThemeBg(Color(0xFF0C1626), Color(0xFF13233B), Color(0xFF1D3150)),
            text = ThemeText(Color(0xFFEAF0FB), Color(0xFFA9B8D0), Color(0xFF7D8FAE), Color(0xFF54688C)),
            border = Color(0xFF24395C),
            status = ThemeStatus(Color(0xFFC9A86A), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFF7D8FAE)),
            statusText = ThemeStatus(Color(0xFFE6C98A), Color(0xFFE0B878), Color(0xFFFF8A8A), Color(0xFF9FB0CC)),
        ),
        ThemePreset(
            key = "titanium", name = "钛金属", dark = true,
            primary = Color(0xFFD8D8DC), grad = Color(0xFFB9B9BE),
            previewStart = Color(0xFF232326), previewEnd = Color(0xFFD8D8DC),
            bg = ThemeBg(Color(0xFF161618), Color(0xFF232326), Color(0xFF2E2E32)),
            text = ThemeText(Color(0xFFF2F2F4), Color(0xFFAEAEB2), Color(0xFF8A8A90), Color(0xFF5A5A5E)),
            border = Color(0x14FFFFFF),
            status = ThemeStatus(Color(0xFFD8D8DC), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFF8A8A90)),
            statusText = ThemeStatus(Color(0xFFE6E6EA), Color(0xFFE0B878), Color(0xFFFF8A8A), Color(0xFFB6B6BA)),
            buttonPrimaryText = Color(0xFF1A1A1A),
        ),
        ThemePreset(
            key = "emerald", name = "墨玉绿", dark = true,
            primary = Color(0xFF3FBF8F), grad = Color(0xFF2F9B70),
            previewStart = Color(0xFF0A1812), previewEnd = Color(0xFF3FBF8F),
            bg = ThemeBg(Color(0xFF0A1812), Color(0xFF0F2419), Color(0xFF16331F)),
            text = ThemeText(Color(0xFFE6F5EC), Color(0xFFA4C9B1), Color(0xFF7BA88F), Color(0xFF4D6B58)),
            border = Color(0xFF1F3A28),
            status = ThemeStatus(Color(0xFF3FBF8F), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFF7BA88F)),
            statusText = ThemeStatus(Color(0xFF7FE0B5), Color(0xFFE0B878), Color(0xFFFF8A8A), Color(0xFFA8D4BB)),
        ),
        ThemePreset(
            key = "wise", name = "悠森绿", dark = true,
            primary = Color(0xFF9FE870), grad = Color(0xFF4A7C2C),
            previewStart = Color(0xFF163300), previewEnd = Color(0xFF9FE870),
            bg = ThemeBg(Color(0xFF163300), Color(0xFF1E4200), Color(0xFF29520E)),
            text = ThemeText(Color(0xFFF2F9EA), Color(0xFFB6D8A0), Color(0xFF8AB06E), Color(0xFF57733F)),
            border = Color(0xFF2C5014),
            status = ThemeStatus(Color(0xFF9FE870), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFF8AB06E)),
            statusText = ThemeStatus(Color(0xFFC4F0A0), Color(0xFFE0B878), Color(0xFFFF8A8A), Color(0xFFB6D8A0)),
            buttonPrimaryText = Color(0xFF163300),
        ),
        ThemePreset(
            key = "burgundy", name = "酒红香槟", dark = true,
            primary = Color(0xFFC9A86A), grad = Color(0xFFA8863F),
            previewStart = Color(0xFF1F0810), previewEnd = Color(0xFFC9A86A),
            bg = ThemeBg(Color(0xFF1F0810), Color(0xFF2E0C18), Color(0xFF3D1222)),
            text = ThemeText(Color(0xFFF7E9EC), Color(0xFFCDA6B1), Color(0xFFA07D89), Color(0xFF66414C)),
            border = Color(0xFF40182A),
            status = ThemeStatus(Color(0xFFC9A86A), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFFA07D89)),
            statusText = ThemeStatus(Color(0xFFE6C98A), Color(0xFFE0B878), Color(0xFFFF8A8A), Color(0xFFD3AAB4)),
        ),
    )

    fun byKey(key: String): ThemePreset? = ALL.firstOrNull { it.key == key }

    /** 解析当前实际生效的预设：`system` → 按系统深浅取默认（浅=靛、深=黑金）。 */
    fun resolve(choice: String, systemDark: Boolean): ThemePreset =
        (if (choice == CHOICE_SYSTEM) byKey(systemKey(systemDark)) else byKey(choice))
            ?: byKey(DEFAULT_LIGHT)!!
}
