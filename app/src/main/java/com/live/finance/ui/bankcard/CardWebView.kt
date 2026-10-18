package com.live.finance.ui.bankcard

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import com.live.finance.core.AppConfig
import com.live.finance.data.model.Card
import com.live.finance.theme.AppTokens
import org.json.JSONArray
import org.json.JSONObject

/**
 * 银行卡管理的**离线 WebView 实现**（取代原生的 `CardManageScreen`，
 * 原实现已备份到 `temp/备份/CardManageScreen-native-20261011/`）。
 *
 * ## 架构：WebView 只渲染，数据出口在原生
 *
 * 与 `DiaryRichEditorWebView` 建立的范式一致（其类注释 `:51-55`）：
 * **WebView 一个网络请求都不发**。卡片列表、银行字典、6 个月动账统计全部由原生
 * `AppGraph` 的 repository 取好后，用 `evaluateJavascript` 推给页面。
 *
 * 这样做的收益（也是离线化能成立的关键）：
 *  - 不必在 WebView 里重建鉴权（AES 握手 + httpOnly Cookie + sessionStorage 存 key）；
 *  - 不必放宽 CSP（`web/.env.production:13` 只允许 `https:`，局域网 `http://`
 *    的接口与 logo 图片全会被拦）；
 *  - `baseURL='/api/v1'` 在 `file://` 下失效的问题自动消失；
 *  - 数据与 App 主通道同源，不会出现「网页看到的和 App 看到的不是一份」。
 *
 * ## 桥协议（两侧必须严格对应）
 * - **JS → 原生** `window.AndroidCardBridge`（桥名见 web `src/webview/bridge.js`）：
 *   `ready()` / `requestData(tab)` / `navigate(path)` / `saveSort(json)` / `onError(msg)`
 * - **原生 → JS** `window.__cardWebview`（页面 `CardOffline.vue` 的 `onMounted` 里注册）：
 *   `applyData(json)` / `setTab(tab)` / `setLoading(tab, v)`
 *
 * JS 侧：`web/src/webview/`，构建入口 `web/vite.config.webview.js`，
 * 产物 `app/src/main/assets/card/`（`webview.html` + `card.js` + `card.css`，约 533KB）。
 */

/** 离线页地址。⚠ 必须与 `assets/card/` 下的文件名一致。 */
internal const val CARD_URL = "file:///android_asset/card/webview.html"

/** 桥名，必须与 web 侧 `bridge.js` 的 `BRIDGE_NAME` 一致。 */
private const val BRIDGE = "AndroidCardBridge"

/** Logcat tag。 */
internal const val TAG = "CardWebView"

/**
 * WebView 侧状态。仿 `RichEditorState` 的写法：普通类 + `mutableStateOf`，由 Composable `remember` 持有。
 */
class CardWebViewState internal constructor() {
    /** JS 已挂载完毕。false 时不能注入 —— 那时 `window.__cardWebview` 还不存在。 */
    var ready by mutableStateOf(false)
        internal set

    /** 渲染进程崩了（低端机 OOM）。 */
    var crashed by mutableStateOf(false)
        internal set

    /** 最近一次错误文案（遮罩 + toast）。 */
    var errorMsg by mutableStateOf<String?>(null)
        internal set

    /** 注入式 toast 出口（项目铁律：`Toast.makeText` 全站保持 0 次）。 */
    internal var onToast: ((String) -> Unit)? = null

    internal fun reset() {
        ready = false
        crashed = false
        errorMsg = null
    }

    internal fun onError(msg: String) {
        android.util.Log.w(TAG, msg)
        errorMsg = msg
        onToast?.invoke(msg)
    }
}

/**
 * 把卡片映射成页面要的 camelCase 结构。
 *
 * 字段与 web `views/BankCard/debit.vue:244-268` 线上版的映射结果**逐字对齐**，
 * 因此离线组件 `CardListPane.vue` 基本不用做数据处理。
 */
