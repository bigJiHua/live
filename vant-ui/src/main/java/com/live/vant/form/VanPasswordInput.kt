package com.live.vant.form

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-password-input（web 项目 5 处使用；props：value / length / focused / mask /
 * gutter / error-info；光标闪烁 1s 与 Vant 动画一致）。
 * 输入本身由调用方配合 van-number-keyboard 完成（与 Vant 设计一致）。
 */
@Composable
fun VanPasswordInput(
    value: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    focused: Boolean = true,
    mask: Boolean = true,
    gutter: Dp = 16.dp,
    errorInfo: String? = null,
) {
    val c = LocalVantColors.current
    val transition = rememberInfiniteTransition("van-password-cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    )

    Column(modifier) {
        Row(
            Modifier
                .padding(horizontal = gutter)
                .defaultMinSize(minHeight = 50.dp)
                .border(1.dp, c.border, RoundedCornerShape(6.dp))
                .background(c.bgCard, RoundedCornerShape(6.dp)),
        ) {
            repeat(length) { i ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    // 左侧分隔线（i>0 时）
                    if (i > 0) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(c.border),
                        )
                    }
                    val filled = i < value.length
                    when {
                        filled && mask -> Box(
                            Modifier.size(10.dp).background(c.textPrimary, RoundedCornerShape(5.dp)),
                        )
                        filled && !mask -> androidx.compose.foundation.text.BasicText(
                            value[i].toString(),
                            style = TextStyle(color = c.textPrimary, fontSize = 20.sp),
                        )
                        !filled && focused && i == value.length && i < length -> Box(
                            Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .alpha(cursorAlpha)
                                .background(c.textPrimary),
                        )
                    }
                }
            }
        }
        if (!errorInfo.isNullOrEmpty()) {
            androidx.compose.foundation.text.BasicText(
                errorInfo,
                style = TextStyle(color = c.danger, fontSize = 14.sp, lineHeight = 20.sp),
                modifier = Modifier.padding(horizontal = gutter, vertical = 8.dp),
            )
        }
    }
}
