package com.live.vant.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.live.vant.R
import com.live.vant.theme.LocalVantColors

/** vant-icon 图标字体族，对应 web 端 @font-face font-family: vant-icon */
val VantIconFontFamily = FontFamily(
    Font(R.font.vant_icon, FontWeight.Normal, FontStyle.Normal),
)

/**
 * 复刻 van-icon（web 项目内 413 处使用，最高频组件）。
 *
 * @param name 图标名（arrow/cross/plus…，完整 259 个见 [VantIconCodes.map]），
 *             以 http/https、/ 或 @ 开头时按图片渲染（对应 Vant 的 image 模式）。
 * @param size 字号，对应 :style="font-size"；缺省 16sp（cell 内 icon 视觉尺寸）。
 * @param color 对应 :color；Unspecified 时继承 LocalContentColor（currentColor）。
 * @param badge 徽标内容，对应 :badge / dot。
 */
@Composable
fun VanIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Color.Unspecified,
    badge: String? = null,
    dot: Boolean = false,
    badgeColor: Color = LocalVantColors.current.danger,
    onClick: (() -> Unit)? = null,
) {
    val clickableMod = if (onClick != null) modifier.then(Modifier.clickable { onClick() }) else modifier
    val glyph = if (isImageUrl(name)) null else VantIconCodes.glyph(name)

    Box(contentAlignment = Alignment.Center, modifier = clickableMod) {
        when {
            isImageUrl(name) -> AsyncImage(
                model = name,
                contentDescription = null,
                modifier = Modifier.size(sizeDp(size)),
                contentScale = ContentScale.Fit,
            )
            glyph != null -> androidx.compose.foundation.text.BasicText(
                text = glyph,
                style = TextStyle(
                    fontFamily = VantIconFontFamily,
                    fontSize = size,
                    color = color,
                ),
            )
            else -> Box(Modifier.size(sizeDp(size))) // 未知名称：与 web 一致，仅占位不出字形
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(8.dp)
                    .background(badgeColor, CircleShape),
            )
        } else if (!badge.isNullOrEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .background(badgeColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 4.dp, vertical = 0.dp),
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = badge,
                    style = TextStyle(color = Color.White, fontSize = 10.sp, lineHeight = 14.sp),
                )
            }
        }
    }
}

private fun isImageUrl(name: String): Boolean =
    name.startsWith("http") || name.startsWith("//") || name.startsWith("/") || name.startsWith("@") ||
        name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".svg", true) || name.endsWith(".gif", true)

/** TextUnit(sp) → Dp 视觉近似，仅用于 image 模式占位 */
private fun sizeDp(size: TextUnit): androidx.compose.ui.unit.Dp =
    if (size.value.isFinite()) size.value.dp else 32.dp
