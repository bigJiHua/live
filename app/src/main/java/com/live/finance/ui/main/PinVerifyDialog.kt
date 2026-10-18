package com.live.finance.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.PinTicket
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonSize
import com.live.vant.basic.VanButtonType
import com.live.vant.form.VanPasswordInput
import com.live.vant.form.VanSafeKeyboard

/**
 * 全局 PIN 验证弹窗 —— 一比一复刻 web `components/PinVerifyDialog.vue`。
 *
 * 触发方式与 web 一致：不由页面自己调用，而是挂在 `PinCoordinator.show` 上。
 * 任何请求命中 8303（`ApiClient.pinGate` → `PinCoordinator.awaitVerify`）时这里就出现；
 * 风险路由（`data.action_type == "route_verify"`）走 `/security/pin/route-verify` 拿一次性令牌，
 * 其余（如 `/auth/lock-system` 的系统锁定）走 `/security/pin/verify`，验证通过后 `ApiClient` 原样重发。
 *
 * 对照 web 的度量：`van-overlay`(rgba(0,0,0,.7)) 不可点关 → 320px 卡片（radius 16 / padding 28·24·20 /
 * bg-secondary）→ 标题「安全验证」18/600 → 描述「请输入 PIN 码以继续操作」14 → `van-password-input`
 * （length 6、gutter 10、focused 跟键盘）→ 错误行（13 danger，仅在有错误时占位）→ **只有「取消」按钮**
 * （web 的确认按钮被注释掉，提交靠满 6 位后 150ms 自动进行）→ 底部 `.pin-keyboard-overlay`
 * （rgba(0,0,0,.5)，点它含键盘内缝隙都只收起键盘、不关弹窗，与 web 的 DOM 冒泡一致）。
 *
 * 8302（PIN 错）→ 就地显示后端文案并清空已输；8304（连错 3 次锁定）→ 收框，
 * 登出由 `ApiClient.onSessionBlocked` 统一走（等价 web `onPinLocked → forceLogin`）。
 */
