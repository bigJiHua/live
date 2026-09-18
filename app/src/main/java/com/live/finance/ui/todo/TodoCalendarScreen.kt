package com.live.finance.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.NewTodo
import com.live.finance.data.model.Todo
import com.live.finance.data.model.TodoCalendarMonth
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanAction
import com.live.vant.feedback.VanActionSheet
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.form.VanRadio
import com.live.vant.form.VanRadioGroup
import com.live.vant.form.VanStepper
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 紫色（web `--van-purple`，用于「事件提醒」标签）。 */
private val VanPurple = Color(0xFF7232DD)
/** 蓝色（web `--van-blue`，用于「计划」分区图标与分期「待入账」）。 */
private val VanBlue = Color(0xFF1989FA)
private val VanOrange = Color(0xFFFF976A)
private val VanGreen = Color(0xFF07C160)
private val VanRed = Color(0xFFEE0A24)

/** 新增/编辑事件表单（web `newEvent` / `editEvent`）。 */
private data class EventForm(
    val content: String = "",
    val remark: String = "",
    val eventType: String = "schedule",
    val priority: Int = 2,
    val isRecurring: Boolean = false,
    val needRemind: Boolean = false,
    val remindDays: Int = 0,
)

/**
 * 待办提醒（日程日历）—— 一比一复刻 web `src/views/Todo/Calendar.vue`（路由 `/todo/calendar`）。
 *
 * 结构：
 *  ① 页头：上一月（绿色）｜`YYYY年M月` + 下拉箭头（点开月份 picker）｜下一月（三级色）
 *  ② `TodoCalendarGrid`（todo 变体、`showHeader=false`、`card=false`、可折叠、默认展开；插槽放「眼睛」按钮切小飞机）
 *  ③ 事件列表卡：`YYYY年M月D日 星期X` + `N个计划` + 「＋添加」胶囊按钮 →
 *     消费分期分区（左红边 + 状态标签）→ 计划分区（标签 + 备注 + 右侧箭头/固定支出图标）
 *  ④ 事件操作：`van-action-sheet`（按来源区分：固定支出→标记已处理/管理固定支出；账单→查看账单；普通→标记完成/编辑/删除）
 *  ⑤ 添加/编辑弹窗：内容(必填,100) + 备注(50) + 事件类型/优先级单选 + 每年重复/开启提醒开关 + 提前天数步进器
 *  ⑥ 月份 picker：`今年-10 ~ 今年+5` × 12 月
 *
 * 接口：`/todo/calendar/month`、`/todo/list?happen_date=`、`/todo/reminders`、`/todo`（增/改/删）、
 * `/recurring/:id/month-status`（固定支出标记）；字段与解析见 `TodoRepository`。
 */
