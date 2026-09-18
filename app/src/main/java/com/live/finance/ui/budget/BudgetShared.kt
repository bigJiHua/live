package com.live.finance.ui.budget

import androidx.compose.ui.graphics.Color
import com.live.finance.data.model.Budget

/**
 * 预算域共享规则（web List.vue / Detail.vue 的类型/周期映射与支出计算口径）。
 */
object BudgetShared {

    // ========== 类型（budget_type：行/买/吃） ==========

    fun typeName(type: String): String = when (type) {
        "行" -> "出行"; "买" -> "购物"; "吃" -> "餐饮"; else -> type
    }

    /** web getTypeColor：行=primary 买=danger 吃=warning。 */
    fun typeTagColor(type: String): VanTagColor = when (type) {
        "行" -> VanTagColor.Primary; "买" -> VanTagColor.Danger; "吃" -> VanTagColor.Warning; else -> VanTagColor.Default
    }

    /** web stat-icon 渐变主色（吃：orange→danger）。 */
    fun typeGradient(type: String): Pair<Color, Color> = when (type) {
        "行" -> Color(0xFF4A90E2) to Color(0xFF3B7BD4)   // 主题 primary 渐变（UI 层实际用 tokens 覆盖）
        "买" -> Color(0xFFFF6034) to Color(0xFFEE0A24)
        "吃" -> Color(0xFFFF976A) to Color(0xFFEE0A24)
        else -> Color(0xFF909399) to Color(0xFF73767C)
    }

    // ========== 周期（日/周/月/季/年） ==========

    fun cycleName(cycle: String): String = when (cycle) {
        "日" -> "日计划"; "周" -> "周计划"; "月" -> "月度"; "季" -> "季度"; "年" -> "年度"; else -> cycle
    }

    fun cycleColor(cycle: String): Color = when (cycle) {
        "日" -> Color(0xFF07C160)
        "周" -> Color(0xFF1989FA)
        "月" -> Color(0xFFFF976A)
        "季" -> Color(0xFF7232DD)
        "年" -> Color(0xFFEE0A24)
        else -> Color(0xFF969799)
    }

    // ========== 支出计算（web calcExpense：行按明细折CNY、买/吃按 actual_total，兜底 used_amount） ==========

    fun expenseOf(b: Budget): Double = when (val d = b.details) {
        is com.live.finance.data.model.TravelDetails -> d.totalCny
        is com.live.finance.data.model.ShoppingDetails -> d.actualTotal
        is com.live.finance.data.model.EatDetails -> d.actualTotal
        else -> b.usedAmount
    }

    fun progressOf(b: Budget): Int {
        if (b.budgetAmount <= 0) return 0
        return (expenseOf(b) / b.budgetAmount * 100).toInt().coerceIn(0, 100)
    }

    fun isOver(b: Budget): Boolean = expenseOf(b) > b.budgetAmount
}

/** web app-tag 的 type 字符串（原生映射到 VanTagType）。 */
enum class VanTagColor { Default, Primary, Success, Warning, Danger }
