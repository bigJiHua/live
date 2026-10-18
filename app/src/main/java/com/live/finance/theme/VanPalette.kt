package com.live.finance.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * # web `--van-*` 别名的**真实生效范围**
 *
 * ⚠⚠ **这是本项目最容易踩的坑，已经踩过两次，改色前务必先读这段。**
 *
 * web 只有 `src/assets/css/style.css` 一个样式文件，里面有三段顶层定义：
 *
 * | 选择器 | 行范围 | 内容 |
 * |---|---|---|
 * | `:root` | 2-56 | `--theme-*` 基础色 + `--money-income/expense`（**固定值**） |
 * | `:root` | 59-146 | Vant **官方**那套：`--van-primary-color` / `--van-success-color` / `--van-warning-color` / `--van-danger-color` / `--van-info-color` |
 * | `[data-theme-mode="dark"]` | 149-173 | 业务那套：`--van-green` / `--van-red` / `--van-blue` / `--van-danger-grad` / `--van-orange` / `--van-purple` / `--van-gray` / `--van-green-bg` / `--van-danger-bg` / `--van-orange-bg` / `--van-blue-bg` |
 *
 * ## 判据
 *
 * 1. **`--van-{primary,success,warning,danger,info}-color` 在 `:root`** → 两模式都等于 `--theme-*`，
 *    **跟主题走**，对应 `colors.primary / success / warning / danger / info`。
 *
 * 2. **业务那套 `--van-green` / `--van-blue` / `--van-green-bg` … 只在 `[data-theme-mode="dark"]`**
 *    → **浅色模式下变量根本不存在**，`var(--van-green, #07c160)` 走的是**行内 fallback**。
 *    即真实语义是「**浅色写死 #07c160，深色跟 `--theme-success`**」，
 *    **不是**「两模式都跟主题」。
 *
 *    > 坑的来源：style.css:153 那句注释「业务语义色别名，供页面直接引用（跟随主题切换）」
 *    > 就写在 `[data-theme-mode="dark"]` 块**里面**，只看注释不看选择器会得出完全相反的结论。
 *
 * 3. `--van-orange` / `--van-purple` / `--van-gray` 也在 dark 块里，但值等于页面行内 fallback
 *    （`#ff976a` / `#7232dd` / `#969799`），所以**两模式同值**，可以直接写死。
 *
 * 4. `--van-green-bg` / `--van-danger-bg` / `--van-orange-bg` / `--van-blue-bg` 同样只在 dark 块，
 *    且**每个页面写的 fallback 都不一样**（`#f0f9eb` / `rgba(7,193,96,.1)` / `#e8f9f0` / `#f0fff4` …），
 *    必须按页面单独处理，见 [vanGreenBg] / [vanDangerBg] / [vanOrangeBg] / [vanBlueBg]。
 *
 * 5. 若 web 写的是 `var(--van-green)`（**没有 fallback**）且处于浅色模式 → 声明「invalid at
 *    computed-value time」：`color` 这类**可继承**属性取**继承值**，`background`/`border-color`
 *    这类**不可继承**属性取**初始值（transparent / currentColor）**。这种情况属于 web 的隐性
 *    表现差异，原生一律按「浅色 = 写死 #07c160」处理并在调用处注明。
 *
 * ## 对应到原生
 *
 * `data-theme-mode` 由 `useUiTheme.js:204` 写入，取值就是 preset 的 `mode`（`'light' | 'dark'`），
 * 与原生 `LocalAppTokens.current.isDark` 一一对应。
 */

/** `var(--van-green, #07c160)` 在**浅色**模式下的取值（行内 fallback）。 */
val VanGreenFixed = Color(0xFF07C160)

/** `var(--van-blue, #1989fa)` 在**浅色**模式下的取值（行内 fallback）。 */
val VanBlueFixed = Color(0xFF1989FA)

/** `--van-green-bg`：深色 = `#f0f9eb`；**常见页面 fallback 也是 `#f0f9eb`** → 两模式同值。
 *  （若页面 fallback 写的是别的值，改用 [vanGreenBg]。） */
val VanGreenBgFixed = Color(0xFFF0F9EB)

/** `var(--van-orange, #ff976a)`：两模式同值。 */
val VanOrangeFixed = Color(0xFFFF976A)

/** `var(--van-purple, #7232dd)`：两模式同值。 */
val VanPurpleFixed = Color(0xFF7232DD)

/** `var(--van-gray, #969799)`：两模式同值。 */
val VanGrayFixed = Color(0xFF969799)

/**
 * `var(--van-green, #07c160)`：浅色 = 写死 #07c160；深色 = `--theme-success`。
 *
 * 典型用法：web 的图标色、状态文字色。
 */
@Composable
fun vanGreen(): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) LocalAppColors.current.success else VanGreenFixed
}

/**
 * `var(--van-blue, #1989fa)`：浅色 = 写死 #1989fa；深色 = `--theme-primary`。
 */
@Composable
fun vanBlue(): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) LocalAppColors.current.primary else VanBlueFixed
}

/**
 * `var(--van-red, #ee0a24)` / `var(--van-danger-grad, …)`：深色 = `--theme-danger`。
 */
@Composable
fun vanRed(lightFallback: Color = Color(0xFFEE0A24)): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) LocalAppColors.current.danger else lightFallback
}

/**
 * `var(--van-orange, <lightFallback>)`：深色 = `#ff976a`，浅色 = 页面行内 fallback。
 * 多数页面写的 fallback 就是 `#ff976a`（两模式同值），但也有写 `#fa8c16` 的（如 DebtOverview）。
 */
@Composable
fun vanOrange(lightFallback: Color = VanOrangeFixed): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) VanOrangeFixed else lightFallback
}

/**
 * `var(--van-green-bg, <lightFallback>)`：深色 = `#f0f9eb`，浅色 = 页面行内写的那个 fallback。
 *
 * @param lightFallback 该页面 CSS 里 `var(--van-green-bg, X)` 的 X（**逐页不同**，别抄别的页面）。
 */
@Composable
fun vanGreenBg(lightFallback: Color): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) Color(0xFFF0F9EB) else lightFallback
}

/**
 * `var(--van-danger-bg, <lightFallback>)`：深色 = `#fff0f0`，浅色 = 页面行内 fallback。
 */
@Composable
fun vanDangerBg(lightFallback: Color): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) Color(0xFFFFF0F0) else lightFallback
}

/**
 * `var(--van-orange-bg, <lightFallback>)`：深色 = `rgba(255,151,106,0.12)`，浅色 = 页面行内 fallback。
 */
@Composable
fun vanOrangeBg(lightFallback: Color): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) Color(0x1FFF976A) else lightFallback
}

/**
 * `var(--van-blue-bg, <lightFallback>)`：深色 = `rgba(25,137,250,0.12)`，浅色 = 页面行内 fallback。
 */
@Composable
fun vanBlueBg(lightFallback: Color): Color {
    val tokens = LocalAppTokens.current
    return if (tokens.isDark) Color(0x1F1989FA) else lightFallback
}
