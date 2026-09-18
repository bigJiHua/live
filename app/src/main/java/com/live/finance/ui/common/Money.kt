package com.live.finance.ui.common

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.truncate

/** 金额展示工具（对应 web utils/money.js 常见用法）。原生统一用 Double→两位小数展示，保留正负号。 */
object Money {
    /** 千分位两位小数，保符号，如 -128.5 → "-128.50"。 */
    fun format(value: Double): String =
        String.format(Locale.US, "%,.2f", value)

    /**
     * 逐字对齐 web `utils/money.js` 的 `formatMoney`：**截断**（不四舍五入）到最多 4 位小数、
     * 最少 2 位（人民币到分）、带千分位、保负号。
     *
     * 与 [format] 的区别正是外币场景：1 HKD @ 86.58 → 0.8658，本函数显示 "0.8658"，[format] 会显示 "0.87"。
     * web 的记账页（汇率折算提示）用的是本函数。
     */
    fun formatMoney(value: Double): String {
        if (!value.isFinite()) return "0.00"
        val neg = value < 0
        // 先按 6 位小数清浮点噪声（对应 JS `Number(abs.toFixed(6))`），再截断到 4 位
        val cleaned = String.format(Locale.US, "%.6f", abs(value)).toDouble()
        val truncated = truncate(cleaned * 10000) / 10000
        var frac = String.format(Locale.US, "%.4f", truncated).substringAfter('.')
        var len = 4
        while (len > 2 && frac[len - 1] == '0') len--   // 多余尾零截到至少 2 位
        frac = frac.substring(0, len)
        val intPart = String.format(Locale.US, "%,d", truncate(truncated).toLong())
        return (if (neg) "-" else "") + intPart + if (frac.isNotEmpty()) ".$frac" else ""
    }

    /** 带收支符号：收入 +、支出 -。 */
    fun signed(value: Double, isIncome: Boolean): String =
        (if (isIncome) "+" else "-") + format(if (value < 0) -value else value)

    /**
     * 缩略格式，逐字对齐 web Home.formatMoney：≥亿 用「x.xxx亿」(截断到3位小数)、≥万「x.xxx万」、否则「int.xx」2位小数，保留负号。
     */
    fun abbrev(value: Double): String {
        val neg = value < 0
        val abs = if (neg) -value else value
        val body = when {
            abs >= 100000000 -> {
                val yi = abs / 100000000; val ip = floor(yi).toInt()
                var dp = ((yi - ip) * 1000).roundToInt(); if (dp >= 1000) dp = 999
                "$ip." + dp.toString().padStart(3, '0') + "亿"
            }
            abs >= 10000 -> {
                val wan = abs / 10000; val ip = floor(wan).toInt()
                var dp = ((wan - ip) * 1000).roundToInt(); if (dp >= 1000) dp = 999
                "$ip." + dp.toString().padStart(3, '0') + "万"
            }
            else -> {
                val ip = floor(abs).toInt(); var dp = ((abs - ip) * 100).roundToInt(); if (dp >= 100) dp = 99
                "$ip." + dp.toString().padStart(2, '0')
            }
        }
        return (if (neg) "-" else "") + body
    }
}
