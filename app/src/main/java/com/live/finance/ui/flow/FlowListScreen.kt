package com.live.finance.ui.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.FlowDayGroup
import com.live.finance.ui.common.FlowItem
import com.live.finance.ui.common.FlowKind
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.common.groupFlowsByDay
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanList
import com.live.vant.feedback.VanListFooter
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import java.time.LocalDate
import kotlinx.coroutines.launch

/** web `Money` 无关：本页金额一律 `toFixed(2)`（见 [flowAmount2]）。 */
private const val LIMIT = 20

/**
 * 流水明细（时间线）—— 一比一复刻 web `views/Finance/flow/List.vue`（路由 `/finance/flow`）。
 *
 * 结构（web 是 `height:100%` 的 flex 列：上面两块固定、时间线内部滚动）：
 *  ① **月份卡**：`YYYY年M月` + `arrow-down`（开月份 picker）｜右上 `calendar-o`（跳每日流水）；
 *     三栏统计（收入 / 支出 / 结余，`toFixed(2)` 金额）+ 买卖迷你进度条（收入绿 / 支出红，各段 `flex=金额`、min 4px）
 *  ② **筛选 Tab**：全部 / 收入 / 支出（选中 = 主色底白字 + `0 2px 6px rgba(primary,.25)`）
 *  ③ **时间线**：贴顶日头（圆点/今天脉冲圈 + 今天·昨天·`M月D日 ddd` + 星期 + 当日收/支）
 *     + 卡片：普通收支（`↑↓` + 分类 + 交易方式 + 银行胶囊 + 18/700 金额 + 时间）、
 *     对外/给我转账（右上角徽标、无箭头无时间）、转账/提现/冲正（三层虚线块、点击展开两条明细）、
 *     信用卡还款（置灰）；空态「本月暂无流水」；滚过 400dp 出现回顶圆钮
 *  ④ 下拉刷新：**仅当月可用**，且只重拉「月度统计 + 今天」（其余月份禁用，与 web 一致）
 *
 * 分页：`limit=20`、`finished = 数据不足一页`；切月/切 Tab/刷新后整月重载。
 * 视图状态（年/月/筛选）用 `rememberSaveable`（web 该页在 keep-alive 名单里，返回要保留）。
 */
