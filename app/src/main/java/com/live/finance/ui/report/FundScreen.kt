package com.live.finance.ui.report

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
import com.live.finance.data.model.Fund
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun FundScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.fund
    var rows by remember { mutableStateOf<List<Fund>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    val totalMarket = rows.sumOf { it.marketVal }
    val totalProfit = rows.sumOf { it.netValue }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                Column(Modifier.fillMaxWidth().background(colors.primary).padding(20.dp)) {
                    FText("理财总览", 13f, FontWeight.Normal, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    FText(Money.format(totalMarket), 28f, FontWeight.Bold, androidx.compose.ui.graphics.Color.White)
                    FText("累计收益 ${Money.format(totalProfit)}", 13f, FontWeight.Normal, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(12.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无持仓", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { f ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(f.fundName, 15f, FontWeight.Medium, colors.textPrimary)
                        FText("成本 ${Money.format(f.invest)} · 份额 ${Money.format(f.share)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        FText(Money.format(f.marketVal), 15f, FontWeight.SemiBold, colors.textPrimary)
                        FText(
                            if (f.rate.isNotBlank()) f.rate else (if (f.isProfit) "+" else "") + Money.format(f.netValue),
                            12f, FontWeight.Medium, if (f.isProfit) colors.danger else colors.success,
                        )
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