internal fun buildCardPayload(
    tab: String,
    cards: List<Card>,
    bankNames: Map<String, String>,
    bankIcons: Map<String, String>,
    flowIdle: Boolean,
): String {
    val arr = cards.map { c ->
        JSONObject().apply {
            put("id", c.id)
            put("bankId", c.bankId)
            put("bankName", bankNames[c.bankId] ?: c.bankName.ifBlank { "未知银行" })
            put("bankIconUrl", bankIcons[c.bankId] ?: "")
            put("cardOrg", c.cardOrg)
            put("cardOrgIconUrl", "")
            put("cardType", c.cardType)
            put("cardBin", c.cardBin)
            put("cardLength", c.cardLength)
            put("last4No", c.last4)
            put("alias", c.alias)
            put("cardLevel", c.cardLevel)
            put("cardImg", c.cardImg)
            put("cardImgUrl", if (c.cardImg.isBlank()) "" else AppConfig.fullFileUrl(c.cardImg))
            put("color", c.color)
            put("isDefault", c.isDefault)
            put("isHide", c.isHide)
            put("status", c.status)
        }
    }
    return JSONObject().apply {
        put("tab", tab)
        // ⚠ 必须显式包 JSONArray：Android 的 org.json 只认 null/String/Number/Boolean/
        //   JSONObject/JSONArray 这几种，把 `List<JSONObject>` 直接 put 是非保证行为
        //   （真机实测：2026-10-11 卡片页 payload 到 JS 侧 `cards` 不是数组 → 空态）。
        put("cards", JSONArray(arr))
        put("flowIdle", flowIdle)
    }.toString()
}

/** 解析 `saveSort` 传来的 `[{"id":"..","sort":1},…]` → `List<Pair<String,Int>>`。 */
internal fun parseSortPairs(json: String): List<Pair<String, Int>> = runCatching {
    JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
        if (!e.isJsonObject) return@mapNotNull null
        val o = e.asJsonObject
        val id = o.get("id")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
        val sort = o.get("sort")?.takeIf { !it.isJsonNull }?.asInt ?: return@mapNotNull null
        id to sort
    }
}.getOrDefault(emptyList())

/**
 * 构造 [WebView]。安全位配置**逐条照抄** `DiaryRichEditorWebView` ——
 * 那里每一条都有踩坑注释，尤其两条血泪：
 *  1. 绝不开 `blockNetworkLoads` / `LOAD_NO_CACHE`（会连主文档带子资源一起被拦 ⇒ 整页空白）
 *  2. `shouldOverrideUrlLoading` 绝不能无条件 `return true`（会中止主文档加载）
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun buildCardWebView(
    ctx: Context,
    state: CardWebViewState,
    onNavigate: (String) -> Unit,
    onSaveSort: (String) -> Unit,
    onRequestData: (String) -> Unit,
    onRefresh: (String) -> Unit,
): WebView {
    val main = Handler(Looper.getMainLooper())

    fun isExternal(url: String) =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    return WebView(ctx).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        // 本页没有输入框（与富文本编辑器不同），不需要那套「焦点 + showSoftInput」兜底
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS

        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setNeedInitialFocus(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            setGeolocationEnabled(false)
            databaseEnabled = false
            // asset 读取官方豁免，维持 targetSdk 34 默认 false：不放开 file:// 通用读盘
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            textZoom = 100
            // ⚠ 2026-10-11 定案：这里曾为「防旧缓存页」开过 LOAD_NO_CACHE，但探针证明
            //   白屏与缓存无关（根因是 height:100% 链在 WebView 首次 0 高布局下解析成 0，
            //   见 CardWebScreen.kt 的 loadUrl 时序注释）。且仓库内编辑器页有
            //   LOAD_NO_CACHE 导致 file:// 资产整页被拦的血泪（DiaryRichEditorWebView.kt:336），
            //   故回退 LOAD_DEFAULT；旧缓存副本改由 loadUrl 前 clearCache(true) 清。
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= 29) forceDark = WebSettings.FORCE_DARK_OFF
        }

        webViewClient = object : WebViewClient() {
            // ⚠⚠ 只拦 http/https 外链，asset 主文档必须放行（否则整页空白）
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = isExternal(url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): Boolean = isExternal(request.url.toString())

            // 主文档加载完成 —— 链路第一环。没打这条 ⇒ HTML 都没加载出来（assets 没进包/路径错）。
            override fun onPageFinished(view: WebView, url: String) {
                CardDebugLog.i("[链路1] onPageFinished: $url")
            }

            // ⚠ 子资源（card.js/card.css）加载失败不会进 onReceivedError 的 isForMainFrame 分支，
            //   不单独记一条的话「JS 没跑」将完全静默 —— 这正是排查「一直 loading」时最需要的信息。
            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                val level = if (request.isForMainFrame) "主文档" else "子资源"
                CardDebugLog.w("[链路?] $level 加载失败 ${error.errorCode}: ${error.description} url=${request.url}")
                if (!request.isForMainFrame) return
                main.post { state.onError("加载失败 ${error.errorCode}: ${error.description}") }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail,
            ): Boolean {
                main.post { state.crashed = true }
                return true
            }
        }

        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                val line = "[console] ${msg.message()} @${msg.lineNumber()}"
                if (msg.messageLevel() >= android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                    CardDebugLog.w(line)
                } else {
                    CardDebugLog.i(line)
                }
                return true
            }
        }

        // WebView → 原生的唯一出口。只暴露这 5 个方法，且都 post 回主线程再动状态。
        val bridge = object {
            @JavascriptInterface
            fun ready() {
                CardDebugLog.i("[链路2] JS ready() 到达 —— 桥已通")
                main.post {
                    state.ready = true
                    // 页面挂载完 ⇒ 立刻推当前 tab 的数据（tab 传空表示「当前」）
                    onRequestData("")
                }
            }

            @JavascriptInterface
            fun requestData(tab: String) {
                CardDebugLog.i("[链路2] JS requestData($tab)")
                main.post { onRequestData(tab) }
            }

            /**
             * 下拉刷新（2026-10-17）：**强制**重新取数，绕开 tab 切换的 10s 节流。
             * 与 requestData 分成两个方法，旧页面（没有 refresh 调用）行为完全不变。
             */
            @JavascriptInterface
            fun refresh(tab: String) {
                CardDebugLog.i("[链路2] JS refresh($tab) —— 下拉刷新")
                main.post { onRefresh(tab) }
            }

            @JavascriptInterface
            fun navigate(path: String) {
                main.post { onNavigate(path) }
            }

            @JavascriptInterface
            fun saveSort(json: String) {
                main.post { onSaveSort(json) }
            }

            @JavascriptInterface
            fun onError(msg: String) {
                CardDebugLog.e("[链路?] JS onError: $msg")
                main.post { state.onError(msg) }
            }
        }
        addJavascriptInterface(bridge, BRIDGE)
    }
}

