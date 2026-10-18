package com.live.finance.ui.common.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 柱状图（支持多系列分组、圆角柱顶、数值标签）。
 *
 * 视觉基准：web `views/Finance/report/stats/MonthlyTrend.vue` 的 echarts option：
 *
 * ```
 * yAxis { splitLine:{ lineStyle:{ type:'dashed', color:'#f0f0f0' } }, axisLabel:{ color:'#969799', fontSize:10 } }
 * xAxis { axisLabel:{ color:'#969799', fontSize:10 } }
 * bar   { barWidth:'35%', itemStyle:{ borderRadius:[4,4,0,0] },
 *         label:{ position:'top', fontSize:10, color:'#323233', formatter:formatChartLabelAmount } }
 * ```
 *
 * - 轴标签 `#969799 / 10sp`、柱顶数值 `#323233 / 10sp`（web 硬编码）。
 * - X 轴标签用 [formatChartDate] 取完整日期（原来是 `takeLast(5).replace("-","/")` 的 09/20 缩略）。
 * - `containLabel:true`：Y 轴标签宽度 / X 轴标签高度由组件自行从网格里扣除。
 */
@Composable
fun BarChart(
    series: List<SeriesData>,
    labels: List<String>,
    colors: List<Color>,
    barWidth: Dp = 12.dp,
    barCornerRadius: Dp = 4.dp,
    showValue: Boolean = true,
    onBarClick: ((seriesIndex: Int, dataIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(left = 4.dp, right = 8.dp, top = 8.dp, bottom = 6.dp),
    gridLines: Int = 5,
    labelStyle: TextStyle? = null,
    valueStyle: TextStyle? = null,
) {
    if (series.isEmpty() || labels.isEmpty()) return

    val axis = rememberChartAxisColors()
    val effLabelStyle: TextStyle = labelStyle ?: chartAxisLabelStyle(10f, axis.axisLabel)
    val effValueStyle: TextStyle = valueStyle ?: chartAxisLabelStyle(10f, axis.valueLabel)
    val textMeasurer = rememberTextMeasurer()

    val allValues = series.flatMap { it.data.filterNotNull() }
    if (allValues.isEmpty()) return

    val dataMin = min(0.0, allValues.minOrNull() ?: 0.0)
    val dataMax = max(0.0, allValues.maxOrNull() ?: 1.0)
    val ticks = remember(dataMin, dataMax, gridLines) {
        niceAxisTicks(dataMin, dataMax, gridLines + 1)
    }

    // containLabel:true —— Y 轴标签宽度从左侧扣除
    val yLabelTexts = ticks.map { formatTickAmount(it) }
    val yLabelLayouts = remember(yLabelTexts, effLabelStyle) {
        yLabelTexts.map { textMeasurer.measure(it, effLabelStyle) }
    }
    val maxYLabelW = yLabelLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f

    val xLabelTexts = labels.map { formatChartDate(it) }
    val xLabelLayouts = remember(xLabelTexts, effLabelStyle) {
        xLabelTexts.map { textMeasurer.measure(it, effLabelStyle) }
    }
    val xLabelH = axisLabelsHeight(xLabelLayouts, 0f)

    // 数值标签预测量（避免在 DrawScope 内重复 measure）
    val valueLayouts = remember(series, showValue, effValueStyle) {
        if (!showValue) emptyMap() else series.flatMapIndexed { sIdx, s ->
            s.data.mapIndexedNotNull { i, v ->
                if (v == null || v <= 0.0) null else (sIdx to i) to textMeasurer.measure(formatTickAmount(v), effValueStyle)
            }
        }.toMap()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onBarClick != null) {
                    Modifier.pointerInput(series, labels) {
                        detectTapGestures { offset ->
                            val gap = 4.dp.toPx()
                            val padL = padding.calculateLeftDp().toPx() + maxYLabelW + gap
                            val padR = padding.calculateRightDp().toPx()
                            val padT = padding.calculateTopDp().toPx()
                            val padB = padding.calculateBottomDp().toPx()
                            val w = size.width - padL - padR
                            val plotTop = padT
                            val plotBottom = size.height - padB - xLabelH - gap
                            val plotH = plotBottom - plotTop

                            val groupWidth = w / labels.size
                            val barW = barWidth.toPx()
                            val seriesCount = series.size
                            val groupBarWidth = min(barW, groupWidth / (seriesCount + 1))
                            val yScale = if (ticks.size > 1) plotH / (ticks.last() - ticks.first()).toFloat() else 1f

                            labels.forEachIndexed { i, _ ->
                                val groupX = padL + i * groupWidth + groupWidth / 2
                                series.forEachIndexed { sIdx, s ->
                                    val barX = groupX - (seriesCount - 1) * groupBarWidth / 2 + sIdx * groupBarWidth
                                    val v = s.data.getOrNull(i) ?: return@forEachIndexed
                                    val barH = ((v - ticks.first()) * yScale).toFloat()
                                    val barY = plotBottom - barH
                                    if (offset.x in barX..barX + groupBarWidth && offset.y in barY..plotBottom) {
                                        onBarClick(sIdx, i)
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        val gap = 4.dp.toPx()
        val padL = padding.calculateLeftDp().toPx() + maxYLabelW + gap
        val padR = padding.calculateRightDp().toPx()
        val padT = padding.calculateTopDp().toPx()
        val padB = padding.calculateBottomDp().toPx()
        val left = padL
        val right = size.width - padR
        val plotTop = padT
        val plotBottom = size.height - padB - xLabelH - gap
        val plotH = plotBottom - plotTop

        // —— 网格线（主题 splitLine）——
        ticks.forEachIndexed { i, _ ->
            val y = plotBottom - plotH * i / (ticks.size - 1).coerceAtLeast(1)
            drawLine(
                color = axis.splitLine,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // —— Y 轴标签：右对齐到网格左边界（containLabel）——
        yLabelLayouts.forEachIndexed { i, layout ->
            val y = plotBottom - plotH * i / (ticks.size - 1).coerceAtLeast(1)
            drawText(
                layout,
                topLeft = Offset(left - gap - layout.size.width, y - layout.size.height / 2f),
            )
        }

        // —— 柱体（web borderRadius:[4,4,0,0] ⇒ 只圆顶部两角）——
        val groupWidth = (right - left) / labels.size
        val seriesCount = series.size
        val groupBarWidth = min(barWidth.toPx(), groupWidth / (seriesCount + 1))
        val yScale = if (ticks.size > 1) plotH / (ticks.last() - ticks.first()).toFloat() else 1f

        labels.forEachIndexed { i, _ ->
            val groupX = left + i * groupWidth + groupWidth / 2
            series.forEachIndexed { sIdx, s ->
                val v = s.data.getOrNull(i) ?: return@forEachIndexed
                val barH = ((v - ticks.first()) * yScale).toFloat()
                val barW = groupBarWidth
                val barX = groupX - (seriesCount - 1) * groupBarWidth / 2 + sIdx * groupBarWidth
                val barY = plotBottom - barH
                val color = colors.getOrElse(sIdx) { Color.Gray }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(barX, barY),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barCornerRadius.toPx(), barCornerRadius.toPx()),
                )

                // —— 柱顶数值（web label position:'top'）——
                valueLayouts[sIdx to i]?.let { layout ->
                    drawText(
                        layout,
                        topLeft = Offset(
                            barX + barW / 2 - layout.size.width / 2f,
                            barY - layout.size.height - 2.dp.toPx(),
                        ),
                    )
                }
            }
        }

        // —— X 轴轴线 + 标签（完整日期 + hideOverlap）——
        drawLine(
            color = axis.axisLine,
            start = Offset(left, plotBottom),
            end = Offset(right, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )
        drawAxisLabels(
            labels = xLabelLayouts.mapIndexed { i, layout ->
                AxisLabel(layout, left + i * groupWidth + groupWidth / 2)
            },
            leftBound = left,
            rightBound = right,
            baselineY = plotBottom + gap,
        )
    }
}
