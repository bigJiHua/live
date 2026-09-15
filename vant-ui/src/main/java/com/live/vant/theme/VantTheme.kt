package com.live.vant.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalVantColors = staticCompositionLocalOf { VantColors() }
val LocalVantDimens = staticCompositionLocalOf { VantDimens() }

/**
 * 组件库主题入口，对应 web 端 `:root` 上的 CSS 变量注入。
 * 在 App 顶层包一层即可让全部 Van* 组件读取同一主题；
 * 深色模式传 [VantDarkColors.dark] 即可对应 web 端 data-theme-mode=dark。
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
