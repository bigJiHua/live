package com.live.finance.ui.asset

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetRegister
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun AssetListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.asset
    var rows by remember { mutableStateOf<List<AssetRegister>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        loading = true
        rows = when (val r = repo.list()) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> emptyList()
        }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("资产登记记录", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无登记", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { r ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(r.registerDate.ifBlank { "—" }, 15f, FontWeight.Medium, colors.textPrimary)
                        if (r.remark.isNotBlank()) FText(r.remark, 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        FText("净资产 ${Money.format(r.totalBalance)}", 14f, FontWeight.Medium, colors.primary)
                        FText("资产 ${Money.format(r.totalAsset)} · 负债 ${Money.format(r.creditDebt)}", 11f, color = colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