@Composable
fun TodoCalendarScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    // web `.header .van-icon{color: var(--van-green,#07c160)}`：浅色 = Vant 默认 #07c160，
    // **深色**被 style.css 重定向到 `--theme-success`（与 SALARY_GREEN 同款规则）
    val headerGreen = if (tokens.isDark) colors.success else VanGreen
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    val today = remember { LocalDate.now() }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }        // 1-12
    var selectedDate by remember { mutableStateOf(today.toString()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showAirplane by remember { mutableStateOf(false) }               // 默认隐藏小飞机

    var loading by remember { mutableStateOf(true) }
    var calendarMonth by remember { mutableStateOf(TodoCalendarMonth()) }
    var reminders by remember { mutableStateOf<List<Todo>>(emptyList()) }
    var selectedEvents by remember { mutableStateOf<List<Todo>>(emptyList()) }
    var loadingEvents by remember { mutableStateOf(false) }

    var showAddPopup by remember { mutableStateOf(false) }
    var showEditPopup by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(EventForm()) }
    var currentEvent by remember { mutableStateOf<Todo?>(null) }

    var showActions by remember { mutableStateOf(false) }
    var actionOptions by remember { mutableStateOf<List<ActionOption>>(emptyList()) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    /** 待打开操作面板的事件（见文件末尾转发，规避局部函数的前置引用限制）。 */
    var actionEvent by remember { mutableStateOf<Todo?>(null) }

    // ── 数据 ──
    fun recurringOfDay(date: String): List<Todo> =
        calendarMonth.days.firstOrNull { it.date == date }?.list?.filter { it.source == "recurring" } ?: emptyList()

    suspend fun loadSelectedDateEvents() {
        loadingEvents = true
        val list = (graph.todo.listByDate(selectedDate) as? ApiResult.Ok)?.data ?: emptyList()
        // web：二次过滤 happen_date，再把「当日固定支出/分期事件」并进来
        selectedEvents = list.filter { it.happenDate == selectedDate } + recurringOfDay(selectedDate)
        loadingEvents = false
    }

    suspend fun reloadAll() {
        loading = true
        calendarMonth = (graph.todo.calendarMonth(year, month) as? ApiResult.Ok)?.data ?: TodoCalendarMonth()
        reminders = (graph.todo.reminders(year, month) as? ApiResult.Ok)?.data ?: emptyList()
        loading = false
        loadSelectedDateEvents()
    }

    LaunchedEffect(year, month) { reloadAll() }
    LaunchedEffect(selectedDate) {
        if (!loading) {
            loadSelectedDateEvents()
        }
    }

    // ── 派生 ──
    val dataset: Map<String, DayMark> = remember(calendarMonth, reminders, showAirplane) {
        val map = mutableMapOf<String, DayMark>()
        calendarMonth.days.forEach { d ->
            val hasRealEvent = d.list.any { it.content != "1" }
            map[d.date] = DayMark(
                eventCount = if (hasRealEvent) (if (d.count > 0) d.count else d.list.size) else 0,
                overdue = d.hasOverdue || d.list.any { it.isOverdue },
                airplane = showAirplane && d.list.any { it.content == "1" },
            )
        }
        // 临期提醒可能落在无事件日；按天取最高级别
        val order = mapOf("red" to 3, "yellow" to 2, "green" to 1)
        reminders.forEach { r ->
            val lv = calcReminderLevel(r) ?: return@forEach
            val prev = map[r.happenDate]?.reminder
            if (prev == null || (order[lv] ?: 0) > (order[prev] ?: 0)) {
                val base = map[r.happenDate] ?: DayMark()
                map[r.happenDate] = base.copy(reminder = lv)
            }
        }
        map
    }
    val banner: List<ReminderChip> = remember(reminders) {
        reminders.filter { it.content != "1" }
            .map { ReminderChip(date = it.happenDate, level = reminderBannerLevel(it.happenDate), content = it.content) }
    }
    val installmentEvents = selectedEvents.filter { it.categoryId == "installment" }
    val otherEvents = selectedEvents.filter { it.categoryId != "installment" }

    fun reloadFromUi() = scope.launch { reloadAll() }

    // ── 页头 / 日历 / 事件列表 ──
    ScreenScaffold { inner ->
        Column(inner.verticalScroll(rememberScrollState()).background(colors.bgPage)) {
            // ① 页头（web .header：padding 16、上下月图标 18px，上一月绿色、下一月三级色）
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VanIcon(
                    name = "arrow-left", size = 18.sp, color = headerGreen,
                    modifier = Modifier.padding(6.dp), onClick = { prevMonth(year, month) { y, m -> year = y; month = m } },
                )
                Row(
                    modifier = Modifier.clickable { showMonthPicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText("${year}年${month}月", 18f, FontWeight.SemiBold, colors.textPrimary)
                    Spacer(Modifier.width(4.dp))
                    VanIcon(name = "arrow-down", size = 18.sp, color = headerGreen)
                }
                VanIcon(
                    name = "arrow", size = 18.sp, color = colors.textTertiary,
                    modifier = Modifier.padding(6.dp), onClick = { nextMonth(year, month) { y, m -> year = y; month = m } },
                )
            }

            // ② 日历（web <CalendarGrid variant="todo" show-header=false card=false collapsible>）
            Box(Modifier.fillMaxWidth().background(colors.bgCard)) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        VanLoading(size = 32.sp, text = "加载中...")
                    }
                } else {
                    TodoCalendarGrid(
                        year = year,
                        month = month,
                        selectedDate = selectedDate,
                        dataset = dataset,
                        reminderBanner = banner,
                        onSelect = { selectedDate = it },
                        onGoToday = {
                            year = today.year
                            month = today.monthValue
                            selectedDate = today.toString()
                            reloadFromUi()
                        },
                        reminderAction = {
                            VanIcon(
                                name = if (showAirplane) "eye-o" else "closed-eye",
                                size = 16.sp, color = tokens.primary,
                                modifier = Modifier.padding(2.dp),
                                onClick = { showAirplane = !showAirplane },
                            )
                        },
                    )
                }
            }

            // ③ 事件列表（web .event-list：radius 16 顶、padding 16、margin-top 8）
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(colors.bgCard)
                    .padding(16.dp)
                    .heightIn(min = 200.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FText(formatSelectedDate(selectedDate), 15f, FontWeight.SemiBold, colors.textPrimary)
                    if (selectedEvents.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        FText("${selectedEvents.size}个计划", 13f, FontWeight.Normal, colors.textTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    // web .add-btn：主色描边胶囊 + 8% 主色底 + plus
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(tokens.primary.copy(alpha = 0.08f))
                            .border(1.dp, tokens.primary, RoundedCornerShape(16.dp))
                            .clickable {
                                form = EventForm()
                                showAddPopup = true
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        VanIcon(name = "plus", size = 13.sp, color = tokens.primary)
                        FText("添加", 13f, FontWeight.SemiBold, tokens.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))

                when {
                    loadingEvents -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        VanLoading(size = 24.sp, text = "加载中...")
                    }
                    selectedEvents.isEmpty() -> VanEmpty(description = "暂无计划", image = "search")
                    else -> {
                        // 消费分期分区
                        if (installmentEvents.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                SectionHeader("bill-o", VanRed, "消费分期")
                                installmentEvents.forEach { e ->
                                    InstallmentItem(event = e) {
                                        if (nav.graph.findNode(Routes.CREDIT_CENTER) != null) nav.navigate(Routes.CREDIT_CENTER)
                                        else toast.show("该页面尚未实现")
                                    }
                                }
                            }
                        }
                        // 计划分区
                        if (otherEvents.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                if (installmentEvents.isNotEmpty()) SectionHeader("todo-list-o", VanBlue, "计划")
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    otherEvents.forEach { e ->
                                        OtherEventItem(event = e, onClick = { actionEvent = e })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))   // web .page-calendar { padding-bottom: 120px }
        }
    }

    // ④ 操作面板
    VanActionSheet(
        show = showActions,
        onDismissRequest = { showActions = false },
        actions = actionOptions.map { VanAction(name = it.name, color = it.color) },
        cancelText = "取消",
        onSelect = { _, index ->
            showActions = false
            actionOptions.getOrNull(index)?.let { it.run() }
        },
    )

    // ④′ 二次确认（标记完成/未完成、删除）
    val confirm = pendingConfirm
    VanConfirmDialog(
        show = confirm != null,
        title = confirm?.title ?: "",
        message = confirm?.message ?: "",
        onConfirm = {
            val c = pendingConfirm
            pendingConfirm = null
            c?.action?.invoke()
        },
        onClose = { pendingConfirm = null },
    )

    // ⑤ 添加弹窗
    if (showAddPopup) {
        EventFormPopup(
            title = "添加事件",
            subtitle = formatSelectedDate(selectedDate),
            form = form,
            onFormChange = { form = it },
            submitting = submitting,
            confirmText = "确认",
            requiredTip = true,
            onClose = { showAddPopup = false },
            onConfirm = {
                if (form.content.trim().isEmpty()) {
                    toast.show("请输入内容")
                } else {
                    submitting = true
                    scope.launch {
                        val r = graph.todo.create(form.toNewTodo(selectedDate))
                        submitting = false
                        when (r) {
                            is ApiResult.Ok -> {
                                toast.success("添加成功")
                                showAddPopup = false
                                reloadAll()
                            }
                            is ApiResult.Fail -> toast.show(r.message.ifBlank { "添加失败" })
                            else -> toast.show("添加失败")
                        }
                    }
                }
            },
        )
    }

    // ⑤′ 编辑弹窗
    if (showEditPopup) {
        EventFormPopup(
            title = "编辑事件",
            subtitle = null,
            form = form,
            onFormChange = { form = it },
            submitting = submitting,
            confirmText = "保存修改",
            requiredTip = false,
            onClose = { showEditPopup = false },
            onConfirm = {
                val target = currentEvent
                if (form.content.trim().isEmpty()) {
                    toast.show("请输入事件内容")
                } else if (target == null) {
                    showEditPopup = false
                } else {
                    submitting = true
                    scope.launch {
                        val r = graph.todo.update(target.id, form.toNewTodo(target.happenDate))
                        submitting = false
                        when (r) {
                            is ApiResult.Ok -> {
                                toast.success("保存成功")
                                showEditPopup = false
                                reloadAll()
                            }
                            is ApiResult.Fail -> toast.show(r.message.ifBlank { "保存失败" })
                            else -> toast.show("保存失败")
                        }
                    }
                }
            },
        )
    }

    // ⑥ 月份 picker（今年-10 ~ 今年+5 × 12 月）
    val years = remember { (today.year - 10..today.year + 5).toList() }
    if (showMonthPicker) {
        VanPopup(
            show = true,
            onDismissRequest = { showMonthPicker = false },
            position = VanPopupPosition.Bottom,
            round = true,
        ) {
            VanPicker(
                columns = listOf(
                    years.map { VanPickerOption("${it}年", it.toString()) },
                    (1..12).map { VanPickerOption("${it}月", it.toString()) },
                ),
                value = listOf(
                    years.indexOf(year).coerceAtLeast(0),
                    (month - 1).coerceIn(0, 11),
                ),
                title = "选择月份",
                onConfirm = { idx, _ ->
                    year = years.getOrElse(idx.firstOrNull() ?: 0) { year }
                    month = (idx.getOrNull(1) ?: 0) + 1
                    showMonthPicker = false
                },
                onCancel = { showMonthPicker = false },
            )
        }
    }

    /** 事件操作面板选项（web `showEventActions` 的四条分支）。 */
    fun showEventActions(event: Todo) {
        currentEvent = event
        val options = mutableListOf<ActionOption>()
        when {
            // 固定支出/分期：不可手动改完成态（避免覆盖入账语义）
            event.source == "recurring" && event.categoryId == "installment" -> {
                options += ActionOption("管理固定支出") {
                    if (nav.graph.findNode(Routes.EVENTS) != null) nav.navigate(Routes.EVENTS)
                    else toast.show("该页面尚未实现")
                }
            }
            event.source == "recurring" -> {
                options += ActionOption(
                    if (event.monthStatus == "done") "标记待处理" else "标记已处理",
                ) {
                    scope.launch {
                        val next = if (event.monthStatus == "done") "pending" else "done"
                        val r = graph.todo.updateRecurringMonthStatus(
                            recurringId = event.recurringId,
                            month = event.happenDate.take(7),
                            status = next,
                            amount = event.amount,
                        )
                        when (r) {
                            is ApiResult.Ok -> {
                                toast.success("操作成功")
                                reloadAll()
                            }
                            is ApiResult.Fail -> toast.show(r.message.ifBlank { "操作失败" })
                            else -> toast.show("操作失败")
                        }
                    }
                }
                options += ActionOption("管理固定支出") {
                    if (nav.graph.findNode(Routes.EVENTS) != null) nav.navigate(Routes.EVENTS)
                    else toast.show("该页面尚未实现")
                }
            }
            // 信用卡还款提醒由账单派生，只读
            event.source == "card_bill" -> {
                options += ActionOption("查看账单") {
                    if (nav.graph.findNode(Routes.BILL_LIST) != null) nav.navigate(Routes.BILL_LIST)
                    else toast.show("该页面尚未实现")
                }
            }
            else -> {
                val done = event.status == "已完成"
                options += ActionOption(if (done) "标记未完成" else "标记完成") {
                    pendingConfirm = PendingConfirm(
                        title = "确认操作",
                        message = if (done) "确定标记为未完成？" else "确定标记为已完成？",
                    ) {
                        scope.launch {
                            val r = graph.todo.updateStatus(event.id, if (done) "待完成" else "已完成")
                            when (r) {
                                is ApiResult.Ok -> {
                                    toast.success("操作成功")
                                    reloadAll()
                                }
                                is ApiResult.Fail -> toast.show(r.message.ifBlank { "操作失败" })
                                else -> toast.show("操作失败")
                            }
                        }
                    }
                }
                options += ActionOption("编辑") {
                    form = EventForm(
                        content = event.content,
                        remark = event.remark,
                        eventType = event.eventType.ifBlank { "schedule" },
                        priority = event.priority.toIntOrNull() ?: 2,
                        isRecurring = event.isRecurring,
                        needRemind = event.needRemind,
                        remindDays = event.remindDays,
                    )
                    showEditPopup = true
                }
                options += ActionOption("删除", color = VanRed) {
                    pendingConfirm = PendingConfirm("确认删除", "确定要删除这条计划吗？") {
                        scope.launch {
                            val r = graph.todo.delete(event.id)
                            when (r) {
                                is ApiResult.Ok -> {
                                    toast.success("删除成功")
                                    reloadAll()
                                }
                                is ApiResult.Fail -> toast.show(r.message.ifBlank { "删除失败" })
                                else -> toast.show("删除失败")
                            }
                        }
                    }
                }
            }
        }
        actionOptions = options
        showActions = true
    }

    // 转发：点击事件先落到 actionEvent；因 Kotlin 局部函数必须在调用点之前声明，
    // 故在这里（声明之后）用 LaunchedEffect 消费一次。
    actionEvent?.let { e ->
        LaunchedEffect(e) {
            showEventActions(e)
            actionEvent = null
        }
    }
}

/** 操作面板项（name + 可选颜色 + 回调）。 */
private data class ActionOption(val name: String, val color: Color? = null, val run: () -> Unit)

/** 待确认操作（web `showConfirmDialog`）。*/
private data class PendingConfirm(val title: String, val message: String, val action: () -> Unit)

// ───────────────────────── 页内小组件 ─────────────────────────

private fun EventForm.toNewTodo(happenDate: String) = NewTodo(
    content = content.trim(),
    happenDate = happenDate,
    eventType = eventType,
    priority = priority,
    isRecurring = isRecurring,
    needRemind = needRemind,
    remindDays = remindDays,
    remark = remark.trim(),
)

private fun prevMonth(year: Int, month: Int, set: (Int, Int) -> Unit) =
    if (month == 1) set(year - 1, 12) else set(year, month - 1)

private fun nextMonth(year: Int, month: Int, set: (Int, Int) -> Unit) =
    if (month == 12) set(year + 1, 1) else set(year, month + 1)

/** web `dayjs(selectedDate).format('YYYY年M月D日 dddd')`。 */
private fun formatSelectedDate(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val week = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[d.dayOfWeek.value - 1]
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日 $week"
}

/**
 * web `calcReminderLevel`：仅 `need_remind` 且未过期的事件参与；
 * 提醒开始日优先用后端算出的 `remind_time`（毫秒时间戳），否则回退「事件前 10 天」；
 * 到点后按距事件天数分级：≥10 绿 / ≥5 黄 / 其余 红。
 */
private fun calcReminderLevel(item: Todo): String? {
    if (!item.needRemind) return null
    val eventDate = runCatching { LocalDate.parse(item.happenDate.take(10)) }.getOrNull() ?: return null
    val today = LocalDate.now()
    val daysUntil = ChronoUnit.DAYS.between(today, eventDate)
    if (daysUntil < 0) return null
    val remindStart = item.remindTime.toLongOrNull()?.let { millis ->
        runCatching {
            java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()?.takeIf { !it.isAfter(eventDate) }
    } ?: eventDate.minusDays(10)
    if (today.isBefore(remindStart)) return null
    return when {
        daysUntil >= 10 -> "green"
        daysUntil >= 5 -> "yellow"
        else -> "red"
    }
}

/** web `getReminderBannerLevel`：今天及以前未完成 → 红；之后 ≥10 绿 / ≥5 黄 / 其余 红。 */
private fun reminderBannerLevel(happenDate: String): String {
    val d = runCatching { LocalDate.parse(happenDate.take(10)) }.getOrNull() ?: return "red"
    val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        daysUntil <= 0 -> "red"
        daysUntil >= 10 -> "green"
        daysUntil >= 5 -> "yellow"
        else -> "red"
    }
}

/** web `.event-section-header`：图标 + 14/600 标题，padding 4 0 10。 */
@Composable
private fun SectionHeader(icon: String, iconColor: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VanIcon(name = icon, size = 16.sp, color = iconColor)
        FText(text, 14f, FontWeight.SemiBold, LocalAppColors.current.textPrimary)
    }
}

