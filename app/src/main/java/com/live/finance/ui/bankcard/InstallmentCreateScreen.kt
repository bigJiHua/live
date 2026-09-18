package com.live.finance.ui.bankcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Card
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 分期期数选项：三三三原则（3 的倍数），最大 60 期（web periodOptions 原文）。 */
private val PERIOD_OPTIONS = listOf(3, 6, 9, 12, 18, 24, 30, 36, 48, 60)

/** 银行默认口径（web BANK_ENTER_MODE 原文）：浦发=本月入账，工行=次月入账，其余默认次月。 */
private val BANK_ENTER_MODE = listOf(
    listOf("浦发", "SPDB") to "current",
    listOf("工商", "工行", "ICBC") to "next",
)

private fun guessEnterMode(card: Card?): String {
    val text = "${card?.bankName.orEmpty()}${card?.bankId.orEmpty()}${card?.alias.orEmpty()}"
    return BANK_ENTER_MODE.firstOrNull { row -> row.first.any { text.contains(it) } }?.second ?: "next"
}

/**
 * 分期每期金额精度分配（web `buildInstallmentSchedule` 逐字复刻）：
 * 本金前 N-1 期 = floor/round(本金/期数,2)，利息前 N-1 期 = round(利息/期数,2)，
 * 零头尾差放末期或第一期，保证 Σ每期 精确 = 本金 + 利息。
 */
internal fun buildInstallmentSchedule(
    principal: Double,
    fee: Double,
    periods: Int,
    principalMode: String = "floor",
    tailMode: String = "last",
): Pair<List<Double>, Double> {
    val p = principal
    val f = fee
    val n = periods.coerceAtLeast(1)
    val total = p + f
    val principalPer = if (principalMode == "round") Math.round(p / n * 100.0) / 100.0 else Math.floor(p / n * 100.0) / 100.0
    val feePer = Math.round(f / n * 100.0) / 100.0
    val sumPer = Math.round((principalPer + feePer) * 100.0) / 100.0
    val tail = Math.round((total - sumPer * (n - 1)) * 100.0) / 100.0
    val schedule = MutableList(n) { sumPer }
    if (tailMode == "last") schedule[n - 1] = tail else schedule[0] = tail
    return schedule to total
}

/**
 * 创建分期 —— 一比一复刻 web `views/BankCard/Installment.vue`。
 * 每期计划入账日 = 首期入账日往后推 N-1 个月、固定同一日（当月不足取月末）；
 * 归属账单月规则与后端 CardBill.getBillMonthByDate 一致（入账日 ≤ 账单日 → 当月，否则次月）。
 */
