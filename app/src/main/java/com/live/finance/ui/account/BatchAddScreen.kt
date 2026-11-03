package com.live.finance.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.rememberBankIconRequest
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.VanToastType
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanSearch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 批量登记：左支出 / 右收入，一屏多笔，提交时按现有 `/account/debit|credit` 逐笔提交。
 *
 * 交互（2026-11-03 负责人定稿）：
 * - 左右各一个「+」；点开 7/10 高度的添加弹窗，一屏选分类/账户/金额，完成即归入对应方向；
 * - 不出现「转账」分类；不做默认分类/账户；金额用项目 custom 数字键盘；
 * - 日期固定「提交当日」，不提供日期选择（往日请走普通「记一笔」）；
 * - 提交逐笔进行，成功一笔右侧出现小绿√；中途失败保留失败行；
 * - 全部提交成功后底部变成 muted 绿「全部完成 √」，必须再点它才返回，不自动 pop。
 */

private val BatchBankIconCredit = Color(0xFFEE0A24)
private val BatchBankIconDebit = Color(0xFF1989FA)

private sealed interface BatchAccount {
    data object Cash : BatchAccount
    data object Balance : BatchAccount
    data class Real(val card: Card) : BatchAccount
}

private class BatchRowState(
    val id: Long,
    amount: String = "",
    category: Category? = null,
    account: BatchAccount? = null,
) {
    var amount by mutableStateOf(amount)
    var category by mutableStateOf(category)
    var account by mutableStateOf(account)
    var submitted by mutableStateOf(false)
    var error by mutableStateOf(false)

    val isBlank: Boolean
        get() = amount.isBlank() && category == null && account == null

    val isComplete: Boolean
        get() = amountValue(amount) != null && category != null && account != null

    /** 编辑弹窗是草稿：只有点「完成」才回写真正列表里的行。 */
    fun copyFrom(other: BatchRowState) {
        amount = other.amount
        category = other.category
        account = other.account
        error = false
    }
}

private data class SubmitItem(
    val row: BatchRowState,
    val isIncome: Boolean,
    val payload: RecordPayload,
)

