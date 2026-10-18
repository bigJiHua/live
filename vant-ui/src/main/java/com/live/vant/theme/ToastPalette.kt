package com.live.vant.theme

import androidx.compose.ui.graphics.Color

/**
 * 单个消息状态的一套配色（对应 web `style.css` 里 `--toast-<state>-a / -b / -accent / -soft`）。
 *
 * 设计稿（负责人 2026-09-20 提供）的形态是「浅色 tint 卡片 + accent 色圆环图标」：
 *  - [bgTop] / [bgBottom]：卡片底色，竖向渐变的两端（极浅的状态色）；
 *  - [accent]：图标圆环的描边与字形颜色；
 *  - [accentSoft]：图标圆环内部的极浅填充。
 */
data class ToastStateColors(
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val accentSoft: Color,
)

/**
 * 消息弹窗（Toast）配色板 —— **独立于主题语义色**。
 *
 * ⚠ 为什么不复用 [VantColors.success] / `warning` / `danger`：
 * 那三个是「组件底色」（亮底 + 白字）用的，跟 toast 的「极浅 tint + 深 accent 字」完全不是一套。
 * 负责人明确要求 toast 走独立变量，改 toast 不牵连标签 / 图表 / 通知条 / 收支绿。
 *
 * ⚠ 五色语义（负责人 2026-09-21）：[info] 是**灰**，专指「拿不到响应」（本地连不上 / 远端 5xx）；
 * [notice] 是**蓝**，接 `Text`（普通信息）与 `Loading`（进行中）。
 */
data class ToastPalette(
    val success: ToastStateColors,
    val warning: ToastStateColors,
    val danger: ToastStateColors,
    val info: ToastStateColors,
    /** 正文色（标题 / 短信息） */
    val textPrimary: Color,
    /** 描述色（长信息的第二行） */
    val textSecondary: Color,
    /** 关闭按钮的常态色 */
    val textTertiary: Color,
    /** 卡片内圈描边（web 的 `inset 0 0 0 1px`） */
    val innerHairline: Color,
    /** 关闭按钮 hover 底 */
    val closeHoverBg: Color,
    /**
     * 卡片投影色（web `--toast-shadow`）。
     *
     * ⚠ **必须显式给**：不传的话 Compose 用 `DefaultShadowColor`（纯黑、按 elevation 算 alpha），
     * 浅色下比 web 的 `rgba(16,24,40,.10)` 重、**深色下更是几乎看不见**（黑投影压黑底），
     * 卡片会显得是平的 —— 而 web 深色用的是 `rgba(0,0,0,.42)`，投影很实。
     * web 是两层阴影（`0 8px 24px .10` + `0 1px 2px .06`），Compose 的 `shadow()` 只有一组
     * ambient/spot，这里取偏浓的那层做单层近似。
     * ⚠ 该色仅在 **API 28+** 生效（更低的版本系统只认 elevation、颜色被忽略）。
     */
    val shadow: Color,
    /**
     * **蓝 = 普通信息**（负责人 2026-09-21 的五色语义）。
     *
     * ⚠ 别跟 [info] 混：[info] 是**灰**，专指「拿不到响应」（本地连不上 API / 远端 5xx）；
     * [notice] 才是「普通信息」（`VanToastType.Text` / `Loading` 走它）。
     * ⚠ 追加在末尾（不插中间）—— 见项目约定「data class 新字段一律追加末尾」。
     */
    val notice: ToastStateColors,
) {
    companion object {
        /** 浅色：web `:root` 的 `--toast-*` */
        val Light = ToastPalette(
            success = ToastStateColors(
                bgTop = Color(0xFFEAFBF2),
                bgBottom = Color(0xFFDCF5E7),
                accent = Color(0xFF0B8A5A),
                accentSoft = Color(0x1A0B8A5A), // rgba(11,138,90,.10)
            ),
            warning = ToastStateColors(
                bgTop = Color(0xFFFFF9E6),
                bgBottom = Color(0xFFFFF2CC),
                accent = Color(0xFFB46F00),
                accentSoft = Color(0x1AB46F00), // rgba(180,111,0,.10)
            ),
            danger = ToastStateColors(
                bgTop = Color(0xFFFEF1F0),
                bgBottom = Color(0xFFFCE6E4),
                accent = Color(0xFFC62F2A),
                accentSoft = Color(0x17C62F2A), // rgba(198,47,42,.09)
            ),
            info = ToastStateColors(
                bgTop = Color(0xFFF6F7F9),
                bgBottom = Color(0xFFEDEFF3),
                accent = Color(0xFF4B5563),
                accentSoft = Color(0x174B5563), // rgba(75,85,99,.09)
            ),
            textPrimary = Color(0xFF1A1F26),
            textSecondary = Color(0x9E1A1F26),  // rgba(26,31,38,.62)
            textTertiary = Color(0x6B1A1F26),   // rgba(26,31,38,.42)
            innerHairline = Color(0x0F101828),  // rgba(16,24,40,.06)
            closeHoverBg = Color(0x14101828),   // rgba(16,24,40,.08)
            shadow = Color(0x1A101828),         // rgba(16,24,40,.10)（取 web 两层里偏浓的那层）
            // 蓝 = 普通信息。取项目主色 #3a66e0 压深一档，与其余 accent 同一量级
            notice = ToastStateColors(
                bgTop = Color(0xFFEEF3FF),
                bgBottom = Color(0xFFDFE9FF),
                accent = Color(0xFF2B52C4),
                accentSoft = Color(0x1A2B52C4), // rgba(43,82,196,.10)
            ),
        )

        /** 深色：web `[data-theme-mode="dark"]` 的 `--toast-*` */
        val Dark = ToastPalette(
            success = ToastStateColors(
                bgTop = Color(0xFF0F6150),
                bgBottom = Color(0xFF0A4536),
                accent = Color(0xFF3DDC97),
                accentSoft = Color(0x293DDC97), // rgba(61,220,151,.16)
            ),
            warning = ToastStateColors(
                bgTop = Color(0xFF6B4F1A),
                bgBottom = Color(0xFF4E3912),
                accent = Color(0xFFFFD166),
                accentSoft = Color(0x29FFD166), // rgba(255,209,102,.16)
            ),
            danger = ToastStateColors(
                bgTop = Color(0xFF7E2626),
                bgBottom = Color(0xFF581818),
                accent = Color(0xFFFF7A6B),
                accentSoft = Color(0x29FF7A6B), // rgba(255,122,107,.16)
            ),
            info = ToastStateColors(
                bgTop = Color(0xFF3B424B),
                bgBottom = Color(0xFF2B2F36),
                accent = Color(0xFFAEB6C2),
                accentSoft = Color(0x29AEB6C2), // rgba(174,182,194,.16)
            ),
            textPrimary = Color(0xFFF2F5F8),
            textSecondary = Color(0x9EF2F5F8),  // rgba(242,245,248,.62)
            textTertiary = Color(0x6BF2F5F8),   // rgba(242,245,248,.42)
            innerHairline = Color(0x0FFFFFFF),  // rgba(255,255,255,.06)
            closeHoverBg = Color(0x1FFFFFFF),   // rgba(255,255,255,.12)
            shadow = Color(0x6B000000),         // rgba(0,0,0,.42)（深色下 web 只给一层）
            notice = ToastStateColors(
                bgTop = Color(0xFF1E3A72),
                bgBottom = Color(0xFF152A52),
                accent = Color(0xFF7AA8FF),
                accentSoft = Color(0x297AA8FF), // rgba(122,168,255,.16)
            ),
        )
    }
}
