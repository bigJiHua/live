package com.live.finance.theme

import androidx.compose.ui.graphics.Color
import com.live.vant.theme.VantColors

/**
 * 语义彩色图标（对应 web `style.css` 里的 `.blue/.green/.orange/.red/.purple/.cyan/.teal/.pink/.gold/.gray`）。
 * 只有 [Blue] / [Gray] 跟随主题（web 里它们分别是 `var(--theme-primary)` / `var(--theme-text-tertiary)`），其余为固定色。
 */
enum class SemanticRole(val fixed: Color) {
    Blue(Color(0xFF3A66E0)),
    Green(Color(0xFF07C160)),
    Orange(Color(0xFFFF976A)),
    Red(Color(0xFFEE0A24)),
    Purple(Color(0xFF7232DD)),
    Cyan(Color(0xFF00BCD4)),
    Teal(Color(0xFF009688)),
    Pink(Color(0xFFE91E63)),
    Gold(Color(0xFFFFB300)),
    Gray(Color(0xFF969799)),
}

/**
 * 一次主题解析的完整产物（= web `buildVars(preset)` 注入 `:root` 的那套变量，Compose 版）。
 *
 * 分两层：
 *  - 业务 token（primary/grad/primaryLight/status/statusText/bg/text/border…）→ 页面直接用；
 *  - [vant]：投影成 [VantColors]，供 `vant-ui` 的 Van* 组件与既有 `LocalAppColors.current.xxx` 使用。
 */
data class AppTokens(
    val presetKey: String,
    val presetName: String,
    val isDark: Boolean,
    /** web `data-theme-mono`：语义彩色图标是否统一成主色 */
    val monochrome: Boolean,
    val hue: ThemeHue,
    val primary: Color,
    val grad: Color,
    val primaryLight: Color,
    val buttonPrimaryText: Color,
    val bgPage: Color,
    val bgCard: Color,
    val bgThird: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val successText: Color,
    val warningText: Color,
    val dangerText: Color,
    val infoText: Color,
    val vant: VantColors,
) {
    /** 语义彩色图标取色：monochrome 主题下一律主色（对齐 web `html[data-theme-mono="1"]` 规则）。 */
    fun semanticIcon(role: SemanticRole): Color = when {
        monochrome -> primary
        role == SemanticRole.Blue -> primary
        role == SemanticRole.Gray -> textTertiary
        else -> role.fixed
    }
}

// ── web useUiTheme.js 的默认值常量（预设未覆盖时按深浅取） ──

private val LIGHT_STATUS = ThemeStatus(
    Color(0xFF07C160), Color(0xFFFF976A), Color(0xFFEE0A24), Color(0xFF969799),
)
private val DARK_STATUS = ThemeStatus(
    Color(0xFF2F9B70), Color(0xFFC9A05A), Color(0xFFE5484D), Color(0xFF7A7A7A),
)
private val LIGHT_STATUS_TEXT = ThemeStatus(
    Color(0xFF0A7A45), Color(0xFFB45309), Color(0xFFC0102A), Color(0xFF646566),
)
private val DARK_STATUS_TEXT = ThemeStatus(
    Color(0xFF5FD6A0), Color(0xFFF0B267), Color(0xFFFF6B6B), Color(0xFFA0A0A0),
)

private val LIGHT_BG = ThemeBg(Color(0xFFF7F8FA), Color(0xFFFFFFFF), Color(0xFFF2F3F5))
private val DARK_BG = ThemeBg(Color(0xFF0F0F12), Color(0xFF1A1A1E), Color(0xFF26262B))

private val LIGHT_TEXT = ThemeText(Color(0xFF323233), Color(0xFF646566), Color(0xFF969799), Color(0xFFC8C9CC))
private val DARK_TEXT = ThemeText(Color(0xFFF0F0F0), Color(0xFFB0B0B0), Color(0xFF8A8A8A), Color(0xFF5A5A5A))

private val LIGHT_BORDER = Color(0xFFEBEDF0)
private val DARK_BORDER = Color(0x14FFFFFF)  // rgba(255,255,255,.08)

