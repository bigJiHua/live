package com.live.finance.ui.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.resolveMoneyColors

/**
 * 收支文字配色（web `useMoneyColor` 默认 `red-in`：收入=红 #ee0a24、支出=绿 #07c160；可切 `red-out` 互换）。
 *
 * 由 `AppGraph` 在启动读取偏好、以及设置页切换时调用 [apply] 注入。
 * 内部是 Compose 快照状态，页面里直接读 `MoneyColor.income` 即会随设置变化自动重组（无需改调用点）。
 */
object MoneyColor {
    private val state = mutableStateOf(resolveMoneyColors(MONEY_RED_IN))

    val income: Color get() = state.value.income
    val incomeText: Color get() = state.value.incomeText
    val expense: Color get() = state.value.expense
    val expenseText: Color get() = state.value.expenseText

    fun apply(mode: String) {
        state.value = resolveMoneyColors(mode)
    }
}
