package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.PinCells
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanNoticeBar
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSafeKeyboard
import kotlinx.coroutines.delay

// web `PinSetup.vue` 里写死的色（var 无定义时的 fallback）
private val PIN_STEP_GREEN = Color(0xFF07C160)   // var(--van-green, #07c160)
private val PIN_STEP_GLOW = Color(0x4D07C160)    // 0 4px 12px rgba(7,193,96,.3)

/**
 * PIN 码设置 / 修改页 —— 1:1 复刻 web `src/views/User/security/PinSetup.vue`。
 *
 * 页面固定 **60vh**（`.page-pin-setup{ height:60vh }`），内容在其中垂直居中；底部贴数字安全键盘
 * （`.keyboard-overlay`，遮罩 `rgba(0,0,0,.5)`）。
 *
 * @param mode `new` = 设置（2 步）／`modify` = 修改（3 步：验证旧 PIN → 输入新 PIN → 确认新 PIN）
 */
@Composable
fun PinSetupScreen(nav: NavHostController, mode: String = "new") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val sec = App.of(ctx).graph.security

    val isModify = mode == "modify"
    var currentStep by remember { mutableIntStateOf(1) }
    var oldPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // onMounted：状态前置校验 + 300ms 后弹键盘
    LaunchedEffect(Unit) {
        val hasPin = sec.hasPinSet()
        if (!isModify && hasPin) {
            toast.show("您已设置过 PIN 码，请选择修改")
            delay(1500)
            nav.popBackStack()
            return@LaunchedEffect
        }
        if (isModify && !hasPin) {
            toast.show("您还未设置 PIN 码，正在跳转到设置页面...")
            delay(1500)
            // web 是 router.replace 到 mode=new；原生用「回退到管理页再进 new」等价实现
            nav.navigate(Routes.pinSetup("new")) {
                popUpTo(Routes.PIN_MANAGE) { inclusive = false }
            }
            return@LaunchedEffect
        }
        delay(300)
        showKeyboard = true
    }

    /** 校验/接口失败回到指定步骤（对应 web 各分支的 currentStep 回退 + 清空）。 */
    fun failReset(message: String, toStep: Int) {
        isSubmitting = false
        errorMessage = message
        currentStep = toStep
        firstPin = ""
        currentPin = ""
    }

    /** 旧 PIN 校验失败：留在第 1 步，只清当前输入。 */
    fun failVerify(message: String) {
        isSubmitting = false
        errorMessage = message
        currentPin = ""
    }

    /**
     * 满 6 位后延迟 100ms 校验（web `handleKeyInput` 的 setTimeout(100)；
     * key = currentPin 使上一轮校验随输入变化自动取消，等价 web 的 clearTimeout）。
     */
    LaunchedEffect(currentPin) {
        if (currentPin.length != 6) return@LaunchedEffect
        delay(100)

        // 强校验：必须 6 位纯数字、且不能 6 位全同
        if (!Regex("^\\d{6}$").matches(currentPin)) {
            errorMessage = "请输入完整的 6 位数字 PIN 码"
            currentPin = ""
            return@LaunchedEffect
        }
        if (Regex("^(\\d)\\1{5}$").matches(currentPin)) {
            errorMessage = "PIN 码不能为6位相同的数字"
            currentPin = ""
            return@LaunchedEffect
        }
        if (isSubmitting) return@LaunchedEffect
        val pin = currentPin

        if (!isModify) {
            when (currentStep) {
                1 -> { // 第一次输入新 PIN
                    firstPin = pin
                    currentStep = 2
                    currentPin = ""
                }
                else -> { // 确认 PIN
                    if (pin != firstPin) {
                        errorMessage = "两次输入的 PIN 码不一致，请重新设置"
                        currentStep = 1
                        firstPin = ""
                        currentPin = ""
                        return@LaunchedEffect
                    }
                    isSubmitting = true
                    when (val r = sec.setPin(firstPin)) {
                        is ApiResult.Ok -> {
                            isSubmitting = false
                            toast.show("PIN 码设置成功")
                            delay(1500)
                            nav.popBackStack()
                        }
                        is ApiResult.Fail -> failReset(r.message.ifBlank { "设置失败，请重试" }, toStep = 1)
                        else -> failReset("设置失败，请重试", toStep = 1)
                    }
                }
            }
        } else {
            when (currentStep) {
                1 -> { // 验证旧 PIN
                    isSubmitting = true
                    when (val r = sec.verifyPin(pin)) {
                        is ApiResult.Ok -> {
                            isSubmitting = false
                            oldPin = pin
                            currentStep = 2
                            currentPin = ""
                        }
                        is ApiResult.Fail -> failVerify(r.message.ifBlank { "PIN 码错误，请重新输入" })
                        is ApiResult.Locked -> failVerify("PIN 码已锁定，请稍后再试")
                        else -> failVerify("PIN 码错误，请重新输入")
                    }
                }
                2 -> { // 输入新 PIN
                    firstPin = pin
                    currentStep = 3
                    currentPin = ""
                }
                else -> { // 确认新 PIN
                    if (pin != firstPin) {
                        errorMessage = "两次输入的 PIN 码不一致，请重新设置"
                        currentStep = 2
                        firstPin = ""
                        currentPin = ""
                        return@LaunchedEffect
                    }
                    if (pin == oldPin && pin != "000000") {
                        errorMessage = "新 PIN 码不能与旧 PIN 码相同"
                        currentStep = 2
                        firstPin = ""
                        currentPin = ""
                        return@LaunchedEffect
                    }
                    isSubmitting = true
                    when (val r = sec.changePin(oldPin, pin)) {
                        is ApiResult.Ok -> {
                            isSubmitting = false
                            toast.show("PIN 码修改成功")
                            delay(1500)
                            nav.popBackStack()
                        }
                        is ApiResult.Fail -> failReset(r.message.ifBlank { "修改失败，请重试" }, toStep = 2)
                        else -> failReset("修改失败，请重试", toStep = 2)
                    }
                }
            }
        }
    }

    ScreenScaffold { inner ->
        Box(inner) {
            // .page-pin-setup{ height:60vh }
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.6f)) {
                // .setup-content{ flex:1; column; center; justify-center; padding:0 20px }
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                ) {
                    PinStepIndicator(currentStep = currentStep, isModify = isModify)

                    if (isModify && currentStep == 1) {
                        FText("验证身份", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 12.dp))
                        FText(
                            "请输入当前的 PIN 码以确认身份", 14f, FontWeight.Normal, colors.textPrimary,
                            Modifier.padding(vertical = 20.dp),
                        )
                    }
                    // .pin-display{ margin: 40px 0 }；:gutter="15"
                    PinCells(
                        value = currentPin,
                        gutter = 15.dp,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )

                    // .tip-text{ width:100%; margin:20px 0 }：有错走 warning 通知条，否则走普通提示
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                        if (errorMessage.isNotEmpty()) {
                            VanNoticeBar(text = errorMessage, leftIcon = "warning-o")
                        } else {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FText(
                                    tipText(currentStep, isModify), 14f, FontWeight.Normal, colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }

            // ===== 底部安全键盘（复刻 web PinSetup.vue 的 .keyboard-overlay）=====
            // 全屏透明遮罩：点键盘外任意区域 → 收起键盘（对应 web
            // .keyboard-overlay @click="showKeyboard=false"）。web 原版遮罩只盖键盘高度、
            // 键盘 @click.stop 导致点外不收起；这里做成全屏透明遮罩，上方有可点区域，满足
            // 「点外自主收起」诉求。键盘面板自身吞掉点击（对应 web @click.stop），避免点键误关。
            if (showKeyboard) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { showKeyboard = false },
                ) {
                    // 键盘面板：贴底，吞掉自身点击（@click.stop）
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clickable { },
                    ) {
                        VanSafeKeyboard(
                            onChar = { d ->
                                errorMessage = ""
                                if (currentPin.length < 6) currentPin += d
                            },
                            onDelete = {
                                errorMessage = ""
                                currentPin = currentPin.dropLast(1)
                            },
                            onClose = { showKeyboard = false },
                        )
                    }
                }
            }
        }
    }
}

