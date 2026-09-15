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
    val fontSizeXs: TextUnit = 10.sp,
    val fontSizeSm: TextUnit = 12.sp,
    val fontSizeMd: TextUnit = 14.sp,
    val fontSizeLg: TextUnit = 16.sp,
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
    val disabledOpacity: Float = 0.5f,
)
