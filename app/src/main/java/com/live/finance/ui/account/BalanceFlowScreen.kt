package com.live.finance.ui.account

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import com.live.finance.App
import com.live.finance.core.TimeFmt
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** 余额账户 card_id（与 web Structure/BalanceFlow 虚拟账户配置一致）。 */
private const val BALANCE_CARD_ID = "yyyy"
/** 信用卡还款分类（后端特殊 category_id，无分类表记录）。 */
private const val CATEGORY_REPAY = "CATEGORY_REPAY"
/** 还款项底色：rgba(255,151,106,.08)。 */
private val REPAY_BG = Color(0x14FF976A)

private data class DayGroup(
    val date: String,
    val incomeItems: List<FlowRow>,
    val expenseItems: List<FlowRow>,
) {
    val income get() = incomeItems.sumOf { it.amount }
    val expense get() = expenseItems.sumOf { it.amount }
    val balance get() = income - expense
}

/**
 * 余额流水明细（web `views/Finance/account/BalanceFlow.vue`，组件名 BalanceFlow）：
 * 顶部月份总览（余额/支出/收入/净值）→ 按日分组、默认全展开、支出|收入左右对账。
 * 数据固定取余额账户 yyyy 的当月流水（GET /account/list?cardId=yyyy）。
 */
@Composable
fun BalanceFlowScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val toast = LocalVanToastController.current

    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }
    var showPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var totalBalance by remember { mutableStateOf(0.0) }
    var items by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    // 默认全部展开：用 collapsed 集合管理（空=全展开）
    val collapsed = remember { mutableStateListOf<String>() }

    fun monthRange(): Pair<String, String> {
        val c = Calendar.getInstance().apply { set(year, month - 1, 1, 0, 0, 0) }
        val start = "%04d-%02d-01".format(year, month)
        val lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val end = "%04d-%02d-%02d".format(year, month, lastDay)
        return start to end
    }

    LaunchedEffect(year, month) {
        loading = true
        val (start, end) = monthRange()
        val flow = graph.flow.listByCard(BALANCE_CARD_ID, page = 1, limit = 10000,
            startDate = start, endDate = end)
        items = (flow as? ApiResult.Ok)?.data ?: emptyList()
        val bal = (graph.balance.list() as? ApiResult.Ok)?.data
            ?.firstOrNull { it.cardId == BALANCE_CARD_ID }?.balance ?: 0.0
        totalBalance = bal
        loading = false
    }

    val groups = remember(items) {
        items.groupBy { it.day }
            .map { (date, list) ->
                DayGroup(
                    date = date,
                    incomeItems = list.filter { it.isIncome },
                    expenseItems = list.filter { !it.isIncome },
                )
            }
            .sortedByDescending { it.date }
    }
    val monthIncome = groups.sumOf { it.income }
    val monthExpense = groups.sumOf { it.expense }
    val monthBalance = monthIncome - monthExpense
    fun isCollapsed(date: String) = collapsed.contains(date)
    fun toggle(date: String) {
        if (collapsed.contains(date)) collapsed.remove(date) else collapsed.add(date)
    }

    val years = remember { (year - 5)..(year + 5) }

    ScreenScaffold { inner ->
        Box(inner) {
            LazyColumn(Modifier.fillMaxSize()) {
                // —— 月份总览 ——
                item {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            .fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { showPicker = true },
                                ) {
                                    FText("${year}年${month}月", 16f, FontWeight.SemiBold, colors.textPrimary)
                                    VanIcon("arrow-down", size = 12.sp, color = colors.textPrimary,
                                        modifier = Modifier.padding(start = 4.dp))
                                }
                                FText("余额", 12f, color = colors.textTertiary)
                                FText(compact(totalBalance), 22f, FontWeight.Bold, colors.textPrimary)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OverviewRow("支出", "-" + compact(abs(monthExpense)), MoneyColor.expense)
                                OverviewRow("收入", "+" + compact(monthIncome), MoneyColor.income)
                                OverviewRow(
                                    "净值",
                                    (if (monthBalance >= 0) "+" else "-") + compact(abs(monthBalance)),
                                    if (monthBalance >= 0) MoneyColor.income else MoneyColor.expense,
                                )
                            }
                        }
                    }
                }

                // —— 按日分组 ——
                items(groups, key = { it.date }) { g ->
                    DayGroupCard(
                        g = g,
                        collapsed = isCollapsed(g.date),
                        onToggle = { toggle(g.date) },
                        onClickItem = { row ->
                            if (row.id.isNotEmpty()) nav.navigate(Routes.flowDetail(row.id))
                            else toast.show("无详情")
                        },
                    )
                }

                if (!loading && groups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center) {
                            VanEmpty(description = "本月暂无余额流水明细")
                        }
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize().background(colors.bgPage), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            }
        }
    }

    // —— 年月双列选择（年今年±5） ——
    if (showPicker) {
        VanPopup(show = true, onDismissRequest = { showPicker = false }) {
            VanPicker(
                columns = listOf(
                    years.map { VanPickerOption("${it}年", it) },
                    (1..12).map { VanPickerOption("${it}月", it) },
                ),
                value = listOf(
                    years.indexOf(year).coerceAtLeast(0),
                    (month - 1).coerceAtLeast(0),
                ),
                title = "选择月份",
                onConfirm = { _, vals ->
                    (vals[0] as? Int)?.let { year = it }
                    (vals[1] as? Int)?.let { month = it }
                    showPicker = false
                },
                onCancel = { showPicker = false },
            )
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String, valueColor: Color) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FText(label, 12f, color = colors.textTertiary,
            modifier = Modifier.width(36.dp))
        FText(value, 14f, FontWeight.SemiBold, valueColor)
    }
}