@Composable
fun PinVerifyDialog() {
    val graph = App.of(LocalContext.current).graph
    val show by graph.pinCoordinator.show.collectAsState()
    if (!show) return

    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var keyboardHeightPx by remember { mutableStateOf(0) }

    // web `show()`：清态 + 300ms 后推上安全键盘
    LaunchedEffect(Unit) {
        pin = ""; errorText = ""; showKeyboard = false
        delay(300); showKeyboard = true
    }

    fun dismiss() { // web `onCancel` → hide() + cancelPinVerify()
        pin = ""; errorText = ""
        graph.pinCoordinator.resolve(null)
    }
    fun fail(msg: String) { // web `setError`：留框、显示文案、清空已输
        errorText = msg; pin = ""
    }
    fun submit() {
        val challenge = graph.pinCoordinator.challenge
        val isRoute = (challenge as? JsonObject)
            ?.get("action_type")?.takeIf { !it.isJsonNull }?.asString == "route_verify"
        scope.launch {
            if (isRoute) {
                when (val r = graph.security.routeVerify(pin, challenge)) {
                    is ApiResult.Ok -> { showKeyboard = false; graph.pinCoordinator.resolve(r.data) }
                    is ApiResult.Fail -> fail(r.message.ifEmpty { "PIN 码错误" })
                    else -> fail("验证失败")   // 8304 由 onSessionBlocked 统一处理
                }
            } else {
                when (val r = graph.security.verifyPin(pin)) {
                    is ApiResult.Ok -> { showKeyboard = false; graph.pinCoordinator.resolve(PinTicket()) }
                    is ApiResult.Fail -> fail(r.message.ifEmpty { "PIN 码错误" })
                    else -> fail(if (r is ApiResult.NeedPin) r.message.ifEmpty { "验证失败" } else "验证失败")
                }
            }
        }
    }
    // web `handleSecurePayload/handleKeyInput`：满 6 位 150ms 后自动提交（弹窗上没有「确认」按钮）
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            delay(150); errorText = ""; submit()
        }
    }

    Dialog(
        onDismissRequest = { /* web onOverlayClick 空实现：点遮罩不关 */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 关掉窗口自带 dim —— web 的暗色由 van-overlay 自己画（rgba(0,0,0,.7)）
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val prevDim = window?.attributes?.dimAmount
            window?.setDimAmount(0f)
            onDispose { prevDim?.let { window?.setDimAmount(it) } }
        }
        Box(Modifier.fillMaxSize().background(Color(0xB3000000))) {
            // ===== 键盘层（先画，保证卡片在其上层）=====
            // .pin-keyboard-overlay：贴底、rgba(0,0,0,.5)、点它（含键缝）只收起键盘。
            // 必须画在卡片之前：否则短屏上键盘会盖住卡片下缘（输入区/取消按钮），
            // 点击被键盘遮罩吞掉 → 又置成 showKeyboard=false，而此前没有任何路径能置回 true。
            if (showKeyboard) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { keyboardHeightPx = it.height }
                        .background(Color(0x80000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showKeyboard = false },
                ) {
                    VanSafeKeyboard(
                        onChar = { d -> if (pin.length < 6 && d.length == 1 && d[0].isDigit()) pin += d },
                        onDelete = { pin = pin.dropLast(1); errorText = "" },
                        onClose = { showKeyboard = false }, // web @confirm="showKeyboard = false"
                    )
                }
            }
            // .pin-verify-card：320×auto / radius 16 / padding 28 24 20 / bg-secondary
            // 键盘弹出时卡片贴到键盘正上方（原 Alignment.Center 悬在屏幕中部，
            // 顶部高出键盘一大截、留大片空白）；键盘收起时保持居中。
            val density = LocalDensity.current
            val keyboardHeightDp = with(density) { keyboardHeightPx.toDp() }
            val cardAboveKeyboard = showKeyboard && keyboardHeightPx > 0
            Box(
                Modifier
                    .align(if (cardAboveKeyboard) Alignment.BottomCenter else Alignment.Center)
                    .let {
                        if (cardAboveKeyboard) it.padding(bottom = keyboardHeightDp + 24.dp) else it
                    }
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.bgCard)
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            ) {
                Column {
                    BasicText(
                        "安全验证", // .pin-title 18/600，margin-bottom 8
                        style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(18f), fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        "请输入 PIN 码以继续操作", // .pin-desc 14 / text-tertiary，margin-bottom 24
                        style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(16f)),
                    )
                    Spacer(Modifier.height(24.dp))
                    // van-password-input：length 6 / gutter 10 / focused 跟键盘开合。
                    // 点击输入区重新唤出键盘：Compose 版没有 Vant 那种「touchstart 自动 focus」兜底，
                    // 之前 showKeyboard 一旦置 false 就再也置不回 true（收起后永久唤不出）。
                    VanPasswordInput(
                        value = pin,
                        length = 6,
                        focused = showKeyboard,
                        gutter = 10.dp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showKeyboard = true },
                    )
                    if (errorText.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        BasicText(
                            errorText, // .pin-error 13 / danger，margin-bottom 16
                            style = TextStyle(color = colors.danger, fontSize = scaledSp(14f)),
                        )
                    } else {
                        Spacer(Modifier.height(12.dp)) // .pin-input-area margin-bottom 12
                    }
                    Spacer(Modifier.height(4.dp))
                    // .pin-actions：居中、gap 16；web 只留「取消」（确认按钮在源码里被注释）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 原来是 Small（minHeight 32dp / 12sp）偏小不好点，改成 Normal + 最小宽度
                        // plain+Default 的字色是 c.border（浅色主题近白），按用户要求改成 textPrimary
                        VanButton(
                            text = "取消",
                            onClick = { dismiss() },
                            type = VanButtonType.Default,
                            size = VanButtonSize.Normal,
                            plain = true,
                            textColor = colors.textPrimary,
                            modifier = Modifier.defaultMinSize(minWidth = 132.dp),
                        )
                    }
                }
            }
        }
    }
}
