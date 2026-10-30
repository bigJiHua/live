package com.live.finance.ui.main

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Picture as AndroidPicture
import android.graphics.RectF as AndroidRectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.glass.BarGlassSpec
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.diary.DiaryListScreen
import com.live.finance.ui.finance.FinanceScreen
import com.live.finance.ui.home.HomeScreen
import com.live.finance.ui.user.UserScreen
import com.live.vant.icon.VanIcon

/**
 * 悬浮岛四入口 —— 逐条对齐 web `layout/MainLayout.vue` 的 `<router-link>` 列表：
 * /home（wap-home-o 首页）、/finance（bill-o 账本）、/diary（notes-o 动态）、/user（user-o 我的）。
 * 注：web `router/map.js` 里 `/home /finance /diary /user` 四条均**未**设 hideTabbar → 四页都常显底部岛。
 */
internal val ISLAND_TABS = listOf(
    "wap-home-o" to "首页",
    "bill-o" to "账本",
    "notes-o" to "动态",
    "user-o" to "我的",
)

/** web `box-shadow: 0 8px 24px rgba(0,0,0,.08)`。 */
private val ISLAND_SHADOW = Color(0x14000000)
private val ISLAND_SHAPE_D = 30.dp // border-radius: 30px

/**
 * 玻璃参数已全部搬到 [BarGlassSpec]（2026-10-19）：
 *  - 浅/深 tint α、模糊半径、饱和度、上缘受光、镜面描边、胶囊 α、环折射五参数
 *    **都不再是这里的常量**，而是由「液态玻璃实验室」调参 → `AppGraph.barGlass` 实时提供；
 *  - 这里只保留与参数无关的几何/投影常量（描边宽度、内底暗边、投影）。
 *
 * 沿革（为什么不再写死）：2026-10-18「太透明」→「灰黑块」→「不要黑色和颗粒」→
 * 「要 Apple 液态玻璃」→ 2026-10-19 实验室逐项选定（浅α .20 / 深α .08 / 模糊 2dp /
 * 饱和 1.09 / 受光 0 / 镜面 0 / 胶囊 .65·.02 / 环 8dp×1.14）⇒ 改为运行时可调 + 可保存。
 */

/** 镜面高光描边宽度（只亮上缘，不再整面泛白）。 */
private val GLASS_SPECULAR_W = 1.2.dp

/** 内底暗边宽度 —— 给出玻璃的"厚度感"。 */
private val GLASS_INNER_SHADOW_W = 1.dp

/**
 * 胶囊相对每个格子的内缩（纵向 6dp、横向 **2dp**）—— 比整格小一圈才读得出"小圆角"。
 * 横向沿革：6dp →（10-19）4dp →（10-19 二次）**2dp**：负责人两次要求「再宽一点点」。
 */
private val CAPSULE_INSET_Y = 6.dp
private val CAPSULE_INSET_X = 2.dp

/** 选中项（图标+文字）放大倍数 —— 负责人 2026-10-19：「做放大镜就好 放大 ICON 和文字」。 */
private const val TAB_SELECTED_SCALE = 1.18f

/**
 * 外投影（对应 web `box-shadow: 0 8px 24px rgba(0,0,0,.08)`）。
 *
 * ⚠ **不能**用 `Modifier.shadow` 挂在岛上：岛在 blur 模式下背景是透明的，而 `shadow` 是
 * `graphicsLayer.shadowElevation` —— 平台把阴影画在**内容之下**、且不挖空内部，透明背景就等于
 * 把整块阴影露出来，玻璃内部被压成灰。所以投影改在玻璃层里、玻璃**之前**画。
 */
private val GLASS_SHADOW_BLUR = 12.dp
private val GLASS_SHADOW_DY = 6.dp
private const val GLASS_SHADOW_ALPHA = 34 // ≈13%，比 web 的 8% 略重（真机小屏上 8% 几乎看不见）

/**
 * 主外壳：内容区 + 悬浮玻璃岛 TabBar（一比一复刻 web 的 `.floating-island-nav`）。
 *
 * 布局对照 web MainLayout：
 *  - `.main-body { height: calc(100vh - 90px) }` —— ⚠ **别照抄成内容区的 `padding(bottom = 90.dp)`**：
 *    web 的页面自己是 `height: 100vh` / `min-height: 100dvh`，会**溢出**这 90px、背景铺满视口；
 *    原生若把内容压扁 90dp，岛一收起就会露出一条空带（深色下即「大黑条」）。详见下方 [Box] 处注释。
 *  - 岛 `position:fixed; bottom:20px; left/right:16px; height:60px; border-radius:30px; padding:0 10px`。
 *  - 各项 `flex:1; height:100%`，图标 22px、文字 11px/500，未选中 `--tabbar-text`、选中 `--theme-primary`。
 *  - 滚动沉浸：**向下浏览时收起**（translateY 120px + opacity 0）、**向上回退时弹起**，transition .3s。
 *    （web 源码注释写作「下滑收起、上滑弹起」，那个「下滑」指的是**页面向下浏览**，不是手指向下滑 ——
 *     ⚠ 两者方向相反，实现见下方 [scrollConnection] 处的符号对照表，别再搞反。）
 *
 * [tabState] 由 `AppNavHost` 持有（`rememberSaveable`）：全局顶栏 [GlobalNavBar] 要按当前 Tab 出标题，
 * 状态必须提到两者共同的上方。
 */
