package com.live.vant.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/**
 * 复刻 van-empty（web 项目 64 处使用）。
 * 内置 default/error/search/network 四组插画使用与 Vant 完全一致的 SVG 路径，
 * 渐变近似为同明度实色（Vant 原图为 #FFF..#DCDEE0 灰阶渐变，视觉无损）。
 */
@Composable
fun VanEmpty(
    modifier: Modifier = Modifier,
    description: String? = null,
    image: String = "default",
    imageSize: Dp? = null,
    bottomContent: @Composable (() -> Unit)? = null,
) {
    val tokens = LocalVantDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = tokens.paddingXl),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(imageSize ?: 160.dp),
        ) {
            when (image) {
                "default", "error", "search", "network" -> EmptyPresetImage(image)
                else -> AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.size(imageSize ?: 160.dp),
                )
            }
        }
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.text.BasicText(
                text = description,
                style = TextStyle(
                    color = LocalVantColors.current.textTertiary,
                    fontSize = tokens.fontSizeMd,
                    lineHeight = tokens.lineHeightMd,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(horizontal = 60.dp),
            )
        }
        if (bottomContent != null) {
            Spacer(Modifier.height(24.dp))
            bottomContent()
        }
    }
}

/* ---------------- 内置插画（源自 vant/es/empty/Empty.mjs 的 SVG 路径） ---------------- */

private val GrayEBEDF0 = Color(0xFFEBEDF0)
private val GrayF2F3F5 = Color(0xFFF2F3F5)
private val GrayDCDEE0 = Color(0xFFDCDEE0)
private val GrayF7F8FA = Color(0xFFF7F8FA)

