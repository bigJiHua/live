package com.live.finance.ui.flow

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.FlowItem
import com.live.finance.ui.common.FlowKind
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.flowCardTypeLabel
import com.live.finance.ui.common.flowCategoryName
import com.live.finance.ui.common.flowCompactBankLabel
import com.live.finance.ui.common.isRepay
import com.live.finance.ui.common.pairDailyItems
import com.live.finance.ui.todo.CalendarVariant
import com.live.finance.ui.todo.DayMark
import com.live.finance.ui.todo.TodoCalendarGrid
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.launch

/** web `--van-green`（buildVars 不覆盖，两模式同值）。 */
private val GreenFixed = Color(0xFF07C160)

/** web `--van-blue`（同上）。 */
private val BlueFixed = Color(0xFF1989FA)

/**
 * 每日流水（日历）—— 一比一复刻 web `views/Finance/flow/Calendar.vue`。
 *
 * ① 页头：`arrow-left` ｜ `YYYY年M月` + `arrow-down`（点开月份 picker，**±5 年**）｜ `arrow`（三个图标都是主色）
 * ② 统计栏：月结余（flex 1）/ 收入 / 支出，中间 1×30 分隔线；**统计与网格/明细同源**（都由当月一次 `/account` 列表累加）
 * ③ 共享网格 [TodoCalendarGrid]（`CalendarVariant.Flow`、`show-header=false`、`card=false`、`show-stat=false`、可折叠）
 *    外部容器 `1px` 边框（**无上边**）—— 与 web `.calendar-grid-wrap` 一致
 * ④ 当日明细卡（`.day-detail` radius 12）：日期 + 当日结余 → **支出/收入双栏**（列头 12/600、
 *    支出 `#fff2f0` 底 / 收入 `#f0fff5` 底、相隔 1px 竖线、空列显示 `-`）→
 *    转账/提现/冲正分区（占位符 `---- 转账 ----`、两张半宽卡片换行）→ 空态「当日无收支记录」+「+ 立即记账」
 *
 * ⚠ 已知 web 口径（照抄，不"修正"）：
 *  - 当日结余 `income - expense` 用的是**原始流水**累加（含转账两腿）；
 *  - 日历页配对函数从**不给**第 1 趟转账组打「提现/冲正」标记（该页的提现只由第 5 趟「余额→银行卡」产出、
 *    冲正只由第 1.5 趟「收入方 pay_type=冲正」产出）——这是 web 原样逻辑。
 */
