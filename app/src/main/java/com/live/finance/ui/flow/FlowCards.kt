package com.live.finance.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.BankIconView
import com.live.finance.ui.common.FlowItem
import com.live.finance.ui.common.FlowKind
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.currencySymbol
import com.live.finance.ui.common.flowCardBankIcon
import com.live.finance.ui.common.flowCardBankName
import com.live.finance.ui.common.flowCardNameById
import com.live.finance.ui.common.flowCategoryName
import com.live.finance.ui.common.flowCompactCardLabel
import com.live.finance.ui.common.flowPayMethod
import com.live.finance.ui.common.isForeignCurrency
import com.live.finance.ui.common.isInstallmentFlow
import com.live.finance.ui.common.isRepay
import com.live.vant.icon.VanIcon
import java.time.LocalDate
import java.util.Locale

/** web `--van-green`（buildVars 不覆盖它，两模式同值）。 */
val VanGreenFixed = Color(0xFF07C160)

/** web `--van-orange`（同理，固定值）。 */
val VanOrangeFixed = Color(0xFFFF976A)

/** web `formatAmount = Number(amount).toFixed(2)`：**无千分位**、四舍五入两位小数。 */
fun flowAmount2(value: Double): String = String.format(Locale.US, "%.2f", value)

/** web `formatTime(ts)`：毫秒时间戳 → `HH:mm`。 */
fun flowTime(createTime: String): String = com.live.finance.core.TimeFmt.hm(createTime)

private val WEEK_CN = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

/** web `formatDateHeader`：今天 / 昨天 / `M月D日 ddd`。 */
fun flowDayHeader(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val today = LocalDate.now()
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${d.monthValue}月${d.dayOfMonth}日 ${WEEK_CN[d.dayOfWeek.value % 7]}"
    }
}

/** web `formatWeekday`：`周X`。 */
fun flowWeekday(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
    return WEEK_CN[d.dayOfWeek.value % 7]
}

/** CSS `border: 2px dashed <color>`（Compose 无虚线 border → drawBehind + dashPathEffect，内缩半个描边宽）。 */
fun Modifier.flowDashedBorder(color: Color, radius: Dp, stroke: Dp = 2.dp): Modifier = drawBehind {
    val sw = stroke.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 3f, sw * 3f))),
    )
}

/** 深色主题等价判断（web `html[data-theme-mono="1"]` 下一部分置灰会被反转）。 */
@Composable
private fun isMonoTheme(): Boolean = LocalAppColors.current.bgPage.luminance() < 0.5f

/** web `.fc-inst-tag`（「分期」小标签）。 */
@Composable
private fun InstTag() {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .padding(start = 6.dp)
            .background(Color(0x24FF976A), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp),
    ) {
        FText("分期", 11f, FontWeight.Medium, colors.warning)
    }
}

/** web `.fc-bank-chip`：页面底色 3 圆角小药丸 + 银行图标 12 + 卡名 10px。 */
@Composable
private fun BankChip(row: FlowRow, cards: List<Card>, banks: List<Category>) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .padding(start = 2.dp)
            .background(colors.bgPage, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BankIconView(
            src = flowCardBankIcon(row, cards, banks),
            name = flowCardBankName(row, cards, banks),
            size = 12.dp,
        )
        FText(flowCardNameById(row.cardId, cards, banks), 10f, FontWeight.Normal, colors.textSecondary)
    }
}

/**
 * 列表页普通流水卡片（web `.flow-card`）：
 *  - 普通收支：`↓/↑` 箭头 + 分类(14/500) + 交易方式(11px) + 银行 chip，右侧 18/700 金额 + 10px 时间；
 *  - 对外转账 / 给我转账：**无箭头、无右侧时间**，右上角 `.fc-badge` 徽标，`交易方式 · 时间` 进 meta；
 *  - 信用卡还款：置灰（web `grayscale(1)+opacity(.55)`；深色主题下 web 反转 → 原生同款判断，用三级色近似灰度）。
 */
