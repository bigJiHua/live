package com.live.finance.ui.asset

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetDetails
import com.live.finance.data.model.AssetItem
import com.live.finance.data.model.AssetRegister
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private enum class AssetKind(val prefix: String, val title: String) {
    BALANCE("bl", "境内资产"),
    OFFSHORE("of", "境外资产"),
    DEBT("db", "负债"),
}

/**
 * 资产结构登记编辑器 —— 一个屏承载 web 两个页面：
 * - 新增/复制：web `views/Finance/assets/Register.vue`（主色、圆角浮卡、汇率弹数字键盘、三类重复确认）；
 * - 编辑：web `views/Finance/assets/Edit.vue`（GET /asset/register/:id、绿色点缀、全宽卡、
 *   汇率直接输入、金额 0 警告、仅境外重复确认）。
 *
 * @param edit true=编辑（PUT）；false=新增（POST）。
 * @param recordId 编辑目标 id。
 * @param copyFromId 新增时从某条历史复制明细（等价 web sessionStorage `editAssetData {copy:1}`）。
 */
@Composable
fun AssetEditorScreen(
    nav: NavHostController,
    edit: Boolean,
    recordId: String? = null,
    copyFromId: String? = null,
) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val repo = App.of(LocalContext.current).graph.asset
    val scope = rememberCoroutineScope()

    val balance = remember { mutableStateListOf<AssetItem>() }
    val offshore = remember { mutableStateListOf<AssetItem>() }
    val debt = remember { mutableStateListOf<AssetItem>() }
    val rates = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            AssetShared.DEFAULT_RATE_CURRENCIES.forEach { add(it to "") }
        }
    }

    var pageLoading by remember { mutableStateOf(recordId != null || copyFromId != null) }
    var submitting by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showDupConfirm by remember { mutableStateOf(false) }
    var dupName by remember { mutableStateOf("") }

    // 明细项弹层
    var popupKind by remember { mutableStateOf<AssetKind?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var fName by remember { mutableStateOf("") }
    var fType by remember { mutableStateOf("") }
    var fAmount by remember { mutableStateOf("") }
    var fRemark by remember { mutableStateOf("") }
    var showTypePicker by remember { mutableStateOf(false) }

    // 自定义汇率
    var showAddRate by remember { mutableStateOf(false) }
    var newRateCode by remember { mutableStateOf("") }
    var newRateValue by remember { mutableStateOf("") }

    // NEW 模式汇率数字键盘
    var rateKeyboardCcy by remember { mutableStateOf<String?>(null) }

    fun rateMap(): Map<String, String> = rates.associate { it }
    fun setRate(ccy: String, v: String) {
        val i = rates.indexOfFirst { it.first == ccy }
        if (i >= 0) rates[i] = ccy to v else rates.add(ccy to v)
    }

    fun details() = AssetDetails(
        balance = balance.toList(),
        offshore = offshore.toList(),
        debt = debt.toList(),
        exchangeRates = rateMap().filterValues { it.isNotEmpty() },
    )

    // ===== 预填（编辑 / 复制） =====
    LaunchedEffect(Unit) {
        val targetId = recordId ?: copyFromId ?: return@LaunchedEffect
        val r = (repo.detail(targetId) as? ApiResult.Ok)?.data
        if (r?.details != null) {
            balance.clear(); balance.addAll(r.details.balance)
            offshore.clear(); offshore.addAll(r.details.offshore)
            debt.clear(); debt.addAll(r.details.debt)
            // web：只回填默认行已存在的币种，不动态扩列（自定义币种需手动添加）
            r.details.exchangeRates.forEach { (ccy, v) ->
                if (rates.any { it.first == ccy }) setRate(ccy, v)
            }
        } else if (recordId != null) {
            toast.show("加载数据失败")
            nav.popBackStack()
            return@LaunchedEffect
        }
        pageLoading = false
    }

    // ===== 明细项弹层 =====
    fun openAdd(kind: AssetKind) {
        editingId = null
        fName = ""; fType = ""; fAmount = ""; fRemark = ""
        popupKind = kind
    }
    fun openEdit(kind: AssetKind, item: AssetItem) {
        editingId = item.id
        fName = item.customName; fType = item.type
        fAmount = if (item.amount == 0.0) "" else trimAmount(item.amount)
        fRemark = item.remark
        popupKind = kind
    }
    fun closePopup() { popupKind = null; showTypePicker = false }
    fun removeCurrent() {
        val id = editingId ?: return
        when (popupKind) {
            AssetKind.BALANCE -> balance.removeAll { it.id == id }
            AssetKind.OFFSHORE -> offshore.removeAll { it.id == id }
            AssetKind.DEBT -> debt.removeAll { it.id == id }
            null -> {}
        }
        closePopup()
        toast.show("已删除")
    }

    /**
     * 重复类型确认规则（逐字对齐两页 web）：
     * - NEW（Register）：三类「新增」时同 type 即弹确认；编辑已有项直接替换不确认；
     * - EDIT（Edit）：仅境外（新增/编辑）同 type 且该项无自定义名时确认。
     */
    fun dupConfirmNeeded(kind: AssetKind): Boolean {
        val list = when (kind) {
            AssetKind.BALANCE -> balance
            AssetKind.OFFSHORE -> offshore
            AssetKind.DEBT -> debt
        }
        return when {
            editingId != null && !edit -> false
            kind == AssetKind.OFFSHORE -> list.any { it.type == fType && it.customName.isEmpty() && it.id != editingId }
            edit -> false
            else -> list.any { it.type == fType && it.id != editingId }
        }
    }

    fun pushItem() {
        val kind = popupKind ?: return
        val amount = fAmount.toDoubleOrNull() ?: 0.0
        val list = when (kind) {
            AssetKind.BALANCE -> balance
            AssetKind.OFFSHORE -> offshore
            AssetKind.DEBT -> debt
        }
        val ccy = if (kind == AssetKind.OFFSHORE) AssetShared.offshoreCurrencyOf(fType) else "CNY"
        val item = AssetItem(
            id = editingId ?: "${kind.prefix}_${System.currentTimeMillis()}",
            type = fType, customName = fName.trim(), amount = amount,
            remark = fRemark.trim(), currency = ccy,
        )
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        closePopup()
    }

    fun commitItem() {
        val kind = popupKind ?: return
        if (fAmount.isEmpty()) { toast.show("请输入金额"); return }
        if (dupConfirmNeeded(kind)) {
            dupName = when (kind) {
                AssetKind.BALANCE -> AssetShared.balanceName(fType)
                AssetKind.OFFSHORE -> AssetShared.offshoreName(fType)
                AssetKind.DEBT -> AssetShared.debtName(fType)
            }
            showDupConfirm = true
        } else {
            pushItem()
        }
    }

    // ===== 提交 =====
    fun submit() {
        val d = details()
        val totalAsset = AssetShared.balanceTotal(d) + AssetShared.offshoreTotal(d)
        if (totalAsset == 0.0 && AssetShared.debtTotal(d) == 0.0) {
            toast.show("至少填写一项资产或负债"); return
        }
        showSubmitConfirm = true
    }
    fun doSubmit() {
        showSubmitConfirm = false
        submitting = true
        val d = details()
        val record = AssetRegister(
            id = recordId.orEmpty(),
            totalAsset = round2(AssetShared.balanceTotal(d) + AssetShared.offshoreTotal(d)),
            creditDebt = round2(AssetShared.debtTotal(d)),
            totalBalance = round2(AssetShared.netBalance(d)),
            remark = "手动资产登记",
            details = d,
        )
        scope.launch {
            val r = if (edit) repo.update(record) else repo.create(record)
            submitting = false
            when (r) {
                is ApiResult.Ok -> { toast.success("保存成功"); nav.popBackStack() }
                is ApiResult.Unauthorized -> nav.navigate(Routes.LOGIN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
                is ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
                is ApiResult.Fail -> toast.show(r.message.ifEmpty { "保存失败" })
                else -> toast.show("保存失败")
            }
        }
    }

    // ================= 渲染 =================
    val pageBg = if (edit) colors.bgPage else colors.bgCard
    val d = details()
    val balTotal = AssetShared.balanceTotal(d)
    val offTotal = AssetShared.offshoreTotal(d)
    val debtSum = AssetShared.debtTotal(d)
    val net = AssetShared.netBalance(d)

    Box(Modifier.fillMaxSize().background(pageBg)) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
            BalanceHeroCard(
                net = net, edit = edit,
                // NEW：web .balance-card 为块级（全宽）+ margin 16；EDIT：全宽贴边。均须 fillMaxWidth
                modifier = if (edit) Modifier.fillMaxWidth()
                else Modifier.fillMaxWidth().padding(16.dp),
            )

            AssetSectionCard(
                title = "境内资产", edit = edit,
                addColor = if (edit) colors.success else colors.primary,
                cardModifier = if (edit) Modifier.padding(bottom = 12.dp) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onAdd = { openAdd(AssetKind.BALANCE) },
                emptyText = "暂无登记，点击 + 添加",
                isEmpty = balance.isEmpty(),
                totalLeft = "境内资产小计",
                totalRight = "¥${Money.format(balTotal)}",
                totalRightColor = colors.textPrimary,
            ) {
                balance.forEach { item ->
                    EditorItemRow(
                        name = item.customName.ifEmpty { AssetShared.balanceName(item.type) },
                        remark = item.remark.takeIf { it.isNotEmpty() },
                        amountText = "¥${Money.format(item.amount)}",
                        amountColor = colors.textPrimary,
                        zeroWarn = edit && item.amount == 0.0,
                        edit = edit,
                        onClick = { openEdit(AssetKind.BALANCE, item) },
                    )
                }
            }

            AssetSectionCard(
                title = "境外资产", edit = edit,
                addColor = if (edit) colors.success else colors.primary,
                cardModifier = if (edit) Modifier.padding(bottom = 12.dp) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onAdd = { openAdd(AssetKind.OFFSHORE) },
                emptyText = "暂无登记，点击 + 添加",
                isEmpty = offshore.isEmpty(),
                totalLeft = if (edit) "境外资产小计" else "境外资产折合（CNY）",
                totalRight = (if (edit) "≈ ¥" else "¥") + Money.format(offTotal),
                totalRightColor = colors.textPrimary,
                topContent = {
                    RateBlock(
                        edit = edit,
                        rates = rates,
                        onRateClick = { ccy -> if (!edit) rateKeyboardCcy = ccy },
                        onRateChange = { ccy, v -> if (edit) setRate(ccy, AssetShared.filterRate(v)) },
                        onDelete = { ccy ->
                            if (ccy == "HKD") { toast.show("HKD不可删除"); return@RateBlock }
                            if (offshore.any { it.currency == ccy && it.amount > 0 }) {
                                toast.show("该汇率已被使用，无法删除"); return@RateBlock
                            }
                            rates.removeAll { it.first == ccy }
                        },
                        onAdd = { showAddRate = true },
                    )
                },
            ) {
                offshore.forEach { item ->
                    val valid = AssetShared.hasRate(rateMap(), item.currency)
                    val cny = AssetShared.toCny(item.amount, item.currency, rateMap())
                    EditorItemRow(
                        name = item.customName.ifEmpty { AssetShared.offshoreName(item.type) },
                        currencyLine = "${item.currency} ${trimAmount(item.amount)}",
                        convertText = if (valid) "≈ ¥${Money.format(cny)}" else null,
                        convertColor = if (edit) colors.success else colors.primary,
                        amountText = if (valid) "¥${Money.format(cny)}" else "${item.currency} ${trimAmount(item.amount)}",
                        amountColor = if (valid) colors.danger else colors.textPrimary,
                        zeroWarn = edit && item.amount == 0.0,
                        edit = edit,
                        onClick = { openEdit(AssetKind.OFFSHORE, item) },
                    )
                }
            }

            AssetSectionCard(
                title = if (edit) "负债" else "信用卡 / 负债", edit = edit,
                addColor = if (edit) colors.success else colors.primary,
                cardModifier = if (edit) Modifier.padding(bottom = 12.dp) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onAdd = { openAdd(AssetKind.DEBT) },
                emptyText = "暂无负债，点击 + 添加",
                isEmpty = debt.isEmpty(),
                totalLeft = if (edit) "负债小计" else "负债合计",
                totalRight = "¥${Money.format(debtSum)}",
                totalRightColor = colors.danger,
            ) {
                debt.forEach { item ->
                    EditorItemRow(
                        name = item.customName.ifEmpty { AssetShared.debtName(item.type) },
                        remark = item.remark.takeIf { it.isNotEmpty() },
                        amountText = "¥${Money.format(item.amount)}",
                        amountColor = colors.danger,
                        zeroWarn = edit && item.amount == 0.0,
                        edit = edit,
                        onClick = { openEdit(AssetKind.DEBT, item) },
                    )
                }
            }
        }

        // 底部固定保存条
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(pageBg).padding(16.dp),
        ) {
            AppButton(
                text = "确认保存资产登记",
                onClick = { submit() },
                type = AppButtonType.Primary,
                size = AppButtonSize.Large,
                block = true, round = true, loading = submitting || pageLoading,
            )
        }

        if (pageLoading) {
            Box(Modifier.fillMaxSize().background(pageBg), contentAlignment = Alignment.Center) {
                FText("加载中…", 14f, color = colors.textTertiary)
            }
        }
    }

    // ========== 明细项编辑弹层 ==========
    val kind = popupKind
    if (kind != null) {
        VanPopup(show = true, onDismissRequest = { closePopup() },
            position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    FText((if (editingId != null) "编辑" else "添加") + kind.title,
                        17f, FontWeight.SemiBold, colors.textPrimary)
                    VanIcon("cross", size = 20.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { closePopup() })
                }
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(value = fName, onValueChange = { fName = it },
                        label = "自定义名称", placeholder = "选填")
                    TypeSelectRow(
                        label = if (kind == AssetKind.OFFSHORE) "账户类型" else "类型",
                        value = when (kind) {
                            AssetKind.BALANCE -> AssetShared.balanceName(fType)
                            AssetKind.OFFSHORE -> AssetShared.offshoreName(fType)
                            AssetKind.DEBT -> AssetShared.debtName(fType)
                        },
                        hasValue = fType.isNotEmpty(),
                        onClick = { showTypePicker = true },
                    )
                    AppField(
                        value = fAmount,
                        onValueChange = { fAmount = AssetShared.filterAmount(it) },
                        label = if (kind == AssetKind.DEBT) "欠款金额" else "金额",
                        placeholder = when (kind) {
                            AssetKind.OFFSHORE -> "输入外币金额"
                            AssetKind.BALANCE -> if (edit) "输入人民币金额" else "请输入金额"
                            AssetKind.DEBT -> "请输入金额"
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    if (kind == AssetKind.OFFSHORE) {
                        AppField(value = AssetShared.offshoreCurrencyOf(fType), onValueChange = {},
                            label = "币种", placeholder = "自动带出")
                    } else {
                        AppField(value = fRemark, onValueChange = { fRemark = it.take(50) },
                            label = "备注", placeholder = "选填")
                    }
                    if (editingId != null) {
                        AppButton(
                            text = if (edit) "删除" else "删除此项",
                            onClick = { removeCurrent() },
                            type = AppButtonType.Danger,
                            size = if (edit) AppButtonSize.Normal else AppButtonSize.Small,
                            block = true, round = edit,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (edit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppButton("取消", { closePopup() }, block = true, round = true,
                            modifier = Modifier.weight(1f))
                        AppButton("确定", { commitItem() }, type = AppButtonType.Primary,
                            block = true, round = true, modifier = Modifier.weight(1f))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.weight(1f))
                        AppButton("取消", { closePopup() }, size = AppButtonSize.Small)
                        AppButton("保存", { commitItem() }, type = AppButtonType.Primary,
                            size = AppButtonSize.Small)
                    }
                }
            }
        }

        if (showTypePicker) {
            val options: List<Pair<String, String>> = when (kind) {
                AssetKind.BALANCE -> AssetShared.BALANCE_TYPES
                AssetKind.DEBT -> AssetShared.DEBT_TYPES
                AssetKind.OFFSHORE -> AssetShared.offshoreTypes(rateMap())
            }
            VanPopup(show = true, onDismissRequest = { showTypePicker = false },
                position = VanPopupPosition.Bottom) {
                VanPicker(
                    columns = listOf(options.map { VanPickerOption(it.second, it) }),
                    title = when (kind) {
                        AssetKind.OFFSHORE -> "选择境外账户"
                        AssetKind.DEBT -> "选择负债类型"
                        AssetKind.BALANCE -> "选择类型"
                    },
                    onConfirm = { _, vals ->
                        @Suppress("UNCHECKED_CAST")
                        (vals[0] as? Pair<String, String>)?.let { fType = it.first }
                        showTypePicker = false
                    },
                    onCancel = { showTypePicker = false },
                )
            }
        }
    }

    // NEW 模式：汇率数字键盘（readonly 行点击弹起，对齐 Register.vue）
    if (!edit && rateKeyboardCcy != null) {
        val ccy = rateKeyboardCcy!!
        VanNumberKeyboard(
            show = true,
            text = rates.firstOrNull { it.first == ccy }?.second.orEmpty(),
            onTextChange = { setRate(ccy, AssetShared.filterRate(it)) },
            theme = VanKeyboardTheme.Custom,
            extraKey = listOf("."),
            closeButtonText = "完成",
            maxLength = 9,
            onClose = { rateKeyboardCcy = null },
            onBlur = { rateKeyboardCcy = null },
        )
    }

    // 添加常用汇率
    if (showAddRate) {
        VanPopup(show = true, onDismissRequest = { showAddRate = false },
            position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                FText("添加常用汇率", 17f, FontWeight.SemiBold, colors.textPrimary)
                Spacer(Modifier.height(20.dp))
                AppField(value = newRateCode,
                    onValueChange = { newRateCode = AssetShared.filterCurrencyCode(it) },
                    label = "币种代码", placeholder = "如：KRW")
                AppField(value = newRateValue,
                    onValueChange = { newRateValue = AssetShared.filterRate(it) },
                    label = "汇率", placeholder = "100外币 = ? CNY",
                    keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton("取消", { showAddRate = false }, block = true, round = true,
                        modifier = Modifier.weight(1f))
                    AppButton("确定", {
                        if (newRateCode.isEmpty() || newRateValue.isEmpty()) {
                            toast.show("请输入币种代码和汇率"); return@AppButton
                        }
                        setRate(newRateCode, newRateValue)
                        newRateCode = ""; newRateValue = ""
                        showAddRate = false
                    }, type = AppButtonType.Primary, block = true, round = true,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // 提交确认
    VanConfirmDialog(
        show = showSubmitConfirm,
        title = "确认提交",
        message = if (edit) "确定要更新这条资产登记记录吗？" else "确定要提交这条资产登记记录吗？",
        onConfirm = { doSubmit() },
        onCancel = { showSubmitConfirm = false },
        onClose = { showSubmitConfirm = false },
    )
    // 重复类型确认
    VanConfirmDialog(
        show = showDupConfirm,
        title = "确认添加",
        message = "已存在「${dupName}」类型，是否继续添加？",
        onConfirm = { showDupConfirm = false; pushItem() },
        onCancel = { showDupConfirm = false },
        onClose = { showDupConfirm = false },
    )
}

// ================= 子组件 =================

@Composable
private fun BalanceHeroCard(net: Double, edit: Boolean, modifier: Modifier) {
    val tokens = LocalAppTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(if (edit) 0.dp else 20.dp))
            .background(Brush.linearGradient(listOf(tokens.primary, tokens.grad)))
            .padding(horizontal = 20.dp, vertical = if (edit) 24.dp else 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FText("最终净资产", if (edit) 14f else 15f, FontWeight.Normal, Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            FText("¥", if (edit) 20f else 22f, FontWeight.SemiBold, Color.White,
                modifier = Modifier.padding(end = 6.dp))
            FText(Money.format(net), if (edit) 32f else 40f, FontWeight.Bold, Color.White)
        }
    }
}

@Composable
private fun AssetSectionCard(
    title: String,
    edit: Boolean,
    addColor: Color,
    cardModifier: Modifier,
    onAdd: () -> Unit,
    emptyText: String,
    isEmpty: Boolean,
    totalLeft: String,
    totalRight: String,
    totalRightColor: Color,
    topContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(cardModifier.fillMaxWidth()
        .clip(RoundedCornerShape(if (edit) 0.dp else 16.dp))
        .background(colors.bgCard)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(title, 16f, FontWeight.SemiBold, colors.textPrimary)
            VanIcon("plus", size = if (edit) 20.sp else 19.sp, color = addColor,
                modifier = Modifier.clickable { onAdd() })
        }
        if (!edit) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        topContent?.invoke()
        Column(Modifier.then(if (edit) Modifier.padding(horizontal = 16.dp) else Modifier.padding(16.dp))) {
            if (isEmpty) {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    FText(emptyText, 13f, color = colors.textTertiary)
                }
            } else {
                content()
            }
        }
        // 小计：NEW 是通栏三级底色块；EDIT 是内容列内顶线行
        if (edit) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    FText(totalLeft, 14f, color = colors.textSecondary)
                    FText(totalRight, 14f, FontWeight.SemiBold, totalRightColor)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(colors.bgThird).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText(totalLeft, 14f, FontWeight.Medium, colors.textSecondary)
                FText(totalRight, 14f, FontWeight.Bold, totalRightColor)
            }
        }
    }
}

