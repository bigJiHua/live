package com.live.finance.ui.common

import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import kotlin.math.abs

/** `CATEGORY_REPAY`：信用卡还款（资金转移，不计支出、卡片置灰）。 */
const val REPAY_CATEGORY = "CATEGORY_REPAY"

/** 虚拟卡 id（后端硬约定）：现金 / 余额。 */
const val VIRTUAL_CASH = "xxxx"
const val VIRTUAL_BALANCE = "yyyy"

/** 是否「信用卡还款」记录（web `isRepay`）。 */
fun isRepay(row: FlowRow): Boolean = row.categoryId == REPAY_CATEGORY

/** 是否信用卡（web `isCreditCard`：先看 `account_type`，再回查卡片类型）。 */
fun isCreditFlow(row: FlowRow, cards: List<Card>): Boolean =
    row.accountType == "credit" ||
        (row.cardId.isNotEmpty() && cards.firstOrNull { it.id == row.cardId }?.cardType == "credit")

/** 交易方式展示（web `formatPayMethod`：分期入账的英文标识统一转中文）。 */
fun formatPayMethod(v: String): String = if (v == "installment") "信用卡分期" else v.ifEmpty { "-" }

/** 是否分期入账流水（web `isInstallmentFlow`：pay_method/pay_type/category_id 任一为 installment）。 */
fun isInstallmentFlow(row: FlowRow): Boolean =
    row.payMethod == "installment" || row.payType == "installment" || row.categoryId == "installment"

/** 分类名（web `getCategoryName`：还款 → 信用卡还款；分期 → 信用卡分期；否则 category_name）。 */
fun flowCategoryName(row: FlowRow): String = when {
    row.categoryId == REPAY_CATEGORY -> "信用卡还款"
    isInstallmentFlow(row) -> "信用卡分期"
    else -> row.categoryName.ifEmpty { "未知分类" }
}

/** 交易方式文案（web `formatPayMethod(item.pay_method)`）。 */
fun flowPayMethod(row: FlowRow): String = formatPayMethod(row.payMethod)

/**
 * 列表页卡型标签（web `getCardTypeLabel`）：`debit → 借记卡`、`credit → 信用卡`、
 * `virtual_cash → 现金`、`virtual_balance → 空`、其它 → 空。
 */
fun flowCardTypeLabel(row: FlowRow): String = when (row.accountType) {
    "debit" -> "借记卡"
    "credit" -> "信用卡"
    "virtual_cash" -> "现金"
    else -> ""      // virtual_balance / 未知 → 空（web 原样）
}

/**
 * 卡片展示名（web Detail `getCardText` / Calendar `getCardText`：现金、余额、`银行名 · 尾号`）。
 * 银行名与尾号都**优先用流水行自带的 JOIN 字段**（`card_alias` / `card_last4`），再退到卡片列表。
 */
fun flowCardText(row: FlowRow, cards: List<Card>, banks: List<Category>): String {
    val cardId = row.cardId
    if (cardId.isEmpty()) return "-"
    if (cardId == VIRTUAL_CASH) return "现金"
    if (cardId == VIRTUAL_BALANCE) return "余额"
    val card = cards.firstOrNull { it.id == cardId }
    val bankName = banks.firstOrNull { it.id == card?.bankId }?.name?.ifEmpty { null }
        ?: row.cardAlias.ifEmpty { card?.alias?.ifEmpty { card.bankName }.orEmpty() }
    val last4 = row.cardLast4.ifEmpty { card?.last4.orEmpty() }
    return when {
        bankName.isNotEmpty() && last4.isNotEmpty() -> "$bankName · $last4"
        bankName.isNotEmpty() -> bankName
        last4.isNotEmpty() -> last4
        else -> "-"
    }
}

/**
 * 紧凑银行标签（web Calendar `getCompactBankLabel`）：现金/余额直接返回；
 * 有卡则「银行名（>6 字截断） 尾号」，退化到尾号或 `-`。
 */
