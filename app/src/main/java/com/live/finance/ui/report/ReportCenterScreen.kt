package com.live.finance.ui.report

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/** 报表中心菜单条目（web `Report.vue`；`route == null` = 原生尚未复刻的子页）。 */
private data class ReportEntry(val title: String, val desc: String, val icon: String, val route: String?)

/**
 * 财务报表中心 —— 一比一复刻 web `views/Finance/report/Report.vue`（`/finance/report`，账本页「报表」的真正落点）。
 * 四分区：流水管理 / 数据统计 / 负债统计 / 理财投资；图标块是**无底色**的 40×40 主色图标（web `.menu-icon`）。
 */
@Composable
fun ReportCenterScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var pendingTip by remember { mutableStateOf<String?>(null) }

    val sections = remember {
        listOf(
            "流水管理" to listOf(
                ReportEntry("流水筛选", "按时段、分类筛选流水并支持导出 Excel", "orders-o", Routes.REPORT_FLOW_FILTER),
                ReportEntry("银行卡收支明细", "按银行卡查询历史收支流水", "credit-pay", Routes.REPORT_CARD_FLOW),
                ReportEntry("转账明细查询", "查看所有转账记录", "exchange", Routes.REPORT_TRANSFER_LIST),
            ),
            "数据统计" to listOf(
                ReportEntry("收支总额与结余", "总收入、总支出、净结余速览", "chart-trending-o", Routes.REPORT_STATS_OVERVIEW),
                ReportEntry("类目消费占比", "各消费分类占比统计图表", "graphic", Routes.REPORT_CATEGORY_RATIO),
                ReportEntry("月度收支趋势", "每月收支变化趋势图表", "bar-chart-o", Routes.REPORT_MONTHLY_TREND),
            ),
            "负债统计" to listOf(
                ReportEntry("负债总览", "卡有效期预警、负债明细、支出类别统计", "balance-list-o", Routes.REPORT_DEBT_OVERVIEW),
            ),
            "理财投资" to listOf(
                ReportEntry("理财总览", "基金持仓、市值、收益概览", "gold-coin-o", Routes.FUND),
            ),
        )
    }

    ScreenScaffold { inner ->
        Column(Modifier.fillMaxSize().then(inner).background(colors.bgPage)) {
            com.live.vant.nav.VanNavBar(title = "财务报表", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp)) {
                sections.forEach { (sectionTitle, entries) ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                        FText(sectionTitle, 15f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgCard)) {
                            entries.forEachIndexed { idx, entry ->
                                val isLast = idx == entries.lastIndex
                                // web `.menu-item` 四边 1px 边框、`:last-child` 去底边（相邻项呈两条线）——逐边绘制
                                val borderMod = Modifier.drawBehind {
                                    val w = 1.dp.toPx()
                                    drawRect(colors.border, Offset(0f, 0f), Size(size.width, w))                       // top
                                    drawRect(colors.border, Offset(0f, 0f), Size(w, size.height))                     // left
                                    drawRect(colors.border, Offset(size.width - w, 0f), Size(w, size.height))         // right
                                    if (!isLast) drawRect(colors.border, Offset(0f, size.height - w), Size(size.width, w)) // bottom
                                }
                                Row(
                                    Modifier.fillMaxWidth().then(borderMod)
                                        .clickable {
                                            val r = entry.route
                                            if (r == null) pendingTip = entry.title
                                            else runCatching { nav.navigate(r) }        // 防目的地缺失再闪退
                                                .onFailure { pendingTip = entry.title }
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        VanIcon(entry.icon, size = 20.sp, color = colors.primary)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        FText(entry.title, 14f, FontWeight.Medium, colors.textPrimary)
                                        Spacer(Modifier.height(2.dp))
                                        FText(entry.desc, 11f, color = colors.textTertiary)
                                    }
                                    VanIcon("arrow", size = 16.sp, color = colors.textTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingTip) {
        pendingTip?.let {
            Toast.makeText(context, "「$it」报表页尚未复刻，敬请期待", Toast.LENGTH_SHORT).show()
            pendingTip = null
        }
    }
}
