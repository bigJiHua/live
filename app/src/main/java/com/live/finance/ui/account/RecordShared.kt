package com.live.finance.ui.account

import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.repo.NewFlow
import com.live.finance.di.AppGraph
import com.live.vant.feedback.VanToastController
import java.time.LocalDate
import java.util.UUID
import kotlin.math.round

/**
 * 「记一笔」(`Add.vue`) 与「快速登记」(`QuickAdd.vue`) 两页共用的业务规则收口。
 *
 * web 上这两页各写了一份同样的常量与工具函数（币种表、`buildCardId`、`getCardDisplayText`、
 * 金额/汇率输入约束、`createAccount` 分流、两笔转账的 `transferGroupId`），原生统一收到这里，
 * 保证两页行为逐字一致（改一处即两页同步）。
 */

// ───────────────────────── 方向 / 转账模式 ─────────────────────────

/** 记账方向（对应 Add.vue 的三个 tab、QuickAdd 的 step 1）。 */
enum class RecordType(val key: String, val title: String) {
    Expense("expense", "支出"),
    Income("income", "收入"),
    Transfer("transfer", "转账"),
}

/** 转账子模式（对应 web `.transfer-toggle` 三态：对外/自转/提现）。 */
enum class TransferMode { External, Self, Withdraw }

// ───────────────────────── 币种（web 两页同一份表） ─────────────────────────

data class CurrencyOption(val code: String, val label: String, val symbol: String, val rate: Double)

val CURRENCIES: List<CurrencyOption> = listOf(
    CurrencyOption("CNY", "人民币", "¥", 1.0),
    CurrencyOption("USD", "美元", "$", 684.51),
    CurrencyOption("EUR", "欧元", "€", 750.0),
    CurrencyOption("HKD", "港币", "HK$", 88.0),
    CurrencyOption("JPY", "日元", "¥", 4.5),
    CurrencyOption("GBP", "英镑", "£", 870.0),
    CurrencyOption("KRW", "韩元", "₩", 0.5),
    CurrencyOption("TWD", "台币", "NT$", 22.0),
)

val DEFAULT_CURRENCY: CurrencyOption = CURRENCIES.first()

fun currencyOf(code: String): CurrencyOption = CURRENCIES.firstOrNull { it.code == code } ?: DEFAULT_CURRENCY

/** picker 文案：`${label} ${symbol}`（web currencyColumns）。 */
fun currencyPickerText(c: CurrencyOption): String = "${c.label} ${c.symbol}"

// ───────────────────────── 支付方式 / 虚拟卡 ─────────────────────────

const val METHOD_CASH = "现金"
const val METHOD_BALANCE = "余额"
const val METHOD_DEBIT = "借记卡"
const val METHOD_CREDIT = "信用卡"

/** 后端 `card/model/index.js` 的 VIRTUAL_CARDS：现金=xxxx、余额=yyyy（`initVirtualCards` 会落这两行，`GET /card` 自带）。 */
const val VIRTUAL_CASH_ID = "xxxx"
const val VIRTUAL_BALANCE_ID = "yyyy"

/** web `expensePayMethods` / `incomePayMethods`：收入方式不含信用卡；转账仅借记卡。 */
fun payMethodsFor(type: RecordType): List<String> = when (type) {
    RecordType.Expense -> listOf(METHOD_CASH, METHOD_BALANCE, METHOD_DEBIT, METHOD_CREDIT)
    RecordType.Income -> listOf(METHOD_CASH, METHOD_BALANCE, METHOD_DEBIT)
    RecordType.Transfer -> listOf(METHOD_DEBIT)
}

/** web `buildCardId(method, card)`：现金→xxxx、余额→yyyy、其余用真实卡 id。 */
fun buildCardId(payMethod: String, cardId: String?): String = when (payMethod) {
    METHOD_CASH -> VIRTUAL_CASH_ID
    METHOD_BALANCE -> VIRTUAL_BALANCE_ID
    else -> cardId.orEmpty()
}

