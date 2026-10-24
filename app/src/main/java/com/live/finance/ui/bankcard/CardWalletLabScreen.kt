package com.live.finance.ui.bankcard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
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
 * 卡包页（Apple Wallet 式；原「卡包实验室」，2026-10-21）。
 *
 * 2026-10-22 起为「我的卡片」可选正式形态：`CardPageHost` 按 `CardStyleStore` 保存的
 * [com.live.finance.core.store.CardPageStyle.Wallet] 渲染本页；同时内嵌在「卡方实验室」
 * 里作为实时预览。与卡片模组页（CardLabScreen）的**范式差异**：
 *
 * | 维度 | CardLabScreen（WebView 版同构） | 本页（Apple Wallet 式） |
 * |---|---|---|
 * | 明细查看 | 弹卡 → 「查看消费明细」按钮 → 跳 CardFlowScreen | 点卡吸顶，**流水内嵌在卡下方同一滚动流**，零跳转 |
 * | 卡组织 | 借记/信用双 tab 两堆 | **单堆混排** + 顶部类型筛选 chip，默认一眼看全部卡 |
 * | 排序 | 先点按钮进「排序模式」 | **同样进排序模式**：卡堆换成 web 同款简洁列表，拖右侧把手排序，松手提交；普通浏览态不挂拖拽（防误触） |
 * | 弹层 | 65% 黑遮罩 + 底部按钮组 | **无遮罩**：展开面板不透明背景直接接管页面 |
 *
 * 数据层全复用：card.list(null) 全量 + category.list("bank") 银行 icon +
 * flow.listByCard(cardId) 单卡流水 + flow.cardsFlowStats(6) 笔数 + card.updateSortBatch 排序。
 *
 * 动画沿用已验证的机制：FLIP = Animatable flight + graphicsLayer（零重组纯 GPU），
 * 展开面板飞行起点坐标系同 CardLabScreen（内容区锚点 + boundsInRoot 差值）。
 */
