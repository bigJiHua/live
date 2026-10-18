package com.live.finance.ui.report

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Fund
import com.live.finance.data.model.FundHistory
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.chart.toFixed2
import com.live.finance.ui.common.chart.SeriesData
import com.live.finance.ui.common.chart.DualAxisChart
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

private data class FundTrendPoint(val date: String, val profit: Double, val marketVal: Double, val hasRecord: Boolean)

@Composable
fun FundTrendScreen(nav: androidx.navigation.NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()
    val toast = LocalVanToastController.current
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    var loading by remember { mutableStateOf(true) }
    var fundList by remember { mutableStateOf<List<Fund>>(emptyList()) }
    var currentFundId by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf("3m") }   // web `range = ref('3m')`（FundTrend.vue:61）

    var recordPoints by remember { mutableStateOf<List<FundTrendPoint>>(emptyList()) }
    var displayPoints by remember { mutableStateOf<List<FundTrendPoint>>(emptyList()) }
    var profitMin by remember { mutableStateOf(0.0) }
    var profitMax by remember { mutableStateOf(1.0) }
    var marketMin by remember { mutableStateOf(0.0) }
    var marketMax by remember { mutableStateOf(1.0) }

    val currentFund = fundList.firstOrNull { it.id == currentFundId }

    fun buildHistoryParams(range: String): Map<String, String> {
        val today = LocalDate.now()
        // web `FundTrend.vue:83-86`：`startDate = dayjs().subtract(months,'month')`，`all`（无 months）只传
        //   `{endDate, limit:400}` ⇒ 档位换算走**月**（1m/3m/6m/1y），不是原生自造的那套按天档。
        val months = when (range) {
            "1m" -> 1
            "3m" -> 3
            "6m" -> 6
            "1y" -> 12
            else -> 0
        }
        return if (months == 0) {
            mapOf("endDate" to today.toString(), "limit" to "400")
        } else {
            mapOf(
                "endDate" to today.toString(),
                "startDate" to today.minusMonths(months.toLong()).toString(),
                "limit" to "400",
            )
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
        // web `FundTrend.vue:140-147`：`floor(min) / ceil(max)`，空数组 → {0,1}，min==max → 各外扩 1。
        //   ⚠ **没有强制 0 基线** —— 原生原来写的是 `minOf(0.0, pMin)`，会把曲线整体压到上半区、
        //   还会凭空多出一档 0 刻度（负责人 2026-09-19：「图表同样的缺少 Y 轴数据/数据不够充分」的成因之一）。
        fun bounds(pick: (FundTrendPoint) -> Double): Pair<Double, Double> {
            val vals = points.map(pick).filter { it.isFinite() }
            if (vals.isEmpty()) return 0.0 to 1.0
            var mn = kotlin.math.floor(vals.min())
            var mx = kotlin.math.ceil(vals.max())
            if (mn == mx) { mn -= 1.0; mx += 1.0 }
            return mn to mx
        }
        val p = bounds { it.profit }
        val m = bounds { it.marketVal }
        profitMin = p.first
        profitMax = p.second
        marketMin = m.first
        marketMax = m.second
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
                        // web `loadChart`：`rows.length <= 0` → 清空图表并提示「当前周期暂无登记记录」
                        if (d.list.isEmpty()) {
                            recordPoints = emptyList()
                            displayPoints = emptyList()
                            loading = false
                            toast.show("当前周期暂无登记记录")
                            return@launch
                        }
                        val pts = buildRecordPoints(d.list, d.range.profitBefore, d.range.capitalBefore)
                        recordPoints = pts
                        displayPoints = buildDisplayPoints(pts, d.range.startDate, d.range.endDate)
                        // web `loadChart` 传的是 **chartPoints**（补过空档日之后的显示点集），不是原始登记点
                        calcRange(displayPoints)
                    }
                }
                // web `loadChart` 的 catch → showToast(e?.message || '加载失败')
                is ApiResult.Fail -> toast.show(res.message.ifBlank { "加载失败" }, VanToastType.Fail)
                else -> toast.showFail(res, "加载失败")
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
                // web `onMounted` 的 catch → showToast(e?.message || '加载失败')
                is ApiResult.Fail -> { toast.show(res.message.ifBlank { "加载失败" }, VanToastType.Fail); loading = false }
                else -> { toast.showFail(res, "加载失败"); loading = false }
            }
        }
    }
    LaunchedEffect(Unit) { reloadList() }
    LaunchedEffect(currentFundId, selectedRange) { if (currentFundId.isNotBlank()) reload() }

    // web `FundTrend.vue:63-71`：区间是**按月**的 5 档 `1m/3m/6m/1y/all`（近1月/近3月/近6月/近1年/全部），
    //   默认 `range = ref('3m')`（`:61`）。原生此前是自造的一套按天档（全部/近30天/近90天/近180天/近1年），
    //   2026-09-19 负责人拍板「那就改 3m」⇒ 档名、顺序、默认值全部对齐 web。
    val ranges = listOf("1m" to "近1月", "3m" to "近3月", "6m" to "近6月", "1y" to "近1年", "all" to "全部")
    val lastPoint = displayPoints.lastOrNull()
    val lastProfitColor = if ((lastPoint?.profit ?: 0.0) >= 0) colors.danger else colors.success

    ScreenScaffold { outerMod ->
        Column(outerMod) {
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
                    // web `.filter-bar` 的**第一行** `.fb-row` = 理财切换：每个持仓一个
                    //   `app-button size=small round plain`，`type = selectedFund===f.id ? 'primary' : 'default'`，
                    //   文案 `f.fund_name.slice(0,4)`（`FundTrend.vue:3-16`）。
                    //   原生此前只有一行当前基金名 ⇒ 切换功能整个丢了（负责人 2026-09-20「安卓端丢失了该功能」）。
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fundList.forEach { item ->
                            val active = item.id == currentFundId
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (active) colors.primary else colors.bgThird)
                                    .clickable { currentFundId = item.id }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                FText(item.fundName.ifEmpty { "未命名" }.take(4), 12f, FontWeight.Medium,
                                    if (active) colors.buttonPrimaryText else colors.textPrimary)
                            }
                        }
                    }

                    // 范围筛选（web 的第二个 `.fb-row`）
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
                                FText("登记收益", 12f, FontWeight.Normal, colors.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(10.dp).height(3.dp).background(colors.primary))
                                FText("当前市值", 12f, FontWeight.Normal, colors.textSecondary)
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(8.dp)) {
                        if (loading) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { VanLoading() }
                        } else if (displayPoints.size <= 1) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                FText("暂无足够数据绘制走势", 14f, FontWeight.Normal, colors.textTertiary)
                            }
                        } else {
                            val leftSeries = listOf(SeriesData("登记收益", displayPoints.map { it.profit }))
                            val rightSeries = listOf(SeriesData("当前市值", displayPoints.map { it.marketVal }))
                            DualAxisChart(
                                leftSeries = leftSeries,
                                rightSeries = rightSeries,
                                leftColor = colors.danger,
                                rightColor = colors.primary,
                                dates = displayPoints.map { it.date },
                                leftLabelFormatter = { it.toFixed2() },
                                rightLabelFormatter = { if (abs(it) >= 10000) "${(it / 10000).toFixed2()}万" else it.toFixed2() },
                                // web xAxis.axisLabel.rotate:45（原来是 -40，且方向与 web 相反）
                                xLabelRotation = 45f,
                                // web 市值系列 lineStyle:{type:'dashed'}
                                rightDashed = true,
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                            )
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


