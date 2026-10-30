package com.live.finance.ui.main

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Picture as AndroidPicture
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.core.glass.NewBarSpec
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import com.live.vant.theme.MotionTokens
import kotlin.math.abs
import kotlin.math.max

/**
 * 全新独立底部 Tab 组件 —— Apple Liquid Glass 风格（2026-10-23）。
 *
 * ## 与经典悬浮岛的边界（负责人明确要求「独立组件、不能通用」）
 * - **本文件不与 `MainScreen.renderLiquidGlass` / `drawSelectionCapsule` / `FloatingIslandBar`
 *   共享任何绘制代码**；采样用的 `AndroidPicture` 只是由调用方提供的背景来源。
 * - 视觉差异：64dp 高 / 32dp 圆角 / 20px 真高斯模糊 / 暗色用近黑 tint（经典版是白 tint）、
 *   顶部弧线高光 + 整块轻微明暗渐变 + 内部低对比选中胶囊（260ms 标准缓动，无弹跳）。
 *
 * ## 关键架构：材质与胶囊分离（2026-10-23 修复「背景不跟随滚动」）
 * - 玻璃材质（backdrop 模糊 + tint + 明暗/高光）在 [renderNewGlassBackdrop] 里，
 *   由**内容区的绘制作用域**在每帧内容录制后调用 ⇒ 页面滚动/内容变化时材质必然重画。
 * - 选中胶囊 / 边框 / 图标文字在 [NewLiquidGlassTabBar] 自己的绘制里（底栏是内容的兄弟节点，
 *   滚动时它不重绘也无需重绘——胶囊只跟选中项有关）。
 * - 若把材质画在底栏自己的 `drawBehind`，页面滚动时底栏不重绘 ⇒ RenderNode 里永远是旧帧，
 *   表现为「玻璃背景固定不动」（本轮负责人报的致命问题）。
 *
 * ## 材质分层（按提示词）
 * ① 真实背景模糊：RenderNode + RenderEffect(20px, CLAMP)，API31+；低版本降级 tint+高光。
 * ② 半透明染色层：亮色白 22% / 暗色近黑 40%。
 * ③ 边缘描边：亮色白 35% / 暗色白 15%，1dp。
 * ④ 顶部高光：亮色白 45% / 暗色白 18%，沿圆角上缘 1.5dp 渐隐。
 * ⑤ 内部明暗：顶部白 6% → 中段透明 → 底部黑 5% 的整块垂直渐变。
 *
 * ## 性能约束
 * - 背景只在调用方逐帧录一次 Picture，本组件只读不复制；不做每帧 Bitmap、不在主线程高斯模糊。
 */
private const val NEW_BAR_HEIGHT = 64f
private const val NEW_BAR_RADIUS = 32f
private const val NEW_BAR_BLUR_PX = 20f
private const val NEW_TAB_COUNT = 4
private const val NEW_BAR_SHADOW_ALPHA = 40f

/**
 * AGSL 折射 shader（API33+）：圆角矩形 SDF → 沿法线折射采样背景 → 色散 → Fresnel 边缘。
 * 参考 apkdv《在 Android 中实现 iOS 液态玻璃效果》系列的数学模型：
 * refractionDistance = C · (1 - sdf/edge)² · amount；中轴淡出，仅边缘折射。
 */
internal const val NEW_GLASS_AGSL = """
    uniform shader uBackdrop;
    uniform float4 uRect;
    uniform float uRadius;
    uniform float uRefraction;
    uniform float uDispersion;
    uniform float uFresnel;
    uniform float uEdge;
    uniform float2 uContent;

    half4 main(float2 fragCoord) {
        float2 halfSize = uRect.zw * 0.5;
        float2 center = uRect.xy + halfSize;
        float2 p = fragCoord - center;
        float2 q = abs(p) - (halfSize - uRadius);
        float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
        float t = clamp(1.0 + sd / uEdge, 0.0, 1.0);
        float fall = 1.0 - t;
        float amt = uRefraction * fall * fall;
        float2 n = normalize(max(q, float2(0.0)) + float2(1e-6));
        float2 uv = clamp(fragCoord + n * amt, float2(0.0), uContent - float2(1.0));
        half4 c = uBackdrop.eval(uv);
        if (uDispersion > 0.0 && amt > 0.001) {
            half4 cr = uBackdrop.eval(clamp(uv + n * amt * uDispersion, float2(0.0), uContent - float2(1.0)));
            half4 cb = uBackdrop.eval(clamp(uv - n * amt * uDispersion, float2(0.0), uContent - float2(1.0)));
            c = half4(cr.r, c.g, cb.b, c.a);
        }
        float fres = fall * fall;
        c.rgb = mix(c.rgb, half3(1.0), half(fres * uFresnel));
        return c;
    }
"""