@Composable
fun InstallmentCreateScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val red = Color(0xFFEE0A24)

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var formAmount by remember { mutableStateOf("") }
    var formFee by remember { mutableStateOf("") }
    var formPeriods by remember { mutableStateOf(3) }
    var startMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var enterMode by remember { mutableStateOf("next") }
    var firstEnterDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var principalMode by remember { mutableStateOf("floor") }
    var tailMode by remember { mutableStateOf("last") }
    var adjustedList by remember { mutableStateOf<List<Double>>(emptyList()) }
    var editIdx by remember { mutableStateOf(-1) }
    var keyboardField by remember { mutableStateOf("amount") }
    var keyboardValue by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var showCardPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    LaunchedEffect(Unit) {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty().filter { it.billDay != 0 }; else -> Unit }
    }

    val anchorDay = ((selectedCard?.billDay ?: 1) - 2).coerceAtLeast(1)
    // 默认首期入账日（切卡/切归属时自动重置）
    LaunchedEffect(selectedCard?.id, enterMode, startMonth) {
        val base = if (enterMode == "current") startMonth else startMonth.plusMonths(1)
        firstEnterDate = base.withDayOfMonth(anchorDay.coerceAtMost(base.lengthOfMonth()))
    }
    LaunchedEffect(selectedCard) { enterMode = guessEnterMode(selectedCard) }

    val periods = formPeriods
    val amount = formAmount.toDoubleOrNull() ?: 0.0
    val fee = formFee.toDoubleOrNull() ?: 0.0
    val schedule = remember(periods, amount, fee, principalMode, tailMode) {
        if (periods <= 0 || amount <= 0.0) emptyList()
        else buildInstallmentSchedule(amount, fee, periods, principalMode, tailMode).first
    }
    LaunchedEffect(schedule) { if (schedule.isNotEmpty()) adjustedList = schedule }

    // 每期计划入账日
    val planDates = remember(firstEnterDate, periods) {
        (0 until periods).map { i ->
            val d = firstEnterDate.plusMonths(i.toLong())
            d.withDayOfMonth(firstEnterDate.dayOfMonth.coerceAtMost(d.lengthOfMonth()))
        }
    }
    val adjustedTotal = adjustedList.sum()
    val totalText = String.format("%.2f", amount + fee)
    val exceedLimit = amount > 0 && adjustedTotal > (amount + fee) + 0.005
    val formReady = selectedCard != null && amount > 0 && periods >= 2 && !exceedLimit

    fun billMonthOf(date: LocalDate): String {
        val bd = selectedCard?.billDay ?: 1
        return if (date.dayOfMonth > bd) date.plusMonths(1).withDayOfMonth(1).toString().take(7)
        else date.withDayOfMonth(1).toString().take(7)
    }

    fun doSubmit() {
        val card = selectedCard ?: return
        submitting = true
        val sched = adjustedList.ifEmpty { schedule }
        val perPeriod = sched.firstOrNull() ?: 0.0
        val firstEnter = planDates.firstOrNull()?.toString().orEmpty()
        val accountInfo = JsonObject().apply {
            addProperty("type", "installment")
            addProperty("card_id", card.id)
            addProperty("card_name", "${card.alias.ifBlank { card.bankName }}(尾号${card.last4})")
            addProperty("billing_day", card.billDay)
            addProperty("start_month", startMonth.toString().take(7))
            addProperty("enter_mode", enterMode)
            addProperty("first_enter_date", firstEnter)
            addProperty("original_amount", amount)
            addProperty("fee", fee)
            addProperty("total_periods", periods)
        }
        val monthRecords = JsonObject()
        planDates.forEachIndexed { i, d ->
            monthRecords.add(d.toString().take(7), JsonObject().apply {
                addProperty("status", "pending")
                addProperty("amount", sched.getOrNull(i) ?: perPeriod)
                addProperty("plan_date", d.toString())
                addProperty("remark", "")
                add("remind_time", JsonNull.INSTANCE)
                add("done_time", JsonNull.INSTANCE)
            })
        }
        val body = JsonObject().apply {
            addProperty("name", "${card.alias.ifBlank { card.bankName }}分期")
            addProperty("amount", perPeriod)
            addProperty("category_id", "installment")
            addProperty("account_id", accountInfo.toString())
            addProperty("cycle", "month")
            addProperty("day_of_cycle", firstEnterDate.dayOfMonth)
            add("month_records", monthRecords)
            addProperty("repeat_count", periods)
            addProperty(
                "remark",
                "总额${formAmount} 手续费${formFee.ifBlank { "0" }} ${periods}期 " +
                    "${if (enterMode == "current") "本月账单" else "次月账单"} 首期$firstEnter " +
                    "本金${if (principalMode == "floor") "截断" else "四舍五入"} 尾差${if (tailMode == "last") "末期" else "第一期"}",
            )
            addProperty("is_active", 1)
        }
        scope.launch {
            when (val r = graph.recurring.createInstallment(body)) {
                is ApiResult.Ok -> { Toast.makeText(context, "分期创建成功", Toast.LENGTH_SHORT).show(); nav.navigate(Routes.CREDIT_INSTALLMENT_LIST) }
                is ApiResult.Fail -> toast(r.message.ifBlank { "创建失败" })
                else -> toast("创建失败")
            }
            submitting = false
        }
    }

    ScreenScaffold { inner ->
        Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
            BankTopBar(title = "创建分期", onBack = { nav.popBackStack() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 头卡（红色渐变）
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(red, Color(0xFFD91A4A)))).padding(20.dp),
                ) {
                    FText("创建分期", 20f, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(4.dp))
                    FText("信用卡分期 · 默认当月，仅可选今年内月份", 13f, color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(16.dp))
                FText("新建分期", 13f, FontWeight.SemiBold, colors.textTertiary, modifier = Modifier.padding(bottom = 8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                    ReadonlyRow("目标信用卡", cardLabelOf(selectedCard), "选择信用卡") { showCardPicker = true }
                    ReadonlyRow("分期开始月份", "${startMonth.year}年${startMonth.monthValue}月", "选择首期归属月份") { showStartPicker = true }
                    // 入账归属
                    SegmentRow(
                        label = "入账归属",
                        options = listOf("current" to "本月账单", "next" to "次月账单"),
                        selected = enterMode,
                        onSelect = { enterMode = it },
                        note = "浦发等：账单日前对本月消费分期 → 本月就是第一期；工行等：次月才入第一期",
                    )
                    ReadonlyRow("首期入账日期", "$firstEnterDate", "选择首期入账日期") { showDatePicker = true }
                    NoteLine("默认账单日前 2 天（${anchorDay}号）入账，可手动调整；每期固定这一日")
                    // 分期总额
                    AmountRow("分期总额", formAmount.ifBlank { "0.00" }) { keyboardField = "amount"; keyboardValue = formAmount; showKeyboard = true }
                    AmountRow("手续费/利息", formFee.ifBlank { "0.0000" }) { keyboardField = "fee"; keyboardValue = formFee; showKeyboard = true }
                    // 期数
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        FText("分期期数", 14f, color = colors.textPrimary, modifier = Modifier.width(80.dp))
                        Column {
                            PERIOD_OPTIONS.chunked(5).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                    rowItems.forEach { p ->
                                        val active = p == formPeriods
                                        Box(
                                            Modifier.clip(RoundedCornerShape(14.dp))
                                                .background(if (active) red else colors.bgThird)
                                                .clickable { formPeriods = p }
                                                .padding(horizontal = 14.dp, vertical = 4.dp),
                                        ) {
                                            FText("${p}期", 13f, if (active) FontWeight.SemiBold else FontWeight.Normal, if (active) Color.White else colors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SegmentRow(
                        label = "本金取整",
                        options = listOf("floor" to "截断", "round" to "四舍五入"),
                        selected = principalMode,
                        onSelect = { principalMode = it },
                        note = "截断：200/12=16.66（舍）；四舍五入：200/12=16.67（入）",
                    )
                    SegmentRow(
                        label = "尾差位置",
                        options = listOf("last" to "放在末期", "first" to "放在第一期"),
                        selected = tailMode,
                        onSelect = { tailMode = it },
                        note = "多出的零头放哪一期：末期（末月多还）或 第一期（首月多还）",
                    )
                }
                // 预览卡
                if (schedule.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(14.dp)) {
                        PreviewLine("每期金额", "￥${String.format("%.2f", adjustedList.getOrNull(0) ?: schedule[0])}", red)
                        PreviewLine("总还款额", "￥$totalText", colors.textPrimary)
                        PreviewLine("账单日", selectedCard?.let { "${it.billDay}号" } ?: "-", colors.textPrimary)
                        PreviewLine("首期入账日", planDates.firstOrNull()?.toString() ?: "-", Color(0xFF07C160))
                        PreviewLine("首期归属账单", billMonthOf(planDates.firstOrNull() ?: LocalDate.now()), Color(0xFF07C160))
                        Spacer(Modifier.height(8.dp))
                        FText("系统将在每期入账日自动入账（账单日前 2 天），无需手动操作；点击每期金额可微调", 11f, color = colors.textTertiary)
                        Spacer(Modifier.height(10.dp))
                        schedule.forEachIndexed { idx, _ ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText("第${idx + 1}期", 13f, color = colors.textSecondary)
                                Spacer(Modifier.width(12.dp))
                                FText(planDates.getOrNull(idx)?.toString() ?: "", 13f, color = colors.textTertiary)
                                Spacer(Modifier.weight(1f))
                                FText(
                                    "￥${String.format("%.2f", adjustedList.getOrNull(idx) ?: schedule[idx])}",
                                    13f, FontWeight.SemiBold, red,
                                    modifier = Modifier.clickable { editIdx = idx; keyboardField = "adjust"; keyboardValue = String.format("%.2f", adjustedList.getOrNull(idx) ?: schedule[idx]); showKeyboard = true },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        FText("微调后合计 ￥${String.format("%.2f", adjustedTotal)} / ￥$totalText", 12f, FontWeight.Medium, colors.textSecondary)
                        if (exceedLimit) {
                            Spacer(Modifier.height(4.dp))
                            FText("⚠ 微调后合计已超过分期总额+手续费，请调减", 12f, FontWeight.Medium, colors.danger)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                AppButton(
                    text = "确认创建分期",
                    onClick = { doSubmit() },
                    type = AppButtonType.Primary, block = true, round = true, disabled = !formReady || submitting, loading = submitting,
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ===== 弹层 =====
    if (showCardPicker) {
        VanPopup(show = true, onDismissRequest = { showCardPicker = false }) {
            val labels = cards.map { "${it.alias.ifBlank { it.bankName }} 尾号${it.last4} · 账单${it.billDay}号" }
            VanPicker(
                columns = vanPickerColumnsOf(labels),
                value = listOf(selectedCard?.let { c -> cards.indexOfFirst { it.id == c.id } }?.coerceAtLeast(0) ?: 0),
                title = "选择信用卡",
                onConfirm = { sel, _ -> selectedCard = cards.getOrNull(sel.firstOrNull() ?: 0); showCardPicker = false },
                onCancel = { showCardPicker = false },
            )
        }
    }
    if (showStartPicker) {
        VanPopup(show = true, onDismissRequest = { showStartPicker = false }) {
            // 列用 remember 稳定（避免每次重组重建导致滚轮/选中态被重置）
            val yearLabels = remember { ((LocalDate.now().year - 1)..(LocalDate.now().year + 1)).map { "${it}年" } }
            val monthLabels = remember { (1..12).map { "${it}月" } }
            VanPicker(
                columns = vanPickerColumnsOf(yearLabels, monthLabels),
                value = listOf(1, startMonth.monthValue - 1),
                title = "分期开始月份",
                onConfirm = { sel, _ ->
                    val y = LocalDate.now().year - 1 + (sel.getOrNull(0) ?: 1)
                    startMonth = LocalDate.of(y, (sel.getOrNull(1) ?: 0) + 1, 1)
                    showStartPicker = false
                },
                onCancel = { showStartPicker = false },
            )
        }
    }
    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            // 列稳定：只按当前首期日期的年月构建，确认时再把日号收敛到目标月天数（web onDateConfirm 口径）
            val dateYears = remember { listOf("${LocalDate.now().year}年", "${LocalDate.now().year + 1}年") }
            val monthLabels = remember { (1..12).map { "${it}月" } }
            val dayLabels = remember(firstEnterDate.year, firstEnterDate.monthValue) {
                (1..firstEnterDate.lengthOfMonth()).map { "${it}日" }
            }
            VanPicker(
                columns = vanPickerColumnsOf(dateYears, monthLabels, dayLabels),
                value = listOf(
                    if (firstEnterDate.year == LocalDate.now().year) 0 else 1,
                    firstEnterDate.monthValue - 1,
                    firstEnterDate.dayOfMonth - 1,
                ),
                title = "首期入账日期",
                onConfirm = { sel, _ ->
                    val y = LocalDate.now().year + (sel.getOrNull(0) ?: 0)
                    val m = (sel.getOrNull(1) ?: 0) + 1
                    val dim = java.time.YearMonth.of(y, m).lengthOfMonth()
                    val d = ((sel.getOrNull(2) ?: 0) + 1).coerceAtMost(dim)
                    firstEnterDate = LocalDate.of(y, m, d)
                    showDatePicker = false
                },
                onCancel = { showDatePicker = false },
            )
        }
    }

    if (showKeyboard) {
        VanNumberKeyboard(
            show = true,
            text = keyboardValue,
            onTextChange = { keyboardValue = it },
            theme = VanKeyboardTheme.Custom,
            closeButtonText = "完成",
            extraKey = listOf("."),
            maxLength = if (keyboardField == "fee") 13 else 12,
            onClose = {
                keyboardCommit(
                    keyboardField, keyboardValue,
                    onAmount = { formAmount = it },
                    onFee = { formFee = it },
                    onAdjust = { num ->
                        val rounded = Math.round(num * 100.0) / 100.0
                        if (editIdx in adjustedList.indices) {
                            adjustedList = adjustedList.toMutableList().also { it[editIdx] = rounded }
                        }
                    },
                )
                showKeyboard = false
            },
        )
    }
}

/** 段选择行（web segment-switch：胶囊滑块语义用两枚按钮等价呈现）。 */
@Composable
private fun SegmentRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    note: String,
) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        FText(label, 14f, color = colors.textPrimary, modifier = Modifier.width(80.dp))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, text) ->
                    val active = value == selected
                    Box(
                        Modifier.clip(RoundedCornerShape(24.dp))
                            .background(if (active) colors.primary else colors.bgThird)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        FText(text, 13f, if (active) FontWeight.SemiBold else FontWeight.Normal, if (active) Color.White else colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            FText(note, 11f, color = colors.textTertiary)
        }
    }
}

/** 键盘落库（web closeKeyboard 原文：各字段独立位数/上限；adjust 就地写回微调列表）。 */
private fun keyboardCommit(
    field: String,
    raw: String,
    onAmount: (String) -> Unit,
    onFee: (String) -> Unit,
    onAdjust: (Double) -> Unit,
) {
    var v = raw.replace(Regex("[^\\d.]"), "")
    val head = v.substringBefore(".").take(8)
    val tail = v.substringAfter(".", "").take(4)
    v = if (tail.isEmpty()) head else "$head.$tail"
    when (field) {
        "fee" -> onFee(v)
        "adjust" -> onAdjust((v.toDoubleOrNull() ?: 0.0).coerceAtMost(99999999.99))
        else -> onAmount(v.take(11))
    }
}

private fun cardLabelOf(card: Card?): String =
    card?.let { "${it.alias.ifBlank { it.bankName }} (尾号${it.last4}) · 账单日${it.billDay}号" } ?: ""

@Composable
private fun NoteLine(text: String) {
    val colors = LocalAppColors.current
    FText(text, 11f, color = colors.textTertiary, modifier = Modifier.fillMaxWidth().padding(start = 96.dp, end = 16.dp, bottom = 8.dp))
}

@Composable
private fun AmountRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 14f, color = colors.textPrimary, modifier = Modifier.width(80.dp))
        Spacer(Modifier.weight(1f))
        FText(value, 16f, FontWeight.SemiBold, Color(0xFFEE0A24))
        Spacer(Modifier.width(6.dp))
        VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
    }
}

@Composable
private fun PreviewLine(label: String, value: String, valueColor: Color) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        FText(value, 14f, FontWeight.SemiBold, valueColor)
    }
}
