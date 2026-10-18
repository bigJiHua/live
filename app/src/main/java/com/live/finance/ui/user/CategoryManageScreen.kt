package com.live.finance.ui.user

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Category
import com.live.finance.data.model.Resource
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.finance.theme.vanBlue
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.compressUriToJpeg
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.common.rememberBankIconRequest
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanOverlay
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanTabItem
import com.live.vant.nav.VanTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/** web `van-tabs` 的四个页签（`<van-tab title="支出" name="expense" />` …）。 */
private val CATE_TABS = listOf(
    VanTabItem("expense", "支出"),
    VanTabItem("income", "收入"),
    VanTabItem("asset", "资产"),
    VanTabItem("fixed", "固定资产"),
)

/** 弹窗底部「取消 / 保存」的最小宽度 —— 用户要求（原来自然宽度太窄）。 */
private val DIALOG_BTN_MIN_W = 96.dp

/**
 * 分类管理 —— 一比一复刻 web `views/User/category/CategoryManage.vue`。
 *
 * 同一屏兼作「银行分类」（web `views/User/category/BankCategoryManage.vue`，由 [initialType] = `bank` 进入）：
 * 差别只有四处 —— ① 银行页**没有页签**；② 文案「分类 → 银行」；③ 卡片左图标走 BankIcon（有图用图、无图取银行名首字）；
 * ④ 图标库 busType = `bank`（分类页是 `other`）。
 *
 * 关键度量（照抄 web CSS，别用 Vant 默认值）：
 *  - `.category-content{padding:16}`、`.category-grid{repeat(2,1fr); gap:12}`、`.action-bar{margin-bottom:16}`
 *  - `.category-card{bg-secondary; radius:12; padding:14; gap:12; shadow 0 2px 8px rgba(0,0,0,.04)}`
 *  - `.card-actions{top:10; right:10; gap:10; color:text-tertiary}`（两个 16px 图标）
 *  - `.category-name{15/600}`、`.category-remark{12 tertiary; margin-top:2}`、`.count{13 tertiary}`
 *  - 弹窗 `app-popup position=bottom round`（圆角 16）→ `.dialog-container{padding:20}`、标题 18/600 居中、
 *    `.dialog-form{bg-primary; radius:12; padding:8 0}`、`.dialog-actions{gap:12; margin-top:24}`
 *  - ⚠ `.dialog-actions .van-button{flex:1}` **对 `app-button` 不生效**（app-button 根类名是 `.app-btn`，
 *    不是 `.van-button`）→ web 两个按钮是**自然宽度、左对齐**。
 *    **本轮按用户要求偏离 web**：改为**右下角**排布 + 各给 96dp 最小宽度（`DIALOG_BTN_MIN_W`）。
 *  - 用户要求：① `.icon-select-wrap` 的 1px 边框去掉；② `app-field` 加「输入区下划线」——
 *    原实现那条 1px 线画在 `bgCard` **之外**、落在 `bgPage` 灰底上（`#EBEDF0` vs `#F7F8FA`）
 *    ⇒ 实际看不见，现已挪进白卡内部、画在输入行正下方。
 *  - 图标选择弹窗：`height:70%`、`.icon-grid{repeat(4,1fr); gap:16}`、图标 48×48、选中描边 `--van-blue #1989fa`（Vant 调色板，非主题色）、
 *    选中打勾在右上角（`background:theme-primary` 圆形 + 10px 白勾）。
 */
