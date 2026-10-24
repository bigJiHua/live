package com.live.finance.ui.account

import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.live.finance.core.net.ApiGetCache
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
import com.live.vant.basic.VanSkeleton
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanTabItem
import com.live.vant.nav.VanTabs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/**
 * 银行卡收支明细 —— 一比一复刻 web `views/Finance/report/flow/CardFlow.vue`：
 * 卡选择器（底部弹层：搜索 + 借记卡列表）→ 全部/收入/支出 tabs + 月份筛选 → 收/支/笔数摘要 →
 * 按日分组流水（还款行置灰，外币金额带币种 tag）→ 下拉刷新 + 分页（20 条/页）。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)   // stickyHeader（置顶块）
fun CardFlowScreen(
    nav: NavHostController,
    cardId: String,
    /** web `Number(route.query.year) || now.year()` —— 0/缺省 = 本年（`CardFlow.vue:177`） */
    yearArg: Int = 0,
    /** web `Number(route.query.month) || now.month()+1`（`CardFlow.vue:178`） */
    monthArg: Int = 0,
) {
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
    // URL 入参优先（web `?year=&month=`），缺省或非法落回本月
    var year by remember { mutableStateOf(if (yearArg > 0) yearArg else now.get(Calendar.YEAR)) }
    var month by remember {
        mutableStateOf(if (monthArg in 1..12) monthArg else now.get(Calendar.MONTH) + 1)
    }

    val rows = remember { mutableStateListOf<FlowRow>() }
    var page by remember { mutableStateOf(1) }
    var finished by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    /** 整表重新加载中（web `loading`）—— 期间列表区显示**骨架屏**，不要直接落到「暂无流水记录」。 */
    var loading by remember { mutableStateOf(false) }
    var summaryIncome by remember { mutableStateOf(0.0) }
    var summaryExpense by remember { mutableStateOf(0.0) }
    var hasSummary by remember { mutableStateOf(false) }

    val limit = 20
    // ⚠ 区间必须**在调用时现算**（函数），不能存成组合期的局部 val：
    //   `onRefresh()` 与月份选择器的 `onConfirm` 是同一 lambda 里「先改 year/month 再刷新」，
    //   闭包捕获的是**上一次组合**的 startDate/endDate ⇒ 拉的还是上个月的数据，而标题已经换新月份，
    //   表现就是负责人说的「选择月份后未能及时加载，下拉刷新才出来」。
    fun monthFirst(): LocalDate = LocalDate.of(year, month, 1)
    fun startDate(): String = monthFirst().toString()
    fun endDate(): String = monthFirst().withDayOfMonth(monthFirst().lengthOfMonth()).toString()

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
    // ⚠ 必须像 web 那样按「空串」逐级兜底（web `getCardBankName = bank?.name || bank?.bank_name || card.bank_name`）：
    //   原来用 `?:` 只判 null ⇒ 字典里有这条银行但 `name` 是空串时返回 ""，
    //   `BankIconView` 的 `if (initial.isNotEmpty())` 就不画首字 ⇒ logo 位只剩一个 16dp 空块（负责人「缺少文字/银行logo展示位」）。
    fun bankNameOf(card: Card): String =
        bankOf(card)?.name?.ifBlank { null } ?: card.bankName.ifBlank { null } ?: ""

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

    /** 刷新首页 + 摘要。**返回协程 Job** 给下拉刷新 `join()` 用（否则 `finishRefresh()` 会同帧收起转圈）。 */
    fun onRefresh(): Job? {
        val card = selectedCard ?: return null
        page = 1; finished = false
        // web `loadData(reset=true)` ⇒ `loading = true` ⇒ 列表区显示**骨架屏**（不是空态）
        loading = true
        return scope.launch {
            // 摘要：当月全量（limit=10000）汇总，与分页列表分离（web loadSummary）
            when (val s = graph.flow.listByCard(card.id, 1, 10000, startDate(), endDate())) {
                is ApiResult.Ok -> {
                    val data = s.data.orEmpty()
                    summaryIncome = data.filter { it.direction == 1 }.sumOf { it.amount }
                    summaryExpense = data.filter { it.direction != 1 }.sumOf { it.amount }
                    hasSummary = true
                }
                else -> hasSummary = false
            }
            requesting = true
            when (val r = graph.flow.listByCard(card.id, 1, limit, startDate(), endDate(), directionFilter())) {
                is ApiResult.Ok -> {
                    val data = r.data.orEmpty()
                    rows.clear(); rows.addAll(data)
                    finished = data.size < limit
                    if (!finished) page = 2
                }
                else -> finished = true
            }
            requesting = false
            loading = false
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
        when (val r = graph.flow.listByCard(card.id, page, limit, startDate(), endDate(), directionFilter())) {
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

    // 唯一的数据入口：选卡 / 换月都走这里（**不在** 选择器 onConfirm 里再调一次，避免重复请求 ——
    //   第十八轮刚处理过一次「重复请求 + 卡死」的同类问题）。
    LaunchedEffect(selectedCard, year, month) { onRefresh() }

    LaunchedEffect(Unit) {
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
        // web onMounted：路由 cardId 预选（任意卡类型）；实际拉数交给上面那个 LaunchedEffect
        if (cardId.isNotBlank()) {
            cards.firstOrNull { it.id == cardId }?.let { selectedCard = it }
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
            VanPullRefresh(
                state = refreshState,
                isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
                onRefresh = {
                    ApiGetCache.clear() // 下拉刷新必须绕过 GET 缓存
                    // 必须等这一次请求真的结束再收刷新头：原来 `scope.launch { onRefresh(); finishRefresh() }`
                    //   里 onRefresh 只是"又起了一个协程"就立刻返回 ⇒ finishRefresh 同帧把 Loading→Normal，
                    //   转圈一闪就没、isTouchable 立刻恢复，连着下拉会并发多个请求抢 rows/page。
                    //   web 是 `Promise.all(...).finally(() => refreshing = false)`（CardFlow.vue:242）。
                    scope.launch { onRefresh()?.join(); refreshState.finishRefresh() }
                },
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
                            FText("请选择银行卡", 14f, color = colors.textTertiary)
                            Spacer(Modifier.width(8.dp))
                            // web 这行是 `app-cell`：`.app-cell__body{flex:1}` 把值整组推到**右边**，
                            //   `.app-cell__value{flex-shrink:0; text-align:right}` + 紧跟 right-icon，
                            //   并且 app-cell 自己还有 16px 横向内缩（叠加外层 .card-selector 的 16 ⇒ 右缘距屏幕 32）。
                            //   原生原来是 `weight(1f)` + 文本再 weight(1f) ⇒ 值贴着 label 靠左、中间一大空档。
                            Row(
                                Modifier.weight(1f).clickable { showCardPicker = true }
                                    .padding(vertical = 10.dp).padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                val card = selectedCard
                                if (card == null) {
                                    FText("请选择银行卡", 16f, color = colors.textTertiary)
                                } else {
                                    BankIconView(bankIconOf(card), bankNameOf(card), size = 16.dp, rounded = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    FText(displayText(card), 16f, color = colors.textPrimary,
                                        modifier = Modifier.weight(1f, fill = false), maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                        // ⚠ 用 stickyHeader 而**不是 item**：负责人要求「这一块 Tab 顶部置顶、下面列表滚动」
                        //   （2026-09-19）。Vant 侧等价做法是给 van-tabs 加 `sticky`，web 这版没加，
                        //   但原生列表是可滚动容器，不置顶会让筛选条跟着划走。
                        stickyHeader {
                            // 置顶块 = 筛选条 + 摘要，两者钉在一起，流水列表从下方滚过。
                            //   外层必须铺 bgPage：摘要四周那 8dp 留白要露出页面底色（web `.stats-summary{margin:8px 16px}`），
                            //   且钉顶后它会盖在滚动的列表之上，不透明才不透字。
                            Column(Modifier.fillMaxWidth().background(colors.bgPage)) {
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
                                    // web `.filter-bar{background:var(--theme-bg-secondary)}` 与
                                    //   `.van-tabs__nav{background:var(--van-tabs-nav-background)}` 在
                                    //   深色下不是同一个值 ⇒ 显式对齐，避免筛选条上出现一条深色接缝。
                                    navBackground = colors.bgCard,
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
                                    FText("${year}年${month}月", 14f, color = colors.textSecondary)
                                    VanIcon("arrow-down", size = 12.sp, color = colors.textSecondary)
                                }
                            }

                            // ===== 摘要（web .stats-summary）=====
                            //   与筛选条同处一个 stickyHeader ⇒ 一起钉顶（负责人 2026-09-19 要求；
                            //   web 这页本来没有 sticky，属**安卓侧有意偏离**，web 不同步）。
                            if (hasSummary) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                ) {
                                    SummaryItem("收入", "+${Money.format(summaryIncome)}", MoneyColor.income)
                                    // web `.summary-item .expense{color:var(--money-expense)}` ⇒ 钱色（red-in 下＝绿）。
                                    //   流水行那条才是黑：`.item-right .expense{color:var(--theme-text-primary)}`（见 FlowLine），
                                    //   两处之前都写成 textPrimary，所以「支出」跟着收入一起变色/不变色的观感与 web 不同。
                                    SummaryItem("支出", "-${Money.format(summaryExpense)}", MoneyColor.expense)
                                    SummaryItem("笔数", "${rows.size}", colors.textPrimary)
                                }
                            }
                            } // Column：置顶块（筛选条 + 摘要）
                        }

                        // ===== 按日分组流水 =====
                        grouped.forEach { (date, items) ->
                            item(key = "h_$date") {
                                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    FText(dateHeaderText(date), 14f, color = colors.textSecondary,
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

                        if (loading) {
                            // web：`<van-skeleton :row="3" v-for="i in 5" class="skeleton-item" />`
                            //   5 个骨架块，整体包在 `van-cell-group inset`（白卡、radius 8）里，每块 padding 16。
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(8.dp)).background(colors.bgCard),
                                ) {
                                    repeat(5) {
                                        VanSkeleton(
                                            loading = true,
                                            row = 3,
                                            modifier = Modifier.padding(16.dp),
                                        ) { }
                                    }
                                }
                            }
                        } else if (rows.isEmpty() && !requesting) {
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
                    FText("选择银行卡或信用卡", 18f, FontWeight.SemiBold, colors.textPrimary)
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
                            FText("未找到匹配的银行卡", 16f, color = colors.textTertiary)
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
                            FText(displayText(card), 16f, color = colors.textPrimary, modifier = Modifier.weight(1f))
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
                    // 不在此处再调 onRefresh()：`LaunchedEffect(selectedCard, year, month)` 会负责拉，
                    //   这里重复调会多一次请求（且正是"选了月份没生效"的旧写法留下的双重触发隐患）。
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
        FText(value, 18f, FontWeight.Bold, color)
    }
}

/** 单条流水（web .flow-item：40 圆形分类底、标题 15/500、副行 12、金额 16 粗；还款行置灰）。 */
@Composable
private fun FlowLine(item: FlowRow, showDivider: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val isRepay = item.categoryId == "CATEGORY_REPAY"
    Column(Modifier.fillMaxWidth().background(colors.bgCard).alpha(if (isRepay) 0.55f else 1f)) {
        Row(
            // web `.flow-item{ padding:14px 16px; margin-left:16px }` ⇒ **内容左边距 32、右边距 16**。
            //   旧实现只有 `start = 16` 且**完全没有 end**，表现为「内容贴左、金额贴右屏幕边」
            //   （2026-09-19 反馈「金额太靠近屏幕左侧，右侧需要收窄，宽度与上方对齐」）。
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp).padding(vertical = 14.dp)
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
                    16f, FontWeight.Medium, colors.textPrimary,
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
                        "${if (item.direction == 1) "+" else "-"}${Money.format(item.amount)}", 18f, FontWeight.Bold,
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
