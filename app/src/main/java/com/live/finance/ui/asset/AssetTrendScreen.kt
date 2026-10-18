package com.live.finance.ui.asset

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetRegister
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanGreen
import com.live.finance.theme.VanGrayFixed
import com.live.finance.ui.common.chart.LineChart
import com.live.finance.ui.common.chart.SeriesData
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

private enum class TrendTab(val key: String, val label: String, val empty: String) {
    TOTAL("total", "总资产", ""),
    BALANCE("balance", "境内资产", "暂无境内资产登记"),
    OFFSHORE("offshore", "境外资产", "暂无境外资产登记"),
    DEBT("debt", "负债", "暂无负债登记"),
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
                    FText("加载中…", 16f, color = colors.textTertiary)
                }
            } else if (n == 0 || (tab != TrendTab.TOTAL && series.isEmpty())) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    VanEmpty(description = if (n == 0) "暂无登记记录" else tab.empty)
                }
            } else {
                // —— 折线图（40vh，min240/max420）——
                //   web `.chart-section{ background:var(--theme-bg-secondary); margin:0; padding:0; radius:0 }`
                //   ⇒ 图表是落在**一条白底带**里的；原生此前直接把 Canvas 铺在页面灰底上（少了这层底）。
                //   负责人 2026-09-19「图表记得加 padding（四周留白）」⇒ 在白底带上加内边距，
                //   让 y 轴标签与日期不贴卡片边缘（echarts 侧靠 `grid.containLabel` 消化，自绘 Canvas 要自己留）。
                val chartH = with(androidx.compose.ui.platform.LocalDensity.current) {
                    val vh = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.4f
                    vh.coerceIn(240.dp, 420.dp)
                }
                Box(
                    Modifier.fillMaxWidth().background(colors.bgCard)
                        // 负责人 2026-09-20 复测仍报「图表贴边」⇒ 留白从 10/8 提到 **16/12**
                        //   （y 轴标签是画在 Canvas 的 x=0 处的，外层不留白就直接贴到卡片边）。
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val windowSeries = series.map { s ->
                        SeriesData(
                            name = s.name,
                            // 缺数据保留 null 占位（LineChart 契约：null 保留原始索引位）；
                            // 此前 mapNotNull 剔 null ⇒ 某系列最新期缺值时整体左移、与日期错位
                            data = (start..end).map { s.data.getOrNull(it) },
                        )
                    }
                    val windowDates = (start..end).map { rows[it].registerDate }
                    LineChart(
                        series = windowSeries,
                        dates = windowDates,
                        colors = PALETTE,
                        showArea = true,
                        smooth = true,
                        onPointClick = { sIdx, dIdx ->
                            val dataIdx = start + dIdx
                            if (dataIdx in 0 until n) activeIndex = dataIdx
                        },
                        activeIndices = series.indices.map { activeIndex - start }.filter { it in 0 until (end - start + 1) },
                        modifier = Modifier.fillMaxWidth().height(chartH),
                    )
                }
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
                                // web legend: textStyle{fontSize:10,color:'#969799'} / itemGap:8
                                FText(s.name, 10f, color = VanGrayFixed,
                                    modifier = Modifier.padding(start = 5.dp))
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
            // 日期**居中**：原来直接 `FText(..., Modifier.weight(1f))` ⇒ BasicText 默认左对齐，
            //   文字紧贴左边的 `<`，负责人两次反馈「上方的左右中间是日期，这个日期要居中显示」。
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FText(date, 18f, FontWeight.SemiBold, colors.textPrimary)
            }
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
                    FText(label, 12f, color = colors.textTertiary)
                    FText(
                        v?.let { (if (offshoreUnit) "" else "¥") + detailFmt(it) } ?: "--",
                        14f, FontWeight.SemiBold,
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
    // 负责人 2026-09-20：「`< >` 日期切换按钮过小」⇒ 视觉圆 30→**40dp**、字形 14→**18sp**，
    //   外面再包一层 52dp 的透明点击热区。只改这一处（其它页的同名件不动），属安卓侧有意偏离。
    Box(
        Modifier.size(52.dp).clip(CircleShape).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(colors.bgThird),
            contentAlignment = Alignment.Center,
        ) {
            VanIcon(icon, size = 18.sp,
                color = if (enabled) colors.primary else colors.textTertiary.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun DetailTable(rows: List<DiffRow>, offshoreUnit: Boolean) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.bgCard)) {
        // 表头 —— 顺序与行内一致：`项目 …… 金额 / 较上期`（web `Trend.vue:70-77` 已于 2026-09-19 同步改过：
        //   原表头写「较上期 / 金额」与下方 dl-row 的 `金额 → diff` 顺序相反，负责人实测发现后要求两端一起改）
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FText("项目", 12f, color = colors.textTertiary, modifier = Modifier.weight(1f))
            FText("金额（${if (offshoreUnit) "CNY" else "¥"}）", 12f, color = colors.textTertiary)
            Spacer(Modifier.width(8.dp))                                  // web `.dl-head-r{gap:8px}`
            FText("较上期", 12f, color = colors.textTertiary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                FText("该日期暂无数据", 12f, color = colors.textTertiary)
            }
        } else {
            rows.forEach { row ->
                Row(
                    // web `.dl-row{display:flex; align-items:center; gap:6px; padding:7px 10px}`
                    //   —— 元素之间是 **6px 间隙**；原来原生没有 arrangement，diff 与金额、箭头与数字全贴在一起。
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(row.color))
                    FText(row.name, 12f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    // ⚠ 行内顺序照 web 模板：`dl-dot → dl-name → **dl-value（金额）** → **dl-diff（较上期）**`
                    //   即涨跌标记在**金额之后**。（表头写的是「较上期 / 金额」两列 —— web 自己就和行内顺序不一致，
                    //   原生此前跟着表头把 diff 放到了金额前面，这是那处偏差的来源；表头保持与 web 一致不动。）
                    FText(
                        (row.value?.let { (if (offshoreUnit) "" else "¥") + detailFmt(it) }) ?: "--",
                        14f, FontWeight.SemiBold,
                        color = if ((row.value ?: 0.0) < 0) colors.danger else colors.textPrimary,
                    )
                    // web `.dl-diff{display:flex; gap:2px; min-width:52px; justify-content:flex-end; flex-shrink:0}`
                    //   web 用 `v-if="row.diff !== null"`：无差值时整列不占位。负责人 2026-09-21 拍板偏离：
                    //   无值也要 **"--" 占位**，缺项不得让行内布局塌陷（web 未改）。
                    val diff = row.diff
                    // 较上期 —— web `assets/Trend.vue:908-915`：
                    //   `.up`   = `var(--theme-danger-color)`（两模式跟主题）
                    //   `.down` = `var(--van-green, #07c160)`（**只在深色** = --theme-success，浅色 #07c160）
                    // ⚠ 这是**红涨绿跌**（A 股口径），与流水页的「红收绿支」不是一回事
                    when {
                        diff == null -> Box(
                            Modifier.widthIn(min = 52.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { FText("--", 12f, color = colors.textTertiary) }
                        diff == 0.0 -> Box(
                            Modifier.widthIn(min = 52.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { FText("0.00", 12f, FontWeight.SemiBold, colors.textTertiary) }
                        else -> {
                            val diffColor = if (diff > 0) colors.danger else vanGreen()
                            Row(
                                Modifier.widthIn(min = 52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                FText(if (diff > 0) "↑" else "↓", 16f, FontWeight.Bold, diffColor)
                                FText(detailFmt(abs(diff)), 12f, FontWeight.SemiBold, diffColor)
                            }
                        }
                    }
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
