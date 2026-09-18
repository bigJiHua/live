package com.live.finance.theme

import androidx.compose.ui.graphics.Color

/** 收支颜色模式（对应 web `localStorage['money-color-mode']`）。 */
const val MONEY_RED_IN = "red-in"   // 红收绿支（默认）
const val MONEY_RED_OUT = "red-out" // 红出绿收

/** 收支文字四色（金额主色 + 落在底色上的衍生小字色）。 */
data class MoneyColors(
    val income: Color,
    val incomeText: Color,
    val expense: Color,
    val expenseText: Color,
)

/**
 * 逐字对齐 web `web/src/composables/useMoneyColor.js` 的 `resolveColors()`：
 * 两套模式只是「红/绿」互换，色值与主题预设无关（固定 hex）。
 */
fun resolveMoneyColors(mode: String): MoneyColors {
    val incomeGreen = Color(0xFF07C160)
    val incomeGreenText = Color(0xFF0A7A45)
    val expenseRed = Color(0xFFEE0A24)
    val expenseRedText = Color(0xFFC0102A)
    return if (mode == MONEY_RED_OUT) {
        MoneyColors(income = incomeGreen, incomeText = incomeGreenText, expense = expenseRed, expenseText = expenseRedText)
    } else {
        MoneyColors(income = expenseRed, incomeText = expenseRedText, expense = incomeGreen, expenseText = incomeGreenText)
    }
}