fun flowCompactBankLabel(row: FlowRow, cards: List<Card>, banks: List<Category>): String {
    val cardId = row.cardId
    if (cardId == VIRTUAL_BALANCE) return "余额"
    if (cardId == VIRTUAL_CASH) return "现金"
    if (cardId.isEmpty()) return row.cardLast4
    val card = cards.firstOrNull { it.id == cardId } ?: return row.cardLast4
    val bankNameRaw = banks.firstOrNull { it.id == card.bankId }?.name?.ifEmpty { null } ?: card.alias.ifEmpty { card.bankName }
    val shortName = if (bankNameRaw.length > 6) bankNameRaw.take(6) else bankNameRaw
    return when {
        shortName.isNotEmpty() && card.last4.isNotEmpty() -> "$shortName ${card.last4}"
        shortName.isNotEmpty() -> shortName
        card.last4.isNotEmpty() -> card.last4
        else -> "-"
    }
}

/** 列表页转账区块用：卡名（web List `getCardName(id)`，不截断银行名）。 */
fun flowCardNameById(cardId: String, cards: List<Card>, banks: List<Category>): String {
    if (cardId.isEmpty()) return ""
    if (cardId == VIRTUAL_CASH) return "现金"
    if (cardId == VIRTUAL_BALANCE) return "余额"
    val card = cards.firstOrNull { it.id == cardId } ?: return cardId
    val bankName = banks.firstOrNull { it.id == card.bankId }?.name?.ifEmpty { null } ?: card.alias.ifEmpty { card.bankName }
    val last4 = card.last4
    return when {
        bankName.isNotEmpty() && last4.isNotEmpty() -> "$bankName $last4"
        bankName.isNotEmpty() -> bankName
        last4.isNotEmpty() -> last4
        else -> card.alias.ifEmpty { card.bankName.ifEmpty { cardId } }
    }
}

/** 列表页兜底卡名（web `getCompactCardLabel`）。 */
fun flowCompactCardLabel(row: FlowRow, cards: List<Card>): String {
    val cardId = row.cardId
    if (cardId == VIRTUAL_BALANCE) return "余额"
    if (cardId == VIRTUAL_CASH) return "现金"
    val card = cards.firstOrNull { it.id == cardId } ?: return row.payMethod
    return card.last4.ifEmpty { card.alias }
}

/** 卡片银行图标路径（web `getCardBankIcon`：虚拟卡返回空）。 */
fun flowCardBankIcon(row: FlowRow, cards: List<Card>, banks: List<Category>): String {
    if (row.cardId.isEmpty() || row.cardId == VIRTUAL_CASH || row.cardId == VIRTUAL_BALANCE) return ""
    val card = cards.firstOrNull { it.id == row.cardId } ?: return ""
    return banks.firstOrNull { it.id == card.bankId }?.iconUrl.orEmpty()
}

/** 卡片银行名（web `getCardBankName`）。 */
fun flowCardBankName(row: FlowRow, cards: List<Card>, banks: List<Category>): String {
    if (row.cardId.isEmpty()) return ""
    val card = cards.firstOrNull { it.id == row.cardId } ?: return ""
    return banks.firstOrNull { it.id == card.bankId }?.name?.ifEmpty { null } ?: card.bankName
}

// ───────────────────────── 配对（对齐 web 的 5 趟算法）─────────────────────────

/** 展示项类型（对齐 web `_processList` / `processDailyDisplayList` 的 `type`）。 */
enum class FlowKind { Flow, Transfer, Withdrawal, Reversal, ExternalTransfer, IncomingTransfer }

/** 一个展示项：单边流水（[data]）或配对（[expense] + [income]）。 */
data class FlowItem(
    val kind: FlowKind,
    val data: FlowRow? = null,
    val expense: FlowRow? = null,
    val income: FlowRow? = null,
    /** 转账是否「确诊转账」（false = 疑似转账） */
    val explicit: Boolean = false,
) {
    /** 归属日期（web：配对取 `expense.trans_date`，单边取 `data.trans_date`）。 */
    val day: String
        get() = when (kind) {
            FlowKind.Flow, FlowKind.ExternalTransfer, FlowKind.IncomingTransfer -> data?.rowDay() ?: ""
            else -> expense?.rowDay() ?: income?.rowDay() ?: ""
        }
}

