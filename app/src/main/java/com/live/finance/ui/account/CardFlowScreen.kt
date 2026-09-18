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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanNavBar
import com.live.vant.nav.VanTabItem
import com.live.vant.nav.VanTabs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/**
 * 银行卡收支明细 —— 一比一复刻 web `views/Finance/report/flow/CardFlow.vue`：
 * 卡选择器（底部弹层：搜索 + 借记卡列表）→ 全部/收入/支出 tabs + 月份筛选 → 收/支/笔数摘要 →
 * 按日分组流水（还款行置灰，外币金额带币种 tag）→ 下拉刷新 + 分页（20 条/页）。
 */
@Composable
fun CardFlowScreen(nav: NavHostController, cardId: String) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var showCardPicker by remember { mutableStateOf(false) }
    var cardSearchKey by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("all") }
    var showDatePicker by remember { mutableStateOf(false) }
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }

    val rows = remember { mutableStateListOf<FlowRow>() }
    var page by remember { mutableStateOf(1) }
    var finished by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var summaryIncome by remember { mutableStateOf(0.0) }
    var summaryExpense by remember { mutableStateOf(0.0) }
    var hasSummary by remember { mutableStateOf(false) }

    val limit = 20
    val monthFirst = remember(year, month) { LocalDate.of(year, month, 1) }
    val startDate = monthFirst.toString()
    val endDate = monthFirst.withDayOfMonth(monthFirst.lengthOfMonth()).toString()

    // ===== web getCardDisplayText / getCardBankIcon / getCardBankName =====
    fun bankOf(card: Card): Category? = banks.firstOrNull { it.id == card.bankId }
    fun displayText(card: Card): String {
        val name = bankOf(card)?.name?.ifBlank { null }
            ?: card.alias.ifBlank { null }
            ?: card.bankName.ifBlank { null }
        val suffix = if (card.isCredit) " (信用卡)" else ""
        return when {
            name != null && card.last4.isNotBlank() -> "$name ${card.last4}$suffix"
            name != null -> "$name$suffix"
            card.last4.isNotBlank() -> "****${card.last4}"
            else -> card.alias.ifBlank { card.id }
        }
    }
    fun bankIconOf(card: Card): String = bankOf(card)?.iconUrl.orEmpty()
    fun bankNameOf(card: Card): String = bankOf(card)?.name ?: card.bankName

    // web availableCards：弹层只列【借记卡】
    val pickerCards = run {
        val key = cardSearchKey.lowercase().trim()
        val base = cards.filter { it.cardType == "debit" }
        if (key.isEmpty()) base
        else base.filter { displayText(it).lowercase().contains(key) || it.last4.lowercase().contains(key) }
    }

    // ===== 数据加载（web loadSummary / loadData / onRefresh）=====
    fun directionFilter(): Int? = when (filterType) {
        "income" -> 1
        "expense" -> 0
        else -> null
    }

    fun onRefresh() {
        val card = selectedCard ?: return
        page = 1; finished = false
        scope.launch {
            // 摘要：当月全量（limit=10000）汇总，与分页列表分离（web loadSummary）
            when (val s = graph.flow.listByCard(card.id, 1, 10000, startDate, endDate)) {
                is ApiResult.Ok -> {
                    val data = s.data.orEmpty()
                    summaryIncome = data.filter { it.direction == 1 }.sumOf { it.amount }
                    summaryExpense = data.filter { it.direction != 1 }.sumOf { it.amount }
                    hasSummary = true
                }
                else -> hasSummary = false
            }
            requesting = true
            when (val r = graph.flow.listByCard(card.id, 1, limit, startDate, endDate, directionFilter())) {
                is ApiResult.Ok -> {
                    val data = r.data.orEmpty()
                    rows.clear(); rows.addAll(data)
                    finished = data.size < limit
                    if (!finished) page = 2
                }
                else -> finished = true
            }
            requesting = false
        }
    }

    // 触底加载下一页（web van-list @load）
    val needLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            selectedCard != null && !finished && !requesting && info.totalItemsCount > 0 &&
                last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(needLoadMore) {
        if (!needLoadMore) return@LaunchedEffect
        val card = selectedCard ?: return@LaunchedEffect
        requesting = true
        when (val r = graph.flow.listByCard(card.id, page, limit, startDate, endDate, directionFilter())) {
            is ApiResult.Ok -> {
                val data = r.data.orEmpty()
                rows.addAll(data)
                finished = data.size < limit
                if (!finished) page++
            }
            else -> finished = true
        }
        requesting = false
    }

    LaunchedEffect(Unit) {
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
        // web onMounted：路由 cardId 预选（任意卡类型）
        if (cardId.isNotBlank()) {
            cards.firstOrNull { it.id == cardId }?.let { selectedCard = it; onRefresh() }
        }
    }

    // ===== 展示辅助 =====
    val grouped = rows.filter { it.transDate.isNotBlank() }.groupBy { it.transDate }
    fun dateHeaderText(date: String): String {
        val d = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return date
        val today = LocalDate.now()
        return when (d) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "${d.monthValue}月${d.dayOfMonth}日 周" + when (d.dayOfWeek.value) {
                1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
            }
        }
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            com.live.vant.nav.VanNavBar(title = "银行卡收支明细", leftArrow = true, onClickLeft = { nav.popBackStack() })
            VanPullRefresh(
                state = refreshState,
                isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
                onRefresh = { scope.launch { onRefresh(); refreshState.finishRefresh() } },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // ===== 卡选择器（web .card-selector）=====
                    item {
                        Row(
                            Modifier.fillMaxWidth().background(colors.bgCard)
                                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FText("请选择银行卡", 13f, color = colors.textTertiary)
                            Spacer(Modifier.width(8.dp))
                            Row(
                                Modifier.weight(1f).clickable { showCardPicker = true }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val card = selectedCard
                                if (card == null) {
                                    FText("请选择银行卡", 14f, color = colors.textTertiary, modifier = Modifier.weight(1f))
                                } else {
                                    BankIconView(bankIconOf(card), bankNameOf(card), size = 16.dp, rounded = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    FText(displayText(card), 14f, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                }
                                VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
                            }
                        }
                    }

                    if (selectedCard == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                VanEmpty(description = "请选择上方的银行卡或信用卡查看流水")
                            }
                        }
                    } else {
                        // ===== 筛选条（web .filter-bar：tabs shrink + 月份筛选）=====
                        item {
                            Row(
                                Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VanTabs(
                                    active = filterType,
                                    onActiveChange = { filterType = it; onRefresh() },
                                    tabs = listOf(
                                        VanTabItem("all", "全部"),
                                        VanTabItem("income", "收入"),
                                        VanTabItem("expense", "支出"),
                                    ),
                                    shrink = true,
                                    modifier = Modifier.weight(1f),
                                    content = { },
                                )
                                Row(
                                    Modifier.clip(RoundedCornerShape(4.dp)).background(colors.bgPage)
                                        .clickable { showDatePicker = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    FText("${year}年${month}月", 13f, color = colors.textSecondary)
                                    VanIcon("arrow-down", size = 12.sp, color = colors.textSecondary)
                                }
                            }
                        }

                        // ===== 摘要（web .stats-summary）=====
                        if (hasSummary) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    SummaryItem("收入", "+${Money.format(summaryIncome)}", MoneyColor.income)
                                    SummaryItem("支出", "-${Money.format(summaryExpense)}", colors.textPrimary)
                                    SummaryItem("笔数", "${rows.size}", colors.textPrimary)
                                }
                            }
                        }

                        // ===== 按日分组流水 =====
                        grouped.forEach { (date, items) ->
                            item(key = "h_$date") {
                                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    FText(dateHeaderText(date), 13f, color = colors.textSecondary,
                                        modifier = Modifier.fillMaxWidth().background(colors.bgPage)
                                            .padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                            items(items.size, key = { i -> items[i].id.ifEmpty { "i_${date}_$i" } }) { i ->
                                FlowLine(items[i], showDivider = i < items.lastIndex) {
                                    if (items[i].id.isNotEmpty()) nav.navigate(Routes.flowDetail(items[i].id))
                                }
                            }
                        }

                        if (rows.isEmpty() && !requesting) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                    VanEmpty(description = "暂无流水记录")
                                }
                            }
                        }
                        if (rows.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    FText(
                                        if (finished) "没有更多了" else if (requesting) "加载中…" else "",
                                        12f, color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // ===== 选卡弹层（web .card-picker-popup）=====
    if (showCardPicker) {
        VanPopup(show = true, onDismissRequest = { showCardPicker = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText("选择银行卡或信用卡", 16f, FontWeight.SemiBold, colors.textPrimary)
                    VanIcon("cross", size = 20.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { showCardPicker = false })
                }
                AppField(
                    cardSearchKey, { cardSearchKey = it },
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = "搜索银行名称或尾号", clearable = true,
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (pickerCards.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                            FText("未找到匹配的银行卡", 14f, color = colors.textTertiary)
                        }
                    }
                    pickerCards.forEach { card ->
                        val active = selectedCard?.id == card.id
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (active) Color(0x1407C160) else Color.Transparent)
                                .clickable {
                                    selectedCard = card; showCardPicker = false; cardSearchKey = ""
                                    onRefresh()   // web onCardSelect
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BankIconView(bankIconOf(card), bankNameOf(card), size = 20.dp, rounded = 3.dp)
                            Spacer(Modifier.width(10.dp))
                            FText(displayText(card), 14f, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            if (active) VanIcon("success", size = 16.sp, color = colors.success)
                        }
                    }
                }
            }
        }
    }

    // ===== 月份选择（web van-picker：近 10 年 + 12 月）=====
    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }, position = VanPopupPosition.Bottom, round = true) {
            val baseYear = now.get(Calendar.YEAR)
            VanPicker(
                columns = listOf(
                    (baseYear - 10..baseYear + 2).map { VanPickerOption("${it}年", it) },
                    (1..12).map { VanPickerOption("${it}月", it) },
                ),
                value = listOf((year - (baseYear - 10)).coerceIn(0, 12), month - 1),
                title = "选择月份",
                onConfirm = { _, vals ->
                    (vals[0] as? Int)?.let { year = it }
                    (vals[1] as? Int)?.let { month = it }
                    showDatePicker = false
                    onRefresh()   // web onPickerConfirm
                },
                onCancel = { showDatePicker = false },
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = colors.textTertiary)
        FText(value, 16f, FontWeight.Bold, color)
    }
}

