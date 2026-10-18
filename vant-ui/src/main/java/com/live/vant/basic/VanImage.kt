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
import coil.request.ImageRequest
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-image（web 项目 22 处使用；props：src / fit / round / radius / width / height /
 * lazy-load / show-loading / show-error + loading/error 插槽）。
 * lazy-load：Compose 图片由调用方布局时机决定，这里映射为「仅可见时加载」——Coil 默认行为已满足。
 *
 * ⚠⚠ **Kotlin 陷阱（2026-09-20 真 bug，已修，别再写回去）**：
 *   插槽是 `@Composable (() -> Unit)?`，**不能**用 elvis 写 `errorSlot ?: VanIcon(...)`。
 *   `a ?: b` 在 `a != null` 时**求值成 a 这个函数引用本身**（不是调用它），而 `b`（`VanIcon()`）返回 `Unit`，
 *   整个表达式类型是 `Any`，值被丢弃 ⇒ **自定义插槽一次都不会执行**，
 *   只有「插槽为 null 走默认控件」那半边还能显示 —— 表现就是「传了兜底 UI 却永远不出现，只剩空白格」。
 *   正确写法：`if (errorSlot != null) errorSlot() else VanIcon(...)`。
 *   （本项目其它组件的插槽都写对了：`VanCell` / `VanNavBar` / `VanField` 用的都是 `xSlot?.invoke()` 或 `!= null -> xSlot()`。）
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
    /**
     * 透传口：传入自定义 [ImageRequest] 以控制 Coil 的缓存键/尺寸等（**默认 null = 行为不变**）。
     *
     * ⚠ 存在的理由：`SubcomposeAsyncImage` 按**布局约束**解析 `size` 并参与拼 `memoryCacheKey`，
     * 所以同一个 URL 在 16dp 与 48dp 两处是**两个互不相通的缓存条目**。调用方若要做
     * 「预热 ⇒ 界面命中」，必须能让自己的请求与预热请求落到**同一把键**——
     * 这在只传 `src`（String）时做不到。银行 logo 走的就是这条路，见 app 侧 `BankIconCache`。
     */
    imageRequest: ImageRequest? = null,
) {
    val c = LocalVantColors.current
    // ⚠ 记 state 的 key 必须是 src：LazyColumn 里的 VanImage 会被**复用**到另一张图，
    //   不带 key 时上一张图的 Error 会残留 ⇒ 新图还在 Loading 就被当成 Error，loading 槽被跳过 → 空白。
    var state by remember(src) { mutableStateOf(ImageState.Loading) }
    val shape = when {
        round -> CircleShape
        radius != Dp.Unspecified -> RoundedCornerShape(radius)
        else -> RoundedCornerShape(0.dp)
    }
    SubcomposeAsyncImage(
        model = imageRequest ?: src,
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
                        if (loadingSlot != null) loadingSlot() else VanLoading(size = 32.sp, color = c.buttonDefaultBorder, type = VanLoadingType.Circular)
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
                    if (errorSlot != null) errorSlot() else VanIcon(name = "photo-fail", size = 32.sp, color = c.buttonDefaultBorder)
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
