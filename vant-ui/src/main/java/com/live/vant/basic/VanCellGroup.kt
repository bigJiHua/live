package com.live.vant.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-cell-group（web 项目 125 处使用；props：title / inset）。
 * inset 对应页面内白卡分组（radius 8、左右 margin 16），与项目实际用法一致。
 */
@Composable
fun VanCellGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    inset: Boolean = false,
    titleSlot: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (titleSlot != null || !title.isNullOrEmpty()) {
            val titleModifier = Modifier.padding(
                horizontal = tokens.paddingMd,
                vertical = tokens.paddingMd,
            )
            when {
                titleSlot != null -> titleSlot()
                else -> androidx.compose.foundation.text.BasicText(
                    title!!,
                    style = TextStyle(color = c.textTertiary, fontSize = tokens.fontSizeMd, lineHeight = 16.sp),
                    modifier = titleModifier,
                )
            }
        }
        if (inset) {
            Column(
                Modifier
                    .padding(horizontal = tokens.paddingMd)
                    .clip(RoundedCornerShape(tokens.cellGroupInsetRadius))
                    .background(c.bgCard),
                content = content,
            )
        } else {
            Column(Modifier.background(c.bgCard), content = content)
        }
    }
}
