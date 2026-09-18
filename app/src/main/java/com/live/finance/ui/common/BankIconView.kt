package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.live.finance.core.AppConfig
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage

/**
 * 银行图标 —— 一比一复刻 web `src/components/BankIcon.vue`：
 *  - 有 `src` 用真实 logo（`object-fit: contain`）；无 src 或**加载失败**时回退「毛玻璃圆角块 + 银行名首字」；
 *  - 首字规则：`中国银行*` → 「中」；`中国*` → 去掉「中国」取首字；其它取首字；**名称为空则显示空**（不显示 "?"）；
 *  - 兜底块：`background rgba(127,127,127,.16)`、`inset 0 0 0 1px rgba(127,127,127,.2)`、
 *    字号 `max(size*.45, 8)`、圆角默认 6。
 *
 * （此前只有 `CategoryManageScreen` 内的私有同名实现，流水域列表/详情也要用 → 收口到共享件。）
 */
@Composable
fun BankIconView(
    src: String,
    name: String,
    size: Dp = 16.dp,
    rounded: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(rounded)
    Box(modifier = modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (src.isNotBlank()) {
            VanImage(
                src = AppConfig.fullFileUrl(src),
                modifier = Modifier.size(size),
                fit = ContentScale.Fit,          // web `.bank-icon-img { object-fit: contain }`
                showLoading = false,
                backgroundColor = Color.Transparent,
                errorSlot = { BankIconMock(bankInitial(name), size, rounded, colors.textSecondary) },
            )
        } else {
            BankIconMock(bankInitial(name), size, rounded, colors.textSecondary)
        }
    }
}

/** web `.bank-icon-mock`（毛玻璃圆角块 + 首字）。 */
@Composable
private fun BankIconMock(initial: String, size: Dp, rounded: Dp, textColor: Color) {
    val shape = RoundedCornerShape(rounded)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color(0x297F7F7F), shape)                       // rgba(127,127,127,.16)
            .border(1.dp, Color(0x337F7F7F), shape),                    // inset 0 0 0 1px rgba(127,127,127,.2)
        contentAlignment = Alignment.Center,
    ) {
        if (initial.isNotEmpty()) {
            FText(initial, (size.value * 0.45f).coerceAtLeast(8f), FontWeight.Bold, textColor)
        }
    }
}

/** web `bankInitial`：中国银行* → 中；中国* → 去前缀取首字；其它取首字；空名 → 空串。 */
fun bankInitial(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return ""
    if (n.startsWith("中国银行")) return "中"
    if (n.startsWith("中国")) return n.drop(2).take(1)
    return n.take(1)
}
