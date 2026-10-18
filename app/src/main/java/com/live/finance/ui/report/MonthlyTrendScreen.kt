package com.live.finance.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.YearMonthSaver
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
import com.live.vant.icon.VanIcon
import com.live.vant.other.VanCalendar
import com.live.vant.other.VanCalendarType
import com.live.vant.theme.VantColors
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/** web `showCount = 5`：初始窗口只显示最后 5 天（原生用横向滚动代替 dataZoom）。 */
private const val TREND_VISIBLE_DAYS = 5

/**
 * 图表高度 = **232dp**（原 290dp 的 4/5）。负责人 2026-09-20：「Y轴过高，需要压缩图表当前高度的 1/5」。
 * 网格层与数据层必须用同一个值（两层是叠放的）。
 */
private const val CHART_HEIGHT_DP = 232

/** `已剔除转账` 开关选中底色（同 StatsOverview：浅色 fallback #f0fff4，深色 --van-green-bg #f0f9eb）。 */
private fun trendGreenBg(isDark: Boolean) = if (isDark) Color(0xFFF0F9EB) else Color(0xFFF0FFF4)

/** 每日趋势点（web `dailyTrend` 元素：date + 收入/支出合计 + 轴标签「日」）。 */
private data class DayTrendPoint(val date: String, val shortLabel: String, val income: Double, val expense: Double)

/**
 * web `formatChartLabelAmount`：<=0 → 空串；>=10000 → `x.xx万`；否则千分位最多 3 位小数。
 * 原生柱状图柱顶数值标签与 y 轴刻度都用它。
 */
private fun formatChartLabelAmount(value: Double): String {
    if (value <= 0.0) return ""
    if (value >= 10000.0) return String.format(java.util.Locale.US, "%.2f", value / 10000.0) + "万"
    return Money.formatAmount(value)
}

/** web `getCardTypeLabel`（MonthlyTrend 版：未知类型原样返回）。 */
private fun trendCardTypeLabel(cardType: String): String = when (cardType) {
    "credit" -> "信用卡"
    "debit" -> "借记卡"
    "virtual_cash" -> "现金"
    "virtual_balance" -> "余额"
    else -> cardType
}

/** web `getCategoryName`（MonthlyTrend 版：还款 → 信用卡还款，否则 category_name || 未知分类）。 */
private fun trendCategoryName(row: FlowRow): String =
    if (row.categoryId == "CATEGORY_REPAY") "信用卡还款" else row.categoryName.ifEmpty { "未知分类" }

/** web `getCardBankIcon`：现金/余额与无卡返回空（BankIcon 走首字兜底块）。 */
private fun trendCardBankIcon(cardId: String, cards: List<Card>, banks: List<Category>): String {
    if (cardId.isEmpty() || cardId == "xxxx" || cardId == "yyyy") return ""
    val card = cards.firstOrNull { it.id == cardId } ?: return ""
    val bank = banks.firstOrNull { it.id == card.bankId } ?: return ""
    return bank.iconUrl
}

/** web `getCardBankName`。 */
private fun trendCardBankName(cardId: String, cards: List<Card>, banks: List<Category>): String {
    if (cardId.isEmpty()) return ""
    val card = cards.firstOrNull { it.id == cardId } ?: return ""
    val bank = banks.firstOrNull { it.id == card.bankId }
    return bank?.name?.ifEmpty { null } ?: card.bankName
}

/** web `getCardInfoText`：无卡 → pay_method || '-'；现金/余额直出；否则「银行 卡型 尾号」按有无拼接。 */
private fun trendCardInfoText(row: FlowRow, cards: List<Card>, banks: List<Category>): String {
    val cardId = row.cardId
    if (cardId.isEmpty()) return row.payMethod.ifEmpty { "-" }
    if (cardId == "xxxx") return "现金"
    if (cardId == "yyyy") return "余额"
    val card = cards.firstOrNull { it.id == cardId } ?: return row.payMethod.ifEmpty { "-" }
    val bank = banks.firstOrNull { it.id == card.bankId }
    val bankName = bank?.name?.ifEmpty { null } ?: card.alias.ifEmpty { card.bankName }
    val cardTypeLabel = trendCardTypeLabel(card.cardType)
    val last4 = card.last4
    return when {
        bankName.isNotEmpty() && cardTypeLabel.isNotEmpty() && last4.isNotEmpty() -> "$bankName $cardTypeLabel $last4"
        bankName.isNotEmpty() && last4.isNotEmpty() -> "$bankName $last4"
        else -> listOf(bankName, cardTypeLabel, last4).firstOrNull { it.isNotEmpty() } ?: row.payMethod.ifEmpty { "-" }
    }
}
/** web `dayjs(date).format('D')`：只取「日」，不带前导零。 */
private fun trendDayLabel(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return d.dayOfMonth.toString()
}