@Composable
fun CardWalletLabScreen(nav: NavHostController, initialTab: String = "debit") {
    val graph = App.of(androidx.compose.ui.platform.LocalContext.current).graph
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val toast = LocalVanToastController.current
    val navController = nav

    // ===== 数据状态 =====
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var bankIconById by remember { mutableStateOf(mapOf<String, String>()) }
    /** `category.list("bank")` 原表：卡组织 logo 要在里面按名称匹配（web `getCardOrgInfo`）。 */
    var orgBanks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var statById by remember { mutableStateOf(mapOf<String, Pair<Int, Int>>()) }
    var loaded by remember { mutableStateOf(false) }
    /** 防重入：下拉刷新 / 首次进入 / ON_RESUME 可能撞在一起。 */
    var requesting by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(if (initialTab == "credit") "credit" else "debit") } // debit（默认）/ credit —— 不设「全部」：
    // 50-100 张卡的用户点全部一次性组合整堆很费资源（2026-10-21 负责人明确要求）
    // 排序模式：卡堆换成简洁列表，拖右侧把手换位；禁用「点开卡」，避免整理时误触；预览 = 卡号脱敏
    var sortMode by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var previewSeed by remember { mutableStateOf(0L) }
    // 预览脱敏（同模组页/正式页口径：BIN 前 3 位保留 + 其余伪随机、尾号伪随机，seed 稳定到本轮开关）
    val displayCards = remember(cards, previewMode, previewSeed) {
        if (!previewMode) cards else cards.map { c ->
            val bin = c.cardBin
            c.copy(
                cardBin = bin.take(3) + pseudoDigits((bin.length - 3).coerceAtLeast(3), previewSeed),
                last4 = pseudoDigits(4, previewSeed + 7),
            )
        }
    }

    // ===== 展开态（吸顶面板）=====
    var openId by remember { mutableStateOf<String?>(null) }
    var closingId by remember { mutableStateOf<String?>(null) }
    val flight = remember { Animatable(0f) }      // 卡面 FLIP：0 = 堆槽位，1 = 吸顶位
    val panelAlpha = remember { Animatable(0f) }  // 面板背景 + 流水区淡入淡出（卡面恒不透明）
    // 下滑收起专用：面板背景/内容向下滑出（卡面层不跟，交给原接力动画）
    val panelExitY = remember { Animatable(0f) }
    var startOffsetPx by remember { mutableStateOf(0f) }
    // ⚠ 收回接力（2026-10-21 第四轮，CardLab 同构）：收回**不再**由面板里的卡飞完全程
    //   —— 面板层在卡堆之上，飞卡会盖着整列卡飘到落点再闪换成堆内真卡，真机读作
    //   「停在卡列表上面盖住上下两张、再隐现遮盖」。改为收回瞬间面板卡让位，堆内该卡
    //   自己按 relay 从面板位飞回槽位：全程处于堆内 z 序（被上下卡按静态关系遮挡），
    //   才是「插回卡堆」的观感。
    val relay = remember { Animatable(1f) }       // 1 = 面板位（静止初值），0 = 堆槽位（仅收回期）
    var closingGapPx by remember { mutableStateOf(0f) } // 接手瞬间卡面距自己槽位的位移
    var closingFlightStart by remember { mutableStateOf(1f) } // 接手瞬间的 flight（缩放/阴影折算）
    // 开卡同构（2026-10-21 第四轮，与 CardLab 对齐）：前段由**堆内卡**抬升（保持真实
    // z 序），到交接点后与面板卡在同一几何上交叉淡入——一上来就画面板卡会「整卡盖在
    // 列表上再飞」。面板卡透明度由 openReveal 控制，堆内卡取反。
    val openReveal = remember { Animatable(0f) }
    var openInStack by remember { mutableStateOf(false) }
    val openHandoffV = 0.28f             // 380ms 飞行的 ~106ms：面板底色还没压住列表
    // 卡片紧贴面板内容区顶部：与「管理/排序/预览」下方内容区对齐，
    // 不再留 24dp 顶距（原来会把下方流水区压缩 24dp）。
    val panelTopPad = 0.dp
    val panelTopPx = with(density) { panelTopPad.toPx() }
    // ⚠ 飞行坐标系锚点：量在**与 WalletPanel 同界**的 Box（VanPullRefresh 内容区）上。
    //   面板嵌在 Column > 顶栏 > Spacer(12) > VanPullRefresh > Box 里，若拿外层 Column 顶
    //   当锚点会少算「顶栏总高 + 12dp」⇒ 接力起点（面板位）与槽位差 ~60dp，交换帧跳位。
    //   ⚠ 2026-10-21 第四轮更正：真机「收回先停在卡堆上再隐现」的**主因不是**这 60dp，
    //   而是收回卡画在面板层（整列卡之上）飞完全程——见 relay 声明处；本锚点仍需保留，
    //   它保证接力卡 relay=1 时与面板卡逐像素重合。CardLab 浮层直接挂 Scaffold 顶层，天然同界。
    var stackTopPx by remember { mutableStateOf(0f) }
    val cardBounds = remember { mutableMapOf<String, Rect>() }

    // 展开的卡（含收回飞行期）：面板容器活到收回动画播完
    val panelActive = openId != null || closingId != null
    val allCards = displayCards
    val panelCard = (openId ?: closingId)?.let { id -> allCards.firstOrNull { it.id == id } }

    // ===== 流水缓存（per 卡，页面实例级）=====
    var flowCache by remember { mutableStateOf(mapOf<String, List<FlowRow>>()) }
    var flowLoading by remember { mutableStateOf(false) }

    // ===== 生命周期：ON_RESUME 重取（对齐 CardLab 的 resumeTick 机制）=====
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    var lastFetchAt by remember { mutableStateOf(0L) }

    suspend fun fetch() {
        // ⚠ 下拉刷新 / 首次进入 / ON_RESUME 可能并发撞上：同一时刻只跑一份。
        if (requesting) return
        requesting = true
        lastFetchAt = System.currentTimeMillis()
        try {
            val (banks, cardsResult, stats) = coroutineScope {
                val b = async {
                    when (val r = graph.category.list("bank")) {
                        is ApiResult.Ok -> r.data.orEmpty()
                        else -> emptyList()
                    }
                }
                val l = async { graph.card.list(null) }
                val s = async { graph.flow.cardsFlowStats(6) }
                Triple(b.await(), l.await(), s.await())
            }
            val newIconMap = banks.filter { it.iconUrl.isNotBlank() }
                .associate { it.id to AppConfig.fullFileUrl(it.iconUrl) }
            if (newIconMap != bankIconById) bankIconById = newIconMap
            if (banks != orgBanks) orgBanks = banks
            when (val r = cardsResult) {
                is ApiResult.Ok -> {
                    val list = r.data.orEmpty().filter { !it.isVirtual }
                    if (list != cards) cards = list
                }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "卡片加载失败" }, VanToastType.Fail)
                else -> toast.show("卡片加载失败", VanToastType.Fail)
            }
            when (val r = stats) {
                is ApiResult.Ok -> if (r.data != statById) statById = r.data.orEmpty()
                else -> {}
            }
            loaded = true
        } catch (t: Throwable) {
            // 异常也必须落 loaded/复位 requesting；刷新头的收尾由调用方 finally 负责。
            if (t is kotlinx.coroutines.CancellationException) throw t
            loaded = true
            toast.show("卡片加载失败", VanToastType.Fail)
        } finally {
            requesting = false
        }
    }

    LaunchedEffect(resumeTick) {
        // 首次必拉；ON_RESUME（从编辑/添加页返回）且距上次 >10s 才重取
        if (lastFetchAt > 0 && System.currentTimeMillis() - lastFetchAt < 10_000) return@LaunchedEffect
        fetch()
    }

    // 展开时拉该卡**近 3 天**流水（缓存命中不重拉）。明细的完整筛选/分页在独立的
    // CardFlowScreen（那是它的职责）；面板只做「最近发生了什么」的速览 + 尾部跳转入口
    //（2026-10-21 负负责人反馈：不要在面板里直接拉整月明细）。
    LaunchedEffect(openId) {
        val id = openId ?: return@LaunchedEffect
        if (flowCache.containsKey(id)) return@LaunchedEffect
        flowLoading = true
        val today = java.time.LocalDate.now()
        when (val r = graph.flow.listByCard(
            id, page = 1, limit = 50,
            startDate = today.minusDays(2).toString(),   // 近 3 天含今天
            endDate = today.toString(),                  // LocalDate.toString = yyyy-MM-dd（含边界天，同 CardFlowScreen）
        )) {
            is ApiResult.Ok -> {
                val rows = r.data.orEmpty().sortedByDescending { it.transDate }
                flowCache = flowCache + (id to rows)
            }
            else -> toast.show("流水加载失败", VanToastType.Fail)
        }
        flowLoading = false
    }

    // ===== 吸顶展开 / 收起（FLIP，无遮罩）=====
    // 起点位移 = 被点卡 root top − 面板卡面层 root top（后者 = stackTopPx + 24dp，锚点与
    // 面板同界 ⇒ 数学上落位帧与堆内槽位精确重合：槽位 top = stackTopPx+12dp+HEAD_STEP*i，
    // v=0 时显示位 = stackTopPx+24dp+startOffsetPx = 槽位 top ✓）
    fun open(card: Card) {
        val top = cardBounds[card.id]?.top ?: return
        startOffsetPx = top - (stackTopPx + panelTopPx)
        openId = card.id
        closingId = null
        openInStack = true
        scope.launch {
            // relay 复位到面板位：此时 closingId 必为 null（relay 分支未激活），不可见；
            // 下一次收回首帧就已经在面板位，不依赖收回协程里的同步时机
            relay.snapTo(1f)
            openReveal.snapTo(0f)
            flight.snapTo(0f)
            panelAlpha.snapTo(0f)
            panelExitY.snapTo(0f)
            launch { flight.animateTo(1f, tween(380, easing = MotionTokens.StandardEasing)) }
            panelAlpha.animateTo(1f, tween(300, easing = WalletEase))
        }
    }

    // 开卡交棒：堆内卡先按自身 z 序抬升，到 openHandoffV 后 80ms 交叉淡入面板卡。
    // openId 变 null（收起/跳明细）立即取消等待，避免上一次的 watcher 串到下一次开卡。
    LaunchedEffect(openId) {
        if (openId == null) {
            openInStack = false
            return@LaunchedEffect
        }
        snapshotFlow { flight.value }.first { it >= openHandoffV }
        openReveal.animateTo(1f, tween(80, easing = MotionTokens.StandardEasing))
        openInStack = false
    }

    fun close(bySwipe: Boolean = false) {
        val id = openId ?: return
        // 接手几何：面板卡位 = 槽位 − startOffset·flight（open 的插值）⇒ 堆内卡从
        // translationY = −startOffset·flight 起步才与面板卡逐像素重合；正常收回 flight=1
        // ⇒ −startOffset；展开中途收起也连续。
        closingGapPx = startOffsetPx * flight.value
        closingFlightStart = flight.value
        closingId = id
        openId = null
        openInStack = false
        scope.launch {
            // 开卡交叉淡入若还在跑，先归零（showCard=false 后不可见）
            openReveal.snapTo(0f)
            // relay 从 1 起步：堆内卡首帧就落在面板卡刚才的位置，与消失的面板卡无缝交换
            relay.snapTo(1f)
            if (bySwipe) {
                // 下滑收起：面板背景/内容层更快淡出（110ms）并向下滑 48dp（180ms），
                // 卡面接力仍走原 520ms —— 只加快“弹窗层消失”，不碰已调好的飞行参数。
                launch {
                    panelExitY.animateTo(
                        with(density) { 48.dp.toPx() },
                        tween(180, easing = WalletEase),
                    )
                }
                launch { panelAlpha.animateTo(0f, tween(110, easing = WalletEase)) }
            } else {
                // 面板背景/流水先退（160ms）：不透明底必须在大半段飞行之前让开，否则堆内
                // 接力卡被盖住、等背景退完只剩落位帧 ⇒ 又回到「隐现」。卡面本身在堆里飞 520ms
                launch { panelAlpha.animateTo(0f, tween(160, easing = WalletEase)) }
            }
            // 520ms：前段慢（背景退完时卡才走 1/3，插入过程全程可见）、尾段冲过槽位回弹
            relay.animateTo(0f, tween(520, easing = WalletRelayEasing))
            if (closingId == id) closingId = null
            panelExitY.snapTo(0f)
            // 面板卡已让位（showCard=false），这里归零不可见；保证下次 open 首帧就在槽位，
            // 不会沿用 flight=1 在顶部闪一帧（旧版 close 自己会飞回 0，接力后不再需要）
            flight.snapTo(0f)
        }
    }

    BackHandler(enabled = panelActive) { close() }
    // 排序模式没有面板，返回键负责退出模式（面板优先，两者互斥）
    BackHandler(enabled = sortMode && !panelActive) { sortMode = false }

    // ===== 排序提交（排序列表拖右侧把手松手后）=====
    // ⚠ 拖拽发生在**单类型视图**（filtered），ordered 只是该类型卡的新序——直接写回 cards
    //   会把另一类型的卡全清掉！合并法：遍历全量，同类卡依次从 ordered 取、异类保持原位
    //   ⇒ 该类型新相对序嵌入原混排结构（updateSortBatch 提交的仍是全量 1-N）。
    fun commitOrder(ordered: List<Card>) {
        val type = filter
        // 预览态下 ordered 是脱敏副本：必须按 id 映射回真实卡再合并，否则会把伪卡号写进 cards
        val byId = cards.associateBy { it.id }
        val realOrdered = ordered.map { byId[it.id] ?: it }
        var i = 0
        val merged = cards.map { c ->
            if (c.cardType == type && i < realOrdered.size) realOrdered[i++] else c
        }
        cards = merged
        scope.launch {
            when (val r = graph.card.updateSortBatch(merged.mapIndexed { idx, c -> c.id to (idx + 1) })) {
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "排序保存失败" }, VanToastType.Fail)
                else -> {}
            }
        }
    }

    // 查看全部明细（面板尾部入口）：直接收面板（不播收回动画）+ 跳独立明细页
    fun goFlow(id: String) {
        openId = null
        closingId = null
        openInStack = false
        scope.launch {
            openReveal.snapTo(0f)
            flight.snapTo(0f)
            panelAlpha.snapTo(0f)
        }
        nav.navigate(Routes.cardFlow(id))
    }

    // ===== 筛选（只有 debit/credit 两态）=====
    val filtered = remember(displayCards, filter) {
        if (filter == "credit") displayCards.filter { it.cardType == "credit" } else displayCards.filter { it.cardType == "debit" }
    }
    // 卡组织 logo：web `getCardOrgInfo` 语义 —— 在 bank 分类里按名称匹配 icon_url，
    // 有资源图就显示图，无/失败才展示内置 CardOrgBadge（逻辑在 BankCardFace）。
    val orgIconOf: (Card) -> String = remember(orgBanks) {
        { c -> cardOrgIconUrlOf(c.cardOrg, orgBanks) }
    }

    // ===== 列表滚动 → 底部添加钮自动收起/唤出（4px 死区，同 CardLab）=====
    val contentScroll = rememberScrollState()
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(contentScroll) {
        var last = contentScroll.value
        snapshotFlow { contentScroll.value }.collect { v ->
            val d = v - last
            if (d > 4) scrolled = true else if (d < -4) scrolled = false
            if (d > 4 || d < -4) last = v
        }
    }

    // ===== 下拉刷新 =====
    val refreshState = rememberVanPullRefreshState()

    ScreenScaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .then(inner)
                .background(colors.bgCard),
        ) {
            // ===== 顶栏：标题 + 右侧动作区 =====
            // 展开时动作区变成「收起」按钮（明显的收起入口，2026-10-21 负责人要求）；
            // 收回动画播完（panelActive=false）才交叉淡入回借记卡/信用卡切换。
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText("卡包", 20f, FontWeight.Bold, colors.textPrimary)
                Spacer(Modifier.weight(1f))
                androidx.compose.animation.AnimatedContent(
                    targetState = panelActive,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(tween(200)) togetherWith
                            androidx.compose.animation.fadeOut(tween(200)))
                    },
                    label = "walletTopAction",
                ) { active ->
                    if (active) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.primary)
                                .clickable { close() }
                                .padding(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon("down", size = 14.sp, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            FText("收起", 13f, FontWeight.SemiBold, Color.White)
                        }
                    } else {
                        // web：排序模式下隐藏借记/信用分段，避免整理时切堆
                        if (!sortMode) WalletFilterChips(filter = filter, onChange = { filter = it })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 第二排：排序（模式）/ 预览（脱敏）——展开面板时淡出但**保留高度**，
            // 打开/收起不改变内容区起点，卡面接力几何不受影响
            val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
                if (panelActive) 0f else 1f,
                tween(200),
                label = "walletControls",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp)
                    .graphicsLayer { alpha = controlsAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(
                    if (sortMode) "拖动右侧把手排序" else "管理你的银行卡",
                    14f, color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                WalletTogglePill("排序", on = sortMode, enabled = !panelActive) {
                    sortMode = !sortMode
                }
                if (!sortMode) {
                    Spacer(Modifier.width(8.dp))
                    WalletTogglePill("预览", on = previewMode, enabled = !panelActive) {
                        val next = !previewMode
                        if (next) previewSeed = System.currentTimeMillis()
                        previewMode = next
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            VanPullRefresh(
                state = refreshState,
                // 与动态页一致：只有列表/卡堆在顶部时下拉才触发刷新
                isChildAtTop = { contentScroll.value == 0 },
                onRefresh = {
                    ApiGetCache.clear() // 下拉刷新必须绕过 GET 缓存
                    scope.launch {
                        try {
                            fetch()
                        } finally {
                            // 无论成功/失败/异常/取消都必须收刷新头，避免「一直在刷新中...」
                            refreshState.finishRefresh()
                        }
                    }
                },
                disabled = panelActive || sortMode || !loaded,
            ) {
                // ⚠ 锚点量在这里（与 WalletPanel 根 Box 同界）——flight 起落位才与堆内
                //   槽位精确重合，详见 stackTopPx 声明处的坐标系注释
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { stackTopPx = it.boundsInRoot().top },
                ) {
                    // ===== 卡堆（常驻；展开面板盖在其上）=====
                    when {
                        // 首载：叠好的骨架卡（与卡堆同几何），数据到达后在 WalletStack 内整堆淡入
                        !loaded -> CardStackSkeleton(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                            cardHeight = CARD_HEIGHT,
                            step = HEAD_STEP,
                        )
                        filtered.isNotEmpty() -> {
                            if (sortMode) {
                                // web `CardListPane` 排序态：简洁列表 + 右侧把手拖拽（拖完一次性提交）。
                                // 堆内不再挂长按拖拽 ⇒ 普通浏览时不会误触排序。
                                WalletSortList(cards = filtered, onCommit = { commitOrder(it) })
                            } else {
                                WalletStack(
                                    cards = filtered,
                                    refreshKey = filter,
                                    bankIconById = bankIconById,
                                    orgIconOf = orgIconOf,
                                    bounds = cardBounds,
                                    clickEnabled = !panelActive,
                                    scrollState = contentScroll,
                                    openedId = openId,
                                    closingId = closingId,
                                    closingGapPx = closingGapPx,
                                    closingFlightStart = closingFlightStart,
                                    relay = relay,
                                    flight = flight,
                                    openedInStack = openInStack,
                                    openReveal = openReveal,
                                    openStartPx = startOffsetPx,
                                    onOpen = { open(it) },
                                )
                            }
                        }
                        else -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(
                                description = if (filter == "credit") "暂无信用卡" else "暂无借记卡",
                            )
                        }
                    }

                    // ===== 底部固定「添加银行卡」：不在滚动列表里，随下滑收起、上滑/回顶唤出 =====
                    val addHidden = !loaded || panelActive || sortMode || scrolled
                    val addHide by androidx.compose.animation.core.animateFloatAsState(
                        if (addHidden) 1f else 0f,
                        tween(220, easing = MotionTokens.StandardEasing),
                        label = "walletAddHide",
                    )
                    val addHidePx = with(density) { 116.dp.toPx() }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp)
                            .graphicsLayer {
                                alpha = 1f - addHide
                                translationY = addHide * addHidePx
                            },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .cssShadow(
                                    28.dp,
                                    WebShadow(0f, 8f, 24f, colors.primary.copy(alpha = 0.35f)),
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(colors.primary)
                                .clickable(enabled = !addHidden) {
                                    navController.navigate(Routes.CARD_ADD)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VanIcon("plus", size = 18.sp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                FText("添加银行卡", 18f, FontWeight.SemiBold, Color.White)
                            }
                        }
                    }

                    // ===== 吸顶展开面板（无遮罩；不透明背景接管页面）=====
                    if (panelActive && panelCard != null) {
                        WalletPanel(
                            card = panelCard,
                            bankIconUrl = bankIconById[panelCard.bankId].orEmpty(),
                            orgIconUrl = orgIconOf(panelCard),
                            stat = statById[panelCard.id],
                            rows = flowCache[panelCard.id],
                            rowsLoading = flowLoading,
                            flight = flight,
                            panelAlpha = panelAlpha,
                            panelExitY = panelExitY,
                            startOffsetPx = startOffsetPx,
                            cardReveal = openReveal,
                            // 收回期卡面由堆内接力渲染，面板卡必须立即让位（同 CardLab overlayCard）
                            showCard = openId != null,
                            onSwipeDismiss = { close(bySwipe = true) },
                            onEdit = {
                                close()
                                navController.navigate(Routes.cardEdit(panelCard.id))
                            },
                            onAll = { goFlow(panelCard.id) },
                        )
                    }
                }
            }
        }
    }
}

