package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Fund
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanNavBar
import kotlinx.coroutines.launch

@Composable
fun FundScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val toast = com.live.vant.feedback.rememberVanToastController()
    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }

    fun reload() {
        scope.launch {
            loading = true
            when (val res = graph.fund.list()) {
                is ApiResult.Ok -> fundList = res.data ?: emptyList()
                is ApiResult.Fail -> toast.show(res.message)
                else -> toast.show("加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val totalInvest = fundList.sumOf { it.invest }
    val totalMarket = fundList.sumOf { it.marketVal }
    val totalProfit = fundList.sumOf { it.netValue }
    val allProfit = totalProfit >= 0

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            VanNavBar(title = "理财总览", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            // 概览统计卡（总本金 / 总市值 / 总收益）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("总持有", "${fundList.size} 只", null, Modifier.weight(1f))
                SummaryCard(
                    "总收益",
                    fundAmount(totalProfit),
                    if (allProfit) colors.danger else colors.success,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("总本金", fundAmount(totalInvest), null, Modifier.weight(1f))
                SummaryCard("总市值", fundAmount(totalMarket), null, Modifier.weight(1f))
            }

            // 功能菜单
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val menus = listOf(
                    Triple("gold-coin-o", "净值登记") { nav.navigate(Routes.FUND_REGISTER) },
                    Triple("replay", "每日收益") { nav.navigate(Routes.FUND_DAILY) },
                    Triple("chart-trending-o", "收益走势") { nav.navigate(Routes.FUND_TREND) },
                    Triple("balance-o", "累计收益") { nav.navigate(Routes.FUND_EARNINGS) },
                )
                menus.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (icon, label, action) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(colors.bgThird)
                                    .clickable { action() }.padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VanIcon(icon, size = 24.sp, color = colors.primary)
                                FText(label, 13f, FontWeight.Medium, colors.textPrimary)
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            // 持仓列表
            FText("我的持仓", 15f, FontWeight.SemiBold, colors.textPrimary)
            if (loading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    com.live.vant.basic.VanLoading()
                }
            } else if (fundList.isEmpty()) {
                com.live.vant.basic.VanEmpty(description = "暂无持仓基金")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    fundList.forEach { fund ->
                        FundCard(fund)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color?, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FText(label, 12f, FontWeight.Normal, colors.textSecondary)
        FText(value, 18f, FontWeight.Bold, valueColor ?: colors.textPrimary)
    }
}

@Composable
private fun FundCard(fund: Fund) {
    val colors = LocalAppColors.current
    val profitColor = if (fund.isProfit) colors.danger else colors.success
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FText(fund.fundName.ifEmpty { "未命名" }, 15f, FontWeight.SemiBold, colors.textPrimary)
                FText(fund.fundCompany.ifEmpty { "—" }, 12f, FontWeight.Normal, colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FText(fundAmount(fund.marketVal), 16f, FontWeight.Bold, colors.textPrimary)
                FText(fund.rate.ifEmpty { "0.00%" }, 12f, FontWeight.Medium, profitColor)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FText("持有 ${String.format("%.2f", fund.share)} 份", 12f, FontWeight.Normal, colors.textSecondary)
            FText((if (fund.isProfit) "+" else "") + fundAmount(fund.netValue), 13f, FontWeight.Medium, profitColor)
        }
    }
}
