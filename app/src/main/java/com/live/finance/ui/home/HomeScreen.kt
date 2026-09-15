package com.live.finance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetHome
import com.live.finance.data.model.Todo
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val cal = remember { Calendar.getInstance() }
    var asset by remember { mutableStateOf<AssetHome?>(null) }
    var reminders by remember { mutableStateOf<List<Todo>>(emptyList()) }

    LaunchedEffect(Unit) {
        asset = (graph.asset.home() as? ApiResult.Ok)?.data
        scope.launch {
            reminders = (graph.todo.reminders(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1) as? ApiResult.Ok)?.data ?: emptyList()
        }
    }

    ScreenScaffold { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().background(colors.primary).padding(24.dp)) {
                FText("净资产", 13f, FontWeight.Normal, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                FText(Money.format(asset?.totalBalance ?: 0.0), 32f, FontWeight.Bold, androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.height(6.dp))
                FText("资产 ${Money.format(asset?.totalAsset ?: 0.0)}    负债 ${Money.format(asset?.creditDebt ?: 0.0)}",
                    13f, FontWeight.Normal, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(16.dp))

            // 快捷宫格
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("记一笔", colors, onClick = { nav.navigate(Routes.ACCOUNT_ADD) }, Modifier.weight(1f))
                QuickTile("流水", colors, onClick = { nav.navigate(Routes.FLOW_LIST) }, Modifier.weight(1f))
                QuickTile("银行卡", colors, onClick = { nav.navigate(Routes.CARD) }, Modifier.weight(1f))
                QuickTile("报表", colors, onClick = { nav.navigate(Routes.REPORT_STATS_OVERVIEW) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("待办", colors, onClick = { nav.navigate(Routes.TODO_CALENDAR) }, Modifier.weight(1f))
                QuickTile("工作", colors, onClick = { nav.navigate(Routes.WORK_JOB_SETTING) }, Modifier.weight(1f))
                QuickTile("动态", colors, onClick = { nav.navigate(Routes.DIARY) }, Modifier.weight(1f))
                QuickTile("还款", colors, onClick = { nav.navigate(Routes.REPAY_LIST) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            FText("待办提醒", 15f, FontWeight.Bold, colors.textPrimary, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (reminders.isEmpty()) FText("暂无待办", 13f, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
            reminders.take(6).forEach { t ->
                Row(Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(t.content.ifBlank { "提醒" }, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
                    FText(t.happenDate, 12f, FontWeight.Normal, colors.textTertiary)
                }
                Spacer(Modifier.height(0.5.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickTile(label: String, colors: com.live.vant.theme.VantColors, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(72.dp).background(colors.bgCard).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        FText(label, 14f, FontWeight.Medium, colors.textPrimary)
    }
}
