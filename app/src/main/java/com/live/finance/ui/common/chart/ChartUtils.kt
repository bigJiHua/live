package com.live.finance.ui.common.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.VanGrayFixed
import com.live.finance.theme.scaledSp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.log10

/** 图表公共工具：刻度 nice 取整、降采样、路径构建。 */

// ================= 刻度 nice 取整 =================

/**
 * 对数据范围做 "nice" 取整，返回等距刻度数组。
 *
 * 规则（对齐 echarts 默认行为）：
 * 1. 若 max <= min，外扩 1 单位；
 * 2. 计算 "步长 step" = nice(min, max, desiredCount)，保证 step 是 1/2/5/10 的倍数；
 * 3. 从 floor(min/step)*step 开始，每次加 step，直到 >= ceil(max/step)*step。
 */
fun niceAxisTicks(
    dataMin: Double,
    dataMax: Double,
    desiredCount: Int = 6,
    minStep: Double = 1.0,
): List<Double> {
    if (dataMin.isNaN() || dataMax.isNaN()) return listOf(0.0, 1.0)
    var lo = min(dataMin, dataMax)
    var hi = max(dataMin, dataMax)
    if (lo == hi) { lo -= 1.0; hi += 1.0 }

    val range = hi - lo
    val roughStep = range / max(desiredCount - 1, 1)
    // ⚠ `kotlin.math.pow(base, exp)` 这个**顶层函数在本工程的 Kotlin 版本里已不存在**
    //   （报 Unresolved reference ⇒ 连带下游 10 处 "Overload resolution ambiguity"）。
    //   正确写法是 `Double.pow(exp)` 扩展（`import kotlin.math.pow`）。
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(roughStep)))
    val residual = roughStep / magnitude

    val step = when {
        residual <= 1.5 -> 1.0 * magnitude
        residual <= 3.0 -> 2.0 * magnitude
        residual <= 7.0 -> 5.0 * magnitude
        else -> 10.0 * magnitude
    }.coerceAtLeast(minStep)

    val start = kotlin.math.floor(lo / step) * step
    val end = kotlin.math.ceil(hi / step) * step

    val ticks = mutableListOf<Double>()
    var v = start
    while (v <= end + step * 0.001) {
        ticks.add(v)
        v += step
    }
    return ticks
}

/**
 * 格式化刻度值 —— 逐字对齐 web `assets/Trend.vue` `formatLabel` 与 `assets/List.vue` `formatLabelMini`：
 *
 * ```
 * if (abs >= 1e8) return (v / 1e8).toFixed(1) + '亿'
 * if (abs >= 1e4) return (v / 1e4).toFixed(1) + '万'
 * return String(Math.round(v))
 * ```
 *
 * ⚠ 之前写的是 `%.2f万` / `%.3g` 且对 `value <= 0` 提前返回 "0"：
 *   小数位与 web 不一致、且把**负值（负债/亏损）**全压成 "0"，轴标签直接失真。
 */
fun formatTickAmount(value: Double): String {
    val a = abs(value)
    return when {
        a >= 1e8 -> String.format(java.util.Locale.US, "%.1f亿", value / 1e8)
        a >= 1e4 -> String.format(java.util.Locale.US, "%.1f万", value / 1e4)
        else -> kotlin.math.round(value).toLong().toString()
    }
}

/**
 * X 轴日期标签 —— 对齐 web `assets/Trend.vue` `formatDate` / `assets/List.vue` `toDateStr`：
 * 纯数字按时间戳（秒/毫秒）解析成 `YYYY-MM-DD`，其余原样返回（截到 10 位）。
 *
 * ⚠ 之前原生写的是 `date.takeLast(5).replace("-", "/")` ⇒ 只显示「09/20」，
 *   web 显示完整「2026-09-20」，这是两端观感差异最大的地方之一。
 */
fun formatChartDate(raw: String): String {
    if (raw.isEmpty()) return "-"
    if (raw.all { it.isDigit() }) {
        var ts = raw.toLongOrNull() ?: return raw
        if (ts < 1_000_000_000_000L) ts *= 1000
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return f.format(java.util.Date(ts))
    }
    return if (raw.length >= 10) raw.substring(0, 10) else raw
}