/** web `.event-item.inst-item`：左 3px 红边 + 分期状态标签 + 金额 + 箭头（整条可点）。 */
@Composable
private fun InstallmentItem(event: Todo, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(colors.bgPage, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // web `.inst-item { border-left: 3px solid --van-danger-color; padding-left: 9px }`
        Box(Modifier.width(3.dp).height(34.dp).background(VanRed, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            FText(event.content, 14f, FontWeight.Normal, colors.textPrimary)
            Spacer(Modifier.height(6.dp))   // web .event-meta { margin-top: 6px }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VanTag(text = "消费分期", type = VanTagType.Danger, size = VanTagSize.Small)
                InstStatusTag(event)
                FText("￥" + String.format(java.util.Locale.US, "%.2f", event.amount), 12f, FontWeight.Medium, colors.textPrimary)
            }
        }
        VanIcon(name = "arrow", size = 16.sp, color = colors.textTertiary)
    }
}

/** 分期期次状态标签（web `.inst-tag` 六态）。 */
@Composable
private fun InstStatusTag(event: Todo) {
    val colors = LocalAppColors.current
    val (text, fg, bg) = when {
        event.isVoid -> Triple("超过期限", colors.textTertiary, Color(0x0D000000))
        event.monthStatus == "entered" -> Triple("已入账", VanGreen, VanGreen.copy(alpha = 0.1f))
        event.monthOverdue -> Triple("逾期未还", VanRed, VanRed.copy(alpha = 0.1f))
        event.monthStatus == "entering" -> Triple("入账中", VanOrange, VanOrange.copy(alpha = 0.12f))
        event.monthStatus == "done" -> Triple("已还", VanGreen, VanGreen.copy(alpha = 0.1f))
        else -> Triple("待入账", VanBlue, VanBlue.copy(alpha = 0.1f))
    }
    Box(
        Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        FText(text, 11f, FontWeight.Medium, fg)
    }
}