@Composable
fun CategoryManageScreen(nav: NavHostController, initialType: String = "expense") {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    val isBank = initialType == "bank"
    // 上传附件业务类型：分类图标=other、银行图标=bank（web BusType.OTHER / BusType.BANK）
    val busType = if (isBank) "bank" else "other"
    val maxFormH = LocalConfiguration.current.screenHeightDp.dp * 0.7f

    // web：allCategories 按 type 分桶缓存，切页签只在「该桶为空」时拉取
    val cache = remember { mutableStateMapOf<String, List<Category>>() }
    var type by remember { mutableStateOf(initialType) }
    var loading by remember { mutableStateOf(false) }
    val list = cache[type].orEmpty()

    // 新增/编辑弹窗
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var name by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var iconUrl by remember { mutableStateOf("") }

    // 删除确认
    var deleting by remember { mutableStateOf<Category?>(null) }

    // 图标选择弹窗
    var showPicker by remember { mutableStateOf(false) }
    var icons by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var iconLoading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    fun fetch(t: String) {
        loading = true
        scope.launch {
            when (val r = graph.category.list(t)) {
                is ApiResult.Ok -> cache[t] = r.data.orEmpty()
                // web `BankCategoryManage.loadCategories` → 「银行分类加载失败」；普通分类页 → 「加载失败」
                else -> toast.show(if (t == "bank") "银行分类加载失败" else "加载失败")
            }
            loading = false
        }
    }

    fun loadIcons() {
        iconLoading = true
        scope.launch {
            icons = when (val r = graph.resource.list(busType, "", 100, 0)) {
                is ApiResult.Ok -> r.data.orEmpty()
                else -> emptyList()
            }
            iconLoading = false
        }
    }

    LaunchedEffect(type) { if (cache[type] == null) fetch(type) }
    // web `watch(showIconPicker, val => val && loadIconList())`：每次打开都重拉
    LaunchedEffect(showPicker) { if (showPicker) loadIcons() }

    // 上传图标（web van-uploader :before-read 校验图片类型 + 2MB 上限）
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null || uploading) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val picked = withContext(Dispatchers.IO) { compressUriToJpeg(ctx, uri, maxSide = 1024) }
            if (picked == null) {
                toast.show("只能上传图片文件")
                uploading = false
                return@launch
            }
            if (picked.bytes.size / 1024 / 1024 >= 2) {
                toast.show("图片大小不能超过 2MB", VanToastType.Fail)
                uploading = false
                return@launch
            }
            when (val r = graph.client.upload(picked.bytes, picked.fileName, picked.mime, busType, "", "")) {
                is ApiResult.Ok -> {
                    val path = r.data.orEmpty()
                    if (path.isBlank()) {
                        toast.show("上传失败", VanToastType.Fail)
                    } else {
                        iconUrl = path // web：新上传的图标自动选中
                        toast.show("上传成功", VanToastType.Success)
                        loadIcons() // web 是把新行 unshift 进列表；原生重拉等价（且字段更全）
                    }
                }
                else -> toast.showFail(r, "上传失败")
            }
            uploading = false
        }
    }

    fun closeForm() {
        showForm = false
        editing = null
        name = ""
        remark = ""
        iconUrl = ""
    }

    fun save() {
        if (name.isBlank()) {
            toast.show(if (isBank) "请输入银行名称" else "请输入名称")
            return
        }
        val cur = editing
        val n = name.trim()
        val rm = remark.trim()
        val ic = iconUrl.trim()
        scope.launch {
            // 两条分支各自 when（create 回传 Category?、update 回 Unit，混在一个 when 里会被推成 ApiResult<out Any?>）
            if (cur == null) {
                when (val r = graph.category.create(type, n, rm, ic)) {
                    is ApiResult.Ok -> {
                        val row = r.data ?: Category(
                            id = "tmp_${System.currentTimeMillis()}",
                            name = n, type = type, iconUrl = ic, remark = rm,
                        )
                        cache[type] = cache[type].orEmpty() + row
                        toast.show("创建成功", VanToastType.Success)
                        closeForm()
                    }
                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                    is ApiResult.Fail -> toast.show("创建失败", VanToastType.Fail)
                    else -> toast.showFail(r, "创建失败")
                }
            } else {
                when (val r = graph.category.update(cur.id, n, rm, ic)) {
                    is ApiResult.Ok -> {
                        // web 命中本地补丁，不重拉
                        cache[type] = cache[type].orEmpty().map {
                            if (it.id == cur.id) it.copy(name = n, remark = rm, iconUrl = ic) else it
                        }
                        toast.show("更新成功", VanToastType.Success)
                        closeForm()
                    }
                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                    is ApiResult.Fail -> toast.show("更新失败", VanToastType.Fail)
                    else -> toast.showFail(r, "更新失败")
                }
            }
        }
    }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            if (isBank) {
                // 银行分类页无页签（web BankCategoryManage 直接是 category-content）
                CategoryBody(
                    list = list,
                    isBank = true,
                    onAdd = { editing = null; name = ""; remark = ""; iconUrl = ""; showForm = true },
                    onEdit = { c ->
                        editing = c
                        name = c.name
                        remark = c.remark
                        iconUrl = c.iconUrl
                        showForm = true
                    },
                    onDelete = { deleting = it },
                )
            } else {
                // 页签常驻顶部 = web `van-tabs sticky`；内容区在其内部滚动
                VanTabs(
                    active = type,
                    onActiveChange = { type = it },
                    tabs = CATE_TABS,
                    content = {
                        CategoryBody(
                            list = list,
                            isBank = false,
                            onAdd = { editing = null; name = ""; remark = ""; iconUrl = ""; showForm = true },
                            onEdit = { c ->
                                editing = c
                                name = c.name
                                remark = c.remark
                                iconUrl = c.iconUrl
                                showForm = true
                            },
                            onDelete = { deleting = it },
                        )
                    },
                )
            }

            // van-overlay :show=loading → 全屏遮罩 + 居中 loading
            VanOverlay(show = loading)
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { VanLoading() }
            }
        }
    }

    // ===== 新增/编辑弹窗（app-popup position=bottom round，max-height 70vh）=====
    VanPopup(
        show = showForm,
        onDismissRequest = { closeForm() },
        modifier = Modifier.heightIn(max = maxFormH),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) { // .dialog-container{padding:20}
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                FText(
                    if (editing != null) (if (isBank) "编辑银行" else "编辑分类") else (if (isBank) "新增银行" else "新增分类"),
                    18f, FontWeight.SemiBold, colors.textPrimary,
                )
            }
            // .dialog-form{bg-primary; radius:12; padding:8 0}
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgPage)
                    .padding(vertical = 8.dp),
            ) {
                AppFieldBox(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = if (isBank) "银行名称" else "名称",
                    placeholder = if (isBank) "请输入银行名称" else "请输入分类名称",
                    maxlength = 20,
                    showWordLimit = true,
                )
                // .icon-select-wrap{padding:12 16; bg-secondary}（web 还有 border:1px border —— 按用户要求去掉）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bgCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText("选择图标", 16f, FontWeight.Normal, colors.textSecondary)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.clickable { showPicker = true }) {
                        if (iconUrl.isNotBlank()) {
                            VanImage(
                                src = AppConfig.fullFileUrl(iconUrl),
                                width = 48.dp,
                                height = 48.dp,
                                fit = ContentScale.Crop,
                                round = true,
                                showError = false,
                            )
                        } else {
                            Box(
                                Modifier.size(48.dp).background(colors.bgPage, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { VanIcon(name = "plus", size = 24.sp, color = colors.textTertiary) }
                        }
                    }
                    if (iconUrl.isNotBlank()) {
                        FText("点击更换图标", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(start = 8.dp))
                    }
                }
                AppFieldBox(
                    value = remark,
                    onValueChange = { remark = it.take(50) },
                    label = "备注",
                    placeholder = "选填",
                    maxlength = 50,
                    showWordLimit = true,
                )
            }
            // .dialog-actions{gap:12; margin-top:24}。用户要求：两个按钮**靠右下角**排布，
            // 且原来「自然宽度」太窄 ⇒ 各给一个最小宽度（Large 高 50 + 96dp 宽，够两只手点）。
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                AppButton(
                    text = "取消",
                    onClick = { closeForm() },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Large,
                    plain = true,
                    round = true,
                    modifier = Modifier.widthIn(min = DIALOG_BTN_MIN_W),
                )
                AppButton(
                    text = "保存",
                    onClick = { save() },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Large,
                    round = true,
                    modifier = Modifier.widthIn(min = DIALOG_BTN_MIN_W),
                )
            }
        }
    }

    // ===== 图标选择弹窗（app-popup position=bottom round，height 70%）=====
    VanPopup(
        show = showPicker,
        onDismissRequest = { showPicker = false },
        modifier = Modifier.fillMaxHeight(0.7f),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { // .icon-picker{padding:16}
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText(if (isBank) "选择银行图标" else "选择分类图标", 18f, FontWeight.SemiBold, colors.textPrimary)
                VanIcon(name = "cross", size = 16.sp, color = colors.textPrimary, onClick = { showPicker = false })
            }
            // .upload-section{padding-bottom:16; border:1px border; margin-bottom:16}
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border)
                    .padding(bottom = 16.dp),
            ) {
                // .upload-trigger{60×60; bg-primary; radius:8; gap:4; 11px tertiary}
                Column(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgPage)
                        .clickable(enabled = !uploading) {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (uploading) {
                        VanLoading(size = 20.sp, color = colors.textTertiary)
                    } else {
                        VanIcon(name = "plus", size = 20.sp, color = colors.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FText("上传图标", 12f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }
            if (iconLoading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    VanLoading(size = 24.sp)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                ) {
                    icons.chunked(4).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            row.forEach { item ->
                                val path = item.filePath.ifBlank { item.thumbnail }
                                IconCell(
                                    thumb = AppConfig.fullFileUrl(item.thumbPath),
                                    active = iconUrl.isNotBlank() && iconUrl == path,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        iconUrl = path
                                        showPicker = false
                                    },
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (icons.isEmpty()) VanEmpty(description = "暂无图标，请上传")
                }
            }
        }
    }

    VanConfirmDialog(
        show = deleting != null,
        title = "确认删除",
        message = "删除「${deleting?.name.orEmpty()}」？",
        onConfirm = {
            val target = deleting ?: return@VanConfirmDialog
            deleting = null
            scope.launch {
                when (val r = graph.category.delete(target.id)) {
                    is ApiResult.Ok -> {
                        cache[type] = cache[type].orEmpty().filter { it.id != target.id }
                        toast.show("已删除")
                    }
                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                    is ApiResult.Fail -> toast.show(r.message, VanToastType.Fail)
                    else -> toast.showFail(r, "删除失败")
                }
            }
        },
        onCancel = { deleting = null },
        onClose = { deleting = null },
    )
}

// ───────────────────────────── 列表区 ─────────────────────────────

/** `.category-content{padding:16}` + action-bar + 双列宫格 / 空态。 */
@Composable
private fun CategoryBody(
    list: List<Category>,
    isBank: Boolean,
    onAdd: () -> Unit,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FText(
                if (isBank) "共 ${list.size} 个银行分类" else "共 ${list.size} 个分类",
                14f, FontWeight.Normal, colors.textTertiary,
            )
            AppButton(
                text = "新增",
                onClick = onAdd,
                type = AppButtonType.Primary,
                size = AppButtonSize.Small,
                icon = "plus",
            )
        }
        if (list.isNotEmpty()) {
            list.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { c ->
                        CategoryCard(
                            item = c,
                            isBank = isBank,
                            modifier = Modifier.weight(1f),
                            onEdit = { onEdit(c) },
                            onDelete = { onDelete(c) },
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            // ⚠ web 银行页的 `<template #icon>` 是**无效插槽**（Vant Empty 只声明 image/description/default），
            //   那个 card 图标实际不会渲染 → 两页空态一致，只差文案。
            VanEmpty(description = if (isBank) "暂无银行分类，点击上方按钮添加" else "暂无分类，点击上方按钮添加")
        }
    }
}

/** `.category-card`：白卡 + 右上编辑/删除 + 左图标 + 名称/备注。 */
@Composable
private fun CategoryCard(
    item: Category,
    isBank: Boolean,
    modifier: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .cssShadow(12.dp, WebShadow(0f, 2f, 8f, Color.Black.copy(alpha = 0.04f)))
            .clip(shape)
            .background(colors.bgCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // .card-left / .card-icon（36×36）
            if (isBank) {
                BankIconBox(iconUrl = item.iconUrl, name = item.name)
            } else if (item.iconUrl.isNotBlank()) {
                VanImage(
                    src = AppConfig.fullFileUrl(item.iconUrl),
                    width = 36.dp,
                    height = 36.dp,
                    fit = ContentScale.Crop,
                    showError = false,
                )
            } else {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { DefaultCategoryIcon() }
            }
            Column(Modifier.weight(1f)) {
                FText(item.name, 16f, FontWeight.SemiBold, colors.textPrimary)
                if (item.remark.isNotBlank()) {
                    FText(item.remark, 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 2.dp))
                }
            }
        }
        // .card-actions{top:10; right:10; gap:10}
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VanIcon(name = "edit", size = 16.sp, color = colors.textTertiary, onClick = onEdit)
            VanIcon(name = "delete", size = 16.sp, color = colors.textTertiary, onClick = onDelete)
        }
    }
}

