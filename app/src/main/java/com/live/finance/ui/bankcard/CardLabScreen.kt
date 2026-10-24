package com.live.finance.ui.bankcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.data.model.Card
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.VanToastType
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.icon.VanIcon
import com.live.vant.theme.MotionTokens
import com.live.vant.theme.vantDimens
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 银行卡管理（原生，2026-10-22 起正式取代离线 WebView 卡片页）。
 *
 * 本页原名「卡片模组实验室」，是离线 WebView 版卡片管理的原生 Compose 重写；
 * 2026-10-22 起 `Routes.CARD / CARD_DEBIT / CARD_CREDIT` 由 `CardPageHost` 按
 * 「卡方实验室」保存的形态选择本页或 `CardWalletLabScreen`；本页同时是该实验室的
 * 「卡片模组」实时预览。不再使用 WebView。
 *
 * ## 沿革
 * 2026-10-11 卡片页从原生 `CardManageScreen` 改离线 WebView 的根因是**旧原生实现的弹卡动画
 * 与点击位置割裂**（浮层凭空出现，只有 scale/alpha）。本页用**原生重做同一套交互**验证回迁可行性，
 * 现已转正：
 *
 * | WebView 版（CardStack.vue） | 本页原生等价 |
 * |---|---|
 * | FLIP（getBoundingClientRect + invert + transition） | `Animatable` progress + `graphicsLayer` 插值（零重组、纯 GPU 合成） |
 * | `.is-selected{top:24;scale:1.05}` | 飞行层 translationY/scale 按 progress 插值，起点=被点卡的实时 `boundsInRoot` |
 * | `.is-dimmed{opacity .6; translateY 10px; scale .95}`（filter 已被 webview 掐掉） | graphicsLayer alpha/scale/translationY（不叠黑罩：遮罩 0.65 盖住后亮度差不可见） |
 * | backdrop-filter 遮罩（已因闪烁掐掉） | 纯色遮罩 alpha 插值，无合成层重建风险 |
 * | vuedraggable 拖拽排序 | `detectDragGestures` 按住即拖 + `key(id)` 防串位（同旧原生 `CardSortList` 的两处修正） |
 * | van-pull-refresh | `VanPullRefresh`（vant-ui 自绘） |
 *
 * ## 操作逻辑逐条对齐 WebView 版（CardOffline / CardListPane / CardStack）
 * 1. 借记/信用分段切换，**各 tab 保留卡片列表**（WebView 用 v-show；这里 per-tab 缓存 + 独立 scrollState）
 * 2. 预览脱敏（bin 前 3 位保留 + 伪随机数字，稳定 seed）
 * 3. 排序模式（按住把手即拖，拖完一次性提交 `PUT /card/sort`）
 * 4. 点击卡片 → **从实际位置飞到顶部**（FLIP 语义）；再点卡/点遮罩/点关闭 → 飞回原槽位
 * 5. 弹出态：右上「管理」→ 编辑；下方「查看消费明细」（借记卡）→ 卡收支报表；底部毛玻璃关闭钮
 * 6. 下拉刷新：强制重新取数（绕过节流）
 * 7. 取数节流：**页面实例级** 10s 窗口、按 tab 分键（对齐原 WebView 版 CardWebThrottle，
 *    语义同 web `debit.vue` 的 `REFRESH_INTERVAL`；失败也记时间戳，防止反复重试打爆）
 *
 * ## 数据（与原 WebView 版同一套仓库出口）
 * `graph.category.list("bank")`（60s 进程内缓存）+ `graph.card.list(tab)` +
 * `graph.flow.cardsFlowStats(6)`（借记卡批量算「近6个月无动账」）+ `graph.card.updateSortBatch`。
 */