/** CSS `ease` = cubic-bezier(0.25,0.1,0.25,1)：面板淡入淡出曲线。 */
private val WalletEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * 收回接力曲线（2026-10-21 第四轮定）：**慢起 + 尾段轻微冲过槽位再回弹**。
 *
 * 为什么不用 CardLab 的 `cubic-bezier(.34,1.56,.64,1)`：那条曲线前 25% 时间就走完 ~75%
 * 位移——CardLab 有半透明遮罩，全程看得见；本页面板是**不透明**整页接管，等背景淡完
 * （~160ms）卡已接近落位，看上去还是「隐现」。这里把前段压慢（160ms 仅 ~34%），
 * 让列表显影后还能完整看到卡滑进卡堆；尾段 1.2 控制点保留 ~2% 回弹。
 */
private val WalletRelayEasing = CubicBezierEasing(0.5f, 0.1f, 0.3f, 1.2f)

/** 卡高（CardStackView.CARD_H = 220px 的本文件 Dp 版；那张常量是 private 只能重声明）。 */
private val CARD_HEIGHT = 220.dp

/** 堆叠卡头露出步进（Apple Wallet 风：整卡 220dp，每张露出 64dp = 20 顶距 + 40 银行行 + 缓冲）。 */
private val HEAD_STEP = 64.dp

