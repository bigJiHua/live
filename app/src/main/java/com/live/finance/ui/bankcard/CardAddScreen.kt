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
import com.live.finance.data.model.Category
import com.live.finance.data.repo.NewCard
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate

/** `card/Add.vue` 的 formData（原文初始值）。 */
private data class CardAddForm(
    val bankId: String = "",
    val bankName: String = "",
    val cardOrg: String = "银联",
    val cardLength: String = "19",
    val cardBin: String = "",
    val last4No: String = "",
    val openDate: String = "",
    val expireDate: String = "",
    val cardLevel: String = "普卡",
    val mainSub: String = "主卡",
    val alias: String = "",
    val color: String = "#0052cc",
    val currency: String = "CNY",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    val tag: String = "",
    val remark: String = "",
)

/**
 * 添加借记卡 —— 一比一复刻 web `views/BankCard/card/Add.vue`。
 *
 * 联动（Add 独有，最完整）：卡组织 → BIN 前缀 / 卡号长度 / 有效期年数；开卡日期 + 卡组织 → 过期日期。
 * 提交：`POST /card`；`cardImg` 不提交；`expireDate` 补 `-01`；可选字段为空的**不提交**。
 */
@Composable
fun CardAddScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf(CardAddForm()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var kbTarget by remember { mutableStateOf<String?>(null) }
    var showBank by remember { mutableStateOf(false) }
    var showOrg by remember { mutableStateOf(false) }
    var showLength by remember { mutableStateOf(false) }
    var showLevel by remember { mutableStateOf(false) }
    var showMainSub by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showOpenDate by remember { mutableStateOf(false) }
    var showExpireDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = graph.category.list("bank")) {
            is ApiResult.Ok -> banks = r.data.orEmpty().filter { it.name.isNotBlank() && it.name.endsWith("银行") }
            else -> Unit
        }
    }

    val binExpected = binExpectedLength(form.cardOrg)

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    /** 选卡组织联动（Add 独有）：BIN 前缀 + 卡号长度 + 过期日期。 */
    fun onOrgPicked(org: String) {
        val cfg = CARD_ORG_BIN_PREFIX[org]
        var f = form.copy(cardOrg = org, cardBin = cfg?.first ?: "", cardLength = (CARD_ORG_LENGTH[org] ?: 16).toString())
        if (f.openDate.isNotBlank()) f = f.copy(expireDate = calcExpireDate(f.openDate, org))
        form = f
    }

    fun validateLast4() {
        if (form.last4No.isNotBlank() && form.last4No.length != 4) {
            toast("卡号后4位必须为4位数字"); form = form.copy(last4No = "")
        }
    }

    fun validateCardBin() {
        if (form.cardBin.isNotBlank() && form.cardBin.length < binExpected) {
            toast("卡BIN必须至少${binExpected}位数字"); form = form.copy(cardBin = "")
        }
    }

    fun submit() {
        if (form.bankId.isBlank()) { toast("请选择银行"); return }
        validateLast4(); validateCardBin()
        if (form.last4No.isBlank() || form.last4No.length != 4) { toast("请输入4位卡号"); return }
        if (form.cardBin.isBlank()) { toast("请输入卡BIN"); return }
        if (form.openDate.isBlank()) { toast("请选择开卡日期"); return }
        submitting = true
        scope.launch {
            val p = NewCard(
                cardType = "debit", bankId = form.bankId, last4No = form.last4No, cardBin = form.cardBin,
                openDate = form.openDate,
                expireDate = if (form.expireDate.isNotBlank()) "${form.expireDate}-01" else "",
                alias = form.alias, cardLevel = form.cardLevel, mainSub = form.mainSub,
                cardOrg = form.cardOrg, cardLength = form.cardLength, currency = form.currency,
                status = form.status, isDefault = form.isDefault, isHide = form.isHide, sort = 99,
                tag = form.tag, remark = form.remark, color = form.color, sourceFrom = "手动",
                omitBlankOptional = true,
            )
            when (val r = graph.card.createNew(p)) {
                is ApiResult.Ok -> { toast("添加成功"); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "添加失败" })
                else -> toast("添加失败")
            }
            submitting = false
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        ScreenScaffold { inner ->
            Column(Modifier.fillMaxSize().then(inner)) {
                BankTopBar(title = "添加卡片", onBack = { nav.popBackStack() })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // ===== 基本信息 =====
                    FormSection(title = "基本信息", requiredHint = true) {
                        ReadonlyRow("银行", form.bankName, "请选择银行", { showBank = true })
                        ReadonlyRow("卡组织", form.cardOrg, "请选择", { showOrg = true },
                            leading = { CardOrgMini(form.cardOrg) })
                        ReadonlyRow("卡号长度", form.cardLength.let { if (it.isBlank()) "" else "${it}位" }, "请选择", { showLength = true })
                        ReadonlyRow("卡号前位", form.cardBin, "请输入", { kbTarget = "cardBin" })
                        ReadonlyRow("卡号后4位", form.last4No, "请输入", { kbTarget = "last4No" })
                        ReadonlyRow("开卡日期", form.openDate, "请选择", { showOpenDate = true }, border = false)
                        ReadonlyRow("过期日期", form.expireDate, "请选择", { showExpireDate = true })
                    }
                    // ===== 选填信息 =====
                    FormSection(title = "选填信息") {
                        ReadonlyRow("卡等级", form.cardLevel, "默认普卡", { showLevel = true })
                        ReadonlyRow("主副卡", form.mainSub, "请选择", { showMainSub = true }, border = false)
                        InputRow("卡片别名", form.alias, { form = form.copy(alias = it) }, "如：工资卡、留学卡")
                    }
                    // ===== 外观 =====
                    FormSection(title = "外观") {
                        ColorPickerRow("卡片颜色", form.color, { form = form.copy(color = it) }, border = false)
                    }
                    // ===== 设置 =====
                    FormSection(title = "设置") {
                        ReadonlyRow("币种", currencyLabel(form.currency), "请选择", { showCurrency = true })
                        ReadonlyRow("卡片状态", form.status, "请选择", { showStatus = true }, border = false)
                        SwitchRow("设为默认卡", form.isDefault, { form = form.copy(isDefault = it) })
                        SwitchRow("隐藏卡片", form.isHide, { form = form.copy(isHide = it) }, border = false)
                    }
                    // ===== 备注 =====
                    FormSection(title = "备注") {
                        InputRow("标签", form.tag, { form = form.copy(tag = it) }, "如：日常消费、出国使用")
                        InputRow("备注", form.remark, { form = form.copy(remark = it) }, "其他备注信息", border = false)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                // 提交
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (submitting) colors.primary.copy(alpha = 0.6f) else colors.primary)
                        .clickable(enabled = !submitting) { submit() },
                    contentAlignment = Alignment.Center,
                ) { FText(if (submitting) "保存中…" else "保存借记卡", 16f, FontWeight.SemiBold, Color.White) }
            }
        }

        // ===== 弹层 =====
        BankPickerSheet(showBank, banks, { b -> form = form.copy(bankId = b.id, bankName = b.name); showBank = false }, { showBank = false })
        PickerSheet(showOrg, CARD_ORG_COLUMNS, CARD_ORG_COLUMNS.indexOf(form.cardOrg).coerceAtLeast(0), null,
            { idx -> onOrgPicked(CARD_ORG_COLUMNS[idx]); showOrg = false }, { showOrg = false })
        IndexedPickerSheet(showLength, CARD_LENGTH_COLUMNS, form.cardLength, null,
            { v -> form = form.copy(cardLength = v); showLength = false }, { showLength = false })
        PickerSheet(showLevel, CARD_LEVEL_ADD, CARD_LEVEL_ADD.indexOf(form.cardLevel).coerceAtLeast(0), null,
            { idx -> form = form.copy(cardLevel = CARD_LEVEL_ADD[idx]); showLevel = false }, { showLevel = false })
        PickerSheet(showMainSub, MAIN_SUB_COLUMNS, MAIN_SUB_COLUMNS.indexOf(form.mainSub).coerceAtLeast(0), null,
            { idx -> form = form.copy(mainSub = MAIN_SUB_COLUMNS[idx]); showMainSub = false }, { showMainSub = false })
        IndexedPickerSheet(showCurrency, CURRENCY_COLUMNS, form.currency, "选择币种",
            { v -> form = form.copy(currency = v); showCurrency = false }, { showCurrency = false })
        PickerSheet(showStatus, STATUS_COLUMNS, STATUS_COLUMNS.indexOf(form.status).coerceAtLeast(0), null,
            { idx -> form = form.copy(status = STATUS_COLUMNS[idx]); showStatus = false }, { showStatus = false })
        DateSheet(showOpenDate, "date", form.openDate.ifBlank { todayYmd() }.let { parseYmd(it) }, { showOpenDate = false }) { d ->
            val s = ymdOf(d)
            var f = form.copy(openDate = s)
            if (f.cardOrg.isNotBlank()) f = f.copy(expireDate = calcExpireDate(s, f.cardOrg))
            form = f; showOpenDate = false
        }
        DateSheet(showExpireDate, "year-month", parseYm(form.expireDate.ifBlank { "2030-12" }), { showExpireDate = false }) { d ->
            form = form.copy(expireDate = "%04d-%02d".format(d.year, d.monthValue)); showExpireDate = false
        }

        // 数字键盘（cardBin / last4No）
        VanNumberKeyboard(
            show = kbTarget != null,
            text = when (kbTarget) { "cardBin" -> form.cardBin; "last4No" -> form.last4No; else -> "" },
            onTextChange = { v ->
                when (kbTarget) {
                    "cardBin" -> form = form.copy(cardBin = v.take(binExpected))
                    "last4No" -> form = form.copy(last4No = v.take(4))
                }
            },
            theme = VanKeyboardTheme.Custom,
            closeButtonText = "完成",
            maxLength = when (kbTarget) { "cardBin" -> binExpected; "last4No" -> 4; else -> 8 },
            onClose = { if (kbTarget == "cardBin") validateCardBin() else validateLast4(); kbTarget = null },
            onBlur = { if (kbTarget == "cardBin") validateCardBin() else validateLast4(); kbTarget = null },
        )
    }
}

