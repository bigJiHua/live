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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.Resource
import com.live.finance.data.repo.NewCard
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate

/** `card/Edit.vue` 的 formData（原文初始值）。 */
private data class CardEditForm(
    val cardType: String = "",
    val bankId: String = "",
    val bankName: String = "",
    val alias: String = "",
    val cardLevel: String = "",
    val mainSub: String = "主卡",
    val cardOrg: String = "",
    val cardLength: String = "",
    val currency: String = "CNY",
    val cardBin: String = "",
    val last4No: String = "",
    val openDate: String = "",
    val expireDate: String = "",
    val billDay: String = "",
    val repayDay: String = "",
    val annualFee: String = "",
    val feeFreeRule: String = "",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    val tag: String = "",
    val remark: String = "",
    val color: String = "#0052cc",
    val cardImg: String = "",
) {
    val isCredit: Boolean get() = cardType == "credit"
}

/**
 * 编辑卡片 —— 一比一复刻 web `views/BankCard/card/Edit.vue`（借/信用通用）。
 *
 * - 顶部卡片预览；信用卡专属区块（账单日/还款日 + 「检查是否可修改」按钮）；
 * - 卡组织仅联动卡号长度与图标（**不联动 BIN / 过期日期**，与 Add 不同）；
 * - 保存/删除均有二次确认；成功后返回卡片列表；
 * - 卡面图走资源库 `busType=other`，过滤 `.png/.jpg/.jpeg/.webp`。
 * - 提交**不包含** creditLimit/tempLimit/pointsRate（额度由「额度与共享池」页负责）。
 */
