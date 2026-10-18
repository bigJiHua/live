package com.live.finance.ui.budget

import androidx.compose.ui.graphics.Color
import com.live.finance.data.model.Budget
import com.live.vant.theme.VantColors
import com.live.finance.theme.VanGrayFixed
import com.live.finance.theme.VanPurpleFixed

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

    // ========== 周期（日/周/月/季/年） ==========

    fun cycleName(cycle: String): String = when (cycle) {
        "日" -> "日计划"; "周" -> "周计划"; "月" -> "月度"; "季" -> "季度"; "年" -> "年度"; else -> cycle
    }

    // ========== 支出计算（web calcExpense：行按明细折CNY、买/吃按 actual_total，兜底 used_amount） ==========

    /**
     * web `.card-cycle-bar.*`（`List.vue:359-363`）—— ⚠ 表单实际只产生 **月/季/年**（三个
     * `cycleColumns` 都只列这三项）；`日`/`周` 是样式里的**防御性 class**，数据不会出现，
     * 映射保留只为兼容历史/异常值。三种真实周期分属两类写法：
     *  - 月 `var(--van-orange, #ff976a)` → 两模式同值 #ff976a            → 固定
     *  - 季 `var(--van-purple, #7232dd)` → 两模式同值 #7232dd            → 固定
     *  - 年 `var(--van-danger-color, #ee0a24)` → **:root 有定义** = --theme-danger → **随主题**
     *
     * 口径沿革：第四十二轮曾按负责人指示全部改主题色，第四十四轮恢复（「我忘记这茬了」）；
     * 第五十二轮发现「五色」实为三色——日/周永不出现在真实数据里。
     */
    fun cycleColor(cycle: String, colors: VantColors, green: Color): Color = when (cycle) {
        "日" -> green
        "周" -> colors.primary
        "月" -> Color(0xFFFF976A)
        "季" -> VanPurpleFixed   // --van-purple（两模式同值）
        "年" -> colors.danger
        else -> VanGrayFixed     // --van-gray（两模式同值）
    }

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
