package com.live.finance.ui.main

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Picture as AndroidPicture
import android.graphics.RectF as AndroidRectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.glass.BarGlassSpec
import com.live.finance.core.glass.NewBarSpec
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 液态玻璃实验室（原生调试页，2026-10-19）。
 *
 * ## 这一版的定位：运行时调参 + 持久化
 *  - 调参直接写 `AppGraph.barGlass`（内存 State）⇒ 返回主页立即生效，不用重新打包；
 *  - 「保存设定」才落盘（`GlassStore` / `finance_glass`）⇒ 重启后仍用这套值。
 *
 * ## 布局要点
 *  - 预览固定在最上方、不参与滚动：此前预览放在 `verticalScroll` 里，上滑时预览整体
 *    滚走、玻璃层与 bar 分离（负责人报「遮罩向上滚动脱离 bar」）——固定后此问题消失。
 *  - 参数行：上方 −/数值/＋ 做精细步进，下方滑轨可自由拖动（拖动消费手势，不会带着面板滚动）。
 *  - 【微调参数】从下方弹面板，面板最大高度 52% 屏高，不遮顶部预览；
 *    【保存设定】落盘。
 *
 * 参数体系参考 `https://github.com/iyinchao/liquid-glass-studio`：
 * Gaussian blur / saturate / refraction（环宽·放大·tint·模糊·强度）/ Fresnel（镜面）/ spring 动画。
 *
 * ## 预览与真机 1:1（2026-10-19 校准）
 * [PreviewBar] 与 `MainScreen.FloatingIslandBar` 逐笔对齐：几何（60dp 高 / 30dp 圆角 /
 * 左右 16dp · 贴底 20dp / 投影 12dp·α34·dy6dp）、胶囊四笔、镜面高光 stop 0.32、
 * 无条件内底暗边、图标 22sp / 文字 scaledSp(12)、选中色 colors.primary + 200ms 过渡。
 * 预览深浅初始值 = 真机当前模式（LocalAppTokens.isDark）。
 * 唯一保留的差异：预览框外围裁剪 18dp 圆角 + 200dp 演示背景（那不是玻璃本身）。
 */
