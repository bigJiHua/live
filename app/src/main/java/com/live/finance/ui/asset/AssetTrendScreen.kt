package com.live.finance.ui.asset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetRegister
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import kotlin.math.abs
import kotlin.math.roundToLong

/** echarts 15 色调色板（web Trend.vue PALETTE，逐字保留）。 */
private val PALETTE = listOf(
    Color(0xFF3B82F6), Color(0xFF07C160), Color(0xFFEE0A24), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
    Color(0xFFF97316), Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFEAB308),
    Color(0xFFD946EF), Color(0xFF22C55E), Color(0xFF0EA5E9),
)
private val UP_RED = Color(0xFFEE0A24)
private val DOWN_GREEN = Color(0xFF07C160)
private val AXIS_GRAY = Color(0xFF969799)
private val SPLIT_LINE = Color(0xFFECEEF1)
private val AXIS_LINE_C = Color(0xFFDCDee0)

private enum class TrendTab(val key: String, val label: String, val empty: String) {
    TOTAL("total", "总资产", ""),
    BALANCE("balance", "境内资产", "暂无境内资产登记"),
    OFFSHORE("offshore", "境外资产", "暂无境外资产登记"),
    DEBT("debt", "信用卡/负债", "暂无负债登记"),
}

private data class TrendSeries(val name: String, val data: List<Double?>)

/**
 * 资产走势（web `views/Finance/assets/Trend.vue`）：
 * 4 个 tab（总资产/境内/境外/负债）+ 自绘折线（>6 期开 6 期窗口跟随）+
 * 日期前后切换 + 同花顺式指标条 + 较上期涨跌明细。数据全部来自 GET /asset/register/list。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetTrendScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.asset
    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<AssetRegister>>(emptyList()) }
    var tab by remember { mutableStateOf(TrendTab.TOTAL) }
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        rows = ((repo.list() as? ApiResult.Ok)?.data ?: emptyList())
            // 后端已按 register_date DESC、create_time DESC；这里统一反转再走「同日取最后一条」
            .sortedWith(compareBy({ it.registerDate }, { it.createTime.toLongOrNull() ?: 0L }))
            .let { sorted ->
                sorted.groupBy { it.registerDate }.values.map { day -> day.last() }
                    .sortedBy { it.registerDate }
            }
        activeIndex = (rows.size - 1).coerceAtLeast(0)
        loading = false
    }

    // 切 tab 后选中点回到最新（web watch(activeTab) → renderAll 重置）
    fun switchTab(t: TrendTab) {
        tab = t
        activeIndex = (rows.size - 1).coerceAtLeast(0)
    }

    val seriesMap = remember(rows) { buildSeriesMap(rows) }
    val series = when (tab) {
        TrendTab.TOTAL -> listOf(TrendSeries("总资产", rows.map { it.totalBalance }))
        TrendTab.BALANCE -> seriesMap.balance
        TrendTab.OFFSHORE -> seriesMap.offshore
        TrendTab.DEBT -> seriesMap.debt
    }
    val n = rows.size
    val current = rows.getOrNull(activeIndex)

    // 可视窗口（与 echarts dataZoom 一致：最多 6 期，跟随 activeIndex 居中）
    val span = minOf(6, n)
    val start = if (n <= 6) 0 else (activeIndex - span / 2).coerceIn(0, n - span)
    val end = if (n == 0) -1 else minOf(n - 1, start + span - 1)

    ScreenScaffold { inner ->
        Column(inner.verticalScroll(rememberScrollState())) {
            // 类型切换（web sticky type-tabs）
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrendTab.entries.forEach { t ->
                    val active = tab == t
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (active) colors.primary else colors.bgThird)
                            .clickable { switchTab(t) }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        FText(t.label, 12f,
                            color = if (active) Color.White else colors.textTertiary)
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            } else if (n == 0 || (tab != TrendTab.TOTAL && series.isEmpty())) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    VanEmpty(description = if (n == 0) "暂无登记记录" else tab.empty)
                }
            } else {
                // —— 折线图（40vh，min240/max420）——
                val chartH = with(androidx.compose.ui.platform.LocalDensity.current) {
                    val vh = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.4f
                    vh.coerceIn(240.dp, 420.dp)
                }
                TrendChart(
                    series = series,
                    dates = rows.map { it.registerDate },
                    start = start, end = end,
                    activeIndex = activeIndex,
                    onPick = { idx -> if (idx in 0 until n) activeIndex = idx },
                    modifier = Modifier.fillMaxWidth().height(chartH),
                )
                // 图例（>1 条时；echarts scroll legend 的 wrap 近似）
                if (series.size > 1) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        series.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape)
                                    .background(PALETTE[i % PALETTE.size]))
                                FText(s.name, 10f, color = AXIS_GRAY,
                                    modifier = Modifier.padding(start = 3.dp))
                            }
                        }
                    }
                }

                if (current != null) {
                    // —— 日期切换 + 指标条 ——
                    QuoteBar(
                        date = formatDate(current.registerDate),
                        index = activeIndex,
                        total = n,
                        onPrev = { if (activeIndex > 0) activeIndex-- },
                        onNext = { if (activeIndex < n - 1) activeIndex++ },
                        chips = when (tab) {
                            TrendTab.TOTAL -> listOf(
                                "净资产" to current.totalBalance,
                                "资产合计" to current.totalAsset,
                                "信用卡欠款" to current.creditDebt,
                            )
                            else -> detailRows(series, activeIndex, prevIdx(rows, activeIndex))
                                .take(4).map { it.name to it.value }
                        },
                        offshoreUnit = tab == TrendTab.OFFSHORE,
                    )

                    // —— 高密度明细（较上期）——
                    val rows2 = when (tab) {
                        TrendTab.TOTAL -> listOf(
                            DiffRow("总资产（净资产）", current.totalBalance,
                                rows.getOrNull(activeIndex - 1)
                                    ?.let { current.totalBalance - it.totalBalance }, PALETTE[0]),
                        )
                        else -> detailRows(series, activeIndex, prevIdx(rows, activeIndex))
                    }
                    DetailTable(rows2.sortedByDescending { it.value != null },
                        offshoreUnit = tab == TrendTab.OFFSHORE)
                }
            }
        }
    }
}

/** 前一期索引（跳过同日；窗口列表已是同日去重，所以就是 idx-1）。 */
private fun prevIdx(rows: List<AssetRegister>, idx: Int): Int = idx - 1