@Composable
fun MainScreen(nav: NavHostController, tabState: MutableIntState) {
    val colors = LocalAppColors.current
    var tab by tabState

    // 系统返回（第三十七轮，负责人口径）：账本/动态/我的 的「最后一层」返回指向首页 ——
    // 任何退出必须先经过首页，真正的退出只在首页发生（「离开确认」弹窗，第三十六轮口径见 HomeExitDialog 注释）。
    // 子页面（独立路由）的系统返回不在此列：它们由 Navigation 逐层 popBackStack，
    // 弹到 MAIN 落在 Tab 根后就归这条规则管。
    // 弹窗开着时返回键由弹窗自己的 window 处理（关闭），不会走到这里。
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler {
        if (tab != 0) tab = 0 else showExitDialog = true
    }

    // 对应 web MainLayout 的 onWindowScroll：向下浏览收起、向上回退弹起（阈值 6px）
    //
    // ⚠⚠ **Compose 的 `consumed.y` 与 web 的 `window.scrollY` 符号相反**，直接照抄 web 的 `delta > 0 → 收起`
    //    会得到完全反向的行为（2026-09-19 真机反馈「底部 Bar 逻辑做反了」）：
    //      | 手势                | 页面位置      | web scrollY | Compose consumed.y | 期望 |
    //      |---|---|---|---|---|
    //      | 手指**上**滑        | 向**下**浏览  | 增大        | **负**             | 收起 |
    //      | 手指**下**滑        | 向**上**回退  | 减小        | **正**             | 弹起 |
    //    符号依据（仓库内两处已验证的实现，别再凭感觉改）：
    //      - `ui/flow/FlowListScreen.kt`：「上滑看后续内容 → consumed.y 为负」，故 `scrolledPx - consumed.y`
    //      - `vant-ui/.../VanPullRefresh.kt`：下拉（= 手指下滑）时 `available.y > 0`
    val tabbarVisible = remember { mutableStateOf(true) }
    // 玻璃 backdrop 的逐帧失效信号（2026-10-23）：内容 Box 的 draw（含录制 Picture + 垫玻璃）
    // **不会因子页面滚动自动重跑**（Compose 的绘制失效会停在滚动容器的图层边界），
    // 必须在滚动帧显式失效一次，玻璃后面的内容才会跟随滚动（负责人报「透背景固定」）。
    // 由嵌套滚动连接 + Coil 图片事件共同 bump（AppGraph.glassBackdropTick）；
    // 只在绘制阶段读 ⇒ 只触发重绘，不触发重组；滚动停止后自然不再刷新。
    // 悬浮岛玻璃参数（2026-10-19）：实验室调参实时写入 graph.barGlass ⇒ 这里读 State，
    // 从实验室返回主页立即是新值；「保存设定」后才落盘（重启仍生效）。
    val context = LocalContext.current
    val graph = com.live.finance.App.of(context).graph
    val scrollConnection = remember {
        object : NestedScrollConnection {
            // 滚动帧信号：preScroll 对所有嵌套滚动（LazyColumn/verticalScroll）逐帧派发，
            // 底栏可见期间每次滚动都让玻璃 backdrop 失效一次（下一帧重录最新背景）。
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (tabbarVisible.value) graph.bumpGlassBackdrop()
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (tabbarVisible.value) graph.bumpGlassBackdrop()
                // 实验室开关「是否因滚动而收起」关掉后：底栏始终固定，不参与滚动收起
                if (!graph.barGlass.value.collapseOnScroll) return Offset.Zero
                val dy = consumed.y
                if (dy < -6f) tabbarVisible.value = false // 向下浏览 → 收起
                else if (dy > 6f) tabbarVisible.value = true // 向上回退 → 弹起
                return Offset.Zero
            }
        }
    }
    // 开关由「开」改「关」时，若底栏正处于收起状态，立刻弹起（否则要等下一次上滑）
    LaunchedEffect(graph.barGlass.value.collapseOnScroll) {
        if (!graph.barGlass.value.collapseOnScroll) tabbarVisible.value = true
    }

    // ── 液态玻璃 backdrop ──────────────────────────────────────────────────
    // 离线自实现（不引 haze，P1-11 维持）：内容区逐帧录进 [AndroidPicture] →
    // 岛区域的扩边条带经 [RenderNode] + [RenderEffect] 做**真高斯模糊 + 提饱和** →
    // 圆角裁剪垫到岛下，再叠 tint / 镜面高光 / 内底暗边（2026-10-19 已删边缘折射环）。
    //
    // ⚠ 2026-10-18 修正：旧实现拿 `BlurMaskFilter` 当"模糊"，可那个 filter 只糊 **alpha 掩膜的边缘**，
    //   对不透明位图的内部像素毫无作用 ⇒ 内容原样清晰地透出来，只是条带边缘被抹软了一点。
    //   这正是此前反复被反馈「太过于透明」「不像毛玻璃」的根因。换成 RenderEffect 才是真 blur。
    //   API31+ 才启用（RenderEffect 要求）；低版本回退纯 tint 玻璃底，无 blur。
    val glassBlur = remember { Build.VERSION.SDK_INT >= 31 }
    val backdropPicture = remember { AndroidPicture() }
    val glassSpec = graph.barGlass.value
    val glassNodeBody = remember { if (Build.VERSION.SDK_INT >= 31) RenderNode("glassBody") else null }

    /**
     * 内容层（API29+）：每帧把页面内容画进这个**硬件** RenderNode，屏幕显示与玻璃取样共用同一份。
     *
     * ⚠ 为什么不再用「先 drawContent() 录进软件 Picture、再 drawContent() 画屏」：
     *   同一帧里把内容（含 `VanPullRefresh` 的 graphicsLayer 等子图层）画进软件 Picture 后，
     *   随后的那次 drawContent() 会拿到被首次绘制污染的图层快照 ⇒ 「从二级页返回首页后
     *   金额 0.00 / 卡数 0 张，下拉刷新也不刷新屏，切 Tab 重建组合才恢复」（2026-10-27 负责人报）。
     *   改为 RenderNode：内容只画一遍（硬件画布，子图层/阴影都保留），再整节点上屏。
     */
    val contentNode = remember { if (Build.VERSION.SDK_INT >= 29) RenderNode("mainContent") else null }
    // 新版独立底部 Tab 组件的玻璃材质节点（NewLiquidGlassTabBar 专用，与经典版分离）
    val newGlassNode = remember { if (Build.VERSION.SDK_INT >= 31) RenderNode("newGlassBackdrop") else null }

    val glassSaturation = remember { ColorMatrix() }
    val density = LocalDensity.current
    val newGlassShadowPaint = remember(density) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(40, 0, 0, 0)
            maskFilter = BlurMaskFilter(
                with(density) { 18.dp.toPx() },
                BlurMaskFilter.Blur.NORMAL,
            )
        }
    }
    // AGSL 折射 shader（API33+；33 以下走纯 blur 链）：SDF 边缘折射 + 色散 + Fresnel
    val newGlassShader = remember {
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
    // 系统「移除动画」（无障碍）开启时：玻璃退化为实底（无透明/模糊），保证可读性
    val reduceGlassTransparency = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val glassShadowPaint = remember(density) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(GLASS_SHADOW_ALPHA, 0, 0, 0)
            maskFilter = BlurMaskFilter(
                with(density) { GLASS_SHADOW_BLUR.toPx() },
                BlurMaskFilter.Blur.NORMAL,
            )
        }
    }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var islandRect by remember { mutableStateOf<Rect?>(null) }
    val tokens = LocalAppTokens.current
    // 底部 Tab 组件选择：true = 全新独立 Liquid Glass 组件（NewLiquidGlassTabBar），
    // false = 经典悬浮岛。玻璃参数 classic spec 仍给经典版用。
    val useNewTabBar = graph.useNewTabBar.value

    // transition: transform .3s ease, opacity .3s ease（岛的出现/收起动画在此外提，
    // backdrop 玻璃要与岛同步淡入淡出，必须共享同一份动画值）
    val islandOffsetY by animateDpAsState(if (tabbarVisible.value) 0.dp else 120.dp, tween(300), label = "islandOffsetY")
    val islandAlpha by animateFloatAsState(if (tabbarVisible.value) 1f else 0f, tween(300), label = "islandAlpha")

    // 选中胶囊位置：**提到 MainScreen**（原来在 FloatingIslandBar 内部）。
    // 胶囊透镜必须在父层、与玻璃同一次绘制里画（取样当帧 Picture），
    // 所以位置状态要放在两者共同的上方，bar 只负责画胶囊本体。
    val capsulePos = animateFloatAsState(
        targetValue = tab.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "islandCapsule",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage), // 岛所在的下方留白跟随页面底色
    ) {
        // ⚠⚠ **内容区必须是整屏高，不能给底部留 90dp**（2026-09-19 负责人真机反馈
        //   「底部岛收起来的时候，下面有一个大黑条」）。
        //
        //   曾经的错解：照抄 web `.main-body{ height: calc(100vh - 90px) }`，给内容区加
        //   `padding(bottom = 90.dp)`。但 web 那边**页面自己是满高的** ——
        //     `.page-home { min-height: 100vh }`、`.page-finance { height: 100vh }`、
        //     `.page-user { height: 100vh }`、`.page-diary { min-height: 100dvh }`
        //   ⇒ 页面**溢出** `.main-body` 那 90px，背景铺满整个视口，**根本不存在那条空带**。
        //   原生把 `padding` 加在内容区上，等于把整屏**压扁** 90dp：页面背景提前结束，
        //   下面 90dp 只剩 `bgPage`。岛显示时把它盖住看不出来，**岛一收起就露出整条**，
        //   深色主题下就是那条「大黑条」。
        //
        //   正确做法：内容 `fillMaxSize()` 铺满，岛用 `align(BottomCenter)` **绝对定位叠在上面**
        //   （web `.floating-island-nav{ position: fixed; bottom: 20px; z-index: 999 }`）。
        //   页面末尾该留多少让位给岛，由**各页面自己的底部内边距**决定（web 亦然：
        //   `.page-user{ padding-bottom: 30px }`、各页自己的收尾间距），**不要在这里统一压高度**。
        //
        //   ⚠ 液态玻璃录制必须挂在这个**内层内容 Box**（不含岛自身）——挂外层会把岛也录进
        //     backdrop 形成自反馈。玻璃状态（islandRect/islandAlpha）在 draw 阶段读取，
        //     岛移动/淡入淡出会自动触发本节点重绘，玻璃与岛逐帧同步。
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .onGloballyPositioned {
                    contentOrigin = it.boundsInRoot().topLeft
                    contentSize = it.size
                }
                // ⚠ 2026-10-27 追加146：原为 drawWithCache。drawWithCache 的绘制结果只在「缓存块里
                //   读到的 State / 尺寸」变化时失效；页面数据由子节点重组更新时它**不失效** ⇒
                //   「二级页返回首页后金额 0.00 / 卡数 0 张，下拉刷新也不刷新屏，切 Tab 重建组合才恢复」。
                //   drawWithContent 每帧参与子内容失效链（内容变即重绘）；玻璃从内容 RenderNode 取样。
                .drawWithContent {
                    val w = size.width.toInt().coerceAtLeast(1)
                    val h = size.height.toInt().coerceAtLeast(1)
                    // ⚠ 滚动帧信号：读取 tick 让本节点在滚动时失效重绘 ——
                    //   内容 Box 的 draw **不会因子页面滚动自动重跑**（绘制失效停在
                    //   滚动容器的图层边界），不读它的话玻璃后面永远是旧帧。
                    //   岛不可见（还没量到位置 / 已收起）时**完全不录内容节点** ——
                    //   录一次等于把整屏内容重放一遍，岛收起时这笔开销纯属白花；
                    //   而「向下浏览」正是岛收起、帧数最多的场景。低版本同理直接画。
                    // ⚠ 在绘制阶段读 State：滚动帧/图片加载事件 bump 时只触发重绘，不触发重组
                    graph.glassBackdropTick.value
                        val rect = islandRect
                        if (!glassBlur || rect == null || islandAlpha <= 0.01f || contentNode == null) {
                            drawContent()
                            return@drawWithContent
                        }
                        // 1) 内容只画一遍：录进硬件 RenderNode（内部 graphicsLayer/阴影均保留）
                        contentNode.setPosition(0, 0, w, h)
                        val rec = contentNode.beginRecording(w, h)
                        val prev = drawContext.canvas
                        try {
                            drawContext.canvas = ComposeCanvas(rec)
                            drawContent()
                        } finally {
                            drawContext.canvas = prev
                            contentNode.endRecording()
                        }
                        // 2) 整节点上屏（屏幕与玻璃取样是同一份内容，永远一致）
                        drawIntoCanvas { it.nativeCanvas.drawRenderNode(contentNode) }
                        // 3) 岛下垫液态玻璃（取样内容节点）。
                        //    新版独立组件：renderNewGlassBackdrop；经典组件：renderLiquidGlass。
                        val islandArea = Rect(
                            rect.left - contentOrigin.x, rect.top - contentOrigin.y,
                            rect.right - contentOrigin.x, rect.bottom - contentOrigin.y,
                        )
                        if (useNewTabBar) {
                            if (!reduceGlassTransparency) {
                                renderNewGlassBackdrop(
                                    picture = backdropPicture,
                                    contentNode = contentNode,
                                    area = islandArea,
                                    canvasW = w,
                                    canvasH = h,
                                    node = newGlassNode,
                                    agsl = newGlassShader,
                                    shadowPaint = newGlassShadowPaint,
                                    dark = tokens.isDark,
                                    appear = islandAlpha,
                                )
                            }
                        } else {
                            renderLiquidGlass(
                                picture = backdropPicture,
                                contentNode = contentNode,
                                area = islandArea,
                                canvasW = w,
                                canvasH = h,
                                bodyNode = glassNodeBody,
                                saturation = glassSaturation,
                                shadowPaint = glassShadowPaint,
                                spec = glassSpec,
                                tint = Color.White.copy(
                                    alpha = (if (tokens.isDark) glassSpec.alphaDark else glassSpec.alphaLight) * islandAlpha,
                                ),
                                appear = islandAlpha,
                            )
                        }
                        // 4) 胶囊透镜已删除（2026-10-19 负责人「不做透视了，效果极差」）——
                        //    改为在 FloatingIslandBar 里放**选中项的图标+文字**（真放大镜）。
                },
        ) {
            when (tab) {
                0 -> HomeScreen(nav)
                1 -> FinanceScreen(nav)
                2 -> DiaryListScreen(nav)
                else -> UserScreen(nav)
            }
        }

        if (useNewTabBar) {
            // 全新独立底部 Tab 组件：只画胶囊/图标/边框；玻璃材质由上面的内容绘制链路垫出
            // （系统减弱动效开启时走实底，无透明/模糊）
            NewLiquidGlassTabBar(
                active = tab,
                onSelect = { tab = it },
                visible = tabbarVisible.value,
                solidBackground = reduceGlassTransparency,
                spec = graph.newBarGlass.value,
                // ⚠ 放大镜不再采样页面背景（137/138 两版均翻车：暗色空页出黑框/接缝），
                //   页面只透过玻璃材质显示；胶囊透镜是 AGSL 连续变倍，组件内部自足
                onPositioned = { islandRect = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            )
        } else {
            FloatingIslandBar(
                active = tab,
                onSelect = { tab = it },
                offsetY = islandOffsetY,
                alpha = islandAlpha,
                spec = glassSpec,
                capsulePos = capsulePos,
                onPositioned = { islandRect = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            )
        }

        HomeExitDialog(show = showExitDialog, onClose = { showExitDialog = false })
    }
}

