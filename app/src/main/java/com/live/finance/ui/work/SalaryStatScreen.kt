package com.live.finance.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.SalaryMonth
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 月度薪酬统计（web Work/SalaryStat.vue，三级页）。 */
@Composable
fun SalaryStatScreen(nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val work = App.of(context).graph.work
    val colors = LocalAppColors.current
    val incomeGreen = Color(0xFF07C160)
    val today = LocalDate.now()

    val argsYear = nav.currentBackStackEntry?.arguments?.getString("year")?.toIntOrNull()
    val argsMonth = nav.currentBackStackEntry?.arguments?.getString("month")?.toIntOrNull()
    var year by remember { mutableStateOf(argsYear ?: today.year) }
    var month by remember { mutableStateOf(argsMonth ?: today.monthValue) }
    var monthData by remember { mutableStateOf<SalaryMonth?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            val res = work.salaryMonth(year, month)
            if (res is ApiResult.Ok) monthData = res.data
            loading = false
        }
    }

    fun prevMonth() {
        if (month == 1) { month = 12; year -= 1 } else month -= 1
        load()
    }

    fun nextMonth() {
        if (month == 12) { month = 1; year += 1 } else month += 1
        load()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    ScreenScaffold { inner ->
        Column(modifier = inner.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 头部：上月 / 年月选择 / 下月
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clickable { prevMonth() }.padding(6.dp)) { VanIcon("arrow-left", size = 20.sp, color = colors.textSecondary) }
                Row(
                    modifier = Modifier.weight(1f).clickable { showMonthPicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FText("${year}年${month}月", 17f, FontWeight.Bold, colors.textPrimary)
                    VanIcon("arrow-down", size = 14.sp, color = colors.textTertiary)
                }
                Box(Modifier.clickable { nextMonth() }.padding(6.dp)) { VanIcon("arrow", size = 20.sp, color = colors.textSecondary) }
            }

            if (loading && monthData == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { FText("加载中…", 14f, color = colors.textTertiary) }
            } else {
                val data = monthData ?: SalaryMonth()
                // 汇总卡
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp)).background(colors.bgCard).padding(16.dp),
                ) {
                    FText("月度总收入", 13f, color = colors.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    FText("+¥${Money.format(data.totalIncome.toDoubleOrNull() ?: 0.0)}", 28f, FontWeight.Bold, incomeGreen)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatLine("正式工资", "+¥${Money.format(data.formalTotal.toDoubleOrNull() ?: 0.0)}", incomeGreen, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(32.dp).background(colors.border))
                        StatLine("兼职收入", "+¥${Money.format(data.parttimeTotal.toDoubleOrNull() ?: 0.0)}", incomeGreen, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 每日明细
                if (data.dailyList.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp).padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        VanEmpty(description = "本月暂无薪酬记录")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(10.dp)).background(colors.bgCard),
                    ) {
                        data.dailyList.forEach { d ->
                            val total = d.formalIncome + d.parttimeTotal
                            val isWorking = total > 0
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = isWorking) {
                                    if (isWorking) nav.navigate("${Routes.WORK_SALARY_DAY}?date=" + d.date)
                                }.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    FText(d.date.substring(5), 15f, FontWeight.Medium, colors.textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    FText(weekdayOf(d.date), 12f, color = colors.textTertiary)
                                }
                                FText(if (isWorking) "+¥${Money.format(total)}" else "休息", 15f,
                                    color = if (isWorking) incomeGreen else colors.textTertiary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    VanPopup(show = showMonthPicker, onDismissRequest = { showMonthPicker = false }) {
        val years = (today.year - 5)..(today.year + 1)
        val months = 1..12
        VanPicker(
            columns = vanPickerColumnsOf(years.map { "${it}年" }, months.map { "${it}月" }),
            value = listOf(year - (today.year - 5), month - 1),
            onConfirm = { sel, _ ->
                year = today.year - 5 + sel[0]
                month = sel[1] + 1
                showMonthPicker = false
                load()
            },
            onCancel = { showMonthPicker = false },
            title = "选择年月",
        )
    }
}

@Composable
private fun StatLine(label: String, value: String, color: Color, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FText(label, 12f, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FText(value, 16f, FontWeight.Bold, color)
    }
}

private val WEEK = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
private fun weekdayOf(dateStr: String): String {
    val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return ""
    return WEEK[d.dayOfWeek.value % 7]
}
