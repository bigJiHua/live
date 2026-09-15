package com.live.vant.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanTagType { Default, Primary, Success, Warning, Danger }
enum class VanTagSize { Small, Medium, Large }

/**
 * 复刻 van-tag（web 项目 3 处使用；props：type / size / plain / round / mark / closeable / color / text-color）。
 * 默认类型背景按项目主题覆盖为 #f2f3f5（--van-tag-default-background）。
 */
@Composable
fun VanTag(
    text: String,
    modifier: Modifier = Modifier,
    type: VanTagType = VanTagType.Default,
    size: VanTagSize = VanTagSize.Medium,
    plain: Boolean = false,
    round: Boolean = false,
    mark: Boolean = false,
    closeable: Boolean = false,
    color: Color? = null, // 自定义背景色（plain 时为边框/文字色）
    textColor: Color? = null,
    onClose: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current

    val themeColor = color ?: when (type) {
        VanTagType.Primary -> c.primary
        VanTagType.Success -> c.success
        VanTagType.Warning -> c.warning
        VanTagType.Danger -> c.danger
        VanTagType.Default -> c.textTertiary
    }
    val fg = textColor ?: when {
        type == VanTagType.Default -> if (color == null) c.textSecondary else Color.White
        plain -> themeColor
        else -> Color.White
    }
    val bg = when {
        plain -> c.bgCard
        type == VanTagType.Default && color == null -> c.bgThird
        else -> color ?: themeColor
    }

    val fontSize = if (size == VanTagSize.Large) tokens.fontSizeMd else tokens.fontSizeSm
    val hPad = when (size) {
        VanTagSize.Medium -> if (mark) 6.dp else tokens.paddingBase
        VanTagSize.Large -> tokens.paddingXs
        VanTagSize.Small -> tokens.paddingBase
    }
    val vPad = if (size == VanTagSize.Large) tokens.paddingBase else if (size == VanTagSize.Medium) 2.dp else 0.dp
    val shape = when {
        mark -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = tokens.radiusMax, bottomEnd = tokens.radiusMax)
        round -> RoundedCornerShape(tokens.radiusMax)
        size == VanTagSize.Large -> RoundedCornerShape(tokens.radiusMd)
        else -> RoundedCornerShape(tokens.radiusSm)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (plain) Modifier.border(tokens.borderWidth, themeColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = hPad, vertical = vPad),
    ) {
        androidx.compose.foundation.text.BasicText(
            text,
            style = TextStyle(color = fg, fontSize = fontSize, lineHeight = 16.sp),
        )
        if (closeable) {
            VanIcon(
                name = "cross",
                size = fontSize,
                color = fg,
                modifier = Modifier.padding(start = 2.dp).clickable { onClose?.invoke() },
            )
        }
    }
}