@Composable
fun LiquidGlassLabScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current

    // 预览的深浅切换在 GlassPreview 内部（右上角太阳/月亮图标，默认太阳=浅色）。
    // 三方案并排：
    //  0 = A·参数微调：**固定默认参数**（点它 + 保存可恢复默认）；
    //  1 = B·推荐样式：固定推荐样式（预览仍走 mine 专用渲染）；
    //  2 = C·自定义：当前工作 spec；任何参数微调后自动切到这里，不再改 A/B。
    val presetA = remember { BarGlassSpec() }
    val presetB = remember {
        BarGlassSpec(
            alphaLight = 0.55f,
            alphaDark = 0.60f,
            blurDp = 24f,
            saturation = 1.30f,
            highlight = 0.30f,
            specular = 0f,
            capsuleLight = 0.55f,
            capsuleDark = 0.12f,
        )
    }
    val savedSpec = remember { graph.barGlass.value }
    var customSpec by remember { mutableStateOf(savedSpec) }
    var collapseOnScroll by remember { mutableStateOf(savedSpec.collapseOnScroll) }
    // 有已保存的自定义就从 C 进入（不把用户的当前效果换成默认预览）；否则从 A 默认开始。
    var plan by remember { mutableStateOf(if (savedSpec == BarGlassSpec()) 0 else 2) }
    var sheet by remember { mutableStateOf(false) }
    // 新版底栏（NewLiquidGlassTabBar）的调参工作值：「底部 Tab 组件」开启时面板调这套。
    var customNew by remember { mutableStateOf(graph.newBarGlass.value) }

    /** A/B/C 当前选中的有效参数（行为开关 collapseOnScroll 三者共用）。 */
    fun effectiveSpec(): BarGlassSpec = when (plan) {
        0 -> presetA.copy(collapseOnScroll = collapseOnScroll)
        1 -> presetB.copy(collapseOnScroll = collapseOnScroll)
        else -> customSpec.copy(collapseOnScroll = collapseOnScroll)
    }

    /**
     * 参数微调入口：**只写入 C·自定义**，然后把选择切到 C。
     * A/B 是固定预设，绝不在这里被改写；实时更新仅用于预览/返回主页。
     */
    fun applyTuned(new: BarGlassSpec) {
        val merged = new.copy(collapseOnScroll = collapseOnScroll)
        customSpec = merged
        plan = 2
        graph.setBarGlass(merged)
    }

    /** 新版底栏调参入口：实时更新预览与真实栏（不落盘）。 */
    fun applyTunedNew(new: NewBarSpec) {
        customNew = new
        graph.setNewBarGlass(new)
    }

    /** 保存设定：持久化当前选中的 A/B/C 参数；A 即恢复默认。 */
    fun saveSelection() {
        val target = effectiveSpec()
        graph.setBarGlass(target)
        graph.persistBarGlass()
    }

    // 每次重组明确读一次（而不是只在参数表达式里调用），保证 A/B/C 或 customSpec
    // 变化时预览拿到的就是最新值。
    val previewSpec = effectiveSpec()

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // ── 固定区（不滚动）：标题 + 深浅切换 + 预览 ──────────────
                Column(Modifier.fillMaxWidth()) {
                    FText(
                        "液态玻璃实验室",
                        18f,
                        FontWeight.SemiBold,
                        colors.textPrimary,
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                    FText(
                        "调参实时生效；A/B 为固定预设，微调任意参数会自动切到 C·自定义；「保存设定」持久化。预览深浅可切换，真机跟随 App 主题；预览背景为示例，材质参数与真机一致。",
                        11f,
                        FontWeight.Normal,
                        colors.textTertiary,
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                    // ── 三方案并排（唯一的切换入口；深浅在预览右上角的太阳/月亮图标）──
                    // ⚠ 仅经典悬浮岛模式显示：新版底栏没有 A/B/C 预设，参数直接微调
                    if (!graph.useNewTabBar.value) Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeChip("A · 参数微调", plan == 0) { plan = 0 }
                        ModeChip("B · 推荐样式", plan == 1) { plan = 1 }
                        ModeChip("C · 自定义", plan == 2) { plan = 2 }
                    }
                    if (graph.useNewTabBar.value) {
                        // 新版独立组件：实验室同样用该组件自己的预览（独立实现）
                        NewLiquidGlassTabBarPreview(
                            spec = customNew,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                                .height(200.dp),
                        )
                    } else {
                        GlassPreview(
                            spec = previewSpec,
                            // B 也不再走实验室专用画法：统一用真机渲染器 + presetB 参数，
                            // 保证「预览看到的」就是「保存后真机上的」。
                            mine = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                                .height(200.dp),
                        )
                    }
                }

                // ── 可滚动区：两个按钮 + 底栏行为 ────────────────
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppButton(
                            text = "微调参数",
                            onClick = { sheet = true },
                            modifier = Modifier.weight(1f),
                            type = AppButtonType.Default,
                            plain = true,
                            round = true,
                        )
                        AppButton(
                            text = "保存设定",
                            onClick = {
                                if (graph.useNewTabBar.value) {
                                    // 新版底栏：没有 A/B/C 预设，保存当前微调值
                                    graph.setNewBarGlass(customNew)
                                    graph.persistNewBarGlass()
                                    toast.show("已保存：新版底栏参数")
                                } else {
                                    saveSelection()
                                    val label = when (plan) {
                                        0 -> "A·默认参数"
                                        1 -> "B·推荐样式"
                                        else -> "C·自定义"
                                    }
                                    toast.show("已保存：$label")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            type = AppButtonType.Primary,
                            round = true,
                        )
                    }

                    SectionTitle("底栏行为")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            FText("是否因滚动而收起", 14f, FontWeight.Medium, colors.textPrimary)
                            Spacer(Modifier.height(3.dp))
                            FText(
                                "开：页面向下滚动时底栏收起；关：底栏始终固定显示",
                                11f,
                                FontWeight.Normal,
                                colors.textTertiary,
                            )
                        }
                        VanSwitch(
                            checked = collapseOnScroll,
                            onCheckedChange = { on ->
                                // 行为开关独立于 A/B/C：即时生效 + 立即落盘（不必再点「保存设定」）。
                                // 同步写 customSpec，之后切到 C 时不会把开关冲回默认。
                                collapseOnScroll = on
                                customSpec = customSpec.copy(collapseOnScroll = on)
                                graph.setBarGlass(graph.barGlass.value.copy(collapseOnScroll = on))
                                graph.persistBarGlass()
                            },
                            size = 24,
                        )
                    }

                    SectionTitle("底部 Tab 组件")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            FText("新版 Liquid Glass 底部 Tab（独立组件）", 14f, FontWeight.Medium, colors.textPrimary)
                            Spacer(Modifier.height(3.dp))
                            FText(
                                "64dp / 32 圆角 / 20px 真模糊 / 内部选中胶囊；与经典悬浮岛完全独立，不与旧组件共用代码。开启后实时生效并保存。",
                                11f,
                                FontWeight.Normal,
                                colors.textTertiary,
                            )
                        }
                        VanSwitch(
                            checked = graph.useNewTabBar.value,
                            onCheckedChange = { on ->
                                graph.setUseNewTabBar(on)
                                graph.persistTabBarStyle()
                                toast.show(if (on) "已切换到新版底部 Tab" else "已切回经典底部 Tab")
                            },
                            size = 24,
                        )
                    }
                }
            }

            // ── 底部微调面板（只覆盖下半屏，不遮预览）──────────────────
            if (sheet) {
                // 透明点击拦截层：面板打开时，上方预览/预设区的点击不再穿透
                //（负责人 2026-10-19「点两个文字能透到底部点到预设方案哪里」）。
                // 不加暗色 ⇒ 只挡点击，不影响预览观感。
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
                TuneSheet(
                    spec = if (graph.useNewTabBar.value) null else previewSpec,
                    newSpec = if (graph.useNewTabBar.value) customNew else null,
                    onSpec = { applyTuned(it) },
                    onNewSpec = { applyTunedNew(it) },
                    onClose = { sheet = false },
                )
            }
        }
    }
}

