package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Foreign
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ForeignScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.foreign
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Foreign>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Foreign?>(null) }
    var rate by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("外币消费登记", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无外币记录", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { f ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard)
                        .clickable { if (f.status == "pending") { editing = f; rate = f.registeredRate.toString(); remark = "" } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText("${f.currency} ${Money.format(f.foreignAmount)}", 15f, FontWeight.Medium, colors.textPrimary)
                        FText("折算 ¥${Money.format(f.registeredRmb)} · ${com.live.finance.core.TimeFmt.date(f.createTime)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    FText(f.statusLabel, 12f, FontWeight.Medium, if (f.status == "reconciled") colors.success else colors.warning)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }

    VanPopup(show = editing != null, onDismissRequest = { editing = null }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            FText("对账 · ${editing?.currency ?: ""} ${editing?.let { Money.format(it.foreignAmount) } ?: ""}", 15f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            VanField(value = rate, onValueChange = { rate = it }, label = "实际汇率", placeholder = "每100外币=?元", type = VanFieldType.Number)
            VanField(value = remark, onValueChange = { remark = it }, label = "备注", placeholder = "选填")
            Spacer(Modifier.height(16.dp))
            VanButton(text = "确认对账", type = VanButtonType.Primary, block = true, loading = saving, onClick = {
                val f = editing ?: return@VanButton
                val rv = rate.toDoubleOrNull() ?: run { toast.show("请输入实际汇率"); return@VanButton }
                if (rv <= 0) { toast.show("汇率必须大于 0"); return@VanButton }
                saving = true
                scope.launch {
                    val today = Calendar.getInstance().let { String.format("%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
                    val r = repo.reconcile(f.id, rv, 0.0, today, remark.trim())
                    saving = false
                    when (r) {
                        is ApiResult.Ok -> { toast.success("已对账"); editing = null; reload() }
                        is ApiResult.Fail -> toast.show(r.message)
                        else -> toast.show("对账失败")
                    }
                }
            })
        }
    }
}
