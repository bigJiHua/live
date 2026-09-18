package com.live.finance.ui.bankcard

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Foreign
import com.live.finance.data.model.ForeignHistory
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanPopup
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 对账目标（web openReconcile 组装的编辑对象，含历史 pending 行的伪对象）。 */
private data class RecTarget(
    val id: String,
    val currency: String,
    val foreignAmount: Double,
    val registeredRate: Double,
)

/**
 * 外币消费登记对账 —— 一比一复刻 web `views/BankCard/credit/ForeignRegister.vue`。
 * 换算公式（原文）：`toCNY = 原币 × 汇率 / 100`（汇率单位「每 100 外币」）；
 * 优先手填实际人民币，留空按汇率自动算。对账确认**无二次确认弹窗**（web 原样）。
 */
@Composable
fun ForeignScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var tab by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var pending by remember { mutableStateOf<List<Foreign>>(emptyList()) }
    var all by remember { mutableStateOf<List<Foreign>>(emptyList()) }
    var history by remember { mutableStateOf<List<ForeignHistory>>(emptyList()) }
    var hisCardId by remember { mutableStateOf("") }
    var hisRange by remember { mutableStateOf("90") }

    var recTarget by remember { mutableStateOf<RecTarget?>(null) }
    var recRate by remember { mutableStateOf("") }
    var recRmb by remember { mutableStateOf("") }
    var showReconcile by remember { mutableStateOf(false) }
    var showCardFilter by remember { mutableStateOf(false) }
    var showRangeFilter by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun loadData() {
        scope.launch {
            when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty(); else -> Unit }
            when (val r = graph.foreign.pending()) { is ApiResult.Ok -> pending = r.data.orEmpty(); else -> Unit }
            when (val r = graph.foreign.list()) { is ApiResult.Ok -> all = r.data.orEmpty(); else -> toast("加载失败") }
        }
    }

    fun loadHistory() {
        scope.launch {
            val range = hisRange.toIntOrNull() ?: 0
            val startDate = if (range > 0) LocalDate.now().minusDays(range.toLong()).format(DateTimeFormatter.ISO_DATE) else null
            val endDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            when (val r = graph.foreign.history(hisCardId.ifBlank { null }, startDate, endDate)) {
                is ApiResult.Ok -> history = r.data.orEmpty()
                is ApiResult.Fail -> toast("加载历史流水失败")
                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) { loadData(); loadHistory() }
    LaunchedEffect(hisCardId, hisRange) { loadHistory() }

    fun openReconcile(id: String, currency: String, foreignAmount: Double, registeredRate: Double) {
        recTarget = RecTarget(id, currency, foreignAmount, registeredRate)
        recRate = if (registeredRate != 0.0) Money.formatMoney(registeredRate) else ""
        recRmb = ""
        showReconcile = true
    }

    fun saveReconcile() {
        val t = recTarget ?: return
        val rate = recRate.toDoubleOrNull() ?: 0.0
        if (rate == 0.0) { toast("请填写实际汇率"); return }
        val rmb = recRmb.toDoubleOrNull() ?: 0.0
        scope.launch {
            when (val r = graph.foreign.reconcile(t.id, rate, rmb, "", "")) {
                is ApiResult.Ok -> { toast("已对账，账单已同步"); showReconcile = false; loadData(); loadHistory() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "对账失败" })
                else -> toast("对账失败")
            }
        }
    }

    fun doRegister(item: ForeignHistory) {
        if (item.accountId.isBlank()) return
        scope.launch {
            when (val r = graph.foreign.register(item.accountId)) {
                is ApiResult.Ok -> { toast("已登记为待对账"); loadData(); loadHistory() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "登记失败" })
                else -> toast("登记失败")
            }
        }
    }

    fun cardName(id: String): String =
        cards.firstOrNull { it.id == id }?.let { it.alias.ifBlank { it.bankName } } ?: id.takeLast(4)

    val cardFilterOptions = remember(cards) {
        listOf("全部卡片" to "") + cards.map { (it.alias.ifBlank { it.bankName.ifBlank { it.id.takeLast(4) } }) to it.id }
    }
    val rangeOptions = remember {
        listOf("近30天" to "30", "近90天" to "90", "近一年" to "365", "全部" to "0")
    }

    ScreenScaffold { inner ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
            item {
                BankTopBar(title = "外币消费登记对账", onBack = { nav.popBackStack() })
                // 提示条（web `.tips`，含加粗「实际结算汇率」段）
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp)) {
                    FText("外币消费入账以银行 App 实际结算汇率 为准。登记后请在还款对账时补全实际汇率/人民币，账单才计入。", 12f, color = colors.textSecondary)
                }
                // 页签
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    listOf("待对账", "全部记录", "历史外币流水").forEachIndexed { i, t ->
                        val active = tab == i
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (active) colors.primary else colors.bgCard)
                                .clickable { tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { FText(t, 13f, FontWeight.Medium, if (active) Color.White else colors.textPrimary) }
                    }
                }
            }
            when (tab) {
                0 -> {
                    if (pending.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanEmpty(description = "暂无待对账外币消费") } }
                    }
                    items(pending.size, key = { pending[it].id }) { i ->
                        val it0 = pending[i]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)
                            .clickable { openReconcile(it0.id, it0.currency, it0.foreignAmount, it0.registeredRate) }
                            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FText("${it0.currency} · ${cardName(it0.cardId)}", 14f, FontWeight.Medium, colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                FText("原币 ${Money.formatMoney(it0.foreignAmount)} @ ${Money.formatMoney(it0.registeredRate)}", 12f, color = colors.textTertiary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FText("登记折合 ¥${Money.formatMoney(it0.registeredRmb)}", 12f, color = colors.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                VanTag(text = "待对账", type = VanTagType.Warning, plain = true)
                            }
                        }
                    }
                }
                1 -> {
                    if (all.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanEmpty(description = "暂无外币登记") } }
                    }
                    items(all.size, key = { all[it].id }) { i ->
                        val it0 = all[i]
                        val reconciled = it0.status == "reconciled"
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FText("${it0.currency} · ${cardName(it0.cardId)}", 14f, FontWeight.Medium, colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                FText("原币 ${Money.formatMoney(it0.foreignAmount)}", 12f, color = colors.textTertiary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FText(
                                    if (reconciled) "入账 ¥${Money.formatMoney(it0.actualRmb)}" else "登记 ¥${Money.formatMoney(it0.registeredRmb)}",
                                    12f, color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                VanTag(text = if (reconciled) "已对账" else "待对账", type = if (reconciled) VanTagType.Success else VanTagType.Warning, plain = true)
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            FilterChip(cardFilterOptions.firstOrNull { it.second == hisCardId }?.first ?: "全部卡片", Modifier.weight(1f)) { showCardFilter = true }
                            FilterChip(rangeOptions.firstOrNull { it.second == hisRange }?.first ?: "近90天", Modifier.weight(1f)) { showRangeFilter = true }
                        }
                    }
                    if (history.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanEmpty(description = "暂无外币消费流水") } }
                    }
                    items(history.size, key = { history[it].accountId }) { i ->
                        val h = history[i]
                        val registered = h.regId.isNotBlank()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                                .alpha(if (!registered) 0.85f else 1f)
                                .clip(RoundedCornerShape(8.dp)).background(colors.bgCard)
                                .clickable {
                                    if (h.regStatus == "pending" && h.regId.isNotBlank()) {
                                        openReconcile(h.regId, h.currency, h.amount, if (h.registeredRate != 0.0) h.registeredRate else h.exchangeRate)
                                    } else if (!registered) doRegister(h)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                FText(cardName(h.cardId), 14f, FontWeight.Medium, colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                FText("${h.transDate.ifBlank { "--" }} · 原币 ${Money.formatMoney(h.amount)} @ ${Money.formatMoney(h.exchangeRate)}", 12f, color = colors.textTertiary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FText(
                                    when {
                                        h.regStatus == "reconciled" -> "入账 ¥${Money.formatMoney(h.actualRmb)}"
                                        h.regStatus == "pending" -> "待对账 ¥${Money.formatMoney(h.registeredRmb)}"
                                        else -> "未登记"
                                    },
                                    12f, color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VanTag(
                                        text = h.statusLabel,
                                        type = when (h.regStatus) { "reconciled" -> VanTagType.Success; "pending" -> VanTagType.Warning; else -> VanTagType.Default },
                                        plain = true,
                                    )
                                    if (!registered) {
                                        Spacer(Modifier.width(6.dp))
                                        AppButton(text = "登记", onClick = { doRegister(h) }, type = AppButtonType.Primary, size = AppButtonSize.Mini, plain = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ===== 对账弹层 =====
    if (showReconcile && recTarget != null) {
        VanPopup(show = true, onDismissRequest = { showReconcile = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard).padding(16.dp)) {
                FText("外币对账", 16f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
                FText("原币金额：${Money.formatMoney(recTarget!!.foreignAmount)} ${recTarget!!.currency}", 12f, color = colors.textSecondary)
                FText("登记汇率：${Money.formatMoney(recTarget!!.registeredRate)}（每100外币）", 12f, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                AppField(value = recRate, onValueChange = { recRate = it }, label = "实际汇率", placeholder = "每100外币人民币")
                AppField(value = recRmb, onValueChange = { recRmb = it }, label = "实际人民币", placeholder = "留空按汇率自动算")
                val rate = recRate.toDoubleOrNull() ?: 0.0
                val rmb = recRmb.toDoubleOrNull() ?: 0.0
                if (rate != 0.0) {
                    val preview = if (rmb > 0) rmb else (recTarget!!.foreignAmount * rate) / 100
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        FText("预计入账：", 14f, color = colors.textPrimary)
                        FText("¥${Money.formatMoney(preview)}", 14f, FontWeight.SemiBold, colors.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppButton(text = "确认对账并同步账单", onClick = { saveReconcile() }, type = AppButtonType.Primary, block = true)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (showCardFilter) {
        PickerSheet(
            show = true, columns = cardFilterOptions.map { it.first },
            selectedIndex = cardFilterOptions.indexOfFirst { it.second == hisCardId }.coerceAtLeast(0),
            title = "选择卡片",
            onConfirm = { idx -> hisCardId = cardFilterOptions.getOrNull(idx)?.second ?: ""; showCardFilter = false },
            onDismiss = { showCardFilter = false },
        )
    }
    if (showRangeFilter) {
        PickerSheet(
            show = true, columns = rangeOptions.map { it.first },
            selectedIndex = rangeOptions.indexOfFirst { it.second == hisRange }.coerceAtLeast(0),
            title = "时间范围",
            onConfirm = { idx -> hisRange = rangeOptions.getOrNull(idx)?.second ?: "90"; showRangeFilter = false },
            onDismiss = { showRangeFilter = false },
        )
    }
}

@Composable
private fun FilterChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(text, 13f, color = colors.textPrimary, modifier = Modifier.weight(1f))
        FText("▾", 12f, color = colors.textTertiary)
    }
}
