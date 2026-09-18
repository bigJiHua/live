package com.live.vant.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.live.vant.theme.LocalVantColors
import kotlin.random.Random

/**
 * 复刻 web FullKeyboard（web/src/components/KeyBoard/FullKeyboard.vue）。
 *
 * 布局/交互严格对齐：
 *  - 两页：字母页 QWERTY（第 2 行居中缩进）、数字符号页；shift 切换大小写、switch 切页。
 *  - 功能键：del(占2) / switch(占2) / space(占4，**不可点击**，仅显示「安全键盘」标签) / login(占3，文字「确认」)。
 *  - 字符键 24sp 粗体；fn 键用三级底色；login 键主色。
 *  - 工具栏「安全模式/普通模式」圆点切换：安全模式下字符做 ±1.2dp 随机偏移（对应 web canvas 防 OCR 抖动）。
 *  - login 键仅关闭键盘（真正提交由外部按钮，与 web @login="activeField=null" 一致）。
 */
private val ABC_ROWS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("shift", "z", "x", "c", "v", "b", "n", "m", "del"),
    listOf("switch", ".", "space", "login"),
)
private val NUM_ROWS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
    listOf("-", "_", "+", "=", ",", ".", "?", ":", "'", "\""),
    listOf("switch", ".", "space", "login"),
)

private fun displayKey(k: String, page: String, shifted: Boolean): String {
    val isLower = k.length == 1 && k[0].isLowerCase()
    return if (page == "abc" && isLower) if (shifted) k.uppercase() else k else k
}

@Composable
fun VanSecureKeyboard(
    show: Boolean,
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onLogin: () -> Unit,
    /** 安全模式逐字符加密（对应 web FullKeyboard 的 RSA 加密）：非空时每个字符加密回传密文；
     *  配合 [onCipherAppend]/[onCipherDelete] 让父组件累积密文数组。null = 普通模式（明文，对应 web 降级）。 */
    encryptChar: ((String) -> String)? = null,
    /** 安全模式：追加一个字符的密文。 */
    onCipherAppend: (String) -> Unit = {},
    /** 安全模式：删除最后一个密文（与 [onDelete] 同步）。 */
    onCipherDelete: () -> Unit = {},
    defaultSecure: Boolean = true,
) {
    if (!show) return
    val c = LocalVantColors.current
    var page by remember { mutableStateOf("abc") }
    var shifted by remember { mutableStateOf(false) }
    var isSecure by remember { mutableStateOf(defaultSecure) }

    Dialog(
        onDismissRequest = onLogin,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 关闭该 Dialog 窗口自身的默认 dim（否则整屏背后被压黑、遮住输入框区域）
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val prevDim = window?.attributes?.dimAmount
            window?.setDimAmount(0f)
            onDispose { prevDim?.let { window?.setDimAmount(it) } }
        }
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
            // 点外收起遮罩（透明，无压暗），对应 web login-container @click 关闭键盘（target===currentTarget）。
            // 因 Dialog setDimAmount(0f) 已关系统 dim，这里自己画一层可点收起的透明遮罩。
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLogin() }
                    .background(Color.Transparent),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgPage)
                    .padding(vertical = 6.dp),
            ) {
                // 工具栏：安全/普通模式切换（圆点 + 文案）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { isSecure = !isSecure },
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (isSecure) c.primary else c.textTertiary, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        if (isSecure) "安全模式" else "普通模式",
                        style = TextStyle(color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Spacer(Modifier.height(6.dp))

                val rows = if (page == "abc") ABC_ROWS else NUM_ROWS
                rows.forEachIndexed { ri, row ->
                    KeyRow(row, ri, page, shifted, isSecure) { k ->
                        when (k) {
                            "shift" -> shifted = !shifted
                            "switch" -> page = if (page == "abc") "num" else "abc"
                            "del" -> {
                                onDelete()
                                // 安全模式下同步 pop 密文（与加密同受 isSecure 门控，保持密文数组与明文一致）
                                if (isSecure) onCipherDelete()
                            }
                            "login" -> onLogin()
                            "space" -> {} // 不可点击
                            else -> {
                                val ch = displayKey(k, page, shifted)
                                // 明文始终回传（持续累加到父组件的明文，供显示/眼睛预览/降级提交）
                                onChar(ch)
                                // 安全模式（isSecure 且有公钥）逐字符加密，回传密文供提交
                                if (isSecure) encryptChar?.let { onCipherAppend(it(ch)) }
                            }
                        }
                    }
                    if (ri < rows.lastIndex) Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyRow(
    row: List<String>,
    ri: Int,
    page: String,
    shifted: Boolean,
    isSecure: Boolean,
    onKey: (String) -> Unit,
) {
    val indent = page == "abc" && ri == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (indent) 16.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (k in row) {
            val weight = when (k) {
                "del", "switch" -> 2f
                "login" -> 3f
                "space" -> 4f
                else -> 1f
            }
            Key(k, weight, page, shifted, isSecure, onKey)
        }
    }
}

@Composable
private fun RowScope.Key(
    k: String,
    weight: Float,
    page: String,
    shifted: Boolean,
    isSecure: Boolean,
    onKey: (String) -> Unit,
) {
    val c = LocalVantColors.current
    val isFn = k == "shift" || k == "switch" || k == "del"
    val isLogin = k == "login"
    val isSpace = k == "space"
    val bg = when {
        isLogin -> c.primary
        isFn -> c.bgThird
        else -> c.bgCard
    }
    val fg = if (isLogin) Color.White else c.textPrimary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .height(52.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .let { m ->
                if (!isSpace) {
                    m.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onKey(k) }
                } else {
                    m
                }
            },
    ) {
        when {
            k == "del" -> BasicText("←", style = TextStyle(color = c.textPrimary, fontSize = 24.sp))
            k == "shift" -> Row {
                BasicText(
                    "A",
                    style = TextStyle(
                        color = if (shifted) c.primary else c.textTertiary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                BasicText(
                    "a",
                    style = TextStyle(
                        color = if (!shifted) c.primary else c.textTertiary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            k == "switch" -> BasicText(
                if (page == "abc") "123" else "ABC",
                style = TextStyle(color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            )
            k == "login" -> BasicText(
                "确认",
                style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            k == "space" -> BasicText(
                "安全键盘",
                style = TextStyle(color = c.textTertiary, fontSize = 13.sp, letterSpacing = 2.sp),
            )
            else -> {
                val ch = displayKey(k, page, shifted)
                // 安全模式：字符做 ±1.2dp 随机偏移，破坏 OCR 模板匹配（对应 web canvas 抖动 + 噪点）
                val offX = if (isSecure) remember(page, k) { (Random.nextFloat() - 0.5f) * 2.4f } else 0f
                val offY = if (isSecure) remember(page, k) { (Random.nextFloat() - 0.5f) * 2.4f } else 0f
                Box(Modifier.offset(x = offX.dp, y = offY.dp)) {
                    BasicText(ch, style = TextStyle(color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
