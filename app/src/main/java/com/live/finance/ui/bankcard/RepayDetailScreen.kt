package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading

/**
 * 还款详情 —— 一比一复刻 web `views/BankCard/repay/Detail.vue`。
 * 溢缴款判定原文：`bill_need_repay < repay_amount` → 「是（+¥全额）」。
 */
@Composable
fun RepayDetailScreen(nav: NavHostController, repayId: String = "") {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph

    var row by remember { mutableStateOf<Repay?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(repayId) {
        when (val r = graph.repay.detail(repayId)) {
            is ApiResult.Ok -> row = r.data
            else -> nav.popBackStack()
        }
        loading = false
    }

    ScreenScaffold { inner ->
        if (loading) {
            Box(Modifier.fillMaxSize().then(inner), contentAlignment = Alignment.Center) { VanLoading() }
        } else {
            val d = row ?: return@ScreenScaffold
            Column(Modifier.fillMaxSize().then(inner).verticalScroll(rememberScrollState())) {
                BankTopBar(title = "还款详情", onBack = { nav.popBackStack() })
                // 金额区
                Column(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FText("还款金额", 14f, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    FText("¥${Money.formatMoney(d.amount)}", 36f, FontWeight.SemiBold, Color.White)
                }
                // 关联信息
                DetailSection("关联信息") {
                    AppCell(title = "卡号", value = "**** ${d.cardLast4}")
                    AppCell(title = "关联账单", value = if (d.billId.isNotBlank()) "是（${d.billId}）" else "否")
                    AppCell(title = "账单流水号", value = d.id)
                    if (d.billAmount != 0.0) AppCell(title = "账单金额", value = "¥${Money.formatMoney(d.billAmount)}")
                    if (d.billNeedRepay != 0.0) AppCell(title = "账单待还", value = "¥${Money.formatMoney(d.billNeedRepay)}")
                    AppCell(
                        title = "是否溢缴款",
                        value = if (d.billNeedRepay < d.amount) "是（+¥${Money.formatMoney(d.amount)}）" else "否",
                        border = false,
                    )
                }
                // 还款详情
                DetailSection("还款详情") {
                    AppCell(title = "所属账单周期", value = d.billMonth.ifBlank { "-" })
                    AppCell(title = "还款方式", value = d.method.ifBlank { "转账" })
                    AppCell(title = "还款时间", value = d.time.ifBlank { "-" }, border = false)
                }
                // 备注
                if (d.remark.isNotBlank()) {
                    DetailSection("备注") {
                        FText(d.remark, 14f, color = colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
