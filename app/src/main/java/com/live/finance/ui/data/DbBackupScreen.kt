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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.DbBackupFile
import com.live.finance.data.model.DbSystemBackupGroup
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanProgress
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 数据库备份 —— 一比一复刻 web `views/Finance/data/DbBackup.vue`。
 *
 * 结构：头部卡（`records` 48 绿 + 标题 + 副标题）→【系统备份】大按钮（异步中禁用、文案变「备份中...」）+
 * 说明 label →【备份进度】（异步中才出现：状态标签「后台备份中」/ 进度 / `van-progress` 绿 12 + 边框 /
 * 「刷新备份列表」）→【已完整备份列表】按**日期分组的折叠面板**（默认展开**最新一天**；标题 = `calendar-o` 主色 +
 * 日期 + 右侧「N 个文件」；内容 = 每行 36×36 类型角标「完整 / 结构」+ 文件名 + 大小 · 时间）→ 分隔线 →
 * 【手动导出记录】（「刷新列表」按钮 / 加载 / 空态 / 列表：40×40 角标「SQL / ZIP」+ 文件名 + 大小 + 时间 + 下载·删除）→ 完。
 *
 * 行为（照抄）：
 * 1. 系统备份**一定异步**：`POST /backups/system` 回 202 + taskId → 5 秒轮询；`completed` → 进度置 100 + toast + **重拉列表**；
 *    `failed`/`cancelled` 各有提示；`reuseExisting` 时提示"已有备份任务执行中，自动加入监控"。
 * 2. 手动备份的**删除受 PIN 保护**（后端 `pinLockGuard`）；删除成功后只**本地剔除**该行，不重拉。
 * 3. 系统备份文件**没有**下载/删除按钮（web 如此）。
 *
 * ⚠ 照抄 web 实际渲染：`.sfi-icon.schema` / `.bi-icon.zip` 用的 `var(--van-orange-bg)` **未定义**
 *   → 底色实际**透明**（只有 `--van-orange` #ff976a 的文字色生效）。
 * ⚠ 原生差异：web 下载走 blob 直接落盘；原生用 SAF `CreateDocument` 选保存位置（免权限）。
 */
