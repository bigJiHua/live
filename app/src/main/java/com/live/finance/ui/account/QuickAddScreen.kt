package com.live.finance.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.form.VanSearch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** web `.big-btn.cash / .withdraw` 用的固定橙（= `--van-orange` 两模式同值，不受 mono 影响）。 */
private val OrangeFixed = Color(0xFFFF976A)

/** 银行卡图标兜底色（web 硬编码：信用卡 #ee0a24、借记卡 #1989fa）。 */
private val BankIconCredit = Color(0xFFEE0A24)
private val BankIconDebit = Color(0xFF1989FA)

/**
 * 快速登记 —— 一比一复刻 web `src/views/Finance/account/QuickAdd.vue`（5 步向导）。
 *
 * 步骤：方向 → 分类（转账则选三种子模式）→ 账户 → 金额 → 确认；顶部 sticky 步骤条可回退、可点已完成步。
 * 关键交互（与 web 一致）：普通收支选完分类/卡片**自动进下一步**；提现选完到账卡 80ms 后自动进下一步；
 * 转账三模式需手动点「下一步」；进入金额步自动唤起数字键盘。
 *
 * 提交：与「记一笔」共用 [submitRecordFlow]（转账/提现为两笔共用 `transferGroupId`），
 * 但成功文案不同（登记成功 / 自转登记成功 / 提现登记成功）。
 */
