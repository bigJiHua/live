package com.live.finance.ui.bankcard

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Card
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
@Composable
fun BankCardFace(
    card: Card,
    cardTypeLabel: String,
    showLast4Suffix: Boolean,
    modifier: Modifier = Modifier,
    bankIconUrl: String = "",
    selected: Boolean = false,
    /** 卡面兜底色（web `CardStack.default-color`）；credit 页为 `#ee0a24`。 */
    defaultColor: Color = Color(0xFF4A90E2),
) {
    // web 是 `item.color || props.defaultColor` ⇒ 用**原始 color**（不是模型里写死蓝兜底的 displayColor）
    val base = parseColor(card.color, defaultColor)
    val imgUrl = card.cardImg.takeIf { it.isNotBlank() }?.let { AppConfig.fullFileUrl(it) }
    // web `debit.vue` 用 `cardImgError[card.id]` 记失败：**卡面图 404 / 解码失败 ⇒ 整张卡回落「无卡面图」分支**，
    //   而不是在卡面上摆一个破图图标（原生旧实现正是摆了 `photo-fail`，很难看）。
    var imgFailed by remember(imgUrl) { mutableStateOf(false) }
    val showImg = imgUrl != null && !imgFailed
    val shape = RoundedCornerShape(20.dp)
    val grad = Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))

    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_H.dp)
            // 阴影用 Compose 的 elevation `shadow()`：**走 RenderNode/GPU**，比 `cssShadow` 的
            // `Paint.setShadowLayer(blur 20~40)` 便宜一个数量级 —— 列表里每张卡都画模糊阴影正是
            // 他反馈「切换 tab / 滑动掉帧」的主因。视觉上等同 CSS 的 `0 8px 20px rgba(0,0,0,.1)`
            //（选中态 `0 20px 40px rgba(0,0,0,.35)` ⇒ 抬高 elevation）。
            .shadow(if (selected) 16.dp else 6.dp, shape, clip = false)
            .clip(shape)
            .background(base)
            .then(if (showImg) Modifier else Modifier.background(grad)),
    ) {
        if (showImg) {
            VanImage(
                src = imgUrl,
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
        Box(Modifier.padding(start = 20.dp, top = 20.dp, end = 5.dp, bottom = 20.dp)) {
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
            ) { CardOrgBadge(org = orgKey, width = w, height = h, filled = true) }
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
