package com.live.finance.ui.diary
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.loc.requestGpsOnce
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.local.DiaryListCache
import com.live.finance.data.model.Resource
import com.live.finance.data.model.htmlToPlain
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanGreen
import com.live.finance.theme.VanGrayFixed
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.compressUriToJpeg
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanAction
import com.live.vant.feedback.VanActionSheet
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType
/** web `moodActions` 六项（中文文案，不是 emoji）。 */
private val MOODS = listOf("开心", "平静", "难过", "累", "兴奋", "郁闷")
/** web `.image-item.checked{border-color: var(--van-green)}`。 */
/** 上传库业务类型（web `BusType.POST`）。 */
private const val BUS_TYPE_POST = "post"
/**
 * 发布动态 —— 复刻 web `views/Diary/Add.vue`（**快速 + 精准**两种模式）。
 *
 * 结构：顶栏（取消 / 快速·精准 / 发布）→ 编辑区（快速=屏幕 3/8；精准=工具条 + 富文本）→
 *      元信息三行（今日心情 / 添加图片 / 当前位置，**紧随编辑区**）→
 *      心情·位置 ActionSheet → 图片选择弹窗（库内多选 + 上传，快速 9 张 / 精准 20 张）。
 *
 * ⚠ **拍板**：
 *  1. ~~（2026-09-15）Android 端不做富文本、不嵌 WebView~~ → **（2026-09-20 负责人改口）精准模式复活**：
 *     用 `app/src/main/assets/editor/` 的**离线 WangEditor v5 + WebView** 承载，
 *     数据流**单向**（WebView → 原生 `onHtml`），提交仍由原生 `moment.create` 走 AES 信封 + PIN 链，
 *     WebView 不发任何请求。编辑器实现见 [DiaryRichEditorWebView]。
 *  2. ~~（2026-09-15）去掉「快速 / 精准」切换~~ → **同日加回**（web `van-nav-bar` 中间的 `.mode-toggle`），
 *     图片上限随模式变：快速 9 / 精准 20（web `MAX_SELECT_QUICK` / `MAX_SELECT_PRECISION`）。
 *  3. （2026-09-17）**编辑区高度 = 屏幕 1/2 再缩 1/4 = 屏幕的 3/8**（原先被 `weight(1f)` 撑满剩余空间，观感太高）；
 *     输入框撑满该区域（点区域内任意处即可聚焦），文案超出后在区域内滚动。
 *     精准模式沿用 web 的 `height:400px`，并按屏幕高度 1/2 收口（否则元信息三行会被挤出屏幕）。
 *  4. （2026-09-17）**元信息三行紧随编辑区**（= web `.meta-cells{margin-top:12px}` 的位置），不再顶到底部。
 *  因此**不要**在 UI 里加"降级说明"之类 web 没有的文案。
 *
 * **定位（2026-09-18 已接入）**：web 走 `utils/geo` 的三条入口，原生按同样三项 1:1 落地 ——
 * ①「精确定位」取系统 GPS；②「高德定位」取 GPS 后调 `api/` 侧逆地理编码接口换地址（原生**不引高德 SDK**，
 * 因为 api 侧已接）；③「大致位置」走 `api/` 侧 IP 定位。
 * 前两项会先弹系统定位权限（等价 web 的浏览器授权弹窗），被拒时按 web 文案提示；
 * 落库字段与 web 一致：`locType`（gps / ip）+ `lat` / `lng`，**不写假坐标**。
 */
