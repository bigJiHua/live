package com.live.finance.ui.diary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Moment
import com.live.finance.data.model.User
import com.live.finance.theme.LocalAppColors

import com.live.finance.theme.VanGreenBgFixed
import com.live.finance.theme.vanGreen
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.VanPurpleFixed
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.live.finance.core.net.showFail

/** Vant `--van-purple`（`.mood-tag` 用它，**不是**主题色）。 */

/** Vant `--van-green`（`.sub-record-tag` / `.date`）。 */

/** `--van-green-bg` 未定义 → 走 fallback `#f0f9eb`。 */
private val VAN_GREEN_BG = Color(0xFFF0F9EB)

private const val PAGE_SIZE = 10
private val HHMM = SimpleDateFormat("HH:mm", Locale.US)

/**
 * 动态（日记）列表 —— 一比一复刻 web `views/Diary/index.vue` + `components/Diary/DiaryCard.vue`。
 *
 * ⚠ 瀑布流是**按索引奇偶分列**（web `filter((_, i) => i % 2 === 0/1)`），不是按高度最短列，
 *   故这里就用「左列 = 偶数项、右列 = 奇数项」的两列 Column 复刻，而不是 StaggeredGrid。
 *
 * 关键度量：`.page-diary{padding:8; bg:theme-bg-tertiary}`；`.waterfall-container{flex; gap:8}`；
 * 卡片 `bg-secondary / radius 12 / border 1px / shadow 0 4px 12px rgba(0,0,0,.04) / margin-bottom 4`；
 * `.card-content{padding:10}`；正文 13/500 行高 1.45 两行截断；心情标签 10px 紫、追文标签 10px 绿；
 * 作者名 11px、时间 10px、日期芯片 9px 绿底；底部 `height:100px` 占位。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DiaryListScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var rows by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }       // 首屏 loading
    var loadingMore by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }    // 独立防重（web `requesting`）
    var user by remember { mutableStateOf<User?>(null) }
    /** 已成功加载的页码，用于触底去重（避免「向上滚再往下滑」就重复打接口 —— 2026-09-19 真机反馈「频繁获取数据」）。 */
    val loadedPages = remember { mutableSetOf<Int>() }

    // ⚠ 列表容器必须是**懒**的：这里原本是 `Column + verticalScroll`（非懒），每加载一页就把已渲染的
    // 全部卡片重新测量/摆放 ⇒ 触底加载那一瞬 fling 动量被打断（负责人 2026-09-20 报「动态页的刷新
    // 打断我的上滑，无法快速上滑」）。改成 `LazyVerticalStaggeredGrid` 两列，只组合可视项。
    val gridState = rememberLazyStaggeredGridState()
    val refreshState = rememberVanPullRefreshState()

    // 回顶按钮可见性 + 发布浮钮收起（web：y>400 显示回顶；下滑 delta>6 收起浮钮、上滑弹起）
    var showBackTop by remember { mutableStateOf(false) }
    var fabHidden by remember { mutableStateOf(false) }
    var lastY by remember { mutableStateOf(0) }

    suspend fun load(append: Boolean) {
        if (requesting) return
        requesting = true
        if (append) loadingMore = true else { loading = true; page = 1 }
        // ⚠ **必须用 try/finally 复位**：旧实现把复位写在末尾，一旦 `graph.moment.list` 抛异常或协程被取消（
        //   LaunchedEffect 因 key 变化重启），`requesting` / `loadingMore` 永远为 true ⇒ 后续 load 全部早退，
        //   表现为「**向下滑动卡在「加载中...」不动**（2026-09-19 真机反馈）。
        try {
            when (val r = graph.moment.list(page, PAGE_SIZE)) {
                is ApiResult.Ok -> {
                    val d = r.data
                    val list = d?.list.orEmpty()
                    total = d?.total ?: 0
                    rows = if (append) rows + list else list
                    if (append) {
                        loadedPages.add(page)
                    } else {
                        loadedPages.clear()
                        loadedPages.add(1)
                    }
                }
                else -> toast.showFail(r, "加载失败")
            }
        } finally {
            requesting = false
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        // 头像/昵称兜底取当前登录用户（后端 moment 行里没有 user_name/avatar）
        (graph.auth.getUserinfo() as? ApiResult.Ok)?.data?.let { user = it }
        load(false)
    }

    // 滚动：回顶显隐 + 浮钮收起弹起 + 触底加载
    //
    // 懒网格拿不到 web 的 `window.scrollY` 总偏移 ⇒ 用「首个可见项序号 × 1_000_000 + 该项内滚动 px」当代理：
    // 同一个 item 内随 px 单调，跨 item 的步进（1e6）远大于 web 的 6px 阈值 ⇒ web 那两条判据
    // （`y > 400` 显示回顶、`delta > 6` 收起/弹起浮钮）**原样保留**，不用重写语义。
    val scrollPos by remember {
        derivedStateOf { gridState.firstVisibleItemIndex * 1_000_000 + gridState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { scrollPos }.collect { y ->
            showBackTop = y > 400
            val delta = y - lastY
            if (abs(delta) > 6) {
                if (delta > 0) fabHidden = true else if (y > 0) fabHidden = false
                lastY = y
            }
        }
    }
    // web `van-list` 是「距底 300px 就检查」⇒ 这里按「最后一个可见项距列表末尾 ≤ 4 项」等价收口
    val nearBottom by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearBottom, rows.size, total) {
        val next = page + 1
        // ⚠ 去重：向上滚再往下滑会重新触发 nearBottom，若不加 `next !in loadedPages` 会**反复打同一页接口**。
        if (nearBottom && total > 0 && rows.size < total && !requesting && !loadingMore && next !in loadedPages) {
            page = next
            load(append = true)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bgThird)) { // .page-diary{bg:theme-bg-tertiary}
        VanPullRefresh(
            state = refreshState,
            // 只有列表滚到顶部时下拉才触发刷新（Vant 语义；VanPullRefresh 默认 `{ true }` 会导致任意位置下拉都刷新）
            isChildAtTop = {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            },
            onRefresh = {
                scope.launch {
                    load(false)
                    refreshState.finishRefresh()
                }
            },
        ) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    VanLoading()
                }

                rows.isEmpty() -> VanEmpty(description = "还没有动态，快来发布第一条吧", image = "search")

                else -> LazyVerticalStaggeredGrid(
                    // web `.waterfall-container{display:flex;gap:8px}` + 两列各自流式（`leftList` 偶、`rightList` 奇）。
                    // 交错网格按「较矮的一列」放下一项 ⇒ 观感与 web 一致；卡片落哪一列可能与 web 不同
                    // （web 是奇偶硬分列），这是懒实现换来的唯一差异，已在构造书 5.6 记档。
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp, // 列内卡片自带 margin-bottom 4 ⇒ 合计 12，同 web
                    modifier = Modifier.fillMaxSize().padding(8.dp), // .page-diary{padding:8px}
                ) {
                    items(rows, key = { it.id }) { m ->
                        DiaryCard(
                            item = m,
                            userName = user?.username.orEmpty(),
                            userAvatar = user?.avatar.orEmpty(),
                            onClick = { nav.navigate("${Routes.DIARY_DETAIL}?id=${m.id}") },
                        )
                    }
                    // van-list 的加载中 / 没有更多了 —— 改成列表内的 item（原非懒实现里是 Column 尾巴）
                    item(key = "footer") {
                        when {
                            loadingMore -> Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                VanLoading(size = 16.sp)
                                Spacer(Modifier.size(6.dp))
                                FText("加载中...", 14f, FontWeight.Normal, colors.textTertiary)
                            }

                            total > 0 && rows.size >= total ->
                                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    FText("没有更多了", 14f, FontWeight.Normal, colors.textTertiary)
                                }
                        }
                    }
                    item(key = "bottom-spacer") { Box(Modifier.height(100.dp)) } // web 底部 `height:100px` 占位
                }
            }
        }

        // 回顶按钮：web `.back-top{fixed; right:16px; bottom:80px}` —— **80dp 必须自己从屏幕底算**。
        // 旧注释「内容区已上抬 90dp，故 +10dp 回到同一视口位置」在第二十一轮之后就不成立了：
        // 那轮把内容区的 `padding(bottom=90dp)` 去掉（岛收起时露大黑条），内容改成整屏高，
        // 这个 +10dp 补偿于是变成「往下 10dp」⇒ 两个浮钮一起沉到屏幕底边、压住底部岛（负责人 2026-09-20 报）。
        if (showBackTop) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
                    .size(40.dp)
                    .cssShadowCircle(WebShadow(0f, 2f, 12f, Color.Black.copy(alpha = 0.15f)))
                    .clip(CircleShape)
                    .background(colors.bgCard)
                    .clickable { scope.launch { gridState.animateScrollToItem(0, 0) } },
                contentAlignment = Alignment.Center,
            ) { VanIcon(name = "back-top", size = 20.sp, color = colors.primary) }
        }

        // 发布浮钮：web `.add-diary-btn{fixed; left:50%; bottom:80px}` —— 80dp 同样自己从屏幕底算
        // （岛 `bottom:20dp` + 高 ≈58dp ⇒ 顶边就在 78dp 附近，所以 80dp 正好「浮在底部 Bar 上面一点点」）。
        val fabAlpha by animateFloatAsState(if (fabHidden) 0f else 1f, tween(300), label = "fabAlpha")
        val fabOffset by animateFloatAsState(if (fabHidden) 100f else 0f, tween(300), label = "fabOffset")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .offset(y = fabOffset.dp)
                .alpha(fabAlpha)
                .size(50.dp)
                .cssShadowCircle(WebShadow(0f, 4f, 12f, Color.Black.copy(alpha = 0.15f)))
                .clip(CircleShape)
                .background(LocalAppTokens.current.primary)
                .clickable(enabled = !fabHidden) { nav.navigate(Routes.DIARY_ADD) },
            contentAlignment = Alignment.Center,
        ) { VanIcon(name = "plus", size = 24.sp, color = Color.White) }
    }
}