/**
 * 月度收支趋势（web `views/Finance/report/stats/MonthlyTrend.vue`）。
 *
 * 结构：月切换条 → 全部/收入/支出 + 剔除转账 + 换曲线/换柱状 → 每日趋势图（all 时图例含收入/支出）
 * → 空态「暂无数据」→ 选中日期流水明细（分类名 + 银行图标 + 卡信息 + 金额 + 时分）。
 *
 * 口径照抄 web：`dailyTrend` 由 `filteredBaseList` 按 `trans_date` 聚合（剔除转账生效）；
 * 选中日明细由**未过滤**的 allList 按日分组后再叠加剔除转账与方向过滤；点图选日。
 * 图形按拍板不引 echarts：柱状/曲线用 Canvas 等价实现，`dataZoom` 改横向滚动（默认停在最后 5 天）。
 */
@Composable
fun MonthlyTrendScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val graph = App.of(LocalContext.current).graph
    val screenHeight = LocalConfiguration.current.screenHeightDp

    // keep-alive：本页会跳「流水详情」，返回时普通 remember 会丢 ⇒ 月份跳回当月
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var showCalendar by remember { mutableStateOf(false) }
    var allList by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeType by remember { mutableStateOf("all") }
    var excludeTransfer by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf("bar") }
    var selectedDate by remember { mutableStateOf("") }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
    }

    LaunchedEffect(month) {
        loading = true
        allList = when (val r = graph.flow.listRange(
            month.atDay(1).toString(), month.atEndOfMonth().toString(), limit = 10000,
        )) {
            is ApiResult.Ok -> r.data ?: emptyList()
            // web 该页 catch 只 `console.error('加载趋势数据失败')` → 原生静默
            else -> emptyList()
        }
        selectedDate = ""
        loading = false
    }

    val greenBg = trendGreenBg(tokens.isDark)
    val baseList = if (excludeTransfer) allList.filter { it.payType != "转账" } else allList

    // 每日趋势（web dailyTrend：按 trans_date 升序，按方向累加）
    val dailyTrend = remember(baseList, activeType) {
        val items = if (activeType == "all") baseList
        else baseList.filter { it.direction == (if (activeType == "income") 1 else 0) }
        val map = LinkedHashMap<String, DoubleArray>()
        items.forEach { row ->
            val date = row.transDate
            if (date.isEmpty()) return@forEach
            val acc = map.getOrPut(date) { DoubleArray(2) }
            if (row.direction == 1) acc[0] += row.amount else acc[1] += row.amount
        }
        map.entries.sortedBy { it.key }.map { (date, acc) ->
            DayTrendPoint(date, trendDayLabel(date), acc[0], acc[1])
        }
    }

    // 选中日流水明细（web selectedDayItems：来自未过滤的 allList，再叠加两个过滤）
    val selectedDayItems = remember(allList, selectedDate, excludeTransfer, activeType) {
        if (selectedDate.isEmpty()) emptyList()
        else {
            var items = allList.filter { it.transDate == selectedDate }
            if (excludeTransfer) items = items.filter { it.payType != "转账" }
            if (activeType != "all") items = items.filter { it.direction == (if (activeType == "income") 1 else 0) }
            items
        }
    }

    val showBoth = activeType == "all"
    val seriesValues = if (showBoth) dailyTrend.flatMap { listOf(it.income, it.expense) }
    else dailyTrend.map { if (activeType == "income") it.income else it.expense }
    val maxValue = (seriesValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                // 月份切换条
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon(
                        "arrow-left", size = 18.sp, color = colors.primary,
                        modifier = Modifier.clickable { month = month.minusMonths(1) }.padding(4.dp),
                    )
                    FText(
                        "${month.year}年${month.monthValue.toString().padStart(2, '0')}月", 18f, FontWeight.SemiBold,
                        colors.textPrimary,
                        modifier = Modifier.clickable { showCalendar = true }.padding(horizontal = 16.dp),
                    )
                    VanIcon(
                        "arrow", size = 18.sp, color = colors.primary,
                        modifier = Modifier.clickable { month = month.plusMonths(1) }.padding(4.dp),
                    )
                }

                // 类型筛选 + 剔除转账 + 换曲线/换柱状
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("全部" to "all", "收入" to "income", "支出" to "expense").forEach { (label, value) ->
                        TrendTypeChip(label, activeType == value, colors) { activeType = value }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (excludeTransfer) greenBg else colors.bgThird)
                            .clickable { excludeTransfer = !excludeTransfer }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        FText(
                            if (excludeTransfer) "已剔除转账" else "已包含转账", 12f,
                            color = if (excludeTransfer) colors.success else colors.textTertiary,
                        )
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(colors.primaryLight)
                            .clickable { chartMode = if (chartMode == "bar") "line" else "bar" }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VanIcon(
                            if (chartMode == "bar") "chart-trending-o" else "bar-chart-o",
                            size = 12.sp, color = colors.primary,
                        )
                        Spacer(Modifier.width(3.dp))
                        FText(if (chartMode == "bar") "换曲线" else "换柱状", 12f, color = colors.primary)
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        VanLoading(size = 24.sp, type = VanLoadingType.Circular, text = "加载中...")
                    }
                } else {
                    if (dailyTrend.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                FText("每日趋势", 16f, FontWeight.SemiBold, colors.textPrimary)
                                if (showBoth) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(MoneyColor.income))
                                        Spacer(Modifier.width(4.dp))
                                        FText("收入", 12f, color = colors.textTertiary)
                                        Spacer(Modifier.width(4.dp))
                                        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(MoneyColor.expense))
                                        Spacer(Modifier.width(4.dp))
                                        FText("支出", 12f, color = colors.textTertiary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            TrendChart(
                                points = dailyTrend,
                                maxValue = maxValue,
                                showBoth = showBoth,
                                isIncomeOnly = activeType == "income",
                                isLine = chartMode == "line",
                                onPickDay = { selectedDate = it },
                            )
                        }
                    }

                    if (dailyTrend.isEmpty()) {
                        VanEmpty(description = "暂无数据")
                    }
                    // 选中日期的流水明细
                    if (selectedDayItems.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(bottom = 4.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FText("$selectedDate 流水明细", 16f, FontWeight.SemiBold, colors.textPrimary)
                                VanIcon(
                                    "cross", size = 18.sp, color = colors.textTertiary,
                                    modifier = Modifier.clickable { selectedDate = "" }.padding(4.dp),
                                )
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 0.dp).border(1.dp, colors.border)) {
                                selectedDayItems.forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { if (row.id.isNotEmpty()) nav.navigate(com.live.finance.core.nav.Routes.flowDetail(row.id)) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            FText(trendCategoryName(row), 16f, color = colors.textPrimary)
                                            Row(
                                                Modifier.padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                BankIconView(
                                                    src = trendCardBankIcon(row.cardId, cards, banks),
                                                    name = trendCardBankName(row.cardId, cards, banks),
                                                    size = 14.dp,
                                                    rounded = 2.dp,
                                                )
                                                FText(trendCardInfoText(row, cards, banks), 12f, color = colors.textTertiary)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            FText(
                                                (if (row.direction == 1) "+" else "-") + Money.formatAmount(row.amount),
                                                16f, FontWeight.SemiBold,
                                                if (row.direction == 1) MoneyColor.income else MoneyColor.expense,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            FText(row.time, 12f, color = colors.textTertiary)
                                        }
                                    }
                                    if (row !== selectedDayItems.last()) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }

    VanCalendar(
        show = showCalendar,
        onClose = { showCalendar = false },
        type = VanCalendarType.Single,
        minDate = LocalDate.of(2020, 1, 1),
        maxDate = LocalDate.now(),
        defaultDate = listOf(month.atDay(1)),
        onConfirm = { dates ->
            dates.firstOrNull()?.let { month = YearMonth.from(it) }
            showCalendar = false
        },
    )
}
@Composable
private fun TrendTypeChip(label: String, active: Boolean, colors: VantColors, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (active) colors.primary else colors.bgThird)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        FText(label, 12f, color = if (active) Color.White else colors.textTertiary)
    }
}