@Composable
fun CardLabScreen(nav: NavHostController, initialTab: String = "debit") {
    val context = LocalContext.current
    val graph = App.of(context).graph
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val toast = LocalVanToastController.current
    val density = LocalDensity.current

    // ===== tab / 模式 =====
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(if (initialTab == "credit") "credit" else "debit")
    }
    val isDebit = tab == "debit"
    var sortMode by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var scrolled by remember { mutableStateOf(false) }

    // ===== per-tab 数据缓存（WebView v-show 的等价物）=====
    val cache = remember { mutableStateMapOf("debit" to emptyList<Card>(), "credit" to emptyList<Card>()) }
    val loaded = remember { mutableStateMapOf("debit" to false, "credit" to false) }
    val debitCards: List<Card> = cache["debit"].orEmpty()
    val creditCards: List<Card> = cache["credit"].orEmpty()
    val cards: List<Card> = if (isDebit) debitCards else creditCards
    var bankIconById by remember { mutableStateOf(mapOf<String, String>()) }
    /** `category.list("bank")` 原表：卡组织 logo 按名称匹配（web `getCardOrgInfo`）。 */
    var orgBanks by remember { mutableStateOf<List<com.live.finance.data.model.Category>>(emptyList()) }
    /** 近6个月无动账的卡 id（借记卡才有；原生批量算一次，对齐原 WebView 版） */
    var idleIds by remember { mutableStateOf(emptySet<String>()) }

    // per-tab 滚动位置（切 tab 不丢，等价 web 两个 keep-alive 子页各自 scrollTop）
    val debitScroll = rememberScrollState()
    val creditScroll = rememberScrollState()
    val scrollState = if (isDebit) debitScroll else creditScroll

    // ===== 下拉刷新 =====
    val refreshState = rememberVanPullRefreshState()

    // ===== 取数节流（页面实例级，对齐 CardWebThrottle；失败也记账防重试风暴）=====
    // ⚠ ON_RESUME 计数 + 节流随计数换新实例：Compose 版被压栈时 composable 常驻组合
    //   （NavHost 2.7 为预测性返回保留回栈条目），LaunchedEffect(tab) 不会因「从添加/
    //   编辑页返回」而重触发 ⇒ 必须靠生命周期计数主动重启取数（对齐原 WebView 版：
    //   WebView 版返回即重建页面、窗口随之重置；2026-10-17「返回后列表变空」教训）。
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val throttle = remember(resumeTick) { LabThrottle() }

    suspend fun fetch(tabArg: String, force: Boolean) {
        val t = if (tabArg == "credit") "credit" else "debit"
        if (!throttle.take(t, force)) return
        // banks 与 cards 并行（旧串行 = 两个 RTT 叠加，credit 首载白屏期肉眼可见）
        val (banks, cardsResult) = coroutineScope {
            val b = async {
                when (val r = graph.category.list("bank")) {
                    is ApiResult.Ok -> r.data.orEmpty()
                    else -> emptyList()
                }
            }
            val l = async { graph.card.list(t) }
            b.await() to l.await()
        }
        // ⚠ 判等写入（2026-10-20 切 tab 卡顿根治之一）：fetch 回来那一帧，写新 map 实例
        //   会让**两个 pane** 的全部卡重组（bankIconOf lambda 捕获此 map）。banks 命中
        //   仓库 60s 缓存时内容不变 ⇒ 判等跳写 ⇒ 已就绪的那个 pane 完全不碰。
        val newIconMap = banks.filter { it.iconUrl.isNotBlank() }
            .associate { it.id to AppConfig.fullFileUrl(it.iconUrl) }
        if (newIconMap != bankIconById) bankIconById = newIconMap
        if (banks != orgBanks) orgBanks = banks
        val list = when (val r = cardsResult) {
            is ApiResult.Ok -> r.data.orEmpty()
            is ApiResult.Fail -> {
                // 失败也要落 loaded（否则首屏永远转圈），且不拿空列表覆盖旧缓存
                loaded[t] = true
                toast.show(r.message.ifBlank { "卡片加载失败" }, VanToastType.Fail)
                return
            }
            else -> {
                loaded[t] = true
                toast.show("卡片加载失败", VanToastType.Fail)
                return
            }
        }
        cache[t] = list
        loaded[t] = true
        if (t == "debit" && list.isNotEmpty()) {
            val stats: Map<String, Pair<Int, Int>> = when (val r = graph.flow.cardsFlowStats(6)) {
                is ApiResult.Ok -> r.data.orEmpty()
                else -> emptyMap()
            }
            val newIdle = list.filter { c ->
                stats[c.id]?.let { (expense, income) -> expense == 0 && income == 0 } == true
            }.map { it.id }.toSet()
            if (newIdle != idleIds) idleIds = newIdle
        }
    }

    // 进页面 / 切 tab：缓存为空必发；resumeTick 变化（返回本页/切回前台）也必发。
    // ⚠ 不能写 `resumeTick > 0`：ON_RESUME 后该值永久 >0，每次切 tab 都会强制重取
    //   ⇒ 新列表/map 实例 ⇒ 切换组合后紧接着第二轮全卡重组（切 tab 卡顿次因）。
    //   记 lastResumeTick 区分「真 resume」与「仅切 tab」：后者命中缓存即不取。
    var lastResumeTick by remember { mutableStateOf(resumeTick) }
    LaunchedEffect(tab, resumeTick) {
        val resumed = resumeTick != lastResumeTick
        lastResumeTick = resumeTick
        if (cache[tab].orEmpty().isEmpty() || resumed) fetch(tab, false)
    }

    // ===== 预览脱敏（bin 前 3 位 + 伪随机，seed 稳定到本轮开关）=====
    // 双 tab 各一份脱敏副本：两 pane 常驻组合（v-show 语义），切 tab 不重建任何卡面
    var previewSeed by remember { mutableStateOf(0L) }
    LaunchedEffect(previewMode, debitCards, creditCards) { if (previewMode) previewSeed = System.currentTimeMillis() }
    fun mask(list: List<Card>): List<Card> = list.map { c ->
        c.copy(
            cardBin = c.cardBin.take(3) + pseudoDigits((c.cardBin.length - 3).coerceAtLeast(3), previewSeed),
            last4 = pseudoDigits(4, previewSeed + 7),
        )
    }
    val displayDebit = remember(debitCards, previewMode, previewSeed) { if (previewMode) mask(debitCards) else debitCards }
    val displayCredit = remember(creditCards, previewMode, previewSeed) { if (previewMode) mask(creditCards) else creditCards }
    val curDisplay = if (isDebit) displayDebit else displayCredit

    // ===== 滚动方向 → 顶栏/底部按钮收起（4px 死区，对齐 web；snapshotFlow 防每帧重启协程）=====
    LaunchedEffect(scrollState) {
        // ⚠ last 必须初值化为当前滚动值：切 tab 换 scrollState 实例、effect 重启，
        //   若 last=0 而新 tab 已恢复滚动位（如 200px），首 emission 的 d=200>4 会
        //   误置 scrolled=true ⇒ 顶栏收起/添加钮隐藏/Spacer 140→24dp 跳变（卡顿主因）。
        var last = scrollState.value.toFloat()
        snapshotFlow { scrollState.value }.collect { v ->
            val d = v - last
            if (d > 4f) scrolled = true else if (d < -4f) scrolled = false
            last = v.toFloat()
        }
    }

    // ===== 弹卡飞行（本页灵魂：原生版 FLIP）=====
    // flight：0 = 堆叠槽位，1 = 顶部弹出位。飞卡/dim/阴影/提亮都由它插值，
    // 且全部读在 graphicsLayer / draw lambda 里 —— 动画期间零重组、零重布局，纯 RenderNode 属性更新。
    // 曲线逐帧对齐 web `CardStack.vue`：
    //   出场 380ms cubic-bezier(.4,0,.2,1)（`.is-selected` 的 top/transform 过渡）
    //   收回 600ms cubic-bezier(.34,1.56,.64,1)（基础态 transform 的弹性 back-out：冲过槽位再落回）
    // ⚠ 初值必须是 0：若为 1，静止态（无选中）LabCardStack 的 dim 分支（flight>0 且 !isSel）
    //   会把全堆卡打成 alpha.6/scale.95 还整体下移 10dp —— 页面一打开整堆发暗。
    val flight = remember { Animatable(0f) }
    // 遮罩与 flight 解耦：web `.stack-backdrop` 本就是独立 opacity .4s 过渡；
    // 且收回曲线会 undershoot 到负值，共用一个进度会把遮罩 alpha 打成负值/闪烁。
    val backdrop = remember { Animatable(0f) }
    var selId by remember { mutableStateOf<String?>(null) }
    // 收回中的卡 id：收回 = **堆内卡自己飞回**（web 同构），浮层在 close() 瞬间让位
    var closingId by remember { mutableStateOf<String?>(null) }
    // lastSelCard 只剩一个用途：收回的 300ms 里给「查看明细」渐隐期提供 id 引用
    var lastSelCard by remember { mutableStateOf<Card?>(null) }
    // 飞行起点位移（相对飞行层布局位置；点击时 = 被点卡 root top − 飞行层 root top）
    var startOffsetPx by remember { mutableStateOf(0f) }
    val endTopPx = with(density) { 24.dp.toPx() }
    // 内容区 root 锚点：浮层与内容 Column 同在 ScreenScaffold 的 systemBarsPadding 内，
    // 飞行层 root top = contentTopPx + 24dp。⚠ 不能在浮层自身上量：浮层在选中时才组合，
    // 首次点击时还没有坐标；内容 Column 常驻 ⇒ 首帧即有真值
    var contentTopPx by remember { mutableStateOf(0f) }
    // ===== 开卡接力（2026-10-21 第四轮）：与卡包页收回接力同构 =====
    // 旧版一选中就把卡抬到浮层（z 序全列表最高），第一帧在槽位就是「整卡盖在列表上」
    // ——真机读作「先在卡列表里完整展示，再突兀弹起」。改为：
    //   ① 前段由**堆内那张卡**按自身 z 序抬升（仍被下方卡遮挡，真正从层里出来）；
    //   ② 到交接点（列表区顶边 / 30% 进度取小者）后，堆内卡与浮层卡在**同一几何**上
    //      交叉淡入（80ms），浮层卡继续飞到顶部。
    // handoffV 由点击卡槽位与列表区顶边实时算，交接时堆内卡正好还没被 pane 顶裁到。
    val openReveal = remember { Animatable(0f) }
    var handoffV by remember { mutableStateOf(0.3f) }
    var inStackOpen by remember { mutableStateOf(false) }
    var openSeq by remember { mutableStateOf(0) }
    var paneTopPx by remember { mutableStateOf(0f) }
    // vantDimens 是 @Composable getter：select/close 的协程里读不到，先在组合期取出
    val flyOutMs = vantDimens.durationSlow
    // 每张卡的实时 root 坐标（点击时取起点；选中后列表锁滚动 ⇒ 坐标稳定）。
    // 非 State 容器：只在手势回调里读写，不需要触发重组。
    val cardBounds = remember { mutableMapOf<String, Rect>() }

    fun select(card: Card, cardTopPx: Float) {
        // 起点位移 = 被点卡 root top − 飞行层 root top（后者 = 内容区 top + 24dp）。
        // ⚠ 两边必须同坐标系：cardBounds 是 boundsInRoot（含状态栏），飞行层布局位置
        //   也在 root 系但 graphicsLayer 的 translationY 相对**自身布局位置**——直接拿
        //   卡的 root top 当位移会多出一个状态栏高度，起飞瞬间先跳下去一截再飞上来
        startOffsetPx = cardTopPx - (contentTopPx + endTopPx)
        // 交接点：堆内卡抬到列表区顶边之前必须交棒（再抬就会被 pane 裁剪），但最多只
        // 走 30%——再晚遮罩已经压暗列表，交接时的亮度差会显出来
        val liftBudget = (cardTopPx - paneTopPx).coerceAtLeast(0f)
        handoffV = if (startOffsetPx > 1f) {
            (liftBudget / startOffsetPx).coerceIn(0.06f, 0.30f)
        } else 0.06f
        lastSelCard = card
        openSeq++
        inStackOpen = true
        selId = card.id
        scope.launch {
            flight.snapTo(0f)
            backdrop.snapTo(0f)
            openReveal.snapTo(0f)
            // 两条并行：飞行 380ms 标准曲线；遮罩 300ms ease（web .fade-*{all .3s ease}）
            launch { flight.animateTo(1f, tween(flyOutMs, easing = MotionTokens.StandardEasing)) }
            backdrop.animateTo(1f, tween(300, easing = LabEase))
        }
    }

    fun close() {
        val id = selId ?: return
        // 收回动画进行中再次触发（连点遮罩/关闭钮）不重启 600ms 计时——重启会让回弹变成两段
        if (flight.isRunning && flight.targetValue == 0f) return
        // ⚠ web flipClose 第一步就是 emit(selectedId=null)：卡**立即**回到卡堆内部
        //   （z-index 99999→index、fixed→absolute 进滚动容器被裁剪、卡面内容瞬时切回
        //   基础态），只剩 transform 从弹出位滑回槽位。原生同构做法：浮层瞬间消失、
        //   堆内该卡（closingId）接力飞回——交换帧两者几何完全重合（v=1 时浮层
        //   translationY=0、堆内卡 translationY=-startOffsetPx，视觉位置相同）。
        //   收回全程卡在堆内层级：被 pane 顶裁剪、被堆里下方卡遮挡 ⇒「滑回原位」
        //   而不是盖在列表表面飞完再闪换（2026-10-20 真机反馈的生硬嵌入感）
        closingId = id
        selId = null
        inStackOpen = false
        scope.launch {
            // 开卡交叉淡入若还在跑，先归零（浮层卡已撤，不可见）
            openReveal.snapTo(0f)
            // 收回 600ms 弹性曲线（web 基础态 transform .6s cubic-bezier(.34,1.56,.64,1)）；
            // 遮罩按 web 实测 300ms ease 先退（`.stack-backdrop` 的 .4s 被后定义的
            // `.fade-leave-active{all .3s ease}` 同特异性覆盖），卡片带着回弹落回槽位
            val fly = launch { flight.animateTo(0f, tween(600, easing = LabPopBackEasing)) }
            backdrop.animateTo(0f, tween(300, easing = LabEase))
            fly.join()
            if (closingId == id) closingId = null
        }
    }

    fun collapseOverlay() {
        selId = null
        closingId = null
        inStackOpen = false
        // snapTo 是挂起函数：借 scope 立即归零（切 tab/排序/跳转时直接收浮层，不播收回动画）
        scope.launch {
            flight.snapTo(0f)
            backdrop.snapTo(0f)
            openReveal.snapTo(0f)
        }
    }

    // 开卡交棒：堆内先抬，到 handoffV 后交叉淡入浮层卡（80ms）。selId/序号变化即重挂，
    // 旧等待协程随之取消——否则上一次没收完的 watcher 会在下一次开卡时误触发。
    LaunchedEffect(selId, openSeq, handoffV) {
        if (selId == null) {
            inStackOpen = false
            return@LaunchedEffect
        }
        snapshotFlow { flight.value }.first { it >= handoffV }
        openReveal.animateTo(1f, tween(80, easing = MotionTokens.StandardEasing))
        inStackOpen = false
    }

    // 浮层卡只在「选中/起飞」期间存在；收回由堆内卡接力（见 close()）
    // ⚠ 用 curDisplay（脱敏副本）而不是 cards：预览态下浮层若取原始卡会泄露真实卡号
    val overlayCard = selId?.let { id -> curDisplay.firstOrNull { it.id == id } }
    // 整个浮层（遮罩/按钮）的生命周期：到收回动画播完才撤（按钮绑 backdrop 渐隐，撤除时已不可见）
    val overlayActive = selId != null || closingId != null

    // ===== 排序模式：进入时快照一份可变顺序 =====
    var sortList by remember { mutableStateOf<List<Card>>(emptyList()) }
    fun enterSort() {
        collapseOverlay()
        // 预览态下用脱敏副本进排序列表（与卡堆显示一致）；提交时按 id 映射回真实卡
        sortList = curDisplay
        sortMode = true
    }
    fun exitSort() {
        sortMode = false
        sortList = emptyList()
    }

    // ⚠ bankIconOf 必须 remember：两个 pane 传**同一个 lambda 实例**，配合 fetch 的
    //   判等跳写 ⇒ 切 tab / 另一 pane 取数回来时，卡堆参数全部 equals ⇒ 整堆 skip
    val bankIconOf: (Card) -> String = remember(bankIconById) {
        { c -> bankIconById[c.bankId].orEmpty() }
    }
    // 卡组织 logo：web `getCardOrgInfo` —— bank 分类里按名称匹配 icon_url；无图/失败才用组件
    val orgIconOf: (Card) -> String = remember(orgBanks) {
        { c -> cardOrgIconUrlOf(c.cardOrg, orgBanks) }
    }

    // ===== 跳转（对齐原 WebView 版 handleNavigate 的翻译结果）=====
    fun goEdit(id: String) {
        collapseOverlay()
        nav.navigate(Routes.cardEdit(id))
    }

    fun goFlow(id: String) {
        collapseOverlay()
        nav.navigate(Routes.cardFlow(id))
    }

    ScreenScaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .then(inner)
                .background(colors.bgCard)
                // 飞行起点的坐标系锚点（详见 select() 注释）
                .onGloballyPositioned { contentTopPx = it.boundsInRoot().top },
        ) {
            // ===== 顶栏（标题 + 分段 + 排序/预览开关；滚动收起 = 高度裁剪 + 淡出，对齐 web .32s）=====
            // 收起动画 = 自然高度 × (1 - collapse)。
            // ⚠ 不要改回 `height(headerH)` + onGloballyPositioned 回写自然高度的写法：
            //   ① 初值 0 时高度恒 0、onGloballyPositioned 永远量到 0 ⇒ 顶栏整体不显示的死锁；
            //   ② 收起过程中量到的是动画中间高度，会被回写污染目标值，再展开就回不到全高。
            //   现在子布局始终按**不限高**约束测量 ⇒ 自然高度恒真，每帧只重布局不重组。
            val headerCollapse by animateFloatAsState(
                if (scrolled && !sortMode) 1f else 0f,
                tween(vantDimens.durationSlow, easing = MotionTokens.StandardEasing),
                label = "labHeaderCollapse",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .graphicsLayer { alpha = 1f - headerCollapse }
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        layout(constraints.maxWidth, (p.height * (1f - headerCollapse)).roundToInt()) {
                            p.place(0, 0)
                        }
                    },
            ) {
                LabHeader(
                    tab = tab,
                    sortMode = sortMode,
                    previewMode = previewMode,
                    onTab = {
                        if (it != tab) {
                            collapseOverlay()
                            tab = it
                            scrolled = false
                        }
                    },
                    onToggleSort = { if (sortMode) exitSort() else enterSort() },
                    onTogglePreview = { previewMode = !previewMode },
                )
            }

            // ===== 列表区（VanPullRefresh 的头叠在内容上方）=====
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 列表区顶边（= header 下沿）：开卡时堆内卡最多抬到这里，再抬会被裁剪
                    .onGloballyPositioned { paneTopPx = it.boundsInRoot().top },
            ) {
                VanPullRefresh(
                    state = refreshState,
                    disabled = sortMode || overlayActive,
                    isChildAtTop = { scrollState.value == 0 },
                    onRefresh = {
                        ApiGetCache.clear() // 下拉刷新必须绕过 GET 缓存
                        scope.launch {
                            fetch(tab, true)
                            refreshState.finishRefresh()
                        }
                    },
                ) {
                    // ⚠ 双 pane 常驻且**不在排序分支里**（WebView v-show 的等价物 + 反超点）：
                    //   - 切 tab：旧写法销毁整棵卡堆子树再重建 ⇒ 主线程单帧做不完必掉帧；
                    //     常驻后只切 zIndex/alpha 两个 layer 属性，图片 layer 与滚动位全保留。
                    //   - 进/出排序模式：webview 版是 v-if（退出排序整列表重建）；这里排序列表
                    //     只是**叠加**在常驻 pane 之上，退出排序 = 撤掉叠加层，卡堆零重建。
                    //   - 命中测试按 zIndex 从高到低：排序列表盖住 pane ⇒ 触摸不穿透。
                    Box(Modifier.fillMaxSize()) {
                        LabListPane(
                            visible = !sortMode && isDebit,
                            scrollState = debitScroll,
                            scrollEnabled = !overlayActive,
                            bottomPad = if (scrolled) 24.dp else 140.dp,
                        ) {
                            when {
                                loaded["debit"] != true -> CardStackSkeleton(
                                    cardHeight = CARD_H.dp,
                                    step = STACK_STEP.dp,
                                )
                                displayDebit.isNotEmpty() -> LabCardStack(
                                    cards = displayDebit,
                                    selectedId = selId,
                                    closingId = closingId,
                                    flightStartPx = startOffsetPx,
                                    openedInStack = inStackOpen,
                                    openReveal = openReveal,
                                    handoffV = handoffV,
                                    flight = flight,
                                    backdrop = backdrop,
                                    bounds = cardBounds,
                                    bankIconOf = bankIconOf,
                                    orgIconOf = orgIconOf,
                                    cardTypeLabel = "DEBIT CARD",
                                    defaultColor = Color(0xFF4A90E2),
                                    onSelect = ::select,
                                )
                                else -> LabPanePlaceholder(debit = true)
                            }
                        }
                        LabListPane(
                            visible = !sortMode && !isDebit,
                            scrollState = creditScroll,
                            scrollEnabled = !overlayActive,
                            bottomPad = if (scrolled) 24.dp else 140.dp,
                        ) {
                            when {
                                loaded["credit"] != true -> CardStackSkeleton(
                                    cardHeight = CARD_H.dp,
                                    step = STACK_STEP.dp,
                                )
                                displayCredit.isNotEmpty() -> LabCardStack(
                                    cards = displayCredit,
                                    selectedId = selId,
                                    closingId = closingId,
                                    flightStartPx = startOffsetPx,
                                    openedInStack = inStackOpen,
                                    openReveal = openReveal,
                                    handoffV = handoffV,
                                    flight = flight,
                                    backdrop = backdrop,
                                    bounds = cardBounds,
                                    bankIconOf = bankIconOf,
                                    orgIconOf = orgIconOf,
                                    cardTypeLabel = "CREDIT CARD",
                                    defaultColor = Color(0xFFEE0A24),
                                    onSelect = ::select,
                                )
                                else -> LabPanePlaceholder(debit = false)
                            }
                        }
                        if (sortMode) {
                            LabSortList(
                                cards = sortList,
                                onReorder = { sortList = it },
                                onCommit = { ordered ->
                                    // 预览态下 ordered 是脱敏副本：按 id 映射回真实卡再提交/落缓存，
                                    // 防止把伪随机号写进本地缓存和后端
                                    val realOrdered = ordered.map { oc -> cards.firstOrNull { it.id == oc.id } ?: oc }
                                    scope.launch {
                                        when (val r = graph.card.updateSortBatch(realOrdered.mapIndexed { i, c -> c.id to (i + 1) })) {
                                            is ApiResult.Ok -> {
                                                // 提交成功才落本地缓存：先写缓存的话，失败即与服务端不一致
                                                cache[tab] = realOrdered
                                                toast.show("排序已保存")
                                            }
                                            is ApiResult.Fail -> {
                                                sortList = curDisplay // 回滚本地顺序
                                                toast.show(r.message.ifBlank { "排序保存失败" }, VanToastType.Fail)
                                            }
                                            else -> sortList = curDisplay
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                // ===== 底部「添加卡片」（选中/排序/滚动收起时位移+淡出，对齐 web .glass-add-btn）=====
                val hideBtn by animateFloatAsState(
                    if (overlayActive || sortMode || scrolled) 1f else 0f,
                    tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing),
                    label = "labAddBtnHide",
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 30.dp)
                        .graphicsLayer {
                            alpha = 1f - hideBtn
                            translationY = hideBtn * (size.height + 80f)
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            // webview 真源 `.glass-add-btn`：**统一 var(--theme-primary)** +
                            // 主色光晕（旧原生 credit 页换红底是跟 web 线上版 credit.vue，
                            // 离线版已统一，2026-10-20 对齐修正）
                            .cssShadow(28.dp, WebShadow(0f, 8f, 24f, Color(0x4D1989FA)))
                            .clip(RoundedCornerShape(28.dp))
                            .background(colors.primary)
                            .clickable {
                                collapseOverlay()
                                nav.navigate(Routes.CARD_ADD)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VanIcon("plus", size = 18.sp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            FText("添加卡片", 18f, FontWeight.SemiBold, Color.White)
                        }
                    }
                }
            }
        }

        // ===== 加载态 =====
        // 由每个 pane 内的 CardStackSkeleton 承担（卡堆骨架，替代居中转圈）；数据到达后
        // 在 LabCardStack 内做「整堆淡入」，骨架同步淡出。不再叠全屏 loading 层。

        // ============================================================
        // 浮层：遮罩 → 飞行卡 → 管理 → 消费明细 → 关闭
        // （全部画在 Scaffold 之上；z 序 = 声明顺序）
        // 容器活到收回动画播完（overlayActive）；飞行卡只在选中期存在——
        // 收回瞬间让位给堆内卡接力（close()），遮罩/按钮按 backdrop 渐隐退场
        // ============================================================
        if (overlayActive) {
            // 明细按钮渐隐期（收回 300ms 内）的卡引用兜底
            val detailCard = overlayCard ?: lastSelCard

            // 全屏浮层容器：给底部关闭钮的 BottomCenter 定位提供 BoxScope
            Box(Modifier.fillMaxSize()) {
                // 遮罩：web .stack-backdrop{rgba(0,0,0,.65); opacity .4s} —— alpha 由 backdrop 独立插值
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.65f * backdrop.value }
                        .background(Color.Black)
                        .clickable { close() },
                )

                // 飞行卡（交接点后才可见——前段由堆内卡接力，见 select()/LaunchedEffect）：
                //   终点 = 顶部 24dp / 左右 20dp / scale 1.05；起点 = 被点卡在堆叠里的位置
                //   translationY = startOffset * (1-p)，scale = 1 + .05*p —— 与 web FLIP 同构，
                //   但整条动画只更新 layer 属性，不触碰布局（web 每帧走 style 重算）。
                if (overlayCard != null) {
                    val sel = overlayCard
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp, start = 20.dp, end = 20.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val v = flight.value
                                translationY = startOffsetPx * (1f - v)
                                val s = 1f + 0.05f * v
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                // 交接前透明：浮层卡与堆内卡同几何，靠 80ms 交叉淡入接力
                                alpha = openReveal.value
                            }
                            // web `handleCardClick`：选中态下点弹出卡本身 = flipClose（收回）。
                            // 管理按钮是子元素，先消费点击，不会误触这里。
                            .clickable { close() }
                            // 选中态提亮：web `.is-selected{filter:brightness(1.1)}` 的近似（叠 8% 白罩）。
                            // ⚠ drawRoundRect 不是 drawRect：方角白罩会把卡的圆角外也涂白
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.08f * flight.value.coerceIn(0f, 1f)),
                                    cornerRadius = CornerRadius(20.dp.toPx()),
                                )
                            },
                    ) {
                        BankCardFace(
                            card = sel,
                            cardTypeLabel = if (isDebit) "DEBIT CARD" else "CREDIT CARD",
                            showLast4Suffix = false,
                            bankIconUrl = bankIconById[sel.bankId].orEmpty(),
                            orgIconUrl = orgIconOf(sel),
                            selected = true,
                            defaultColor = if (isDebit) Color(0xFF4A90E2) else Color(0xFFEE0A24),
                            // 2026-10-20 真机反馈：弹起时不要阴影（elevation 弥散灰影观感脏，
                            // 且收回时跟着卡飞、落地 16→6dp 跳变）。flat = 无阴影，对齐用户要求
                            flat = true,
                        )
                        // 管理按钮（卡右上角）：web `.card-actions-quick` 容器 padding 20px 10px 0
                        //   ⇒ 距卡右边缘 **10dp**（此前写 20dp 偏内一截）；顶部 20dp 不变
                        Row(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 10.dp)) {
                            Row(
                                Modifier
                                    .cssShadow(30.dp, WebShadow(0f, 10f, 20f, Color(0x33000000)))
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(colors.bgCard)
                                    .clickable { goEdit(sel.id) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VanIcon("setting-o", size = 14.sp, color = colors.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                FText("管理", 16f, FontWeight.SemiBold, colors.textPrimary)
                            }
                        }
                    }
                }
                }

                // 按钮/明细的显隐：跟遮罩进度走（300ms ease，web .fade-*{all .3s ease}；
                //   起始位 translate(-50%,-30%) ⇒ 从上方 30% 落下淡入、收回时升出淡出）。
                //   ⚠ 不绑 flight：收回是 600ms 弹性曲线，按钮跟着拖到后半程才退完（web 是独立 .3s）

                // 查看消费明细（卡片正下方，仅借记卡；web .flow-bar top:280px）
                if (isDebit && detailCard != null) {
                    val sel = detailCard
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 280.dp)
                            .graphicsLayer {
                                val v = backdrop.value.coerceIn(0f, 1f)
                                alpha = v
                                translationY = -(1f - v) * 40.dp.toPx()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // web `.flow-btn:active{opacity:.85}`（无 transition，瞬时）——按压反馈
                        val flowInteraction = remember { MutableInteractionSource() }
                        val flowPressed by flowInteraction.collectIsPressedAsState()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                // web `.flow-btn:active{opacity:.85}`：整钮（含 rgba 底）乘 0.85
                                .graphicsLayer { alpha = if (flowPressed) 0.85f else 1f }
                                // webview 真源 `.flow-btn`（backdrop-filter 已掐）：白 0.3 底 +
                                // 白 0.3 描边 + 白字 15px/600。⚠ 不能加 cssShadow：它的实现是
                                // 「纯黑实心垫层 + background 覆盖」（CssShadow.kt:35），半透明
                                // bg 盖不住垫层 ⇒ 0.7 黑露出 = 按钮成深灰死色不透光（2026-10-20
                                // 真机「透明度不对」的根因）。web 的 0,8,24 黑30% 阴影画在 65%
                                // 黑遮罩上本就不可见，删掉无观感损失，30% 白直接叠遮罩才是同构
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = flowInteraction,
                                    indication = null,
                                ) { goFlow(sel.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // web：van-icon 无 size 时继承按钮 font-size 15px
                                VanIcon("orders-o", size = 15.sp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                // web `.flow-btn{font-size:15px; font-weight:600}`（此前 16f 偏大）
                                FText("查看消费明细", 15f, FontWeight.SemiBold, Color.White)
                            }
                        }
                        if (sel.id in idleIds) FText("近6个月无动账", 12f, color = Color(0xFFFF7A7A))
                    }
                }

                // 底部毛玻璃关闭钮（无 backdrop-filter —— WebView 版刚因它闪烁被掐掉，原生同样不做）
                // web `.close-btn:active{scale(.92); bg .3}`，transition .2s ease
                val closeInteraction = remember { MutableInteractionSource() }
                val closePressed by closeInteraction.collectIsPressedAsState()
                val closeScale by animateFloatAsState(
                    if (closePressed) 0.92f else 1f,
                    tween(200, easing = LabEase),
                    label = "labClosePress",
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .size(56.dp)
                        .graphicsLayer {
                            val v = backdrop.value.coerceIn(0f, 1f)
                            alpha = v
                            translationY = -(1f - v) * size.height * 0.3f
                            scaleX = closeScale
                            scaleY = closeScale
                        }
                        // webview 真源 `.close-btn`（覆盖块掐 backdrop-filter 后）：白 0.3 底 +
                        // 白 0.3 描边 + 白 24px 图标。⚠ 同明细钮不能加 cssShadow——黑垫层被
                        // 半透明 bg 盖不住会露出 0.7 黑（「透明度不对」根因，详见 flow-btn 注）；
                        // web 的 0,8,24 黑25% 阴影在 65% 黑遮罩上不可见。
                        // 按压态只 scale(.92)：webview 覆盖块的 `!important` 顶死了 `:active`
                        // 里的 bg 变化规则，实际 web 按压只剩 scale 生效（特异性 0,3,3 > 0,1,1）
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                        ) { close() },
                    contentAlignment = Alignment.Center,
                ) { VanIcon("cross", size = 24.sp, color = Color.White) }
            }
        }
    }
}

