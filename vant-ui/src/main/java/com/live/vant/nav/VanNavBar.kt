package com.live.vant.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-nav-bar（web 项目 4 处使用；props：title / left-arrow / fixed / placeholder /
 * @click-left / @click-right / #left / #right / #title）。
 * 项目主题：标题色 #323233、返回箭头色 #323233（--van-nav-bar-icon-color 覆盖）。
 *
 * **几何照 Vant 源码**（`vant/lib/index.css`）：`__left` / `__right` 是
 * `position:absolute; top:0; bottom:0; left/right:0` 的贴边浮层，`__title` 是
 * `max-width:60%; margin:0 auto` 的常规流元素 —— 即**标题恒定居中，不随左右簇宽度漂移**
 * （早先按 `Row{左|标题 weight|右}` 实现会让标题被左右挤偏，与 web 不一致）。
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
    val d = LocalVantDimens.current
    Box(
        modifier
            .fillMaxWidth()
            .let { if (statusbarInset) it.statusBarsPadding() else it }
            .height(46.dp)
            .background(c.navBarBg),  // --van-nav-bar-background（web navBg）
    ) {
        // 标题层（常规流：`margin:0 auto` + `max-width:60%`，整条居中）
        // ⚠ 必须自带 fillMaxHeight + contentAlignment=Center：Box 的 contentAlignment 默认是 TopStart，
        // 只写 fillMaxWidth 会让标题贴到栏顶、与左右簇（各自 align(Center*)）不在同一水平线上。
        BoxWithConstraints(
            Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val titleMax = maxWidth * 0.6f  // max-width: 60%
            when {
                titleSlot != null -> titleSlot()
                !title.isNullOrEmpty() -> BasicText(
                    title,
                    modifier = Modifier.widthIn(max = titleMax),
                    // 走 `fontSizeLg` token（Vant 出厂 16px → 移动端最佳字号口径 18），这样「文字大小」档位能作用到顶栏标题
                    style = TextStyle(color = c.textPrimary, fontSize = d.fontSizeLg, textAlign = TextAlign.Center),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
        // 左簇（`.van-nav-bar__left{position:absolute;top:0;bottom:0;left:0}`）
        // ⚠ 整簇 clickable 只在真的传了 `onClickLeft` 时启用：传 `leftSlot` 时槽位内部自带热区，
        // 外层再开一次会与子节点的点击争用（`leftSlot` 为空时点击本就无意义）。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .clickable(
                    enabled = onClickLeft != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClickLeft?.invoke() },
            contentAlignment = Alignment.CenterStart,
        ) {
            // web 把 Vant 默认的 16px 覆写成 `padding: 8px 14px` → 起始内边距 14dp（不是 16）
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                if (leftArrow) {
                    VanIcon(name = "arrow-left", size = 18.sp, color = c.textPrimary)
                    Spacer(Modifier.size(2.dp))
                }
                if (!leftText.isNullOrEmpty()) {
                    BasicText(leftText, style = TextStyle(color = c.primary, fontSize = d.fontSizeMd))
                }
                leftSlot?.invoke()
            }
        }
        // 右簇（`.van-nav-bar__right{…;right:0}`）
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .clickable(
                    enabled = onClickRight != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClickRight?.invoke() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!rightText.isNullOrEmpty()) {
                BasicText(
                    rightText, style = TextStyle(color = c.primary, fontSize = d.fontSizeMd),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Box(Modifier.padding(horizontal = 16.dp)) { rightSlot?.invoke() }
            }
        }
    }
}
