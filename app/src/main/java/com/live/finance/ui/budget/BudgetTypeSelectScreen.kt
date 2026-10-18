package com.live.finance.ui.budget

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanOrange
import com.live.finance.theme.vanRed
import com.live.finance.theme.vanGreen
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/**
 * 选择预算类型（web `views/Finance/budget/TypeSelect.vue`）：
 * 上方两宫格（购物/出行）+ 下方餐饮横条，点击进对应表单页。
 */
@Composable
fun BudgetTypeSelectScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    ScreenScaffold { inner ->
        Column(
            inner.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeCard(
                    title = "购物预算", desc = "电子产品、日用品等购物计划",
                    icon = "shopping-cart-o",
                    iconBg = Brush.linearGradient(listOf(vanRed(Color(0xFFFF6034)), colors.danger)),
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.BUDGET_SHOPPING) },
                )
                TypeCard(
                    title = "出行预算", desc = "旅游、出差等交通住宿费用",
                    icon = "location-o",
                    iconBg = Brush.verticalGradient(listOf(Color(0xFF4A90E2), Color(0xFF3B7BD4))),
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.BUDGET_TRAVEL) },
                )
            }
            // 餐饮横条（web eat-card）
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(colors.bgCard)
                    .clickable { nav.navigate(Routes.BUDGET_EAT) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(vanOrange(), colors.danger))),
                    contentAlignment = Alignment.Center,
                ) { VanIcon("fire-o", size = 24.sp, color = Color.White) }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    FText("餐饮预算", 18f, FontWeight.SemiBold,
                        colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    FText("日常餐饮开销控制", 12f,
                        color = com.live.finance.theme.LocalAppColors.current.textTertiary)
                }
            }

            // 周期色条图例（第四十五轮新增、第五十二轮修正）：
            // ⚠ web 三个表单的周期选择器只有 月/季/年 —— 样式里的 .日/.周 是防御性 class，
            //   实际数据永远不会出现，图例只列三种真实周期
            Spacer(Modifier.height(12.dp))
            FText("周期状态提示：", 14f, FontWeight.SemiBold, colors.textPrimary)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("月", "季", "年").forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.width(4.dp).height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BudgetShared.cycleColor(c, colors, vanGreen())),
                        )
                        FText(BudgetShared.cycleName(c), 13f, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeCard(
    title: String,
    desc: String,
    icon: String,
    iconBg: Brush,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = com.live.finance.theme.LocalAppColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.bgCard)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) { VanIcon(icon, size = 28.sp, color = Color.White) }
        Spacer(Modifier.height(12.dp))
        FText(title, 18f, FontWeight.SemiBold, colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        FText(desc, 12f, color = colors.textTertiary)
    }
}
