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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.vantDimens
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
    /** 容器底色；null = Vant 默认（bgCard）。页面里 web 用 `:deep(.van-collapse){background:transparent}`
     *  覆写时传 `Color.Transparent` —— 底座默认值不动，避免影响其它调用点。 */
    containerColor: Color? = null,
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
                .background(containerColor ?: LocalVantColors.current.bgCard),
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
    /** 条目底色覆写；null = Vant 默认（bgCard）。对应 web 页面 `:deep(.van-collapse-item){background:transparent}`。 */
    itemColor: Color? = null,
    /** 内容区底色覆写；null = bgCard。对应 `:deep(.van-collapse-item__content){background:transparent}`。 */
    contentColor: Color? = null,
    /** 内容区内边距；null = Vant 默认（水平 16 / 垂直 12）。web 该页覆写成 `padding:0`。 */
    contentPadding: PaddingValues? = null,
    /** 内容区与下分隔线之间的留白（web `.van-collapse-item{padding-bottom:4px}`）。 */
    contentBottomGap: Dp = 0.dp,
    /** 下分隔线之后的外边距（web `.van-collapse-item{margin-bottom:20px}`，`:last-child` 为 0）。 */
    bottomSpacing: Dp = 0.dp,
    /** 标题行内边距；null = Vant 默认 16/12。Vant 真实值是 cell 的 `10px 16px`（`--van-cell-vertical-padding`），
     *  页面按 Vant 原样复刻时传 `PaddingValues(horizontal = 16.dp, vertical = 10.dp)`。 */
    titlePadding: PaddingValues? = null,
    /** 展开时标题**下方**再加一条 hairline（Vant `__title:after` 只在 `--expanded` 时 display:block，
     *  且页面把它从 `right:16px` 覆写成 `right:0` ⇒ 左缩进 16、右到边）。 */
    titleDivider: Boolean = false,
    /** 条目**顶部**的 hairline（Vant `.van-collapse-item--border:after{top:0;left:16px;right:16px}`，
     *  只在「非首个条目」且 `border` 为真时出现）。由调用点按序号传。 */
    topDivider: Boolean = false,
    titleSlot: @Composable (() -> Unit)? = null,
    iconSlot: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalVantColors.current
    val state = LocalCollapseState.current
    val expanded = state?.activeNames?.contains(name) == true
    val showBorder = border && (state?.border != false)

    Column(modifier.fillMaxWidth().background(itemColor ?: c.bgCard)) {
        // Vant `.van-collapse-item--border:after{top:0;left:16px;right:16px}` —— 非首条目的顶部线
        if (topDivider) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(c.border))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !disabled && state != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state?.toggle?.invoke(name) }
                .padding(titlePadding ?: PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
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
                        fontSize = vantDimens.fontSizeMd,
                        lineHeight = vantDimens.cellLineHeight,
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
        // Vant `__title:after` 只在展开时显示（`__title--expanded:after{display:block}`）；
        //   页面覆写 `right:0` ⇒ 左缩进 16dp、右到边。
        if (titleDivider && expanded) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(c.border))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(contentColor ?: c.bgCard)
                    .padding(contentPadding ?: PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            ) {
                content()
            }
        }
        // web `.van-collapse-item{padding-bottom:4px}` —— 分隔线**之前**的留白
        if (contentBottomGap > 0.dp) Spacer(Modifier.height(contentBottomGap))
        if (showBorder) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }
        // web `.van-collapse-item{margin-bottom:20px}` —— 分隔线**之后**的外边距
        if (bottomSpacing > 0.dp) Spacer(Modifier.height(bottomSpacing))
    }
}