@Composable
fun FlowCardRow(
    item: FlowItem,
    cards: List<Card>,
    banks: List<Category>,
    onClick: () -> Unit,
) {
    val row = item.data ?: return
    val colors = LocalAppColors.current
    val dim = isRepay(row) && !isMonoTheme()
    val badged = item.kind == FlowKind.ExternalTransfer || item.kind == FlowKind.IncomingTransfer
    val income = row.isIncome
    val amtColor = when {
        dim -> colors.textTertiary
        income -> MoneyColor.income
        else -> MoneyColor.expense
    }

    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (dim) 0.55f else 1f)
                .background(colors.bgCard, RoundedCornerShape(5.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!badged) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {   // web `.fc-arrow { width:22 }`
                    FText(if (income) "↓" else "↑", 18f, FontWeight.Bold, amtColor)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(
                        flowCategoryName(row), 14f, FontWeight.Medium,
                        if (dim) colors.textTertiary else colors.textPrimary,
                    )
                    if (isInstallmentFlow(row)) InstTag()
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FText(
                        flowPayMethod(row) + if (badged) " · " + flowTime(row.createTime) else "",
                        11f, FontWeight.Normal, colors.textTertiary,
                    )
                    if (row.cardId.isNotEmpty() && row.cardId != "xxxx" && row.cardId != "yyyy") {
                        BankChip(row, cards, banks)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (isForeignCurrency(row)) {
                        FText(currencySymbol(row.currency), 15f, FontWeight.Medium, amtColor, Modifier.padding(end = 1.dp))
                    }
                    FText((if (income) "+" else "-") + flowAmount2(row.amount), 18f, FontWeight.Bold, amtColor)
                }
                if (!badged) {
                    Spacer(Modifier.height(2.dp))
                    FText(flowTime(row.createTime), 10f, FontWeight.Normal, colors.textTertiary)
                }
            }
        }
        if (badged) {
            // web `.fc-badge { position:absolute; top:-1px; right:10px; border-radius:0 5px 0 5px }`
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-1).dp)
                    .padding(end = 10.dp)
                    .background(
                        if (income) Color(0xFFE8F9EE) else Color(0xFFFFF7E6),
                        RoundedCornerShape(topEnd = 5.dp, bottomStart = 5.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                FText(
                    if (income) "给我转账" else "对外转账",
                    9f, FontWeight.SemiBold,
                    if (income) VanGreenFixed else VanOrangeFixed,
                )
            }
        }
    }
}

/** web `.day-head`（贴顶日头）：圆点 + 日期 + 星期 + 当日收/支。 */
@Composable
fun FlowDayHeaderRow(date: String, income: Double, expense: Double) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val isToday = runCatching { LocalDate.parse(date) }.getOrNull() == LocalDate.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPage)      // 贴顶时遮住下方内容，必须与页面底色一致
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // `.day-dot`：10px 圆 + 2px 页面底色描边；今天 = 主色 + `0 0 0 3px rgba(primary,.18)`
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(Modifier.size(16.dp).background(tokens.primary.copy(alpha = 0.18f), CircleShape))
            }
            Box(
                Modifier.size(10.dp)
                    .background(if (isToday) tokens.primary else colors.textPlaceholder, CircleShape),
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FText(flowDayHeader(date), 15f, FontWeight.SemiBold, colors.textPrimary)
            FText(flowWeekday(date), 12f, FontWeight.Normal, colors.textTertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (income > 0) FText("+" + flowAmount2(income), 12f, FontWeight.SemiBold, MoneyColor.income)
            if (expense > 0) FText("-" + flowAmount2(expense), 12f, FontWeight.SemiBold, MoneyColor.expense)
        }
    }
}

/**
 * 列表页「转账 / 提现 / 冲正」区块（web `.tf-main` / `.pd-main`）：
 * 三层（时间·标签·时间 / 金额→金额 / 银行·银行），点主体展开/收起两条明细。
 */
