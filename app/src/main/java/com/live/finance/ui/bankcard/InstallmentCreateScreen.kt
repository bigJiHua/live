package com.live.finance.ui.bankcard

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.data.model.Card
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanGreen
import com.live.finance.theme.vanRed
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanToastType
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

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

// ── web `Installment.vue` 的固定几何（`.segment-item{flex:0 0 100px}` / `padding:5px`） ──
private val SEG_ITEM_W = 100.dp
private val SEG_ITEM_H = 26.dp

/**
 * 创建分期 —— 一比一复刻 web `views/BankCard/Installment.vue`。
 *
 * 布局口径（照 web 样式逐条对，别用「差不多」的写法）：
 *  - `.page-installment{padding:12px 16px 100px}` → 滚动列 `start/end=16, top=12` + 末尾 `Spacer(100.dp)`。
 *  - **白底行**：`app-field` / `.input-cell` 都是 `background:--theme-bg-secondary`（= `colors.bgCard`），
 *    `app-field` 额外有 `border-bottom:1px solid --theme-border`；`.input-cell` **没有**分隔线。
 *  - **透明行**：`.opt-row`（label 左 / 分段控件右，`space-between`，`padding:10px 0`）与
 *    `.opt-note`（独占一行、12px、text-tertiary）都在**页面底色**上，**不是**白底 ——
 *    旧实现把所有行塞进一张白卡片，属结构性偏差。
 *  - 分段控件 `.segment-switch` = **一个** `bgThird` 胶囊容器（`padding:5px; radius:30px`）
 *    + 内部一个 `theme-primary` 滑块（`radius:24px`，`translateX` 0/100px），未选中项**无独立底色**。
 *
 * 每期计划入账日 = 首期入账日往后推 N-1 个月、固定同一日（当月不足取月末）；
 * 归属账单月规则与后端 `CardBill.getBillMonthByDate` 一致（入账日 ≤ 账单日 → 当月，否则次月）。
 */