@Composable
fun BatchAddScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    var expenseCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var incomeCats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }

    val expenseRows = remember { mutableStateListOf<BatchRowState>() }
    val incomeRows = remember { mutableStateListOf<BatchRowState>() }
    var nextRowId by remember { mutableStateOf(1L) }
    val expenseListState = rememberLazyListState()
    val incomeListState = rememberLazyListState()

    var submitting by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editorDraft by remember { mutableStateOf<BatchRowState?>(null) }
    var editorTarget by remember { mutableStateOf<BatchRowState?>(null) }
    var editorIsIncome by remember { mutableStateOf(false) }
    var editorStep by remember { mutableStateOf(1) }   // 1 分类 / 2 账户 / 3 金额
    var editorError by remember { mutableStateOf<String?>(null) }
    var accountCardTab by remember { mutableStateOf("debit") }   // 支出端账户：信用卡 / 借记卡
    var accountSearchKey by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var showBackConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val e = async { graph.category.list("expense") }
            val i = async { graph.category.list("income") }
            val c = async { graph.card.list(null) }
            val b = async { graph.category.list("bank") }
            // 批量页不处理转账：两侧都不展示「转账」分类，避免生成单边转账。
            expenseCats = (((e.await() as? ApiResult.Ok)?.data) ?: emptyList()).filter { it.name != "转账" }
            incomeCats = (((i.await() as? ApiResult.Ok)?.data) ?: emptyList()).filter { it.name != "转账" }
            cards = ((c.await() as? ApiResult.Ok)?.data) ?: emptyList()
            banks = ((b.await() as? ApiResult.Ok)?.data) ?: emptyList()
        }
    }

    fun newId(): Long {
        val id = nextRowId
        nextRowId += 1
        return id
    }

    fun openAdd(isIncome: Boolean) {
        editorDraft = BatchRowState(newId())
        editorTarget = null
        editorIsIncome = isIncome
        editorStep = 1
        editorError = null
        accountCardTab = "debit"
        accountSearchKey = ""
        showEditor = true
    }

    fun openEdit(row: BatchRowState, isIncome: Boolean) {
        if (row.submitted) return
        val draft = BatchRowState(row.id, row.amount, row.category, row.account)
        editorDraft = draft
        editorTarget = row
        editorIsIncome = isIncome
        editorStep = 3   // 编辑直接落到金额节点，可点回 1/2 改分类/账户
        editorError = null
        val card = (row.account as? BatchAccount.Real)?.card
        accountCardTab = if (card?.cardType == "credit") "credit" else "debit"
        accountSearchKey = ""
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        showKeyboard = false
        accountSearchKey = ""
        editorStep = 1
        editorError = null
        editorDraft = null
        editorTarget = null
    }

    fun saveEditor() {
        val draft = editorDraft ?: return
        val cat = draft.category
        if (cat == null) {
            editorError = "请选择分类"
            editorStep = 1
            return
        }
        val acc = draft.account
        if (acc == null) {
            editorError = "请选择账户"
            editorStep = 2
            return
        }
        if (amountValue(draft.amount) == null) {
            editorError = "请输入金额"
            return
        }
        val target = editorTarget
        if (target == null) {
            val row = BatchRowState(draft.id, draft.amount, cat, acc)
            if (editorIsIncome) {
                incomeRows.add(row)
                scope.launch { incomeListState.animateScrollToItem(incomeRows.lastIndex) }
            } else {
                expenseRows.add(row)
                scope.launch { expenseListState.animateScrollToItem(expenseRows.lastIndex) }
            }
        } else {
            target.copyFrom(draft)
        }
        closeEditor()
    }

    // 金额节点自动唤起项目 custom 数字键盘（与快速登记一致）；离开节点/关闭弹窗时收起。
    LaunchedEffect(showEditor, editorStep) {
        if (showEditor && editorStep == 3) {
            delay(80)
            showKeyboard = true
        } else {
            showKeyboard = false
        }
    }

    fun doSubmit() {
        if (submitting) return
        val items = mutableListOf<SubmitItem>()
        var invalid = false

        fun collect(rows: List<BatchRowState>, isIncome: Boolean) {
            rows.forEach { row ->
                if (row.submitted) return@forEach
                val payload = row.toPayload(isIncome)
                if (payload == null) {
                    row.error = true
                    invalid = true
                } else {
                    items += SubmitItem(row, isIncome, payload)
                }
            }
        }
        collect(expenseRows, false)
        collect(incomeRows, true)

        if (invalid) {
            toast.show("有未填完整的笔数，请点开修改", VanToastType.Fail)
            return
        }
        if (items.isEmpty()) {
            toast.show("请先添加一笔", VanToastType.Fail)
            return
        }

        submitting = true
        scope.launch {
            var success = 0
            var failure: ApiResult<Unit>? = null
            var failedItem: SubmitItem? = null
            for (item in items) {
                when (val r = createRecord(graph, cards, item.payload)) {
                    is ApiResult.Ok -> {
                        // 不删行：逐笔留下小绿√，全部完成后再由用户手动返回。
                        item.row.submitted = true
                        item.row.error = false
                        success++
                    }
                    else -> {
                        failure = r
                        failedItem = item
                        item.row.error = true
                        break
                    }
                }
            }
            submitting = false
            when (failure) {
                null -> Unit // 全部完成后由底部「全部完成」按钮返回，不自动 pop
                ApiResult.Unauthorized -> {
                    toast.show("登录已失效，请重新登录")
                    graph.auth.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                }
                ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
                else -> {
                    val prefix = if (success > 0) "已成功 $success 笔；" else ""
                    toast.show(
                        prefix + (failedItem?.row?.category?.name ?: "下一笔") +
                            "提交失败：" + failure.failMessage("提交失败"),
                        VanToastType.Fail,
                    )
                }
            }
        }
    }

    val pendingRows = expenseRows.filter { !it.submitted } + incomeRows.filter { !it.submitted }
    val pendingCount = pendingRows.size
    val submittedCount = expenseRows.count { it.submitted } + incomeRows.count { it.submitted }
    val allDone = !submitting && submittedCount > 0 && pendingCount == 0

    // 有未提交数据时，系统返回/顶栏返回先二次确认；添加弹窗打开时让弹窗自己处理返回。
    // 提交进行中不允许返回：页面级提交协程随页面销毁取消，会丢失在途提交结果。
    BackHandler(enabled = !showEditor && !showBackConfirm && !submitting && pendingRows.isNotEmpty()) {
        showBackConfirm = true
    }

    ScreenScaffold { inner ->
        Column(inner.background(colors.bgPage)) {
            // 日期固定今日（提交当日）；批量/快速登记都不允许选日期。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .background(colors.bgCard, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon("calendar-o", size = 14.sp, color = tokens.primary)
                    Spacer(Modifier.width(4.dp))
                    FText("今日 · ${today.monthValue}月${today.dayOfMonth}日", 13f, FontWeight.Medium, colors.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                FText(
                    "支出 ${expenseRows.size} · 收入 ${incomeRows.size}",
                    11f,
                    FontWeight.Normal,
                    colors.textTertiary,
                )
            }

            Row(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BatchSideColumn(
                    title = "支出",
                    isIncome = false,
                    accent = colors.danger,
                    rows = expenseRows,
                    banks = banks,
                    listState = expenseListState,
                    addEnabled = !allDone && !submitting,
                    addLockedText = if (allDone) "本次已完成" else "正在提交…",
                    onAdd = { if (!allDone && !submitting) openAdd(false) },
                    onEdit = { row -> openEdit(row, false) },
                    onDelete = { row -> if (!submitting) expenseRows.remove(row) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                BatchSideColumn(
                    title = "收入",
                    isIncome = true,
                    accent = colors.success,
                    rows = incomeRows,
                    banks = banks,
                    listState = incomeListState,
                    addEnabled = !allDone && !submitting,
                    addLockedText = if (allDone) "本次已完成" else "正在提交…",
                    onAdd = { if (!allDone && !submitting) openAdd(true) },
                    onEdit = { row -> openEdit(row, true) },
                    onDelete = { row -> if (!submitting) incomeRows.remove(row) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (allDone) {
                    CompleteButton { nav.popBackStack() }
                } else {
                    AppButton(
                        text = when {
                            submitting -> "提交中"
                            pendingCount > 0 -> "提交 $pendingCount 笔"
                            else -> "提交"
                        },
                        type = AppButtonType.Primary,
                        block = true,
                        round = true,
                        size = AppButtonSize.Large,
                        loading = submitting,
                        disabled = submitting || pendingCount == 0,
                        onClick = { doSubmit() },
                    )
                }
                if (submittedCount > 0 && !allDone) {
                    Box(Modifier.fillMaxWidth().padding(top = 5.dp), contentAlignment = Alignment.Center) {
                        FText(
                            if (submitting) "正在逐笔提交…" else "已提交 $submittedCount 笔 · 剩余 $pendingCount 笔",
                            10f,
                            FontWeight.Normal,
                            colors.textTertiary,
                        )
                    }
                }
            }
        }
    }

    // ── 8/10 高度添加/编辑弹窗：1 分类 → 2 账户 → 3 金额（与快速登记同流程，去掉方向） ──
    if (showEditor) {
        val draft = editorDraft
        val accent = if (editorIsIncome) colors.success else colors.danger
        VanPopup(
            show = true,
            onDismissRequest = { closeEditor() },
            position = VanPopupPosition.Bottom,
            round = true,
            closeOnClickOverlay = false, // 防误触：只允许 X / 系统返回收起
        ) {
            if (draft != null) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
                    SheetHeader(
                        title = (if (editorIsIncome) "收入" else "支出") + if (editorTarget == null) " · 添加" else " · 编辑",
                        onClose = { closeEditor() },
                    )
                    BatchEditorStepBar(
                        step = editorStep,
                        accent = accent,
                        onStepClick = { target ->
                            if (target < editorStep) {
                                editorStep = target
                                editorError = null
                            }
                        },
                    )
                    when (editorStep) {
                        1 -> CategoryStepContent(
                            categories = if (editorIsIncome) incomeCats else expenseCats,
                            selectedId = draft.category?.id,
                            accent = accent,
                            onSelect = { cat ->
                                draft.category = cat
                                draft.error = false
                                editorError = null
                                editorStep = 2
                            },
                            modifier = Modifier.weight(1f),
                        )
                        2 -> AccountStepContent(
                            isIncome = editorIsIncome,
                            cards = cards,
                            banks = banks,
                            selected = draft.account,
                            cardTab = accountCardTab,
                            onCardTabChange = { accountCardTab = it },
                            searchKey = accountSearchKey,
                            onSearchChange = { accountSearchKey = it },
                            onSelect = { acc ->
                                draft.account = acc
                                draft.error = false
                                editorError = null
                                accountSearchKey = ""
                                editorStep = 3
                            },
                            modifier = Modifier.weight(1f),
                        )
                        else -> AmountStepContent(
                            amount = draft.amount,
                            categoryName = draft.category?.name,
                            accountLabel = batchAccountLabel(draft.account, banks),
                            error = editorError,
                            keyboardOpen = showKeyboard,
                            canFinish = amountValue(draft.amount) != null,
                            onOpenKeyboard = { showKeyboard = true },
                            onFinish = { saveEditor() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    // 金额走项目 custom 数字键盘（与快速登记同一底座）
    VanNumberKeyboard(
        show = showKeyboard,
        text = editorDraft?.amount.orEmpty(),
        onTextChange = { value ->
            editorDraft?.amount = filterAmountInput(value)
            editorDraft?.error = false
            showKeyboard = true
        },
        theme = VanKeyboardTheme.Custom,
        closeButtonText = "完成",
        extraKey = listOf("."),
        maxLength = 12,
        onClose = { showKeyboard = false },
        onBlur = { showKeyboard = false },
    )

    VanDialog(
        show = showBackConfirm,
        onDismissRequest = { showBackConfirm = false },
        title = "已有数据",
        content = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    "确定返回吗？",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = scaledSp(14f),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "*返回数据列表将丢失",
                    style = TextStyle(
                        color = colors.danger,
                        fontSize = scaledSp(11f),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButtonText = "确定",
        cancelButtonText = "取消",
        showConfirmButton = true,
        showCancelButton = true,
        onConfirm = {
            showBackConfirm = false
            nav.popBackStack()
        },
        onCancel = { showBackConfirm = false },
    )
}

@Composable
private fun BatchSideColumn(
    title: String,
    isIncome: Boolean,
    accent: Color,
    rows: SnapshotStateList<BatchRowState>,
    banks: List<Category>,
    listState: LazyListState,
    addEnabled: Boolean,
    addLockedText: String,
    onAdd: () -> Unit,
    onEdit: (BatchRowState) -> Unit,
    onDelete: (BatchRowState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val total = rows.sumOf { amountValue(it.amount) ?: 0.0 }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            FText(title, 15f, FontWeight.SemiBold, colors.textPrimary)
            Spacer(Modifier.width(4.dp))
            FText("${rows.size} 笔", 11f, FontWeight.Normal, colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(24.dp)
                    .background(if (addEnabled) colors.bgThird else colors.bgThird.copy(alpha = 0.45f), CircleShape)
                    .clickable(enabled = addEnabled, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                VanIcon("plus", size = 13.sp, color = if (addEnabled) accent else colors.textTertiary)
            }
        }
        FText(
            "合计 ¥${Money.format(total)}",
            11f,
            FontWeight.Normal,
            colors.textTertiary,
            Modifier.padding(start = 9.dp, top = 2.dp, bottom = 7.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                BatchSummaryRow(
                    row = row,
                    banks = banks,
                    onEdit = { onEdit(row) },
                    onDelete = { onDelete(row) },
                )
            }
            if (rows.isEmpty()) {
                item(key = "empty") { EmptyBatchSide(isIncome, addEnabled, addLockedText, onAdd) }
            }
            item(key = "bottom") { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun BatchSummaryRow(
    row: BatchRowState,
    banks: List<Category>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val amountText = amountValue(row.amount)?.let { Money.format(it) } ?: "0.00"
    val accText = batchAccountLabel(row.account, banks)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCard, RoundedCornerShape(8.dp))
            .border(1.dp, if (row.error) colors.danger else colors.border, RoundedCornerShape(8.dp))
            .clickable(enabled = !row.submitted, onClick = onEdit)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                "¥ $amountText",
                style = TextStyle(
                    color = if (row.submitted) colors.textSecondary else colors.textPrimary,
                    fontSize = scaledSp(17f),
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                "${row.category?.name ?: "未选分类"} · ${accText ?: "未选账户"}",
                style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(11f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.submitted) {
            VanIcon("success", size = 15.sp, color = colors.success)
        } else {
            Box(Modifier.size(24.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                VanIcon("clear", size = 14.sp, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun EmptyBatchSide(isIncome: Boolean, addEnabled: Boolean, lockedText: String, onAdd: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCard, RoundedCornerShape(10.dp))
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FText(if (isIncome) "还没有收入笔数" else "还没有支出笔数", 12f, FontWeight.Normal, colors.textTertiary)
        Spacer(Modifier.height(8.dp))
        if (addEnabled) {
            AppButton(
                text = "添加一笔",
                type = AppButtonType.Primary,
                plain = true,
                round = true,
                size = AppButtonSize.Small,
                icon = "plus",
                onClick = onAdd,
            )
        } else {
            FText(lockedText, 12f, FontWeight.Normal, colors.textTertiary)
        }
    }
}

@Composable
private fun CompleteButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color(0xFF3F7D5A), shape) // muted 绿：不抢眼
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FText("全部完成", 16f, FontWeight.SemiBold, Color.White)
        Spacer(Modifier.width(6.dp))
        VanIcon("success", size = 18.sp, color = Color.White)
    }
}

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(title, 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
            Box(Modifier.size(24.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                VanIcon("clear", size = 16.sp, color = colors.textTertiary)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text,
        style = TextStyle(
            color = LocalAppColors.current.textSecondary,
            fontSize = scaledSp(12f),
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun BatchEditorStepBar(step: Int, accent: Color, onStepClick: (Int) -> Unit) {
    val colors = LocalAppColors.current
    val labels = listOf("分类", "账户", "金额")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { idx, label ->
            val num = idx + 1
            val active = step == num
            val done = step > num
            Column(
                Modifier.weight(1f).clickable(enabled = num < step) { onStepClick(num) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(22.dp).background(
                        when {
                            active -> accent
                            done -> accent.copy(alpha = 0.15f)
                            else -> colors.bgThird
                        },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "$num",
                        style = TextStyle(
                            color = when {
                                active -> Color.White
                                done -> accent
                                else -> colors.textTertiary
                            },
                            fontSize = scaledSp(11f),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                BasicText(
                    label,
                    style = TextStyle(
                        color = if (active || done) accent else colors.textTertiary,
                        fontSize = scaledSp(11f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CategoryStepContent(
    categories: List<Category>,
    selectedId: String?,
    accent: Color,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionLabel("选择分类")
        if (categories.isEmpty()) {
            BasicText(
                "暂无可用分类",
                style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(13f)),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.chunked(3).forEach { rowCats ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCats.forEach { cat ->
                            EditorCategoryTile(
                                name = cat.name,
                                active = selectedId == cat.id,
                                accent = accent,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(cat) },
                            )
                        }
                        repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountStepContent(
    isIncome: Boolean,
    cards: List<Card>,
    banks: List<Category>,
    selected: BatchAccount?,
    cardTab: String,
    onCardTabChange: (String) -> Unit,
    searchKey: String,
    onSearchChange: (String) -> Unit,
    onSelect: (BatchAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val real = remember(cards) { realCards(cards) }
    val baseCards = remember(real, isIncome, cardTab) {
        real.filter { if (isIncome) it.cardType == "debit" else it.cardType == cardTab }
    }
    val filtered = remember(baseCards, banks, searchKey) { filterCards(baseCards, banks, searchKey) }
    val cashColor = Color(0xFFFF976A)
    val balanceColor = Color(0xFF1989FA)

    LazyColumn(modifier.fillMaxSize()) {
        item(key = "top") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountTopButton(
                    icon = "cash-back-record-o",
                    label = "现金",
                    accent = cashColor,
                    selected = selected == BatchAccount.Cash,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(BatchAccount.Cash) },
                )
                AccountTopButton(
                    icon = "balance-o",
                    label = "余额",
                    accent = balanceColor,
                    selected = selected == BatchAccount.Balance,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(BatchAccount.Balance) },
                )
            }
        }
        if (real.isNotEmpty()) {
            item(key = "divider") { AccountSectionDivider("我的卡片") }
            if (!isIncome) {
                item(key = "tabs") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AccountTypeTab("信用卡", cardTab == "credit", Modifier.weight(1f)) { onCardTabChange("credit") }
                        AccountTypeTab("借记卡", cardTab == "debit", Modifier.weight(1f)) { onCardTabChange("debit") }
                    }
                }
            }
            item(key = "search") {
                VanSearch(
                    value = searchKey,
                    onValueChange = onSearchChange,
                    placeholder = "搜索银行卡名/尾号",
                    shape = "round",
                    background = Color.Transparent,
                    clearable = true,
                )
            }
            items(filtered.chunked(2), key = { it.first().id }) { rowCards ->
                AccountCardRow(
                    rowCards = rowCards,
                    banks = banks,
                    selectedId = (selected as? BatchAccount.Real)?.card?.id,
                    onSelect = { onSelect(BatchAccount.Real(it)) },
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty-filtered") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        FText("该类型暂无卡片", 13f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }
        } else {
            item(key = "empty-real") {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    FText("暂无真实银行卡", 13f, FontWeight.Normal, colors.textTertiary)
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AccountTopButton(
    icon: String,
    label: String,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .heightIn(min = 72.dp)
            .background(if (selected) accent.copy(alpha = 0.10f) else colors.bgCard, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) accent else colors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VanIcon(icon, size = 20.sp, color = accent)
        Spacer(Modifier.height(4.dp))
        BasicText(
            label,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = scaledSp(13f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AccountSectionDivider(text: String) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
        BasicText(
            text,
            style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(11f)),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
    }
}

@Composable
private fun AccountTypeTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Box(
        modifier
            .height(34.dp)
            .background(if (active) tokens.primary else colors.bgThird, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (active) Color.White else colors.textSecondary,
                fontSize = scaledSp(13f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun AccountCardRow(
    rowCards: List<Card>,
    banks: List<Category>,
    selectedId: String?,
    onSelect: (Card) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rowCards.forEach { card ->
            AccountCardTile(
                card = card,
                banks = banks,
                selected = card.id == selectedId,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(card) },
            )
        }
        if (rowCards.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AccountCardTile(
    card: Card,
    banks: List<Category>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val bankName = bankNameOf(banks, card.bankId).ifBlank { card.bankName }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(if (selected) tokens.primary.copy(alpha = 0.08f) else colors.bgCard, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) tokens.primary else colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AccountBankLogo(
            src = bankIconUrl(banks, card.bankId),
            isCredit = card.isCredit,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    cardLast4(card),
                    style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(14f), fontWeight = FontWeight.Bold),
                )
                BasicText(
                    cardTypeText(card.cardType),
                    style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f)),
                )
            }
            BasicText(
                bankName,
                style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) VanIcon("success", size = 18.sp, color = colors.success)
    }
}

/** 账户卡 tile 专用轻量图标：AsyncImage 不做 Subcompose，也不在 tile 里读加载状态（避免滚动时重组）。 */
@Composable
private fun AccountBankLogo(src: String, isCredit: Boolean) {
    val colors = LocalAppColors.current
    val url = src.takeIf { it.isNotBlank() }?.let { AppConfig.fullFileUrl(it) }.orEmpty()
    val placeholder = remember(colors.bgPage) { ColorPainter(colors.bgPage) }
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Box(
                Modifier.fillMaxSize().background(colors.bgPage, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                VanIcon(
                    name = if (isCredit) "credit-pay" else "debit-pay",
                    size = 18.sp,
                    color = if (isCredit) BatchBankIconCredit else BatchBankIconDebit,
                )
            }
        } else {
            AsyncImage(
                model = rememberBankIconRequest(url) ?: url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
            )
        }
    }
}

@Composable
private fun AmountStepContent(
    amount: String,
    categoryName: String?,
    accountLabel: String?,
    error: String?,
    keyboardOpen: Boolean,
    canFinish: Boolean,
    onOpenKeyboard: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicText(
            "${categoryName ?: "未选分类"} · ${accountLabel ?: "未选账户"}",
            style = TextStyle(color = colors.textSecondary, fontSize = scaledSp(12f)),
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            "金额（人民币）",
            style = TextStyle(
                color = colors.textSecondary,
                fontSize = scaledSp(12f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.bgThird, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenKeyboard)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("¥", style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(18f)))
            Spacer(Modifier.width(4.dp))
            BasicText(
                amount.ifBlank { "0.00" },
                style = TextStyle(
                    color = if (amount.isBlank()) colors.textTertiary else colors.textPrimary,
                    fontSize = scaledSp(32f),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            VanIcon("arrow-down", size = 12.sp, color = colors.textTertiary)
        }
        if (!keyboardOpen) {
            val msg = error ?: if (!canFinish) "请输入金额" else null
            if (msg != null) {
                BasicText(
                    msg,
                    style = TextStyle(color = colors.danger, fontSize = scaledSp(12f)),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (!keyboardOpen) {
            AppButton(
                text = "完成",
                type = AppButtonType.Primary,
                block = true,
                round = true,
                size = AppButtonSize.Large,
                disabled = !canFinish,
                onClick = onFinish,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun EditorCategoryTile(
    name: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier
            .height(44.dp)
            .background(if (active) accent.copy(alpha = 0.12f) else colors.bgThird, RoundedCornerShape(8.dp))
            .border(1.dp, if (active) accent else colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            name,
            style = TextStyle(
                color = if (active) accent else colors.textPrimary,
                fontSize = scaledSp(13f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun batchAccountLabel(account: BatchAccount?, banks: List<Category>): String? = when (account) {
    BatchAccount.Cash -> "现金"
    BatchAccount.Balance -> "余额"
    is BatchAccount.Real -> cardDisplayText(account.card, banks)
    null -> null
}

private fun BatchRowState.toPayload(isIncome: Boolean): RecordPayload? {
    val cat = category ?: return null
    val amt = amountValue(amount) ?: return null
    val (cardId, payMethod) = when (val a = account) {
        BatchAccount.Cash -> VIRTUAL_CASH_ID to METHOD_CASH
        BatchAccount.Balance -> VIRTUAL_BALANCE_ID to METHOD_BALANCE
        is BatchAccount.Real -> a.card.id to cardTypeText(a.card.cardType)
        null -> return null
    }
    return RecordPayload(
        direction = if (isIncome) 1 else 0,
        categoryId = cat.id,
        payType = cat.name,
        payMethod = payMethod,
        amount = amt,
        currency = DEFAULT_CURRENCY.code,
        exchangeRate = 1.0,
        transDate = todayString(),
        cardId = cardId,
        remark = "",
    )
}
