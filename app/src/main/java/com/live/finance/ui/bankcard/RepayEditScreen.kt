package com.live.finance.ui.bankcard

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
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanSkeleton
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.vanPickerColumnsOf
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType

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
    var showDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)
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
            else -> { toast(r.failMessage("缺少还款记录ID"), r.failToastType()); nav.popBackStack() }
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
                is ApiResult.Ok -> { toast("保存成功", VanToastType.Success); nav.popBackStack() }
                is ApiResult.Fail -> toast(r.message.ifBlank { "保存失败" }, VanToastType.Fail)
                else -> toast(r.failMessage("保存失败"), r.failToastType())
            }
            saving = false
        }
    }

    ScreenScaffold { inner ->
        if (loading) {
            // web `repay/Edit.vue` 的 loading 是**骨架屏** `<van-skeleton title :row="8" />`
            //   （注意：本页**没有** `van-overlay` + 转圈，与 repay/Detail.vue 不同 ⇒ 这里也别加遮罩）。
            Box(Modifier.fillMaxSize().then(inner)) {
                VanSkeleton(loading = true, row = 8, title = true) { }
            }
        } else {
            Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
                // ⚠ web 是**两个** `.form-section`：「还款信息」与「备注」各自一张 inset 白卡、各带标题
                //   （旧实现把「备注」塞进了「还款信息」的卡里，结构不符）。
                //   `.form-section{ margin-top:12px }` + `.section-title{ padding:16px 16px 8px }` ⇒ 标题上方 28dp。
                Column(Modifier.padding(top = 12.dp)) {
                    FText("还款信息", 16f, color = colors.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                        // web 三行都是 `app-field`：**label 在上、控件在下**（旧实现把 label 放在左边，结构不符）
                        InputRow("还款金额", amount, { amount = it }, "请输入还款金额")
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            FText("还款方式", 14f, color = colors.textSecondary) // .app-field__label{13px→14; text-secondary}
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        FText(if (m == "柜台还款") "柜台" else m, 16f, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border)) // app-field 底边线
                        Column(
                            Modifier.fillMaxWidth().clickable { showDatePicker = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            FText("还款时间", 14f, color = colors.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FText("$datePart $timePart", 16f, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                // web `is-link` 的右箭头（旧实现用的是「›」字符）
                                VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 12.dp)) {
                    FText("备注", 16f, color = colors.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                        InputRow("备注", remark, { remark = it }, "可选填写备注信息", border = false)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 32.dp)) {
                    AppButton(text = "保存修改", onClick = { save() }, type = AppButtonType.Primary, block = true, round = true, loading = saving, disabled = saving)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "删除还款记录", onClick = { showDelete = true }, type = AppButtonType.Danger, plain = true, block = true, round = true)
                }
                Spacer(Modifier.height(40.dp)) // .page-repay-edit{ padding-bottom: 40px }
            }
        }
    }

    if (showDatePicker) {
        // web `van-datetime-picker type="datetime" title="选择日期时间"`：单个弹层一次选齐年月日时分
        val yearBase = datePart.year - 50
        val years = (yearBase..(datePart.year + 10)).map { "${it}年" }
        val months = (1..12).map { "${it}月" }
        val days = (1..31).map { "${it}日" }
        val hours = (0..23).map { "${it}时" }
        val minutes = (0..59).map { "${it}分" }
        val selHour = timePart.substringBefore(":").toIntOrNull() ?: 0
        val selMin = timePart.substringAfter(":").toIntOrNull() ?: 0
        VanPopup(show = true, onDismissRequest = { showDatePicker = false }) {
            VanPicker(
                columns = vanPickerColumnsOf(years, months, days, hours, minutes),
                value = listOf(
                    (datePart.year - yearBase).coerceIn(0, years.size - 1),
                    (datePart.monthValue - 1).coerceIn(0, 11),
                    (datePart.dayOfMonth - 1).coerceIn(0, 30),
                    selHour.coerceIn(0, 23),
                    selMin.coerceIn(0, 59),
                ),
                title = "选择日期时间",
                onConfirm = { sel, _ ->
                    val y = yearBase + (sel.getOrNull(0) ?: 0)
                    val mo = (sel.getOrNull(1) ?: 0) + 1
                    val dy = (sel.getOrNull(2) ?: 0) + 1
                    datePart = runCatching { LocalDate.of(y, mo, dy) }.getOrDefault(datePart)
                    timePart = "%02d:%02d".format(sel.getOrNull(3) ?: 0, sel.getOrNull(4) ?: 0)
                    showDatePicker = false
                },
                onCancel = { showDatePicker = false },
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
                    is ApiResult.Ok -> { toast("删除成功", VanToastType.Success); nav.popBackStack() }
                    is ApiResult.Fail -> toast(r.message.ifBlank { "删除失败" }, VanToastType.Fail)
                    else -> toast(r.failMessage("删除失败"), r.failToastType())
                }
            }
        },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )
}