/**
 * 每日趋势图（web echarts 的 Canvas 等价）：柱状（barWidth 35%、barGap 15%、圆角柱顶、柱顶数值标签）
 * 或曲线（smooth + symbol circle 6 + 面积渐变、无标签）；点击某天 → 回调选中该日。
 *
 * 与 web 的差异（均已按拍板确认不处理）：无 dataZoom（改横向滚动，默认停在最后 5 天）；
 * 数值标签与轴标签取主题文字色（web 硬编码 #323233/#969799，浅色下完全一致、深色下更可读）。
 * ⚠ 原这里还写着「无 y 轴刻度/网格线」—— 第二十二轮负责人打回后已补：网格层固定可视区
 *   （6 条 dashed、`grid.left:46`）、刻度值 nice 取整，不再是"数据最大值直接顶格"。
 */
@Composable
private fun TrendChart(
    points: List<DayTrendPoint>,
    maxValue: Double,
    showBoth: Boolean,
    isIncomeOnly: Boolean,
    isLine: Boolean,
    onPickDay: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val incomeColor = MoneyColor.income
    val expenseColor = MoneyColor.expense
    // ⚠ 图表内的柱顶数值与轴标签**不参与** 7.9 提档：柱宽 = 屏宽/5，标签放大到 12 会前后叠字。
    // 图表按「暂不处理」（构造书 10.3）保持 web 的 10px。
    val valueStyle = TextStyle(color = colors.textPrimary, fontSize = 10.sp)
    val axisStyle = TextStyle(color = colors.textTertiary, fontSize = 10.sp)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perDay: Dp = maxWidth / TREND_VISIBLE_DAYS
        val chartWidth: Dp = perDay * points.size
        val hScroll = rememberScrollState()
        // Y 轴刻度（第二十二轮补）：web `yAxis = { type:'value', minInterval:1, splitLine:{dashed #f0f0f0},
        //   axisLabel:{color:#969799, fontSize:10, formatter:formatChartLabelAmount} }`（MonthlyTrend.vue:360-365），
        //   未写 splitNumber ⇒ echarts 默认 5 等分 = **6 条线**（含 0 与顶格），并把刻度 nice 取整。
        //   原来原生直接把"数据最大值"当顶格、且一条线都不画 ⇒ 刻度文字会出现 1237.4 这种非整值。
        val ticks = remember(maxValue) { chartAxisTicks(maxValue) }
        val axisMax = ticks.last().coerceAtLeast(1.0)
        // web dataZoom 初始窗口 = 最后 5 天 → 原生默认滚到最右（等布局出 maxValue 再滚）
        LaunchedEffect(points.size, isLine) {
            snapshotFlow { hScroll.maxValue }.first { it > 0 }
            hScroll.scrollTo(hScroll.maxValue)
        }
        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
            // ===== 网格层：固定在可视区，不随横向滚动平移 =====
            //   echarts 的 splitLine/axisLabel 属于 grid（`left:46`、`right:8`），dataZoom 只平移 series；
            //   所以这层单独画，数据层整体右移 46dp 与它对齐。
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val xLabelH = 16.dp.toPx()
                val plotTop = 14.dp.toPx()
                val plotBottom = size.height - xLabelH
                val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
                val left = 46.dp.toPx()
                val right = (size.width - 8.dp.toPx()).coerceAtLeast(left + 1f)
                ticks.forEachIndexed { i, v ->
                    val gy = plotBottom - plotH * i / (ticks.size - 1).coerceAtLeast(1)
                    drawLine(
                        // web 是 echarts 里写死的 `color:'#f0f0f0'`；这里取主题的 `border`（浅色 #ebedf0，
                        //   与 web 视觉等价），深色下才不会在白卡上刷出近白的线 —— 沿用本页既有的
                        //   "图表文字取主题色"口径（见下方 KDoc 的偏差说明）。
                        // ⚠ 线宽必须是 `1.dp.toPx()`：写成 `1f` 是 **1 物理像素**（3x 屏 ≈0.33dp），
                        //   负责人 2026-09-20 反馈「Y轴虚线不明显」就是因为线只有 1px、又被虚线间隙切碎。
                        colors.border, Offset(left, gy), Offset(right, gy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                    // web `formatChartLabelAmount`：<=0 返回空串 ⇒ 0 那条只有线没有字
                    val label = formatChartLabelAmount(v)
                    if (label.isNotEmpty()) {
                        val r = measurer.measure(label, axisStyle)
                        drawText(r, topLeft = Offset(left - r.size.width - 4.dp.toPx(), gy - r.size.height / 2f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 46.dp).horizontalScroll(hScroll)) {
                Canvas(
                    Modifier.width(chartWidth).height(CHART_HEIGHT_DP.dp).pointerInput(points) {
                    detectTapGestures { offset ->
                        val idx = (offset.x / perDay.toPx()).toInt().coerceIn(0, points.lastIndex)
                        onPickDay(points[idx].date)
                    }
                },
            ) {
                val xLabelH = 16.dp.toPx()
                val plotTop = 14.dp.toPx()
                val plotBottom = size.height - xLabelH
                val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
                val perDayPx = perDay.toPx()
                val barW = (perDayPx * 0.35f).coerceAtLeast(3f)
                val barGap = barW * 0.15f

                fun xAt(i: Int) = perDayPx * i + perDayPx / 2f
                fun yAt(v: Double) = plotBottom - (v / axisMax).toFloat() * plotH

                // 顶部圆角柱（web itemStyle.borderRadius [4,4,0,0]）
                fun bar(left: Float, width: Float, value: Double, color: Color, label: String) {
                    if (value <= 0.0) return
                    val top = yAt(value)
                    val h = plotBottom - top
                    val rad = 4.dp.toPx().coerceAtMost(h)
                    val p = Path().apply {
                        moveTo(left, plotBottom)
                        lineTo(left, top + rad)
                        quadraticBezierTo(left, top, left + rad, top)
                        lineTo(left + width - rad, top)
                        quadraticBezierTo(left + width, top, left + width, top + rad)
                        lineTo(left + width, plotBottom)
                        close()
                    }
                    drawPath(p, color)
                    if (label.isNotEmpty()) {
                        val r = measurer.measure(label, valueStyle)
                        drawText(
                            r,
                            topLeft = Offset(
                                left + width / 2f - r.size.width / 2f,
                                (top - r.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                            ),
                        )
                    }
                }

                // 曲线 + 面积 + 圆点（web smooth/areaStyle 0.2→0.02、symbol circle 6）
                fun lineSeries(values: List<Double>, color: Color) {
                    if (values.isEmpty()) return
                    val area = Path()
                    values.forEachIndexed { i, v ->
                        if (i == 0) area.moveTo(xAt(0), yAt(v)) else area.lineTo(xAt(i), yAt(v))
                    }
                    area.lineTo(xAt(values.lastIndex), plotBottom)
                    area.lineTo(xAt(0), plotBottom)
                    area.close()
                    drawPath(
                        area,
                        Brush.verticalGradient(
                            listOf(color.copy(alpha = 0x33 / 255f), color.copy(alpha = 0x05 / 255f)),
                            startY = plotTop,
                            endY = plotBottom,
                        ),
                    )
                    val line = Path()
                    values.forEachIndexed { i, v ->
                        val px = xAt(i)
                        val py = yAt(v)
                        if (i == 0) line.moveTo(px, py)
                        else {
                            val prevX = xAt(i - 1)
                            val prevY = yAt(values[i - 1])
                            val mx = (prevX + px) / 2f
                            line.cubicTo(mx, prevY, mx, py, px, py)
                        }
                    }
                    drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    values.forEachIndexed { i, v -> drawCircle(color, radius = 3.dp.toPx(), center = Offset(xAt(i), yAt(v))) }
                }

                points.forEachIndexed { i, p ->
                    val cx = xAt(i)
                    if (isLine) {
                        // 曲线模式不画柱与柱顶标签
                    } else if (showBoth) {
                        val total = barW * 2 + barGap
                        bar(cx - total / 2f, barW, p.income, incomeColor, formatChartLabelAmount(p.income))
                        bar(cx - total / 2f + barW + barGap, barW, p.expense, expenseColor, formatChartLabelAmount(p.expense))
                    } else if (isIncomeOnly) {
                        bar(cx - barW / 2f, barW, p.income, incomeColor, formatChartLabelAmount(p.income))
                    } else {
                        bar(cx - barW / 2f, barW, p.expense, expenseColor, formatChartLabelAmount(p.expense))
                    }
                    val r = measurer.measure(p.shortLabel, axisStyle)
                    drawText(r, topLeft = Offset(cx - r.size.width / 2f, plotBottom + 4.dp.toPx()))
                }

                if (isLine) {
                    if (showBoth || isIncomeOnly) lineSeries(points.map { it.income }, incomeColor)
                    if (showBoth || !isIncomeOnly) lineSeries(points.map { it.expense }, expenseColor)
                }
                    }
                }
            }
        }
    }

/**
 * Y 轴刻度（对齐 echarts 默认 `splitNumber:5` ⇒ 6 条线）：把最大值向上取整到
 * 「1 / 2 / 5 × 10^k」的 5 倍，返回自下而上的 6 个刻度值（首个恒为 0）。
 * 不做取整就会出现 1237.4 这类非整刻度，负责人 2026-09-19 打回「缺 Y 轴金额等级」。
 */
private fun chartAxisTicks(maxValue: Double): List<Double> {
    val raw = (maxValue.coerceAtLeast(1.0)) / 5.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val norm = raw / mag
    val step = (if (norm <= 1.0) 1.0 else if (norm <= 2.0) 2.0 else if (norm <= 5.0) 5.0 else 10.0) * mag
    return (0..5).map { it * step }
}