// ================= 数据构建（对齐 web buildSeries） =================

private data class SeriesMap(
    val balance: List<TrendSeries>,
    val offshore: List<TrendSeries>,
    val debt: List<TrendSeries>,
)

private fun buildSeriesMap(rows: List<AssetRegister>): SeriesMap {
    fun build(category: String): List<TrendSeries> {
        val keyName = LinkedHashMap<String, String>()
        val valueMap = LinkedHashMap<String, HashMap<String, Double>>()
        rows.forEach { r ->
            val d = r.details ?: return@forEach
            val arr = when (category) {
                "balance" -> d.balance
                "offshore" -> d.offshore
                else -> d.debt
            }
            arr.forEach { item ->
                val key = item.customName.ifEmpty { item.type }.ifEmpty { "unknown" }
                keyName[key] = AssetShared.itemName(item, category)
                var v = item.amount
                if (category == "offshore") {
                    v = AssetShared.toCny(item.amount, item.currency, d.exchangeRates)
                }
                val m = valueMap.getOrPut(key) { HashMap() }
                // 同名同日多笔累加（web）
                m[r.registerDate] = (m[r.registerDate] ?: 0.0) + v
            }
        }
        return valueMap.keys.sorted().map { key ->
            TrendSeries(keyName[key] ?: key,
                rows.map { valueMap[key]?.get(it.registerDate) })
        }
    }
    return SeriesMap(build("balance"), build("offshore"), build("debt"))
}

private data class DiffRow(
    val name: String,
    val value: Double?,
    val diff: Double?,
    val color: Color,
)

private fun detailRows(series: List<TrendSeries>, idx: Int, prevIdx: Int): List<DiffRow> =
    series.mapIndexed { i, s ->
        val cur = s.data.getOrNull(idx)
        val pv = if (prevIdx >= 0) s.data.getOrNull(prevIdx) else null
        val diff = if (cur != null && pv != null) round2(cur - pv) else null
        DiffRow(s.name, cur, diff, PALETTE[i % PALETTE.size])
    }