/** 按日分组结果（列表页）。 */
data class FlowDayGroup(val date: String, val items: List<FlowItem>, val income: Double, val expense: Double)

private fun FlowRow.rowDay(): String = transDate.ifEmpty { createTime.take(10) }.take(10)

private fun FlowRow.payCat(): String = payType.ifEmpty { categoryName }

private fun FlowRow.ts(): Long? = createTime.toLongOrNull()

private fun isVirtualCardId(cardId: String): Boolean = cardId == VIRTUAL_CASH || cardId == VIRTUAL_BALANCE

/** 基础匹配（web `baseMatch`）：同日、同额、同币种、**不同卡**。 */
private fun baseMatch(exp: FlowRow, inc: FlowRow): Boolean =
    exp.rowDay() == inc.rowDay() &&
        exp.amount == inc.amount &&
        exp.currency.ifEmpty { "CNY" } == inc.currency.ifEmpty { "CNY" } &&
        exp.cardId != inc.cardId

/** 列表页 / 日历页的配对差异点（web 两页是两套函数，此处用同一内核 + 模式开关）。 */
private enum class PairMode {
    /** 列表页 `_processList`：第 1 趟会从转账组里再判「提现 / 冲正」；第 1.5 趟有 5 分钟兜底。 */
    ListPage,

    /** 日历页 `processDailyDisplayList`：第 1 趟一律按普通转账；第 1.5 趟只认收入方 pay_type='冲正'。 */
    CalendarPage,
}

