package com.live.finance.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.live.finance.data.model.FundHistory
import kotlin.math.abs
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 理财收益日历的单格（对应 web calDays 计算产物）。 */
data class FundCalDay(
    val date: String,
    val day: Int,
    val inMonth: Boolean,
    val earnings: Double?,
    val earningsDisplay: String,
    val hasRecord: Boolean,
    val isBuy: Boolean,
    val isToday: Boolean,
)

/**
 * 构建月历格（逐字对齐 web `calDays`）：
 *  - 周一为首列（JS getDay: 周日=0 → offset = day===0?6:day-1）；
 *  - earnings = 当日收益（net_value），abs 后缩略展示，符号仅 ≥0 时加 "+"；
 *  - isBuy = 买入日(买入月旗) 或 当日有增持本金(>0.005)。
 */
fun buildFundCalDays(
    historyList: List<FundHistory>,
    buyDate: String,
    month: LocalDate,
    showToday: Boolean,
): List<FundCalDay> {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val monthStart = month.withDayOfMonth(1)
    val daysInMonth = month.lengthOfMonth()
    val firstDay = monthStart.dayOfWeek.value // 1=Mon..7=Sun
    val offset = if (firstDay == 7) 6 else firstDay - 1
    val todayStr = if (showToday) LocalDate.now().format(fmt) else ""
    val buyStr = parseFundDate(buyDate)

    val dateMap = mutableMapOf<String, Pair<Double, Double>>()
    historyList.forEach { h ->
        if (h.recordDate.isEmpty()) return@forEach
        if (!dateMap.containsKey(h.recordDate)) dateMap[h.recordDate] = h.netValue to h.marketVal
    }

    val cells = mutableListOf<FundCalDay>()
    repeat(offset) { cells.add(FundCalDay("", 0, false, null, "", false, false, false)) }
    for (d in 1..daysInMonth) {
        val date = monthStart.plusDays((d - 1).toLong()).format(fmt)
        val rec = dateMap[date]
        val earnings = rec?.first
        val absE = abs(earnings ?: 0.0)
        val isBuy = buyStr == date || (rec != null && abs(rec.second) > 0.005)
        cells.add(
            FundCalDay(
                date = date, day = d, inMonth = true,
                earnings = earnings,
                earningsDisplay = if (earnings == null) "" else fundAmount(absE),
                hasRecord = rec != null, isBuy = isBuy, isToday = date == todayStr,
            ),
        )
    }
    return cells
}

private val ORANGE_BG = Color(0x1FFF976A) // 近似 --van-orange-bg（暗色 rgba(255,151,106,.12)）

/** 收益日历图例（当日收益 / 买入·增持）。 */
@Composable
fun FundCalendarLegend() {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(8.dp).height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(colors.bgThird))
            FText("当日收益", 11f, FontWeight.Normal, colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FText("🚩", 11f, FontWeight.Normal, colors.textSecondary)
            FText("基金买入日 / 增持", 11f, FontWeight.Normal, colors.textSecondary)
        }
    }
}

/**
 * 收益日历网格（7 列，对应 web `.cal-grid`）。
 * [clickable] 控制某格是否可点；[onClick] 点击回调。
 */
@Composable
fun FundCalendar(
    days: List<FundCalDay>,
    clickable: (FundCalDay) -> Boolean,
    onClick: (FundCalDay) -> Unit,
) {
    val colors = LocalAppColors.current
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            weekdays.forEach { w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    FText(w, 11f, FontWeight.Normal, colors.textTertiary)
                }
            }
        }
        days.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    Box(
                        Modifier.weight(1f).height(52.dp)
                            .then(if (day.inMonth && clickable(day)) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        day.isBuy -> ORANGE_BG
                                        day.hasRecord -> colors.bgThird
                                        else -> Color.Transparent
                                    }
                                ).clickable { onClick(day) } else Modifier)
                            .then(if (day.isToday) Modifier.border(1.5.dp, colors.primary, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day.inMonth) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FText(
                                    text = day.day.toString(),
                                    sizeSp = 12f,
                                    weight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (day.isToday) colors.primary else colors.textPrimary,
                                )
                                if (day.isBuy) {
                                    FText("🚩", 12f, FontWeight.Normal, colors.textPrimary, Modifier.padding(top = 1.dp))
                                }
                                if (day.earnings != null) {
                                    FText(
                                        text = (if (day.earnings >= 0) "+" else "") + day.earningsDisplay,
                                        sizeSp = 10f, weight = FontWeight.Normal,
                                        color = if (day.earnings >= 0) colors.danger else colors.success,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 月历头部（上一月 / 标题 / 下一月），对应 web `.cal-header`。 */
@Composable
fun FundCalHeader(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FText("‹", 22f, FontWeight.Bold, colors.textSecondary, Modifier.clickable { onPrev() }.padding(4.dp))
        Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
            FText(title, 14f, FontWeight.SemiBold, colors.textPrimary)
        }
        FText("›", 22f, FontWeight.Bold, colors.textSecondary, Modifier.clickable { onNext() }.padding(4.dp))
    }
}