/**
 * 收回动画专用缓动：精确复刻 web 基础态 `transform .6s cubic-bezier(0.34,1.56,0.64,1)` 的
 * back-out 回弹（y 控制点 1.56 > 1 ⇒ Compose 无内置等价曲线，直接按 CSS 语义构造）。
 * 表现：冲过终点约 10% 再落回，把「卡片砸回槽位」的手感做出来。
 */
private val LabPopBackEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** CSS `ease` = cubic-bezier(0.25,0.1,0.25,1)：遮罩 / 按钮组的 fade 曲线（web .fade-*{all .3s ease}）。 */
private val LabEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

// ============================================================================
// 双 pane 容器 / 占位态 / 卡堆
// ============================================================================

/**
 * 常驻列表 pane（双 pane 结构）：激活 zIndex=1/alpha=1，非激活 zIndex=0/alpha=0。
 * 命中测试按 zIndex 从高到低，激活 pane fillMaxSize 全区可滚 ⇒ 隐藏 pane 收不到触摸，
 * 且组合/布局/图片 layer 与滚动位置全保留 —— 切 tab 零重建。
 *
 * 两个性能/视觉要点（2026-10-20 切 tab 卡顿根治）：
 * 1. **底部留白动画**：web `.page-card-list{transition:padding-bottom .32s}`——滚动收起/
 *    展开时 24↔140dp 平滑过渡；旧实现硬切 ⇒ 切 tab 复位 scrolled 时整列跳变。
 *    animateDpAsState 放**本组件内部**：动画每帧只重组本 Column 的 modifier，
 *    content slot 的 lambda 参数实例未变 ⇒ 卡堆零重组（放父级会每帧重组整页）。
 * 2. ⚠ **隐藏 pane 绝不能跳过绘制**（2026-10-20 真机踩坑后回滚）：曾加
 *    `drawWithContent { if (visible) drawContent() }` 想省隐藏期成本，结果隐藏期
 *    RenderNode 内容被清空，切回那一帧要**全量重录 DisplayList + 位图重新引用**——
 *    卡多的 pane（借记卡通常 N 张）切换帧集中爆发掉帧；而反向「借→信」时对侧
 *    还是空占位/卡少，于是呈现「信→借才卡」的**方向不对称卡顿**。正确姿势：
 *    alpha=0 的 layer RenderThread 直接跳过合成，常驻成本近零；flight 驱动的
 *    graphicsLayer 每帧只是 layer 属性更新，并不会重录子树 DisplayList
 *    （当初「隐藏 pane 白白重录」的判断是错的，跳绘纯属负优化）。
 */
