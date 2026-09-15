package com.live.finance.ui.account

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
import com.live.finance.data.model.Balance
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun AccountStructureScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.balance
    var rows by remember { mutableStateOf<List<Balance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    val total = rows.sumOf { it.balance }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                Column(Modifier.fillMaxWidth().background(colors.primary).padding(20.dp)) {
                    FText("系统内计总资产", 13f, weight = FontWeight.Normal, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    FText(Money.format(total), 28f, FontWeight.Bold, androidx.compose.ui.graphics.Color.White)
                }
                Spacer(Modifier.height(12.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无账户", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { b ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(b.label, 15f, FontWeight.Medium, colors.textPrimary)
                        if (b.cardLast4.isNotBlank() && !b.isVirtual)
                            FText("****${b.cardLast4}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    FText(Money.format(b.balance), 16f, FontWeight.SemiBold, if (b.balance < 0) colors.danger else colors.textPrimary)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
