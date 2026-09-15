package com.live.vant.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanLoadingType { Spinner, Circular }

/**
 * 复刻 van-loading（web 项目 46 处使用；props：type / size / color / vertical + 默认插槽文字）。
 * spinner = 12 根旋转辐条；circular = 旋转圆弧，动画周期 0.8s 与 --van-loading-spinner-duration 一致。
 */
@Composable
fun VanLoading(
    modifier: Modifier = Modifier,
    size: TextUnit = 30.sp,
    color: Color = LocalVantColors.current.textPlaceholder,
    type: VanLoadingType = VanLoadingType.Spinner,
    vertical: Boolean = false,
    text: String? = null,
    textColor: Color = LocalVantColors.current.textTertiary,
) {
    val transition = rememberInfiniteTransition("van-loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
    )
    val dimens = LocalVantDimens.current
    val icon: @Composable () -> Unit = {
        Canvas(Modifier.size(size.value.dp)) {
            val r = this.size.maxDimension
            when (type) {
                VanLoadingType.Spinner -> rotate(angle) {
                    // 12 根辐条，透明度按序递减（同 Vant .van-loading__spinner span）
                    for (i in 0 until 12) {
                        val theta = Math.toRadians((i * 30).toDouble())
                        val inner = r * 0.26f
                        val outer = r * 0.44f
                        val dx = kotlin.math.cos(theta).toFloat()
                        val dy = kotlin.math.sin(theta).toFloat()
                        drawLine(
                            color = color.copy(alpha = (12 - i) / 12f * 0.85f + 0.15f),
                            start = Offset(r / 2 + dx * inner, r / 2 + dy * inner),
                            end = Offset(r / 2 + dx * outer, r / 2 + dy * outer),
                            strokeWidth = r / 9f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                VanLoadingType.Circular -> {
                    // 底环 + 270° 弧（同 Vant loading circle.svg）
                    drawCircle(
                        color.copy(alpha = 0.25f),
                        radius = r / 2f - r / 18f,
                        center = Offset(r / 2f, r / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = r / 9f),
                    )
                    rotate(angle) {
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(r / 9f, r / 9f),
                            size = androidx.compose.ui.geometry.Size(r * 7f / 9f, r * 7f / 9f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r / 9f),
                        )
                    }
                }
            }
        }
    }
    if (text.isNullOrEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { icon() }
    } else if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            icon()
            androidx.compose.foundation.text.BasicText(
                text,
                style = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = dimens.fontSizeMd, lineHeight = dimens.lineHeightMd),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            icon()
            androidx.compose.foundation.text.BasicText(
                text,
                style = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = dimens.fontSizeMd, lineHeight = dimens.lineHeightMd),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
