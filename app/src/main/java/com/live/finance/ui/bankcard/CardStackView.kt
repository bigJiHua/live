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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Card
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
) {
    if (cards.isEmpty()) return
    val stackHeight = (CARD_H + (cards.size - 1) * STACK_H_STEP).dp
    Box(modifier.fillMaxWidth().height(stackHeight)) {
        cards.forEachIndexed { index, card ->
            if (card.id == selectedId) return@forEachIndexed   // 选中卡由页面浮层渲染
            val dimmed = selectedId != null
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = (index * STACK_STEP).dp)
                    .then(if (dimmed) Modifier.alpha(0.6f) else Modifier.clickable { onSelect(card.id) }),
            ) {
                BankCardFace(card = card, cardTypeLabel = cardTypeLabel, showLast4Suffix = true)
            }
        }
    }
}

/**
 * 单张卡面（对齐 web `.bank-card-item`）。
 * 有卡面图 → 「图片 + 右上角尾号」分支；否则 → 「银行信息 + 卡号 + 卡组织」分支。
 */
@Composable
fun BankCardFace(
    card: Card,
    cardTypeLabel: String,
    showLast4Suffix: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = parseColor(card.displayColor, Color(0xFF4A90E2))
    val imgUrl = card.cardImg.takeIf { it.isNotBlank() }?.let { AppConfig.fullFileUrl(it) }
    val shape = RoundedCornerShape(20.dp)
    val grad = Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))

    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_H.dp)
            .clip(shape)
            .background(base)
            .then(if (imgUrl == null) Modifier.background(grad) else Modifier)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(start = 20.dp, top = 20.dp, end = 5.dp, bottom = 20.dp),
    ) {
        if (imgUrl != null) {
            VanImage(
                src = imgUrl,
                modifier = Modifier.matchParentSize().clip(shape),
                fit = ContentScale.Crop,
                showLoading = false,
            )
            if (showLast4Suffix) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 15.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) { FText(card.last4, 16f, FontWeight.SemiBold, Color.White) }
            }
        } else {
            // 左上装饰圆（web `top:-20%; right:-10%; 200×200`）
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-40).dp)
                    .size(200.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BankInitialBadge(card.bankName, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    FText(
                        text = card.bankName.ifBlank { "未知银行" } +
                            if (showLast4Suffix && card.last4.isNotBlank()) "（${card.last4}）" else "",
                        sizeSp = 18f,
                        weight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (card.isDefault) DefaultTag()
                }
                Spacer(Modifier.height(30.dp))
                FText(card.groupedNo, 20f, FontWeight.SemiBold, Color.White)
                Spacer(Modifier.height(30.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(cardTypeLabel, 14f, color = Color.White.copy(alpha = 0.5f))
                        Spacer(Modifier.height(4.dp))
                        FText(
                            card.alias.ifBlank { card.cardLevel.ifBlank { "银行卡" } },
                            14f, FontWeight.SemiBold, Color.White,
                        )
                    }
                    val orgKey = cardOrgKey(card.cardOrg)
                    if (orgKey.isNotEmpty()) {
                        val w = if (orgKey == "amex") 50.dp else 80.dp
                        val h = if (orgKey == "amex") 50.dp else 40.dp
                        Box(Modifier.padding(end = 12.dp)) {
                            CardOrgBadge(org = orgKey, width = w, height = h, filled = true)
                        }
                    }
                }
            }
        }
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
