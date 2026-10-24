package com.live.finance.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Category
import com.live.finance.data.model.Recurring
import com.live.finance.data.model.RecurringSummary
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.VanPurpleFixed
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanDivider
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.form.VanRadio
import com.live.vant.form.VanRadioGroup
import com.live.vant.form.VanStepper
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/**
 * 固定事件 / 固定支出。
 *
 * web 真相源是**两个结构完全不同的页面**，因此本文件拆成两块独立实现（此前原生用一份代码 + mode 混写，
 * 导致两页的 UI 与文案都跟 web 对不上）：
 *  - `mode = "event"`     → [FixedEventScreen]，对应 `web/src/views/Finance/events/Index.vue`
 *  - `mode = "recurring"` → [FixedExpenseScreen]，对应 `web/src/views/Finance/recurring/List.vue`
 */
@Composable
fun RecurringListScreen(nav: NavHostController, mode: String = "event") {
    if (mode == "recurring") FixedExpenseScreen() else FixedEventScreen(nav)
}

/** `--van-purple` 项目覆写值。 */

/** web `formatAmount = Number(v||0).toFixed(2)`：两位小数、**无千分位**、不四舍五入补零。 */
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", if (v.isFinite()) v else 0.0)

private fun thisMonthKey(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

private fun nextMonthKey(): String {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, 1)
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

private fun parseMonth(key: String): Pair<Int, Int> {
    val p = key.split("-")
    return (p.getOrNull(0)?.toIntOrNull() ?: 2026) to (p.getOrNull(1)?.toIntOrNull() ?: 9)
}

private fun monthTitleOf(key: String): String {
    val (y, m) = parseMonth(key)
    return "${y}年${m}月"
}

private fun shiftMonth(key: String, delta: Int): String {
    var (y, m) = parseMonth(key)
    m += delta
    while (m < 1) { m += 12; y-- }
    while (m > 12) { m -= 12; y++ }
    return "%04d-%02d".format(y, m)
}

/** web `app-field` 的「标签在上、控件在下」容器（用于 switch / stepper / radio 这类非输入控件）。 */
@Composable
private fun FieldBlock(label: String? = null, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (!label.isNullOrEmpty()) {
                FText(label, 14f, FontWeight.Normal, colors.textSecondary)
                Spacer(Modifier.height(6.dp))
            }
            content()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** 只读链接式字段（web `readonly is-link`，右侧箭头 + 点击唤起选择器）。 */
@Composable
private fun PickerField(label: String, value: String, placeholder: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() },
    ) {
        AppField(
            value = value,
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            trailing = { VanIcon("arrow", size = 14.sp, color = colors.textTertiary) },
        )
    }
}

/** web `.section-title`（13/600/tetriary，左内缩 4）。 */
@Composable
private fun SectionTitle(text: String) {
    val colors = LocalAppColors.current
    FText(text, 14f, FontWeight.SemiBold, colors.textTertiary, Modifier.padding(start = 20.dp, bottom = 8.dp))
}
/** 等价 JS `String(number)`：整数不带 `.0`（web 编辑表单回填用的是 `String(item.amount)`）。 */
private fun numStr(v: Double): String {
    if (!v.isFinite()) return "0"
    return if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
}

/** 月度处理记录一行（web `getMonthRecords` 的结果项）。 */
private data class MonthRecRow(val month: String, val status: String, val amount: Double)

/**
 * web `getMonthRecords(item)`：合并 month_records 的键 + 本月 + 下月 → 升序排序 → 倒序 → 取前 14 条。
 */
private fun monthRecRows(item: Recurring): List<MonthRecRow> {
    val all = (item.monthRecords.keys + thisMonthKey() + nextMonthKey()).toSortedSet().toList().reversed().take(14)
    return all.map { m ->
        val rec = item.monthRecords[m]
        MonthRecRow(
            month = m,
            status = rec?.status?.ifEmpty { "pending" } ?: "pending",
            amount = rec?.amount ?: item.amount,
        )
    }
}

/** 处理状态 tag 文案（web 三态）。 */
private fun statusTextOf(status: String): String = when (status) {
    "done" -> "已处理"
    "skipped" -> "已跳过"
    else -> "待处理"
}