fun cardTypeText(cardType: String): String = when (cardType) {
    "credit" -> METHOD_CREDIT
    "debit" -> METHOD_DEBIT
    "virtual_cash" -> METHOD_CASH
    "virtual_balance" -> METHOD_BALANCE
    else -> cardType.ifBlank { "卡片" }
}

fun isVirtualCard(card: Card): Boolean =
    card.cardType == "virtual_cash" || card.cardType == "virtual_balance"

fun cardLast4(card: Card): String = card.last4.ifBlank { "****" }

fun bankNameOf(banks: List<Category>, bankId: String): String =
    banks.firstOrNull { it.id == bankId }?.name.orEmpty()

/** 银行图标（`GET /category?type=bank` 行的 `icon_url`），拼 `AppConfig.fullFileUrl` 前的前缀。 */
fun bankIconUrl(banks: List<Category>, bankId: String): String =
    banks.firstOrNull { it.id == bankId }?.iconUrl.orEmpty()

/** web `getCardDisplayText`：虚拟卡只显示类型名；真实卡「银行名 尾号」，无银行名退化「类型 尾号」。 */
fun cardDisplayText(card: Card, banks: List<Category>): String {
    val typeText = cardTypeText(card.cardType)
    if (isVirtualCard(card)) return typeText
    val bank = bankNameOf(banks, card.bankId)
    val no = cardLast4(card)
    return if (bank.isNotBlank()) "$bank $no" else "$typeText $no"
}

/** 真实卡（排除虚拟现金/余额）。 */
fun realCards(cards: List<Card>): List<Card> = cards.filter { !isVirtualCard(it) }

/** 卡片搜索过滤：银行名 / 尾号 / 类型文案（web 三处列表用的同一套判定，此处合并为一致行为）。 */
fun filterCards(cards: List<Card>, banks: List<Category>, key: String): List<Card> {
    val k = key.trim().lowercase()
    if (k.isEmpty()) return cards
    return cards.filter { c ->
        bankNameOf(banks, c.bankId).lowercase().contains(k) ||
            cardLast4(c).contains(k) ||
            cardTypeText(c.cardType).lowercase().contains(k)
    }
}

// ───────────────────────── 输入约束 ─────────────────────────

/**
 * 只保留数字与单个小数点，整数位 ≤[maxInt]、小数位 ≤[maxDec]（对应 web 两页 watch 里的同一套规则）。
 * - 金额：decimal(12,2) → 整数 ≤10、小数 ≤2
 * - 汇率：999.9999 → 整数 ≤3、小数 ≤4
 */
private fun decimalFilter(raw: String, maxInt: Int, maxDec: Int): String {
    val s = raw.filter { it.isDigit() || it == '.' }
    val parts = s.split(".")
    if (parts.size > 2) return parts[0] + "." + parts.drop(1).joinToString("")
    val intPart = parts[0]
    val dec = parts.getOrNull(1)
    if (dec != null && dec.length > maxDec) return "$intPart.${dec.take(maxDec)}"
    if (intPart.length > maxInt) return intPart.take(maxInt) + if (dec != null) ".$dec" else ""
    return s
}

fun filterAmountInput(raw: String): String = decimalFilter(raw, maxInt = 10, maxDec = 2)

fun filterRateInput(raw: String): String = decimalFilter(raw, maxInt = 3, maxDec = 4)

/** 金额有效（web `Number(amount) > 0 && isFinite`）。 */
fun amountValue(raw: String): Double? = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** 展示用汇率：空/0 → 用币种内置 rate（web `Number(exchangeRate) || currency.rate`）。 */
fun liveRate(raw: String, currency: CurrencyOption): Double =
    raw.toDoubleOrNull()?.takeIf { it != 0.0 } ?: currency.rate