@Composable
fun QuickAddScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    // ── 向导状态 ──
    var step by remember { mutableIntStateOf(1) }
    val stepLabels = listOf("方向", "分类", "账户", "金额", "确认")

    // ── 表单（对应 web form ref）──
    var type by remember { mutableStateOf<RecordType?>(null) }
    var transferMode by remember { mutableStateOf<TransferMode?>(null) }
    var categoryId by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var cardId by remember { mutableStateOf("") }
    var cardLabel by remember { mutableStateOf("") }
    var cardType by remember { mutableStateOf("") }
    var outCardId by remember { mutableStateOf("") }
    var outCardLabel by remember { mutableStateOf("") }
    var outCardType by remember { mutableStateOf("") }
    var inCardId by remember { mutableStateOf("") }
    var inCardLabel by remember { mutableStateOf("") }
    var inCardType by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    val transDate = remember { todayString() }   // QuickAdd 无日期选择，固定当天

    var currency by remember { mutableStateOf(DEFAULT_CURRENCY) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var exchangeRate by remember { mutableStateOf("") }

    var cardTab by remember { mutableStateOf("debit") }   // 信用卡 / 借记卡
    var cardSearchKey by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    // ── 数据 ──
    var expenseCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var incomeCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }

    // web onMounted：Promise.all 拉 4 份数据（失败各自兜底为空）
    LaunchedEffect(Unit) {
        coroutineScope {
            val e = async { graph.category.list("expense") }
            val i = async { graph.category.list("income") }
            val c = async { graph.card.list(null) }
            val b = async { graph.category.list("bank") }
            expenseCats = (e.await() as? ApiResult.Ok)?.data ?: emptyList()
            incomeCats = (i.await() as? ApiResult.Ok)?.data ?: emptyList()
            cards = (c.await() as? ApiResult.Ok)?.data ?: emptyList()
            banks = (b.await() as? ApiResult.Ok)?.data ?: emptyList()
        }
    }

    // 进入金额步自动唤起键盘（web watch(step)：step===4 → 50ms 后 showKeyboard = true）
    LaunchedEffect(step) {
        if (step == 4) {
            delay(50)
            showKeyboard = true
        } else {
            showKeyboard = false
        }
    }

    // ── 派生（web computed）──
    val isTransfer = type == RecordType.Transfer
    val isIncome = type == RecordType.Income
    val ts = transferMode
    val realCardList = realCards(cards)
    val categoryOptions: List<Category> = when {
        isTransfer -> emptyList()
        type == RecordType.Expense -> expenseCats
        type == RecordType.Income -> incomeCats
        else -> emptyList()
    }
    /** web displayedRealCards：对外转账/收入只看借记卡，其余按 cardTab 过滤，再套搜索。 */
    val displayedCards: List<Card> = run {
        val base = when {
            isTransfer && ts == TransferMode.External -> realCardList.filter { it.cardType == "debit" }
            isIncome -> realCardList.filter { it.cardType == "debit" }
            else -> realCardList.filter { it.cardType == cardTab }
        }
        filterCards(base, banks, cardSearchKey)
    }
    val selfOutCards = filterCards(realCardList.filter { it.cardType == "debit" }, banks, cardSearchKey)
    val selfInCards = filterCards(realCardList.filter { it.cardType == "debit" }, banks, cardSearchKey)
    val withdrawInCards = filterCards(realCardList.filter { it.cardType == "debit" }, banks, cardSearchKey)

    val amountOk = amountValue(amount) != null

    val canPrev = step > 1
    val canNext: Boolean = when (step) {
        1 -> type != null
        2 -> if (isTransfer) ts != null else categoryId.isNotEmpty()
        3 -> when {
            isTransfer && ts == TransferMode.Self -> outCardId.isNotEmpty() && inCardId.isNotEmpty() && outCardId != inCardId
            isTransfer && ts == TransferMode.Withdraw ->
                inCardId.isNotEmpty() && inCardId != VIRTUAL_CASH_ID && inCardId != VIRTUAL_BALANCE_ID
            isTransfer && ts == TransferMode.External ->
                cardId.isNotEmpty() && cardId != VIRTUAL_CASH_ID && cardId != VIRTUAL_BALANCE_ID
            else -> cardId.isNotEmpty()
        }
        4 -> amountOk
        else -> false
    }
    val canSubmit: Boolean = run {
        if (!amountOk) return@run false
        if (isTransfer) {
            return@run when (ts) {
                TransferMode.Self -> outCardId.isNotEmpty() && inCardId.isNotEmpty() && outCardId != inCardId
                TransferMode.Withdraw ->
                    inCardId.isNotEmpty() && inCardId != VIRTUAL_CASH_ID && inCardId != VIRTUAL_BALANCE_ID
                TransferMode.External ->
                    cardId.isNotEmpty() && cardId != VIRTUAL_CASH_ID && cardId != VIRTUAL_BALANCE_ID && payee.trim().isNotEmpty()
                null -> false
            }
        }
        type != null && categoryId.isNotEmpty() && cardId.isNotEmpty()
    }

    val remarkTitle: String = when {
        isTransfer && ts == TransferMode.External -> "转给谁？"
        else -> "补充信息（可选）"
    }
    val directionLabel: String = when (type) {
        RecordType.Expense -> "支出"
        RecordType.Income -> "收入"
        RecordType.Transfer -> "转账"
        null -> "-"
    }
    val confirmText: String = when {
        isTransfer && ts == TransferMode.Withdraw -> "确认提现"
        isTransfer && ts == TransferMode.Self -> "确认自转"
        else -> "确认登记"
    }

    // ── 动作（对应 web 的 select* 系列）──
    fun selectDirection(t: RecordType) {
        type = t
        categoryId = ""
        categoryName = ""
        cardId = ""; cardLabel = ""; cardType = ""
        outCardId = ""; outCardLabel = ""; outCardType = ""
        inCardId = ""; inCardLabel = ""; inCardType = ""
        payee = ""; remark = ""
        transferMode = null
        currency = DEFAULT_CURRENCY
        exchangeRate = ""
        cardTab = "debit"
        cardSearchKey = ""
        step = 2
    }

    fun selectTransferMode(mode: TransferMode) {
        transferMode = mode
        // 分类固定为「转账」（与 Add.vue 一致：取 expense 分类里的同名项）
        expenseCats.firstOrNull { it.name == "转账" }?.let { categoryId = it.id; categoryName = it.name }
        cardId = ""; cardLabel = ""; cardType = ""
        outCardId = ""; outCardLabel = ""; outCardType = ""
        inCardId = ""; inCardLabel = ""; inCardType = ""
        step = 3
    }

    fun selectCategory(cat: Category) {
        categoryId = cat.id
        categoryName = cat.name
        step = 3
    }

    fun selectVirtualCard(id: String, label: String, typeText: String) {
        cardId = id; cardLabel = label; cardType = typeText
        step = 4
    }

    fun selectRealCard(card: Card) {
        cardId = card.id
        cardLabel = cardDisplayText(card, banks)
        cardType = cardTypeText(card.cardType)
        step = 4
    }

    fun selectSelfOut(card: Card) {
        outCardId = card.id
        outCardLabel = cardDisplayText(card, banks)
        outCardType = cardTypeText(card.cardType)
    }

    fun selectSelfIn(card: Card) {
        inCardId = card.id
        inCardLabel = cardDisplayText(card, banks)
        inCardType = cardTypeText(card.cardType)
        // 提现：选完到账卡自动进入金额步（web 80ms setTimeout）
        if (isTransfer && ts == TransferMode.Withdraw) {
            scope.launch { delay(80); if (canNext) step = 4 }
        }
    }

    fun doSubmit() {
        if (!canSubmit || submitting) return
        val amt = amountValue(amount) ?: return
        val rate = submitRate(exchangeRate, currency)
        val base = { direction: Int, payType: String, method: String, cid: String, remarkText: String, tg: String ->
            RecordPayload(
                direction = direction, categoryId = categoryId, payType = payType, payMethod = method,
                amount = amt, currency = currency.code, exchangeRate = rate, transDate = transDate,
                cardId = cid, remark = remarkText, transferGroupId = tg,
            )
        }
        val payloads: List<RecordPayload>
        val successText: String
        when {
            isTransfer && ts == TransferMode.External -> {
                payloads = listOf(base(0, "转账", cardType, buildCardId(cardType, cardId), payee.trim(), ""))
                successText = "登记成功"
            }
            isTransfer && ts == TransferMode.Self -> {
                val tg = newTransferGroupId()
                val text = remark.trim()
                payloads = listOf(
                    base(0, "转账", outCardType, buildCardId(outCardType, outCardId), if (text.isEmpty()) "转出" else "转出 - $text", tg),
                    base(1, "转账", inCardType, buildCardId(inCardType, inCardId), if (text.isEmpty()) "转入" else "转入 - $text", tg),
                )
                successText = "自转登记成功"
            }
            isTransfer && ts == TransferMode.Withdraw -> {
                val tg = newTransferGroupId()
                val text = remark.trim().ifEmpty { "提现" }
                payloads = listOf(
                    base(0, "转账", METHOD_BALANCE, VIRTUAL_BALANCE_ID, text, tg),
                    base(1, "转账", inCardType, inCardId, text, tg),
                )
                successText = "提现登记成功"
            }
            else -> {
                payloads = listOf(base(if (type == RecordType.Income) 1 else 0, categoryName, cardType, buildCardId(cardType, cardId), remark.trim(), ""))
                successText = "登记成功"
            }
        }
        submitting = true
        scope.launch {
            submitRecordFlow(graph, cards, payloads, successText, nav, toast)
            submitting = false
        }
    }

    ScreenScaffold { inner ->
        Column(inner.background(colors.bgPage)) {
            // ───────── 顶部步骤条（web .qsa-bar：sticky top 0） ─────────
            StepBar(
                step = step,
                labels = stepLabels,
                canPrev = canPrev,
                canNext = canNext && step < 5,
                onPrev = { if (canPrev) step -= 1 },
                onNext = { if (canNext && step < 5) step += 1 },
                onStepClick = { target -> if (target < step) step = target },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
            ) {
                when (step) {
                    // ───────── Step 1：方向 ─────────
                    1 -> {
                        StepTitle("这一笔是？")
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            BigButton(
                                icon = "arrow-up", label = "支出", sub = "花钱出去",
                                iconColor = colors.danger,
                                borderColor = colors.danger.copy(alpha = 0.3f),
                                activeBg = colors.danger.copy(alpha = 0.08f),
                                active = false, minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                onClick = { selectDirection(RecordType.Expense) },
                            )
                            BigButton(
                                icon = "arrow-down", label = "收入", sub = "钱到账了",
                                iconColor = colors.success,
                                borderColor = colors.success.copy(alpha = 0.3f),
                                activeBg = colors.success.copy(alpha = 0.08f),
                                active = false, minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                onClick = { selectDirection(RecordType.Income) },
                            )
                            BigButton(
                                icon = "exchange", label = "转账", sub = "A卡 → B卡",
                                iconColor = tokens.primary,
                                borderColor = tokens.primary.copy(alpha = 0.3f),
                                activeBg = tokens.primary.copy(alpha = 0.1f),
                                active = false, minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                onClick = { selectDirection(RecordType.Transfer) },
                            )
                        }
                    }

                    // ───────── Step 2：分类 / 转账子模式 ─────────
                    2 -> {
                        StepTitle(if (isTransfer) "哪种转账？" else "哪个分类？")
                        if (isTransfer) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                BigButton(
                                    icon = "send-gift-o", label = "对外转账", sub = "转给他人 · 单笔支出",
                                    iconColor = tokens.primary,
                                    borderColor = tokens.primary.copy(alpha = 0.3f),
                                    activeBg = tokens.primary.copy(alpha = 0.1f),
                                    active = ts == TransferMode.External,
                                    minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                    onClick = { selectTransferMode(TransferMode.External) },
                                )
                                BigButton(
                                    icon = "exchange", label = "自转", sub = "我的卡之间互转",
                                    iconColor = colors.success,
                                    borderColor = colors.success.copy(alpha = 0.3f),
                                    activeBg = colors.success.copy(alpha = 0.08f),
                                    active = ts == TransferMode.Self,
                                    minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                    onClick = { selectTransferMode(TransferMode.Self) },
                                )
                                BigButton(
                                    icon = "cash-back-record-o", label = "提现", sub = "余额 → 银行卡",
                                    iconColor = OrangeFixed,
                                    borderColor = OrangeFixed.copy(alpha = 0.3f),
                                    activeBg = OrangeFixed.copy(alpha = 0.12f),
                                    active = ts == TransferMode.Withdraw,
                                    minHeight = 120.dp, iconSize = 44.sp, labelSize = 20f,
                                    onClick = { selectTransferMode(TransferMode.Withdraw) },
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                categoryOptions.chunked(3).forEach { rowCats ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        rowCats.forEach { cat ->
                                            BigButton(
                                                icon = null, label = cat.name, sub = null,
                                                iconColor = tokens.primary,
                                                borderColor = colors.border,
                                                activeBg = tokens.primary.copy(alpha = 0.1f),
                                                active = categoryId == cat.id,
                                                minHeight = 96.dp, iconSize = 26.sp, labelSize = 15f,
                                                modifier = Modifier.weight(1f),
                                                onClick = { selectCategory(cat) },
                                            )
                                        }
                                        repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                                if (categoryOptions.isEmpty()) EmptyTip("该方向暂无可用分类")
                            }
                        }
                    }

                    // ───────── Step 3：账户 ─────────
                    3 -> {
                        StepTitle(
                            when {
                                isTransfer && ts == TransferMode.Self -> "从哪张卡转出 / 转入？"
                                isTransfer && ts == TransferMode.Withdraw -> "提现到哪张卡？"
                                isTransfer && ts == TransferMode.External -> "从哪张卡转出？"
                                else -> "从哪出 / 进？"
                            },
                        )
                        when {
                            // 自转：左右两列（转出 / 转入），互斥禁用
                            isTransfer && ts == TransferMode.Self -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SideLabel("转出", "arrow-up", MoneyColor.expense, alignEnd = false)
                                        Column(
                                            Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            selfOutCards.forEach { card ->
                                                QuickCardTile(
                                                    card = card, banks = banks,
                                                    selected = outCardId == card.id,
                                                    disabled = inCardId == card.id,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onClick = { selectSelfOut(card) },
                                                )
                                            }
                                            if (selfOutCards.isEmpty()) EmptyTip("暂无可选转出卡")
                                        }
                                    }
                                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                                        VanIcon(name = "arrow", size = 18.sp, color = tokens.primary)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SideLabel("转入", "arrow-down", MoneyColor.income, alignEnd = true)
                                        Column(
                                            Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            selfInCards.forEach { card ->
                                                QuickCardTile(
                                                    card = card, banks = banks,
                                                    selected = inCardId == card.id,
                                                    disabled = outCardId == card.id,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onClick = { selectSelfIn(card) },
                                                )
                                            }
                                            if (selfInCards.isEmpty()) EmptyTip("暂无可选转入卡")
                                        }
                                    }
                                }
                            }

                            // 提现：顶部固定「余额」→ 到账借记卡
                            isTransfer && ts == TransferMode.Withdraw -> {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    SideLabel("从", "arrow-up", colors.danger, alignEnd = false)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        Modifier
                                            .border(1.5.dp, tokens.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            .background(colors.bgCard, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        VanIcon(name = "balance-o", size = 28.sp, color = BankIconDebit)
                                        Spacer(Modifier.width(8.dp))
                                        FText("余额", 16f, FontWeight.SemiBold, tokens.primary)
                                    }
                                }
                                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    VanIcon(name = "arrow-down", size = 18.sp, color = tokens.primary)
                                }
                                SectionDivider("到账借记卡")
                                VanSearch(
                                    value = cardSearchKey,
                                    onValueChange = { cardSearchKey = it },
                                    placeholder = "搜索银行名/尾号",
                                    shape = "round",
                                    background = Color.Transparent,
                                    clearable = true,
                                )
                                Spacer(Modifier.height(10.dp))
                                CardGrid(
                                    cards = withdrawInCards,
                                    banks = banks,
                                    selectedId = inCardId,
                                    emptyText = "暂无借记卡",
                                    checkColor = BankIconDebit,
                                    onSelect = { selectSelfIn(it) },
                                )
                            }

                            // 对外转账 / 普通收支
                            else -> {
                                if (!isTransfer) {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        BigButton(
                                            icon = "cash-back-record-o", label = "现金", sub = null,
                                            iconColor = OrangeFixed, borderColor = OrangeFixed.copy(alpha = 0.3f),
                                            activeBg = OrangeFixed.copy(alpha = 0.12f), active = cardId == VIRTUAL_CASH_ID,
                                            minHeight = 48.dp, iconSize = 20.sp, labelSize = 13f,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectVirtualCard(VIRTUAL_CASH_ID, "现金", METHOD_CASH) },
                                        )
                                        BigButton(
                                            icon = "balance-o", label = "余额", sub = null,
                                            iconColor = BankIconDebit, borderColor = tokens.primary.copy(alpha = 0.3f),
                                            activeBg = tokens.primary.copy(alpha = 0.1f), active = cardId == VIRTUAL_BALANCE_ID,
                                            minHeight = 48.dp, iconSize = 20.sp, labelSize = 13f,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectVirtualCard(VIRTUAL_BALANCE_ID, "余额", METHOD_BALANCE) },
                                        )
                                    }
                                }
                                if (realCardList.isNotEmpty()) {
                                    SectionDivider(if (isTransfer && ts == TransferMode.External) "转出卡片" else "我的卡片")
                                    if (!isIncome && !(isTransfer && ts == TransferMode.External)) {
                                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CardTypeTab("信用卡", cardTab == "credit", Modifier.weight(1f)) { cardTab = "credit" }
                                            CardTypeTab("借记卡", cardTab == "debit", Modifier.weight(1f)) { cardTab = "debit" }
                                        }
                                    }
                                    VanSearch(
                                        value = cardSearchKey,
                                        onValueChange = { cardSearchKey = it },
                                        placeholder = "搜索银行卡名/尾号",
                                        shape = "round",
                                        background = Color.Transparent,
                                        clearable = true,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    CardGrid(
                                        cards = displayedCards,
                                        banks = banks,
                                        selectedId = cardId,
                                        emptyText = "该类型暂无卡片",
                                        checkColor = BankIconDebit,
                                        onSelect = { selectRealCard(it) },
                                    )
                                } else {
                                    EmptyTip("暂无真实银行卡")
                                }
                            }
                        }
                        if (isTransfer) {
                            NextBigButton(text = "下一步", enabled = canNext) { if (canNext) step += 1 }
                        }
                    }

                    // ───────── Step 4：金额 ─────────
                    4 -> {
                        StepTitle("多少金额？")
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                Modifier
                                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                    .background(colors.bgCard, RoundedCornerShape(16.dp))
                                    .clickable { showCurrencyPicker = true }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FText(currency.label, 13f, FontWeight.Normal, colors.textPrimary)
                                Spacer(Modifier.width(4.dp))
                                VanIcon(name = "arrow-down", size = 13.sp, color = colors.textPrimary)
                            }
                            Spacer(Modifier.height(32.dp))   // web .amount-display { margin: 32px 0 12px }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp)
                                    .background(colors.bgCard, RoundedCornerShape(12.dp))
                                    .clickable { showKeyboard = true },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                FText(currency.symbol, 28f, FontWeight.Normal, colors.textTertiary)
                                Spacer(Modifier.width(4.dp))
                                FText(amount.ifBlank { "0.00" }, 56f, FontWeight.Bold, colors.textPrimary)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (currency.code != "CNY" && amount.isNotBlank()) {
                                FText(
                                    "约 ¥ ${Money.formatMoney(exchangedCny(amount, exchangeRate, currency))}",
                                    13f, FontWeight.Normal, tokens.primary,
                                )
                            }
                            if (currency.code != "CNY") {
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(colors.bgCard, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FText("汇率", 14f, FontWeight.Normal, colors.textSecondary)
                                    Box(Modifier.weight(1f)) {
                                        if (exchangeRate.isEmpty()) {
                                            FText("如: 684.5125", 15f, FontWeight.Normal, colors.textTertiary)
                                        }
                                        BasicTextField(
                                            value = exchangeRate,
                                            onValueChange = { exchangeRate = filterRateInput(it) },
                                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                                            cursorBrush = SolidColor(tokens.primary),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))   // web .amount-tip { margin-top: 8px }（在汇率行之后）
                            FText("点击金额区域唤起键盘", 12f, FontWeight.Normal, colors.textTertiary)
                        }
                        if (!showKeyboard) {
                            NextBigButton(text = "下一步", enabled = canNext) { if (canNext) step += 1 }
                        }
                    }

                    // ───────── Step 5：备注 + 确认 ─────────
                    else -> {
                        StepTitle(remarkTitle)
                        if (isTransfer && ts == TransferMode.External) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)   // web `.payee-field { margin-bottom: 12px }`
                                    .background(colors.bgCard, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FText("转给", 15f, FontWeight.SemiBold, colors.textPrimary)
                                Box(Modifier.weight(1f)) {
                                    if (payee.isEmpty()) {
                                        FText("请输入收款人（必填）", 15f, FontWeight.Normal, colors.textTertiary)
                                    }
                                    BasicTextField(
                                        value = payee,
                                        onValueChange = { payee = it.take(30) },
                                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                                        cursorBrush = SolidColor(tokens.primary),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            AppField(
                                value = remark,
                                onValueChange = { remark = it },
                                placeholder = "备注（不填直接提交）",
                                maxlength = 50,
                                showWordLimit = true,
                                clearable = true,
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.bgCard, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            SummaryRow("方向", directionLabel, first = true)
                            if (!isTransfer) SummaryRow("分类", categoryName.ifBlank { "-" })
                            if (isTransfer && ts == TransferMode.Self) {
                                SummaryRow("转出账户", outCardLabel)
                                SummaryRow("转入账户", inCardLabel)
                            } else if (isTransfer && ts == TransferMode.Withdraw) {
                                SummaryRow("转出账户", "余额")
                                SummaryRow("到账账户", inCardLabel)
                            } else {
                                SummaryRow(if (isTransfer) "转出账户" else "账户", cardLabel.ifBlank { "-" })
                            }
                            SummaryRow(
                                label = "金额",
                                value = "${currency.symbol} ${amount.ifBlank { "0.00" }}",
                                amount = true,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        AppButton(
                            text = confirmText,
                            type = AppButtonType.Primary,
                            block = true,
                            round = true,
                            size = AppButtonSize.Large,
                            disabled = !canSubmit,
                            loading = submitting,
                            onClick = { doSubmit() },
                        )
                    }
                }
            }
        }
    }

    // 数字键盘（custom/extra-key="."/"完成"/maxlength 12）
    VanNumberKeyboard(
        show = showKeyboard,
        text = amount,
        onTextChange = { amount = filterAmountInput(it); showKeyboard = true },
        theme = VanKeyboardTheme.Custom,
        closeButtonText = "完成",
        extraKey = listOf("."),
        maxLength = 12,
        onClose = { showKeyboard = false },
        onBlur = { showKeyboard = false },
    )

    // 币种 picker
    VanPopup(show = showCurrencyPicker, onDismissRequest = { showCurrencyPicker = false }, position = VanPopupPosition.Bottom, round = true) {
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
}

