package com.live.finance.ui.bankcard

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
import com.live.finance.data.model.Bill
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun BillListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.bill
    var rows by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("信用卡账单", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无账单", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { b ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(b.cardLabel, 15f, FontWeight.Medium, colors.textPrimary)
                        FText("${b.billMonth.ifBlank { "账单" }} · 账单日${b.billDay} 还款日${b.repayDay}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                        if (b.isOverdue) FText("已逾期${b.overdueDays}天", 11f, FontWeight.Medium, color = colors.danger, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        FText("应还 ${Money.format(b.needRepay)}", 15f, FontWeight.SemiBold, if (b.needRepay > 0) colors.danger else colors.success)
                        FText(b.statusLabel, 11f, FontWeight.Normal, if (b.needRepay > 0) colors.warning else colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