@Composable
private fun EditorItemRow(
    name: String,
    remark: String? = null,
    currencyLine: String? = null,
    convertText: String? = null,
    convertColor: Color = Color.Unspecified,
    amountText: String,
    amountColor: Color,
    zeroWarn: Boolean,
    edit: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = if (edit) 12.dp else 14.dp)
            .then(
                if (edit) Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bgThird).padding(12.dp)
                else Modifier
            )
            .clickable { onClick() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText(name, 15f, FontWeight.Medium, colors.textPrimary)
                if (!remark.isNullOrEmpty() || !currencyLine.isNullOrEmpty()) {
                    Spacer(Modifier.height(if (edit) 4.dp else 3.dp))
                    Row {
                        if (!remark.isNullOrEmpty()) {
                            FText(remark, 12f, color = colors.textTertiary)
                        } else if (!currencyLine.isNullOrEmpty()) {
                            FText(currencyLine, 12f, color = colors.textSecondary)
                            if (!convertText.isNullOrEmpty()) {
                                FText("  $convertText", 12f, FontWeight.Medium, convertColor)
                            }
                        }
                    }
                }
            }
            FText(amountText, 15f, FontWeight.SemiBold, amountColor)
        }
        if (zeroWarn) {
            Spacer(Modifier.height(4.dp))
            FText("该项数据金额为0 请检查", 12f, color = colors.dangerText)
        }
    }
}

