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
import com.live.finance.data.model.Pool
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun LimitManageScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.pool
    var rows by remember { mutableStateOf<List<Pool>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("额度与共享池", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无额度池", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { p ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(p.bankName.ifBlank { "共享额度池" }, 15f, FontWeight.Medium, colors.textPrimary)
                        if (p.totalTempLimit > 0)
                            FText("临时额度 ${Money.format(p.totalTempLimit)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    FText("总额度 ${Money.format(p.totalCreditLimit)}", 15f, FontWeight.SemiBold, colors.primary)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