// ─────────────────────────── 预览 ───────────────────────────

/**
 * 实时预览：有内容的背景 + 底部玻璃条。
 *
 * 与真实悬浮岛同一套手法：内容 Box 逐帧录 Picture → 条带经 RenderNode 真模糊 → 垫在条下方；
 * 条自身只画图标/文字/胶囊/高光。预览固定在页面顶部，不参与滚动。
 */
@Composable
private fun GlassPreview(spec: BarGlassSpec, mine: Boolean, modifier: Modifier = Modifier) {
    // 预览的深浅默认跟随 App 当前主题：否则深色主题用户看到的是浅色预览，
    // 保存后回到真实栏会判若两套（2026-10-23 负责人反馈「感觉被欺骗」）。
    val previewTokens = LocalAppTokens.current
    var dark by remember { mutableStateOf(previewTokens.isDark) }
    // ⚠ 实时预览：spec/mine/dark 用 rememberUpdatedState，并在**绘制 lambda 内**读 .value。
    //   只捕获普通参数时，drawWithCache 会复用旧绘制结果，调参后预览不刷新（2026-10-23 负责人报）。
    val liveSpec = rememberUpdatedState(spec)
    val liveMine = rememberUpdatedState(mine)
    val liveDark = rememberUpdatedState(dark)
    val density = LocalDensity.current
    val picture = remember { AndroidPicture() }
    val node = remember { if (Build.VERSION.SDK_INT >= 31) RenderNode("labGlass") else null }
    val saturation = remember { ColorMatrix() }
    // 与真机同款投影（MainScreen GLASS_SHADOW_*）：blur 12dp / α34 / dy 6dp。
    val shadowPaint = remember(density) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(34, 0, 0, 0)
            maskFilter = BlurMaskFilter(with(density) { 12.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    // 选中项与胶囊位置：与真机 MainScreen 同构 —— 位置放在父层，透镜才能与玻璃同帧取样
    var active by remember { mutableStateOf(0) }
    val capsulePos = animateFloatAsState(
        targetValue = active.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "labCapsule",
    )

    // 与真机 ISLAND_SHAPE_D = 30dp 同值：圆角半径直接影响折射环带的形状，
    // 预览若用 28dp，环宽 8dp 的占比就不一样了。
    val corner = 30.dp
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color(0xFF0F0F12) else Color(0xFFF7F8FA)),
    ) {
        // 内容层：录 Picture + 垫玻璃
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                .drawWithContent {
                    val w = size.width.toInt().coerceAtLeast(1)
                    val h = size.height.toInt().coerceAtLeast(1)
                    val rect = barRect
                    if (Build.VERSION.SDK_INT < 31 || rect == null) {
                        drawContent()
                        return@drawWithContent
                    }
                    // 在绘制时读 State：参数变化会直接 invalidate draw（不依赖 drawCache 重建）
                    val s = liveSpec.value
                    val m = liveMine.value
                    val d = liveDark.value
                    val pc = picture.beginRecording(w, h)
                    val prev = drawContext.canvas
                    drawContext.canvas = ComposeCanvas(pc)
                    drawContent()
                    drawContext.canvas = prev
                    picture.endRecording()
                    drawIntoCanvas { it.nativeCanvas.drawPicture(picture) }
                    val islandArea = Rect(
                        rect.left - origin.x, rect.top - origin.y,
                        rect.right - origin.x, rect.bottom - origin.y,
                    )
                    // B 方案（推荐样式）：材料参数写死，不走滑块 ——
                    // blur 24dp + 饱和 1.30 + 无环；tint 深浅两套（深=黑 60%，浅=白 55%）。
                    // 行业口径：模糊承担质感，tint 只做轻微罩色（iOS "material" 思路）。
                    val effSpec = if (m) {
                        s.copy(blurDp = 24f, saturation = 1.30f, highlight = 0.30f, specular = 0f)
                    } else {
                        s
                    }
                    val tint = if (m) {
                        if (d) Color(0xFF101014).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.55f)
                    } else {
                        Color.White.copy(alpha = if (d) s.alphaDark else s.alphaLight)
                    }
                    renderGlass(
                        picture = picture,
                        area = islandArea,
                        canvasW = w,
                        canvasH = h,
                        node = node,
                        saturation = saturation,
                        shadowPaint = shadowPaint,
                        spec = effSpec,
                        tint = tint,
                        cornerRadius = corner,
                    )
                },
        ) {
            SampleBackdrop(dark)
        }

        // 玻璃条：背景透明，材质由上一层垫出（与真实 MainScreen 同构）。
        // 几何与真机悬浮岛 1:1：左右 16dp、贴底 20dp、高 60dp（MainScreen 的
        // padding(start=16,end=16,bottom=20) + height(60dp)）。
        PreviewBar(
            spec = spec,
            dark = dark,
            mine = mine,
            shape = shape,
            active = active,
            onSelect = { active = it },
            capsulePos = capsulePos,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                .fillMaxWidth()
                .height(60.dp)
                .onGloballyPositioned { barRect = it.boundsInRoot() },
        )

        // 深浅切换：预览右上角的太阳/月亮图标（默认太阳=浅色；负责人 2026-10-19）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dark = !dark }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            FText(
                if (dark) "🌙" else "☀️",
                13f,
                FontWeight.Normal,
                if (dark) Color.White else Color.Black,
            )
        }
    }
}

