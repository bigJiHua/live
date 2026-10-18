package com.live.finance.ui.bankcard

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType
import kotlinx.coroutines.launch

/**
 * 银行卡管理页（离线 WebView 版），取代原生的 `CardManageScreen`。
 *
 * 原实现的备份在 `temp/备份/CardManageScreen-native-20261011/`（文件本体未删）。
 * 要回退：把 `AppRoot.kt` 里三条 CARD 路由的注册改回 `CardManageScreen(nav, "debit"/"credit")`。
 *
 * ## 本文件只做三件事
 * 1. 把桥事件接到 `AppGraph` 的 repository（取数 / 排序 / 6 个月动账）
 * 2. 把 web 的 router path 翻译成原生 `Routes`
 * 3. 管 WebView 的生命周期与两层遮罩（崩溃 / 加载中）
 *
 * WebView 的安全位与销毁顺序在 [buildCardWebView] 与 `AndroidView(onRelease=…)` 里，
 * 逐条对齐 `DiaryRichEditorWebView`（尤其：不开 `blockNetworkLoads`、
 * `shouldOverrideUrlLoading` 不无条件 `return true`）。
 *
 * @param initialTab `"debit"` / `"credit"`，语义与原实现一致（路由 `card/credit` 传 credit）
 */
@Composable
fun CardWebScreen(nav: NavHostController, initialTab: String = "debit") {
    val context = LocalContext.current
    val graph = App.of(context).graph
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val scope = rememberCoroutineScope()
    val toast = LocalVanToastController.current

    val state = remember { CardWebViewState() }
    var webRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // 崩溃后靠换 key 强制重建 WebView（照抄 DiaryRichEditorWebView:246,250）
    var reloadKey by remember { mutableStateOf(0) }

    val defaultTab = remember(initialTab) { if (initialTab == "credit") "credit" else "debit" }

    // toast 用注入而不是 Toast.makeText（项目铁律：全站 0 次）
    SideEffect {
        state.onToast = { msg -> toast.long("卡片页异常", msg, VanToastType.Fail, duration = 4_000) }
    }
    DisposableEffect(Unit) { onDispose { state.onToast = null } }

    // 进页面打点 + 8s 看门狗：没收到 ready 就在 Logcat 直接给结论
    LaunchedEffect(Unit) {
        CardDebugLog.i("[链路0] 进入卡片页，开始加载离线 WebView")
        kotlinx.coroutines.delay(8_000)
        if (!state.ready) {
            CardDebugLog.w("[看门狗] 8s 未收到 ready —— JS 未执行或桥未通（看上面有没有 [链路1]/[console]）")
        }
    }

    // ===== 取数 =====
    // ⚠ 节流必须是**页面实例级**，不能再用进程级 object：从编辑/删除页 popBackStack()
    //   返回时 WebView 会重建，若窗口仍记着上一次进入页面时的取数时间，
    //   首屏 fetch 会被直接跳过 ⇒ 页面没有任何数据（2026-10-17 报的「返回后列表变空」）。
    //   每次进入页面（以及崩溃后 reloadKey++）都换一个全新窗口；
    //   tab 之间的 10s 节流语义不变。
    val throttle = remember(reloadKey) { CardWebThrottle() }

    // 桥的 `requestData` / 下拉 `refresh` 与页面 `ready` 都走这里；`tabArg` 为空表示「当前 tab」。
    // `force=true` 只来自下拉刷新：跳过节流，必须真发一次。
    // ⚠ 10s 时间窗节流，对齐 web `debit.vue:290-303` 的 REFRESH_INTERVAL：
    //   页面在两个分段之间来回切时，10s 内不再发请求。
    val fetch: suspend (String, Boolean) -> Unit = fetch@{ tabArg, force ->
        val tab = when (tabArg) {
            "credit" -> "credit"
            "debit" -> "debit"
            else -> defaultTab
        }
        val wv = webRef ?: run { CardDebugLog.w("[链路3] fetch 跳过：webRef 为 null"); return@fetch }
        if (!state.ready) { CardDebugLog.w("[链路3] fetch 跳过：页面未 ready"); return@fetch }
        if (!throttle.take(tab, force)) {
            CardDebugLog.i("[链路3] fetch 跳过：10s 节流窗口内 tab=$tab")
            return@fetch
        }
        CardDebugLog.i("[链路3] 开始取数 tab=$tab")

        // 下拉刷新已有自己的顶部「刷新中…」指示器，不再叠一层居中 loading
        if (!force) wv.setCardLoading(tab, true)

        // 银行字典走 CategoryRepository 的进程内缓存（60s TTL + 并发合并），一般不会打网络
        val banks = when (val r = graph.category.list("bank")) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> emptyList()
        }
        val names = banks.associate { it.id to it.name }
        val icons = banks.filter { it.iconUrl.isNotBlank() }
            .associate { it.id to AppConfig.fullFileUrl(it.iconUrl) }

        val cards = when (val r = graph.card.list(tab)) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> { CardDebugLog.e("[链路3] card.list 失败: $r"); emptyList() }
        }
        CardDebugLog.i("[链路4] 取数完成 tab=$tab cards=${cards.size} banks=${banks.size}")

        // 「近6个月无动账」：web 是选中某张卡后才查（debit.vue:158-173）。
        // 离线版改成对当前列表**批量判断一次**，避免每点选一次就回原生一趟。
        var anyIdle = false
        if (tab == "debit" && cards.isNotEmpty()) {
            // cardsFlowStats 已经把它整理成 `Map<cardId, 支出笔数 to 收入笔数>`
            //（FlowRepository.kt:313-324），这里不能再按 JsonObject 去 cast —— 那条分支永远为 null。
            val stats: Map<String, Pair<Int, Int>> = when (val r = graph.flow.cardsFlowStats(6)) {
                is ApiResult.Ok -> r.data.orEmpty()
                else -> emptyMap()
            }
            anyIdle = cards.any { stats[it.id]?.let { (expense, income) -> expense == 0 && income == 0 } == true }
        }

        // ⚠ 这里必须等页面 onMounted 注册完 __cardWebview 才有效；ready 信号本身就来自
        //   onMounted 之后的 native.ready()，所以到这一步 window.__cardWebview 一定存在。
        val payload = buildCardPayload(tab, cards, names, icons, anyIdle)
        wv.applyCardData(payload)
        // 顺序要紧：**先注数据、后关 loading**。
        // 反了的话注入一旦丢失，页面会先亮出「暂无借记卡」空态，把「注入丢了」伪装成
        // 「加载成功但确实没数据」（2026-10-11 就是被这个假象带偏了一轮）。
        if (!force) wv.setCardLoading(tab, false)
        CardDebugLog.i("[链路5] 数据已注入 tab=$tab cards=${cards.size} payloadLen=${payload.length}")
        // 回读探针：延迟 300ms（让 Vue 的异步 DOM 更新跑完），读页面真实状态。
        // 「一直加载中」的最后一块拼图 —— 页面里那个加载中到底是 boot-hint 还是 van-overlay。
        kotlinx.coroutines.delay(300)
        wv.probePageState()
    }

    // 页面就绪 ⇒ 落到指定 tab 并推数据（非 force：紧跟着 bridge.ready() 的请求，二者只发一次）
    LaunchedEffect(state.ready, reloadKey) {
        if (!state.ready) return@LaunchedEffect
        webRef?.setCardTab(defaultTab)
        fetch("", false)
    }

    // 主题跟随：进页面推一次，之后用户换主题（深浅、自定义主色、单色模式）再推 ——
    // 页面活着的时候也得变。不推就停在 style.css 的浅色默认值，
    // 而系统栏/原生顶栏跟的是原生 tokens，两边颜色对不上。
    // key 用 tokens 本身：AppTokens 是 data class，任一字段变了都会重推。
    LaunchedEffect(state.ready, tokens) {
        if (state.ready) webRef?.applyCardTheme(tokens)
    }

    // ===== web router path → 原生 Routes =====
    val handleNavigate: (String) -> Unit = { path ->
        when {
            path.startsWith("/card/add") -> nav.navigate(Routes.CARD_ADD)
            path.startsWith("/card/edit") -> {
                val id = Regex("id=([^&]+)").find(path)?.groupValues?.getOrNull(1).orEmpty()
                if (id.isNotBlank()) nav.navigate(Routes.cardEdit(id))
            }
            path.startsWith("/finance/report/card-flow") -> {
                val id = Regex("cardId=([^&]+)").find(path)?.groupValues?.getOrNull(1).orEmpty()
                // ⚠ 别用 Routes.CARD_FLOW：那是 web 自身的死页面、原生**有意不注册**
                //   （Routes.kt:102-104）。web 这条实际指向 `finance/report/card-flow`，
                //   原生对应 Routes.cardFlow(cardId)（与旧 CardManageScreen:461 一致）。
                if (id.isNotBlank()) nav.navigate(Routes.cardFlow(id))
            }
            // 其余路径离线页不会发出（页内没有 router），记日志便于发现意外
            else -> android.util.Log.w("CardWebView", "未映射的跳转路径: $path")
        }
    }

    // ===== 拖拽排序 → PUT /card/sort =====
    val handleSaveSort: (String) -> Unit = sort@{ json ->
        val pairs = parseSortPairs(json)
        if (pairs.isEmpty()) return@sort
        scope.launch {
            val r = graph.card.updateSortBatch(pairs)
            if (r is ApiResult.Fail) toast.show(r.message.ifBlank { "排序保存失败" }, VanToastType.Fail)
        }
    }

    // ===== 生命周期 =====
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_PAUSE -> webRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        key(reloadKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    buildCardWebView(
                        ctx = it,
                        state = state,
                        onNavigate = handleNavigate,
                        onSaveSort = handleSaveSort,
                        onRequestData = { tab -> scope.launch { fetch(tab, false) } },
                        // 下拉刷新：强制重新取数，不受 10s 节流限制
                        onRefresh = { tab -> scope.launch { fetch(tab, true) } },
                    ).also { wv ->
                        webRef = wv
                        // ⚠⚠ 必须等 WebView 完成**首次 layout**（拿到真实高度）再 loadUrl：
                        //   factory 执行时 View 还没被测量，Chromium 会按 0 高视口做首次布局，
                        //   页面里 html/body/#app 的 height:100% 全部解析成 0px 且之后不重解析
                        //   ⇒ 高度链上的顶栏/列表全被裁成 0，屏幕上只剩 position:fixed 的元素
                        //   （2026-10-11 真机 + 模拟器实测，探针 chain= 原话：
                        //   `#app render=0 css=0px < BODY render=0 css=0px < HTML render=0 css=0px`）。
                        //   这是「整屏只有一个 +添加卡片 按钮」的根因；与缓存/合成无关。
                        //   并列的第二道修复是根容器改 position:fixed;inset:0（CardOffline.vue）。
                        wv.doOnLayout { v ->
                            if (webRef === wv && v.height > 0) {
                                // 清掉可能残留的旧 file:// 资产缓存副本（编辑器页同款兜底；
                                // 注意别学以前开 LOAD_NO_CACHE —— 那在本仓库有整页被拦的血泪）
                                wv.clearCache(true)
                                CardDebugLog.i("[链路0] loadUrl($CARD_URL) after-layout ${v.width}x${v.height}")
                                wv.loadUrl(CARD_URL)
                            }
                        }
                    }
                },
                onRelease = { wv ->
                    // 销毁顺序照抄 DiaryRichEditorWebView:257-265
                    webRef = null
                    wv.stopLoading()
                    wv.removeJavascriptInterface("AndroidCardBridge")
                    wv.webViewClient = android.webkit.WebViewClient()
                    wv.clearHistory()
                    wv.removeAllViews()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.onPause()
                    wv.destroy()
                },
            )
        }

        // 两层遮罩（后声明 = 盖在上面）
        if (state.crashed || state.errorMsg != null) {
            Column(
                modifier = Modifier.fillMaxSize().background(colors.bgPage),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppButton(text = "重新加载", onClick = { state.reset(); reloadKey++ })
            }
        } else if (!state.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VanLoading(size = 22.sp, text = "卡片加载中…")
            }
        }
    }
}

