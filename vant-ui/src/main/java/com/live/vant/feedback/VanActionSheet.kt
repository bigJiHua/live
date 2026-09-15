package com.live.vant.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

/** 动作面板选项（对应 Vant ActionSheetAction） */
data class VanAction(
    val name: String,
    val subname: String? = null,
    val color: Color? = null,
    val icon: String? = null,
    val disabled: Boolean = false,
    val loading: Boolean = false,
)

/**
 * 复刻 van-action-sheet（web 项目 5 处使用；props：v-model:show / actions / title /
 * cancel-text / description / close-on-click-action；事件 select / cancel / close）。
 */
@Composable
fun VanActionSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<VanAction>,
    onSelect: (VanAction, Int) -> Unit,
    title: String? = null,
    description: String? = null,
    cancelText: String? = null,
    closeOnClickAction: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!show) return
    val c = LocalVantColors.current
    VanPopup(
        show = true,
        onDismissRequest = onDismissRequest,
        position = VanPopupPosition.Bottom,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 标题栏
            if (!title.isNullOrEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        title,
                        style = TextStyle(color = c.textTertiary, fontSize = 14.sp),
                    )
                }
            }
            // 选项列表（max-height 80% 可滚，对应 --van-action-sheet-max-height）
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                actions.forEachIndexed { index, action ->
                    val fg = action.color ?: c.textPrimary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.bgCard)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !action.disabled && !action.loading,
                            ) {
                                onSelect(action, index)
                                if (closeOnClickAction) onDismissRequest()
                            }
                            .padding(vertical = 14.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (action.loading) {
                                com.live.vant.basic.VanLoading(size = 22.sp, color = c.primary)
                                Spacer(Modifier.height(0.dp))
                            } else if (action.icon != null) {
                                VanIcon(name = action.icon, size = 18.sp, color = fg)
                            }
                            androidx.compose.foundation.text.BasicText(
                                action.name,
                                style = TextStyle(
                                    color = if (action.disabled) c.textTertiary else fg,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                ),
                                modifier = Modifier.padding(start = if (action.icon != null || action.loading) 8.dp else 0.dp),
                            )
                        }
                        if (!action.subname.isNullOrEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                action.subname,
                                style = TextStyle(color = c.textTertiary, fontSize = 12.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            )
                        }
                        // 选项间 hairline
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(top = 8.dp)
                                .background(c.border),
                        )
                    }
                }
            }
            // 描述区
            if (!description.isNullOrEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    description,
                    style = TextStyle(color = c.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().background(c.bgCard).padding(16.dp),
                )
            }
            // 取消按钮（白卡 + 顶部 8px 灰缝）
            if (!cancelText.isNullOrEmpty()) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(c.bgPage))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bgCard)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onDismissRequest()
                        }
                        .padding(vertical = 14.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        cancelText,
                        style = TextStyle(color = c.textSecondary, fontSize = 16.sp, lineHeight = 22.sp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp).background(c.bgPage).fillMaxWidth())
        }
    }
}
