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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.nav.VanNavBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FundEarningsScreen(nav: androidx.navigation.NavHostController) {
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

    val currentFund = fundList.firstOrNull { it.id == currentFundId }
    val totalInvested = fundList.sumOf { it.invest }
    val totalMarket = fundList.sumOf { it.marketVal }
    val totalEarnings = fundList.sumOf { it.netValue }
    val totalRate = if (totalInvested > 0) totalEarnings / totalInvested * 100 else 0.0
    val allProfit = totalEarnings >= 0

    fun fundRateValue(f: Fund): Double = if (f.invest > 0) f.netValue / f.invest * 100 else 0.0

    val calDays = if (currentFund != null) buildFundCalDays(monthRecords, currentFund.buyDate, calendarMonth, false) else emptyList()

    var showDayPopup by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<FundHistory?>(null) }

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            VanNavBar(title = "收益明细", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanLoading() }
            } else if (fundList.isEmpty()) {
                com.live.vant.basic.VanEmpty(description = "暂无持仓基金，请先登记")
            } else {
                // 全局汇总
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FundOverviewItem("当前本金", fundAmount(totalInvested), null, Modifier.weight(1f))
                        FundOverviewItem("当前市值", fundAmount(totalMarket), colors.primary, Modifier.weight(1f))
                        FundOverviewItem("累计收益", (if (allProfit) "+" else "") + fundAmount(totalEarnings), if (allProfit) colors.danger else colors.success, Modifier.weight(1f))
                        FundOverviewItem("总回报率", (if (totalRate >= 0) "+" else "") + fundRate(totalRate), if (allProfit) colors.danger else colors.success, Modifier.weight(1f))
                    }
                }

                // 基金选择
                FText("选择基金", 14f, FontWeight.SemiBold, colors.textPrimary)
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

                currentFund?.let { f ->
                    val rate = fundRateValue(f)
                    val profitColor = if (f.isProfit) colors.danger else colors.success
                    // 收益卡
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                FText(f.fundName.ifEmpty { "未命名" }, 15f, FontWeight.SemiBold, colors.textPrimary)
                                FText(f.fundCompany.ifEmpty { "基金公司未填" }, 12f, FontWeight.Normal, colors.textSecondary)
                            }
                            FText(fundRate(rate), 16f, FontWeight.Bold, profitColor)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                FText("累计收益", 12f, FontWeight.Normal, colors.textSecondary)
                                FText((if (f.isProfit) "+" else "") + fundAmount(f.netValue), 18f, FontWeight.Bold, profitColor)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FundOverviewItem("当前市值", fundAmount(f.marketVal), null, Modifier.weight(1f))
                            FundOverviewItem("当前本金", fundAmount(f.invest), null, Modifier.weight(1f))
                            FundOverviewItem("初始本金", fundAmount(if (f.baseInvest > 0) f.baseInvest else f.invest), null, Modifier.weight(1f))
                            FundOverviewItem("持有份额", String.format("%.2f", f.share), null, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val meta = mutableListOf<String>().apply {
                                add("购入 " + (parseFundDate(f.buyDate).ifBlank { "-" }))
                                if (f.sellOrg.isNotBlank()) add(f.sellOrg)
                                if (f.fundAccount.isNotBlank()) add("账户 ${f.fundAccount}")
                            }
                            meta.forEach { FText(it, 11f, FontWeight.Normal, colors.textTertiary) }
                        }
                        // 收益率进度条
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.bgThird)) {
                            val frac = (kotlin.math.min(kotlin.math.abs(rate), 30.0) / 30.0).toFloat()
                            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (rate >= 0) colors.danger else colors.success))
                        }
                    }

                    // 每日收益日历
                    FText("每日收益日历", 14f, FontWeight.SemiBold, colors.textPrimary)
                    FundCalHeader("${calendarMonth.year}年${calendarMonth.monthValue}月", { calendarMonth = calendarMonth.minusMonths(1) }, { calendarMonth = calendarMonth.plusMonths(1) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.bgThird).clickable { if (monthInit) { monthInit = false; loadMonthly() } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            FText("收益月", 12f, FontWeight.Medium, colors.primary)
                        }
                        if (f.buyDate.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.bgThird).clickable {
                            runCatching { LocalDate.parse(f.buyDate, fmt) }.getOrNull()?.let { calendarMonth = it.withDayOfMonth(1); monthInit = true }
                        }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            FText("买入月", 12f, FontWeight.Medium, colors.primary)
                        }
                    }
                    FundCalendarLegend()
                    if (monthLoading) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { VanLoading() }
                    } else {
                        FundCalendar(days = calDays, clickable = { it.hasRecord }, onClick = {
                            selectedRecord = monthRecords.firstOrNull { h -> h.recordDate == it.date }
                            showDayPopup = true
                        })
                    }
                }
            }
        }
        }
    }

    // 当日明细弹窗（编辑收益 / 增持本金）
    if (showDayPopup && selectedRecord != null) {
        val rec = selectedRecord!!
        var editNet by remember { mutableStateOf("") }
        var editMkt by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        var showConfirmDel by remember { mutableStateOf(false) }

        LaunchedEffect(rec.id) {
            editNet = String.format("%.2f", rec.netValue)
            editMkt = String.format("%.2f", rec.marketVal)
        }

        fun saveDay() {
            if (editNet.isBlank() && editMkt.isBlank()) { toast.show("今日收益和增持本金至少填写一项"); return }
            val body = JsonObject().apply {
                addProperty("netValue", editNet.toDoubleOrNull() ?: 0.0)
                addProperty("marketVal", editMkt.toDoubleOrNull() ?: 0.0)
            }
            scope.launch {
                saving = true
                when (val res = graph.fund.updateHistory(rec.id, body)) {
                    is ApiResult.Ok -> { toast.show("修改成功"); showDayPopup = false; loadMonthly() }
                    is ApiResult.Fail -> toast.show(res.message)
                    else -> toast.show("操作失败")
                }
                saving = false
            }
        }

        fun deleteDay() {
            scope.launch {
                when (val res = graph.fund.deleteHistory(rec.id)) {
                    is ApiResult.Ok -> { toast.show("已删除"); showDayPopup = false; loadMonthly() }
                    is ApiResult.Fail -> toast.show(res.message)
                    else -> toast.show("操作失败")
                }
            }
        }

        VanPopup(show = true, onDismissRequest = { showDayPopup = false }) {
            Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FText("${rec.recordDate} 记录", 16f, FontWeight.SemiBold, colors.textPrimary)
                AppField(editNet, { editNet = it }, label = "今日收益", placeholder = "如 0.19 / -0.30", keyboardType = KeyboardType.Number)
                AppField(editMkt, { editMkt = it }, label = "增持本金", placeholder = "如 5000，不增持填 0", keyboardType = KeyboardType.Number)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton("删除", onClick = { showConfirmDel = true }, size = AppButtonSize.Small, type = AppButtonType.Danger, block = true)
                    AppButton("保存修改", onClick = { saveDay() }, size = AppButtonSize.Small, type = AppButtonType.Primary, block = true)
                }
            }
        }

        if (showConfirmDel) {
            VanConfirmDialog(
                show = true, title = "确认删除",
                message = "确定删除 ${rec.recordDate} 的记录？",
                confirmButtonText = "删除",
                onConfirm = { showConfirmDel = false; deleteDay() },
                onCancel = { showConfirmDel = false },
            )
        }
    }
}