/** 把一段 JS 塞进页面（参数用 JSON 字面量转义，避免引号/换行把脚本打断）。 */
internal fun WebView.runCardScript(script: String) {
    evaluateJavascript(script, null)
}

/** 注入一份卡片数据。带回执：成功 / 页面抛错 / 被丢弃，三种结局都能在面板区分。 */
internal fun WebView.applyCardData(json: String) {
    // ⚠ 不能用「window.__cardWebview && …」静默守卫 + null callback：脚本执行失败在 WebView 上
    //   **完全静默**（本页 30KB 注入丢失时就是这么哑掉的）。这里让脚本自己返回结局：
    //   成功 → `ok`；页面抛错 → `err:<消息>`；脚本被丢弃 → **callback 根本不回调**（也算证据）。
    val script =
        "(function(){try{if(!window.__cardWebview)return 'no-bridge';" +
            "window.__cardWebview.applyData(${JSONObject.quote(json)});return 'ok'}" +
            "catch(e){return 'err:'+e.message}})()"
    evaluateJavascript(script) { r ->
        val n = json.length
        when {
            r == null || r == "null" -> CardDebugLog.w("[注入回执] 无返回（脚本被丢弃？）len=$n")
            else -> {
                val v = r.removeSurrounding("\"")
                if (v.startsWith("err:") || v == "no-bridge") CardDebugLog.e("[注入回执] $v len=$n")
                else CardDebugLog.i("[注入回执] $v len=$n")
            }
        }
    }
}

/** 切换分段。 */
internal fun WebView.setCardTab(tab: String) {
    runCardScript("window.__cardWebview && window.__cardWebview.setTab(${JSONObject.quote(tab)});")
}

/** 切 loading 遮罩，避免「转圈」与真实请求脱节。 */
internal fun WebView.setCardLoading(tab: String, loading: Boolean) {
    runCardScript("window.__cardWebview && window.__cardWebview.setLoading(${JSONObject.quote(tab)}, $loading);")
}

