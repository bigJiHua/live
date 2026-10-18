package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Fund
import com.live.finance.data.model.FundHistory
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.theme.VanOrangeFixed
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/**
 * 每日净值登记（对齐 web Finance/report/fund/FundDaily.vue）：
 * 四格概览（当前本金 / 累计收益 / 当前市值 / 回报率）+ 基金 chips + 快速跳转 + 收益日历 + 底部登记弹层。
 */
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
    var calendarMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var latestRecordDate by remember { mutableStateOf("") }

    var monthRecords by remember { mutableStateOf<List<FundHistory>>(emptyList()) }
    var monthLoading by remember { mutableStateOf(false) }
    var showDayPopup by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<FundCalDay?>(null) }

    val currentFund = fundList.firstOrNull { it.id == currentFundId }
    val latestRecordMonth = runCatching { LocalDate.parse(latestRecordDate, fmt) }.getOrNull()?.withDayOfMonth(1)
    val buyDateMonth = parseFundDate(currentFund?.buyDate ?: "").takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it, fmt) }.getOrNull() }?.withDayOfMonth(1)
    val isLatestMonth = latestRecordMonth != null && calendarMonth == latestRecordMonth
    val isBuyMonth = buyDateMonth != null && calendarMonth == buyDateMonth

    val fundProfit = currentFund?.netValue ?: 0.0
    // 对齐 web getFundRate：四舍五入到 2 位小数
    val rateVal = currentFund?.let { if (it.invest > 0) it.netValue / it.invest * 100 else 0.0 } ?: 0.0

    fun loadMonthly() {
        val fid = currentFundId
        if (fid.isBlank()) return
        scope.launch {
            monthLoading = true
            when (val res = graph.fund.historyMonthly(fid, calendarMonth.year, calendarMonth.monthValue)) {
                is ApiResult.Ok -> {
                    val d = res.data
                    monthRecords = d?.records ?: emptyList()
                    if (d != null && d.latestRecordDate.isNotBlank()) latestRecordDate = d.latestRecordDate
                }
                // web `loadHistory` 的 catch：静默清空列表，不提示
                else -> monthRecords = emptyList()
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
                    if (d != null && d.isNotEmpty()) currentFundId = d.first().id
                    loadMonthly()
                }
                // web `loadFundList` 无 try/catch，冒泡到 onMounted → showToast('加载失败')
                else -> toast.showFail(res, "加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(currentFundId, calendarMonth) { if (currentFundId.isNotBlank()) loadMonthly() }

    val calDays = if (currentFund != null) buildFundCalDays(monthRecords, currentFund.buyDate, calendarMonth, true) else emptyList()

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanLoading() }
                } else {
                    if (currentFund != null) {
                        // 概览四格
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgCard)
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                        ) {
                            DcOverviewItem("当前本金", "¥${fundAmount(currentFund.invest)}", null, Modifier.weight(1f))
                            DcOverviewItem(
                                "累计收益",
                                "${if (fundProfit >= 0) "+" else ""}¥${fundAmount(abs(fundProfit))}",
                                if (fundProfit >= 0) colors.danger else colors.success, Modifier.weight(1f),
                            )
                            DcOverviewItem("当前市值", "¥${fundAmount(currentFund.marketVal)}", colors.primary, Modifier.weight(1f))
                            DcOverviewItem(
                                "回报率",
                                "${if (fundProfit >= 0) "+" else ""}${fundRate(Math.round(rateVal * 100.0) / 100.0)}",
                                if (fundProfit >= 0) colors.danger else colors.success, Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // web `.section-title{font-size:15px; font-weight:600; margin-bottom:10px; padding-left:2px}` → §7.9：15px ⇒ 16f
                    FText("选择基金", 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(start = 2.dp, bottom = 10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fundList.forEach { item ->
                            val selected = item.id == currentFundId
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) colors.primary else colors.bgThird)
                                    .clickable {
                                        if (item.id != currentFundId) {
                                            currentFundId = item.id
                                            monthRecords = emptyList()
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                FText(item.fundName.ifEmpty { "未命名" }, 12f, FontWeight.Normal, if (selected) Color.White else colors.textSecondary)
                            }
                        }
                    }

                    if (currentFund != null) {
                        FText("每日收益登记", 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(start = 2.dp, bottom = 10.dp))

                        // 快速跳转
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x1FFF976A))
                                .border(1.dp, Color(0x66FF976A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // web `.stale-tip{font-size:13px; padding:10px 14px; border-radius:8px}` → §7.9：13px ⇒ 14f
                            FText("📅 快速跳转", 14f, FontWeight.Normal, VanOrangeFixed, Modifier.weight(1f))
                            QuickJumpBtn("今日") { calendarMonth = LocalDate.now().withDayOfMonth(1) }
                            if (latestRecordMonth != null && !isLatestMonth) {
                                QuickJumpBtn("最新") { calendarMonth = latestRecordMonth }
                            }
                            if (buyDateMonth != null && !isBuyMonth) {
                                QuickJumpBtn("买入月") { calendarMonth = buyDateMonth }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        FundCalHeader("${calendarMonth.year}年${calendarMonth.monthValue}月", {
                            calendarMonth = calendarMonth.minusMonths(1)
                        }, {
                            calendarMonth = calendarMonth.plusMonths(1)
                        })
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (latestRecordMonth != null) {
                                CalShortcut("收益月") { calendarMonth = latestRecordMonth }
                            }
                            if (buyDateMonth != null) {
                                Spacer(Modifier.width(8.dp))
                                CalShortcut("买入月") { calendarMonth = buyDateMonth }
                            }
                        }

                        if (monthLoading) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { VanLoading() }
                        } else {
                            FundCalendarLegend()
                            FundCalendar(days = calDays, clickable = { it.inMonth }, onClick = {
                                selectedDay = it
                                showDayPopup = true
                            })
                        }
                    } else {
                        com.live.vant.basic.VanEmpty(description = "请先选择基金")
                    }
                }
            }
        }
    }

    // 当日登记 / 修改弹窗
    if (showDayPopup && selectedDay != null) {
        val day = selectedDay!!
        val existing = monthRecords.firstOrNull { it.recordDate == day.date }
        val isEdit = existing != null
        var netValueStr by remember(day.date, existing?.id) { mutableStateOf(existing?.let { trimDecimal(it.netValue, 2) } ?: "") }
        var marketValStr by remember(day.date, existing?.id) { mutableStateOf(existing?.let { trimDecimal(it.marketVal, 2) } ?: "") }
        var saving by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }

        fun saveDay() {
            // web `handleSave`：两项都为空 → 「今日收益和增持本金至少填写一项」
            if (netValueStr.isBlank() && marketValStr.isBlank()) {
                toast.show("今日收益和增持本金至少填写一项")
                return
            }
            val body = JsonObject().apply {
                addProperty("netValue", netValueStr.ifBlank { "0" })
                addProperty("marketVal", marketValStr.ifBlank { "0" })
                if (existing == null) addProperty("recordDate", day.date)
            }
            scope.launch {
                saving = true
                val res = if (existing != null) graph.fund.updateHistory(existing.id, body) else graph.fund.addHistory(currentFundId, body)
                when (res) {
                    is ApiResult.Ok -> {
                        toast.show(if (existing != null) "修改成功" else "登记成功")
                        showDayPopup = false
                        reload()
                    }
                    is ApiResult.Fail -> toast.show(res.message.ifBlank { "保存失败" }, VanToastType.Fail)
                    else -> toast.showFail(res, "保存失败")
                }
                saving = false
            }
        }

        fun deleteDay() {
            if (existing == null) return
            scope.launch {
                deleting = true
                when (graph.fund.deleteHistory(existing.id)) {
                    is ApiResult.Ok -> {
                        toast.show("删除成功", VanToastType.Success)
                        showDayPopup = false
                        reload()
                    }
                    else -> toast.show("删除失败", VanToastType.Fail)
                }
                deleting = false
            }
        }

        VanPopup(show = true, onDismissRequest = { showDayPopup = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // web `.popup-header{font-size:16px; font-weight:600; margin-bottom:12px}` → §7.9：16px ⇒ 18f
                FText("${day.date} ${if (isEdit) "修改记录" else "登记收益"}", 18f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
                    VanIcon("cross", size = 20.sp, color = colors.textTertiary, onClick = { showDayPopup = false })
                }
                Spacer(Modifier.height(12.dp))
                AppField(day.date, {}, label = "日期")
                AppField(netValueStr, { netValueStr = it }, label = "今日收益", placeholder = "如 0.19 / -0.30", keyboardType = KeyboardType.Number, clearable = true)
                AppField(marketValStr, { marketValStr = it }, label = "增持本金", placeholder = "如 5000，不增持留空", keyboardType = KeyboardType.Number, clearable = true)
                if (!isEdit) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1FFF976A)).padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        // web `.form-tip{font-size:11px; padding:8px 10px; border-radius:6px}` → §7.9：11px ⇒ 12f
                        FText("今日收益只填当天赚亏金额；增持本金只填新投入的本金。", 12f, FontWeight.Normal, VanOrangeFixed)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.weight(1f))
                    if (isEdit) {
                        AppButton("删除", onClick = { confirmDelete = true }, size = AppButtonSize.Small, type = AppButtonType.Danger, plain = true, loading = deleting)
                    }
                    AppButton(
                        if (isEdit) "保存修改" else "登记",
                        onClick = { saveDay() },
                        size = AppButtonSize.Small, type = AppButtonType.Primary, loading = saving,
                    )
                }
            }
        }

        if (confirmDelete) {
            VanConfirmDialog(
                show = true,
                title = "确认删除",
                message = "确定删除 ${day.date} 的记录？",
                onConfirm = { confirmDelete = false; deleteDay() },
                onCancel = { confirmDelete = false },
                onClose = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun DcOverviewItem(label: String, value: String, valueColor: Color?, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.padding(horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // web `.oc-label{font-size:10px; margin-bottom:4px}` → §7.9：10px ⇒ 12f
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        // web `.oc-value{font-size:14px; font-weight:700}` → §7.9：14px ⇒ 16f
        FText(value, 16f, FontWeight.Bold, valueColor ?: colors.textPrimary)
    }
}

@Composable
private fun QuickJumpBtn(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(colors.bgCard)
            .border(1.dp, VanOrangeFixed, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        // web `.stale-btn{font-size:13px; font-weight:600; padding:3px 12px; border-radius:14px}` → §7.9：13px ⇒ 14f
        FText(text, 14f, FontWeight.SemiBold, VanOrangeFixed)
    }
}

@Composable
private fun CalShortcut(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(colors.bgCard)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        FText(text, 12f, FontWeight.Normal, colors.textPrimary)
    }
}

