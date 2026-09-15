package com.live.finance.ui.common

import java.util.Locale

/** 金额展示工具（对应 web utils/money.js 常见用法）。原生统一用 Double→两位小数展示，保留正负号。 */
object Money {
    /** 千分位两位小数，保符号，如 -128.5 → "-128.50"。 */
    fun format(value: Double): String =
        String.format(Locale.US, "%,.2f", value)

    /** 带收支符号：收入 +、支出 -。 */
    fun signed(value: Double, isIncome: Boolean): String =
        (if (isIncome) "+" else "-") + format(if (value < 0) -value else value)
}
