package com.live.vant.other

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.live.vant.theme.LocalVantColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 复刻 van-swipe + van-swipe-item（web 项目 4 处使用；props：autoplay / loop / vertical /
 * touchable / show-indicators / indicator-color / initial-slide；事件 change / click）。
 *
 * 横向：HorizontalPager（loop=无限循环、autoplay=定时翻页）；
 * 纵向通知条（项目 CalendarGrid 用法）：AnimatedContent 上下切换。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VanSwipe(
    count: Int,
    modifier: Modifier = Modifier,
    autoplay: Long = 0, // 0 = 不自动轮播
    loop: Boolean = true,
    vertical: Boolean = false,
    touchable: Boolean = true,
    showIndicators: Boolean = true,
    indicatorColor: Color? = null,
    initialSlide: Int = 0,
    onChange: ((Int) -> Unit)? = null,
    content: @Composable (index: Int) -> Unit,
) {
    if (count <= 0) return
    val c = LocalVantColors.current
    val scope = rememberCoroutineScope()

    if (vertical) {
        // 纵向轮播（通知条场景）：固定高度 Box + 上下滑动切换
        var current by remember { mutableIntStateOf(initialSlide) }
        LaunchedEffect(count, autoplay) {
            if (autoplay > 0) {
                while (true) {
                    delay(autoplay)
                    current = if (loop) (current + 1) % count else if (current < count - 1) current + 1 else current
                }
            }
        }
        LaunchedEffect(current) { onChange?.invoke(current) }
        Box(modifier) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (slideInVertically(tween(400)) { it / 2 } + fadeIn(tween(400)))
                        .togetherWith(slideOutVertically(tween(400)) { -it / 2 } + fadeOut(tween(400)))
                },
            ) { idx ->
                content(idx)
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialSlide) { if (loop && count > 1) Int.MAX_VALUE else count }
    LaunchedEffect(pagerState.currentPage, count, loop) {
        val logical = if (loop) pagerState.currentPage % count else pagerState.currentPage
        onChange?.invoke(logical)
    }
    LaunchedEffect(autoplay, count, loop) {
        if (autoplay > 0 && count > 1) {
            while (true) {
                delay(autoplay)
                scope.launch {
                    val next = if (loop) pagerState.currentPage + 1
                    else if (pagerState.currentPage < count - 1) pagerState.currentPage + 1 else 0
                    pagerState.animateScrollToPage(next, pageOffsetFraction = 0f, animationSpec = tween(350))
                }
            }
        }
    }

    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = touchable,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val logical = if (loop) page % count else page
            Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                content(logical)
            }
        }
        if (showIndicators && count > 1) {
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                val currentLogical = if (loop) pagerState.currentPage % count else pagerState.currentPage
                repeat(count) { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                color = if (i == currentLogical) (indicatorColor ?: c.primary)
                                else (indicatorColor ?: c.border).copy(alpha = 0.3f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
