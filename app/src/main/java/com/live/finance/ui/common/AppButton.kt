package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
import com.live.vant.icon.VanIcon

/** 对应 web `components/base/AppButton.vue` 的 type。 */
enum class AppButtonType { Default, Primary, Success, Warning, Danger, Text }

/** 对应 web `components/base/AppButton.vue` 的 size。 */
enum class AppButtonSize { Large, Normal, Small, Mini }

/**
 * 自写主题化按钮 —— 一比一复刻 web `src/components/base/AppButton.vue`
 * （**不是** `van-button`：`vant-ui` 里的 [com.live.vant.basic.VanButton] 复刻的是 van-button）。
 *
 * 关键差异（这几点正是"用 VanButton 会跑偏"的地方）：
 *  - primary 实心是 **135° 主色渐变 + primary 25% 阴影**，不是纯色；
 *  - plain 是 **透明底**（不是白卡底），描边与文字同 type 色；
 *  - `Danger` 默认是实心红，**web 的退出登录用的是 `plain + round`**（空心红描边），不是大红块；
 *  - 尺寸 normal 44 / large 50 / small 34 / mini 28，圆角 8（round → 999）；
 *  - 按压 scale(.97)、禁用 opacity .55。
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: AppButtonType = AppButtonType.Default,
    size: AppButtonSize = AppButtonSize.Normal,
    plain: Boolean = false,
    block: Boolean = false,
    round: Boolean = false,
    disabled: Boolean = false,
    loading: Boolean = false,
    icon: String? = null,
    /** 页面级覆盖（web 用 `.quick-add-btn{height:32px;padding:0 15px}` 这类页面类覆写底座度量） */
    height: Dp? = null,
    hPadding: Dp? = null,
    cornerRadius: Dp? = null,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDisabled = disabled || loading

    val typeColor = when (type) {
        AppButtonType.Primary -> tokens.primary
        AppButtonType.Success -> tokens.success
        AppButtonType.Warning -> tokens.warning
        AppButtonType.Danger -> tokens.danger
        AppButtonType.Text -> tokens.primary
        AppButtonType.Default -> colors.border
    }
    val fg: Color = when {
        type == AppButtonType.Text -> tokens.primary
        // web `.app-btn--default.app-btn--plain { color: var(--theme-text-secondary) }`（其余 plain 色仍取 type 色）
        plain && type == AppButtonType.Default -> colors.textSecondary
        plain -> typeColor
        type == AppButtonType.Default -> colors.textPrimary
        type == AppButtonType.Primary -> tokens.buttonPrimaryText
        else -> Color.White
    }
    val solidBg: Color = when {
        plain || type == AppButtonType.Text || type == AppButtonType.Primary -> Color.Transparent
        type == AppButtonType.Default -> colors.bgCard
        else -> typeColor
    }
    // web `.app-btn` 基础是 `border: 1px solid transparent`，再由各 type 覆盖 border-color
    val borderColor: Color = if (type == AppButtonType.Text) Color.Transparent else typeColor

    val sizeHeight = when (size) {
        AppButtonSize.Large -> 50.dp
        AppButtonSize.Normal -> 44.dp
        AppButtonSize.Small -> 34.dp
        AppButtonSize.Mini -> 28.dp
    }
    val sizeHPadding = when (size) {
        AppButtonSize.Large -> 18.dp
        AppButtonSize.Normal -> 16.dp
        AppButtonSize.Small -> 12.dp
        AppButtonSize.Mini -> 8.dp
    }
    val fontSize = when (size) {
        AppButtonSize.Large -> 16f
        AppButtonSize.Normal -> 15f
        AppButtonSize.Small -> 13f
        AppButtonSize.Mini -> 12f
    }
    val btnHeight = height ?: sizeHeight
    // web `.app-btn--text { padding-left/right: 8px }`：text 类型横向内边距固定 8，不随 size 变
    val btnHPadding = when {
        type == AppButtonType.Text -> 8.dp
        else -> hPadding ?: sizeHPadding
    }
    val radius = cornerRadius ?: if (round) 999.dp else 8.dp
    val shape = RoundedCornerShape(radius)
    val gradientPrimary = type == AppButtonType.Primary && !plain

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .let { if (block) it.fillMaxWidth() else it }
            .alpha(if (isDisabled) 0.55f else 1f)
            .height(btnHeight)
            .graphicsLayer(scaleX = if (pressed && !isDisabled) 0.97f else 1f, scaleY = if (pressed && !isDisabled) 0.97f else 1f)
            // 主色实心：0 4px 12px rgba(var(--theme-primary-rgb), .25)
            .let {
                if (gradientPrimary) {
                    it.cssShadow(radius, WebShadow(0f, 4f, 12f, tokens.primary.copy(alpha = 0.25f)))
                } else {
                    it
                }
            }
            .clip(shape)
            .background(
                brush = if (gradientPrimary) Brush.linearGradient(listOf(tokens.primary, tokens.grad)) else SolidColor(solidBg),
                shape = shape,
            )
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = !isDisabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = btnHPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                loading -> VanLoading(size = 16.sp, color = fg, type = VanLoadingType.Circular)
                icon != null -> VanIcon(
                    name = icon,
                    size = (fontSize * 1.1f).sp, // .app-btn__icon { font-size: 1.1em }
                    color = fg,
                    modifier = Modifier.padding(end = 6.dp), // .app-btn { gap: 6px }
                )
            }
            if (text.isNotEmpty()) FText(text, fontSize, FontWeight.Medium, fg)
        }
    }
}