/** 预览用的示例内容（200dp 高）：渐变 + 彩色圆 + 卡片/文字条，保证模糊有东西可糊。 */
@Composable
private fun SampleBackdrop(dark: Boolean) {
    val card = if (dark) Color(0xFF1A1A1E) else Color(0xFFFFFFFF)
    val line = if (dark) Color(0xFF2A2A30) else Color(0xFFE8EAEE)
    val text = if (dark) Color(0xFFF0F0F0) else Color(0xFF323233)
    val sub = if (dark) Color(0xFFA0A0A8) else Color(0xFF8A8A90)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF17171C), Color(0xFF0F0F12))
                    else listOf(Color(0xFFFDFDFF), Color(0xFFF0F2F6)),
                ),
            ),
    ) {
        // 上半部彩色光斑
        Box(
            Modifier.offset(x = 14.dp, y = 10.dp).size(92.dp)
                .background(Color(0xFF3A66E0).copy(alpha = 0.85f), CircleShape),
        )
        Box(
            Modifier.offset(x = 120.dp, y = (-8).dp).size(76.dp)
                .background(Color(0xFF07C160).copy(alpha = 0.80f), CircleShape),
        )
        Box(
            Modifier.offset(x = 230.dp, y = 26.dp).size(62.dp)
                .background(Color(0xFFFF976A).copy(alpha = 0.85f), CircleShape),
        )
        // 卡片行（被玻璃压住的下半部分）
        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 96.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(card)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).background(Color(0xFF3A66E0), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText("预估总资产", style = TextStyle(color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(3.dp))
                BasicText("¥ 128,660.00", style = TextStyle(color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
        }
        // 底部彩色光斑 + 竖条：故意压在玻璃条后面，对比模糊/折射强弱
        Box(
            Modifier.offset(x = 18.dp, y = 128.dp).size(66.dp)
                .background(Color(0xFF3A66E0).copy(alpha = 0.90f), CircleShape),
        )
        Box(
            Modifier.offset(x = 96.dp, y = 138.dp).size(56.dp)
                .background(Color(0xFF07C160).copy(alpha = 0.90f), CircleShape),
        )
        Box(
            Modifier.offset(x = 176.dp, y = 126.dp).size(58.dp)
                .background(Color(0xFFFF976A).copy(alpha = 0.92f), CircleShape),
        )
        // 底部彩色竖条：故意压在玻璃条后面，对比模糊/折射强弱。
        // ⚠ 贴右端排布（align+负 offset），不能用绝对 x=258dp —— 320dp 级小屏会把
        //   右端彩条裁掉，折射环预览就失去对比物了。
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-18).dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(
                Color(0xFFEE0A24), Color(0xFFFFB300), Color(0xFF07C160),
                Color(0xFF00BCD4), Color(0xFF3A66E0), Color(0xFF7232DD),
            ).forEach { c ->
                Box(Modifier.width(8.dp).height(58.dp).background(c))
            }
        }
        // 底部细条纹：高频内容，用来肉眼判断胶囊透镜的放大是否生效
        // （纯色/大色块被放大后几乎看不出差别 —— 负责人 2026-10-19 反馈「只有一小环」的原因之一）
        Row(
            Modifier.offset(x = 0.dp, y = 150.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(26) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .background(
                            if (i % 2 == 0) Color(0x66FFFFFF) else Color(0x22000000),
                        ),
                )
            }
        }
        // 底部细字，保证"内容"一直延伸到玻璃后面
        BasicText(
            "近期消费",
            style = TextStyle(color = sub, fontSize = 11.sp),
            modifier = Modifier.padding(start = 16.dp, top = 180.dp),
        )
    }
}

