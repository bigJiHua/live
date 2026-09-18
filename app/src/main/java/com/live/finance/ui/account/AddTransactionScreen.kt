package com.live.finance.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.form.VanSearch
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanTabItem
import com.live.vant.nav.VanTabs
import com.live.vant.other.VanCalendar
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 记一笔（新增收支明细）—— 一比一复刻 web `src/views/Finance/account/Add.vue`。
 *
 * 结构（与 web DOM 同序）：
 *  1. `.amount-card`：币种胶囊 → 「请输入支出/收入/转账金额」→ 48px 金额（`van-number-keyboard` custom/extra-key="."/"完成"/maxlength 12）
 *     → 非 CNY 且已输金额时「约 ¥ formatMoney(折算)」；
 *  2. `.info-section`：`van-tabs` 支出/收入/转账 + 表单项（分类 → 支付方式 chips → 关联卡片 → 日期 → 汇率 → 备注；转账另有三模式）；
 *  3. `.submit-wrap`：`app-button primary block round`（文案 5 变体，`disabled=!canSubmit`）；
 *  4. 四个弹层：分类（3 列网格）/ 关联卡片（搜索列表）/ 转入卡片 / 币种 picker + `van-calendar` 日期。
 *
 * 提交：`POST /account/debit|credit`（按所选卡 card_type 分流），转账为两笔共用 `transferGroupId`；
 * 业务规则（支付方式集合/币种表/汇率语义/输入约束/虚拟卡 xxxx·yyyy）全部走 [RecordShared]。
 */
