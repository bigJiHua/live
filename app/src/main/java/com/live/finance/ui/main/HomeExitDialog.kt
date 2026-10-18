package com.live.finance.ui.main

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastPosition
import com.live.vant.feedback.VanToastType
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/**
 * 首页按系统返回的「离开确认」弹窗（第三十六轮，**原生特有** —— web 是网页、没有「离开 App」，
 * 浏览器返回也没有可拦截的退出语义，故无 web 对照）。
 *
 * 行为口径（负责人原话）：
 *  - 「确认」→ 直接离开（`finishAffinity` 结束任务）；
 *  - 「上锁」→ 锁定后离开：`auth.lockSystem()`（= 顶栏 lock 图标同一接口，Ok/NeedPin 都算锁定成功，
 *    见 [com.live.finance.ui.main.GlobalNavBar] 注释）成功才离开；失败 toast、留在 App；
 *  - 右上角 **×**（及点遮罩 / 系统返回）= 取消：只关弹窗，什么都不做、也不退出；
 *  - 文案第二行报 PIN 状态：已设置 → 「当前PIN码未锁定」，未设置 → 「未设置PIN码」
 *    （未设置时不给「上锁后离开」—— 没有 PIN 就没有可解锁的锁）。
 *
 * 样式与 van-dialog 同套（白卡圆角 16、宽 320、居中标题、底栏 48 高 + 1px 中缝分隔）；
 * `VanDialog` 没有右上角 × 的口子，故自绘。
 */
@Composable
fun HomeExitDialog(show: Boolean, onClose: () -> Unit) {
    if (!show) return
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()
    // LocalContext 在 Dialog 内容里会变成弹窗自己的 ContextWrapper，finishAffinity 必须
    // 拿宿主 Activity —— 在 Dialog **外**取好。
    val activity = LocalContext.current as? Activity

    var hasPinSet by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) hasPinSet = graph.security.hasPinSet()
    }

    fun leave() {
        activity?.finishAffinity()
    }

    fun lockAndLeave() {
        scope.launch {
            val r = graph.auth.lockSystem()
            when (r) {
                is ApiResult.Ok, is ApiResult.NeedPin -> leave()
                is ApiResult.Fail -> {
                    toast.show(r.message.ifEmpty { "锁定失败" }, VanToastType.Fail, position = VanToastPosition.Top)
                    onClose()
                }
                else -> {
                    toast.show("锁定失败", VanToastType.Fail, position = VanToastPosition.Top)
                    onClose()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x80000000)) // 与 AppDialogShell 同款遮罩 rgba(0,0,0,.5)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.bgCard)
                    // 吞掉卡片内点击，防止穿透到遮罩误关
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
            ) {
                // 右上角 × = 取消（负责人口径：点击后关闭弹窗，什么都不做、也不退出）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(32.dp)
                            .height(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onClose() },
                    ) {
                        VanIcon(name = "cross", size = 16.sp, color = colors.textTertiary)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FText("你确定要离开吗？", 16f, FontWeight.Medium, colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    FText(
                        if (hasPinSet) "当前PIN码未锁定" else "未设置PIN码",
                        14f,
                        FontWeight.Normal,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    Row(Modifier.fillMaxWidth().height(48.dp)) {
                        if (hasPinSet) {
                            ExitButton("上锁后离开", colors.textSecondary, Modifier.weight(1f)) { lockAndLeave() }
                            Box(Modifier.fillMaxHeight().width(1.dp).background(colors.border))
                        }
                        ExitButton("确认离开", colors.primary, Modifier.weight(1f)) { leave() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        FText(text, 16f, FontWeight.Medium, color)
    }
}
