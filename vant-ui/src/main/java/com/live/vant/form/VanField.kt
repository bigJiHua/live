package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanFieldType { Text, Password, Number, Digit, Tel, Email, Textarea }

/**
 * 复刻 van-field（web 项目 9 处使用；props：v-model / label / placeholder / type / readonly /
 * disabled / clearable / required / maxlength / show-word-limit / error-message / input-align /
 * left-icon / right-icon / label-width + slots input/label/left-icon/right-icon）。
 */
@Composable
fun VanField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    type: VanFieldType = VanFieldType.Text,
    readonly: Boolean = false,
    disabled: Boolean = false,
    required: Boolean = false,
    clearable: Boolean = false,
    maxlength: Int? = null,
    showWordLimit: Boolean = false,
    errorMessage: String? = null,
    inputAlign: TextAlign = TextAlign.Left,
    labelWidth: Dp = 62.dp, // --van-field-label-width: 6.2em ≈ 62px（14px 字号）
    leftIcon: String? = null,
    rightIcon: String? = null,
    rows: Int = if (type == VanFieldType.Textarea) 3 else 1,
    imeAction: ImeAction = ImeAction.Default,
    /** readonly + 点击（用于唤起 Picker 等，对应 @click） */
    onClick: (() -> Unit)? = null,
    /** input 插槽：完全自定义输入区（对应 Vant #input slot） */
    inputSlot: @Composable (() -> Unit)? = null,
    /** 右侧插槽（对应 #button / #extra） */
    buttonSlot: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current

    val keyboardType = when (type) {
        VanFieldType.Number -> KeyboardType.NumberPassword // 数字无负号/小数点（vant type=number）
        VanFieldType.Digit -> KeyboardType.Decimal
        VanFieldType.Tel -> KeyboardType.Phone
        VanFieldType.Email -> KeyboardType.Email
        VanFieldType.Password -> KeyboardType.Password
        else -> KeyboardType.Text
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(c.bgCard)
            .let { if (readonly && onClick != null) it.clickable { onClick() } else it },
    ) {
        Row(
            verticalAlignment = if (type == VanFieldType.Textarea) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = tokens.cellHorizontalPadding,
                end = tokens.cellHorizontalPadding,
                top = tokens.cellVerticalPadding,
                bottom = tokens.cellVerticalPadding,
            ),
        ) {
            if (!label.isNullOrEmpty()) {
                Row(Modifier.width(labelWidth)) {
                    if (required) {
                        Box(Modifier.padding(end = 2.dp)) {
                            androidx.compose.foundation.text.BasicText(
                                "*", style = TextStyle(color = c.danger, fontSize = tokens.fontSizeMd),
                            )
                        }
                    }
                    androidx.compose.foundation.text.BasicText(
                        label,
                        style = TextStyle(
                            color = c.textSecondary, // 项目覆盖 --van-field-label-color: text-secondary
                            fontSize = tokens.fontSizeMd,
                            lineHeight = tokens.cellLineHeight,
                        ),
                    )
                }
                Spacer(Modifier.width(tokens.paddingSm))
            }
            Box(Modifier.weight(1f)) {
                val inputStyle = TextStyle(
                    color = if (disabled) c.textPlaceholder else c.textPrimary,
                    fontSize = tokens.fontSizeMd,
                    lineHeight = tokens.cellLineHeight,
                    textAlign = inputAlign,
                )
                if (inputSlot != null) {
                    inputSlot()
                } else if (readonly && onClick != null) {
                    // readonly：不渲染可编辑文本框（与 web pointer-events:none 一致）
                    androidx.compose.foundation.text.BasicText(
                        text = if (value.isNotEmpty()) value else (placeholder ?: ""),
                        style = inputStyle.copy(
                            color = if (value.isNotEmpty()) inputStyle.color else c.textPlaceholder,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = { v -> onValueChange(maxlength?.let { v.take(it) } ?: v) },
                        enabled = !disabled,
                        textStyle = inputStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                        visualTransformation = if (type == VanFieldType.Password) PasswordVisualTransformation() else VisualTransformation.None,
                        singleLine = type != VanFieldType.Textarea,
                        minLines = rows,
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (value.isEmpty()) {
                                    androidx.compose.foundation.text.BasicText(
                                        placeholder ?: "",
                                        style = inputStyle.copy(color = c.textPlaceholder),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }
            // 清除按钮（clearable 且有输入内容时）
            if (clearable && !readonly && value.isNotEmpty()) {
                VanIcon(
                    name = "clear",
                    size = 18.sp,
                    color = c.textPlaceholder,
                    modifier = Modifier.padding(start = tokens.paddingBase).clickable { onValueChange("") },
                )
            }
            if (leftIcon != null) {
                VanIcon(name = leftIcon, size = 18.sp, color = c.textTertiary, modifier = Modifier.padding(start = tokens.paddingBase))
            }
            if (rightIcon != null) {
                VanIcon(name = rightIcon, size = 18.sp, color = c.textTertiary, modifier = Modifier.padding(start = tokens.paddingBase))
            }
            if (buttonSlot != null) {
                Spacer(Modifier.width(tokens.paddingBase))
                buttonSlot()
            }
        }
        if (showWordLimit && maxlength != null) {
            androidx.compose.foundation.text.BasicText(
                "${value.length}/$maxlength",
                style = TextStyle(color = c.textTertiary, fontSize = tokens.fontSizeSm, lineHeight = 16.sp, textAlign = TextAlign.End),
                modifier = Modifier.fillMaxWidth().padding(end = tokens.cellHorizontalPadding),
            )
        }
        if (!errorMessage.isNullOrEmpty()) {
            androidx.compose.foundation.text.BasicText(
                errorMessage,
                style = TextStyle(color = c.danger, fontSize = tokens.fontSizeSm, lineHeight = tokens.lineHeightSm),
                modifier = Modifier.padding(start = tokens.cellHorizontalPadding, bottom = tokens.cellVerticalPadding),
            )
        }
        Spacer(Modifier.height(tokens.borderWidth).background(c.border).fillMaxWidth())
    }
}
