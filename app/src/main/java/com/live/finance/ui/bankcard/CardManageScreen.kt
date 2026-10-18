package com.live.finance.ui.bankcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanLoadingType
import com.live.vant.icon.VanIcon
import com.live.vant.theme.MotionTokens
import com.live.vant.theme.vantDimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType

/** web `debit.vue:33` `default-color="#4A90E2"` / `credit.vue:33` `default-color="var(--theme-danger-color,#ee0a24)"`。 */
private val CARD_DEFAULT_DEBIT = Color(0xFF4A90E2)
private val CARD_DEFAULT_CREDIT = Color(0xFFEE0A24)

/**
 * 银行卡管理（壳层 + 借记卡/信用卡列表）—— 一比一复刻 web：
 * `views/BankCard/index.vue`（顶栏：标题 / 借记卡·信用卡分段切换 / 排序·预览开关）
 * + `views/BankCard/debit.vue`、`credit.vue`（卡片堆叠 / 排序拖拽 / 预览脱敏 / 查看消费明细）。
 *
 * 路由：`Routes.CARD`（默认借记卡）、`Routes.CARD_DEBIT`、`Routes.CARD_CREDIT`。
 *
 * 遗留：web 的「近6个月无动账」提示依赖 `GET /account/stats/cards-flow`，原生暂无该仓储 → 未接。
 */
