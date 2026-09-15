package com.live.vant.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanButtonType { Default, Primary, Success, Warning, Danger, Info }
enum class VanButtonSize { Large, Normal, Small, Mini }

/** 按压态：Vant 通过 :active 叠加 10% 黑遮罩（rgba(0,0,0,.1) 覆盖原色） */
internal fun Color.vantPressed(): Color =
    Color(red * 0.9f, green * 0.9f, blue * 0.9f, alpha)

/**
 * 复刻 van-button（web 项目 12 处使用；props：type / size / block / plain / round / disabled / loading / icon）。
 */
@Composable
fun VanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: VanButtonType = VanButtonType.Default,
    size: VanButtonSize = VanButtonSize.Normal,
    block: Boolean = false,
    plain: Boolean = false,
    round: Boolean = false,
    loading: Boolean = false,
    disabled: Boolean = false,
    icon: String? = null,
    /** 自定义背景色，对应 :color 覆盖（文字自动取白） */
    color: Color? = null,
    textColor: Color? = null,
) {
    val tokens = LocalVantDimens.current
    val c = LocalVantColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val typeColor = color ?: when (type) {
        VanButtonType.Primary -> c.primary
        VanButtonType.Success -> c.success
        VanButtonType.Warning -> c.warning
        VanButtonType.Danger -> c.danger
        VanButtonType.Info -> c.info
        VanButtonType.Default -> c.border
    }
    val fg: Color = textColor ?: when {
        plain -> typeColor
        type == VanButtonType.Default && color == null -> c.textPrimary
        else -> Color.White
    }
    val baseBg: Color = when {
        plain -> c.bgCard
        type == VanButtonType.Default && color == null -> c.bgCard
        else -> color ?: typeColor
    }
    val borderColor = when {
        plain -> typeColor
        type == VanButtonType.Default && color == null -> c.border
        else -> color ?: typeColor
    }

    val minHeight = when (size) {
        VanButtonSize.Large -> 50.dp
        VanButtonSize.Normal -> 44.dp
        VanButtonSize.Small -> 32.dp
        VanButtonSize.Mini -> 24.dp
    }
    val fontSize = when (size) {
        VanButtonSize.Large, VanButtonSize.Normal -> 16.sp
        VanButtonSize.Small -> 12.sp
        VanButtonSize.Mini -> 10.sp
    }
    val hPadding = when (size) {
        VanButtonSize.Large, VanButtonSize.Normal -> 15.dp
        VanButtonSize.Small -> tokens.paddingXs
        VanButtonSize.Mini -> tokens.paddingBase
    }
    val shape = RoundedCornerShape(if (round) tokens.radiusMax else tokens.radiusMd)

    Box(
        modifier = modifier
            .let { if (block) it.fillMaxWidth() else it }
            .alpha(if (disabled) tokens.disabledOpacity else 1f)
            .clip(shape)
            .background(if (pressed && !disabled && !loading) baseBg.vantPressed() else baseBg)
            .border(tokens.borderWidth, borderColor, shape)
            .clickable(
                enabled = !disabled && !loading,
                interactionSource = interaction,
                indication = null, // Vant 无 Material 水波纹，仅 :active 变色
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = hPadding),
        ) {
            if (loading) {
                VanLoading(size = 20.sp, color = fg)
                if (text.isNotEmpty()) Box(Modifier.padding(start = 4.dp)) { VanButtonLabel(text, fg, fontSize) }
            } else if (icon != null) {
                VanIcon(name = icon, size = fontSize * 1.2f, color = fg)
                if (text.isNotEmpty()) Box(Modifier.padding(start = 4.dp)) { VanButtonLabel(text, fg, fontSize) }
            } else {
                VanButtonLabel(text, fg, fontSize)
            }
        }
    }
}

/** loading 仅转圈的按钮（对应 van-button :loading 且无文字） */
@Composable
fun VanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: VanButtonType = VanButtonType.Default,
    size: VanButtonSize = VanButtonSize.Normal,
    block: Boolean = false,
    plain: Boolean = false,
    round: Boolean = false,
    disabled: Boolean = false,
) = VanButton(
    text = "",
    onClick = onClick,
    modifier = modifier,
    type = type,
    size = size,
    block = block,
    plain = plain,
    round = round,
    disabled = disabled,
)

@Composable
internal fun VanButtonLabel(text: String, color: Color, size: TextUnit) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = TextStyle(color = color, fontSize = size, lineHeight = 19.sp),
    )
}

/** Vant 图标 1.2em 换算 */
internal fun TextUnit.em(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) (16f * scale).sp else (value * scale).sp
