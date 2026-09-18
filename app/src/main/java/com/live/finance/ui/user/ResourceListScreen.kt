package com.live.finance.ui.user

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Resource
import com.live.finance.data.repo.DeleteOutcome
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.compressUriToJpeg
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonSize
import com.live.vant.basic.VanButtonType
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanImagePreview
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanCheckbox
import com.live.vant.form.VanCheckboxShape
import com.live.vant.form.VanSearch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** web 的「上传到 X」映射表（**逐字对齐**：post=动态、product=商品、avatar=头像，其余=资源）。 */
private fun typeDisplayName(type: String): String = when (type) {
    "avatar" -> "头像"
    "post" -> "动态"
    "product" -> "商品"
    else -> "资源"
}

/** web `formatName`：超 12 字截断加省略号。 */
private fun formatName(name: String): String =
    if (name.isEmpty()) "未知文件" else if (name.length > 12) name.take(12) + "..." else name

/** web `formatSize`：1024 进制，保留 1 位小数。 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val units = listOf("B", "KB", "MB", "GB")
    val i = minOf((Math.log(bytes.toDouble()) / Math.log(k)).toInt(), units.lastIndex)
    val v = bytes / Math.pow(k, i.toDouble())
    return String.format("%.1f %s", v, units[i])
}

/**
 * 文件资源列表 —— 1:1 复刻 web `src/views/User/resource/ResourceList.vue`。
 *
 * 结构：`.action-bar`（搜索 + 上传 + 条件出现的「删除(N)」）
 *      → 2 列网格（卡片：缩略图 160 高 + 左上勾选 + 右上编辑/使用中 + 名称/大小/说明/标签）
 *      → 空态 / 加载遮罩 / 上传弹层（底部 90%，含说明+标签）/ 编辑弹窗 / 大图预览。
 *
 * 契约：`GET /upload/list|search`、`POST /upload/single`（+ `/upload/:id` 写标签）、
 *      `POST /upload/batch-delete`（**受 pinLockGuard 保护**，走 8303→route-verify 一次性令牌链路）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResourceListScreen(nav: NavHostController, type: String = "other") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.resource
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var undeletable by remember { mutableStateOf(listOf<String>()) }
    var searchKey by remember { mutableStateOf("") }

    // 上传弹层
    var showUpload by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var uploadRemark by remember { mutableStateOf("") }
    var uploadTags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }

    // 编辑弹窗
    var showEdit by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf("") }
    var editRemark by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf(listOf<String>()) }
    var editTagInput by remember { mutableStateOf("") }

    // 预览
    var previewIndex by remember { mutableIntStateOf(-1) }

    // 删除确认
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 列表加载（web onMounted → loadResourceList）
    LaunchedEffect(reloadKey, type) {
        loading = true
        rows = when (val r = repo.list(busType = type, limit = 100)) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> emptyList()
        }
        loading = false
    }

    // 多选相册（web van-uploader :max-count=10 multiple）
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) picked = (picked + uris).take(10)
    }

    fun doSearch() {
        // web handleSearch：关键词限 10 字符
        if (searchKey.length > 10) searchKey = searchKey.take(10)
        val key = searchKey.trim()
        scope.launch {
            loading = true
            rows = if (key.isEmpty()) {
                (repo.list(busType = type, limit = 100) as? ApiResult.Ok)?.data ?: emptyList()
            } else {
                (repo.search(type = type, key = key, limit = 50) as? ApiResult.Ok)?.data ?: emptyList()
            }
            loading = false
        }
    }

    fun submitUpload() {
        if (picked.isEmpty()) return
        uploading = true
        val remark = uploadRemark.trim()
        val tags = uploadTags.toList()
        scope.launch {
            var failed = 0
            for (uri in picked) {
                val file = withContext(Dispatchers.IO) { compressUriToJpeg(ctx, uri, maxSide = 1920) }
                if (file == null) { failed++; continue }
                val r = repo.upload(file.bytes, file.fileName, file.mime, type, remark, tags)
                if (r !is ApiResult.Ok) failed++
            }
            uploading = false
            if (failed == 0) {
                toast.show("上传成功")
                uploadRemark = ""; uploadTags = emptyList(); tagInput = ""; picked = emptyList()
                showUpload = false
                reloadKey++
            } else {
                toast.show("上传失败")
            }
        }
    }

    fun submitEdit() {
        scope.launch {
            when (val r = repo.update(editId, editRemark, editTags)) {
                is ApiResult.Ok -> { toast.show("更新成功"); showEdit = false; reloadKey++ }
                is ApiResult.Fail -> toast.show(r.message.ifBlank { "更新失败" })
                else -> toast.show("更新失败")
            }
        }
    }

    fun doBatchDelete() {
        scope.launch {
            when (val outcome = repo.batchDelete(selected)) {
                is DeleteOutcome.Ok -> {
                    toast.show("批量删除成功")
                    selected = emptyList()
                    // web：PIN 验证后重发可能尚未完成，延迟 600ms 再刷新
                    kotlinx.coroutines.delay(600)
                    reloadKey++
                }
                is DeleteOutcome.Referenced -> {
                    undeletable = outcome.ids
                    selected = selected.filter { !outcome.ids.contains(it) }
                    toast.show(outcome.message.ifBlank { "部分文件无法删除" })
                    reloadKey++
                }
                is DeleteOutcome.Fail -> if (outcome.message.isNotBlank()) toast.show(outcome.message)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ===== .action-bar（吸顶）=====
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                VanSearch(
                    value = searchKey,
                    onValueChange = { searchKey = it },
                    placeholder = "搜索图片说明/标签",
                    shape = "round",
                    background = Color.Transparent, // van-search background="transparent"
                    modifier = Modifier.weight(1f),
                    onSearch = { doSearch() },
                    onClear = { searchKey = ""; reloadKey++ },
                )
                VanButton(
                    text = "上传",
                    icon = "plus",
                    type = VanButtonType.Primary,
                    size = VanButtonSize.Small,
                    onClick = { showUpload = true },
                )
                if (selected.isNotEmpty()) {
                    VanButton(
                        text = "删除 (${selected.size})",
                        type = VanButtonType.Danger,
                        size = VanButtonSize.Small,
                        plain = true,
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }

            // ===== 2 列网格 =====
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows, key = { it.id }) { item ->
                    ResourceCard(
                        item = item,
                        selected = selected.contains(item.id),
                        undeletable = undeletable.contains(item.id),
                        onToggle = {
                            selected = if (selected.contains(item.id)) selected - item.id else selected + item.id
                        },
                        onPreview = { previewIndex = rows.indexOf(item) },
                        onEdit = {
                            editId = item.id
                            editRemark = item.remark
                            editTags = item.tags
                            editTagInput = ""
                            showEdit = true
                        },
                    )
                }
            }
        }

        // 空态
        if (!loading && rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                FText("暂无资源", 14f, FontWeight.Normal, colors.textTertiary)
            }
        }
        // 加载遮罩（web van-overlay + 加载中）
        if (loading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(Color(0x40000000)),
            ) {
                FText("加载中...", 14f, FontWeight.Normal, Color.White)
            }
        }
    }

    // ===== 上传弹层（底部 90%）=====
    VanPopup(
        show = showUpload,
        onDismissRequest = { showUpload = false },
        position = VanPopupPosition.Bottom,
        round = true,
        modifier = Modifier.fillMaxHeight(0.9f),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // .popup-header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            ) {
                FText("上传到 ${typeDisplayName(type)}", 15f, FontWeight.Bold, colors.textPrimary)
                VanIcon(name = "cross", size = 18.sp, color = colors.textTertiary, onClick = { showUpload = false })
            }

            // .upload-trigger（虚线框）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, colors.border, RoundedCornerShape(8.dp))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VanIcon(name = "photograph", size = 40.sp, color = Color(0xFF969799))
                    Spacer(Modifier.height(6.dp))
                    FText("点击或拖拽上传", 13f, FontWeight.Normal, colors.textTertiary)
                }
            }

            // 已选图片（web van-uploader 的预览列表）
            if (picked.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    picked.forEach { uri ->
                        Box(Modifier.size(72.dp)) {
                            VanImage(
                                src = uri,
                                width = 72.dp,
                                height = 72.dp,
                                radius = 8.dp,
                                showError = false,
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xB3000000))
                                    .clickable { picked = picked - uri },
                            ) {
                                VanIcon(name = "cross", size = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 图片说明
            Box(Modifier.padding(top = 12.dp)) {
                AppFieldBox(
                    value = uploadRemark,
                    onValueChange = { uploadRemark = it },
                    label = "图片说明",
                    placeholder = "可选，描述这张图片",
                    maxlength = 30,
                    showWordLimit = true,
                    clearable = true,
                )
            }
            // 标签
            Box(Modifier.padding(top = 12.dp)) {
                AppFieldBox(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = "标签",
                    placeholder = "输入标签后回车添加",
                    maxlength = 10,
                    clearable = true,
                    rightSlot = {
                        VanButton(
                            text = "添加",
                            type = VanButtonType.Primary,
                            size = VanButtonSize.Small,
                            onClick = {
                                val t = tagInput.trim()
                                if (t.isNotEmpty() && !uploadTags.contains(t)) {
                                    uploadTags = uploadTags + t
                                    tagInput = ""
                                }
                            },
                        )
                    },
                )
            }
            if (uploadTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    uploadTags.forEach { tag ->
                        VanTag(
                            text = tag,
                            size = VanTagSize.Medium,
                            closeable = true,
                            onClose = { uploadTags = uploadTags - tag },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FText("支持多选，单文件最大 10MB", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(12.dp))
            VanButton(
                text = "提交上传",
                type = VanButtonType.Primary,
                block = true,
                loading = uploading,
                disabled = picked.isEmpty() || uploading,
                onClick = { submitUpload() },
            )
        }
    }

    // ===== 编辑弹窗（web app-dialog：图片说明 + 标签）=====
    AppDialogShell(
        show = showEdit,
        title = "编辑图片信息",
        onOverlay = { showEdit = false },
        onCancel = { showEdit = false },
        onConfirm = { submitEdit() },
    ) {
        AppFieldBox(
            value = editRemark,
            onValueChange = { editRemark = it },
            label = "图片说明",
            placeholder = "描述这张图片",
            maxlength = 30,
            showWordLimit = true,
            clearable = true,
        )
        Spacer(Modifier.height(6.dp))
        AppFieldBox(
            value = editTagInput,
            onValueChange = { editTagInput = it },
            label = "标签",
            placeholder = "输入标签后点击添加",
            maxlength = 10,
            rightSlot = {
                VanButton(
                    text = "添加",
                    type = VanButtonType.Primary,
                    size = VanButtonSize.Small,
                    onClick = {
                        val t = editTagInput.trim()
                        if (t.isNotEmpty() && !editTags.contains(t)) {
                            editTags = editTags + t
                            editTagInput = ""
                        }
                    },
                )
            },
        )
        if (editTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                editTags.forEach { tag ->
                    VanTag(
                        text = tag,
                        size = VanTagSize.Medium,
                        closeable = true,
                        onClose = { editTags = editTags - tag },
                    )
                }
            }
        }
    }

    // ===== 批量删除确认（web showConfirmDialog 标题「警告」）=====
    VanConfirmDialog(
        show = showDeleteConfirm,
        title = "警告",
        message = "确定删除这 ${selected.size} 项吗？",
        onConfirm = { showDeleteConfirm = false; doBatchDelete() },
        onClose = { showDeleteConfirm = false },
    )

    // ===== 大图预览 =====
    if (previewIndex >= 0) {
        VanImagePreview(
            show = true,
            images = rows.map { AppConfig.fullFileUrl(it.previewPath) },
            startPosition = previewIndex,
            closeable = true,
            onChange = { previewIndex = it },
            onClose = { previewIndex = -1 },
        )
    }
}

/** 单张资源卡片（web `.resource-item`）。 */
@Composable
private fun ResourceCard(
    item: Resource,
    selected: Boolean,
    undeletable: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard),
    ) {
        Column {
            VanImage(
                src = AppConfig.fullFileUrl(item.thumbPath),
                height = 160.dp, // .resource-image{ height:160px }
                modifier = Modifier.fillMaxWidth(),
                showError = false,
                onClick = onPreview,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                FText(formatName(item.fileName), 13f, FontWeight.Normal, colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                FText(formatSize(item.fileSize), 11f, FontWeight.Normal, colors.textTertiary)
                if (item.remark.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    // `.resource-remark{ overflow:hidden; text-overflow:ellipsis; white-space:nowrap }`
                    androidx.compose.foundation.text.BasicText(
                        item.remark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = colors.textSecondary, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (item.tags.isNotEmpty()) {
                    FlowRowWrap {
                        item.tags.forEach { tag ->
                            VanTag(text = tag, type = VanTagType.Primary, size = VanTagSize.Small)
                        }
                    }
                }
            }
        }

        // 左上勾选（白 80% 底 + 圆角 4 + padding 2）
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCCFFFFFF))
                .padding(2.dp),
        ) {
            VanCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                shape = VanCheckboxShape.Square,
            )
        }

        // 右上：选中且可删 → 编辑按钮；不可删 → 「使用中」标签
        when {
            undeletable -> Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 8.dp)) {
                VanTag(text = "使用中", type = VanTagType.Warning, size = VanTagSize.Small)
            }
            selected -> Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 8.dp)) {
                VanButton(
                    text = "",
                    icon = "edit",
                    type = VanButtonType.Primary,
                    size = VanButtonSize.Mini,
                    onClick = onEdit,
                )
            }
        }
    }
}

/** 标签换行容器（FlowRow + 4dp 间距，对应 `.resource-tags{flex-wrap:wrap;gap:4px;margin-top:4px}`）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowWrap(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) { content() }
}

/**
 * `app-field`（label 在上、输入在下、通栏底线）—— web 上传/编辑表单用的自写输入：
 * `.app-field{padding:10px 16px; border-bottom:1px solid border}`、label 13 textSecondary(mb 6)、
 * 输入 14/行高 1.5、`.app-field__count` 计数右对齐 12 tertiary。
 */
@Composable
private fun AppFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    maxlength: Int,
    showWordLimit: Boolean = false,
    clearable: Boolean = false,
    rightSlot: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 13f, FontWeight.Normal, colors.textSecondary, Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    FText(placeholder, 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(vertical = 2.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.take(maxlength)) },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
            if (clearable && value.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                VanIcon(name = "clear", size = 16.sp, color = colors.textTertiary, onClick = { onValueChange("") })
            }
            if (rightSlot != null) {
                Spacer(Modifier.width(8.dp))
                rightSlot()
            }
        }
        if (showWordLimit) {
            FText(
                "${value.length}/$maxlength", 12f, FontWeight.Normal, colors.textTertiary,
                Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}