// ───────────────────────────── 页内组件 ─────────────────────────────

/** web `.qsa-bar` + `.qsa-step`：顶部步骤条（圆形返回/前进按钮 + 5 个步骤圆点）。 */
@Composable
private fun StepBar(
    step: Int,
    labels: List<String>,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStepClick: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cssShadow(0.dp, WebShadow(0f, 1f, 2f, Color.Black.copy(alpha = 0.02f)))
            .background(colors.bgCard)
            .border(1.dp, colors.border)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepNavButton(icon = "arrow-left", enabled = canPrev, onClick = onPrev)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { idx, label ->
                val index = idx + 1
                val active = step == index
                val done = step > index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onStepClick(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            // 选中态外圈 3px 光晕（web `box-shadow: 0 0 0 3px rgba(primary,.15)`）
                            .let {
                                if (active) it.background(tokens.primary.copy(alpha = 0.15f), CircleShape).padding(3.dp)
                                else it
                            }
                            .size(22.dp)
                            .background(
                                when {
                                    active -> tokens.primary
                                    done -> tokens.primary.copy(alpha = 0.15f)
                                    else -> colors.bgThird
                                },
                                CircleShape,
                            ),
                    ) {
                        FText(
                            "$index",
                            12f,
                            FontWeight.SemiBold,
                            when {
                                active -> Color.White
                                done -> tokens.primary
                                else -> colors.textTertiary
                            },
                        )
                    }
                    FText(
                        label,
                        10f,
                        if (active) FontWeight.SemiBold else FontWeight.Normal,
                        if (active || done) tokens.primary else colors.textTertiary,
                    )
                }
            }
        }
        StepNavButton(icon = "arrow", enabled = canNext, onClick = onNext)
    }
}

