package com.live.finance.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import kotlin.math.abs

/** 任意值转 Double（对齐 web 的 toNumber）。 */
fun toNum(v: Any?): Double {
    val n = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
    return n ?: 0.0
}

/** 截断到 digits 位小数并去掉尾随 0（对齐 web trimDecimal）。 */
fun trimDecimal(value: Double, digits: Int = 3): String {
    val fixed = String.format("%.${digits}f", value)
    return fixed.replace(Regex("\\.?0+$"), "")
}

/**
 * 金额单位缩写（对齐 web 的 formatAmount）：亿 / 千万 / 万 / 原始，保负号。
 * 用于理财域概览卡、收益日历等。
 */
fun fundAmount(v: Double): String {
    if (!v.isFinite()) return "0"
    val sign = if (v < 0) "-" else ""
    val a = abs(v)
    return when {
        a >= 100_000_000 -> "$sign${trimDecimal(a / 100_000_000)}亿"
        a >= 10_000_000 -> "$sign${trimDecimal(a / 10_000_000)}千万"
        a >= 10_000 -> "$sign${trimDecimal(a / 10_000)}万"
        else -> "$sign${trimDecimal(a)}"
    }
}

/** 收益率字符串（对齐 web formatRate）。 */
fun fundRate(v: Double): String = "${trimDecimal(v)}%"

/** 日期归一（对齐 web parseFundDate）：YYYYMMDD → YYYY-MM-DD。 */
fun parseFundDate(value: String): String {
    val raw = value.trim()
    if (Regex("^\\d{8}$").matches(raw)) return raw.replace(Regex("^(\\d{4})(\\d{2})(\\d{2})$"), "$1-$2-$3")
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw)) return raw
    return ""
}

/** 理财概览统计项（标签 + 数值，数值可着色）。 */
@Composable
fun FundOverviewItem(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color?, modifier: Modifier) {
    val colors = LocalAppColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FText(label, 12f, FontWeight.Normal, colors.textSecondary)
        FText(value, 16f, FontWeight.Bold, valueColor ?: colors.textPrimary)
    }
}
