package com.live.vant.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 复刻 van-image-preview（web 项目 3 处使用；props：v-model:show / images / start-position /
 * closeable / show-index / loop；事件 change / close / opened / closed）。
 * 背景 rgba(0,0,0,.9)，顶部页码 1/N，底部居中关闭按钮——与 Vant 一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VanImagePreview(
    show: Boolean,
    images: List<String>,
    onClose: () -> Unit,
    startPosition: Int = 0,
    closeable: Boolean = false,
    showIndex: Boolean = true,
    loop: Boolean = true,
    onChange: ((Int) -> Unit)? = null,
) {
    if (!show || images.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startPosition.coerceIn(0, images.size - 1)) { images.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { onChange?.invoke(it) }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xE6000000))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (showIndex) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(
                        "${pagerState.currentPage + 1}/${images.size}",
                        style = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 20.sp),
                    )
                }
            }
            if (closeable) {
                VanIcon(
                    name = "cross",
                    size = 22.sp,
                    color = Color(0xFFC8C9CC),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .clickable { onClose() },
                )
            }
        }
    }
}
