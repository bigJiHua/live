package com.live.vant.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Vant 语义色组。
 *
 * 默认值 = web 项目 `style.css` 对 Vant CSS 变量的覆盖结果（见 --van-primary-color: #3a66e0 等），
 * 即复刻的是「本项目实际生效的主题」而非 Vant 出厂主题。
 * 若需要还原 Vant 出厂主题（primary #1989fa），使用 [VantColors.classic]。
 */
@Immutable
data class VantColors(
    /** --van-primary-color（项目覆盖为 #3a66e0；Vant 默认 #1989fa） */
    val primary: Color = Color(0xFF3A66E0),
    /** --van-success-color */
    val success: Color = Color(0xFF07C160),
    /** --van-warning-color */
    val warning: Color = Color(0xFFFF976A),
    /** --van-danger-color */
    val danger: Color = Color(0xFFEE0A24),
    /** --van-info-color */
    val info: Color = Color(0xFF969799),
    /** --theme-text-primary / --van-cell-text-color */
    val textPrimary: Color = Color(0xFF323233),
    /** --theme-text-secondary（field label、dialog message 等次级文字） */
    val textSecondary: Color = Color(0xFF646566),
    /** --theme-text-tertiary（cell label、group title、占位描述） */
    val textTertiary: Color = Color(0xFF969799),
    /** --theme-text-placeholder / disabled 文字 */
    val textPlaceholder: Color = Color(0xFFC8C9CC),
    /** --theme-border / --van-border-color 1px 分隔线 */
    val border: Color = Color(0xFFEBEDF0),
    /** --theme-bg-primary 页面底色 */
    val bgPage: Color = Color(0xFFF7F8FA),
    /** --theme-bg-secondary 卡片/弹窗/单元格底色 */
    val bgCard: Color = Color(0xFFFFFFFF),
    /** --theme-bg-tertiary 三级底色（active、tag default、keyboard delete 键） */
    val bgThird: Color = Color(0xFFF2F3F5),
    /** --van-active-color，点击态背景 */
    val active: Color = Color(0xFFF2F3F5),
    /** --van-overlay-background rgba(0,0,0,.7) */
    val overlay: Color = Color(0xB3000000),
    /** --van-switch-background rgba(120,120,128,.16) */
    val switchOff: Color = Color(0x29787880),
    /** --van-toast-background rgba(0,0,0,.7) */
    val toastBg: Color = Color(0xB3000000),
    /** --van-button-default-border-color */
    val buttonDefaultBorder: Color = Color(0xFFDCDEE0),
    /** van-icon 继承的 currentColor 兜底色 */
    val iconDefault: Color = Color(0xFF323233),
    /** --van-picker-mask 渐变使用的页面色 */
    val pickerMaskColor: Color = Color(0xFFF7F8FA),
    /** 主色浅底 --theme-primary-light（选中项背景等） */
    val primaryLight: Color = Color(0xFFEAF0FD),
    /** 进度条底色 --van-progress-background */
    val progressBg: Color = Color(0xFFEBEDF0),
    /** 通知条文字 --van-notice-bar-text-color（orange-dark） */
    val noticeText: Color = Color(0xFFED6A0C),
    /** 通知条背景 --van-notice-bar-background（orange-light） */
    val noticeBg: Color = Color(0xFFFFFBE8),
) {
    companion object {
        /** Vant 4.9.22 出厂浅色主题（primary #1989fa） */
        fun classic() = VantColors(
            primary = Color(0xFF1989FA),
            textPrimary = Color(0xFF323233),
            textSecondary = Color(0xFF969799),
            textTertiary = Color(0xFF969799),
            pickerMaskColor = Color(0xFFF7F8FA),
            primaryLight = Color(0xFFECF5FE),
        )
    }
}

/** 运行时深浅色：web 端 useUiTheme 通过 data-theme-mode=dark 覆盖变量；安卓侧提供对应深色预设 */
object VantDarkColors {
    /** 项目深色主题下常见覆盖（与 [style.css] [data-theme-mode="dark"] 区块对应） */
    fun dark(): VantColors = VantColors().copy(
        textPrimary = Color(0xFFF5F5F5),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF8A8A8C),
        textPlaceholder = Color(0xFF6E6E70),
        border = Color(0xFF3A3A3C),
        bgPage = Color(0xFF121212),
        bgCard = Color(0xFF1E1E1E),
        bgThird = Color(0xFF2C2C2E),
        active = Color(0xFF2C2C2E),
        pickerMaskColor = Color(0xFF121212),
    )
}
