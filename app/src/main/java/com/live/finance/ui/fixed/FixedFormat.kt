package com.live.finance.ui.fixed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.data.model.FixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanTagType
import com.live.vant.icon.VanIcon
import java.time.LocalDate
import java.util.Locale

/**
 * 固定资产域专用格式化（**不复用 `Money`**：web 本域用的是自己那份 `formatAmount`）。
 *
 * web `List.vue`：`formatAmount = (v) => (parseFloat(v) || 0).toFixed(2)` —— **无千分位**，
 * 与 `Money.format`（`%,.2f` 带千分位）不同，别混用。
 */
internal fun fixed2(v: Double): String = String.format(Locale.US, "%.2f", v)

/** JS `String(number)` 语义：整数不带 `.0`（表单回填用，如 `18999` / `3.5`）。 */
internal fun jsNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/**
 * web `List.vue` 的 `formatUsedTime` + `calculateUsage` + `formatUsageTime`：
 * 结束日 = **已归档且 `scrap_date` 非空** ? `scrap_date` : 今天；
 * 起止按「日借位 → 月借位」相减（借位用**上个月的天数**），拼 `X年Y个月Z天`，全 0 时 `0天`。
 */
internal fun usedTimeLabel(a: FixedAsset): String {
    if (a.buyDate.isBlank()) return "-"
    val start = runCatching { LocalDate.parse(a.buyDate.take(10)) }.getOrNull() ?: return "-"
    val endStr = if (a.archived && a.scrapDate.isNotBlank()) a.scrapDate.take(10) else LocalDate.now().toString()
    val end = runCatching { LocalDate.parse(endStr) }.getOrNull() ?: return "-"
    if (end < start) return "-"

    var years = end.year - start.year
    var months = end.monthValue - start.monthValue
    var days = end.dayOfMonth - start.dayOfMonth
    if (days < 0) {
        months--
        days += end.minusMonths(1).lengthOfMonth()   // JS: new Date(end.y, end.m, 0).getDate()
    }
    if (months < 0) {
        years--
        months += 12
    }
    val out = buildString {
        if (years > 0) append("${years}年")
        if (months > 0) append("${months}个月")
        if (days > 0) append("${days}天")
    }
    return out.ifEmpty { "0天" }
}

/** web `formatDailyCost`：购买价 ÷ (使用年限 × 365)，两位小数（年限 0 时按 1 算）。 */
internal fun dailyCost(a: FixedAsset): String {
    val years = if (a.useYears > 0.0) a.useYears else 1.0
    return fixed2(a.buyPrice / (years * 365))
}

/** web `getStatusType` → `app-tag` 的 type（原生对应 [VanTagType]）。 */
internal fun String.toVanTagType(): VanTagType = when (this) {
    "success" -> VanTagType.Success
    "danger" -> VanTagType.Danger
    "primary" -> VanTagType.Primary
    "warning" -> VanTagType.Warning
    else -> VanTagType.Default
}

/**
 * **只读选择行**（对齐 web `app-field[readonly]` 的度量：根 `padding 10/16` + 1px 通栏底线、
 * 标签 13sp `text-secondary` + 下距 6、值 14sp `text-primary`、占位 `text-tertiary`）。
 *
 * 用于「品类标签 / 购买日期」这类点一下弹选择器的字段（原生 [com.live.finance.ui.common.AppField]
 * 目前没有 readonly/onClick，故在本域内自带一个，避免改共享底座）。
 */
@Composable
internal fun FixedReadonlyField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 13f, FontWeight.Normal, colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(
                value.ifEmpty { placeholder },
                14f, FontWeight.Normal,
                if (value.isEmpty()) colors.textTertiary else colors.textPrimary,
                Modifier.weight(1f),
            )
            VanIcon(name = "arrow", size = 14.sp, color = colors.textTertiary)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
