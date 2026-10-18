package com.live.finance.ui.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 双轴折线图（左右 Y 轴不同刻度，适合对比两个指标）。
 *
 * 视觉基准：web `views/Finance/report/fund/FundTrend.vue` 的 echarts option：
 *
 * ```
 * legend { data:['登记收益','当前市值'], bottom:0, textStyle:{fontSize:10} }
 * grid  { left:3%, right:4%, bottom:20%, top:8%, containLabel:true }
 * xAxis { axisLabel:{ fontSize:10, rotate:45, interval:'auto' } }
 * yAxis[0] { name:'收益', axisLabel:{ fontSize:10, formatter:v=>v.toFixed(2) } }
 * yAxis[1] { name:'市值', axisLabel:{ fontSize:10, formatter:v=>abs>=1e4?(v/1e4).toFixed(2)+'万':v.toFixed(2) } }
 * series[0] { name:'登记收益', smooth:true, symbol:'none', yAxisIndex:0, lineStyle:{width:2} }
 * series[1] { name:'当前市值', smooth:true, symbol:'none', yAxisIndex:1, lineStyle:{type:'dashed',width:2} }
 * ```
 *
 * 实现要点：
 * - 两轴标签一律 `#969799 / 10sp`（web `axisLabel.fontSize:10`，无显式色 ⇒ 项目 tertiary）。
 * - 网格线走主题 `splitLine`（不再用系列色 alpha 0.1，web 未指定系列色网格）。
 * - `symbol:'none'` ⇒ 默认不画数据点圆点（`pointRadius = 0.dp`）。
 * - X 轴标签 `rotate:45` + `interval:'auto'`（用 `drawAxisLabels` 的 hideOverlap 实现）。
 */
