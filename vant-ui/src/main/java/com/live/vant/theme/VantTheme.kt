package com.live.vant.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalVantColors = staticCompositionLocalOf { VantColors() }
val LocalVantDimens = staticCompositionLocalOf { VantDimens() }

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