/**
 * 回读探针：把页面内部状态取回原生，写进调试面板。
 *
 * 排查「数据已注入但页面一直加载中」的最后一环：
 *  - `bridge=undefined`      ⇒ `__cardWebview` 没注册（Vue 的 onMounted 没跑）
 *  - `bootHint=true`         ⇒ 防白闪兜底 div 还在 ⇒ **Vue 根本没挂载**（用户看到的「加载中…」就是它）
 *  - `appLen=0`              ⇒ #app 是空的，同上
 *  - `overlay=true`          ⇒ van-overlay 还在（loading 状态没被关掉）
 *  - `text`                  ⇒ 页面可见文字前 80 字，直接看出显示的是什么
 */
internal fun WebView.probePageState() {
    val script = """
        (function(){try{return JSON.stringify({
          bridge: typeof window.__cardWebview,
          bootHint: !!document.getElementById('boot-hint'),
          appLen: (document.getElementById('app')||{innerHTML:''}).innerHTML.length,
          overlay: !!document.querySelector('.van-overlay'),
          cards: document.querySelectorAll('.card-item,[class*=card-]').length,
          // ===== 观感层几何：当只有 position:fixed 的「+添加卡片」可见时，答案在这些字段里 =====
          vw: window.innerWidth + 'x' + window.innerHeight,
          vv: window.visualViewport ? Math.round(window.visualViewport.height) : -1,
          docScroll: window.scrollY + '/' + document.documentElement.scrollTop + '/' + document.body.scrollTop,
          docSize: document.documentElement.scrollHeight + '/' + document.documentElement.clientHeight,
          // 一次把 #app→body→html 这条祖先链全量出来：渲染高度 / computed height / display。
          // 「视口 674 但根容器只有 154」这类矛盾只能在这里断案 —— 断在哪一环、
          // 以及那一环的 height 到底是被解析成了 auto 还是 100%。
          chain: (function(){
            var out = [], n = document.getElementById('app');
            while (n && n !== document) {
              var cs = getComputedStyle(n);
              var nmm = n.id ? '#' + n.id : String(n.className || n.tagName).split(' ')[0];
              out.push(nmm + ' render=' + Math.round(n.getBoundingClientRect().height) +
                ' css=' + cs.height + ' disp=' + cs.display + ' ovf=' + cs.overflowY);
              n = n.parentElement;
            }
            return out.join(' < ');
          })(),
          paneScroll: (function(){var p=document.querySelector('.page-card-list');
            return p ? p.scrollTop + '/' + p.clientHeight + '/' + p.scrollHeight : 'nil'})(),
          // 顶栏被 scrolled 状态收起时是 opacity:0 + max-height:0，DOM 一切正常、肉眼全空
          header: (function(){var h=document.querySelector('.card-manage-header');
            if(!h) return 'nil'; var c=getComputedStyle(h);
            return (h.className||'') + ' op=' + c.opacity + ' disp=' + c.display +
              ' h=' + Math.round(h.getBoundingClientRect().height)})(),
          card0: (function(){var k=document.querySelector('.bank-card-item');
            if(!k) return 'nil'; var r=k.getBoundingClientRect(); var c=getComputedStyle(k);
            return Math.round(r.width) + 'x' + Math.round(r.height) + '@' + Math.round(r.left) + ',' +
              Math.round(r.top) + ' pos=' + c.position + ' top=' + c.top + ' tf=' + c.transform +
              ' op=' + c.opacity + ' z=' + c.zIndex})(),
          text: (document.body.innerText||'').replace(/\s+/g,' ').slice(0,80)
        })}catch(e){return 'probe-err: '+e.message}})()
    """.trimIndent()
    evaluateJavascript(script) { v -> CardDebugLog.i("[探针] $v") }
}

/**
 * 主题注入：把原生 [AppTokens] 映射成与 web `composables/useUiTheme.js` 的 buildVars
 * **同名**的 CSS 变量，推给页面挂到 `documentElement` 上。
 *
 * 为什么必须运行时推：`style.css` 的 `:root` 只写死浅色默认值，深色/自定义主题在 web 上
 * 全靠 useUiTheme 运行时 setProperty；离线入口不引那个 composable ⇒ 不推就永远浅色，
 * 而系统栏/原生顶栏跟的是原生主题，两边颜色就对不上（2026-10-11 负责人报的问题）。
 *
 * 只推这页 CSS 真正 `var()` 到的变量：14 个 `--theme-*` + `--app-bg` / `--app-text-main` /
 * `--app-card-bg` / `--theme-primary-rgb`。bundle 里 `--van-*` 已整体映射到 `--theme-*`，
 * Vant 组件跟着走，不必逐个覆盖。
 * bg 的对应关系照 web：`bg-primary`=bgPage、`bg-secondary`=bgCard、`bg-tertiary`=bgThird。
 *
 * ⚠ `--app-text-main` 必须推：`style.css` 的 `body{color:var(--app-text-main)}` 是页面
 * 文字的继承源，不推就停在 :root 浅色默认值 #323233 —— 深色模式下标题等继承文字全黑
 * （2026-10-11 负责人报的「部分文字呈现黑色」）。
 */
