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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Card
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 还款记录列表 —— 一比一复刻 web `views/BankCard/repay/List.vue`。
 * 撤销按**来源流水 account_id**（`POST /account/credit/{accountId}/reverse/repay`），非记录 id。
 */
@Composable
fun RepayListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    var rows by remember { mutableStateOf<List<Repay>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var selectedCardName by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var month by remember { mutableStateOf(LocalDate.now().monthValue) }
    var showCardPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<Repay?>(null) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun load() {
        val billMonth = "%04d-%02d".format(year, month)
        scope.launch {
            loading = true
            when (val r = graph.repay.list(selectedCardId, billMonth)) {
                is ApiResult.Ok -> rows = r.data.orEmpty()
                is ApiResult.Fail -> { toast(r.message.ifBlank { "加载失败" }); rows = emptyList() }
                else -> rows = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
        load()
    }

    val cardColumns = remember(cards) {
        listOf("全部卡片") + cards.map { c ->
            "${c.alias.ifBlank { c.bankName.ifBlank { "卡片" } }} **** ${c.last4.ifBlank { "****" }}"
        }
    }
    val years = remember { ((LocalDate.now().year - 10)..(LocalDate.now().year + 10)).map { "${it}年" } }
    val months = remember { (1..12).map { "${it}月" } }

    VanPullRefresh(
        state = refreshState,
        isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
        onRefresh = { scope.launch { load(); refreshState.finishRefresh() } },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                item {
                    BankTopBar(title = "还款记录", onBack = { nav.popBackStack() })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterField("选择卡片", selectedCardName.ifBlank { "全部卡片" }, Modifier.weight(1f)) { showCardPicker = true }
                        Box(Modifier.width(1.dp).height(38.dp).background(colors.border))
                        FilterField("账单月份", "${year}年${month}月", Modifier.weight(1f)) { showMonthPicker = true }
                    }
                }
                if (rows.isNotEmpty()) {
                    items(rows.size, key = { rows[it].id }) { i ->
                        RepayCard(
                            item = rows[i],
                            onDetail = { nav.navigate("${Routes.REPAY_DETAIL}?id=${rows[i].id}") },
                            onAgain = { nav.navigate("${Routes.REPAY_ADD}?billId=${rows[i].billId}") },
                            onRevoke = { revokeTarget = rows[i] },
                        )
                    }
                } else if (!loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无还款记录")
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }

            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { VanLoading() }
            }
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
                        selectedCardId = cards.getOrNull(idx - 1)?.id
                        selectedCardName = cardColumns.getOrNull(idx) ?: ""
                    }
                    showCardPicker = false
                    load()
                },
                onCancel = { showCardPicker = false },
            )
        }
    }
    if (showMonthPicker) {
        VanPopup(show = true, onDismissRequest = { showMonthPicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf(years, months),
                value = listOf((year - (LocalDate.now().year - 10)).coerceIn(0, years.size - 1), month - 1),
                title = "选择月份",
                onConfirm = { sel, _ ->
                    year = LocalDate.now().year - 10 + (sel.getOrNull(0) ?: 0)
                    month = (sel.getOrNull(1) ?: 0) + 1
                    showMonthPicker = false
                    load()
                },
                onCancel = { showMonthPicker = false },
            )
        }
    }

    val target = revokeTarget
    if (target != null) {
        VanConfirmDialog(
            show = true,
            title = "确认撤销",
            message = "确定要撤销这笔 ¥${Money.formatMoney(target.amount)} 的还款记录吗？",
            onConfirm = {
                revokeTarget = null
                scope.launch {
                    if (target.accountId.isBlank()) { toast("该记录无法撤销"); return@launch }
                    when (val r = graph.repay.reverse(target.accountId)) {
                        is ApiResult.Ok -> { toast("撤销成功"); load() }
                        is ApiResult.Fail -> toast(r.message.ifBlank { "撤销失败" })
                        else -> toast("撤销失败")
                    }
                }
            },
            onCancel = { revokeTarget = null },
            onClose = { revokeTarget = null },
        )
    }
}

@Composable
private fun FilterField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(value, 14f, color = colors.textPrimary, modifier = Modifier.weight(1f))
            VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
        }
    }
}

/** 单条还款记录卡（web `.repay-card`）。 */
@Composable
private fun RepayCard(item: Repay, onDetail: () -> Unit, onAgain: () -> Unit, onRevoke: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).clickable { onDetail() }.padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText(item.cardLabel, 16f, FontWeight.SemiBold, colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                FText("本次记录还款日期 ${monthDayLabel(item)}", 13f, FontWeight.Medium, colors.primary)
            }
            VanTag(text = "已还款", type = VanTagType.Success)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("账单金额", "¥${Money.formatMoney(item.billAmount)}", colors.textSecondary)
                InfoRow("已还金额", "¥${Money.formatMoney(item.amount)}", colors.textSecondary)
                InfoRow("待还金额", "¥${Money.formatMoney(item.billNeedRepay)}", colors.danger)
            }
            Column(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                    .padding(horizontal = 16.dp, vertical = 8.dp).width(100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FText("本次还款", 12f, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(4.dp))
                FText("¥${Money.formatMoney(item.amount)}", 18f, FontWeight.Bold, Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                VanIcon("card", size = 14.sp, color = colors.textTertiary)
                Spacer(Modifier.width(4.dp))
                FText(repayMethodText(item.method), 12f, color = colors.textTertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = "还款撤销", onClick = onRevoke, type = AppButtonType.Warning, size = AppButtonSize.Small, round = true)
                AppButton(text = "再次还款", onClick = onAgain, type = AppButtonType.Default, size = AppButtonSize.Small, round = true, plain = true)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    val colors = LocalAppColors.current
    Row {
        FText(label, 13f, color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        FText(value, 14f, FontWeight.Medium, valueColor)
    }
}

/** web `formatRepayMethod`。 */
internal fun repayMethodText(method: String): String = when (method) {
    "cash" -> "现金还款"
    "balance" -> "余额还款"
    "bank_card" -> "借记卡还款"
    "card" -> "卡还款"
    else -> method.ifBlank { "还款" }
}

private fun monthDayLabel(item: Repay): String {
    val d = item.time.take(10)
    if (d.length < 10) return item.dateLabel
    val m = d.substring(5, 7).toIntOrNull() ?: return item.dateLabel
    val day = d.substring(8, 10).toIntOrNull() ?: return item.dateLabel
    return "${m}月${day}日"
}