/** web `.qsa-nav-btn`：32 圆按钮（禁用时换页面底 + 三级色）。 */
@Composable
private fun StepNavButton(icon: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.bgThird else colors.bgPage)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        VanIcon(name = icon, size = 16.sp, color = if (enabled) colors.textPrimary else colors.textTertiary)
    }
}

/** web `.qsa-title`：20px/700 居中 + 下距 20。 */
@Composable
private fun StepTitle(text: String) {
    FText(
        text, 20f, FontWeight.Bold, LocalAppColors.current.textPrimary,
        Modifier.fillMaxWidth().padding(bottom = 20.dp),
    )
}

/** web `.qsa-empty`：13px 三级色居中 + 上距 24。 */
@Composable
private fun EmptyTip(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        FText(text, 13f, FontWeight.Normal, LocalAppColors.current.textTertiary)
    }
}

/**
 * web `.big-btn`：大块按钮（图标 + 主文案 + 副文案；active = 主题色描边 + 主题色 10~12% 底；按压 scale .97）。
 */
@Composable
private fun BigButton(
    icon: String?,
    label: String,
    sub: String?,
    iconColor: Color,
    borderColor: Color,
    activeBg: Color,
    active: Boolean,
    minHeight: Dp,
    iconSize: androidx.compose.ui.unit.TextUnit,
    labelSize: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()  // web `.big-btn { width: 100% }`：方向/转账子模式单列布局需满宽并居中
            .graphicsLayer(scaleX = if (pressed) 0.97f else 1f, scaleY = if (pressed) 0.97f else 1f)  // web `:active { scale(.97) }`
            .background(if (active || pressed) activeBg else colors.bgCard, RoundedCornerShape(12.dp))
            .border(1.dp, if (active || pressed) borderColor else borderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = minHeight)
            .padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                VanIcon(name = icon, size = iconSize, color = iconColor)
                Spacer(Modifier.height(6.dp))
            }
            FText(label, labelSize, FontWeight.SemiBold, colors.textPrimary)
            if (!sub.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                FText(sub, 11f, FontWeight.Normal, colors.textTertiary)
            }
        }
    }
}

