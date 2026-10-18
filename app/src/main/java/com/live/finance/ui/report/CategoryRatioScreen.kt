package com.live.finance.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.YearMonthSaver
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.icon.VanIcon
import com.live.vant.other.VanCalendar
import com.live.vant.other.VanSwipe
import com.live.vant.other.VanCalendarType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private val CAT_COLORS = listOf(
    0xFFEE0A24, 0xFFFF976A, 0xFFFFB300, 0xFF1989FA,
    0xFF07C160, 0xFF7232DD, 0xFF00BCD4, 0xFFE91E63,
    0xFF009688, 0xFFFF5722, 0xFF607D8B, 0xFF795548,
).map { Color(it) }

private data class CatBar(val name: String, val amount: Double, val percent: Int, val color: Color)

/**
 * 类目消费占比（web `views/Finance/report/stats/CategoryRatio.vue`）：
 * 月份切换 → 全部/收入/支出 → 分类色条（可勾选参与统计、可点开明细抽屉）→
 * 大/中/小三档环形图（勾选占比 ≥5% / 2~5% / <2%）→ 分类明细抽屉。
 */
@Composable
fun CategoryRatioScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph

    // keep-alive：本页会跳「流水详情」，返回时普通 remember 会丢 ⇒ 月份跳回当月
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var showMonthPick by remember { mutableStateOf(false) }
    var activeType by remember { mutableStateOf("all") }
    var allList by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val checked = remember { mutableStateListOf<String>() }
    var showAll by remember { mutableStateOf(false) }
    var drawerCat by remember { mutableStateOf<String?>(null) }
    var drawerType by remember { mutableStateOf("all") }

    LaunchedEffect(month) {
        loading = true
        allList = when (val r = graph.flow.listRange(
            month.atDay(1).toString(), month.atEndOfMonth().toString(), limit = 10000,
        )) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> emptyList()
        }
        loading = false
    }

    // 分类汇总（web categoryBreakdown）
    val breakdown = remember(allList, activeType) {
        val items = when (activeType) {
            "income" -> allList.filter { it.direction == 1 }
            "expense" -> allList.filter { it.direction == 0 }
            else -> allList
        }
        val map = LinkedHashMap<String, Double>()
        items.forEach { m -> map[m.categoryName] = (map[m.categoryName] ?: 0.0) + m.amount }
        val total = map.values.sum()
        if (total <= 0) emptyList()
        else map.entries.sortedByDescending { it.value }.mapIndexed { i, (name, amount) ->
            CatBar(name, amount, ((amount / total) * 100).roundToInt(), CAT_COLORS[i % CAT_COLORS.size])
        }
    }
    // 数据变化自动全选
    LaunchedEffect(breakdown) { checked.clear(); checked.addAll(breakdown.map { it.name }); showAll = false }

    val allChecked = checked.size == breakdown.size && breakdown.isNotEmpty()
    // ⚠ key 不能用 `checked` 本身：它是**同一个** `SnapshotStateList` 实例，`remember` 按 `equals`
    //   比 key ⇒ 永远"没变"，缓存里停在首帧（`LaunchedEffect(breakdown)` 还没填充、checked 为空）算出的
    //   `emptyList()`，`tiers` 随之为空 ⇒ 圆环图整块不进组合，只有切 tab 让 breakdown 换实例才重算一次。
    //   web 侧是 `watch(..., {immediate:true})` + `computed`（同一 tick 生效），首帧就有环。
    val filtered = remember(breakdown, checked.toSet()) {
        val raw = breakdown.filter { it.name in checked }
        val total = raw.sumOf { it.amount }
        if (total <= 0) emptyList()
        else raw.map { it to ((it.amount / total) * 100).roundToInt() }
    }
    val suffix = when (activeType) { "income" -> "收入"; "expense" -> "支出"; else -> "" }
    val tiers = remember(filtered, suffix) {
        val big = mutableListOf<Pair<CatBar, Int>>()
        val mid = mutableListOf<Pair<CatBar, Int>>()
        val small = mutableListOf<Pair<CatBar, Int>>()
        filtered.forEach { (c, pct) ->
            when { pct >= 5 -> big.add(c to pct); pct >= 2 -> mid.add(c to pct); else -> small.add(c to pct) }
        }
        buildList {
            if (big.isNotEmpty()) add(Triple("大额$suffix", big, big.sumOf { it.first.amount }))
            if (mid.isNotEmpty()) add(Triple("中等$suffix", mid, mid.sumOf { it.first.amount }))
            if (small.isNotEmpty()) add(Triple("小额$suffix", small, small.sumOf { it.first.amount }))
        }
    }

    val drawerItems = remember(drawerCat, drawerType, allList) {
        allList.filter { item ->
            (item.categoryName) == drawerCat && when (drawerType) {
                "income" -> item.direction == 1
                "expense" -> item.direction == 0
                else -> true
            }
        }
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 月份切换
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VanIcon("arrow-left", size = 18.sp, color = colors.primary,
                    modifier = Modifier.clickable { month = month.minusMonths(1) })
                // web `.month-text{font-size:16px; font-weight:600}` → 按 §7.9 阶梯 16px ⇒ **18f**
                FText("${month.year}年${month.monthValue.toString().padStart(2, '0')}月", 18f, FontWeight.SemiBold,
                    colors.textPrimary, modifier = Modifier
                        .clickable { showMonthPick = true }
                        .padding(horizontal = 16.dp))
                VanIcon("arrow", size = 18.sp, color = colors.primary,
                    modifier = Modifier.clickable { month = month.plusMonths(1) })
            }
            // 类型 tabs
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部" to "all", "收入" to "income", "支出" to "expense").forEach { (label, value) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (activeType == value) colors.primary else colors.bgThird)
                            .clickable { activeType = value }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) { FText(label, 12f, color = if (activeType == value) Color.White else colors.textTertiary) }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    FText("加载中…", 16f, color = colors.textTertiary)
                }
            } else if (breakdown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    VanEmpty(description = "暂无数据")
                }
            } else {
                Column(Modifier.padding(16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // web `.section-title{font-size:14px; font-weight:600}` → 按 §7.9 阶梯 14px ⇒ **16f**
                        FText("分类占比", 16f, FontWeight.SemiBold, colors.textPrimary)
                        // ⚠ `.section-toggle` 在 web **没有任何 CSS 规则** ⇒ 继承 `.section-title`（14px/600/text-primary ⇒ §7.9 16f），
                        //   不是「12px 主色」（旧实现凭感觉写成了主色小字）
                        FText(if (allChecked) "取消全选" else "全选", 16f, FontWeight.SemiBold, colors.textPrimary,
                            modifier = Modifier.clickable {
                                checked.clear()
                                if (!allChecked) checked.addAll(breakdown.map { it.name })
                            })
                    }
                    Spacer(Modifier.height(8.dp))
                    val visible = if (showAll) breakdown else breakdown.take(5)
                    visible.forEach { bar ->
                        val off = bar.name !in checked
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { drawerCat = bar.name; drawerType = "all" }
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                                .let { if (off) it.alpha(0.35f) else it },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 色点（点击勾选/取消）
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(if (off) colors.textTertiary else bar.color)
                                        .clickable {
                                            if (bar.name in checked) checked.remove(bar.name) else checked.add(bar.name)
                                        },
                                )
                                // web `.cat-bar-name{font-size:13px}`→§7.9 14f / `.cat-bar-pct{font-size:11px; min-width:26px; text-align:right}`→§7.9 12f /
                                //   `.cat-bar-value{font-size:12px; font-weight:600; min-width:52px; text-align:right}`（12px 不动）
                                FText(bar.name, 14f, color = colors.textPrimary,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                                BarText("${bar.percent}%", 12f, colors.textTertiary,
                                    Modifier.widthIn(min = 26.dp), textAlign = TextAlign.End)
                                Spacer(Modifier.width(8.dp))
                                BarText(
                                    if (bar.amount >= 1000) "¥${Money.format(bar.amount / 10000)}万" else "¥${Money.format(bar.amount)}",
                                    12f, colors.textPrimary,
                                    Modifier.widthIn(min = 52.dp), weight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End,
                                )
                                VanIcon("arrow", size = 12.sp, color = colors.textTertiary,
                                    modifier = Modifier.padding(start = 4.dp).clickable { drawerCat = bar.name; drawerType = "all" })
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.bgThird)) {
                                Box(Modifier.fillMaxWidth(bar.percent / 100f).height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(bar.color))
                            }
                        }
                    }
                    if (breakdown.size > 5) {
                        Row(
                            // web `.cat-bar-expand{padding:10px 0 4px; gap:4px}`
                            Modifier.fillMaxWidth().clickable { showAll = !showAll }
                                .padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FText(if (showAll) "收起" else "展开全部 (${breakdown.size}项)", 12f, color = colors.primary)
                            Spacer(Modifier.width(4.dp))
                            VanIcon(if (showAll) "arrow-up" else "arrow-down", size = 10.sp, color = colors.primary)
                        }
                    }
                }

                // ===== 三档环形图：web 是**左右翻页** =====
                // `<van-swipe :loop="false" :show-indicators="tiers.length > 1" indicator-color="#1989fa">`
                //   + 一档一个 `van-swipe-item`（`CategoryRatio.vue:56-66`）⇒ 一屏只看到一档，横向滑动切换。
                //   原生此前是 `tiers.forEach` 上下堆叠 —— 负责人 2026-09-20 明确「我要的就是左右滑」。
                //   底座 `VanSwipe`（横向走 HorizontalPager、带指示点）之前 0 处业务调用，这里是第一处。
                if (tiers.isNotEmpty()) {
                    VanSwipe(
                        count = tiers.size,
                        loop = false,                                     // web :loop="false"（不首尾相接）
                        showIndicators = tiers.size > 1,                   // web 只在多档时显示圆点
                        indicatorColor = Color(0xFF1989FA),               // web indicator-color="#1989fa"
                        modifier = Modifier.fillMaxWidth(),
                    ) { idx ->
                        val (label, cats, total) = tiers[idx]
                        // web `.tier-big/.tier-mid/.tier-small`：底 4% + **1px 同色系描边** + radius 10
                        val (bg, bd) = when {
                            label.startsWith("大额") -> Color(0x0AEE0A24) to Color(0x1FEE0A24)
                            label.startsWith("中等") -> Color(0x0AFFB300) to Color(0x24FFB300)
                            else -> Color(0x0A1989FA) to Color(0x1F1989FA)
                        }
                        val shape = RoundedCornerShape(10.dp)
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                                .clip(shape).background(bg)
                                .border(1.dp, bd, shape)
                                .padding(14.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText(label, 16f, FontWeight.Bold, colors.textPrimary)
                                FText(Money.format(total), 16f, FontWeight.Bold, colors.textPrimary)
                            }
                            DonutChart(
                                data = cats.map { (c, _) -> Triple(c.name, c.amount.toFloat(), c.color) },
                                // web `.tier-chart{ width:100%; height:240px }`（原来是 220dp）
                                modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 8.dp),
                            )
                            // 图例（原生补充的读数区，web 靠 echarts tooltip；翻页后每档自带，保留）
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                cats.forEach { (c, pct) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.color))
                                        FText(" ${c.name}", 12f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                                        FText("${pct}% · ¥${Money.format(c.amount)}", 12f, FontWeight.Medium, colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // 月份选择（web `van-calendar`：single / min 2020-01-01 / max 今天，确认后取当月 1 日）
    VanCalendar(
        show = showMonthPick,
        onClose = { showMonthPick = false },
        type = VanCalendarType.Single,
        minDate = LocalDate.of(2020, 1, 1),
        maxDate = LocalDate.now(),
        defaultDate = listOf(month.atDay(1)),
        onConfirm = { dates ->
            dates.firstOrNull()?.let { month = YearMonth.from(it) }
            showMonthPick = false
        },
    )
    // 分类明细抽屉
    if (drawerCat != null) {
        VanPopup(show = true, onDismissRequest = { drawerCat = null }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    FText(drawerCat.orEmpty(), 18f, FontWeight.SemiBold, colors.textPrimary)
                    VanIcon("cross", size = 18.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { drawerCat = null })
                }
                Row(Modifier.padding(start = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部" to "all", "收入" to "income", "支出" to "expense").forEach { (label, value) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (drawerType == value) colors.primary else colors.bgThird)
                                .clickable { drawerType = value }
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) { FText(label, 12f, color = if (drawerType == value) Color.White else colors.textTertiary) }
                    }
                }
                Column(Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState())) {
                    if (drawerItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                            FText("暂无记录", 14f, color = colors.textTertiary)
                        }
                    }
                    drawerItems.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (item.id.isNotEmpty()) { drawerCat = null; nav.navigate(com.live.finance.core.nav.Routes.flowDetail(item.id)) }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (item.direction == 1) Color(0xFFE8F8EE) else Color(0xFFFFF0F0)),
                                contentAlignment = Alignment.Center,
                            ) {
                                VanIcon(if (item.direction == 1) "arrow-down" else "arrow-up", size = 14.sp,
                                    color = if (item.direction == 1) MoneyColor.income else MoneyColor.expense)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    FText(item.categoryName, 14f, FontWeight.Medium, colors.textPrimary)
                                    FText(
                                        (if (item.direction == 1) "+" else "-") + Money.format(item.amount), 16f, FontWeight.Bold,
                                        if (item.direction == 1) MoneyColor.income else MoneyColor.expense,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    FText(item.transDate.take(10), 12f, color = colors.textTertiary)
                                    FText(item.remark.ifEmpty { "无备注" }, 12f, color = colors.textTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 环形图（web echarts pie 40%~62% 的 Canvas 等价）。 */
@Composable
private fun DonutChart(data: List<Triple<String, Float, Color>>, modifier: Modifier) {
    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val total = data.sumOf { it.second.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val strokeOuter = size.minDimension * 0.31f
        val strokeInner = size.minDimension * 0.20f
        val stroke = strokeOuter - strokeInner
        val radius = (strokeOuter + strokeInner) / 2f
        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
        var start = -90f
        data.forEach { (_, v, color) ->
            val sweep = v / total * 360f
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

/**
 * 带 `textAlign` 的纯文本（web 里靠 `text-align` 排版的单元格用）。
 *
 * ⚠ **不要叫 `FTextA`**：`DebtOverviewScreen.kt` 里已有同包同签名的 `private fun FTextA`，
 *   顶层 private 虽是文件级可见，但解析时会同时列出两个候选而报「None of the following functions
 *   can be called with the arguments supplied」（2026-09-19 实测）。换个名字最省事。
 */
@Composable
private fun BarText(
    text: String, sizeSp: Float, color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) = androidx.compose.foundation.text.BasicText(
    text, modifier,
    androidx.compose.ui.text.TextStyle(color = color, fontSize = scaledSp(sizeSp), fontWeight = weight, textAlign = textAlign),
)
