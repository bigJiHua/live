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
    /** --tabbar-glass-bg 底部悬浮岛玻璃底（浅色 rgba(255,255,255,.62)，见 web useUiTheme 的 TABBAR.light） */
    val tabbarGlassBg: Color = Color(0x9EFFFFFF),
    /** --tabbar-glass-border 悬浮岛玻璃描边（浅色 rgba(0,0,0,.06)） */
    val tabbarGlassBorder: Color = Color(0x0F000000),
    /** --tabbar-text 悬浮岛未选中项文字色（浅色 #7d7e80，注意不是 textPrimary） */
    val tabbarText: Color = Color(0xFF7D7E80),
    /** --van-tabs-nav-background / --van-nav-bar-background（web useUiTheme 的 navBg：浅色=卡片白、深色=页面最深色） */
    val navBarBg: Color = Color(0xFFFFFFFF),
    /** --theme-primary-grad：主色渐变终点（app-button primary 实心、首页头卡渐变） */
    val primaryGrad: Color = Color(0xFF2A4FB8),
    /** --van-button-primary-color：primary 实心按钮的文字色（深色亮主色主题下为深字，如钛金属/悠森绿） */
    val buttonPrimaryText: Color = Color(0xFFFFFFFF),
    /** --theme-success-text / warning / danger / info：落在底色上的状态小字（浅/深各一套，保证对比度） */
    val successText: Color = Color(0xFF0A7A45),
    val warningText: Color = Color(0xFFB45309),
    val dangerText: Color = Color(0xFFC0102A),
    val infoText: Color = Color(0xFF646566),
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

/**
 * 运行时深色兜底：逐项对齐 web `useUiTheme.buildVars` 在 `mode=dark` 且预设未覆盖时的**默认值**
 * （此前这里是凭感觉取的中性灰 #121212/#1E1E1E/#2C2C2E，已按 web 原值校正为 #0f0f12/#1a1a1e/#26262b）。
 *
 * 注：正式主题由 `:app` 的 `resolveTokens()` 按「预设 + 深浅」解析；本对象仅作库层兜底 / 单测用。
 */
object VantDarkColors {
    fun dark(): VantColors = VantColors().copy(
        // web buildVars dark 默认：bg #0f0f12/#1a1a1e/#26262b、text #f0f0f0/#b0b0b0/#8a8a8a/#5a5a5a、border rgba(255,255,255,.08)
        bgPage = Color(0xFF0F0F12),
        bgCard = Color(0xFF1A1A1E),
        bgThird = Color(0xFF26262B),
        active = Color(0xFF26262B),
        textPrimary = Color(0xFFF0F0F0),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF8A8A8A),
        textPlaceholder = Color(0xFF5A5A5A),
        border = Color(0x14FFFFFF),
        pickerMaskColor = Color(0xFF0F0F12),
        primaryLight = Color(0x14FFFFFF),   // dark 下 --theme-primary-light = rgba(255,255,255,.08)
        iconDefault = Color(0xFFF0F0F0),    // .van-icon 继承 currentColor → 跟随正文色
        navBarBg = Color(0xFF0F0F12),       // web navBg：dark 取 bg.primary
        buttonDefaultBorder = Color(0x14FFFFFF),
        // web STATUS_COMP.dark（组件底色，亮底 + 白字场景）
        success = Color(0xFF2F9B70),
        warning = Color(0xFFC9A05A),
        danger = Color(0xFFE5484D),
        info = Color(0xFF7A7A7A),
        // web STATUS_TEXT.dark（落在深底上的状态小字）
        successText = Color(0xFF5FD6A0),
        warningText = Color(0xFFF0B267),
        dangerText = Color(0xFFFF6B6B),
        infoText = Color(0xFFA0A0A0),
        // web buildVars：dark 下 --van-button-primary-color = #1a1a1a
        buttonPrimaryText = Color(0xFF1A1A1A),
        // web useUiTheme 的 TABBAR.dark：glassBg rgba(18,18,22,.55) / border rgba(255,255,255,.12) / text #b0b0b0
        tabbarGlassBg = Color(0x8C121216),
        tabbarGlassBorder = Color(0x1FFFFFFF),
        tabbarText = Color(0xFFB0B0B0),
    )
}