@Composable
private fun BoxScope.LabListPane(
    visible: Boolean,
    scrollState: ScrollState,
    scrollEnabled: Boolean,
    bottomPad: Dp,
    content: @Composable () -> Unit,
) {
    val pad by androidx.compose.animation.core.animateDpAsState(
        bottomPad,
        tween(vantDimens.durationSlow, easing = MotionTokens.StandardEasing),
        label = "labPaneBottomPad",
    )
        Column(
            Modifier
                .fillMaxSize()
                .zIndex(if (visible) 1f else 0f)
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                // 选中卡飞行期间锁列表（web lockScroll 的对应物；起点坐标才不会被滚动打散）
                .verticalScroll(scrollState, enabled = scrollEnabled)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        ) {
        content()
        Spacer(Modifier.height(pad))
    }
}

/**
 * 空列表占位（web `van-empty`）。加载中的视觉提示由 pane 内的 [CardStackSkeleton] 承担
 * （叠好的骨架卡，替代全屏转圈）；数据到达后由 [LabCardStack] 做整堆淡入。
 */
@Composable
private fun LabPanePlaceholder(debit: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        VanEmpty(
            description = if (debit)
                "暂无借记卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！"
            else
                "暂无信用卡",
        )
    }
}

/**
 * 原生版卡堆（对应 web `CardStack.vue` 的列表态）。
 *
 * **入场**（2026-10-22 改）：数据帧不一次性组合整棵卡堆——revealCount 每帧放行 4 张，
 * 组合成本摊平；整堆组合期间 alpha=0 并由骨架卡覆盖，组合完 220ms 整体淡入
 * （旧版逐张 40ms + 单卡淡入，观感「一叠一叠」）。web 不卡是浏览器渲染管线天然分帧 +
 * 图片懒加载，Compose 无等价机制只能显式摊。
 *
 * 选中卡在堆叠里默认**占位**（Spacer 撑高）：起飞/选中期由浮层接管渲染（web is-selected
 * 的 position:fixed 等价物）。但 2026-10-21 第四轮起，**弹起前段不再直接交给浮层**：
 * `openedInStack` 为真时，堆内这张卡自己按自身 z 序抬升（仍被下方卡遮挡、被 pane 顶裁剪），
 * 到交接点后再与浮层卡交叉淡入——旧版浮层卡第一帧就以整卡姿态盖在列表上（真机反馈
 * 「先在卡列表里完整展示再突兀弹起」）。收回时反过来：浮层瞬间消失，**堆内卡自己飞回**
 * （isClosing 分支），交换帧两者几何重合 ⇒ 无缝接力。
 */
