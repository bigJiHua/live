package com.live.finance.ui.report

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Fund
import com.live.finance.data.model.FundHistory
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.nav.VanNavBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private data class FundTrendPoint(val date: String, val profit: Double, val marketVal: Double, val hasRecord: Boolean)

@Composable
fun FundTrendScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }
    var currentFundId by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf("all") }

    var recordPoints by remember { mutableStateOf<List<FundTrendPoint>>(emptyList()) }
    var displayPoints by remember { mutableStateOf<List<FundTrendPoint>>(emptyList()) }
    var profitMin by remember { mutableStateOf(0.0) }
    var profitMax by remember { mutableStateOf(1.0) }
    var marketMin by remember { mutableStateOf(0.0) }
    var marketMax by remember { mutableStateOf(1.0) }

    val currentFund = fundList.firstOrNull { it.id == currentFundId }

    fun buildHistoryParams(range: String): Map<String, String> {
        val today = LocalDate.now()
        return when (range) {
            "30" -> mapOf("endDate" to today.toString(), "startDate" to today.minusDays(29).toString(), "limit" to "400")
            "90" -> mapOf("endDate" to today.toString(), "startDate" to today.minusDays(89).toString(), "limit" to "400")
            "180" -> mapOf("endDate" to today.toString(), "startDate" to today.minusDays(179).toString(), "limit" to "400")
            "year" -> mapOf("endDate" to today.toString(), "startDate" to today.minusYears(1).plusDays(1).toString(), "limit" to "400")
            else -> mapOf("endDate" to today.toString(), "limit" to "400")
        }
    }

    fun buildRecordPoints(rows: List<FundHistory>, rangeProfitBefore: Double, rangeCapitalBefore: Double): List<FundTrendPoint> {
        val baseInvest = currentFund?.baseInvest?.takeIf { it > 0 } ?: (currentFund?.invest ?: 0.0)
        var principal = baseInvest + rangeCapitalBefore
        var profit = rangeProfitBefore
        val pts = mutableListOf<FundTrendPoint>()
        rows.forEach { r ->
            principal += r.marketVal
            profit += r.netValue
            pts.add(FundTrendPoint(r.recordDate, profit, principal + profit, true))
        }
        return pts
    }

    fun buildDisplayPoints(points: List<FundTrendPoint>, startDate: String?, endDate: String?): List<FundTrendPoint> {
        if (points.isEmpty()) return emptyList()
        val start = if (startDate != null) runCatching { LocalDate.parse(startDate, fmt) }.getOrNull() ?: LocalDate.parse(points.first().date, fmt) else LocalDate.parse(points.first().date, fmt)
        val end = if (endDate != null) runCatching { LocalDate.parse(endDate, fmt) }.getOrNull() ?: LocalDate.now() else LocalDate.now()
        val byDate = points.associateBy { it.date }
        val result = mutableListOf<FundTrendPoint>()
        var last: FundTrendPoint? = null
        var d = start
        while (!d.isAfter(end)) {
            val ds = d.toString()
            val real = byDate[ds]
            if (real != null) { result.add(real); last = real }
            else if (last != null) result.add(FundTrendPoint(ds, last.profit, last.marketVal, false))
            d = d.plusDays(1)
        }
        return result
    }

    fun calcRange(points: List<FundTrendPoint>) {
        val pMin = points.minOfOrNull { it.profit } ?: 0.0
        val pMax = points.maxOfOrNull { it.profit } ?: 0.0
        val mMin = points.minOfOrNull { it.marketVal } ?: 0.0
        val mMax = points.maxOfOrNull { it.marketVal } ?: 0.0
        profitMin = minOf(0.0, pMin)
        profitMax = pMax
        marketMin = minOf(0.0, mMin)
        marketMax = mMax
        if (profitMax == profitMin) profitMax = profitMin + 1.0
        if (marketMax == marketMin) marketMax = marketMin + 1.0
    }

    fun reload() {
        val fid = currentFundId
        if (fid.isBlank() || currentFund == null) return
        scope.launch {
            loading = true
            val params = buildHistoryParams(selectedRange)
            when (val res = graph.fund.history(fid, params)) {
                is ApiResult.Ok -> {
                    val d = res.data
                    if (d != null) {
                        val pts = buildRecordPoints(d.list, d.range.profitBefore, d.range.capitalBefore)
                        recordPoints = pts
                        displayPoints = buildDisplayPoints(pts, d.range.startDate, d.range.endDate)
                        calcRange(pts)
                    }
                }
                is ApiResult.Fail -> { /* toast */ }
                else -> { /* toast */ }
            }
            loading = false
        }
    }

    fun reloadList() {
        scope.launch {
            loading = true
            when (val res = graph.fund.list()) {
                is ApiResult.Ok -> {
                    val d = res.data
                    fundList = d ?: emptyList()
                    if (currentFundId.isBlank() && d != null && d.isNotEmpty()) currentFundId = d.first().id
                    reload()
                }
                else -> loading = false
            }
        }
    }
    LaunchedEffect(Unit) { reloadList() }
    LaunchedEffect(currentFundId, selectedRange) { if (currentFundId.isNotBlank()) reload() }

    val ranges = listOf("all" to "全部", "30" to "近30天", "90" to "近90天", "180" to "近180天", "year" to "近1年")
    val lastPoint = displayPoints.lastOrNull()
    val lastProfitColor = if ((lastPoint?.profit ?: 0.0) >= 0) colors.danger else colors.success

    ScreenScaffold { outerMod ->
        Column(outerMod) {
            VanNavBar(title = "变动走势图", leftArrow = true, onClickLeft = { nav.popBackStack() })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            if (loading && fundList.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { VanLoading() }
            } else if (fundList.isEmpty()) {
                com.live.vant.basic.VanEmpty(description = "暂无持仓基金，请先登记")
            } else {
                currentFund?.let { f ->
                    FText(f.fundName.ifEmpty { "未命名" }, 15f, FontWeight.SemiBold, colors.textPrimary)

                    // 范围筛选
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ranges.forEach { (key, label) ->
                            val active = key == selectedRange
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) colors.primary else colors.bgThird)
                                    .clickable { selectedRange = key }.padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                FText(label, 12f, FontWeight.Medium, if (active) colors.buttonPrimaryText else colors.textPrimary)
                            }
                        }
                    }

                    // 图例 + 当前值
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(10.dp).height(3.dp).background(colors.danger))
                                FText("登记收益", 11f, FontWeight.Normal, colors.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(10.dp).height(3.dp).background(colors.primary))
                                FText("当前市值", 11f, FontWeight.Normal, colors.textSecondary)
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(8.dp)) {
                        if (loading) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { VanLoading() }
                        } else if (displayPoints.size <= 1) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                FText("暂无足够数据绘制走势", 13f, FontWeight.Normal, colors.textTertiary)
                            }
                        } else {
                            FundTrendChart(displayPoints, profitMin, profitMax, marketMin, marketMax)
                        }
                    }

                    // 当前累计
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FundOverviewItem("登记收益", (if ((lastPoint?.profit ?: 0.0) >= 0) "+" else "") + fundAmount(lastPoint?.profit ?: 0.0), lastProfitColor, Modifier.weight(1f))
                        FundOverviewItem("当前市值", fundAmount(lastPoint?.marketVal ?: 0.0), colors.primary, Modifier.weight(1f))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun FundTrendChart(
    points: List<FundTrendPoint>,
    profitMin: Double, profitMax: Double,
    marketMin: Double, marketMax: Double,
) {
    val colors = LocalAppColors.current
    val profitColor = colors.danger
    val marketColor = colors.primary
    val gridColor = colors.border
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val padL = 46.dp.toPx()
        val padR = 46.dp.toPx()
        val padT = 12.dp.toPx()
        val padB = 28.dp.toPx()
        val plotW = w - padL - padR
        val plotH = h - padT - padB
        val n = points.size
        val nc = drawContext.canvas.nativeCanvas

        val steps = 4
        for (i in 0..steps) {
            val yy = padT + plotH * i / steps
            drawLine(gridColor, Offset(padL, yy), Offset(w - padR, yy), strokeWidth = 1.dp.toPx())
            val pVal = profitMax - (profitMax - profitMin) * i / steps
            val pPaint = Paint().apply { color = profitColor.toArgb(); textSize = 9.sp.toPx(); isAntiAlias = true }
            nc.drawText(trendAxis(pVal), 4.dp.toPx(), yy + 3.dp.toPx(), pPaint)
            val mVal = marketMax - (marketMax - marketMin) * i / steps
            val mPaint = Paint().apply { color = marketColor.toArgb(); textSize = 9.sp.toPx(); isAntiAlias = true; textAlign = Paint.Align.RIGHT }
            nc.drawText(trendAxis(mVal), w - 4.dp.toPx(), yy + 3.dp.toPx(), mPaint)
        }

        // x labels (rotated) at ~6 positions
        val labelCount = minOf(6, n)
        if (labelCount >= 2) {
            for (k in 0 until labelCount) {
                val idx = if (labelCount == 1) 0 else k * (n - 1) / (labelCount - 1)
                val p = points[idx]
                val xx = padL + plotW * idx / (n - 1)
                nc.save()
                nc.translate(xx, h - 4.dp.toPx())
                nc.rotate(-40f)
                val xPaint = Paint().apply { color = colors.textTertiary.toArgb(); textSize = 9.sp.toPx(); isAntiAlias = true }
                nc.drawText(p.date.takeLast(5), 0f, 0f, xPaint)
                nc.restore()
            }

            val profitPath = Path()
            points.forEachIndexed { i, p ->
                val xx = padL + plotW * i / (n - 1)
                val yy = (padT + plotH * (1 - (p.profit - profitMin) / (profitMax - profitMin))).toFloat()
                if (i == 0) profitPath.moveTo(xx, yy) else profitPath.lineTo(xx, yy)
            }
            drawPath(profitPath, profitColor, style = Stroke(width = 2.dp.toPx()))

            val marketPath = Path()
            points.forEachIndexed { i, p ->
                val xx = padL + plotW * i / (n - 1)
                val yy = (padT + plotH * (1 - (p.marketVal - marketMin) / (marketMax - marketMin))).toFloat()
                if (i == 0) marketPath.moveTo(xx, yy) else marketPath.lineTo(xx, yy)
            }
            drawPath(marketPath, marketColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun trendAxis(v: Double): String {
    val a = abs(v)
    val sign = if (v < 0) "-" else ""
    return when {
        a >= 10000 -> "$sign${trimDecimal(a / 10000, 1)}万"
        else -> "$sign${trimDecimal(a, 0)}"
    }
}
