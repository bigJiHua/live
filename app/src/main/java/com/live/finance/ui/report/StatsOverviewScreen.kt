package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.chart.DonutChart
import com.live.finance.ui.common.chart.DonutSlice
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.icon.VanIcon
import com.live.vant.other.VanCalendar
import com.live.vant.other.VanCalendarType
import com.live.vant.theme.VantColors
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.cos
import kotlin.math.sin

/** web `categoryColors`（12 色按「分类首次出现顺序」取，不是按金额排序后的下标）。 */
private val STATS_CATEGORY_COLORS = listOf(
    0xFFEE0A24, 0xFFFF976A, 0xFFFFB300, 0xFF1989FA,
    0xFF07C160, 0xFF7232DD, 0xFF00BCD4, 0xFFE91E63,
    0xFF009688, 0xFFFF5722, 0xFF607D8B, 0xFF795548,
).map { Color(it) }

/** 分类排行行（`category_name` 聚合结果 + 取色）。 */
private data class StatsCategoryRow(val name: String, val amount: Double, val color: Color)

/** `已剔除转账` / `已包含转账` 开关的选中底色：浅色走 fallback `#f0fff4`，深色 = `--van-green-bg #f0f9eb`。 */
private fun statsGreenBg(isDark: Boolean) = if (isDark) Color(0xFFF0F9EB) else Color(0xFFF0FFF4)

/** `--van-danger-bg`：浅色 fallback 与深色定义同值 `#fff0f0`。 */
private val STATS_RED_BG = Color(0xFFFFF0F0)/**
 * 收支概览（web `views/Finance/report/stats/StatsOverview.vue`）。
 *
 * 结构逐段对齐 web：月切换条 → 全部/收入/支出 + 剔除转账 → 4 张汇总卡 → 4 张日均/最大单笔卡
 * → 分类排行色条（前 8 条，点击开明细抽屉）→ 收支占比环形图 → 分类明细抽屉 → 日期选择。
 *
 * 口径照抄 web：数据源 `GET /account?startDate&endDate&limit=10000`（`listRange`）；
 * `excludeTransfer` 过滤 `pay_type != '转账'`；`activeType` 过滤 direction；日均 = 月合计 ÷ 当月天数；
 * 最大单笔取 `filteredBaseList`（不受 activeType 影响）；分类排行在 all 下默认给**支出**。
 * 图形部分按拍板不引 echarts：环形图用 Canvas 等价（web 为 echarts pie 48%~70%）。
 */
