package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
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
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.form.VanDatePicker
import com.live.vant.nav.VanNavBar
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun FundRegisterScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val toast = rememberVanToastController()

    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }

    var editId by remember { mutableStateOf<String?>(null) }
    var formName by remember { mutableStateOf("") }
    var formCompany by remember { mutableStateOf("") }
    var formSellOrg by remember { mutableStateOf("") }
    var formAccount by remember { mutableStateOf("") }
    var formShare by remember { mutableStateOf("") }
    var formInvest by remember { mutableStateOf("") }
    var formBuyDate by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun resetForm() {
        editId = null
        formName = ""
        formCompany = ""
        formSellOrg = ""
        formAccount = ""
        formShare = ""
        formInvest = ""
        formBuyDate = ""
    }

    fun selectFund(f: Fund) {
        editId = f.id
        formName = f.fundName
        formCompany = f.fundCompany
        formSellOrg = f.sellOrg
        formAccount = f.fundAccount
        formShare = String.format("%.2f", f.share)
        formInvest = if (f.baseInvest > 0) String.format("%.2f", f.baseInvest) else String.format("%.2f", f.invest)
        formBuyDate = f.buyDate
    }

    fun reload() {
        scope.launch {
            loading = true
            when (val res = graph.fund.list()) {
                is ApiResult.Ok -> fundList = res.data ?: emptyList()
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun save() {
        if (formName.isBlank()) { toast.show("基金名称不能为空"); return }
        val body = JsonObject().apply {
            addProperty("fundName", formName.trim())
            addProperty("fundCompany", formCompany.trim())
            addProperty("sellOrg", formSellOrg.trim())
            addProperty("fundAccount", formAccount.trim())
            addProperty("share", formShare.toDoubleOrNull() ?: 0.0)
            addProperty("invest", formInvest.toDoubleOrNull() ?: 0.0)
            addProperty("netValue", "0")
            addProperty("marketVal", (formInvest.toDoubleOrNull() ?: 0.0))
            addProperty("rate", "0.00%")
            addProperty("buyDate", formBuyDate.takeIf { it.isNotBlank() } ?: LocalDate.now().toString())
        }
        scope.launch {
            saving = true
            val res = if (editId == null) graph.fund.create(body) else graph.fund.update(editId!!, body)
            when (res) {
                is ApiResult.Ok -> {
                    toast.show(if (editId == null) "新增成功" else "保存成功")
                    resetForm()
                    reload()
                }
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("操作失败")
            }
            saving = false
        }
    }

    fun delete() {
        val id = editId ?: return
        scope.launch {
            when (val res = graph.fund.delete(id)) {
                is ApiResult.Ok -> { toast.show("删除成功"); resetForm(); reload() }
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("操作失败")
            }
        }
    }

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            VanNavBar(title = "理财登记", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { com.live.vant.basic.VanLoading() }
            } else {
                // 基金选择（chip）
                FText("选择基金", 14f, FontWeight.SemiBold, colors.textPrimary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    fundList.forEach { f ->
                        val selected = f.id == editId
                        val label = f.fundName.ifEmpty { "未命名" }
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (selected) colors.primary else colors.bgThird)
                                .clickable { selectFund(f) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            FText(label, 13f, FontWeight.Medium, if (selected) colors.buttonPrimaryText else colors.textPrimary)
                        }
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (editId == null) colors.primary else colors.bgThird)
                            .clickable { resetForm() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        FText("＋ 新增", 13f, FontWeight.Medium, if (editId == null) colors.buttonPrimaryText else colors.textPrimary)
                    }
                }

                // 表单
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppField(formName, { formName = it }, label = "基金名称", placeholder = "请输入基金名称")
                    AppField(formCompany, { formCompany = it }, label = "基金公司", placeholder = "如：易方达基金")
                    AppField(formSellOrg, { formSellOrg = it }, label = "销售机构", placeholder = "如：天天基金 / 支付宝")
                    AppField(formAccount, { formAccount = it }, label = "交易账号", placeholder = "选填")
                    AppField(formShare, { formShare = it }, label = "持有份额", placeholder = "0.00", keyboardType = KeyboardType.Number)
                    AppField(formInvest, { formInvest = it }, label = "初始本金", placeholder = "0.00", keyboardType = KeyboardType.Number)
                    AppCell(
                        title = "买入日期",
                        value = if (formBuyDate.isBlank()) "请选择" else formBuyDate,
                        isLink = true,
                        onClick = { showDatePicker = true },
                    )
                }

                AppButton(
                    if (editId == null) "新增基金" else "保存修改",
                    onClick = { save() }, size = AppButtonSize.Large, type = AppButtonType.Primary, block = true,
                )

                if (editId != null) {
                    AppButton(
                        "删除该基金",
                        onClick = { showDeleteConfirm = true }, size = AppButtonSize.Large, type = AppButtonType.Danger, block = true,
                    )
                }
            }
        }
        }
    }

    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            VanDatePicker(
                type = "date",
                value = if (formBuyDate.isNotBlank()) runCatching { LocalDate.parse(formBuyDate) }.getOrDefault(LocalDate.now()) else LocalDate.now(),
                onValueChange = {},
                onConfirm = { formBuyDate = it.toString(); showDatePicker = false },
                onCancel = { showDatePicker = false },
            )
        }
    }

    if (showDeleteConfirm) {
        VanConfirmDialog(
            show = true,
            title = "删除基金",
            message = "确定删除「${formName.ifBlank { "该基金" }}」？该基金的净值记录也将一并删除，且不可恢复。",
            confirmButtonText = "删除",
            onConfirm = { showDeleteConfirm = false; delete() },
            onCancel = { showDeleteConfirm = false },
        )
    }
}
