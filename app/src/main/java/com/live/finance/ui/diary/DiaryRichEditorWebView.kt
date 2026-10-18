package com.live.finance.ui.diary

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.live.finance.theme.AppTokens
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastType
import org.json.JSONObject

private const val EDITOR_URL = "file:///android_asset/editor/index.html"

/**
 * 精准模式编辑器 —— **离线 WebView 承载 WangEditor v5**（`assets/editor/index.html`）。
 *
 * 数据流**单向**：WebView 只把编辑结果交给原生（`onHtml` / `onReady`），
 * 提交、AES 信封、PIN 链全部由原生 `graph.moment.create` 完成 —— **WebView 不发任何请求**。
 * 原生 → WebView 只下发「指令」（内联图片 / 注入初值 / 换主题）。
 *
 * 2026-09-20 复活：此能力曾在 2026-09-15 被拍板砍掉（不引库、不嵌 WebView），
 * 现按负责人要求加回，取舍记录见 `DiaryAddScreen` 头注第 1 条。
 */
class RichEditorState internal constructor() {

    /** 编辑器最新的 HTML（`editor.getHtml()`）—— 提交时原样作为 `content`。 */
    var html by mutableStateOf("")
        internal set

    /** `editor.getText()`：web 用它判 `hasText`，原生口径一致。 */
    var text by mutableStateOf("")
        internal set

    /** `onCreated` 回调过才为 true（web：`if (!editor) return showToast("编辑器未就绪")`）。 */
    var ready by mutableStateOf(false)
        internal set

    /** WebView 渲染进程崩了（低端机加载 1.3MB 的 wangEditor 有 OOM 风险）。true ⇒ 页面应提示用户重进。 */
    var crashed by mutableStateOf(false)
        internal set

    /**
     * 页面回报的「工具条 + 10 行书写区」总高度（CSS px，与 dp 1:1）。
     *
     * 2026-10-18：书写区改为固定 10 行后，页面总高与视口无关，
     * 必须由页面实测后回报、原生据此设置 WebView 高度 —— 否则 WebView 要么留着
     * 一大块空白，要么把页面裁掉。0 = 还没量到（用兜底高度）。
     */
    var editorHeightPx by mutableStateOf(0)
        internal set

    private var view: WebView? = null
    private val pendingScript = mutableListOf<String>()
    private var seeded = false
    private val main = Handler(Looper.getMainLooper())

    /**
     * 错误提示出口 —— 由 [DiaryRichEditorWebView] 从 `LocalVanToastController` 注入。
     *
     * ⚠ **不要退回 `android.widget.Toast`**：本 State 是普通类（没有 Composition），
     * 早先只能拿系统 Toast 兜底，但系统 Toast 是「底部深灰方块」，与全站自研的
     * 「顶部居中 tint 卡片」观感完全不同 ⇒ 项目铁律是 `Toast.makeText` 出现次数保持 0。
     * 注入失败（理论上只在组合之外调用）时退化成只打 Logcat，不会误弹一个异形提示。
     */
    internal var onToast: ((String) -> Unit)? = null

    internal fun attach(webView: WebView) {
        view = webView
        seeded = false
    }

    internal fun detach() { view = null }

    /** 编辑器尚未 created 时先排队（`AndroidView` 的 factory 一定早于 JS 初始化）。 */
    private fun runScript(js: String) {
        val wv = view
        if (wv == null || !ready) {
            pendingScript.add(js)
            return
        }
        main.post { wv.evaluateJavascript(js, null) }
    }

    /** 插入 HTML（web：`editor.focus(); editor.dangerouslyInsertHtml(imgsHtml)`）。 */
    fun insertHtml(html: String) = runScript("liveEditor.insertHtml(${JSONObject.quote(html)})")

    /**
     * 首次就绪时注入初值（快速→精准迁移来的纯文本），只播一次，
     * 否则用户后续编辑会被 recomposition 冲掉。
     */
    fun seedHtml(html: String) {
        if (seeded || html.isBlank()) return
        seeded = true
        runScript("liveEditor.setHtml(${JSONObject.quote(html)})")
    }