/**
 * AGSL 平滑透镜 shader（API33+）：对图标行做**连续变倍**采样——胶囊中心 1.3×、
 * 左右边缘 1.7×，**仅横向**在过渡带内 smoothstep 连续过渡。没有分层就没有接缝。
 * ⚠ 过渡带必须只看横向（hx = |x−center| 距左右端的距离），不能用圆角矩形 SDF：
 *   SDF 量「到最近边的距离」，扁胶囊（高 50dp）里文字/icon 上下天然贴边
 *   （SDF 距离仅 10~20px），会被推进边缘 1.7× 区 ⇒ 文字被吹大 + 上下裁切
 *   （2026-10-27 模拟器实锤：SDF 版文字列实测被放大 ~1.47×，中心倍率怎么调都裁）。
 * ⚠ shader 字符串内不写任何注释（RuntimeShader 由 SkSL 运行时解析，非 ASCII 注释
 *   在部分机型有解析失败风险，失败即静默降级纯 1.3×）；说明全放本 KDoc。
 * uCapsule = 胶囊中心 xy + 半宽 z（bar 本地 px；w 分量不再使用）；
 * uMag = 中心倍率 / 边缘倍率 / 边缘过渡带宽 px；uContent = 源尺寸。
 */
internal const val NEW_LENS_AGSL = """
    uniform shader uBackdrop;
    uniform float4 uCapsule;
    uniform float4 uMag;
    uniform float2 uContent;

    half4 main(float2 fragCoord) {
        float2 center = uCapsule.xy;
        float hx = uCapsule.z - abs(fragCoord.x - center.x);
        float t = smoothstep(0.0, 1.0, clamp(hx / uMag.z, 0.0, 1.0));
        float m = mix(uMag.y, uMag.x, t);
        float2 src = clamp(center + (fragCoord - center) / m, float2(0.0), uContent - float2(1.0));
        return uBackdrop.eval(src);
    }
"""

private val NEW_BAR_SHAPE
    get() = RoundedCornerShape(NEW_BAR_RADIUS.dp)

private val NEW_BAR_TABS = listOf(
    "wap-home-o" to "首页",
    "bill-o" to "账本",
    "notes-o" to "动态",
    "user-o" to "我的",
)

/**
 * 底部 Tab 本体：胶囊选中态 + 图标/文字 + 边框 + 放大镜 + 长按拖拽切页。
 *
 * ⚠ 玻璃材质**不在这里画**——材质由调用方（内容区绘制）在每帧录制背景后调用
 * [renderNewGlassBackdrop] 垫在底下；这样页面滚动时材质必然跟随内容更新。
 *
 * ## 选中胶囊与拖拽（2026-10-26）
 * - 胶囊位置是 bar 本地**中心点 px**（[Animatable]），点击 260ms 标准缓动滑过去；
 *   **普通横向滑动即可拖拽**（`detectHorizontalDragGestures`，越过触摸斜率即起拖、
 *   竖直滑动不拦截），逐帧 `snapTo` 绝对跟手，胶囊中心越过格中线的瞬间回调 `onSelect` 切页。
 * - 拖拽/动画期间胶囊位置只在**绘制期**被读取（drawBehind + 各图层的 graphicsLayer），
 *   没有任何 per-frame 重组 ⇒ 丝滑无卡顿。
 *
 * ## 放大镜（2026-10-26 三改：AGSL 平滑透镜，消灭「中央矩形」）
 * - 第一条 Row 是**隐形录制行**：`drawWithContent` 把整条图标文字录进 [AndroidPicture]
 *   而不画到屏幕（内容永远全亮、全尺寸）。
 * - API33+：图标行录进 [RenderNode]，经 **AGSL 透镜 shader**（[NEW_LENS_AGSL]）以
 *   **仅横向**过渡带做连续变倍（中心 1.3× → 左右边缘 1.7×，16dp 横向过渡 smoothstep；
 *   上下边不参与变倍，文字/icon 永远走基础倍率不裁切）——
 *   没有分层就没有接缝，肉眼分不出「中央区/边带区」。裁进胶囊后叠加，边缘再压一圈
 *   **specular 高光**（上亮下暗）承担空档时的「border 感」。
 * - API<33 降级：只画基础 1.3×（无边带）+ 高光环。
 * - ⚠ 放大镜**不采样页面背景**（追加137/138 两版边带采样背景均翻车：暗色空页出
 *   黑框/接缝）——页面内容一律只透过玻璃材质显示（模糊+染色），胶囊内不再出现
 *   原生背景补丁。胶囊盖住的项在可见行里按距离渐隐（由透镜接管显示）。
 * - 点击无水波纹（indication = null）：胶囊本身就是选中反馈。
 *
 * @param visible 滚动收起/弹出状态（组件内部自己做 offset/alpha 动画）。
 */