@Composable
fun InstallmentCreateScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    // web 大量 `var(--van-danger-color, #ee0a24)` —— 在 `:root`（两模式都 = --theme-danger）→ colors.danger；
    // 渐变另一端 `--van-danger-grad` 只在深色块，浅色走 fallback #d91a4a。
    val red = colors.danger
    val now = remember { LocalDate.now() }

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var formAmount by remember { mutableStateOf("") }
    var formFee by remember { mutableStateOf("") }
    var formPeriods by remember { mutableStateOf(3) }
    var startMonth by remember { mutableStateOf(now.withDayOfMonth(1)) }
    var enterMode by remember { mutableStateOf("next") }
    var firstEnterDate by remember { mutableStateOf(now.withDayOfMonth(1)) }
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
    // web `datePickerSel`：日期选择器自身的三列选中值（年/月/日），日列长度随年月联动
    var dateSel by remember { mutableStateOf(listOf(now.year, now.monthValue, 1)) }
    val scope = rememberCoroutineScope()

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)
    LaunchedEffect(Unit) {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cards = r.data.orEmpty().filter { it.billDay != 0 }; else -> Unit }
    }

    val anchorDay = ((selectedCard?.billDay ?: 1) - 2).coerceAtLeast(1)
    // 默认首期入账日（web `watch([defaultFirstEnterDate, selectedCard.id, enterMode], …, {immediate:true})`）
    LaunchedEffect(selectedCard?.id, enterMode, startMonth) {
        val base = if (enterMode == "current") startMonth else startMonth.plusMonths(1)
        firstEnterDate = base.withDayOfMonth(anchorDay.coerceAtMost(base.lengthOfMonth()))
    }
    // web 在 `onCardConfirm` 里按银行口径预选入账归属
    LaunchedEffect(selectedCard) { enterMode = guessEnterMode(selectedCard) }
    // 打开日期选择器时定位到当前首期日期（web `watch(showDatePicker, …)`）
    LaunchedEffect(showDatePicker) {
        if (showDatePicker) dateSel = listOf(firstEnterDate.year, firstEnterDate.monthValue, firstEnterDate.dayOfMonth)
    }

    val periods = formPeriods
    val amount = formAmount.toDoubleOrNull() ?: 0.0
    val fee = formFee.toDoubleOrNull() ?: 0.0
    val schedule = remember(periods, amount, fee, principalMode, tailMode) {
        if (periods <= 0 || amount <= 0.0) emptyList()
        else buildInstallmentSchedule(amount, fee, periods, principalMode, tailMode).first
    }
    // web `watch([preview.total, preview.schedule.join(",")], () => adjustedList = [...schedule])`
    // ⚠ 均分方案清空时也要跟着清空（旧实现带 `isNotEmpty()` 守卫，导致清空金额后残留旧微调值）
    LaunchedEffect(schedule) { adjustedList = schedule }

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
        // web `handleSubmit` 的四道校验（按钮已 disabled，这里与 web 同源兜底）
        val card = selectedCard ?: return toast("请选择信用卡")
        if (formAmount.isBlank() || (formAmount.toDoubleOrNull() ?: 0.0) <= 0) return toast("请输入分期总额")
        if (formPeriods < 2) return toast("期数至少2期")
        if (exceedLimit) return toast("微调后合计不能超过总还款额", VanToastType.Fail)
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
                is ApiResult.Ok -> { vanToast.success("分期创建成功"); nav.navigate(Routes.CREDIT_INSTALLMENT_LIST) }
                is ApiResult.Fail -> toast(r.message.ifBlank { "创建失败" }, VanToastType.Fail)
                else -> toast(r.failMessage("创建失败"), r.failToastType())
            }
            submitting = false
        }
    }

    ScreenScaffold { inner ->
        Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                // ── .header-card：红色渐变 135deg，radius 12，padding 20，margin-bottom 16 ──
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(red, vanRed(Color(0xFFD91A4A))))).padding(20.dp),
                ) {
                    FText("创建分期", 20f, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(6.dp)) // .header-sub{ margin-top: 6px }
                    FText("信用卡分期 · 默认当月，仅可选今年内月份", 14f, color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(16.dp))

                // ── .section-title{font-size:13px→14; weight:600; padding-left:4px; margin-bottom:8px} ──
                FText(
                    "新建分期", 14f, FontWeight.SemiBold, colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )

                // ── 白底 app-field 行（label 在上 + 值在下 + 底边线）──
                ReadonlyRow("目标信用卡", cardLabelOf(selectedCard), "选择信用卡", onClick = { showCardPicker = true })
                ReadonlyRow("分期开始月份", "${startMonth.year}年${startMonth.monthValue}月", "选择首期归属月份", onClick = { showStartPicker = true })

                // ── .opt-row（透明底）+ .opt-note ──
                OptRow(
                    label = "入账归属",
                    options = listOf("current" to "本月账单", "next" to "次月账单"),
                    selected = enterMode,
                    onSelect = { enterMode = it },
                )
                OptNote("浦发等：账单日前对本月消费分期 → 本月就是第一期；工行等：次月才入第一期")

                ReadonlyRow("首期入账日期", formatDate(firstEnterDate), "选择首期入账日期", onClick = { showDatePicker = true })
                OptNote("默认账单日前 2 天（${anchorDay}号）入账，可手动调整；每期固定这一日")

                // ── .input-cell（白底、无分隔线）──
                InputCell("分期总额", formAmount.ifBlank { "0.00" }) {
                    keyboardField = "amount"; keyboardValue = formAmount; showKeyboard = true
                }
                InputCell("手续费/利息", formFee.ifBlank { "0.0000" }) {
                    keyboardField = "fee"; keyboardValue = formFee; showKeyboard = true
                }

                // ── app-field label="分期期数"：label 在上、按钮组在下 ──
                FieldBlock(label = "分期期数") { PeriodButtons(formPeriods) { formPeriods = it } }

                OptRow(
                    label = "本金取整",
                    options = listOf("floor" to "截断", "round" to "四舍五入"),
                    selected = principalMode,
                    onSelect = { principalMode = it },
                )
                OptNote("截断：200/12=16.66（舍）；四舍五入：200/12=16.67（入）")
                OptRow(
                    label = "尾差位置",
                    options = listOf("last" to "放在末期", "first" to "放在第一期"),
                    selected = tailMode,
                    onSelect = { tailMode = it },
                )
                OptNote("多出的零头放哪一期：末期（末月多还）或 第一期（首月多还）")

                // ── .preview-card（v-if="preview.periods > 0"）──
                if (periods > 0 && amount > 0.0 && schedule.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp)) // .preview-card{ margin-top: 12px }
                    PreviewCard(
                        standardPerPeriod = schedule[0],
                        totalText = totalText,
                        billDay = selectedCard?.billDay,
                        firstDate = planDates.firstOrNull(),
                        firstBillMonth = billMonthOf(planDates.firstOrNull() ?: now),
                        dates = planDates,
                        adjusted = adjustedList,
                        adjustedTotal = adjustedTotal,
                        exceedLimit = exceedLimit,
                        red = red,
                        onEdit = { idx, value ->
                            editIdx = idx; keyboardField = "adjust"
                            keyboardValue = String.format("%.2f", value); showKeyboard = true
                        },
                    )
                }

                Spacer(Modifier.height(16.dp)) // .form-actions{ margin-top: 16px }
                AppButton(
                    text = "确认创建分期",
                    onClick = { doSubmit() },
                    type = AppButtonType.Primary, block = true, round = true, disabled = !formReady || submitting, loading = submitting,
                )
                Spacer(Modifier.height(100.dp)) // .page-installment{ padding-bottom: 100px }
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
            // web `startColumns`：**年列只有今年**（`const years = [{text: `${curYear}年`}]`）
            // —— 页面注释「仅限今年内选择（用于补登/测试）」。旧实现给了去年/今年/明年三年，不符。
            val yearLabels = remember { listOf("${now.year}年") }
            val monthLabels = remember { (1..12).map { "${it}月" } }
            VanPicker(
                columns = vanPickerColumnsOf(yearLabels, monthLabels),
                value = listOf(0, startMonth.monthValue - 1),
                title = "分期开始月份",
                onConfirm = { sel, _ ->
                    startMonth = LocalDate.of(now.year, (sel.getOrNull(1) ?: 0) + 1, 1)
                    showStartPicker = false
                },
                onCancel = { showStartPicker = false },
            )
        }
    }
    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            // web `dateYears = [今年, 明年]`；日列长度由 **datePickerSel 的年月** 决定（不是首期日期的年月）
            val dateYears = remember { listOf(now.year, now.year + 1) }
            val monthLabels = remember { (1..12).map { "${it}月" } }
            val selYear = dateSel.getOrNull(0) ?: now.year
            val selMonth = dateSel.getOrNull(1) ?: 1
            val dim = remember(selYear, selMonth) { YearMonth.of(selYear, selMonth).lengthOfMonth() }
            val dayLabels = remember(dim) { (1..dim).map { "${it}日" } }
            VanPicker(
                columns = vanPickerColumnsOf(dateYears.map { "${it}年" }, monthLabels, dayLabels),
                value = listOf(
                    (selYear - now.year).coerceIn(0, 1),
                    (selMonth - 1).coerceIn(0, 11),
                    ((dateSel.getOrNull(2) ?: 1) - 1).coerceIn(0, dim - 1),
                ),
                // web `watch(datePickerSel)`：切年月时把日号收敛到该月实际天数，避免「2月31日」
                onValueChange = { sel ->
                    val y = now.year + (sel.getOrNull(0) ?: 0)
                    val m = (sel.getOrNull(1) ?: 0) + 1
                    val d = ((sel.getOrNull(2) ?: 0) + 1).coerceAtMost(YearMonth.of(y, m).lengthOfMonth())
                    dateSel = listOf(y, m, d)
                },
                title = "首期入账日期",
                onConfirm = { sel, _ ->
                    val y = now.year + (sel.getOrNull(0) ?: 0)
                    val m = (sel.getOrNull(1) ?: 0) + 1
                    val d = ((sel.getOrNull(2) ?: 0) + 1).coerceAtMost(YearMonth.of(y, m).lengthOfMonth())
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
            // web `watch(keyboardValue)`：amount / fee **实时**写回表单（预览跟着动），adjust 在关闭时落库
            onTextChange = { raw ->
                keyboardValue = raw
                val clean = raw.replace(Regex("[^\\d.]"), "")
                when (keyboardField) {
                    "amount" -> formAmount = clean
                    "fee" -> formFee = clean
                    else -> Unit
                }
            },
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

// ══════════════════════════ 局部件（严格照 web 结构） ══════════════════════════

/** web `app-field`（**含 label 的通用块**）：白底 + 上 label / 下控件 + 底边线。 */
@Composable
private fun FieldBlock(label: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            FText(label, 14f, color = colors.textSecondary) // .app-field__label{font-size:13px→14; text-secondary}
            Spacer(Modifier.height(6.dp)) // margin-bottom: 6px
            content()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border)) // border-bottom
    }
}

