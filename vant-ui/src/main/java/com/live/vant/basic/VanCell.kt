package com.live.vant.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-cell（web 项目高频；props：title / value / label / icon / is-link / center / border /
 * required / large / clickable + slots title/value/label/icon/right-icon）。
 */
@Composable
fun VanCell(
    modifier: Modifier = Modifier,
    title: String? = null,
    value: String? = null,
    label: String? = null,
    icon: String? = null,
    isLink: Boolean = false,
    /** is-link 箭头方向：right/up/down/left */
    arrowDirection: String = "right",
    center: Boolean = false,
    border: Boolean = true,
    required: Boolean = false,
    large: Boolean = false,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    /** slot 化扩展（对应 Vant 各具名插槽） */
    iconSlot: @Composable (() -> Unit)? = null,
    titleSlot: @Composable (() -> Unit)? = null,
    labelSlot: @Composable (() -> Unit)? = null,
    valueSlot: @Composable (() -> Unit)? = null,
    rightIconSlot: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (pressed) c.active else c.bgCard)
            .let {
                if (onClick != null) {
                    it.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else it
            },
    ) {
        Row(
            verticalAlignment = if (center) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier.padding(
                PaddingValues(
                    start = tokens.cellHorizontalPadding,
                    end = tokens.cellHorizontalPadding,
                    top = if (large) tokens.paddingSm else tokens.cellVerticalPadding,
                    bottom = if (large) tokens.paddingSm else tokens.cellVerticalPadding,
                ),
            ),
        ) {
            if (iconSlot != null) {
                Box(Modifier.padding(end = tokens.paddingXs)) { iconSlot() }
            } else if (icon != null) {
                VanIcon(
                    name = icon,
                    size = tokens.fontSizeLg,
                    color = c.textPrimary,
                    modifier = Modifier.padding(end = tokens.paddingXs, top = 4.dp),
                )
            }
            // 标题 + 可选 label
            Column(Modifier.weight(1f)) {
                if (required) {
                    // 必填星号（.van-cell--required::before content *  danger 色）
                }
                val titleMod = Modifier.then(
                    if (required) Modifier else Modifier
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = titleMod) {
                    if (required) {
                        androidx.compose.foundation.text.BasicText(
                            "*", style = TextStyle(color = c.danger, fontSize = tokens.fontSizeMd, lineHeight = tokens.cellLineHeight),
                        )
                    }
                    when {
                        titleSlot != null -> titleSlot()
                        title != null -> androidx.compose.foundation.text.BasicText(
                            title,
                            style = TextStyle(
                                color = c.textPrimary,
                                fontSize = if (large) tokens.fontSizeLg else tokens.fontSizeMd,
                                lineHeight = tokens.cellLineHeight,
                            ),
                        )
                    }
                }
                when {
                    labelSlot != null -> labelSlot()
                    !label.isNullOrEmpty() -> {
                        Spacer(Modifier.height(tokens.paddingBase))
                        androidx.compose.foundation.text.BasicText(
                            label,
                            style = TextStyle(
                                color = c.textTertiary,
                                fontSize = tokens.fontSizeSm,
                                lineHeight = tokens.lineHeightSm,
                            ),
                        )
                    }
                }
            }
            // 右侧内容区 .van-cell__value（14 #969799 右对齐；Vant 中 value 不抢权重）
            Box(
                contentAlignment = if (center) Alignment.CenterEnd else Alignment.TopEnd,
                modifier = Modifier.padding(start = tokens.paddingXs),
            ) {
                when {
                    valueSlot != null -> valueSlot()
                    !value.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
                        value,
                        style = TextStyle(
                            color = valueColor ?: c.textTertiary,
                            fontSize = tokens.fontSizeMd,
                            lineHeight = tokens.cellLineHeight,
                        ),
                    )
                }
            }
            when {
                rightIconSlot != null -> rightIconSlot()
                isLink -> VanIcon(
                    name = when (arrowDirection) {
                        "up" -> "arrow-up"
                        "down" -> "arrow-down"
                        "left" -> "arrow-left"
                        else -> "arrow"
                    },
                    size = tokens.fontSizeLg,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        if (border) {
            // 底部 hairline（.van-cell::after，左右 inset 同 padding）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(tokens.borderWidth)
                    .padding(start = tokens.cellHorizontalPadding, end = tokens.cellHorizontalPadding)
                    .background(c.border),
            )
        }
    }
}