/**
 * 卡片页的取数节流 —— 10s 窗口，与 web `debit.vue:290-303` 的 `REFRESH_INTERVAL` 对齐。
 *
 * ⚠ **实例级**（`remember(reloadKey)`），不是进程级单例：
 *   web 的 REFRESH_INTERVAL 是页面组件里的局部变量，离开页面即失效；
 *   而卡片页每次从编辑/删除页返回都会重建 WebView，进程级窗口会把新页面的首屏取数挡掉
 *   （2026-10-17「操作完返回列表变空」的根因）。页面实例销毁即随之丢弃。
 * ⚠ 时间窗在取数调用时记录（web 是只在成功后记；这里取数已返回即视为成功，
 *   失败也会走 `ApiResult.Network/Fail`，同样不该被反复重试）。
 */
private class CardWebThrottle {
    private val lastTakenAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * 返回 true 表示允许这次真发请求。
     * @param force 下拉刷新传 true：不查窗口，直接放行并刷新时间戳
     *              （避免刷完立刻又被 tab 切换的自动请求重复打一次）。
     */
    fun take(key: String, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val last = lastTakenAt[key] ?: 0L
        if (!force && now - last < INTERVAL_MS) return false
        lastTakenAt[key] = now
        return true
    }

    private companion object {
        const val INTERVAL_MS = 10_000L
    }
}