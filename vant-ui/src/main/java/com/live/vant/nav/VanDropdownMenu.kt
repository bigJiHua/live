package com.live.vant.nav

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors

data class VanDropdownOption(
    val value: Any,
    val text: String,
    val disabled: Boolean = false,
    val icon: String? = null,
)

data class VanDropdownMenuItemData(
    /** 菜单标题（对应 van-dropdown-item :title，Vant 无 title 时显示选中项文字） */
    val title: String,
    val value: Any,
    val options: List<VanDropdownOption>,
    val disabled: Boolean = false,
)

/**
 * 复刻 van-dropdown-menu + van-dropdown-item（web 项目 2 处使用；props：v-model / options）。
 * 菜单高 48、标题 15px、下拉面板选项 48px 行高、选中主题色 + success 勾。
 * 面板内联展开（点击选项或再点标题收起）。
 */
@Composable
fun VanDropdownMenu(
    items: List<VanDropdownMenuItemData>,
    /** 点击选项后回写（菜单 index, 选中值） */
    onChange: (Int, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalVantColors.current
    var openIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth().background(c.bgCard)) {
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            items.forEachIndexed { index, item ->
                val opened = openIndex == index
                val titleColor = when {
                    item.disabled -> c.textTertiary
                    opened -> c.primary
                    else -> c.textPrimary
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = !item.disabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { openIndex = if (opened) null else index },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicText(
                            item.title,
                            style = TextStyle(color = titleColor, fontSize = 15.sp, lineHeight = 22.sp),
                        )
                        Spacer(Modifier.width(4.dp))
                        VanIcon(
                            name = "arrow-down",
                            size = 12.sp,
                            color = titleColor.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
        if (openIndex != null) {
            val open = openIndex ?: 0
            val item = items.getOrNull(open)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.border),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .background(c.bgCard)
                    .padding(bottom = 16.dp),
            ) {
                item?.options?.forEach { option ->
                    val selected = option.value == item.value
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(
                                enabled = !option.disabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!option.disabled) {
                                    onChange(open, option.value)
                                    openIndex = null
                                }
                            }
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (option.icon != null) {
                                VanIcon(name = option.icon, size = 20.sp, color = if (selected) c.primary else c.textTertiary)
                                Spacer(Modifier.width(8.dp))
                            }
                            androidx.compose.foundation.text.BasicText(
                                option.text,
                                style = TextStyle(
                                    color = if (option.disabled) c.textTertiary else if (selected) c.primary else c.textPrimary,
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                        if (selected) {
                            VanIcon(name = "success", size = 16.sp, color = c.primary, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }
    }
}
