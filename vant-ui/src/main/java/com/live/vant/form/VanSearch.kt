package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-search（web 项目 8 处使用；props：v-model / placeholder / shape / background /
 * show-action / action-text / clearable / label；事件 search / cancel / clear）。
 */
@Composable
fun VanSearch(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    shape: String = "square", // "square" | "round"
    background: Color? = null,
    showAction: Boolean = false,
    actionText: String = "取消",
    clearable: Boolean = true,
    label: String? = null,
    onSearch: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val containerBg = background ?: c.bgCard

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(containerBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(tokens.searchInputHeight)
                .background(
                    c.bgPage, // --van-search-content-background
                    RoundedCornerShape(if (shape == "round") 17.dp else 4.dp),
                )
                .padding(horizontal = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!label.isNullOrEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        label,
                        style = TextStyle(color = c.textPrimary, fontSize = tokens.fontSizeMd),
                        modifier = Modifier.padding(horizontal = 5.dp),
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(c.border))
                }
                VanIcon(name = "search", size = 16.sp, color = c.textTertiary, modifier = Modifier.padding(horizontal = 8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        androidx.compose.foundation.text.BasicText(
                            placeholder,
                            style = TextStyle(color = c.textTertiary, fontSize = tokens.fontSizeMd),
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(color = c.textPrimary, fontSize = tokens.fontSizeMd),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(value) }),
                    )
                }
                if (clearable && value.isNotEmpty()) {
                    VanIcon(
                        name = "clear",
                        size = 16.sp,
                        color = c.textPlaceholder,
                        modifier = Modifier.padding(start = 4.dp).clickable { onValueChange(""); onClear?.invoke() },
                    )
                }
            }
        }
        if (showAction) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText(
                actionText,
                style = TextStyle(color = c.textPrimary, fontSize = tokens.fontSizeMd),
                modifier = Modifier
                    .clickable { onCancel?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