@Composable
private fun RateBlock(
    edit: Boolean,
    rates: List<Pair<String, String>>,
    onRateClick: (String) -> Unit,
    onRateChange: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = LocalAppColors.current
    // NEW：通栏三级底，web `padding:12px 16px`；EDIT：卡内圆角块（横向 16 外边距 + 12 内边距）
    Column(
        Modifier.fillMaxWidth()
            .then(if (edit) Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp) else Modifier)
            .clip(RoundedCornerShape(if (edit) 8.dp else 0.dp))
            .background(colors.bgThird)
            .padding(
                start = if (edit) 12.dp else 16.dp,
                end = if (edit) 12.dp else 16.dp,
                top = 12.dp, bottom = 12.dp,
            ),
    ) {
        FText("实时汇率（100外币 = ? 人民币）", 12f, color = colors.textSecondary,
            modifier = Modifier.padding(bottom = if (edit) 12.dp else 8.dp))
        val rows = rates.chunked(2)
        rows.forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (ccy, v) ->
                    RateCell(ccy, v, edit,
                        onClick = { onRateClick(ccy) },
                        onChange = { nv -> onRateChange(ccy, nv) },
                        onDelete = { onDelete(ccy) },
                        Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onAdd() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) { VanIcon("plus", size = 16.sp, color = colors.textSecondary) }
    }
}

