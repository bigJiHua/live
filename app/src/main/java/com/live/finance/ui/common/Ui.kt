package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.theme.LocalAppColors

/** 页面统一底：项目页面底色 + 状态栏避让（对应 web body 背景）。 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppColors.current.bgPage)
            .systemBarsPadding(),
    ) { content(Modifier.fillMaxSize()) }
}

/** 卡片白底容器。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(LocalAppColors.current.bgCard)
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun SectionText(text: String, large: Boolean = false) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = TextStyle(
            color = LocalAppColors.current.textPrimary,
            fontSize = if (large) 20.sp else 15.sp,
        ),
    )
}

/** 通用可点击列表行（左侧标题、右侧值 + 箭头），对应 web van-cell is-link。 */
@Composable
fun MenuRow(
    title: String,
    value: String = "",
    onClick: () -> Unit = {},
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            title,
            style = TextStyle(color = LocalAppColors.current.textPrimary, fontSize = 15.sp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = androidx.compose.ui.Alignment.CenterEnd,
        ) {
            androidx.compose.foundation.text.BasicText(
                value,
                style = TextStyle(color = LocalAppColors.current.textTertiary, fontSize = 13.sp),
            )
        }
    }
}