@Composable
private fun LabCardStack(
    cards: List<Card>,
    selectedId: String?,
    closingId: String?,
    flightStartPx: Float,
    openedInStack: Boolean,
    openReveal: Animatable<Float, *>,
    handoffV: Float,
    flight: Animatable<Float, *>,
    backdrop: Animatable<Float, *>,
    bounds: MutableMap<String, Rect>,
    bankIconOf: (Card) -> String,
    orgIconOf: (Card) -> String,
    cardTypeLabel: String,
    defaultColor: Color,
    onSelect: (Card, Float) -> Unit,
) {
    // ===== 入场：骨架卡 + 整堆淡入（2026-10-22，替代「逐张 40ms 淡入」）=====
    // 数据帧仍分批组合（每帧 4 张）避免一次组合整棵卡堆掉帧，但**整堆先 alpha=0**，
    // 组合完再 220ms 整体淡入；期间盖同几何骨架卡 ⇒ 看不到逐张 pop、高度也不跳。
    // ⚠ 开卡/收回的 flight/relay 动画完全不在这层，未动。
    // 切 tab / 下拉刷新不重播：revealCount 是 LabCardStack 组合实例的 remember 状态，
    // pane 常驻不销毁；LaunchedEffect(cards.size) 只在新卡加入时继续递增到新总数。
    var revealCount by remember { mutableStateOf(0) }
    LaunchedEffect(cards.size) {
        while (revealCount < cards.size) {
            revealCount = (revealCount + 4).coerceAtMost(cards.size)
            if (revealCount < cards.size) delay(16)
        }
    }
    // 钳到 size：删卡后 revealCount 可能 > size，高度按实际卡数算（避免底部多一截空白）
    val shown = revealCount.coerceAtMost(cards.size)
    val ready = cards.isNotEmpty() && shown >= cards.size
    // hasEntered：只在**首次整堆入场**显示骨架；后续刷新/加卡不再盖住已有卡
    var hasEntered by remember { mutableStateOf(false) }
    var skeletonVisible by remember { mutableStateOf(true) }
    LaunchedEffect(ready) {
        if (ready) hasEntered = true else if (!hasEntered) skeletonVisible = true
    }
    val stackAlpha by animateFloatAsState(
        targetValue = if (ready || hasEntered) 1f else 0f,
        animationSpec = tween(220, easing = MotionTokens.StandardEasing),
        label = "labStackEnter",
        finishedListener = { if (it >= 1f) skeletonVisible = false },
    )
    // 高度按**全量**算：组合期间容器不随 shown 长高（旧实现逐张撑高=一叠一叠感来源之一）
    val stackHeight = (CARD_H + (cards.size - 1).coerceAtLeast(0) * STACK_STEP).dp
    // ⚠ 先落局部变量：graphicsLayer block 的 receiver（GraphicsLayerScope）自带 shape
    //   同名成员，直接写 shape 会解析到 receiver 而不是这张卡的圆角
    val cardShape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().height(stackHeight)) {
        // 整堆入场层：alpha 只作用于这一层，弹起/收回的 flight/relay 逻辑原样保留
        Box(Modifier.matchParentSize().graphicsLayer { alpha = stackAlpha }) {
        cards.forEachIndexed { index, card ->
            // 未放行的卡不组合（见函数头 reveal 注释）：revealCount++ 触发的重组里，
            // 新增的只有 4 张卡子树，旧卡全 key(id) skip ⇒ 每帧成本恒定
            if (index >= shown) return@forEachIndexed
            val isSel = card.id == selectedId
            val isClosing = card.id == closingId
            key(card.id) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (index * STACK_STEP).dp)
                        // 浮层飞行的起点 = 收回动画的终点。boundsInRoot 不受嵌套滚动影响，
                        // 布局/滚动后自动刷新（选中期间列表已锁滚 ⇒ 飞行中坐标稳定）
                        .onGloballyPositioned { bounds[card.id] = it.boundsInRoot() }
                        .graphicsLayer {
                            val d = flight.value
                            if (isClosing) {
                                // 收回接力（web flipClose 同构）：translationY 相对**自己槽位**，
                                // v=1 ⇒ -flightStartPx（与浮层交换帧几何重合：槽位-startOffset
                                // = 弹出位）、v=0 ⇒ 0 落回槽位；undershoot v<0 冲过槽位再回弹。
                                // 不提 zIndex ⇒ 卡在堆内声明序层级：被 pane 顶裁剪、被下方
                                // 卡遮挡——正是 web「z-index 99999→index」的收回观感
                                translationY = -flightStartPx * d
                                val s = 1f + 0.05f * d
                                scaleX = s
                                scaleY = s
                                // 接手时可能开卡交叉淡入还没走完（alpha<1），收回卡必须不透明，
                                // 否则会半透明地飞回去
                                alpha = 1f
                                // 阴影 0→6dp 随收回渐入（web box-shadow .4s ease 渐变）：
                                // 交换帧浮层卡无阴影，落地时正好接上堆内基础态 6dp——
                                // 不做的话交换帧 0→6dp 突现一下，收回观感「生硬」
                                shadowElevation = (1f - d).coerceIn(0f, 1f) * 6.dp.toPx()
                                shape = cardShape
                                clip = false
                            } else if (isSel && openedInStack) {
                                // 开卡前段：堆内卡抬升（与浮层卡同一 translation/scale 公式），
                                // 此时仍在堆内 z 序——下方卡继续盖住它的下半身，观感是
                                // 「从层叠里抽出来」。alpha 在交接点后由 openReveal 交棒给浮层。
                                translationY = -flightStartPx * d
                                val s = 1f + 0.05f * d
                                scaleX = s
                                scaleY = s
                                alpha = (1f - openReveal.value).coerceIn(0f, 1f)
                                // 阴影在交接点前降到 0（浮层卡 flat 无阴影），交接帧同值
                                shadowElevation =
                                    (1f - (d / handoffV).coerceIn(0f, 1f)) * 6.dp.toPx()
                                shape = cardShape
                                clip = false
                            } else if (!isSel && (d > 0.001f || backdrop.value > 0.001f)) {
                                // dim 分两个进度（web 各自独立 transition 的同构）：
                                //  - alpha 绑 backdrop（300ms）：web 的 opacity 是瞬时切换（无
                                //    transition），净观感=「遮罩在则暗」；若绑 flight 600ms 弹性，
                                //    遮罩 300ms 退完后卡还要拖 300ms 才变亮 = 收回后半程发暗拖沓
                                //  - translateY/scale 绑 flight：web 基础态 transform .6s 弹性过渡
                                val bd = backdrop.value
                                alpha = (1f - 0.4f * bd).coerceAtMost(1f)
                                val t = d
                                val s = 1f - 0.05f * t
                                scaleX = s
                                scaleY = s
                                translationY = 10.dp.toPx() * t
                                shadowElevation = 0f
                            } else {
                                // 静止态复位：layer 属性跨分支保留，收回/开卡卡的阴影与 scale
                                // 不显式清掉会永久留在那一张上
                                alpha = 1f
                                translationY = 0f
                                scaleX = 1f
                                scaleY = 1f
                                shadowElevation = 0f
                            }
                        }
                        .then(
                            // 收回飞行中的卡不可点（web pointer-events 由遮罩接管；连点防串位）；
                            // 入场淡入完成前也不可点（避免在透明卡面上误开卡）
                            if (ready && selectedId == null && closingId == null) Modifier.clickable {
                                onSelect(card, bounds[card.id]?.top ?: 0f)
                            } else Modifier
                        ),
                ) {
                    if (isSel) {
                        if (openedInStack) {
                            // 开卡前段：**未选中卡面**（与列表静态一致，尾号不挪位），
                            // 交接时与浮层的选中卡面交叉淡入；外层阴影由 layer 渐隐到 0
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .drawWithContent {
                                        drawContent()
                                        // 选中态提亮（与浮层卡同式）：0.08 × flight
                                        drawRoundRect(
                                            Color.White.copy(alpha = 0.08f * flight.value.coerceIn(0f, 1f)),
                                            cornerRadius = CornerRadius(20.dp.toPx()),
                                        )
                                    },
                            ) {
                                BankCardFace(
                                    card = card,
                                    cardTypeLabel = cardTypeLabel,
                                    showLast4Suffix = true,
                                    bankIconUrl = bankIconOf(card),
                                    orgIconUrl = orgIconOf(card),
                                    defaultColor = defaultColor,
                                    // flat：阴影交给上面 graphicsLayer 的 6→0 渐变
                                    flat = true,
                                )
                            }
                        } else {
                            Spacer(Modifier.height(CARD_H.dp))
                        }
                    } else {
                        BankCardFace(
                            card = card,
                            cardTypeLabel = cardTypeLabel,
                            showLast4Suffix = true,
                            bankIconUrl = bankIconOf(card),
                            orgIconUrl = orgIconOf(card),
                            defaultColor = defaultColor,
                            // 收回期阴影由上面的 graphicsLayer shadowElevation 渐变接管
                            //（flat 掐掉内部静态 6dp），落地 closingId=null 恢复静态 6dp，同值交接
                            flat = isClosing,
                        )
                    }
                }
            }
        }
        }
        // 骨架与卡堆同几何：叠在卡堆之上，随整堆 alpha 上升同步淡出
        if (skeletonVisible) {
            CardStackSkeleton(
                modifier = Modifier.graphicsLayer { alpha = (1f - stackAlpha).coerceIn(0f, 1f) },
                cardHeight = CARD_H.dp,
                step = STACK_STEP.dp,
                count = cards.size.coerceAtMost(3),
            )
        }
    }
}