@Composable
fun DbBackupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var backupList by remember { mutableStateOf<List<DbBackupFile>>(emptyList()) }

    var sysLoading by remember { mutableStateOf(false) }
    var sysLoadingList by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<DbSystemBackupGroup>>(emptyList()) }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    var isBackupRunning by remember { mutableStateOf(false) }
    var backupTaskId by remember { mutableStateOf("") }
    var backupProgress by remember { mutableStateOf(0) }

    suspend fun loadManualBackups() {
        loading = true
        when (val r = graph.data.backups()) {
            is ApiResult.Ok -> backupList = r.data.orEmpty()
            is ApiResult.Fail -> toast.show("加载手动导出记录失败")
            else -> Unit
        }
        loading = false
    }

    suspend fun loadSystemBackups() {
        sysLoadingList = true
        when (val r = graph.data.systemBackups()) {
            is ApiResult.Ok -> {
                groups = r.data.orEmpty()
                // web：默认展开**第一天**（后端已按日期倒序）
                expandedDates.clear()
                groups.firstOrNull()?.let { expandedDates[it.date] = true }
            }
            else -> Unit
        }
        sysLoadingList = false
    }

    LaunchedEffect(Unit) {
        loadManualBackups()
        loadSystemBackups()
    }

    // 5 秒轮询系统备份任务
    LaunchedEffect(backupTaskId, isBackupRunning) {
        if (!isBackupRunning || backupTaskId.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(5_000)
            when (val r = graph.data.taskStatus(backupTaskId)) {
                is ApiResult.Ok -> {
                    val t = r.data
                    if (t == null) { isBackupRunning = false; break }
                    backupProgress = t.progress
                    when (t.status) {
                        "completed" -> {
                            isBackupRunning = false
                            backupProgress = 100
                            toast.success("系统备份完成！")
                            loadSystemBackups()
                            break
                        }
                        "failed" -> {
                            isBackupRunning = false
                            toast.show(t.error.ifBlank { "系统备份失败" })
                            break
                        }
                        "cancelled" -> {
                            isBackupRunning = false
                            toast.show("备份任务已取消")
                            break
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    // 下载（先取字节 → SAF 选位置）
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf("") }
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
            if (ok) toast.success("下载开始") else toast.show("下载失败")
        }
    }

    fun handleDownload(filename: String) {
        scope.launch {
            when (val r = graph.data.downloadBackup(filename)) {
                is ApiResult.Ok -> {
                    pendingBytes = r.data ?: ByteArray(0)
                    pendingName = filename
                    saveLauncher.launch(pendingName)
                }
                else -> toast.show("下载失败")
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
            // 头部卡
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgCard).padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VanIcon(name = "records", size = 48.sp, color = DB_GREEN)
                Spacer(Modifier.height(12.dp))
                FText("数据库备份", 20f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
                FText("管理和查看数据库备份文件", 14f, FontWeight.Normal, colors.textTertiary)
            }
            Spacer(Modifier.height(20.dp))

            // ===== 系统备份 =====
            DbGroupTitle("系统备份")
            DbCardGroup {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    AppButton(
                        text = if (isBackupRunning) "备份中..." else "系统性备份数据库",
                        onClick = {
                            sysLoading = true
                            scope.launch {
                                when (val r = graph.data.createSystemBackup()) {
                                    is ApiResult.Ok -> {
                                        val t = r.data
                                        if (t != null && t.id.isNotEmpty()) {
                                            isBackupRunning = true
                                            backupTaskId = t.id
                                            backupProgress = 0
                                            toast.show("系统备份任务已提交，后台处理中...")
                                        } else {
                                            // 兼容旧版同步返回
                                            toast.success("系统备份成功")
                                            loadSystemBackups()
                                        }
                                    }
                                    is ApiResult.Fail -> toast.show(r.message.ifBlank { "系统备份失败" })
                                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                                    else -> toast.show("系统备份失败")
                                }
                                sysLoading = false
                            }
                        },
                        type = AppButtonType.Primary,
                        size = AppButtonSize.Large,
                        block = true,
                        round = true,
                        loading = sysLoading,
                        disabled = isBackupRunning,
                    )
                    Spacer(Modifier.height(4.dp))
                    FText("将自动执行完整备份（含数据）和仅结构备份，保存至日期目录", 12f, FontWeight.Normal, colors.textTertiary)
                }
            }

            if (isBackupRunning) {
                Spacer(Modifier.height(12.dp))
                DbGroupTitle("备份进度")
                DbCardGroup {
                    AppCellTagWarning("状态", "后台备份中")
                    AppCellValue("进度", "$backupProgress%")
                    VanProgress(
                        percentage = backupProgress.toFloat(),
                        color = DB_GREEN,
                        strokeWidth = 12.dp,
                        showPivot = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        AppButton(
                            text = "刷新备份列表",
                            icon = "replay",
                            onClick = { scope.launch { loadSystemBackups() } },
                            type = AppButtonType.Default,
                            size = AppButtonSize.Small,
                            block = true,
                            round = true,
                        )
                    }
                }
            }

            if (groups.isNotEmpty()) DbGroupTitle("已完整备份列表")

            if (sysLoadingList) {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    VanLoading(size = 24.sp, text = "加载中...")
                }
            }

            if (!sysLoadingList && groups.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgCard),
                ) {
                    groups.forEach { g ->
                        val expanded = expandedDates[g.date] == true
                        SysGroup(g, expanded) { expandedDates[g.date] = !expanded }
                    }
                }
            }

            // .divider{margin 20 0}
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(20.dp))

            // ===== 手动导出记录 =====
            DbGroupTitle("手动导出记录")
            AppButton(
                text = "刷新列表",
                icon = "replay",
                onClick = { scope.launch { loadManualBackups() } },
                type = AppButtonType.Primary,
                size = AppButtonSize.Large,
                block = true,
                round = true,
                loading = loading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 12.dp),
            )

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    VanLoading(size = 24.sp, text = "加载中...")
                }
            } else if (backupList.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                    VanEmpty(description = "暂无手动导出记录")
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    backupList.forEach { item ->
                        ManualBackupItem(
                            item = item,
                            onDownload = { handleDownload(item.filename) },
                            onDelete = {
                                scope.launch {
                                    when (graph.data.deleteBackup(item.filename)) {
                                        is ApiResult.Ok -> {
                                            toast.success("删除成功")
                                            // web：仅本地剔除，不重拉
                                            backupList = backupList.filter { it.filename != item.filename }
                                        }
                                        is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                                        else -> toast.show("删除失败")
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 日期分组折叠（Vant `van-collapse-item`：标题行 = 单元格样式，含 1px 底线）。 */
@Composable
private fun SysGroup(g: DbSystemBackupGroup, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VanIcon(name = "calendar-o", size = 16.sp, color = colors.primary)
            Spacer(Modifier.width(6.dp))
            FText(g.date, 14f, FontWeight.Medium, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            FText("${g.files.size} 个文件", 11f, FontWeight.Normal, colors.textTertiary)
            Spacer(Modifier.width(6.dp))
            VanIcon(name = "arrow", size = 14.sp, color = colors.textTertiary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                g.files.forEach { f -> SysFileRow(f) }
            }
        }
    }
}

@Composable
private fun SysFileRow(f: DbBackupFile) {
    val colors = LocalAppColors.current
    val schema = f.type == "schema-only"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 36×36 类型角标（schema 底透明：`--van-orange-bg` 未定义）
        TypeBadge(
            text = if (schema) "结构" else "完整",
            bg = if (schema) Color.Transparent else DB_GREEN.copy(alpha = 0.1f),
            fg = if (schema) DB_ORANGE else DB_GREEN,
            size = 36,
            fontSize = 10f,
        )
        Column(Modifier.weight(1f)) {
            BasicText(
                f.filename,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FText(backupFileSize(f.size), 11f, FontWeight.Normal, colors.textTertiary)
                FText(backupTime(f.createdAt), 10f, FontWeight.Normal, colors.textTertiary)
            }
        }
    }
}

/** 手动导出记录的一行（`.backup-item`：白卡 radius 12、`padding 14/16`、space-between、浅投影）。 */
@Composable
private fun ManualBackupItem(item: DbBackupFile, onDownload: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    val zip = item.type == "zip"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 40×40 角标（ZIP 底透明：`--van-orange-bg` 未定义）
            TypeBadge(
                text = if (zip) "ZIP" else "SQL",
                bg = if (zip) Color.Transparent else colors.primary.copy(alpha = 0.1f),
                fg = if (zip) DB_ORANGE else colors.primary,
                size = 40,
                fontSize = 11f,
                letterSpacing = 0.5f,
            )
            Column(Modifier.weight(1f)) {
                BasicText(
                    item.filename,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                FText(backupFileSize(item.size), 11f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 3.dp))
                FText(backupTime(item.createdAt), 10f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 1.dp))
            }
        }
        Row(
            Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppButton(text = "下载", onClick = onDownload, type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true, round = true)
            AppButton(text = "删除", onClick = onDelete, type = AppButtonType.Danger, size = AppButtonSize.Small, plain = true, round = true)
        }
    }
}

/** SQL/完整/ZIP/结构 的圆角文字角标。 */
@Composable
private fun TypeBadge(text: String, bg: Color, fg: Color, size: Int, fontSize: Float, letterSpacing: Float = 0f) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(if (size >= 40) 10.dp else 8.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp),
        )
    }
}