/**
 * web `.opt-row` + `.segment-switch`：**透明底**，label 左、分段控件右（`space-between`），`padding: 10px 0`。
 * 分段控件是**一个** `bgThird` 胶囊容器 + 内部 `theme-primary` 滑块（未选中项无独立底色）。
 */
@Composable
private fun OptRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FText(label, 16f, FontWeight.Medium, colors.textPrimary) // .opt-label{font-size:14px→16; weight:500}
        SegmentSwitch(options, selected, colors.primary, onSelect)
    }
}

/** web `.opt-note{font-size:11px→12; color:text-tertiary; margin-top:4px; line-height:1.4}` —— 独占一行。 */
@Composable
private fun OptNote(text: String) {
    val colors = LocalAppColors.current
    FText(text, 12f, color = colors.textTertiary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

/** web `.input-cell{display:flex; padding:10px 16px; background:--theme-bg-secondary}` —— 白底、**无**分隔线。 */
@Composable
private fun InputCell(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 16f, color = colors.textPrimary, modifier = Modifier.width(80.dp)) // .input-label{width:80px; 14px→16}
        Spacer(Modifier.weight(1f))
        FText(value, 18f, FontWeight.SemiBold, colors.danger) // .input-value{16px→18; weight:600; danger}
        Spacer(Modifier.width(8.dp)) // .input-arrow{ margin-left: 8px }
        VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
    }
}

