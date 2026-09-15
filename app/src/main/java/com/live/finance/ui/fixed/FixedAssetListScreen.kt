package com.live.finance.ui.fixed

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
import com.live.finance.data.model.FixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun FixedAssetListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.fixed
    var rows by remember { mutableStateOf<List<FixedAsset>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("固定资产", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无固定资产", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { a ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(a.info.ifBlank { "未命名资产" }, 15f, FontWeight.Medium, colors.textPrimary)
                        FText("${a.tag} · ${a.statusLabel} · 已用${a.monthsUsed}月 · 购于${a.buyDate.ifBlank { "—" }}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        FText("现值 ${Money.format(a.nowVal)}", 14f, FontWeight.Medium, colors.primary)
                        FText("原价 ${Money.format(a.buyPrice)}", 11f, color = colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
