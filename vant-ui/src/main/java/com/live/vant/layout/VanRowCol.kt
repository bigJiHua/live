package com.live.vant.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

/**
 * 复刻 van-row / van-col（web 项目 3/8 处使用；props：gutter；span(24 栅格) / offset）。
 *
 * Vant 用 24 列 flex 栅格 + gutter 负边距。Compose 等价：Row 容器 + 子项 weight(span/24)。
 * 注意：Vant 的 span 语义即占 24 栅格中的列数。
 */
@Composable
fun VanRow(
    modifier: Modifier = Modifier,
    gutter: Int = 0, // 列间距 px（对应 Vant :gutter，支持数组时取水平值）
    align: String = "top", // top / center / bottom
    content: @Composable RowScope.() -> Unit,
) {
    val arrangement = when (align) {
        "center" -> Alignment.CenterVertically
        "bottom" -> Alignment.Bottom
        else -> Alignment.Top
    }
    // Vant gutter 为两侧各 1/2 负 margin + 列内 padding 的间距和；Compose 等价：列间距 = gutter
    Row(
        verticalAlignment = arrangement,
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gutter.dp),
        content = content,
    )
}

/** van-col：span 1-24 */
@Composable
fun RowScope.VanCol(
    span: Int = 24,
    modifier: Modifier = Modifier,
    offset: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mod = if (onClick != null) {
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    } else modifier
    androidx.compose.foundation.layout.Box(
        modifier = mod.weight((span.coerceIn(1, 24)) / 24f),
    ) {
        content()
    }
}
