package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Bill
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 账单流水明细 —— 一比一复刻 web `views/BankCard/bill/Ledger.vue`。
 *
 * 对账（原文）：仅累加 `direction === 0` 的 `amount`；一致判定 `|expense − bill_amount| < 0.01`
 * 且 `bill_amount` 为真值；差异提示额外要求 `expense > 0`。
 * 分组键 = `trans_date` 原始串（空值**丢弃**），组序随接口（`trans_date DESC`）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BillLedgerScreen(nav: NavHostController, billId: String = "") {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    var bill by remember { mutableStateOf<Bill?>(null) }
    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var finished by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var requesting by remember { mutableStateOf(false) }

    val limit = 50
    val startDate = bill?.billStartDate?.take(10).orEmpty()
    val endDate = bill?.billEndDate?.take(10).orEmpty()

    // 流水支出汇总（仅 direction == 0）
    val summaryExpense = remember(rows) { rows.filter { it.direction == 0 }.sumOf { it.amount } }
    val summaryCount = remember(rows) { rows.count { it.direction == 0 } }
    val isMatched = bill?.let { b -> b.billAmount != 0.0 && kotlin.math.abs(summaryExpense - b.billAmount) < 0.01 } == true

    fun load(reset: Boolean) {
        if (requesting || bill == null) return
        scope.launch {
            requesting = true
            loading = true
            val p = if (reset) 1 else page
            when (val r = graph.flow.listByCard(bill!!.cardId, p, limit, startDate, endDate)) {
                is ApiResult.Ok -> {
                    val data = r.data.orEmpty()
                    rows = if (reset) data else rows + data
                    finished = data.size < limit
                    if (!finished) page = p + 1 else page = p
                }
                else -> { finished = true; if (reset) rows = emptyList() }
            }
            loading = false
            requesting = false
        }
    }

    LaunchedEffect(billId) {
        if (billId.isBlank()) { nav.popBackStack(); return@LaunchedEffect }
        pageLoading = true
        when (val r = graph.bill.detail(billId)) {
            is ApiResult.Ok -> bill = r.data
            else -> { pageLoading = false; nav.popBackStack(); return@LaunchedEffect }
        }
        load(reset = true)
        pageLoading = false
    }

    // 触底加载下一页
    LaunchedEffect(bill, finished) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (bill != null && !finished && !loading && !requesting && rows.isNotEmpty() && last >= rows.size - 2) {
                    load(reset = false)
                }
            }
    }

    // 按 trans_date 分组（保持接口顺序）
    val grouped: List<Pair<String, List<FlowRow>>> = remember(rows) {
        val order = ArrayList<String>()
        val map = LinkedHashMap<String, MutableList<FlowRow>>()
        rows.forEach { r ->
            if (r.transDate.isBlank()) return@forEach
            if (!map.containsKey(r.transDate)) { map[r.transDate] = mutableListOf(); order.add(r.transDate) }
            map[r.transDate]!!.add(r)
        }
        order.map { it to map[it].orEmpty() }
    }

    ScreenScaffold { inner ->
        VanPullRefresh(
            state = refreshState,
            // 列表滚到顶才允许下拉刷新（Vant 语义）
            isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
            onRefresh = { scope.launch { load(reset = true); refreshState.finishRefresh() } },
            modifier = Modifier.then(inner),
        ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // ===== 账单信息 =====
            item {
                val b = bill
                if (b != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(colors.bgCard).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BankIconView(src = "", name = b.cardAlias.ifBlank { "信用卡" }, size = 28.dp, rounded = 4.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            FText(b.cardAlias.ifBlank { "信用卡" }, 15f, FontWeight.SemiBold, colors.textPrimary)
                            if (b.cardLast4.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                FText("**** ${b.cardLast4}", 12f, color = colors.textTertiary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            FText("${dateOnly(b.billStartDate)} ~ ${dateOnly(b.billEndDate)}", 12f, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            FText("${b.billMonth} 账单", 11f, color = colors.primary)
                        }
                    }
                }
            }
            // ===== 对账统计 =====
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(colors.bgCard).padding(12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    StatCol("账单金额", "¥${Money.formatMoney(bill?.billAmount ?: 0.0)}", MoneyColor.expense)
                    StatCol("流水支出", "¥${Money.formatMoney(summaryExpense)}", MoneyColor.expense)
                    StatCol("流水笔数", "$summaryCount", colors.textPrimary)
                }
                val tipColor = if (isMatched) colors.success else colors.danger
                if (isMatched || summaryExpense > 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        VanIcon(if (isMatched) "success" else "cross", size = 12.sp, color = tipColor)
                        Spacer(Modifier.width(4.dp))
                        FText(
                            if (isMatched) "流水金额与账单一致" else "流水与账单金额有差异",
                            12f, color = tipColor,
                        )
                    }
                }
            }
            // ===== 流水分组列表 =====
            grouped.forEach { (date, items) ->
                stickyHeader(key = "h_$date") {
                    Box(Modifier.fillMaxWidth().background(colors.bgPage).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        FText(dateHeaderText(date), 13f, color = colors.textSecondary)
                    }
                }
                items(items.size, key = { i -> items[i].id }) { i ->
                    FlowLine(items[i]) { nav.navigate(Routes.flowDetail(items[i].id)) }
                }
            }
            if (!pageLoading && grouped.isEmpty() && bill != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        VanEmpty(description = "该账单周期内暂无流水记录")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }

        if (pageLoading) {
            Box(Modifier.fillMaxSize().then(inner).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { VanLoading() }
        }
    }
}

@Composable
private fun StatCol(label: String, value: String, color: Color) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FText(label, 12f, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        FText(value, 16f, FontWeight.Bold, color)
    }
}

