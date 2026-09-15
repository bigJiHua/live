package com.live.vant.basic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanProgressPosition { Horizontal, TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * 复刻 van-progress（web 项目 4 处使用；props：percentage / stroke-width / color / track-color /
 * pivot-text / show-pivot / pivot-color / text-color / position / striped）。
 */
@Composable
fun VanProgress(
    percentage: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    color: Color = LocalVantColors.current.primary,
    trackColor: Color? = null,
    gradient: List<Color>? = null, // 对应 :color 渐变数组
    showPivot: Boolean = true,
    pivotText: String? = null,
    pivotColor: Color? = null,
    pivotTextColor: Color = Color.White,
    position: VanProgressPosition = VanProgressPosition.Horizontal,
    duration: Int = 400,
) {
    val c = LocalVantColors.current
    val fraction by animateFloatAsState(
        targetValue = (percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(duration),
    )
    val pivot = pivotText ?: "${percentage.toInt()}%"
    val pivotColorFinal = pivotColor ?: (gradient?.last() ?: color)

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(strokeWidth)
                .clip(RoundedCornerShape(strokeWidth / 2))
                .background(trackColor ?: c.border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(strokeWidth)
                    .clip(RoundedCornerShape(strokeWidth / 2))
                    .background(gradient?.let { Brush.horizontalGradient(it) } ?: Brush.horizontalGradient(listOf(pivotColorFinal, pivotColorFinal))),
            )
        }
        if (showPivot) {
            when (position) {
                VanProgressPosition.Horizontal -> Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pivotColorFinal)
                        .padding(horizontal = 5.dp, vertical = 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.text.BasicText(
                        pivot,
                        style = TextStyle(color = pivotTextColor, fontSize = 10.sp, lineHeight = 16.sp),
                    )
                }
                VanProgressPosition.TopLeft, VanProgressPosition.BottomLeft -> Box(
                    Modifier
                        .align(if (position == VanProgressPosition.TopLeft) Alignment.TopStart else Alignment.BottomStart)
                        .padding(top = if (position == VanProgressPosition.TopLeft) 0.dp else strokeWidth + 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pivotColorFinal)
                        .padding(horizontal = 5.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(pivot, style = TextStyle(color = pivotTextColor, fontSize = 10.sp, lineHeight = 16.sp))
                }
                VanProgressPosition.TopRight, VanProgressPosition.BottomRight -> Box(
                    Modifier
                        .align(if (position == VanProgressPosition.TopRight) Alignment.TopEnd else Alignment.BottomEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pivotColorFinal)
                        .padding(horizontal = 5.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(pivot, style = TextStyle(color = pivotTextColor, fontSize = 10.sp, lineHeight = 16.sp))
                }
            }
        }
    }
}

/**
 * 复刻 van-circle（环形进度；props：rate / text / color / layer-color / size / speed / clockwise / round）。
 */
@Composable
fun VanCircle(
    rate: Float,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = LocalVantColors.current.primary,
    layerColor: Color = LocalVantColors.current.bgCard,
    text: String? = null,
    textColor: Color = LocalVantColors.current.textPrimary,
    clockwise: Boolean = true,
    duration: Int = 400,
    content: @Composable (() -> Unit)? = null, // 中心自定义内容（对应默认插槽）
) {
    val animated by animateFloatAsState(
        targetValue = (rate / 100f).coerceIn(0f, 1f),
        animationSpec = tween(duration),
    )
    val tokens = LocalVantDimens.current
    Box(contentAlignment = Alignment.Center, modifier = modifier.height(size)) {
        Canvas(Modifier.height(size)) {
            val stroke = strokeWidth.toPx()
            val d = this.size.minDimension
            val topLeft = (this.size.width - d) / 2
            // 底层圆环
            drawArc(
                color = layerColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeft + stroke / 2, stroke / 2),
                size = Size(d - stroke, d - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // 进度弧（Vant 从 12 点方向顺时针）
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = if (clockwise) 360f * animated else -360f * animated,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeft + stroke / 2, stroke / 2),
                size = Size(d - stroke, d - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
        when {
            content != null -> content()
            !text.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
                text,
                style = TextStyle(color = textColor, fontSize = tokens.fontSizeMd, lineHeight = tokens.lineHeightMd),
            )
        }
    }
}