/**
 * 在 [area]（内容区局部坐标）上画一块 Apple 式液态玻璃。
 *
 * 参数全部来自 [BarGlassSpec]（2026-10-19 起由实验室实时下发，不再是写死常量）：
 *  ① **主体磨砂**：backdrop 条带经 `RenderEffect` 真高斯模糊（[BarGlassSpec.blurDp]）
 *     + 提饱和（[BarGlassSpec.saturation]）；
 *  ② **磨砂体 tint**：白 α（[BarGlassSpec.alphaLight] / [BarGlassSpec.alphaDark]，调用方按模式算好传入）；
 *  ③ **折射环（可选）**：[BarGlassSpec.rimWidthDp] > 0 且 [BarGlassSpec.rimStrength] > 0 时，
 *     把同一份 backdrop 绕中心放大 [BarGlassSpec.rimScale]，经 `saveLayerAlpha(rimStrength)`
 *     叠在环带上，再压 [BarGlassSpec.rimTintRatio] 倍 tint；环用独立 [rimNode] 录制 ⇒
 *     [BarGlassSpec.rimBlurScale] 可单独调模糊。
 *
 * [appear] 与岛的出现/收起动画共用同一份 alpha，玻璃不会比岛先到或后走。
 * API<31 / 非硬件加速时降级为纯 tint（无 blur、无环），布局与可读性不受影响。
 */