@Composable
private fun RateCell(
    ccy: String,
    value: String,
    edit: Boolean,
    onClick: () -> Unit,
    onChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FText(ccy, 13f, color = if (edit) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.width(40.dp))
        if (edit) {
            Box(Modifier.weight(1f)) {
                AppField(value = value, onValueChange = onChange, placeholder = "0.0000",
                    keyboardType = KeyboardType.Number, border = false)
            }
        } else {
            Box(
                Modifier.weight(1f).height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClick() }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                FText(value.ifEmpty { "0.0000" }, 14f,
                    color = if (value.isEmpty()) colors.textTertiary else colors.textPrimary)
            }
        }
        if (ccy != "HKD") {
            VanIcon("cross", size = 14.sp, color = colors.textTertiary,
                modifier = Modifier.clickable { onDelete() })
        }
    }
}

@Composable
private fun TypeSelectRow(label: String, value: String, hasValue: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().background(colors.bgCard)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 13f, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(value.ifEmpty { "选择类型" }, 14f,
                color = if (hasValue) colors.textPrimary else colors.textTertiary,
                modifier = Modifier.weight(1f))
            VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

private fun trimAmount(v: Double): String {
    val r = Math.round(v * 1000.0) / 1000.0
    return if (r == r.toLong().toDouble()) r.toLong().toString()
    else String.format(java.util.Locale.US, "%.3f", r).trimEnd('0').trimEnd('.')
}

private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