/**
 * 预览玻璃条：图标/文字 + 胶囊 + 高光/描边；材质来自父层。
 *
 * 与真机 FloatingIslandBar 逐笔对齐（2026-10-19 校准）：
 *  - 胶囊四笔 = MainScreen.drawSelectionCapsule 全套：白填充 + 上缘 1dp 高光(α0.75) +
 *    定形描边 + 内底 1dp 暗边（此前只画两笔，深色下胶囊明显比真机「平」）；
 *  - 镜面高光 stop 0.32 / 内底暗边无条件画 = MainScreen.drawGlassEdge（specular=0 时
 *    镜面 alpha 为 0 自然不显，但内底暗边真机永远在画，预览缺它就少一层「厚度」）；
 *  - 图标 22sp / 文字 scaledSp(12) / 选中色 colors.primary（跟主题，同真机）+
 *    200ms 颜色过渡（真机 transition: color .2s ease）；
 *  - 未选中色与描边是「只跟深浅模式」的 token（tabbarText / tabbarGlassBorder），
 *    因预览的深浅开关独立于 App 全局模式，这里按 token 等值展开
 *    （浅 #7D7E80·黑6% / 深 #B0B0B0·白12%，AppTokens LIGHT/DARK_TABBAR）。
 */
@Composable
private fun PreviewBar(
    spec: BarGlassSpec,
    dark: Boolean,
    /** C 方案·我的设计：胶囊/描边/动效换行业做法，参数写死不跟滑块。 */
    mine: Boolean,
    shape: RoundedCornerShape,
    active: Int,
    onSelect: (Int) -> Unit,
    capsulePos: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tabs = listOf("wap-home-o" to "首页", "bill-o" to "账本", "notes-o" to "动态", "user-o" to "我的")
    // 未选中 = tabbarText（只跟深浅）；选中 = colors.primary（跟主题预设，与真机同源）
    val inactive = if (dark) Color(0xFFB0B0B0) else Color(0xFF7D7E80)
    val border = if (dark) Color(0x1FFFFFFF) else Color(0x0F000000)

    Row(
        modifier
            .clip(shape)
            .drawBehind {
                // ── 受光面：四周（与真机 drawGlassLight 同步，2026-10-19「受光 改为四周」）──
                if (spec.highlight > 0.001f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = spec.highlight.coerceIn(0f, 0.5f)),
                        cornerRadius = CornerRadius(30.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
                // 胶囊透镜由父层 GlassPreview 画（与玻璃同帧取样），这里只画胶囊本体
                // ── 选中胶囊四笔 ＝ MainScreen.drawSelectionCapsule ──
                // 位置口径与下面 Row 的 padding(horizontal = 10.dp) + weight(1f) 严格对齐
                val pad = 10.dp.toPx()
                val slot = (size.width - pad * 2f) / tabs.size
                // 与真机 CAPSULE_INSET_X 同步（2026-10-19：6dp → 4dp → 2dp，两次加宽）
                val insetX = 2.dp.toPx()
                val insetY = 6.dp.toPx()
                val w = slot - insetX * 2f
                val h = size.height - insetY * 2f
                if (w > 0f && h > 0f) {
                    val left = pad + capsulePos.value * slot + insetX
                    val top = insetY
                    val radius = h / 2f
                    val corner = CornerRadius(radius)
                    val topLeft = Offset(left, top)
                    val capsuleSize = Size(w, h)
                    // ① 胶囊填充：C 方案走低对比半透明（iOS 药丸不做强白，靠模糊底衬托）；
                    //    你的方案保持白填充凸起
                    drawRoundRect(
                        color = Color.White.copy(
                            alpha = if (mine) (if (dark) 0.12f else 0.55f)
                            else (if (dark) spec.capsuleDark else spec.capsuleLight),
                        ),
                        topLeft = topLeft,
                        size = capsuleSize,
                        cornerRadius = corner,
                    )
                    // ② 描边：C 方案 = 菲涅尔边缘（上亮下暗的渐隐描边，模拟玻璃边缘聚光）；
                    //    你的方案 = 受光联动的四周描边
                    if (mine) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.50f),
                                1f to Color.White.copy(alpha = 0.08f),
                                startY = top,
                                endY = top + h,
                            ),
                            topLeft = topLeft,
                            size = capsuleSize,
                            cornerRadius = corner,
                            style = Stroke(1.dp.toPx()),
                        )
                    } else {
                        drawRoundRect(
                            color = Color.White.copy(alpha = (spec.highlight * 2.5f).coerceIn(0.30f, 0.85f)),
                            topLeft = topLeft,
                            size = capsuleSize,
                            cornerRadius = corner,
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                    // ③ 定形描边：C 方案用极淡白（低对比），你的方案用 tabbarGlassBorder
                    drawRoundRect(
                        color = if (mine) Color.White.copy(alpha = if (dark) 0.10f else 0.06f) else border,
                        topLeft = topLeft,
                        size = capsuleSize,
                        cornerRadius = corner,
                        style = Stroke(1.dp.toPx()),
                    )
                    // ④ 内底暗边：一道极淡的暗，给出厚度
                    val inset = 1.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (dark) 0.10f else 0.06f),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left + inset, top + inset),
                        size = Size(w - inset * 2f, h - inset * 2f),
                        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                        style = Stroke(inset),
                    )
                }
            }
            // 外缘描边：C 方案 = 菲涅尔渐隐（上亮下暗，玻璃边缘聚光）；你的方案 = 1dp token 描边
            .then(
                if (mine) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.32f),
                                1f to Color.White.copy(alpha = 0.06f),
                            ),
                            cornerRadius = CornerRadius(30.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                } else {
                    Modifier.border(1.dp, border, shape)
                },
            )
            .drawWithContent {
                drawContent()
                // ── 镜面高光 + 内底暗边 ＝ MainScreen.drawGlassEdge（两笔无条件画）──
                val radiusPx = 30.dp.toPx()
                val w = size.width
                val h = size.height
                // 镜面高光：沿外缘 1.2dp 描边，垂直渐变 ⇒ 只有上缘亮（specular=0 时 alpha 为 0 不显）
                val specInset = 0.6.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = spec.specular.coerceIn(0f, 1f)),
                        0.32f to Color.Transparent,
                        startY = 0f,
                        endY = h,
                    ),
                    topLeft = Offset(specInset, specInset),
                    size = Size(w - specInset * 2, h - specInset * 2),
                    cornerRadius = CornerRadius((radiusPx - specInset).coerceAtLeast(0f)),
                    style = Stroke(1.2.dp.toPx()),
                )
                // 内底暗边：给出玻璃的「厚度」，否则像一张贴纸（真机永远画，与镜面无关）
                val inset = 1.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.08f),
                        startY = 0f,
                        endY = h,
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(w - inset * 2, h - inset * 2),
                    cornerRadius = CornerRadius((radiusPx - inset).coerceAtLeast(0f)),
                    style = Stroke(inset),
                )
            }
            .padding(horizontal = 10.dp), // 真机 padding: 0 10px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, (icon, title) ->
            val selected = index == active
            // transition: color .2s ease（真机同款 200ms 过渡）
            val color by animateColorAsState(
                if (selected) colors.primary else inactive,
                tween(200),
                label = "labTabColor$index",
            )
            // 放大镜 = 选中项图标+文字放大：C 方案 1.12×（更克制，iOS 药丸不做强放大）；你的方案 1.18×
            val itemScale by animateFloatAsState(
                targetValue = if (selected) (if (mine) 1.12f else 1.18f) else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                label = "labTabScale$index",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .scale(itemScale),
            ) {
                VanIcon(
                    name = icon,
                    size = 22.sp, // 真机 .nav-icon 22px
                    color = color,
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    title,
                    style = TextStyle(
                        color = color,
                        fontSize = scaledSp(12f), // 真机 scaledSp(12f)，随字号档位缩放
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

// ─────────────────────────── 玻璃渲染（参数化）───────────────────────────

/**
 * 参数化液态玻璃：与 `MainScreen.renderLiquidGlass` 同一套分层。
 * ① 投影 → ② 真模糊 backdrop（RenderNode + RenderEffect）→ ③ tint → ④ 折射环（可选）。
 */
private fun DrawScope.renderGlass(
    picture: AndroidPicture,
    area: Rect,
    canvasW: Int,
    canvasH: Int,
    node: RenderNode?,
    saturation: ColorMatrix,
    shadowPaint: AndroidPaint,
    spec: BarGlassSpec,
    tint: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
) {
    val radiusPx = cornerRadius.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(area, CornerRadius(radiusPx))) }

    // ⓪ 投影（画在玻璃之前、裁剪之外）
    drawIntoCanvas { nc ->
        val native = nc.nativeCanvas
        native.save()
        native.translate(0f, 6.dp.toPx())
        native.drawRoundRect(area.left, area.top, area.right, area.bottom, radiusPx, radiusPx, shadowPaint)
        native.restore()
    }

    val blurPx = spec.blurDp.dp.toPx()
    val expand = maxOf(blurPx * 2f, 8.dp.toPx())
    val src = AndroidRectF(
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
            blurNode.recordBackdrop(picture, src, canvasW, canvasH, blurPx, spec.saturation, saturation)
            drawIntoCanvas { it.nativeCanvas.drawRenderNode(blurNode) }
        }
        drawRect(tint, topLeft = area.topLeft, size = area.size)
        // 折射不再画在 bar 外缘：改由 PreviewBar 的胶囊透镜（drawPreviewLens）负责。
    }
}

