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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Job
import com.live.finance.data.model.SalaryMonth
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.ui.todo.CalendarVariant
import com.live.finance.ui.todo.DayMark
import com.live.finance.ui.todo.TodoCalendarGrid
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 工资日历 —— 一比一复刻 web `views/Work/SalaryCalendar.vue`。
 *
 * ⭐ **复用共享日历组件**（web 该页 `<CalendarGrid variant="salary" :card="false" :show-stat="false"
 * :show-header="false" :dataset="calendarDataset" @select="onSalarySelect" :primary="'var(--theme-primary)'" />`）：
 * 原生对应 [TodoCalendarGrid] 的 [CalendarVariant.Salary]。⚠ 该页**不传 `collapsible`**（共享件默认 false）
 * → **没有收起条**；**不传 `selectedDate`** → **没有右下角「今」圆环**；星期行/6×80 网格/今日主色实心圆全部沿用共享件。
 *
 * 结构：页头（`padding 16`、bg 卡片；**上一月 `arrow-left` 是绿色 `#07c160`**，月份标题 18/600 + 绿色 `arrow-down`，
 * 右侧动作区 `setting-o`/`bars`/`arrow` 为**三级色**）→ 统计栏（月总收入[收支色·flex 1] / 正式[**主色**] /
 * 兼职[**#ff976a**]，两条 `1×30` 分隔线；金额是**后端原样字符串**如 `0.00`，不是 Money.format）→
 * `.calendar-grid-wrap`（卡片底 + 1px 边框、**无顶边**）内嵌共享网格 → 月份选择器（**年 = 今天所在年 ±5**）。
 *
 * 交互：格子数据按 web `calendarDataset` 口径逐日生成（在职区间 + 当日 `daily_list` 的正式收入/兼职合计）；
 * 点**非工作日**不跳转，其余跳 `/work/salary-day?date=`；`setting-o` → 工作设置，`bars` → 薪资统计（带 year/month）；
 * 左右箭头与选月都重拉（web `loadMonthData`）。
 */
@Composable
fun SalaryCalendarScreen(nav: NavHostController) {
    val context = LocalContext.current
    val work = App.of(context).graph.work
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    // web 页头箭头色 `var(--van-green,#07c160)`：浅色 #07c160，深色被 style.css 重定向到 `--theme-success`
    val salaryGreen = if (tokens.isDark) colors.success else SALARY_GREEN
    val today = LocalDate.now()

    var year by remember { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(today.monthValue) }
    var monthData by remember { mutableStateOf<SalaryMonth?>(null) }
    var formalJob by remember { mutableStateOf<Job?>(null) }
    var parttimeJobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showMonthPicker by remember { mutableStateOf(false) }
    // web `pickerSelectedValues`：初始 = **今天**的年月，确认后才改为所选（不是当前查看月）
    var pickerYear by remember { mutableStateOf(today.year) }
    var pickerMonth by remember { mutableStateOf(today.monthValue) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            val jobsRes = work.jobList()
            val monthRes = work.salaryMonth(year, month)
            if (jobsRes is ApiResult.Ok) {
                val jobs = jobsRes.data.orEmpty()
                formalJob = jobs.firstOrNull { it.jobType == "formal" && it.status == "在职" }
                parttimeJobs = jobs.filter { it.jobType == "parttime" && it.status == "在职" }
            }
            if (monthRes is ApiResult.Ok) monthData = monthRes.data
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

    LaunchedEffect(Unit) { load() }

    // web `calendarDataset`：整月逐日 → dataset
    val dataset = remember(year, month, monthData, formalJob, parttimeJobs) {
        val out = HashMap<String, DayMark>()
        val first = LocalDate.of(year, month, 1)
        val dailyList = monthData?.dailyList.orEmpty()
        for (d in 1..first.lengthOfMonth()) {
            val date = first.withDayOfMonth(d)
            val ds = date.toString()
            val formalWorking = isFormalWorking(date, formalJob)
            val parttimeWorking = isParttimeWorking(date, parttimeJobs)
            val day = dailyList.firstOrNull { it.date == ds }
            out[ds] = DayMark(
                formalIncome = day?.formalIncome ?: 0.0,
                parttimeTotal = day?.parttimeTotal ?: 0.0,
                isWorkingDay = formalWorking || parttimeWorking,
                notWorking = !formalWorking && !parttimeWorking,
            )
        }
        out
    }

    ScreenScaffold { inner ->
        Column(modifier = inner.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ① 页头（.header：padding 16、bg 卡片；左侧箭头绿色、动作区三级色）
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.clickable { prevMonth() }.padding(6.dp)) {
                    VanIcon("arrow-left", size = 18.sp, color = salaryGreen)
                }
                Row(
                    modifier = Modifier.clickable { showMonthPicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FText("${year}年${month}月", 18f, FontWeight.SemiBold, colors.textPrimary)
                    // `.header .van-icon` 统一 18px + padding 6 + 绿色（标题里的箭头也在其中）
                    Box(Modifier.padding(6.dp)) { VanIcon("arrow-down", size = 18.sp, color = salaryGreen) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.clickable { nav.navigate(Routes.WORK_JOB_SETTING) }.padding(6.dp)) {
                        VanIcon("setting-o", size = 18.sp, color = colors.textTertiary)
                    }
                    Box(
                        Modifier
                            .clickable { nav.navigate("${Routes.WORK_SALARY_STAT}?year=${year}&month=${month}") }
                            .padding(6.dp),
                    ) { VanIcon("bars", size = 18.sp, color = colors.textTertiary) }
                    Box(Modifier.clickable { nextMonth() }.padding(6.dp)) {
                        VanIcon("arrow", size = 18.sp, color = colors.textTertiary)
                    }
                }
            }

            // ② 统计栏（.stat-bar：bg 卡片、padding 16、mb 8、gap 16；主项 flex 1，其余自然宽度）
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 金额是后端原样字符串（web `¥{{ monthData.total_income || "0.00" }}`）
                StatItem("月总收入", "¥${monthData?.totalIncome ?: "0.00"}", MoneyColor.income, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(colors.border))
                StatItem("正式", "¥${monthData?.formalTotal ?: "0.00"}", colors.primary)
                StatItem("兼职", "¥${monthData?.parttimeTotal ?: "0.00"}", SALARY_ORANGE)
            }
            Spacer(Modifier.height(8.dp))

            // ③ 日历（.calendar-grid-wrap：卡片底 + 1px 边框、**无顶边**）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard)
                    .drawBehind {
                        // web `border:1px solid var(--theme-border); border-top:none`
                        val w = 1.dp.toPx()
                        drawLine(colors.border, Offset(0f, 0f), Offset(0f, size.height), w)                      // left
                        drawLine(colors.border, Offset(size.width, 0f), Offset(size.width, size.height), w)      // right
                        drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), w)     // bottom
                    },
            ) {
                if (loading) {
                    // `.calendar-loading{center; height 300; bg secondary}` + `van-loading size 32`
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        VanLoading(size = 32.sp, text = "加载中...")
                    }
                } else {
                    TodoCalendarGrid(
                        year = year,
                        month = month,
                        selectedDate = "",                       // web 不传 → 无「今」圆环
                        dataset = dataset,
                        onSelect = { ds ->
                            // web `onSalarySelect`：非工作日直接 return
                            if (dataset[ds]?.notWorking != true) {
                                nav.navigate("${Routes.WORK_SALARY_DAY}?date=$ds")
                            }
                        },
                        onGoToday = {},
                        variant = CalendarVariant.Salary,
                        collapsible = false,                     // web 不传 collapsible（共享件默认 false）
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // ④ 月份选择器（年 = 今天所在年 ±5）
    VanPopup(show = showMonthPicker, onDismissRequest = { showMonthPicker = false }) {
        val years = (today.year - 5)..(today.year + 5)
        VanPicker(
            columns = vanPickerColumnsOf(years.map { "${it}年" }, (1..12).map { "${it}月" }),
            value = listOf(pickerYear - (today.year - 5), pickerMonth - 1),
            onConfirm = { sel, _ ->
                year = today.year - 5 + sel[0]
                month = sel[1] + 1
                pickerYear = year
                pickerMonth = month
                showMonthPicker = false
                load()
            },
            onCancel = { showMonthPicker = false },
            title = "选择月份",
        )
    }
}

/** `--van-green`（web 页头箭头用的 Vant 调色板色，非主题色）。 */
private val SALARY_GREEN = Color(0xFF07C160)

/** `--van-orange`（兼职工资金额）。 */
private val SALARY_ORANGE = Color(0xFFFF976A)

/** `.stat-item{column; gap 4}` + `.stat-label{12px tertiary}` + `.stat-value{18px/600}`。 */
@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        FText(value, 18f, FontWeight.SemiBold, color)
    }
}

