package com.live.vant.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-nav-bar（web 项目 4 处使用；props：title / left-arrow / fixed / placeholder /
 * @click-left / @click-right / #left / #right / #title）。
 * 项目主题：标题色 #323233、返回箭头色 #323233（--van-nav-bar-icon-color 覆盖）。
 */
@Composable
fun VanNavBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    leftArrow: Boolean = false,
    fixed: Boolean = false,
    statusbarInset: Boolean = fixed, // fixed 时自动加状态栏 padding（对应 placeholder/fixed 行为）
    leftText: String? = null,
    rightText: String? = null,
    onClickLeft: (() -> Unit)? = null,
    onClickRight: (() -> Unit)? = null,
    leftSlot: @Composable (() -> Unit)? = null,
    rightSlot: @Composable (() -> Unit)? = null,
    titleSlot: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .let { if (statusbarInset) it.statusBarsPadding() else it }
            .height(46.dp)
            .background(c.bgCard),
    ) {
        // 左侧区
        Box(
            Modifier
                .height(46.dp)
                .clickable(
                    enabled = onClickLeft != null || leftSlot != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClickLeft?.invoke() },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                if (leftArrow) {
                    VanIcon(name = "arrow-left", size = 18.sp, color = c.textPrimary)
                    Spacer(Modifier.size(2.dp))
                }
                if (!leftText.isNullOrEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        leftText, style = TextStyle(color = c.primary, fontSize = 14.sp),
                    )
                }
                leftSlot?.invoke()
            }
        }
        // 标题
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                titleSlot != null -> titleSlot()
                !title.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
                    title,
                    style = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
        // 右侧区
        Box(
            Modifier
                .height(46.dp)
                .clickable(
                    enabled = onClickRight != null || rightSlot != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClickRight?.invoke() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!rightText.isNullOrEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    rightText, style = TextStyle(color = c.primary, fontSize = 14.sp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Box(Modifier.padding(horizontal = 16.dp)) { rightSlot?.invoke() }
            }
        }
    }
}