private fun DrawScope.renderLiquidGlass(
    picture: AndroidPicture,
    contentNode: RenderNode? = null,
    area: Rect,
    canvasW: Int,
    canvasH: Int,
    bodyNode: RenderNode?,
    saturation: ColorMatrix,
    shadowPaint: AndroidPaint,
    spec: BarGlassSpec,
    tint: Color,
    appear: Float,
) {
    val radiusPx = ISLAND_SHAPE_D.toPx()
    val outline = Path().apply { addRoundRect(RoundRect(area, CornerRadius(radiusPx))) }

    // ⓪ 外投影：画在玻璃之前、且在裁剪之外（见 GLASS_SHADOW_BLUR 注释）
    shadowPaint.alpha = (GLASS_SHADOW_ALPHA * appear).toInt()
    drawIntoCanvas { nc ->
        val native = nc.nativeCanvas
        native.save()
        native.translate(0f, GLASS_SHADOW_DY.toPx())
        native.drawRoundRect(area.left, area.top, area.right, area.bottom, radiusPx, radiusPx, shadowPaint)
        native.restore()
    }

    // 采样条带：比岛大出 2× 模糊半径，避免模糊后边缘出现"吸边"的透明渐隐
    val blurPx = spec.blurDp.dp.toPx()
    val expand = maxOf(blurPx * 2f, 8.dp.toPx())
    val src = AndroidRectF(
        (area.left - expand).coerceAtLeast(0f),
        (area.top - expand).coerceAtLeast(0f),
        (area.right + expand).coerceAtMost(canvasW.toFloat()),
        (area.bottom + expand).coerceAtMost(canvasH.toFloat()),
    )

    val hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
    // ⚠ 用局部 val：`canBlur` 是布尔量，不会把可空 node 智能转换成非空
    val blurNode = bodyNode
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hardware && blurNode != null && blurPx > 0.01f

    clipPath(outline) {
        if (canBlur && blurNode != null) {
            // ① 主体：真模糊 + 提饱和（优先从内容 RenderNode 取样，其次才是旧 Picture）
            if (contentNode != null) {
                blurNode.recordBackdropFromNode(contentNode, src, canvasW, canvasH, blurPx, spec.saturation, saturation)
            } else {
                blurNode.recordBackdrop(picture, src, canvasW, canvasH, blurPx, spec.saturation, saturation)
            }
            drawIntoCanvas { it.nativeCanvas.drawRenderNode(blurNode) }
        }
        // ② tint 整块盖满圆角轮廓
        drawRect(tint, topLeft = area.topLeft, size = area.size)

        // ③ **不做透视/折射**（2026-10-19 负责人「算了 不做透视了，效果极差」）：
        //    放大镜改为放大**选中项的图标与文字**，在 FloatingIslandBar 里用 scale 实现。
    }
}

