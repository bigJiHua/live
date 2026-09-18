package com.live.finance.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.currencySymbol
import com.live.finance.ui.common.isRepay

/** 通用文本（Vant 字号/字重约定）。 */
@Composable
fun FText(
    text: String,
    sizeSp: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color,
    modifier: Modifier = Modifier,
) = BasicText(text, modifier, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight))

/** 单个流水行（收入/支出）。 */
@Composable
fun FlowItemRow(row: FlowRow, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val repay = isRepay(row)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .clickable(enabled = !repay || true, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText(if (repay) "信用卡还款" else row.categoryName, 15f, FontWeight.Medium,
                    if (repay) colors.textTertiary else colors.textPrimary)
                if (row.isTransfer) {
                    Box(
                        Modifier.padding(start = 6.dp).background(colors.primaryLight)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) { FText("转账", 10f, FontWeight.Normal, colors.primary) }
                }
            }
            FText(
                row.bankLabel, 11f, FontWeight.Normal, colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState()),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val sym = if (row.currency == "CNY") "" else currencySymbol(row.currency)
            FText(
                Money.signed(row.amount, row.isIncome).let { if (sym.isEmpty()) it else "${if (row.isIncome) "+" else "-"}$sym${Money.format(row.amount)}" },
                16f, FontWeight.SemiBold,
                if (repay) colors.textTertiary else if (row.isIncome) MoneyColor.income else MoneyColor.expense,
            )
            if (row.currency != "CNY") FText(row.currency, 10f, FontWeight.Normal, colors.textTertiary)
            FText(row.time, 10f, FontWeight.Normal, colors.textTertiary)
        }
    }
    Spacer(Modifier.height(0.5.dp))
}

// 说明：原 `FlowCellRow`（转账合并行）已被 `FlowCards.kt` 的
// `FlowCardRow` / `FlowPairedBlock` 取代（对齐 web `flow/List.vue` 的卡片与三层转账块）。
// `FlowItemRow` 保留：余额流水页（`ui/account/BalanceFlowScreen.kt`）仍在用。