private fun statusTagType(status: String): VanTagType = when (status) {
    "done" -> VanTagType.Success
    "skipped" -> VanTagType.Danger
    else -> VanTagType.Warning
}

// ============================================================================================
// 固定事件（web src/views/Finance/events/Index.vue）
// ============================================================================================

@Composable
private fun FixedEventScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.recurring
    val catRepo = App.of(ctx).graph.category
    val scope = rememberCoroutineScope()

    var list by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var expandedId by remember { mutableStateOf("") }
    var savingId by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Recurring?>(null) }

    // 展开卡片内的编辑表单（对应 web editForm）
    var eName by remember { mutableStateOf("") }
    var eAmount by remember { mutableStateOf("") }
    var eDay by remember { mutableStateOf(1) }
    var eMonth by remember { mutableStateOf(1) }
    var eEnd by remember { mutableStateOf("") }
    var eRemark by remember { mutableStateOf("") }

    var showEndPicker by remember { mutableStateOf(false) }
    var endPickerValue by remember { mutableStateOf(LocalDate.now()) }

    // 新增表单
    var showAdd by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var aName by remember { mutableStateOf("") }
    var aAmount by remember { mutableStateOf("") }
    var aIsExpense by remember { mutableStateOf(true) }
    var aCycle by remember { mutableStateOf("month") }
    var aDay by remember { mutableStateOf(1) }
    var aMonth by remember { mutableStateOf(1) }
    var aEnd by remember { mutableStateOf("") }
    var aCatId by remember { mutableStateOf("") }
    var aCatLabel by remember { mutableStateOf("") }
    var aRemark by remember { mutableStateOf("") }
    var aRemindDays by remember { mutableStateOf(0) }
    var aActive by remember { mutableStateOf(true) }
    var showAddCat by remember { mutableStateOf(false) }
    var showAddEnd by remember { mutableStateOf(false) }
    var addEndPickerValue by remember { mutableStateOf(LocalDate.now()) }

    val minDate = remember { LocalDate.of(2020, 1, 1) }

    fun loadData() {
        scope.launch {
            loading = true
            val l = repo.list(excludeInstallment = true)
            when (l) {
                is ApiResult.Ok -> list = l.data ?: emptyList()
                is ApiResult.Fail -> toast.show(l.message.ifEmpty { "加载失败" })
                else -> {}
            }
            categories = (catRepo.list("expense") as? ApiResult.Ok)?.data ?: emptyList()
            loading = false
        }
    }

    fun fillEdit(item: Recurring) {
        eName = item.name
        eAmount = if (item.amount > 0) numStr(item.amount) else ""
        eDay = if (item.dayOfCycle > 0) item.dayOfCycle else 1
        eMonth = if (item.monthOfCycle > 0) item.monthOfCycle else 1
        eEnd = item.endDate
        eRemark = item.remark
    }

    fun toggleExpand(item: Recurring) {
        if (expandedId == item.id) { expandedId = ""; return }
        expandedId = item.id
        fillEdit(item)
    }

    fun toggleActive(item: Recurring, active: Boolean) {
        scope.launch {
            val body = JsonObject().apply { addProperty("is_active", if (active) 1 else 0) }
            when (val r = repo.update(item.id, body)) {
                is ApiResult.Ok -> {
                    list = list.map { if (it.id == item.id) it.copy(isActive = active) else it }
                    toast.show(if (active) "已启用" else "已停用", com.live.vant.feedback.VanToastType.Success)
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "操作失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "操作失败")
            }
        }
    }

    fun toggleMonthStatus(item: Recurring, m: MonthRecRow) {
        val next = if (m.status == "done") "pending" else "done"
        scope.launch {
            when (val r = repo.updateMonthStatus(item.id, m.month, next)) {
                is ApiResult.Ok -> {
                    toast.show("${m.month} 已更新", com.live.vant.feedback.VanToastType.Success)
                    loadData()
                    // 保持展开 + 回填刷新后的 editForm（web 同款处理）
                    repo.list(excludeInstallment = true).let { res ->
                        (res as? ApiResult.Ok)?.data?.firstOrNull { it.id == item.id }?.let { fillEdit(it) }
                    }
                    expandedId = item.id
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "操作失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "操作失败")
            }
        }
    }

    fun handleSave(item: Recurring) {
        savingId = item.id
        val body = JsonObject().apply {
            addProperty("name", eName.trim())
            addProperty("amount", if (item.amount > 0) (eAmount.toDoubleOrNull() ?: 0.0) else 0.0)
            addProperty("day_of_cycle", eDay)
            if (item.cycle == "year") addProperty("month_of_cycle", eMonth) else add("month_of_cycle", JsonNull.INSTANCE)
            if (eEnd.trim().isEmpty()) add("end_date", JsonNull.INSTANCE) else addProperty("end_date", eEnd.trim())
            addProperty("remark", eRemark.trim())
        }
        scope.launch {
            when (val r = repo.update(item.id, body)) {
                is ApiResult.Ok -> {
                    toast.show("保存成功", com.live.vant.feedback.VanToastType.Success)
                    loadData()
                    expandedId = ""
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
            savingId = ""
        }
    }

    fun doDelete(item: Recurring) {
        scope.launch {
            when (val r = repo.delete(item.id)) {
                is ApiResult.Ok -> {
                    toast.show("已删除", com.live.vant.feedback.VanToastType.Success)
                    expandedId = ""
                    loadData()
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "删除失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "删除失败")
            }
        }
    }

    fun openAdd() {
        aName = ""; aAmount = ""; aIsExpense = true; aCycle = "month"
        aDay = 1; aMonth = 1; aEnd = ""; aCatId = ""; aCatLabel = ""
        aRemark = ""; aRemindDays = 0; aActive = true
        showAdd = true
    }

    fun handleAdd() {
        if (aName.trim().isEmpty()) { toast.show("请输入名称"); return }
        if (aIsExpense && (aAmount.isEmpty() || (aAmount.toDoubleOrNull() ?: 0.0) <= 0.0)) { toast.show("请输入金额"); return }
        adding = true
        val body = JsonObject().apply {
            addProperty("name", aName.trim())
            addProperty("amount", if (aIsExpense) (aAmount.toDoubleOrNull() ?: 0.0) else 0.0)
            if (aIsExpense && aCatId.isNotEmpty()) addProperty("category_id", aCatId) else add("category_id", JsonNull.INSTANCE)
            addProperty("cycle", aCycle)
            addProperty("day_of_cycle", aDay)
            if (aCycle == "year") addProperty("month_of_cycle", aMonth) else add("month_of_cycle", JsonNull.INSTANCE)
            if (aEnd.trim().isEmpty()) add("end_date", JsonNull.INSTANCE) else addProperty("end_date", aEnd.trim())
            addProperty("remind_days", aRemindDays)
            addProperty("remark", aRemark.trim())
            addProperty("is_active", if (aActive) 1 else 0)
        }
        scope.launch {
            when (val r = repo.create(body)) {
                is ApiResult.Ok -> {
                    toast.show("创建成功", com.live.vant.feedback.VanToastType.Success)
                    showAdd = false
                    loadData()
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "创建失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "创建失败")
            }
            adding = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val monthly = list.filter { it.cycle != "year" }
    val yearly = list.filter { it.cycle == "year" }
    val expenseCount = list.count { it.amount > 0 }
    val eventCount = list.size - expenseCount

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                            .padding(20.dp),
                    ) {
                        FText("固定事件", 20f, FontWeight.Bold, Color.White)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FText("${list.size} 项总计", 14f, FontWeight.Normal, Color.White.copy(alpha = 0.85f))
                            FText("$expenseCount 项支出 · $eventCount 项事件", 14f, FontWeight.Normal, Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                if (monthly.isNotEmpty()) {
                    item { SectionTitle("每月") }
                    items(monthly, key = { it.id }) { item ->
                        EventCard(
                            item = item,
                            expanded = expandedId == item.id,
                            saving = savingId == item.id,
                            eName = eName, eAmount = eAmount, eDay = eDay, eMonth = eMonth, eEnd = eEnd, eRemark = eRemark,
                            onName = { eName = it }, onAmount = { eAmount = it }, onDay = { eDay = it },
                            onMonth = { eMonth = it }, onRemark = { eRemark = it },
                            onToggleExpand = { toggleExpand(item) },
                            onToggleActive = { v -> toggleActive(item, v) },
                            onPickEnd = {
                                if (eEnd.isNotEmpty()) {
                                    val p = eEnd.split("-")
                                    runCatching {
                                        LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
                                    }.onSuccess { endPickerValue = it }
                                }
                                showEndPicker = true
                            },
                            onToggleMonthStatus = { m -> toggleMonthStatus(item, m) },
                            onSave = { handleSave(item) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }

                if (yearly.isNotEmpty()) {
                    item { SectionTitle("每年") }
                    items(yearly, key = { it.id }) { item ->
                        EventCard(
                            item = item,
                            expanded = expandedId == item.id,
                            saving = savingId == item.id,
                            eName = eName, eAmount = eAmount, eDay = eDay, eMonth = eMonth, eEnd = eEnd, eRemark = eRemark,
                            onName = { eName = it }, onAmount = { eAmount = it }, onDay = { eDay = it },
                            onMonth = { eMonth = it }, onRemark = { eRemark = it },
                            onToggleExpand = { toggleExpand(item) },
                            onToggleActive = { v -> toggleActive(item, v) },
                            onPickEnd = {
                                if (eEnd.isNotEmpty()) {
                                    val p = eEnd.split("-")
                                    runCatching {
                                        LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
                                    }.onSuccess { endPickerValue = it }
                                }
                                showEndPicker = true
                            },
                            onToggleMonthStatus = { m -> toggleMonthStatus(item, m) },
                            onSave = { handleSave(item) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }

                if (!loading && list.isEmpty()) {
                    item { VanEmpty(description = "暂无固定事件") }
                }
            }

            // 底部固定双键：按月管理 / 新增事件
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton(
                    text = "按月管理", round = true, block = true,
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.RECURRING) },
                )
                AppButton(
                    text = "新增事件", type = AppButtonType.Primary, icon = "plus", round = true, block = true,
                    modifier = Modifier.weight(1f),
                    onClick = { openAdd() },
                )
            }
        }
    }
    // ===== 失效日期选择器 =====
    VanPopup(show = showEndPicker, onDismissRequest = { showEndPicker = false }, round = true) {
        VanDatePicker(
            type = "date",
            value = endPickerValue,
            onValueChange = { endPickerValue = it },
            minDate = minDate,
            title = "选择失效日期",
            onConfirm = { d -> eEnd = d.toString(); showEndPicker = false },
            onCancel = { showEndPicker = false },
        )
    }

    // ===== 删除确认 =====
    val del = pendingDelete
    if (del != null) {
        AppDialogShell(
            show = true,
            title = "确认删除",
            onOverlay = { pendingDelete = null },
            onCancel = { pendingDelete = null },
            onConfirm = { pendingDelete = null; doDelete(del) },
            confirmColor = colors.danger,
        ) {
            FText("确定删除「${del.name}」？", 16f, FontWeight.Normal, colors.textPrimary)
        }
    }

    // ===== 新增事件表单（web height:80vh; overflow-y:auto）=====
    VanPopup(show = showAdd, onDismissRequest = { showAdd = false }, round = true, closeOnClickOverlay = true) {
        val cfg = LocalConfiguration.current
        Column(
            Modifier.fillMaxWidth().height((cfg.screenHeightDp * 0.8f).dp).verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FText("新增固定事件", 16f, FontWeight.SemiBold, colors.textPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            AppField(aName, { aName = it }, label = "名称", placeholder = "如 话费、房租、生日", maxlength = 100)
            FieldBlock("金额") { VanSwitch(checked = aIsExpense, onCheckedChange = { aIsExpense = it }, size = 20) }
            if (aIsExpense) {
                AppField(aAmount, { aAmount = it }, placeholder = "0.00", keyboardType = KeyboardType.Number)
            } else {
                FieldBlock { FText("仅事件提醒，不计入支出", 14f, FontWeight.Normal, colors.textTertiary) }
            }
            FieldBlock("周期") {
                VanRadioGroup(value = aCycle, onValueChange = { aCycle = it }, direction = "horizontal") {
                    VanRadio(name = "month", label = "每月")
                    VanRadio(name = "year", label = "每年")
                }
            }
            if (aCycle == "year") {
                FieldBlock("每年几月") { VanStepper(value = aMonth, onValueChange = { aMonth = it }, min = 1, max = 12) }
            }
            FieldBlock(if (aCycle == "year") "几号" else "每月几号") {
                VanStepper(value = aDay, onValueChange = { aDay = it }, min = 1, max = 31)
            }
            PickerField("失效日期", aEnd, "选填") {
                if (aEnd.isNotEmpty()) {
                    val pp = aEnd.split("-")
                    runCatching { LocalDate.of(pp[0].toInt(), pp[1].toInt(), pp[2].toInt()) }.onSuccess { addEndPickerValue = it }
                }
                showAddEnd = true
            }
            if (aIsExpense) {
                PickerField("分类", aCatLabel, "选择支出分类") { showAddCat = true }
            }
            AppField(aRemark, { aRemark = it }, label = "备注", placeholder = "选填", maxlength = 100)
            FieldBlock("提前提醒") {
                VanStepper(value = aRemindDays, onValueChange = { aRemindDays = it }, min = 0, max = 30)
            }
            FieldBlock("启用") { VanSwitch(checked = aActive, onCheckedChange = { aActive = it }) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(text = "取消", round = true, block = true, modifier = Modifier.weight(1f), onClick = { showAdd = false })
                AppButton(text = "保存", type = AppButtonType.Primary, round = true, block = true, loading = adding, modifier = Modifier.weight(1f), onClick = { handleAdd() })
            }
        }
    }

    // ===== 分类选择器 =====
    VanPopup(show = showAddCat, onDismissRequest = { showAddCat = false }, round = true) {
        VanPicker(
            columns = listOf(listOf(VanPickerOption("未分类", "")) + categories.map { VanPickerOption(it.name, it.id) }),
            title = "选择分类",
            onConfirm = { _, vals ->
                val id = vals.getOrNull(0) as? String ?: ""
                aCatId = id
                aCatLabel = if (id.isEmpty()) "未分类" else categories.firstOrNull { it.id == id }?.name ?: ""
                showAddCat = false
            },
            onCancel = { showAddCat = false },
        )
    }

    // ===== 新增表单的失效日期选择器 =====
    VanPopup(show = showAddEnd, onDismissRequest = { showAddEnd = false }, round = true) {
        VanDatePicker(
            type = "date",
            value = addEndPickerValue,
            onValueChange = { addEndPickerValue = it },
            minDate = minDate,
            title = "选择失效日期",
            onConfirm = { d -> aEnd = d.toString(); showAddEnd = false },
            onCancel = { showAddEnd = false },
        )
    }
}

/** 固定事件卡片（web `.event-card`，含展开编辑区 + 月度处理记录 + 保存/删除）。 */
@Composable
private fun EventCard(
    item: Recurring,
    expanded: Boolean,
    saving: Boolean,
    eName: String, eAmount: String, eDay: Int, eMonth: Int, eEnd: String, eRemark: String,
    onName: (String) -> Unit, onAmount: (String) -> Unit, onDay: (Int) -> Unit,
    onMonth: (Int) -> Unit, onRemark: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onPickEnd: () -> Unit,
    onToggleMonthStatus: (MonthRecRow) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val amountText = if (item.amount > 0) "￥${fmt2(item.amount)}" else "事件"
    val metaLead = if (item.cycle == "year") "每年${item.monthOfCycle}月${item.dayOfCycle}号" else "每月${item.dayOfCycle}号"

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgCard)
            .alpha(if (item.isActive) 1f else 0.55f),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggleExpand() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                FText(item.name, 18f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(metaLead, 12f, FontWeight.Normal, colors.textTertiary)
                    if (item.categoryName.isNotEmpty()) FText(" · ${item.categoryName}", 12f, FontWeight.Normal, colors.textTertiary)
                    if (item.endDate.isNotEmpty()) FText(" · 至 ${item.endDate}", 12f, FontWeight.Normal, colors.danger)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.amount > 0) {
                    FText(amountText, 18f, FontWeight.Bold, colors.danger)
                } else {
                    Box(
                        Modifier.background(VanPurpleFixed.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        FText(amountText, 14f, FontWeight.Normal, VanPurpleFixed)
                    }
                }
                VanIcon(
                    if (expanded) "arrow-up" else "arrow-down",
                    size = 14.sp,
                    color = colors.textTertiary,
                )
            }
        }

        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                VanDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FText(if (item.isActive) "已启用" else "已停用", 16f, FontWeight.Normal, colors.textPrimary)
                    VanSwitch(checked = item.isActive, onCheckedChange = onToggleActive, size = 20)
                }
                AppField(eName, onName, label = "名称", maxlength = 100)
                if (item.amount > 0) {
                    AppField(eAmount, onAmount, label = "金额", keyboardType = KeyboardType.Number)
                } else {
                    FieldBlock("金额") { FText("仅事件提醒", 14f, FontWeight.Normal, colors.textTertiary) }
                }
                FieldBlock(if (item.cycle == "year") "几号" else "每月几号") {
                    VanStepper(value = eDay, onValueChange = onDay, min = 1, max = 31)
                }
                if (item.cycle == "year") {
                    FieldBlock("每年几月") { VanStepper(value = eMonth, onValueChange = onMonth, min = 1, max = 12) }
                }
                PickerField("失效日期", eEnd, if (eEnd.isNotEmpty()) eEnd else "选填", onPickEnd)
                AppField(eRemark, onRemark, label = "备注", placeholder = "选填", maxlength = 100)

                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    FText("月度处理记录", 14f, FontWeight.SemiBold, colors.textSecondary, Modifier.padding(bottom = 6.dp))
                    monthRecRows(item).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, colors.border).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FText(m.month, 14f, FontWeight.Medium, colors.textPrimary, Modifier.width(64.dp))
                            VanTag(statusTextOf(m.status), type = statusTagType(m.status), size = VanTagSize.Small)
                            if (m.amount > 0) FText("￥${fmt2(m.amount)}", 14f, FontWeight.Medium, colors.danger)
                            Spacer(Modifier.weight(1f))
                            AppButton(text = "切换状态", size = AppButtonSize.Mini, plain = true, onClick = { onToggleMonthStatus(m) })
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppButton(text = "保存修改", type = AppButtonType.Primary, round = true, block = true, loading = saving, modifier = Modifier.weight(1f), onClick = onSave)
                    AppButton(text = "删除此项", type = AppButtonType.Danger, round = true, block = true, modifier = Modifier.weight(1f), onClick = onDelete)
                }
            }
        }
    }
}
// ============================================================================================
// 固定支出（web src/views/Finance/recurring/List.vue）
// ============================================================================================

@Composable
private fun FixedExpenseScreen() {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.recurring
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(thisMonthKey()) }
    var list by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var summary by remember { mutableStateOf(RecurringSummary()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Recurring?>(null) }
    var editingMonth by remember { mutableStateOf("") }
    var editingSkipped by remember { mutableStateOf(false) }
    var fName by remember { mutableStateOf("") }
    var fAmount by remember { mutableStateOf("") }
    var fIsExpense by remember { mutableStateOf(true) }
    var fRemark by remember { mutableStateOf("") }
    var pendingSkip by remember { mutableStateOf<Recurring?>(null) }

    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    fun loadData() {
        scope.launch {
            loading = true
            val l = repo.list(month = month)
            val s = repo.summary(month)
            when (l) {
                is ApiResult.Ok -> list = l.data ?: emptyList()
                is ApiResult.Fail -> toast.show(l.message.ifEmpty { "加载失败" })
                else -> {}
            }
            summary = (s as? ApiResult.Ok)?.data ?: RecurringSummary()
            loading = false
            if (refreshState.isLoading) refreshState.finishRefresh()
        }
    }

    fun changeMonth(step: Int) {
        month = shiftMonth(month, step)
        loadData()
    }

    fun openEdit(item: Recurring) {
        val itemAmount = item.amount
        val rec = item.monthRecord
        val monthAmount = rec?.amount ?: itemAmount
        editingMonth = item.happenDate.ifEmpty { month }
        editingSkipped = item.monthStatus == "skipped"
        fName = item.name
        fAmount = if (itemAmount > 0) numStr(monthAmount) else "0"
        fIsExpense = itemAmount > 0
        fRemark = rec?.remark ?: ""
        editingItem = item
        showForm = true
    }

    fun restoreMonth() {
        val item = editingItem
        if (item == null) { toast.show("数据异常", VanToastType.Fail); return }
        scope.launch {
            when (val r = repo.updateMonthStatus(item.id, month, "pending")) {
                is ApiResult.Ok -> {
                    toast.show("已恢复为待处理", com.live.vant.feedback.VanToastType.Success)
                    showForm = false
                    loadData()
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "操作失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "操作失败")
            }
        }
    }

    fun handleSubmit() {
        if (fName.trim().isEmpty()) { toast.show("请输入名称"); return }
        if (fIsExpense && (fAmount.isEmpty() || (fAmount.toDoubleOrNull() ?: 0.0) <= 0.0)) { toast.show("请输入金额"); return }
        val item = editingItem
        if (item == null) { toast.show("数据异常", VanToastType.Fail); return }
        submitting = true
        scope.launch {
            val status = if (item.monthStatus == "skipped") "pending" else item.monthStatus
            val amount = if (fIsExpense) (fAmount.toDoubleOrNull() ?: 0.0) else 0.0
            when (val r = repo.updateMonthStatus(item.id, month, status, amount, fRemark.trim())) {
                is ApiResult.Ok -> {
                    toast.show("保存成功", com.live.vant.feedback.VanToastType.Success)
                    showForm = false
                    loadData()
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "保存失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "保存失败")
            }
            submitting = false
        }
    }

    fun toggleDone(item: Recurring) {
        val status = if (item.monthStatus == "done") "pending" else "done"
        scope.launch {
            when (val r = repo.updateMonthStatus(item.id, month, status, item.monthAmount)) {
                is ApiResult.Ok -> {
                    toast.show("已更新", com.live.vant.feedback.VanToastType.Success)
                    loadData()
                }
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "更新失败" }, VanToastType.Fail)
                else -> toast.showFail(r, "更新失败")
            }
        }
    }

    fun doSkip(item: Recurring) {
        scope.launch {
            when (repo.updateMonthStatus(item.id, month, "skipped")) {
                is ApiResult.Ok -> {
                    toast.show("已跳过", com.live.vant.feedback.VanToastType.Success)
                    loadData()
                }
                is ApiResult.Fail -> toast.show("操作失败", VanToastType.Fail)
                else -> toast.show("操作失败", VanToastType.Fail)
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    ScreenScaffold { inner ->
        VanPullRefresh(
            state = refreshState,
            isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
            onRefresh = { ApiGetCache.clear(); loadData() },
            modifier = inner.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            ) {
                // ===== 汇总卡 =====
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(14.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            VanIcon("arrow-left", size = 16.sp, color = colors.textSecondary, onClick = { changeMonth(-1) })
                            FText(monthTitleOf(month), 16f, FontWeight.Normal, colors.textSecondary, Modifier.padding(horizontal = 18.dp))
                            VanIcon("arrow", size = 16.sp, color = colors.textSecondary, onClick = { changeMonth(1) })
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                FText("本月固定支出", 12f, FontWeight.Normal, colors.textTertiary)
                                FText("￥${fmt2(summary.totalAmount)}", 28f, FontWeight.Bold, colors.danger, Modifier.padding(top = 4.dp))
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FText("${summary.total}项", 12f, FontWeight.Normal, colors.textSecondary)
                                FText("${summary.pending}项待处理", 12f, FontWeight.Normal, colors.textSecondary)
                            }
                        }
                    }
                }

                // ===== 组成分类卡 =====
                if (summary.categoryStats.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(14.dp),
                        ) {
                            FText("组成", 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(bottom = 10.dp))
                            summary.categoryStats.forEach { cs ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    FText(cs.categoryName, 14f, FontWeight.Normal, colors.textSecondary)
                                    FText(
                                        if (cs.categoryName == "事件提醒") "${cs.count}件" else "￥${fmt2(cs.amount)}",
                                        14f, FontWeight.Normal, colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== 列表 =====
                if (!loading && list.isEmpty()) {
                    item { VanEmpty(description = "暂无固定支出") }
                } else {
                    items(list, key = { it.id }) { item ->
                        RecurringRow(
                            item = item,
                            onToggleDone = { toggleDone(item) },
                            onEdit = { openEdit(item) },
                            onSkip = { pendingSkip = item },
                        )
                    }
                }
            }
        }
    }

    // ===== 编辑当月 popup =====
    VanPopup(show = showForm, onDismissRequest = { showForm = false }, round = true, closeOnClickOverlay = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 22.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FText("编辑当月 — $editingMonth", 16f, FontWeight.SemiBold, colors.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            AppField(fName, {}, label = "名称")
            if (fIsExpense) {
                AppField(fAmount, { fAmount = it }, label = "本月金额", placeholder = "0.00", keyboardType = KeyboardType.Number)
            } else {
                FieldBlock { FText("仅事件提醒，不计入支出", 14f, FontWeight.Normal, colors.textTertiary) }
            }
            AppField(fRemark, { fRemark = it }, label = "本月备注", placeholder = "选填", maxlength = 100)
            if (editingSkipped) {
                AppButton(
                    text = "恢复事件（取消跳过）", type = AppButtonType.Warning, round = true, block = true,
                    modifier = Modifier.padding(bottom = 10.dp),
                    onClick = { restoreMonth() },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(text = "取消", round = true, block = true, modifier = Modifier.weight(1f), onClick = { showForm = false })
                AppButton(text = "保存", type = AppButtonType.Primary, round = true, block = true, loading = submitting, modifier = Modifier.weight(1f), onClick = { handleSubmit() })
            }
        }
    }

    // ===== 跳过本月确认 =====
    val sk = pendingSkip
    if (sk != null) {
        AppDialogShell(
            show = true,
            title = "跳过本月",
            onOverlay = { pendingSkip = null },
            onCancel = { pendingSkip = null },
            onConfirm = { pendingSkip = null; doSkip(sk) },
            confirmColor = colors.danger,
        ) {
            FText("确定跳过「${sk.name}」本月？跳过后不再计入统计。", 16f, FontWeight.Normal, colors.textPrimary)
        }
    }
}

/** 固定支出列表行（web `.recurring-item`）。 */
@Composable
private fun RecurringRow(
    item: Recurring,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalAppColors.current
    val subLead = if (item.cycle == "year") {
        "每年" + (if (item.monthOfCycle > 0) "${item.monthOfCycle}月" else "") + "${item.dayOfCycle}号"
    } else {
        "每月${item.dayOfCycle}号"
    }

    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)
            .alpha(if (item.isActive) 1f else 0.58f)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                FText(item.name, 18f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(bottom = 5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(subLead, 12f, FontWeight.Normal, colors.textTertiary)
                    if (item.repeatCount > 0) {
                        FText(" · 第${item.doneCount}/${item.repeatCount}期", 12f, FontWeight.Medium, VanPurpleFixed)
                    }
                    if (item.categoryName.isNotEmpty()) {
                        FText(" · ${item.categoryName}", 12f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }
            if (item.monthAmount > 0) {
                FText("￥${fmt2(item.monthAmount)}", 18f, FontWeight.Bold, colors.danger)
            } else {
                FText("仅提醒", 14f, FontWeight.Normal, colors.primary)
            }
        }

        if (!item.isInstallment) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VanTag(statusTextOf(item.monthStatus), type = statusTagType(item.monthStatus), size = VanTagSize.Small)
                    if (item.cycle == "year") VanTag("年", type = VanTagType.Primary, size = VanTagSize.Small)
                    if (item.repeatCount > 0) {
                        VanTag("${item.doneCount}/${item.repeatCount}期", color = VanPurpleFixed, textColor = Color.White, size = VanTagSize.Small)
                    }
                    if (!item.isActive) VanTag("已停用", type = VanTagType.Default)
                    if (item.endDate.isNotEmpty()) FText("至 ${item.endDate}", 12f, FontWeight.Normal, colors.danger)
                    FText(item.happenDate, 12f, FontWeight.Normal, colors.textTertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.monthStatus != "skipped") {
                        AppButton(
                            text = if (item.monthStatus == "done") "设为待处理" else "设为已处理",
                            type = AppButtonType.Primary, size = AppButtonSize.Mini, plain = true,
                            onClick = onToggleDone,
                        )
                    } else {
                        VanTag("已跳过", type = VanTagType.Danger, size = VanTagSize.Small)
                    }
                    AppButton(text = "编辑", size = AppButtonSize.Mini, plain = true, onClick = onEdit)
                    if (item.monthStatus != "skipped") {
                        AppButton(
                            text = "跳过本月", type = AppButtonType.Danger, size = AppButtonSize.Mini, plain = true,
                            onClick = onSkip,
                        )
                    }
                }
            }
        } else {
            FText(item.happenDate, 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 12.dp))
        }
    }
}