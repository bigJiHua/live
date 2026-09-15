package com.live.finance.ui.recurring

import androidx.compose.foundation.background
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
fun RecurringListScreen(nav: NavHostController, title: String = "固定支出") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = com.live.vant.feedback.LocalVanToastController.current
    val repo = App.of(ctx).graph.recurring
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Recurring>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val thisMonth = remember {
        java.util.Calendar.getInstance().let { String.format("%04d-%02d", it.get(java.util.Calendar.YEAR), it.get(java.util.Calendar.MONTH) + 1) }
    }
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
                FText(title, 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无数据", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { r ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(r.name, 15f, FontWeight.Medium, colors.textPrimary)
                        FText("${r.categoryName} · ${r.accountLabel} · ${cycleLabel(r)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    FText(Money.format(r.amount), 16f, FontWeight.SemiBold, colors.textPrimary)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}

private fun cycleLabel(r: Recurring): String = when (r.cycle) {
    "monthly" -> "每月${r.dayOfCycle}号"
    "weekly" -> "每周${r.dayOfCycle}"
    else -> r.cycle.ifBlank { "周期" }
}
