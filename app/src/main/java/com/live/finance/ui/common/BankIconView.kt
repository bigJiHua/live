package com.live.finance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.live.finance.core.AppConfig
import com.live.finance.core.BankIconCache
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage

/**
 * 银行图标 —— 一比一复刻 web `src/components/BankIcon.vue`：
 *  - 有 `src` 用真实 logo（`object-fit: contain`）；无 src 或**加载失败**时回退「毛玻璃圆角块 + 银行名首字」；
 *  - ⚠ 加载中/失败两条路都靠 [VanImage] 的 `loadingSlot` / `errorSlot`；`VanImage` 2026-09-20 前用
 *    `errorSlot ?: VanIcon(...)` 的 elvis 写法，**传了插槽却不调用** ⇒ 这两条路全是空白格（真 bug，已修）。
 *  - 首字规则：**取银行名第一个字**（`中国工商银行`→中）—— 与 web 的「去『中国』」规则**有意不同**，见 [bankInitial]；
 *  - 兜底块：`background rgba(127,127,127,.16)`、`inset 0 0 0 1px rgba(127,127,127,.2)`、
 *    字号 `max(size*.45, 8)`、圆角默认 6。
 *
 * （此前只有 `CategoryManageScreen` 内的私有同名实现，流水域列表/详情也要用 → 收口到共享件。）
 */
@Composable
fun BankIconView(
    src: String,
    name: String,
    size: Dp = 16.dp,
    rounded: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(rounded)
    Box(modifier = modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (src.isNotBlank()) {
            val url = AppConfig.fullFileUrl(src)
            // ⚠ 必须自带请求：SVG 走**固定 memoryCacheKey（不含 size）**，让 12dp~48dp 共用一条缓存，
            //   且与 `BankIconWarmer` 的预热请求键完全一致 ⇒ 预热一次、任意尺寸直接命中（见 BankIconCache）。
            //   只传字符串 src 的话，`SubcomposeAsyncImage` 会按各自布局尺寸分键，预热永远命中不了。
            VanImage(
                src = url,
                imageRequest = rememberBankIconRequest(url),
                modifier = Modifier.size(size),
                fit = ContentScale.Fit,          // web `.bank-icon-img { object-fit: contain }`
                showLoading = true,
                backgroundColor = Color.Transparent,
                // 下载期间也先垫「毛玻璃首字块」：web 的 `<img>` 在拿到图之前是透明的，浏览器里同样空着，
                //   但手机走局域网拉 logo 常有几秒空窗（后端不通时更久）⇒ 负责人看到的就是「LOGO 位置没做文字占位」。
                //   垫底后：图到了就盖住，失败/无图即所见，不会出现空白格。
                loadingSlot = { BankIconMock(bankInitial(name), size, rounded, colors.textSecondary) },
                errorSlot = { BankIconMock(bankInitial(name), size, rounded, colors.textSecondary) },
            )
        } else {
            BankIconMock(bankInitial(name), size, rounded, colors.textSecondary)
        }
    }
}

/** web `.bank-icon-mock`（毛玻璃圆角块 + 首字）。 */
@Composable
private fun BankIconMock(initial: String, size: Dp, rounded: Dp, textColor: Color) {
    val shape = RoundedCornerShape(rounded)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color(0x297F7F7F), shape)                       // rgba(127,127,127,.16)
            .border(1.dp, Color(0x337F7F7F), shape),                    // inset 0 0 0 1px rgba(127,127,127,.2)
        contentAlignment = Alignment.Center,
    ) {
        if (initial.isNotEmpty()) {
            // web `mockStyle.fontSize = Math.max(Math.round(size * 0.45), 8) + 'px'` —— **取整**，
            //   28dp 时是 13sp；原来直接用 28*0.45 = 12.6f，字体落到 12.6 视觉上比 web 小一档。
            FText(initial, kotlin.math.round(size.value * 0.45f).coerceAtLeast(8f), FontWeight.Bold, textColor)
        }
    }
}

/**
 * **bank logo 专用取图请求** —— 所有加载银行 logo 的地方都该用它，否则预热命不中。
 *
 * 背景见 [BankIconCache]：bank logo 是 SVG，Coil 的 `SvgDecoder` 产出的是**矢量对象**，
 * 同一份可以渲染任意尺寸；而 `SubcomposeAsyncImage` 会把**布局尺寸**拼进 `memoryCacheKey`，
 * 于是 12dp~48dp 各自成键。走这个请求 → 键与尺寸无关，且与 `BankIconWarmer` 的预热请求
 * 用的是**同一把键** ⇒ 启动时预热一次，之后任意尺寸、任意界面都是内存命中。
 *
 * 用法：`VanImage(src = url, imageRequest = rememberBankIconRequest(url), ...)`
 *
 * @return 位图（webp/png）时返回 `null` —— 位图复用错尺寸会糊，交给 Coil 按尺寸正常分键。
 */
@Composable
fun rememberBankIconRequest(url: String): ImageRequest? {
    val ctx = LocalContext.current
    return remember(url) {
        if (!BankIconCache.isVector(url)) null
        else BankIconCache.applyMemoryKey(ImageRequest.Builder(ctx).data(url), url).build()
    }
}

/**
 * 银行名首字 —— **与 web `BankIcon.vue:50-56` 完全一致**（2026-09-20 负责人最终口径：匹配掉「中国」两个字）：
 *  - `中国银行*` → 「中」（白名单：`中国银行` 整名或以其开头都保留「中」）
 *  - 其它 `中国*` → 去掉「中国」再取首字 ⇒ `中国工商银行`→「工」、`中国邮政储蓄银行`→「邮」、`中国交通银行`→「交」
 *  - 不以「中国」开头 → 直接取首字（`农业银行`→「农」、`广西农村信用社`→「广」）
 *  - 空名 → 空串（web `:52`「不显示 '?'」的口径）
 *
 * ⚠ 本函数 2026-09-20 上午曾被改成「纯首字」（`中国工商银行`→中），当天下午负责人明确纠正为
 *   **去「中国」**（→工）。`ui/bankcard/CardStackView.kt` 的 [bankInitialOf] 一直是 web 规则，
 *   两处现在一致；**不要再改回纯首字**。
 */
fun bankInitial(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return ""
    if (n.startsWith("中国银行")) return "中"
    if (n.startsWith("中国")) return n.drop(2).take(1)
    return n.take(1)
}
