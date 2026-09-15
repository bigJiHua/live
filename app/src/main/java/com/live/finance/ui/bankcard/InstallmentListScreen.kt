package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Recurring
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import kotlinx.coroutines.launch

@Composable
fun InstallmentListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = com.live.vant.feedback.LocalVanToastController.current
    val repo = App.of(ctx).graph.recurring
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    fun reload() {
        scope.launch {
            rows = when (val r = repo.installments()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    val thisMonth = remember {
        java.util.Calendar.getInstance().let { String.format("%04d-%02d", it.get(java.util.Calendar.YEAR), it.get(java.util.Calendar.MONTH) + 1) }
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("分期", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无分期", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { r ->
                Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FText(r.name.ifBlank { "分期" }, 15f, FontWeight.Medium, colors.textPrimary, Modifier.weight(1f))
                        FText("${r.enteredPeriods}/${r.totalPeriods} 期", 12f, FontWeight.Medium, colors.textSecondary)
                    }
                    FText("金额 ${Money.format(r.amount)} · ${r.categoryName}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.bgThird)) {
                        val frac = if (r.totalPeriods <= 0) 0f else (r.enteredPeriods.toFloat() / r.totalPeriods).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(frac).height(6.dp).background(colors.primary))
                    }
                    if (r.enteredPeriods < r.totalPeriods) {
                        Spacer(Modifier.height(10.dp))
                        com.live.vant.basic.VanButton(
                            text = "入账本月($thisMonth)",
                            type = com.live.vant.basic.VanButtonType.Primary,
                            size = com.live.vant.basic.VanButtonSize.Small,
                            onClick = {
                                scope.launch {
                                    when (val rr = repo.enter(r.id, thisMonth)) {
                                        is ApiResult.Ok -> { toast.success("已入账"); reload() }
                                        is ApiResult.NeedPin -> toast.show("入账需先验证 PIN")
                                        is ApiResult.Fail -> toast.show(rr.message)
                                        else -> toast.show("入账失败")
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