/** 与 MainScreen 同款的 RenderNode 录制（模糊 + 提饱和）。 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
private fun RenderNode.recordBackdrop(
    picture: AndroidPicture,
    src: AndroidRectF,
    canvasW: Int,
    canvasH: Int,
    radiusPx: Float,
    saturate: Float,
    saturation: ColorMatrix,
) {
    val left = src.left.toInt()
    val top = src.top.toInt()
    val w = (src.right - src.left).toInt().coerceAtLeast(1)
    val h = (src.bottom - src.top).toInt().coerceAtLeast(1)
    setPosition(left, top, left + w, top + h)
    saturation.setSaturation(saturate)
    setRenderEffect(
        RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturation)),
        ),
    )
    val rc = beginRecording(w, h)
    rc.translate(-src.left, -src.top)
    rc.drawPicture(picture, AndroidRectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat()))
    endRecording()
}

// ─────────────────────────── 微调面板 ───────────────────────────

/**
 * 底部微调面板：最大高度 52% 屏高，顶部预览（约 320dp：标题+开关+200dp 预览）不被遮挡。
 * 无全屏遮罩 —— 遮罩会把预览一起压暗，违背「不能遮挡预览面板」。
 */
@Composable
private fun BoxScope.TuneSheet(
    /** 经典悬浮岛参数（新版底栏模式下为 null）。 */
    spec: BarGlassSpec?,
    /** 新版底栏参数（经典模式下为 null）。 */
    newSpec: NewBarSpec?,
    onSpec: (BarGlassSpec) -> Unit,
    onNewSpec: (NewBarSpec) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.52f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(colors.bgCard)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText("微调参数（实时生效）", 15f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
            // 真按钮（此前是一段可点文字，负责人 2026-10-19「关闭不做一个按钮想怎么样」）
            AppButton(
                text = "关闭",
                onClick = onClose,
                type = AppButtonType.Default,
                plain = true,
                round = true,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            if (newSpec != null) {
                // ── 新版底栏（NewLiquidGlassTabBar）参数集 ──
                Stepper("染色 α(浅)", newSpec.tintLight, 0.02f, 0f..0.85f, 2) { onNewSpec(newSpec.copy(tintLight = it)) }
                Stepper("染色 α(深)", newSpec.tintDark, 0.02f, 0f..0.85f, 2) { onNewSpec(newSpec.copy(tintDark = it)) }
                Stepper("模糊 px", newSpec.blurPx, 1f, 0f..40f, 0) { onNewSpec(newSpec.copy(blurPx = it)) }
                Stepper("折射强度", newSpec.refraction, 0.5f, 0f..24f, 1) { onNewSpec(newSpec.copy(refraction = it)) }
                Stepper("色散", newSpec.dispersion, 0.05f, 0f..1f, 2) { onNewSpec(newSpec.copy(dispersion = it)) }
                Stepper("边缘白边", newSpec.fresnel, 0.05f, 0f..1f, 2) { onNewSpec(newSpec.copy(fresnel = it)) }
                Stepper("透镜中心 ×", newSpec.lensCenter, 0.05f, 1f..1.5f, 2) { onNewSpec(newSpec.copy(lensCenter = it)) }
                Stepper("透镜边缘 ×", newSpec.lensEdge, 0.05f, 1f..2.2f, 2) { onNewSpec(newSpec.copy(lensEdge = it)) }
                Stepper("过渡带 dp", newSpec.lensBandDp, 1f, 4f..32f, 0) { onNewSpec(newSpec.copy(lensBandDp = it)) }
                Stepper("胶囊 α(浅)", newSpec.capsuleLight, 0.02f, 0f..0.8f, 2) { onNewSpec(newSpec.copy(capsuleLight = it)) }
                Stepper("胶囊 α(深)", newSpec.capsuleDark, 0.02f, 0f..0.8f, 2) { onNewSpec(newSpec.copy(capsuleDark = it)) }
                Stepper("投影 α", newSpec.shadowAlpha.toFloat(), 2f, 0f..80f, 0) { onNewSpec(newSpec.copy(shadowAlpha = it.roundToInt())) }
            } else {
                val spec = spec!!
                // ── 经典悬浮岛参数集（8 项全部接线有效，2026-10-27 AVD 实测）──
                Stepper("浅色 tint α", spec.alphaLight, 0.01f, 0.15f..0.80f, 2) { onSpec(spec.copy(alphaLight = it)) }
                Stepper("深色 tint α", spec.alphaDark, 0.01f, 0.05f..0.60f, 2) { onSpec(spec.copy(alphaDark = it)) }
                Stepper("模糊半径 dp", spec.blurDp, 1f, 0f..40f, 0) { onSpec(spec.copy(blurDp = it)) }
                Stepper("饱和度", spec.saturation, 0.05f, 1.0f..2.0f, 2) { onSpec(spec.copy(saturation = it)) }
                Stepper("上缘受光", spec.highlight, 0.01f, 0f..0.30f, 2) { onSpec(spec.copy(highlight = it)) }
                Stepper("镜面描边", spec.specular, 0.01f, 0f..0.85f, 2) { onSpec(spec.copy(specular = it)) }
                Stepper("胶囊 α(浅)", spec.capsuleLight, 0.01f, 0f..0.95f, 2) { onSpec(spec.copy(capsuleLight = it)) }
                Stepper("胶囊 α(深)", spec.capsuleDark, 0.01f, 0f..0.60f, 2) { onSpec(spec.copy(capsuleDark = it)) }
            }
        }
    }
}

