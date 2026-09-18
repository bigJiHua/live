package com.live.finance.ui.bankcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanDatePicker
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 编辑还款记录 —— 一比一复刻 web `views/BankCard/repay/Edit.vue`。 */
@Composable
fun RepayEditScreen(nav: NavHostController, repayId: String = "") {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()

    var row by remember { mutableStateOf<Repay?>(null) }
    var loading by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("转账") }
    var datePart by remember { mutableStateOf(LocalDate.now()) }
    var timePart by remember { mutableStateOf("00:00") }
    var remark by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    LaunchedEffect(repayId) {
        when (val r = graph.repay.detail(repayId)) {
            is ApiResult.Ok -> {
                val d = r.data ?: return@LaunchedEffect
                row = d
                amount = if (d.amount != 0.0) Money.formatMoney(d.amount) else ""
                method = d.method.ifBlank { "转账" }
                val dt = d.time.replace("T", " ")
                if (dt.length >= 10) datePart = runCatching { LocalDate.parse(dt.take(10)) }.getOrDefault(LocalDate.now())
                if (dt.length >= 16) timePart = dt.substring(11, 16)
                remark = d.remark
            }
            else -> { toast("缺少还款记录ID"); nav.popBackStack() }
        }
        loading = false
    }

    fun save() {
        val d = row ?: return
        val amt = amount.toDoubleOrNull() ?: 0.0
        if (amt <= 0) { toast("请输入还款金额"); return }
        if (timePart.isBlank()) { toast("请选择还款时间"); return }
        scope.launch {
            saving = true
            val r = graph.repay.update(d.id, amt, method, "$datePart $timePart:00", remark)
            when (r) {
                is ApiResult.Ok -> { toast("保存成功"); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "保存失败" })
                else -> toast("保存失败")
            }
            saving = false
        }
    }

    ScreenScaffold { inner ->
        if (loading) {
            Box(Modifier.fillMaxSize().then(inner), contentAlignment = Alignment.Center) { VanLoading() }
        } else {
            Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
                BankTopBar(title = "编辑还款记录", onBack = { nav.popBackStack() })
                Column(Modifier.padding(top = 12.dp)) {
                    FText("还款信息", 14f, color = colors.textTertiary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                        InputLine("还款金额", amount, "请输入还款金额") { amount = it }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FText("还款方式", 14f, color = colors.textPrimary)
                            Spacer(Modifier.width(24.dp))
                            listOf("转账", "自动扣款", "柜台还款").forEach { m ->
                                val selected = method == m
                                Row(
                                    Modifier.padding(end = 18.dp).clickable { method = m },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(18.dp).clip(CircleShape)
                                            .background(if (selected) colors.primary else Color.Transparent)
                                            .border(1.dp, if (selected) colors.primary else colors.border, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.primary))
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    FText(if (m == "柜台还款") "柜台" else m, 14f, color = colors.textPrimary)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable { showDatePicker = true }, verticalAlignment = Alignment.CenterVertically) {
                            FText("还款时间", 14f, color = colors.textPrimary)
                            Spacer(Modifier.weight(1f))
                            FText("$datePart $timePart", 14f, color = colors.textPrimary)
                            Spacer(Modifier.width(6.dp))
                            FText("›", 16f, color = colors.textTertiary)
                        }
                        InputLine("备注", remark, "可选填写备注信息") { remark = it }
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 32.dp)) {
                    AppButton(text = "保存修改", onClick = { save() }, type = AppButtonType.Primary, block = true, round = true, loading = saving, disabled = saving)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "删除还款记录", onClick = { showDelete = true }, type = AppButtonType.Danger, plain = true, block = true, round = true)
                }
            }
        }
    }

    if (showDatePicker) {
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            VanDatePicker(
                type = "date",
                value = datePart,
                onValueChange = { },
                title = "选择日期",
                onConfirm = { datePart = it; showDatePicker = false },
                onCancel = { showDatePicker = false },
            )
        }
    }
    if (showTimePicker) {
        VanPopup(show = true, onDismissRequest = { showTimePicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf((0..23).map { "${it}时" }, (0..59).map { "${it}分" }),
                value = listOf((timePart.substringBefore(":").toIntOrNull() ?: 0), (timePart.substringAfter(":").toIntOrNull() ?: 0)),
                title = "选择时间",
                onConfirm = { sel, _ ->
                    val h = "%02d".format(sel.getOrNull(0) ?: 0)
                    val m = "%02d".format(sel.getOrNull(1) ?: 0)
                    timePart = "$h:$m"
                    showTimePicker = false
                },
                onCancel = { showTimePicker = false },
            )
        }
    }

    VanConfirmDialog(
        show = showDelete,
        title = "删除确认",
        message = "确定要删除这条还款记录吗？",
        onConfirm = {
            showDelete = false
            scope.launch {
                when (val r = graph.repay.delete(repayId)) {
                    is ApiResult.Ok -> { toast("删除成功"); nav.popBackStack() }
                    is ApiResult.Fail -> toast(r.message.ifBlank { "删除失败" })
                    else -> toast("删除失败")
                }
            }
        },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )
}

@Composable
private fun InputLine(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FText(label, 14f, color = colors.textPrimary)
        Spacer(Modifier.width(24.dp))
        AppField(value = value, onValueChange = onChange, placeholder = placeholder, border = false, modifier = Modifier.weight(1f))
    }
}
