package com.live.finance.ui.bankcard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Category
import com.live.finance.data.repo.NewCard
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.form.VanSwitch
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun CardEditScreen(nav: NavHostController, id: String = "") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val isEdit = id.isNotBlank()

    var cardType by remember { mutableStateOf("debit") }
    var bankId by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var showBank by remember { mutableStateOf(false) }
    var last4 by remember { mutableStateOf("") }
    var cardBin by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var cardOrg by remember { mutableStateOf("银联") }
    var cardLevel by remember { mutableStateOf("普卡") }
    var annualFee by remember { mutableStateOf("") }
    var feeFreeRule by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(false) }
    var creditLimit by remember { mutableStateOf("") }
    var billDay by remember { mutableStateOf("") }
    var repayDay by remember { mutableStateOf("") }
    var openDate by remember { mutableStateOf(fmt(Calendar.getInstance())) }
    var expireDate by remember { mutableStateOf(fmt(Calendar.getInstance().apply { add(Calendar.YEAR, 3) })) }
    var submitting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (graph.category.list("bank") as? ApiResult.Ok)?.let { banks = it.data ?: emptyList() }
        if (isEdit) (graph.card.detail(id) as? ApiResult.Ok)?.data?.let { c ->
            cardType = c.cardType; bankId = c.bankId; bankName = c.bankName; last4 = c.last4; cardBin = c.cardBin
            alias = c.alias; isDefault = c.isDefault; openDate = c.openDate.ifBlank { openDate }
            expireDate = c.expireDate.ifBlank { expireDate }; billDay = if (c.billDay > 0) c.billDay.toString() else ""
            repayDay = if (c.repayDay > 0) c.repayDay.toString() else ""; creditLimit = if (c.creditLimit > 0) c.creditLimit.toString() else ""
        }
    }

    val bankLabel = banks.firstOrNull { it.id == bankId }?.name ?: bankName

    ScreenScaffold { mod ->
        Column(mod.verticalScroll(rememberScrollState()).padding(16.dp)) {
            FText(if (isEdit) "编辑卡片" else "新增卡片", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                VanButton(text = "储蓄卡", type = if (cardType == "debit") VanButtonType.Primary else VanButtonType.Default,
                    modifier = Modifier.weight(1f), onClick = { cardType = "debit" })
                Spacer(Modifier.width(12.dp))
                VanButton(text = "信用卡", type = if (cardType == "credit") VanButtonType.Primary else VanButtonType.Default,
                    modifier = Modifier.weight(1f), onClick = { cardType = "credit" })
            }
            Spacer(Modifier.height(16.dp))
            VanField(value = bankLabel, onValueChange = {}, label = "银行", placeholder = "选择银行", readonly = true, onClick = { showBank = true })
            VanField(value = last4, onValueChange = { last4 = it.filter { c -> c.isDigit() }.take(4) }, label = "尾号", placeholder = "后四位", type = VanFieldType.Number, maxlength = 4)
            VanField(value = cardBin, onValueChange = { cardBin = it.filter { c -> c.isDigit() }.take(6) }, label = "卡BIN", placeholder = "前六位", type = VanFieldType.Number, maxlength = 6)
            VanField(value = alias, onValueChange = { alias = it }, label = "别名", placeholder = "选填")
            VanField(value = cardOrg, onValueChange = { cardOrg = it }, label = "卡组织", placeholder = "银联/VISA…")
            VanField(value = cardLevel, onValueChange = { cardLevel = it }, label = "卡等级", placeholder = "普卡/金卡/白金卡")
            if (cardType == "credit") {
                VanField(value = creditLimit, onValueChange = { creditLimit = it }, label = "额度", placeholder = "信用额度", type = VanFieldType.Number)
                VanField(value = billDay, onValueChange = { billDay = it.filter { c -> c.isDigit() }.take(2) }, label = "账单日", placeholder = "1-31", type = VanFieldType.Number)
                VanField(value = repayDay, onValueChange = { repayDay = it.filter { c -> c.isDigit() }.take(2) }, label = "还款日", placeholder = "1-31", type = VanFieldType.Number)
                VanField(value = annualFee, onValueChange = { annualFee = it }, label = "年费", placeholder = "选填", type = VanFieldType.Number)
                VanField(value = feeFreeRule, onValueChange = { feeFreeRule = it }, label = "免年费", placeholder = "刷卡满6次免年费")
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                FText("设为默认卡", 15f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
                VanSwitch(checked = isDefault, onCheckedChange = { isDefault = it })
            }
            Spacer(Modifier.height(24.dp))
            VanButton(text = "保存", type = VanButtonType.Primary, block = true, loading = submitting, onClick = {                when {
                    bankId.isBlank() -> toast.show("请选择银行")
                    last4.length != 4 -> toast.show("请输入卡号后四位")
                    cardBin.length != 6 -> toast.show("请输入卡BIN前六位")
                    cardType == "credit" && (billDay.isBlank() || repayDay.isBlank()) -> toast.show("信用卡需填写账单日与还款日")
                    else -> {
                        submitting = true
                        scope.launch {
                            val p = NewCard(
                                cardType = cardType, bankId = bankId, last4No = last4, cardBin = cardBin,
                                openDate = openDate, expireDate = expireDate, alias = alias.trim(), isDefault = isDefault,
                                cardOrg = cardOrg.trim(), cardLevel = cardLevel.trim(),
                                creditLimit = creditLimit.toDoubleOrNull() ?: 0.0,
                                annualFee = annualFee.toDoubleOrNull() ?: 0.0, feeFreeRule = feeFreeRule.trim(),
                                billDay = billDay.toIntOrNull() ?: 0, repayDay = repayDay.toIntOrNull() ?: 0,
                            )
                            val r = if (isEdit) graph.card.update(id, p) else graph.card.createNew(p)
                            submitting = false
                            when (r) {
                                is ApiResult.Ok -> { toast.success("已保存"); nav.popBackStack() }
                                is ApiResult.Fail -> toast.show(r.message)
                                else -> toast.show("保存失败")
                            }
                        }
                    }
                }
            })
            if (isEdit) {
                Spacer(Modifier.height(12.dp))
                VanButton(text = "删除该卡片", type = VanButtonType.Danger, block = true, onClick = { showDeleteConfirm = true })
            }
        }
    }

    VanPopup(show = showBank, onDismissRequest = { showBank = false }) {
        VanPicker(
            columns = listOf(banks.map { VanPickerOption(it.name, it.id) }),
            value = listOf(banks.indexOfFirst { it.id == bankId }.coerceAtLeast(0)),
            title = "选择银行",
            onConfirm = { idx, _ -> val b = banks.getOrNull(idx.firstOrNull() ?: 0); b?.let { bankId = it.id; bankName = it.name }; showBank = false },
            onCancel = { showBank = false },
        )
    }

    com.live.vant.feedback.VanConfirmDialog(
        show = showDeleteConfirm,
        title = "删除卡片",
        message = "确定删除该卡片？删除后关联流水需注意。",
        onConfirm = {
            showDeleteConfirm = false
            scope.launch {
                when (val rr = graph.card.delete(id)) {
                    is ApiResult.Ok -> { toast.success("已删除"); nav.popBackStack() }
                    is ApiResult.NeedPin -> toast.show("删除需先验证 PIN")
                    is ApiResult.Fail -> toast.show(rr.message)
                    else -> toast.show("删除失败")
                }
            }
        },
        onCancel = { showDeleteConfirm = false },
        onClose = { showDeleteConfirm = false },
    )
}

private fun fmt(c: Calendar): String =
    String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