/** 单条流水行（web `.flow-item`；还款行 grayscale+半透明）。 */
@Composable
private fun FlowLine(item: FlowRow, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val isRepay = item.categoryId == "CATEGORY_REPAY"
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (isRepay) 0.55f else 1f)
            .clickable { onClick() }
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.bgPage), contentAlignment = Alignment.Center) {
            VanIcon(categoryIcon(item.categoryName), size = 20.sp, color = colors.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FText(if (isRepay) "信用卡还款" else item.categoryName.ifBlank { "未知分类" }, 15f, FontWeight.Medium, colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            FText("${item.payMethod.ifBlank { "-" }} · ${hhmm(item.createTime)}", 12f, color = colors.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (item.direction == 1) "+" else "-"
            FText("$sign${symbolOf(item.currency)}${Money.formatMoney(item.amount)}", 16f, FontWeight.Bold,
                if (item.direction == 1) MoneyColor.income else colors.textPrimary)
            if (item.currency.isNotBlank() && item.currency != "CNY") {
                Spacer(Modifier.height(2.dp))
                FText("≈ ¥${Money.formatMoney(item.amount * item.exchangeRate / 100)}", 11f, color = colors.textTertiary)
            }
        }
    }
}

private fun categoryIcon(name: String): String = when (name) {
    "餐饮" -> "orders-o"; "购物" -> "shopping-cart-o"; "交通" -> "logistics"
    "娱乐" -> "gem-o"; "工资" -> "paid"; "还款" -> "credit-pay"; else -> "balance-o"
}

private fun symbolOf(code: String): String = when (code) {
    "CNY" -> "¥"; "USD" -> "$"; "EUR" -> "€"; "HKD" -> "HK$"; "JPY" -> "¥"
    "GBP" -> "£"; "KRW" -> "₩"; "TWD" -> "NT$"; else -> code.ifBlank { "¥" }
}

private fun hhmm(ts: String): String = if (ts.length >= 16) ts.substring(11, 16) else ""

/** web `formatDateHeader`：今天 / 昨天 / `M月D日 周X`。 */
private fun dateHeaderText(date: String): String {
    val d = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return date
    val today = LocalDate.now()
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> {
            val w = when (d.dayOfWeek.value) { 1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日" }
            "${d.monthValue}月${d.dayOfMonth}日 周$w"
        }
    }
}
