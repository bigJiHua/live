package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import kotlin.random.Random

/**
 * 复刻 web `components/KeyBoard/index.vue`（SafeKeyboard）—— **数字安全键盘**，用于 PIN 码输入。
 *
 * ⚠ 与另两个键盘组件不是一回事：
 *  - [VanSimpleKeyboard] = web `SimpleKeyboard.vue`（字母/符号，编辑资料用）；
 *  - [VanSecureKeyboard] = web `FullKeyboard.vue`（全键盘，登录用）；
 *  - 本组件 = web `KeyBoard/index.vue`：3 列 4 行 = `[1-9] + 收起 + 0 + 删除`。
 *
 * 安全模式：打乱 1-9 顺序 + 数字**随机偏移**（web 还用 canvas 画噪点/干扰线做 OCR 对抗，
 * 原生以随机偏移近似，不再画噪点）。
 */
@Composable
fun VanSafeKeyboard(
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalVantColors.current
    var isSecure by remember { mutableStateOf(true) }
    /** 每次重排 +1，用于重新生成键序与随机偏移（web `initLayout` 的等价物）。 */
    var layoutSeed by remember { mutableIntStateOf(0) }

    val digitKeys = remember(layoutSeed, isSecure) {
        val nums = (1..9).toMutableList()
        if (isSecure) nums.shuffle() // 安全模式打乱 1-9
        nums.map { it.toString() }
    }
    val keys = remember(digitKeys) { digitKeys + listOf("close", "0", "del") }
    // 12 个键各一对随机偏移（±1.2dp），仅安全模式生效
    val offsets = remember(layoutSeed, isSecure) {
        List(12) { Random.nextFloat() * 2.4f - 1.2f to Random.nextFloat() * 2.4f - 1.2f }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bgThird), // .safe-keyboard{ background: var(--theme-bg-tertiary) }
    ) {
        // 工具栏（普通模式 ↔ 安全模式切换）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp), // .keyboard-toolbar{ padding: 10px 16px }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    isSecure = !isSecure
                    layoutSeed++
                },
            ) {
                // .status-dot（安全模式：主色 + 6px 光晕）
                Box(
                    Modifier
                        .size(7.dp)
                        .let {
                            if (isSecure) it.shadow(3.dp, CircleShape, clip = false, ambientColor = c.primary, spotColor = c.primary)
                            else it
                        }
                        .background(if (isSecure) c.primary else c.textTertiary, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicText(
                    if (isSecure) "安全模式" else "普通模式",
                    style = TextStyle(color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.width(6.dp))
                VanIcon(name = if (isSecure) "shield-o" else "shield", size = 14.sp, color = c.primary)
            }
        }

        // 键格：3 列 × 4 行，gap 8，padding 4/12/12
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            keys.chunked(3).forEachIndexed { rowIndex, rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowKeys.forEachIndexed { colIndex, k ->
                        SafeKey(
                            k = k,
                            isSecure = isSecure,
                            jitter = offsets[rowIndex * 3 + colIndex],
                            modifier = Modifier.weight(1f),
                            onKey = { key ->
                                when (key) {
                                    "close" -> onClose()
                                    "del" -> onDelete()
                                    else -> onChar(key)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeKey(
    k: String,
    isSecure: Boolean,
    jitter: Pair<Float, Float>,
    modifier: Modifier,
    onKey: (String) -> Unit,
) {
    val c = LocalVantColors.current
    val isFn = k == "del" || k == "close"
    val shape = RoundedCornerShape(12.dp) // .key-item{ border-radius:12px }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(54.dp) // .key-item{ height:54px }
            .offset(y = if (pressed) 2.dp else 0.dp) // :active{ transform: translateY(2px) }
            .shadow(
                elevation = if (pressed) 1.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x14000000),
            )
            .clip(shape)
            .background(
                when {
                    pressed -> c.bgThird // :active{ background: var(--theme-bg-tertiary) }
                    isFn -> c.bgPage // .is-functional{ background: var(--theme-bg-primary) }
                    else -> c.bgCard
                },
            )
            .clickable(interactionSource = interaction, indication = null) { onKey(k) },
    ) {
        when {
            k == "del" -> androidx.compose.foundation.text.BasicText(
                "删除",
                style = TextStyle(color = c.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )

            k == "close" -> VanIcon(name = "arrow-down", size = 22.sp, color = c.textSecondary)

            else -> androidx.compose.foundation.text.BasicText(
                k,
                style = TextStyle(color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                // 安全模式：随机偏移，破坏 OCR 模板匹配
                modifier = if (isSecure) Modifier.offset(x = jitter.first.dp, y = jitter.second.dp) else Modifier,
            )
        }
    }
}