private fun pairCore(items: List<FlowRow>, cards: List<Card>, mode: PairMode): List<FlowItem> {
    val pairs = HashMap<String, FlowItem>()          // key = 支出方 id
    val usedExp = HashSet<String>()
    val usedInc = HashSet<String>()

    fun add(exp: FlowRow, inc: FlowRow, explicit: Boolean, withdrawal: Boolean = false, reversal: Boolean = false) {
        val kind = when {
            reversal -> FlowKind.Reversal
            withdrawal -> FlowKind.Withdrawal
            else -> FlowKind.Transfer
        }
        pairs[exp.id] = FlowItem(kind = kind, expense = exp, income = inc, explicit = explicit)
        usedExp.add(exp.id)
        usedInc.add(inc.id)
    }

    // ── 第 1 趟：transfer_group_id（后端明确分组）──
    val groups = LinkedHashMap<String, MutableList<FlowRow>>()
    items.forEach { it ->
        if (it.categoryId == REPAY_CATEGORY || it.transferGroupId.isEmpty()) return@forEach
        groups.getOrPut(it.transferGroupId) { mutableListOf() }.add(it)
    }
    groups.values.forEach { group ->
        val expense = if (mode == PairMode.ListPage) {
            group.firstOrNull { it.direction == 0 }                       // web 列表页第 1 趟只认 direction===0
        } else {
            group.firstOrNull { it.isExpense }                            // web 日历页第 1 趟用 isExpense（含 2）
        }
        val income = group.firstOrNull { it.isIncome }
        if (expense != null && income != null) {
            if (mode == PairMode.ListPage) {
                val withdrawal = expense.cardId == VIRTUAL_BALANCE && !isVirtualCardId(income.cardId)
                val reversal = expense.reversedId.isNotEmpty() || income.reversedId.isNotEmpty()
                add(expense, income, explicit = true, withdrawal = withdrawal, reversal = reversal)
            } else {
                add(expense, income, explicit = true)
            }
        }
    }

    // ── 第 1.5 趟：信用卡支出 + 现金/余额收入 → 冲正 ──
    items.forEach { item ->
        if (usedExp.contains(item.id) || usedInc.contains(item.id)) return@forEach
        if (item.categoryId == REPAY_CATEGORY) return@forEach
        if (!item.isExpense) return@forEach
        if (!isCreditFlow(item, cards)) return@forEach
        val match = items.firstOrNull { inc ->
            if (inc.id == item.id || usedInc.contains(inc.id)) return@firstOrNull false
            if (inc.categoryId == REPAY_CATEGORY) return@firstOrNull false
            if (!inc.isIncome) return@firstOrNull false
            if (!baseMatch(item, inc)) return@firstOrNull false
            if (!isVirtualCardId(inc.cardId)) return@firstOrNull false
            val incCat = inc.payCat()
            if (incCat == "冲正") return@firstOrNull true
            if (mode == PairMode.CalendarPage) return@firstOrNull false
            // 列表页兜底：无 pay_type='冲正' 时要求时间接近（5 分钟内）；缺时间信息则放行
            val t1 = item.ts()
            val t2 = inc.ts()
            !(t1 != null && t2 != null && abs(t1 - t2) > 300_000)
        }
        if (match != null) add(item, match, explicit = true, reversal = true)
    }

    // ── 第 2 趟：双方分类均为「转账」→ 确诊转账（支出方不能为信用卡）──
    items.forEach { item ->
        if (usedExp.contains(item.id) || usedInc.contains(item.id)) return@forEach
        if (item.categoryId == REPAY_CATEGORY) return@forEach
        if (!item.isExpense) return@forEach
        if (isCreditFlow(item, cards)) return@forEach
        val match = items.firstOrNull { inc ->
            if (inc.id == item.id || usedInc.contains(inc.id)) return@firstOrNull false
            if (inc.categoryId == REPAY_CATEGORY) return@firstOrNull false
            if (!inc.isIncome) return@firstOrNull false
            if (!baseMatch(item, inc)) return@firstOrNull false
            item.payCat() == "转账" && inc.payCat() == "转账"
        }
        if (match != null) add(item, match, explicit = true)
    }

    // ── 第 3 趟：支出「其他支出」+ 收入「其他收入」→ 疑似转账 ──
    items.forEach { item ->
        if (usedExp.contains(item.id) || usedInc.contains(item.id)) return@forEach
        if (item.categoryId == REPAY_CATEGORY) return@forEach
        if (!item.isExpense) return@forEach
        if (isCreditFlow(item, cards)) return@forEach
        val match = items.firstOrNull { inc ->
            if (inc.id == item.id || usedInc.contains(inc.id)) return@firstOrNull false
            if (inc.categoryId == REPAY_CATEGORY) return@firstOrNull false
            if (!inc.isIncome) return@firstOrNull false
            if (!baseMatch(item, inc)) return@firstOrNull false
            item.payCat() == "其他支出" && inc.payCat() == "其他收入"
        }
        if (match != null) add(item, match, explicit = false)
    }

    // ── 第 4 趟：其余满足基础条件的 → 疑似转账（排除「余额 → 银行卡」方向与信用卡支出方）──
    items.forEach { item ->
        if (usedExp.contains(item.id) || usedInc.contains(item.id)) return@forEach
        if (item.categoryId == REPAY_CATEGORY) return@forEach
        if (!item.isExpense) return@forEach
        val isExpenseVirtual = isVirtualCardId(item.cardId)
        if (isCreditFlow(item, cards)) return@forEach
        val match = items.firstOrNull { inc ->
            if (inc.id == item.id || usedInc.contains(inc.id)) return@firstOrNull false
            if (inc.categoryId == REPAY_CATEGORY) return@firstOrNull false
            if (!inc.isIncome) return@firstOrNull false
            if (!baseMatch(item, inc)) return@firstOrNull false
            !(isExpenseVirtual && !isVirtualCardId(inc.cardId))
        }
        if (match != null) add(item, match, explicit = item.payCat() == "转账" && match.payCat() == "转账")
    }

    // ── 第 5 趟：余额 → 银行卡（同天同额 + 时间接近）→ 提现 ──
    items.forEach { item ->
        if (usedExp.contains(item.id) || usedInc.contains(item.id)) return@forEach
        if (item.categoryId == REPAY_CATEGORY) return@forEach
        if (!item.isExpense) return@forEach
        if (item.cardId != VIRTUAL_BALANCE) return@forEach
        val match = items.firstOrNull { inc ->
            if (inc.id == item.id || usedInc.contains(inc.id)) return@firstOrNull false
            if (inc.categoryId == REPAY_CATEGORY) return@firstOrNull false
            if (!inc.isIncome) return@firstOrNull false
            if (!baseMatch(item, inc)) return@firstOrNull false
            if (isVirtualCardId(inc.cardId)) return@firstOrNull false
            val t1 = item.ts()
            val t2 = inc.ts()
            if (t1 != null && t2 != null) abs(t1 - t2) <= 300_000 else true   // 无时间信息时降级认定为提现
        }
        if (match != null) add(item, match, explicit = false, withdrawal = true)
    }

    // ── 输出（按原始顺序，配对项挂在支出方位置；收入方被吞掉）──
    val incomeIds = pairs.values.mapNotNull { it.income?.id }.toHashSet()
    val out = ArrayList<FlowItem>(items.size)
    items.forEach { item ->
        val pair = pairs[item.id]
        when {
            pair != null -> out.add(pair)
            incomeIds.contains(item.id) -> Unit      // 已被配对吞掉
            mode == PairMode.CalendarPage -> out.add(FlowItem(FlowKind.Flow, data = item))
            item.payType == "转账" && item.isIncome -> out.add(FlowItem(FlowKind.IncomingTransfer, data = item))
            item.payType == "转账" -> out.add(FlowItem(FlowKind.ExternalTransfer, data = item))
            else -> out.add(FlowItem(FlowKind.Flow, data = item))
        }
    }
    return out
}