/**
 * 玻璃的「表面处理」，拆成**两层**，分别挂在岛的两条修饰符上：
 *
 *  - [drawGlassLight]（受光面）→ `Modifier.drawBehind`：**必须压在图标/文字之下**。
 *    ⚠ 2026-10-18 踩过：一开始三层全塞进 `drawWithContent { drawContent(); … }`，
 *    而 `drawContent()` 已经把孩子（图标/文字）画完了 ⇒ 那层白色渐隐直接盖在图标上，把文字洗淡。
 *    ⚠ 2026-10-19 负责人「受光 改为四周而不是上」⇒ 改为**沿外缘整圈**的受光描边。
 *  - [drawGlassEdge]（镜面高光 + 内底暗边）→ `Modifier.drawWithContent`：这两笔只落在
 *    外缘 1dp 的环带上，不碰内容，而且**必须盖在描边之上**才有用。
 *
 * 为什么不放回父级 [renderLiquidGlass] 一起画：玻璃材质由父级内容 Box 垫在岛**下面**，
 * 而岛的 1dp 描边（`colors.tabbarGlassBorder`）是子节点、后画 ⇒ 画在父级的高光会被描边盖住。
 *
 * 透明度不在这里乘 —— 岛整体已经挂了 `Modifier.alpha`，出现/收起时自动跟着淡。
 */
