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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Job
import com.live.finance.data.model.SalaryDay
import com.live.finance.data.model.SalaryFormal
import com.live.finance.data.model.SalaryParttime
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.finance.theme.VanOrangeFixed
import com.live.finance.theme.vanOrangeBg
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/** 当日薪酬详情（web Work/SalaryDay.vue，三级页）。 */
@Composable
fun SalaryDayScreen(nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val work = App.of(context).graph.work
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    // ⚠ 金额色一律走 `MoneyColor`，不要写死绿/红：
    // web `.item-value.deduction { color: var(--money-expense) }`、
    //    `.item-value.income    { color: var(--money-income)  }`（SalaryDay.vue:659-666）——
    // `--money-*` 是「红收绿支/红出绿收」开关，两模式都可能是红，写死必然错一半。
    val today = LocalDate.now()

    val args = nav.currentBackStackEntry?.arguments?.getString("date")
    var workDate by remember { mutableStateOf(args ?: today.toString()) }

    var salaryDay by remember { mutableStateOf<SalaryDay?>(null) }
    var formalJob by remember { mutableStateOf<Job?>(null) }
    var parttimeJobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var showEditFormal by remember { mutableStateOf(false) }
    var showEditPart by remember { mutableStateOf<SalaryParttime?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    var fCut by remember { mutableStateOf("") }
    var pHours by remember { mutableStateOf("8") }
    var pSubsidy by remember { mutableStateOf("") }
    var pCut by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            val jobsRes = work.jobList()
            val dayRes = work.salaryDay(workDate)
            if (jobsRes is ApiResult.Ok) {
                val jobs = jobsRes.data.orEmpty()
                formalJob = jobs.firstOrNull { it.jobType == "formal" && it.status == "在职" }
                parttimeJobs = jobs.filter { it.jobType == "parttime" }
            }
            if (dayRes is ApiResult.Ok) salaryDay = dayRes.data
            loading = false
        }
    }

    fun openFormalEdit() { fCut = ""; showEditFormal = true }

    fun openPartEdit(p: SalaryParttime) {
        pHours = (p.workHours ?: 8.0).fmt()
        pSubsidy = ""
        pCut = ""
        showEditPart = p
    }

    fun saveFormal() {
        val f = salaryDay?.formal ?: return
        val body = JsonObject().apply {
            addProperty("job_id", f.jobId)
            addProperty("work_date", workDate)
            addProperty("cut", fCut.toDoubleOrNull() ?: 0.0)
        }
        scope.launch {
            when (val r = work.saveSalaryDay(body)) {
                is ApiResult.Ok -> { toast.show("保存成功", VanToastType.Success); showEditFormal = false; load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
        }
    }

    fun savePart() {
        val p = showEditPart ?: return
        // web `saveEditingParttime`：`job_id || id`，都没有 → 「无法获取工作信息」
        val partJobId = p.jobId.ifBlank { p.id }
        if (partJobId.isBlank()) return toast.show("无法获取工作信息", VanToastType.Fail)
        val body = JsonObject().apply {
            addProperty("job_id", partJobId)
            addProperty("work_date", workDate)
            addProperty("work_hours", pHours.toDoubleOrNull() ?: 8.0)
            addProperty("subsidy_meal", pSubsidy.toDoubleOrNull() ?: 0.0)
            addProperty("cut", pCut.toDoubleOrNull() ?: 0.0)
        }
        scope.launch {
            when (val r = work.saveSalaryDay(body)) {
                is ApiResult.Ok -> { toast.show("保存成功", VanToastType.Success); showEditPart = null; load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
        }
    }

    fun submitFormal() {
        val job = formalJob ?: return
        val body = JsonObject().apply {
            addProperty("job_id", job.id)
            addProperty("work_date", workDate)
        }
        scope.launch {
            when (val r = work.saveSalaryDay(body)) {
                is ApiResult.Ok -> { toast.show("提交成功", VanToastType.Success); load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "提交失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "提交失败")
            }
        }
    }

    fun doDelete() {
        scope.launch {
            when (val r = work.deleteSalaryDay(workDate)) {
                is ApiResult.Ok -> { toast.show("删除成功", VanToastType.Success); showDelete = false; load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "删除失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "删除失败")
            }
        }
    }

    fun prevDay() { workDate = LocalDate.parse(workDate).minusDays(1).toString(); load() }
    fun nextDay() { workDate = LocalDate.parse(workDate).plusDays(1).toString(); load() }

    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    val dateObj = runCatching { LocalDate.parse(workDate) }.getOrDefault(today)
    val statusHint = when {
        formalJob == null && parttimeJobs.isEmpty() -> "请先设置工作信息"
        formalJob?.status != "在职" && parttimeJobs.isEmpty() -> "当日不计薪（已离职）"
        else -> ""
    }
    val canSubmitFormal = formalJob != null && formalJob?.status == "在职" && salaryDay?.formal == null
    val hasData = salaryDay?.formal != null || (salaryDay?.parttimes?.isNotEmpty() == true)
    val totalIncome = (salaryDay?.formal?.income ?: 0.0) + (salaryDay?.parttimes?.sumOf { it.income } ?: 0.0)

    ScreenScaffold { inner ->
        Column(modifier = inner.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 头部：上一天 / 日期 / 下一天
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(colors.bgCard).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clickable { prevDay() }.padding(8.dp)) { VanIcon("arrow-left", size = 20.sp, color = colors.textSecondary) }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    VanIcon("calendar-o", size = 16.sp, color = colors.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    FText(dateTitle(dateObj), 18f, FontWeight.Bold, colors.textPrimary)
                }
                Box(Modifier.clickable { nextDay() }.padding(8.dp)) { VanIcon("arrow", size = 20.sp, color = colors.textSecondary) }
            }
            if (statusHint.isNotEmpty()) {
                // web `.status-hint`（SalaryDay.vue:588-596）：`padding: 10px 16px`、
                // `border-radius: 12px`、`margin-bottom: 12px`、
                // `color: var(--van-orange, #ff976a)`（两模式同值）、
                // `background: var(--van-orange-bg, #fff7e6)`（**浅色走 fallback #fff7e6**，深色才是 .12 半透明）。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(vanOrangeBg(Color(0xFFFFF7E6)))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon("info-o", size = 14.sp, color = VanOrangeFixed)
                    Spacer(Modifier.width(6.dp))
                    // web `.status-hint{ font-size: 13px }` → 按 7.9 定档 13 → **14**（旧写 13f，漏网）
                    FText(statusHint, 14f, color = VanOrangeFixed)
                }
            }

            if (loading && salaryDay == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { FText("加载中…", 16f, color = colors.textTertiary) }
            } else {
                val sd = salaryDay
                if (sd == null || (!hasData && statusHint.isEmpty())) {
                    Box(Modifier.fillMaxWidth().height(200.dp).padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        VanEmpty(description = if (statusHint.isEmpty()) "当日无工资记录" else statusHint)
                    }
                } else {
                    // 正式工卡片
                    sd.formal?.let { f ->
                        if (f.daySalary > 0) {
                            val items = mutableListOf<Pair<String, Double>>().apply {
                                add("日薪" to f.daySalary)
                                add("社保扣除" to -f.social)
                                add("公积金扣除" to -f.fund)
                                add("个税扣除" to -f.tax)
                                if (f.cut > 0) add("其他扣款" to -f.cut)
                            }
                            SalaryCard(f.company.ifEmpty { "正式工作" }, f.status, VanTagType.Primary, items, f.income) { openFormalEdit() }
                        }
                    }
                    // 兼职卡片
                    sd.parttimes.forEach { p ->
                        val items = mutableListOf<Pair<String, Double>>().apply {
                            add("时薪(元/小时)" to p.hourlyWage)
                            add("工作小时" to p.workHours)
                            add("日薪" to p.daySalary)
                            add("补贴" to p.subsidy)
                            if (p.cut > 0) add("扣款" to -p.cut)
                        }
                        SalaryCard(p.company.ifEmpty { "兼职" }, p.status, VanTagType.Warning, items, p.income) { openPartEdit(p) }
                    }
                    // 正式工作待提交提示（web：在职 && 今日无正式工资记录 && 有兼职）
                    if (formalJob?.status == "在职" && salaryDay?.formal == null && parttimeJobs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color(0x1FFF976A)),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon("clock-o", size = 16.sp, color = VanOrangeFixed)
                            Spacer(Modifier.width(6.dp))
                            FText("正式工资待提交", 14f, color = VanOrangeFixed)
                        }
                    }
                    // 提交正式工资
                    if (canSubmitFormal) {
                        Spacer(Modifier.height(12.dp))
                        VanButton(text = "提交正式工资", type = VanButtonType.Primary, block = true, onClick = { submitFormal() })
                    }
                    // 今日总收入
                    if (totalIncome > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(10.dp)).background(colors.primary).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FText("今日总收入", 16f, color = Color.White)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                FText("+¥${Money.format(totalIncome)}", 22f, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                    // 删除今日薪酬
                    if (hasData) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.fillMaxWidth().clickable { showDelete = true }
                                .padding(vertical = 12.dp), contentAlignment = Alignment.Center,
                        ) { FText("删除今日薪酬", 16f, color = colors.danger) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 编辑正式工弹窗（只读日薪/社保/公积金/个税，仅调当日扣款）
    VanPopup(show = showEditFormal, onDismissRequest = { showEditFormal = false }, closeOnClickOverlay = false) {
        val f = salaryDay?.formal
        val preview = if (f != null) f.daySalary - f.social - f.fund - f.tax - (fCut.toDoubleOrNull() ?: 0.0) else 0.0
        EditPopup(title = "编辑正式工资", onCancel = { showEditFormal = false }, onConfirm = { saveFormal() }, previewLabel = "实收（预览）", preview = preview) {
            AppField("¥${((f?.daySalary ?: 0.0)).fmt()}", {}, label = "日薪", disabled = true)
            AppField("-¥${((f?.social ?: 0.0)).fmt()}", {}, label = "社保", disabled = true)
            AppField("-¥${((f?.fund ?: 0.0)).fmt()}", {}, label = "公积金", disabled = true)
            AppField("-¥${((f?.tax ?: 0.0)).fmt()}", {}, label = "个税", disabled = true)
            GroupTitle("手动调整")
            AppField(fCut, { fCut = it }, label = "当日扣款", placeholder = "如迟到、请假等", keyboardType = KeyboardType.Number,
                trailing = { FText("元", 16f, color = colors.textTertiary) })
        }
    }

    // 编辑兼职弹窗（只读时薪/日薪，仅调工作小时/补贴/扣款）
    showEditPart?.let { p ->
        val hourly = p.hourlyWage
        val hours = pHours.toDoubleOrNull() ?: 8.0
        val preview = hourly * hours + (pSubsidy.toDoubleOrNull() ?: 0.0) - (pCut.toDoubleOrNull() ?: 0.0)
        VanPopup(show = true, onDismissRequest = { showEditPart = null }, closeOnClickOverlay = false) {
            EditPopup(title = "编辑兼职 - ${p.company}", onCancel = { showEditPart = null }, onConfirm = { savePart() }, previewLabel = "实收（预览）", preview = preview) {
                AppField("¥${hourly.fmt()}/小时", {}, label = "时薪", disabled = true)
                AppField("¥${p.daySalary.fmt()}", {}, label = "日薪", disabled = true)
                GroupTitle("手动调整")
                AppField(pHours, { pHours = it }, label = "工作小时", keyboardType = KeyboardType.Number,
                    trailing = { FText("小时", 16f, color = colors.textTertiary) })
                AppField(pSubsidy, { pSubsidy = it }, label = "当日补贴", placeholder = "覆盖自动值", keyboardType = KeyboardType.Number,
                    trailing = { FText("元", 16f, color = colors.textTertiary) })
                AppField(pCut, { pCut = it }, label = "当日扣款", placeholder = "如迟到、请假等", keyboardType = KeyboardType.Number,
                    trailing = { FText("元", 16f, color = colors.textTertiary) })
            }
        }
    }

    VanConfirmDialog(
        show = showDelete,
        title = "确认删除",
        message = "确定要删除今日的所有薪酬记录吗？",
        confirmButtonText = "删除",
        onConfirm = { doDelete() },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )
}

private val WEEK_CN = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
private fun dateTitle(d: LocalDate): String {
    val w = WEEK_CN[d.dayOfWeek.value % 7]
    return "%02d月%02d日 $w".format(d.monthValue, d.dayOfMonth)
}

@Composable
private fun SalaryCard(
    title: String,
    status: Int,
    tagType: VanTagType,
    items: List<Pair<String, Double>>,
    income: Double,
    onEdit: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(colors.bgCard).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(title, 18f, FontWeight.Bold, colors.textPrimary, Modifier.weight(1f))
            if (status >= 1) VanTag(text = "已确认", type = tagType, size = VanTagSize.Small)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clickable { onEdit() }) { FText("编辑", 14f, color = colors.primary) }
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { (label, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                FText(label, 14f, color = colors.textSecondary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    // web：`.item-value`（默认 = --theme-text-primary）＋ `.deduction` = --money-expense
                    val text = if (v < 0) "-¥${Money.format(-v)}" else "¥${Money.format(v)}"
                    FText(text, 14f, color = if (v < 0) MoneyColor.expense else colors.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText("当日实收", 16f, FontWeight.Bold, colors.textPrimary)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                // web `.item-value.income`：`--money-income`、18px、700；文本是纯 `¥xxx`（**无 + 号**）
                FText("¥${Money.format(income)}", 18f, FontWeight.Bold, MoneyColor.income)
            }
        }
    }
}

@Composable
private fun EditPopup(
    title: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    previewLabel: String,
    preview: Double,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(title, 18f, FontWeight.Bold, colors.textPrimary, Modifier.weight(1f))
            Box(Modifier.clickable { onCancel() }.padding(4.dp)) { VanIcon("cross", size = 18.sp, color = colors.textSecondary) }
        }
        Spacer(Modifier.height(8.dp))
        content()
        // web `.preview-card`（SalaryDay.vue:774-792）：`margin: 16px`、`padding: 16px`、
        // `background: linear-gradient(135deg, --theme-primary 0%, --theme-primary-grad 100%)`、
        // `border-radius: 12px`、`color: #fff`；label 14px/opacity .9，value 24px/700。
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(previewLabel, 14f, FontWeight.Normal, Color.White.copy(alpha = 0.9f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                FText("¥${Money.format(preview)}", 24f, FontWeight.Bold, Color.White)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).clickable { onCancel() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                FText("取消", 16f, color = colors.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(colors.primary)
                .clickable { onConfirm() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                FText("保存", 16f, FontWeight.Bold, Color.White)
            }
        }
    }
}

private fun Double.fmt(): String = "%.2f".format(this)

@Composable
private fun GroupTitle(text: String) {
    val colors = LocalAppColors.current
    FText(text, 14f, FontWeight.Normal, colors.textSecondary, Modifier.padding(top = 12.dp, bottom = 2.dp))
}