/** `.step-indicator`：40 圆（默认 bg-tertiary / 当前主色 / 完成绿）+ 12px 文案 + 40×2 连接线，间距 12，下边距 60。 */
@Composable
private fun PinStepIndicator(currentStep: Int, isModify: Boolean) {
    val colors = LocalAppColors.current
    val total = if (isModify) 3 else 2
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 60.dp),
    ) {
        for (s in 1..total) {
            if (s > 1) {
                // 连接线：左侧步骤已完成 → 主色
                Box(
                    Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .background(if (currentStep > s - 1) colors.primary else colors.border),
                )
            }
            val active = currentStep == s
            val completed = currentStep > s
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .let {
                            if (active) {
                                it.shadow(6.dp, CircleShape, clip = false, ambientColor = PIN_STEP_GLOW, spotColor = PIN_STEP_GLOW)
                            } else it
                        }
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> colors.primary
                                completed -> PIN_STEP_GREEN
                                else -> colors.bgThird
                            },
                        ),
                ) {
                    FText(
                        "$s", 16f, FontWeight.Bold,
                        if (active || completed) Color.White else colors.textTertiary,
                    )
                }
                FText(
                    stepText(s, isModify),
                    12f,
                    if (active) FontWeight.Medium else FontWeight.Normal,
                    if (active) colors.primary else colors.textTertiary,
                )
            }
        }
    }
}

private fun stepText(step: Int, isModify: Boolean): String =
    if (!isModify) {
        if (step == 1) "输入新 PIN 码" else "确认 PIN 码"
    } else when (step) {
        1 -> "验证旧 PIN"
        2 -> "输入新 PIN"
        else -> "确认新 PIN"
    }

private fun tipText(step: Int, isModify: Boolean): String =
    if (!isModify) {
        if (step == 1) "请设置 6 位数字 PIN 码" else "请再次输入以确认"
    } else when (step) {
        1 -> "请输入当前的 PIN 码"
        2 -> "请设置新的 6 位数字 PIN 码"
        else -> "请再次输入新 PIN 码以确认"
    }
