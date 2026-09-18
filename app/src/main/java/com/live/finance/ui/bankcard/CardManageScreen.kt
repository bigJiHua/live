package com.live.finance.ui.bankcard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var selectedId by remember { mutableStateOf<String?>(null) }
    var scrolled by remember { mutableStateOf(false) }
    var previewSeed by remember { mutableStateOf(0L) }
    val scrollState = rememberScrollState()

    // ===== 数据加载 =====
    suspend fun loadCards(silent: Boolean) {
        if (!silent) loading = true
        when (val res = graph.card.list(tab)) {
            is ApiResult.Ok -> cards = res.data.orEmpty()
            is ApiResult.Fail -> {
                Toast.makeText(context, res.message.ifBlank { "加载失败" }, Toast.LENGTH_SHORT).show()
                cards = emptyList()
            }
            else -> cards = emptyList()
        }
        if (!silent) loading = false
    }

    suspend fun loadBanks() {
        when (val res = graph.category.list("bank")) {
            is ApiResult.Ok -> {
                val list = res.data.orEmpty()
                bankNameById = list.associate { it.id to it.name }
                bankIconById = list.filter { it.iconUrl.isNotBlank() }
                    .associate { it.id to AppConfig.fullFileUrl(it.iconUrl) }
            }
            else -> Unit
        }
    }

    LaunchedEffect(tab) {
        selectedId = null
        loadBanks()
        loadCards(silent = false)
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
    var lastScroll by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(scrollState.value) {
        val d = scrollState.value - lastScroll
        if (d > 4f) scrolled = true else if (d < -4f) scrolled = false
        lastScroll = scrollState.value.toFloat()
    }

    val selectedCard = displayCards.firstOrNull { it.id == selectedId }
    val isDebit = tab == "debit"

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold { inner ->
            Column(Modifier.fillMaxSize().then(inner)) {
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
                                graph.card.updateSortBatch(newList.mapIndexed { i, c -> c.id to (i + 1) })
                            }
                        },
                    )
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp)
                            .padding(top = 10.dp),
                    ) {
                        if (cards.isNotEmpty()) {
                            CardStackView(
                                cards = displayCards,
                                selectedId = selectedId,
                                onSelect = { selectedId = it },
                                cardTypeLabel = if (isDebit) "DEBIT CARD" else "CREDIT CARD",
                            )
                        } else if (!loading) {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                VanEmpty(
                                    description = if (isDebit)
                                        "暂无借记卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！"
                                    else
                                        "暂无信用卡，点击下方按钮添加信用卡",
                                )
                            }
                        }
                        Spacer(Modifier.height(140.dp))
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
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.primary)
                        .clickable { nav.navigate(if (isDebit) Routes.CARD_ADD else Routes.CREDIT_FULL) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VanIcon("plus", size = 18.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        FText("添加卡片", 16f, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        // ===== 选中卡浮层（遮罩 + 顶部卡片 + 管理/关闭 + 查看消费明细）=====
        if (selectedCard != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { selectedId = null },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, start = 20.dp, end = 20.dp),
            ) {
                Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = 1.05f; scaleY = 1.05f }) {
                    BankCardFace(
                        card = selectedCard,
                        cardTypeLabel = if (isDebit) "DEBIT CARD" else "CREDIT CARD",
                        showLast4Suffix = false,
                    )
                    // 管理按钮（右上角）
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
                    ) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(30.dp))
                                .background(colors.bgCard)
                                .clickable { onEditCard(nav, selectedCard, tab) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon("setting-o", size = 14.sp, color = colors.textPrimary)
                            Spacer(Modifier.width(8.dp))
                            FText("管理", 14f, FontWeight.SemiBold, colors.textPrimary)
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
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { selectedId = null },
                contentAlignment = Alignment.Center,
            ) { VanIcon("cross", size = 24.sp, color = Color.White) }

            // 借记卡：查看消费明细（卡片正下方）
            if (isDebit) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 20.dp, end = 20.dp, top = 290.dp)
                        .fillMaxWidth(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                val id = selectedCard.id
                                selectedId = null
                                nav.navigate("${Routes.REPORT_CARD_FLOW}?cardId=$id")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VanIcon("orders-o", size = 16.sp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            FText("查看消费明细", 15f, FontWeight.SemiBold, Color.White)
                        }
                    }
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
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(if (tab == "credit") "信用卡" else "借记卡", 20f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            if (!sortMode) SegmentSwitch(tab = tab, onTab = onTab)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText(
                if (sortMode) "长按拖动卡片排序" else "管理你的银行卡",
                13f, color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ToggleDot("排序", sortMode, onToggleSort)
            if (!sortMode) {
                Spacer(Modifier.width(8.dp))
                ToggleDot("预览", previewMode, onTogglePreview)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 分段切换（滑动高亮块 + 白字激活态）。 */
@Composable
private fun SegmentSwitch(tab: String, onTab: (String) -> Unit) {
    val colors = LocalAppColors.current
    val isDebit = tab == "debit"
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgCard)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentItem("借记卡", active = isDebit) { onTab("debit") }
        SegmentItem("信用卡", active = !isDebit) { onTab("credit") }
    }
}

@Composable
private fun SegmentItem(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
    ) {
        FText(text, 14f, FontWeight.SemiBold, if (active) Color.White else colors.textSecondary)
    }
}

/** 「排序 / 预览」勾选（圆点点亮）。 */
@Composable
private fun ToggleDot(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 13f, if (on) FontWeight.SemiBold else FontWeight.Normal,
            if (on) colors.primary else colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(if (on) colors.primary else Color.Transparent)
                .then(
                    if (!on) Modifier.background(colors.border, RoundedCornerShape(50))
                    else Modifier
                ),
        )
    }
}

/** 排序模式：简洁 list，长按拖拽把手整列重排（提交 1-N sort）。 */
@Composable
private fun CardSortList(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    onReorder: (List<Card>) -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val rowH = 42.dp
    val rowHpx = with(density) { rowH.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()

    Column(modifier.verticalScroll(scroll)) {
        cards.forEachIndexed { index, card ->
            val isDragging = card.id == draggingId
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragAccum
                            scaleX = 1.02f; scaleY = 1.02f
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).padding(start = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    FText(card.bankName, 15f, FontWeight.SemiBold, colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    FText(card.last4, 14f, color = colors.textSecondary)
                }
                Box(
                    Modifier
                        .padding(end = 15.dp)
                        .pointerInput(cards.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = card.id; dragAccum = 0f },
                                onDrag = { change, delta ->
                                    change.consume()
                                    val id = draggingId ?: return@detectDragGesturesAfterLongPress
                                    dragAccum += delta.y
                                    val rows = (dragAccum / rowHpx).roundToInt()
                                    if (rows != 0) {
                                        val cur = cards.indexOfFirst { it.id == id }
                                        val target = (cur + rows).coerceIn(0, cards.size - 1)
                                        if (target != cur) {
                                            val m = cards.toMutableList()
                                            val item = m.removeAt(cur)
                                            m.add(target, item)
                                            onReorder(m)
                                            dragAccum = 0f
                                        }
                                    }
                                },
                                onDragEnd = { draggingId = null; dragAccum = 0f },
                                onDragCancel = { draggingId = null; dragAccum = 0f },
                            )
                        },
                ) { VanIcon("bars", size = 20.sp, color = colors.textSecondary) }
            }
            Spacer(Modifier.height(2.dp))
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
