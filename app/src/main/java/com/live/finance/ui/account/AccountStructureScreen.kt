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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Balance
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSearch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/** 虚拟账户图标底色（web virtualConfig 硬编码 #07c160）。 */
private val VIRTUAL_GREEN = Color(0xFF07C160)
/** 余额为 0 提示条底色/字色（web var(--van-orange-bg)/--van-orange，取 Vant 橙系）。 */
private val ORANGE_BG = Color(0x1AFF976A)
private val ORANGE = Color(0xFFFF976A)
private val ARROW_GRAY = Color(0xFFC8C9CC)

private data class BankInfo(val name: String, val iconUrl: String, val last4: String)

/**
 * 系统账户余额（web `views/Finance/account/Structure.vue`）：
 * 顶部总资产卡（显隐切换/零额提示）→ 虚拟账户（xxxx 现金、yyyy 余额，后者可点进余额流水）
 * → 银行卡（搜索 + 余额/近6月动账笔数切换，点击进卡流水）。
 */
@Composable
fun AccountStructureScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<Balance>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAmount by remember { mutableStateOf(true) }
    var searchKey by remember { mutableStateOf("") }
    var showFlowStats by remember { mutableStateOf(false) }
    val flowStats = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val listState = rememberLazyListState()
    val showBackTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 400 } }

    LaunchedEffect(Unit) {
        // web：并行加载银行分类 + 借记卡，再拉余额
        banks = ((graph.category.list("bank") as? ApiResult.Ok)?.data) ?: emptyList()
        cards = ((graph.card.list("debit") as? ApiResult.Ok)?.data) ?: emptyList()
        accounts = when (val r = graph.balance.list()) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> { toast.show("加载失败"); emptyList() }
        }
        loading = false
    }

    fun cardBankInfo(cardId: String): BankInfo {
        val card = cards.firstOrNull { it.id == cardId }
        val bank = card?.bankId?.let { bid -> banks.firstOrNull { it.id == bid } }
        return BankInfo(
            name = bank?.name?.ifEmpty { null } ?: card?.bankName.orEmpty(),
            iconUrl = bank?.iconUrl.orEmpty(),
            last4 = card?.last4.orEmpty(),
        )
    }

    val virtualAccounts = accounts.filter { it.isVirtual }
        // 后端排序 xxxx、yyyy；保险再排一次
        .sortedBy { if (it.cardId == "xxxx") 0 else 1 }
    val bankAccounts = accounts.filter { !it.isVirtual }
    val filteredBank = remember(bankAccounts, searchKey, cards, banks) {
        val key = searchKey.trim().lowercase()
        if (key.isEmpty()) bankAccounts
        else bankAccounts.filter { acc ->
            val info = cardBankInfo(acc.cardId)
            info.name.lowercase().contains(key) ||
                acc.cardAlias.lowercase().contains(key) ||
                (acc.cardLast4.ifEmpty { info.last4 }).lowercase().contains(key)
        }
    }
    val totalBalance = accounts.sumOf { it.balance }

    fun togglePreview() {
        if (showFlowStats) { showFlowStats = false; return }
        scope.launch {
            when (val r = graph.flow.cardsFlowStats(6)) {
                is ApiResult.Ok -> {
                    flowStats.clear()
                    r.data?.forEach { (k, v) -> flowStats[k] = v }
                    showFlowStats = true
                }
                else -> toast.show("获取失败")
            }
        }
    }

    fun amountText(v: Double): String = if (showAmount) "¥" + Money.format(v) else "******"

    ScreenScaffold { inner ->
        Box(inner) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // —— 总资产卡 ——
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(colors.bgCard).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FText("系统内计总资产", 14f, color = colors.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.clickable { showAmount = !showAmount }) {
                            FText("¥", 20f, FontWeight.Medium, colors.textPrimary,
                                modifier = Modifier.padding(end = 4.dp))
                            FText(
                                if (showAmount) Money.format(totalBalance) else "******",
                                32f, FontWeight.Bold, colors.textPrimary,
                            )
                        }
                        if (totalBalance == 0.0) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(ORANGE_BG)
                                    .clickable {
                                        // web → /finance（账本 tab，原生即 MAIN 的账本页）
                                        nav.popBackStack(Routes.MAIN, false)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                VanIcon("info-o", size = 14.sp, color = ORANGE)
                                FText("余额为零，去记账获取收支计划", 13f, color = ORANGE)
                                VanIcon("arrow", size = 12.sp, color = ORANGE)
                            }
                        }
                    }
                }

                // —— 虚拟账户 ——
                item { SectionTitle("虚拟账户") }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgCard)) {
                        virtualAccounts.forEachIndexed { i, acc ->
                            VirtualRow(
                                acc = acc,
                                amountText = amountText(acc.balance),
                                divider = i < virtualAccounts.lastIndex,
                                onClick = {
                                    if (acc.cardId == "yyyy") nav.navigate(Routes.ACCOUNT_BALANCE_FLOW)
                                },
                            )
                        }
                    }
                }

                // —— 银行卡 ——
                if (bankAccounts.isNotEmpty()) {
                    item { SectionTitle("银行卡") }
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(colors.bgCard).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                VanSearch(
                                    value = searchKey,
                                    onValueChange = { searchKey = it },
                                    placeholder = "搜索银行卡",
                                    shape = "round",
                                    background = colors.bgCard,
                                    showAction = false,
                                    onClear = { searchKey = "" },
                                )
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(colors.primary)
                                    .clickable { togglePreview() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                VanIcon(if (showFlowStats) "exchange" else "eye-o",
                                    size = 14.sp, color = Color.White)
                                FText(if (showFlowStats) "余额" else "动账", 13f, color = Color.White)
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgCard)) {
                            filteredBank.forEachIndexed { i, acc ->
                                BankRow(
                                    acc = acc,
                                    info = cardBankInfo(acc.cardId),
                                    showFlowStats = showFlowStats,
                                    expense = flowStats[acc.cardId]?.first ?: 0,
                                    income = flowStats[acc.cardId]?.second ?: 0,
                                    amountText = amountText(acc.balance),
                                    divider = i < filteredBank.lastIndex,
                                    onClick = {
                                        nav.navigate("${Routes.REPORT_CARD_FLOW}?cardId=${acc.cardId}")
                                    },
                                )
                            }
                        }
                    }
                }

                if (!loading && accounts.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无账户数据")
                        }
                    }
                }

                // —— 底部提示 ——
                if (accounts.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon("eye-o", size = 13.sp, color = colors.textTertiary)
                            FText("余额由收支计划自动计算，仅供参考", 12f,
                                color = colors.textTertiary, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            // 回顶
            if (showBackTop) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                        .size(40.dp).clip(RoundedCornerShape(50))
                        .background(colors.bgCard).clickable {
                            scope.launch { listState.scrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center,
                ) { VanIcon("back-top", size = 20.sp, color = colors.primary) }
            }

            if (loading) {
                Box(Modifier.fillMaxSize().background(colors.bgPage), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    FText(text, 13f, color = LocalAppColors.current.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 0.dp))
}

@Composable
private fun VirtualRow(
    acc: Balance,
    amountText: String,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val (icon, typeLabel) = when (acc.cardId) {
        "xxxx" -> "coupon-o" to "现金"
        else -> "wechat" to "余额"
    }
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(VIRTUAL_GREEN),
                contentAlignment = Alignment.Center,
            ) { VanIcon(icon, size = 20.sp, color = Color.White) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                FText(acc.cardAlias.ifBlank { acc.alias.ifBlank { if (acc.cardId == "xxxx") "现金账户" else "余额账户(微信+支付宝)" } },
                    15f, FontWeight.Medium, colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                FText(typeLabel, 12f, color = colors.textTertiary)
            }
            FText(amountText, 16f, FontWeight.SemiBold,
                color = when {
                    acc.balance < 0 -> colors.danger
                    acc.balance == 0.0 -> colors.textTertiary
                    else -> colors.textPrimary
                })
            if (acc.cardId == "yyyy") {
                VanIcon("arrow", size = 14.sp, color = ARROW_GRAY,
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun BankRow(
    acc: Balance,
    info: BankInfo,
    showFlowStats: Boolean,
    expense: Int,
    income: Int,
    amountText: String,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val name = info.name.ifBlank { acc.cardAlias.ifBlank { "银行卡" } }
    val last4 = acc.cardLast4.ifBlank { info.last4 }
    val idle = expense == 0 && income == 0
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BankIconView(src = info.iconUrl, name = name, size = 28.dp, rounded = 10.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                FText(name, 15f, FontWeight.Medium, colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                FText(
                    if (last4.isNotBlank()) "****$last4" else "借记卡",
                    12f, color = colors.textTertiary,
                )
            }
            if (showFlowStats) {
                Column(horizontalAlignment = Alignment.End) {
                    if (idle) {
                        FText("近6个月无动账", 13f, FontWeight.Medium, colors.danger)
                    } else {
                        FText("消费 $expense 笔", 13f, color = colors.textSecondary)
                        FText("收入 $income 笔", 13f, color = colors.textSecondary)
                    }
                }
            } else {
                FText(amountText, 16f, FontWeight.SemiBold,
                    color = when {
                        acc.balance < 0 -> colors.danger
                        acc.balance == 0.0 -> colors.textTertiary
                        else -> colors.textPrimary
                    })
            }
            VanIcon("arrow", size = 14.sp, color = ARROW_GRAY,
                modifier = Modifier.padding(start = 12.dp))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
