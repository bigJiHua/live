package com.live.finance.ui.budget

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Budget
import com.live.finance.data.model.EatDetails
import com.live.finance.data.model.ShoppingDetails
import com.live.finance.data.model.TravelDetails
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
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/**
 * 预算详情（web `views/Finance/budget/Detail.vue`）：
 * 渐变头卡（标题/类型 tag/路线/日期 + 预算/已用/剩余 + 进度）→
 * 按类型展示明细（出行每日/购物清单/餐饮菜单）→ 备注 → 编辑/删除。
 * 超额判定以前端剩余为准（web 注释口径）。
 */
@Composable
fun BudgetDetailScreen(nav: NavHostController, budgetId: String) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val repo = App.of(LocalContext.current).graph.budget
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<Budget?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(budgetId) {
        detail = (repo.detail(budgetId) as? ApiResult.Ok)?.data
        loading = false
    }

    val b = detail
    val used = b?.let { BudgetShared.expenseOf(it) } ?: 0.0
    val remaining = (b?.budgetAmount ?: 0.0) - used
    val progress = if (b == null || b.budgetAmount <= 0) 0
    else ((used / b.budgetAmount * 100).toInt()).coerceIn(0, 100)
    val isOver = remaining < 0

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            } else if (b == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VanEmpty(description = "预算不存在")
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // ===== 渐变头卡 =====
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(tokens.primary, tokens.grad)))
                            .padding(20.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            FText(b.title, 18f, FontWeight.SemiBold, Color.White,
                                modifier = Modifier.weight(1f))
                            VanTag(BudgetShared.typeName(b.budgetType),
                                type = BudgetShared.typeTagColor(b.budgetType).toVanType(),
                                size = VanTagSize.Medium)
                        }
                        if (b.route.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VanIcon("location-o", size = 13.sp, color = Color.White.copy(alpha = 0.9f))
                                FText(" ${b.route}", 14f, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VanIcon("calendar-o", size = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            FText(" ${b.planDate} · ${b.cycle}", 13f, color = Color.White.copy(alpha = 0.85f))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AmountItem("预算", "¥${Money.format(b.budgetAmount)}", Color.White, Modifier.weight(1f))
                            AmountItem("已用", "¥${Money.format(used)}", Color.White.copy(alpha = 0.95f), Modifier.weight(1f))
                            AmountItem(
                                "剩余", "¥${Money.format(remaining)}",
                                if (remaining >= 0) Color(0xFF90F0C8) else colors.danger, Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxWidth(progress / 100f).height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isOver) colors.danger else Color(0xFF07C160)))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText("使用率 $progress%", 12f, color = Color.White.copy(alpha = 0.85f))
                            if (isOver) FText("已超支", 12f, FontWeight.SemiBold, colors.danger)
                        }
                    }

                    // ===== 出行明细 =====
                    val d = b.details
                    if (b.budgetType == "行" && d is TravelDetails) {
                        if (d.exchangeRates.isNotEmpty()) {
                            SectionCard("汇率设置", "exchange") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    d.exchangeRates.forEach { rate ->
                                        FText("${rate.currency}: ${rate.value}", 12f,
                                            color = colors.textSecondary,
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                                .background(colors.bgPage).padding(horizontal = 10.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        SectionCard("每日消费明细", "orders-o") {
                            if (d.days.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    FText("暂无每日明细", 13f, color = colors.textTertiary)
                                }
                            }
                            d.days.forEach { day ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(colors.bgThird).padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VanIcon("calendar-o", size = 13.sp, color = colors.primary)
                                        FText(" ${day.date}", 14f, FontWeight.SemiBold, colors.primary)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (day.items.isEmpty()) {
                                        FText("暂无消费记录", 13f, color = colors.textTertiary,
                                            modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                    day.items.forEach { item ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)) {
                                                VanTag(item.type, plain = true, size = VanTagSize.Small,
                                                    type = VanTagType.Default,
                                                    color = expenseTypeColor(item.type),
                                                    textColor = expenseTypeColor(item.type))
                                                FText(" ${item.description.ifEmpty { "无描述" }}", 13f,
                                                    color = colors.textPrimary)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                FText("${trimAmt(item.amount)} ${item.currency}", 14f,
                                                    FontWeight.SemiBold, colors.danger)
                                                if (item.currency != "CNY") {
                                                    FText("≈ ¥${Money.format(item.cnyAmount)}", 11f,
                                                        color = colors.textTertiary)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        FText("当天合计", 13f, color = colors.textSecondary)
                                        FText("¥${Money.format(d.dayTotal(day))}", 13f, FontWeight.SemiBold, colors.primary)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }

                    // ===== 购物明细 =====
                    if (b.budgetType == "买" && d is ShoppingDetails) {
                        SectionCard("购物清单", "shopping-cart-o") {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(colors.bgPage).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                ShopStat("预计总额", "¥${Money.format(d.estimatedTotal)}", Modifier.weight(1f))
                                ShopStat("实际总额", "¥${Money.format(d.actualTotal)}", Modifier.weight(1f), colors.danger)
                                ShopStat("已购/总数", "${d.purchasedCount}/${d.items.size}", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            if (d.items.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    FText("暂无购物清单", 13f, color = colors.textTertiary)
                                }
                            }
                            d.items.forEachIndexed { i, item ->
                                Row(verticalAlignment = Alignment.Top) {
                                    IndexBadge(i + 1, colors.primary)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VanTag(item.priority.ifEmpty { "未设置" },
                                                type = priorityColor(item.priority), size = VanTagSize.Small)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        FText(item.name.ifEmpty { "未命名商品" }, 14f, FontWeight.SemiBold, colors.textPrimary)
                                        Spacer(Modifier.height(4.dp))
                                        Row {
                                            if (item.category.isNotEmpty()) FText(item.category, 12f, color = colors.textTertiary)
                                            if (item.shop.isNotEmpty()) FText(" · ${item.shop}", 12f, color = colors.textTertiary)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row {
                                            FText("预计 ¥${Money.format(item.price)} × ${item.quantity}", 13f, color = colors.textSecondary)
                                            if (item.actualPrice > 0) {
                                                FText("  实付 ¥${Money.format(item.actualPrice)}", 13f, FontWeight.SemiBold, colors.danger)
                                            }
                                        }
                                        if (item.purchaseDate.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                VanIcon("clock-o", size = 11.sp, color = colors.textTertiary)
                                                FText(" ${item.purchaseDate}", 12f, color = colors.textTertiary)
                                            }
                                        }
                                        if (item.notes.isNotEmpty()) {
                                            FText(item.notes, 12f, color = colors.textTertiary,
                                                modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }

                    // ===== 餐饮明细 =====
                    if (b.budgetType == "吃" && d is EatDetails) {
                        SectionCard("菜单明细", "fire-o") {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AEE0A24)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                FText("实际消费", 14f, color = colors.textSecondary)
                                FText("¥${Money.format(d.actualTotal)}", 18f, FontWeight.Bold, colors.danger)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (d.dishes.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    FText("暂无菜单明细", 13f, color = colors.textTertiary)
                                }
                            }
                            d.dishes.forEachIndexed { i, dish ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.Top) {
                                    IndexBadge(i + 1, colors.danger)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        FText(dish.name.ifEmpty { "未命名菜品" }, 14f, color = colors.textPrimary)
                                        if (dish.notes.isNotEmpty()) {
                                            FText(dish.notes, 12f, color = colors.textTertiary,
                                                modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        FText("¥${Money.format(dish.price)} × ${dish.quantity}", 13f, color = colors.textSecondary)
                                        FText("= ¥${Money.format(d.dishSubtotal(dish))}", 13f, FontWeight.SemiBold, colors.danger)
                                    }
                                }
                            }
                        }
                    }

                    // ===== 备注（行类型不显示——web detailNotes 口径）=====
                    val notes = when (val dd = b.details) {
                        is TravelDetails -> ""
                        is ShoppingDetails -> dd.notes
                        is EatDetails -> dd.notes
                        else -> ""
                    }
                    if (notes.isNotEmpty()) {
                        SectionCard("备注", "comment-o") {
                            FText(notes, 14f, color = colors.textSecondary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    // ===== 操作 =====
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val editRoute = when (b.budgetType) {
                            "买" -> Routes.BUDGET_SHOPPING; "行" -> Routes.BUDGET_TRAVEL; else -> Routes.BUDGET_EAT
                        }
                        AppButton("编辑预算",
                            onClick = { nav.navigate("$editRoute?id=${b.id}") },
                            type = AppButtonType.Primary, size = AppButtonSize.Large,
                            block = true, round = true)
                        AppButton("删除预算",
                            onClick = { showDelete = true },
                            type = AppButtonType.Default, size = AppButtonSize.Large,
                            block = true, round = true)
                    }
                }
            }
        }
    }

    VanConfirmDialog(
        show = showDelete,
        title = "确认删除",
        message = "删除后不可恢复，是否继续？",
        onConfirm = {
            showDelete = false
            scope.launch {
                when (val r = repo.delete(budgetId)) {
                    is ApiResult.Ok -> {
                        toast.show("删除成功")
                        nav.popBackStack(Routes.BUDGET, false)
                    }
                    is ApiResult.Unauthorized -> nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                    is ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
                    is ApiResult.Fail -> toast.show(r.message.ifEmpty { "删除失败" })
                    else -> toast.show("删除失败")
                }
            }
        },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )
}

@Composable
private fun AmountItem(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = Color.White.copy(alpha = 0.85f))
        FText(value, 18f, FontWeight.Bold, valueColor)
    }
}

@Composable
private fun SectionCard(title: String, icon: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)
        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)) {
            VanIcon(icon, size = 16.sp, color = colors.primary)
            FText(" $title", 15f, FontWeight.SemiBold, colors.textPrimary)
        }
        content()
    }
}

@Composable
private fun ShopStat(label: String, value: String, modifier: Modifier, valueColor: Color = LocalAppColors.current.textPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 11f, color = LocalAppColors.current.textTertiary)
        FText(value, 14f, FontWeight.SemiBold, valueColor)
    }
}

@Composable
private fun IndexBadge(n: Int, color: Color) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) { FText("$n", 11f, FontWeight.SemiBold, Color.White) }
}