/** 卡组织 BIN 期望长度 = 前缀位数 + 需补录位数（缺省 6）。 */
fun binExpectedLength(org: String): Int {
    val cfg = CARD_ORG_BIN_PREFIX[org] ?: return 6
    return cfg.first.length + cfg.second
}

/** 过期日期 = 开卡年 + 有效期年数，月份沿用开卡月（web `calculateExpireDate`）。 */
fun calcExpireDate(openDate: String, org: String): String {
    if (openDate.length < 7) return ""
    val years = CARD_ORG_VALIDITY[org] ?: 5
    val y = openDate.substring(0, 4).toIntOrNull() ?: return ""
    val m = openDate.substring(5, 7)
    return "%04d-%s".format(y + years, m)
}

fun currencyLabel(code: String): String = CURRENCY_COLUMNS.firstOrNull { it.first == code }?.second ?: code

fun todayYmd(): String = java.time.LocalDate.now().toString()

fun parseYmd(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrDefault(LocalDate.now())

fun parseYm(s: String): LocalDate {
    if (s.length < 7) return LocalDate.now()
    return runCatching { LocalDate.of(s.substring(0, 4).toInt(), s.substring(5, 7).toInt(), 1) }
        .getOrDefault(LocalDate.now())
}

fun ymdOf(d: LocalDate): String = "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)

/** 通用顶部栏（web 由 MainLayout 全局提供，原生各页自绘）。 */
@Composable
fun BankTopBar(title: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clickable { onBack() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
            VanIcon("arrow-left", size = 20.sp, color = colors.textPrimary)
        }
        FText(title, 17f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
        Spacer(Modifier.width(44.dp))
    }
}

/** 日期选择弹层（web `van-date-picker`）。 */
@Composable
private fun DateSheet(
    show: Boolean,
    type: String,
    value: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    if (!show) return
    var cur by remember(value) { mutableStateOf(value) }
    VanPopup(show = true, onDismissRequest = onDismiss) {
        VanDatePicker(
            type = type,
            value = cur,
            onValueChange = { cur = it },
            minDate = LocalDate.of(2000, 1, 1),
            maxDate = LocalDate.of(2050, 12, 31),
            title = "选择日期",
            onConfirm = { onConfirm(it) },
            onCancel = onDismiss,
        )
    }
}

/** 卡组织小徽标（只读行前缀，对齐 web `CardOrgIcon small`）。 */
@Composable
fun CardOrgMini(orgName: String) {
    val key = cardOrgKey(orgName)
    if (key.isEmpty()) return
    CardOrgBadge(org = key, width = 32.dp, height = 20.dp, filled = true)
}
