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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.vant.basic.VanOverlay
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

enum class VanKeyboardTheme { Default, Custom }

/**
 * 复刻 van-number-keyboard（web 项目 9 处使用；props：show / v-model / theme / title /
 * close-button-text / extra-key / maxlength / close-on-click-outside；事件 input/delete/close/blur）。
 *
 * 以 Dialog 形式从屏幕底部弹出，自带遮罩（对应 Vant popup-wrapper 行为）。
 */
@Composable
fun VanNumberKeyboard(
    show: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    theme: VanKeyboardTheme = VanKeyboardTheme.Default,
    title: String? = null,
    /** custom 主题右侧蓝色大按钮文字（如 "完成"）；传 null 显示默认主题 */
    closeButtonText: String? = null,
    /** 额外按键：["."] 或 ["0","."]（对应 Vant :extra-key） */
    extraKey: List<String> = emptyList(),
    maxLength: Int? = null,
    showDeleteKey: Boolean = true,
    closeOnClickOutside: Boolean = true,
    onInput: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
) {
    if (!show) return
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current

    fun append(k: String) {
        if (maxLength != null && text.length >= maxLength) return
        onTextChange(text + k)
        onInput?.invoke(k)
    }

    Dialog(
        onDismissRequest = {
            if (closeOnClickOutside) { onClose?.invoke(); onBlur?.invoke() }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val keyBg = if (theme == VanKeyboardTheme.Custom) c.bgPage else c.bgCard
        androidx.compose.runtime.CompositionLocalProvider(LocalKeyboardKeyBg provides keyBg) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
            VanOverlay(show = true, onClick = { if (closeOnClickOutside) { onClose?.invoke(); onBlur?.invoke() } })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (theme == VanKeyboardTheme.Custom) c.bgCard else c.bgThird,
                    ),
            ) {
                // 标题栏（title 存在时显示；custom 主题右侧文字按钮）
                if (!title.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 16.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                title,
                                style = TextStyle(color = c.textTertiary, fontSize = 16.sp),
                            )
                        }
                        androidx.compose.foundation.text.BasicText(
                            "完成",
                            style = TextStyle(color = c.primary, fontSize = 14.sp),
                            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onClose?.invoke(); onBlur?.invoke()
                            },
                        )
                    }
                }
                // 按键区
                Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    KeyboardRow { key("1", ::append); key("2", ::append); key("3", ::append) }
                    KeyboardRow { key("4", ::append); key("5", ::append); key("6", ::append) }
                    if (theme == VanKeyboardTheme.Custom && closeButtonText != null) {
                        // custom 主题：左 2 列按键 + 右侧跨两行的「完成」大按钮
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(2f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    key("7", ::append); key("8", ::append)
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (ek in extraKey) key(ek, ::append)
                                    key("0", ::append)
                                    if (showDeleteKey && extraKey.isEmpty()) DeleteKey {
                                        if (text.isNotEmpty()) { onTextChange(text.dropLast(1)); onDelete?.invoke() }
                                    }
                                }
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(104.dp) // 2×48 + 8 行距
                                    .background(c.primary, RoundedCornerShape(5.dp))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        onClose?.invoke(); onBlur?.invoke()
                                    },
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    closeButtonText,
                                    style = TextStyle(color = Color.White, fontSize = 16.sp),
                                )
                            }
                        }
                    } else {
                        KeyboardRow { key("7", ::append); key("8", ::append); key("9", ::append) }
                        KeyboardRow {
                            for (ek in extraKey) key(ek, ::append)
                            if (extraKey.isEmpty()) WideKey("0", ::append) else key("0", ::append)
                            if (showDeleteKey) DeleteKey {
                                if (text.isNotEmpty()) {
                                    onTextChange(text.dropLast(1))
                                    onDelete?.invoke()
                                }
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        }
    }
}

/** 当前主题下按键底色（Default 白色 / Custom 浅灰） */
internal val LocalKeyboardKeyBg = androidx.compose.runtime.staticCompositionLocalOf { Color.White }

@Composable
private fun KeyboardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.key(k: String, onClick: (String) -> Unit) {
    val c = LocalVantColors.current
    val keyBg = LocalKeyboardKeyBg.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .background(keyBg, RoundedCornerShape(5.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick(k) },
    ) {
        androidx.compose.foundation.text.BasicText(
            k,
            style = TextStyle(color = c.textPrimary, fontSize = 28.sp),
        )
    }
}

@Composable
private fun RowScope.WideKey(k: String, onClick: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(2f)
            .height(48.dp)
            .background(LocalKeyboardKeyBg.current, RoundedCornerShape(5.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick(k) },
    ) {
        androidx.compose.foundation.text.BasicText(
            k,
            style = TextStyle(color = LocalVantColors.current.textPrimary, fontSize = 28.sp),
        )
    }
}

@Composable
private fun RowScope.DeleteKey(onDelete: () -> Unit) {
    val c = LocalVantColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .background(c.bgThird, RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onDelete() },
            ),
    ) {
        // Vant 删除键 ⌫（delete-back svg 路径，viewBox 0 0 32 22）
        androidx.compose.foundation.Canvas(Modifier.size(32.dp, 22.dp)) {
            val path = com.live.vant.svgPath(
                "M28 0a4 4 0 0 1 4 4v14a4 4 0 0 1-4 4H10.4a2 2 0 0 1-1.4-.6L1 13.1c-.6-.5-.9-1.3-.9-2 0-1 .3-1.7.9-2.2L9 .6A2 2 0 0 1 10.4 0ZM28 2H10.4l-8.2 8.3a1 1 0 0 0-.3.7c0 .3.1.5.3.7l8.2 8.4H28a2 2 0 0 0 2-2V4c0-1.1-.9-2-2-2ZM23 6a1 1 0 0 1 .7.3 1 1 0 0 1 0 1.4L20.4 11l3.3 3.3c.2.2.3.5.3.7 0 .3-.1.5-.3.7a1 1 0 0 1-.7.3 1 1 0 0 1-.7-.3L19 12.4l-3.4 3.3a1 1 0 0 1-.6.3 1 1 0 0 1-.7-.3 1 1 0 0 1-.3-.7c0-.2.1-.5.3-.7l3.3-3.3-3.3-3.3A1 1 0 0 1 14 7c0-.3.1-.5.3-.7A1 1 0 0 1 15 6a1 1 0 0 1 .6.3L19 9.6l3.3-3.3A1 1 0 0 1 23 6Z",
            )
            val sx = size.width / 33f
            val sy = size.height / 24f
            path.transform(androidx.compose.ui.graphics.Matrix().apply { scale(sx, sy) })
            drawPath(path, androidx.compose.ui.graphics.Color(0xFF646566))
        }
    }
}