private val LIGHT_TABBAR = Triple(Color(0x9EFFFFFF), Color(0x0F000000), Color(0xFF7D7E80))
private val DARK_TABBAR = Triple(Color(0x8C121216), Color(0x1FFFFFFF), Color(0xFFB0B0B0))

/**
 * 「预设 + 系统深浅」→ 完整 token 集。逐条复刻 web `useUiTheme.buildVars()` 的取值与回退顺序。
 *
 * 少数 web 未随主题驱动的 Vant 变量（`--van-active-color`、`--van-progress-background`、
 * notice-bar 两色、dark 下的 nav 底色），此处按「深色可读」取值，避免深色主题下出现浅灰块/白底。
 */
fun resolveTokens(choice: String, systemDark: Boolean): AppTokens {
    val preset = ThemePresets.resolve(choice, systemDark)
    val dark = preset.dark

    val bg = preset.bg ?: if (dark) DARK_BG else LIGHT_BG
    val text = preset.text ?: if (dark) DARK_TEXT else LIGHT_TEXT
    val border = preset.border ?: if (dark) DARK_BORDER else LIGHT_BORDER
    val status = preset.status ?: if (dark) DARK_STATUS else LIGHT_STATUS
    val statusText = preset.statusText ?: if (dark) DARK_STATUS_TEXT else LIGHT_STATUS_TEXT
    val buttonPrimaryText = preset.buttonPrimaryText ?: if (dark) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    // web: --theme-primary-light = dark ? rgba(255,255,255,.08) : #eef3ff
    val primaryLight = if (dark) Color(0x14FFFFFF) else Color(0xFFEEF3FF)
    val tabbar = if (dark) DARK_TABBAR else LIGHT_TABBAR

    val vant = VantColors(
        primary = preset.primary,
        success = status.success,
        warning = status.warning,
        danger = status.danger,
        info = status.info,
        textPrimary = text.primary,
        textSecondary = text.secondary,
        textTertiary = text.tertiary,
        textPlaceholder = text.placeholder,
        border = border,
        bgPage = bg.primary,
        bgCard = bg.secondary,
        bgThird = bg.tertiary,
        active = bg.tertiary,
        overlay = Color(0xB3000000),
        switchOff = Color(0x29787880),
        toastBg = Color(0xB3000000),
        buttonDefaultBorder = border,
        iconDefault = text.primary,
        pickerMaskColor = bg.primary,
        primaryLight = primaryLight,
        progressBg = if (dark) bg.tertiary else Color(0xFFEBEDF0),
        noticeText = if (dark) Color(0xFFF0B267) else Color(0xFFED6A0C),
        noticeBg = if (dark) bg.tertiary else Color(0xFFFFFBE8),
        tabbarGlassBg = tabbar.first,
        tabbarGlassBorder = tabbar.second,
        tabbarText = tabbar.third,
        // web buildVars：navBg = dark ? bg.primary : bg.secondary（tab 栏 / 页头底色）
        navBarBg = if (dark) bg.primary else bg.secondary,
        primaryGrad = preset.grad,
        buttonPrimaryText = buttonPrimaryText,
        successText = statusText.success,
        warningText = statusText.warning,
        dangerText = statusText.danger,
        infoText = statusText.info,
    )

    return AppTokens(
        presetKey = preset.key,
        presetName = preset.name,
        isDark = dark,
        monochrome = preset.monochrome,
        hue = preset.hue,
        primary = preset.primary,
        grad = preset.grad,
        primaryLight = primaryLight,
        buttonPrimaryText = buttonPrimaryText,
        bgPage = bg.primary,
        bgCard = bg.secondary,
        bgThird = bg.tertiary,
        textPrimary = text.primary,
        textSecondary = text.secondary,
        textTertiary = text.tertiary,
        textPlaceholder = text.placeholder,
        border = border,
        success = status.success,
        warning = status.warning,
        danger = status.danger,
        info = status.info,
        successText = statusText.success,
        warningText = statusText.warning,
        dangerText = statusText.danger,
        infoText = statusText.info,
        vant = vant,
    )
}

/** 供 `LocalAppTokens` 默认值使用的一次解析（正常都会被 AppRoot 覆盖）。 */
internal fun defaultTokens(): AppTokens = resolveTokens(ThemePresets.CHOICE_SYSTEM, false)
