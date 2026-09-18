package com.live.finance.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.DbExportTask
import com.live.finance.data.model.DbTableStatus
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanProgress
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanRadio
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出数据库/表 —— 一比一复刻 web `views/Finance/data/DbExport.vue`。
 *
 * 结构：头部卡（`arrow-down` 48 **绿 #07c160** + 标题 + 副标题）→ 导出类型（两张可点 `app-cell` + 右侧 `van-radio`：
 * 导出全部数据 / 导出单张表）→ 单表模式下「选择表」（带左图标的搜索框 + 200 高可滚列表）→
 * 数据库概览（表数量/总行数）或表信息（表名/行数）→ 导出选项（`van-switch size=20` 包含数据 + 一行说明）→
 * 确认导出（`loading && !isAsyncTask`，`!canExport` 时禁用；异步中多一个「取消导出」warning 按钮）→
 * 导出进度（`van-progress show-pivot stroke-width=12 color #07c160` + 「手动刷新状态」）→
 * 导出结果（状态标签 / 文件名 / 大小 / 时间 / 「点击下载」链接）→ 底部「查看数据库备份」入口（绿色）。
 *
 * 行为（照抄）：
 * 1. 提交导出**一定**是异步：后端回 **202 + taskId**（`reuseExisting` 时提示"已有导出任务正在执行，自动加入监控"）；
 * 2. **5 秒轮询** `GET /export/task/:id` 更新进度；`completed` → 取 `result.filename || result.full.filename`
 *    （系统备份任务的 `result.full` 是**对象**）、`size || full.size`；`failed` / `cancelled` 各有终态；
 * 3. 「手动刷新状态」：终态走上面同一套处理，否则 toast「当前进度: N%」；
 * 4. 「确认导出」的 loading 只在**提交阶段**出现（异步轮询阶段按钮文案变「导出中...」）。
 *
 * ⚠ 原生差异（记档）：web「点击下载」是 blob + `a.download`（浏览器直接落盘）；
 *   原生改用 **SAF `CreateDocument`**：先在内存拿到字节，再让你选保存位置（免存储权限），失败则 toast。
 */
