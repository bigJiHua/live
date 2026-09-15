package com.live.vant.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-skeleton（web 项目 5 处使用；props：row / title / loading / animate / avatar / rows-width）。
 * loading=true 显示骨架屏，false 渲染 [content]（默认插槽）。
 * 扫光动画周期 1.2s（--van-skeleton-duration）。
 */
@Composable
fun VanSkeleton(
    loading: Boolean,
    modifier: Modifier = Modifier,
    row: Int = 0,
    title: Boolean = false,
    avatar: Boolean = false,
    animate: Boolean = true,
    rowsWidth: List<Float>? = null, // 每行宽度比例（如 listOf(0.6f,0.8f)），默认满宽
    content: @Composable () -> Unit,
) {
    if (!loading) {
        content()
        return
    }
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val base = c.border
    val highlight = c.bgThird

    val transition = rememberInfiniteTransition("van-skeleton")
    val shift by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
    )
    val brush = if (animate) {
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(shift, 0f),
            end = Offset(shift + 400f, 0f),
        )
    } else {
        Brush.linearGradient(listOf(base, base))
    }

    Column(modifier.padding(16.dp)) {
        if (avatar) {
            Row {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(brush, CircleShape),
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    RepeatRows(row.coerceAtLeast(2), brush, rowsWidth)
                }
            }
        } else {
            if (title) {
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(20.dp)
                        .background(brush, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
            RepeatRows(row, brush, rowsWidth)
        }
    }
}

@Composable
private fun RepeatRows(row: Int, brush: Brush, rowsWidth: List<Float>?) {
    Column {
        repeat(row) { i ->
            val w = rowsWidth?.getOrNull(i) ?: 1f
            Box(
                Modifier
                    .padding(top = if (i == 0) 0.dp else 12.dp)
                    .fillMaxWidth(w)
                    .height(16.dp)
                    .background(brush, RoundedCornerShape(2.dp)),
            )
        }
    }
}
