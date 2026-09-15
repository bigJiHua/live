package com.live.finance.ui.common

import androidx.compose.ui.graphics.Color

/**
 * 收支文字配色，对齐 web useMoneyColor 默认「red-in」：收入=红 #ee0a24，支出=绿 #07c160。
 * （时间格式化见 core/TimeFmt.kt。）后续可在设置页加 red-in/red-out 切换。
 */
object MoneyColor {
    val income = Color(0xFFEE0A24)
    val expense = Color(0xFF07C160)
    val incomeText = Color(0xFFC0102A)
    val expenseText = Color(0xFF0A7A45)
}
