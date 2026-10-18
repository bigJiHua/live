package com.live.finance.ui.work

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
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
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
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
    val tokens = LocalAppTokens.current
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
                    FText("${year}年${month}月", 18f, FontWeight.Bold, colors.textPrimary)
                    VanIcon("arrow-down", size = 14.sp, color = colors.textTertiary)
                }
                Box(Modifier.clickable { nextMonth() }.padding(6.dp)) { VanIcon("arrow", size = 20.sp, color = colors.textSecondary) }
            }

            if (loading && monthData == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { FText("加载中…", 16f, color = colors.textTertiary) }
            } else {
                val data = monthData ?: SalaryMonth()
                // 汇总渐变卡（web .stat-summary：主色渐变 + 白字）
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(tokens.primary, tokens.grad)))
                        .padding(20.dp),
                ) {
                    FText("月度总收入", 14f, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    FText("¥${data.totalIncome.ifEmpty { "0.00" }}", 32f, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    Spacer(Modifier.height(12.dp))
                    // web：`<span class="detail-value blue">` / `<span class="detail-value orange">`。
                    // 基类 `.detail-value{color:#fff}` 被 scoped 的 `.detail-value.blue{color:rgba(255,255,255,.92)}`、
                    // `.detail-value.orange{color:#ffae78}` 覆盖；但二者**仍带 .blue/.orange 类**，
                    // 所以照样命中 `html[data-theme-mono="1"] .blue{color:var(--theme-primary)!important}`。
                    // ⇒ 非单色主题用各自的 scoped 基色，单色主题统一主色（tokens.semanticOr）。
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column {
                            FText("正式工资", 12f, color = Color.White)
                            Spacer(Modifier.height(2.dp))
                            FText(
                                "¥${data.formalTotal.ifEmpty { "0.00" }}", 18f, FontWeight.Bold,
                                tokens.semanticOr(Color.White.copy(alpha = 0.92f)),
                            )
                        }
                        Column {
                            FText("兼职收入", 12f, color = Color.White)
                            Spacer(Modifier.height(2.dp))
                            FText(
                                "¥${data.parttimeTotal.ifEmpty { "0.00" }}", 18f, FontWeight.Bold,
                                tokens.semanticOr(Color(0xFFFFAE78)),
                            )
                        }
                    }
                }

                // 每日明细
                val validDays = data.dailyList.count { it.formalIncome + it.parttimeTotal > 0 }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FText("每日明细", 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
                        FText("${validDays}个计薪日", 14f, FontWeight.Normal, colors.textTertiary)
                    }
                    if (data.dailyList.isEmpty() && !loading) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无工资记录")
                        }
                    } else {
                        data.dailyList.forEach { d ->
                            val dayTotal = d.formalIncome + d.parttimeTotal
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { nav.navigate("${Routes.WORK_SALARY_DAY}?date=" + d.date) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    FText(mmdd(d.date), 16f, FontWeight.Medium, colors.textPrimary)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (d.formalIncome > 0) {
                                            VanTag(text = "正式 ¥${d.formalIncome}", type = VanTagType.Primary, size = VanTagSize.Small)
                                        }
                                        if (d.parttimeIncomes.isNotEmpty()) {
                                            VanTag(text = "兼职×${d.parttimeIncomes.size}", type = VanTagType.Warning, size = VanTagSize.Small)
                                        }
                                    }
                                }
                                FText("¥$dayTotal", 16f, FontWeight.Medium, colors.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
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

/** 对齐 web `formatDate`：YYYY-MM-DD → MM/DD。 */
private fun mmdd(dateStr: String): String {
    val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
    return String.format("%02d/%02d", d.monthValue, d.dayOfMonth)
}
