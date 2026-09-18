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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Bill
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.Pool
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanSearch
import com.live.vant.form.vanPickerColumnsOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 还款方式（web repayMethods：value/文案 原文）。 */
private val REPAY_METHODS = listOf("cash" to "现金还款", "bank_card" to "借记卡还款", "balance" to "余额还款")

/**
 * 新增还款 —— 一比一复刻 web `views/BankCard/repay/Add.vue`。
 * 合并模式（`?mergePoolId=`）：金额 = 池内全部卡 `need_repay>0` 之和（只读），提交走 merge 接口。
 */
@Composable
fun RepayAddScreen(nav: NavHostController, billId: String = "", mergePoolId: String = "") {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    val isMergeMode = mergePoolId.isNotBlank()
    var loading by remember { mutableStateOf(true) }
    var bill by remember { mutableStateOf<Bill?>(null) }
    var debitCards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }

    var oweAmount by remember { mutableStateOf(0.0) }
    var cardName by remember { mutableStateOf("") }
    var billName by remember { mutableStateOf("") }
    var repayAmount by remember { mutableStateOf("0") }
    var repayMethod by remember { mutableStateOf("bank_card") }
    var methodCardId by remember { mutableStateOf("") }
    var repayTime by remember { mutableStateOf(LocalDate.now().toString()) }
    var remark by remember { mutableStateOf("") }
    var bankSearchKey by remember { mutableStateOf("") }

    var showMethod by remember { mutableStateOf(false) }
    var showDebitPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    // 弹窗互斥：同一时刻只允许一个弹层/键盘（防窗口堆叠导致的「两弹窗叠加 + 页面卡死」）
    fun closeOverlays(except: String = "") {
        if (except != "method") showMethod = false
        if (except != "debit") showDebitPicker = false
        if (except != "date") showDatePicker = false
        if (except != "keyboard") showKeyboard = false
    }

    LaunchedEffect(billId, mergePoolId) {
        when (val r = graph.card.list(null)) { is ApiResult.Ok -> debitCards = r.data.orEmpty(); else -> Unit }
        debitCards = debitCards.filter { it.cardType != "credit" && it.cardType != "virtual_cash" && it.cardType != "virtual_balance" }
        when (val r = graph.category.list("bank")) { is ApiResult.Ok -> banks = r.data.orEmpty(); else -> Unit }

        if (isMergeMode) {
            val p = when (val r = graph.pool.list()) { is ApiResult.Ok -> r.data.orEmpty().firstOrNull { it.id == mergePoolId }; else -> null }
            cardName = "${p?.bankName?.ifBlank { null } ?: "共享池"}（信报合一）"
            val poolCards = when (val r = graph.card.list("credit")) {
                is ApiResult.Ok -> r.data.orEmpty().filter { it.sharePoolId == mergePoolId }
                else -> emptyList()
            }
            var total = 0.0
            var entry: Bill? = null
            for (c in poolCards) {
                when (val br = graph.bill.list(c.id, null)) {
                    is ApiResult.Ok -> br.data.orEmpty().forEach { b ->
                        if (b.needRepay > 0) { total += b.needRepay; if (entry == null) entry = b }
                    }
                    else -> Unit
                }
            }
            oweAmount = total
            repayAmount = Money.formatMoney(total)
            entry?.let { bill = it }
            billName = "共享额度待还 ¥${Money.formatMoney(total)}"
        } else {
            // 无 billId：web 仅显示空表单（欠款 0 → 已结清提示），绝不卡在 loading
            if (billId.isBlank()) { loading = false; return@LaunchedEffect }
            when (val r = runCatching { graph.bill.detail(billId) }.getOrNull()) {
                is ApiResult.Ok -> {
                    val b = r.data
                    if (b == null) { toast("账单不存在"); nav.popBackStack(); return@LaunchedEffect }
                    bill = b
                    oweAmount = b.needRepay
                    repayAmount = "0"
                    val no = if (b.cardLast4.isNotBlank()) "****${b.cardLast4}" else ""
                    cardName = listOf(b.cardAlias.ifBlank { "信用卡" }, no).filter { it.isNotBlank() }.joinToString(" ")
                    billName = "欠款 ¥${Money.formatMoney(oweAmount)}"
                }
                else -> { toast("加载失败"); nav.popBackStack(); return@LaunchedEffect }
            }
        }
        loading = false
    }

    fun submit() {
        scope.launch {
            if (oweAmount <= 0) { toast(if (isMergeMode) "共享池内无欠款，无需还款" else "该账单无欠款，无需还款"); return@launch }
            if (!isMergeMode) {
                val amt = repayAmount.toDoubleOrNull() ?: 0.0
                if (amt <= 0) { toast("请输入还款金额"); return@launch }
                if (amt > oweAmount) { toast("还款金额不能超过欠款 ¥${Money.formatMoney(oweAmount)}"); return@launch }
            }
            if (repayMethod == "bank_card" && methodCardId.isBlank()) { toast("请选择还款银行卡"); return@launch }
            submitting = true
            val r = if (isMergeMode) {
                graph.repay.merge(mergePoolId, repayMethod, repayTime, methodCardId.ifBlank { null }, remark)
            } else {
                graph.repay.create(
                    cardId = bill?.cardId.orEmpty(),
                    billId = bill?.id,
                    repayAmount = repayAmount.toDoubleOrNull() ?: 0.0,
                    repayMethod = repayMethod,
                    repayTime = repayTime,
                    repayMethodCardId = if (repayMethod == "bank_card") methodCardId else null,
                    billMonth = bill?.billMonth?.takeIf { it.isNotBlank() },
                    remark = remark,
                )
            }
            when (r) {
                is ApiResult.Ok -> { toast(if (isMergeMode) "合并还款成功" else "添加成功"); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { if (isMergeMode) "合并还款失败" else "添加失败" })
                else -> toast(if (isMergeMode) "合并还款失败" else "添加失败")
            }
            submitting = false
        }
    }

    ScreenScaffold { inner ->
        Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
            BankTopBar(title = if (isMergeMode) "合并还款" else "新增还款", onBack = { nav.popBackStack() })
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { VanLoading() }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (isMergeMode) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFAA00)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon("warning-o", size = 14.sp, color = Color(0xFFED6A0C))
                            Spacer(Modifier.width(8.dp))
                            FText("信报合一共享池：本次还款将一次性结清该银行共享池内全部卡的欠款", 13f, color = Color(0xFFED6A0C), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    FormSection {
                        ReadonlyRow(label = if (isMergeMode) "共享池（信报合一）" else "信用卡", value = cardName, arrow = false)
                        ReadonlyRow(label = if (isMergeMode) "共享额度" else "账单", value = billName, arrow = false)
                    }
                    if (oweAmount <= 0) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            FText("该笔账单已结清，无需还款", 15f, FontWeight.Medium, colors.primary)
                        }
                    } else {
                        FormSection("还款信息") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                                    .then(if (!isMergeMode) Modifier.clickable { closeOverlays("keyboard"); showKeyboard = true } else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FText("还款金额", 14f, color = colors.textPrimary)
                                Spacer(Modifier.weight(1f))
                                FText(if (isMergeMode) Money.formatMoney(oweAmount) else repayAmount.ifBlank { "0" }, 20f, FontWeight.SemiBold, colors.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                FText("元", 13f, color = colors.textTertiary)
                            }
                            if (!isMergeMode) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.End) {
                                    AppButton(
                                        text = "全额还款",
                                        onClick = { repayAmount = Money.formatMoney(oweAmount) },
                                        type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true,
                                    )
                                }
                            }
                            ReadonlyRow("还款方式", repayMethodLabel(repayMethod), onClick = { closeOverlays("method"); showMethod = true })
                            if (repayMethod == "bank_card") {
                                ReadonlyRow("还款银行卡", debitCardLabel(methodCardId, debitCards, banks), "请选择还款银行卡") { closeOverlays("debit"); showDebitPicker = true }
                            }
                            ReadonlyRow("还款时间", repayTime, "请选择") { closeOverlays("date"); showDatePicker = true }
                        }
                        FormSection("备注") {
                            AppField(
                                value = remark, onValueChange = { remark = it },
                                placeholder = "可选填写备注信息", border = false,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 32.dp)) {
                            AppButton(
                                text = if (isMergeMode) "合并还款" else "立即还款",
                                onClick = { submit() },
                                type = AppButtonType.Primary, block = true, round = true, loading = submitting, disabled = submitting,
                            )
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    // ===== 弹层 =====
    if (showMethod) {
        PickerSheet(
            show = true,
            columns = REPAY_METHODS.map { it.second },
            selectedIndex = REPAY_METHODS.indexOfFirst { it.first == repayMethod }.coerceAtLeast(0),
            title = "选择还款方式",
            onConfirm = { idx ->
                repayMethod = REPAY_METHODS.getOrNull(idx)?.first ?: "bank_card"
                if (repayMethod != "bank_card") methodCardId = ""
                showMethod = false
            },
            onDismiss = { showMethod = false },
        )
    }
    if (showDebitPicker) {
        DebitCardPicker(
            cards = debitCards, banks = banks, keyword = bankSearchKey,
            onKeyword = { bankSearchKey = it },
            selectedId = methodCardId,
            onPick = { methodCardId = it; bankSearchKey = ""; showDebitPicker = false },
            onClose = { showDebitPicker = false },
        )
    }
    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            VanDatePicker(
                type = "date",
                value = runCatching { LocalDate.parse(repayTime) }.getOrDefault(LocalDate.now()),
                onValueChange = { },
                minDate = LocalDate.of(2020, 1, 1), maxDate = LocalDate.now(),
                title = "选择日期",
                onConfirm = { repayTime = it.toString(); showDatePicker = false },
                onCancel = { showDatePicker = false },
            )
        }
    }

    VanNumberKeyboard(
        show = showKeyboard,
        text = repayAmount,
        onTextChange = { v -> repayAmount = sanitizeAmount(v) },
        theme = VanKeyboardTheme.Custom,
        closeButtonText = "完成",
        extraKey = listOf("."),
        maxLength = 10,
        onClose = { showKeyboard = false },
        onBlur = { showKeyboard = false },
    )
}