/**
 * 参数行：上方 −/数值/＋ 做精细步进，下方轨道可自由滑动。
 * 滑动与步进都写同一个 onChange；滑动按 step 取整，保持数值精度。
 */
@Composable
private fun Stepper(
    label: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    onChange: (Float) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText(label, 13f, FontWeight.Medium, colors.textSecondary, Modifier.weight(1f))
            StepButton("−", enabled = value > range.start + step * 0.01f) {
                onChange((value - step).coerceIn(range.start, range.endInclusive))
            }
            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                FText(fmt(value, decimals), 13f, FontWeight.Medium, colors.textPrimary)
            }
            StepButton("＋", enabled = value < range.endInclusive - step * 0.01f) {
                onChange((value + step).coerceIn(range.start, range.endInclusive))
            }
        }
        GlassParamSlider(
            value = value,
            range = range,
            step = step,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 2.dp),
        )
    }
}

/** 细参数滑轨：拖动/点击都直接改值，松手后由上层按 step 落定。 */
@Composable
private fun GlassParamSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val ratio = ((value - range.start) / span).coerceIn(0f, 1f)
    // 绘制/手势闭包跨重组存活，必须读最新值，否则从 A/B 起调时会一直用旧 spec。
    val liveRatio = rememberUpdatedState(ratio)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    Box(
        modifier
            .height(32.dp)
            .drawBehind {
                val pad = 9.dp.toPx()
                val trackH = 4.dp.toPx()
                val cy = size.height / 2f
                val start = pad
                val end = (size.width - pad).coerceAtLeast(start + 1f)
                val usable = end - start
                val thumbX = start + usable * liveRatio.value
                val corner = CornerRadius(trackH / 2f)
                drawRoundRect(
                    color = colors.bgThird,
                    topLeft = Offset(start, cy - trackH / 2f),
                    size = Size(usable, trackH),
                    cornerRadius = corner,
                )
                if (thumbX > start) {
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset(start, cy - trackH / 2f),
                        size = Size(thumbX - start, trackH),
                        cornerRadius = corner,
                    )
                }
                drawCircle(
                    color = colors.primary,
                    radius = 9.dp.toPx(),
                    center = Offset(thumbX, cy),
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(thumbX, cy),
                )
            }
            .pointerInput(range, step) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun setFromX(x: Float) {
                        val pad = 9.dp.toPx()
                        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                        val r = ((x - pad) / usable).coerceIn(0f, 1f)
                        val raw = range.start + r * (range.endInclusive - range.start)
                        val snapped = range.start + ((raw - range.start) / step).roundToInt() * step
                        currentOnValueChange.value(snapped.coerceIn(range.start, range.endInclusive))
                    }
                    // ⚠ 方向锁（2026-10-27 负责人报「上下滚动列表容易误触滑块，值快速变大/变小」）：
                    //   按下不再立即设值——横向移动超过触摸斜率才进入滑轨模式；
                    //   竖向移动先行就让给列表滚动（本手势直接退出，不消费 move）；
                    //   原地点击（未越斜率）在抬起时按点击位置设值。
                    val startX = down.position.x
                    val startY = down.position.y
                    var active = false
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!active) {
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val slop = viewConfiguration.touchSlop
                            if (abs(dy) > slop && abs(dy) >= abs(dx)) break
                            if (abs(dx) > slop) active = true
                        }
                        if (active) {
                            setFromX(change.position.x)
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (!active) setFromX(startX)
                            break
                        }
                    }
                }
            },
    )
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) colors.bgThird else colors.bgThird.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FText(
            symbol,
            16f,
            FontWeight.Medium,
            if (enabled) colors.textPrimary else colors.textPlaceholder,
        )
    }
}

// ─────────────────────────── 小组件 ───────────────────────────

@Composable
private fun SectionTitle(text: String) {
    FText(
        text,
        14f,
        FontWeight.Medium,
        LocalAppColors.current.textTertiary,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun ModeChip(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    FText(
        text,
        12f,
        if (active) FontWeight.Medium else FontWeight.Normal,
        if (active) colors.primary else colors.textSecondary,
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.bgCard)
            // 选中态加一圈同色描边：芯片挨在一起时，当前方案一眼可辨
            .border(
                1.dp,
                if (active) colors.primary.copy(alpha = 0.45f) else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}


private fun fmt(v: Float, decimals: Int = 2): String =
    String.format(java.util.Locale.US, "%." + decimals + "f", v)
