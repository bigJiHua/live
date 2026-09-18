package com.live.finance.theme

import androidx.compose.runtime.compositionLocalOf
import com.live.vant.theme.VantColors

/**
 * 应用内取色入口，复用 vant-ui 的调色板（同一份 VantColors），避免第二套魔法色值。
 * 由 `AppRoot` 按当前主题（预设 + 深浅）解析后注入 —— 即 web `buildVars()` 注入 `:root` 的等价物。
 */
val LocalAppColors = compositionLocalOf { VantColors() }

/**
 * 业务级主题 token（主色渐变 / 状态文字衍生色 / mono 语义图标规则等 VantColors 装不下的部分）。
 * 页面优先取 `LocalAppTokens.current`；需要 Vant 语义色时用 `LocalAppColors.current`。
 */
val LocalAppTokens = compositionLocalOf { defaultTokens() }
