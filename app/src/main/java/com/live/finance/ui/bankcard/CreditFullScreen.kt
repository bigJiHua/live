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
import com.live.finance.core.nav.Routes
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

/** `credit/AddFull.vue` 的 formData（原文初始值）。 */
private data class CreditFullForm(
    val bankId: String = "",
    val bankName: String = "",
    val last4No: String = "",
    val cardBin: String = "",
    val openDate: String = "",
    val expireDate: String = "",
    val color: String = "#0052cc",
    val billDay: String = "",
    val repayDay: String = "",
    val annualFee: String = "",
    val feeFreeRule: String = "",
    val cardLevel: String = "白金卡",
    val mainSub: String = "主卡",
    val cardOrg: String = "银联",
    val cardLength: String = "16",
    val alias: String = "",
    val currency: String = "CNY",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    val tag: String = "",
    val remark: String = "",
    val creditLimit: String = "",
    val tempLimit: String = "",
    val pointsRate: String = "1",
    val remindSwitch: Boolean = true,
    val remindDays: String = "3",
)

/**
 * 信用卡全功能录入 —— 一比一复刻 web `views/BankCard/credit/AddFull.vue`（4 步向导）。
 *
 * ⚠ 与 `card/Add.vue` 的差异（原文事实）：**无**卡组织联动（BIN/长度/有效期）；币种是**普通文本框**；
 * 卡等级选项为 `普卡/金卡/白金卡/钻石卡/黑卡/无限卡`；BIN 校验文案「卡BIN至少需要6位数字」；
 * 额度上限 100 万（超出自动夹到 1000000）。
 *
 * 遗留：web 有 localStorage 草稿（key `creditCardDraft`，进入自动加载、创建成功清除），原生未接。
 */
