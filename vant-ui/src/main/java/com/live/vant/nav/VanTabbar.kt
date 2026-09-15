package com.live.vant.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

data class VanTabbarItemData(
    val icon: String,
    val title: String,
    val name: Int? = null,
    val badge: String? = null,
    val dot: Boolean = false,
)

/**
 * 复刻 van-tabbar + van-tabbar-item（web 项目 1/4 处使用；props：v-model / fixed /
 * safe-area-inset-bottom；item：icon / badge / dot / name）。
 *
 * active 以索引表达（对应 Vant v-model）；选中色 --van-tabbar-item-active-color 主题主色。
 */
@Composable
fun VanTabbar(
    active: Int,
    onActiveChange: (Int) -> Unit,
    items: List<VanTabbarItemData>,
    modifier: Modifier = Modifier,
    fixed: Boolean = false,
    safeAreaInsetBottom: Boolean = true,
) {
    val c = LocalVantColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(c.bgCard)
            .let { if (safeAreaInsetBottom) it.systemBarsPadding() else it },
    ) {
        items.forEachIndexed { index, item ->
            val isActive = index == active
            val color = if (isActive) c.primary else c.textPrimary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onActiveChange(index) },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        VanIcon(name = item.icon, size = 22.sp, color = color)
                        if (item.dot) {
                            Box(
                                Modifier
                                    .offset(x = 3.dp, y = (-2).dp)
                                    .size(8.dp)
                                    .background(c.danger, androidx.compose.foundation.shape.CircleShape),
                            )
                        } else if (!item.badge.isNullOrEmpty()) {
                            Box(
                                Modifier
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .background(c.danger, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp),
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    item.badge, style = TextStyle(color = Color.White, fontSize = 10.sp, lineHeight = 14.sp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.text.BasicText(
                        item.title,
                        style = TextStyle(color = color, fontSize = 12.sp, lineHeight = 12.sp),
                    )
                }
            }
        }
    }
}