@Composable
fun NewLiquidGlassTabBar(
    active: Int,
    onSelect: (Int) -> Unit,
    visible: Boolean,
    /** 系统「减弱动效/移除动画」开启时走实底（无透明/模糊），保证可读性。 */
    solidBackground: Boolean = false,
    /** 材质/透镜参数（实验室「底部 Tab 组件」微调；默认=出厂值）。 */
    spec: NewBarSpec = NewBarSpec(),
    onPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val dark = tokens.isDark
    val density = LocalDensity.current

    // —— 出现/收起：自己的动画，不与旧组件共用状态 ——
    val offsetY by animateDpAsState(if (visible) 0.dp else 120.dp, tween(300), label = "newBarOffsetY")
    val barAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "newBarAlpha")

    // —— 几何（bar 本地 px）——
    var barW by remember { mutableStateOf(0f) }
    // 四边等距（负责人 2026-10-27：胶囊与 bar 的左右间隙=上下间隙=4dp）
    val padPx = with(density) { 6.dp.toPx() }
    val insetXpx = 0f               // 胶囊占满整格宽（= 底栏 1/4，负责人 2026-10-27）
    val insetYpx = with(density) { 6.dp.toPx() }   // 四边间隙 6dp（负责人 2026-10-30）
    val slotW = if (barW > 0f) ((barW - padPx * 2f) / NEW_TAB_COUNT).coerceAtLeast(1f) else 0f
    val cw = (slotW - insetXpx * 2f).coerceAtLeast(1f)

    // —— 胶囊中心 x（Animatable，px）——
    val capsuleX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    // 拖拽目标（bar 本地 px，绝对跟手：起拖锚到手指，之后累加横向位移）
    var dragX by remember { mutableStateOf(0f) }
    val currentActive = rememberUpdatedState(active)
    val currentOnSelect = rememberUpdatedState(onSelect)

    fun centerOf(i: Int) = padPx + i * slotW + slotW / 2f

    // 点击/程序切换：胶囊 260ms 标准缓动滑到目标格中心（拖拽中不打断）
    LaunchedEffect(active, barW) {
        if (barW > 0f && !dragging) {
            val target = centerOf(active)
            if (capsuleX.value == 0f) capsuleX.snapTo(target) // 首帧不滑入
            else capsuleX.animateTo(
                target,
                // 灵动微弹（负责人 2026-10-27「弹弹的」）：dampingRatio<1 有一次轻微过冲回弹；
                // 过冲幅度≈格宽 8%，不会越出底栏
                spring(dampingRatio = 0.6f, stiffness = 400f),
            )
        }
    }
    // 拖拽结束：吸附回当前格中心
    LaunchedEffect(dragging, barW) {
        if (!dragging && barW > 0f) {
            val target = centerOf(currentActive.value)
            if (capsuleX.value == 0f) capsuleX.snapTo(target)
            else capsuleX.animateTo(
                target,
                spring(dampingRatio = 0.65f, stiffness = 450f),
            )
        }
    }
    // 拖拽中：胶囊逐帧跟手（绝对位置，钳制在首末格中心之间），越过格中线即切页
    LaunchedEffect(dragging, dragX) {
        if (dragging && barW > 0f) {
            val slot = ((barW - padPx * 2f) / NEW_TAB_COUNT).coerceAtLeast(1f)
            val minC = padPx + slot / 2f
            val maxC = barW - padPx - slot / 2f
            val next = dragX.coerceIn(minC, maxC)
            capsuleX.snapTo(next)
            val idx = (((next - padPx) / slot).toInt()).coerceIn(0, NEW_TAB_COUNT - 1)
            if (idx != currentActive.value) currentOnSelect.value(idx)
        }
    }
    // 放大镜素材：隐形录制行的整条 Picture（bar 本地全尺寸）
    val iconPicture = remember { AndroidPicture() }
    var lensReady by remember { mutableStateOf(false) }
    // 🔍 AGSL 平滑透镜（API33+）：图标行 RenderNode + 连续变倍 shader；<33 回退纯 1.3×
    val lensNode = remember { if (Build.VERSION.SDK_INT >= 33) RenderNode("newBarLens") else null }
    val lensShader = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                RuntimeShader(NEW_LENS_AGSL)
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
    }

    val edge = if (dark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f)
    val capsuleFill = if (dark) Color.White.copy(alpha = spec.capsuleDark) else Color.White.copy(alpha = spec.capsuleLight)
    val capsuleEdge = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.20f)
    // 镜头边缘 specular 高光（上亮下暗一圈）：空档时四条边常显的「border 感」来自这里
    val rimTop = if (dark) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.90f)
    val rimBottom = if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.12f)

    Box(
        modifier
            .offset(y = offsetY)
            .graphicsLayer { alpha = barAlpha }
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .height(NEW_BAR_HEIGHT.dp)
            .clip(NEW_BAR_SHAPE)
            .onSizeChanged { barW = it.width.toFloat() }
            // 普通横向滑动即可拖动胶囊（越过触摸斜率即起拖，绝对跟手；长按再拖同样生效），
            // 越格即切页；竖直滑动不拦截，普通点击仍走子项 onClick
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { press ->
                        dragging = true
                        dragX = press.x
                    },
                    onHorizontalDrag = { change, amount -> dragX += amount },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                )
            }
            .drawBehind {
                val radiusPx = NEW_BAR_RADIUS.dp.toPx()
                // 无障碍降级：实底背景（材质由调用方跳过）
                if (solidBackground) {
                    drawRoundRect(
                        color = colors.bgCard,
                        cornerRadius = CornerRadius(radiusPx),
                    )
                }
                // 选中态：玻璃内部的低对比胶囊（材质由调用方垫在底下）
                val pad = 6.dp.toPx()
                val slot = (size.width - pad * 2f) / NEW_TAB_COUNT
                val insetX = 0f
                val insetY = 6.dp.toPx()
                val capW = slot - insetX * 2f
                val capH = size.height - insetY * 2f
                val capCenter = capsuleX.value
                val left = capCenter - capW / 2f
                val top = insetY
                // 圆角与底栏同曲率（负责人 2026-10-27「radio 和 bar 同样的」）：32dp 被 capH/2 收口
                val radius = NEW_BAR_RADIUS.dp.toPx().coerceAtMost(capH / 2f)
                val corner = CornerRadius(radius)
                drawRoundRect(
                    color = capsuleFill,
                    topLeft = Offset(left, top),
                    size = Size(capW, capH),
                    cornerRadius = corner,
                )
                // 🔍 放大镜（2026-10-26 三改）：AGSL 平滑透镜——中心 1.3× → 左右边缘 1.7× 仅横向连续变倍，
                //    无分层无接缝；<33 降级只画基础 1.3×。空档「border 感」由下方高光环承担。
                if (lensReady && capW > 0f) {
                    drawIntoCanvas { nc ->
                        val native = nc.nativeCanvas
                        val clip = AndroidPath().apply {
                            addRoundRect(
                                RectF(left, top, left + capW, top + capH),
                                radius, radius,
                                AndroidPath.Direction.CW,
                            )
                        }
                        val midY = top + capH / 2f
                        val lens = lensNode
                        val shader = lensShader
                        if (lens != null && shader != null && Build.VERSION.SDK_INT >= 33) {
                            // API33+：图标行 → RenderNode → AGSL 连续变倍 → 裁进胶囊
                            val w = size.width.toInt().coerceAtLeast(1)
                            val h = size.height.toInt().coerceAtLeast(1)
                            shader.setFloatUniform("uCapsule", capCenter, midY, capW / 2f, capH / 2f)
                            // 中心 1.3×：横向过渡下整列 ±43px×1.3=±56px < 胶囊半高 65px，完整放下；
                            // 边缘 1.7× 只在左右过渡带生效——滑过邻居 icon/文字的放大镜观感
                            shader.setFloatUniform("uMag", spec.lensCenter, spec.lensEdge, spec.lensBandDp.dp.toPx(), 0f)
                            shader.setFloatUniform("uContent", size.width, size.height)
                            lens.setPosition(0, 0, w, h)
                            // ⚠ 每帧设完 uniform 后**新建** RenderEffect（与材质同款）——
                            //   缓存 effect 会被 HWUI 固化 uniform 快照（创建时全 0 → 全透明，
                            //   2026-10-27 模拟器实锤：缓存的 effect 恒输出透明）
                            lens.setRenderEffect(
                                RenderEffect.createRuntimeShaderEffect(shader, "uBackdrop"),
                            )
                            val lc = lens.beginRecording(w, h)
                            lc.drawPicture(iconPicture)
                            lens.endRecording()
                            native.save()
                            native.clipPath(clip)
                            native.drawRenderNode(lens)
                            native.restore()
                        } else {
                            // <33 / shader 失败降级：基础中心倍率（无边带、无接缝）
                            native.save()
                            native.clipPath(clip)
                            native.scale(spec.lensCenter, spec.lensCenter, capCenter, midY)
                            native.drawPicture(iconPicture)
                            native.restore()
                        }
                    }
                }
                // ③ 镜头边缘 specular 高光：上亮下暗一圈，空档时四条边也常显（替代原内高光）
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to rimTop,
                        1f to rimBottom,
                        startY = top,
                        endY = top + capH,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(capW, capH),
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                // 细微边缘
                drawRoundRect(
                    color = capsuleEdge,
                    topLeft = Offset(left, top),
                    size = Size(capW, capH),
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // ③ 边缘描边（压在内容之上，1dp 浅色）
            .border(1.dp, edge, NEW_BAR_SHAPE),
    ) {
        // 隐形录制行：内容全亮全尺寸录进 Picture（放大镜素材），不上屏
        Row(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val w = size.width.toInt().coerceAtLeast(1)
                    val h = size.height.toInt().coerceAtLeast(1)
                    val pc = iconPicture.beginRecording(w, h)
                    val prev = drawContext.canvas
                    drawContext.canvas = androidx.compose.ui.graphics.Canvas(pc)
                    drawContent()
                    drawContext.canvas = prev
                    iconPicture.endRecording()
                    if (!lensReady) lensReady = true
                }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NEW_BAR_TABS.forEachIndexed { index, (icon, title) ->
                val isActive = index == active
                val contentColor = if (isActive) colors.primary
                else if (dark) Color(0xFFB0B0B0) else Color(0xFF7D7E80)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    VanIcon(name = icon, size = 22.sp, color = contentColor)
                    Spacer(Modifier.size(2.dp))
                    FText(title, 11f, FontWeight.Medium, contentColor, maxLines = 1)
                }
            }
        }
        // 可见行：胶囊盖住的项按距离渐隐（由放大镜接管显示）；点击无水波纹
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NEW_BAR_TABS.forEachIndexed { index, (icon, title) ->
                val isActive = index == active
                val contentColor = if (isActive) colors.primary
                else if (dark) Color(0xFFB0B0B0) else Color(0xFF7D7E80)
                val itemCenter = padPx + index * slotW + slotW / 2f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        // 绘制期按胶囊距离渐隐：不触发重组，逐帧丝滑
                        .graphicsLayer {
                            val d = abs(itemCenter - capsuleX.value)
                            val fade = 10.dp.toPx()
                            alpha = if (!lensReady || slotW <= 0f) 1f
                            else ((d - cw / 2f) / fade).coerceIn(0f, 1f)
                        }
                        .selectable(
                            selected = isActive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                ) {
                    VanIcon(name = icon, size = 22.sp, color = contentColor)
                    Spacer(Modifier.size(2.dp))
                    FText(title, 11f, FontWeight.Medium, contentColor, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 玻璃材质（本组件专用）：外投影 → backdrop 真高斯模糊(20px) → 染色 → 明暗渐变 → 顶部高光。
 *
 * ⚠ 必须由**内容区的绘制作用域**在每帧录制背景后调用（与经典玻璃同一条链路），
 *   页面滚动/内容变化时材质才会跟着更新。底栏组件自己画材质的话，
 *   滚动时底栏不重绘 ⇒ 背景固定（2026-10-23 负责人报的致命问题）。
 *
 * @param area 岛区域（backdrop Picture 的坐标系）。
 * @param appear 与出现/收起动画同 alpha（材质跟随淡入淡出）。
 */
internal fun DrawScope.renderNewGlassBackdrop(
    picture: AndroidPicture,
    /** 内容 RenderNode（2026-10-27：优先用它取样，屏幕与玻璃共用同一份内容；null 时退回 Picture）。 */
    contentNode: RenderNode? = null,
    area: Rect,
    canvasW: Int,
    canvasH: Int,
    node: RenderNode?,
    /** AGSL 折射 shader（API33+ 创建；null = 降级纯 blur 链）。 */
    agsl: RuntimeShader?,
    shadowPaint: AndroidPaint,
    dark: Boolean,
    appear: Float,
    /** 材质参数（实验室「底部 Tab 组件」微调；null=出厂默认）。 */
    spec: NewBarSpec = NewBarSpec(),
) {
    val radiusPx = NEW_BAR_RADIUS.dp.toPx()
    val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(area, CornerRadius(radiusPx))) }

    // ⓪ 外投影（柔和，画在玻璃之前）
    shadowPaint.alpha = (spec.shadowAlpha * appear).toInt()
    drawIntoCanvas { nc ->
        val native = nc.nativeCanvas
        native.save()
        native.translate(0f, 8.dp.toPx())
        native.drawRoundRect(area.left, area.top, area.right, area.bottom, radiusPx, radiusPx, shadowPaint)
        native.restore()
    }

    // 采样条带：比岛大出 2× 模糊半径，避免模糊边缘"吸边"
    val blurPx = spec.blurPx
    val expand = max(blurPx * 2f, 16f)
    val src = RectF(
        (area.left - expand).coerceAtLeast(0f),
        (area.top - expand).coerceAtLeast(0f),
        (area.right + expand).coerceAtMost(canvasW.toFloat()),
        (area.bottom + expand).coerceAtMost(canvasH.toFloat()),
    )

    val hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
    val blurNode = node
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hardware && blurNode != null && blurPx > 0.01f

    clipPath(outline) {
        if (canBlur && blurNode != null) {
            if (Build.VERSION.SDK_INT >= 33 && agsl != null) {
                // AGSL 链：blur → 折射/色散/Fresnel（逐像素真折射）
                agsl.setFloatUniform(
                    "uRect",
                    area.left - src.left, area.top - src.top,
                    area.width, area.height,
                )
                agsl.setFloatUniform("uRadius", radiusPx)
                agsl.setFloatUniform("uRefraction", spec.refraction)
                agsl.setFloatUniform("uDispersion", spec.dispersion)
                agsl.setFloatUniform("uFresnel", spec.fresnel)
                agsl.setFloatUniform("uEdge", 16f)
                agsl.setFloatUniform("uContent", src.width(), src.height())
                blurNode.setRenderEffect(
                    RenderEffect.createChainEffect(
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
                        // ⚠ API33 的名字是 createRuntimeShaderEffect（createRuntimeShader 不存在）
                        RenderEffect.createRuntimeShaderEffect(agsl, "uBackdrop"),
                    ),
                )
            } else {
                // ① 真高斯模糊（无提饱和，与经典版参数分离）
                blurNode.setRenderEffect(
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
                )
            }
            blurNode.recordBackdropNew(picture, contentNode, src, canvasW, canvasH, blurPx)
            drawIntoCanvas { it.nativeCanvas.drawRenderNode(blurNode) }
        }
        // ② 染色层：亮=白（默认 22%），暗=近黑（默认 40%）——α 可在实验室调
        val tint = if (dark) Color(0xFF0B0B0F).copy(alpha = spec.tintDark * appear)
        else Color.White.copy(alpha = spec.tintLight * appear)
        drawRoundRect(color = tint, cornerRadius = CornerRadius(radiusPx))

        // ⑤ 内部明暗：顶部微亮 → 中段透明 → 底部微暗
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.06f * appear),
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.05f * appear),
            ),
            cornerRadius = CornerRadius(radiusPx),
        )

        // ④ 顶部高光：沿圆角上缘渐隐
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.18f else 0.45f),
                1f to Color.Transparent,
                startY = 0f,
                endY = size.height * 0.40f,
            ),
            cornerRadius = CornerRadius(radiusPx),
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