@Composable
fun CardEditScreen(nav: NavHostController, cardId: String = "") {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf(CardEditForm()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    /** web：`isEditable === null` 表示「尚未检查」→ 账单日/还款日不可点。 */
    var isEditable by remember { mutableStateOf<Boolean?>(null) }
    var checkLoading by remember { mutableStateOf(false) }
    var kbTarget by remember { mutableStateOf<String?>(null) }

    var showBank by remember { mutableStateOf(false) }
    var showOrg by remember { mutableStateOf(false) }
    var showMainSub by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showOpenDate by remember { mutableStateOf(false) }
    var showExpireDate by remember { mutableStateOf(false) }

    var showSaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var showImagePicker by remember { mutableStateOf(false) }
    var imageLoading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var pickedImg by remember { mutableStateOf("") }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun loadDetail() {
        scope.launch {
            loading = true
            when (val br = graph.category.list("bank")) {
                is ApiResult.Ok -> banks = br.data.orEmpty().filter { it.name.isNotBlank() && it.name.endsWith("银行") }
                else -> Unit
            }
            when (val r = graph.card.detail(cardId)) {
                is ApiResult.Ok -> {
                    val c = r.data
                    if (c == null) { toast("卡片不存在"); loading = false; return@launch }
                    form = CardEditForm(
                        cardType = c.cardType, bankId = c.bankId,
                        bankName = banks.firstOrNull { it.id == c.bankId }?.name ?: c.bankName,
                        alias = c.alias, cardLevel = c.cardLevel, mainSub = c.mainSub,
                        cardOrg = c.cardOrg, cardLength = c.cardLength, currency = c.currency,
                        cardBin = c.cardBin, last4No = c.last4,
                        openDate = c.openDate, expireDate = c.expireDate.take(7),
                        billDay = c.billDay.takeIf { it > 0 }?.toString() ?: "",
                        repayDay = c.repayDay.takeIf { it > 0 }?.toString() ?: "",
                        annualFee = c.annualFee.takeIf { it > 0 }?.toString() ?: "",
                        feeFreeRule = c.feeFreeRule, status = c.status,
                        isDefault = c.isDefault, isHide = c.isHide,
                        tag = c.tag, remark = c.remark, color = c.displayColor, cardImg = c.cardImg,
                    )
                }
                is ApiResult.Fail -> toast(r.message.ifBlank { "加载失败" })
                else -> Unit
            }
            loading = false
        }
    }

    LaunchedEffect(cardId) {
        if (cardId.isBlank()) { toast("缺少卡片ID"); nav.popBackStack() } else loadDetail()
    }

    val binExpected = binExpectedLength(form.cardOrg)

    fun validateLast4() {
        if (form.last4No.isNotBlank() && form.last4No.length != 4) { toast("卡号后4位必须为4位数字"); form = form.copy(last4No = "") }
    }

    fun validateCardBin() {
        if (form.cardBin.isNotBlank() && form.cardBin.length < 6) { toast("卡BIN必须至少6位数字"); form = form.copy(cardBin = "") }
    }

    fun validateBillDay() {
        val d = form.billDay.toIntOrNull()
        if (form.billDay.isNotBlank() && (d == null || d < 1 || d > 31)) { toast("账单日范围为1-31"); form = form.copy(billDay = "") }
    }

    fun validateRepayDay() {
        val d = form.repayDay.toIntOrNull()
        if (form.repayDay.isNotBlank() && (d == null || d < 1 || d > 31)) { toast("还款日范围为1-31"); form = form.copy(repayDay = "") }
    }

    fun checkEditable() {
        checkLoading = true
        scope.launch {
            when (val r = graph.flow.listByCard(cardId, 1, 1)) {
                is ApiResult.Ok -> {
                    val has = r.data.orEmpty().isNotEmpty()
                    isEditable = !has
                    toast(if (has) "该卡片已有流水记录，账单日和还款日不可修改" else "该卡片暂无流水，可以修改账单日和还款日")
                }
                else -> toast("检查失败")
            }
            checkLoading = false
        }
    }

    fun loadImages() {
        imageLoading = true
        scope.launch {
            when (val r = graph.resource.list("other", "", 100, 0)) {
                is ApiResult.Ok -> images = r.data.orEmpty().filter { it.fileExt.lowercase() in setOf(".png", ".jpg", ".jpeg", ".webp") }
                else -> images = emptyList()
            }
            imageLoading = false
        }
    }

    fun buildNewCard() = NewCard(
        cardType = form.cardType, bankId = form.bankId, last4No = form.last4No, cardBin = form.cardBin,
        openDate = form.openDate, expireDate = form.expireDate,
        alias = form.alias, cardLevel = form.cardLevel, mainSub = form.mainSub, cardOrg = form.cardOrg,
        cardLength = form.cardLength, cardImg = form.cardImg, currency = form.currency, status = form.status,
        isDefault = form.isDefault, isHide = form.isHide, sort = 99,
        tag = form.tag, remark = form.remark, color = form.color, sourceFrom = "手动",
        billDay = form.billDay.toIntOrNull() ?: 0, repayDay = form.repayDay.toIntOrNull() ?: 0,
        annualFee = form.annualFee.toDoubleOrNull() ?: 0.0, feeFreeRule = form.feeFreeRule,
        omitLimitFields = true,
    )

    fun doSave() {
        validateLast4(); validateCardBin(); validateBillDay(); validateRepayDay()
        submitting = true
        scope.launch {
            when (val r = graph.card.update(cardId, buildNewCard())) {
                is ApiResult.Ok -> { toast("保存成功"); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "保存失败" })
                else -> toast("保存失败")
            }
            submitting = false
        }
    }

    fun doDelete() {
        submitting = true
        scope.launch {
            when (val r = graph.card.delete(cardId)) {
                is ApiResult.Ok -> { toast("删除成功"); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "删除失败" })
                else -> toast("删除失败")
            }
            submitting = false
        }
    }

    val previewCard = Card(
        id = cardId, cardType = form.cardType.ifBlank { "debit" }, bankId = form.bankId,
        bankName = form.bankName, alias = form.alias, last4 = form.last4No, cardBin = form.cardBin,
        cardOrg = form.cardOrg, cardLevel = form.cardLevel, cardLength = form.cardLength.ifBlank { "16" },
        color = form.color, isDefault = form.isDefault, cardImg = form.cardImg,
    )

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        ScreenScaffold { inner ->
            if (loading) {
                Box(Modifier.fillMaxSize().then(inner), contentAlignment = Alignment.Center) { VanLoading() }
            } else {
                Column(Modifier.fillMaxSize().then(inner)) {
                    BankTopBar(title = "编辑卡片", onBack = { nav.popBackStack() })
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        // 卡片预览
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BankCardFace(previewCard, if (form.isCredit) "CREDIT CARD" else "DEBIT CARD", showLast4Suffix = false)
                        }

                        // ===== 基本信息 =====
                        FormSection(title = "基本信息") {
                            ReadonlyRow("银行", form.bankName, "请选择", { showBank = true })
                            ReadonlyRow("卡类型", if (form.isCredit) "信用卡" else "借记卡", "", onClick = null, arrow = false)
                            InputRow("卡片别名", form.alias, { form = form.copy(alias = it) }, "如：工资卡、留学卡")
                            InputRow("卡等级", form.cardLevel, { form = form.copy(cardLevel = it) }, "如：金卡、白金卡")
                            ReadonlyRow("主副卡", form.mainSub, "请选择", { showMainSub = true }, border = false)
                        }

                        // ===== 卡片信息 =====
                        FormSection(title = "卡片信息") {
                            ReadonlyRow("卡组织", form.cardOrg, "请选择", { showOrg = true }, leading = { CardOrgMini(form.cardOrg) })
                            ReadonlyRow("卡号长度", form.cardLength, "请输入", { kbTarget = "cardLength" })
                            ReadonlyRow("币种", currencyLabel(form.currency), "请选择", { showCurrency = true })
                            ReadonlyRow("卡BIN", form.cardBin, "请输入", { kbTarget = "cardBin" })
                            ReadonlyRow("卡号后4位", form.last4No, "请输入", { kbTarget = "last4No" })
                            ReadonlyRow("开卡日期", form.openDate, "请选择", { showOpenDate = true })
                            ReadonlyRow("过期日期", form.expireDate, "请选择", { showExpireDate = true }, border = false)
                        }

                        // ===== 信用卡专属 =====
                        if (form.isCredit) {
                            FormSection(title = "信用卡专属", titleColor = colors.danger, titleIcon = "star") {
                                ReadonlyRow(
                                    "账单日", form.billDay, "请输入",
                                    onClick = { if (isEditable == true) kbTarget = "billDay" },
                                    suffix = "日", enabled = isEditable == true,
                                )
                                ReadonlyRow(
                                    "还款日", form.repayDay, "请输入",
                                    onClick = { if (isEditable == true) kbTarget = "repayDay" },
                                    suffix = "日", enabled = isEditable == true,
                                )
                                ReadonlyRow("年费", form.annualFee, "请输入", { kbTarget = "annualFee" }, suffix = "元")
                                InputRow("免年费规则", form.feeFreeRule, { form = form.copy(feeFreeRule = it) }, "如：刷6次免年费")
                                // 「检查是否可修改」
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(if (isEditable != null) colors.border else colors.primary)
                                            .clickable(enabled = checkLoading || isEditable == null) { checkEditable() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        FText(
                                            when {
                                                checkLoading -> "检查中…"
                                                isEditable == true -> "已解锁"
                                                isEditable == false -> "已锁定"
                                                else -> "检查是否可修改"
                                            },
                                            13f, FontWeight.Medium, if (isEditable != null) colors.textSecondary else Color.White,
                                        )
                                    }
                                    if (isEditable == false) {
                                        Spacer(Modifier.height(6.dp))
                                        FText("有流水记录，不可修改", 12f, color = colors.danger)
                                    } else if (isEditable == true) {
                                        Spacer(Modifier.height(6.dp))
                                        FText("可自由修改", 12f, color = colors.success)
                                    }
                                }
                            }
                        }

                        // ===== 基本设置 =====
                        FormSection(title = "基本设置") {
                            ReadonlyRow("卡片状态", form.status, "请选择", { showStatus = true }, border = false)
                            SwitchRow("设为默认卡", form.isDefault, { form = form.copy(isDefault = it) })
                            SwitchRow("隐藏卡片", form.isHide, { form = form.copy(isHide = it) }, border = false)
                        }

                        // ===== 外观 =====
                        FormSection(title = "外观") {
                            ColorPickerRow("卡片颜色", form.color, { form = form.copy(color = it) })
                            ReadonlyRow(
                                "卡面选择",
                                if (form.cardImg.isBlank()) "未选择" else "已选择",
                                "未选择",
                                onClick = { showImagePicker = true; if (form.cardImg.isBlank()) pickedImg = "" else pickedImg = form.cardImg; loadImages() },
                                trailing = {
                                    if (form.cardImg.isNotBlank()) {
                                        VanIcon("clear", size = 16.sp, color = colors.danger, onClick = { form = form.copy(cardImg = ""); toast("已清空卡面") })
                                    }
                                },
                                border = false,
                            )
                        }

                        // ===== 备注 =====
                        FormSection(title = "备注") {
                            InputRow("标签", form.tag, { form = form.copy(tag = it) }, "如：日常消费、出国使用")
                            InputRow("备注", form.remark, { form = form.copy(remark = it) }, "其他备注信息", border = false)
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // 保存 / 删除
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (submitting) colors.primary.copy(alpha = 0.6f) else colors.primary)
                                .clickable(enabled = !submitting) { showSaveConfirm = true },
                            contentAlignment = Alignment.Center,
                        ) { FText("保存修改", 16f, FontWeight.SemiBold, Color.White) }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.bgCard)
                                .clickable(enabled = !submitting) { showDeleteConfirm = true },
                            contentAlignment = Alignment.Center,
                        ) { FText("删除卡片", 16f, FontWeight.SemiBold, colors.danger) }
                    }
                }
            }
        }

        // ===== 弹层 =====
        BankPickerSheet(showBank, banks, { b -> form = form.copy(bankId = b.id, bankName = b.name); showBank = false }, { showBank = false })
        PickerSheet(showOrg, CARD_ORG_COLUMNS, CARD_ORG_COLUMNS.indexOf(form.cardOrg).coerceAtLeast(0), null,
            { idx -> val org = CARD_ORG_COLUMNS[idx]; form = form.copy(cardOrg = org, cardLength = (CARD_ORG_LENGTH[org] ?: 16).toString()); showOrg = false },
            { showOrg = false })
        PickerSheet(showMainSub, MAIN_SUB_COLUMNS, MAIN_SUB_COLUMNS.indexOf(form.mainSub).coerceAtLeast(0), null,
            { idx -> form = form.copy(mainSub = MAIN_SUB_COLUMNS[idx]); showMainSub = false }, { showMainSub = false })
        IndexedPickerSheet(showCurrency, CURRENCY_COLUMNS, form.currency, "选择币种",
            { v -> form = form.copy(currency = v); showCurrency = false }, { showCurrency = false })
        PickerSheet(showStatus, STATUS_COLUMNS, STATUS_COLUMNS.indexOf(form.status).coerceAtLeast(0), null,
            { idx -> form = form.copy(status = STATUS_COLUMNS[idx]); showStatus = false }, { showStatus = false })
        EditDateSheet(showOpenDate, "date", form.openDate.ifBlank { todayYmd() }.let { parseYmd(it) }, { showOpenDate = false }) { d ->
            form = form.copy(openDate = ymdOf(d)); showOpenDate = false
        }
        EditDateSheet(showExpireDate, "year-month", parseYm(form.expireDate.ifBlank { "2030-12" }), { showExpireDate = false }) { d ->
            form = form.copy(expireDate = "%04d-%02d".format(d.year, d.monthValue)); showExpireDate = false
        }

        VanNumberKeyboard(
            show = kbTarget != null,
            text = when (kbTarget) {
                "cardBin" -> form.cardBin; "last4No" -> form.last4No; "cardLength" -> form.cardLength
                "billDay" -> form.billDay; "repayDay" -> form.repayDay; "annualFee" -> form.annualFee; else -> ""
            },
            onTextChange = { v ->
                when (kbTarget) {
                    "cardBin" -> form = form.copy(cardBin = v.take(6))
                    "last4No" -> form = form.copy(last4No = v.take(4))
                    "cardLength" -> form = form.copy(cardLength = v.take(2))
                    "billDay" -> form = form.copy(billDay = v.take(2))
                    "repayDay" -> form = form.copy(repayDay = v.take(2))
                    "annualFee" -> form = form.copy(annualFee = v.take(10))
                }
            },
            theme = VanKeyboardTheme.Custom,
            closeButtonText = "完成",
            maxLength = when (kbTarget) {
                "cardBin" -> 6; "last4No" -> 4; "cardLength" -> 2; "billDay" -> 2; "repayDay" -> 2; else -> 10
            },
            onClose = {
                when (kbTarget) { "cardBin" -> validateCardBin(); "last4No" -> validateLast4(); "billDay" -> validateBillDay(); "repayDay" -> validateRepayDay() }
                kbTarget = null
            },
            onBlur = {
                when (kbTarget) { "cardBin" -> validateCardBin(); "last4No" -> validateLast4(); "billDay" -> validateBillDay(); "repayDay" -> validateRepayDay() }
                kbTarget = null
            },
        )

        // 卡面图片选择
        if (showImagePicker) {
            VanPopup(show = true, onDismissRequest = { showImagePicker = false }) {
                Column(Modifier.fillMaxWidth().height(460.dp).background(colors.bgCard)) {
                    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FText("选择卡面", 16f, FontWeight.Medium, colors.textPrimary, Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(colors.border).clickable { form = form.copy(cardImg = ""); pickedImg = "" }.padding(horizontal = 12.dp, vertical = 5.dp),
                        ) { FText("清空", 12f, color = colors.danger) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(colors.primary).clickable { form = form.copy(cardImg = pickedImg); showImagePicker = false }.padding(horizontal = 12.dp, vertical = 5.dp),
                        ) { FText("确认", 12f, FontWeight.Medium, Color.White) }
                    }
                    if (imageLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { VanLoading() }
                    } else if (images.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FText("暂无图片", 14f, color = colors.textTertiary) }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(images) { img ->
                                val selected = pickedImg == img.filePath
                                Box(Modifier.height(88.dp).clip(RoundedCornerShape(6.dp)).clickable { pickedImg = if (selected) "" else img.filePath }) {
                                    VanImage(
                                        src = AppConfig.fullFileUrl(img.filePath),
                                        modifier = Modifier.fillMaxSize(),
                                        fit = ContentScale.Crop,
                                        showLoading = false,
                                    )
                                    if (selected) {
                                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { VanIcon("success", size = 16.sp, color = colors.primary) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 确认弹窗
        VanConfirmDialog(
            show = showSaveConfirm, title = "确认保存", message = "确定要保存对这张卡片的修改吗？",
            onConfirm = { showSaveConfirm = false; doSave() }, onCancel = { showSaveConfirm = false }, onClose = { showSaveConfirm = false },
        )
        VanConfirmDialog(
            show = showDeleteConfirm, title = "删除确认", message = "确定要删除这张卡片吗？删除后无法恢复。",
            onConfirm = { showDeleteConfirm = false; doDelete() }, onCancel = { showDeleteConfirm = false }, onClose = { showDeleteConfirm = false },
        )
    }
}

/** 编辑页的日期弹层（与 Add 页同构，命名区分避免与 add 页私有件冲突）。 */
@Composable
private fun EditDateSheet(
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
            minDate = LocalDate.of(2000, 1, 1), maxDate = LocalDate.of(2050, 12, 31),
            title = "选择日期", onConfirm = { onConfirm(it) }, onCancel = onDismiss,
        )
    }
}
