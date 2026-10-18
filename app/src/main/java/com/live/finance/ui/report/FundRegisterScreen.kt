package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Fund
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.form.VanDatePicker
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/** 理财登记（对齐 web Finance/report/fund/FundRegister.vue）：顶栏列表 + 底部 80% 弹层表单。 */
@Composable
fun FundRegisterScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val toast = rememberVanToastController()

    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }

    var showEditor by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showOptionalInfo by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Fund?>(null) }

    var formName by remember { mutableStateOf("") }
    var formCompany by remember { mutableStateOf("") }
    var formSellOrg by remember { mutableStateOf("") }
    var formAccount by remember { mutableStateOf("") }
    var formShare by remember { mutableStateOf("") }
    var formInvest by remember { mutableStateOf("") }
    var formBuyDate by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun openEditor(f: Fund?) {
        editingId = f?.id
        showOptionalInfo = f != null
        formName = f?.fundName ?: ""
        formCompany = f?.fundCompany ?: ""
        formSellOrg = f?.sellOrg ?: ""
        formAccount = f?.fundAccount ?: ""
        formShare = f?.let { trimDecimal(it.share) } ?: ""
        formInvest = f?.let { trimDecimal(if (it.baseInvest > 0) it.baseInvest else it.invest) } ?: ""
        formBuyDate = f?.buyDate ?: ""
        showEditor = true
    }

    fun reload() {
        scope.launch {
            loading = true
            when (val res = graph.fund.list()) {
                is ApiResult.Ok -> fundList = res.data ?: emptyList()
                // web `loadList` 的 catch → showToast("加载失败")
                else -> toast.showFail(res, "加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun save() {
        // web app-field 校验规则：基金名称 / 购入日期 / 持有份额 / 初始本金
        if (formName.isBlank()) { toast.show("请输入基金名称"); return }
        if (formBuyDate.isBlank()) { toast.show("请选择购入日期"); return }
        if (formShare.isBlank()) { toast.show("请输入持有份额"); return }
        if ((formShare.toDoubleOrNull() ?: 0.0) <= 0.0) { toast.show("份额必须大于0"); return }
        if (formInvest.isBlank()) { toast.show("请输入初始本金"); return }
        if ((formInvest.toDoubleOrNull() ?: 0.0) <= 0.0) { toast.show("本金必须大于0"); return }

        val invest = formInvest.toDoubleOrNull() ?: 0.0
        val body = JsonObject().apply {
            addProperty("fundName", formName.trim())
            addProperty("fundCompany", formCompany.trim())
            addProperty("sellOrg", formSellOrg.trim())
            addProperty("fundAccount", formAccount.trim())
            addProperty("share", formShare)
            addProperty("netValue", "0")
            addProperty("marketVal", String.format("%.2f", invest))
            addProperty("invest", formInvest)
            addProperty("buyDate", formBuyDate)
            addProperty("rate", "0.00%")
        }
        val id = editingId
        scope.launch {
            saving = true
            val res = if (id != null) graph.fund.update(id, body) else graph.fund.create(body)
            when (res) {
                is ApiResult.Ok -> {
                    toast.show(if (id != null) "修改成功" else "添加成功")
                    showEditor = false
                    reload()
                }
                is ApiResult.Fail -> toast.show(res.message.ifBlank { if (id != null) "修改失败" else "添加失败" }, VanToastType.Fail)
                else -> toast.show(if (id != null) "修改失败" else "添加失败")
            }
            saving = false
        }
    }

    fun doDelete(f: Fund) {
        scope.launch {
            when (graph.fund.delete(f.id)) {
                is ApiResult.Ok -> { toast.show("删除成功", VanToastType.Success); reload() }
                else -> toast.show("删除失败", VanToastType.Fail)
            }
        }
    }

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { com.live.vant.basic.VanLoading() }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FText("基金列表", 18f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
                        AppButton(
                            "新增基金", onClick = { openEditor(null) },
                            type = AppButtonType.Primary, size = AppButtonSize.Small, round = true, icon = "plus",
                        )
                    }
                    if (fundList.isEmpty()) {
                        com.live.vant.basic.VanEmpty(description = "暂无基金数据")
                    } else {
                        fundList.forEach { f ->
                            FundItemRow(f, onEdit = { openEditor(f) }, onDelete = { deleteTarget = f })
                        }
                    }
                }
            }
        }
    }

    // 编辑弹层（底部 80% 高度）
    if (showEditor) {
        VanPopup(
            show = true, onDismissRequest = { showEditor = false },
            position = VanPopupPosition.Bottom, round = true,
            modifier = Modifier.fillMaxHeight(0.8f),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 30.dp),
            ) {
                FText(
                    if (editingId != null) "编辑基金" else "新增基金",
                    18f, FontWeight.SemiBold, colors.textPrimary,
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                AppField(formName, { formName = it }, label = "基金名称", placeholder = "如：易方达蓝筹精选")
                AppCell(
                    title = "购入日期",
                    value = formBuyDate.ifBlank { "请选择日期" },
                    isLink = true,
                    valuePlaceholder = formBuyDate.isBlank(),
                    onClick = { showDatePicker = true },
                )
                AppField(formShare, { formShare = it }, label = "持有份额", placeholder = "0.00", keyboardType = KeyboardType.Number)
                AppField(formInvest, { formInvest = it }, label = "初始本金", placeholder = "0.00", keyboardType = KeyboardType.Number)
                AppCell(
                    title = "补充信息",
                    value = if (showOptionalInfo) "收起" else "选填",
                    isLink = true,
                    modifier = Modifier.padding(top = 6.dp),
                    onClick = { showOptionalInfo = !showOptionalInfo },
                    rightIcon = {
                        VanIcon(if (showOptionalInfo) "arrow-up" else "arrow-down", size = 14.sp, color = colors.textTertiary)
                    },
                )
                if (showOptionalInfo) {
                    AppField(formCompany, { formCompany = it }, label = "基金公司", placeholder = "选填，如：易方达基金")
                    AppField(formSellOrg, { formSellOrg = it }, label = "销售机构", placeholder = "选填，如：支付宝")
                    AppField(formAccount, { formAccount = it }, label = "基金账号", placeholder = "选填，尾号6位", maxlength = 6)
                }
                Spacer(Modifier.height(20.dp))
                AppButton(
                    if (editingId != null) "保存修改" else "确认添加",
                    onClick = { save() },
                    type = AppButtonType.Primary, block = true, round = true, loading = saving,
                )
                Spacer(Modifier.height(8.dp))
                AppButton("取消", onClick = { showEditor = false }, plain = true, block = true, round = true)
            }
        }
    }

    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }, position = VanPopupPosition.Bottom, round = true) {
            // ⚠ `VanDatePicker` 是**受控**组件：空的 `onValueChange` 会让滚轮状态写不回（同 `RepayAddScreen` 的 BUG）。
            var pendingBuy by remember {
                mutableStateOf(
                    if (formBuyDate.isNotBlank()) runCatching { LocalDate.parse(formBuyDate) }.getOrDefault(LocalDate.now()) else LocalDate.now()
                )
            }
            VanDatePicker(
                type = "date",
                value = pendingBuy,
                onValueChange = { pendingBuy = it },
                title = "选择购入日期",
                onConfirm = { formBuyDate = it.toString(); showDatePicker = false },
                onCancel = { showDatePicker = false },
            )
        }
    }

    deleteTarget?.let { target ->
        VanConfirmDialog(
            show = true,
            title = "确认删除",
            message = "确定删除「${target.fundName}」？",
            onConfirm = { deleteTarget = null; doDelete(target) },
            onCancel = { deleteTarget = null },
            onClose = { deleteTarget = null },
        )
    }
}

