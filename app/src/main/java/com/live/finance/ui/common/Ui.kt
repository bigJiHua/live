package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.live.finance.theme.LocalAppColors

/**
 * `java.time.YearMonth` 的 Saveable 适配器。
 *
 * ⚠ `YearMonth` 既不是 Parcelable 也不是 Bundle 能存的类型，直接
 *   `rememberSaveable { mutableStateOf(YearMonth.now()) }` 会在**运行时**抛
 *   `IllegalArgumentException: ... cannot be saved using the current SaveStateRegistry`（编译期看不出来）。
 *   报表几页（类目占比 / 负债总览 / 月度趋势）的月份筛选要跨「进流水详情再返回」保留 ⇒ 统一走这个 Saver，
 *   存成 `"2026-06"` 字符串，解析失败兜回当月。
 */
val YearMonthSaver: androidx.compose.runtime.saveable.Saver<java.time.YearMonth, String> =
    androidx.compose.runtime.saveable.Saver(
        save = { it.toString() },
        restore = { runCatching { java.time.YearMonth.parse(it) }.getOrDefault(java.time.YearMonth.now()) },
    )

/** 页面统一底：项目页面底色 + 状态栏避让（对应 web body 背景）。 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.bgPage)
            .systemBarsPadding(),
    ) { content(Modifier.fillMaxSize()) }
}

/**
 * web `filter: grayscale(1)` 的等价实现（**全 API 可用**）。
 *
 * Compose 没有 CSS `filter`。第一反应会去用 `GraphicsLayerScope.renderEffect`，但它
 * **要 API 31+**（本工程 `minSdk = 24`），而且这个 Compose 版本（ui-graphics 1.6.8）里
 * `renderEffect` 期望的是 `androidx.compose.ui.graphics.RenderEffect`、**不是**
 * `android.graphics.RenderEffect` —— 直接写 `android.graphics.RenderEffect.createColorFilterEffect(...)`
 * **编译不过**（已踩过，见 8.2 P1-21 附近的记录）。
 *
 * 正确做法是 `saveLayer` + `ColorMatrix` 自绘：`saveLayer` 自 API 21 起支持硬件加速，
 * 无需版本守卫，也就没有「低版本降级」的偏差。
 *
 * 用例：`bill/Ledger` 的还款行、`InstallmentList` 的已结束卡片（web 都是 `grayscale(1)` + `opacity`）。
 */
fun Modifier.grayscale(active: Boolean): Modifier =
    if (!active) this
    else drawWithContent {
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