@Composable
fun CardManageScreen(nav: NavHostController, initialTab: String = "debit") {
    val context = LocalContext.current
    val vanToast = LocalVanToastController.current
    val graph = App.of(context).graph
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var bankNameById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var bankIconById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    // 缓存：同一 tab 的卡列表 / 全局银行字典 —— 短时切回已加载过的 tab 即时展示，
    // 不重拉接口、不在背景列表重新试探拉取卡面/银行 SVG（配合 App.kt 的 Coil 内存缓存）。
    var cardsCache by remember { mutableStateOf<Map<String, List<Card>>>(emptyMap()) }
    var banksCache by remember { mutableStateOf<Pair<Map<String, String>, Map<String, String>>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    // web `debit.vue` 的 `cardFlowIdle`：选中卡片后查「近 6 个月该卡是否 0 笔支出且 0 笔收入」，
    // 为真则在「查看消费明细」按钮下方显示 `.flow-empty-tip`「近6个月无动账」。
    // ⚠ 旧实现整个漏了这个提示（接口 `graph.flow.cardsFlowStats(6)` 本来就已存在）。
    var cardFlowIdle by remember { mutableStateOf(false) }
    var scrolled by remember { mutableStateOf(false) }
    var previewSeed by remember { mutableStateOf(0L) }
    val scrollState = rememberScrollState()

    // ===== 数据加载 =====
    suspend fun loadCards(silent: Boolean) {
        if (!silent) loading = true
        when (val res = graph.card.list(tab)) {
            is ApiResult.Ok -> {
                val data = res.data.orEmpty()
                cards = data
                cardsCache = cardsCache + (tab to data)
                SilentRefreshThrottle.markLoaded(tab)
            }
            is ApiResult.Fail -> {
                if (!silent) vanToast.show(res.message.ifBlank { "加载失败" })
                cards = cardsCache[tab] ?: emptyList()
            }
            else -> cards = cardsCache[tab] ?: emptyList()
        }
        if (!silent) loading = false
    }

    // 后台静默刷新：不转圈、不阻塞；命中 Coil 内存缓存 ⇒ 卡面/银行 SVG 不会重新走网络试探。
    suspend fun silentRefresh(t: String) {
        when (val res = graph.card.list(t)) {
            is ApiResult.Ok -> {
                val data = res.data.orEmpty()
                cardsCache = cardsCache + (t to data)
                if (tab == t) cards = data
                SilentRefreshThrottle.markLoaded(t)
            }
            else -> Unit
        }
    }

    suspend fun loadBanks() {
        val cached = banksCache
        if (cached != null) {
            bankNameById = cached.first
            bankIconById = cached.second
            return
        }
        when (val res = graph.category.list("bank")) {
            is ApiResult.Ok -> {
                val list = res.data.orEmpty()
                val names = list.associate { it.id to it.name }
                val icons = list.filter { it.iconUrl.isNotBlank() }
                    .associate { it.id to AppConfig.fullFileUrl(it.iconUrl) }
                bankNameById = names
                bankIconById = icons
                banksCache = names to icons
            }
            else -> Unit
        }
    }

    LaunchedEffect(tab) {
        selectedId = null
        val cached = cardsCache[tab]
        if (cached != null) {
            // 短时切回已加载过的 tab：即时展示，不转圈、不重拉、不在背景列表重新试探 SVG
            cards = cached
            loadBanks()                          // 已缓存则瞬间返回
            // ⚠ 「静默」不等于「免费」：原先这里无条件 `silentRefresh`，于是**每拨一次分段就发一个
            //   `GET /card?...`**（来回拨 10 次 = 10 个请求）——这就是「切换时疯狂请求」的来源。
            //   对齐 web `debit.vue:290-303` 的 `onActivated` 节流：`REFRESH_INTERVAL` 内不再重复请求。
            if (SilentRefreshThrottle.shouldRefresh(tab)) scope.launch { silentRefresh(tab) }
        } else {
            loadBanks()
            loadCards(silent = false)
        }
    }

    // 预览脱敏（稳定缓存，随列表/开关变化重建，对齐 web maskedCardList）
    val displayCards = remember(cards, previewMode, previewSeed) {
        if (!previewMode) cards
        else cards.map { c ->
            val bin = c.cardBin
            val maskedBin = bin.take(3) + pseudoDigits((bin.length - 3).coerceAtLeast(3), previewSeed)
            c.copy(cardBin = maskedBin, last4 = pseudoDigits(4, previewSeed + 7))
        }
    }
    LaunchedEffect(previewMode, cards) { previewSeed = System.currentTimeMillis() }

    // 滚动方向 → 收起顶栏与底部按钮（web：向上滚收起、向下滚展开，4px 死区）
    // ⚠ 用 `snapshotFlow` 而不是 `LaunchedEffect(scrollState.value)`：后者把 scroll 值当 key，
    //   **每滚动 1px 就会取消并重启一次协程**（滑动时一帧好几次），本身就是掉帧源。
    var lastScroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { v ->
            val d = v - lastScroll          // scrollState.value 是 Int，lastScroll 记 Float
            if (d > 4f) scrolled = true else if (d < -4f) scrolled = false
            lastScroll = v.toFloat()
        }
    }

    val selectedCard = displayCards.firstOrNull { it.id == selectedId }

    // web `watch(selectedId, …)`：选中即查 6 个月动账统计，取不到条目则不算 idle
    LaunchedEffect(selectedId) {
        val id = selectedId
        if (id == null) { cardFlowIdle = false; return@LaunchedEffect }
        when (val r = graph.flow.cardsFlowStats(6)) {
            is ApiResult.Ok -> {
                val hit = r.data?.get(id)
                cardFlowIdle = hit != null && hit.first == 0 && hit.second == 0
            }
            else -> cardFlowIdle = false
        }
    }
    val isDebit = tab == "debit"
    // 底部留白：未滚动时给底部按钮让位；滚动收起后按 web `.scrolled{padding-bottom:24px}` 收到 24
    val bottomPad by animateDpAsState(
        if (scrolled) 24.dp else 140.dp,
        tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing),
        label = "cardListBottomPad",
    )
    // ===== 选中卡弹出 / 收起动画 =====
    //   web：`.is-selected{ transform:scale(1.05); transition:transform .38s cubic-bezier(.4,0,.2,1) }`
    //        + `.stack-backdrop{ transition:opacity .4s }`。他反馈「卡面弹起来卡卡的、没动画」⇒ 补上。
    val selScale by animateFloatAsState(
        if (selectedCard != null) 1.05f else 1f,
        tween(vantDimens.durationSlow, easing = MotionTokens.StandardEasing),
        label = "selScale",
    )
    val selAlpha by animateFloatAsState(if (selectedCard != null) 1f else 0f, tween(vantDimens.durationBase), label = "selAlpha")
    // 收起瞬间 `selectedCard` 就变 null 了，浮层内容会**先消失再淡出** ⇒ 留住最后一张卡撑到动画结束
    var lastCard by remember { mutableStateOf<Card?>(null) }
    LaunchedEffect(selectedCard) { if (selectedCard != null) lastCard = selectedCard }
    val overlayCard = selectedCard ?: lastCard?.takeIf { selAlpha > 0.01f }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold { inner ->
            // ⚠ 本页是**白底**（web `index.vue:91` `.page-bank-card-manage{background:var(--theme-bg-secondary)}`
            //   与 `debit.vue:328` `.page-card-list` 同）⇒ 必须盖掉 `ScreenScaffold` 的 `bgPage` 灰底。
            //   注意本项目 token 命名：`--theme-bg-primary/secondary` = **页面灰 / 卡片白**
            //   （`useUiTheme.js:96` light: primary #f7f8fa、secondary #ffffff），与直觉相反，别再搞反。
            Column(Modifier.fillMaxSize().then(inner).background(colors.bgCard)) {
                // ===== 顶栏 =====
                AnimatedVisibility(
                    visible = !scrolled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    CardManageHeader(
                        tab = tab,
                        sortMode = sortMode,
                        previewMode = previewMode,
                        onTab = { if (it != tab) { tab = it } },
                        onToggleSort = { sortMode = !sortMode; selectedId = null },
                        onTogglePreview = { previewMode = !previewMode },
                    )
                }

                if (sortMode) {
                    CardSortList(
                        cards = displayCards,
                        modifier = Modifier.weight(1f),
                        onReorder = { newList ->
                            cards = newList
                            scope.launch {
                                // 拖完给回执：web 只在失败时 `console.error`（用户看不见），
                                //   他明确要「成功了也要有提示」⇒ 原生成功/失败都 toast。
                                when (val r = graph.card.updateSortBatch(newList.mapIndexed { i, c -> c.id to (i + 1) })) {
                                    is ApiResult.Ok -> vanToast.show("排序已保存")
                                    is ApiResult.Fail -> vanToast.show(r.message.ifBlank { "排序保存失败" }, VanToastType.Fail)
                                    else -> Unit
                                }
                            }
                        },
                    )
                } else {
                    // ⚠ 这里**故意不做** web 的 `.card-tab-fade` 切页过渡（`AnimatedContent` + slide）：
                    //   实测切 tab 时会掉帧，且过渡期间整列卡要重绘两次。流畅度优先 ⇒ 直接换内容。
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp)
                            // web `.page-card-list{padding: 24px 20px 120px}` ⇒ 卡堆距顶栏 24
                            .padding(top = 24.dp),
                    ) {
                        if (cards.isNotEmpty()) {
                            CardStackView(
                                cards = displayCards,
                                selectedId = selectedId,
                                onSelect = { selectedId = it },
                                cardTypeLabel = if (isDebit) "DEBIT CARD" else "CREDIT CARD",
                                // web `.bank-icon img`：卡面上的银行 logo（web 由 debit/credit.vue 传 bankIconUrl）
                                bankIconOf = { c -> bankIconById[c.bankId].orEmpty() },
                                // debit.vue `default-color="#4A90E2"` / credit.vue `default-color="#ee0a24"`
                                defaultColor = if (isDebit) CARD_DEFAULT_DEBIT else CARD_DEFAULT_CREDIT,
                            )
                        } else if (loading) {
                            // 首次/空列表加载中：转圈（切 tab 命中缓存时不会进这里）
                            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                                VanLoading(size = 36.sp, color = colors.primary, type = VanLoadingType.Circular)
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                VanEmpty(
                                    description = if (isDebit)
                                        "暂无借记卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！"
                                    else
                                        "暂无信用卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！",
                                )
                            }
                        }
                        // `.page-card-list{padding-bottom:120px}` ⇒ 给底部按钮留位；
                        //   `.page-card-list.scrolled{padding-bottom:24px}` ⇒ 按钮收起后跟着收
                        Spacer(Modifier.height(bottomPad))
                    }
                }
            }
        }

        // ===== 底部「添加卡片」（选中/排序/滚动收起时隐藏）=====
        if (selectedId == null && !sortMode) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 30.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (scrolled) 0f else 1f
                        translationY = if (scrolled) size.height else 0f
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        // web `.glass-add-btn`：debit `box-shadow:0 8px 24px rgba(25,137,250,.3)`，
                        //   credit 页是 **红底** `var(--van-danger-color,#ee0a24)` + `rgba(238,10,36,.3)`
                        .cssShadow(28.dp, WebShadow(0f, 8f, 24f,
                            if (isDebit) Color(0x4D1989FA) else Color(0x4DEE0A24)))
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (isDebit) colors.primary else colors.danger)
                        .clickable { nav.navigate(if (isDebit) Routes.CARD_ADD else Routes.CREDIT_FULL) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VanIcon("plus", size = 18.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        // web：debit.vue 是「添加卡片」、credit.vue 是「添加信用卡」（原文不一致，照抄）
                        FText(if (isDebit) "添加卡片" else "添加信用卡", 18f, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        // ===== 选中卡浮层（遮罩 + 顶部卡片 + 管理/关闭 + 查看消费明细）=====
        overlayCard?.let { sel ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .graphicsLayer { alpha = selAlpha }        // web `.stack-backdrop{ opacity .4s }`
                    .clickable { selectedId = null },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = selAlpha }
                    .padding(top = 24.dp, start = 20.dp, end = 20.dp),
            ) {
                // web `.is-selected{ transform:scale(1.05); transition:transform .38s cubic-bezier(.4,0,.2,1) }`
                Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = selScale; scaleY = selScale }) {
                    BankCardFace(
                        card = sel,
                        cardTypeLabel = if (isDebit) "DEBIT CARD" else "CREDIT CARD",
                        showLast4Suffix = false,
                        bankIconUrl = bankIconById[sel.bankId].orEmpty(),
                        selected = true,                       // `.is-selected`：阴影 / 卡组织 / 尾号都换位
                        defaultColor = if (isDebit) CARD_DEFAULT_DEBIT else CARD_DEFAULT_CREDIT,
                    )
                    // 管理按钮（右上角）
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
                    ) {
                        Row(
                            Modifier
                                // web `.action-pill-btn{ background: var(--theme-bg-secondary) = **卡片白**
                                //   （本页白底 ⇒ 白胶囊靠阴影浮起来）; box-shadow: 0 10px 20px rgba(0,0,0,.2) }`
                                .cssShadow(30.dp, WebShadow(0f, 10f, 20f, Color(0x33000000)))
                                .clip(RoundedCornerShape(30.dp))
                                .background(colors.bgCard)
                                .clickable { onEditCard(nav, sel, tab) }
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

            // 关闭按钮（底部居中，毛玻璃）
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .size(56.dp)
                    // web `.close-btn{ border:1px solid rgba(255,255,255,.3); background:rgba(255,255,255,.18) }`
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .graphicsLayer { alpha = selAlpha }
                    .clickable { selectedId = null },
                contentAlignment = Alignment.Center,
            ) { VanIcon("cross", size = 24.sp, color = Color.White) }

            // 借记卡：查看消费明细（卡片正下方）
            if (isDebit) {
                // web `.flow-bar{ position:fixed; top:280px; left/right:20px; z-index:96000;
                //   display:flex; flex-direction:column; align-items:center; gap:8px }`
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        // web `.flow-bar{ top:280px }`（卡片 fixed top:24 + 卡高 220 + 间距）
                        .padding(start = 20.dp, end = 20.dp, top = 280.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = selAlpha },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp), // gap: 8px
                ) {
                    // `.flow-btn{ height:48px; border:1px solid rgba(255,255,255,.3); border-radius:24px;
                    //   background: rgba(255,255,255,.18); box-shadow: 0 8px 24px rgba(0,0,0,.3);
                    //   font-size:15px→16; font-weight:600; gap:8px }`
                    // （web 还有 `backdrop-filter: blur(20px) saturate(180%)` —— 按既有拍板不做真模糊）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false,
                                ambientColor = Color(0x4D000000), spotColor = Color(0x4D000000))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .clickable {
                                val id = sel.id
                                selectedId = null
                                nav.navigate(Routes.cardFlow(id))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VanIcon("orders-o", size = 16.sp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            FText("查看消费明细", 16f, FontWeight.SemiBold, Color.White)
                        }
                    }
                    // `.flow-empty-tip{ font-size:12px; color:#ff7a7a }`（web 另有 text-shadow，Compose 不做）
                    if (cardFlowIdle) FText("近6个月无动账", 12f, color = Color(0xFFFF7A7A))
                }
            }
        }
    }
}

