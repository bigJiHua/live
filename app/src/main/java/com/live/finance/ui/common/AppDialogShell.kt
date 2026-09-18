package com.live.finance.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText

/** `AppPopup(position=center, round)` 的圆角：center 是 12px（bottom/top 才是 16）。 */
private val DIALOG_SHAPE = RoundedCornerShape(12.dp)

/**
 * 居中弹窗外壳 —— 复刻 web `components/base/AppPopup.vue`(position=center, round) + `AppDialog.vue`：
 * 遮罩 `rgba(0,0,0,.5)`、卡片圆角 12、`padding 20px 16px 0`、标题 16/600 **左对齐**、
 * 内容 14、底栏 48 高两键（取消 `text-secondary` + 中缝 1px 分隔线、确认 `primary` 500）。
 *
 * 命名说明：web 组件名是 `AppDialog`；本项目有并行会话在写登录/首页，为避免与将来可能出现的
 * 同名共享件冲突（同名同包直接编译失败），此处以 `Shell` 后缀注册，落地后可合并。
 *
 * 与 web 的对应关系：
 *  - `show` / `onOverlay` / `onCancel` / `onConfirm` = `v-model:show` / `@click-overlay` / `@cancel` / `@confirm`；
 *  - **本组件不自动关窗**：开关由调用方控制（web `before-close` 语义由调用方决定是否置 `show=false`）；
 *  - [lift] 对应键盘弹出时整卡上移；[bottomOverlay] 用于同层放置安全键盘。
 */
@Composable
fun AppDialogShell(
    show: Boolean,
    title: String,
    onOverlay: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确认",
    cancelText: String = "取消",
    /** 键盘弹出时整卡上移量（0 = 不上移）。 */
    lift: Dp = 0.dp,
    /** 内容区最大高度（null = 不限制；超出可滚动）。 */
    maxContentHeight: Dp? = null,
    /** 与卡片同层的贴底浮层（安全键盘等）。 */
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    val colors = LocalAppColors.current

    Dialog(
        onDismissRequest = onOverlay,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x80000000)), // .app-popup__overlay{ background: rgba(0,0,0,.5) }
        ) {
            // 遮罩层（点卡片外区域 → onOverlay）
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOverlay() },
            )

            // ===== 卡片 =====
            Column(
                modifier = modifier
                    .align(Alignment.Center)
                    .offset(y = if (lift > 0.dp) -lift else 0.dp)
                    .fillMaxWidth(0.85f) // app-dialog{ max-width: 85vw }
                    .widthIn(min = 280.dp) // app-popup--center{ min-width: 280px }
                    .clip(DIALOG_SHAPE)
                    .background(colors.bgCard)
                    // 吞掉卡片内点击：Compose 命中测试会继续向下找可响应节点，不吃掉会穿透到遮罩
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
            ) {
                FText(
                    title, 16f, FontWeight.SemiBold, colors.textPrimary,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (maxContentHeight != null) it.heightIn(max = maxContentHeight) else it }
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
                    content = content,
                )
                // 底栏：通栏分隔线 + 两枚 48 高按钮
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    ShellButton(cancelText, colors.textSecondary, FontWeight.Normal, Modifier.weight(1f), onCancel)
                    Box(Modifier.fillMaxHeight().width(1.dp).background(colors.border))
                    ShellButton(confirmText, colors.primary, FontWeight.Medium, Modifier.weight(1f), onConfirm)
                }
            }

            // ===== 同层贴底浮层（安全键盘等）=====
            bottomOverlay?.invoke(this)
        }
    }
}

@Composable
private fun ShellButton(
    text: String,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
        FText(text, 15f, weight, color)
    }
}

/** web `--theme-shadow-color` 未定义 → 用其 fallback `rgba(7,193,96,.2)`。 */
private val PIN_FOCUS_GLOW = Color(0x3307C160)

/**
 * PIN 码 6 格输入 —— 复刻两个 PIN 页面对 `van-password-input` 的同一套覆写：
 * `.van-password-input__item{ 1px border; bg-secondary; radius 6; w35 h50; flex:1 }`、
 * `--focus{ border-color: primary; box-shadow: 0 0 8px rgba(7,193,96,.2) }`、`__cursor{ background: primary }`。
 *
 * 注：两个页面都**没传** `mask`（Vant 默认 false）→ 数字明文显示（与 web 一致）。
 */
@Composable
fun PinCells(
    value: String,
    modifier: Modifier = Modifier,
    length: Int = 6,
    focused: Boolean = true,
    gutter: Dp = 15.dp,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gutter),
    ) {
        repeat(length) { i ->
            val isFocus = focused && i == value.length
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .let {
                        if (isFocus) {
                            it.shadow(4.dp, shape, clip = false, ambientColor = PIN_FOCUS_GLOW, spotColor = PIN_FOCUS_GLOW)
                        } else it
                    }
                    .clip(shape)
                    .background(colors.bgCard)
                    .border(1.dp, if (isFocus) colors.primary else colors.border, shape),
            ) {
                when {
                    i < value.length -> FText(value[i].toString(), 20f, FontWeight.Normal, colors.textPrimary)
                    isFocus -> PinCursor(colors.primary)
                }
            }
        }
    }
}

/** `.van-password-input__cursor`：主色竖条闪烁（1s，与 Vant 动画一致）。 */
@Composable
private fun PinCursor(color: Color) {
    val transition = rememberInfiniteTransition("pin-cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    )
    Box(
        Modifier
            .width(2.dp)
            .height(20.dp)
            .alpha(a)
            .background(color),
    )
}