@OptIn(ExperimentalFoundationApi::class)   // stickyHeader 在 foundation 1.6 仍为实验 API
@Composable
fun FlowListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    val now = remember { LocalDate.now() }
    // web：URL query 记忆年月（keep-alive 返回时恢复）；原生等价 = rememberSaveable
    var year by rememberSaveable { mutableIntStateOf(now.year) }
    var month by rememberSaveable { mutableIntStateOf(now.monthValue) }
    var filterType by rememberSaveable { mutableStateOf("all") }        // all / income / expense

    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var summary by remember { mutableStateOf<Pair<Double, Double>?>(null) }  // (income, expense)
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()
    val density = LocalDensity.current

    // 回顶钮：web 以 `scrollTop > 400` 判断（用 NestedScroll 累计像素）
    var scrolledPx by remember { mutableFloatStateOf(0f) }
    val showBackTop = scrolledPx > with(density) { 400.dp.toPx() }
    val scrollSpy = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 上滑看后续内容 → consumed.y 为负 → 累计滚动量增加
                scrolledPx = (scrolledPx - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }

    val isCurrentMonth = year == now.year && month == now.monthValue

    suspend fun loadSummary() {
        summary = when (val r = graph.flow.monthStats(year, month)) {
            is ApiResult.Ok -> (r.data?.income ?: 0.0) to (r.data?.expense ?: 0.0)
            else -> null
        }
    }

    /** 整月分页加载（web `loadData`：`requesting` 防重、按 id 去重、`finished` 判定、失败 toast）。 */
    suspend fun loadData(reset: Boolean) {
        if (requesting) return
        requesting = true
        loading = true
        try {
            val p = if (reset) 1 else page
            val dir = when (filterType) {
                "income" -> 1
                "expense" -> 0
                else -> null
            }
            val r = graph.flow.list(year, month, direction = dir, page = p, limit = LIMIT)
            if (r !is ApiResult.Ok) {
                finished = true
                toast.show("加载失败")                 // web catch → showToast('加载失败')
                return
            }
            val data = r.data.orEmpty()
            rows = if (reset || p == 1) {
                data
            } else {
                val seen = rows.map { it.id }.toHashSet()
                rows + data.filter { !seen.contains(it.id) }
            }
            finished = data.size < LIMIT
            if (!finished) page = p + 1
        } finally {
            requesting = false
            loading = false
        }
    }

    /** 下拉刷新：只重拉「今天」并按 id 原地增删改（web `loadTodayOnly`，不动分页状态）。 */
    suspend fun loadTodayOnly() {
        val today = LocalDate.now().toString()
        val dir = when (filterType) {
            "income" -> 1
            "expense" -> 0
            else -> null
        }
        val todayList = when (val r = graph.flow.listRange(today, today, dir, 1, 100)) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> emptyList()
        }
        if (todayList.isEmpty()) {
            rows = rows.filter { it.day != today }
            return
        }
        val map = todayList.associateBy { it.id }
        // ① 已存在且被改过的 → 原地替换；② 服务端新增的 → 插到头部；③ 服务端没有的 → 移除
        rows = rows.map { cur -> if (cur.day == today && map.containsKey(cur.id)) map.getValue(cur.id) else cur }
        val existing = rows.map { it.id }.toHashSet()
        val newOnes = todayList.filter { !existing.contains(it.id) }
        rows = newOnes + rows.filter { it.day != today || map.containsKey(it.id) }
    }

    suspend fun reloadAll() {
        page = 1
        finished = false
        loading = true
        loadSummary()
        loadData(reset = true)
    }

    LaunchedEffect(Unit) {
        // web onMounted：分类(bank) + 卡片列表 + 月度统计 + 首屏数据
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data.orEmpty()
        cards = (graph.card.list() as? ApiResult.Ok)?.data.orEmpty()
        reloadAll()
    }

    val groups: List<FlowDayGroup> = remember(rows, cards) { groupFlowsByDay(rows, cards) }
    val showEmpty = rows.isEmpty() && !loading

    ScreenScaffold { inner ->
        Column(inner.background(colors.bgPage)) {
            // ① 月份卡
            MonthCard(
                year = year, month = month,
                income = summary?.first ?: 0.0, expense = summary?.second ?: 0.0,
                hasSummary = summary != null,
                onPicker = { showMonthPicker = true },
                onCalendar = { nav.navigate(Routes.FLOW_CALENDAR) },
            )
            // ② 筛选 Tab
            FilterTabs(filterType) { key ->
                if (key != filterType) {
                    filterType = key
                    scope.launch { reloadAll() }
                }
            }
            // ③ 时间线（固定高度内滚动，贴顶日头才有意义）
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(scrollSpy),
            ) {
                VanPullRefresh(
                    state = refreshState,
                    disabled = !isCurrentMonth,      // web：`:disabled="!isCurrentMonth"`
                    isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
                    onRefresh = {
                        scope.launch {
                            loadSummary()
                            loadTodayOnly()
                            refreshState.finishRefresh()
                        }
                    },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        groups.forEach { group ->
                            stickyHeader(key = "day-${group.date}") {
                                FlowDayHeaderRow(group.date, group.income, group.expense)
                            }
                            items(
                                count = group.items.size,
                                key = { i -> group.items[i].expense?.id ?: group.items[i].data?.id ?: "i$i" },
                            ) { i ->
                                val item = group.items[i]
                                // web `.day-cards { margin-left:5; padding-left:14 }`
                                Box(Modifier.padding(start = 19.dp)) {
                                    when {
                                        item.kind == FlowKind.Flow ||
                                            item.kind == FlowKind.ExternalTransfer ||
                                            item.kind == FlowKind.IncomingTransfer ->
                                            FlowCardRow(item, cards, banks) { item.data?.let { nav.navigate(Routes.flowDetail(it.id)) } }

                                        else -> {
                                            val key = item.expense?.id ?: ""
                                            FlowPairedBlock(
                                                item = item,
                                                cards = cards,
                                                banks = banks,
                                                expanded = expandedIds.contains(key),
                                                onToggle = {
                                                    expandedIds = if (expandedIds.contains(key)) expandedIds - key
                                                    else expandedIds + key
                                                },
                                                onDetail = { row -> nav.navigate(Routes.flowDetail(row.id)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            VanList(
                                state = listState,
                                loading = loading,
                                finished = finished,
                                onLoad = { scope.launch { loadData(reset = false) } },
                            )
                        }
                        item {
                            VanListFooter(loading = loading, finished = finished, finishedText = "— 已经看到底了 —")
                        }
                        if (showEmpty) {
                            item { VanEmpty(description = "本月暂无流水") }
                        }
                    }
                }
                if (showBackTop) {
                    // web `.back-top`：fixed right 16 / bottom 60 / 40×40 圆 / `0 2px 12px rgba(0,0,0,.15)`
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 60.dp)
                            .cssShadowCircle(WebShadow(0f, 2f, 12f, Color(0x26000000)))
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.bgCard)
                            .clickable { scope.launch { listState.animateScrollToItem(0) } },
                    ) {
                        VanIcon(name = "back-top", size = 20.sp, color = tokens.primary)
                    }
                }
            }
        }
    }

    // 月份选择器（web：今年-10 ~ 今年+2 × 12 月）
    if (showMonthPicker) {
        val years = remember { (now.year - 10..now.year + 2).toList() }
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
                    scrolledPx = 0f
                    scope.launch { reloadAll() }
                },
                onCancel = { showMonthPicker = false },
            )
        }
    }
}

