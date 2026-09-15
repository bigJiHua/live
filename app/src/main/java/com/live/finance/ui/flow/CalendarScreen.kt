package com.live.finance.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import java.util.Calendar

@Composable
fun CalendarScreen(nav: NavHostController, initYear: Int = 0, initMonth: Int = 0) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.flow
    val cal0 = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(if (initYear > 0) initYear else cal0.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(if (initMonth > 0) initMonth else cal0.get(Calendar.MONTH) + 1) }
    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(year, month) {
        selected = null
        rows = when (val r = repo.list(year, month)) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> emptyList()
        }
    }

    val byDay: Map<String, List<FlowRow>> = remember(rows) { rows.groupBy { it.day } }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item { MonthHeader(year, month, onPrev = { shiftMonth(cal0, year, month, -1).let { year = it.first; month = it.second } }, onNext = { shiftMonth(cal0, year, month, 1).let { year = it.first; month = it.second } }) }
            item { WeekGrid(year, month, byDay, selected) { d -> selected = if (selected == d) null else d } }
            item { SelectedDaySection(selected, byDay, colors, nav) }
        }
    }
}

private fun shiftMonth(@Suppress("UNUSED_PARAMETER") base: Calendar, y: Int, m: Int, delta: Int): Pair<Int, Int> {
    var year = y; var month = m + delta
    while (month > 12) { month -= 12; year++ }
    while (month < 1) { month += 12; year-- }
    return year to month
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText("‹", Modifier.clickable(onClick = onPrev).padding(horizontal = 16.dp), TextStyle(color = colors.primary, fontSize = 22.sp))
        androidx.compose.foundation.text.BasicText("${year}年${month}月", Modifier, TextStyle(color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        androidx.compose.foundation.text.BasicText("›", Modifier.clickable(onClick = onNext).padding(horizontal = 16.dp), TextStyle(color = colors.primary, fontSize = 22.sp))
    }
}

@Composable
private fun WeekGrid(year: Int, month: Int, byDay: Map<String, List<FlowRow>>, selected: String?, onSelect: (String) -> Unit) {
    val colors = LocalAppColors.current
    val labels = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(Modifier.fillMaxWidth()) {
        labels.forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.text.BasicText(it, style = TextStyle(color = colors.textTertiary, fontSize = 12.sp))
            }
        }
    }
    val firstDow = remember(year, month) {
        Calendar.getInstance().apply { set(year, month - 1, 1) }.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    }
    val lastDay = remember(year, month) {
        Calendar.getInstance().apply { set(year, month - 1, 1); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_MONTH, -1); add(Calendar.MONTH, 1) }.get(Calendar.DAY_OF_MONTH)
    }
    val cells = firstDow + lastDay
    val totalCells = (cells + 6) / 7 * 7
    Column(Modifier.fillMaxWidth()) {
        for (idx in 0 until totalCells) {
            if (idx % 7 == 0) {
                Row(Modifier.fillMaxWidth().height(56.dp)) {
                    for (col in 0 until 7) {
                        val cellIdx = idx + col
                        val dayNum = cellIdx - firstDow + 1
                        Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.TopCenter) {
                            if (dayNum in 1..lastDay) {
                                val date = String.format("%d-%02d-%02d", year, month, dayNum)
                                val dayRows = byDay[date]
                                val isSel = selected == date
                                Column(
                                    Modifier.fillMaxWidth().height(56.dp)
                                        .background(if (isSel) colors.primaryLight else colors.bgCard)
                                        .clickable { onSelect(date) }
                                        .padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    androidx.compose.foundation.text.BasicText("$dayNum", style = TextStyle(color = if (isSel) colors.primary else colors.textPrimary, fontSize = 14.sp))
                                    if (!dayRows.isNullOrEmpty()) {
                                        val expense = dayRows.filter { !it.isIncome && it.transferGroupId.isEmpty() }.sumOf { it.amount }
                                        val income = dayRows.filter { it.isIncome && it.transferGroupId.isEmpty() }.sumOf { it.amount }
                                        val shown = if (expense > 0) "-" + Money.format(expense) else "+" + Money.format(income)
                                        androidx.compose.foundation.text.BasicText(
                                            shown,
                                            Modifier.padding(top = 2.dp),
                                            style = TextStyle(color = if (expense > 0) com.live.finance.ui.common.MoneyColor.expense else com.live.finance.ui.common.MoneyColor.income, fontSize = 9.sp),
                                            maxLines = 1,
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

@Composable
private fun SelectedDaySection(selected: String?, byDay: Map<String, List<FlowRow>>, colors: com.live.vant.theme.VantColors, nav: NavHostController) {
    val dayRows = selected?.let { byDay[it] } ?: emptyList()
    Spacer(Modifier.height(8.dp))
    if (selected == null) {
        androidx.compose.foundation.text.BasicText("点击日期查看当日流水", Modifier.padding(16.dp), TextStyle(color = colors.textTertiary, fontSize = 13.sp))
    } else {
        androidx.compose.foundation.text.BasicText("$selected · ${dayRows.size} 笔", Modifier.padding(16.dp), TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        dayRows.forEach { row ->
            FlowItemRow(row) {
                if (row.transferGroupId.isEmpty()) nav.navigate(Routes.flowDetail(row.id))
            }
        }
    }
}