/**
 * web `components/BankIcon.vue`：有 logo 用 logo（object-fit: contain），否则「毛玻璃圆角块 + 银行名首字」。
 * 首字规则：中国银行→中；其它「中国xx银行」→ 去前缀取首字；其余取首字；空名不显示。
 */
@Composable
private fun BankIconBox(iconUrl: String, name: String, size: Dp = 36.dp) {
    val colors = LocalAppColors.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (iconUrl.isNotBlank()) {
            val url = AppConfig.fullFileUrl(iconUrl)
            VanImage(
                src = url,
                // 与启动预热同一把缓存键（见 BankIconCache），否则这里拉的是另一条缓存条目
                imageRequest = rememberBankIconRequest(url),
                modifier = Modifier.fillMaxSize(),
                fit = ContentScale.Fit,
                showError = false,
            )
            return@Box
        }
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0x297F7F7F)) // rgba(127,127,127,.16)
                .border(1.dp, Color(0x337F7F7F), shape), // box-shadow: inset 0 0 0 1px rgba(127,127,127,.2)
            contentAlignment = Alignment.Center,
        ) {
            val initial = bankInitial(name)
            if (initial.isNotEmpty()) {
                // font-size ≈ max(round(size*.45), 8)
                FText(initial, (size.value * 0.45f).coerceAtLeast(8f), FontWeight.Bold, colors.textSecondary)
            }
        }
    }
}