@Composable
fun CalendarScreen(nav: NavHostController, initYear: Int = 0, initMonth: Int = 0) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    // web `--van-green`/`--van-blue`：浅色取 Vant 默认（#07c160/#1989fa），**深色**被 style.css 重定向到
    // `--theme-success` / `--theme-primary`（与页头箭头同款规则）
    val green = if (tokens.isDark) colors.success else GreenFixed
    val blue = if (tokens.isDark) colors.primary else BlueFixed
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    // web 该页在 keep-alive 名单（FinanceFlowCalendar）→ 返回要保留月份/选中日
    var year by rememberSaveable { mutableStateOf(if (initYear > 0) initYear else today.year) }
    var month by rememberSaveable { mutableStateOf(if (initMonth > 0) initMonth else today.monthValue) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    var showMonthPicker by remember { mutableStateOf(false) }

    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    suspend fun load() {
        loading = true
        // web `limit: 10000`：一次拉全月（否则网格/统计会缺数据）
        rows = when (val r = graph.flow.list(year, month, limit = 10000)) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> emptyList()
        }
        loading = false
    }

    LaunchedEffect(year, month) { load() }
    LaunchedEffect(Unit) {
        cards = (graph.card.list() as? ApiResult.Ok)?.data.orEmpty()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data.orEmpty()
    }

    // 统计：与 web 同源（当月列表按方向累加）
    val income = rows.filter { it.direction == 1 }.sumOf { it.amount }
    val expense = rows.filter { it.direction != 1 }.sumOf { it.amount }
    val balance = income - expense

    // 网格数据：web `calendarDataset`（direction===1 → income，其余 → expense，**转账两腿都算**）
    val dataset: Map<String, DayMark> = remember(rows) {
        rows.groupBy { it.day }.filterKeys { it.isNotEmpty() }.mapValues { (_, list) ->
            DayMark(
                income = list.filter { it.isIncome }.sumOf { it.amount },
                expense = list.filter { !it.isIncome }.sumOf { it.amount },
            )
        }
    }

    // 当日明细（web `dayDetail`）
    val dayRows = remember(rows, selected) { rows.filter { it.day == selected } }
    val dayItems = remember(dayRows, cards) { pairDailyItems(dayRows, cards) }
    val expenseItems = dayItems.filter { it.kind == FlowKind.Flow && it.data?.isExpense == true }
    val incomeItems = dayItems.filter { it.kind == FlowKind.Flow && it.data?.isIncome == true }
    val transferItems = dayItems.filter { it.kind == FlowKind.Transfer }
    val withdrawalItems = dayItems.filter { it.kind == FlowKind.Withdrawal }
    val reversalItems = dayItems.filter { it.kind == FlowKind.Reversal }
    val dayBalance = dayRows.filter { it.isIncome }.sumOf { it.amount } - dayRows.filter { !it.isIncome }.sumOf { it.amount }

    ScreenScaffold { inner ->
        LazyColumn(state = listState, modifier = inner.background(colors.bgPage)) {
            // ① 页头
            item {
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon(
                        name = "arrow-left", size = 18.sp, color = tokens.primary,
                        modifier = Modifier.padding(6.dp),
                        onClick = { shiftMonth(year, month, -1).let { year = it.first; month = it.second } },
                    )
                    Row(
                        modifier = Modifier.clickable { showMonthPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FText("${year}年${month}月", 18f, FontWeight.SemiBold, colors.textPrimary)
                        VanIcon(name = "arrow-down", size = 18.sp, color = tokens.primary)
                    }
                    VanIcon(
                        name = "arrow", size = 18.sp, color = tokens.primary,
                        modifier = Modifier.padding(6.dp),
                        onClick = { shiftMonth(year, month, 1).let { year = it.first; month = it.second } },
                    )
                }
            }

            // ② 统计栏（月结余 / 收入 / 支出）
            item {
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatItem("月结余", signAmount(balance), if (balance >= 0) MoneyColor.income else MoneyColor.expense, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(30.dp).background(colors.border))
                    StatItem("收入", "+" + Money.abbrev(income), MoneyColor.income)
                    StatItem("支出", "-" + Money.abbrev(expense), MoneyColor.expense)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }

            // ③ 日历网格（1px 边框但无上边）
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bgCard)
                        .drawBehind {
                            val w = 1.dp.toPx()
                            drawRect(colors.border, topLeft = Offset(0f, 0f), size = Size(w, size.height))
                            drawRect(colors.border, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
                            drawRect(colors.border, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
                        },
                ) {
                    if (loading) {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            VanLoading(size = 32.sp, text = "加载中...")
                        }
                    } else {
                        TodoCalendarGrid(
                            year = year,
                            month = month,
                            selectedDate = selected,
                            dataset = dataset,
                            onSelect = { selected = it },
                            onGoToday = {
                                val now = LocalDate.now()
                                val sameMonth = year == now.year && month == now.monthValue
                                year = now.year
                                month = now.monthValue
                                selected = now.toString()
                                if (!sameMonth) scope.launch { load() }   // web：仅跨月才重拉
                            },
                            variant = CalendarVariant.Flow,
                            collapsible = true,
                            defaultExpanded = true,
                        )
                    }
                }
            }

            // ④ 当日明细
            item { DayDetailCard(selected, dayBalance, dayItems, expenseItems, incomeItems, transferItems, withdrawalItems, reversalItems, cards, banks, nav) }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showMonthPicker) {
        val years = remember { (today.year - 5..today.year + 5).toList() }
        VanPopup(show = true, onDismissRequest = { showMonthPicker = false }, position = VanPopupPosition.Bottom, round = true) {
            VanPicker(
                columns = listOf(
                    years.map { VanPickerOption("${it}年", it.toString()) },
                    (1..12).map { VanPickerOption("${it}月", it.toString()) },
                ),
                value = listOf(years.indexOf(year).coerceAtLeast(0), (month - 1).coerceIn(0, 11)),
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
}

/** 月份加减（跨年回绕）。 */
private fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    var y = year
    var m = month + delta
    while (m > 12) { m -= 12; y++ }
    while (m < 1) { m += 12; y-- }
    return y to m
}

/** web `.stat-label` 12/三级 + `.stat-value` 18/600。 */
@Composable
private fun StatItem(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        FText(value, 18f, FontWeight.SemiBold, valueColor)
    }
}

