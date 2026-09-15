package com.live.finance.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.live.finance.data.model.Budget
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun BudgetListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.budget
    var rows by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("预算管理", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无预算", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { b ->
                Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FText(b.title.ifBlank { b.budgetType }, 15f, FontWeight.Medium, colors.textPrimary, Modifier.weight(1f))
                        FText("${Money.format(b.usedAmount)} / ${Money.format(b.budgetAmount)}", 13f, FontWeight.Medium,
                            if (b.isOverBudget) colors.danger else colors.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 自绘进度条（颜色按是否超支）
                    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.bgThird, RoundedCornerShape(3.dp))) {
                        Box(
                            Modifier.fillMaxWidth(b.ratio).height(6.dp)
                                .background(if (b.isOverBudget) colors.danger else colors.primary, RoundedCornerShape(3.dp)),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    FText(
                        if (b.isOverBudget) "超支 ${Money.format(-b.remain)}" else "剩余 ${Money.format(b.remain)}",
                        11f, color = if (b.isOverBudget) colors.danger else colors.textTertiary,
                    )
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
