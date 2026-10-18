package com.live.finance.ui.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 迷你折线图（紧凑型，适合卡片内嵌）。
 *
 * 视觉基准：web `views/Finance/assets/List.vue` `renderMini()` 的 echarts option：
 *
 * ```
 * grid      { top:8, right:10, bottom:18, left:4, containLabel:true }
 * xAxis     { boundaryGap:false, axisTick:{show:false}, axisLine{#dcdee0},
 *             axisLabel{color:#969799, fontSize:9, hideOverlap:true, formatter:v=>v} }
 * yAxis     { minInterval:1, splitNumber:2, splitLine{solid #eceef1},
 *             axisLine:{show:false}, axisLabel{color:#969799, fontSize:9, formatter:formatLabelMini} }
 * series    { smooth:true, symbol:'circle', symbolSize:4, connectNulls:true,
 *             lineStyle{width:2}, emphasis:{scale:false}, areaStyle{opacity:0.1, color:'#3b82f6'} }
 * ```
 *
 * @param points 数据点列表（值，按时间升序）
 * @param dates X 轴日期原始值（长度需与 points 一致；web 的 `miniDates` 就是完整日期串）
 * @param color 折线颜色（web 硬编码 `#3b82f6`）
 * @param showArea 是否显示面积（web `areaStyle.opacity:0.1`）
 * @param gridLines 网格分段数（web `splitNumber:2`）
 * @param modifier 容器 modifier
 * @param padding 网格外扩量（web `grid.left/right/top/bottom`，轴标签宽度由 containLabel 扣除）
 * @param labelStyle Y 轴标签样式；null 时用主题默认（web `#969799 / 9sp`）
 * @param xLabelStyle X 轴标签样式；null 时用主题默认（web `#969799 / 9sp`）
 * @param pointRadius 数据点半径（web `symbolSize:4` 是直径）
 * @param areaOpacity 面积透明度（web `areaStyle.opacity:0.1`）
 */
@Composable
fun MiniChart(
    points: List<Double>,
    dates: List<String> = emptyList(),
    color: Color,
    showArea: Boolean = true,
    gridLines: Int = 2,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(left = 4.dp, right = 10.dp, top = 8.dp, bottom = 4.dp),
    labelStyle: TextStyle? = null,
    xLabelStyle: TextStyle? = null,
    pointRadius: Dp = 2.dp,
    areaOpacity: Float = 0.1f,
    maxPoints: Int = 200,
) {
    if (points.size < 2) return

    val axis = rememberChartAxisColors()
    val effLabelStyle: TextStyle = labelStyle ?: chartAxisLabelStyle(9f, axis.axisLabel)
    val effXLabelStyle: TextStyle = xLabelStyle ?: chartAxisLabelStyle(9f, axis.axisLabel)
    val textMeasurer = rememberTextMeasurer()

    // web echarts 默认 `scale:false` —— 值域包含 0
    val dataMin = min(0.0, points.minOrNull() ?: 0.0)
    val dataMax = max(0.0, points.maxOrNull() ?: 1.0)
    val ticks = remember(dataMin, dataMax, gridLines) {
        niceAxisTicks(dataMin, dataMax, gridLines + 1, minStep = 1.0)
    }

    val sampled = remember(points, maxPoints) { downsampleSimple(points, maxPoints) }
    // 降采样后索引位可能对不上原始日期 —— 同步筛一份日期（web 不做降采样，这里只为控点数）
    val sampledDates = remember(dates, points, maxPoints) {
        if (dates.isEmpty()) emptyList() else {
            val step = if (points.size <= maxPoints) 1.0
            else (points.size - 1).toDouble() / (maxPoints - 1)
            (sampled.indices).map { i ->
                dates.getOrElse(
                    if (points.size <= maxPoints) i
                    else min((i * step).roundToInt(), dates.lastIndex)
                ) { "" }
            }
        }
    }

    // containLabel:true —— Y 轴标签宽度从网格左侧扣除
    val yLabelTexts = ticks.map { formatTickAmount(it) }
    val yLabelLayouts = remember(yLabelTexts, effLabelStyle) {
        yLabelTexts.map { textMeasurer.measure(it, effLabelStyle) }
    }
    val maxYLabelW = yLabelLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f

    // web xAxis.formatter 是 `v => v`（原样输出日期），所以必须用 miniDates 而不是数值
    val xLabelTexts = sampledDates.map { formatChartDate(it) }.ifEmpty {
        // 调用方未提供 dates 时的兜底：按序号占位，避免再把数值画到 X 轴上
        sampled.indices.map { it.toString() }
    }
    val xLabelLayouts = remember(xLabelTexts, effXLabelStyle) {
        xLabelTexts.map { textMeasurer.measure(it, effXLabelStyle) }
    }
    val xLabelH = xLabelLayouts.maxOfOrNull { it.size.height }?.toFloat() ?: 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        val gap = 4.dp.toPx()
        val padL = padding.calculateLeftDp().toPx() + maxYLabelW + gap
        val padR = padding.calculateRightDp().toPx()
        val padT = padding.calculateTopDp().toPx()
        val padB = padding.calculateBottomDp().toPx()
        val w = size.width - padL - padR
        val right = size.width - padR
        val plotTop = padT
        val plotBottom = size.height - padB - xLabelH - gap
        val plotH = plotBottom - plotTop

        // —— 网格线（web splitLine：实线 #eceef1）——
        if (gridLines > 0) {
            repeat(gridLines + 1) { i ->
                val gy = plotBottom - plotH * i / gridLines
                drawLine(
                    color = axis.splitLine,
                    start = Offset(padL, gy),
                    end = Offset(right, gy),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        // web `yAxis.axisLine:{show:false}`

        // —— Y 轴标签（web splitNumber:2 ⇒ 顶/中/底三条）——
        // ⚠ 这里要遍历的是**已测量好的** yLabelLayouts（TextLayoutResult），不是 ticks（List<Double>）
        yLabelLayouts.forEachIndexed { i, layout ->
            val gy = plotBottom - plotH * i / (yLabelLayouts.size - 1).coerceAtLeast(1)
            drawText(
                layout,
                topLeft = Offset(padL - gap - layout.size.width, gy - layout.size.height / 2f),
            )
        }

        val yScale = if (ticks.size > 1) plotH / (ticks.last() - ticks.first()).toDouble() else 1.0
        val xStep = if (sampled.size > 1) w / (sampled.size - 1) else 0f

        val pts = sampled.mapIndexed { i, v ->
            Offset(padL + i * xStep, plotBottom - ((v - ticks.first()) * yScale).toFloat())
        }
        val path = buildSmoothPath(pts, closed = false)
        if (showArea) drawPath(buildAreaPath(pts, plotBottom), color.copy(alpha = areaOpacity))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { drawCircle(color, radius = pointRadius.toPx(), center = it) }

        // —— X 轴轴线（web axisLine{#dcdee0}，axisTick:{show:false}）——
        drawLine(
            color = axis.axisLine,
            start = Offset(padL, plotBottom),
            end = Offset(right, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )

        // —— X 轴标签：完整日期 + hideOverlap（web formatter:v=>v）——
        drawAxisLabels(
            labels = xLabelLayouts.mapIndexed { i, layout -> AxisLabel(layout, padL + i * xStep) },
            leftBound = padL,
            rightBound = right,
            baselineY = plotBottom + gap,
        )
    }
}
