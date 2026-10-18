package com.live.vant.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vant 尺寸 token。
 * 数值直接取自 vant/lib/index.css 的 --van-* 定义（CSS px → Android dp/sp 按 1:1）。
 */
@Immutable
data class VantDimens(
    // 圆角
    val radiusSm: Dp = 2.dp,
    val radiusMd: Dp = 4.dp,
    val radiusLg: Dp = 8.dp,
    val radiusMax: Dp = 999.dp,
    val popupRoundRadius: Dp = 16.dp,
    val dialogRadius: Dp = 16.dp,
    val toastRadius: Dp = 8.dp,
    val cellGroupInsetRadius: Dp = 8.dp,

    // 间距
    val paddingBase: Dp = 4.dp,
    val paddingXs: Dp = 8.dp,
    val paddingSm: Dp = 12.dp,
    val paddingMd: Dp = 16.dp,
    val paddingLg: Dp = 24.dp,
    val paddingXl: Dp = 24.dp,

    // 字号 / 行高
    // ⚠ 2026-09-18 起按「移动端最佳字号」口径调整（负责人拍板）：Vant 出厂的 10/14/16 在手机上偏小。
    // 定档依据（审计 M3 / iOS HIG / 国内主流 App 后）：**全偶数阶梯 12 / 14 / 16 / 18 / 20 / 24** ——
    // 12 = M3 bodySmall & iOS caption1（最小可读下限）；14 = M3 bodyMedium（次要）；
    // 16 = M3 bodyLarge & 微信/支付宝正文；18 = 小标题；每档差 ≥2sp，避免出现 16/17 这种分不出层级的两档。
    // 图标一律**不走**这套 token（图标继续用各自的 `size`，不要跟着放大，否则组件几何会被撑变形）。
    val fontSizeXs: TextUnit = 12.sp,
    val fontSizeSm: TextUnit = 12.sp,
    val fontSizeMd: TextUnit = 16.sp,
    val fontSizeLg: TextUnit = 18.sp,
    val lineHeightXs: TextUnit = 14.sp,
    val lineHeightSm: TextUnit = 18.sp,
    val lineHeightMd: TextUnit = 20.sp,
    val lineHeightLg: TextUnit = 22.sp,

    // 通用高度
    val borderWidth: Dp = 1.dp,
    val navBarHeight: Dp = 46.dp,
    val tabbarHeight: Dp = 50.dp,
    val tabsLineHeight: Dp = 44.dp,
    val tabsCardHeight: Dp = 30.dp,
    val tabsBarWidth: Dp = 40.dp,
    val tabsBarHeight: Dp = 3.dp,
    val cellLineHeight: TextUnit = 24.sp,
    val cellVerticalPadding: Dp = 10.dp,
    val cellHorizontalPadding: Dp = 16.dp,
    val searchInputHeight: Dp = 34.dp,
    val pickerToolbarHeight: Dp = 44.dp,
    val pickerOptionHeight: Dp = 44.dp,
    val dialogWidth: Dp = 320.dp,
    val dialogButtonHeight: Dp = 48.dp,
    val toastDefaultWidth: Dp = 88.dp,
    val toastMinHeight: Dp = 88.dp,
    val emptyImageSize: Dp = 160.dp,
    val loadingSpinnerSize: Dp = 30.dp,
    val noticeBarHeight: Dp = 40.dp,
    val dropdownMenuHeight: Dp = 48.dp,
    val stepperInputWidth: Dp = 32.dp,
    val stepperInputHeight: Dp = 28.dp,
    val radioSize: Dp = 20.dp,
    val radioDotSize: Dp = 8.dp,
    val checkboxSize: Dp = 20.dp,
    val switchSize: Dp = 26.dp,
    val switchWidth: Dp = 51.dp, // 1.8em + 4px, 字号 26px 下
    val switchHeight: Dp = 30.dp, // 1em + 4px
    val passwordInputHeight: Dp = 50.dp,
    val keyboardKeyHeight: Dp = 48.dp,
    val uploaderSize: Dp = 80.dp,
    val calendarDayHeight: Dp = 64.dp,
    val calendarSelectedDaySize: Dp = 54.dp,
    val calendarHeaderTitleHeight: Dp = 44.dp,
    val calendarWeekdaysHeight: Dp = 30.dp,
    val progressHeight: Dp = 4.dp,
    val iconSizeMd: Dp = 16.dp,
    val tabbarIconSize: Dp = 22.dp,
    val popupCloseIconSize: Dp = 22.dp,
    val buttonDefaultHeight: Dp = 44.dp,
    val buttonSmallHeight: Dp = 32.dp,
    val buttonMiniHeight: Dp = 24.dp,
    val buttonLargeHeight: Dp = 50.dp,

    // 动效时长（ms），对应 --van-duration-base/.fast
    val durationBase: Int = 300,
    val durationFast: Int = 200,
    // 长过渡（卡片弹层 / 大幅位移入场），web `transition:.38s` 对齐
    val durationSlow: Int = 380,
    val disabledOpacity: Float = 0.5f,

    /**
     * 应用侧「文字大小」档位带来的 sp 偏移（默认 0 = 出厂值不变，由 `:app` 的 `AppRoot` 注入）。
     *
     * 用途只有一类：底座里**没有对应 token** 的字面字号（如 14sp 的刷新提示、19sp 的行高），
     * 写成 `fontSp(14f)` 即可跟档。⚠ 已经在读 [fontSizeXs]/[fontSizeSm]/[fontSizeMd]/[fontSizeLg]
     * 的地方**不要再叠一次** `fontOffsetSp` —— 那几个 token 本身已经含偏移，叠了就是双份放大。
     * 图标（`VanIcon` 的 `size`）与 §7.9 排除表（四个键盘 / `VanPasswordInput` / badge / pivot）一律不接。
     */
    val fontOffsetSp: Float = 0f,
)

/** 见 [VantDimens.fontOffsetSp]：给「没有 token 的字面字号」加上档位偏移。 */
fun VantDimens.fontSp(baseSp: Float): TextUnit = (baseSp + fontOffsetSp).sp

/**
 * 把「已含偏移」的字号 token 还原成基准值。
 *
 * 只给一类场合用：**图标尺寸跟着字号 token 走**的地方（`VanIcon(size = tokens.fontSizeLg)` 等）。
 * 构造书 §7.9 第 1 条硬规矩是「图标不动」—— 文字放大时字形必须停在原尺寸，否则卡片/网格几何会被撑变形。
 */
fun VantDimens.baseOf(scaled: TextUnit): TextUnit = (scaled.value - fontOffsetSp).sp