// ================= 图表 =================

@Composable
private fun TrendChart(
    series: List<TrendSeries>,
    dates: List<String>,
    start: Int,
    end: Int,
    activeIndex: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AXIS_GRAY, fontSize = 10.sp)
    val count = (end - start + 1).coerceAtLeast(1)
    val window = start..end
    // 窗口内全部 series 的非空值
    val visibleVals = series.flatMap { s ->
        window.mapNotNull { s.data.getOrNull(it) }
    }
    var maxV = visibleVals.maxOrNull() ?: 1.0
    var minV = visibleVals.minOrNull() ?: 0.0
    if (maxV == minV) { maxV += 1.0; minV -= 1.0 }
    val pad = (maxV - minV) * 0.1f
    maxV += pad; minV -= pad

    Canvas(
        modifier.pointerInput(start, end) {
            detectTapGestures { off ->
                val padL = 42f; val padR = 8f
                val w = size.width - padL - padR
                if (count <= 1) { onPick(start); return@detectTapGestures }
                val ratio = ((off.x - padL) / w).coerceIn(0f, 1f)
                onPick(start + (ratio * (count - 1)).roundToLong().toInt())
            }
        },
    ) {
        val padL = 42f; val padR = 8f; val padT = 8f; val padB = 40f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        fun px(i: Int) = padL + if (count == 1) w / 2 else w * i / (count - 1)
        fun py(v: Double) = padT + h * (1f - ((v - minV) / (maxV - minV)).toFloat())

        // 横网格 4 段 5 线
        repeat(5) { i ->
            val gy = padT + h * i / 4f
            drawLine(SPLIT_LINE, Offset(padL, gy), Offset(size.width - padR, gy), strokeWidth = 1f)
            val v = maxV - (maxV - minV) * i / 4
            val r = textMeasurer.measure(axisLabel(v), labelStyle)
            drawText(r, topLeft = Offset(0f, gy - r.size.height / 2f))
        }

        series.forEachIndexed { si, s ->
            val color = PALETTE[si % PALETTE.size]
            val pts = (0 until count).map { k ->
                val gi = start + k
                s.data.getOrNull(gi)?.let { Offset(px(k), py(it)) }
            }
            // 分段连线（connectNulls 语义：跳过 null 不断线 → web connectNulls:true）
            val line = Path()
            val area = Path()
            var started = false
            var prevP: Offset? = null
            pts.forEach { p ->
                if (p != null) {
                    if (!started) { line.moveTo(p.x, p.y); started = true }
                    else { val pv = prevP!!; val mx = (pv.x + p.x) / 2f; line.cubicTo(mx, pv.y, mx, p.y, p.x, p.y) }
                    prevP = p
                }
            }
            // 面积（8%）
            val firstP = pts.firstOrNull { it != null }
            val lastP = pts.lastOrNull { it != null }
            if (firstP != null && lastP != null) {
                area.moveTo(firstP.x, padT + h)
                area.lineTo(firstP.x, firstP.y)
                var ap: Offset? = firstP
                pts.forEach { p ->
                    if (p != null && ap != null) {
                        if (p != ap) { val pv = ap!!; val mx = (pv.x + p.x) / 2f; area.cubicTo(mx, pv.y, mx, p.y, p.x, p.y) }
                        ap = p
                    }
                }
                area.lineTo(lastP.x, padT + h); area.close()
                drawPath(area, color.copy(alpha = 0.08f))
            }
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { p ->
                if (p != null) drawCircle(color, radius = 5f / 2f, center = p)
            }
        }

        // x 轴日期（窗口≤6 全显，10sp → 用 9sp 防挤）
        (0 until count).forEach { k ->
            val date = formatDate(dates[start + k])
            val r = textMeasurer.measure(date, labelStyle.copy(fontSize = 9.sp))
            drawText(r, topLeft = Offset(px(k) - r.size.width / 2f, size.height - padB + 6f))
        }
        // 轴线
        drawLine(AXIS_LINE_C, Offset(padL, padT + h), Offset(size.width - padR, padT + h), strokeWidth = 1f)

        // 选中竖线（蓝色虚线）+ 高亮圈
        if (activeIndex in window) {
            val k = activeIndex - start
            val x = px(k)
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            drawLine(Color(0xFF3B82F6), Offset(x, padT), Offset(x, padT + h),
                strokeWidth = 1.5f, pathEffect = dash)
            // 竖线与各 series 交点高亮
            series.forEachIndexed { si, s ->
                val v = s.data.getOrNull(activeIndex)
                if (v != null) {
                    val p = Offset(x, py(v))
                    drawCircle(Color.White, radius = 5f, center = p)
                    drawCircle(PALETTE[si % PALETTE.size], radius = 5f, center = p,
                        style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

private fun axisLabel(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e8 -> String.format(java.util.Locale.US, "%.1f亿", v / 1e8)
        a >= 1e4 -> String.format(java.util.Locale.US, "%.1f万", v / 1e4)
        else -> Math.round(v).toString()
    }
}

// ================= 指标条 + 明细表 =================

@Composable
private fun QuoteBar(
    date: String,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    chips: List<Pair<String, Double?>>,
    offshoreUnit: Boolean,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)
        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavCircleBtn("arrow-left", enabled = index > 0, onClick = onPrev)
            FText(date, 16f, FontWeight.SemiBold, colors.textPrimary,
                modifier = Modifier.weight(1f))
            NavCircleBtn("arrow", enabled = index < total - 1, onClick = onNext)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (label, v) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                        .background(colors.bgThird).padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FText(label, 10f, color = colors.textTertiary)
                    FText(
                        v?.let { (if (offshoreUnit) "" else "¥") + detailFmt(it) } ?: "--",
                        13f, FontWeight.SemiBold,
                        if ((v ?: 0.0) < 0) colors.danger else colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavCircleBtn(icon: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(colors.bgThird)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        VanIcon(icon, size = 14.sp,
            color = if (enabled) colors.primary else colors.textTertiary.copy(alpha = 0.35f))
    }
}

@Composable
private fun DetailTable(rows: List<DiffRow>, offshoreUnit: Boolean) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
        // 表头
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FText("项目", 11f, color = colors.textTertiary, modifier = Modifier.weight(1f))
            FText("较上期", 10f, color = colors.textTertiary)
            Spacer(Modifier.width(8.dp))
            FText("金额（${if (offshoreUnit) "CNY" else "¥"}）", 11f, color = colors.textTertiary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                FText("该日期暂无数据", 12f, color = colors.textTertiary)
            }
        } else {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(row.color))
                    FText(row.name, 12f, color = colors.textSecondary,
                        modifier = Modifier.weight(1f).padding(start = 6.dp))
                    // 较上期（固定宽度，红涨绿跌）
                    val diff = row.diff
                    Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                        if (diff != null && diff != 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText(if (diff > 0) "↑" else "↓", 14f, FontWeight.Bold,
                                    color = if (diff > 0) UP_RED else DOWN_GREEN)
                                FText(detailFmt(abs(diff)), 12f, FontWeight.SemiBold,
                                    color = if (diff > 0) UP_RED else DOWN_GREEN)
                            }
                        }
                    }
                    FText(
                        (row.value?.let { (if (offshoreUnit) "" else "¥") + detailFmt(it) }) ?: "--",
                        13f, FontWeight.SemiBold,
                        color = if ((row.value ?: 0.0) < 0) colors.danger else colors.textPrimary,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
            }
        }
    }
}

// ================= 工具 =================

/** 兼容时间戳（秒/毫秒）与日期串 → YYYY-MM-DD（web formatDate）。 */
private fun formatDate(raw: String): String {
    if (raw.isEmpty()) return "-"
    if (raw.matches(Regex("\\d+"))) {
        var ts = raw.toLong()
        if (ts < 1_000_000_000_000L) ts *= 1000
        val d = java.util.Date(ts)
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return f.format(d)
    }
    return raw.take(10)
}

private fun detailFmt(v: Double): String =
    String.format(java.util.Locale.US, "%,.2f", v)

private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