/**
 * 进入卡片编辑。web 用 `?id=&from=debit|credit` 决定返回路径；原生借记卡/信用卡是同一个目的地
 * （tab 由 `rememberSaveable` 保留），故只需传 id。
 */
private fun onEditCard(nav: NavHostController, card: Card, tab: String) {
    nav.navigate(Routes.cardEdit(card.id))
}

/** 顶栏：标题 + 分段切换 + 排序/预览开关（对齐 web `.card-manage-header`）。 */
@Composable
private fun CardManageHeader(
    tab: String,
    sortMode: Boolean,
    previewMode: Boolean,
    onTab: (String) -> Unit,
    onToggleSort: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val colors = LocalAppColors.current
    // web `.card-manage-header{ margin:10px 20px 0; gap:12px }`（无 padding-bottom：间距由子页 padding-top:24 给）
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // web 是 `<h1 class="title">` 且没设 font-size ⇒ 浏览器默认 `2em` = 32px。
        //   2026-09-20 试过跟到 32f，他反馈「Header 太高」⇒ **回退 20f**（安卓侧有意偏离，记录在此别再改回去）。
        FText(if (tab == "credit") "信用卡" else "借记卡", 20f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            if (!sortMode) SegmentSwitch(tab = tab, onTab = onTab)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(
                if (sortMode) "长按拖动卡片排序" else "管理你的银行卡",
                14f, color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // `.header-actions{ width:164px; display:flex; justify-content:space-between }`
            Row(Modifier.width(164.dp), verticalAlignment = Alignment.CenterVertically) {
                // `.preview-toggle{ flex:1; gap:6px; padding:6px 10px; bg:theme-bg-primary; radius:20px;
                //   box-shadow:0 2px 8px rgba(0,0,0,.06) }`
                ToggleDot("排序", sortMode, onToggleSort, Modifier.weight(1f))
                if (!sortMode) {
                    Spacer(Modifier.width(8.dp))
                    ToggleDot("预览", previewMode, onTogglePreview, Modifier.weight(1f))
                }
            }
        }
    }
}