// ============================================================================
// 顶栏 / 排序 / 节流 / 工具
// ============================================================================

/** 顶栏：标题 + 分段 + 排序/预览开关（结构对齐 web `.card-manage-header`）。 */
@Composable
private fun LabHeader(
    tab: String,
    sortMode: Boolean,
    previewMode: Boolean,
    onTab: (String) -> Unit,
    onToggleSort: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(if (tab == "credit") "信用卡" else "借记卡", 20f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            if (!sortMode) LabSegmentSwitch(tab = tab, onTab = onTab)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(
                if (sortMode) "长按拖动卡片排序" else "管理你的银行卡",
                14f, color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Row(Modifier.width(164.dp), verticalAlignment = Alignment.CenterVertically) {
                LabToggleDot("排序", sortMode, onToggleSort, Modifier.weight(1f))
                if (!sortMode) {
                    Spacer(Modifier.width(8.dp))
                    LabToggleDot("预览", previewMode, onTogglePreview, Modifier.weight(1f))
                }
            }
        }
    }
}

private val SEG_ITEM_MIN_W = 78.dp

/** 分段开关（滑块 0.28s 标准 easing，对齐 web `.segment-switch/.segment-thumb`）。 */
@Composable
private fun LabSegmentSwitch(tab: String, onTab: (String) -> Unit) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val isDebit = tab == "debit"
    // web：`.segment-item{padding:7px 18px;font-size:14px}`，滑块宽 = `calc(50% - 4px)`。
    // 宽度取实测半宽而非写死 dp —— 字体随系统放大时胶囊跟着长，不会裁字。
    var trackWpx by remember { mutableStateOf(0) }
    val thumbW = with(density) { (trackWpx / 2f).toDp() }
    val thumbX by androidx.compose.animation.core.animateDpAsState(
        if (isDebit) 0.dp else thumbW,
        tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing),
        label = "labSegThumb",
    )
    Box(
        Modifier
            .cssShadow(22.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgThird)
            .padding(4.dp)
            .onGloballyPositioned { trackWpx = it.size.width },
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .width(thumbW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.primary),
        )
        Row {
            LabSegmentItem("借记卡", active = isDebit) { onTab("debit") }
            LabSegmentItem("信用卡", active = !isDebit) { onTab("credit") }
        }
    }
}

