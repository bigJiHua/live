package com.live.finance.ui.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 多系列折线图（支持平滑曲线、面积渐变、点击选点、高亮索引）。
 *
 * 视觉基准：web `views/Finance/assets/Trend.vue` 的 echarts option，逐项对齐：
 *
 * ```
 * yAxis      { minInterval:1, splitNumber:4, splitLine{solid #eceef1 w:1}, axisLine:{show:false},
 *              axisLabel{color:#969799, fontSize:10, formatter:formatLabel} }
 * xAxis      { boundaryGap:false, axisTick{alignWithLabel:true}, axisLine{#dcdee0},
 *              axisLabel{color:#969799, fontSize:10, hideOverlap:true, rotate:many?30:0, formatter:formatDate} }
 * grid       { top:8, right:8, left:4, containLabel:true }
 * series     { smooth:true, symbol:'circle', symbolSize:5, connectNulls:true,
 *              lineStyle{width:2}, emphasis:{scale:false}, areaStyle{opacity:0.08} }
 * axisPointer{ type:'line', snap:true, lineStyle{color:'#3b82f6', type:'dashed', width:1.5} }
 * ```
 *
 * @param series 系列列表，每个系列包含名称和数据点（可为 null 表示缺数据）
 * @param dates X 轴日期原始值（长度与 series[0].data 一致；支持 `YYYY-MM-DD` 或时间戳）
 * @param colors 系列颜色列表
 * @param showArea 是否显示面积填充（web `areaStyle.opacity`）
 * @param smooth 是否平滑曲线（二次贝塞尔，web `smooth:true`）
 * @param onPointClick 点击数据点回调（seriesIndex, dataIndex）
 * @param activeIndices 高亮索引列表（每个系列对应一个索引，-1 表示不高亮）
 * @param modifier 容器 modifier
 * @param padding 网格外扩量（web `grid.left/right/top/bottom`，**不含**轴标签宽度——
 *              轴标签由 `containLabel:true` 自行扣除，见下方实现）
 * @param gridLines 网格分段数（web `splitNumber`）
 * @param labelStyle 轴标签文字样式；null 时用主题默认（web `#969799 / 10sp`）
 * @param pointRadius 数据点半径（web `symbolSize` 是直径，故半径取一半）
 * @param lineWidth 线宽（web `lineStyle.width:2`）
 * @param areaOpacity 面积透明度（web `areaStyle.opacity:0.08`）
 * @param highlightColor 高亮竖线颜色（web `axisPointer.lineStyle.color`）
 * @param maxPoints 单系列最大绘制点数（超出降采样）
 */