@Composable
fun AddTransactionScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    // ── 状态（对应 Add.vue 的 ref）──
    var type by remember { mutableStateOf(RecordType.Expense) }
    var amount by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    var payMethod by remember { mutableStateOf("") }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var showCardPicker by remember { mutableStateOf(false) }
    var cardSearchKey by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var remark by remember { mutableStateOf("") }

    var transferMode by remember { mutableStateOf(TransferMode.External) }
    var incomeMethod by remember { mutableStateOf(METHOD_DEBIT) }
    var selectedIncomeCard by remember { mutableStateOf<Card?>(null) }
    var showIncomePicker by remember { mutableStateOf(false) }
    var incomeSearchKey by remember { mutableStateOf("") }

    var currency by remember { mutableStateOf(DEFAULT_CURRENCY) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var exchangeRate by remember { mutableStateOf("") }

    // ── 数据加载（web onMounted：loadBankList + loadCategories + loadCardList）──
    LaunchedEffect(Unit) {
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
    }
    // 分类随方向变化重载；转账用 expense 分类里的「转账」并自动选中（web onTypeChange → loadCategories）
    LaunchedEffect(type) {
        selectedCategory = null
        val t = if (type == RecordType.Transfer) "expense" else type.key
        categories = (graph.category.list(t) as? ApiResult.Ok)?.data ?: emptyList()
        if (type == RecordType.Transfer) {
            categories.firstOrNull { it.name == "转账" }?.let { selectedCategory = it }
        }
    }

    // ── 派生（web computed）──
    val isExpense = type == RecordType.Expense
    val isIncome = type == RecordType.Income
    val isTransfer = type == RecordType.Transfer
    /** web `showCardCell`：支付方式非现金/余额时才需要选卡 */
    val needCard = payMethod.isNotEmpty() && payMethod != METHOD_CASH && payMethod != METHOD_BALANCE
    val cardColumns = cards.filter {
        when (payMethod) {
            METHOD_DEBIT -> it.cardType == "debit"
            METHOD_CREDIT -> it.cardType == "credit"
            else -> false
        }
    }
    val filteredCardColumns = filterCards(cardColumns, banks, cardSearchKey)
    val incomeCardColumns = run {
        // web incomeCardColumns：自转时排除已选的转出卡
        val excludeId = if (isTransfer && transferMode == TransferMode.Self) selectedCard?.id else null
        cards.filter {
            when (incomeMethod) {
                METHOD_DEBIT -> it.cardType == "debit"
                METHOD_BALANCE -> it.cardType == "virtual_balance"
                else -> false
            }
        }.filter { excludeId == null || it.id != excludeId }
    }
    val filteredIncomeCards = filterCards(incomeCardColumns, banks, incomeSearchKey)
    val sameCard = selectedCard?.id != null && selectedIncomeCard?.id != null &&
        selectedCard!!.id == selectedIncomeCard!!.id

    val canSubmit: Boolean = run {
        val hasAmount = amountValue(amount) != null
        if (isTransfer) {
            when (transferMode) {
                TransferMode.External -> hasAmount && payMethod.isNotEmpty() && (!needCard || selectedCard?.id != null)
                TransferMode.Withdraw -> hasAmount && selectedIncomeCard?.id != null
                TransferMode.Self -> hasAmount && payMethod.isNotEmpty() && (!needCard || selectedCard?.id != null) &&
                    incomeMethod.isNotEmpty() && selectedIncomeCard?.id != null && !sameCard
            }
        } else {
            hasAmount && selectedCategory?.id != null && payMethod.isNotEmpty() && (!needCard || selectedCard?.id != null)
        }
    }

    /** web onTypeChange */
    fun onTypeChange(t: RecordType) {
        type = t
        selectedCategory = null
        if (t == RecordType.Transfer) {
            payMethod = METHOD_DEBIT
            selectedCard = null
            incomeMethod = METHOD_DEBIT
            selectedIncomeCard = null
            remark = ""
            return
        }
        if (t != RecordType.Expense && payMethod == METHOD_CREDIT) {
            payMethod = ""
            selectedCard = null
        }
    }

    /** web `watch(transferMode)` */
    fun onTransferModeChange(m: TransferMode) {
        transferMode = m
        payMethod = if (m == TransferMode.Withdraw) METHOD_BALANCE else METHOD_DEBIT
        incomeMethod = METHOD_DEBIT
        selectedCard = null
        selectedIncomeCard = null
        if (m == TransferMode.Withdraw) incomeSearchKey = ""
    }

    fun submitLabel(): String = when {
        isExpense -> "记一笔支出"
        isIncome -> "记一笔收入"
        transferMode == TransferMode.External -> "登记转账支出"
        transferMode == TransferMode.Withdraw -> "确认提现"
        else -> "登记自转账"
    }

    fun doSubmit() {
        if (!canSubmit || submitting) return
        val amt = amountValue(amount) ?: return
        val catId = selectedCategory?.id.orEmpty()
        val catName = selectedCategory?.name.orEmpty()
        val date = selectedDate.toString()
        val rate = submitRate(exchangeRate, currency)
        val base = { direction: Int, payType: String, method: String, cardId: String, remarkText: String, tg: String ->
            RecordPayload(
                direction = direction, categoryId = catId, payType = payType, payMethod = method,
                amount = amt, currency = currency.code, exchangeRate = rate, transDate = date,
                cardId = cardId, remark = remarkText, transferGroupId = tg,
            )
        }
        val payloads: List<RecordPayload> = when {
            isTransfer && transferMode == TransferMode.External ->
                listOf(base(0, "转账", payMethod, buildCardId(payMethod, selectedCard?.id), remark.trim(), ""))
            isTransfer && transferMode == TransferMode.Withdraw -> {
                val tg = newTransferGroupId()
                val text = remark.trim().ifEmpty { "提现" }
                listOf(
                    base(0, "转账", METHOD_BALANCE, VIRTUAL_BALANCE_ID, text, tg),
                    base(1, "转账", METHOD_DEBIT, selectedIncomeCard?.id.orEmpty(), text, tg),
                )
            }
            isTransfer -> {   // 自转
                val tg = newTransferGroupId()
                val text = remark.trim()
                listOf(
                    base(0, "转账", payMethod, buildCardId(payMethod, selectedCard?.id), if (text.isEmpty()) "转出" else "转出 - $text", tg),
                    base(1, "转账", incomeMethod, buildCardId(incomeMethod, selectedIncomeCard?.id), if (text.isEmpty()) "转入" else "转入 - $text", tg),
                )
            }
            else -> listOf(
                base(if (isExpense) 0 else 1, catName, payMethod, buildCardId(payMethod, selectedCard?.id), remark.trim(), ""),
            )
        }
        submitting = true
        scope.launch {
            submitRecordFlow(graph, cards, payloads, "提交成功", nav, toast)
            submitting = false
        }
    }

    ScreenScaffold { inner ->
        Column(
            inner
                .verticalScroll(rememberScrollState())
                .background(colors.bgPage),
        ) {
            // ───────── ① 金额卡（web .amount-card） ─────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard)
                    .border(1.dp, colors.border)
                    .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { showKeyboard = true },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 币种胶囊（web .currency-selector：bg-primary 底 + radius 16 + 13px）
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.bgPage)
                        .clickable { showCurrencyPicker = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(currency.label, 13f, FontWeight.Normal, colors.textPrimary)
                    Spacer(Modifier.width(4.dp))
                    VanIcon(name = "arrow-down", size = 13.sp, color = colors.textPrimary)
                }
                Spacer(Modifier.height(12.dp))   // .currency-selector { margin-bottom: 12px }
                FText("请输入${if (isExpense) "支出" else if (isIncome) "收入" else "转账"}金额", 14f, FontWeight.Normal, colors.textTertiary)
                Spacer(Modifier.height(15.dp))   // .value { margin-top: 15px }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(currency.symbol, 32f, FontWeight.Normal, colors.textPrimary)
                    Spacer(Modifier.width(2.dp))  // .currency-symbol { margin-right: 2px }
                    FText(amount.ifBlank { "0.00" }, 48f, FontWeight.Bold, colors.textPrimary)
                }
                if (currency.code != "CNY" && amount.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    FText(
                        "约 ¥ ${Money.formatMoney(exchangedCny(amount, exchangeRate, currency))}",
                        13f, FontWeight.Normal, colors.textTertiary,
                    )
                }
            }

            // ───────── ② 表单区（web .info-section：bg-secondary + margin-top 12） ─────────
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
                VanTabs(
                    active = type.key,
                    onActiveChange = { key -> onTypeChange(RecordType.values().first { t -> t.key == key }) },
                    tabs = RecordType.values().map { VanTabItem(name = it.key, title = it.title) },
                    animated = true,
                ) { /* 表单在 tabs 之外，内容区留空 */ }

                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    if (!isTransfer) {
                        // 分类
                        AppCell(
                            title = "分类",
                            value = selectedCategory?.name ?: "请选择分类",
                            valuePlaceholder = selectedCategory == null,
                            isLink = true,
                            onClick = { showCategoryPicker = true },
                        )
                        // 支付方式 chips（web .method-row）
                        MethodChipsRow(
                            label = if (isExpense) "支出方式" else "收入方式",
                            methods = payMethodsFor(type),
                            selected = payMethod,
                            onSelect = { payMethod = it; selectedCard = null },
                        )
                        // 关联卡片
                        if (needCard) {
                            AppCell(
                                title = payMethod,
                                value = selectedCard?.let { cardDisplayText(it, banks) } ?: "请选择",
                                valuePlaceholder = selectedCard?.id == null,
                                isLink = true,
                                onClick = { showCardPicker = true },
                            )
                        }
                        // 日期
                        AppCell(
                            title = "日期",
                            value = selectedDate.toString(),
                            isLink = true,
                            onClick = { showDatePicker = true },
                        )
                        // 汇率（非 CNY，web `<app-field type="number">`）
                        if (currency.code != "CNY") {
                            AppField(
                                value = exchangeRate,
                                onValueChange = { exchangeRate = filterRateInput(it) },
                                label = "汇率",
                                placeholder = "如: 684.5125",
                                keyboardType = KeyboardType.Decimal,
                                onFocus = { showKeyboard = false },   // web @focus 收起金额键盘
                            )
                        }
                        // 备注（支出）/ 说明（收入）
                        AppField(
                            value = remark,
                            onValueChange = { remark = it },
                            label = if (isExpense) "备注" else "说明",
                            placeholder = "选填",
                            clearable = true,
                            border = false,
                        )
                    } else {
                        // 转账三模式（web .transfer-toggle）
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TransferMode.entries.forEach { m ->
                                TransferToggleButton(
                                    text = when (m) {
                                        TransferMode.External -> "对外"
                                        TransferMode.Self -> "自转"
                                        TransferMode.Withdraw -> "提现"
                                    },
                                    active = transferMode == m,
                                    onClick = { onTransferModeChange(m) },
                                )
                            }
                        }
                        when (transferMode) {
                            TransferMode.External -> {
                                AppCell(title = "分类", value = "转账")
                                MethodChipsRow(
                                    label = "支出方式",
                                    methods = listOf(METHOD_DEBIT),
                                    selected = METHOD_DEBIT,
                                    onSelect = { },
                                )
                                if (needCard) {
                                    AppCell(
                                        title = payMethod,
                                        value = selectedCard?.let { cardDisplayText(it, banks) } ?: "请选择",
                                        valuePlaceholder = selectedCard?.id == null,
                                        isLink = true,
                                        onClick = { showCardPicker = true },
                                    )
                                }
                                AppCell(title = "日期", value = selectedDate.toString(), isLink = true, onClick = { showDatePicker = true })
                                AppField(
                                    value = remark,
                                    onValueChange = { remark = it },
                                    label = "备注",
                                    placeholder = "收款人：xxx",
                                    border = false,
                                )
                            }
                            TransferMode.Self -> {
                                AppCell(
                                    title = "转出账户",
                                    value = selectedCard?.let { cardDisplayText(it, banks) } ?: "选择转出卡片",
                                    valuePlaceholder = selectedCard?.id == null,
                                    isLink = true,
                                    onClick = { showCardPicker = true },
                                )
                                AppCell(
                                    title = "转入账户",
                                    value = selectedIncomeCard?.let { cardDisplayText(it, banks) } ?: "选择转入卡片",
                                    valuePlaceholder = selectedIncomeCard?.id == null,
                                    isLink = true,
                                    onClick = { showIncomePicker = true },
                                )
                                AppCell(title = "日期", value = selectedDate.toString(), isLink = true, onClick = { showDatePicker = true })
                                AppField(
                                    value = remark,
                                    onValueChange = { remark = it },
                                    label = "备注",
                                    placeholder = "选填",
                                    clearable = true,
                                    border = false,
                                )
                            }
                            TransferMode.Withdraw -> {
                                AppCell(title = "转出账户", value = "余额")
                                AppCell(
                                    title = "提现到卡",
                                    value = selectedIncomeCard?.let { cardDisplayText(it, banks) } ?: "选择到账卡片",
                                    valuePlaceholder = selectedIncomeCard?.id == null,
                                    isLink = true,
                                    onClick = { showIncomePicker = true },
                                )
                                AppCell(title = "日期", value = selectedDate.toString(), isLink = true, onClick = { showDatePicker = true })
                                AppField(
                                    value = remark,
                                    onValueChange = { remark = it },
                                    label = "备注",
                                    placeholder = "选填",
                                    clearable = true,
                                    border = false,
                                )
                            }
                        }
                    }
                }
            }

            // ───────── ③ 提交（web .submit-wrap：padding 30/20） ─────────
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp)) {
                AppButton(
                    text = submitLabel(),
                    type = AppButtonType.Primary,
                    block = true,
                    round = true,
                    disabled = !canSubmit,
                    loading = submitting,
                    onClick = { doSubmit() },
                )
            }
        }
    }

    // ───────── ④ 数字键盘（van-number-keyboard custom/extra-key="."/"完成"/maxlength 12） ─────────
    com.live.vant.form.VanNumberKeyboard(
        show = showKeyboard,
        text = amount,
        onTextChange = { amount = filterAmountInput(it); showKeyboard = true },
        theme = com.live.vant.form.VanKeyboardTheme.Custom,
        closeButtonText = "完成",
        extraKey = listOf("."),
        maxLength = 12,
        onClose = { showKeyboard = false },
        onBlur = { showKeyboard = false },
    )

    // ───────── ⑤ 分类弹层（web .category-popup：3 列网格 + title 弹窗头） ─────────
    VanPopup(
        show = showCategoryPicker,
        onDismissRequest = { showCategoryPicker = false },
        position = VanPopupPosition.Bottom,
        round = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PopupHeader("选择分类") { showCategoryPicker = false }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                categories.chunked(3).forEach { rowCats ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowCats.forEach { cat ->
                            CategoryTile(
                                name = cat.name,
                                active = selectedCategory?.id == cat.id,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategory = cat; showCategoryPicker = false },
                            )
                        }
                        repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    // ───────── ⑥ 关联卡片弹层 ─────────
    CardPickerPopup(
        show = showCardPicker,
        title = "选择关联卡片",
        cards = filteredCardColumns,
        banks = banks,
        searchKey = cardSearchKey,
        onSearchChange = { cardSearchKey = it },
        selectedId = selectedCard?.id,
        emptyText = "未找到匹配的银行卡",
        onDismiss = { showCardPicker = false; cardSearchKey = "" },
        onSelect = { selectedCard = it; cardSearchKey = ""; showCardPicker = false },
    )

    // ───────── ⑦ 转入卡片弹层 ─────────
    CardPickerPopup(
        show = showIncomePicker,
        title = "选择转入卡片",
        cards = filteredIncomeCards,
        banks = banks,
        searchKey = incomeSearchKey,
        onSearchChange = { incomeSearchKey = it },
        selectedId = selectedIncomeCard?.id,
        emptyText = "未找到匹配的银行卡",
        onDismiss = { showIncomePicker = false; incomeSearchKey = "" },
        onSelect = { selectedIncomeCard = it; incomeSearchKey = ""; showIncomePicker = false },
    )

    // ───────── ⑧ 币种 picker ─────────
    VanPopup(
        show = showCurrencyPicker,
        onDismissRequest = { showCurrencyPicker = false },
        position = VanPopupPosition.Bottom,
        round = true,
    ) {
        VanPicker(
            columns = listOf(CURRENCIES.map { VanPickerOption(currencyPickerText(it), it.code) }),
            value = listOf(CURRENCIES.indexOf(currency).coerceAtLeast(0)),
            title = "选择币种",
            onConfirm = { idx, _ ->
                currency = CURRENCIES.getOrElse(idx.firstOrNull() ?: 0) { DEFAULT_CURRENCY }
                showCurrencyPicker = false
            },
            onCancel = { showCurrencyPicker = false },
        )
    }

    // ───────── ⑨ 日期日历（web: min 2020-01-01 / max 今天） ─────────
    VanCalendar(
        show = showDatePicker,
        onClose = { showDatePicker = false },
        minDate = LocalDate.of(2020, 1, 1),
        maxDate = LocalDate.now(),
        defaultDate = listOf(selectedDate),
        onConfirm = { dates -> dates.firstOrNull()?.let { selectedDate = it }; showDatePicker = false },
    )
}

