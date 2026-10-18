package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Fund
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanGreen
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/**
 * 理财总览（对齐 web Finance/report/fund/Fund.vue）。
 * 四宫格汇总卡 + 「操作」菜单列表 + 「持仓概览」持仓卡列表。
 */
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
                is ApiResult.Fail -> toast.show(res.message, VanToastType.Fail)
                else -> toast.showFail(res, "加载失败")
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val totalInvest = fundList.sumOf { it.invest }
    val totalMarket = fundList.sumOf { it.marketVal }
    val totalEarnings = fundList.sumOf { it.netValue }
    val allProfit = totalEarnings >= 0

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            if (!loading) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 汇总卡：持有基金 / 当前本金 / 当前市值 / 累计收益
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("持有基金", "${fundList.size}支", null, Modifier.weight(1f))
                        SummaryCard("当前本金", "¥${fundAmount(totalInvest)}", null, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("当前市值", "¥${fundAmount(totalMarket)}", colors.primary, Modifier.weight(1f))
                        SummaryCard(
                            "累计收益",
                            "${if (allProfit) "+" else ""}¥${fundAmount(abs(totalEarnings))}",
                            if (allProfit) colors.danger else colors.success,
                            Modifier.weight(1f),
                        )
                    }

                    // 操作
                    FText("操作", 16f, FontWeight.SemiBold, colors.textPrimary)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard),
                    ) {
                        // ⚠ 四个入口的图标色统一吃 web `.menu-icon{ color: var(--theme-primary) }`（旧实现曾写成 textPrimary）。
                        //   「每日收益登记」原本 web 多了一句内联 `style="color: var(--van-green,#07c160)"` 把它钉死成绿，
                        //   原生当时照抄成 `vanGreen()` —— 2026-09-19 负责人判定那是 web 的 bug 且要求**两端一起改**：
                        //   web 已删该内联（`Fund.vue:36-40`），原生这里改回 `colors.primary`。
                    FundMenuItem("edit", "理财登记", "新增 / 编辑 / 删除持有的基金", colors.primary) { nav.navigate(Routes.FUND_REGISTER) }
                        FundMenuItem("edit", "每日收益登记", "记录今日收益和增持本金", colors.primary) { nav.navigate(Routes.FUND_DAILY) }
                        FundMenuItem("chart-trending-o", "变化走势图", "查看累计收益和当前市值变化", colors.primary) { nav.navigate(Routes.FUND_TREND) }
                        FundMenuItem("gold-coin-o", "收益明细", "每支基金的收益详情与回报率", colors.primary) { nav.navigate(Routes.FUND_EARNINGS) }
                    }

                    // 持仓概览
                    FText("持仓概览", 16f, FontWeight.SemiBold, colors.textPrimary)
                    if (fundList.isEmpty()) {
                        com.live.vant.basic.VanEmpty(description = "暂无基金数据，点上方「理财登记」添加")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            fundList.forEach { fund ->
                                FundCard(fund) { nav.navigate(Routes.FUND_TREND) }
                            }
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
private fun FundMenuItem(icon: String, title: String, desc: String, iconColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VanIcon(icon, size = 20.sp, color = iconColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FText(title, 16f, FontWeight.Medium, colors.textPrimary)
            FText(desc, 12f, FontWeight.Normal, colors.textSecondary)
        }
        VanIcon("arrow", size = 16.sp, color = colors.textTertiary)
    }
}

@Composable
private fun FundCard(fund: Fund, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val profit = fund.netValue
    val profitColor = if (profit >= 0) colors.danger else colors.success
    val rate = if (fund.invest > 0) profit / fund.invest * 100 else 0.0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FText(fund.fundName.ifEmpty { "未命名" }, 16f, FontWeight.SemiBold, colors.textPrimary)
            FText("¥${fundAmount(fund.marketVal)}", 18f, FontWeight.Bold, colors.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FText(fund.fundCompany.ifEmpty { "-" }, 12f, FontWeight.Normal, colors.textSecondary)
            FText("${if (rate >= 0) "+" else ""}${fundRate(rate)}", 12f, FontWeight.Medium, profitColor)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("份额 ${trimDecimal(fund.share)}", 12f, FontWeight.Normal, colors.textSecondary)
            FText(
                "收益 ${if (profit >= 0) "+" else ""}¥${fundAmount(abs(profit))}",
                12f, FontWeight.Medium, profitColor, Modifier.weight(1f),
            )
            FText("购入 ${fund.buyDate.ifBlank { "-" }}", 12f, FontWeight.Normal, colors.textSecondary)
        }
    }
}