/** web `.period-btns{display:flex; gap:2px; flex-wrap:wrap; padding:4px 0}` + `.period-btn`。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodButtons(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalAppColors.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PERIOD_OPTIONS.forEach { p ->
            val active = p == selected
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (active) colors.danger else colors.bgThird)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                FText(
                    "${p}期", 14f,
                    if (active) FontWeight.SemiBold else FontWeight.Normal,
                    if (active) Color.White else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * web `.segment-switch`：容器 `bgThird` + `padding:5px` + `radius:30px`；
 * 滑块 `theme-primary`、`width:100px`、`radius:24px`、`translateX(0|100px)`、`cubic-bezier(.4,0,.2,1) .28s`。
 */
@Composable
private fun SegmentSwitch(
    options: List<Pair<String, String>>,
    selected: String,
    thumbColor: Color,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val thumbX by animateDpAsState(
        SEG_ITEM_W * idx,
        tween(280, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "segThumb",
    )
    Box(
        Modifier.clip(RoundedCornerShape(30.dp)).background(colors.bgThird).padding(5.dp),
    ) {
        // 滑块先画（在下层），文字后画（在上层）
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = thumbX)
                .width(SEG_ITEM_W).height(SEG_ITEM_H)
                .clip(RoundedCornerShape(24.dp)).background(thumbColor),
        )
        Row {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(
                    Modifier.width(SEG_ITEM_W).height(SEG_ITEM_H)
                        .clip(RoundedCornerShape(24.dp)).clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    // .segment-item{font-size:13px→14; weight:600; color:text-secondary} / .active{color:#fff}
                    FText(text, 14f, FontWeight.SemiBold, if (active) Color.White else colors.textSecondary)
                }
            }
        }
    }
}