/** 分段切换（滑动高亮块 + 白字激活态）—— 对齐 web `.segment-switch` / `.segment-thumb`。 */
private val SEG_ITEM_W = 84.dp     // `.segment-item{ padding:7px 18px }` + 三字 ≈ 84
private val SEG_ITEM_H = 36.dp

@Composable
private fun SegmentSwitch(tab: String, onTab: (String) -> Unit) {
    val colors = LocalAppColors.current
    val isDebit = tab == "debit"
    // `.segment-thumb{ transform: translateX(0|100%); transition: transform .28s cubic-bezier(.4,0,.2,1) }`
    val thumbX by animateDpAsState(
        if (isDebit) 0.dp else SEG_ITEM_W,
        tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing),
        label = "cardSegThumb",
    )
        Box(
            Modifier
                // `.segment-switch{ box-shadow: 0 2px 8px rgba(0,0,0,.06); radius:22px; padding:4px }`
                //   底色是 `--theme-bg-primary` = **页面灰**（不是卡片白，本页白底 ⇒ 灰胶囊）
                .cssShadow(22.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
                .clip(RoundedCornerShape(22.dp))
                .background(colors.bgPage)
                .padding(4.dp),
    ) {
        // 滑块先画（下层），文字后画（上层）—— 与 `InstallmentCreateScreen.SegmentSwitch` 同一套写法
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbX)
                .width(SEG_ITEM_W)
                .height(SEG_ITEM_H)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.primary),
        )
        Row {
            SegmentItem("借记卡", active = isDebit) { onTab("debit") }
            SegmentItem("信用卡", active = !isDebit) { onTab("credit") }
        }
    }
}