/** web `.event-item`（普通计划）：标题 + 标签行 + 备注 + 右侧箭头（固定支出用 cash-back-record 图标）。 */
@Composable
private fun OtherEventItem(event: Todo, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val completed = event.status == "已完成"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.6f else 1f)               // web .event-item.completed { opacity: .6 }
            .background(colors.bgPage, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (completed) {
                // web `.completed .event-title { text-decoration: line-through }`
                BasicText(
                    event.content,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                )
            } else {
                FText(event.content, 14f, FontWeight.Normal, colors.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event.source == "recurring" && event.amount > 0) {
                    VanTag(text = "固定支出", type = VanTagType.Warning, size = VanTagSize.Small)
                } else if (event.source == "recurring") {
                    VanTag(text = "事件提醒", size = VanTagSize.Small, color = VanPurple, textColor = Color.White)
                }
                if (event.eventType.isNotBlank() && event.eventType != "fixed_expense") {
                    VanTag(text = eventTypeName(event.eventType), type = VanTagType.Primary, size = VanTagSize.Small)
                }
                if (event.priority == "1") {
                    VanTag(text = "高优", type = VanTagType.Danger, size = VanTagSize.Small)
                }
                if (event.source == "recurring" && event.cycle == "year") {
                    VanTag(text = "每年", type = VanTagType.Success, size = VanTagSize.Small)
                }
                if (event.source != "recurring" && event.isRecurring) {
                    VanTag(text = "每年", type = VanTagType.Success, size = VanTagSize.Small)
                }
                if (event.source == "recurring" && event.amount > 0) {
                    FText("￥" + String.format(java.util.Locale.US, "%.2f", event.amount), 12f, FontWeight.Medium, colors.textPrimary)
                }
            }
            if (event.remark.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                FText(event.remark, 12f, FontWeight.Normal, colors.textTertiary)
            }
        }
        VanIcon(
            name = if (event.source == "recurring") "cash-back-record" else "arrow",
            size = 16.sp, color = colors.textTertiary,
        )
    }
}

