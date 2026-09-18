package com.live.finance.ui.common

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CSS `box-shadow` 参数（dx/dy/blur 单位 dp；color 自带 alpha）。 */
data class WebShadow(
    val dx: Float = 0f,
    val dy: Float = 4f,
    val blur: Float = 12f,
    val color: Color = Color.Black.copy(alpha = 0.06f),
)

/**
 * 复刻 web CSS `box-shadow`。
 *
 * Compose 的 `Modifier.shadow()` 只有 elevation，无法表达 CSS 的偏移 / 模糊半径 / 透明度，
 * 故用原生 `Paint.setShadowLayer`：先在卡片圆角内画一层实心矩形（随后被链上的 `.background()` 完全覆盖），
 * 外侧只留下偏移 + 模糊的投影。
 *
 * 用法：`Modifier.cssShadow(radius = 20.dp, shadow = WebShadow(0f, 4f, 12f, Color.Black.copy(alpha = .03f)))`
 * 必须紧接 `.background(...)` 之前，才能盖住实心层。
 */
fun Modifier.cssShadow(radius: Dp, shadow: WebShadow): Modifier = drawBehind {
    val r = radius.toPx()
    val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        setShadowLayer(shadow.blur.dp.toPx(), shadow.dx.dp.toPx(), shadow.dy.dp.toPx(), shadow.color.toArgb())
    }
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}

/**
 * 圆形专用 CSS box-shadow —— **纯阴影、无本体**。
 *
 * [cssShadow] 的实现是「先画一层纯黑实心矩形垫层，靠链上的 `.background()` 盖住」；它要求
 * 背景与垫层【同形状】。圆形背景（CircleShape）盖不住方形垫层 → 四角露黑；背景透明的元素 →
 * 整块黑垫层露出（这正是「日历选中日发黑」「FAB 圆钮四角发黑」的根因）。
 *
 * 圆形场景改用 `BlurMaskFilter` 只画一圈高斯模糊色斑（无本体）：落在背景上的内圈会被实色
 * 背景盖住，或对透明背景形成与 web `box-shadow` 一致的柔光晕圈。
 */
fun Modifier.cssShadowCircle(shadow: WebShadow): Modifier = drawBehind {
    val blurPx = shadow.blur.dp.toPx()
    val paint = Paint().apply {
        isAntiAlias = true
        color = shadow.color.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { it.nativeCanvas.drawCircle(size.width / 2f, size.height / 2f, size.minDimension / 2f, paint) }
}