// ───────────────────────────── 页内组件 ─────────────────────────────

/** web `.popup-header`：左右分布、padding 16、1px 边框、16px/600 + 关闭图标。 */
@Composable
private fun PopupHeader(title: String, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 16f, FontWeight.SemiBold, colors.textPrimary)
        VanIcon(name = "cross", size = 16.sp, color = colors.textSecondary, onClick = onClose)
    }
}

/** web `.method-row`：左标签 + 右侧 chips（选中 = 绿边 + 8% 绿底 + 绿字）。 */
@Composable
private fun MethodChipsRow(
    label: String,
    methods: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FText(label, 14f, FontWeight.Normal, colors.textPrimary)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            methods.forEach { m ->
                val active = selected == m
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (active) colors.success.copy(alpha = 0.08f) else colors.bgCard)
                        .border(1.dp, if (active) colors.success else colors.border, RoundedCornerShape(5.dp))
                        .clickable { onSelect(m) }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                ) {
                    FText(m, 10.4f, FontWeight.Normal, if (active) colors.success else colors.textPrimary)
                }
            }
        }
    }
}

/** web `.toggle-btn`：12px 胶囊（选中 = 主色底白字）。 */
@Composable
private fun TransferToggleButton(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) tokens.primary else colors.bgThird)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        FText(text, 12f, FontWeight.Normal, if (active) Color.White else colors.textTertiary)
    }
}