/** web `isFormalWorking`：在职 + join_date ≤ date ≤（leave_date 为空则视为未来）。 */
private fun isFormalWorking(date: LocalDate, formalJob: Job?): Boolean {
    val fj = formalJob ?: return false
    if (fj.status != "在职") return false
    if (date.isBefore(parseDate(fj.joinDate, false))) return false
    if (fj.leaveDate.isNotEmpty() && date.isAfter(parseDate(fj.leaveDate, true))) return false
    return true
}

/** web `isParttimeWorking`：任一兼职在职且落在其区间内。 */
private fun isParttimeWorking(date: LocalDate, parttimeJobs: List<Job>): Boolean = parttimeJobs.any { pj ->
    if (pj.joinDate.isEmpty()) return@any false
    if (date.isBefore(parseDate(pj.joinDate, false))) return@any false
    if (pj.leaveDate.isNotEmpty() && date.isAfter(parseDate(pj.leaveDate, true))) return@any false
    true
}

private fun parseDate(s: String, defaultFuture: Boolean): LocalDate {
    val def = if (defaultFuture) LocalDate.of(2099, 12, 31) else LocalDate.of(1900, 1, 1)
    if (s.isEmpty()) return def
    return runCatching { LocalDate.parse(s) }.getOrDefault(def)
}