    /**
     * 注入主题（2026-09-20 负责人拍板：**编辑区背景跟随主题**，不留 web 那套白底 #333）。
     * 页面里所有 `--w-e-*` 取色点都挂在这七个 `--t-*` 上，一次给全，避免半主题半白色。
     */
    internal fun setTheme(tokens: AppTokens) {
        val hex = { c: Color -> "#%06X".format(0xFFFFFF and c.toArgb()) }
        runScript(
            "liveEditor.setTheme({" +
                "'--t-bg-page':'${hex(tokens.bgPage)}'," +
                "'--t-bg-card':'${hex(tokens.bgCard)}'," +
                "'--t-bg-third':'${hex(tokens.bgThird)}'," +
                "'--t-border':'${hex(tokens.border)}'," +
                "'--t-text-primary':'${hex(tokens.textPrimary)}'," +
                "'--t-text-secondary':'${hex(tokens.textSecondary)}'," +
                "'--t-text-tertiary':'${hex(tokens.textTertiary)}'" +
                "})",
        )
    }

    internal fun onReady() {
        ready = true
        val queue = pendingScript.toList()
        pendingScript.clear()
        queue.forEach { runScript(it) }
    }

    /**
     * 把防抖窗口里还没同步的内容**立刻**推回来。
     * 提交 / 页面暂停 / 失焦前必须调，否则会拿到最多 250ms 前的旧 HTML。
     */
    fun flush() = runScript("liveEditor.flush()")

    /**
     * `flush()` + **等到内容真的回到原生**（最多 300ms）。
     *
     * ⚠ `evaluateJavascript` 是异步的，而 `onHtml` 又是 post 回主线程 ⇒ `flush()` 之后**立刻**读
     *   [html] 拿到的还是旧值。提交前必须用它，否则「打完字马上点发布」会丢最后几个字。
     */
    suspend fun flushAndAwait() {
        val d = kotlinx.coroutines.CompletableDeferred<Unit>()
        flushWait = d
        flush()
        kotlinx.coroutines.withTimeoutOrNull(300) { d.await() }
        flushWait = null
    }

    private var flushWait: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** [onHtml] 落库后调用：唤醒 [flushAndAwait]。 */
    internal fun notifySynced() { flushWait?.complete(Unit) }

    /**
     * 诊断出口：页面/JS 出错、加载失败都走这里。
     * 编辑器是**离线黑盒**，一旦整页空白（他反馈的「没有输入区域」）在真机上根本看不出原因，
     * 所以出错直接 toast 出来 + 打 Logcat（`RichEditor` tag）。
     *
     * 提示走注入的 [onToast]（全站统一的顶部 tint 卡片），**不再用系统 Toast**。
     */
    internal fun onError(msg: String) {
        android.util.Log.e("RichEditor", msg)
        onToast?.invoke(msg)
    }

    /** 重试前清干净状态（crashed 后点「重新加载」）。 */
    internal fun reset() {
        crashed = false
        ready = false
        seeded = false
        view = null
        pendingScript.clear()
    }

    internal fun onCrashed() {
        crashed = true
        ready = false
        view = null
        pendingScript.clear()
    }
}

