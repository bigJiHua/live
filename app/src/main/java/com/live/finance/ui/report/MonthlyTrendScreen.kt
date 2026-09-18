package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import java.util.Calendar
import kotlinx.coroutines.launch

private data class MonthPoint(val label: String, val income: Double, val expense: Double)

@Composable
fun MonthlyTrendScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.flow
    var points by remember { mutableStateOf<List<MonthPoint>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    // web 把本页列入 keep-alive（FinanceReportMonthlyTrend）；原生每次进入都会重拉，
    // 这里补下拉刷新，保证登记/记账后能手动刷新到最新数据。
    val refreshState = rememberVanPullRefreshState()
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        val cal = Calendar.getInstance()
        val list = mutableListOf<MonthPoint>()
        repeat(6) { back ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
            val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1
            val st = (repo.monthStats(y, m) as? ApiResult.Ok)?.data
            list.add(MonthPoint("${m}月", st?.income ?: 0.0, st?.expense ?: 0.0))
        }
        points = list.reversed()   // 旧→新
        loading = false
    }

    LaunchedEffect(Unit) { load() }
    val max = (points.flatMap { listOf(it.income, it.expense) }.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    ScreenScaffold { mod ->
        Column(mod) {
            com.live.vant.nav.VanNavBar(title = "月度收支趋势", leftArrow = true, onClickLeft = { nav.popBackStack() })
            VanPullRefresh(
                state = refreshState,
                // 只有内容滚到顶部时下拉才触发刷新（Vant 语义）
                isChildAtTop = { scroll.value == 0 },
                onRefresh = {
                    scope.launch {
                        load()
                        refreshState.finishRefresh()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            FText("月度收支趋势（近 6 个月）", 18f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Row {
                Dot(com.live.finance.ui.common.MoneyColor.income); FText("收入", 12f, color = colors.textSecondary, modifier = Modifier.padding(end = 16.dp, start = 4.dp))
                Dot(com.live.finance.ui.common.MoneyColor.expense); FText("支出", 12f, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            if (loading) FText("加载中…", 14f, color = colors.textTertiary)
            Row(Modifier.fillMaxWidth().height(220.dp), verticalAlignment = Alignment.Bottom) {
                points.forEach { p ->
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        // 收入柱
                        Box(Modifier.width(14.dp).height((200f * (p.income / max)).dp.coerceAtLeast(2.dp)).background(com.live.finance.ui.common.MoneyColor.income))
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.width(14.dp).height((200f * (p.expense / max)).dp.coerceAtLeast(2.dp)).background(com.live.finance.ui.common.MoneyColor.expense))
                        Spacer(Modifier.height(6.dp))
                        FText(p.label, 11f, color = colors.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            points.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    FText(p.label, 13f, color = colors.textSecondary, modifier = Modifier.width(48.dp))
                    FText("收 ${Money.format(p.income)}", 13f, color = com.live.finance.ui.common.MoneyColor.income, modifier = Modifier.weight(1f))
                    FText("支 ${Money.format(p.expense)}", 13f, color = com.live.finance.ui.common.MoneyColor.expense)
                }
            }
        }
        }
    }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) =
    Box(Modifier.width(10.dp).height(10.dp).background(color))