/** `+/-` + 缩写金额（**取绝对值**，避免 `Money.abbrev` 自带的负号叠加成 `--`）。 */
private fun signAmount(v: Double): String = (if (v >= 0) "+" else "-") + Money.abbrev(abs(v))

/** web `.day-detail` 当日明细卡。 */
@Composable
private fun DayDetailCard(
    selected: String,
    dayBalance: Double,
    dayItems: List<FlowItem>,
    expenseItems: List<FlowItem>,
    incomeItems: List<FlowItem>,
    transferItems: List<FlowItem>,
    withdrawalItems: List<FlowItem>,
    reversalItems: List<FlowItem>,
    cards: List<Card>,
    banks: List<Category>,
    nav: NavHostController,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val green = if (tokens.isDark) colors.success else GreenFixed
    val blue = if (tokens.isDark) colors.primary else BlueFixed
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard),
    ) {
        // `.detail-header`：padding 16 + 1px 边框
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(formatDetailDate(selected), 15f, FontWeight.SemiBold, colors.textPrimary)
            FText(
                signAmount(dayBalance), 14f, FontWeight.SemiBold,
                if (dayBalance >= 0) MoneyColor.income else MoneyColor.expense,
            )
        }
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
            val hasColumns = expenseItems.isNotEmpty() || incomeItems.isNotEmpty()
            if (hasColumns) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayColumn("支出", income = false, items = expenseItems, cards = cards, banks = banks, nav = nav, modifier = Modifier.weight(1f))
                    Box(Modifier.width(1.dp).heightIn(min = 40.dp).background(colors.border))
                    DayColumn("收入", income = true, items = incomeItems, cards = cards, banks = banks, nav = nav, modifier = Modifier.weight(1f))
                }
            }
            PairedSection("---- 转账 ----", null, transferItems, cards, banks)
            PairedSection("---- 提现 ----", green, withdrawalItems, cards, banks)
            PairedSection("---- 冲正 ----", colors.textTertiary, reversalItems, cards, banks)

            if (dayItems.isEmpty()) {
                VanEmpty(description = "当日无收支记录")
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    AppButton(
                        text = "立即记账",
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Small,
                        round = true,
                        icon = "plus",
                        onClick = { nav.navigate(Routes.ACCOUNT_ADD) },
                    )
                }
            }
        }
    }
}

