package com.live.vant.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors

data class VanTabItem(
    val name: String,
    val title: String,
    val disabled: Boolean = false,
    val badge: String? = null,
    val dot: Boolean = false,
)

/**
 * 复刻 van-tabs + van-tab（web 项目 8 组 / 25 页签使用；props：v-model:active / sticky / shrink /
 * animated / swipeable / @change）。
 *
 * - line 模式：44px 行高、14px 文字、选中主题色 + 底部 40×3 指示条（缩放动画）
 * - card 模式：30px 胶囊，选中实底白字
 * 内容区由 [content] 按 active 渲染（animated 淡切）。
 */
@Composable
fun VanTabs(
    active: String,
    onActiveChange: (String) -> Unit,
    tabs: List<VanTabItem>,
    modifier: Modifier = Modifier,
    type: String = "line",
    color: Color? = null,
    titleActiveColor: Color? = null,
    titleInactiveColor: Color? = null,
    animated: Boolean = false,
    shrink: Boolean = false, // 页签宽度收缩（不拉伸满屏）
    content: @Composable (name: String) -> Unit,
) {
    val c = LocalVantColors.current
    val activeColor = color ?: c.primary
    val fgActive = titleActiveColor ?: if (type == "card") Color.White else activeColor
    val fgInactive = titleInactiveColor ?: c.textPrimary

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(c.navBarBg),  // --van-tabs-nav-background（web navBg）
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = tab.name == active
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f, fill = !shrink)
                        .height(44.dp)
                        .clickable(enabled = !tab.disabled) { onActiveChange(tab.name) },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = if (type == "card") 0.dp else 4.dp)) {
                        if (type == "card") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(30.dp)
                                    .background(if (isActive) activeColor else Color.Transparent, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 12.dp),
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    tab.title,
                                    style = TextStyle(color = if (isActive) Color.White else activeColor, fontSize = 14.sp, lineHeight = 20.sp),
                                )
                            }
                        } else {
                            androidx.compose.foundation.text.BasicText(
                                tab.title,
                                style = TextStyle(
                                    color = if (tab.disabled) c.textTertiary else if (isActive) fgActive else fgInactive,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                ),
                            )
                        }
                    }
                    if (tab.dot) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 4.dp)
                                .size(8.dp)
                                .background(c.danger, RoundedCornerShape(4.dp)),
                        )
                    } else if (!tab.badge.isNullOrEmpty()) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .background(c.danger, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp),
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                tab.badge, style = TextStyle(color = Color.White, fontSize = 10.sp, lineHeight = 14.sp),
                            )
                        }
                    }
                    // line 指示条：40×3 缩放动画（对应 --van-tabs-bottom-bar-*）
                    if (type == "line") {
                        val scale by animateFloatAsState(if (isActive) 1f else 0f, tween(250), label = "tabbar")
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width((40 * scale).dp)
                                .height(3.dp)
                                .background(activeColor, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        // 内容区
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (animated) {
                AnimatedContent(
                    targetState = active,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                ) { name ->
                    content(name)
                }
            } else {
                content(active)
            }
        }
    }
}
