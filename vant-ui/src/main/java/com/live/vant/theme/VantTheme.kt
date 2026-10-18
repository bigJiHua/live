package com.live.vant.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

val LocalVantColors = staticCompositionLocalOf { VantColors() }
val LocalVantDimens = staticCompositionLocalOf { VantDimens() }

/**
 * `LocalVantDimens.current` 的简写，供组件内联取字号 token 用
 * （一个 `TextStyle(...)` 里往往同时读 fontSize 与 lineHeight，写全称会把一行拉到三倍宽）。
 */
val vantDimens: VantDimens
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalVantDimens.current

/**
 * 键盘按键触感（设置页「按键震动」）总开关，由 `:app` 的 `AppRoot` 注入；默认开。
 * 判定只住在这一个 local 里 —— 各键盘组件一律通过 [rememberKeyHaptic] 取回调，不自己读设置。
 */
val LocalKeyHapticEnabled = staticCompositionLocalOf { true }

/**
 * 取一个「按键震动」回调，在按键的 `clickable` lambda 里调一次。
 *
 * 触感的下发与三级降级全部住在 [performAppHaptic]（见 `Haptics.kt`：不需要 `VIBRATE` 权限、
 * 跟随系统「触摸反馈」开关、`KEYBOARD_PRESS → VIRTUAL_KEY → decorView` 逐级看返回值成功即止）；
 * 主常量由设置页「震动效果」档位（[LocalHapticStrength]）决定，默认「中」= 第三十轮的 `KEYBOARD_PRESS`
 * 原手感。关闭「按键震动」设置时返回空动作，调用点无需判空。
 *
 * ⚠ 与「全局操作点击震动」（[globalClickHaptic]）同刻触发时由 [performAppHaptic] 的时间戳去重，
 * 键盘键上不会出现双震。
 */
@Composable
fun rememberKeyHaptic(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalKeyHapticEnabled.current
    val strength = LocalHapticStrength.current
    return remember(view, enabled, strength) {
        if (!enabled) {
            {}
        } else {
            { view.performAppHaptic(strength) }
        }
    }
}

/**
 * 组件库主题入口，对应 web 端 `:root` 上的 CSS 变量注入。
 * 在 App 顶层包一层即可让全部 Van* 组件读取同一主题；
 * 深色模式传 [VantDarkColors.dark]（或由 `:app` 的 `resolveTokens()` 解析出的调色板）即可对应 web `data-theme-mode=dark`。
 *
 * 注：web `.van-icon` 的 `currentColor` 语义由 [com.live.vant.icon.VanIcon] 自身兜底（未传 color 时取 `iconDefault`），
 * 这里不注入 `LocalContentColor`（foundation 未提供该 CompositionLocal，引入 material 只为一个默认色不值当）。
 */
@Composable
fun VantTheme(
    colors: VantColors = VantColors(),
    dimens: VantDimens = VantDimens(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVantColors provides colors,
        LocalVantDimens provides dimens,
        content = content,
    )
}
