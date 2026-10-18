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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.TimeFmt
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Repay
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanOverlay
import com.live.vant.basic.VanSkeleton

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
            // web 是**骨架屏** `<van-skeleton title :row="8" />` + 上层 `van-overlay` + 转圈
            //   （旧实现只画了一个居中 VanLoading，与账单详情页同一个疏漏）。
            Box(Modifier.fillMaxSize().then(inner)) {
                VanSkeleton(loading = true, row = 8, title = true) { }
            }
        } else {
            val d = row ?: return@ScreenScaffold
            Column(Modifier.fillMaxSize().then(inner).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp)) // web `.repay-content{ padding: 12px 0 }`
                // 金额区 —— web `.amount-display{ background: linear-gradient(135deg, primary 0%, primary-grad 150%);
                //   color:#fff; text-align:center; padding: 32px 16px }`
                // ⚠ 是**通栏渐变带**：左右不留边距、**没有圆角**（旧实现做成了 inset 16dp 的圆角卡片，结构不符）。
                //   `.amount-label{ font-size:14px→16; opacity:.8; margin-bottom:8px }`、`.amount-value{ 36px; 600 }`
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(colors.primary, colors.primaryGrad)))
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FText("还款金额", 16f, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    FText("¥${Money.formatMoney(d.amount)}", 36f, FontWeight.SemiBold, Color.White)
                }
                // 关联信息
                DetailSection("关联信息") {
                    AppCell(title = "卡号", value = "**** ${d.cardLast4}")
                    AppCell(title = "关联账单", value = if (d.billId.isNotBlank()) "是（${d.billId}）" else "否")
                    // ⚠ web 这行**不是**普通 `app-cell` 的左右结构，而是 `.stack-cell`（标题在上、值在下）：
                    //   `__title{font-size:14px→16f; text-primary}` + `__value{margin-top:6px; 13px→14f;
                    //   text-secondary; word-break:break-all}` —— 流水号有 23 位，普通单行 value 会被截断。
                    Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        FText("账单流水号", 16f, color = colors.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        FText(d.id, 14f, color = colors.textSecondary)
                    }
                    // AppCell 的 border 是「整宽 1dp」分隔线 ⇒ 这里手动补一条，保持与上下行一致
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
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
                    // web formatDateTime(create_time)：毫秒时间戳 → YYYY-MM-DD HH:mm:ss，空 → -（web repay/Detail 取 create_time，非 repay_time）
                    AppCell(title = "还款时间", value = if (d.createTime.isBlank()) "-" else TimeFmt.dateTimeSec(d.createTime))
                    // web formatDateTime(update_time)：同格式，空 → -
                    AppCell(title = "更新时间", value = if (d.updateTime.isBlank()) "-" else TimeFmt.dateTimeSec(d.updateTime), border = false)
                }
                // 备注
                if (d.remark.isNotBlank()) {
                    DetailSection("备注") {
                        // web `.remark-content{ color: text-secondary; line-height:1.5 }`（16f × 1.5 = 24）
                        FText(d.remark, 16f, color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp), lineHeight = 24.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(40.dp)) // .page-repay-detail{ padding-bottom: 40px }
            }
        }
        // web `van-overlay :show="loading" z-index=2000` + `van-loading size=36px vertical color=#fff`
        VanOverlay(show = loading)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VanLoading(size = 36.sp, vertical = true, color = Color.White, text = "加载中...")
            }
        }
    }
}