// ============================================================================
// 排序模式：web `CardListPane` 同款「简洁列表 + 右侧把手拖拽」
// ============================================================================

/**
 * 排序列表（对齐 web `CardListPane.vue` 的 `.sort-list`）：
 * 40dp 行、圆角 12、bgThird、左侧银行名 15sp + 尾号 14sp、右侧 bars 把手。
 * **只有把手响应拖拽**（`detectDragGestures` 直接挂在 40dp 热区上，不需要长按），
 * 普通浏览态不渲染本组件、卡堆也不挂拖拽 ⇒ 从根上消除误触。
 */
@Composable
private fun WalletSortList(
    cards: List<Card>,
    onCommit: (List<Card>) -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    // 行高 40 + 间距 2：拖拽换位按整个 pitch 计算（与 web `.sort-item{height:40;margin-bottom:2}` 一致）
    val rowPitchPx = with(density) { 42.dp.toPx() }
    var order by remember(cards) { mutableStateOf(cards) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        order.forEachIndexed { index, card ->
            val isDragging = card.id == draggingId
            val shiftPx = if (isDragging) dragOffsetPx - (index - dragStartIndex) * rowPitchPx else 0f
            key(card.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .offset { IntOffset(0, shiftPx.roundToInt()) }
                        .cssShadow(12.dp, WebShadow(0f, 1f, 4f, Color.Black.copy(alpha = 0.05f)))
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgThird)
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                                alpha = 0.95f
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).padding(start = 20.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        FText(card.bankName, 15f, FontWeight.SemiBold, colors.textPrimary, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        FText(card.last4, 14f, color = colors.textSecondary, letterSpacing = 1.sp)
                    }
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .pointerInput(card.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = card.id
                                        dragStartIndex = order.indexOfFirst { it.id == card.id }
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val id = draggingId ?: return@detectDragGestures
                                        dragOffsetPx += delta.y
                                        val to = (dragStartIndex + (dragOffsetPx / rowPitchPx).roundToInt())
                                            .coerceIn(0, order.lastIndex.coerceAtLeast(0))
                                        val cur = order.indexOfFirst { it.id == id }
                                        if (to != cur && cur >= 0) {
                                            val m = order.toMutableList()
                                            m.add(to, m.removeAt(cur))
                                            order = m
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffsetPx = 0f
                                        onCommit(order)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffsetPx = 0f
                                        onCommit(order)
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

// ============================================================================
// 卡堆：混排单堆（排序在独立的排序模式里做，堆内不挂长按拖拽）
// ============================================================================

@Composable
private fun WalletStack(
    cards: List<Card>,
    refreshKey: String,
    bankIconById: Map<String, String>,
    orgIconOf: (Card) -> String,
    bounds: MutableMap<String, Rect>,
    /** 面板展开/收回期禁用点卡（面板自己接管触摸）。 */
    clickEnabled: Boolean,
    /** 由页面持有：底部添加钮要根据滚动方向自动收起/唤出。 */
    scrollState: ScrollState,
    /** 面板展开的那张：开卡前段由本堆内渲染（openedInStack），交接后只留占位。 */
    openedId: String?,
    /** 收回中的那张：由**本堆内**接力飞回（见 close() 注释）。 */
    closingId: String?,
    closingGapPx: Float,
    closingFlightStart: Float,
    relay: Animatable<Float, *>,
    flight: Animatable<Float, *>,
    /** true = 开卡前段：堆内卡按自身 z 序抬升，交接后为 false。 */
    openedInStack: Boolean,
    openReveal: Animatable<Float, *>,
    openStartPx: Float,
    onOpen: (Card) -> Unit,
) {
    val vantDimens = vantDimens
    val cardShape = RoundedCornerShape(20.dp)

    // 渐进入场（2026-10-22 改「骨架 + 整堆淡入」，同 CardLab）：
    // 仍分批组合（每帧 4 张）防首帧掉帧，但整堆先 alpha=0，组合完 220ms 整体淡入；
    // 期间盖同几何骨架卡 ⇒ 不再逐张蹦出、容器高度也不随 shown 长高。
    // ⚠ 开卡/收回的 flight/relay 动画不在这一层，未动。
    var revealCount by remember(refreshKey) { mutableStateOf(0) }
    LaunchedEffect(cards.size, refreshKey) {
        while (revealCount < cards.size) {
            revealCount = (revealCount + 4).coerceAtMost(cards.size)
            if (revealCount < cards.size) delay(16)
        }
    }
    val shown = revealCount.coerceAtMost(cards.size)
    val ready = cards.isNotEmpty() && shown >= cards.size
    var hasEntered by remember(refreshKey) { mutableStateOf(false) }
    var skeletonVisible by remember(refreshKey) { mutableStateOf(true) }
    // 用 Animatable 而不是 animateFloatAsState：切筛选时 ready 立刻变 false，
    // 先 snapTo(0) 让旧堆当帧消失（避免 1→0 的淡出再淡入＝双段动画），再等组合完淡入。
    val stackAlpha = remember { Animatable(0f) }
    LaunchedEffect(ready, refreshKey) {
        if (!ready) {
            if (!hasEntered) {
                stackAlpha.snapTo(0f)
                skeletonVisible = true
            }
        } else {
            if (!hasEntered) {
                stackAlpha.snapTo(0f)
                stackAlpha.animateTo(1f, tween(220, easing = MotionTokens.StandardEasing))
            }
            hasEntered = true
            skeletonVisible = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
    ) {
        // 高度按全量算：组合期间不随 shown 长高
        Box(Modifier.fillMaxWidth().height(CARD_HEIGHT + HEAD_STEP * (cards.size - 1).coerceAtLeast(0))) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = stackAlpha.value }) {
            cards.forEachIndexed { index, card ->
                if (index >= shown) return@forEachIndexed
                val isClosing = card.id == closingId
                val animated by animateDpAsState(
                    HEAD_STEP * index,
                    tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing),
                    label = "walletStackPos$index",
                )
                key(card.id) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                        .offset { IntOffset(0, animated.roundToPx()) }
                            .zIndex(index.toFloat())
                            .onGloballyPositioned { bounds[card.id] = it.boundsInRoot() }
                            .graphicsLayer {
                                if (isClosing) {
                                    // 收回接力：translationY 相对**自己槽位**——relay=1 时恰好
                                    // 落在面板卡收回瞬间的位置（正常收回=面板位；中途收起=当前
                                    // 飞行位），relay=0 落回槽位；曲线尾段让 relay 略小于 0 ⇒
                                    // 冲过槽位再回弹。不提 zIndex：卡在堆内声明序里飞，上下卡
                                    // 按静态关系遮挡它 ⇒ 真「插回」，而不是盖在列表表面飞
                                    val p = relay.value
                                    val v0 = closingFlightStart
                                    translationY = -closingGapPx * p
                                    // 缩放/阴影也按接手时的 flight 折算：展开动画中途点收起时
                                    // （v0<1）与面板卡的当前缩放/阴影同值，交换帧零跳变
                                    val s = 1f + 0.03f * v0 * p
                                    scaleX = s
                                    scaleY = s
                                    // 阴影随落位渐入到静态 6dp；正常收回 v0=1 ⇒ 起点 0
                                    shadowElevation = (1f - v0 * p).coerceIn(0f, 1f) * 6.dp.toPx()
                                    alpha = 1f
                                    shape = cardShape
                                    clip = false
                                } else if (card.id == openedId && openedInStack) {
                                    // 开卡前段：堆内卡按自身 z 序抬升（与面板卡同一公式），
                                    // 被上下卡按静态关系遮挡；到交接点再交叉淡入面板卡
                                    val d = flight.value
                                    translationY = -openStartPx * d
                                    val s = 1f + 0.03f * d
                                    scaleX = s
                                    scaleY = s
                                    alpha = (1f - openReveal.value).coerceIn(0f, 1f)
                                    // 与面板卡同式：v=0 6dp → v=1 0dp，交接帧同值
                                    shadowElevation = (1f - d).coerceIn(0f, 1f) * 6.dp.toPx()
                                    shape = cardShape
                                    clip = false
                                } else {
                                    // 静止态必须显式复位：layer 属性会跨分支保留——收回卡的
                                    // 6dp 外阴影不清掉会永久留在那一张上
                                    translationY = 0f
                                    scaleX = 1f
                                    scaleY = 1f
                                    alpha = 1f
                                    shadowElevation = 0f
                                }
                            }
                            .clip(cardShape)
                            .background(Color.Transparent)
                            // 展开/收回期面板接管触摸；普通浏览只负责点开卡（排序在独立模式里做）
                            .clickable(enabled = clickEnabled && ready) { onOpen(card) },
                    ) {
                        if (card.id == openedId) {
                            if (openedInStack) {
                                // 开卡前段：与列表静态卡同面；交接时淡出，面板卡淡入
                                BankCardFace(
                                    card = card,
                                    cardTypeLabel = if (card.cardType == "credit") "CREDIT CARD" else "DEBIT CARD",
                                    showLast4Suffix = true,
                                    bankIconUrl = bankIconById[card.bankId].orEmpty(),
                                    orgIconUrl = orgIconOf(card),
                                    defaultColor = if (card.cardType == "credit") Color(0xFFEE0A24) else Color(0xFF4A90E2),
                                    flat = true,
                                )
                            } else {
                                // 展开卡已交给面板渲染，槽位留同高占位（不改变堆布局）
                                Spacer(Modifier.height(CARD_HEIGHT))
                            }
                        } else {
                            BankCardFace(
                                card = card,
                                cardTypeLabel = if (card.cardType == "credit") "CREDIT CARD" else "DEBIT CARD",
                                showLast4Suffix = true,
                                bankIconUrl = bankIconById[card.bankId].orEmpty(),
                                orgIconUrl = orgIconOf(card),
                                defaultColor = if (card.cardType == "credit") Color(0xFFEE0A24) else Color(0xFF4A90E2),
                                // 收回期阴影交给外层 graphicsLayer 渐变；落地 relay=0 恢复静态 6dp
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
                modifier = Modifier.graphicsLayer { alpha = (1f - stackAlpha.value).coerceIn(0f, 1f) },
                cardHeight = CARD_HEIGHT,
                step = HEAD_STEP,
                count = cards.size.coerceAtMost(3),
            )
        }
        }

        // 底部留白：给页面级固定「添加银行卡」钮让位（钮高 56 + bottom 30 + 余量）
        Spacer(Modifier.height(100.dp))
    }
}

// ============================================================================
// 吸顶展开面板：卡面 FLIP 飞顶 + 流水内嵌（零跳转核心）
// ============================================================================

@Composable
private fun WalletPanel(
    card: Card,
    bankIconUrl: String,
    orgIconUrl: String,
    stat: Pair<Int, Int>?,
    rows: List<FlowRow>?,
    rowsLoading: Boolean,
    flight: Animatable<Float, *>,
    panelAlpha: Animatable<Float, *>,
    /** 下滑收起专用：背景/内容层向下滑出（卡面层不跟，接力动画不变）。 */
    panelExitY: Animatable<Float, *>,
    startOffsetPx: Float,
    /** 开卡交接：堆内卡淡出（1-reveal）↔ 面板卡淡入（reveal），80ms 交叉。 */
    cardReveal: Animatable<Float, *>,
    /** false = 收回中：卡面已交给堆内接力，面板只留背景/流水退场（同 CardLab overlayCard）。 */
    showCard: Boolean,
    onSwipeDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAll: () -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 80.dp.toPx() }
    val flowListState = rememberLazyListState()
    val currentOnSwipeDismiss by rememberUpdatedState(onSwipeDismiss)
    // 面板根在窗口内的 top / 流水列表在窗口内的 top：用来判断手势起点是否落在
    // 「卡片+头部区」（列表之上）。列表已滚动时从列表上下滑不能收起，交给列表自己滚。
    var panelTopInRoot by remember { mutableStateOf(0f) }
    var flowTopInRoot by remember { mutableStateOf(Float.NaN) }
    var flowBottomInRoot by remember { mutableStateOf(Float.NaN) }

    // 三层分层（2026-10-21 重构）：
    //   背景层（不透明底色，随 panelAlpha 淡入）→ 内容层（信息条+流水，淡入+轻上移）
    //   → 卡面层（flight FLIP，**不乘 panelAlpha**——飞行全程清晰可见）。
    // 旧版整块 Column 乘 alpha ⇒ 展开瞬间卡面也被压成半透明跟着背景一起渐显，飞行观感脏。
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { panelTopInRoot = it.boundsInRoot().top }
            // 下滑收起：只在 Initial pass **观察、不消费**（不抢列表滚动/按钮点击）。
            // 起点在卡片/头部区，或流水列表已在顶部 ⇒ 下滑超过 80dp 触发下滑退场；
            // 背景/内容层快速下滑淡出，卡面接力仍走原有 520ms 动画。
            .pointerInput(flowListState) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val startedAtTop = flowListState.firstVisibleItemIndex == 0 &&
                        flowListState.firstVisibleItemScrollOffset == 0
                    val flowTopLocal = if (flowTopInRoot.isFinite()) {
                        flowTopInRoot - panelTopInRoot
                    } else {
                        Float.NaN
                    }
                    val flowBottomLocal = if (flowBottomInRoot.isFinite()) {
                        flowBottomInRoot - panelTopInRoot
                    } else {
                        Float.NaN
                    }
                    // 起点在流水区之外（卡片/头部/底部按钮）⇒ 下滑可直接收起；
                    // 起点在流水区内 ⇒ 只有列表已在顶部时才收起，否则交给列表滚。
                    val outsideFlow = if (flowTopLocal.isFinite() && flowBottomLocal.isFinite()) {
                        down.position.y < flowTopLocal || down.position.y > flowBottomLocal
                    } else {
                        true
                    }
                    var endDownPx = 0f
                    var completed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // 用**抬手时**的净位移判定：下拉后又推回去不收起
                        endDownPx = change.position.y - down.position.y
                        if (!change.pressed) {
                            completed = true
                            break
                        }
                    }
                    if (completed && (outsideFlow || startedAtTop) && endDownPx >= dismissThresholdPx) {
                        currentOnSwipeDismiss()
                    }
                }
            },
    ) {
        // 背景层：**拦截全部触摸**（穿透 bug 修复核心）。旧版面板只有 graphicsLayer +
        // background——无 pointerInput 的布局节点不参与命中测试 ⇒ 触摸直接落到下层卡堆
        // （loading 层是故意利用这个特性，面板忘了拦 = 穿透）。卡堆在兄弟子树，Compose
        // 无事件冒泡 ⇒ 本层命中后下层永远收不到。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = panelAlpha.value
                    translationY = panelExitY.value
                }
                .background(colors.bgCard)
                .pointerInput(Unit) { detectTapGestures { } },
        )

        // 内容层：卡面下方起排（卡面 220dp + 12dp 间距），随 panelAlpha 淡入 + 12dp 上移
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val v = panelAlpha.value
                    alpha = v
                    translationY = (1f - v) * 12.dp.toPx() + panelExitY.value
                },
        ) {
            Spacer(Modifier.height(232.dp))

            // ===== 信息条：别名 / 近6月笔数 / 管理 =====
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(card.displayName, 16f, FontWeight.SemiBold, colors.textPrimary, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                if (stat != null) {
                    val (expense, income) = stat
                    FText("近6月 支出${expense}笔 收入${income}笔", 12f, color = colors.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(colors.bgPage)
                        .clickable { onEdit() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon("setting-o", size = 14.sp, color = colors.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    FText("管理", 14f, FontWeight.SemiBold, colors.textPrimary)
                }
            }

            // ===== 近 3 天流水速览：固定高度区域（面板内 weight），仅列表内部滚动 =====
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                when {
                    rowsLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            VanLoading(size = 24.sp, color = colors.primary, type = VanLoadingType.Circular)
                        }
                    }
                    rows == null -> {}
                    else -> {
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned {
                                    flowTopInRoot = it.boundsInRoot().top
                                    flowBottomInRoot = it.boundsInRoot().bottom
                                },
                            state = flowListState,
                        ) {
                            if (rows.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                        FText("近 3 天无动账", 13f, color = colors.textSecondary)
                                    }
                                }
                            } else {
                                val today = java.time.LocalDate.now()
                                val groups = rows.groupBy { it.transDate }.toSortedMap(compareByDescending { it })
                                groups.forEach { (date, dayRows) ->
                                    item(key = "d_$date") {
                                        val label = when (date) {
                                            today.toString() -> "今天"
                                            today.minusDays(1).toString() -> "昨天"
                                            today.minusDays(2).toString() -> "前天"
                                            else -> "${date.take(5).replace("-", "月")}日"
                                        }
                                        val dayExpense = dayRows.filter { it.isExpense }.sumOf { it.amount }
                                        // 日期分组头：比流水行更大字号 + bgThird 色带 + 主色竖条，
                                        // 滚动时一眼区分「今天/昨天/前天」与交易行。
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.bgThird)
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .height(16.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(colors.primary),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            FText(label, 16f, FontWeight.Bold, colors.textPrimary)
                                            Spacer(Modifier.width(6.dp))
                                            FText("${dayRows.size}笔", 12f, color = colors.textTertiary)
                                            Spacer(Modifier.weight(1f))
                                            FText("支出 ¥${Money.format(dayExpense)}", 13f, color = colors.textSecondary)
                                        }
                                    }
                                    items(dayRows, key = { it.id }) { row -> WalletFlowRow(row) }
                                }
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
                }
            }

            // ===== 固定底部：查看全部明细（不随流水滚动）=====
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.bgPage)
                    .clickable { onAll() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FText("查看全部明细", 15f, FontWeight.SemiBold, colors.primary)
                Spacer(Modifier.width(4.dp))
                VanIcon("arrow", size = 16.sp, color = colors.primary)
            }

            // 底部收起提示：向下箭头 + 小字（静态提示，不做循环动画）
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                VanIcon("arrow-down", size = 12.sp, color = colors.textTertiary)
                Spacer(Modifier.width(4.dp))
                FText("向下滑动收起", 12f, FontWeight.Normal, colors.textTertiary)
            }
        }

        // 收回期触摸闸：面板内容已淡出（alpha≈0）但仍在树上，若不加这层，点到原「管理/
        // 查看全部明细」热区仍会触发（透明不等于不可点）。只拦点击，不拦滚动。
        if (!showCard) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }

        // ===== 吸顶卡面（FLIP：从堆槽位飞来；最后声明 = 最上层）=====
        // 收起只支持三种：顶部「收起」按钮 / 下滑手势 / 系统返回键；点卡面不再收起。
        // 收回期 showCard=false：卡面立即让位给堆内接力（否则面板卡会盖着列表飞完全程，
        // 落地才闪换成真卡 = 真机「停在列表上再隐现」）。
        // ⚠ cardShape 先落局部变量：graphicsLayer 的 receiver（GraphicsLayerScope）自带
        //   shape 同名成员，直接写会解析到 receiver（CardLab 踩过的坑）
        if (showCard) {
            val cardShape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp)
                    .graphicsLayer {
                        val v = flight.value
                        translationY = startOffsetPx * (1f - v)
                        val s = 1f + 0.03f * v
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        // 交接点前透明：与堆内卡同几何，靠 80ms 交叉淡入接力
                        alpha = cardReveal.value
                        // 阴影随 flight 渐变：v=0（槽位）= 堆内静态 6dp、v=1（吸顶）= 0
                        //（flat 掐了 BankCardFace 内部静态阴影）——起飞时阴影渐消；收回
                        // 由堆内接力卡接管，relay 同步 0→6dp，落位与静态卡同值交接
                        shadowElevation = (1f - v).coerceIn(0f, 1f) * 6.dp.toPx()
                        shape = cardShape
                        clip = false
                    },
            ) {
                BankCardFace(
                    card = card,
                    cardTypeLabel = if (card.cardType == "credit") "CREDIT CARD" else "DEBIT CARD",
                    showLast4Suffix = true,
                    bankIconUrl = bankIconUrl,
                    orgIconUrl = orgIconUrl,
                    defaultColor = if (card.cardType == "credit") Color(0xFFEE0A24) else Color(0xFF4A90E2),
                    flat = true,
                )
            }
        }
    }
}