/** web `.side-label`：转出（支出色）/ 转入（收入色）。 */
@Composable
private fun SideLabel(text: String, icon: String, color: Color, alignEnd: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        VanIcon(name = icon, size = 14.sp, color = color)
        Spacer(Modifier.width(4.dp))
        FText(text, 13f, FontWeight.SemiBold, color)
    }
}

/** web `.qsa-divider`：左右横线夹文字。 */
@Composable
private fun SectionDivider(text: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        FText(text, 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
    }
}

/** web `.card-type-tabs`：信用卡 / 借记卡 胶囊 tab。 */
@Composable
private fun CardTypeTab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (active) tokens.primary else colors.bgThird, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        FText(text, 14f, if (active) FontWeight.SemiBold else FontWeight.Normal, if (active) Color.White else colors.textSecondary)
    }
}

/** web `.card-list`（2 列网格）+ `.card-item`（银行图标 + 尾号 + 类型 + 银行名）。 */
@Composable
private fun CardGrid(
    cards: List<Card>,
    banks: List<Category>,
    selectedId: String,
    emptyText: String,
    checkColor: Color,
    onSelect: (Card) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        cards.chunked(2).forEach { rowCards ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCards.forEach { card ->
                    QuickCardTile(
                        card = card,
                        banks = banks,
                        selected = selectedId == card.id,
                        disabled = false,
                        checkColor = checkColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(card) },
                    )
                }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (cards.isEmpty()) EmptyTip(emptyText)
    }
}

