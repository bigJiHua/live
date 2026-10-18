package com.live.finance.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.live.vant.theme.VantColors
import com.live.vant.theme.VantDimens

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

// ─────────────────── 文字大小档位（原生设置，web 无对应项） ───────────────────

/** 设置页「文字大小」的五档区间（一…五）；[FONT_LEVEL_DEFAULT] 档 = 「标准」。 */
const val FONT_LEVEL_MIN = 1
const val FONT_LEVEL_MAX = 5
const val FONT_LEVEL_DEFAULT = 3

/**
 * 「标准」档落在**现状字号 - 2sp**（负责人 2026-09-20 定：标准往下两档、往上两档，
 * 于是五档正好 = 现状、一档 = 现状 -4sp）。整个阶梯因此整体下移 2sp。
 */
private const val FONT_LEVEL_BASE_OFFSET = -2f

/** 档位 → 相对现状的 sp 偏移：一档 -4、二档 -3、**三档(标准) -2**、四档 -1、五档 0（每档进退 1sp）。 */
fun fontOffsetSp(level: Int): Float =
    (level.coerceIn(FONT_LEVEL_MIN, FONT_LEVEL_MAX) - FONT_LEVEL_DEFAULT).toFloat() + FONT_LEVEL_BASE_OFFSET

/**
 * 当前生效的 sp 偏移，由 `AppRoot` 按 [com.live.finance.di.AppGraph.fontLevel] 注入。
 *
 * 只落到两处（口径同构造书 7.9）：
 *  1. [com.live.finance.ui.flow.FText] —— 业务文字全量跟档；
 *  2. `VantDimens` 的字号 token（见 [vantDimensWithFontOffset]）—— 走 token 的底座组件跟档。
 *
 * ⚠ 底座里「字号 = 容器几何」的硬编码字号（四个键盘、`VanPasswordInput`、badge/pivot 10sp）
 * 按 7.9 的排除表**不参与**，放大必撑破；系统 fontScale 的收敛仍是 7.10 那套，两者互不覆盖。
 */
val LocalFontOffsetSp = staticCompositionLocalOf { 0f }

/**
 * 基准 sp → 叠加档位偏移后的 `TextUnit`。**所有**取字号的地方都走它：
 * `FText` 内部用它，旁路 `FText` 直接写 `BasicText`/`TextStyle` 的页面也用 `fontSize = scaledSp(12f)`。
 * 传进去的数**保持 web 的 px 数值不变**（§7.9 提档后的阶梯值），偏移只在这一处叠加。
 */
@Composable
fun scaledSp(baseSp: Float): TextUnit = (baseSp + LocalFontOffsetSp.current).sp

/** 行高版：`TextUnit.Unspecified` 原样返回，其余同 [scaledSp]（字号涨了行高不涨会挤行）。 */
@Composable
fun scaledLineHeight(base: TextUnit): TextUnit =
    if (base.isUnspecified) base else scaledSp(base.value)

/**
 * 把档位偏移叠到 Vant 的字号 token 上（行高同幅，避免放大后挤行）；非字号 token 原样保留。
 * 末尾的 `fontOffsetSp` 是给底座里**没有 token** 的字面字号用的（见 [VantDimens.fontOffsetSp]）。
 */
fun vantDimensWithFontOffset(offset: Float): VantDimens {
    val d = VantDimens()
    fun TextUnit.scaled() = if (isUnspecified) this else (value + offset).sp
    return d.copy(
        fontSizeXs = d.fontSizeXs.scaled(),
        fontSizeSm = d.fontSizeSm.scaled(),
        fontSizeMd = d.fontSizeMd.scaled(),
        fontSizeLg = d.fontSizeLg.scaled(),
        lineHeightXs = d.lineHeightXs.scaled(),
        lineHeightSm = d.lineHeightSm.scaled(),
        lineHeightMd = d.lineHeightMd.scaled(),
        lineHeightLg = d.lineHeightLg.scaled(),
        // `cellLineHeight` = Vant 的 cell/collapse/field 标题行高（24sp），跟着字号一起长才不挤行
        cellLineHeight = d.cellLineHeight.scaled(),
        fontOffsetSp = offset,
    )
}