@Composable
fun DiaryRichEditorWebView(
    state: RichEditorState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAppTokens.current
    // 进组合的那一刻的 HTML = 快速模式迁移过来的初值（web：`htmlContent = htmlContent || <p>…</p>`）
    val initialHtml = remember { state.html }

    // 错误提示出口：`RichEditorState` 是普通类、拿不到 Composition，所以在这里把自研 toast 注入进去
    // （长信息形态 = 标题 + 描述，4 秒够看清；**不退回系统 Toast**，见 `onToast` 的注释）。
    val toast = LocalVanToastController.current
    SideEffect { state.onToast = { msg -> toast.long("编辑器错误", msg, VanToastType.Fail, duration = 4_000) } }
    DisposableEffect(Unit) { onDispose { state.onToast = null } }

    // 未就绪时这两条只会入队，`onReady` 统一补发，故不需要以 ready 为 key。
    // 主题的 key 必须同时带 `isDark`：同一预设切深浅色时 `presetKey` 不变。
    LaunchedEffect(Unit) { state.seedHtml(initialHtml) }
    // 看门狗：1.3MB 的 wangEditor 在差的机器上解析要几秒，但**超过 8 秒还没 ready 基本就是没起来**
    //   ⇒ 报出来，避免「整页空白、用户只会以为点不动」。
    LaunchedEffect(Unit) {
        delay(8_000)
        if (!state.ready && !state.crashed) state.onError("8s 未就绪（编辑器可能没起来）")
    }
    LaunchedEffect("${tokens.presetKey}-${tokens.isDark}") { state.setTheme(tokens) }

    // 后台时暂停 WebView（停掉 JS 定时器 / 视频解码 / 合成），回到前台再恢复 —— 省电且避免后台空转。
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { state.flush(); wv.onPause() }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> wv.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 崩溃后点「重新加载」⇒ 换 key 强制重建 WebView
    var reloadKey by remember { mutableStateOf(0) }

    // 10 行固定书写区（2026-10-18）：高度 = 页面回报的「工具条 + 288px 书写区」。
    // 工具条会随宽度换行，原生算不出准确值，所以必须由页面实测回报；
    // 未回报前用 420dp 兜底，避免首帧 0 高导致 WebView 不加载。
    //
    // ⚠⚠ 回报值是 **CSS px**，WebView 的 1 CSS px ≈ 1 dp，**直接当 dp 用**。
    //   不要再走 `with(density){ px.toDp() }` —— 那是把「物理像素」换算成 dp，
    //   在 2.75 密度设备上会把 380 缩成 138dp，表现就是 WebView 高度塌陷、
    //   输入区只剩一两条行高（2026-10-18 真机实测踩到）。
    val editorHeight = if (state.editorHeightPx > 0) {
        state.editorHeightPx.dp
    } else {
        // 兜底要**大于**「工具条 + 288px 书写区」（约 380dp），
        // 否则页面回报前的首帧会把输入区底部裁掉。多出的底部与页面同色，看不见。
        420.dp
    }

    Box(modifier.height(editorHeight)) {
        // WebView 先声明（画在下层），状态遮罩后声明 ⇒ **盖在上面**。
        key(reloadKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // 一次布局可能触发多次（尺寸变化），只允许加载一次
                    var loaded = false
                    buildWebView(ctx, state).also { wv ->
                        webViewRef = wv
                        // ⚠⚠ 必须等 WebView 完成**首次 layout**（拿到真实视口高度）再 loadUrl：
                        //   factory 执行时 View 还没被测量，Chromium 会按 0 高视口做首次布局，
                        //   页面 html/body 的 height:100% 全部解析成 0 且之后不重解析
                        //   ⇒ 旧页只剩 min-height 兜底的 200px，工具栏再吃掉一半，
                        //   书写区只有 4 行文字高（2026-10-17 负责人报的问题）。
                        //   与 CardWebScreen.kt 的 loadUrl 时序修复同因同解，不要退回直接 loadUrl。
                        wv.doOnLayout { v ->
                            if (!loaded && webViewRef === wv && v.height > 0) {
                                loaded = true
                                // ⚠ 必须清 file:// 缓存副本：assets 页面同样会被 WebView 缓存，
                                //   只装新 APK 仍可能继续跑旧 index.html（2026-10-17 负责人反馈
                                //   「日记改了无变化」的头号嫌疑，与 CardWebScreen loadUrl 前同款处理）。
                                wv.clearCache(true)
                                wv.loadUrl(EDITOR_URL)
                            }
                        }
                    }
                },
                onRelease = { wv ->
                    // 标准销毁顺序：先停加载 → 摘掉 JS 桥 → 清 history/client → 摘子 View → destroy。
                    // 少了前几步，WebView 会持有 Activity/Context 引用导致泄漏（编辑器页反复进出会累积）。
                    state.detach()
                    wv.stopLoading()
                    wv.removeJavascriptInterface("AndroidBridge")
                    wv.webViewClient = WebViewClient()
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.onPause()
                    wv.destroy()
                    webViewRef = null
                },
            )
        }

        // 加载中 / 崩溃时**别留一片空白**（他反馈「没有输入区域、点不动」却完全不知道发生了什么）。
        if (state.crashed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FText("编辑器异常退出", 15f, FontWeight.Medium, LocalAppColors.current.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    AppButton(
                        text = "重新加载",
                        onClick = { state.reset(); reloadKey++ },
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Small,
                        round = true,
                    )
                }
            }
        } else if (!state.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VanLoading(size = 22.sp, text = "编辑器加载中...")
            }
        }
    }
}

