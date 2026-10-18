package com.live.finance.ui.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 环形图（饼图 donut，支持多扇区、扇区标签、中心文字、emphasis 放大标签）。
 *
 * 视觉基准：web `views/Finance/report/stats/StatsOverview.vue` 的 echarts option：
 *
 * ```
 * series[0] { type:'pie', radius:['48%','70%'], avoidLabelOverlap:true,
 *             label:{show:true, formatter:'{b}', fontSize:12},
 *             emphasis:{label:{show:true, fontSize:14, fontWeight:'bold'}} }
 * ```
 *
 * - `radius['48%','70%']`：内外径按**短边**百分比折算（ECharts 同语义）。
 * - `avoidLabelOverlap:true`：标签按角度排布后做垂直错位消重叠。
 * - emphasis：由 [activeSliceIndex] 指定选中扇区，该扇区标签放大为 14sp / bold。
 *
 * @param slices 扇区列表（标签、数值、颜色）
 * @param innerRadiusRatio 内径比例（web `radius[0]`）
 * @param outerRadiusRatio 外径比例（web `radius[1]`）
 * @param showLabel 是否显示扇区标签（web `label.show`）
 * @param activeSliceIndex emphasis 扇区下标，-1 表示无
 * @param centerText 中心文字（可为 null）
 * @param modifier 容器 modifier
 * @param labelStyle 扇区标签样式；null 时用主题默认（`#969799 / 12sp`）
 * @param emphasisLabelStyle emphasis 扇区标签样式；null 时 `14sp / bold` + `textPrimary`
 * @param centerTextStyle 中心文字样式
 * @param labelGap 标签与环外缘的间距（dp）
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    innerRadiusRatio: Float = 0.48f,
    outerRadiusRatio: Float = 0.70f,
    showLabel: Boolean = true,
    activeSliceIndex: Int = -1,
    centerText: String? = null,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle? = null,
    emphasisLabelStyle: TextStyle? = null,
    centerTextStyle: TextStyle = TextStyle.Default,
    labelGap: Dp = 6.dp,
) {
    if (slices.isEmpty()) return

    val axis = rememberChartAxisColors()
    // web `label:{fontSize:12}`（无显式色 ⇒ 走项目文本体系 tertiary）
    val effLabelStyle: TextStyle = labelStyle ?: chartAxisLabelStyle(12f, axis.axisLabel)
    // web `emphasis.label:{fontSize:14, fontWeight:'bold'}`
    val effEmphasisStyle: TextStyle = emphasisLabelStyle
        ?: chartAxisLabelStyle(14f, axis.valueLabel).copy(fontWeight = FontWeight.Bold)

    val textMeasurer = rememberTextMeasurer()
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    // 预先测量所有标签（emphasis 扇区用放大的样式），避免在 DrawScope 内重复 measure
    val measured = remember(slices, showLabel, effLabelStyle, effEmphasisStyle, activeSliceIndex) {
        if (!showLabel) emptyList() else slices.mapIndexed { i, s ->
            if (s.label.isBlank()) null else LabelSlot(
                layout = textMeasurer.measure(
                    s.label,
                    if (i == activeSliceIndex) effEmphasisStyle else effLabelStyle,
                ),
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f * outerRadiusRatio
        val inner = size.minDimension / 2f * innerRadiusRatio
        val radius = (outer + inner) / 2f
        val strokeWidth = outer - inner
        val gapPx = labelGap.toPx()

        var startAngle = -90f

        // 每圈只画一次，先集中收集标签位（角度 → 位置），画完环再统一做避让
        val slots = ArrayList<LabelSlot>()
        slices.forEachIndexed { i, slice ->
            val sweep = (slice.value / total * 360f).coerceAtLeast(0.1f)
            if (sweep > 0f) {
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth),
                )
                measured.getOrNull(i)?.let { slot ->
                    val mid = Math.toRadians((startAngle + sweep / 2f).toDouble())
                    slot.dx = cos(mid).toFloat()
                    slot.dy = sin(mid).toFloat()
                    slots.add(slot)
                }
                startAngle += sweep
            }
        }

        if (slots.isNotEmpty()) drawPieLabels(slots, cx, cy, outer + gapPx)

        // 中心文字
        centerText?.let { text ->
            val layout = textMeasurer.measure(text, centerTextStyle)
            drawText(
                layout,
                topLeft = Offset(
                    cx - layout.size.width / 2f,
                    cy - layout.size.height / 2f,
                ),
            )
        }
    }
}

/** 标签槽位：测量结果 + 单位向量 + 目标锚点（在避让阶段回填）。 */
private class LabelSlot(
    val layout: TextLayoutResult,
) {
    var dx: Float = 0f
    var dy: Float = 0f
    var left: Float = 0f
    var top: Float = 0f
}

/**
 * 绘制扇区标签，并实现 web `avoidLabelOverlap:true` 的简化版：
 * 标签贴环外缘放置，随后按 y 排序做垂直推开，最后夹紧在画布内。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPieLabels(
    slots: List<LabelSlot>,
    cx: Float,
    cy: Float,
    anchorRadius: Float,
) {
    if (slots.isEmpty()) return

    // 1) 初始锚点（web 的 label 默认 position 'outside'）
    slots.forEach { slot ->
        val w = slot.layout.size.width.toFloat()
        val h = slot.layout.size.height.toFloat()
        val x = cx + slot.dx * anchorRadius
        val y = cy + slot.dy * anchorRadius
        // dx<0 在左侧 ⇒ 文本右对齐到锚点；dx>=0 在右侧 ⇒ 左对齐到锚点
        slot.left = (if (slot.dx < 0f) x - w else x)
            .coerceIn(0f, maxOf(0f, size.width - w))
        slot.top = y - h / 2f
    }

    // 2) avoidLabelOverlap：自上而下把垂直重叠的标签逐个推开
    val minGap = 2.dp.toPx()
    val ordered = slots.sortedBy { it.top }
    for (i in 1 until ordered.size) {
        val prev = ordered[i - 1]
        val cur = ordered[i]
        val prevBottom = prev.top + prev.layout.size.height
        if (cur.top < prevBottom + minGap) cur.top = prevBottom + minGap
    }

    // 3) 底部溢出时整体上移，再逐个夹紧，保证标签不出画布
    val lastBottom = ordered.last().top + ordered.last().layout.size.height
    val shift = lastBottom - size.height
    if (shift > 0f) ordered.forEach { it.top -= shift }
    ordered.forEach { slot ->
        drawText(
            slot.layout,
            topLeft = Offset(
                slot.left,
                slot.top.coerceIn(0f, maxOf(0f, size.height - slot.layout.size.height)),
            ),
        )
    }
}

/** 环形图扇区数据。 */
data class DonutSlice(
    val label: String,
    val value: Float,
    val color: Color,
)
