package com.live.finance.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.repo.NewFlow
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
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AddTransactionScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    var direction by remember { mutableStateOf(0) }        // 0 支出 1 收入
    var amount by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var account by remember { mutableStateOf<Card?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Card>>(emptyList()) }
    var showCat by remember { mutableStateOf(false) }
    var showAcc by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(direction) {
        category = null
        when (val r = graph.category.list(if (direction == 1) "income" else "expense")) {
            is ApiResult.Ok -> categories = r.data ?: emptyList()
            else -> categories = emptyList()
        }
    }
    LaunchedEffect(Unit) {
        when (val r = graph.card.list(null)) {
            is ApiResult.Ok -> accounts = r.data ?: emptyList()
            else -> accounts = emptyList()
        }
    }

    ScreenScaffold { _ ->
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                VanButton(
                    text = "支出", type = if (direction == 0) VanButtonType.Danger else VanButtonType.Default,
                    modifier = Modifier.weight(1f), onClick = { direction = 0 },
                )
                Spacer(Modifier.width(12.dp))
                VanButton(
                    text = "收入", type = if (direction == 1) VanButtonType.Success else VanButtonType.Default,
                    modifier = Modifier.weight(1f), onClick = { direction = 1 },
                )
            }
            Spacer(Modifier.height(16.dp))

            VanField(value = amount, onValueChange = { amount = it }, label = "金额", placeholder = "0.00", type = VanFieldType.Number)
            // 分类（只读，点开选择器）
            VanField(value = category?.name ?: "", onValueChange = {}, label = "分类", placeholder = "选择分类", readonly = true, onClick = { showCat = true })
            // 账户（只读，点开选择器）
            VanField(value = account?.displayName ?: "", onValueChange = {}, label = "账户", placeholder = "选择账户", readonly = true, onClick = { showAcc = true })
            VanField(value = remark, onValueChange = { remark = it }, label = "备注", placeholder = "选填")
            val today = remember {
                Calendar.getInstance().let {
                    String.format("%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
                }
            }
            FText("交易日期：$today", 13f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 12.dp))

            Spacer(Modifier.height(24.dp))
            VanButton(
                text = "保存", type = VanButtonType.Primary, block = true, loading = submitting,
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val cat = category
                    val acc = account
                    when {
                        amt <= 0 -> toast.show("请输入金额")
                        cat == null -> toast.show("请选择分类")
                        acc == null -> toast.show("请选择账户")
                        else -> {
                            submitting = true
                            scope.launch {
                                val (payMethod, cardId) = resolveAccount(acc)
                                val r = graph.flow.create(
                                    NewFlow(
                                        direction = direction, amount = amt, categoryId = cat.id,
                                        categoryName = cat.name, payMethod = payMethod, cardId = cardId,
                                        transDate = today, remark = remark.trim(), isCredit = acc.isCredit,
                                    )
                                )
                                submitting = false
                                when (r) {
                                    is ApiResult.Ok -> { toast.success("登记成功"); nav.popBackStack() }
                                    is ApiResult.Fail -> toast.show(r.message)
                                    else -> toast.show("登记失败")
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    // 分类选择器
    VanPopup(show = showCat, onDismissRequest = { showCat = false }) {
        VanPicker(
            columns = listOf(categories.map { VanPickerOption(it.name, it.id) }),
            value = listOf(categories.indexOfFirst { it.id == category?.id }.coerceAtLeast(0)),
            title = "选择分类",
            onConfirm = { idx, _ -> category = categories.getOrNull(idx.firstOrNull() ?: 0); showCat = false },
            onCancel = { showCat = false },
        )
    }
    // 账户选择器
    VanPopup(show = showAcc, onDismissRequest = { showAcc = false }) {
        VanPicker(
            columns = listOf(accounts.map { VanPickerOption(it.displayName, it.id) }),
            value = listOf(accounts.indexOfFirst { it.id == account?.id }.coerceAtLeast(0)),
            title = "选择账户",
            onConfirm = { idx, _ -> account = accounts.getOrNull(idx.firstOrNull() ?: 0); showAcc = false },
            onCancel = { showAcc = false },
        )
    }
}

/** 与 web buildCardId 对齐：现金→xxxx、余额→yyyy，其余用真实卡 id；同时给出 payMethod 文案。 */
private fun resolveAccount(acc: Card): Pair<String, String> {
    val label = when {
        acc.bankName.contains("现金") || acc.alias.contains("现金") -> "现金"
        acc.bankName.contains("余额") || acc.alias.contains("余额") -> "余额"
        acc.isCredit -> "信用卡"
        else -> "借记卡"
    }
    val cardId = when (label) {
        "现金" -> "xxxx"
        "余额" -> "yyyy"
        else -> acc.id
    }
    return label to cardId
}
