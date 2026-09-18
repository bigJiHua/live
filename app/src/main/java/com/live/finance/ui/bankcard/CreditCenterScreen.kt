package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/** 信用卡专项功能行。 */
private data class FuncRow(val title: String, val desc: String, val icon: String, val color: Color, val route: String)

private val Blue = Color(0xFF1989FA)
private val Red = Color(0xFFEE0A24)
private val Green = Color(0xFF07C160)
private val Purple = Color(0xFF7232DD)
private val Gold = Color(0xFFFFB300)

/**
 * 信用卡专项 —— 一比一复刻 web `views/BankCard/CreditCenter.vue`。
 * 串行统计：先信用卡数（cardType=credit），再未还清账单数（need_repay>0）。
 */
@Composable
fun CreditCenterScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph

    var cardCount by remember { mutableStateOf(0) }
    var billCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        when (val r = graph.card.list("credit")) { is ApiResult.Ok -> cardCount = r.data.orEmpty().size; else -> cardCount = 0 }
        when (val r = graph.bill.list()) {
            is ApiResult.Ok -> billCount = r.data.orEmpty().count { it.needRepay > 0 }
            else -> billCount = 0
        }
    }

    val sections = remember {
        listOf(
            "卡片管理" to listOf(FuncRow("我的卡片", "查看所有信用卡", "card", Blue, Routes.CARD_CREDIT)),
            "账单与还款" to listOf(
                FuncRow("账单列表", "查看所有信用卡账单", "todo-list-o", Red, Routes.BILL_LIST),
                FuncRow("还款记录", "查看所有还款记录", "paid", Green, Routes.REPAY_LIST),
            ),
            "分期管理" to listOf(
                FuncRow("创建分期", "新建信用卡分期", "plus", Purple, Routes.CREDIT_INSTALLMENT),
                FuncRow("分期列表", "查看所有分期记录", "bill-o", Purple, Routes.CREDIT_INSTALLMENT_LIST),
            ),
            "额度与外币" to listOf(
                FuncRow("额度与共享池管理", "固定/临时额度独立设置 · 同银行共享额度", "gold-coin-o", Gold, Routes.CREDIT_LIMIT_MANAGE),
                FuncRow("外币消费登记对账", "按银行实际结算汇率入账", "exchange", Gold, Routes.CREDIT_FOREIGN_REGISTER),
            ),
        )
    }

    ScreenBody { inner ->
        BankTopBar(title = "信用卡专项", onBack = { nav.popBackStack() })
        Column(Modifier.fillMaxSize().then(inner).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 头部统计卡
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VanIcon("credit-pay", size = 24.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    FText("信用卡专项", 20f, FontWeight.SemiBold, Color.White)
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    StatItem("卡片数量", "$cardCount", Modifier.weight(1f)) { nav.navigate(Routes.CARD_CREDIT) }
                    StatItem("待还账单", "$billCount", Modifier.weight(1f)) { nav.navigate(Routes.BILL_LIST) }
                }
            }
            Spacer(Modifier.height(20.dp))
            sections.forEach { (sectionTitle, rows) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgCard).padding(16.dp)) {
                    FText(sectionTitle, 14f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    rows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { nav.navigate(row.route) }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(row.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) { VanIcon(row.icon, size = 22.sp, color = row.color) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                FText(row.title, 15f, FontWeight.Medium, colors.textPrimary)
                                Spacer(Modifier.height(2.dp))
                                FText(row.desc, 12f, color = colors.textTertiary)
                            }
                            VanIcon("arrow", size = 16.sp, color = colors.textTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }) {
        FText(value, 28f, FontWeight.Bold, Color.White)
        Spacer(Modifier.height(4.dp))
        FText(label, 12f, color = Color.White.copy(alpha = 0.8f))
    }
}

/** 统一页面壳（渐变头卡页共用；[inner] 为 Scaffold 内容边距）。 */
@Composable
private fun ScreenBody(content: @Composable androidx.compose.foundation.layout.BoxScope.(Modifier) -> Unit) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize().background(colors.bgPage)) { content(Modifier) }
}