internal fun WebView.applyCardTheme(tokens: AppTokens) {
    // ⚠⚠ 2026-10-12 定案：不能用 `Color.value` 按位取色！Compose 的 Color(value: Int)
    //    内部是 `int.toULong() shl 32`（ARGB 在**高 32 位**，低 32 位是色彩空间），
    //    按 `value shr 24 and 0xFF` 取出来永远是 0 ⇒ 推出 rgba(0,0,0,0.000)，
    //    页面所有 var() 全变透明（「有颜色变成透明/白色」的根凶）。
    //    正确做法：用 red/green/blue/alpha 浮点组件（与内部打包方式无关）。
    fun css(c: androidx.compose.ui.graphics.Color): String {
        fun ch(f: Float) = Math.round(f * 255f).coerceIn(0, 255)
        val r = ch(c.red)
        val g = ch(c.green)
        val b = ch(c.blue)
        // alpha 走 Locale.ROOT：默认locale 若是逗号小数点，%.3f 会产出 "0,078" ⇒ CSS 非法
        val a = if (c.alpha >= 0.999f) 1f else c.alpha
        return if (a >= 1f) "#%02X%02X%02X".format(r, g, b)
        else "rgba($r,$g,$b,${java.lang.String.format(java.util.Locale.ROOT, "%.3f", a)})"
    }
    fun rgb(c: androidx.compose.ui.graphics.Color): String {
        fun ch(f: Float) = Math.round(f * 255f).coerceIn(0, 255)
        return "${ch(c.red)}, ${ch(c.green)}, ${ch(c.blue)}"
    }
    val vars = JSONObject().apply {
        put("--theme-primary", css(tokens.primary))
        put("--theme-primary-grad", css(tokens.grad))
        put("--theme-primary-light", css(tokens.primaryLight))
        put("--theme-button-primary-text", css(tokens.buttonPrimaryText))
        put("--theme-bg-primary", css(tokens.bgPage))
        put("--theme-bg-secondary", css(tokens.bgCard))
        put("--theme-bg-tertiary", css(tokens.bgThird))
        put("--theme-text-primary", css(tokens.textPrimary))
        put("--theme-text-secondary", css(tokens.textSecondary))
        put("--theme-text-tertiary", css(tokens.textTertiary))
        put("--theme-text-placeholder", css(tokens.textPlaceholder))
        put("--theme-border", css(tokens.border))
        put("--theme-success", css(tokens.success))
        put("--theme-warning", css(tokens.warning))
        put("--theme-danger", css(tokens.danger))
        put("--theme-info", css(tokens.info))
        put("--app-bg", css(tokens.bgPage))
        // 文字继承源（body 的 color）与卡片容器底色，口径同 web buildVars
        put("--app-text-main", css(tokens.textPrimary))
        put("--app-card-bg", css(tokens.bgCard))
        // rgba(var(--theme-primary-rgb), a) 半透明场景（web buildVars 同名同格式 "r, g, b"）
        put("--theme-primary-rgb", rgb(tokens.primary))
    }
    // ⚠ 必须带回执：旧页（WebView 缓存里没有 setTheme 的版本）会让这行脚本静默失败，
    //   表现为「页面停在浅色、切换主题无效」且 Logcat 无任何线索（2026-10-12 实测踩到）。
    //   回执三态：ok / no-bridge（__cardWebview 没注册）/ no-setTheme（旧页）。
    evaluateJavascript(
        "(function(){try{if(!window.__cardWebview)return 'no-bridge';" +
            "if(typeof window.__cardWebview.setTheme!=='function')return 'no-setTheme(旧页)';" +
            "window.__cardWebview.setTheme(${JSONObject.quote(vars.toString())});return 'ok'}" +
            "catch(e){return 'err:'+e.message}})()"
    ) { r -> CardDebugLog.i("[主题回执] $r vars=${vars.length()}") }
}