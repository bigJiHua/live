package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText

/**
 * 自写主题化输入 —— 一比一复刻 web `src/components/base/AppField.vue`（**不是** van-field：
 * 本项目 AppField 是「标签在上、控件在下」的纵向布局，而 van-field 是左侧固定宽标签）。
 *
 * 度量：根 `padding: 10px 16px` + 底 `1px solid var(--theme-border)`（`border=false` 去掉）；
 * 标签 13px `text-secondary` + 下距 6；输入 14px `text-primary`（占位 `text-tertiary`）；
 * 清除「×」18px `text-tertiary`；字数 `n/max` 12px `text-tertiary` 右对齐 + 上距 4。
 */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    border: Boolean = true,
    clearable: Boolean = false,
    maxlength: Int? = null,
    showWordLimit: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** 聚焦时回调（web `@focus`，用于收起数字键盘） */
    onFocus: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgCard),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (!label.isNullOrEmpty()) {
                FText(label, 13f, FontWeight.Normal, colors.textSecondary)
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                        FText(placeholder, 14f, FontWeight.Normal, colors.textTertiary)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { v -> onValueChange(if (maxlength != null) v.take(maxlength) else v) },
                        enabled = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
                    )
                }
                if (clearable && value.isNotEmpty()) {
                    FText("×", 18f, FontWeight.Normal, colors.textTertiary, Modifier.clickable { onValueChange("") })
                }
                if (trailing != null) trailing()
            }
            if (showWordLimit && maxlength != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    FText("${value.length}/$maxlength", 12f, FontWeight.Normal, colors.textTertiary)
                }
            }
        }
        if (border) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
    }
}
