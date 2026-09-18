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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.data.model.Job
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppCard
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 工作信息设置（web Work/JobSetting.vue，三级页；用于配置正式工/兼职以驱动薪酬计算）。 */
@Composable
fun JobListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val work = App.of(context).graph.work
    val colors = LocalAppColors.current
    val incomeGreen = Color(0xFF07C160)

    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showEditFormal by remember { mutableStateOf(false) }
    var editingFormalId by remember { mutableStateOf<String?>(null) }
    var showEditPart by remember { mutableStateOf(false) }
    var editingPartId by remember { mutableStateOf<String?>(null) }
    var confirmStatus by remember { mutableStateOf<Job?>(null) }
    var confirmDelete by remember { mutableStateOf<Job?>(null) }

    var fCompany by remember { mutableStateOf("") }
    var fJoin by remember { mutableStateOf("") }
    var fLeave by remember { mutableStateOf("") }
    var fPayDay by remember { mutableStateOf("10") }
    var fBase by remember { mutableStateOf("0") }
    var fDays by remember { mutableStateOf("22") }
    var fMeal by remember { mutableStateOf("0") }
    var fTraffic by remember { mutableStateOf("0") }
    var fPost by remember { mutableStateOf("0") }
    var fSocial by remember { mutableStateOf("0") }
    var fFund by remember { mutableStateOf("0") }
    var fTaxRate by remember { mutableStateOf("0") }

    var pCompany by remember { mutableStateOf("") }
    var pJoin by remember { mutableStateOf("") }
    var pLeave by remember { mutableStateOf("") }
    var pPayDay by remember { mutableStateOf("10") }
    var pHourly by remember { mutableStateOf("0") }

    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            val res = work.jobList()
            if (res is ApiResult.Ok) jobs = res.data.orEmpty()
            loading = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    val formalJob = jobs.firstOrNull { it.jobType == "formal" }
    val parttimeJobs = jobs.filter { it.jobType == "parttime" }

    fun openFormal(id: String?) {
        editingFormalId = id
        val j = id?.let { jobs.firstOrNull { it.id == id } }
        fCompany = j?.company ?: ""
        fJoin = j?.joinDate ?: ""
        fLeave = j?.leaveDate ?: ""
        fPayDay = (j?.payDay ?: 10).toString()
        fBase = (j?.baseSalary ?: 0.0).fmt()
        fDays = (j?.baseWorkDays ?: 22).toString()
        fMeal = (j?.subsidyMeal ?: 0.0).fmt()
        fTraffic = (j?.subsidyTraffic ?: 0.0).fmt()
        fPost = (j?.subsidyPost ?: 0.0).fmt()
        fSocial = (j?.social ?: 0.0).fmt()
        fFund = (j?.fund ?: 0.0).fmt()
        fTaxRate = (j?.taxRate ?: 0.0).fmt()
        showEditFormal = true
    }

    fun openPart(id: String?) {
        editingPartId = id
        val j = id?.let { jobs.firstOrNull { it.id == id } }
        pCompany = j?.company ?: ""
        pJoin = j?.joinDate ?: ""
        pLeave = j?.leaveDate ?: ""
        pPayDay = (j?.payDay ?: 10).toString()
        pHourly = (j?.hourlyWage ?: 0.0).fmt()
        showEditPart = true
    }

    fun formalBody(): JsonObject = JsonObject().apply {
        addProperty("job_type", "formal")
        addProperty("company", fCompany.trim())
        addProperty("join_date", fJoin)
        addProperty("leave_date", fLeave)
        addProperty("pay_day", fPayDay.toIntOrNull() ?: 10)
        addProperty("base_salary", fBase.toDoubleOrNull() ?: 0.0)
        addProperty("base_work_days", fDays.toIntOrNull() ?: 22)
        addProperty("subsidy_meal", fMeal.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_traffic", fTraffic.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_post", fPost.toDoubleOrNull() ?: 0.0)
        addProperty("social", fSocial.toDoubleOrNull() ?: 0.0)
        addProperty("fund", fFund.toDoubleOrNull() ?: 0.0)
        addProperty("tax_rate", fTaxRate.toDoubleOrNull() ?: 0.0)
        addProperty("status", "在职")
    }

    fun partBody(): JsonObject = JsonObject().apply {
        addProperty("job_type", "parttime")
        addProperty("company", pCompany.trim())
        addProperty("join_date", pJoin)
        addProperty("leave_date", pLeave)
        addProperty("pay_day", pPayDay.toIntOrNull() ?: 10)
        addProperty("hourly_wage", pHourly.toDoubleOrNull() ?: 0.0)
        addProperty("status", "在职")
    }

    fun jobBody(j: Job, status: String): JsonObject = JsonObject().apply {
        addProperty("job_type", j.jobType)
        addProperty("company", j.company)
        addProperty("join_date", j.joinDate)
        addProperty("leave_date", j.leaveDate)
        addProperty("pay_day", j.payDay)
        addProperty("base_salary", j.baseSalary)
        addProperty("base_work_days", j.baseWorkDays)
        addProperty("subsidy_meal", j.subsidyMeal)
        addProperty("subsidy_traffic", j.subsidyTraffic)
        addProperty("subsidy_post", j.subsidyPost)
        addProperty("social", j.social)
        addProperty("fund", j.fund)
        addProperty("tax_rate", j.taxRate)
        addProperty("hourly_wage", j.hourlyWage)
        addProperty("status", status)
    }

    fun submitFormal() {
        val body = formalBody()
        scope.launch {
            if (editingFormalId != null) work.updateJob(editingFormalId!!, body) else work.saveJob(body)
            showEditFormal = false; load()
        }
    }

    fun submitPart() {
        val body = partBody()
        scope.launch {
            if (editingPartId != null) work.updateJob(editingPartId!!, body) else work.saveJob(body)
            showEditPart = false; load()
        }
    }

    fun doToggleStatus() {
        val j = confirmStatus ?: return
        val newStatus = if (j.status == "在职") "离职" else "在职"
        scope.launch { work.updateJob(j.id, jobBody(j, newStatus)); confirmStatus = null; load() }
    }

    fun doDelete() {
        val j = confirmDelete ?: return
        scope.launch { work.deleteJob(j.id); confirmDelete = null; load() }
    }

    // 正式工预览计算（对应 web computedFormalDaily 等）
    val formalDaily = if (formalJob != null && formalJob.baseWorkDays > 0) formalJob.baseSalary / formalJob.baseWorkDays else 0.0
    val formalMonthly = formalDaily * (formalJob?.baseWorkDays ?: 0)
    val formalSubsidy = (formalJob?.subsidyMeal ?: 0.0) + (formalJob?.subsidyTraffic ?: 0.0) + (formalJob?.subsidyPost ?: 0.0)
    val formalTax = formalJob?.baseSalary?.let { it * formalJob.taxRate } ?: 0.0

    ScreenScaffold { inner ->
        Column(modifier = inner.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clickable { nav.popBackStack() }.padding(8.dp)) { VanIcon("arrow-left", size = 20.sp, color = colors.textSecondary) }
                FText("工作信息", 17f, FontWeight.Bold, colors.textPrimary)
            }

            if (loading && jobs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { FText("加载中…", 14f, color = colors.textTertiary) }
            } else {
                // ===== 正式工 =====
                FText("正式工", 15f, FontWeight.Bold, colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                if (formalJob != null) {
                    AppCard(Modifier.padding(horizontal = 12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            SalaryRow("日薪", "+¥${Money.format(formalDaily)}", incomeGreen)
                            SalaryRow("月预估", "+¥${Money.format(formalMonthly)}", incomeGreen)
                            SalaryRow("社保", "-¥${Money.format(formalJob.social)}", colors.textPrimary)
                            SalaryRow("公积金", "-¥${Money.format(formalJob.fund)}", colors.textPrimary)
                            SalaryRow("补贴", "+¥${Money.format(formalSubsidy)}", incomeGreen)
                            SalaryRow("个税", "-¥${Money.format(formalTax)}", colors.textPrimary)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f).clickable { openFormal(formalJob.id) }, contentAlignment = Alignment.CenterStart) { FText("编辑", 14f, color = colors.primary) }
                                Box(Modifier.weight(1f).clickable { confirmStatus = formalJob }, contentAlignment = Alignment.CenterEnd) {
                                    FText(if (formalJob.status == "在职") "离职" else "复职", 14f, color = colors.danger)
                                }
                            }
                        }
                    }
                } else {
                    ActionButton("添加正式工信息") { openFormal(null) }
                }

                // ===== 兼职 =====
                FText("兼职", 15f, FontWeight.Bold, colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                if (parttimeJobs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).background(colors.bgCard).padding(20.dp), contentAlignment = Alignment.Center) {
                        VanEmpty(description = "暂无兼职信息")
                    }
                } else {
                    parttimeJobs.forEach { j ->
                        AppCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    FText(j.company.ifEmpty { "兼职" }, 15f, FontWeight.Medium, colors.textPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    FText("时薪 ¥${Money.format(j.hourlyWage)}/小时", 12f, color = colors.textTertiary)
                                }
                                Box(Modifier.clickable { openPart(j.id) }.padding(horizontal = 8.dp)) { FText("编辑", 14f, color = colors.primary) }
                                Box(Modifier.clickable { confirmStatus = j }.padding(start = 8.dp)) {
                                    FText(if (j.status == "在职") "离职" else "复职", 14f, color = colors.danger)
                                }
                            }
                        }
                    }
                }
                ActionButton("添加兼职") { openPart(null) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 编辑正式工弹窗
    VanPopup(show = showEditFormal, onDismissRequest = { showEditFormal = false }) {
        Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp).verticalScroll(rememberScrollState())) {
            FText(if (editingFormalId != null) "编辑正式工" else "添加正式工", 16f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            VanField(value = fCompany, onValueChange = { fCompany = it }, label = "公司名称")
            DateField("入职日期", fJoin) { fJoin = it }
            DateField("离职日期", fLeave) { fLeave = it }
            VanField(value = fPayDay, onValueChange = { fPayDay = it }, label = "发薪日(每月几号)", type = VanFieldType.Number)
            VanField(value = fBase, onValueChange = { fBase = it }, label = "月基本工资(元)", type = VanFieldType.Number)
            VanField(value = fDays, onValueChange = { fDays = it }, label = "月计薪天数", type = VanFieldType.Number)
            VanField(value = fMeal, onValueChange = { fMeal = it }, label = "餐补(元)", type = VanFieldType.Number)
            VanField(value = fTraffic, onValueChange = { fTraffic = it }, label = "交通补(元)", type = VanFieldType.Number)
            VanField(value = fPost, onValueChange = { fPost = it }, label = "岗位补(元)", type = VanFieldType.Number)
            VanField(value = fSocial, onValueChange = { fSocial = it }, label = "社保(元)", type = VanFieldType.Number)
            VanField(value = fFund, onValueChange = { fFund = it }, label = "公积金(元)", type = VanFieldType.Number)
            VanField(value = fTaxRate, onValueChange = { fTaxRate = it }, label = "个税税率(0~1)", type = VanFieldType.Number)
            Spacer(Modifier.height(12.dp))
            VanButton(text = "保存", type = VanButtonType.Primary, block = true, onClick = { submitFormal() })
            Spacer(Modifier.height(8.dp))
        }
    }

    // 编辑兼职弹窗
    VanPopup(show = showEditPart, onDismissRequest = { showEditPart = false }) {
        Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp)) {
            FText(if (editingPartId != null) "编辑兼职" else "添加兼职", 16f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            VanField(value = pCompany, onValueChange = { pCompany = it }, label = "公司名称")
            DateField("入职日期", pJoin) { pJoin = it }
            DateField("离职日期", pLeave) { pLeave = it }
            VanField(value = pPayDay, onValueChange = { pPayDay = it }, label = "发薪日(每月几号)", type = VanFieldType.Number)
            VanField(value = pHourly, onValueChange = { pHourly = it }, label = "时薪(元/小时)", type = VanFieldType.Number)
            Spacer(Modifier.height(12.dp))
            VanButton(text = "保存", type = VanButtonType.Primary, block = true, onClick = { submitPart() })
            Spacer(Modifier.height(8.dp))
        }
    }

    confirmStatus?.let { j ->
        VanConfirmDialog(
            show = true,
            title = "提示",
            message = if (j.status == "在职") "确定让「${j.company}」离职吗？" else "确定「${j.company}」复职吗？",
            confirmButtonText = "确定",
            onConfirm = { doToggleStatus() },
            onCancel = { confirmStatus = null },
            onClose = { confirmStatus = null },
        )
    }
    confirmDelete?.let { j ->
        VanConfirmDialog(
            show = true,
            title = "提示",
            message = "确定删除「${j.company}」的工作信息吗？",
            confirmButtonText = "删除",
            onConfirm = { doDelete() },
            onCancel = { confirmDelete = null },
            onClose = { confirmDelete = null },
        )
    }
}

@Composable
private fun SalaryRow(label: String, value: String, color: Color) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        FText(label, 13f, color = colors.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { FText(value, 14f, FontWeight.Medium, color) }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp)).background(colors.primaryLight)
            .clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { FText(text, 15f, FontWeight.Medium, colors.primary) }
}

@Composable
private fun DateField(label: String, value: String, onValueChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val init = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    VanField(value = value, onValueChange = {}, label = label, placeholder = "YYYY-MM-DD", readonly = true, onClick = { show = true })
    if (show) {
        VanPopup(show = true, onDismissRequest = { show = false }) {
            VanDatePicker(
                type = "date",
                value = init,
                onValueChange = {},
                title = label,
                onConfirm = { show = false; onValueChange(it.toString()) },
                onCancel = { show = false },
            )
        }
    }
}

private fun Double.fmt(): String = "%.2f".format(this)
