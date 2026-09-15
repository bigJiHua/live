package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

enum class VanCheckboxShape { Square, Round }

internal val LocalCheckboxGroup = compositionLocalOf<VanCheckboxGroupState?> { null }

class VanCheckboxGroupState(
    val value: List<String>,
    val onValueChange: (List<String>) -> Unit,
    val disabled: Boolean,
)

/**
 * 复刻 van-checkbox-group（props：v-model / shape / disabled / icon-size / max）。
 */
@Composable
fun VanCheckboxGroup(
    value: List<String>,
    onValueChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    direction: String = "vertical",
    disabled: Boolean = false,
    max: Int? = null,
    content: @Composable () -> Unit,
) {
    val state = VanCheckboxGroupState(value, { next -> onValueChange(if (max != null) next.take(max) else next) }, disabled)
    CompositionLocalProvider(LocalCheckboxGroup provides state) {
        if (direction == "horizontal") {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { content() }
        } else {
            Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/**
 * 复刻 van-checkbox（web 项目 4 处；props：v-model / name / shape / disabled / label-disabled /
 * label-position + 默认插槽文字；Group 内 name 收集为数组）。
 */
@Composable
fun VanCheckbox(
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    name: String? = null,
    shape: VanCheckboxShape = VanCheckboxShape.Square,
    disabled: Boolean = false,
    indeterminate: Boolean = false,
    labelDisabled: Boolean = false,
    labelPosition: String = "right",
    iconSize: Int = 20,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val group = LocalCheckboxGroup.current
    val checkedFinal = checked ?: (name != null && group?.value?.contains(name) == true)
    val disabledFinal = disabled || (group?.disabled == true)

    fun toggle() {
        if (disabledFinal) return
        if (group != null && name != null) {
            val cur = group.value.toMutableList()
            if (checkedFinal) cur.remove(name) else cur.add(name)
            group.onValueChange(cur)
        } else {
            onCheckedChange?.invoke(!checkedFinal)
        }
    }

    val shapeCorner = if (shape == VanCheckboxShape.Round) CircleShape else RoundedCornerShape(2.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(enabled = !labelDisabled) { toggle() },
    ) {
        val icon: @Composable () -> Unit = {
            Box(
                Modifier
                    .size(iconSize.dp)
                    .alpha(if (disabledFinal) 0.5f else 1f)
                    .let {
                        when {
                            checkedFinal || indeterminate -> it.background(c.primary, shapeCorner)
                            else -> it.background(c.bgCard, shapeCorner).border(1.dp, c.textPlaceholder, shapeCorner)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    indeterminate -> Box(Modifier.size((iconSize * 0.5f).dp).background(Color.White, shapeCorner))
                    checkedFinal -> VanIcon(name = "success", size = (iconSize - 8).sp, color = Color.White)
                }
            }
        }
        val labelContent: @Composable () -> Unit = {
            when {
                content != null -> content()
                !label.isNullOrEmpty() -> androidx.compose.foundation.text.BasicText(
                    label,
                    style = TextStyle(
                        color = if (disabledFinal) c.textTertiary else c.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                    ),
                )
            }
        }
        if (labelPosition == "left") {
            labelContent()
            Box(Modifier.padding(start = 8.dp)) { icon() }
        } else {
            icon()
            Box(Modifier.padding(start = 8.dp)) { labelContent() }
        }
    }
}