@Composable
private fun WalletFlowRow(row: FlowRow) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FText(row.payType.ifBlank { row.categoryName }, 15f, FontWeight.Medium, colors.textPrimary, maxLines = 1)
            if (row.remark.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                FText(row.remark, 12f, color = colors.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            FText(
                Money.signed(row.amount, row.isIncome),
                15f, FontWeight.SemiBold,
                if (row.isIncome) Color(0xFF07C160) else Color(0xFFEE0A24),
            )
            Spacer(Modifier.height(2.dp))
            FText(row.time, 11f, color = colors.textSecondary)
        }
    }
}

// ============================================================================
// 筛选 chip
// ============================================================================

@Composable
private fun WalletFilterChips(filter: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 只有两态（无「全部」：50-100 张卡的用户点全部一次性组合整堆很费资源）
        listOf("debit" to "借记卡", "credit" to "信用卡").forEach { (k, label) ->
            val selected = filter == k
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    // 与 CardLab 分段 item 同配置：未选中 bgThird + 次要色，选中主色 + 白字
                    .background(if (selected) colors.primary else colors.bgThird)
                    .clickable { onChange(k) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText(
                    label,
                    14f,
                    FontWeight.SemiBold,
                    if (selected) Color.White else colors.textSecondary,
                )
            }
        }
    }
}

/** 排序 / 预览胶囊（对齐 web `.preview-toggle` + `.dot`：13px 标签 + 18dp 空心圆环）。 */
@Composable
private fun WalletTogglePill(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .cssShadow(20.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgThird)
            .clickable(enabled = enabled) { onClick() }
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

/** 稳定伪随机数字串（预览脱敏，同 CardLabScreen/CardManageScreen 的实现）。 */
private fun pseudoDigits(n: Int, seed: Long): String {
    var s = if (seed == 0L) 1L else seed
    val sb = StringBuilder()
    repeat(n.coerceAtLeast(0)) {
        s = s * 6364136223846793005L + 1442695040888963407L
        sb.append((s.ushr(33) % 10L).toInt())
    }
    return sb.toString()
}