/** `app-cell` 的「title + 右侧标签」一行（本页只用于进度区的状态）。 */
@Composable
private fun AppCellTagWarning(title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 14f, FontWeight.Normal, LocalAppColors.current.textPrimary, Modifier.weight(1f))
        VanTag(text = text, type = VanTagType.Warning, size = VanTagSize.Large)
    }
}

/** `app-cell` 的「title + 右侧值」一行。 */
@Composable
private fun AppCellValue(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(title, 14f, FontWeight.Normal, LocalAppColors.current.textPrimary, Modifier.weight(1f))
        FText(value, 14f, FontWeight.Normal, LocalAppColors.current.textSecondary)
    }
}

internal val DB_ORANGE = Color(0xFFFF976A)    // `--van-orange`（Vant 调色板；`--van-orange-bg` 未定义）

/** web `formatFileSize`（备份页口径，与导出页一致）。 */
private fun backupFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

/** web `dayjs(ts).format('YYYY-MM-DD HH:mm:ss')`：兼容 ISO 8601（后端 Date→JSON）与 `yyyy-MM-dd HH:mm:ss`。 */
private fun backupTime(ts: String): String {
    if (ts.isEmpty()) return ""
    val out = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return runCatching {
        OffsetDateTime.parse(ts).atZoneSameInstant(ZoneId.systemDefault()).format(out)
    }.recoverCatching {
        LocalDateTime.parse(ts.replace(' ', 'T')).format(out)
    }.getOrDefault(ts)
}