@Composable
fun DiaryAddScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val maxPopupH = LocalConfiguration.current.screenHeightDp.dp * 0.7f
    /** 编辑区默认高度 = 屏幕 1/2 再缩 1/4 = **屏幕的 3/8**（用户拍板 2026-09-17）。 */
    val editorHeight = LocalConfiguration.current.screenHeightDp.dp * (0.5f * 0.75f)

    // ===== 键盘策略（2026-10-17 负责人拍板）=====
    // manifest 里 Activity 是 adjustResize：键盘一弹出窗口就整体变矮，Column 跟着压缩，
    // 底部的「今日心情 / 添加图片 / 当前位置」被顶到键盘上方 —— 负责人明确不要这个行为。
    // 只在本页临时切 ADJUST_NOTHING：窗口尺寸不变，键盘直接覆盖底部三段元信息；
    // 编辑器（weight 1f）仍在键盘上方，WebView 自己会把光标滚进可视区。
    // 离开本页恢复原值，其它页面继续吃 manifest 的 adjustResize。
    val activity = remember(ctx) { ctx.findActivity() }
    DisposableEffect(activity) {
        val win = activity?.window
        val original = win?.attributes?.softInputMode
            ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
        var disposed = false
        if (win != null) {
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            android.util.Log.i("DiaryAdd", "softInputMode=ADJUST_NOTHING (was=$original)")
            // 部分 ROM 在首帧前设置会被后续布局覆盖 ⇒ decorView 附着后再补一次
            win.decorView.post {
                if (!disposed) {
                    win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                }
            }
        }
        onDispose {
            disposed = true
            win?.setSoftInputMode(original)
        }
    }
    /** `quick` / `precision`（web `mode`）。 */
    var mode by remember { mutableStateOf("quick") }
    /** 精准模式的编辑器状态（HTML / 纯文本 / 就绪标记），WebView 单向写回。 */
    val rich = remember { RichEditorState() }
    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("开心") }
    /** 写入数据的展示名（不含 emoji 前缀，对齐 web `locationData.name`）。 */
    var locationName by remember { mutableStateOf("") }
    /** 页面展示文案（含 emoji 前缀，对齐 web `locationText`）。 */
    var locationDisplay by remember { mutableStateOf("") }
    var locType by remember { mutableStateOf("") }
    var locLat by remember { mutableStateOf(0.0) }
    var locLng by remember { mutableStateOf(0.0) }
    /** 待系统授权完成后要执行的动作（gps / amap）。 */
    var pendingLoc by remember { mutableStateOf("") }
    var showMood by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    // 已选图片（顺序展示）
    var selected by remember { mutableStateOf<List<Resource>>(emptyList()) }
    // 图片库
    var library by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(false) }
    var localSelected by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    /** web `MAX_SELECT`：快速 9 张 / 精准 20 张。 */
    val maxSelect = if (mode == "precision") 20 else 9
    /**
     * web `switchMode`：两模式之间搬运纯文本。
     * 精准→快速取 `getText()`（仅在快速框为空时写入），快速→精准把文本转义成 `<p>`。
     */
    fun switchMode(newMode: String) {
        if (newMode == mode) return
        if (mode == "precision" && newMode == "quick") {
            val text = rich.text.trim()
            if (text.isNotEmpty() && content.isBlank()) content = text
        }
        if (mode == "quick" && newMode == "precision" && content.isNotBlank() && rich.html.isBlank()) {
            rich.html = "<p>${escapeHtml(content)}</p>"
        }
        mode = newMode
    }
    /** web `utils/geo` 失败文案（仅 2 条浏览器专属文案按平台改写，其余逐字照抄）。 */
    fun gpsErrorMsg(reason: String): String = when (reason) {
        "denied" -> "定位权限已被拒绝，请在系统设置中允许定位权限"
        "unsupported" -> "设备不支持定位"
        else -> "获取位置失败"
    }
    /** 执行一次定位（对齐 web `onLocationSelect` 三分支的提示文案与落库口径）。 */
    fun runLocation(action: String) {
        when (action) {
            "gps" -> {
                toast.show("正在获取精确位置...")
                scope.launch {
                    val fix = requestGpsOnce(ctx)
                    if (!fix.granted) { toast.show(gpsErrorMsg(fix.reason)); return@launch }
                    locationName = fix.address
                    locationDisplay = "📍 ${fix.address}"
                    locType = "gps"; locLat = fix.lat; locLng = fix.lng
                }
            }
            "amap" -> {
                toast.show("正在获取高德定位...")
                scope.launch {
                    val fix = requestGpsOnce(ctx)
                    if (!fix.granted) { toast.show(gpsErrorMsg(fix.reason)); return@launch }
                    val addr = when (val r = graph.geo.reverseGeocode(fix.lng, fix.lat)) {
                        is ApiResult.Ok -> r.data?.address.orEmpty().ifBlank { fix.address }
                        else -> fix.address
                    }
                    locationName = addr
                    locationDisplay = "📍 $addr"
                    locType = "gps"; locLat = fix.lat; locLng = fix.lng
                }
            }
            "ip" -> {
                toast.show("正在获取大致位置...")
                scope.launch {
                    when (val r = graph.geo.ipLocation()) {
                        is ApiResult.Ok -> {
                            val place = r.data
                            if (place == null || place.address.isBlank()) {
                                toast.show("IP定位失败", VanToastType.Fail)
                            } else {
                                locationName = place.address
                                locationDisplay = "🌐 ${place.address}"
                                locType = "ip"; locLat = place.lat; locLng = place.lng
                            }
                        }
                        else -> toast.showFail(r, "IP定位失败")
                    }
                }
            }
        }
    }
    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val action = pendingLoc
        pendingLoc = ""
        if (result.values.none { it }) {
            toast.show(gpsErrorMsg("denied"))
        } else if (action.isNotEmpty()) {
            runLocation(action)
        }
    }
    /** 三路定位入口：GPS 两路先申请系统定位权限（对应 web 的浏览器权限弹窗）。 */
    fun startLocation(action: String) {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (action != "ip" && !fine && !coarse) {
            pendingLoc = action
            locPermLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            return
        }
        runLocation(action)
    }
    fun loadLibrary() {
        libraryLoading = true
        scope.launch {
            library = when (val r = graph.resource.list(BUS_TYPE_POST, "", 100, 0)) {
                is ApiResult.Ok -> r.data.orEmpty()
                else -> emptyList()
            }
            libraryLoading = false
        }
    }
    // 编辑器渲染进程崩了（1.3MB 的 wangEditor 在低端机有 OOM 风险）⇒ 明确告诉用户，
    // 否则表现就是「一块空白、怎么点都没反应」，用户完全不知道发生了什么。
    LaunchedEffect(rich.crashed) {
        if (rich.crashed) toast.show("编辑器异常已退出，请重新进入本页", VanToastType.Fail)
    }
    // web：watch(showImagePicker) → 打开时拉列表 + 回填已选
    LaunchedEffect(showPicker) {
        if (showPicker) {
            loadLibrary()
            localSelected = selected.map { it.id }
        }
    }
    // ===== 上传（web van-uploader：#before-read 校验图片 + 35MB；>5MB 先压缩再传）=====
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris: List<Uri> ->
        if (uris.isEmpty() || uploading) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            var failed = 0
            for (uri in uris) {
                val f = withContext(Dispatchers.IO) { compressUriToJpeg(ctx, uri, maxSide = 2048) }
                if (f == null) {
                    failed++
                    continue
                }
                val form = mapOf("busType" to BUS_TYPE_POST)
                when (val up = graph.client.uploadRaw("/upload/single", "file", f.fileName, f.mime, f.bytes, form)) {
                    is ApiResult.Ok -> {
                        val o = up.data
                        val path = o?.get("file_path")?.takeIf { !it.isJsonNull }?.asString
                            ?: o?.get("url")?.takeIf { !it.isJsonNull }?.asString
                        val newId = o?.get("id")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        if (path.isNullOrBlank()) {
                            failed++
                        } else {
                            val row = Resource(
                                id = newId.ifBlank { "tmp_${System.currentTimeMillis()}" },
                                busType = BUS_TYPE_POST,
                                fileName = f.fileName,
                                filePath = path,
                                thumbnail = o?.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                            )
                            library = listOf(row) + library
                            if (!localSelected.contains(row.id)) localSelected = listOf(row.id) + localSelected
                        }
                    }
                    else -> failed++
                }
            }
            uploading = false
            if (failed > 0) toast.show("部分图片上传失败", VanToastType.Fail) else toast.show("上传成功", VanToastType.Success)
        }
    }
    fun toggleSelect(item: Resource) {
        localSelected = if (localSelected.contains(item.id)) {
            localSelected - item.id
        } else {
            if (localSelected.size >= maxSelect) {
                toast.show("最多${maxSelect}张")
                localSelected
            } else {
                localSelected + item.id
            }
        }
    }
    /** web `confirmSelection` 的精准分支：URL 过安全校验后拼 `<img>`，交给编辑器 `dangerouslyInsertHtml`。 */
    fun precisionImgsHtml(items: List<Resource>): String {
        val base = AppConfig.publicBaseUrl
        return items.mapNotNull { item ->
            val url = AppConfig.fullFileUrl(item.filePath)
            if (!url.startsWith(base) && !url.startsWith("https://")) return@mapNotNull null
            "<img src=\"$url\" style=\"max-width:100%;border-radius:8px;margin:4px 0\"/>"
        }.joinToString("")
    }
    /** web `handleSave`：两种模式各自校验，精准模式提交 HTML 并从 HTML 里回收图片相对路径。 */
    fun doPublish(finalContent: String, images: List<String>) {
        submitting = true
        scope.launch {
            val r = graph.moment.create(
                content = finalContent,
                images = images,
                mood = mood,
                locationName = locationName,
                lat = locLat,
                lng = locLng,
                locationType = locType,
            )
            submitting = false
            when (r) {
                is ApiResult.Ok -> {
                    // 列表有本地快照：发布成功必须失效，否则返回到动态页会先显示旧列表。
                    DiaryListCache.clear(ctx)
                    toast.show("成功", VanToastType.Success)
                    nav.popBackStack()
                }
                else -> toast.showFail(r, "失败")
            }
        }
    }
    fun publish() {
        if (mode == "quick") {
            if (content.isBlank()) {
                toast.show("请输入内容")
                return
            }
            doPublish(content, selected.map { it.filePath })
            return
        }
        // ===== 精准模式 =====
        // ⚠ 编辑器内容是**防抖**同步的（250ms），且 `evaluateJavascript` 异步 ⇒
        //   点「发布」前必须 `flushAndAwait()`，否则「打完字立刻点发布」会丢最后几个字。
        if (!rich.ready) {
            toast.show("编辑器未就绪")
            return
        }
        scope.launch {
            rich.flushAndAwait()
            val hasImg = RE_IMG_TAG.containsMatchIn(rich.html)
            if (rich.text.isBlank() && !hasImg) {
                toast.show("请输入内容")
                return@launch
            }
            // 归一化空 HTML：仅空段落且无图片时清空（web `isEmptyHtml`）
            val finalContent = if (htmlToPlain(rich.html).isBlank() && !hasImg) "" else rich.html
            doPublish(finalContent, extractImagePaths(rich.html))
        }
    }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // ===== 顶栏（van-nav-bar：取消 / 快速·精准 / 发布）=====
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(colors.bgCard)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AppButton(
                        text = "取消",
                        onClick = { nav.popBackStack() },
                        type = AppButtonType.Default,
                        size = AppButtonSize.Small,
                        plain = true,
                        round = true,
                    )
                    // web `.mode-toggle`（快速 / 精准）
                    ModeToggle(mode = mode, onSelect = { switchMode(it) })
                    AppButton(
                        text = "发布",
                        onClick = { showConfirm = true },
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Small,
                        round = true,
                        loading = submitting,
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                // ===== 编辑区（.editor-container{bg-secondary}）=====
                if (mode == "precision") {
                    // ===== 精准模式：离线 WebView + WangEditor（工具条在页面内，见 assets/editor）=====
                    // **不写死高度**，撑满「工具条下方 → 元信息卡片上沿」的全部空高（负责人 2026-09-22 口径：
                    // 输入区域就该到下方"心情"那一行为止）。原先的 `min(400dp, 屏高×0.5)` 是把「工具条 + 文本区」
                    // 合计成 400dp，而 web 那句 `height:400px` 只管文本区 ⇒ 工具条换行吃掉 82dp 后文本区只剩
                    // 286dp，比快速模式（334dp）还矮。改撑满后元信息必然落在屏内，当年"收口免得挤出屏幕"的理由不再成立。
                    // 高度由编辑器自己按「工具条 + 固定 10 行」决定（页面回报给原生），
                    // 不再用 weight(1f) 撑满 —— 2026-10-18 负责人要求书写区固定 10 行、内部滚动。
                    DiaryRichEditorWebView(rich, Modifier.fillMaxWidth())
                } else {
                    // ===== 快速模式：纯文本输入 =====
                    // 高度 = 屏幕 3/8（1/2 再缩 1/4，用户拍板 2026-09-17）；输入框撑满该区域，文案超出后区域内滚动
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(editorHeight)
                            .verticalScroll(rememberScrollState())
                            .background(colors.bgCard),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(16.dp)) { // .diary-input{font-size:16; padding:16}
                            if (content.isEmpty()) {
                                FText("这一刻的想法...", 18f, FontWeight.Normal, colors.textTertiary)
                            }
                            BasicTextField(
                                value = content,
                                onValueChange = { content = it },
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = scaledSp(18f), lineHeight = scaledSp(25.6f)),
                                cursorBrush = SolidColor(colors.primary),
                                // 撑满编辑区（减掉上下 16 内边距）→ 点区域内任意处都能聚焦输入
                                modifier = Modifier.fillMaxWidth().heightIn(min = editorHeight - 32.dp),
                            )
                        }
                    }
                    // ⚠ 已选图片必须放在**固定高度编辑区的外面**：
                    //   输入框 `heightIn(min = 编辑区高度)` 会把整个区域占满，
                    //   图片若摆在它下面 ⇒ 永远在折叠线以下（内层还能滚，但用户根本看不到）
                    //   ⇒ 他 2026-09-20 反馈「选择图片无法下挂到页面里展示」。web 也是编辑区下方的独立块。
                    if (selected.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            selected.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { item ->
                                        Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))) {
                                            VanImage(
                                                src = AppConfig.fullFileUrl(item.filePath),
                                                modifier = Modifier.fillMaxSize(),
                                                fit = ContentScale.Crop,
                                                showError = false,
                                            )
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(18.dp)
                                                    .background(Color(0x80000000), CircleShape)
                                                    .clickable { selected = selected.filterNot { it.id == item.id } },
                                                contentAlignment = Alignment.Center,
                                            ) { VanIcon(name = "cross", size = 10.sp, color = Color.White) }
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.size(80.dp)) }
                                }
                            }
                        }
                    }
                }
                // 元信息三行**紧随编辑区**（web `.meta-cells{margin-top:12px}` 即紧接在 `.editor-container` 之后）；
                // 不再用 Spacer(weight) 顶到底部（用户拍板 2026-09-17）
                // ===== 元信息三行（van-cell-group inset class=app-card；margin-top 12）=====
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgCard),
                ) {
                    MetaCell(
                        icon = "smile-o",
                        title = "今日心情",
                        value = mood,
                        onClick = { showMood = true },
                    )
                    MetaCell(
                        icon = "photograph",
                        title = "添加图片",
                        value = "",
                        onClick = { showPicker = true },
                    )
                    MetaCell(
                        icon = "location-o",
                        title = "当前位置",
                        value = locationDisplay.ifBlank { "点击获取位置" },
                        onClick = { showLocation = true },
                        last = true,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            // ===== 图片选择弹窗（app-popup bottom round，height 70%）=====
            if (showPicker) {
                VanPopup(
                    show = true,
                    onDismissRequest = { showPicker = false },
                    modifier = Modifier.height(maxPopupH),
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        // .picker-header{gap:12; padding-bottom:16; border:1px}
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, colors.border).padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FText("选择图片", 18f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
                            if (localSelected.isNotEmpty()) {
                                FText("已选 ${localSelected.size} 张", 14f, FontWeight.Normal, vanGreen())
                                Spacer(Modifier.width(12.dp))
                            }
                            AppButton(
                                text = "确认",
                                onClick = {
                                    // web `confirmSelection`：快速存进 selectedImages，精准把图片内联进编辑器
                                    val picked = library.filter { localSelected.contains(it.id) }
                                    if (mode == "quick") selected = picked
                                    else rich.insertHtml(precisionImgsHtml(picked))
                                    showPicker = false
                                },
                                type = AppButtonType.Primary,
                                size = AppButtonSize.Small,
                            )
                        }
                        // 上传触发（80×80；plus 24 + 文案 12）
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Column(
                                Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.bgPage)
                                    .clickable(enabled = !uploading) {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (uploading) {
                                    VanLoading(size = 24.sp, color = colors.textTertiary)
                                } else {
                                    VanIcon(name = "plus", size = 24.sp, color = colors.textTertiary)
                                    Spacer(Modifier.height(4.dp))
                                    FText("上传图片", 12f, FontWeight.Normal, colors.textTertiary)
                                }
                            }
                        }
                        // 图片库（4 列 gap 8，选中绿描边）
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                            if (libraryLoading) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    VanLoading()
                                }
                            } else if (library.isEmpty()) {
                                VanEmpty(description = "暂无图片，请上传", image = "search")
                            } else {
                                library.chunked(4).forEach { row ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { item ->
                                            val checked = localSelected.contains(item.id)
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(2.dp, if (checked) vanGreen() else Color.Transparent, RoundedCornerShape(8.dp))
                                                    .clickable { toggleSelect(item) },
                                            ) {
                                                VanImage(
                                                    src = AppConfig.fullFileUrl(item.thumbPath),
                                                    modifier = Modifier.fillMaxSize(),
                                                    fit = ContentScale.Crop,
                                                    showError = false,
                                                )
                                                // van-checkbox（右上角）
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(16.dp)
                                                        .background(if (checked) vanGreen() else colors.bgCard, CircleShape)
                                                        .border(1.dp, if (checked) vanGreen() else colors.border, CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (checked) VanIcon(name = "success", size = 10.sp, color = Color.White)
                                                }
                                            }
                                        }
                                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    VanActionSheet(
        show = showMood,
        onDismissRequest = { showMood = false },
        actions = MOODS.map { VanAction(it) },
        onSelect = { a, _ ->
            mood = a.name
            showMood = false
        },
    )
    VanActionSheet(
        show = showLocation,
        onDismissRequest = { showLocation = false },
        title = "选择位置",
        actions = listOf(
            VanAction("精确定位", "GPS（需要授权）"),
            VanAction("高德定位", "GPS + 高德地址解析"),
            VanAction("大致位置", "IP 定位"),
        ),
        onSelect = { _, i ->
            showLocation = false
            // 顺序与 actions 一致：精确定位 / 高德定位 / 大致位置
            startLocation(listOf("gps", "amap", "ip").getOrElse(i) { "" })
        },
    )
    VanConfirmDialog(
        show = showConfirm,
        title = "确认发布",
        message = "",
        onConfirm = {
            showConfirm = false
            publish()
        },
        onCancel = { showConfirm = false },
        onClose = { showConfirm = false },
    )
}
/**
 * `app-cell`（与「我的」页同款度量）：12/16 内边距、图标 18 主色、标题 14、
 * value 14 text-secondary、右箭头 16 硬编码 `#969799`、底边线**通栏不内缩**。
 */
