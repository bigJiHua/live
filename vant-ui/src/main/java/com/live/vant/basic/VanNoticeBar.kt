package com.live.vant.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-notice-bar（web 项目 6 处使用；props：text / mode / color / background /
 * left-icon / scrollable / wrapable / speed）。
 * mode: "closeable"（右侧关闭钮）/ "link"（右侧箭头）。
 */
@Composable
fun VanNoticeBar(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalVantColors.current.noticeText,
    background: Color = LocalVantColors.current.noticeBg,
    leftIcon: String? = null,
    mode: String? = null,
    scrollable: Boolean? = null, // null = 文字超宽时自动滚动
    wrapable: Boolean = false,
    speed: Float = 50f, // px/s，对应 --van-notice-bar-speed
    onClosed: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tokens = LocalVantDimens.current
    var visible by remember { mutableStateOf(true) }
    if (!visible) return

    val containerWidth = remember { mutableStateOf(0) }
    val textWidth = remember { mutableStateOf(0) }
    val shouldScroll = scrollable ?: (textWidth.value > containerWidth.value && !wrapable)

    val transition = rememberInfiniteTransition("van-notice-bar")
    val offsetAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldScroll) -(textWidth.value + containerWidth.value).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = if (shouldScroll && speed > 0) {
                    ((textWidth.value + containerWidth.value) / speed * 1000).toInt().coerceAtLeast(1)
                } else 1,
                easing = LinearEasing,
            ),
        ),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .height(if (wrapable) 54.dp else tokens.noticeBarHeight)
            .onSizeChanged { containerWidth.value = it.width }
            .clickable(enabled = onClick != null || mode == "link") { onClick?.invoke() }
            .padding(horizontal = tokens.paddingMd),
    ) {
        if (!leftIcon.isNullOrEmpty()) {
            VanIcon(name = leftIcon, size = 16.sp, color = color)
            Spacer(Modifier.width(tokens.paddingXs))
        }
        Box(
            Modifier
                .weight(1f)
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (shouldScroll && !wrapable) {
                androidx.compose.foundation.text.BasicText(
                    text,
                    style = TextStyle(color = color, fontSize = tokens.fontSizeMd, lineHeight = tokens.lineHeightLg),
                    maxLines = 1,
                    modifier = Modifier
                        .onSizeChanged { textWidth.value = it.width }
                        .offset { IntOffset(offsetAnim.toInt(), 0) },
                )
            } else {
                androidx.compose.foundation.text.BasicText(
                    text,
                    style = TextStyle(
                        color = color,
                        fontSize = tokens.fontSizeMd,
                        lineHeight = tokens.lineHeightLg,
                    ),
                    maxLines = if (wrapable) 2 else 1,
                    overflow = if (wrapable) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.onSizeChanged { textWidth.value = it.width },
                )
            }
        }
        if (mode == "closeable") {
            VanIcon(
                name = "cross",
                size = 16.sp,
                color = color,
                modifier = Modifier.padding(start = tokens.paddingXs).clickable { visible = false; onClosed?.invoke() },
            )
        } else if (mode == "link") {
            VanIcon(name = "arrow", size = 16.sp, color = color, modifier = Modifier.padding(start = tokens.paddingXs))
        }
    }
}
