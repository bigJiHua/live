package com.live.finance.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
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
import com.live.finance.core.net.failMessage
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanBlue
import com.live.finance.theme.VanOrangeFixed
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.icon.VanIcon
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

// web `JobSetting.vue:588` `.job-card.formal { border-left: 4px solid var(--van-blue, #1989fa) }`
// （兼职工是 `--van-orange`）→ `--van-blue` 只在深色块，浅色 #1989fa → vanBlue()

/**
 * 工作信息设置（对齐 web `Work/JobSetting.vue`）。
 * 正式工作卡（信息网格 + 编辑）与兼职工作卡（信息网格 + 编辑/删除），配底部弹层表单。
 */
@Composable
fun JobListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val work = App.of(context).graph.work
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current

    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showEditFormal by remember { mutableStateOf(false) }
    var editingFormalId by remember { mutableStateOf<String?>(null) }
    var showEditPart by remember { mutableStateOf(false) }
    var editingPartId by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRejoin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Job?>(null) }

    var fCompany by remember { mutableStateOf("") }
    var fJoin by remember { mutableStateOf("") }
    var fLeave by remember { mutableStateOf("") }
    var fStatus by remember { mutableStateOf("在职") }
    var fPayDay by remember { mutableStateOf("10") }
    var fBase by remember { mutableStateOf("") }
    var fDays by remember { mutableStateOf("22") }
    var fMeal by remember { mutableStateOf("") }
    var fTraffic by remember { mutableStateOf("") }
    var fPost by remember { mutableStateOf("") }
    var fSocial by remember { mutableStateOf("") }
    var fFund by remember { mutableStateOf("") }
    var fTaxRate by remember { mutableStateOf("") }

    var pCompany by remember { mutableStateOf("") }
    var pJoin by remember { mutableStateOf("") }
    var pLeave by remember { mutableStateOf("") }
    var pStatus by remember { mutableStateOf("在职") }
    var pPayDay by remember { mutableStateOf("10") }
    var pHourly by remember { mutableStateOf("") }
    var pMeal by remember { mutableStateOf("") }
    var pTraffic by remember { mutableStateOf("") }
    var pPost by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            when (val res = work.jobList()) {
                is ApiResult.Ok -> jobs = res.data.orEmpty()
                // web `loadJobList` 的 catch 只 console.error，不提示
                else -> jobs = emptyList()
            }
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
        fStatus = j?.status?.ifBlank { "在职" } ?: "在职"
        fPayDay = (j?.payDay ?: 10).toString()
        fBase = j?.baseSalary?.numOrBlank() ?: ""
        fDays = (j?.baseWorkDays ?: 22).toString()
        fMeal = j?.subsidyMeal?.numOrBlank() ?: ""
        fTraffic = j?.subsidyTraffic?.numOrBlank() ?: ""
        fPost = j?.subsidyPost?.numOrBlank() ?: ""
        fSocial = j?.social?.numOrBlank() ?: ""
        fFund = j?.fund?.numOrBlank() ?: ""
        fTaxRate = j?.taxRate?.numOrBlank() ?: ""
        showEditFormal = true
    }

    fun openPart(id: String?) {
        editingPartId = id
        val j = id?.let { jobs.firstOrNull { it.id == id } }
        pCompany = j?.company ?: ""
        pJoin = j?.joinDate ?: ""
        pLeave = j?.leaveDate ?: ""
        pStatus = j?.status?.ifBlank { "在职" } ?: "在职"
        pPayDay = (j?.payDay ?: 10).toString()
        pHourly = j?.hourlyWage?.numOrBlank() ?: ""
        pMeal = j?.subsidyMeal?.numOrBlank() ?: ""
        pTraffic = j?.subsidyTraffic?.numOrBlank() ?: ""
        pPost = j?.subsidyPost?.numOrBlank() ?: ""
        showEditPart = true
    }

    fun formalBody(status: String): JsonObject = JsonObject().apply {
        addProperty("job_type", "formal")
        addProperty("company", fCompany.trim())
        addProperty("base_salary", fBase.toDoubleOrNull() ?: 0.0)
        addProperty("base_work_days", fDays.toIntOrNull() ?: 22)
        addProperty("subsidy_meal", fMeal.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_traffic", fTraffic.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_post", fPost.toDoubleOrNull() ?: 0.0)
        addProperty("social", fSocial.toDoubleOrNull() ?: 0.0)
        addProperty("fund", fFund.toDoubleOrNull() ?: 0.0)
        addProperty("tax_rate", fTaxRate.toDoubleOrNull() ?: 0.0)
        if (fJoin.isEmpty()) add("join_date", com.google.gson.JsonNull.INSTANCE) else addProperty("join_date", fJoin)
        if (fLeave.isEmpty()) add("leave_date", com.google.gson.JsonNull.INSTANCE) else addProperty("leave_date", fLeave)
        addProperty("status", status)
        addProperty("pay_day", fPayDay.toIntOrNull() ?: 10)
    }

    fun partBody(): JsonObject = JsonObject().apply {
        addProperty("job_type", "parttime")
        addProperty("company", pCompany.trim())
        addProperty("hourly_wage", pHourly.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_meal", pMeal.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_traffic", pTraffic.toDoubleOrNull() ?: 0.0)
        addProperty("subsidy_post", pPost.toDoubleOrNull() ?: 0.0)
        addProperty("join_date", pJoin)
        addProperty("leave_date", pLeave)
        addProperty("status", pStatus)
        addProperty("pay_day", pPayDay.toIntOrNull() ?: 10)
    }

    fun submitFormal(status: String = fStatus) {
        // web `saveFormal`：先校验再提交
        if (fCompany.isBlank()) return toast.show("请输入公司名称")
        if (fBase.isBlank()) return toast.show("请输入月基本工资")
        val body = formalBody(status)
        scope.launch {
            saving = true
            val r = if (editingFormalId != null) work.updateJob(editingFormalId!!, body) else work.saveJob(body)
            when (r) {
                is ApiResult.Ok -> { toast.show("保存成功", VanToastType.Success); showEditFormal = false; load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
            saving = false
        }
    }

    fun submitPart() {
        // web `saveParttime`：先校验再提交
        if (pCompany.isBlank()) return toast.show("请输入单位名称")
        if (pHourly.isBlank()) return toast.show("请输入时薪")
        val body = partBody()
        scope.launch {
            saving = true
            val r = if (editingPartId != null) work.updateJob(editingPartId!!, body) else work.saveJob(body)
            when (r) {
                is ApiResult.Ok -> { toast.show("保存成功", VanToastType.Success); showEditPart = false; load() }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
            saving = false
        }
    }

    fun doDelete(j: Job) {
        scope.launch {
            when (val r = work.deleteJob(j.id)) {
                is ApiResult.Ok -> { toast.show("删除成功", VanToastType.Success); load() }
                else -> toast.showFail(r, "删除失败")
            }
        }
    }

    fun isParttimeActive(j: Job): Boolean {
        if (j.joinDate.isBlank()) return false
        val today = LocalDate.now().toString()
        if (j.joinDate > today) return false
        if (j.leaveDate.isNotEmpty() && j.leaveDate < today) return false
        return true
    }

    fun formalDailyText(): String {
        val base = fBase.toDoubleOrNull() ?: 0.0
        val days = fDays.toDoubleOrNull() ?: 0.0
        if (base <= 0.0 || days <= 0.0) return "0.00"
        return String.format("%.2f", base / days)
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (loading && jobs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        FText("加载中…", 16f, color = colors.textTertiary)
                    }
                } else {
                    // ===== 正式工作 =====
                    SectionHeader(title = "正式工作") {
                        StatusTag(
                            text = if (formalJob?.status == "在职") "在职" else "已离职",
                            ok = formalJob?.status == "在职",
                        )
                    }
                    JobCard(accent = vanBlue(), enabled = formalJob?.status == "在职") {
                        val job = formalJob
                        if (job != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    InfoGridRow(
                                        "公司" to job.company.ifEmpty { "-" },
                                        "月工资" to "¥${job.baseSalary.numOrBlank().ifEmpty { "0" }}",
                                    )
                                    InfoGridRow(
                                        "出勤天数" to "${if (job.baseWorkDays == 0) 22 else job.baseWorkDays}天",
                                        "日薪" to "¥${jobFormalDaily(job)}",
                                    )
                                    InfoGridRow(
                                        "入职日期" to job.joinDate.ifEmpty { "-" },
                                        if (job.status != "在职") "离职日期" to job.leaveDate.ifEmpty { "-" } else null,
                                    )
                                }
                                AppButton(
                                    "编辑", onClick = { openFormal(job.id) },
                                    size = AppButtonSize.Small, type = AppButtonType.Primary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                VanEmpty(description = "暂无正式工作")
                                Spacer(Modifier.height(8.dp))
                                AppButton("创建正式工作", onClick = { openFormal(null) }, size = AppButtonSize.Small, type = AppButtonType.Primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ===== 兼职工作 =====
                    SectionHeader(title = "兼职工作") {
                        AppButton("+ 添加兼职", onClick = { openPart(null) }, type = AppButtonType.Primary, plain = true, size = AppButtonSize.Small)
                    }
                    if (parttimeJobs.isEmpty()) {
                        JobCard(accent = null, enabled = true) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                VanEmpty(description = "暂无兼职工作")
                            }
                        }
                    } else {
                        parttimeJobs.forEach { j ->
                            JobCard(accent = VanOrangeFixed, enabled = isParttimeActive(j)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        InfoGridRow(
                                            "单位" to j.company.ifEmpty { "-" },
                                            "时薪" to "¥${j.hourlyWage.numOrBlank().ifEmpty { "0" }}/小时",
                                        )
                                        InfoGridRow(
                                            "开始日期" to j.joinDate.ifEmpty { "-" },
                                            "结束日期" to j.leaveDate.ifEmpty { "长期" },
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            InfoCell("状态", Modifier.weight(1f)) {
                                                StatusTag(
                                                    text = if (isParttimeActive(j)) "在职" else "已结束",
                                                    ok = isParttimeActive(j),
                                                    warning = !isParttimeActive(j),
                                                    size = VanTagSize.Small,   // web `size="small"`
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                    Column(
                                        Modifier.padding(start = 8.dp),
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        AppButton("编辑", onClick = { openPart(j.id) }, size = AppButtonSize.Small)
                                        AppButton(
                                            "删除", onClick = { confirmDelete = j },
                                            size = AppButtonSize.Small, type = AppButtonType.Danger, plain = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ===== 正式工作编辑弹层 =====
    if (showEditFormal) {
        VanPopup(show = true, onDismissRequest = { showEditFormal = false }, position = com.live.vant.feedback.VanPopupPosition.Bottom, round = true) {
            Column(
                Modifier.fillMaxWidth().background(colors.bgCard)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(
                        if (editingFormalId != null) "编辑正式工作" else "创建正式工作",
                        18f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f),
                    )
                    VanIcon("cross", size = 20.sp, color = colors.textTertiary, onClick = { showEditFormal = false })
                }
                AppField(fCompany, { fCompany = it }, label = "公司名称", placeholder = "请输入公司名称")
                AppField(fBase, { fBase = it }, label = "月基本工资", placeholder = "请输入月工资", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(fDays, { fDays = it }, label = "月应出勤天数", placeholder = "默认22天", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField("¥${formalDailyText()}", {}, label = "日薪（自动）", disabled = true)

                GroupTitle("补贴（元/月）")
                AppField(fMeal, { fMeal = it }, label = "餐补", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(fTraffic, { fTraffic = it }, label = "交通补贴", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(fPost, { fPost = it }, label = "岗位补贴", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

                GroupTitle("扣除（元/月）")
                AppField(fSocial, { fSocial = it }, label = "个人社保", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(fFund, { fFund = it }, label = "个人公积金", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(fTaxRate, { fTaxRate = it }, label = "个税税率", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    trailing = { FText("%", 16f, color = colors.textTertiary) })

                DateField("入职日期", fJoin, "请选择") { fJoin = it }
                if (formalJob != null && formalJob.status == "离职") {
                    Spacer(Modifier.height(10.dp))
                    AppButton("重新入职", onClick = { confirmRejoin = true }, type = AppButtonType.Primary, block = true, round = true)
                }
                if (formalJob != null && formalJob.status == "在职") {
                    DateField("离职日期", fLeave, "选择离职日期") { fLeave = it }
                    Spacer(Modifier.height(10.dp))
                    AppButton("登记离职", onClick = { confirmLeave = true }, type = AppButtonType.Danger, block = true, round = true)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("取消", { showEditFormal = false }, size = AppButtonSize.Large, round = true, block = true, modifier = Modifier.weight(1f))
                    AppButton("保存", { submitFormal() }, size = AppButtonSize.Large, type = AppButtonType.Primary, round = true, block = true, loading = saving, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ===== 兼职编辑弹层 =====
    if (showEditPart) {
        VanPopup(show = true, onDismissRequest = { showEditPart = false }, position = com.live.vant.feedback.VanPopupPosition.Bottom, round = true) {
            Column(
                Modifier.fillMaxWidth().background(colors.bgCard)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(
                        if (editingPartId != null) "编辑兼职" else "添加兼职",
                        18f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f),
                    )
                    VanIcon("cross", size = 20.sp, color = colors.textTertiary, onClick = { showEditPart = false })
                }
                AppField(pCompany, { pCompany = it }, label = "单位名称", placeholder = "请输入单位名称")
                AppField(pHourly, { pHourly = it }, label = "时薪", placeholder = "元/小时", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

                GroupTitle("补贴（元/天）")
                AppField(pMeal, { pMeal = it }, label = "餐补", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(pTraffic, { pTraffic = it }, label = "交通补贴", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                AppField(pPost, { pPost = it }, label = "岗位补贴", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

                DateField("开始日期", pJoin, "请选择") { pJoin = it }
                DateField("结束日期", pLeave, "不填表示长期") { pLeave = it }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("取消", { showEditPart = false }, size = AppButtonSize.Large, round = true, block = true, modifier = Modifier.weight(1f))
                    AppButton("保存", { submitPart() }, size = AppButtonSize.Large, type = AppButtonType.Primary, round = true, block = true, loading = saving, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmLeave) {
        VanConfirmDialog(
            show = true,
            title = "确认离职",
            message = "确定要登记离职吗？",
            onConfirm = {
                confirmLeave = false
                if (fLeave.isBlank()) fLeave = LocalDate.now().toString()
                fStatus = "离职"
                submitFormal("离职")
            },
            onCancel = { confirmLeave = false },
            onClose = { confirmLeave = false },
        )
    }
    if (confirmRejoin) {
        VanConfirmDialog(
            show = true,
            title = "确认重新入职",
            message = "确定要重新入职吗？",
            onConfirm = {
                confirmRejoin = false
                fLeave = ""
                fStatus = "在职"
                submitFormal("在职")
            },
            onCancel = { confirmRejoin = false },
            onClose = { confirmRejoin = false },
        )
    }
    confirmDelete?.let { j ->
        VanConfirmDialog(
            show = true,
            title = "确认删除",
            message = "确定要删除这条兼职记录吗？",
            confirmButtonText = "确认",
            onConfirm = { confirmDelete = null; doDelete(j) },
            onCancel = { confirmDelete = null },
            onClose = { confirmDelete = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 18f, FontWeight.SemiBold, LocalAppColors.current.textPrimary, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun JobCard(accent: Color?, enabled: Boolean, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
            .alpha(if (enabled) 1f else 0.6f),
    ) {
        if (accent != null) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        }
        Box(Modifier.weight(1f).padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoCell(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        value()
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier) {
    val colors = LocalAppColors.current
    InfoCell(label, modifier) { FText(value, 16f, FontWeight.Medium, colors.textPrimary) }
}

@Composable
private fun InfoGridRow(left: Pair<String, String>, right: Pair<String, String>?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCell(left.first, left.second, Modifier.weight(1f))
        if (right != null) InfoCell(right.first, right.second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/**
 * 在职 / 已离职 / 已结束 状态标签。
 *
 * web `JobSetting.vue:7-9 / 188-190` 用的是 `<app-tag :type="success|danger|warning">`，
 * 而 `AppTag.vue` 的 `--success/--warning/--danger` 是 **白字 + 实心 `--theme-*` 底**
 * （不是「浅底彩字」）。原实现自己画了个浅底彩字的 Box，结构与 web 不符 → 改回 [VanTag]。
 */
@Composable
private fun StatusTag(text: String, ok: Boolean, warning: Boolean = false, size: VanTagSize = VanTagSize.Medium) {
    VanTag(
        text = text,
        type = if (ok) VanTagType.Success else if (warning) VanTagType.Warning else VanTagType.Danger,
        size = size,
    )
}

@Composable
private fun GroupTitle(text: String) {
    val colors = LocalAppColors.current
    FText(text, 14f, FontWeight.Normal, colors.textSecondary, Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun DateField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val init = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
    Box(Modifier.clickable { show = true }) {
        AppField(value, {}, label = label, placeholder = placeholder, disabled = true)
    }
    if (show) {
        VanPopup(show = true, onDismissRequest = { show = false }) {
            // ⚠ `VanDatePicker` 是**受控**组件：空的 `onValueChange` 会让滚轮状态写不回（同 `RepayAddScreen` 的 BUG）。
            var pendingDate by remember { mutableStateOf(init) }
            VanDatePicker(
                type = "date",
                value = pendingDate,
                onValueChange = { pendingDate = it },
                title = "选择日期",
                onConfirm = { show = false; onValueChange(it.toString()) },
                onCancel = { show = false },
            )
        }
    }
}

/** 展示用：整数不带小数，其余保留原值。 */
private fun Double.numOrBlank(): String = if (this == 0.0) "" else trimNum(this)

private fun trimNum(v: Double): String {
    if (v == v.toLong().toDouble()) return v.toLong().toString()
    return v.toString()
}

private fun jobFormalDaily(job: Job): String {
    val days = if (job.baseWorkDays == 0) 22 else job.baseWorkDays
    if (job.baseSalary <= 0.0 || days <= 0) return "0.00"
    return String.format("%.2f", job.baseSalary / days)
}