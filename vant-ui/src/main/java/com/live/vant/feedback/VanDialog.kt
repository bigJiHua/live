package com.live.vant.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.vant.theme.LocalVantColors

/**
 * 复刻 van-dialog（web 项目 showDialog(2) + showConfirmDialog(28) 的声明式形态）。
 *
 * show=false 时不渲染；按钮布局遵循 Vant：双按钮=左取消右确认 + 中缝分隔线，
 * 单按钮=通栏确认；圆角 16、宽 320、文字 14/16。
 */
@Composable
fun VanDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    confirmButtonText: String = "确认",
    cancelButtonText: String = "取消",
    showConfirmButton: Boolean = true,
    showCancelButton: Boolean = false,
    confirmButtonColor: Color? = null,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    width: androidx.compose.ui.unit.Dp = 320.dp,
    content: (@Composable () -> Unit)? = null, // 自定义内容区（对应 Vant #default 插槽，替代 message）
) {
    if (!show) return
    val c = LocalVantColors.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgCard),
        ) {
            if (!title.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, bottom = if (message == null && content == null) 26.dp else 8.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        title,
                        style = TextStyle(color = c.textPrimary, fontSize = 16.sp, lineHeight = 24.sp),
                    )
                }
            }
            if (content != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = 20.dp)) { content() }
            } else if (!message.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp, end = 24.dp,
                            bottom = if (showConfirmButton || showCancelButton) 26.dp else 26.dp,
                            top = if (title.isNullOrEmpty()) 24.dp else 8.dp,
                        ),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        message,
                        style = TextStyle(
                            color = if (title.isNullOrEmpty()) c.textPrimary else c.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // 按钮区
            if (showConfirmButton || showCancelButton) {
                Spacer(Modifier.height(0.dp))
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    if (showCancelButton) {
                        DialogButton(
                            text = cancelButtonText,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { onCancel(); onDismissRequest() },
                        )
                        Box(Modifier.width(1.dp).fillMaxWidth().background(c.border))
                    }
                    if (showConfirmButton) {
                        DialogButton(
                            text = confirmButtonText,
                            color = confirmButtonColor ?: c.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { onConfirm(); onDismissRequest() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        androidx.compose.foundation.text.BasicText(
            text,
            style = TextStyle(color = color, fontSize = 16.sp),
        )
    }
}

/**
 * 对应 web 端 `showConfirmDialog({title, message, confirmButtonText, cancelButtonText})`。
 * 用法：把状态提上来 —— var open by remember { mutableStateOf(false) }。
 */
@Composable
fun VanConfirmDialog(
    show: Boolean,
    title: String = "",
    message: String = "",
    confirmButtonText: String = "确认",
    cancelButtonText: String = "取消",
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    VanDialog(
        show = show,
        onDismissRequest = onClose,
        title = title.ifEmpty { null },
        message = message.ifEmpty { null },
        confirmButtonText = confirmButtonText,
        cancelButtonText = cancelButtonText,
        showConfirmButton = true,
        showCancelButton = true,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

/** 对应 web 端 `showDialog({...})` 单按钮提示框 */
@Composable
fun VanAlertDialog(
    show: Boolean,
    title: String = "",
    message: String = "",
    confirmButtonText: String = "确认",
    onConfirm: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    VanDialog(
        show = show,
        onDismissRequest = onClose,
        title = title.ifEmpty { null },
        message = message.ifEmpty { null },
        confirmButtonText = confirmButtonText,
        showConfirmButton = true,
        showCancelButton = false,
        onConfirm = onConfirm,
    )
}
