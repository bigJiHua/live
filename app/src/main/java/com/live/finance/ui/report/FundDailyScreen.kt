package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Fund
import com.live.finance.data.model.FundHistory
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.form.VanSwitch
import com.live.vant.nav.VanNavBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FundDailyScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val toast = rememberVanToastController()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }
    var currentFundId by remember { mutableStateOf("") }
    var calendarMonth by remember { mutableStateOf(LocalDate.now()) }
    var monthInit by remember { mutableStateOf(false) }

    var monthRecords by remember { mutableStateOf<List<FundHistory>>(emptyList()) }
    var monthLoading by remember { mutableStateOf(false) }
    var showDayPopup by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<FundCalDay?>(null) }

    val currentFund = fundList.firstOrNull { it.id == currentFundId }
    val earnProfit = monthRecords.sumOf { it.netValue }
    val earnCapital = monthRecords.sumOf { it.marketVal }

    fun loadMonthly() {
        val fid = currentFundId
        if (fid.isBlank()) return
        scope.launch {
            monthLoading = true
            when (val res = graph.fund.historyMonthly(fid, calendarMonth.year, calendarMonth.monthValue)) {
                is ApiResult.Ok -> {
                    val d = res.data
                    monthRecords = d?.records ?: emptyList()
                    if (!monthInit && d != null && d.latestRecordDate.isNotBlank()) {
                        runCatching { LocalDate.parse(d.latestRecordDate, fmt) }.getOrNull()?.let {
                            calendarMonth = it.withDayOfMonth(1)
                        }
                        monthInit = true
                    }
                }
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("加载失败")
            }
            monthLoading = false
        }
    }

    fun reload() {
        scope.launch {
            loading = true
            when (val res = graph.fund.list()) {
                is ApiResult.Ok -> {
                    val d = res.data
                    fundList = d ?: emptyList()
                    if (currentFundId.isBlank() && d != null && d.isNotEmpty()) currentFundId = d.first().id
                    loadMonthly()
                }
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(currentFundId, calendarMonth) { if (currentFundId.isNotBlank()) loadMonthly() }

    val calDays = if (currentFund != null) buildFundCalDays(monthRecords, currentFund.buyDate, calendarMonth, true) else emptyList()

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            VanNavBar(title = "每日净值登记", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanLoading() }
            } else if (fundList.isEmpty()) {
                com.live.vant.basic.VanEmpty(description = "暂无持仓基金，请先登记")
            } else {
                currentFund?.let { f ->
                    // 概览卡
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FundCalHeader("${calendarMonth.year}年${calendarMonth.monthValue}月", { calendarMonth = calendarMonth.minusMonths(1) }, { calendarMonth = calendarMonth.plusMonths(1) })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FundOverviewItem("总持仓市值", fundAmount(f.marketVal), null, Modifier.weight(1f))
                            FundOverviewItem("总收益", fundAmount(f.netValue), if (f.isProfit) colors.danger else colors.success, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FundOverviewItem("本月收益", fundAmount(earnProfit), if (earnProfit >= 0) colors.danger else colors.success, Modifier.weight(1f))
                            FundOverviewItem("本月增持本金", fundAmount(earnCapital), null, Modifier.weight(1f))
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgThird).clickable {
                            if (f.buyDate.isNotBlank()) runCatching { LocalDate.parse(f.buyDate, fmt) }.getOrNull()?.let { calendarMonth = it.withDayOfMonth(1); monthInit = true }
                        }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            FText("🚩 跳到买入月", 13f, FontWeight.Medium, colors.primary)
                        }
                    }

                    // 基金切换
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fundList.forEach { item ->
                            val selected = item.id == currentFundId
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) colors.primary else colors.bgThird)
                                    .clickable {
                                        currentFundId = item.id
                                        monthRecords = emptyList()
                                        monthInit = false
                                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                FText(item.fundName.ifEmpty { "未命名" }, 13f, FontWeight.Medium, if (selected) colors.buttonPrimaryText else colors.textPrimary)
                            }
                        }
                    }

                    // 日历
                    FundCalendarLegend()
                    if (monthLoading) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { VanLoading() }
                    } else {
                        FundCalendar(days = calDays, clickable = { it.inMonth }, onClick = {
                            selectedDay = it
                            showDayPopup = true
                        })
                    }
                }
            }
        }
        }
    }

    // 当日登记弹窗
    if (showDayPopup && selectedDay != null) {
        val day = selectedDay!!
        var isCapital by remember { mutableStateOf(false) }
        var netValueStr by remember { mutableStateOf("") }
        var marketValStr by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        val existing = monthRecords.firstOrNull { it.recordDate == day.date }

        LaunchedEffect(day.date) {
            isCapital = existing != null && existing.marketVal > 0.005
            netValueStr = if (existing != null && existing.marketVal <= 0.005) String.format("%.2f", existing.netValue) else ""
            marketValStr = if (existing != null && existing.marketVal > 0.005) String.format("%.2f", existing.marketVal) else ""
        }

        fun saveDay() {
            val body = JsonObject().apply {
                addProperty("recordDate", day.date)
                addProperty("netValue", if (isCapital) "0" else (netValueStr.toDoubleOrNull() ?: 0.0).toString())
                addProperty("marketVal", if (isCapital) (marketValStr.toDoubleOrNull() ?: 0.0).toString() else "0")
            }
            scope.launch {
                saving = true
                val res = if (existing != null) graph.fund.updateHistory(existing.id, body) else graph.fund.addHistory(currentFundId, body)
                when (res) {
                    is ApiResult.Ok -> { toast.show("已保存"); showDayPopup = false; loadMonthly() }
                    is ApiResult.Fail -> toast.show(res.message)
                    else -> toast.show("操作失败")
                }
                saving = false
            }
        }

        fun deleteDay() {
            if (existing == null) return
            scope.launch {
                when (val res = graph.fund.deleteHistory(existing.id)) {
                    is ApiResult.Ok -> { toast.show("已删除"); showDayPopup = false; loadMonthly() }
                    is ApiResult.Fail -> toast.show(res.message)
                    else -> toast.show("操作失败")
                }
            }
        }

        VanPopup(show = true, onDismissRequest = { showDayPopup = false }) {
            Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FText(day.date, 16f, FontWeight.SemiBold, colors.textPrimary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FText(if (isCapital) "追加本金" else "当日收益", 14f, FontWeight.Medium, colors.textPrimary)
                    VanSwitch(checked = isCapital, onCheckedChange = { isCapital = it })
                }
                if (isCapital) {
                    AppField(marketValStr, { marketValStr = it }, label = "追加本金", placeholder = "0.00", keyboardType = KeyboardType.Number)
                } else {
                    AppField(netValueStr, { netValueStr = it }, label = "当日收益", placeholder = "0.00（正收益填正数，亏损填负数）", keyboardType = KeyboardType.Number)
                }
                AppButton("保存", onClick = { saveDay() }, size = AppButtonSize.Large, type = AppButtonType.Primary, block = true)
                if (existing != null) {
                    AppButton("删除当日记录", onClick = { deleteDay() }, size = AppButtonSize.Large, type = AppButtonType.Danger, block = true)
                }
            }
        }
    }
}
