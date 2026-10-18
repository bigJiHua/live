package com.live.finance.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.DbBackupFile
import com.live.finance.data.model.DbSystemBackupGroup
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.finance.theme.vanGreen
import com.live.finance.theme.vanOrangeBg
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
import com.live.finance.core.net.showFail

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
    /** 首次加载已完成 ⇒ 之后「刷新列表」**只转按钮**、不再把整列表换成转圈（他反馈刷新体验差）。 */
    var listReady by remember { mutableStateOf(false) }

    var sysLoading by remember { mutableStateOf(false) }
    var sysLoadingList by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<DbSystemBackupGroup>>(emptyList()) }
    val expandedDates = remember { mutableStateMapOf<String, Boolean>() }

    var isBackupRunning by remember { mutableStateOf(false) }
    var backupTaskId by remember { mutableStateOf("") }
    var backupProgress by remember { mutableStateOf(0) }

    /**
     * @param notify true = 刷新完成后给个 toast（他反馈「刷新无提示」）。
     *   web 的「刷新列表」点完只有加载态、没有任何完成提示，手机上根本看不出有没有刷成功。
     */
    suspend fun loadManualBackups(notify: Boolean = false) {
        loading = true
        when (val r = graph.data.backups()) {
            is ApiResult.Ok -> {
                backupList = r.data.orEmpty()
                listReady = true
                if (notify) toast.success("列表已刷新")
            }
            // web：showToast({ message: "加载手动导出记录失败", type: "fail" })
            is ApiResult.Fail -> toast.fail("加载手动导出记录失败")
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
    // ⚠ **先查再等**（他反馈「进度无反馈」）：原实现是先 `delay(5_000)` 再请求 ⇒ 进度块出现的头 5 秒
    //   永远是 0%、进度条一动不动，任务若很快完成更是「0% → 直接完成」，中间毫无反馈。
    LaunchedEffect(backupTaskId, isBackupRunning) {
        if (!isBackupRunning || backupTaskId.isEmpty()) return@LaunchedEffect
        while (true) {
            var done = false
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
                            done = true
                        }
                        "failed" -> {
                            isBackupRunning = false
                            toast.fail(t.error.ifBlank { "系统备份失败" })
                            done = true
                        }
                        "cancelled" -> {
                            isBackupRunning = false
                            toast.warning("备份任务已取消")
                            done = true
                        }
                    }
                }
                else -> Unit
            }
            if (done) break
            delay(5_000)
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
            if (ok) toast.success("下载开始") else toast.fail("下载失败")
        }
    }

    fun handleDownload(filename: String) {
        scope.launch {
            // ⚠ 原生差异：web 是 blob 直接落盘（几乎瞬间），原生必须**先把整个文件取回内存**再让用户在
            //   SAF 里选保存位置 —— 备份文件动辄几 MB～几十 MB，这一步会明显卡住，
            //   而原来**什么提示都没有**（他反馈「进度无反馈」）⇒ 加 loading 遮挡，取完再关。
            toast.loading("正在准备文件...")
            when (val r = graph.data.downloadBackup(filename)) {
                is ApiResult.Ok -> {
                    pendingBytes = r.data ?: ByteArray(0)
                    pendingName = filename
                    toast.close()
                    saveLauncher.launch(pendingName)
                }
                else -> { toast.close(); toast.showFail(r, "下载失败") }
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
                VanIcon(name = "records", size = 48.sp, color = vanGreen())
                Spacer(Modifier.height(12.dp))
                FText("数据库备份", 20f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
                FText("管理和查看数据库备份文件", 16f, FontWeight.Normal, colors.textTertiary)
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
                                            // web：reuseExisting 复用已有任务 → warning，否则 success
                                            if (t.reuseExisting) toast.warning("已有备份任务执行中，自动加入监控")
                                            else toast.success("系统备份任务已提交，后台处理中...")
                                        } else {
                                            // 兼容旧版同步返回
                                            toast.success("系统备份成功")
                                            loadSystemBackups()
                                        }
                                    }
                                    is ApiResult.Fail -> toast.fail(r.message.ifBlank { "系统备份失败" })
                                    is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                                    else -> toast.showFail(r, "系统备份失败")
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
                        color = vanGreen(),
                        strokeWidth = 12.dp,
                        showPivot = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        AppButton(
                            text = "刷新备份列表",
                            icon = "replay",
                            // 刷新完给个提示（他反馈「刷新无提示」）
                            onClick = { scope.launch { loadSystemBackups(); toast.success("备份列表已刷新") } },
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
                onClick = { scope.launch { loadManualBackups(notify = true) } },
                type = AppButtonType.Primary,
                size = AppButtonSize.Large,
                block = true,
                round = true,
                loading = loading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 12.dp),
            )

            // ⚠ 只有**首次**加载才整页转圈；之后刷新保留列表（按钮上的 loading 已经在转）
            if (!listReady && loading) {
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
                                        else -> toast.fail("删除失败")
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
            FText(g.date, 16f, FontWeight.Medium, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            FText("${g.files.size} 个文件", 12f, FontWeight.Normal, colors.textTertiary)
            Spacer(Modifier.width(6.dp))
            // web `van-collapse-item__right-icon` 展开时旋转 90°（旧实现箭头永远朝右 ⇒ 看不出展开态）
            //   Vant 的旋转带 `transition: transform .3s` ⇒ 用 `animateFloatAsState` 补上过渡。
            val rot by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(300),
                label = "db-backup-arrow",
            )
            VanIcon(
                name = "arrow",
                size = 14.sp,
                color = colors.textTertiary,
                modifier = Modifier.graphicsLayer { rotationZ = rot },
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        // web `van-collapse` 展开/收起是 `transition: height .3s` 的**平滑动画**；
        //   旧实现是 `if (expanded)` 直接挂/摘 ⇒ 内容瞬间跳出来（「UI 不适配」的一部分）。
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(300)),
        ) {
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
        // web `DbBackup.vue:454-455` `.sfi-icon.{full,schema}`：
        //   full   `background: rgba(7,193,96,.1)`（**裸 rgba**，两模式同值）
        //          `color: var(--van-green)`（深色 = --theme-success）
        //   schema `background: var(--van-orange-bg)`（**没写 fallback** → 浅色变量不存在 → 透明；深色 = .12）
        //          `color: var(--van-orange)` = #ff976a（两模式同值）
        // ⚠ 早期注释写「--van-orange-bg 未定义」方向对但理由错：它**只在深色块**里定义。
        TypeBadge(
            text = if (schema) "结构" else "完整",
            bg = if (schema) vanOrangeBg(Color.Transparent) else Color(0x1A07C160),
            fg = if (schema) DB_ORANGE else vanGreen(),
            size = 36,
            fontSize = 10f,
        )
        Column(Modifier.weight(1f)) {
            BasicText(
                f.filename,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(12f), fontWeight = FontWeight.Medium),
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FText(backupFileSize(f.size), 12f, FontWeight.Normal, colors.textTertiary)
                // ⚠ 窄屏适配：时间比大小长得多，不给 weight 会**顶出容器**（web 靠 flex 收缩），
                //   这里让时间吃掉剩余宽度并省略号截断。
                FText(backupTime(f.createdAt), 12f, FontWeight.Normal, colors.textTertiary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 手动导出记录的一行（`.backup-item`：白卡 radius 12、`padding 14/16`、浅投影）。
 *
 * ⚠ **手机宽度适配（他反馈「宽度不佳」）**：web 是 `display:flex; justify-content:space-between` 的
 *   **单行**结构（左：角标 + 文件名/大小/时间；右：下载 + 删除两个按钮）。在桌面够用，
 *   但手机上两个按钮要占掉约 140dp ⇒ 文件名只剩 100 出头 dp，几乎全被省略号截掉。
 *   ⇒ 安卓侧改成**两行**：第一行角标 + 文件信息（占满宽），第二行两个按钮**右对齐**
 *   —— 与本工程账单卡「信息在上、操作在下」的排布一致。
 */
@Composable
private fun ManualBackupItem(item: DbBackupFile, onDownload: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    val zip = item.type == "zip"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    maxLines = 2,                      // 宽度让出来了 ⇒ 允许折行显示 2 行
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textPrimary, fontSize = scaledSp(14f), fontWeight = FontWeight.Medium),
                )
                FText(backupFileSize(item.size), 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 3.dp))
                FText(backupTime(item.createdAt), 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 1.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(text = "下载", onClick = onDownload, type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true, round = true)
            Spacer(Modifier.width(8.dp))
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
            style = TextStyle(color = fg, fontSize = scaledSp(fontSize), fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp),
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
        FText(title, 16f, FontWeight.Normal, LocalAppColors.current.textPrimary, Modifier.weight(1f))
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
        FText(title, 16f, FontWeight.Normal, LocalAppColors.current.textPrimary, Modifier.weight(1f))
        FText(value, 16f, FontWeight.Normal, LocalAppColors.current.textSecondary)
    }
}

internal val DB_ORANGE = Color(0xFFFF976A)    // `--van-orange`（style.css:158 写死 #ff976a，跟 `--van-orange-bg` 无关）

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
