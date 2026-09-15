package com.live.vant

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 用 SVG path-data 字符串构造 Compose [Path]。
 *
 * 为什么自己写：Android 版 androidx.compose.ui.graphics.Path 无 `Path(String)` 重载，
 * 且 SDK34 的 android.graphics.Path 也不含 parsePathData/setPathFromPathData ——
 * 桌面版才有的能力在真机不可用，故空状态图/键盘删除图标需要自带解析。
 *
 * 覆盖命令 M/L/H/V/C/S/Q/T/A/Z（含小写相对），支持隐式命令重复、负号/'.'连写、
 * 弧标志位单字符读取；椭圆弧按 W3C SVG 2 附录 B.6 做「端点→中心」转换后走 Compose arcTo
 * （本项目所有弧的 x-轴旋转均为 0，故用未旋转包围盒近似精确）。
 */
fun svgPath(pathData: String): Path = SvgPathParser(pathData).parse()

private class SvgPathParser(private val src: String) {
    private val path = Path()
    private var i = 0
    private var cmd = ' '
    private var curX = 0f
    private var curY = 0f
    private var startX = 0f
    private var startY = 0f
    private var lastCtrlX = 0f
    private var lastCtrlY = 0f
    private var prevCurve = false   // 上一步是 C/S → 供 S/T 反射；true 时也覆盖 Q/T 语义