private fun DrawScope.drawGlassLight(highlight: Float) {
    if (highlight <= 0.001f) return
    // 受光 = **四周**：沿外缘整圈描一圈白（不再只有上缘）
    drawRoundRect(
        color = Color.White.copy(alpha = highlight.coerceIn(0f, 0.5f)),
        cornerRadius = CornerRadius(ISLAND_SHAPE_D.toPx()),
        style = Stroke(width = 1.5.dp.toPx()),
    )
}


/**
 * 选中胶囊：画在**图标之下**（和 [drawGlassLight] 同一个 `drawBehind`），
 * 水平位置由 [pos]（0..count-1 的浮点）驱动 —— 调用方传动画值进来，切 Tab 时就滑过去。
 *
 * 位置口径与 [FloatingIslandBar] 的 `padding(horizontal = 10.dp)` + `weight(1f)` 严格对齐：
 * 格子宽 = (行宽 − 2×10dp) / count，胶囊在格子里再左右各缩 [CAPSULE_INSET_X]。
 * ⚠ 改 `padding` 或条目数必须同步这里，否则胶囊会跟格子错位。
 */
private fun DrawScope.drawSelectionCapsule(
    pos: Float,
    count: Int,
    isDark: Boolean,
    border: Color,
    capsuleLight: Float,
    capsuleDark: Float,
    /** 受光白 α（BG 的「上缘受光」滑块；胶囊的受光描边与它联动）。 */
    highlight: Float = 0f,
) {
    if (count <= 0) return
    val padPx = 10.dp.toPx()
    val slot = (size.width - padPx * 2f) / count
    val insetX = CAPSULE_INSET_X.toPx()
    val insetY = CAPSULE_INSET_Y.toPx()
    val w = slot - insetX * 2f
    val h = size.height - insetY * 2f
    if (w <= 0f || h <= 0f) return

    val left = padPx + pos * slot + insetX
    val top = insetY
    val radius = h / 2f
    val corner = CornerRadius(radius)
    val topLeft = Offset(left, top)

    // 胶囊本体：比磨砂体更白 ⇒ 凸起感（透镜已下线，不再让位）
    drawRoundRect(
        color = Color.White.copy(alpha = if (isDark) capsuleDark else capsuleLight),
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = corner,
    )
    // 受光描边：**四周**（2026-10-19 负责人「受光只作用在BG 胶囊没有作用到」⇒ 与 BG 的
    // 「上缘受光」滑块联动；保底 0.30 保证深色模式下胶囊始终读得出形状）
    drawRoundRect(
        color = Color.White.copy(alpha = (highlight * 2.5f).coerceIn(0.30f, 0.85f)),
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    // ⚠ 定形描边：白胶囊压在白磨砂体上**几乎看不出边界**（2026-10-19 实测确认），
    //   靠这一圈 1dp 的 `tabbarGlassBorder` 才读得出"一个圆角形状"，别删。
    drawRoundRect(
        color = border,
        topLeft = topLeft,
        size = Size(w, h),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    // 内底暗边：一道极淡的暗，给出厚度
    val inset = 1.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (isDark) 0.10f else 0.06f),
            startY = top,
            endY = top + h,
        ),
        topLeft = Offset(left + inset, top + inset),
        size = Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
        style = Stroke(width = inset),
    )
}

