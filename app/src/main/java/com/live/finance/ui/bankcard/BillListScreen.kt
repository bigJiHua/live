package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Bill
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.Foreign
import com.live.finance.data.model.Pool
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppTag
import com.live.finance.ui.common.AppTagType
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.flow.FText
import com.live.vant.nav.VanCollapse
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanOverlay
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanCollapseItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType

/**
 * **keep-alive 式数据缓存**（对齐 web `MainLayout` 对 `BillList` 的 `<keep-alive>` + `onActivated`）：
 *
 * key = 「选中卡片 + 账单月份」。从「账单流水明细」返回本页时：
 *  - 年月/卡片由 `rememberSaveable` 恢复（不再被重置成当月）；
 *  - 数据从缓存直接落屏（**不闪 loading、不白屏**），随后静默刷新一遍
 *    —— web 的 `onActivated` 也是「保留旧 DOM + 重新拉一遍」。
 *
 * ⚠ 只在内存里（进程级），不做磁盘持久化：退出 App 后自然失效，这点与 web 刷新即失效等价。
 */
private object BillListKeepAlive {
    data class Key(val cardId: String?, val billMonth: String)
    data class Snapshot(
        val bills: List<Bill>,
        val cards: List<Card>,
        val banks: List<Category>,
        val pools: List<Pool>,
        val foreignPending: List<Foreign>,
    )

    private val map = LinkedHashMap<Key, Snapshot>()

    @Synchronized
    fun get(key: Key): Snapshot? = map[key]

    @Synchronized
    fun put(key: Key, snap: Snapshot) {
        map[key] = snap
        // 只留最近 12 组（月份 × 卡片组合不会太多，防止长会话无限增长）
        while (map.size > 12) map.remove(map.keys.first())
    }
}

/** 按银行聚合的账单分组（含共享池合并还款信息）。 */
private data class BillGroup(
    val key: String,
    val name: String,
    val items: List<Bill>,
    val pool: Pool? = null,
    val poolMerged: Boolean = false,
    val poolTotalDebt: Double = 0.0,
)

/**
 * 信用卡账单列表 —— 一比一复刻 web `views/BankCard/bill/List.vue`。
 *
 * 要点：① 前端排序（**有欠款优先 + 账单日升序**）后再按 `card.bank_id` 分组（Map 插入序）；
 * ② 「信报合一·合并还款」栏条件 = 该银行存在 `credit_report_merged=1` 的池 且 池内待还 > 0；
 * ③ 状态 tag 由本地 `getBillStatus` 现算（已复刻在 `Bill.statusType/statusText/statusExtra`）；
 * ④ `''`（正常）用**主色实底白字**；`default`（未出账）落灰底。
 */