/**
 * 动态卡片（web `components/Diary/DiaryCard.vue`）。
 *
 * 字段兜底同 web：昵称/头像优先后端 `author.name/avatar` → `user_name/avatar` → 登录用户（[userName]/[userAvatar]）；
 * 时间/日期由 `create_time`（毫秒时间戳）算出（今天显示「今天」，否则 `M-D`）。
 */
@Composable
private fun DiaryCard(
    item: Moment,
    userName: String,
    userAvatar: String,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp) // .diary-item-card{margin-bottom:4px}（叠加列间距 8 = 12）
            .graphicsLayer(scaleX = if (pressed) 0.97f else 1f, scaleY = if (pressed) 0.97f else 1f)
            .cssShadow(12.dp, WebShadow(0f, 4f, 12f, Color.Black.copy(alpha = 0.04f)))
            .clip(shape)
            .background(if (pressed) colors.bgThird else colors.bgCard)
            .border(1.dp, colors.border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        // 封面（.card-cover 最大高 200，多图角标）
        if (item.coverImage.isNotBlank()) {
            Box(Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(shape)) {
                VanImage(
                    src = AppConfig.fullFileUrl(item.coverImage),
                    modifier = Modifier.fillMaxWidth(),
                    fit = androidx.compose.ui.layout.ContentScale.Crop,
                    showError = false,
                )
                if (item.imageCount > 1) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color(0x80000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VanIcon(name = "photo-o", size = 10.sp, color = Color.White)
                        Spacer(Modifier.size(2.dp))
                        FText("${item.imageCount}", 12f, FontWeight.Normal, Color.White)
                    }
                }
            }
        }

        Column(Modifier.padding(10.dp)) { // .card-content{padding:10}
            // 正文：后端列表已去标签+截断 20 字，web 再补一个省略号；两行截断
            BasicText(
                text = item.content + "...",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = scaledSp(14f),
                    lineHeight = scaledSp(18.85f), // line-height: 1.45
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(10.dp)) // .main-text{margin-bottom:10}

            // 心情标签 + 追文标签（.interaction-bar{gap:6; margin-bottom:10}）
            if (item.mood.isNotBlank() || item.childrenCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.mood.isNotBlank()) {
                        Box(
                            Modifier
                                .background(VanPurpleFixed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) { FText(item.mood, 12f, FontWeight.Normal, VanPurpleFixed) }
                    }
                    if (item.childrenCount > 0) {
                        Row(
                            Modifier
                                // web `DiaryCard.vue:250`：`background: var(--van-green-bg, #f0f9eb)` —— 深浅两模式**都**是 #f0f9eb
                                .background(VanGreenBgFixed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VanIcon(name = "chat-o", size = 10.sp, color = vanGreen())
                            Spacer(Modifier.size(3.dp))
                            FText("${item.childrenCount} 笔追文", 12f, FontWeight.Normal, vanGreen())
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 作者 + 时间（.user-meta：padding-bottom 8 + 1px 底线 + margin-bottom 6）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { // .author-info{gap:5}
                    // 后端 moment 行里没有 author/user_name/avatar → web 的兜底链最终落在登录用户上
                    if (userAvatar.isNotBlank()) {
                        VanImage(src = AppConfig.fullFileUrl(userAvatar), width = 14.dp, height = 14.dp, round = true, showError = false)
                    } else {
                        Box(Modifier.size(14.dp).background(colors.bgThird, CircleShape))
                    }
                    Spacer(Modifier.size(5.dp))
                    BasicText(
                        text = userName.ifBlank { "用户" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.textSecondary, fontSize = scaledSp(12f)),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) { // .time-wrap{gap:8}
                    val d = momentDate(item.createTime)
                    if (d.isNotBlank()) {
                        Box(
                            Modifier
                                .background(VAN_GREEN_BG, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) { FText(d, 12f, FontWeight.Normal, vanGreen()) }
                    }
                    Spacer(Modifier.size(8.dp))
                    FText(momentTime(item.createTime), 12f, FontWeight.Normal, colors.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(colors.border))
            Spacer(Modifier.height(6.dp))

            // 位置
            if (item.locationName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VanIcon(name = "location-o", size = 10.sp, color = colors.textTertiary)
                    Spacer(Modifier.size(3.dp))
                    FText(item.locationName, 12f, FontWeight.Normal, colors.textTertiary)
                }
            }
        }
    }
}

/** `create_time`（毫秒时间戳）→ `HH:mm`；无法解析返回空串。 */
private fun momentTime(ts: String): String {
    val ms = ts.toLongOrNull() ?: return ""
    return HHMM.format(Date(ms))
}

/** `create_time` → 「今天」或 `M-D`（web `displayDate`）。 */
private fun momentDate(ts: String): String {
    val ms = ts.toLongOrNull() ?: return ""
    val c1 = Calendar.getInstance().apply { time = Date(ms) }
    val c2 = Calendar.getInstance()
    val sameDay = c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "今天" else "${c1.get(Calendar.MONTH) + 1}-${c1.get(Calendar.DAY_OF_MONTH)}"
}
