package com.live.vant.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.vant.basic.VanOverlay
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class VanPopupPosition { Bottom, Top, Left, Right, Center }

/**
 * 复刻 van-popup（web 项目 8 处使用；props：v-model:show / position / round / overlay /
 * overlay-class / close-on-click-overlay / closeable / close-icon / close-icon-position /
 * safe-area-inset-bottom）。
 *
 * 使用独立 Window（Dialog）呈现，天然覆盖全屏；淡入/滑入动画 300ms 与 Vant 一致。
 */
@Composable
fun VanPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    position: VanPopupPosition = VanPopupPosition.Bottom,
    round: Boolean = false,
    overlay: Boolean = true,
    closeOnClickOverlay: Boolean = true,
    closeable: Boolean = false,
    closeIconPosition: String = "top-right",
    safeAreaInsetBottom: Boolean = true,
    background: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!show) return
    // ⚠ **返回键必须能关掉弹层**（2026-09-19 真机反馈「按返回无效」）。
    //   Comopse 的 `Dialog` 虽然自身处理返回，但：① 它的 `onDismissRequest` 被我们按
    //   `closeOnClickOverlay` 门控了（`closeOnClickOverlay=false` 时返回同样失效）；
    //   ② 弹层内还套着别的 `Dialog`（如日期选择器里的滚轮层）时，返回会被内层吃掉。
    //   这里显式挂一个 `BackHandler`：返回键**一律**关闭当前弹层，与遮罩点击策略解耦。
    BackHandler { onDismissRequest() }

    val c = LocalVantColors.current
    val bg = background ?: c.bgCard

    Dialog(
        onDismissRequest = { if (closeOnClickOverlay) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            if (overlay) {
                VanOverlay(show = true, onClick = { if (closeOnClickOverlay) onDismissRequest() })
            }
            val corner = if (round) 16.dp else 0.dp
            AnimatedVisibility(
                visible = true,
                enter = when (position) {
                    VanPopupPosition.Bottom -> slideInVertically(tween(300)) { it }
                    VanPopupPosition.Top -> slideInVertically(tween(300)) { -it }
                    VanPopupPosition.Left -> slideInHorizontally(tween(300)) { -it }
                    VanPopupPosition.Right -> slideInHorizontally(tween(300)) { it }
                    VanPopupPosition.Center -> fadeIn(tween(300))
                },
                exit = when (position) {
                    VanPopupPosition.Bottom -> slideOutVertically(tween(300)) { it }
                    VanPopupPosition.Top -> slideOutVertically(tween(300)) { -it }
                    VanPopupPosition.Left -> slideOutHorizontally(tween(300)) { -it }
                    VanPopupPosition.Right -> slideOutHorizontally(tween(300)) { it }
                    VanPopupPosition.Center -> fadeOut(tween(300))
                },
                modifier = when (position) {
                    VanPopupPosition.Bottom -> Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    VanPopupPosition.Top -> Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    VanPopupPosition.Left -> Modifier.align(Alignment.CenterStart).fillMaxHeight()
                    VanPopupPosition.Right -> Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    VanPopupPosition.Center -> Modifier.align(Alignment.Center)
                }
                    .clip(
                        when (position) {
                            VanPopupPosition.Bottom -> RoundedCornerShape(topStart = corner, topEnd = corner)
                            VanPopupPosition.Top -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
                            else -> RoundedCornerShape(0.dp)
                        },
                    )
                    .background(bg)
                    // ⚠ 面板必须是"实心"的：Compose 命中测试只认带 pointerInput 的节点，
                    //   面板空白处（内边距、标题行中间、按钮间 gap）若没人接管，点击会落到背后
                    //   铺满全屏的 VanOverlay 上，被误当成"点遮罩"收起。这里吞掉落在面板上的
                    //   点按，关闭只保留「取消/关闭」与「点遮罩」两条路。
                    .pointerInput(Unit) { detectTapGestures { } }
            .then(modifier),
            ) {
                Box(content = content)
                if (closeable) {
                    val align = when (closeIconPosition) {
                        "top-left" -> Alignment.TopStart
                        "bottom-left" -> Alignment.BottomStart
                        "bottom-right" -> Alignment.BottomEnd
                        else -> Alignment.TopEnd
                    }
                    VanIcon(
                        name = "cross",
                        size = 22.sp,
                        color = c.textPlaceholder,
                        modifier = Modifier
                            .align(align)
                            .padding(16.dp)
                            .clickable { onDismissRequest() },
                    )
                }
            }
        }
    }
}
