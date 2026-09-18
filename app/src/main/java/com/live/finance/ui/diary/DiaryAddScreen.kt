package com.live.finance.ui.diary

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Resource
import com.live.finance.theme.LocalAppColors
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

/** web `moodActions` 六项（中文文案，不是 emoji）。 */
private val MOODS = listOf("开心", "平静", "难过", "累", "兴奋", "郁闷")

/** web `.image-item.checked{border-color: var(--van-green)}`。 */
private val VAN_GREEN = Color(0xFF07C160)

/** 上传库业务类型（web `BusType.POST`）。 */
private const val BUS_TYPE_POST = "post"

/**
 * 发布动态 —— 复刻 web `views/Diary/Add.vue` 的**普通（快速）模式**。
 *
 * 结构：顶栏（取消 / 发布）→ 编辑区（高度 = 屏幕 3/8）→ 元信息三行（今日心情 / 添加图片 / 当前位置，**紧随编辑区**）→
 *      心情·位置 ActionSheet → 图片选择弹窗（库内多选 + 上传，最多 9 张）。
 *
 * ⚠ **拍板**：
 *  1. （2026-09-15）Android 端**不做富文本**（web 精准模式用 WangEditor，原生无等价且不引库/不嵌 WebView）。
 *  2. （2026-09-15）**去掉「快速 / 精准」切换**（`van-nav-bar` 中间的 `.mode-toggle` 不再渲染），
 *     `content` 一律按纯文本提交，图片上限取 web 快速模式的 **9**。
 *  3. （2026-09-17）**编辑区高度 = 屏幕 1/2 再缩 1/4 = 屏幕的 3/8**（原先被 `weight(1f)` 撑满剩余空间，观感太高）；
 *     输入框撑满该区域（点区域内任意处即可聚焦），文案超出后在区域内滚动。
 *  4. （2026-09-17）**元信息三行紧随编辑区**（= web `.meta-cells{margin-top:12px}` 的位置），不再顶到底部。
 *  因此**不要**在 UI 里加"降级说明"之类 web 没有的文案。
 *
 * ⚠ **定位**：web 走 `utils/geo`（浏览器 GPS / 高德 / IP）；原生未接入定位能力，
 * 点击三项会提示「暂未接入」，位置不写入（避免伪造坐标）。
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

    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("开心") }
    var locationName by remember { mutableStateOf("") }

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

    /** 图片上限（去掉模式切换后取 web 快速模式的 `MAX_SELECT_QUICK = 9`）。 */
    val maxSelect = 9

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
            if (failed > 0) toast.show("部分图片上传失败") else toast.show("上传成功")
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

    fun publish() {
        if (content.isBlank()) {
            toast.show("请输入内容")
            return
        }
        submitting = true
        scope.launch {
            val r = graph.moment.create(
                content = content,
                images = selected.map { it.filePath },
                mood = mood,
                locationName = locationName,
            )
            submitting = false
            when (r) {
                is ApiResult.Ok -> {
                    toast.show("成功")
                    nav.popBackStack()
                }
                else -> toast.show("失败")
            }
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
                    // 中间原为 web 的 `.mode-toggle`（快速/精准）—— 已拍板去掉模式切换，故不再渲染
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
                            FText("这一刻的想法...", 16f, FontWeight.Normal, colors.textTertiary)
                        }
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp, lineHeight = 25.6.sp),
                            cursorBrush = SolidColor(colors.primary),
                            // 撑满编辑区（减掉上下 16 内边距）→ 点区域内任意处都能聚焦输入
                            modifier = Modifier.fillMaxWidth().heightIn(min = editorHeight - 32.dp),
                        )
                    }
                    // 已选图片（.selected-preview{gap:8; padding:12 16}）
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
                        value = locationName.ifBlank { "点击获取位置" },
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
                            FText("选择图片", 16f, FontWeight.SemiBold, colors.textPrimary, Modifier.weight(1f))
                            if (localSelected.isNotEmpty()) {
                                FText("已选 ${localSelected.size} 张", 13f, FontWeight.Normal, VAN_GREEN)
                                Spacer(Modifier.width(12.dp))
                            }
                            AppButton(
                                text = "确认",
                                onClick = {
                                    selected = library.filter { localSelected.contains(it.id) }
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
                                                    .border(2.dp, if (checked) VAN_GREEN else Color.Transparent, RoundedCornerShape(8.dp))
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
                                                        .background(if (checked) VAN_GREEN else colors.bgCard, CircleShape)
                                                        .border(1.dp, if (checked) VAN_GREEN else colors.border, CircleShape),
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
        onSelect = { _, _ ->
            showLocation = false
            // web 走 utils/geo（浏览器 GPS / 高德 / IP）；原生未接入定位能力，这里如实提示、不写假坐标
            toast.show("原生暂未接入定位，位置将不写入")
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
        FText(title, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
        if (value.isNotEmpty()) FText(value, 14f, FontWeight.Normal, colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        VanIcon(name = "arrow", size = 16.sp, color = Color(0xFF969799))
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
