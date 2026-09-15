package com.live.vant.form

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-stepper（web 项目 9 处使用；props：v-model / min / max / step / disable-input /
 * disable-plus / disable-minus / allow-empty / show-plus / show-minus / theme(round-plus-minus) /
 * button-size / input-width / input-height）。
 */
@Composable
fun VanStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    disabled: Boolean = false,
    disableInput: Boolean = false,
    disablePlus: Boolean = false,
    disableMinus: Boolean = false,
    /** 输入框宽/高（默认 32×28） */
    inputWidth: Int = 32,
    inputHeight: Int = 28,
    buttonSize: Int? = null, // 方形按钮边长（默认同 inputHeight）
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current
    val btn = (buttonSize ?: inputHeight).dp

    fun change(delta: Int) {
        val next = (value + delta * step).coerceIn(min, max)
        if (next != value) onValueChange(next)
    }
    val minusDisabled = disabled || disableMinus || value <= min
    val plusDisabled = disabled || disablePlus || value >= max

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // 减号按钮：.van-stepper__minus（28px 高、bg #f2f3f5、图标 minus）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(btn).height(btn)
                .background(c.bgThird)
                .clickable(enabled = !minusDisabled) { change(-1) },
        ) {
            VanIcon(
                name = "minus",
                size = 14.sp,
                color = if (minusDisabled) c.textPlaceholder else c.textPrimary,
            )
        }
        // 输入区：白底 + 上下 1px 边框（.van-stepper__input）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(inputWidth.dp)
                .height(inputHeight.dp)
                .background(c.bgCard)
                .padding(horizontal = 2.dp),
        ) {
            BasicTextField(
                value = value.toString(),
                onValueChange = { raw ->
                    val v = raw.filter { it.isDigit() || it == '-' }.toIntOrNull()
                    if (v != null && !disableInput && !disabled) {
                        onValueChange(v.coerceIn(min, max))
                    }
                },
                enabled = !disableInput && !disabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = c.textPrimary,
                    fontSize = tokens.fontSizeMd,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.defaultMinSize(minWidth = 24.dp),
            )
        }
        // 加号按钮
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(btn).height(btn)
                .background(c.bgThird)
                .clickable(enabled = !plusDisabled) { change(1) },
        ) {
            VanIcon(
                name = "plus",
                size = 14.sp,
                color = if (plusDisabled) c.textPlaceholder else c.textPrimary,
            )
        }
    }
}

/**
 * 复刻 van-switch（web 项目 24 处使用；props：v-model / size / active-color / inactive-color /
 * disabled / loading / loading-color）。
 * 默认尺寸 26px：轨道 1.8em+4 × 1em+4 = 51×30，节点 1em（与 Vant CSS 公式一致）。
 */
@Composable
fun VanSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 26, // px → dp，对应 :size（项目大量传 size="24"）
    activeColor: Color? = null,
    inactiveColor: Color? = null,
    disabled: Boolean = false,
    loading: Boolean = false,
    loadingColor: Color? = null,
) {
    val c = LocalVantColors.current
    val trackW = (size * 1.8f + 4).dp
    val trackH = (size + 4).dp
    val node = size.dp

    val trackColor = when {
        checked -> activeColor ?: c.primary
        else -> inactiveColor ?: c.switchOff
    }
    val enabled = !disabled && !loading

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .width(trackW)
            .height(trackH)
            .background(
                color = trackColor,
                shape = androidx.compose.foundation.shape.CircleShape,
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        val padding = 2.dp
        val travel = trackW - node - padding * 2
        val offset by animateDpAsState(
            targetValue = if (checked) travel else 0.dp,
            animationSpec = tween(300), // --van-switch-duration: .3s
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = offset + padding, y = padding)
                .size(node)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape),
        ) {
            if (loading) {
                com.live.vant.basic.VanLoading(
                    size = (size * 0.55f).sp,
                    color = loadingColor ?: c.primary,
                    type = com.live.vant.basic.VanLoadingType.Circular,
                )
            }
        }
    }
}