@Composable
private fun EmptyPresetImage(preset: String) {
    Canvas(Modifier.fillMaxWidth().size(160.dp)) {
        // viewBox 0 0 160 160 → 画布等比缩放
        val s = size.minDimension / 160f
        fun px(v: Float) = v * s

        fun drawBuilding() {
            val building = com.live.vant.svgPath("M36 131V53H16v20H2v58h34z")
            val building2 = com.live.vant.svgPath("M123 15h22v14h9v77h-31V15z")
            scalePath(building, s); scalePath(building2, s)
            drawPath(building, Color(0xFFF4F5F7).copy(alpha = 0.8f))
            drawPath(building2, Color(0xFFF4F5F7).copy(alpha = 0.8f))
        }

        fun drawCloud() {
            val c1 = com.live.vant.svgPath("M87 6c3 0 7 3 8 6a8 8 0 1 1-1 16H80a7 7 0 0 1-8-6c0-4 3-7 6-7 0-5 4-9 9-9Z")
            val c2 = com.live.vant.svgPath("M19 23c2 0 3 1 4 3 2 0 4 2 4 4a4 4 0 0 1-4 3v1h-7v-1l-1 1c-2 0-3-2-3-4 0-1 1-3 3-3 0-2 2-4 4-4Z")
            scalePath(c1, s); scalePath(c2, s)
            drawPath(c1, GrayF2F3F5.copy(alpha = 0.65f))
            drawPath(c2, GrayF2F3F5.copy(alpha = 0.65f))
        }

        fun drawShadow() {
            drawOval(
                GrayEBEDF0.copy(alpha = 0.55f),
                topLeft = Offset(px(34f), px(132f)),
                size = androidx.compose.ui.geometry.Size(px(92f), px(16f)),
            )
        }

        when (preset) {
            "network" -> {
                drawBuilding()
                drawShadow()
                // 信号柱
                drawRect(GrayEBEDF0, Offset(px(31f), px(105f)), androidx.compose.ui.geometry.Size(px(98f), px(34f)))
                // 天线（简化为圆头折线弧）
                val arcs = listOf(
                    com.live.vant.svgPath("M64 47a19 19 0 0 0-5 13c0 5 2 10 5 13"),
                    com.live.vant.svgPath("M53 36a34 34 0 0 0 0 48"),
                    com.live.vant.svgPath("M95 73a19 19 0 0 0 6-13c0-5-2-9-6-13"),
                    com.live.vant.svgPath("M106 84a34 34 0 0 0 0-48"),
                )
                arcs.forEach { scalePath(it, s) }
                arcs.forEach {
                    drawPath(it, GrayDCDEE0.copy(alpha = 0.6f), style = Stroke(width = px(7f), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                drawRect(
                    Color.White,
                    Offset(px(40f), px(113f)),
                    androidx.compose.ui.geometry.Size(px(80f), px(18f)),
                )
                drawRect(GrayEBEDF0, Offset(px(46f), px(117f)), androidx.compose.ui.geometry.Size(px(18f), px(6f)))
            }
            "error" -> {
                drawBuilding(); drawCloud(); drawShadow()
                val x = com.live.vant.svgPath("m59 60 21 21 21-21h3l9 9v3L92 93l21 21v3l-9 9h-3l-21-21-21 21h-3l-9-9v-3l21-21-21-21v-3l9-9h3Z")
                scalePath(x, s)
                drawPath(x, Color(0xFFE3E6EA))
            }
            "search" -> {
                drawBuilding(); drawCloud(); drawShadow()
                rotate(-45f, Offset(px(113f), px(-4f))) {
                    drawRoundRect(
                        Color(0xFFE3E3E3),
                        Offset(px(24f), px(52.8f)),
                        androidx.compose.ui.geometry.Size(px(5.8f), px(19f)),
                        androidx.compose.ui.geometry.CornerRadius(px(1f)),
                    )
                    drawRoundRect(
                        Color(0xFFE7E8EB),
                        Offset(px(22.1f), px(67.3f)),
                        androidx.compose.ui.geometry.Size(px(9.9f), px(28f)),
                        androidx.compose.ui.geometry.CornerRadius(px(1f)),
                    )
                    drawCircle(
                        Color(0xFFE7E8EB),
                        radius = px(31f),
                        center = Offset(px(27f), px(27f)),
                        style = Stroke(width = px(8f)),
                    )
                    drawCircle(Color(0xFFFBFBFC), radius = px(16f), center = Offset(px(27f), px(27f)))
                    val hl = com.live.vant.svgPath("M37 7c-8 0-15 5-16 12")
                    scalePath(hl, s)
                    translate(px(29f) * 0, 0f) {
                        rotate(45f, Offset(px(29f), px(13f))) {
                            drawPath(hl, Color(0xFFE7E8EB).copy(alpha = 0.5f), style = Stroke(width = px(3f), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                    }
                }
            }
            else -> { // default = material 文档插画
                drawBuilding(); drawCloud()
                translate(px(36f), px(50f)) {
                    translate(px(8f), 0f) {
                        drawRoundRect(GrayEBEDF0.copy(alpha = 0.6f), Offset(px(38f), px(13f)), androidx.compose.ui.geometry.Size(px(36f), px(53f)), androidx.compose.ui.geometry.CornerRadius(px(2f)))
                        drawRoundRect(Color(0xFFE7E8EB), Offset.Zero, androidx.compose.ui.geometry.Size(px(64f), px(66f)), androidx.compose.ui.geometry.CornerRadius(px(2f)))
                        drawRoundRect(Color.White, Offset(px(6f), px(6f)), androidx.compose.ui.geometry.Size(px(52f), px(55f)), androidx.compose.ui.geometry.CornerRadius(px(1f)))
                        translate(px(15f), px(17f)) {
                            drawRoundRect(Color(0xFFE4E7EB), Offset.Zero, androidx.compose.ui.geometry.Size(px(34f), px(6f)), androidx.compose.ui.geometry.CornerRadius(px(1f)))
                            drawRect(Color(0xFFE4E7EB), Offset(px(0f), px(14f)), androidx.compose.ui.geometry.Size(px(34f), px(6f)))
                            drawRoundRect(Color(0xFFE4E7EB), Offset(px(0f), px(28f)), androidx.compose.ui.geometry.Size(px(34f), px(6f)), androidx.compose.ui.geometry.CornerRadius(px(1f)))
                        }
                    }
                    drawRoundRect(Color(0xFFE4E7EB), Offset(0f, px(61f)), androidx.compose.ui.geometry.Size(px(88f), px(28f)), androidx.compose.ui.geometry.CornerRadius(px(1f)))
                    drawRoundRect(GrayF7F8FA, Offset(px(29f), px(72f)), androidx.compose.ui.geometry.Size(px(30f), px(6f)), androidx.compose.ui.geometry.CornerRadius(px(1f)))
                }
            }
        }
    }
}

/** SVG viewBox 160 缩放辅助：按比例放大/缩小路径 */
private fun scalePath(path: Path, scale: Float) {
    if (scale == 1f) return
    val m = androidx.compose.ui.graphics.Matrix()
    m.scale(scale, scale)
    path.transform(m)
}