/** web `getEventTypeName`。 */
private fun eventTypeName(type: String): String = when (type) {
    "schedule" -> "日程"
    "birthday" -> "生日"
    "anniversary" -> "纪念日"
    "countdown" -> "倒数日"
    "fixed_expense" -> "固定支出"
    else -> type
}

/**
 * 添加/编辑事件弹窗（web `app-popup position=bottom round` + 两组单选 + 两个开关 + 步进器 + 双按钮）。
 * 添加态：标题「添加事件」+ 右侧选中日期 + 内容必填角标；编辑态：标题「编辑事件」。
 */
@Composable
private fun EventFormPopup(
    title: String,
    subtitle: String?,
    form: EventForm,
    onFormChange: (EventForm) -> Unit,
    submitting: Boolean,
    confirmText: String,
    requiredTip: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    VanPopup(show = true, onDismissRequest = onClose, position = VanPopupPosition.Bottom, round = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(title, 16f, FontWeight.SemiBold, colors.textPrimary)
                if (subtitle != null) FText(subtitle, 14f, FontWeight.Normal, colors.textSecondary)
            }
            AppField(
                value = form.content,
                onValueChange = { onFormChange(form.copy(content = it)) },
                label = "事件内容",
                placeholder = "请输入事件内容",
                maxlength = 100,
                trailing = if (requiredTip) {
                    {
                        // web `.field-required-tip`：主危险色描边 + 8% 底 + 12/600
                        Box(
                            Modifier
                                .border(1.dp, tokens.danger, RoundedCornerShape(4.dp))
                                .background(VanRed.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp),
                        ) { FText("必填", 12f, FontWeight.SemiBold, tokens.danger) }
                    }
                } else null,
            )
            AppField(
                value = form.remark,
                onValueChange = { onFormChange(form.copy(remark = it)) },
                label = "备注",
                placeholder = if (requiredTip) "选填" else "选填（最多50字）",
                maxlength = 50,
            )
            // 事件类型
            RadioField("事件类型") {
                VanRadioGroup(
                    value = form.eventType,
                    onValueChange = { onFormChange(form.copy(eventType = it)) },
                    direction = "horizontal",
                ) {
                    listOf("schedule" to (if (requiredTip) "日程" else "日程"), "birthday" to "生日",
                        "anniversary" to (if (requiredTip) "纪念" else "纪念日"), "countdown" to (if (requiredTip) "倒数" else "倒数日"))
                        .forEach { (v, label) -> VanRadio(name = v, label = label) }
                }
            }
            // 优先级
            RadioField("优先级") {
                VanRadioGroup(
                    value = form.priority.toString(),
                    onValueChange = { onFormChange(form.copy(priority = it.toIntOrNull() ?: 2)) },
                    direction = "horizontal",
                ) {
                    VanRadio(name = "1", label = "高")
                    VanRadio(name = "2", label = "中")
                    VanRadio(name = "3", label = "低")
                }
            }
            // 每年重复
            SwitchField("每年重复", form.isRecurring) { onFormChange(form.copy(isRecurring = it)) }
            // 开启提醒
            SwitchField("开启提醒", form.needRemind) { onFormChange(form.copy(needRemind = it)) }
            if (form.needRemind) {
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText("提前天数", 13f, FontWeight.Normal, colors.textSecondary)
                    VanStepper(
                        value = form.remindDays,
                        onValueChange = { onFormChange(form.copy(remindDays = it)) },
                        min = 0,
                        max = 30,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = "取消",
                    size = AppButtonSize.Large,
                    round = true,
                    modifier = Modifier.weight(1f),
                    onClick = onClose,
                )
                AppButton(
                    text = confirmText,
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Large,
                    round = true,
                    loading = submitting,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

/** web `<app-field label>` 包一组控件（标签在上、控件在下）。 */
@Composable
private fun RadioField(label: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText(label, 13f, FontWeight.Normal, colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        content()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

/** web `<app-field label> + van-switch`（开关靠右）。 */
@Composable
private fun SwitchField(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 13f, FontWeight.Normal, colors.textSecondary)
        VanSwitch(checked = checked, onCheckedChange = onChange)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}


