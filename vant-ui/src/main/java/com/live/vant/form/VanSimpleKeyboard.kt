package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.rememberKeyHaptic

/** 字母页（QWERTY）；第 2 行在渲染时整体右缩进（对应 web `.kb-indent-1 { padding-left: 5.5% }`）。 */
private val SIMPLE_ABC_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("shift", "z", "x", "c", "v", "b", "n", "m", "del"),
    listOf("switch", ".", "space", "login"),
)

/** 数字/符号页。 */
private val SIMPLE_NUM_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
    listOf("-", "_", "+", "=", ",", ".", "?", ":", "'", "\""),
    listOf("switch", ".", "space", "login"),
)

/**
 * 复刻 web `components/KeyBoard/SimpleKeyboard.vue`（**非** FullKeyboard/[VanSecureKeyboard]）。
 *
 * 与 FullKeyboard 的差别（逐项对齐 web）：
 *  - 工具栏**只有状态提示**（圆点 + 「普通键盘」），不可点、无安全/普通模式切换；
 *  - 字符**直接文本渲染**，无 canvas 防 OCR、无 RSA 加密载荷、无随机偏移；
 *  - `del`/`switch` 占 2 格、`space` 占 4 格（不可点）、`login` 占 3 格；
 *  - 字母页第 2 行右缩进（CSS grid 10 列语义）。
 *
 * ⚠ 键面度量**必须与登录用 [VanSecureKeyboard] 完全一致**，否则编辑资料的全键盘比登录键盘
 * 明显高出一截（用户反馈）。统一口径：键高 52 / 行距 6 / 键距 4 / 圆角 8 /
 * 字符 24·Bold / 功能键 17·Medium / 删除箭头 24 / 「确认」17·SemiBold / 「空格」13。
 *
 * 用法：由调用方决定挂载位置（本组件不自带 Dialog/遮罩），通常贴在弹层底部。
 */
@Composable
fun VanSimpleKeyboard(
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    /** 初始页：abc / num */
    initialPage: String = "abc",
) {
    val c = LocalVantColors.current
    var page by remember { mutableStateOf(initialPage) }
    var shifted by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) // .kb-sheet{ border-radius: 18px 18px 0 0 }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x1F000000), // box-shadow: 0 -6px 24px rgba(0,0,0,.12)
                spotColor = Color(0x1F000000),
            )
            .clip(shape)
            .background(c.bgCard) // .kb-sheet{ background: var(--theme-bg-secondary) }
            .padding(5.dp), // .kb-sheet{ padding: 5px }（与 Login.vue 的 .kb-sheet 一致）
    ) {
        // 工具栏（仅状态提示，不可点）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp) // .status-dot{ width:7px; height:7px }
                    .background(c.primary, CircleShape),
            )
            Spacer(Modifier.width(5.dp)) // gap: 5px
            androidx.compose.foundation.text.BasicText(
                "普通键盘",
                style = TextStyle(color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        val rows = if (page == "abc") SIMPLE_ABC_ROWS else SIMPLE_NUM_ROWS
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp), // .kb-rows{ gap:6px }
            modifier = Modifier.height(226.dp), // 4×52 + 3×6（与 VanSecureKeyboard 的键区同高）
        ) {
            rows.forEachIndexed { ri, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp), // .kb-row{ gap:4px }
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 字母页第 2 行整体右缩进 .kb-indent-1{ padding-left:5.5% }
                    if (page == "abc" && ri == 1) {
                        Box(Modifier.fillMaxWidth(0.055f))
                    }
                    row.forEach { k ->
                        SimpleKey(
                            k = k,
                            page = page,
                            shifted = shifted,
                            onKey = { key ->
                                when (key) {
                                    "del" -> onDelete()
                                    "shift" -> shifted = !shifted
                                    "switch" -> page = if (page == "abc") "num" else "abc"
                                    "login" -> onConfirm()
                                    "space" -> Unit // 不可点
                                    else -> onChar(displayKey(key, page, shifted))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun displayKey(k: String, page: String, shifted: Boolean): String =
    if (page == "abc" && k.length == 1 && k[0].isLowerCase()) {
        if (shifted) k.uppercase() else k
    } else k

@Composable
private fun RowScope.SimpleKey(
    k: String,
    page: String,
    shifted: Boolean,
    onKey: (String) -> Unit,
) {
    val c = LocalVantColors.current
    val haptic = rememberKeyHaptic()
    val isFn = k == "del" || k == "switch" || k == "shift"
    val isSpace = k == "space"
    val isLogin = k == "login"
    val shape = RoundedCornerShape(8.dp) // 与 VanSecureKeyboard 一致（原为 10.dp）

    val bg = when {
        isLogin -> c.primary
        isFn -> c.bgThird
        else -> c.bgPage
    }
    val fg = when {
        isLogin -> c.buttonPrimaryText
        isFn -> c.textPrimary // 与 VanSecureKeyboard 一致（功能键也用主文本色）
        else -> c.textPrimary
    }
    val span = when (k) {
        "del", "switch" -> 2f
        "space" -> 4f
        "login" -> 3f
        else -> 1f
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(span)
            .height(52.dp) // .kb-key{ height:52px }（与 VanSecureKeyboard 一致，原为 58.dp）
            .offset(y = if (pressed && !isSpace) 1.dp else 0.dp) // :active{ translateY(1px) }
            .shadow(
                elevation = if (pressed) 1.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x14000000),
            )
            .clip(shape)
            .background(bg)
            .let {
                if (isSpace) it
                else it.clickable(interactionSource = interaction, indication = null) { haptic(); onKey(k) }
            },
    ) {
        when {
            k == "del" -> androidx.compose.foundation.text.BasicText(
                "←",
                style = TextStyle(color = fg, fontSize = 24.sp),
            )

            k == "space" -> androidx.compose.foundation.text.BasicText(
                "空格",
                style = TextStyle(
                    color = c.textTertiary,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                ),
            )

            k == "shift" -> Row(verticalAlignment = Alignment.CenterVertically) {
                // .sh-aa{ font-size:17px; font-weight:600 }，`.sh-aa.on` 为主色
                androidx.compose.foundation.text.BasicText(
                    "A",
                    style = TextStyle(
                        color = if (shifted) c.primary else c.textTertiary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                androidx.compose.foundation.text.BasicText(
                    "a",
                    style = TextStyle(
                        color = if (!shifted) c.primary else c.textTertiary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            k == "switch" -> androidx.compose.foundation.text.BasicText(
                if (page == "abc") "123" else "ABC",
                style = TextStyle(color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            )

            k == "login" -> androidx.compose.foundation.text.BasicText(
                "确认",
                style = TextStyle(color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )

            else -> androidx.compose.foundation.text.BasicText(
                displayKey(k, page, shifted),
                style = TextStyle(color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}