// ================= 轴线 / 网格 / 标签主题 =================

/**
 * 图表轴线三件套颜色。web echarts option 里是**硬编码**的（不随主题切换）：
 *
 * ```
 * axisLabel: { color: '#969799', fontSize: 10 }
 * splitLine : { lineStyle: { type: 'solid', color: '#eceef1', width: 1 } }
 * axisLine  : { lineStyle: { color: '#dcdee0' } }   // yAxis axisLine:{show:false}
 * ```
 *
 * 浅色逐字照搬；深色下 #eceef1 网格线会亮得刺眼、#969799 文字对比不足，
 * 故用主题 token 兜底（可读性优先，web 该处未适配深色）。
 */
data class ChartAxisColors(
    val axisLabel: Color,
    val splitLine: Color,
    val axisLine: Color,
    val valueLabel: Color,
)

@Composable
fun rememberChartAxisColors(): ChartAxisColors {
    val colors = LocalAppColors.current
    // ⚠ LocalAppColors 没有 isDark（它在 AppTokens 上）
    val isDark = LocalAppTokens.current.isDark
    return if (isDark) {
        ChartAxisColors(
            axisLabel = colors.textTertiary,
            splitLine = Color(0x1FFFFFF),
            axisLine = Color(0x33FFFFFF),
            valueLabel = colors.textPrimary,
        )
    } else {
        ChartAxisColors(
            axisLabel = VanGrayFixed,
            splitLine = Color(0xFFECEEF1),
            axisLine = Color(0xFFDCDEE0),
            valueLabel = Color(0xFF323233),
        )
    }
}

/** 轴标签文字 —— web `axisLabel.fontSize` 主图 10、迷你图 9。 */
@Composable
fun chartAxisLabelStyle(fontSize: Float, color: Color): TextStyle =
    TextStyle(color = color, fontSize = scaledSp(fontSize))

/** 一条已测量的轴标签：文字布局 + 锚点 x（网格坐标系）。 */
data class AxisLabel(val layout: TextLayoutResult, val anchorX: Float)

/** 旋转后轴标签的**垂直**占位高度（px），供 `containLabel:true` 从底部扣除。 */
fun axisLabelsHeight(layouts: List<TextLayoutResult>, rotationDeg: Float): Float {
    if (layouts.isEmpty()) return 0f
    if (rotationDeg == 0f) return layouts.maxOf { it.size.height }.toFloat()
    val rad = abs(rotationDeg * 0.017453292f)
    val s = sin(rad)
    val c = cos(rad)
    var m = 0f
    layouts.forEach { m = max(m, it.size.width * s + it.size.height * c) }
    return m
}

/**
 * 绘制 echarts `axisLabel`，含两项关键行为：
 *
 * 1. `hideOverlap: true` —— 从左到右依次绘制，与上一条已绘制标签重叠即**跳过**
 *    （web 靠这个在 6 期窗口里自动隐藏被挤掉的日期，而不是粗放地只画 6 等分点）。
 * 2. `containLabel: true` —— 标签整体不越出网格左右边界，越界则夹回边界内。
 *
 * @param rotationDeg 标签旋转角度（web `rotate: 30 / 45`）；非 0 时绕标签盒**中心**旋转，
 *                    且标签盒顶边落在 [baselineY] 上（占位高度用 [axisLabelsHeight] 预估）。
 */