@Composable
fun DualAxisChart(
    leftSeries: List<SeriesData>,
    rightSeries: List<SeriesData>,
    leftColor: Color,
    rightColor: Color,
    dates: List<String>,
    leftLabelStyle: TextStyle? = null,
    rightLabelStyle: TextStyle? = null,
    xLabelStyle: TextStyle? = null,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(left = 4.dp, right = 4.dp, top = 8.dp, bottom = 6.dp),
    gridLines: Int = 5,
    lineWidth: Dp = 2.dp,
    pointRadius: Dp = 0.dp,
    xLabelRotation: Float = 45f,
    rightDashed: Boolean = false,
    leftLabelFormatter: ((Double) -> String)? = null,
    rightLabelFormatter: ((Double) -> String)? = null,
    maxPoints: Int = 200,
) {
    val allLeft = leftSeries.flatMap { it.data.filterNotNull() }
    val allRight = rightSeries.flatMap { it.data.filterNotNull() }
    if (allLeft.isEmpty() && allRight.isEmpty()) return

    val axis = rememberChartAxisColors()
    val effLeftStyle: TextStyle = leftLabelStyle ?: chartAxisLabelStyle(10f, axis.axisLabel)
    val effRightStyle: TextStyle = rightLabelStyle ?: chartAxisLabelStyle(10f, axis.axisLabel)
    val effXStyle: TextStyle = xLabelStyle ?: chartAxisLabelStyle(10f, axis.axisLabel)
    val textMeasurer = rememberTextMeasurer()

    val leftMin = if (allLeft.isEmpty()) 0.0 else allLeft.minOrNull()!!
    val leftMax = if (allLeft.isEmpty()) 1.0 else allLeft.maxOrNull()!!
    val rightMin = if (allRight.isEmpty()) 0.0 else allRight.minOrNull()!!
    val rightMax = if (allRight.isEmpty()) 1.0 else allRight.maxOrNull()!!

    val leftTicks = remember(leftMin, leftMax, gridLines) { niceAxisTicks(leftMin, leftMax, gridLines + 1) }
    val rightTicks = remember(rightMin, rightMax, gridLines) { niceAxisTicks(rightMin, rightMax, gridLines + 1) }

    val leftFmt = leftLabelFormatter ?: { formatTickAmount(it) }
    val rightFmt = rightLabelFormatter ?: { formatTickAmount(it) }
    val leftLabelTexts = leftTicks.map { leftFmt(it) }
    val rightLabelTexts = rightTicks.map { rightFmt(it) }

    // containLabel:true —— 主轴标签宽度从网格两侧扣除
    val leftLayouts = remember(leftLabelTexts, effLeftStyle) {
        leftLabelTexts.map { textMeasurer.measure(it, effLeftStyle) }
    }
    val rightLayouts = remember(rightLabelTexts, effRightStyle) {
        rightLabelTexts.map { textMeasurer.measure(it, effRightStyle) }
    }
    val maxLeftW = leftLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
    val maxRightW = rightLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f

    val xLabelTexts = dates.map { formatChartDate(it) }
    val xLabelLayouts = remember(xLabelTexts, effXStyle) {
        xLabelTexts.map { textMeasurer.measure(it, effXStyle) }
    }
    val xLabelH = axisLabelsHeight(xLabelLayouts, xLabelRotation)

    // 降采样
    val sampledLeft = remember(leftSeries, maxPoints) {
        leftSeries.map { s -> s.copy(data = downsampleSimple(s.data.filterNotNull(), maxPoints)) }
    }
    val sampledRight = remember(rightSeries, maxPoints) {
        rightSeries.map { s -> s.copy(data = downsampleSimple(s.data.filterNotNull(), maxPoints)) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val gap = 4.dp.toPx()
        val padL = padding.calculateLeftDp().toPx() + maxLeftW + gap
        val padR = padding.calculateRightDp().toPx() + maxRightW + gap
        val padT = padding.calculateTopDp().toPx()
        val padB = padding.calculateBottomDp().toPx()
        val left = padL
        val right = size.width - padR
        val plotTop = padT
        val plotBottom = size.height - padB - xLabelH - gap
        val plotH = plotBottom - plotTop

        // —— 网格线（web splitLine 主题色，宽 1）——
        leftTicks.forEachIndexed { i, _ ->
            val y = plotBottom - plotH * i / (leftTicks.size - 1).coerceAtLeast(1)
            drawLine(
                color = axis.splitLine,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // —— 左轴标签：右对齐到网格左边界（containLabel）——
        leftLayouts.forEachIndexed { i, layout ->
            val y = plotBottom - plotH * i / (leftTicks.size - 1).coerceAtLeast(1)
            drawText(
                layout,
                topLeft = Offset(left - gap - layout.size.width, y - layout.size.height / 2f),
            )
        }

        // —— 右轴标签：左对齐到网格右边界（containLabel）——
        rightLayouts.forEachIndexed { i, layout ->
            val y = plotBottom - plotH * i / (rightTicks.size - 1).coerceAtLeast(1)
            drawText(layout, topLeft = Offset(right + gap, y - layout.size.height / 2f))
        }

        val xStep = if (dates.size > 1) (right - left) / (dates.size - 1) else 0f
        val leftYScale = if (leftTicks.size > 1) plotH / (leftTicks.last() - leftTicks.first()).toFloat() else 1f
        val rightYScale = if (rightTicks.size > 1) plotH / (rightTicks.last() - rightTicks.first()).toFloat() else 1f

        val leftPaths = sampledLeft.flatMap { s ->
            val points = s.data.mapIndexedNotNull { i, v ->
                if (v == null) return@mapIndexedNotNull null
                Offset(left + i * xStep, plotBottom - (v - leftTicks.first()).toFloat() * leftYScale)
            }
            if (points.size >= 2) listOf(buildSmoothPath(points, closed = false) to points)
            else emptyList()
        }
        val rightPaths = sampledRight.flatMap { s ->
            val points = s.data.mapIndexedNotNull { i, v ->
                if (v == null) return@mapIndexedNotNull null
                Offset(left + i * xStep, plotBottom - (v - rightTicks.first()).toFloat() * rightYScale)
            }
            if (points.size >= 2) listOf(buildSmoothPath(points, closed = false) to points)
            else emptyList()
        }

        val r = pointRadius.toPx()
        leftPaths.forEach { (path, pts) ->
            drawPath(path, leftColor, style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round))
            // web `symbol:'none'` —— pointRadius 为 0 时不画数据点
            if (r > 0f) pts.forEach { drawCircle(leftColor, radius = r, center = it) }
        }
        rightPaths.forEach { (path, pts) ->
            // web 市值系列 `lineStyle:{type:'dashed'}`
            drawPath(
                path, rightColor,
                style = Stroke(
                    width = lineWidth.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (rightDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) else null,
                ),
            )
            if (r > 0f) pts.forEach { drawCircle(rightColor, radius = r, center = it) }
        }

        // —— X 轴轴线（web axisLine 主题色）+ 标签 rotate:45 / hideOverlap ——
        drawLine(
            color = axis.axisLine,
            start = Offset(left, plotBottom),
            end = Offset(right, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )
        drawAxisLabels(
            labels = xLabelLayouts.mapIndexed { i, layout -> AxisLabel(layout, left + i * xStep) },
            leftBound = left,
            rightBound = right,
            baselineY = plotBottom + gap,
            rotationDeg = xLabelRotation,
        )
    }
}