/** 本组件自己的 RenderNode 录制：只做真高斯模糊（无提饱和，与经典版参数分离）。 */
@RequiresApi(Build.VERSION_CODES.S)
private fun RenderNode.recordBackdropNew(
    picture: AndroidPicture,
    contentNode: RenderNode?,
    src: RectF,
    canvasW: Int,
    canvasH: Int,
    radiusPx: Float,
) {
    val left = src.left.toInt()
    val top = src.top.toInt()
    val w = (src.right - src.left).toInt().coerceAtLeast(1)
    val h = (src.bottom - src.top).toInt().coerceAtLeast(1)
    setPosition(left, top, left + w, top + h)
    setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP))
    val rc = beginRecording(w, h)
    rc.translate(-src.left, -src.top)
    if (contentNode != null) rc.drawRenderNode(contentNode)
    else rc.drawPicture(picture, RectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat()))
    endRecording()
}

/**
 * 实验室用预览（同样独立实现）：自绘示例背景 → 逐帧录 Picture → 内容绘制后调用
 * [renderNewGlassBackdrop] 垫材质 → 上面叠 [NewLiquidGlassTabBar] 的胶囊/图标。
 * 预览深浅跟随 App 主题，不做独立切换（所见即真机）。
 */
@Composable
fun NewLiquidGlassTabBarPreview(
    /** 材质/透镜参数（实验室调参实时传入；默认出厂值）。 */
    spec: NewBarSpec = NewBarSpec(),
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val dark = tokens.isDark

    val picture = remember { AndroidPicture() }
    val node = remember { if (Build.VERSION.SDK_INT >= 31) RenderNode("newGlassPreviewBackdrop") else null }
    val agsl = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                RuntimeShader(NEW_GLASS_AGSL)
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
    }
    val reduceTransparency = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val shadowPaint = remember(density) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(NEW_BAR_SHADOW_ALPHA.toInt(), 0, 0, 0)
            maskFilter = BlurMaskFilter(with(density) { 18.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf(0) }

    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color(0xFF0F0F12) else Color(0xFFF7F8FA)),
    ) {
        // 示例背景：逐帧录 Picture（玻璃材质由内容绘制链路垫出），不含 bar 自身
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.boundsInRoot().topLeft
                    contentSize = it.size
                }
                .drawWithContent {
                    val w = size.width.toInt().coerceAtLeast(1)
                    val h = size.height.toInt().coerceAtLeast(1)
                    val rect = barRect
                    if (Build.VERSION.SDK_INT < 31 || rect == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    val pc = picture.beginRecording(w, h)
                    val prev = drawContext.canvas
                    drawContext.canvas = androidx.compose.ui.graphics.Canvas(pc)
                    drawContent()
                    drawContext.canvas = prev
                    picture.endRecording()
                    drawContent()
                    // 材质垫在内容之上（与真机同链路），bar 的胶囊/图标再叠在上面
                    // 系统「减弱动效」开启时跳过材质（bar 自画实底）
                    val area = Rect(
                        rect.left - origin.x, rect.top - origin.y,
                        rect.right - origin.x, rect.bottom - origin.y,
                    )
                    if (!reduceTransparency) {
                        renderNewGlassBackdrop(
                            picture = picture,
                            area = area,
                            canvasW = w,
                            canvasH = h,
                            node = node,
                            agsl = agsl,
                            shadowPaint = shadowPaint,
                            dark = dark,
                            appear = 1f,
                            spec = spec,
                        )
                    }
                },
        ) {
            NewPreviewBackdrop(dark)
        }

        NewLiquidGlassTabBar(
            active = active,
            onSelect = { active = it },
            visible = true,
            solidBackground = reduceTransparency,
            spec = spec,
            onPositioned = { barRect = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                .fillMaxWidth(),
        )
    }
}

/** 预览示例背景（与旧 SampleBackdrop 独立，不共用）。 */
@Composable
private fun NewPreviewBackdrop(dark: Boolean) {
    val card = if (dark) Color(0xFF1C1C22) else Color.White
    val accentA = if (dark) Color(0xFF2A4B7C) else Color(0xFF7FA8FF)
    val accentB = if (dark) Color(0xFF4A3A6B) else Color(0xFFFFC07A)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF17171C), Color(0xFF0F0F12))
                else listOf(Color(0xFFFDFDFF), Color(0xFFEFF2F7)),
            ),
        ),
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 18.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accentA),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-18).dp, y = (-6).dp)
                .size(88.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accentB),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 96.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(card.copy(alpha = 0.85f)),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(card.copy(alpha = 0.55f)),
            )
        }
    }
}