@Composable
private fun DayGroupCard(
    g: DayGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onClickItem: (FlowRow) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard),
    ) {
        // 日父节点
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    FText(dayLabel(g.date), 15f, FontWeight.SemiBold, colors.textPrimary)
                    FText(weekLabel(g.date), 12f, color = colors.textTertiary,
                        modifier = Modifier.padding(start = 6.dp))
                }
                FText(
                    (if (g.balance >= 0) "+" else "-") + Money.format(abs(g.balance)),
                    13f, FontWeight.SemiBold,
                    if (g.balance >= 0) MoneyColor.income else MoneyColor.expense,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FText("-" + Money.format(g.expense), 13f, color = MoneyColor.expense)
                    FText("+" + Money.format(g.income), 13f, color = MoneyColor.income)
                }
                VanIcon(if (collapsed) "arrow-down" else "arrow-up", size = 14.sp,
                    color = colors.textTertiary, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (!collapsed) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                if (g.expenseItems.isNotEmpty()) {
                    FlowColumn(
                        title = "支出", titleColor = MoneyColor.expense,
                        items = g.expenseItems, sign = "-", onClickItem = onClickItem,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (g.expenseItems.isNotEmpty() && g.incomeItems.isNotEmpty()) {
                    Box(Modifier.width(1.dp).background(colors.border))
                }
                if (g.incomeItems.isNotEmpty()) {
                    FlowColumn(
                        title = "收入", titleColor = MoneyColor.income,
                        items = g.incomeItems, sign = "+", onClickItem = onClickItem,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowColumn(
    title: String,
    titleColor: Color,
    items: List<FlowRow>,
    sign: String,
    onClickItem: (FlowRow) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier.padding(horizontal = 10.dp)) {
        FText(title, 12f, FontWeight.SemiBold, titleColor,
            modifier = Modifier.padding(bottom = 8.dp))
        items.forEach { row ->
            val repay = row.categoryId == CATEGORY_REPAY
            val isCredit = row.accountType.equals("credit", ignoreCase = true)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (repay) REPAY_BG else colors.bgPage)
                    .clickable { onClickItem(row) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        FText(categoryName(row), 13f, color = colors.textPrimary)
                        if (isCredit) {
                            FText("信用卡", 11f, color = colors.primary,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    FText(TimeFmt.hm(row.createTime), 11f, color = colors.textTertiary)
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FText("$sign${Money.format(row.amount)}", 13f, FontWeight.SemiBold, titleColor)
                    FText(bankLabel(row), 11f, color = colors.textTertiary)
                }
            }
        }
    }
}

private fun categoryName(row: FlowRow): String {
    if (row.categoryId == CATEGORY_REPAY) return "信用卡还款"
    return row.categoryName.takeIf { it.isNotBlank() && it != "未知" }
        ?: row.payType.takeIf { it.isNotBlank() }
        ?: "未分类"
}

/** 余额账户自身显示「余额」；跨卡流水显示对方卡尾号（web getCompactBankLabel）。 */
private fun bankLabel(row: FlowRow): String {
    val cid = row.cardId
    return if (cid == BALANCE_CARD_ID || cid.isEmpty()) "余额" else "****${cid.takeLast(4)}"
}

/** "M月D日"（web formatDay）。 */
private fun dayLabel(date: String): String {
    val p = date.split("-")
    if (p.size < 3) return date
    return "${p[1].toIntOrNull() ?: p[1]}月${p[2].toIntOrNull() ?: p[2]}日"
}

/** 周几（web weekOf）。 */
private fun weekLabel(date: String): String {
    val p = date.split("-")
    if (p.size < 3) return ""
    val c = Calendar.getInstance().apply {
        runCatching { set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }
    }
    val names = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    return names[c.get(Calendar.DAY_OF_WEEK) - 1]
}

/** web BalanceFlow.formatCompact：≥亿/万用 2 位小数缩写，否则千分位 2 位。 */
private fun compact(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e8 -> String.format(Locale.US, "%.2f亿", v / 1e8)
        a >= 1e4 -> String.format(Locale.US, "%.2f万", v / 1e4)
        else -> String.format(Locale.US, "%,.2f", v)
    }
}