@Composable
private fun FundItemRow(f: Fund, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    val profit = f.netValue
    val profitColor = if (profit >= 0) colors.danger else colors.success
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(f.fundName.ifEmpty { "未命名" }, 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                VanIcon("edit", size = 18.sp, color = colors.primary, onClick = { onEdit() })
                VanIcon("delete", size = 18.sp, color = colors.danger, onClick = { onDelete() })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("份额 ${trimDecimal(f.share)}", 12f, FontWeight.Normal, colors.textPrimary)
            FText("收益 ${String.format("%.2f", profit)}", 12f, FontWeight.Normal, profitColor)
            FText("市值 ${fundRegAmount(f.marketVal)}", 12f, FontWeight.Normal, colors.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (f.fundCompany.isNotBlank()) FText(f.fundCompany, 12f, FontWeight.Normal, colors.textTertiary)
            FText("购入 ${f.buyDate}", 12f, FontWeight.Normal, colors.textTertiary)
        }
    }
}

/** 对齐 web FundRegister.vue 局部 formatAmount：>=10000 用「万」两位小数，否则两位小数。 */
private fun fundRegAmount(v: Double): String {
    if (!v.isFinite()) return "0.00"
    return if (v >= 10000) String.format("%.2f万", v / 10000) else String.format("%.2f", v)
}