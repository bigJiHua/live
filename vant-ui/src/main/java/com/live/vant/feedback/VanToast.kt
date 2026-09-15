package com.live.vant.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import kotlinx.coroutines.delay

enum class VanToastType { Text, Success, Fail, Loading }
enum class VanToastPosition { Middle, Top, Bottom }

class VanToastMessage internal constructor(
    val message: String,
    val type: VanToastType,
    val position: VanToastPosition,
    val duration: Long,
    val forbidClick: Boolean,
    val icon: String?,
)

/** 控制器：对应 Vant 函数式 API（showToast / showSuccessToast / showFailToast / showLoadingToast / closeToast） */
class VanToastController internal constructor() {
    internal val current = mutableStateOf<VanToastMessage?>(null)
    internal var seq by mutableStateOf(0L)
        private set

    /** showToast(message) / showToast({ message, type, duration ... }) */
    fun show(
        message: String,
        type: VanToastType = VanToastType.Text,
        position: VanToastPosition = VanToastPosition.Middle,
        duration: Long = 2000,
        forbidClick: Boolean = false,
        icon: String? = null,
    ) {
        seq++
        current.value = VanToastMessage(message, type, position, duration, forbidClick, icon)
    }

    fun success(message: String) = show(message, VanToastType.Success)
    fun fail(message: String) = show(message, VanToastType.Fail)
    /** showLoadingToast：loading 类型默认不自动消失（duration=0） */
    fun loading(message: String = "", forbidClick: Boolean = true) =
        show(message, VanToastType.Loading, duration = 0, forbidClick = forbidClick)

    /** closeToast */
    fun close() { current.value = null }
}

val LocalVanToastController = staticCompositionLocalOf { VanToastController() }

/**
 * Toast 渲染宿主。用法与 web 端函数式 API 对应：
 * ```
 * val rootToast = rememberVanToastController()
 * VantTheme { CompositionLocalProvider(LocalVanToastController provides rootToast) {
 *     App()
 *     VanToastHost(rootToast)
 * } }
 * // 任意子组件内：
 * val toast = LocalVanToastController.current
 * toast.success("提交成功")   // ← showSuccessToast
 * ```
 */
@Composable
fun rememberVanToastController(): VanToastController =
    remember { VanToastController() }

@Composable
fun VanToastHost(controller: VanToastController = LocalVanToastController.current) {
    val message = controller.current.value
    if (message == null) return

    val c = LocalVantColors.current
    LaunchedEffect(controller.seq) {
        if (message.duration > 0) {
            delay(message.duration)
            if (controller.current.value === message) controller.close()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (message.forbidClick) swallowClicksModifier() else Modifier),
            contentAlignment = when (message.position) {
                VanToastPosition.Top -> Alignment.TopCenter
                VanToastPosition.Bottom -> Alignment.BottomCenter
                else -> Alignment.Center
            },
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = if (message.position != VanToastPosition.Middle) Modifier.padding(vertical = 80.dp) else Modifier,
            ) {
                ToastBubble(message, c)
            }
        }
    }
}

@Composable
private fun ToastBubble(message: VanToastMessage, c: LocalVantColorAlias) {
    val isPlain = message.type == VanToastType.Text && message.icon == null
    if (isPlain) {
        // 纯文字 toast：max-width 70%、padding 8 12、radius 8
        Box(
            contentAlignment = if (message.position == VanToastPosition.Middle) Alignment.Center else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .widthIn(max = 250.dp)
                    .background(c.toastBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                androidx.compose.foundation.text.BasicText(
                    message.message,
                    style = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center),
                )
            }
        }
    } else if (message.type == VanToastType.Loading) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 88.dp)
                .background(c.toastBg, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            VanLoading(size = 30.sp, color = Color.White)
            if (message.message.isNotEmpty()) {
                Text(message.message, c, Modifier.padding(top = 4.dp))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 88.dp)
                .background(c.toastBg, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            val iconName = message.icon ?: when (message.type) {
                VanToastType.Success -> "success"
                VanToastType.Fail -> "fail"
                else -> null
            }
            if (iconName != null) {
                VanIcon(name = iconName, size = 36.sp, color = Color.White)
            }
            if (message.message.isNotEmpty()) {
                Text(message.message, c, Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Text(text: String, c: LocalVantColorAlias, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center),
        modifier = modifier,
    )
}

private typealias LocalVantColorAlias = com.live.vant.theme.VantColors

/** forbid-click：吞掉遮罩区域内的所有点击（对应 Vant .van-toast--forbid-click） */
private fun swallowClicksModifier(): Modifier = Modifier.pointerInput(Unit) {
    detectTapGestures { /* 消费所有点击 */ }
}