private fun DrawScope.drawGlassEdge(specular: Float) {
    val radiusPx = ISLAND_SHAPE_D.toPx()
    val w = size.width
    val h = size.height

    // ⑤ 镜面高光：沿外缘 1.2dp 描边，垂直渐变 ⇒ 只有上缘亮（内缩半个描边宽，避免被裁掉一半）
    //    specular 来自 BarGlassSpec（实验室「镜面描边」滑块，0 = 关闭）
    val specInset = GLASS_SPECULAR_W.toPx() / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = specular.coerceIn(0f, 1f)),
            0.32f to Color.Transparent,
            startY = 0f,
            endY = h,
        ),
        topLeft = Offset(specInset, specInset),
        size = Size(w - specInset * 2, h - specInset * 2),
        cornerRadius = CornerRadius((radiusPx - specInset).coerceAtLeast(0f)),
        style = Stroke(width = GLASS_SPECULAR_W.toPx()),
    )

    // ⑥ 内底暗边：给出玻璃的"厚度"，否则像一张贴纸
    val inset = GLASS_INNER_SHADOW_W.toPx()
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
        style = Stroke(width = inset),
    )
}

/**
 * 把 [picture] 里 [src] 这块区域录进本 [RenderNode]，并挂上「高斯模糊 → 提饱和」的渲染效果。
 *
 * ⚠ 位置与录制的偏移必须配套：`setPosition` 用**绝对**画布坐标（src 左上角），
 *   录制时再 `translate(-src.left, -src.top)` 把 src 对齐到节点原点 —— 少任一步都会错位。
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun RenderNode.recordBackdrop(
    picture: AndroidPicture,
    src: AndroidRectF,
    canvasW: Int,
    canvasH: Int,
    radiusPx: Float,
    saturate: Float,
    saturation: ColorMatrix,
) {    val left = src.left.toInt()
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

/**
 * 同 [recordBackdrop]，但取样源是**内容 RenderNode**（2026-10-27）：
 * 内容只画一遍进节点，屏幕与玻璃共用，避免软件 Picture 二次绘制污染子图层。
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun RenderNode.recordBackdropFromNode(
    content: RenderNode,
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
    rc.drawRenderNode(content)
    endRecording()
}


/**
 * 悬浮玻璃岛（web `.floating-island-nav`，`web/src/layout/MainLayout.vue:229-262`）。
 *
 * 玻璃拟态口径（2026-10-18 第五十七轮定稿 + 同日「不要黑色和颗粒」「要 Apple 液态玻璃」修订，
 * 负责人拍板原文：「我要的和web一模一样 不允许跟随主题变化 任何UI都是毛玻璃」「苹果的那种」
 * 「主题UI和bar无关联 不允许匹配 永远保持毛玻璃」「透明度你给我把握好啊」
 * 「我要的是 毛玻璃bar不要黑色和颗粒」「我要Apple那样的效果能做到么？ 液态玻璃？」）：
 *  1. **玻璃三色只跟深浅模式、不跟主题预设**（web `useUiTheme.js` 的 `TABBAR` 同款思路）：
 *     浅 = 白 α0.20；深 = **白 α0.08**（深色玻璃 tint 弃用 web 的近黑 `rgba(18,18,22,.55)`，
 *     负责人明确「不要黑色」）。描边与文字色仍走主题 token（浅黑 6% / 深白 12%、
 *     `#7d7e80` / `#b0b0b0`，只跟模式）。
 *     ⇒ 同模式内切预设 bar 完全不变（「主题 UI 和 bar 无关联」的结构性保证）。
 *  2. **真 backdrop 液态玻璃**（API31+）：内容区逐帧录 Picture → 岛区域扩边条带经
 *     `RenderEffect` 真高斯模糊 + 提饱和 → 圆角裁剪垫到岛下，再叠镜面高光 / 内底暗边
 *     （全部在 [renderLiquidGlass] 里，离线自实现、不引 haze，P1-11 维持）。
 *     ⚠ 折射环的去留与参数**全部由实验室 [BarGlassSpec] 下发**（renderLiquidGlass ③）：
 *     2026-10-19 曾按「我不想要这个环」删除，同日实验室定版 B 预设又带回
 *     8dp×1.14 环（rimWidthDp/rimScale/rimStrength 非 0 时即画，见 BarGlassSpec 默认值）。
 *     曾用噪点颗粒近似磨砂，「不要颗粒」⇒ 已删除；也曾用 `BlurMaskFilter` 当模糊，
 *     但它只糊 alpha 边缘、糊不了内容，2026-10-18 已换成 RenderEffect。
 *     **API<31 回退**：无 blur，Row 自绘半透玻璃底 + 上缘高光（本函数 else 分支）。
 *  3. blur 模式下 Row 背景透明，只保留描边/内容 —— 玻璃与投影都由内容区垫出，
 *     出现/收起动画（offsetY/alpha）由 MainScreen 外提传入并与玻璃同步。
 *  4. 选中色仍走 `colors.primary`（web `.router-link-active` 同口径；毛玻璃本身与主题预设无关）。
 *  5. **选中胶囊**：选中项背后垫一颗小圆角玻璃（[drawSelectionCapsule]），切 Tab 时走 spring
 *     从旧位置滑到新位置、带轻微回弹。浅色比磨砂体更白（0.65 > 0.20）⇒ 凸起感；
 *     深色只加 0.02 白。**不跟主题预设**，与玻璃本体同口径。
 *
 * 沿革留痕：2026-09-15 拍板不做真模糊 → 2026-10-18 「太过于透明」→「灰黑色 bg 不是毛玻璃」
 * （第五十七轮）→「不要黑色和颗粒」（白玻璃 + 高光）→ α0.18 被反馈「这个色？」提档 0.30 →
 * 「要 Apple 液态玻璃」⇒ 真 backdrop blur（API31+）+ 白 tint/高光，无颗粒无黑 →
 * 2026-10-19「我不想要这个环」⇒ 删除 6dp 折射环，三层变两层（玻璃 + 选中胶囊）→
 * 同日「降低磨砂雾蒙蒙，改成环的透明度」⇒ 白 α 浅 0.58→0.49 / 深 0.30→0.25 →
 * 2026-10-19 实验室实测选定：浅 0.20 / 深 0.08、模糊 2dp、饱和 1.09、受光 0、镜面 0、
 * 胶囊 浅0.65/深0.02、环 8dp×1.14·tint0.85·强度1（B 预设带回环，全部参数由实验室下发）。
 */