@Composable
fun CreditFullScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf(CreditFullForm()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var step by remember { mutableStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var kbTarget by remember { mutableStateOf<String?>(null) }

    var showBank by remember { mutableStateOf(false) }
    var showLevel by remember { mutableStateOf(false) }
    var showMainSub by remember { mutableStateOf(false) }
    var showOrg by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showOpenDate by remember { mutableStateOf(false) }
    var showExpireDate by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    LaunchedEffect(Unit) {
        when (val r = graph.category.list("bank")) {
            is ApiResult.Ok -> banks = r.data.orEmpty().filter { it.name.isNotBlank() && it.name.endsWith("银行") }
            else -> Unit
        }
    }

    fun validateLast4() { if (form.last4No.isNotBlank() && form.last4No.length != 4) { toast("卡号后4位必须为4位数字"); form = form.copy(last4No = "") } }
    fun validateCardBin() { if (form.cardBin.isNotBlank() && form.cardBin.length < 6) { toast("卡BIN至少需要6位数字"); form = form.copy(cardBin = "") } }
    fun validateDay(v: String, isBill: Boolean): String {
        val d = v.toIntOrNull()
        if (v.isNotBlank() && (d == null || d < 1 || d > 31)) { toast(if (isBill) "账单日范围为1-31" else "还款日范围为1-31"); return "" }
        return v
    }
    fun validateLimit(v: String): String {
        val n = v.toDoubleOrNull() ?: return v
        if (n > 1_000_000) { toast("信用额度不能超过100万"); return "1000000" }
        return v
    }
    fun validateTempLimit(v: String): String {
        val n = v.toDoubleOrNull() ?: return v
        if (n > 1_000_000) { toast("临时额度不能超过100万"); return "1000000" }
        return v
    }

    /** 步骤校验（web `nextStep` 原文规则）。 */
    fun nextStep() {
        if (step == 0) {
            if (form.bankId.isBlank() || form.last4No.isBlank() || form.cardBin.isBlank() ||
                form.openDate.isBlank() || form.expireDate.isBlank()
            ) { toast("请填写完整的卡片基本信息"); return }
            validateLast4(); validateCardBin()
            if (form.last4No.isBlank() || form.cardBin.isBlank()) return
        } else if (step == 1) {
            if (form.billDay.isBlank() || form.repayDay.isBlank() || form.annualFee.isBlank() ||
                form.feeFreeRule.isBlank() || form.creditLimit.isBlank() || form.tempLimit.isBlank()
            ) { toast("请填写完整的信用卡专属信息（含额度）"); return }
            form = form.copy(
                billDay = validateDay(form.billDay, true),
                repayDay = validateDay(form.repayDay, false),
                creditLimit = validateLimit(form.creditLimit),
                tempLimit = validateTempLimit(form.tempLimit),
            )
            if (form.billDay.isBlank() || form.repayDay.isBlank()) return
        }
        if (step < 3) step++
    }

    fun submit() {
        submitting = true
        scope.launch {
            val p = NewCard(
                cardType = "credit", bankId = form.bankId, last4No = form.last4No, cardBin = form.cardBin,
                openDate = form.openDate, expireDate = form.expireDate,
                alias = form.alias, cardLevel = form.cardLevel.ifBlank { "白金卡" }, mainSub = form.mainSub,
                cardOrg = form.cardOrg, cardLength = form.cardLength.ifBlank { "16" },
                currency = form.currency, status = form.status, isDefault = form.isDefault, isHide = form.isHide,
                sort = 99, tag = form.tag, remark = form.remark, color = form.color, sourceFrom = "手动",
                billDay = form.billDay.toIntOrNull() ?: 0, repayDay = form.repayDay.toIntOrNull() ?: 0,
                annualFee = form.annualFee.toDoubleOrNull() ?: 0.0, feeFreeRule = form.feeFreeRule,
                creditLimit = form.creditLimit.toDoubleOrNull() ?: 0.0,
                tempLimit = form.tempLimit.toDoubleOrNull() ?: 0.0,
                pointsRate = form.pointsRate.toDoubleOrNull() ?: 1.0,
            )
            when (val r = graph.card.createNew(p)) {
                is ApiResult.Ok -> {
                    toast("创建成功！")
                    if (!nav.popBackStack(Routes.CARD, false)) nav.navigate(Routes.CARD)
                }
                is ApiResult.Fail -> toast(r.message.ifBlank { "创建失败，请重试" })
                else -> toast("创建失败，请重试")
            }
            submitting = false
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        ScreenScaffold { inner ->
            Column(Modifier.fillMaxSize().then(inner)) {
                BankTopBar(title = "信用卡全功能录入", onBack = { nav.popBackStack() })
                // 步骤指示
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    FText("${step + 1} / 4 步", 13f, color = colors.textSecondary)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (step) {
                        0 -> StepBasic(form, { form = it }, banks, { showBank = true }, { showOpenDate = true }, { showExpireDate = true }, { kbTarget = it })
                        1 -> StepCredit(form, { form = it }, { kbTarget = it })
                        2 -> StepSettings(form, { form = it }, { showLevel = true }, { showMainSub = true }, { showOrg = true }, { showStatus = true }, { kbTarget = it })
                        else -> StepConfirm(form)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                // 底部按钮
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) {
                        Box(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(colors.bgCard)
                                .clickable { step-- },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VanIcon("arrow-left", size = 14.sp, color = colors.textPrimary)
                                Spacer(Modifier.width(6.dp))
                                FText("上一步", 15f, FontWeight.Medium, colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp))
                            .background(if (submitting) colors.primary.copy(alpha = 0.6f) else colors.primary)
                            .clickable(enabled = !submitting) { if (step < 3) nextStep() else submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        FText(if (step < 3) "下一步" else if (submitting) "提交中…" else "提交卡片", 15f, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        // ===== 弹层 =====
        BankPickerSheet(showBank, banks, { b -> form = form.copy(bankId = b.id, bankName = b.name); showBank = false }, { showBank = false })
        PickerSheet(showLevel, CARD_LEVEL_FULL, CARD_LEVEL_FULL.indexOf(form.cardLevel).coerceAtLeast(0), null,
            { i -> form = form.copy(cardLevel = CARD_LEVEL_FULL[i]); showLevel = false }, { showLevel = false })
        PickerSheet(showMainSub, MAIN_SUB_COLUMNS, MAIN_SUB_COLUMNS.indexOf(form.mainSub).coerceAtLeast(0), null,
            { i -> form = form.copy(mainSub = MAIN_SUB_COLUMNS[i]); showMainSub = false }, { showMainSub = false })
        PickerSheet(showOrg, CARD_ORG_COLUMNS, CARD_ORG_COLUMNS.indexOf(form.cardOrg).coerceAtLeast(0), null,
            { i -> form = form.copy(cardOrg = CARD_ORG_COLUMNS[i]); showOrg = false }, { showOrg = false })
        PickerSheet(showStatus, STATUS_COLUMNS, STATUS_COLUMNS.indexOf(form.status).coerceAtLeast(0), null,
            { i -> form = form.copy(status = STATUS_COLUMNS[i]); showStatus = false }, { showStatus = false })
        CreditDateSheet(showOpenDate, "date", form.openDate.ifBlank { todayYmd() }.let { parseYmd(it) }, { showOpenDate = false }) { d ->
            form = form.copy(openDate = ymdOf(d)); showOpenDate = false
        }
        CreditDateSheet(showExpireDate, "year-month", parseYm(form.expireDate.ifBlank { "2030-12" }), { showExpireDate = false }) { d ->
            form = form.copy(expireDate = "%04d-%02d".format(d.year, d.monthValue)); showExpireDate = false
        }

        VanNumberKeyboard(
            show = kbTarget != null,
            text = when (kbTarget) {
                "last4No" -> form.last4No; "cardBin" -> form.cardBin; "billDay" -> form.billDay
                "repayDay" -> form.repayDay; "annualFee" -> form.annualFee; "creditLimit" -> form.creditLimit
                "tempLimit" -> form.tempLimit; "pointsRate" -> form.pointsRate; "remindDays" -> form.remindDays
                "cardLength" -> form.cardLength; else -> ""
            },
            onTextChange = { v -> form = applyKb(form, kbTarget, v) },
            theme = VanKeyboardTheme.Custom,
            closeButtonText = "完成",
            maxLength = when (kbTarget) {
                "last4No" -> 4; "cardBin" -> 6; "billDay", "repayDay", "remindDays", "cardLength" -> 2
                "creditLimit", "tempLimit" -> 7; "pointsRate" -> 3; else -> 10
            },
            onClose = { form = validateKb(form, kbTarget); kbTarget = null },
            onBlur = { form = validateKb(form, kbTarget); kbTarget = null },
        )
    }
}

private fun applyKb(f: CreditFullForm, target: String?, v: String): CreditFullForm = when (target) {
    "last4No" -> f.copy(last4No = v)
    "cardBin" -> f.copy(cardBin = v)
    "billDay" -> f.copy(billDay = v)
    "repayDay" -> f.copy(repayDay = v)
    "annualFee" -> f.copy(annualFee = v)
    "creditLimit" -> f.copy(creditLimit = v)
    "tempLimit" -> f.copy(tempLimit = v)
    "pointsRate" -> f.copy(pointsRate = v)
    "remindDays" -> f.copy(remindDays = v)
    "cardLength" -> f.copy(cardLength = v)
    else -> f
}

private fun validateKb(f: CreditFullForm, target: String?): CreditFullForm = when (target) {
    "last4No" -> if (f.last4No.isNotEmpty() && f.last4No.length != 4) f.copy(last4No = "") else f
    "cardBin" -> if (f.cardBin.isNotEmpty() && f.cardBin.length < 6) f.copy(cardBin = "") else f
    else -> f
}

// ===== 步骤 1：基本信息 + 卡片外观 =====
@Composable
private fun StepBasic(
    form: CreditFullForm,
    onChange: (CreditFullForm) -> Unit,
    banks: List<Category>,
    onPickBank: () -> Unit,
    onPickOpen: () -> Unit,
    onPickExpire: () -> Unit,
    openKb: (String) -> Unit,
) {
    FormSection(title = "基本信息", requiredHint = true) {
        ReadonlyRow("银行", form.bankName, "请选择银行", onPickBank)
        ReadonlyRow("卡号后4位", form.last4No, "请输入", { openKb("last4No") })
        ReadonlyRow("卡BIN", form.cardBin, "卡号前6位", { openKb("cardBin") })
        ReadonlyRow("开卡日期", form.openDate, "请选择", onPickOpen)
        ReadonlyRow("过期日期", form.expireDate, "请选择", onPickExpire, border = false)
    }
    FormSection(title = "卡片外观") {
        ColorPickerRow("卡片颜色", form.color, { onChange(form.copy(color = it)) }, border = false)
    }
}

// ===== 步骤 2：信用卡专属 + 额度 + 积分提醒 =====
@Composable
private fun StepCredit(form: CreditFullForm, onChange: (CreditFullForm) -> Unit, openKb: (String) -> Unit) {
    val colors = LocalAppColors.current
    FormSection(title = "信用卡专属信息", requiredHint = true, titleColor = colors.danger, titleIcon = "star") {
        ReadonlyRow("账单日", form.billDay, "如：5", { openKb("billDay") }, suffix = "日")
        ReadonlyRow("还款日", form.repayDay, "如：25", { openKb("repayDay") }, suffix = "日")
        ReadonlyRow("年费", form.annualFee, "如：0", { openKb("annualFee") }, suffix = "元")
        InputRow("免年费规则", form.feeFreeRule, { onChange(form.copy(feeFreeRule = it)) }, "如：刷6次免年费", border = false)
    }
    FormSection(title = "额度信息", requiredHint = true, titleColor = colors.danger, titleIcon = "card") {
        ReadonlyRow("信用额度", form.creditLimit, "请输入信用额度", { openKb("creditLimit") }, suffix = "元")
        ReadonlyRow("临时额度", form.tempLimit, "请输入临时额度", { openKb("tempLimit") }, suffix = "元", border = false)
    }
    FormSection(title = "积分与提醒") {
        ReadonlyRow("积分倍率", form.pointsRate, "如: 1", { openKb("pointsRate") }, suffix = "倍")
        SwitchRow("还款提醒", form.remindSwitch, { onChange(form.copy(remindSwitch = it)) })
        ReadonlyRow(
            "提前提醒", form.remindDays, "请输入天数",
            onClick = { if (form.remindSwitch) openKb("remindDays") },
            suffix = "天", enabled = form.remindSwitch, border = false,
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText("账单日：银行生成账单的日期 / 还款日：必须还清欠款的日期", 12f, color = colors.textTertiary)
    }
}

// ===== 步骤 3：卡片设置 + 状态 + 备注 =====
@Composable
private fun StepSettings(
    form: CreditFullForm,
    onChange: (CreditFullForm) -> Unit,
    onPickLevel: () -> Unit,
    onPickMainSub: () -> Unit,
    onPickOrg: () -> Unit,
    onPickStatus: () -> Unit,
    openKb: (String) -> Unit,
) {
    FormSection(title = "卡片设置") {
        ReadonlyRow("卡等级", form.cardLevel, "请选择", onPickLevel)
        ReadonlyRow("主副卡", form.mainSub, "请选择", onPickMainSub)
        ReadonlyRow("卡组织", form.cardOrg, "请选择", onPickOrg, leading = { CardOrgMini(form.cardOrg) })
        ReadonlyRow("卡号长度", form.cardLength, "默认16位", { openKb("cardLength") })
        InputRow("卡片别名", form.alias, { onChange(form.copy(alias = it)) }, "如：工资卡、留学卡", border = false)
    }
    FormSection(title = "状态设置") {
        // ⚠ web 此页币种是普通文本框（非 picker）
        InputRow("币种", form.currency, { onChange(form.copy(currency = it)) }, "默认CNY")
        ReadonlyRow("卡片状态", form.status, "请选择", onPickStatus, border = false)
        SwitchRow("设为默认卡", form.isDefault, { onChange(form.copy(isDefault = it)) })
        SwitchRow("隐藏卡片", form.isHide, { onChange(form.copy(isHide = it)) }, border = false)
    }
    FormSection(title = "备注") {
        InputRow("标签", form.tag, { onChange(form.copy(tag = it)) }, "如：日常消费、出国使用")
        InputRow("备注", form.remark, { onChange(form.copy(remark = it)) }, "其他备注信息", border = false)
    }
}

// ===== 步骤 4：确认（只读） =====
@Composable
private fun StepConfirm(form: CreditFullForm) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VanIcon("passed", size = 16.sp, color = colors.success)
            Spacer(Modifier.width(6.dp))
            FText("请确认卡片信息", 15f, FontWeight.SemiBold, colors.success)
        }
    }
    FormSection(title = "卡片信息") {
        ConfirmRow("银行", form.bankName.ifBlank { "-" })
        ConfirmRow("卡号后4位", if (form.last4No.isNotBlank()) "****${form.last4No}" else "-")
        ConfirmRow("卡BIN", if (form.cardBin.isNotBlank()) "${form.cardBin}******" else "-")
        ConfirmRow("开卡日期", form.openDate.ifBlank { "-" })
        ConfirmRow("过期日期", form.expireDate.ifBlank { "-" }, border = false)
    }
    FormSection(title = "信用卡专属") {
        ConfirmRow("账单日", if (form.billDay.isNotBlank()) "每月${form.billDay}日" else "-")
        ConfirmRow("还款日", if (form.repayDay.isNotBlank()) "每月${form.repayDay}日" else "-")
        ConfirmRow("年费", if (form.annualFee.isNotBlank()) "${form.annualFee}元" else "-")
        ConfirmRow("免年费规则", form.feeFreeRule.ifBlank { "-" })
        ConfirmRow("信用额度", if (form.creditLimit.isNotBlank()) "${form.creditLimit}元" else "-")
        ConfirmRow("临时额度", if (form.tempLimit.isNotBlank()) "${form.tempLimit}元" else "0元", border = false)
    }
    FormSection(title = "卡片设置") {
        ConfirmRow("卡片别名", form.alias.ifBlank { "-" })
        ConfirmRow("卡片状态", form.status)
        ConfirmRow("默认卡", if (form.isDefault) "是" else "否", border = false)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        FText("确认信息无误后，点击「提交卡片」按钮创建卡片", 12f, color = colors.textTertiary)
    }
}

@Composable
private fun ConfirmRow(title: String, value: String, border: Boolean = true) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(title, 14f, color = colors.textSecondary, modifier = Modifier.weight(1f))
        FText(value, 14f, color = colors.textPrimary)
    }
    RowDivider(border)
}

/** 4 步向导的日期弹层。 */
@Composable
private fun CreditDateSheet(
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
            type = type, value = cur, onValueChange = { cur = it },
            title = "选择日期", onConfirm = { onConfirm(it) }, onCancel = onDismiss,
        )
    }
}