/** 转账/提现/冲正分区：分隔线（`lineColor` 为空取主色）+ 两张半宽卡片换行。 */
@Composable
private fun PairedSection(
    title: String,
    lineColor: Color?,
    items: List<FlowItem>,
    cards: List<Card>,
    banks: List<Category>,
) {
    if (items.isEmpty()) return
    val tokens = LocalAppTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        // `.transfer-divider`：12px 主色 / 500 / letter-spacing 2px / 居中
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            BasicText(
                title,
                style = TextStyle(
                    color = lineColor ?: tokens.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
            )
        }
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { item ->
                    PairedMiniCard(item, cards, banks, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 日历页配对卡片（web `.transfer-row` / `.withdrawal-row` / `.reversal-row`：半宽、圆角 10）。 */
@Composable
private fun PairedMiniCard(
    item: FlowItem,
    cards: List<Card>,
    banks: List<Category>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val green = if (tokens.isDark) colors.success else GreenFixed
    val blue = if (tokens.isDark) colors.primary else BlueFixed
    val expense = item.expense ?: return
    val income = item.income ?: return
    // 三套配色（web `.transfer-row` / `.withdrawal-row` / `.reversal-row`）：
    // 转账=蓝色虚线 + #f0f7ff 底；提现=绿色实线 + rgba(7,193,96,.06) 底；冲正=灰色虚线 + 三级底 + opacity .72
    val bgColor: Color
    val bdColor: Color
    var isDashed = true
    val lblColor: Color
    val bkColor: Color
    val a: Float
    when (item.kind) {
        FlowKind.Withdrawal -> {
            bgColor = Color(0x0F07C160); bdColor = Color(0x4007C160); isDashed = false
            lblColor = green; bkColor = green; a = 1f
        }
        FlowKind.Reversal -> {
            bgColor = colors.bgThird; bdColor = colors.border; isDashed = true
            lblColor = colors.textTertiary; bkColor = colors.textTertiary; a = 0.72f
        }
        else -> {
            bgColor = Color(0xFFF0F7FF); bdColor = blue; isDashed = true
            lblColor = tokens.primary; bkColor = colors.textSecondary; a = 1f
        }
    }

    Column(
        modifier = modifier
            .alpha(a)
            .background(bgColor, RoundedCornerShape(10.dp))
            .then(
                if (isDashed) Modifier.flowDashedBorder(bdColor, 10.dp, 1.dp)
                else Modifier.border(1.dp, bdColor, RoundedCornerShape(10.dp)),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            FText(flowTime(expense.createTime), 10f, FontWeight.Normal, colors.textTertiary)
            FText(
                when (item.kind) {
                    FlowKind.Withdrawal -> "提现"
                    FlowKind.Reversal -> "冲正"
                    else -> "转账"
                },
                11f, FontWeight.SemiBold, lblColor,
            )
            FText(flowTime(income.createTime), 10f, FontWeight.Normal, colors.textTertiary)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            FText("-" + Money.abbrev(expense.amount), 16f, FontWeight.Bold, if (item.kind == FlowKind.Reversal) colors.textTertiary else MoneyColor.expense)
            FText("→", 16f, FontWeight.Bold, lblColor)
            FText("+" + Money.abbrev(income.amount), 16f, FontWeight.Bold, if (item.kind == FlowKind.Reversal) colors.textTertiary else MoneyColor.income)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FText(flowCompactBankLabel(expense, cards, banks), 10f, FontWeight.Normal, bkColor, Modifier.weight(1f, fill = false))
            FText(flowCompactBankLabel(income, cards, banks), 10f, FontWeight.Normal, bkColor, Modifier.weight(1f, fill = false))
        }
    }
}

/** 双栏里的一列（web `.flow-col`）。 */
@Composable
private fun DayColumn(
    title: String,
    income: Boolean,
    items: List<FlowItem>,
    cards: List<Card>,
    banks: List<Category>,
    nav: NavHostController,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (income) Color(0xFFF0FFF5) else Color(0xFFFFF2F0), RoundedCornerShape(6.dp))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FText(title, 12f, FontWeight.SemiBold, if (income) MoneyColor.income else MoneyColor.expense)
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                FText("-", 13f, FontWeight.Normal, colors.textTertiary)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    item.data?.let { row ->
                        DayColumnItem(row, cards, banks, Modifier.padding(bottom = 5.dp)) {
                            nav.navigate(Routes.flowDetail(row.id))
                        }
                    }
                }
            }
        }
    }
}

/** 双栏流水项（web `.flow-item-col`：页面底色底 + 6/10 + radius 8）。 */
@Composable
private fun DayColumnItem(
    row: FlowRow,
    cards: List<Card>,
    banks: List<Category>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val mono = colors.bgPage.luminance() < 0.5f
    val dim = isRepay(row) && !mono
    val amount = Money.abbrev(row.amount)
    val cardType = flowCardTypeLabel(row)
    val wrapAmount = row.amount > 999.99

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.55f else 1f)
            .background(colors.bgPage, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText(flowCategoryName(row), 12f, FontWeight.Normal, colors.textPrimary)
                if (cardType.isNotEmpty()) {
                    FText(cardType, 9f, FontWeight.Normal, colors.textTertiary, Modifier.padding(start = 2.dp))
                }
            }
            FText(flowTime(row.createTime), 10f, FontWeight.Normal, colors.textTertiary)
        }
        val amtColor = if (dim) colors.textTertiary else if (row.isIncome) MoneyColor.income else MoneyColor.expense
        if (wrapAmount) {
            // web `.fi-line2-wrap`：金额占一行，银行标签整行右对齐
            Column(Modifier.fillMaxWidth()) {
                FText((if (row.isIncome) "+" else "-") + amount, 17f, FontWeight.SemiBold, amtColor)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    FText(flowCompactBankLabel(row, cards, banks), 10f, FontWeight.Normal, colors.textSecondary)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                FText((if (row.isIncome) "+" else "-") + amount, 17f, FontWeight.SemiBold, amtColor)
                // web `.fi-bank`：10px、右对齐、超宽横向可滑 → 这里用单行省略（等价观感）
                Box(Modifier.weight(1f).padding(start = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    BasicText(
                        flowCompactBankLabel(row, cards, banks),
                        style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** web `formatDetailDate`：`M月D日 周X`。 */
private fun formatDetailDate(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val week = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[d.dayOfWeek.value % 7]
    return "${d.monthValue}月${d.dayOfMonth}日 $week"
}
