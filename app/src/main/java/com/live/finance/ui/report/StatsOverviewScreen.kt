package com.live.finance.ui.report

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.CatStat
import com.live.finance.data.model.MonthStats
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import java.util.Calendar

@Composable
fun StatsOverviewScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.flow
    val cal = remember { Calendar.getInstance() }
    val year = remember { cal.get(Calendar.YEAR) }
    val month = remember { cal.get(Calendar.MONTH) + 1 }
    var stats by remember { mutableStateOf<MonthStats?>(null) }

    LaunchedEffect(Unit) {
        stats = (repo.monthStats(year, month) as? ApiResult.Ok)?.data
    }

    ScreenScaffold { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            FText("收支概览 · ${year}年${month}月", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
            // 三格总览
            Row(Modifier.padding(horizontal = 12.dp)) {
                StatCell("结余", Money.format(stats?.balance ?: 0.0), colors.primary, Modifier.weight(1f))
                Spacer(Modifier.height(0.dp))
                StatCell("收入", Money.format(stats?.income ?: 0.0), com.live.finance.ui.common.MoneyColor.income, Modifier.weight(1f))
                Spacer(Modifier.height(0.dp))
                StatCell("支出", Money.format(stats?.expense ?: 0.0), com.live.finance.ui.common.MoneyColor.expense, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            FText("支出构成", 15f, FontWeight.Medium, colors.textPrimary, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            val exp = stats?.expenseBreakdown ?: emptyList()
            if (exp.isEmpty()) FText("暂无支出分类", 13f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            exp.forEach { c -> CatBar(c, exp.maxOf { it.total }, colors) }
            Spacer(Modifier.height(16.dp))
            FText("收入构成", 15f, FontWeight.Medium, colors.textPrimary, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            val inc = stats?.incomeBreakdown ?: emptyList()
            if (inc.isEmpty()) FText("暂无收入分类", 13f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            inc.forEach { c -> CatBar(c, inc.maxOf { it.total }, colors, income = true) }
            Spacer(Modifier.height(24.dp))
            com.live.vant.basic.VanButton(
                text = "查看月度趋势", type = com.live.vant.basic.VanButtonType.Default, block = true,
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { nav.navigate(com.live.finance.core.nav.Routes.REPORT_MONTHLY_TREND) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier.padding(6.dp).background(colors.bgCard).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        FText(value, 16f, FontWeight.Bold, color)
    }
}

@Composable
private fun CatBar(c: CatStat, max: Double, colors: com.live.vant.theme.VantColors, income: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(c.name, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
            FText("${Money.format(c.total)} (${c.count}笔)", 12f, FontWeight.Normal, colors.textTertiary)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(colors.bgThird)) {
            val frac = if (max <= 0) 0f else (c.total / max).toFloat().coerceIn(0.05f, 1f)
            Box(Modifier.fillMaxWidth(frac).height(8.dp).background(if (income) com.live.finance.ui.common.MoneyColor.income else com.live.finance.ui.common.MoneyColor.expense))
        }
    }
}