@Composable
private fun SegmentItem(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .width(SEG_ITEM_W)
            .height(SEG_ITEM_H)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // `.segment-item{ font-size:14px(→16f); weight:600; color:text-secondary }` / `.active{color:#fff}`
        FText(text, 16f, FontWeight.SemiBold, if (active) Color.White else colors.textSecondary)
    }
}

/** 「排序 / 预览」勾选：web `.preview-toggle` + `.dot`（**空心圆环**，不是实心点）。 */
@Composable
private fun ToggleDot(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .cssShadow(20.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.06f)))
            .clip(RoundedCornerShape(20.dp))
            // `.preview-toggle{ background: var(--theme-bg-primary,#fff) }` = 页面灰（白底页上的灰胶囊）
            .background(colors.bgPage)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `.preview-label{ font-size:13px(→14f); weight:500 }` / `.on .preview-label{ primary; 600 }`
        FText(label, 14f, if (on) FontWeight.SemiBold else FontWeight.Normal,
            if (on) colors.primary else colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                // `.on .dot{ box-shadow: 0 0 0 3px color-mix(primary 20%) }`
                .then(
                    if (on) Modifier.cssShadowCircle(
                        WebShadow(0f, 0f, 3f, colors.primary.copy(alpha = 0.2f))
                    ) else Modifier
                )
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                // `.dot{ 18×18; border:2px solid var(--theme-border,#dcdee0); background:transparent }`
                .background(if (on) colors.primary else Color.Transparent)
                .border(2.dp, if (on) colors.primary else colors.border, RoundedCornerShape(50)),
        )
    }
}

