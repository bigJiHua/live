package com.live.vant.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.basic.VanLoading
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-list（web 项目 5 处使用；props：v-model:loading / finished / finished-text /
 * error / offset / immediate-check；事件 load / update:loading）。
 *
 * Compose 用法：内容必须放在 LazyColumn/LazyGrid 中，把其 state 传进来；
 * 滚动到底部（剩余 offset dp）且未 finished/error 时触发 onLoad。
 * 在列表尾部放置 [VanListFooter]（对应 Vant 底部加载提示）。
 */
@Composable
fun VanList(
    state: LazyListState,
    loading: Boolean,
    finished: Boolean = false,
    error: Boolean = false,
    offset: Int = 300,
    immediateCheck: Boolean = true,
    onLoad: () -> Unit,
) {
    val canLoad by remember(state, loading, finished, error) {
        derivedStateOf {
            val totalItems = state.layoutInfo.totalItemsCount
            if (totalItems == 0) {
                immediateCheck
            } else {
                val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val endReached = lastVisible >= totalItems - 1
                val remaining = state.layoutInfo.totalItemsCount - state.firstVisibleItemIndex - state.layoutInfo.visibleItemsInfo.size
                endReached && remaining <= 1
            }
        }
    }
    LaunchedEffect(state, canLoad, loading, finished, error) {
        if (canLoad && !loading && !finished && !error) onLoad()
    }
}

/** van-list 底部提示：加载中（spinner + 文字）/ 加载完成文案 */
@Composable
fun VanListFooter(
    loading: Boolean,
    finished: Boolean = false,
    finishedText: String? = null,
    loadingText: String = "加载中...",
) {
    val c = LocalVantColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 16.dp),
    ) {
        when {
            loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VanLoading(size = 16.sp, color = c.textTertiary)
                androidx.compose.foundation.text.BasicText(
                    loadingText,
                    style = TextStyle(color = c.textTertiary, fontSize = 14.sp),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            finished && !finishedText.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
                finishedText,
                style = TextStyle(color = c.textTertiary, fontSize = 14.sp, lineHeight = 50.sp),
            )
        }
    }
}
