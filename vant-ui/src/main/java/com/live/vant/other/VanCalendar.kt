package com.live.vant.other

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class VanCalendarType { Single, Multiple, Range }

private val TitleFmt = DateTimeFormatter.ofPattern("yyyy 年 M 月")
private val SubFmt = DateTimeFormatter.ofPattern("MM月DD日")

/**
 * 复刻 van-calendar（web 项目 6 处使用；props：v-model:show / type / min-date / max-date /
 * default-date / color / @confirm / title / show-confirm / confirm-text / allow-same-day /
 * week-starts-on）。
 *
 * 底部弹出 80% 高度面板：顶栏（标题/副标题/月份切换箭头 + 星期行）+ 月历滚动 + 圆角确认按钮。
 * 选区视觉：单/多选=实心圆 54px；range=首尾实心圆 + 中间 10% 主色横带，与 Vant 一致。
 */
@Composable
fun VanCalendar(
    show: Boolean,
    onClose: () -> Unit,
    type: VanCalendarType = VanCalendarType.Single,
    minDate: LocalDate = LocalDate.now().minusYears(1),
    maxDate: LocalDate = LocalDate.now().plusYears(1),
    defaultDate: List<LocalDate> = emptyList(),
    color: Color? = null,
    title: String = "选择日期",
    confirmText: String = "确定",
    showConfirm: Boolean = true,
    allowSameDay: Boolean = false,
    weekStartsOn: Int = 0, // 0=周日
    onConfirm: ((List<LocalDate>) -> Unit)? = null,
) {
    if (!show) return
    val c = LocalVantColors.current
    val selectedColor = color ?: c.primary
    var selected by remember { mutableStateOf(defaultDate) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val months = remember(minDate, maxDate) {
        val out = mutableListOf<YearMonth>()
        var ym = YearMonth.from(minDate)
        val end = YearMonth.from(maxDate)
        while (!ym.isAfter(end)) {
            out.add(ym)
            ym = ym.plusMonths(1)
        }
        out
    }

    fun isDisabled(day: LocalDate): Boolean =
        day.isBefore(minDate.withDayOfMonth(1)) || day.isAfter(maxDate)

    fun pickDay(day: LocalDate) {
        if (isDisabled(day)) return
        when (type) {
            VanCalendarType.Single -> selected = listOf(day)
            VanCalendarType.Multiple -> {
                selected = if (selected.contains(day)) selected.filter { it != day } else selected + day
            }
            VanCalendarType.Range -> {
                selected = when {
                    selected.isEmpty() -> listOf(day)
                    selected.size == 1 -> {
                        val start = selected[0]
                        if (day.isBefore(start)) listOf(day)
                        else if (day == start && !allowSameDay) listOf(day)
                        else listOf(start, day)
                    }
                    else -> listOf(day)
                }
            }
        }
    }

    val subtitle = when (type) {
        VanCalendarType.Single -> selected.firstOrNull()?.let { SubFmt.format(it) } ?: "选择日期"
        VanCalendarType.Multiple -> if (selected.isEmpty()) "选择日期" else "已选 ${selected.size} 天"
        VanCalendarType.Range -> when (selected.size) {
            0 -> "选择日期"
            1 -> "${SubFmt.format(selected[0])} - ?"
            else -> "${SubFmt.format(selected[0])} - ${SubFmt.format(selected[1])}"
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            com.live.vant.basic.VanOverlay(show = true, onClick = onClose)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .background(c.bgCard, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                // 顶栏（标题区 + 月份切换 + 星期行）
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            title,
                            style = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                    ) {
                        // 上一月/下一月
                        val canPrev = remember { mutableStateOf(true) }
                        val canNext = remember { mutableStateOf(true) }
                        LaunchedEffect(listState.firstVisibleItemIndex) {
                            val idx = listState.firstVisibleItemIndex
                            canPrev.value = idx > 0
                            canNext.value = idx < months.size - 1
                        }
                        MonthArrow(
                            icon = "arrow-left",
                            enabled = canPrev.value,
                            onClick = {
                                val idx = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                scope.launch { listState.animateScrollToItem(idx) }
                            },
                            color = c,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.text.BasicText(
                                subtitle,
                                style = TextStyle(color = c.textSecondary, fontSize = 14.sp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        MonthArrow(
                            icon = "arrow",
                            enabled = canNext.value,
                            onClick = {
                                val idx = (listState.firstVisibleItemIndex + 1).coerceAtMost(months.size - 1)
                                scope.launch { listState.animateScrollToItem(idx) }
                            },
                            color = c,
                        )
                    }
                    // 星期行（周日开始）
                    Row(Modifier.fillMaxWidth().height(30.dp)) {
                        val names = remember(weekStartsOn) {
                            (0..6).map { i ->
                                when ((i + weekStartsOn) % 7) {
                                    0 -> "日"; 1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; else -> "六"
                                }
                            }
                        }
                        names.forEach { n ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.text.BasicText(
                                    n, style = TextStyle(color = c.textSecondary, fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }

                // 月份体
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    months.forEachIndexed { mIdx, ym ->
                        item(key = "head-$mIdx") {
                            // 月份小标题
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    TitleFmt.format(ym.atDay(1)),
                                    style = TextStyle(color = c.textPrimary, fontSize = 14.sp),
                                )
                            }
                        }
                        item(key = "month-$mIdx") {
                            val first = ym.atDay(1)
                            val daysInMonth = ym.lengthOfMonth()
                            val startDow = (first.dayOfWeek.value % 7 - weekStartsOn + 7) % 7
                            val rows = ((startDow + daysInMonth + 6) / 7)
                            val selSet = selected.toSet()
                            val rangeStart = if (type == VanCalendarType.Range) selected.firstOrNull() else null
                            val rangeEnd = if (type == VanCalendarType.Range && selected.size >= 2) selected[1] else null

                            Column(Modifier.fillMaxWidth()) {
                                // 大号月份水印（--van-calendar-month-mark 近似）
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        ym.monthValue.toString(),
                                        style = TextStyle(
                                            color = c.textTertiary.copy(alpha = 0.12f),
                                            fontSize = 100.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                                repeat(rows) { row ->
                                    Row(Modifier.fillMaxWidth()) {
                                        repeat(7) { col ->
                                            val dayNum = row * 7 + col - startDow + 1
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(64.dp),
                                            ) {
                                                if (dayNum in 1..daysInMonth) {
                                                    val day = ym.atDay(dayNum)
                                                    val disabled = isDisabled(day)
                                                    val inRange = rangeStart != null && rangeEnd != null &&
                                                        !day.isBefore(rangeStart) && !day.isAfter(rangeEnd)
                                                    val edge = day == rangeStart || day == rangeEnd ||
                                                        (type == VanCalendarType.Range && selected.size == 1 && day == rangeStart)
                                                    val isSelected = selSet.contains(day) || edge
                                                    // range 中段底色
                                                    if (inRange && !edge) {
                                                        Box(
                                                            Modifier
                                                                .fillMaxWidth(0.92f)
                                                                .height(54.dp)
                                                                .align(Alignment.Center)
                                                                .background(selectedColor.copy(alpha = 0.1f)),
                                                        )
                                                    }
                                                    val textColor = when {
                                                        disabled -> c.textTertiary
                                                        (isSelected && (edge || type != VanCalendarType.Range)) -> Color.White
                                                        inRange -> c.textPrimary
                                                        else -> c.textPrimary
                                                    }
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(if (isSelected && !disabled) 54.dp else 64.dp)
                                                            .background(
                                                                if (isSelected && !disabled) selectedColor else Color.Transparent,
                                                                CircleShape,
                                                            )
                                                            .clickable(enabled = !disabled) { pickDay(day) },
                                                    ) {
                                                        androidx.compose.foundation.text.BasicText(
                                                            dayNum.toString(),
                                                            style = TextStyle(
                                                                color = textColor,
                                                                fontSize = 16.sp,
                                                                fontWeight = if (isSelected && !disabled) FontWeight.Bold else FontWeight.Normal,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // 确认按钮（36 高圆角，7px 上下边距）
                if (showConfirm) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                            .height(36.dp)
                            .background(selectedColor, RoundedCornerShape(18.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (selected.isNotEmpty()) onConfirm?.invoke(selected)
                            },
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            confirmText,
                            style = TextStyle(color = Color.White, fontSize = 14.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: String, enabled: Boolean, onClick: () -> Unit, color: LocalVantColorAlias) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = enabled) { onClick() },
    ) {
        VanIcon(name = icon, size = 16.sp, color = if (enabled) color.textPrimary else color.textTertiary)
    }
}

private typealias LocalVantColorAlias = com.live.vant.theme.VantColors
