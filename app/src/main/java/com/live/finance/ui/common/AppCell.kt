package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/**
 * 自写主题化单元格 —— 一比一复刻 web `src/components/base/AppCell.vue`（**不是** van-cell）。
 *
 * 度量（照抄 web 样式）：
 *  - 根：`padding: 12px 16px`、底 `1px solid var(--theme-border)`（通栏，`border=false` 去掉）、`background: bg-secondary`
 *  - 图标：18px + `color: var(--theme-primary)` + 右距 10px（`flex-shrink:0`）
 *  - 标题：14px `text-primary`；副标题：12px `text-tertiary` + 上距 4、行高 1.4
 *  - 值：14px `text-secondary` + 左距 8、**最大宽 60%**、右对齐、`word-break: break-all`（故用 BoxWithConstraints 取 60%）
 *  - 右图标/箭头：16px `text-tertiary` + 左距 6
 */
@Composable
fun AppCell(
    title: String? = null,
    value: String? = null,
    label: String? = null,
    icon: String? = null,
    isLink: Boolean = false,
    border: Boolean = true,
    /** 垂直居中（默认顶部对齐，对应 web `.app-cell--center`） */
    center: Boolean = false,
    /** 值是占位文案时置灰（对应 web `:class="{ placeholder: !selected }"`） */
    valuePlaceholder: Boolean = false,
    valueColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    rightIcon: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .let { if (onClick != null) it.clickable { onClick() } else it },
    ) {
        val maxValueWidth = maxWidth * 0.6f   // web `.app-cell__value { max-width: 60% }`
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = if (center) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (icon != null) {
                    VanIcon(
                        name = icon,
                        size = 18.sp,
                        color = colors.primary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                // 正文（flex:1; min-width:0）
                Column(Modifier.weight(1f)) {
                    if (title != null) FText(title, 14f, FontWeight.Normal, colors.textPrimary)
                    if (!label.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
                    }
                }
                if (!value.isNullOrEmpty()) {
                    FText(
                        value,
                        14f,
                        FontWeight.Normal,
                        valueColor ?: if (valuePlaceholder) colors.textTertiary else colors.textSecondary,
                        Modifier
                            .padding(start = 8.dp)
                            .widthIn(max = maxValueWidth),
                    )
                }
                if (rightIcon != null) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(start = 6.dp)) { rightIcon() }
                } else if (isLink) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(start = 6.dp)) {
                        VanIcon(name = "arrow", size = 16.sp, color = colors.textTertiary)
                    }
                }
            }
            if (border) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().height(1.dp).background(colors.border),
                )
            }
        }
    }
}
