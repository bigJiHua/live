package com.live.vant.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.live.vant.basic.VanLoading
import com.live.vant.theme.LocalVantColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 复刻 van-pull-refresh（web 项目 9 处使用；props：v-model(loading) / @refresh / success-text /
 * disabled / pulling-text / loosing-text / loading-text / head-height）。
 *
 * Compose 中通过 NestedScroll 实现：内部维护 loading 状态与下拉距离，
 * 触发刷新后由 [onRefresh] 通知业务加载，业务完成后调用 [VanPullRefreshState.finishRefresh]。
 * [isChildAtTop] 返回内容是否已滚动到顶部（Vant 自动检测，Compose 需显式传入）。
 */
class VanPullRefreshState internal constructor() {
    internal var offset by mutableFloatStateOf(0f)
    internal var phase by mutableStateOf(PullPhase.Idle)
    /** v-model 语义：当前是否正在刷新 */
    val isLoading: Boolean get() = phase == PullPhase.Loading
    internal val successDuration = 1200L

    /** 业务加载完成后调用：收起并短暂显示成功文案（对应 Vant 刷新完成动效） */
    fun finishRefresh() { phase = PullPhase.Success }

    internal fun reset() { phase = PullPhase.Idle; offset = 0f }

    enum class PullPhase { Idle, Pulling, Loosing, Loading, Success }
}

@Composable
fun rememberVanPullRefreshState(): VanPullRefreshState = remember { VanPullRefreshState() }

@Composable
fun VanPullRefresh(
    state: VanPullRefreshState = rememberVanPullRefreshState(),
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    successText: String? = null,
    pullingText: String = "下拉即可刷新...",
    loosingText: String = "释放即可刷新...",
    loadingText: String = "加载中...",
    disabled: Boolean = false,
    headHeight: Int = 50,
    isChildAtTop: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val c = LocalVantColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // loading 外部控制（v-model 语义）：phase 变化同步
    LaunchedEffect(state.phase) {
        when (state.phase) {
            VanPullRefreshState.PullPhase.Loading -> onRefresh()
            VanPullRefreshState.PullPhase.Success -> {
                delay(state.successDuration)
                state.reset()
            }
            else -> {}
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (disabled || state.phase == VanPullRefreshState.PullPhase.Loading || state.phase == VanPullRefreshState.PullPhase.Success) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                // 已下拉中：继续消费
                if (available.y > 0 && (state.offset > 0f || isChildAtTop())) {
                    state.offset = (state.offset + available.y / 2.5f).coerceIn(0f, headHeight * 2.5f)
                    state.phase = if (state.offset >= headHeight) VanPullRefreshState.PullPhase.Loosing else VanPullRefreshState.PullPhase.Pulling
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                // 子内容到顶后继续下拉 → 父级接管
                if (consumed.y == 0f && available.y > 0 && isChildAtTop() &&
                    state.phase != VanPullRefreshState.PullPhase.Loading
                ) {
                    state.offset = (state.offset + available.y / 2.5f).coerceIn(0f, headHeight * 2.5f)
                    state.phase = if (state.offset >= headHeight) VanPullRefreshState.PullPhase.Loosing else VanPullRefreshState.PullPhase.Pulling
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.phase == VanPullRefreshState.PullPhase.Loosing) {
                    state.offset = headHeight.toFloat()
                    state.phase = VanPullRefreshState.PullPhase.Loading
                } else if (state.phase == VanPullRefreshState.PullPhase.Pulling) {
                    state.reset()
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(connection)
            .then(modifier),
    ) {
        content()
        // 头部提示区（Vant .van-pull-refresh__head）
        val visible = state.offset > 0f || state.isLoading || state.phase == VanPullRefreshState.PullPhase.Success
        val height = when (state.phase) {
            VanPullRefreshState.PullPhase.Loading, VanPullRefreshState.PullPhase.Success -> headHeight.dp
            else -> state.offset.roundToInt().dp
        }
        if (visible) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .zIndex(1f),
            ) {
                when (state.phase) {
                    VanPullRefreshState.PullPhase.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VanLoading(size = 16.sp, color = c.textTertiary)
                        if (loadingText.isNotEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                loadingText, style = TextStyle(color = c.textTertiary, fontSize = 14.sp),
                            )
                        }
                    }
                    VanPullRefreshState.PullPhase.Success -> androidx.compose.foundation.text.BasicText(
                        successText ?: "", style = TextStyle(color = c.textTertiary, fontSize = 14.sp),
                    )
                    else -> androidx.compose.foundation.text.BasicText(
                        if (state.phase == VanPullRefreshState.PullPhase.Loosing) loosingText else pullingText,
                        style = TextStyle(color = c.textTertiary, fontSize = 14.sp),
                    )
                }
            }
        }
    }
}
