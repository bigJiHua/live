package com.live.vant.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanDividerContentPosition { Left, Center, Right }

/**
 * 复刻 van-divider（web 项目 13 处使用；props：content-position / dashed / vertical / hairline）。
 */
@Composable
fun VanDivider(
    modifier: Modifier = Modifier,
    content: String? = null,
    contentPosition: VanDividerContentPosition = VanDividerContentPosition.Center,
    dashed: Boolean = false,
    vertical: Boolean = false,
    textColor: Color? = null,
    borderColor: Color? = null,
    marginTop: Dp = 16.dp,
    marginBottom: Dp = 16.dp,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val lineColor = borderColor ?: c.border

    if (vertical) {
        // 竖分隔线：1px 宽 × 1em 高，左右 margin 8
        Box(modifier.padding(horizontal = 8.dp).width(tokens.borderWidth).height(16.dp)) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), size.width, pathEffect = dash)
            }
        }
        return
    }

    if (content.isNullOrEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(tokens.lineHeightMd.toDp())
                .padding(vertical = (tokens.lineHeightMd.toDp() - tokens.borderWidth) / 2),
        ) {
            Canvas(Modifier.fillMaxWidth().height(tokens.borderWidth)) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
                )
            }
        }
        return
    }

    // 带文字：左右两截线 + 中间文字（内容左右 padding 16，同 --van-divider-content-padding）
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = marginTop, bottom = marginBottom),
    ) {
        val weightLeft = when (contentPosition) {
            VanDividerContentPosition.Left -> 0.1f
            VanDividerContentPosition.Center -> 0.5f
            VanDividerContentPosition.Right -> 0.9f
        }
        DividerLine(Modifier.weight(weightLeft), lineColor, dashed)
        androidx.compose.foundation.text.BasicText(
            text = content,
            style = TextStyle(color = textColor ?: c.textTertiary, fontSize = tokens.fontSizeMd, lineHeight = tokens.lineHeightMd),
            modifier = Modifier.padding(horizontal = tokens.paddingMd),
        )
        DividerLine(Modifier.weight(1f - weightLeft), lineColor, dashed)
    }
}

private fun androidx.compose.ui.unit.TextUnit.toDp(): Dp =
    if (value.isFinite()) value.dp else 24.dp

@Composable
private fun DividerLine(modifier: Modifier, color: Color, dashed: Boolean) {
    val tokens = LocalVantDimens.current
    Canvas(modifier.height(24.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = tokens.borderWidth.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
        )
    }
}