/** web 数字键盘输入规则：单小数点 + 最多 2 位小数 + 前导 0 归一 + 上限长度由键盘控制。 */
internal fun sanitizeAmount(v: String): String {
    var s = v.replace(Regex("[^\\d.]"), "")
    val dot = s.indexOf('.')
    if (dot >= 0) {
        val head = s.substring(0, dot)
        var tail = s.substring(dot + 1).replace(".", "")
        if (tail.length > 2) tail = tail.substring(0, 2)
        s = "$head.$tail"
    }
    if (s.length > 1 && s.startsWith("0") && !s.startsWith("0.")) s = s.substring(1)
    return s
}

private fun repayMethodLabel(v: String): String =
    when (v) { "cash" -> "现金还款"; "bank_card" -> "借记卡还款"; "balance" -> "余额还款"; else -> "请选择" }

private fun debitCardLabel(id: String, cards: List<Card>, banks: List<Category>): String {
    if (id.isBlank()) return ""
    val c = cards.firstOrNull { it.id == id } ?: return ""
    val bank = banks.firstOrNull { it.id == c.bankId }?.name.orEmpty()
    val last4 = c.last4.ifBlank { "****" }
    return if (bank.isNotBlank()) "$bank $last4" else "借记卡 ****$last4"
}

/** 借记卡选择弹层（web：搜索框 + 列表 + 选中 success 图标）。 */
@Composable
private fun DebitCardPicker(
    cards: List<Card>,
    banks: List<Category>,
    keyword: String,
    onKeyword: (String) -> Unit,
    selectedId: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val filtered = remember(keyword, cards, banks) {
        val key = keyword.trim().lowercase()
        if (key.isBlank()) cards
        else cards.filter { c ->
            val bank = banks.firstOrNull { it.id == c.bankId }?.name.orEmpty()
            val label = "${bank} ${c.last4}".lowercase()
            label.contains(key) || bank.lowercase().contains(key) || c.last4.contains(key)
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("选择还款银行卡", 16f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.weight(1f))
            VanIcon("cross", size = 18.sp, color = colors.textTertiary, onClick = { onClose() })
        }
        VanSearch(value = keyword, onValueChange = onKeyword, placeholder = "搜索银行")
        LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        FText("未找到匹配的银行卡", 14f, color = colors.textTertiary)
                    }
                }
            }
            items(filtered.size, key = { filtered[it].id }) { i ->
                val c = filtered[i]
                val bank = banks.firstOrNull { it.id == c.bankId }?.name.orEmpty()
                val last4 = c.last4.ifBlank { "****" }
                val label = if (bank.isNotBlank()) "$bank $last4" else "借记卡 ****$last4"
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(label, 14f, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (selectedId == c.id) VanIcon("success", size = 16.sp, color = colors.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
