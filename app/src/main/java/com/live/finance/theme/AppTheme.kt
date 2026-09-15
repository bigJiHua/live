package com.live.finance.theme

import androidx.compose.runtime.compositionLocalOf
import com.live.vant.theme.VantColors

/**
 * 应用内取色入口，复用 vant-ui 的调色板（同一份 VantColors），避免第二套魔法色值。
 * 在 AppRoot 里与 VantTheme 一并提供。
 */
val LocalAppColors = compositionLocalOf { VantColors() }