@Composable
fun FlowPairedBlock(
    item: FlowItem,
    cards: List<Card>,
    banks: List<Category>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDetail: (FlowRow) -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val expense = item.expense ?: return
    val income = item.income ?: return
    val reversal = item.kind == FlowKind.Reversal

    // 配色：转账=主色、提现=固定绿、冲正=三级底+灰边（web 三套 .tf-main/.pd-withdrawal/.pd-reversal）
    val bg = when (item.kind) {
        FlowKind.Withdrawal -> Color(0x0D07C160)
        FlowKind.Reversal -> colors.bgThird
        else -> tokens.primary.copy(alpha = 0.04f)
    }
    val borderColor = when (item.kind) {
        FlowKind.Withdrawal -> VanGreenFixed
        FlowKind.Reversal -> colors.border
        else -> tokens.primary
    }
    val labelText = when (item.kind) {
        FlowKind.Withdrawal -> "提现"
        FlowKind.Reversal -> "冲正"
        else -> if (item.explicit) "转账" else "疑似转账"
    }
    val labelColor = when (item.kind) {
        FlowKind.Withdrawal -> VanGreenFixed
        FlowKind.Reversal -> colors.textTertiary
        else -> tokens.primary
    }
    val outAmt = if (reversal) colors.textTertiary else MoneyColor.expense
    val inAmt = if (reversal) colors.textTertiary else MoneyColor.income
    val bankColor = if (reversal) colors.textTertiary else colors.textSecondary

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).alpha(if (reversal) 0.85f else 1f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(5.dp))
                .flowDashedBorder(borderColor, 5.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FText(flowTime(expense.createTime), 11f, FontWeight.Normal, colors.textTertiary)
                if (reversal) {
                    // web 冲正标签：`text-decoration: line-through`
                    BasicText(
                        labelText,
                        style = TextStyle(
                            color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.LineThrough,
                        ),
                    )
                } else {
                    FText(labelText, 11f, FontWeight.SemiBold, labelColor)
                }
                FText(flowTime(income.createTime), 11f, FontWeight.Normal, colors.textTertiary)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText("-" + flowAmount2(expense.amount), 19f, FontWeight.Bold, outAmt)
                when (item.kind) {
                    FlowKind.Withdrawal -> FText("→", 18f, FontWeight.Bold, VanGreenFixed)
                    FlowKind.Reversal -> VanIcon(name = "revoke", size = 18.sp, color = colors.textTertiary)
                    else -> VanIcon(name = "exchange", size = 18.sp, color = tokens.primary)
                }
                FText("+" + flowAmount2(income.amount), 19f, FontWeight.Bold, inAmt)
            }
            Row(Modifier.fillMaxWidth()) {
                // web `.tf-bank-name { max-width:45%; ellipsis }` + `justify-content: space-between`
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicText(
                        flowCardNameById(expense.cardId, cards, banks).ifEmpty { flowCompactCardLabel(expense, cards) },
                        style = TextStyle(color = bankColor, fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    BasicText(
                        flowCardNameById(income.cardId, cards, banks).ifEmpty { flowCompactCardLabel(income, cards) },
                        style = TextStyle(color = bankColor, fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp)) {   // web `.tf-detail { margin-left:14 }`
                PairedDetailLine(expense, true, onDetail)
                PairedDetailLine(income, false, onDetail)
            }
        }
    }
}

/** web `.tf-detail-item`：圆点 + 分类/方式·时间 + 金额。 */
@Composable
private fun PairedDetailLine(row: FlowRow, isExpense: Boolean, onDetail: (FlowRow) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(colors.bgCard, RoundedCornerShape(5.dp))
            .clickable { onDetail(row) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 10.dp)
                .size(7.dp)
                .background(if (isExpense) MoneyColor.expense else VanGreenFixed, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            FText(flowCategoryName(row), 13f, FontWeight.Medium, colors.textPrimary)
            Spacer(Modifier.height(1.dp))
            FText(flowPayMethod(row) + " · " + flowTime(row.createTime), 11f, FontWeight.Normal, colors.textTertiary)
        }
        FText(
            (if (isExpense) "-" else "+") + flowAmount2(row.amount),
            15f, FontWeight.SemiBold,
            if (isExpense) MoneyColor.expense else MoneyColor.income,
            Modifier.padding(start = 8.dp),
        )
    }
}