fun DrawScope.drawAxisLabels(
    labels: List<AxisLabel>,
    leftBound: Float,
    rightBound: Float,
    baselineY: Float,
    rotationDeg: Float = 0f,
    overlapGap: Float = 4f,
) {
    if (labels.isEmpty()) return
    val rad = rotationDeg * 0.017453292f
    val cos = abs(cos(rad))
    val sin = abs(sin(rad))
    var lastRight = Float.NEGATIVE_INFINITY
    labels.forEach { label ->
        val layout = label.layout
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val extent = if (rotationDeg == 0f) w else w * cos + h * sin
        val left = label.anchorX - extent / 2f
        if (left < lastRight + overlapGap) return@forEach
        val clampedLeft = left.coerceIn(leftBound, max(leftBound, rightBound - extent))
        if (rotationDeg == 0f) {
            drawText(layout, topLeft = Offset(clampedLeft, baselineY))
        } else {
            val boxH = w * sin + h * cos
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.translate(label.anchorX, baselineY + boxH / 2f)
            nc.rotate(rotationDeg)
            drawText(layout, topLeft = Offset(-w / 2f, -h / 2f))
            nc.restore()
        }
        lastRight = clampedLeft + extent
    }
}

// ================= 降采样 =================

/**
 * 简单降采样：保留首尾 + 均匀间隔取点。
 * 适合图表渲染，保持整体趋势，点数 <= maxPoints。
 */
fun downsampleSimple(points: List<Double>, maxPoints: Int = 200): List<Double> {
    if (points.size <= maxPoints) return points
    val step = (points.size - 1).toDouble() / (maxPoints - 1)
    val result = mutableListOf<Double>()
    for (i in 0 until maxPoints) {
        val idx = (i * step).roundToInt().coerceIn(0, points.size - 1)
        result.add(points[idx])
    }
    return result
}

/**
 * LTTB (Largest-Triangle-Three-Buckets) 降采样：保留视觉特征更好，适合曲线图。
 */
fun downsampleLTTB(data: List<Double>, maxPoints: Int = 200): List<Double> {
    if (data.size <= maxPoints) return data
    val result = mutableListOf<Double>()
    val bucketSize = (data.size - 2).toDouble() / (maxPoints - 2)

    result.add(data.first())

    var a = 0
    for (i in 1 until maxPoints - 1) {
        val avgRangeStart = ((i) * bucketSize).toInt() + 1
        val avgRangeEnd = min(((i + 1) * bucketSize).toInt() + 1, data.size)
        val avgX = (avgRangeStart + avgRangeEnd) / 2.0

        var maxArea = -1.0
        var maxIdx = avgRangeStart
        val rangeOff = avgRangeEnd - avgRangeStart

        val pointAX = a.toDouble()
        val pointAY = data[a].toDouble()

        for (j in avgRangeStart until avgRangeEnd) {
            val area = abs(
                (pointAX - avgX) * (data[j] - pointAY) -
                (pointAX - j.toDouble()) * (data[avgRangeStart] - pointAY)
            ) * 0.5
            if (area > maxArea) {
                maxArea = area
                maxIdx = j
            }
        }
        result.add(data[maxIdx])
        a = maxIdx
    }

    result.add(data.last())
    return result
}

// ================= 路径构建 =================

/**
 * 构建平滑折线路径（二次贝塞尔近似 echarts smooth）。
 */
fun buildSmoothPath(
    points: List<Offset>,
    closed: Boolean = false,
    closeY: Float = 0f,
): Path {
    val path = Path()
    if (points.size == 1) {
        path.moveTo(points[0].x, points[0].y)
        if (closed) {
            path.lineTo(points[0].x, closeY)
            path.close()
        }
        return path
    }
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) / 2f
        path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
    }
    if (closed) {
        val last = points.last()
        path.lineTo(last.x, closeY)
        path.close()
    }
    return path
}

/** 构建面积路径（折线 + 底部闭合）。 */
fun buildAreaPath(
    points: List<Offset>,
    bottomY: Float,
): Path {
    val path = buildSmoothPath(points, closed = false)
    val last = points.last()
    val first = points.first()
    path.lineTo(last.x, bottomY)
    path.lineTo(first.x, bottomY)
    path.close()
    return path
}

// ================= 数值辅助 =================

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

/** 固定 2 位小数（web `toFixed(2)`）。原重构在 FundTrendScreen 里调用了 `toFixed2()` 但**根本没定义**。 */
fun Double.toFixed2(): String = String.format(java.util.Locale.US, "%.2f", this)
