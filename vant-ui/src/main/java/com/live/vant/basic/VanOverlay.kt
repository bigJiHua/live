package com.live.vant.basic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-overlay（web 项目 14 处使用；props：show / z-index / @click）。
 * Compose 中 z-index 由布局顺序表达；此处提供遮罩本体，淡入淡出 300ms。
 */
@Composable
fun VanOverlay(
    show: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    onClick: (() -> Unit)? = null,
    lockScroll: Boolean = true, // 语义占位：原生由调用容器自行处理
    /** 遮罩底色（默认沿用主题 overlay；数字键盘等需要看穿底层时可传 Color.Transparent）。 */
    color: Color = LocalVantColors.current.overlay,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(color = color)
                .then(if (onClick != null) Modifier.clickable(onClick = { onClick() }) else Modifier),
        )
    }
}