/** web `.preview-card` 全块。 */
@Composable
private fun PreviewCard(
    standardPerPeriod: Double,
    totalText: String,
    billDay: Int?,
    firstDate: LocalDate?,
    firstBillMonth: String,
    dates: List<LocalDate>,
    adjusted: List<Double>,
    adjustedTotal: Double,
    exceedLimit: Boolean,
    red: Color,
    onEdit: (Int, Double) -> Unit,
) {
    val colors = LocalAppColors.current
    val green = vanGreen()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgCard).padding(14.dp)) {
        // .preview-row：两端对齐、padding 6px 0、16f、text-primary；金额 700/danger，日期 600/green
        PreviewRow("每期金额", "￥${String.format("%.2f", standardPerPeriod)}", red, FontWeight.Bold)
        PreviewRow("总还款额", "￥$totalText", colors.textPrimary, FontWeight.Normal)
        PreviewRow("账单日", billDay?.let { "${it}号" } ?: "-", colors.textPrimary, FontWeight.Normal)
        PreviewRow("首期入账日", formatDate(firstDate), green, FontWeight.SemiBold)
        PreviewRow("首期归属账单", firstBillMonth, green, FontWeight.SemiBold)

        Spacer(Modifier.height(8.dp)) // .adjust-tip{ margin: 8px 0 2px }
        FText("系统将在每期入账日自动入账（账单日前 2 天），无需手动操作；点击每期金额可微调", 12f, color = colors.textTertiary)
        Spacer(Modifier.height(2.dp))

        // .preview-list{margin-top:6px; padding-top:8px; max-height:320px; overflow-y:auto}
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        ) {
            dates.forEachIndexed { idx, d ->
                val v = adjusted.getOrNull(idx) ?: standardPerPeriod
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, // .preview-item{ justify-content: space-between }
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText("第${idx + 1}期", 14f, FontWeight.Medium, colors.textPrimary) // .pi-idx
                    FText(formatDate(d), 14f, color = green) // .pi-date
                    // .pi-amount{ danger } + .editable{ text-decoration: underline dotted; cursor:pointer }
                    FText(
                        "￥${String.format("%.2f", v)}", 14f, color = red,
                        modifier = Modifier.dottedUnderline(red).clickable { onEdit(idx, v) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp)) // .adjust-summary{ margin-top: 8px }
        DashedDivider(colors.border) // border-top: 1px dashed --theme-border
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FText("微调后合计", 14f, color = colors.textPrimary)
            FText("￥${String.format("%.2f", adjustedTotal)} / ￥$totalText", 14f, FontWeight.SemiBold, red) // .as-amount
        }
        if (exceedLimit) {
            Spacer(Modifier.height(4.dp)) // .adjust-warn{ margin-top: 4px }
            FText("⚠ 微调后合计已超过分期总额+手续费，请调减", 12f, color = colors.danger)
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String, valueColor: Color, valueWeight: FontWeight) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp), // .preview-row{ padding: 6px 0 }
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 16f, color = colors.textPrimary) // 14px→16
        FText(value, 16f, valueWeight, valueColor)
    }
}

/** `text-decoration: underline dotted` —— Compose 没有虚线装饰，用 drawBehind 画一条点线。 */
private fun Modifier.dottedUnderline(color: Color): Modifier = drawBehind {
    val y = size.height - 1f
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f), 0f),
    )
}

/** `border-top: 1px dashed` —— 自绘虚线分隔。 */
@Composable
private fun DashedDivider(color: Color, dash: Dp = 4.dp, gap: Dp = 3.dp) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        val d = with(density) { dash.toPx() }
        val g = with(density) { gap.toPx() }
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(d, g), 0f),
        )
    }
}

/** 键盘落库（web `closeKeyboard` 原文：**各字段位数不同**）。 */
private fun keyboardCommit(
    field: String,
    raw: String,
    onAmount: (String) -> Unit,
    onFee: (String) -> Unit,
    onAdjust: (Double) -> Unit,
) {
    val parts = raw.replace(Regex("[^\\d.]"), "").split(".")
    val head = parts.getOrNull(0).orEmpty()
    val tail = parts.getOrNull(1).orEmpty()
    when (field) {
        // fee：整数 ≤ 8、小数 ≤ 4，上限 99999999.9999
        "fee" -> {
            var h = head.take(8)
            var t = tail.take(4)
            if ((h + "." + t).toDoubleOrNull()?.let { it > 99999999.9999 } == true) { h = "99999999"; t = "9999" }
            onFee(if (t.isEmpty()) h else "$h.$t")
        }
        // adjust：整数 ≤ 8、小数 ≤ 2，上限 99999999.99
        "adjust" -> {
            val v = if (tail.isEmpty()) head.take(8) else "${head.take(8)}.${tail.take(2)}"
            onAdjust(Math.min(v.toDoubleOrNull() ?: 0.0, 99999999.99))
        }
        // amount：整数 ≤ 8、小数 ≤ 2，上限 99999999.99
        else -> {
            var h = head.take(8)
            var t = tail.take(2)
            if ((h + "." + t).toDoubleOrNull()?.let { it > 99999999.99 } == true) { h = "99999999"; t = "99" }
            onAmount(if (t.isEmpty()) h else "$h.$t")
        }
    }
}

private fun formatDate(d: LocalDate?): String =
    d?.let { "%d年%02d月%02d日".format(it.year, it.monthValue, it.dayOfMonth) } ?: ""

private fun cardLabelOf(card: Card?): String =
    card?.let { "${it.alias.ifBlank { it.bankName }} (尾号${it.last4}) · 账单日${it.billDay}号" } ?: ""