private fun bankInitial(name: String): String {
    val n = name.trim()
    return when {
        n.isEmpty() -> ""
        n.startsWith("中国银行") -> "中"
        n.startsWith("中国") -> n.removePrefix("中国").take(1)
        else -> n.take(1)
    }
}

/**
 * 默认分类图标 = web `@/assets/icon/cate.svg`（22×22）：描边式 2×2 宫格（3 个圆角方 + 右下 3 条递减胶囊线）。
 * ⚠ 该 svg 以 `<img>` 引入且 path **没有 fill 属性** → 实际渲染是**纯黑**（不随主题变，深色下也黑）。
 */
@Composable
private fun DefaultCategoryIcon(size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 1024f
        val sw = 74f * s
        val black = Color.Black
        fun square(x: Float, y: Float, w: Float, h: Float) {
            drawRoundRect(
                color = black,
                topLeft = Offset(x * s + sw / 2f, y * s + sw / 2f),
                size = Size(w * s - sw, h * s - sw),
                cornerRadius = CornerRadius(74f * s),
                style = Stroke(width = sw),
            )
        }
        square(0f, 0f, 465.7f, 465.7f)
        square(557.8f, 0f, 326.2f, 465.7f)
        square(0f, 557.8f, 465.7f, 465.7f)
        fun line(y: Float, w: Float) {
            drawRoundRect(
                color = black,
                topLeft = Offset(590.5f * s, y * s),
                size = Size(w * s, 74f * s),
                cornerRadius = CornerRadius(37f * s),
            )
        }
        line(590.5f, 362.9f)
        line(753.3f, 270.3f)
        line(916.0f, 177.6f)
    }
}