@Composable
fun BillListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    // keep-alive：滚动位置也要留（web 的 keep-alive 组件实例不销毁 ⇒ 滚动位置天然保留）
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val refreshState = rememberVanPullRefreshState()

    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var pools by remember { mutableStateOf<List<Pool>>(emptyList()) }
    var foreignPending by remember { mutableStateOf<List<Foreign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // ⚠ 年月 / 选中卡片必须 `rememberSaveable`：从「账单流水明细」返回时 NavBackStackEntry 还在，
    //   普通 `remember` 会随组合销毁一起丢 ⇒ 表现就是「跳回当月」（他反馈的问题）。
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCardName by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableStateOf(LocalDate.now().monthValue) }
    // 折叠展开态：keep-alive 返回时保留用户手动折叠/展开的结果（web `activeGroupNames` 同理）。
    //   ⚠ `List<String>` 不能直接进 Saveable（会抛「cannot be saved」）⇒ 用逗号拼接的字符串存。
    var activeGroupsRaw by rememberSaveable { mutableStateOf("") }
    val activeGroups: List<String> =
        if (activeGroupsRaw.isBlank()) emptyList() else activeGroupsRaw.split(",")
    fun setActiveGroups(v: List<String>) { activeGroupsRaw = v.joinToString(",") }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showCardPicker by remember { mutableStateOf(false) }

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)
    /** 当前查询键（卡片 + 月份），同时是 keep-alive 缓存的 key。 */
    fun currentKey() = BillListKeepAlive.Key(selectedCardId, "%04d-%02d".format(year, month))

    /** 把当前这一屏的数据写进 keep-alive 缓存（每次加载完都写，保证返回时能直接落屏）。 */
    fun saveSnapshot() = BillListKeepAlive.put(
        currentKey(),
        BillListKeepAlive.Snapshot(bills, cards, banks, pools, foreignPending),
    )

    /** @param silent true = 不显示 loading 遮罩（keep-alive 返回后的静默刷新 / 下拉刷新之外的场景）。 */
    /** @return 本次请求的 Job —— 下拉刷新要 `join()` 完再收刷新头（否则刷新头同帧收起 =「抽风」）。 */
    fun loadBillList(silent: Boolean = false): Job {
        val billMonth = "%04d-%02d".format(year, month)
        return scope.launch {
            if (!silent) loading = true
            when (val r = graph.bill.list(selectedCardId, billMonth)) {
                is ApiResult.Ok -> {
                    // web：有欠款优先，其次按账单日升序
                    bills = r.data.orEmpty().sortedWith(
                        compareBy<Bill> { if (it.needRepay > 0) 0 else 1 }
                            .thenBy { Bill.parseMonthDay(it.billMonth, it.billDay) ?: LocalDate.MAX }
                    )
                }
                is ApiResult.Fail -> { toast(r.message.ifBlank { "加载失败" }, VanToastType.Fail) }
                // 刷新失败保留旧账单：清空会让整页变空，也分不清「没账单」和「没加载出来」
                else -> toast(r.failMessage("加载失败"), r.failToastType())
            }
            saveSnapshot()
            if (!silent) loading = false
        }
    }

    suspend fun loadAux() {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
        when (val r = graph.category.list("bank")) { is ApiResult.Ok -> banks = r.data.orEmpty(); else -> Unit }
        when (val r = graph.pool.list()) { is ApiResult.Ok -> pools = r.data.orEmpty(); else -> Unit }
        when (val r = graph.foreign.pending()) { is ApiResult.Ok -> foreignPending = r.data.orEmpty(); else -> Unit }
        saveSnapshot()
    }

    LaunchedEffect(Unit) {
        // keep-alive：命中缓存 ⇒ 先落旧数据（不闪 loading），再静默刷新一遍（web `onActivated` 同款行为）；
        //   未命中（首次进入 / 换了月份或卡片）⇒ 正常带 loading 加载。
        val snap = BillListKeepAlive.get(currentKey())
        if (snap != null) {
            bills = snap.bills
            cards = snap.cards
            banks = snap.banks
            pools = snap.pools
            foreignPending = snap.foreignPending
            loading = false
            loadAux()
            loadBillList(silent = true)
        } else {
            loadAux()
            loadBillList()
        }
    }

    fun bankName(bankId: String): String = banks.firstOrNull { it.id == bankId }?.name.orEmpty()

    fun cardOf(bill: Bill): Card? = cards.firstOrNull { it.id == bill.cardId }

    fun groupName(bill: Bill): String {
        val c = cardOf(bill) ?: return "其他"
        return bankName(c.bankId).ifBlank { c.bankName }
    }

    fun pendingForeignCount(cardId: String): Int =
        if (cardId.isBlank()) 0 else foreignPending.count { it.cardId == cardId }

    // ===== 分组（Map 插入序 = 已排序 bills 中该银行首次出现的顺序） =====
    val groups: List<BillGroup> = remember(bills, cards, banks, pools) {
        val map = LinkedHashMap<String, MutableList<Bill>>()
        bills.forEach { b ->
            val key = cardOf(b)?.bankId?.takeIf { it.isNotBlank() } ?: "other"
            map.getOrPut(key) { mutableListOf() }.add(b)
        }
        map.map { (key, items) ->
            val name = if (items.isNotEmpty()) groupName(items.first()) else "其他"
            if (key == "other") {
                BillGroup(key, name.ifBlank { "其他" }, items)
            } else {
                val pool = pools.firstOrNull { it.bankId == key && it.creditReportMerged }
                if (pool == null) {
                    BillGroup(key, name.ifBlank { "其他" }, items)
                } else {
                    val cardsInPool = cards.filter { it.bankId == key && it.sharePoolId == pool.id }.map { it.id }.toSet()
                    val debt = items.filter { cardsInPool.contains(it.cardId) }.sumOf { it.needRepay }
                    BillGroup(key, name.ifBlank { "其他" }, items, pool, true, debt)
                }
            }
        }
    }

    // 默认展开全部分组；keep-alive 返回时 `activeGroupsRaw` 已有值 ⇒ **不再强制全展开**，
    //   只把本次新出现的分组补进去（否则用户折起来的组会被重新打开）。
    LaunchedEffect(groups.map { it.key }.joinToString()) {
        val keys = groups.map { it.key }
        setActiveGroups(
            if (activeGroupsRaw.isBlank()) keys
            else activeGroups + keys.filter { it !in activeGroups }
        )
    }

    val years = remember { ((LocalDate.now().year - 10)..(LocalDate.now().year + 10)).map { "${it}年" } }
    val months = remember { (1..12).map { "${it}月" } }
    val cardColumns = remember(cards) {
        listOf("全部卡片") + cards.map { c ->
            "${c.alias.ifBlank { bankName(c.bankId).ifBlank { c.bankName.ifBlank { "卡片" } } }} **** ${c.last4.ifBlank { "****" }}"
        }
    }

    VanPullRefresh(
        state = refreshState,
        isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
        onRefresh = {
            ApiGetCache.clear() // 下拉刷新必须绕过 GET 缓存
            scope.launch {
                // ⚠ 下拉刷新不该弹全屏 loading 遮罩（web 只有 `van-pull-refresh` 自己的指示器）；
                //   `loadBillList()` 不带 silent 会把 `loading` 置 true ⇒ 列表上盖一层黑遮罩，很难看。
                // `loadAux()` 是 suspend，`loadBillList()` 返回 Job ⇒ 都结束后才收刷新头
                loadAux(); loadBillList(silent = true).join()
                refreshState.finishRefresh()
            }
        },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                // ===== 过滤行（web van-cell-group inset：白卡内左右两字段 + 竖分隔线）=====
                item {
                    Row(
                        // web `.filter-section{ padding:12px 0 }` + `van-cell-group inset`（radius **8**、左右 16 外边距）
                        //   + `.bill-filter-row{ display:flex; align-items:stretch }` + `.bill-filter-item{ flex:1 }`
                        //   + `.bill-filter-sep{ width:1px; background:var(--theme-border); margin:12px 0 }`
                        //   `align-items:stretch` + `margin:12px 0` ⇒ 分隔线高 = **行高 − 24**（不是写死 40dp）；
                        //   Compose 里等价 = Row 取 `IntrinsicSize.Max`、线 `fillMaxHeight()` 再纵向 padding 12。
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(8.dp)).background(colors.bgCard),
                    ) {
                        FilterField("待还月份", "${year}年${month}月", false, Modifier.weight(1f)) { showMonthPicker = true }
                        Box(
                            Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp)
                                .background(colors.border),
                        )
                        FilterField("选择卡片", selectedCardName.ifBlank { "全部卡片" }, selectedCardName.isBlank(), Modifier.weight(1f)) { showCardPicker = true }
                    }
                }
                // ===== 分组列表 =====
                if (bills.isNotEmpty()) {
                    item {
                        VanCollapse(
                            activeNames = activeGroups,
                            onActiveNamesChange = { setActiveGroups(it) },
                            modifier = Modifier.padding(horizontal = 16.dp),   // web `.bill-list{padding:0 16px}`
                            // web 这页用 `:deep` 把折叠面板的三级底色全部覆写成 transparent
                            //   （`.van-collapse` / `.van-collapse-item` / `__content`）⇒ 分组层不该是白卡，
                            //   否则和每家银行的账单卡（bgCard + radius 12）叠成「卡中卡」。
                            containerColor = Color.Transparent,
                        ) {
                            groups.forEachIndexed { gi, g ->
                                val lastGroup = gi == groups.lastIndex
                                VanCollapseItem(
                                    name = g.key,
                                    title = "${g.name} · ${g.items.size} 张账单",
                                    itemColor = Color.Transparent,
                                    contentColor = Color.Transparent,
                                    contentPadding = PaddingValues(0.dp),                       // `__content{padding:0}`
                                    contentBottomGap = 4.dp,                                    // item `padding-bottom:4px`
                                    bottomSpacing = if (lastGroup) 0.dp else 20.dp,             // item `margin-bottom:20px`
                                    border = !lastGroup,                                         // `:last-child{border-bottom:none}`
                                    // Vant 的标题本质是一个 cell ⇒ 内边距 `10px 16px`（`--van-cell-vertical-padding`，
                                    //   页面只覆写了 对齐/字号/字重/色，**没改 padding**）。底座默认是 12dp 上下，此处按 cell 原值。
                                    titlePadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    titleDivider = true,      // Vant `__title--expanded:after` + 页面 `right:0`
                                    topDivider = gi > 0,      // Vant `.van-collapse-item--border:after`（非首条目，左右各缩 16）
                                    // web `:deep(.van-collapse-item__title){justify-content:flex-start;font-size:15px;font-weight:600}`
                                    //   ⇒ 左对齐 + 600（底座默认是居中 16sp 不加粗）；15px 按 §7.9 阶梯落 16f。
                                    //   `lineHeight:24px` 来自 Vant cell（`--van-cell-line-height`），银行名过长时省略号
                                    //   （web 标题单元格 overflow:hidden + nowrap）。
                                    titleSlot = {
                                        FText("${g.name} · ${g.items.size} 张账单",
                                            16f, FontWeight.SemiBold, colors.textPrimary,
                                            // 底座把 titleSlot 包在 `Box(weight(1f), Center)` 里 ⇒ 这里只能 fillMaxWidth
                                            //   铺满再靠左落字（不能再用 weight，Box 作用域没有该修饰符）
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            lineHeight = 24.sp)
                                    },
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        if (g.poolMerged && g.poolTotalDebt > 0) {
                                            MergeRepayBar(g.poolTotalDebt) {
                                                // web openMergeRepay：取池内**有欠款**的首张账单作入口
                                                val firstBill = g.items.firstOrNull { it.needRepay > 0 }
                                                if (firstBill == null) toast("共享池内暂无待还账单")
                                                else nav.navigate("${Routes.REPAY_ADD}?billId=${firstBill.id}&mergePoolId=${g.pool?.id.orEmpty()}")
                                            }
                                            Spacer(Modifier.height(12.dp))
                                        }
                                        g.items.forEachIndexed { gi, b ->
                                            if (gi > 0) Spacer(Modifier.height(12.dp))   // web .bill-card:last-child 不加外边距
                                            BillCard(
                                                bill = b,
                                                bankIconUrl = bankName(cardOf(b)?.bankId.orEmpty()),
                                                bankIconPath = banks.firstOrNull { it.id == cardOf(b)?.bankId }?.iconUrl.orEmpty(),
                                                pendingForeign = pendingForeignCount(b.cardId),
                                                onLedger = { nav.navigate("${Routes.BILL_LEDGER}?id=${b.id}") },
                                                onDetail = { nav.navigate("${Routes.BILL_DETAIL}?id=${b.id}") },
                                                onForeign = { nav.navigate(Routes.CREDIT_FOREIGN_REGISTER) },
                                                onRefreshBill = {
                                                    scope.launch {
                                                        when (val r = graph.bill.rebuild(b.cardId)) {
                                                            is ApiResult.Ok -> toast(r.message.ifBlank { "账单已刷新" }, VanToastType.Success)
                                                            is ApiResult.Fail -> toast(r.message.ifBlank { "刷新失败" }, VanToastType.Fail)
                                                            else -> toast(r.failMessage("刷新失败"), r.failToastType())
                                                        }
                                                        loadBillList()
                                                    }
                                                },
                                                onRepay = { nav.navigate("${Routes.REPAY_ADD}?billId=${b.id}") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!loading) {
                    // web 只有一个 `van-empty`，**没有**额外的 60dp 顶部留白（旧实现凭空加的）；
                    //   Vant `.van-empty{ padding:32px 0 }` 由底座 `VanEmpty` 承担（底座口径，不在本页补）。
                    item { VanEmpty(description = "暂无账单记录") }
                }
                item { Spacer(Modifier.height(100.dp)) } // .page-bill-list{ padding-bottom: 100px }
            }

            // web `van-overlay :show="loading" z-index=2000` + `van-loading size=36px vertical color=#fff`
            //   遮罩底色 `--van-overlay-background: rgba(0,0,0,.7)` ⇒ 底座 `VanOverlay` 默认已是 `0xB3000000`
            //   （旧实现自绘成 alpha .4 的半透明黑，偏浅）。文字「加载中...」保持 Vant 的 textTertiary #969799
            //   —— web 的 `color="#fff"` 只作用于 spinner，不作用于文字，所以**不要**给 textColor。
            //   残余差异：web 的 overlay 是 `position:fixed; inset:0`（含顶栏与系统栏），原生这层在
            //   `ScreenScaffold` 内 ⇒ 不覆盖顶栏；要做到全视口得把遮罩提到 NavHost 外层，另议。
            //   （底座 `VanOverlay` 没有 content 槽 ⇒ 转圈与文字作为**后一个兄弟节点**叠在遮罩之上。）
            VanOverlay(show = loading)
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VanLoading(
                        size = 36.sp,
                        vertical = true,
                        color = Color.White,
                        text = "加载中...",
                    )
                }
            }
        }
    }

    // ===== 弹层 =====
    if (showMonthPicker) {
        // web `<app-popup position="bottom" round>` ⇒ 顶部 16/16/0/0 圆角；
        //   下面的**卡片选择弹层** web 没写 round ⇒ 保持直角，两处不要一起改。
        VanPopup(show = true, onDismissRequest = { showMonthPicker = false }, round = true) {
            VanPicker(
                columns = vanPickerColumnsOf(years, months),
                value = listOf((year - (LocalDate.now().year - 10)).coerceIn(0, years.size - 1), (month - 1).coerceIn(0, 11)),
                title = "选择月份",
                onConfirm = { sel, _ ->
                    year = LocalDate.now().year - 10 + (sel.getOrNull(0) ?: 0)
                    month = (sel.getOrNull(1) ?: 0) + 1
                    showMonthPicker = false
                    loadBillList()
                },
                onCancel = { showMonthPicker = false },
            )
        }
    }
    if (showCardPicker) {
        VanPopup(show = true, onDismissRequest = { showCardPicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf(cardColumns),
                value = listOf(if (selectedCardId == null) 0 else cards.indexOfFirst { it.id == selectedCardId } + 1),
                onConfirm = { sel, _ ->
                    val idx = sel.firstOrNull() ?: 0
                    if (idx <= 0) { selectedCardId = null; selectedCardName = "" }
                    else {
                        val c = cards.getOrNull(idx - 1)
                        selectedCardId = c?.id; selectedCardName = cardColumns.getOrNull(idx) ?: ""
                    }
                    showCardPicker = false
                    loadBillList()
                },
                onCancel = { showCardPicker = false },
            )
        }
    }
}

/**
 * 过滤字段 —— web `components/base/AppField.vue`：`.app-field{ display:block; padding:10px 16px;
 * background:var(--theme-bg-secondary); font-size:14px }` ⇒ **标签在上、值在下（纵向）**，
 * `.app-field__label{ margin-bottom:6px; font-size:13px; color:--theme-text-secondary }`，
 * `.app-field__control{ display:flex; align-items:center; gap:6px }`，
 * `input{ flex:1; min-width:0; ellipsis; font-size:14px; line-height:1.5 }`，
 * `::placeholder{ color: var(--theme-text-placeholder) }`（#c8c9cc，**不是** tertiary），
 * `.app-field__right-icon{ color:--theme-text-tertiary }`（`van-icon arrow` 14px，图标不提档）。
 *
 * 旧实现是「label 左 · 值右」的横排 Row（还凭空多了 12dp 横距、字段内边距 14dp）⇒ 整块高度与 web 差约 20dp。
 * 字号按 §7.9：label 13px ⇒ **14f**、值 14px ⇒ **16f**（这两个本来就对，别回改成 web 原值）。
 */
@Composable
private fun FilterField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 14f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))                       // web `.app-field__label{margin-bottom:6px}`
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),   // web `.app-field__control{gap:6px}`
        ) {
            FText(
                value, 16f,
                color = if (isPlaceholder) colors.textPlaceholder else colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,   // web `overflow:hidden; text-overflow:ellipsis`
            )
            VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
        }
    }
}