/** web `.card-item`：48 高、padding 6/8、圆角 8、选中主色描边 + 8% 底、禁用 opacity .4。 */
@Composable
private fun QuickCardTile(
    card: Card,
    banks: List<Category>,
    selected: Boolean,
    disabled: Boolean,
    checkColor: Color = BankIconDebit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val iconUrl = bankIconUrl(banks, card.bankId)
    Row(
        modifier = modifier
            .alpha(if (disabled) 0.4f else 1f)
            .heightIn(min = 48.dp)
            .background(if (selected) tokens.primary.copy(alpha = 0.08f) else colors.bgCard, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) tokens.primary else colors.border, RoundedCornerShape(8.dp))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 银行图标：失败时露出下层兜底图标（对应 web van-image #error 插槽）
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(28.dp)
                    .background(colors.bgPage, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                VanIcon(
                    name = if (card.isCredit) "credit-pay" else "debit-pay",
                    size = 18.sp,
                    color = if (card.isCredit) BankIconCredit else BankIconDebit,
                )
            }
            if (iconUrl.isNotBlank()) {
                VanImage(
                    src = AppConfig.fullFileUrl(iconUrl),
                    width = 28.dp,
                    height = 28.dp,
                    showError = false,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FText(cardLast4(card), 13f, FontWeight.Bold, colors.textPrimary)
                FText(cardTypeText(card.cardType), 11f, FontWeight.Normal, colors.textTertiary)
            }
            FText(bankNameOf(banks, card.bankId), 10f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 2.dp))
        }
        if (selected) VanIcon(name = "success", size = 18.sp, color = checkColor)
    }
}

/** web `.next-big-btn`：80 高渐变巨大按钮（禁用变三级底 + 三级色）。 */
@Composable
private fun NextBigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .heightIn(min = 80.dp)
            .let {
                if (enabled) it.cssShadow(14.dp, WebShadow(0f, 6f, 16f, tokens.primary.copy(alpha = 0.25f))) else it
            }
            .background(
                if (enabled) Brush.linearGradient(listOf(tokens.primary, tokens.grad)) else SolidColor(colors.bgThird),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FText(text, 20f, FontWeight.Bold, if (enabled) Color.White else colors.textTertiary)
            VanIcon(name = "arrow", size = 22.sp, color = if (enabled) Color.White else colors.textTertiary)
        }
    }
}

/** web `.summary-row`：左右分布 + 除首行外上边框；金额行 24px/700 红字。 */
@Composable
private fun SummaryRow(label: String, value: String, first: Boolean = false, amount: Boolean = false) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (amount) 12.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(label, 14f, FontWeight.Normal, colors.textTertiary)
            if (amount) {
                FText(value, 24f, FontWeight.Bold, colors.danger)
            } else {
                FText(value, 14f, FontWeight.Medium, colors.textPrimary)
            }
        }
    }
}