/**
 * 列表页：原始流水 → 按日分组（web `TableList/_processList`）。
 * 日收/支口径照抄 web：只统计 `type=flow` 的收支（对外转账计入支出），配对项（转账/提现/冲正）不计入。
 */
fun groupFlowsByDay(rows: List<FlowRow>, cards: List<Card> = emptyList()): List<FlowDayGroup> {
    val items = pairCore(rows, cards, PairMode.ListPage)
    val byDay = LinkedHashMap<String, MutableList<FlowItem>>()
    items.forEach { fi -> byDay.getOrPut(fi.day) { mutableListOf() }.add(fi) }
    return byDay.entries
        .filter { it.key.isNotEmpty() }
        .sortedByDescending { it.key }
        .map { (date, dayItems) ->
            val income = dayItems.sumOf { fi ->
                if (fi.kind == FlowKind.Flow && fi.data?.isIncome == true) fi.data.amount else 0.0
            }
            val expense = dayItems.sumOf { fi ->
                when {
                    fi.kind == FlowKind.Flow && fi.data?.isExpense == true -> fi.data.amount
                    fi.kind == FlowKind.ExternalTransfer -> fi.data?.amount ?: 0.0
                    else -> 0.0
                }
            }
            FlowDayGroup(date, dayItems, income, expense)
        }
}

/** 日历页：某一天的流水 → 配对后的展示项（web `processDailyDisplayList`）。 */
fun pairDailyItems(rows: List<FlowRow>, cards: List<Card> = emptyList()): List<FlowItem> =
    pairCore(rows, cards, PairMode.CalendarPage)

private val CURRENCY_SYMBOLS = mapOf(
    "CNY" to "¥", "¥" to "¥", "USD" to "$", "HKD" to "HK$", "JPY" to "¥",
    "EUR" to "€", "GBP" to "£", "KRW" to "₩", "TWD" to "NT$", "AUD" to "A$", "CAD" to "C$",
)

fun currencySymbol(code: String): String = CURRENCY_SYMBOLS[code.uppercase()] ?: code

/** 是否外币（web `isForeignCurrency`：currency 存在且非 CNY）。 */
fun isForeignCurrency(row: FlowRow): Boolean = row.currency.isNotEmpty() && row.currency != "CNY"
