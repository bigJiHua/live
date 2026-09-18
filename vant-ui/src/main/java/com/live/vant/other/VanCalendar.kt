package com.live.vant.other

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicText
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.VantColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class VanCalendarType { Single, Multiple, Range }

/** web / Vant：`formatMonthTitle` = `YYYY年M月` */
private val MonthTitleFmt = DateTimeFormatter.ofPattern("yyyy年M月")

/**
 * 复刻 van-calendar（web 项目 6 处使用）。
 *
 * 与真实 Vant 4 逐项对齐的点（依据 `vant/lib/calendar/index.css` + `es/calendar/Calendar.mjs`）：
 *  - 面板 80% 高、顶部圆角 16（`--van-calendar-popup-height` / `--van-popup-round-radius`）；
 *  - 标题 44 高 / 16px / 600，默认文案 **「日期选择」**；右上角 cross 关闭图标（top 11 / right 16 / 22px / 灰 3）；
 *  - 副标题行 44 高 / 14px / 600，内容 = **当前可见月份标题**（`YYYY年M月`，随滚动更新）；
 *  - 星期行 30 高 / 12px；月份标题行 44 高 / 14px / 600 / 居中，且**首月不渲染**（有副标题时）；
 *  - 日格 1/7 宽 × 64 高 + 行间 4px；选中 = **54×54 圆角 4 的方块**（不是圆）、文字不加粗；
 *    range 端点为**整格填色**（外缘 4px 圆角），range 中段整格 10% 主色 + **主色文字**；
 *  - 月份水印 160px、浅色 `rgba(242,243,245,.8)`／深色 `rgba(100,101,102,.2)`、**绝对定位叠加**（不进文档流）；
 *  - 确认按钮 36 高 / 胶囊 / 16px，**未选满时禁用**（range 需两端、multiple 需非空）+ 禁用态 opacity .5，底部避让安全区。
 *
 * 本项目 `switchMode` 固定为 Vant 默认的 `none`：因此**不显示月份切换箭头**，月份靠上下滚动切换（与 web 一致）。
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
    title: String = "日期选择",
    confirmText: String = "确认",
    showConfirm: Boolean = true,
    allowSameDay: Boolean = false,
    weekStartsOn: Int = 0, // 0=周日（= Vant firstDayOfWeek）
    onConfirm: ((List<LocalDate>) -> Unit)? = null,
) {
    if (!show) return
    val c = LocalVantColors.current
    val selectedColor = color ?: c.primary
    val dark = c.bgCard.luminance() < 0.5f
    var selected by remember { mutableStateOf(defaultDate) }
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

    // 打开时滚动到 defaultDate（clamp 到 [minDate,maxDate]）/ 今天所在月 —— Vant `scrollToCurrentDate`
    LaunchedEffect(show) {
        val target = (defaultDate.firstOrNull() ?: LocalDate.now())
            .let { if (it.isBefore(minDate)) minDate else if (it.isAfter(maxDate)) maxDate else it }
        val idx = months.indexOf(YearMonth.from(target)).coerceAtLeast(0)
        listState.scrollToItem(idx * 2)
    }

    fun isDisabled(day: LocalDate): Boolean = day.isBefore(minDate) || day.isAfter(maxDate)

    fun pickDay(day: LocalDate) {
        if (isDisabled(day)) return
        when (type) {
            VanCalendarType.Single -> selected = listOf(day)
            VanCalendarType.Multiple -> {
                selected = if (selected.contains(day)) selected.filter { it != day } else selected + day
            }
            VanCalendarType.Range -> {
                selected = when {
                    // 已选满两端 → 重新开一段（Vant：`select([date])`）
                    selected.size >= 2 -> listOf(day)
                    selected.isEmpty() -> listOf(day)
                    else -> {
                        val start = selected[0]
                        when {
                            day.isBefore(start) -> listOf(day)                       // 早于起点 → 重开
                            day == start -> if (allowSameDay) listOf(start, start) else listOf(day)
                            else -> listOf(start, day)
                        }
                    }
                }
            }
        }
    }

    // 副标题 = 当前可见月（每个自然月占 2 个 item：标题行 + 月体）
    val visibleMonth by remember {
        derivedStateOf {
            val idx = (listState.firstVisibleItemIndex / 2).coerceIn(0, (months.size - 1).coerceAtLeast(0))
            months.getOrNull(idx) ?: YearMonth.now()
        }
    }

    /** Vant：range 必须两端齐全、multiple 非空才可确认；single 恒可。 */
    val canConfirm = when (type) {
        VanCalendarType.Single -> selected.isNotEmpty()
        VanCalendarType.Multiple -> selected.isNotEmpty()
        VanCalendarType.Range -> selected.size >= 2
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
                    .fillMaxHeight(0.8f)   // --van-calendar-popup-height: 80%
                    .background(c.bgCard, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .navigationBarsPadding(),   // safeAreaInsetBottom
            ) {
                // ── 顶栏（标题 + 副标题 + 关闭） ──
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        // 标题行：44 高 / 16px / 600
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            BasicText(
                                title,
                                style = TextStyle(color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        // 副标题行：44 高 / 14px / 600，内容 = 当前可见月
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            BasicText(
                                MonthTitleFmt.format(visibleMonth.atDay(1)),
                                style = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    // 右上角关闭（Vant closeable：cross 图标 top 11 / right 16 / 22px / --van-text-color-3）
                    VanIcon(
                        name = "cross",
                        size = 22.sp,
                        // web 覆盖 `--van-calendar-header-action-color: var(--theme-text-primary)`（style.css:119），
                        // 不是 Vant 默认的 `--van-text-color-3`
                        color = c.textPrimary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 16.dp),
                        onClick = onClose,
                    )
                }

                // ── 星期行（30 高 / 12px，色继承正文色） ──
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
                            // web 覆盖 `--van-calendar-weekdays-text-color: var(--theme-text-secondary)`（style.css:120）
                            BasicText(n, style = TextStyle(color = c.textSecondary, fontSize = 12.sp))
                        }
                    }
                }

                // ── 月份列表 ──
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    months.forEachIndexed { mIdx, ym ->
                        item(key = "head-$mIdx") {
                            // 月份标题行：44 高 / 14px / 600 / 居中；Vant 在显示副标题时**首月不渲染**
                            if (mIdx == 0) {
                                Spacer(Modifier.height(0.dp))
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                ) {
                                    BasicText(
                                        MonthTitleFmt.format(ym.atDay(1)),
                                        style = TextStyle(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                    )
                                }
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
                            // 月份水印：web 覆盖为 `--van-calendar-month-mark-color: var(--theme-text-tertiary)`
                            // （style.css:129）——Vant 本身**没有透明度**规则，故这里是**实色**三级色
                            val markColor = c.textTertiary

                            Box(Modifier.fillMaxWidth()) {
                                // 月份水印：160px、绝对定位居中叠加（不进文档流）
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.matchParentSize(),
                                ) {
                                    BasicText(
                                        ym.monthValue.toString(),
                                        style = TextStyle(color = markColor, fontSize = 160.sp, fontWeight = FontWeight.Bold),
                                    )
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    repeat(rows) { row ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)   // --van-calendar-day-height
                                                .padding(bottom = if (row == rows - 1) 0.dp else 4.dp),
                                        ) {
                                            repeat(7) { col ->
                                                val dayNum = row * 7 + col - startDow + 1
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                ) {
                                                    if (dayNum in 1..daysInMonth) {
                                                        val day = ym.atDay(dayNum)
                                                        val disabled = isDisabled(day)
                                                        val isEdge = rangeStart != null && (day == rangeStart || day == rangeEnd)
                                                        val inRange = rangeStart != null && rangeEnd != null &&
                                                            !day.isBefore(rangeStart) && !day.isAfter(rangeEnd)
                                                        val isSelected = selSet.contains(day)

                                                        when {
                                                            // range 端点：整格填色 + 外缘 4px 圆角
                                                            isEdge -> {
                                                                val shape = when {
                                                                    day == rangeStart && day == rangeEnd -> RoundedCornerShape(4.dp)
                                                                    day == rangeStart -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                                                                    else -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                                                }
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier
                                                                        .matchParentSize()
                                                                        .background(selectedColor, shape)
                                                                        .clickable(enabled = !disabled) { pickDay(day) },
                                                                ) {
                                                                    BasicText(
                                                                        dayNum.toString(),
                                                                        // web 覆盖 `--van-calendar-selected-day-text-color: var(--theme-button-primary-text)`（style.css:124）
                                                                        style = TextStyle(color = c.buttonPrimaryText, fontSize = 16.sp),
                                                                    )
                                                                }
                                                            }
                                                            // range 中段：整格 10% 主色 + 主色文字
                                                            inRange -> {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier
                                                                        .matchParentSize()
                                                                        // web 覆盖 `--van-calendar-range-middle-background: var(--theme-bg-tertiary)`（style.css:127）
                                                                        .background(c.bgThird)
                                                                        .clickable(enabled = !disabled) { pickDay(day) },
                                                                ) {
                                                                    BasicText(
                                                                        dayNum.toString(),
                                                                        // 区间中段文字 = `--van-calendar-range-middle-color: var(--theme-text-primary)`（style.css:128）
                                                                        style = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                                                                    )
                                                                }
                                                            }
                                                            // 单选/多选选中：54×54 圆角 4 的方块（Vant 不是圆）
                                                            isSelected && !disabled -> {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier
                                                                        .size(54.dp)
                                                                        .background(selectedColor, RoundedCornerShape(4.dp))
                                                                        .clickable(enabled = true) { pickDay(day) },
                                                                ) {
                                                                    BasicText(
                                                                        dayNum.toString(),
                                                                        // 同上：选中文字 = `--van-calendar-selected-day-text-color: var(--theme-button-primary-text)`
                                                                        style = TextStyle(color = c.buttonPrimaryText, fontSize = 16.sp),
                                                                    )
                                                                }
                                                            }
                                                            else -> {
                                                                BasicText(
                                                                    dayNum.toString(),
                                                                    style = TextStyle(
                                                                        color = if (disabled) c.textTertiary else c.textPrimary,
                                                                        fontSize = 16.sp,
                                                                    ),
                                                                    modifier = Modifier.clickable(enabled = !disabled) { pickDay(day) },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 确认按钮（36 高 / 胶囊 / 16px；未选满禁用 opacity .5） ──
                if (showConfirm) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp)   // footer padding 0 16 + button margin 7 0
                            .height(36.dp)
                            .background(
                                if (canConfirm) selectedColor else selectedColor.copy(alpha = 0.5f),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable(
                                enabled = canConfirm,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onConfirm?.invoke(selected) },
                    ) {
                        BasicText(
                            confirmText,
                            // web 覆盖 `--van-calendar-confirm-button-color: var(--theme-button-primary-text)`（style.css:132）
                            style = TextStyle(color = c.buttonPrimaryText, fontSize = 16.sp),
                        )
                    }
                }
            }
        }
    }
}