@Composable
private fun FloatingIslandBar(
    active: Int,
    onSelect: (Int) -> Unit,
    offsetY: Dp,
    alpha: Float,
    spec: BarGlassSpec,
    /** 选中胶囊位置（0..3 浮点，spring 动画；由 MainScreen 持有，透镜与胶囊共用同一个值）。 */
    capsulePos: State<Float>,
    onPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val shape = RoundedCornerShape(ISLAND_SHAPE_D)
    val glassBlur = remember { Build.VERSION.SDK_INT >= 31 }

    // 回退玻璃底（API<31 无真 blur）：**只跟深浅模式**，深浅两套都是白色玻璃
    val fallbackGlass = if (!glassBlur) {
        Color.White.copy(alpha = if (tokens.isDark) spec.alphaDark else spec.alphaLight)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .offset(y = offsetY)
            .alpha(alpha)
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            // ⚠ blur 模式下投影必须由玻璃层自己画（见 GLASS_SHADOW_BLUR 注释）：
            //   岛背景透明，再挂 Modifier.shadow 会让阴影从内部透出来，把玻璃压灰。
            .then(
                if (glassBlur) {
                    Modifier
                } else {
                    Modifier.shadow(8.dp, shape, clip = false, ambientColor = ISLAND_SHADOW, spotColor = ISLAND_SHADOW)
                },
            )
            .clip(shape)
            .then(
                if (glassBlur) {
                    // 玻璃底由内容区 backdrop 真模糊垫出（见 MainScreen drawWithCache）
                    Modifier
                } else {
                    Modifier.background(fallbackGlass) // 受光面/高光统一由下面的 drawGlassLight 画
                },
            )
            // 上缘受光面 + 选中胶囊：都压在图标/文字之下（见 drawGlassLight 注释）
            .drawBehind {
                drawGlassLight(spec.highlight)
                // 选中胶囊本体 + 高光/描边（放大镜效果由每个 tab 自己的 scale 实现）
                // 只读 State 的 value ⇒ 只让绘制失效，不触发重组
                drawSelectionCapsule(
                    capsulePos.value,
                    ISLAND_TABS.size,
                    tokens.isDark,
                    colors.tabbarGlassBorder,
                    spec.capsuleLight,
                    spec.capsuleDark,
                    spec.highlight,
                )
            }
            .border(1.dp, colors.tabbarGlassBorder, shape)
            // 镜面高光 + 内底暗边：只落在边缘环带上，且必须盖在描边之上
            .drawWithContent {
                drawContent()
                drawGlassEdge(spec.specular)
            }
            .padding(horizontal = 10.dp), // padding: 0 10px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ISLAND_TABS.forEachIndexed { index, (icon, title) ->
            val isActive = index == active
            // transition: color .2s ease（未选中 --tabbar-text，选中 --theme-primary）
            val color by animateColorAsState(
                if (isActive) colors.primary else colors.tabbarText,
                tween(200),
                label = "islandTabColor$index",
            )
            // 放大镜 = **选中项的图标+文字整体放大**（2026-10-19 负责人：「不做透视了，
            // 做放大镜就好，放大 ICON 和文字」；放大不碰背景，所以没有透视那种错位感）。
            val itemScale by animateFloatAsState(
                targetValue = if (isActive) TAB_SELECTED_SCALE else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                label = "islandTabScale$index",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f) // flex: 1
                    .fillMaxHeight()
                    .clickable(
                        enabled = alpha > 0.9f, // web `.nav-hidden{ pointer-events:none }`：收起时不可点
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    // 缩放放在 clickable 之后：点按热区仍是整格，只有图标/文字被放大
                    .scale(itemScale),
            ) {
                VanIcon(
                    name = icon,
                    size = 22.sp, // .nav-icon { font-size: 22px }
                    color = color,
                )
                Spacer(Modifier.height(2.dp)) // .nav-icon { margin-bottom: 2px }
                BasicText(
                    title,
                    style = TextStyle(
                        color = color,
                        fontSize = scaledSp(12f), // .nav-text { font-size: 11px }
                        fontWeight = FontWeight.Medium, // font-weight: 500（行高留默认，对齐 web line-height: normal）
                    ),
                )
            }
        }
    }
}

