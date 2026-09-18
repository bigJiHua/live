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
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/**
 * 选择预算类型（web `views/Finance/budget/TypeSelect.vue`）：
 * 上方两宫格（购物/出行）+ 下方餐饮横条，点击进对应表单页。
 */
@Composable
fun BudgetTypeSelectScreen(nav: NavHostController) {
    ScreenScaffold { inner ->
        Column(
            inner.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeCard(
                    title = "购物预算", desc = "电子产品、日用品等购物计划",
                    icon = "shopping-cart-o",
                    iconBg = Brush.linearGradient(listOf(Color(0xFFFF6034), Color(0xFFEE0A24))),
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
                    .background(com.live.finance.theme.LocalAppColors.current.bgCard)
                    .clickable { nav.navigate(Routes.BUDGET_EAT) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFF976A), Color(0xFFEE0A24)))),
                    contentAlignment = Alignment.Center,
                ) { VanIcon("fire-o", size = 24.sp, color = Color.White) }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    FText("餐饮预算", 16f, FontWeight.SemiBold,
                        com.live.finance.theme.LocalAppColors.current.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    FText("日常餐饮开销控制", 12f,
                        color = com.live.finance.theme.LocalAppColors.current.textTertiary)
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
        FText(title, 16f, FontWeight.SemiBold, colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        FText(desc, 12f, color = colors.textTertiary)
    }
}