@Composable
private fun LabSegmentItem(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .widthIn(min = SEG_ITEM_MIN_W)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        FText(text, 14f, FontWeight.SemiBold, if (active) Color.White else colors.textSecondary)
    }
}

/** 「排序 / 预览」空心圆环开关（对齐 web `.preview-toggle` + `.dot`）。 */
@Composable
private fun LabToggleDot(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .cssShadow(20.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgThird)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 13f, if (on) FontWeight.SemiBold else FontWeight.Medium,
            if (on) colors.primary else colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(if (on) colors.primary else Color.Transparent)
                .border(2.dp, if (on) colors.primary else colors.border, RoundedCornerShape(50)),
        )
    }
}

/**
 * 排序模式列表（对应 webview `CardListPane` 的 draggable 块）。
 *
 * 沿用旧 `CardSortList` 实测修好的三点：按住即拖、`key(id)` 防串位、
 * 目标下标按「起始下标 + 总位移/行高」。差异：**拖完一次性提交**（对齐 webview
 * `@end="commitSort"`，旧原生每移一位就打一次 PUT）。
 */
@Composable
private fun LabSortList(
    cards: List<Card>,
    onReorder: (List<Card>) -> Unit,
    onCommit: (List<Card>) -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val rowH = 42.dp
    val rowHpx = with(density) { rowH.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val scroll = rememberScrollState()
    val cardsRef = rememberUpdatedState(cards)

    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Spacer(Modifier.height(10.dp))
        cards.forEachIndexed { index, card ->
            val isDragging = card.id == draggingId
            val shiftPx = if (isDragging) dragOffsetPx - (index - dragStartIndex) * rowHpx else 0f
            key(card.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .offset { IntOffset(0, shiftPx.roundToInt()) }
                        .cssShadow(12.dp, WebShadow(0f, 1f, 4f, Color.Black.copy(alpha = 0.05f)))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgPage)
                        .graphicsLayer { if (isDragging) { scaleX = 1.02f; scaleY = 1.02f; alpha = 0.95f } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f).padding(start = 20.dp), verticalAlignment = Alignment.Bottom) {
                        FText(card.bankName, 16f, FontWeight.SemiBold, colors.textPrimary)
                        Spacer(Modifier.width(8.dp))
                        FText(card.last4, 16f, color = colors.textSecondary, letterSpacing = 1.sp)
                    }
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = card.id
                                        dragStartIndex = cardsRef.value.indexOfFirst { it.id == card.id }
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val id = draggingId ?: return@detectDragGestures
                                        dragOffsetPx += delta.y
                                        val list = cardsRef.value
                                        val to = (dragStartIndex + (dragOffsetPx / rowHpx).roundToInt())
                                            .coerceIn(0, list.lastIndex.coerceAtLeast(0))
                                        val cur = list.indexOfFirst { it.id == id }
                                        if (to != cur && cur >= 0) {
                                            val m = list.toMutableList()
                                            m.add(to, m.removeAt(cur))
                                            onReorder(m)
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffsetPx = 0f
                                        onCommit(cardsRef.value)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffsetPx = 0f
                                        onCommit(cardsRef.value)
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { VanIcon("bars", size = 20.sp, color = colors.textSecondary) }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 页面实例级取数节流（对齐原 WebView 版 CardWebThrottle：按 tab 分键、失败也记账）。 */
private class LabThrottle {
    private val lastTakenAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun take(key: String, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val last = lastTakenAt[key] ?: 0L
        if (!force && now - last < INTERVAL_MS) return false
        lastTakenAt[key] = now
        return true
    }
    private companion object { const val INTERVAL_MS = 10_000L }
}

/** 稳定伪随机数字串（预览脱敏，同旧 `CardManageScreen` 的实现）。 */
private fun pseudoDigits(n: Int, seed: Long): String {
    var s = if (seed == 0L) 1L else seed
    val sb = StringBuilder()
    repeat(n.coerceAtLeast(0)) {
        s = s * 6364136223846793005L + 1442695040888963407L
        sb.append((s.ushr(33) % 10L).toInt())
    }
    return sb.toString()
}

/** 卡片几何常量（对齐 web `CardStack.vue`：卡高 220、堆叠步进 70）。 */
private const val CARD_H = 220f
private const val STACK_STEP = 70f