@Composable
fun DbExportScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("all") }     // all / single
    var selectedTable by remember { mutableStateOf("") }
    var includeData by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var allTables by remember { mutableStateOf<List<String>>(emptyList()) }
    var tableStatus by remember { mutableStateOf<Map<String, DbTableStatus>>(emptyMap()) }
    var totalRows by remember { mutableStateOf(0L) }

    var loading by remember { mutableStateOf(false) }
    var isAsyncTask by remember { mutableStateOf(false) }
    var currentTaskId by remember { mutableStateOf("") }
    var asyncProgress by remember { mutableStateOf(0) }

    var exportCompleted by remember { mutableStateOf(false) }
    var exportTime by remember { mutableStateOf("") }
    var resultFilename by remember { mutableStateOf("") }
    var resultSize by remember { mutableStateOf(0L) }
    var statusType by remember { mutableStateOf("") }    // success / danger / warning
    var statusText by remember { mutableStateOf("") }

    val filteredTables = remember(allTables, searchQuery) {
        if (searchQuery.isEmpty()) allTables
        else allTables.filter { it.lowercase().contains(searchQuery.lowercase()) }
    }
    val canExport = selectedType == "all" || (selectedType == "single" && selectedTable.isNotEmpty())

    fun nowText() = SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.US).format(Date())

    LaunchedEffect(Unit) {
        val listRes = graph.data.tables()
        val statusRes = graph.data.tableStatus()
        val list = (listRes as? ApiResult.Ok)?.data.orEmpty()
        val status = (statusRes as? ApiResult.Ok)?.data.orEmpty()
        allTables = list
        tableStatus = status.associateBy { it.name }
        totalRows = status.sumOf { it.rowCount }
        val err = (listRes as? ApiResult.Fail)?.message ?: (statusRes as? ApiResult.Fail)?.message
        if (err != null) toast.show(err)
    }

    // 终态处理（轮询与手动刷新共用）
    fun applyTaskResult(t: DbExportTask) {
        isAsyncTask = false
        exportTime = nowText()
        resultFilename = t.resultFilename.ifEmpty { t.resultFull }
        resultSize = t.resultSize
        statusType = "success"
        statusText = "导出完成"
        exportCompleted = true
        toast.success("导出完成，请点击下载")
    }

    fun applyTaskTerminal(t: DbExportTask): Boolean = when (t.status) {
        "completed" -> { applyTaskResult(t); true }
        "failed" -> {
            isAsyncTask = false
            statusType = "danger"; statusText = "导出失败"
            exportCompleted = true
            toast.show(t.error.ifBlank { "导出失败" })
            true
        }
        "cancelled" -> {
            isAsyncTask = false
            statusType = "warning"; statusText = "已取消"
            exportCompleted = true
            toast.show("任务已取消")
            true
        }
        else -> false
    }

    // 5 秒轮询（web `setInterval(..., 5000)`）
    LaunchedEffect(currentTaskId, isAsyncTask) {
        if (!isAsyncTask || currentTaskId.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000)
            when (val r = graph.data.taskStatus(currentTaskId)) {
                is ApiResult.Ok -> {
                    val t = r.data
                    if (t == null) {
                        isAsyncTask = false
                        statusType = "danger"; statusText = "任务丢失"
                        exportCompleted = true
                        break
                    }
                    asyncProgress = t.progress
                    if (applyTaskTerminal(t)) break
                }
                else -> Unit   // web 轮询异常只打日志，不打断
            }
        }
    }

    fun handleExport() {
        loading = true
        exportCompleted = false
        isAsyncTask = false
        asyncProgress = 0
        scope.launch {
            val r = if (selectedType == "all") {
                graph.data.exportFull(includeData)
            } else {
                graph.data.exportTable(selectedTable, includeData)
            }
            loading = false
            when (r) {
                is ApiResult.Ok -> {
                    val t = r.data
                    if (t != null && t.id.isNotEmpty()) {
                        // 后端统一 202 + taskId（异步）
                        currentTaskId = t.id
                        isAsyncTask = true
                        statusType = "warning"; statusText = "后台导出中..."
                        toast.show("导出任务已提交，后台处理中...")
                    } else if (t != null && t.resultFilename.isNotEmpty()) {
                        // 兜底：同步返回（兼容旧版后端）
                        exportTime = nowText()
                        resultFilename = t.resultFilename
                        resultSize = t.resultSize
                        statusType = "success"; statusText = "导出成功"
                        exportCompleted = true
                        toast.success("导出成功，请点击下载")
                    } else {
                        statusType = "danger"; statusText = "导出失败"
                        exportCompleted = true
                        toast.show(r.message.ifBlank { "导出失败" })
                    }
                }
                is ApiResult.NeedPin -> {
                    statusType = "danger"; statusText = "导出失败"; exportCompleted = true
                    toast.show("需先验证 PIN")
                }
                is ApiResult.Fail -> {
                    statusType = "danger"; statusText = "导出失败"; exportCompleted = true
                    toast.show(r.message.ifBlank { "导出失败" })
                }
                else -> {
                    statusType = "danger"; statusText = "导出失败"; exportCompleted = true
                    toast.show("导出失败")
                }
            }
        }
    }

    // 「点击下载」：先取字节，再走 SAF 选保存位置（web 是 blob 直接落盘）
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    true
                }.getOrDefault(false)
            }
            if (ok) toast.success("下载已开始") else toast.show("下载失败：写入失败")
        }
    }

    fun handleDownload() {
        if (resultFilename.isEmpty()) {
            toast.show("没有可下载的文件")
            return
        }
        scope.launch {
            when (val r = graph.data.downloadBackup(resultFilename)) {
                is ApiResult.Ok -> {
                    pendingBytes = r.data ?: ByteArray(0)
                    saveLauncher.launch(resultFilename)
                }
                is ApiResult.Fail -> toast.show("下载失败：${r.message}")
                else -> toast.show("下载失败：未知错误")
            }
        }
    }

    ScreenScaffold { inner ->
        Column(
            inner
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        ) {
            // 头部卡（图标是绿色 #07c160，不是主色）
            Card16 {
                VanIcon(name = "arrow-down", size = 48.sp, color = DB_GREEN)
                Spacer(Modifier.height(12.dp))
                FText("导出数据库/表", 20f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
                FText("将数据库或指定表数据导出为文件", 14f, FontWeight.Normal, colors.textTertiary)
            }
            Spacer(Modifier.height(20.dp))

            DbGroupTitle("选择导出类型")
            DbCardGroup {
                AppCell(
                    title = "导出全部数据",
                    label = "包含所有表数据（SQL或ZIP格式）",
                    onClick = { selectedType = "all" },
                    rightIcon = { VanRadio(checked = selectedType == "all", onCheckedChange = { selectedType = "all" }) },
                    border = false,
                )
                AppCell(
                    title = "导出单张表",
                    label = "选择一个表导出",
                    onClick = { selectedType = "single" },
                    rightIcon = { VanRadio(checked = selectedType == "single", onCheckedChange = { selectedType = "single" }) },
                    border = false,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (selectedType == "single") {
                DbGroupTitle("选择表")
                DbCardGroup {
                    DbSearchField(searchQuery, { searchQuery = it })
                    // `.table-list{max-height:200; overflow-y:auto}`
                    Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        filteredTables.forEach { t ->
                            AppCell(
                                title = t,
                                onClick = { selectedTable = t },
                                rightIcon = { VanRadio(checked = selectedTable == t, onCheckedChange = { selectedTable = t }) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (selectedTable.isNotEmpty()) {
                    DbGroupTitle("表信息")
                    DbCardGroup {
                        AppCell(title = "表名", value = selectedTable)
                        tableStatus[selectedTable]?.let { st ->
                            AppCell(title = "数据行数", value = "%,d 行".format(st.rowCount), border = false)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                DbGroupTitle("数据库概览")
                DbCardGroup {
                    AppCell(title = "表数量", value = "${allTables.size} 张")
                    AppCell(title = "总数据行数", value = "%,d 行".format(totalRows), border = false)
                }
                Spacer(Modifier.height(16.dp))
            }

            DbGroupTitle("导出选项")
            DbCardGroup {
                AppCell(
                    title = "包含数据",
                    rightIcon = { VanSwitch(checked = includeData, onCheckedChange = { includeData = it }, size = 20) },
                )
                AppCell(title = "仅结构（不含数据）", label = "适用于仅备份表结构", border = false)
            }
            Spacer(Modifier.height(16.dp))

            // 动作区
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                AppButton(
                    text = if (isAsyncTask) "导出中..." else "确认导出",
                    onClick = { handleExport() },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Large,
                    block = true,
                    round = true,
                    loading = loading && !isAsyncTask,
                    disabled = !canExport,
                )
                if (isAsyncTask) {
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = "取消导出",
                        onClick = {
                            scope.launch {
                                when (graph.data.cancelTask(currentTaskId)) {
                                    is ApiResult.Ok -> {
                                        isAsyncTask = false
                                        toast.show("已取消导出")
                                    }
                                    else -> toast.show("取消失败")
                                }
                            }
                        },
                        type = AppButtonType.Warning,
                        size = AppButtonSize.Large,
                        block = true,
                        round = true,
                    )
                }
            }

            if (isAsyncTask) {
                DbGroupTitle("导出进度")
                DbCardGroup {
                    AppCell(
                        title = "状态",
                        rightIcon = { VanTag(text = "后台导出中", type = VanTagType.Warning, size = VanTagSize.Large) },
                    )
                    AppCell(title = "进度", value = "$asyncProgress%")
                    VanProgress(
                        percentage = asyncProgress.toFloat(),
                        color = DB_GREEN,
                        strokeWidth = 12.dp,
                        showPivot = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    AppCell(
                        border = false,
                        rightIcon = {
                            AppButton(
                                text = "手动刷新状态",
                                icon = "replay",
                                onClick = {
                                    scope.launch {
                                        when (val r = graph.data.taskStatus(currentTaskId)) {
                                            is ApiResult.Ok -> {
                                                val t = r.data ?: return@launch
                                                asyncProgress = t.progress
                                                if (!applyTaskTerminal(t)) toast.show("当前进度: ${t.progress}%")
                                            }
                                            else -> Unit
                                        }
                                    }
                                },
                                type = AppButtonType.Default,
                                size = AppButtonSize.Small,
                                block = true,
                                round = true,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (exportCompleted) {
                DbGroupTitle("导出结果")
                DbCardGroup {
                    AppCell(
                        title = "状态",
                        rightIcon = { VanTag(text = statusText, type = statusType.toVanTagType(), size = VanTagSize.Large) },
                    )
                    if (statusType == "success") {
                        AppCell(
                            title = "文件名",
                            rightIcon = {
                                FText(resultFilename, 12f, FontWeight.Normal, colors.textSecondary, Modifier.width(180.dp))
                            },
                        )
                        AppCell(title = "文件大小", value = formatFileSize(resultSize))
                        AppCell(title = "导出时间", value = exportTime)
                        AppCell(
                            title = "操作",
                            border = false,
                            rightIcon = {
                                FText(
                                    "点击下载", 14f, FontWeight.Normal, colors.primary,
                                    Modifier.clickable { handleDownload() },
                                    )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(0.dp))
            }

            Spacer(Modifier.height(20.dp))
            // 「查看数据库备份」入口（绿字）
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .clickable { nav.navigate(Routes.DATA_BACKUP) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                VanIcon(name = "records", size = 16.sp, color = DB_GREEN)
                Spacer(Modifier.width(6.dp))
                FText("查看数据库备份", 14f, FontWeight.Medium, DB_GREEN)
                Spacer(Modifier.width(6.dp))
                VanIcon(name = "arrow", size = 14.sp, color = DB_GREEN)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

internal val DB_GREEN = Color(0xFF07C160)

/** 头部卡：`padding 30/0`、`bg secondary`、圆角 16、居中、下方 20。 */
@Composable
private fun Card16(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgCard)
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** `.section-title{padding 8/16; 13px; tertiary; weight 500; mb 8}`（数据管理域共用）。 */
@Composable
internal fun DbGroupTitle(text: String) {
    val colors = LocalAppColors.current
    FText(
        text, 13f, FontWeight.Medium, colors.textTertiary,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

/** `van-cell-group inset .app-card`：圆角 12、左右内缩 16、overflow hidden（数据管理域共用）。 */
@Composable
internal fun DbCardGroup(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard),
    ) { content() }
}

/**
 * `app-field`（无标签）+ `left-icon="search"` + `clearable`：
 * 根 `padding 10/16` + 1px 通栏底线；左图标 16px placeholder 色、右距 6；清除「×」18px tertiary。
 * （原生共享 `AppField` 没有左图标/只读，故域内自带一个。）
 */
@Composable
internal fun DbSearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VanIcon(name = "search", size = 16.sp, color = colors.textPlaceholder)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                FText("搜索表名...", 14f, FontWeight.Normal, colors.textPlaceholder)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Default),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            FText(
                "×", 18f, FontWeight.Normal, colors.textTertiary,
                Modifier.clickable { onValueChange("") },
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

/** web `formatFileSize`（导出页专属口径）：`<1KB → N B`、`<1MB → x.xx KB`、否则 `x.xx MB`。 */
private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

/** web `app-tag` type → [VanTagType]。 */
private fun String.toVanTagType(): VanTagType = when (this) {
    "success" -> VanTagType.Success
    "warning" -> VanTagType.Warning
    "danger" -> VanTagType.Danger
    "primary" -> VanTagType.Primary
    else -> VanTagType.Default
}
