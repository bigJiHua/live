package com.live.vant.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

/** 折叠面板内部状态（v-model / accordion） */
class VanCollapseInternalState(
    val activeNames: List<String>,
    val accordion: Boolean,
    val toggle: (String) -> Unit,
    val border: Boolean = true,
)

internal val LocalCollapseState = compositionLocalOf<VanCollapseInternalState?> { null }

/**
 * 复刻 van-collapse + van-collapse-item（web 项目 8 处使用；props：v-model / accordion / border；
 * item：name / title / icon / disabled）。
 * 项目 CSS 覆盖标题居中（.van-collapse-item__title justify-content:center），此处 1:1。
 */
@Composable
fun VanCollapse(
    /** 当前展开的 name 列表（对应 v-model：数组/字符串） */
    activeNames: List<String>,
    onActiveNamesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    accordion: Boolean = false,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val toggle: (String) -> Unit = { name ->
        if (accordion) {
            onActiveNamesChange(if (activeNames.contains(name)) emptyList() else listOf(name))
        } else {
            onActiveNamesChange(
                if (activeNames.contains(name)) activeNames.filter { it != name }
                else activeNames + name,
            )
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCollapseState provides VanCollapseInternalState(activeNames, accordion, toggle, border),
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .background(LocalVantColors.current.bgCard),
            content = content,
        )
    }
}

/** 折叠面板项（放在 VanCollapse 内自动接管展开状态） */
@Composable
fun VanCollapseItem(
    name: String,
    title: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    disabled: Boolean = false,
    border: Boolean = true,
    titleSlot: @Composable (() -> Unit)? = null,
    iconSlot: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalVantColors.current
    val state = LocalCollapseState.current
    val expanded = state?.activeNames?.contains(name) == true
    val showBorder = border && (state?.border != false)

    Column(modifier.fillMaxWidth().background(c.bgCard)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !disabled && state != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state?.toggle?.invoke(name) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (iconSlot != null) {
                iconSlot()
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                VanIcon(name = icon, size = 16.sp, color = if (disabled) c.textTertiary else c.textPrimary)
                Spacer(Modifier.width(8.dp))
            }
            when {
                titleSlot != null -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { titleSlot() }
                else -> androidx.compose.foundation.text.BasicText(
                    title,
                    style = TextStyle(
                        color = if (disabled) c.textTertiary else c.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            VanIcon(
                name = if (expanded) "arrow-up" else "arrow-down",
                size = 16.sp,
                color = c.textTertiary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(c.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                content()
            }
        }
        if (showBorder) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }
    }
}