/** web `.category-tile`：3 列网格里的分类格（选中 = 绿边 + 8% 绿底 + 右上打勾）。 */
@Composable
private fun CategoryTile(name: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (active) colors.success.copy(alpha = 0.08f) else colors.bgCard)
                .border(1.dp, if (active) colors.success else colors.border, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FText(name, 12f, FontWeight.Normal, colors.textPrimary)
        }
        if (active) {
            VanIcon(
                name = "success",
                size = 14.sp,
                color = Color(0xFF07C160),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

/** 卡片选择弹层（web `.card-picker-popup`：弹窗头 + 搜索 + 列表，选中打勾）。 */
@Composable
private fun CardPickerPopup(
    show: Boolean,
    title: String,
    cards: List<Card>,
    banks: List<Category>,
    searchKey: String,
    onSearchChange: (String) -> Unit,
    selectedId: String?,
    emptyText: String,
    onDismiss: () -> Unit,
    onSelect: (Card) -> Unit,
) {
    val colors = LocalAppColors.current
    VanPopup(show = show, onDismissRequest = onDismiss, position = VanPopupPosition.Bottom, round = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PopupHeader(title, onDismiss)
            VanSearch(
                value = searchKey,
                onValueChange = onSearchChange,
                placeholder = "搜索银行卡",
                showAction = true,
                onCancel = { onSearchChange("") },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                cards.forEach { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border)
                            .clickable { onSelect(card) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FText(cardDisplayText(card, banks), 14f, FontWeight.Normal, colors.textPrimary)
                        if (card.id == selectedId) VanIcon(name = "success", size = 16.sp, color = Color(0xFF07C160))
                    }
                }
                if (cards.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        FText(emptyText, 14f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }
        }
    }
}
