package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/** Radio 形状：dot（圆形单选点）/ square（对勾方框），对应 Vant shape */
enum class VanRadioShape { Dot, Square }

internal val LocalRadioGroup = compositionLocalOf<VanRadioGroupState?> { null }

class VanRadioGroupState(
    val value: String?,
    val onValueChange: (String) -> Unit,
    val disabled: Boolean,
)

/**
 * 复刻 van-radio-group（web 项目 11 处使用；props：v-model / direction / disabled / icon-size）。
 */
@Composable
fun VanRadioGroup(
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    direction: String = "vertical", // vertical / horizontal
    disabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val state = VanRadioGroupState(value, onValueChange, disabled)
    val arrangement = if (direction == "horizontal") Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(8.dp)
    CompositionLocalProvider(LocalRadioGroup provides state) {
        if (direction == "horizontal") {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement) { content() }
        } else {
            Column(modifier = modifier, verticalArrangement = arrangement) { content() }
        }
    }
}

/**
 * 复刻 van-radio（web 项目 28 处使用；props：name / shape / disabled / icon-size + 默认插槽为文字）。
 */
@Composable
fun VanRadio(
    modifier: Modifier = Modifier,
    name: String? = null,
    checked: Boolean? = null, // 单独使用时直接控制；在 Group 内忽略
    onCheckedChange: ((Boolean) -> Unit)? = null,
    shape: VanRadioShape = VanRadioShape.Dot,
    disabled: Boolean = false,
    labelPosition: String = "right", // left / right
    iconSize: Int = 20,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val group = LocalRadioGroup.current
    val checkedFinal = checked ?: (group?.value != null && name != null && group.value == name)
    val disabledFinal = disabled || (group?.disabled == true)
    val toggle: () -> Unit = {
        if (!disabledFinal && !checkedFinal) {
            if (group != null && name != null) group.onValueChange(name)
            else onCheckedChange?.invoke(true)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable { toggle() }.alpha(if (disabledFinal) 0.5f else 1f),
    ) {
        val box: @Composable () -> Unit = {
            val sizeMod = Modifier.size(iconSize.dp)
            when {
                // 选中 + dot 圆形（.van-radio__icon--checked）
                checkedFinal && shape == VanRadioShape.Dot -> Box(
                    sizeMod.background(c.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                }
                // 选中 + square
                checkedFinal && shape == VanRadioShape.Square -> Box(
                    sizeMod.background(c.primary, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    VanIcon(name = "success", size = (iconSize - 8).sp, color = Color.White)
                }
                // 未选中
                !checkedFinal -> when (shape) {
                    VanRadioShape.Dot -> Box(
                        sizeMod
                            .background(c.bgCard, CircleShape)
                            .border(1.dp, c.textPlaceholder, CircleShape),
                    )
                    VanRadioShape.Square -> Box(
                        sizeMod
                            .background(c.bgCard, RoundedCornerShape(2.dp))
                            .border(1.dp, c.textPlaceholder, RoundedCornerShape(2.dp)),
                    )
                }
                else -> Box(sizeMod)
            }
        }
        if (labelPosition == "left") {
            box()
            LabelText(label, content, c, disabledFinal)
        } else {
            LabelText(label, content, c, disabledFinal)
            Box(Modifier.padding(start = 8.dp)) { box() }
        }
    }
}

@Composable
private fun LabelText(
    label: String?,
    content: @Composable (() -> Unit)?,
    c: com.live.vant.theme.VantColors,
    disabled: Boolean,
) {
    when {
        content != null -> content()
        !label.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(
                color = if (disabled) c.textTertiary else c.textPrimary,
                fontSize = 14.sp,
                lineHeight = 24.sp,
            ),
        )
    }
}