    fun parse(): Path {
        while (i < src.length) {
            skipSep()
            if (i >= src.length) break
            val ch = src[i]
            if (isCmd(ch)) { cmd = ch; i++ }
            when (cmd) {
                'M' -> { moveAbs(num(), num()); cmd = 'L' }
                'm' -> { moveRel(num(), num()); cmd = 'l' }
                'L' -> lineAbs(num(), num())
                'l' -> lineRel(num(), num())
                'H' -> lineAbs(num(), curY)
                'h' -> lineRel(num(), 0f)
                'V' -> lineAbs(curX, num())
                'v' -> lineRel(0f, num())
                'C' -> cubicAbs(num(), num(), num(), num(), num(), num())
                'c' -> cubicRel(num(), num(), num(), num(), num(), num())
                'S' -> { val r = reflect(); cubicAbs(r.first, r.second, num(), num(), num(), num()) }
                's' -> { val r = reflect(); cubicRel(r.first - curX, r.second - curY, num(), num(), num(), num()) }
                'Q' -> quadAbs(num(), num(), num(), num())
                'q' -> quadRel(num(), num(), num(), num())
                'T' -> { val r = reflect(); quadAbs(r.first, r.second, num(), num()) }
                't' -> { val r = reflect(); quadRel(r.first - curX, r.second - curY, num(), num()) }
                'A', 'a' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val la = flag(); val sw = flag()
                    val ex = num(); val ey = num()
                    arc(rx, ry, rot, la, sw, ex, ey, cmd == 'a')
                }
                'Z', 'z' -> { path.close(); curX = startX; curY = startY; prevCurve = false }
                else -> { i++ }
            }
        }
        return path
    }

    private fun reflect(): Pair<Float, Float> =
        if (prevCurve) Pair(2 * curX - lastCtrlX, 2 * curY - lastCtrlY) else Pair(curX, curY)

    private fun moveAbs(x: Float, y: Float) { path.moveTo(x, y); curX = x; curY = y; startX = x; startY = y; prevCurve = false }
    private fun moveRel(x: Float, y: Float) = moveAbs(curX + x, curY + y)
    private fun lineAbs(x: Float, y: Float) { path.lineTo(x, y); curX = x; curY = y; prevCurve = false }
    private fun lineRel(x: Float, y: Float) = lineAbs(curX + x, curY + y)

    private fun cubicAbs(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        path.cubicTo(c1x, c1y, c2x, c2y, x, y)
        lastCtrlX = c2x; lastCtrlY = c2y; curX = x; curY = y; prevCurve = true
    }
    private fun cubicRel(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        cubicAbs(curX + c1x, curY + c1y, curX + c2x, curY + c2y, curX + x, curY + y)

    private fun quadAbs(cx: Float, cy: Float, x: Float, y: Float) {
        path.quadraticBezierTo(cx, cy, x, y)
        lastCtrlX = cx; lastCtrlY = cy; curX = x; curY = y; prevCurve = true
    }
    private fun quadRel(cx: Float, cy: Float, x: Float, y: Float) =
        quadAbs(curX + cx, curY + cy, curX + x, curY + y)

    private fun arc(rxIn: Float, ryIn: Float, phiDeg: Float, largeArc: Boolean, sweep: Boolean, exIn: Float, eyIn: Float, relative: Boolean) {
        val x1 = curX; val y1 = curY
        val x2 = if (relative) curX + exIn else exIn
        val y2 = if (relative) curY + eyIn else eyIn
        if (x1 == x2 && y1 == y2) return
        var rx = abs(rxIn); var ry = abs(ryIn)
        if (rx == 0f || ry == 0f) { lineAbs(x2, y2); return }
        val phi = Math.toRadians(phiDeg.toDouble())
        val cosPhi = cos(phi); val sinPhi = sin(phi)
        val dx = (x1 - x2) / 2; val dy = (y1 - y2) / 2
        val x1p = (cosPhi * dx + sinPhi * dy).toFloat()
        val y1p = (-sinPhi * dx + cosPhi * dy).toFloat()
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1f) { val s = sqrt(lambda); rx *= s; ry *= s }
        val sgn = if (largeArc == sweep) -1.0 else 1.0
        val num2 = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den2 = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val co = if (den2 == 0f) 0.0 else sgn * sqrt(max(0.0, (num2 / den2).toDouble()))
        val cxp = (co * rx * y1p / ry).toFloat()
        val cyp = (-co * ry * x1p / rx).toFloat()
        val cx = (cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2).toFloat()
        val cy = (sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2).toFloat()
        val ux = ((x1p - cxp) / rx); val uy = ((y1p - cyp) / ry)
        val vx = ((-x1p - cxp) / rx); val vy = ((-y1p - cyp) / ry)
        val theta1 = angle(1f, 0f, ux, uy)
        var dtheta = angle(ux, uy, vx, vy)
        if (!sweep && dtheta > 0) dtheta -= 2 * Math.PI
        else if (sweep && dtheta < 0) dtheta += 2 * Math.PI
        val rect = Rect(Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
        path.arcTo(rect, Math.toDegrees(theta1).toFloat(), Math.toDegrees(dtheta).toFloat(), forceMoveTo = false)
        curX = x2; curY = y2; prevCurve = false
    }

    private fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
        if (len == 0f) return 0.0
        var a = acos((dot / len).coerceIn(-1f, 1f)).toDouble()
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }

    private fun isCmd(c: Char) = c in "MmLlHhVvCcSsQqTtAaZz"
    private fun skipSep() { while (i < src.length && (src[i] == ' ' || src[i] == ',' || src[i] == '\n' || src[i] == '\r' || src[i] == '\t')) i++ }
    private fun flag(): Boolean { skipSep(); val c = if (i < src.length) src[i] else '0'; i++; return c == '1' }

    private fun num(): Float {
        skipSep()
        val start = i
        if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
        var hasDot = false
        var seenDigit = false
        var exp = false
        while (i < src.length) {
            val c = src[i]
            when {
                c in '0'..'9' -> { i++; seenDigit = true }
                c == '.' && !hasDot && !exp -> { hasDot = true; i++ }
                (c == 'e' || c == 'E') && seenDigit && !exp -> {
                    exp = true; i++
                    if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                }
                else -> return parseNum(src.substring(start, i))
            }
        }
        return parseNum(src.substring(start, i))
    }

    private fun parseNum(s: String) = s.toFloatOrNull() ?: 0f
}
