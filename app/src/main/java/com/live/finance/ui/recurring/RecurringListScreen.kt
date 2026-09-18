package com.live.finance.ui.recurring

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.Recurring
import com.live.finance.data.model.RecurringSummary
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanAction
import com.live.vant.feedback.VanActionSheet
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanStepper
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

private fun thisMonthKey(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}
private fun parseMonth(key: String): Pair<Int, Int> {
    val p = key.split("-")
    return (p.getOrNull(0)?.toIntOrNull() ?: 2026) to (p.getOrNull(1)?.toIntOrNull() ?: 9)
}
private fun shiftMonth(key: String, delta: Int): String {
    var (y, m) = parseMonth(key)
    m += delta
    while (m < 1) { m += 12; y-- }
    while (m > 12) { m -= 12; y++ }
    return "%04d-%02d".format(y, m)
}

/**
 * 固定事件 / 固定支出（web views/Finance/events/Index.vue 与 recurring/List.vue 共用本屏）。
 * - mode="event"：固定事件 —— 列出全部（排除分期），卡片可展开看本期明细、启停、编辑、删除。
 * - mode="recurring"：固定支出 —— 按选中月份过滤，支持入账/跳过/改额/编辑。
 */
@Composable
fun RecurringListScreen(nav: NavHostController, mode: String = "event") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.recurring
    val catRepo = App.of(ctx).graph.category
    val cardRepo = App.of(ctx).graph.card
    val scope = rememberCoroutineScope()

    val isEvent = mode != "recurring"
    val title = if (isEvent) "固定事件" else "固定支出"

    var month by remember { mutableStateOf(thisMonthKey()) }
    var rows by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var summary by remember { mutableStateOf<RecurringSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recurring?>(null) }

    // 表单状态
    var fName by remember { mutableStateOf("") }
    var fAmount by remember { mutableStateOf("") }
    var fCycle by remember { mutableStateOf("month") }
    var fDay by remember { mutableStateOf(1) }
    var fMonth by remember { mutableStateOf(1) }
    var fCatId by remember { mutableStateOf("") }
    var fCatName by remember { mutableStateOf("") }
    var fAccId by remember { mutableStateOf("") }
    var fAccName by remember { mutableStateOf("") }
    var fRemark by remember { mutableStateOf("") }
    var fEnd by remember { mutableStateOf("") }
    var fActive by remember { mutableStateOf(true) }
    var showCat by remember { mutableStateOf(false) }
    var showAcc by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showAmountEdit by remember { mutableStateOf<String?>(null) } // 固定支出改额：行 id
    var amountEdit by remember { mutableStateOf("") }

    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }

    fun reload() {
        scope.launch {
            val listRes = if (isEvent) repo.list(excludeInstallment = true) else repo.list(month = month)
            rows = when (listRes) { is ApiResult.Ok -> listRes.data ?: emptyList(); else -> emptyList() }
            summary = when (val s = repo.summary(month)) { is ApiResult.Ok -> s.data; else -> null }
            loading = false
        }
    }
    fun openEdit(item: Recurring) {
        fName = item.name; fAmount = if (item.amount == 0.0) "" else item.amount.toString()
        fCycle = if (item.cycle in listOf("year", "yearly")) "year" else "month"
        fDay = item.dayOfCycle.coerceAtLeast(1); fMonth = item.monthOfCycle.coerceAtLeast(1)
        fCatId = item.categoryId; fCatName = item.categoryName
        fAccId = item.accountId; fAccName = item.accountName
        fRemark = item.remark; fEnd = item.endDate; fActive = item.isActive
        editing = item; showForm = true
    }
    fun save() {
        if (fName.isBlank()) { toast.show("请输入名称"); return }
        val amt = fAmount.toDoubleOrNull() ?: 0.0
        if (amt <= 0) { toast.show("金额需大于 0"); return }
        val body = JsonObject().apply {
            addProperty("name", fName)
            addProperty("amount", amt)
            addProperty("cycle", fCycle)
            if (fCycle == "month") addProperty("day_of_cycle", fDay) else addProperty("month_of_cycle", fMonth)
            addProperty("category_id", fCatId); addProperty("category_name", fCatName)
            addProperty("account_id", fAccId); addProperty("account_name", fAccName)
            if (fRemark.isNotEmpty()) addProperty("remark", fRemark)
            if (fEnd.isNotEmpty()) addProperty("end_date", fEnd)
            addProperty("is_active", fActive)
        }
        scope.launch {
            val res = if (editing != null) repo.update(editing!!.id, body) else repo.create(body)
            when (res) {
                is ApiResult.Ok -> { toast.show(if (editing != null) "已更新" else "已保存"); showForm = false; editing = null; reload() }
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show((res as? ApiResult.Fail)?.message ?: "操作失败")
            }
        }
    }
    fun del(item: Recurring) {
        scope.launch {
            when (val r = repo.delete(item.id)) {
                is ApiResult.Ok -> { toast.show("已删除"); reload() }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show((r as? ApiResult.Fail)?.message ?: "操作失败")
            }
        }
    }
    fun setMonthStatus(item: Recurring, status: String, amount: Double? = null) {
        scope.launch {
            when (val r = repo.updateMonthStatus(item.id, month, status, amount)) {
                is ApiResult.Ok -> { toast.show(if (status == "entered") "已入账" else if (status == "skipped") "已跳过" else "已更新"); reload() }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show((r as? ApiResult.Fail)?.message ?: "操作失败")
            }
        }
    }
    fun toggleActive(item: Recurring) {
        val body = JsonObject().apply { addProperty("is_active", !item.isActive) }
        scope.launch { repo.update(item.id, body); reload() }
    }

    LaunchedEffect(month, mode) { reload() }
    LaunchedEffect(Unit) {
        scope.launch {
            cats = (catRepo.list("expense") as? ApiResult.Ok)?.data ?: emptyList()
            cards = (cardRepo.list() as? ApiResult.Ok)?.data ?: emptyList()
        }
    }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner.fillMaxSize()) {
            // 标题 + 月份切换
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(title, 18f, FontWeight.Bold, colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VanIcon("arrow-left", size = 18.sp, color = colors.textSecondary, modifier = Modifier.clickable { month = shiftMonth(month, -1) }.padding(6.dp))
                        FText("${parseMonth(month).first}年${parseMonth(month).second}月", 14f, FontWeight.Medium, colors.textPrimary, Modifier.padding(horizontal = 8.dp))
                        VanIcon("arrow-right", size = 18.sp, color = colors.textSecondary, modifier = Modifier.clickable { month = shiftMonth(month, 1) }.padding(6.dp))
                        FText("今天", 12f, color = colors.primary, modifier = Modifier.clickable { month = thisMonthKey() }.padding(start = 8.dp))
                    }
                }
            }
            // 汇总卡
            item {
                val s = summary
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(colors.bgCard, RoundedCornerShape(12.dp)).padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("本月合计", s?.totalAmount ?: 0.0, colors.success)
                        Stat("生效中", (s?.total ?: 0).toDouble(), colors.textPrimary)
                        Stat("待处理", (s?.pending ?: 0).toDouble(), colors.warning)
                    }
                    if (!s?.categoryStats.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        s!!.categoryStats.take(5).forEach { cs ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText(cs.categoryName.ifEmpty { "未分类" }, 12f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                                FText(Money.format(cs.amount), 12f, color = colors.textPrimary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // 新增按钮（固定事件）
            if (isEvent) item {
                AppButton(
                    text = "新增固定事件", type = AppButtonType.Primary, block = true, round = true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = {
                        editing = null; fName = ""; fAmount = ""; fCycle = "month"; fDay = 1; fMonth = 1
                        fCatId = ""; fCatName = ""; fAccId = ""; fAccName = ""; fRemark = ""; fEnd = ""; fActive = true
                        showForm = true
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
            // 列表
            if (loading) item { FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp)) }
            if (!loading && rows.isEmpty()) item { FText("暂无数据", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp)) }
            items(rows) { r ->
                val mr = r.monthRecords[month]
                val expanded = expandedId == r.id
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(colors.bgCard, RoundedCornerShape(12.dp)).clickable { expandedId = if (expanded) null else r.id },
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            FText(r.name, 15f, FontWeight.Medium, colors.textPrimary)
                            FText("${r.categoryName} · ${r.accountLabel} · ${r.cycleLabel}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (!r.isActive) FText("已停用", 11f, color = colors.textTertiary, modifier = Modifier.padding(end = 6.dp))
                        StatusChip(r.monthStatus.ifEmpty { mr?.status ?: "" })
                        FText(Money.format(r.amount), 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(start = 10.dp))
                    }
                    if (expanded) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            // 本期明细
                            val plan = mr?.planDate ?: (if (r.cycle == "month") "$month-${r.dayOfCycle.toString().padStart(2, '0')}" else "$month-01")
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText("计划日 $plan", 12f, color = colors.textSecondary)
                                Spacer(Modifier.weight(1f))
                                val sc = when (mr?.status) {
                                    "entered", "done" -> colors.success
                                    "skipped" -> colors.textTertiary
                                    "pending" -> colors.warning
                                    else -> colors.textTertiary
                                }
                                FText("状态：${statusText(mr?.status ?: "")}", 12f, color = sc)
                            }
                            if (mr != null && mr.actualAmount != 0.0) FText("实际：${Money.format(mr.actualAmount)}", 12f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FText(if (r.isActive) "已启用" else "已停用", 12f, color = colors.textSecondary)
                                VanSwitch(checked = r.isActive, onCheckedChange = { toggleActive(r) }, modifier = Modifier.padding(start = 8.dp))
                                Spacer(Modifier.weight(1f))
                                AppButton(text = "编辑", type = AppButtonType.Text, size = AppButtonSize.Small, onClick = { openEdit(r) })
                                AppButton(text = "删除", type = AppButtonType.Danger, plain = true, size = AppButtonSize.Small, onClick = { del(r) })
                            }
                            // 固定支出模式：月状态操作
                            if (!isEvent) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val st = r.monthStatus.ifEmpty { mr?.status ?: "" }
                                    if (st != "entered") AppButton(text = "标记入账", type = AppButtonType.Success, size = AppButtonSize.Small, onClick = { setMonthStatus(r, "entered") })
                                    if (st != "skipped") AppButton(text = "跳过", type = AppButtonType.Default, size = AppButtonSize.Small, onClick = { setMonthStatus(r, "skipped") })
                                    AppButton(text = "改额", type = AppButtonType.Text, size = AppButtonSize.Small, onClick = { showAmountEdit = r.id; amountEdit = (r.monthAmount.takeIf { it != 0.0 } ?: r.amount).toString() })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // —— 新增/编辑表单 ——
    VanPopup(show = showForm, onDismissRequest = { showForm = false }, position = VanPopupPosition.Bottom, round = true) {
        Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
            FText(if (editing != null) "编辑${title}" else "新增$title", 16f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
            AppField(fName, { fName = it }, label = "名称", placeholder = "如 房租 / 宽带")
            AppField(fAmount, { fAmount = it }, label = "金额", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            // 周期
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FText("周期", 13f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Chip(fCycle == "month", "每月") { fCycle = "month" }
                Spacer(Modifier.padding(8.dp))
                Chip(fCycle == "year", "每年") { fCycle = "year" }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(if (fCycle == "month") "每月几号" else "每年几月", 13f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                VanStepper(value = if (fCycle == "month") fDay else fMonth, onValueChange = { if (fCycle == "month") fDay = it else fMonth = it }, min = 1, max = if (fCycle == "month") 31 else 12)
            }
            // 分类
            PickerRow("分类", fCatName.ifEmpty { "请选择" }) { showCat = true }
            // 账户
            PickerRow("账户", fAccName.ifEmpty { "请选择" }) { showAcc = true }
            AppField(fRemark, { fRemark = it }, label = "备注", placeholder = "选填")
            // 结束日期
            PickerRow("结束日期", fEnd.ifEmpty { "不结束" }) { showDate = true }
            // 启用
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FText("启用", 13f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                VanSwitch(checked = fActive, onCheckedChange = { fActive = it })
            }
            // 分析预览（日均）
            val amt = fAmount.toDoubleOrNull() ?: 0.0
            if (amt > 0) {
                val daily = amt / (if (fCycle == "month") 30.0 else 365.0)
                FText("分析：日均约 ${Money.format(daily)} 元", 12f, color = colors.textTertiary, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            AppButton(text = "保存", type = AppButtonType.Primary, block = true, round = true, onClick = { save() })
        }
    }

    // 分类选择
    VanActionSheet(
        show = showCat, onDismissRequest = { showCat = false }, title = "选择分类",
        cancelText = "取消", closeOnClickAction = true,
        actions = cats.map { VanAction(name = it.name) },
        onSelect = { _, i -> cats.getOrNull(i)?.let { fCatId = it.id; fCatName = it.name } },
    )
    // 账户选择
    VanActionSheet(
        show = showAcc, onDismissRequest = { showAcc = false }, title = "选择账户",
        cancelText = "取消", closeOnClickAction = true,
        actions = cards.map { VanAction(name = "${it.alias.ifEmpty { it.bankName }} ${if (it.last4.isNotEmpty()) "(" + it.last4 + ")" else ""}") },
        onSelect = { _, i -> cards.getOrNull(i)?.let { fAccId = it.id; fAccName = it.alias.ifEmpty { it.bankName } } },
    )
    // 结束日期
    if (showDate) {
        VanDatePicker(
            type = "date",
            value = runCatching { LocalDate.parse(fEnd) }.getOrNull() ?: LocalDate.now(),
            onValueChange = {},
            title = "结束日期",
            onConfirm = { showDate = false; fEnd = it.toString() },
            onCancel = { showDate = false },
        )
    }
    // 固定支出改额
    VanPopup(show = showAmountEdit != null, onDismissRequest = { showAmountEdit = null }, position = VanPopupPosition.Center, round = true) {
        Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
            FText("修改本期金额", 15f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
            AppField(amountEdit, { amountEdit = it }, label = "金额", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "取消", type = AppButtonType.Default, size = AppButtonSize.Small, onClick = { showAmountEdit = null })
                AppButton(text = "保存", type = AppButtonType.Primary, size = AppButtonSize.Small, onClick = {
                    val id = showAmountEdit!!; val a = amountEdit.toDoubleOrNull() ?: 0.0
                    showAmountEdit = null; setMonthStatus(rows.first { it.id == id }, "entered", a)
                })
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FText(label, 11f, color = colors.textTertiary)
        FText(if (label == "本月合计") Money.format(value) else value.toInt().toString(), 18f, FontWeight.Bold, color, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatusChip(status: String) {
    val colors = LocalAppColors.current
    val (text, bg) = when (status) {
        "entered", "done" -> "已入账" to colors.success.copy(alpha = 0.12f)
        "skipped" -> "已跳过" to colors.textTertiary.copy(alpha = 0.18f)
        "pending" -> "待处理" to colors.warning.copy(alpha = 0.12f)
        else -> return
    }
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        FText(text, 10f, color = if (status == "skipped") colors.textTertiary else if (status == "pending") colors.warning else colors.success)
    }
}

@Composable
private fun Chip(selected: Boolean, text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val c = com.live.vant.theme.LocalVantColors.current
    Box(
        Modifier.background(if (selected) c.primary.copy(alpha = 0.12f) else colors.bgThird, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        FText(text, 13f, FontWeight.Medium, if (selected) c.primary else colors.textSecondary)
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 13f, color = colors.textSecondary, modifier = Modifier.weight(1f))
        FText(value, 14f, color = if (value.startsWith("请选择") || value == "不结束") colors.textTertiary else colors.textPrimary)
        VanIcon("arrow", size = 14.sp, color = colors.textTertiary, modifier = Modifier.padding(start = 6.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

private fun statusText(s: String) = when (s) {
    "entered", "done" -> "已入账"
    "skipped" -> "已跳过"
    "pending" -> "待处理"
    else -> "未设置"
}