/** web `.month-card`：`margin 10/12/8` + radius 5 + `padding 16 18 14` + `0 1px 8px rgba(0,0,0,.04)`。 */
@Composable
private fun MonthCard(
    year: Int,
    month: Int,
    income: Double,
    expense: Double,
    hasSummary: Boolean,
    onPicker: () -> Unit,
    onCalendar: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            .cssShadow(5.dp, WebShadow(0f, 1f, 8f, Color(0x0A000000)))
            .background(colors.bgCard, RoundedCornerShape(5.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onPicker),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FText("${year}年${month}月", 20f, FontWeight.Bold, colors.textPrimary)   // web `.month-year 20/700`
                VanIcon(name = "arrow-down", size = 16.sp, color = colors.textPrimary)  // Vant 默认图标色 = 正文色
            }
            VanIcon(name = "calendar-o", size = 22.sp, color = tokens.primary, onClick = onCalendar)
        }
        if (hasSummary) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SummaryKv("收入", "+" + flowAmount2(income), MoneyColor.income, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(colors.border))
                SummaryKv("支出", "-" + flowAmount2(expense), MoneyColor.expense, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(colors.border))
                SummaryKv(
                    "结余",
                    (if (income - expense >= 0) "+" else "-") + flowAmount2(kotlin.math.abs(income - expense)),
                    if (income - expense >= 0) MoneyColor.income else MoneyColor.expense,
                    Modifier.weight(1f),
                )
            }
            if (income + expense > 0) {
                // web `.mini-bar`：高 4、radius 2、段宽 `flex = 金额`、min-width 4px
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.bgThird),
                ) {
                    Box(
                        Modifier
                            .weight(income.toFloat().coerceAtLeast(0.0001f))
                            .fillMaxHeight()
                            .widthIn(min = 4.dp)
                            .background(VanGreenFixed),
                    )
                    Box(
                        Modifier
                            .weight(expense.toFloat().coerceAtLeast(0.0001f))
                            .fillMaxHeight()
                            .widthIn(min = 4.dp)
                            .background(colors.danger),
                    )
                }
            }
        }
    }
}

/** web `.stat-kv`：label 11px 三级（带 letter-spacing）+ 值 19/700。 */
@Composable
private fun SummaryKv(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FText(label, 11f, FontWeight.Normal, colors.textTertiary)
        FText(value, 19f, FontWeight.Bold, valueColor)
    }
}

/** web `.tab-bar` / `.tab-item`：外底 radius 5 + padding 3；选中主色底白字 + 主色 25% 投影。 */
@Composable
private fun FilterTabs(current: String, onPick: (String) -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .background(colors.bgCard, RoundedCornerShape(5.dp))
            .padding(3.dp),
    ) {
        listOf("all" to "全部", "income" to "收入", "expense" to "支出").forEach { (key, label) ->
            val active = current == key
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .then(
                        if (active) {
                            Modifier
                                .cssShadow(5.dp, WebShadow(0f, 2f, 6f, tokens.primary.copy(alpha = 0.25f)))
                                .background(tokens.primary, RoundedCornerShape(5.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(key) }
                    .padding(vertical = 7.dp),
            ) {
                FText(label, 13f, FontWeight.Medium, if (active) Color.White else colors.textTertiary)
            }
        }
    }
}
