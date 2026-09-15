package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.live.finance.data.model.Card
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType

private enum class Filter(val label: String, val type: String?) { All("全部", null), Debit("储蓄卡", "debit"), Credit("信用卡", "credit") }

@Composable
fun CardListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.card
    var filter by remember { mutableStateOf(Filter.All) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(filter) {
        loading = true
        cards = when (val r = repo.list(filter.type)) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> emptyList()
        }
        loading = false
    }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("银行卡", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                Row(Modifier.padding(horizontal = 12.dp)) {
                    Filter.values().forEach { f ->
                        val sel = f == filter
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .background(if (sel) colors.primary else colors.bgThird)
                                .clickable { filter = f }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) { FText(f.label, 13f, FontWeight.Medium, if (sel) androidx.compose.ui.graphics.Color.White else colors.textSecondary) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 12.dp)) {
                    VanButton(text = "＋ 新增卡片", type = VanButtonType.Primary, onClick = { nav.navigate(Routes.CARD_ADD) })
                    Spacer(Modifier.width(12.dp))
                    VanButton(text = "信用卡账单", type = VanButtonType.Default, onClick = { nav.navigate(Routes.BILL_LIST) })
                    Spacer(Modifier.width(12.dp))
                    VanButton(text = "外币记录", type = VanButtonType.Default, onClick = { nav.navigate(Routes.CREDIT_FOREIGN_REGISTER) })
                }
                Row(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
                    VanButton(text = "额度与共享池", type = VanButtonType.Default, onClick = { nav.navigate(Routes.CREDIT_LIMIT_MANAGE) })
                    Spacer(Modifier.width(12.dp))
                    VanButton(text = "分期", type = VanButtonType.Default, onClick = { nav.navigate(Routes.CREDIT_INSTALLMENT_LIST) })
                    Spacer(Modifier.width(12.dp))
                    VanButton(text = "信用卡录入", type = VanButtonType.Default, onClick = { nav.navigate(Routes.CREDIT_FULL) })
                }
                Spacer(Modifier.height(8.dp))
                if (loading) FText("加载中…", 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(16.dp))
                if (!loading && cards.isEmpty()) FText("暂无卡片", 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(16.dp))
            }
            items(cards.filter { !it.isHide }) { card ->
                CardRow(card, Modifier.clickable { nav.navigate(Routes.cardEdit(card.id)) })
            }
        }
    }
}

@Composable
private fun CardRow(card: Card, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText(card.displayName, 15f, FontWeight.Medium, colors.textPrimary)
                if (card.isDefault) {
                    Box(Modifier.padding(start = 6.dp).background(colors.primaryLight).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        FText("默认", 10f, FontWeight.Normal, colors.primary)
                    }
                }
            }
            FText(
                buildString {
                    append(card.cardOrg.ifBlank { card.bankName })
                    if (card.isCredit && card.billDay > 0) append(" · 账单日${card.billDay} 还款日${card.repayDay}")
                },
                11f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 2.dp),
            )
        }
        FText(if (card.isCredit) "信用卡" else "储蓄卡", 12f, FontWeight.Normal, if (card.isCredit) colors.warning else colors.info)
    }
    Spacer(Modifier.height(0.5.dp))
}