/** 提交用汇率：CNY 恒 1；其余 `round(rate*1e5)/1e5`（web `Math.round(r*100000)/100000`）。 */
fun submitRate(raw: String, currency: CurrencyOption): Double {
    if (currency.code == "CNY") return 1.0
    return round(liveRate(raw, currency) * 100000) / 100000
}

/** 折算人民币：汇率语义 = **每 100 外币等值人民币** → `amount × rate ÷ 100`（与后端一致）。 */
fun exchangedCny(amountRaw: String, rateRaw: String, currency: CurrencyOption): Double {
    if (currency.code == "CNY") return 0.0
    val a = amountRaw.toDoubleOrNull() ?: return 0.0
    return a * liveRate(rateRaw, currency) / 100
}

fun todayString(): String = LocalDate.now().toString()   // yyyy-MM-dd

// ───────────────────────── 提交 ─────────────────────────

/** 一笔待提交的记账请求（→ `POST /account/debit|credit`）。 */
data class RecordPayload(
    val direction: Int,
    val categoryId: String,
    val payType: String,
    val payMethod: String,
    val amount: Double,
    val currency: String,
    val exchangeRate: Double,
    val transDate: String,
    val cardId: String,
    val remark: String,
    /** 自转/提现两笔共用；后端凭它回查支出方并写 `account_transfer`。 */
    val transferGroupId: String = "",
)

/** 转账组 ID（web 用 `crypto.randomUUID()`，此处等价）。 */
fun newTransferGroupId(): String = UUID.randomUUID().toString()

/**
 * 单笔提交：按所选卡的 `card_type` 分流 `/account/credit`（信用卡）或 `/account/debit`
 * （虚拟卡/借记卡）—— 对齐 web `createAccount()`；卡不在列表里（如虚拟卡）按借记卡走。
 */
suspend fun createRecord(graph: AppGraph, cards: List<Card>, p: RecordPayload): ApiResult<Unit> {
    val isCredit = cards.firstOrNull { it.id == p.cardId }?.isCredit == true
    return graph.flow.create(
        NewFlow(
            direction = p.direction,
            amount = p.amount,
            categoryId = p.categoryId,
            categoryName = p.payType,
            payMethod = p.payMethod,
            cardId = p.cardId,
            transDate = p.transDate,
            currency = p.currency,
            exchangeRate = p.exchangeRate,
            remark = p.remark,
            transferGroupId = p.transferGroupId,
            isCredit = isCredit,
        ),
    )
}

/** 顺序提交多笔（自转/提现两笔）：**遇错即停**，与 web 一致（支出失败则不发收入那笔）。 */
suspend fun createRecords(graph: AppGraph, cards: List<Card>, payloads: List<RecordPayload>): ApiResult<Unit> {
    for (p in payloads) {
        val r = createRecord(graph, cards, p)
        if (r !is ApiResult.Ok) return r
    }
    return ApiResult.Ok(Unit, "")
}

/**
 * 提交并把结果分流（两页共用，行为对齐 web）：
 * 成功 → `showSuccessToast(成功文案)` + 返回上一页；失败 → `showFailToast(message)`；
 * 401 → 清会话回登录页、429 → 429 页（与全站其它页一致）。
 */
suspend fun submitRecordFlow(
    graph: AppGraph,
    cards: List<Card>,
    payloads: List<RecordPayload>,
    successMessage: String,
    nav: NavHostController,
    toast: VanToastController,
) {
    when (val r = createRecords(graph, cards, payloads)) {
        is ApiResult.Ok -> {
            toast.success(successMessage)
            nav.popBackStack()
        }
        ApiResult.Unauthorized -> {
            toast.show("登录已失效，请重新登录")
            graph.auth.logout()
            nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
        }
        ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
        is ApiResult.Fail -> toast.show(r.message.ifBlank { "提交失败" })
        else -> toast.show("提交失败")
    }
}