/**
 * 图标选择弹窗里的一格（web `.icon-item`）：格子宽 = 宫格列宽（1fr）、高 = 48 + 2×2 描边，
 * 48×48 缩略图贴在**左上角**（web 是块级排布，没有居中）；选中态描边 `--van-blue #1989fa`
 * （Vant 调色板色，**不是**主题色）+ 右上角 2px 处的主题色圆勾（10px 白 success）。
 */
@Composable
private fun IconCell(thumb: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .border(2.dp, if (active) vanBlue() else Color.Transparent, shape)
            .clickable(onClick = onClick),
    ) {
        VanImage(
            src = thumb,
            modifier = Modifier.align(Alignment.TopStart),
            width = 48.dp,
            height = 48.dp,
            fit = ContentScale.Crop,
            showError = false,
        )
        if (active) {
            // .check-icon{top:2; right:2; background:theme-primary; border-radius:50%; padding:2; color:#fff; font-size:10px}
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(14.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) { VanIcon(name = "success", size = 10.sp, color = Color.White) }
        }
    }
}

// ───────────────────────────── 表单件 ─────────────────────────────

/**
 * `app-field`（web `components/base/AppField.vue`）：`padding:10 16`、bg-secondary、
 * label 13 text-secondary（下距 6）、输入 14/行高 1.5、计数右对齐 12 tertiary（上距 4）。
 *
 * ⚠ 底部那条 1px 线（web `.app-field { border-bottom }`）**必须画在白卡内部、输入行正下方**：
 * 旧实现挂在 `bgCard` 之外、落在 `bgPage` 灰底上（`#EBEDF0` vs `#F7F8FA`）⇒ 肉眼看不见，
 * 用户反馈「不知道哪里是输入项」。现改为输入区下划线（下方留白仍是白卡，才读得出是下划线）。
 */
@Composable
private fun AppFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    maxlength: Int,
    showWordLimit: Boolean = false,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 14f, FontWeight.Normal, colors.textSecondary, Modifier.padding(bottom = 6.dp))
        Box {
            if (value.isEmpty()) {
                FText(placeholder, 16f, FontWeight.Normal, colors.textTertiary, Modifier.padding(vertical = 2.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(maxlength)) },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = scaledSp(16f), lineHeight = scaledSp(21f)),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
        // ⭐ 输入区下划线（用户要求：让「名称 / 备注」一眼看出是可输入项）。
        // ⚠ 必须画在**白卡内部**：原来那条 1px 线挂在 `bgCard` 之外、落在 `bgPage` 灰底上，
        //    `#EBEDF0` 与 `#F7F8FA` 几乎同色 ⇒ 实际根本看不见，等于没有下划线。
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(1.dp)
                .background(colors.border),
        )
        if (showWordLimit) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                FText("${value.length}/$maxlength", 12f, FontWeight.Normal, colors.textTertiary)
            }
        }
    }
}