/** 单条流水（web .flow-item：40 圆形分类底、标题 15/500、副行 12、金额 16 粗；还款行置灰）。 */
@Composable
private fun FlowLine(item: FlowRow, showDivider: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val isRepay = item.categoryId == "CATEGORY_REPAY"
    Column(Modifier.fillMaxWidth().background(colors.bgCard).alpha(if (isRepay) 0.55f else 1f)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp).padding(vertical = 14.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.bgPage), contentAlignment = Alignment.Center) {
                VanIcon(categoryIconOf(item.categoryName), size = 20.sp, color = colors.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FText(
                    if (isRepay) "信用卡还款" else item.categoryName.ifBlank { "未知分类" },
                    15f, FontWeight.Medium, colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                FText("${item.payMethod.ifBlank { "-" }} · ${item.time}", 12f, color = colors.textTertiary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.currency.isNotBlank() && item.currency != "CNY") {
                        FText(symbolOf(item.currency), 12f, color = colors.textTertiary, modifier = Modifier.padding(end = 2.dp))
                    }
                    FText(
                        "${if (item.direction == 1) "+" else "-"}${Money.format(item.amount)}", 16f, FontWeight.Bold,
                        if (item.direction == 1) MoneyColor.income else colors.textPrimary,
                    )
                }
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(colors.border))
    }
}

/** web getCategoryIcon。 */
private fun categoryIconOf(name: String): String = when (name) {
    "餐饮" -> "orders-o"; "购物" -> "shopping-cart-o"; "交通" -> "logistics"
    "娱乐" -> "gem-o"; "工资" -> "paid"; else -> "balance-o"
}

/** web getCurrencySymbol。 */
private fun symbolOf(code: String): String = when (code) {
    "CNY" -> "¥"; "USD" -> "$"; "EUR" -> "€"; "HKD" -> "HK$"; "JPY" -> "¥"
    "GBP" -> "£"; "KRW" -> "₩"; "TWD" -> "NT$"; else -> code.ifBlank { "¥" }
}