@Composable
fun LineChart(
    series: List<SeriesData>,
    dates: List<String>,
    colors: List<Color>,
    showArea: Boolean = false,
    smooth: Boolean = true,
    onPointClick: ((seriesIndex: Int, dataIndex: Int) -> Unit)? = null,
    activeIndices: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(left = 4.dp, right = 8.dp, top = 8.dp, bottom = 6.dp),
    gridLines: Int = 4,
    labelStyle: TextStyle? = null,
    pointRadius: Dp = 2.5.dp,
    lineWidth: Dp = 2.dp,
    areaOpacity: Float = 0.08f,
    highlightColor: Color = Color(0xFF3B82F6),
    maxPoints: Int = 200,
) {
    if (series.isEmpty() || series.all { it.data.isEmpty() }) return

    val axis = rememberChartAxisColors()
    val effLabelStyle: TextStyle = labelStyle
        ?: chartAxisLabelStyle(10f, axis.axisLabel)
    val textMeasurer = rememberTextMeasurer()

    val allValues = series.flatMap { it.data.filterNotNull() }
    if (allValues.isEmpty()) return

    // web echarts 默认 `scale:false` —— 值域一定包含 0（全正从 0 起，全负到 0 止）
    val dataMin = min(0.0, allValues.minOrNull() ?: 0.0)
    val dataMax = max(0.0, allValues.maxOrNull() ?: 1.0)
    val ticks = remember(dataMin, dataMax, gridLines) {
        niceAxisTicks(dataMin, dataMax, gridLines + 1, minStep = 1.0)
    }

    // 降采样：保持**原始索引位**不变（null 缺数据也保留位），否则 X 轴会错位。
    // web 是 `connectNulls:true` —— 跳过断点但 x 位置仍按 dataIndex 对齐。
    val sampledSeries = remember(series, maxPoints) {
        series.map { s ->
            val compact = s.data.filterNotNull()
            if (compact.size <= maxPoints) s else {
                val step = (compact.size - 1).toDouble() / (maxPoints - 1)
                val keep = (0 until maxPoints)
                    .map { (it * step).roundToInt().coerceIn(0, compact.size - 1) }
                    .toHashSet()
                var ci = 0
                s.copy(data = s.data.map { v ->
                    if (v == null) null else {
                        val idx = ci++
                        if (idx in keep) v else null
                    }
                })
            }
        }
    }

    // —— containLabel:true —— 轴标签宽度由组件自己从网格里扣除（web 靠 containLabel 做）
    val yLabelTexts = ticks.map { formatTickAmount(it) }
    val yLabelLayouts = remember(yLabelTexts, effLabelStyle) {
        yLabelTexts.map { textMeasurer.measure(it, effLabelStyle) }
    }
    val maxYLabelW = yLabelLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f

    val xLabelTexts = dates.map { formatChartDate(it) }
    val xLabelLayouts = remember(xLabelTexts, effLabelStyle) {
        xLabelTexts.map { textMeasurer.measure(it, effLabelStyle) }
    }
    // web `rotate: many ? 30 : 0`（many = xData.length > 6）
    val rotation = if (dates.size > 6) 30f else 0f
    // 旋转后的标签占位更高，`containLabel` 必须按真实外扩扣除，否则会被画布底边裁掉
    val xLabelH = axisLabelsHeight(xLabelLayouts, rotation)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onPointClick != null) {
                    Modifier.pointerInput(series, dates, ticks, rotation) {
                        detectTapGestures { offset ->
                            val gap = AxisGapDp.toPx()
                            val padL = padding.calculateLeftDp().toPx() + maxYLabelW + gap
                            val padR = padding.calculateRightDp().toPx()
                            val padT = padding.calculateTopDp().toPx()
                            val padB = padding.calculateBottomDp().toPx()
                            val w = size.width - padL - padR
                            val plotBottom = size.height - padB - xLabelH - gap
                            val plotH = plotBottom - padT
                            val xStep = if (dates.size > 1) w / (dates.size - 1) else 0f
                            val yScale =
                                if (ticks.size > 1) plotH / (ticks.last() - ticks.first()).toDouble() else 1.0

                            series.forEachIndexed { sIdx, s ->
                                s.data.forEachIndexed { dIdx, v ->
                                    if (v == null) return@forEachIndexed
                                    val cx = padL + dIdx * xStep
                                    val cy = plotBottom - ((v - ticks.first()) * yScale).toFloat()
                                    val dist = abs(offset.x - cx) + abs(offset.y - cy)
                                    if (dist < 30.dp.toPx()) onPointClick(sIdx, dIdx)
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        val gap = AxisGapDp.toPx()
        val padL = padding.calculateLeftDp().toPx() + maxYLabelW + gap
        val padR = padding.calculateRightDp().toPx()
        val padT = padding.calculateTopDp().toPx()
        val padB = padding.calculateBottomDp().toPx()
        val w = size.width - padL - padR
        val right = size.width - padR
        val plotTop = padT
        val plotBottom = size.height - padB - xLabelH - gap
        val plotH = plotBottom - plotTop

        // —— 网格线（web splitLine：实线 #eceef1，宽 1）——
        ticks.forEachIndexed { i, _ ->
            val y = plotBottom - plotH * i / (yLabelLayouts.size - 1).coerceAtLeast(1)
            drawLine(
                color = axis.splitLine,
                start = Offset(padL, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // web `yAxis.axisLine:{show:false}` —— 不画 Y 轴轴线

        // —— Y 轴标签：右对齐到网格左边界内侧（containLabel）——
        // ⚠ 这里要遍历的是**已测量好的** yLabelLayouts（TextLayoutResult），不是 ticks（List<Double>）
        yLabelLayouts.forEachIndexed { i, layout ->
            val y = plotBottom - plotH * i / (ticks.size - 1).coerceAtLeast(1)
            drawText(
                layout,
                topLeft = Offset(padL - gap - layout.size.width, y - layout.size.height / 2f),
            )
        }

        val xStep = if (dates.size > 1) w / (dates.size - 1) else 0f
        val yScale =
            if (ticks.size > 1) plotH / (ticks.last() - ticks.first()).toDouble() else 1.0

        val seriesPaths = sampledSeries.map { s ->
            val pts = s.data.mapIndexedNotNull { i, v ->
                if (v == null) return@mapIndexedNotNull null
                Offset(padL + i * xStep, plotBottom - ((v - ticks.first()) * yScale).toFloat())
            }
            val path = if (smooth && pts.size >= 2) {
                buildSmoothPath(pts, closed = false)
            } else if (pts.size >= 2) {
                Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
            } else null
            val areaPath = if (showArea && pts.size >= 2) buildAreaPath(pts, plotBottom) else null
            Triple(path, areaPath, pts)
        }

        seriesPaths.forEachIndexed { sIdx, (path, areaPath, pts) ->
            val color = colors.getOrElse(sIdx) { Color.Gray }
            if (areaPath != null) drawPath(areaPath, color.copy(alpha = areaOpacity))
            if (path != null) {
                drawPath(path, color, style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round))
            }
            pts.forEach { p -> drawCircle(color, radius = pointRadius.toPx(), center = p) }
        }

        // —— X 轴轴线 + 刻度（web axisLine{#dcdee0} / axisTick{alignWithLabel:true}）——
        drawLine(
            color = axis.axisLine,
            start = Offset(padL, plotBottom),
            end = Offset(right, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )
        if (dates.size > 1) {
            for (i in dates.indices) {
                val x = padL + i * xStep
                drawLine(
                    color = axis.axisLine,
                    start = Offset(x, plotBottom),
                    end = Offset(x, plotBottom + 5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // —— X 轴标签：完整日期 + hideOverlap（web `formatter:formatDate`）——
        drawAxisLabels(
            labels = xLabelLayouts.mapIndexed { i, layout -> AxisLabel(layout, padL + i * xStep) },
            leftBound = padL,
            rightBound = right,
            baselineY = plotBottom + gap,
            rotationDeg = rotation,
        )

        // —— 高亮：web axisPointer（type:line, snap, dashed #3b82f6, width:1.5）——
        if (activeIndices.isNotEmpty()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            activeIndices.forEachIndexed { sIdx, idx ->
                if (idx >= 0 && idx < series[sIdx].data.size) {
                    val v = series[sIdx].data[idx]
                    if (v != null) {
                        val x = padL + idx * xStep
                        val y = plotBottom - ((v - ticks.first()) * yScale).toFloat()
                        val color = colors.getOrElse(sIdx) { Color.Gray }
                        drawLine(
                            color = highlightColor,
                            start = Offset(x, plotTop),
                            end = Offset(x, plotBottom),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = dash,
                        )
                        val r = pointRadius.toPx()
                        drawCircle(Color.White, radius = r + 2.dp.toPx(), center = Offset(x, y))
                        drawCircle(
                            color = color,
                            radius = r + 1.dp.toPx(),
                            center = Offset(x, y),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

/** 网格与轴标签之间的间距（web `grid.containLabel` 的等效留白）。 */
private val AxisGapDp = 4.dp

/** 系列数据。 */
data class SeriesData(
    val name: String,
    val data: List<Double?>,
)

/** 内边距值对象。 */
data class PaddingValues(
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
) {
    /**
     * 便捷构造：`PaddingValues(horizontal = 16.dp, vertical = 12.dp)`。
     *
     * ⚠ 本类是**项目自定义**的（与 `androidx.compose.foundation.layout.PaddingValues` 同名），
     *   主构造只有 `left/right/top/bottom`；调用处按官方写法传 `horizontal/vertical` 走这个次构造。
     */
    constructor(horizontal: Dp = 0.dp, vertical: Dp = 0.dp) : this(
        left = horizontal, right = horizontal, top = vertical, bottom = vertical,
    )

    fun calculateLeftDp() = left
    fun calculateRightDp() = right
    fun calculateTopDp() = top
    fun calculateBottomDp() = bottom
}
