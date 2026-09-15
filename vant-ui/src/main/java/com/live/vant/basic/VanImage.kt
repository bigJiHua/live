package com.live.vant.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-image（web 项目 22 处使用；props：src / fit / round / radius / width / height /
 * lazy-load / show-loading / show-error + loading/error 插槽）。
 * lazy-load：Compose 图片由调用方布局时机决定，这里映射为「仅可见时加载」——Coil 默认行为已满足。
 */
@Composable
fun VanImage(
    src: Any?,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    fit: ContentScale = ContentScale.Crop, // Vant 默认 cover
    radius: Dp = Dp.Unspecified,
    round: Boolean = false,
    showLoading: Boolean = true,
    showError: Boolean = true,
    backgroundColor: Color? = null,
    onClick: (() -> Unit)? = null,
    loadingSlot: @Composable (() -> Unit)? = null,
    errorSlot: @Composable (() -> Unit)? = null,
) {
    val c = LocalVantColors.current
    var state by remember { mutableStateOf(ImageState.Loading) }
    val shape = when {
        round -> CircleShape
        radius != Dp.Unspecified -> RoundedCornerShape(radius)
        else -> RoundedCornerShape(0.dp)
    }
    SubcomposeAsyncImage(
        model = src,
        contentDescription = null,
        contentScale = fit,
        loading = {
            if (state != ImageState.Error) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor ?: c.bgPage),
                ) {
                    if (showLoading) {
                        loadingSlot ?: VanLoading(size = 32.sp, color = c.buttonDefaultBorder, type = VanLoadingType.Circular)
                    }
                }
            }
        },
        error = {
            state = ImageState.Error
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor ?: c.bgPage),
            ) {
                if (showError) {
                    errorSlot ?: VanIcon(name = "photo-fail", size = 32.sp, color = c.buttonDefaultBorder)
                }
            }
        },
        modifier = modifier
            .let {
                val m = if (width != Dp.Unspecified) it.width(width) else it
                if (height != Dp.Unspecified) m.height(height) else m
            }
            .clip(shape)
            .let { if (onClick != null) it.clickable { onClick() } else it },
    )
}

private enum class ImageState { Loading, Success, Error }