/**
 * 「信报合一·合并还款」栏 —— web `bill/List.vue:55-61` + `:914-949`。
 *
 * DOM 是两簇：`.merge-repay-info`（tag + 文案，`display:flex; gap:8px; min-width:0`）与右侧按钮；
 * 原来原生没有 `.merge-repay-info` 这层 ⇒ tag 与文案平铺进外层 Row，`min-width:0` 无处生效、
 * 金额一长就把「合并还款」按钮顶出屏幕（web 靠 `.merge-repay-text{ellipsis}` 省略号截断）。
 */
@Composable
private fun MergeRepayBar(totalDebt: Double, onMerge: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth()
            // web .merge-repay-bar { box-shadow: 0 2px 8px rgba(0,0,0,.06) }
            .cssShadow(12.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),   // web `.merge-repay-bar{gap:8px}`
    ) {
        Row(
            Modifier.weight(1f),                               // web `.merge-repay-info{ min-width:0 }`
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(colors.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),   // `.merge-repay-tag{flex-shrink:0}`
            ) {
                FText("信报合一", 12f, color = Color.White, maxLines = 1)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // web `.merge-repay-info{font-size:13px→14; color:text-secondary}`（§7.9 已提档，别回改）
                FText("共享额度共待还 ", 14f, color = colors.textSecondary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // `.merge-repay-text b{color:var(--theme-danger,#ee0a24); font-size:15px}` ⇒ §7.9：15px → **16f**
                //   （15 不在偶数阶梯上；上一版落的 15f 是漏提档）。只是加粗 + 红，不是「与整行同字号」。
                FText("¥${Money.formatMoney(totalDebt)}", 16f, FontWeight.Bold, colors.danger, maxLines = 1)
            }
        }
        AppButton(text = "合并还款", onClick = onMerge, type = AppButtonType.Primary, size = AppButtonSize.Small, round = true)
    }
}

