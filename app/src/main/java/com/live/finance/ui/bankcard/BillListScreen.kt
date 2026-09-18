package com.live.finance.ui.bankcard

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Bill
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.Foreign
import com.live.finance.data.model.Pool
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
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
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanCollapseItem
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var pools by remember { mutableStateOf<List<Pool>>(emptyList()) }
    var foreignPending by remember { mutableStateOf<List<Foreign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var selectedCardName by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var month by remember { mutableStateOf(LocalDate.now().monthValue) }
    var activeGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showCardPicker by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun loadBillList() {
        val billMonth = "%04d-%02d".format(year, month)
        scope.launch {
            loading = true
            when (val r = graph.bill.list(selectedCardId, billMonth)) {
                is ApiResult.Ok -> {
                    // web：有欠款优先，其次按账单日升序
                    bills = r.data.orEmpty().sortedWith(
                        compareBy<Bill> { if (it.needRepay > 0) 0 else 1 }
                            .thenBy { Bill.parseMonthDay(it.billMonth, it.billDay) ?: LocalDate.MAX }
                    )
                }
                is ApiResult.Fail -> { toast(r.message.ifBlank { "加载失败" }); bills = emptyList() }
                else -> bills = emptyList()
            }
            loading = false
        }
    }

    suspend fun loadAux() {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
        when (val r = graph.category.list("bank")) { is ApiResult.Ok -> banks = r.data.orEmpty(); else -> Unit }
        when (val r = graph.pool.list()) { is ApiResult.Ok -> pools = r.data.orEmpty(); else -> Unit }
        when (val r = graph.foreign.pending()) { is ApiResult.Ok -> foreignPending = r.data.orEmpty(); else -> Unit }
    }

    LaunchedEffect(Unit) {
        loadAux()
        loadBillList()
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

    LaunchedEffect(groups) { activeGroups = groups.map { it.key } }

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
            scope.launch {
                loadAux(); loadBillList()
                refreshState.finishRefresh()
            }
        },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                // ===== 过滤行（web van-cell-group inset：白卡内左右两字段 + 竖分隔线）=====
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(4.dp)).background(colors.bgCard),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterField("待还月份", "${year}年${month}月", false, Modifier.weight(1f)) { showMonthPicker = true }
                        Box(Modifier.width(1.dp).height(40.dp).background(colors.border))   // web .bill-filter-sep
                        FilterField("选择卡片", selectedCardName.ifBlank { "全部卡片" }, selectedCardName.isBlank(), Modifier.weight(1f)) { showCardPicker = true }
                    }
                }
                // ===== 分组列表 =====
                if (bills.isNotEmpty()) {
                    item {
                        VanCollapse(
                            activeNames = activeGroups,
                            onActiveNamesChange = { activeGroups = it },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            groups.forEach { g ->
                                VanCollapseItem(name = g.key, title = "${g.name} · ${g.items.size} 张账单") {
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
                                                            is ApiResult.Ok -> toast(r.message.ifBlank { "账单已刷新" })
                                                            is ApiResult.Fail -> toast(r.message.ifBlank { "刷新失败" })
                                                            else -> toast("刷新失败")
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
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无账单记录")
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }

            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { VanLoading() }
            }
        }
    }

    // ===== 弹层 =====
    if (showMonthPicker) {
        VanPopup(show = true, onDismissRequest = { showMonthPicker = false }) {
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

/** 过滤字段（web app-field：label 左 · 值/占位右 · is-link 箭头）。 */
@Composable
private fun FilterField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        FText(value, 14f, color = if (isPlaceholder) colors.textTertiary else colors.textPrimary, modifier = Modifier.weight(1f))
        VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
    }
}

/** 「信报合一·合并还款」栏。 */
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
    ) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(colors.primary).padding(horizontal = 8.dp, vertical = 2.dp)) {
            FText("信报合一", 11f, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        FText("共享额度共待还 ", 13f, color = colors.textSecondary)
        FText("¥${Money.formatMoney(totalDebt)}", 15f, FontWeight.Bold, colors.danger)
        Spacer(Modifier.weight(1f))
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
    val tagType = when (bill.statusType) {
        "danger" -> VanTagType.Danger
        "success" -> VanTagType.Success
        "warning" -> VanTagType.Warning
        "" -> VanTagType.Primary            // 「正常」= 主色实底白字（web `.tag-normal`）
        else -> VanTagType.Default
    }
    Column(
        Modifier.fillMaxWidth()
            // web .bill-card { box-shadow: 0 2px 8px rgba(0,0,0,.06) }
            .cssShadow(12.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
    ) {
        // 头部：银行图标 + 卡名 + 尾号 + 明细 + 状态
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bankIconPath.isNotBlank()) {
                        BankIconView(src = AppConfig.fullFileUrl(bankIconPath), name = bankIconUrl, size = 24.dp, rounded = 4.dp)
                    } else {
                        BankIconView(src = "", name = bankIconUrl, size = 24.dp, rounded = 4.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    FText(billDisplayName(bill, bankIconUrl), 16f, FontWeight.SemiBold, colors.textPrimary)
                    if (bill.cardLast4.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        FText("**** ${bill.cardLast4}", 12f, color = colors.textTertiary)
                    }
                    Spacer(Modifier.width(6.dp))
                    AppButton(text = "明细", onClick = onLedger, type = AppButtonType.Primary, size = AppButtonSize.Mini, plain = true)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("年费 ¥${Money.formatMoney(bill.annualFee)}", 11f, color = colors.textTertiary)
                    FText(" | ", 11f, color = colors.textTertiary)
                    FText(feeFreeRuleText(bill.feeFreeRule), 11f, color = colors.textTertiary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                VanTag(text = bill.statusText, type = tagType)
                if (bill.statusExtra.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    FText(bill.statusExtra, 10f, color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 主体：额度列 + 金额列（点击进详情；web .bill-body align-items: flex-start）
        Row(Modifier.fillMaxWidth().clickable { onDetail() }, verticalAlignment = Alignment.Top) {
            Column(Modifier.padding(end = 12.dp)) {
                LimitRow("额度", "¥${Money.formatMoney(bill.creditLimit)}")
                LimitRow("可用", "¥${Money.formatMoney(bill.availLimit)}")
                if (pendingForeign > 0) {
                    Row(Modifier.clickable { onForeign() }, verticalAlignment = Alignment.CenterVertically) {
                        FText("待对账", 11f, FontWeight.SemiBold, Color(0xFFED6A0C))
                        Spacer(Modifier.width(8.dp))
                        FText("$pendingForeign 笔", 11f, FontWeight.SemiBold, Color(0xFFED6A0C))
                        VanIcon("arrow", size = 12.sp, color = Color(0xFFED6A0C))
                    }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FText(billMonthText(bill), 12f, color = colors.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    FText("¥${Money.formatMoney(bill.usedLimit)}", 20f, FontWeight.SemiBold,
                        if (bill.usedLimit > 0) colors.danger else colors.textPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FText("待还", 12f, color = colors.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    FText("¥${Money.formatMoney(bill.needRepay)}", 20f, FontWeight.SemiBold, colors.danger)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 底部：账单日/还款日 + 操作
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DayRow("账单日", "${bill.billDay}号")
                Spacer(Modifier.height(5.dp))
                DayRow("还款日", "${bill.repayDay}号")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "刷新账单", onClick = onRefreshBill, type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true, round = true)
                AppButton(text = "立即还款", onClick = onRepay, type = AppButtonType.Primary, size = AppButtonSize.Small, round = true)
            }
        }
    }
}

@Composable
private fun LimitRow(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(label, 11f, color = colors.textTertiary)
        Spacer(Modifier.width(8.dp))
        FText(value, 11f, color = colors.textSecondary)
    }
}

@Composable
private fun DayRow(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FText(label, 11f, FontWeight.Medium, colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        FText(value, 11f, FontWeight.SemiBold, colors.textPrimary)
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