@Composable
fun StatsOverviewScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val graph = App.of(LocalContext.current).graph
    val screenHeight = LocalConfiguration.current.screenHeightDp

    var month by remember { mutableStateOf(YearMonth.now()) }
    var showCalendar by remember { mutableStateOf(false) }
    var allList by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeType by remember { mutableStateOf("all") }
    var excludeTransfer by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var drawerCategory by remember { mutableStateOf("") }
    var drawerType by remember { mutableStateOf("all") }

    LaunchedEffect(month) {
        loading = true
        allList = when (val r = graph.flow.listRange(
            month.atDay(1).toString(), month.atEndOfMonth().toString(), limit = 10000,
        )) {
            is ApiResult.Ok -> r.data ?: emptyList()
            // web 该页 catch 只 `console.error('加载统计数据失败')`，无 toast → 原生保持静默
            else -> emptyList()
        }
        loading = false
    }

    val greenBg = statsGreenBg(tokens.isDark)

    // 基础过滤：剔除转账（web filteredBaseList）
    val baseList = if (excludeTransfer) allList.filter { it.payType != "转账" } else allList
    // 类型过滤（web filteredList）
    val list = when (activeType) {
        "income" -> baseList.filter { it.direction == 1 }
        "expense" -> baseList.filter { it.direction == 0 }
        else -> baseList
    }
    var income = 0.0
    var expense = 0.0
    list.forEach { if (it.direction == 1) income += it.amount else expense += it.amount }
    val net = income - expense
    val daysInMonth = month.lengthOfMonth()
    val dailyIncome = income / daysInMonth
    val dailyExpense = expense / daysInMonth
    val maxIncome = (baseList.filter { it.direction == 1 }.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(0.0)
    val maxExpense = (baseList.filter { it.direction == 0 }.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(0.0)

    // 分类排行：全部时默认支出（web categoryStats）；颜色按首次出现顺序取，再过滤 >0 并按金额降序
    val categoryStats = remember(baseList, activeType) {
        val items = if (activeType == "income") baseList.filter { it.direction == 1 }
        else baseList.filter { it.direction == 0 }
        val map = LinkedHashMap<String, Double>()
        items.forEach { row ->
            val name = row.categoryName.ifEmpty { "未分类" }
            map[name] = (map[name] ?: 0.0) + row.amount
        }
        map.entries.mapIndexed { i, (name, amount) ->
            StatsCategoryRow(name, amount, STATS_CATEGORY_COLORS[i % STATS_CATEGORY_COLORS.size])
        }.filter { it.amount > 0 }.sortedByDescending { it.amount }
    }
    val maxCategoryAmount = (categoryStats.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(1.0)

    // 明细抽屉：注意 web 用的是**未过滤转账**的 allList
    val drawerItems = remember(allList, drawerCategory, drawerType) {
        allList.filter { row ->
            (row.categoryName.ifEmpty { "未分类" }) == drawerCategory && when (drawerType) {
                "income" -> row.direction == 1
                "expense" -> row.direction == 0
                else -> true
            }
        }
    }

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
                // 类型筛选 + 剔除转账开关
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("全部" to "all", "收入" to "income", "支出" to "expense").forEach { (label, value) ->
                        StatsTypeChip(label, activeType == value, colors) { activeType = value }
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
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        VanLoading(size = 24.sp, type = VanLoadingType.Circular, text = "加载中...")
                    }
                } else {
                    // 汇总卡：收入 / 支出 / 结余 / 笔数
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsSummaryCard(
                                "收入", "+" + Money.formatAmount(income), MoneyColor.income,
                                "arrow-down", greenBg, colors.success, Modifier.weight(1f),
                            )
                            StatsSummaryCard(
                                "支出", "-" + Money.formatAmount(expense), MoneyColor.expense,
                                "arrow-up", STATS_RED_BG, colors.danger, Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsSummaryCard(
                                "结余", (if (net >= 0) "+" else "") + Money.formatAmount(net),
                                if (net >= 0) MoneyColor.income else MoneyColor.expense,
                                "chart-trending-o", if (net >= 0) greenBg else STATS_RED_BG,
                                if (net >= 0) colors.success else colors.danger, Modifier.weight(1f),
                            )
                            StatsSummaryCard(
                                "笔数", list.size.toString(), colors.primary,
                                "notes-o", Color(0xFFF0F5FF), colors.primary, Modifier.weight(1f),
                            )
                        }
                    }

                    // 日均 / 最大单笔
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsExtraCard("日均收入", "+" + Money.formatAmount(dailyIncome), MoneyColor.income, Modifier.weight(1f))
                            StatsExtraCard("日均支出", "-" + Money.formatAmount(dailyExpense), MoneyColor.expense, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsExtraCard("最大单笔收入", "+" + Money.formatAmount(maxIncome), MoneyColor.income, Modifier.weight(1f))
                            StatsExtraCard("最大单笔支出", "-" + Money.formatAmount(maxExpense), MoneyColor.expense, Modifier.weight(1f))
                        }
                    }
                    // 分类排行（前 8 条，点击开明细抽屉）
                    if (categoryStats.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                FText(
                                    if (activeType == "income") "分类收入排行" else "分类支出排行", 16f,
                                    FontWeight.SemiBold, colors.textPrimary,
                                )
                                FText("点击分类查看明细", 12f, color = colors.textTertiary)
                            }
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                categoryStats.take(8).forEach { item ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            drawerCategory = item.name
                                            drawerType = "all"
                                            showDrawer = true
                                        },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicText(
                                            item.name,
                                            style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(12f)),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(50.dp),
                                        )
                                        Box(
                                            Modifier.weight(1f).height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)).background(colors.bgThird),
                                        ) {
                                            Box(
                                                Modifier.fillMaxWidth((item.amount / maxCategoryAmount).toFloat().coerceIn(0f, 1f))
                                                    .fillMaxHeight().background(item.color),
                                            )
                                        }
                                        BasicText(
                                            Money.formatAmount(item.amount),
                                            style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
                                            maxLines = 1,
                                            modifier = Modifier.width(60.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 收支占比（web echarts pie，48%~70% 环形）
                    if (income + expense > 0) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
                        ) {
                            FText("收支占比", 16f, FontWeight.SemiBold, colors.textPrimary)
                            Spacer(Modifier.height(12.dp))
                            DonutChart(
                                slices = listOf(
                                    DonutSlice("收入", income.toFloat(), MoneyColor.income),
                                    DonutSlice("支出", expense.toFloat(), MoneyColor.expense),
                                ),
                                innerRadiusRatio = 0.48f,
                                outerRadiusRatio = 0.70f,
                                showLabel = true,
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
    // 分类明细抽屉（web app-popup：bottom / round / max-height 75vh / min-height 35vh）
    VanPopup(
        show = showDrawer,
        onDismissRequest = { showDrawer = false },
        position = VanPopupPosition.Bottom,
        round = true,
        modifier = Modifier.heightIn(min = (screenHeight * 0.35f).dp, max = (screenHeight * 0.75f).dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(drawerCategory, 18f, FontWeight.SemiBold, colors.textPrimary)
                VanIcon(
                    "cross", size = 18.sp, color = colors.textTertiary,
                    modifier = Modifier.clickable { showDrawer = false },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部" to "all", "收入" to "income", "支出" to "expense").forEach { (label, value) ->
                    StatsTypeChip(label, drawerType == value, colors) { drawerType = value }
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = (screenHeight * 0.55f).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            ) {
                if (drawerItems.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                        FText("暂无记录", 14f, color = colors.textTertiary)
                    }
                }
                drawerItems.forEach { row -> StatsDrawerItemRow(row, greenBg) }
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
private fun StatsTypeChip(label: String, active: Boolean, colors: VantColors, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (active) colors.primary else colors.bgThird)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        FText(label, 12f, color = if (active) Color.White else colors.textTertiary)
    }
}

/** web `.summary-card`：38×38 圆角 10 图标块 + label 11px + value 16px/700（超长省略号）。 */
@Composable
private fun StatsSummaryCard(
    label: String,
    value: String,
    valueColor: Color,
    icon: String,
    iconBg: Color,
    iconFg: Color,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) { VanIcon(icon, size = 18.sp, color = iconFg) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            FText(label, 12f, color = colors.textTertiary)
            Spacer(Modifier.height(2.dp))
            BasicText(
                value,
                style = TextStyle(color = valueColor, fontSize = scaledSp(18f), fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** web `.extra-card`：label 11px + value 15px/700。 */
@Composable
private fun StatsExtraCard(label: String, value: String, valueColor: Color, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(colors.bgCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        FText(label, 12f, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FText(value, 16f, FontWeight.Bold, valueColor)
    }
}

/** web `.drawer-item`：32 圆形收支图标 + 分类/金额 + trans_date/备注，底部 1px 分隔线。 */
@Composable
private fun StatsDrawerItemRow(row: FlowRow, greenBg: Color) {
    val colors = LocalAppColors.current
    val isIncome = row.direction == 1
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (isIncome) greenBg else STATS_RED_BG),
                contentAlignment = Alignment.Center,
            ) {
                VanIcon(
                    if (isIncome) "arrow-down" else "arrow-up", size = 14.sp,
                    color = if (isIncome) colors.success else colors.danger,
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(row.categoryName, 14f, FontWeight.Medium, colors.textPrimary)
                    FText(
                        (if (isIncome) "+" else "-") + Money.formatAmount(row.amount), 16f, FontWeight.Bold,
                        if (isIncome) MoneyColor.income else MoneyColor.expense,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FText(row.transDate, 12f, color = colors.textTertiary)
                    FText(row.remark.ifEmpty { "无备注" }, 12f, color = colors.textTertiary)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