/** 单张账单卡（对齐 web `.bill-card`）。 */
@Composable
private fun BillCard(
    bill: Bill,
    bankIconUrl: String,
    bankIconPath: String,
    pendingForeign: Int,
    onLedger: () -> Unit,
    onDetail: () -> Unit,
    onForeign: () -> Unit,
    onRefreshBill: () -> Unit,
    onRepay: () -> Unit,
) {
    val colors = LocalAppColors.current
    // web 用的是自写 `app-tag`（AppTag.vue），不是 van-tag：padding 2·6 / radius 4 / 1px 透明描边 /
    //   line-height 1.4；`default` 是**灰底深灰字**，`''`（正常）走 `.tag-normal` ⇒ 主色实底白字。
    val tagType = when (bill.statusType) {
        "danger" -> AppTagType.Danger
        "success" -> AppTagType.Success
        "warning" -> AppTagType.Warning
        "" -> AppTagType.Primary            // 「正常」= 主色实底白字（web `.tag-normal`）
        else -> AppTagType.Default          // 「未出账」：web 没有 `.app-tag--default` 规则 ⇒ 吃 base（灰底）
    }
    Column(
        Modifier.fillMaxWidth()
            // web .bill-card { box-shadow: 0 2px 8px rgba(0,0,0,.06) }
            .cssShadow(12.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
    ) {
        // 头部（web `.bill-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}`）
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // ===== `.bill-name-row{display:flex; align-items:center; gap:8px}` =====
                //   DOM：BankIcon(22) —8— 卡名 —8— 尾号(可选) —(8+6)=14—「明细」
                //   `.detail-btn{flex-shrink:0}` 而 `.bill-card-name` **没有** flex-shrink:0
                //   ⇒ 卡名可被压缩/换行，按钮必须留在屏内。旧实现两处都错：按钮前只留了 6dp、卡名没加 weight。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // web `<BankIcon :size="22">` + `class="bank-icon"`（CSS 写 24px，但组件根元素的
                    //   **内联** width/height 由 `size` prop 给出 ⇒ 实际渲染 22×22；CSS 里生效的只有 radius 4）。
                    //   `BankIconView` 内部已经会拼 `AppConfig.fullFileUrl`，这里必须传**原始路径**。
                    BankIconView(src = bankIconPath, name = bankIconUrl, size = 22.dp, rounded = 4.dp)
                    Spacer(Modifier.width(8.dp))
                    // web `.bill-card-name{font-size:16px; font-weight:600}` → 按 §7.9 阶梯 16px ⇒ **18f**
                    FText(billDisplayName(bill, bankIconUrl), 18f, FontWeight.SemiBold, colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                    if (bill.cardLast4.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        FText("**** ${bill.cardLast4}", 12f, color = colors.textTertiary, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                    }
                    Spacer(Modifier.width(6.dp))   // `.detail-btn{margin-left:6px}`（与 gap:8 叠加 ⇒ 有尾号时共 14）
                    AppButton(text = "明细", onClick = onLedger, type = AppButtonType.Primary, size = AppButtonSize.Mini, plain = true)
                }
                Spacer(Modifier.height(4.dp))
                // web `.bill-fee-info{display:flex; align-items:center; gap:4px}` —— **三个 span 靠 gap 撑开**，
                //   不是把 " | " 连空格一起写进文案（旧实现如此 ⇒ 竖线与文字间距不对、分隔符也无法单独着色）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // §7.9：11px ⇒ 12f（命中标题档，不在排除表内，别回改成 11）
                    FText("年费 ¥${Money.formatMoney(bill.annualFee)}", 12f, color = colors.textTertiary, maxLines = 1)
                    FText("|", 12f, color = colors.textTertiary, maxLines = 1)     // `.fee-divider` 同为 tertiary
                    FText(feeFreeRuleText(bill.feeFreeRule), 12f, color = colors.textTertiary, maxLines = 1)
                }
            }
            // `.bill-status-col{display:flex;flex-direction:column;align-items:flex-end;gap:2px}`
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppTag(text = bill.statusText, type = tagType)
                if (bill.statusExtra.isNotBlank()) {
                    // web `.status-extra{white-space:nowrap}` ⇒ 单行，不换行
                    FText(bill.statusExtra, 12f, color = colors.textTertiary, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // ===== 主体（web `.bill-body{display:flex; justify-content:space-between; align-items:flex-start; gap:8px}`）
        //   ⚠ web 的 `.bill-body` **没有点击**，详情入口只挂在右侧 `.bill-amount-right`（`:116`）；
        //     旧实现把 clickable 挂在整个主体行上 ⇒ 点「额度/可用」的空白也会跳详情，与 web 不一致。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,                        // `align-items:flex-start`
            horizontalArrangement = Arrangement.spacedBy(8.dp),       // `.bill-body{gap:8px}`（与左列 padding-right:12 叠加）
        ) {
            // `.bill-limit-small{flex:0 0 auto; display:flex; flex-direction:column; justify-content:center;
            //   gap:4px; padding-right:12px}` —— `gap` 是**行间距**（末行不多 4dp，旧实现用每行 padding-bottom 会多）；
            //   列宽 = 最宽那行（`flex:0 0 auto`）⇒ 用 IntrinsicSize.Max 复现，行内 space-between 才能把值右对齐。
            Column(
                Modifier.width(IntrinsicSize.Max).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),   // `gap:4px`（web 另有 justify-content:center，
                                                                    //   但子项总高=容器高 ⇒ 视觉无差）
            ) {
                LimitRow("额度", "¥${Money.formatMoney(bill.creditLimit)}")
                LimitRow("可用", "¥${Money.formatMoney(bill.availLimit)}")
                if (pendingForeign > 0) {
                    // `待对账` 行本身也是 `.limit-row`（`.pending-fx-row` 只叠了 align-items:center + cursor）
                    //   ⇒ **三个子节点** 走 space-between：待对账 左、N 笔 中、箭头 右（不是「N 笔+箭头」成组）
                    Row(
                        Modifier.fillMaxWidth().clickable { onForeign() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FText("待对账", 12f, FontWeight.SemiBold, FOREIGN_ORANGE, maxLines = 1)
                        FText("$pendingForeign 笔", 12f, FontWeight.SemiBold, FOREIGN_ORANGE, maxLines = 1)
                        VanIcon("arrow", size = 12.sp, color = FOREIGN_ORANGE)   // §7.9：图标一律不提档
                    }
                }
            }
            // `.bill-amount-right{flex:1; display:flex; justify-content:space-around; align-items:center; cursor:pointer}`
            //   + `.bill-amount-right:active{opacity:.6}` ⇒ 按压整块转暗
            val pressSrc = remember { MutableInteractionSource() }
            val pressed by pressSrc.collectIsPressedAsState()
            Row(
                Modifier.weight(1f).alpha(if (pressed) 0.6f else 1f)
                    .clickable(interactionSource = pressSrc, indication = null) { onDetail() },
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FText(billMonthText(bill), 12f, color = colors.textTertiary, maxLines = 1)
                    Spacer(Modifier.height(4.dp))                     // web `.repay-label{margin-bottom:4px}`
                    FText("¥${Money.formatMoney(bill.usedLimit)}", 20f, FontWeight.SemiBold,
                        if (bill.usedLimit > 0) colors.danger else colors.textPrimary, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FText("待还", 12f, color = colors.textTertiary, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    FText("¥${Money.formatMoney(bill.needRepay)}", 20f, FontWeight.SemiBold, colors.danger, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // ===== 底部（web `.bill-footer{display:flex; justify-content:space-between; align-items:center; padding-top:12dp}`）
        //   左簇 `.bill-day-info` **没有** flex:1 ⇒ 用 SpaceBetween 而非 weight(1f)（按钮很宽时 web 会挤压左簇）
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {   // `.bill-day-info{gap:5px}`
                DayRow("账单日", "${bill.billDay}号")
                DayRow("还款日", "${bill.repayDay}号")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {    // `.bill-actions{gap:8px}`
                AppButton(text = "刷新账单", onClick = onRefreshBill, type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true, round = true)
                AppButton(text = "立即还款", onClick = onRepay, type = AppButtonType.Primary, size = AppButtonSize.Small, round = true)
            }
        }
    }
}

/** web `.pending-fx-*{color:#ed6a0c}`（三色同源：标签/笔数/箭头）。 */
private val FOREIGN_ORANGE = Color(0xFFED6A0C)

/** web `.limit-row{display:flex; justify-content:space-between; gap:8px; font-size:11px; color:tertiary}`
 *  + `.limit-row span:last-child{color:var(--theme-text-secondary)}` ⇒ **标签 tertiary、值 secondary**；
 *  两端子对齐（不是「标签 + 固定 8dp + 值」）。§7.9：11px ⇒ 12f。 */
@Composable
private fun LimitRow(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FText(label, 12f, color = colors.textTertiary, maxLines = 1)
        FText(value, 12f, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun DayRow(label: String, value: String) {
    val colors = LocalAppColors.current
    // web `.day-row{font-size:11px; gap:6px}`（§7.9：11px ⇒ 12f）+ `.day-label{500,secondary}` + `.day-month{600,primary}`
    Row(verticalAlignment = Alignment.CenterVertically) {
        FText(label, 12f, FontWeight.Medium, colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        FText(value, 12f, FontWeight.SemiBold, colors.textPrimary)
    }
}

/** 卡名（web `getCardDisplayName`）：`card_alias` 优先，其次银行名，最后「信用卡」。 */
private fun billDisplayName(bill: Bill, bankName: String): String =
    bill.cardAlias.ifBlank { bankName.ifBlank { "信用卡" } }

/** web `getFeeFreeRuleText`：纯数字 → `N笔消费免`；空 → `无免年费规则`。 */
private fun feeFreeRuleText(rule: String): String {
    if (rule.isBlank()) return "无免年费规则"
    return if (rule.all { it.isDigit() }) "${rule}笔消费免" else rule
}

/** web `getBillMonthText`：取账单开始日的月份 → `M月账单`。 */
private fun billMonthText(bill: Bill): String {
    val d = bill.billStartDate
    if (d.length < 7) return "账单"
    val m = d.substring(5, 7).toIntOrNull() ?: return "账单"
    return "${m}月账单"
}