/**
 * 排序模式：简洁 list，拖把手**按住即拖**整列重排（提交 1-N sort）。
 *
 * 三个都是他实测反馈后重写的点：
 * 1. **按住即拖**（`detectDragGestures`）而不是长按 —— web `vuedraggable` 的 `handle` 就是按下即拖，
 *    长按 400ms 的那版「非常难用」。
 * 2. **不串位**：① 每行用 `key(card.id)` 包住，重排时 composition 跟着条目走（否则第 i 个槽位会换成别的卡，
 *    手势回调里拿到的 `card` 就错位了）；② 目标下标按「**起始下标 + 总位移/行高**」算，
 *    而不是「当前下标 + 增量」（后者在重排后会累积漂移）。
 * 3. 跟手位移 = 总位移 −（当前下标 − 起始下标）× 行高，避免列表重排后位移叠加。
 */
@Composable
private fun CardSortList(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    onReorder: (List<Card>) -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val rowH = 42.dp                       // 40 行高 + 2 间距（web `.sort-item{margin-bottom:2px}`）
    val rowHpx = with(density) { rowH.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()
    // 手势协程里要读**最新**列表：用 UpdatedState 而不是把 `cards` 当 pointerInput 的 key
    //   （拿 cards 当 key 会在每次重排时重启手势 ⇒ 拖到一半被中断）。
    val cardsRef = rememberUpdatedState(cards)

    Column(modifier.verticalScroll(scroll)) {
        cards.forEachIndexed { index, card ->
            val isDragging = card.id == draggingId
            val shiftPx = if (isDragging) {
                dragOffsetPx - (index - dragStartIndex) * rowHpx
            } else 0f
            key(card.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .then(
                            if (isDragging) Modifier.shadow(
                                8.dp, RoundedCornerShape(12.dp), clip = false,
                            ) else Modifier
                        )
                        .offset { IntOffset(0, shiftPx.roundToInt()) }
                        // web `.sort-item{ box-shadow: 0 1px 4px rgba(0,0,0,.05); radius:12px; bg:theme-bg-primary }`
                        .cssShadow(12.dp, WebShadow(0f, 1f, 4f, Color.Black.copy(alpha = 0.05f)))
                        .clip(RoundedCornerShape(12.dp))
                        // `.sort-item{ background: var(--theme-bg-primary,#fff) }` = 页面灰
                        .background(colors.bgPage)
                        .graphicsLayer {
                            if (isDragging) { scaleX = 1.02f; scaleY = 1.02f; alpha = 0.95f }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).padding(start = 20.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        FText(card.bankName, 16f, FontWeight.SemiBold, colors.textPrimary)
                        Spacer(Modifier.width(8.dp))
                        // `.sort-last4{ font-size:14px(→16f); color:text-secondary; letter-spacing:1px }`
                        FText(card.last4, 16f, color = colors.textSecondary, letterSpacing = 1.sp)
                    }
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)                       // 把手热区放到 40dp（原来只有图标大小，太难点）
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
                                            val item = m.removeAt(cur)
                                            m.add(to, item)
                                            onReorder(m)
                                        }
                                    },
                                    onDragEnd = { draggingId = null; dragOffsetPx = 0f },
                                    onDragCancel = { draggingId = null; dragOffsetPx = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { VanIcon("bars", size = 20.sp, color = colors.textSecondary) }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** 稳定伪随机数字串（用于预览脱敏）。 */
private fun pseudoDigits(n: Int, seed: Long): String {
    var s = if (seed == 0L) 1L else seed
    val sb = StringBuilder()
    repeat(n.coerceAtLeast(0)) {
        s = s * 6364136223846793005L + 1442695040888963407L
        sb.append((s.ushr(33) % 10L).toInt())
    }
    return sb.toString()
}

/**
 * 「切回已加载过的分段」的后台刷新节流 —— 对齐 web `BankCard/debit.vue:290-303` 的 `onActivated`。
 *
 * ## web 的口径（照抄）
 * ```js
 * let lastLoadTime = 0;
 * const REFRESH_INTERVAL = 10000;
 * onActivated(() => {
 *   if (Date.now() - lastLoadTime < REFRESH_INTERVAL) return;  // 不足 10s 不重复请求
 *   loadBankList(); loadCards(true);                            // silent=true：不转 loading
 * });
 * ```
 * ⚠ web 只在**成功后**记录 `lastLoadTime`（`debit.vue:269-270`），失败不记 ——
 *   否则一次失败会被锁死 10s。本实现同样只在成功分支调 [markLoaded]。
 *
 * ## 存在进程级而不是页面级的原因
 * 页面内 `remember` 的节流时间戳会随「离开路由 → 返回」一起丢失 ⇒ 回切时又变成必刷。
 * 这里用进程内 map，进出页面都保留，正好补上原生没有 `keep-alive` 的那块短板。
 */
internal object SilentRefreshThrottle {
    /** 与 web `REFRESH_INTERVAL` 对齐。 */
    private const val INTERVAL_MS = 10_000L

    private val lastLoadedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** 距上次**成功**加载是否已超过间隔；超过才允许发请求。 */
    fun shouldRefresh(key: String): Boolean =
        System.currentTimeMillis() - (lastLoadedAt[key] ?: 0L) >= INTERVAL_MS

    fun markLoaded(key: String) {
        lastLoadedAt[key] = System.currentTimeMillis()
    }
}
