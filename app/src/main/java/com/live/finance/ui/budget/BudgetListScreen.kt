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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Budget
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanTabs
import com.live.vant.nav.VanTabItem
import kotlin.math.roundToInt

/**
 * 预算登记列表（web `views/Finance/budget/List.vue`）：
 * 渐变统计卡（总预算/总支出/剩余 + 整体进度）→ 全部/出行/购物/餐饮 tabs →
 * 预算卡（左侧周期色条 + 类型/周期 tag + 进度条 + 超支标签）→ 底部「登记预算」→ 类型选择。
 */
@Composable
fun BudgetListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val repo = App.of(LocalContext.current).graph.budget

    var rows by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        rows = (repo.list() as? ApiResult.Ok)?.data ?: emptyList()
        loading = false
    }

    // web calcExpense 口径
    val expense = { b: Budget -> BudgetShared.expenseOf(b) }
    val totalBudget = rows.sumOf { it.budgetAmount }
    val totalExpense = rows.sumOf { expense(it) }
    val totalBalance = totalBudget - totalExpense
    val totalProgress = if (totalBudget == 0.0) 0
    else ((totalExpense / totalBudget * 100).roundToInt()).coerceIn(0, 100)

    val filtered = if (tab == "all") rows else rows.filter { it.budgetType == tab }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(bottom = 88.dp)) {
                // —— 顶部渐变统计 ——
                item {
                    Column(
                        Modifier.padding(12.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(tokens.primary, tokens.grad)))
                            .padding(16.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummaryItem("总预算", "¥${Money.format(totalBudget)}", Color.White, Modifier.weight(1f))
                            SummaryItem("总支出", "¥${Money.format(totalExpense)}", Color.White.copy(alpha = 0.95f), Modifier.weight(1f))
                            SummaryItem(
                                "剩余", "¥${Money.format(totalBalance)}",
                                if (totalBalance >= 0) Color(0xFF90F0C8) else Color.White.copy(alpha = 0.95f),
                                Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // 整体进度条（底白 30%，填充 #90f0c8）
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.3f)),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(totalProgress / 100f)
                                    .height(6.dp).clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF90F0C8)),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        FText("整体进度 $totalProgress%", 11f, color = Color.White.copy(alpha = 0.85f))
                    }
                }

                // —— 类型过滤 tabs ——
                item {
                    VanTabs(
                        active = tab,
                        onActiveChange = { tab = it },
                        tabs = listOf(
                            VanTabItem("all", "全部"),
                            VanTabItem("行", "出行"),
                            VanTabItem("买", "购物"),
                            VanTabItem("吃", "餐饮"),
                        ),
                        content = { },
                    )
                }

                // —— 预算卡 ——
                items(filtered, key = { it.id }) { b ->
                    BudgetCard(b, onClick = { nav.navigate(Routes.budgetDetail(b.id)) })
                }

                if (!loading && filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无预算")
                        }
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            }

            // 底部固定「登记预算」（web bottom-actions fixed）
            AppButton(
                text = "登记预算",
                onClick = { nav.navigate(Routes.BUDGET_TYPE_SELECT) },
                type = AppButtonType.Primary, size = AppButtonSize.Large,
                block = true, round = true, icon = "plus",
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = Color.White.copy(alpha = 0.85f))
        FText(value, 18f, FontWeight.Bold, valueColor)
    }
}

@Composable
private fun BudgetCard(b: Budget, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val used = BudgetShared.expenseOf(b)
    val progress = BudgetShared.progressOf(b)
    val over = BudgetShared.isOver(b)
    val remaining = b.budgetAmount - used

    Box(
        Modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
            .clickable { onClick() }.padding(14.dp),
    ) {
        // 左侧周期色条（card-cycle-bar）
        Box(
            Modifier.align(Alignment.CenterStart).width(4.dp).height(200.dp)
                .background(BudgetShared.cycleColor(b.cycle)),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    FText(b.title, 15f, FontWeight.SemiBold, colors.textPrimary)
                    if (b.route.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VanIcon("location-o", size = 12.sp, color = colors.textSecondary)
                            FText(" ${b.route}", 12f, color = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FText(b.planDate, 11f, color = colors.textTertiary)
                        VanTag(BudgetShared.typeName(b.budgetType),
                            type = BudgetShared.typeTagColor(b.budgetType).toVanType(),
                            size = VanTagSize.Small)
                        VanTag(BudgetShared.cycleName(b.cycle),
                            type = VanTagType.Default, plain = true, size = VanTagSize.Small)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    FText("¥${Money.format(b.budgetAmount)}", 16f, FontWeight.Bold, colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    FText(
                        if (remaining >= 0) "剩余 ¥${Money.format(remaining)}" else "超支 ¥${Money.format(-remaining)}",
                        12f,
                        color = if (remaining >= 0) Color(0xFF07C160) else colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 进度条（超支红渐变 / 正常主色→绿渐变）
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(colors.bgThird),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress / 100f).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (over) Brush.horizontalGradient(listOf(Color(0xFFEE0A24), Color(0xFFFF6034)))
                            else Brush.horizontalGradient(listOf(colors.primary, Color(0xFF07C160))),
                        ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FText("已 ¥${Money.format(used)}", 11f, color = colors.textTertiary)
                FText("$progress%", 11f, FontWeight.SemiBold, colors.primary)
            }
            if (over) {
                Spacer(Modifier.height(8.dp))
                VanTag("已超支", type = VanTagType.Danger, size = VanTagSize.Small)
            }
        }
    }
}

/** BudgetShared 的 tag 色 → vant VanTagType。 */
internal fun VanTagColor.toVanType(): VanTagType = when (this) {
    VanTagColor.Primary -> VanTagType.Primary
    VanTagColor.Success -> VanTagType.Success
    VanTagColor.Warning -> VanTagType.Warning
    VanTagColor.Danger -> VanTagType.Danger
    VanTagColor.Default -> VanTagType.Default
}

/** Detail/表单用的支出类型色（web getExpenseTypeColor：行=primary 吃=danger 喝=success 买=warning 住=purple 玩=orange）。 */
internal fun expenseTypeColor(type: String): Color = when (type) {
    "行" -> Color(0xFF1989FA)
    "吃" -> Color(0xFFEE0A24)
    "喝" -> Color(0xFF07C160)
    "买" -> Color(0xFFFF976A)
    "住" -> Color(0xFF7232DD)
    "玩" -> Color(0xFFFF976A)
    else -> Color(0xFF969799)
}

/** 购物优先级色（web getPriorityColor：必买=danger 想要=warning）。 */
internal fun priorityColor(priority: String): VanTagType = when (priority) {
    "必买" -> VanTagType.Danger
    "想要" -> VanTagType.Warning
    else -> VanTagType.Default
}