/** 只有 http/https 才算「要跳出去的外链」；`file:///android_asset/…` 必须放行由 WebView 自己加载。
 *
 * ## 安全整改 P2-21 的处置说明（2026-10-08）
 * 初版审计把这里列为「放行明文 http 的外链」，**经复核判定不改**，理由：
 * 1. 本函数是 `shouldOverrideUrlLoading` 的判定，语义是「这个 URL 要不要交给系统浏览器」，
 *    而不是「这个 URL 安不安全」—— 它**必须**同时认 http 与 https，否则内网 http 场景下
 *    `http://192.168.0.103:666/...` 的外链会被误判为站内地址、由 WebView 直接加载，
 *    反而破坏功能。
 * 2. 真正的修复是「所有请求走 https」，即安全整改 **P0-4**（强制 HTTPS + Pinning）。
 *    在 P0-4 落地前（`build.gradle.kts:45,51` 的 BASE_URL 仍是内网 http），
 *    此处收紧只会让 App 无法使用。
 * 3. 因此「明文传输」这一风险统一登记在 Plan.md 的 **R-2**，随 P0-4 一并关闭。
 */
private fun isExternalUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(ctx: Context, state: RichEditorState): WebView {
    val main = Handler(Looper.getMainLooper())
    return WebView(ctx).apply {
        val wv = this
        state.attach(this)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        // 「点得进、键盘不弹」的对症：WebView 嵌在 Compose 里时，窗口焦点不一定落到它身上，
        // 而 contenteditable 要 IME 就必须先有 view 级焦点（页面里 pointerdown 会回调 onNeedFocus）。
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        // 兜底：JS 那条 `pointerdown → onNeedFocus` 链路万一没生效（脚本还没注入 / 事件被吞），
        // 手指按下时也直接把焦点要过来 —— 不消费事件，照常交还 WebView 处理点击与光标定位。
        setOnTouchListener { v, ev ->
            if (ev.action == android.view.MotionEvent.ACTION_DOWN && !v.hasFocus()) {
                v.requestFocus()
            }
            false
        }
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // —— 性能 / 内存 / 安全（2026-09-20 优化）——
            // ⚠ **不要再开 `blockNetworkLoads = true` / `LOAD_NO_CACHE`**：
            //   这两个看着"纯离线很安全"，但 `file:///android_asset/` 页面在某些 WebView 版本上
            //   会**连主文档带子资源一起被拦**，表现就是整页空白、点哪儿都没反应（他 2026-09-20 反馈的
            //   「没有输入区域 / 键盘不弹」）。离线诉求改由「页面不发请求 + 不放开跨源 file 读盘」保证。
            // 资产页需要 file 通道（子资源 wangEditor.js / wangEditor.css 都是相对路径的 asset URL）。
            allowFileAccess = true
            // 别在加载时抢焦点（否则一进页面软键盘就顶上来）
            setNeedInitialFocus(false)
            // 编辑器不需要缩放：关掉缩放手势处理，省一层触摸拦截与双击判定
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // 编辑器内容是 base64 内联图，不需要网络图；本地子资源照常加载
            loadsImagesAutomatically = true
            // 用不到的能力一律关掉
            setGeolocationEnabled(false)
            databaseEnabled = false
            // file:///android_asset 的读取不受 allowFileAccess 约束（官方文档明示豁免），
            // 所以这里维持 targetSdk 34 的默认 false：不放开 file:// 通用读盘。
            allowFileAccess = false
            allowContentAccess = false
            // 绝不开 allowUniversalAccessFromFileURLs / allowFileAccessFromFileURLs ——
            // 那会让页面脚本能 XHR 读应用私有目录。
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= 29) forceDark = WebSettings.FORCE_DARK_OFF
        }
        // 编辑器里可以有 `<a>`，点它不该把整页带走；图片等子资源不走这个回调，所以放行 asset 即可。
        webViewClient = object : WebViewClient() {
            // ⚠⚠ **原实现 `return true` 是无条件的** ⇒ 连主文档 `file:///android_asset/editor/index.html`
            //   都被它拦掉（WebView 认为宿主已处理 ⇒ **中止本次加载**）。
            //   后果正是他反馈的全套症状：**整页空白 → 没有输入区域 → 没有光标 → 键盘不弹**，
            //   且 `onReady` 永远不来 ⇒ 插图的 `insertHtml` 永久排在队列里（精准模式图片也不显示）。
            //   正确做法：只拦 http/https 外链，asset 主文档必须放行。
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = isExternalUrl(url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): Boolean = isExternalUrl(request.url.toString())

            // 低端机加载 1.3MB 的 wangEditor 有概率把渲染进程搞崩（OOM）。
            // 不处理的话表现是「编辑器整块空白、怎么点都没反应」，用户完全不知道发生了什么。
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                main.post { state.onCrashed() }
                return true   // 已处理，别让系统再弹一次崩溃
            }

            // 主文档加载失败（API 23+）：asset 路径写错 / 资源没打进包 / 被拦截都会走这里
            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                val m = "加载失败 ${error.errorCode}: ${error.description}"
                main.post { state.onError(m) }
            }
        }
        // JS 的 console / 运行期错误：走 Logcat，方便真机取证
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("RichEditor", "[${msg.messageLevel()}] ${msg.message()} @${msg.lineNumber()}")
                return true
            }
        }
        // WebView → 原生的唯一数据出口（见 [RichEditorState]）：编辑器算好的 HTML + 纯文本 + 就绪信号。
        // 只有这三个方法暴露给 JS，且都切回主线程再动状态/焦点。
        val bridge = object {
            @JavascriptInterface
            fun onReady() { main.post { state.onReady() } }

            /**
             * 页面回报「工具条 + 固定 10 行书写区」的总高度（CSS px ≈ dp）。
             * 原生据此设置 WebView 高度，页面不再依赖视口高度。
             */
            @JavascriptInterface
            fun onEditorHeight(px: Int) {
                main.post { if (px > 0) state.editorHeightPx = px }
            }

            @JavascriptInterface
            fun onHtml(html: String, text: String) {
                // 内容没变就别 post（JS 侧已判过一次，这里再挡一道跨线程开销）
                if (state.html == html && state.text == text) return
                main.post {
                    state.html = html
                    state.text = text
                    state.notifySynced()
                }
            }

            /** 手指按文本区时把窗口焦点要回来，否则软键盘不弹。 */
            /** 手指按文本区时把窗口焦点要回来，否则软键盘不弹。 */
            @JavascriptInterface
            fun onNeedFocus() {
                main.post {
                    if (!wv.hasFocus()) wv.requestFocus()
                    // ⚠ 光 requestFocus 不够：Compose 里嵌的 WebView 常常**焦点有了但 IME 不起**，
                    //   必须显式 showSoftInput（他反馈「连键盘都不弹起」的对症）。
                    val imm = wv.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(wv, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }

            /** JS 运行期错误（window.onerror）—— 编辑器起不来时唯一的线索。 */
            @JavascriptInterface
            fun onError(msg: String) { main.post { state.onError(msg) } }
        }
        addJavascriptInterface(bridge, "AndroidBridge")
    }
}