@Composable
private fun MetaCell(
    icon: String,
    title: String,
    value: String,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VanIcon(name = icon, size = 18.sp, color = colors.primary, modifier = Modifier.padding(end = 10.dp))
        FText(title, 16f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
        if (value.isNotEmpty()) FText(value, 16f, FontWeight.Normal, colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        VanIcon(name = "arrow", size = 16.sp, color = VanGrayFixed)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
/**
 * web `.mode-toggle`：`bg-tertiary` 胶囊（radius 16、padding 2、13px），
 * 选中项 `bg-secondary` + `text-primary` + 500 字重 + `box-shadow 0 1px 3px rgba(0,0,0,.1)`。
 */
@Composable
private fun ModeToggle(mode: String, onSelect: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(Modifier.background(colors.bgThird, RoundedCornerShape(16.dp)).padding(2.dp)) {
        listOf("quick" to "快速", "precision" to "精准").forEach { (key, label) ->
            val active = mode == key
            Box(
                Modifier
                    .then(if (active) Modifier.shadow(1.dp, RoundedCornerShape(14.dp)) else Modifier)
                    .background(if (active) colors.bgCard else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                FText(
                    label,
                    13f,
                    if (active) FontWeight.Medium else FontWeight.Normal,
                    if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
/** web `escapeHtml`（`div.textContent` → `innerHTML`）：只需处理 `& < >`。 */
private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
/** web `/<img\s[^>]*>/i`：精准模式用它判 `hasImg`。 */
private val RE_IMG_TAG = Regex("<img\\s[^>]*>", RegexOption.IGNORE_CASE)
/** web `extractImagesFromHtml`：取每个 `<img>` 的 `src`。 */
private val RE_IMG_SRC = Regex("<img\\s[^>]*?src=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
/**
 * web `extractImagesFromHtml`：把内联图的 `src` 还原成相对路径写进 `images`，
 * 保证后端图片关联不丢（否则精准模式的动态在列表页没有缩略图）。
 */
private fun extractImagePaths(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    val base = AppConfig.publicBaseUrl
    return RE_IMG_SRC.findAll(html).map { it.groupValues[1] }.map { src ->
        if (src.startsWith(base)) src.substring(base.length) else src
    }.toList()
}

/**
 * 从 Compose 的 `LocalContext`（通常被 ContextThemeWrapper 之类包了一层）向上找 Activity。
 * 找不到就返回 null —— 键盘策略退化为「不改」，不抛异常。
 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        val base = (c as? ContextWrapper)?.baseContext
        c = if (base === c) null else base
    }
    return null
}
