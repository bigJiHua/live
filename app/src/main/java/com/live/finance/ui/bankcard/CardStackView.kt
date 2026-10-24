package com.live.finance.ui.bankcard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Card
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.rememberBankIconRequest
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage

/** 卡片堆叠几何（对齐 web `CardStack.vue` CSS）。 */
private const val CARD_H = 220f        // 卡高 220px
private const val STACK_STEP = 70f     // 堆叠中每张卡的纵向步进
private const val STACK_H_STEP = 45f   // 容器高度增量（web 用 45，保持原样）

/**
 * 卡片堆叠（一比一复刻 web `components/BankCard/CardStack.vue`）：
 * - 每张卡绝对定位 `top = index * 70dp`，容器高 `220 + (n-1) * 45 dp`；
 * - 点击卡片 → 选中（选中卡由调用方以全屏浮层渲染：top 24dp + scale 1.05）；
 * - 已有选中项时其余卡压暗（web: brightness .5 + blur 1px + translateY 10px + scale .95 + alpha .6）。
 *
 * 本组件只负责「堆叠 + 压暗」。选中卡浮层/遮罩/操作按钮由页面负责（对应 web `position:fixed`）。
 */
@Composable
fun CardStackView(
    cards: List<Card>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    cardTypeLabel: String = "DEBIT CARD",
    modifier: Modifier = Modifier,
    /** 银行 logo（card → 完整 URL）。web `.bank-icon img`；为空或加载失败才回落首字块。 */
    bankIconOf: (Card) -> String = { "" },
    /**
     * 卡面兜底色（web `CardStack` 的 `default-color` prop）：
     * debit 页传 `#4A90E2`、**credit 页传 `#ee0a24`**（`credit.vue:33` 的 `--theme-danger-color`）。
     */
    defaultColor: Color = Color(0xFF4A90E2),
) {
    if (cards.isEmpty()) return
    // ⚠ 容器高度取**满展开高** `220 + (n-1)*70`（不是 web 的 45 步进）：
    //   web 的卡是 `position:absolute`，容器矮一截也不会被裁；原生若照抄 45 且用 `padding(top)` 定位，
    //   父容器会把子项的可用高度**压缩**到最后一张只剩一条（他反馈「最下面那张只能看见一小条」）。
    //   改法：容器给满高 + 用 `offset` 定位（offset 不参与测量，不会压缩自身高度）。
    val stackHeight = (CARD_H + (cards.size - 1) * STACK_STEP).dp
    Box(modifier.fillMaxWidth().height(stackHeight)) {
        cards.forEachIndexed { index, card ->
            val dimmed = selectedId != null
            val isSel = card.id == selectedId
            key(card.id) {
                // ⚠ 用 `key(card.id)` 包住（与 `CardSortList` 同套写法）：拖拽排序 / 切 tab 重组时让 composition
                //   跟着卡走，避免「槽位被别的卡顶替」导致 `BankCardFace` 的 `imgFailed` 探针状态错位 →
                //   切 tab/滚动时被反复重新试探拉取卡面 SVG（短时切换疯狂试探的根因之一）。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (index * STACK_STEP).dp)
                        .then(
                            if (isSel) Modifier
                            // 选中卡由页面浮层渲染；这里**只占位、不留洞**——原实现 `return@forEachIndexed`
                            //   直接跳过选中卡，背景堆叠会出现一个空缺、下方卡整体上移，浮层弹起时「背景列表错位」。
                            //   改成占位保留 `CARD_H` 高度，背景列表始终完整（只是被浮层遮罩压暗）。
                            else if (dimmed) Modifier
                                // web `.is-dimmed{ filter:brightness(.5) blur(1px); transform:translateY(10px) scale(.95); opacity:.6 }`
                                //   brightness / blur 是滤镜，Compose 无对应（项目口径：不做真模糊）⇒ 只做几何 + 透明度。
                                .alpha(0.6f).scale(0.95f).offset(y = 10.dp)
                            else Modifier.clickable { onSelect(card.id) }
                        ),
                ) {
                    if (isSel) {
                        Spacer(Modifier.height(CARD_H.dp))    // 占位，保留堆叠间距
                    } else {
                        BankCardFace(
                            card = card,
                            cardTypeLabel = cardTypeLabel,
                            showLast4Suffix = true,
                            bankIconUrl = bankIconOf(card),
                            defaultColor = defaultColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单张卡面（对齐 web `.bank-card-item`）。
 * 有卡面图 → 「图片 + 尾号胶囊（未选中在右上、选中后挪到左下）」；否则 → 「银行信息 + 卡号 + 卡组织」。
 *
 * @param bankIconUrl 银行 logo 完整 URL（web `.bank-icon img`）；为空或加载失败 → `.bank-icon-mock` 首字块。
 * @param selected    是否「已弹出」那张（web `.is-selected`：换阴影 + 卡组织改浮动位 + 尾号挪左下）。
 */
/**
 * 卡面大图请求 —— 卡片堆、卡片模组实验室、卡包实验室、卡表单预览共用。
 *
 * 卡面是用户上传的照片，不能像银行 SVG 那样只按 URL 复用任意尺寸：
 * - 固定解码尺寸 **1080×680**（标准银行卡 85.6×54 ≈ 1.586）；
 * - 固定 `memoryCacheKey = card-face:<url>`：同一张卡在不同页面/槽位复用同一份位图；
 * - `diskCacheKey = url`：与普通 Coil 请求共享磁盘缓存，网络只下一次。
 *
 * `.size(1080, 680)` 会在 request 上设置 SizeResolver，Coil Compose 不会再按布局尺寸覆盖
 * ⇒ 卡片页 / 实验室 / 预览拿到的都是同一份 1080×680 位图。
 */
@Composable
fun rememberCardFaceRequest(url: String): ImageRequest {
    val ctx = LocalContext.current
    return remember(url) {
        ImageRequest.Builder(ctx)
            .data(url)
            .size(1080, 680)
            .memoryCacheKey("card-face:$url")
            .diskCacheKey(url)
            .build()
    }
}

@Composable
fun BankCardFace(
    card: Card,
    cardTypeLabel: String,
    showLast4Suffix: Boolean,
    modifier: Modifier = Modifier,
    bankIconUrl: String = "",
    /**
     * 卡组织 logo 资源 URL（web `item.cardOrgIconUrl`）：
     * 由 `category.list("bank")` 按卡组织名匹配出的 `icon_url`。非空且加载成功就显示图片，
     * 空或加载失败才回落 [CardOrgBadge] 组件（web `CardStack.vue:73-87` 同语义）。
     */
    orgIconUrl: String = "",
    selected: Boolean = false,
    /** 卡面兜底色（web `CardStack.default-color`）；credit 页为 `#ee0a24`。 */
    defaultColor: Color = Color(0xFF4A90E2),
    /** 无阴影版（CardLabScreen 浮层弹卡专用）：真机 2026-10-20 反馈弹起时不要阴影 —— Compose 的 elevation 阴影是环境光弥散灰影，与 CSS `0 20px 40px` 定向投影观感完全不同，且飞行中跟着卡走很脏。 */
    flat: Boolean = false,
) {
    // web 是 `item.color || props.defaultColor` ⇒ 用**原始 color**（不是模型里写死蓝兜底的 displayColor）
    val base = parseColor(card.color, defaultColor)
    val imgUrl = card.cardImg.takeIf { it.isNotBlank() }?.let { AppConfig.fullFileUrl(it) }
    // web `debit.vue` 用 `cardImgError[card.id]` 记失败：**卡面图 404 / 解码失败 ⇒ 整张卡回落「无卡面图」分支**，
    //   而不是在卡面上摆一个破图图标（原生旧实现正是摆了 `photo-fail`，很难看）。
    var imgFailed by remember(imgUrl) { mutableStateOf(false) }
    val showImg = imgUrl != null && !imgFailed
    var orgImgFailed by remember(orgIconUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val grad = Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))

    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_H.dp)
            // 阴影用 Compose 的 elevation `shadow()`：**走 RenderNode/GPU**，比 `cssShadow` 的
            // `Paint.setShadowLayer(blur 20~40)` 便宜一个数量级 —— 列表里每张卡都画模糊阴影正是
            // 他反馈「切换 tab / 滑动掉帧」的主因。视觉上等同 CSS 的 `0 8px 20px rgba(0,0,0,.1)`。
            // 浮层弹卡（flat）不带阴影：web 弹出态虽有 `0 20px 40px .35`，但 elevation 模拟不出
            // 定向投影的观感，用户明确不要。
            .then(
                if (flat) Modifier
                else Modifier.shadow(if (selected) 16.dp else 6.dp, shape, clip = false),
            )
            .clip(shape)
            .background(base)
            .then(if (showImg) Modifier else Modifier.background(grad)),
    ) {
        if (showImg) {
            val faceUrl = imgUrl.orEmpty()
            VanImage(
                src = faceUrl,
                // 与两个实验室/卡片堆共用固定 1080×680 位图；credit/debit 同一路径，只有无卡面图时走渐变。
                imageRequest = rememberCardFaceRequest(faceUrl),
                modifier = Modifier.matchParentSize().clip(shape),
                fit = ContentScale.Crop,
                showLoading = false,
                backgroundColor = Color.Transparent,
                // 只置位、不画东西：置位后本卡重组进「无卡面图」分支（web 行为）
                errorSlot = { LaunchedEffect(Unit) { imgFailed = true } },
            )
        } else {
            // `.bg-pattern{ position:absolute; top:-20%; right:-10%; 200×200; rgba(255,255,255,.08); border-radius:50% }`
            //   卡高 220 ⇒ top = -44dp；right -10% ≈ -33.5dp（屏宽 375 时卡宽 ≈ 335）
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 33.dp, y = (-44).dp)
                    .size(200.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }

        // ===== 内容区（web `padding: 20px 5px 20px 20px`）=====
        // ⚠ 必须 matchParentSize：web 的 header/footer-img 是占满卡宽的 flex/absolute 定位；
        //   wrap 尺寸的 Box 里 align(TopEnd/BottomStart) 全部失效（2026-10-20 真机反馈
        //   「有卡面时卡号不在右上角」的根因——尾号胶囊全挤到了左上）
        Box(Modifier.matchParentSize().padding(start = 20.dp, top = 20.dp, end = 5.dp, bottom = 20.dp)) {
            if (showImg) {
                if (selected) {
                    // `.card-footer-img{position:absolute; left:20px; bottom:20px}` + `.card-img-last4-bottom`
                    Box(Modifier.align(Alignment.BottomStart)) { Last4Pill(card.last4, bottom = true) }
                } else {
                    // `.card-header-img{display:flex; justify-content:flex-end; align-items:flex-start; gap:8px}`
                    Row(
                        Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (card.isDefault) DefaultTag()
                        if (showLast4Suffix) Last4Pill(card.last4)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    // `.card-header{display:flex; justify-content:space-between}`
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // `.bank-info{display:flex; align-items:center}`
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (bankIconUrl.isNotBlank()) {
                                VanImage(
                                    src = bankIconUrl,
                                    // 与启动预热同一把缓存键：借记卡↔信用卡来回切时 logo 直接内存命中
                                    imageRequest = rememberBankIconRequest(bankIconUrl),
                                    modifier = Modifier.size(40.dp),
                                    fit = ContentScale.Fit,      // `.bank-icon img{object-fit:contain}`
                                    // ⚠ showLoading 必须为 true：VanImage 里 loadingSlot 是**在 `if (showLoading)` 里**
                                    //   才调用的，传 false 会让下载期间这一格全空（见 VanImage 的写法）。
                                    showLoading = true,
                                    backgroundColor = Color.Transparent,
                                    loadingSlot = { BankInitialBadge(card.bankName, 40.dp) },
                                    errorSlot = { BankInitialBadge(card.bankName, 40.dp) },
                                )
                            } else {
                                BankInitialBadge(card.bankName, 40.dp)
                            }
                            Spacer(Modifier.width(12.dp))         // `.bank-name{margin-left:12px}`
                            FText(
                                card.bankName.ifBlank { "未知银行" }, 18f, FontWeight.SemiBold, Color.White,
                                modifier = Modifier.weight(1f, fill = false), maxLines = 1,
                            )
                            if (showLast4Suffix && card.last4.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                // `.bank-last4{font-size:1rem(16px→18f); font-weight:normal}`
                                FText("（${card.last4}）", 18f, FontWeight.Normal, Color.White, maxLines = 1)
                            }
                        }
                        if (card.isDefault) DefaultTag()
                    }
                    // `.card-number{font-size:20px(→20f); font-weight:600; letter-spacing:2px; margin:30px 0}`
                    //   web 是普通文本节点（按空格**换行**，超出被卡面 `overflow:hidden` 裁掉）⇒ 同样给 2 行，
                    //   不设 1 行截断（19 位卡号分 4 位组后约 24 字符，1 行必然被切）。
                    Spacer(Modifier.height(30.dp))
                    FText(card.groupedNo, 20f, FontWeight.SemiBold, Color.White,
                        letterSpacing = 2.sp, maxLines = 2)
                    Spacer(Modifier.height(30.dp))
                    // `.card-footer{display:flex; justify-content:space-between; align-items:flex-end}`
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            // `.holder-section .label{font-size:14px(→16f); opacity:.5; letter-spacing:1px}`
                            FText(cardTypeLabel, 16f, color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 1.sp, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            // `.holder-section .value{font-size:14px(→16f); font-weight:600; margin-top:4px}`
                            FText(
                                card.alias.ifBlank { card.cardLevel.ifBlank { "银行卡" } },
                                16f, FontWeight.SemiBold, Color.White, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ===== 卡组织角标（web 是**绝对定位**，不进 footer 的 flex 流）=====
        val orgKey = cardOrgKey(card.cardOrg)
        if (orgKey.isNotEmpty() && !showImg) {
            val w = if (orgKey == "amex") 50.dp else 80.dp
            val h = if (orgKey == "amex") 50.dp else 40.dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (selected) Modifier.offset(x = (-10).dp, y = (-16).dp)  // `.card-org-floating{margin-bottom:16px}`
                        else Modifier.offset(x = (-12).dp, y = (-20).dp)          // `.card-org{right:12px; bottom:20px}`
                    ),
            ) {
                // web：`cardOrgIconUrl` 优先显示资源图，图片 404/解码失败才回落内置 CardOrgIcon。
                if (orgIconUrl.isNotBlank() && !orgImgFailed) {
                    // web 的 `<img>` 按 SVG 的 width/height 属性原尺寸展示（六张资源图实测：
                    // 银联/万事达 80×48、大莱 76×48、visa/JCB 48×48、运通 45×45）；
                    // 回退组件才是 80×40 / 运通 50×50。这里按 orgKey 给对应盒，保持右缘对齐。
                    val imgW = when (orgKey) {
                        "visa", "jcb" -> 48.dp
                        "amex" -> 45.dp
                        "diners" -> 76.dp
                        else -> 80.dp
                    }
                    val imgH = if (orgKey == "amex") 45.dp else 48.dp
                    VanImage(
                        src = orgIconUrl,
                        // 卡组织 logo 与银行 logo 同属 category 字典里的 SVG：走同一把缓存键，
                        // 启动预热（BankIconWarmer 会预热 bank 分类全部 icon_url）直接命中。
                        imageRequest = rememberBankIconRequest(orgIconUrl),
                        modifier = Modifier.width(imgW).height(imgH),
                        fit = ContentScale.Fit,
                        showLoading = false,
                        backgroundColor = Color.Transparent,
                        // 只置位、不画东西：下一帧回落到 CardOrgBadge（web @error 同语义）
                        errorSlot = { LaunchedEffect(Unit) { orgImgFailed = true } },
                    )
                } else {
                    CardOrgBadge(org = orgKey, width = w, height = h, filled = true)
                }
            }
        }
    }
}

/** 尾号胶囊：web `.card-img-last4`（右上）/ `.card-img-last4-bottom`（选中后左下，稍大）。 */
@Composable
private fun Last4Pill(last4: String, bottom: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = if (bottom) 14.dp else 12.dp, vertical = if (bottom) 6.dp else 4.dp),
    ) {
        FText(last4, 18f, FontWeight.SemiBold, Color.White,
            letterSpacing = if (bottom) 3.sp else 2.sp)
    }
}

/** 银行图标兜底块（毛玻璃圆角 + 银行名首字）——对应 web `.bank-icon-mock`。 */
@Composable
fun BankInitialBadge(name: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (name.isNotBlank()) {
            FText(bankInitialOf(name), (size.value * 0.45f).coerceAtLeast(10f), FontWeight.Bold, Color.White)
        }
    }
}

/** 默认标签（web `app-tag` 被覆写为半透明白底、无边框）。 */
@Composable
private fun DefaultTag() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { FText("默认", 12f, color = Color.White) }
}

/** web `bankInitial`：中国银行* → 中；中国* → 去前缀取首字；其它取首字；空名 → 空串。 */
fun bankInitialOf(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return ""
    if (n.startsWith("中国银行")) return "中"
    if (n.startsWith("中国")) return n.drop(2).take(1)
    return n.take(1)
}

/** `#RRGGBB` / `#AARRGGBB` → Color，失败回退 fallback。 */
fun parseColor(hex: String, fallback: Color): Color {
    val h = hex.trim().removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> Color(0xFF000000L or h.toLong(16))
            8 -> Color(h.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

/**
 * 卡堆加载骨架（两个实验室共用）：2~3 张叠好的空卡 + 轻呼吸，代替居中转圈。
 *
 * 卡面可能是整张照片，骨架只画卡形和银行行占位条，不暗示具体内容；
 * 数据到达后由页面做「整堆淡入」，骨架同步淡出（见 LabCardStack / WalletStack）。
 */
@Composable
fun CardStackSkeleton(
    modifier: Modifier = Modifier,
    cardHeight: Dp = 220.dp,
    step: Dp = 64.dp,
    count: Int = 3,
) {
    val colors = LocalAppColors.current
    val n = count.coerceAtLeast(1)
    val pulse = rememberInfiniteTransition(label = "cardStackSkeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cardStackSkeletonPulse",
    )
    // 呼吸只走 graphicsLayer：状态在 layer 里读，不触发每帧重组
    Box(modifier.fillMaxWidth().height(cardHeight + step * (n - 1)).graphicsLayer { alpha = pulse.value }) {
        repeat(n) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = step * i)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.bgThird)
                    .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.border.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.border.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}
