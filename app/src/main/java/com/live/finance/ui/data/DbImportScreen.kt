package com.live.finance.ui.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.DbSqlImportResult
import com.live.finance.data.model.DbValidateResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanNoticeBar
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.form.VanRadio
import com.live.vant.form.VanRadioShape
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导入 —— 一比一复刻 web `views/Finance/data/DbImport.vue`。
 *
 * 两种模式（顶部 `van-radio shape=square` 横向切换）：
 * - **JSON（单表）**：搜索选择目标表 → 选 .json（前端校验：后缀、≤10MB、必须是数组、≤5 万条）→
 *   数据预览（前 5 条：行号 + `key: value`，总记录数/字段数）→「校验数据」(`POST /validate`) →
 *   校验结果（可导入/有问题 标签 + 目标表字段数 + 有效行数 + 无效行数[红]）→ 导入选项（清空后导入开关 + 说明）
 *   → 危险提示条 → 确认导入（弹窗文案随「清空后导入」变化）→ 结果卡（导入记录数/备份文件/备份时间）。
 * - **SQL / ZIP（全库）**：选文件（≤100MB）→ 文件信息 → **红色**危险提示条（不可逆）→
 *   **两层确认弹窗**（"⚠️ 确认 SQL 导入 / 我已备份，继续" → "⚠️ 二次确认 / 确定执行"）→ 上传执行 →
 *   结果卡（执行语句 / 失败语句[红] / 失败详情清单 / 备份文件 / 备份时间）。
 *
 * ⚠ 照抄 web 实际渲染：两处 `van-notice-bar` 的 `background` 传的都是 `var(--van-orange-bg)`，
 *   而该变量**未定义** → 底色实际**透明**（只有文字色生效：SQL 那条是 `--theme-danger`，JSON 那条是 `--van-orange`）。
 * ⚠ 原生差异（记档）：web 用 `res.status === 207` 判定"部分失败"；原生 `ApiResult` 拿不到 207 之外的信息，
 *   故改用 **`errorCount > 0`** 判定（两者等价：后端只有在有失败语句时才回 207）。
 * ⚠ 两个上传接口都**受 PIN 保护**（`/import/sql` 与删除类同），普通账号在 `/data-manager` 全模块会先吃 403。
 */
@Composable
fun DbImportScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf("json") }        // json / sql
    var allTables by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTable by remember { mutableStateOf("") }

    // JSON 侧
    var jsonRows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var jsonFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var jsonFileName by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<DbValidateResult?>(null) }
    var forceClear by remember { mutableStateOf(false) }

    // SQL 侧
    var sqlFileName by remember { mutableStateOf("") }
    var sqlFileSize by remember { mutableStateOf(0L) }
    var sqlBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sqlResult by remember { mutableStateOf<DbSqlImportResult?>(null) }

    // 通用
    var loading by remember { mutableStateOf(false) }
    var importCompleted by remember { mutableStateOf(false) }
    var importTime by remember { mutableStateOf("") }
    var importedCount by remember { mutableStateOf(0) }
    var statusType by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var backupFile by remember { mutableStateOf("") }
    var backupTime by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }            // "" / jsonConfirm / sql1 / sql2

    val filteredTables = remember(allTables, searchQuery) {
        if (searchQuery.isEmpty()) allTables
        else allTables.filter { it.lowercase().contains(searchQuery.lowercase()) }
    }
    val canImport = if (format == "json") {
        selectedTable.isNotEmpty() && jsonRows.isNotEmpty()
    } else {
        sqlFileName.isNotEmpty() && sqlBytes != null
    }

    fun nowText() = SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.US).format(Date())

    LaunchedEffect(Unit) {
        when (val r = graph.data.tables()) {
            is ApiResult.Ok -> allTables = r.data.orEmpty()
            else -> toast.show("加载表列表失败")
        }
    }

    // ===== JSON 选文件（前端三重校验：后缀 / ≤10MB / 数组 / ≤5 万条）=====
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uriDisplayName(ctx, uri)
            if (!name.lowercase().endsWith(".json")) {
                toast.show("请上传 JSON 文件")
                return@launch
            }
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (text == null) {
                toast.show("JSON解析失败")
                return@launch
            }
            if (text.size > 10 * 1024 * 1024) {
                toast.show("文件超过10MB，请拆分后导入")
                jsonRows = emptyList(); jsonFields = emptyList(); jsonFileName = ""
                return@launch
            }
            val parsed = runCatching { JsonParser.parseString(String(text, Charsets.UTF_8)) }.getOrNull()
            if (parsed == null || !parsed.isJsonArray) {
                toast.show("JSON文件格式错误，应为数组")
                jsonRows = emptyList(); jsonFields = emptyList(); jsonFileName = ""
                return@launch
            }
            val arr = parsed.asJsonArray
            if (arr.size() > 50_000) {
                toast.show("单次导入不能超过5万条数据")
                jsonRows = emptyList(); jsonFields = emptyList(); jsonFileName = ""
                return@launch
            }
            val rows = arr.mapNotNull { it as? JsonObject }
            jsonRows = rows
            jsonFields = rows.firstOrNull()?.keySet()?.toList() ?: emptyList()
            jsonFileName = name
            validation = null
            importCompleted = false
            toast.success("已加载 ${rows.size} 条数据")
        }
    }

    // ===== SQL/ZIP 选文件（后缀 + ≤100MB）=====
    val sqlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uriDisplayName(ctx, uri)
            val lower = name.lowercase()
            if (!lower.endsWith(".sql") && !lower.endsWith(".zip")) {
                toast.show("请上传 .sql 或 .zip 文件")
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                toast.show("读取文件失败")
                return@launch
            }
            if (bytes.size > 100 * 1024 * 1024) {
                toast.show("文件超过 100MB 限制")
                return@launch
            }
            sqlFileName = name
            sqlFileSize = bytes.size.toLong()
            sqlBytes = bytes
            importCompleted = false
            toast.success("已选择文件: $name")
        }
    }

    fun runJsonImport() {
        loading = true
        importCompleted = false
        scope.launch {
            when (val r = graph.data.importData(selectedTable, jsonRows, forceClear)) {
                is ApiResult.Ok -> {
                    val d = r.data
                    importTime = nowText()
                    importedCount = d?.importedRows ?: jsonRows.size
                    backupFile = d?.backupFile.orEmpty()
                    backupTime = ""
                    statusType = "success"; statusText = "导入成功"
                    importCompleted = true
                    toast.success("导入成功")
                }
                is ApiResult.Fail -> {
                    importTime = nowText()
                    statusType = "danger"; statusText = "导入失败"
                    importCompleted = true
                    toast.show(r.message.ifBlank { "导入失败" })
                }
                is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                else -> {
                    importTime = nowText()
                    statusType = "danger"; statusText = "导入失败"
                    importCompleted = true
                    toast.show("导入失败")
                }
            }
            loading = false
        }
    }

    fun runSqlImport() {
        val bytes = sqlBytes ?: return
        loading = true
        importCompleted = false
        scope.launch {
            when (val r = graph.data.importSql(bytes, sqlFileName)) {
                is ApiResult.Ok -> {
                    val d = r.data ?: DbSqlImportResult()
                    importTime = nowText()
                    backupFile = d.backupFile
                    backupTime = d.backupTimestamp
                    sqlResult = d
                    if (d.errorCount > 0) {
                        statusType = "warning"; statusText = "部分失败"
                        toast.show("SQL 导入完成，${d.errorCount} 条语句失败")
                    } else {
                        statusType = "success"; statusText = "导入成功"
                        toast.success("SQL 导入成功")
                    }
                    importCompleted = true
                }
                is ApiResult.Fail -> {
                    importTime = nowText()
                    statusType = "danger"; statusText = "导入失败"
                    importCompleted = true
                    toast.show(r.message.ifBlank { "导入失败" })
                }
                is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                else -> {
                    importTime = nowText()
                    statusType = "danger"; statusText = "导入失败"
                    importCompleted = true
                    toast.show("导入失败")
                }
            }
            loading = false
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
            // 头部卡（图标是 `--van-orange` #ff976a）
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.bgCard).padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VanIcon(name = "arrow-up", size = 48.sp, color = DB_ORANGE)
                Spacer(Modifier.height(12.dp))
                FText("数据导入", 20f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
                FText("从文件导入数据到数据库", 14f, FontWeight.Normal, colors.textTertiary)
            }
            Spacer(Modifier.height(20.dp))

            // 导入格式（横向方形单选）
            DbGroupTitle("导入格式")
            DbCardGroup {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    VanRadio(
                        checked = format == "json",
                        shape = VanRadioShape.Square,
                        label = "JSON (单表)",
                        onCheckedChange = { format = "json" },
                    )
                    VanRadio(
                        checked = format == "sql",
                        shape = VanRadioShape.Square,
                        label = "SQL / ZIP (全库)",
                        onCheckedChange = { format = "sql" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (format == "json") {
                // 选择目标表
                DbGroupTitle("选择目标表")
                DbCardGroup {
                    DbSearchField(searchQuery, { searchQuery = it })
                    Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        filteredTables.forEach { t ->
                            AppCell(
                                title = t,
                                onClick = {
                                    selectedTable = t
                                    validation = null
                                },
                                rightIcon = { VanRadio(checked = selectedTable == t, onCheckedChange = { selectedTable = t; validation = null }) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 选择文件
                DbGroupTitle("选择文件")
                DbCardGroup {
                    AppCell(title = "支持格式", label = "JSON 文件")
                    if (selectedTable.isNotEmpty()) {
                        AppCell(
                            title = "当前选择表",
                            border = false,
                            rightIcon = { FText(selectedTable, 14f, FontWeight.Medium, colors.primary) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppButton(
                    text = if (jsonFileName.isEmpty()) "选择 JSON 文件" else jsonFileName,
                    icon = "plus",
                    onClick = { jsonPicker.launch(arrayOf("application/json", "*/*")) },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Small,
                    block = true,
                )
                Spacer(Modifier.height(16.dp))

                if (jsonRows.isNotEmpty()) {
                    val previewN = minOf(5, jsonRows.size)
                    DbGroupTitle("数据预览 (前$previewN 条)")
                    DbCardGroup {
                        AppCell(title = "总记录数", value = "${jsonRows.size} 条")
                        AppCell(title = "字段数", value = "${jsonFields.size} 个", border = false)
                    }
                    // `.data-preview`：bg tertiary、圆角 12、上距 8、上下内边距 8；行 `padding 8/16` + 1px 边框
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgThird)
                            .padding(vertical = 8.dp),
                    ) {
                        jsonRows.take(previewN).forEachIndexed { idx, row ->
                            Row(Modifier.fillMaxWidth().border(1.dp, colors.border).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                FText("${idx + 1}", 12f, FontWeight.Normal, colors.textTertiary, Modifier.width(28.dp))
                                Column(Modifier.weight(1f)) {
                                    row.entrySet().forEach { e ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FText("${e.key}:", 12f, FontWeight.Normal, colors.textSecondary)
                                            BasicText(
                                                jsonValueText(e.value),
                                                modifier = Modifier.weight(1f),
                                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, lineHeight = 21.6.sp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    AppButton(
                        text = "校验数据",
                        onClick = {
                            validating = true
                            scope.launch {
                                when (val r = graph.data.validateImport(selectedTable, jsonRows)) {
                                    is ApiResult.Ok -> {
                                        validation = r.data
                                        val v = r.data
                                        if (v?.canImport == true) toast.success("数据校验通过")
                                        else toast.show("有 ${v?.invalidRows ?: 0} 行数据存在问题")
                                    }
                                    else -> toast.show("校验失败")
                                }
                                validating = false
                            }
                        },
                        type = AppButtonType.Default,
                        size = AppButtonSize.Large,
                        block = true,
                        round = true,
                        loading = validating,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                }

                validation?.let { v ->
                    DbGroupTitle("校验结果")
                    DbCardGroup {
                        AppCell(
                            title = "状态",
                            rightIcon = {
                                VanTag(
                                    text = if (v.canImport) "可导入" else "有问题",
                                    type = if (v.canImport) VanTagType.Success else VanTagType.Danger,
                                    size = VanTagSize.Large,
                                )
                            },
                        )
                        AppCell(title = "目标表字段", value = "${v.tableColumns.size} 个")
                        AppCell(title = "有效行数", value = "${v.validRows} 条")
                        if (v.invalidRows > 0) {
                            AppCell(title = "无效行数", value = "${v.invalidRows} 条", valueColor = colors.danger, border = false)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                DbCardGroup {
                    AppCell(
                        title = "清空后导入",
                        rightIcon = { VanSwitch(checked = forceClear, onCheckedChange = { forceClear = it }, size = 20) },
                    )
                    AppCell(title = "导入前自动备份目标表", label = "启用后导入前会先备份目标表数据", border = false)
                }
                Spacer(Modifier.height(16.dp))

                VanNoticeBar(
                    text = "导入数据会修改数据库，请谨慎操作！",
                    color = DB_ORANGE,
                    background = Color.Transparent,
                    leftIcon = "warning-o",
                )
                Spacer(Modifier.height(16.dp))
            } else {
                // ===== SQL / ZIP =====
                DbGroupTitle("选择文件")
                DbCardGroup {
                    AppCell(title = "支持格式", label = ".sql 文件 或 .zip 压缩包（内含 .sql 文件）")
                    AppCell(title = "导入范围", value = "全库导入")
                    AppCell(title = "文件大小限制", value = "100MB", border = false)
                }
                Spacer(Modifier.height(12.dp))
                AppButton(
                    text = if (sqlFileName.isEmpty()) "选择 SQL / ZIP 文件" else sqlFileName,
                    icon = "plus",
                    onClick = { sqlPicker.launch(arrayOf("*/*")) },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Small,
                    block = true,
                )
                Spacer(Modifier.height(16.dp))

                if (sqlFileName.isNotEmpty()) {
                    DbGroupTitle("文件信息")
                    DbCardGroup {
                        AppCell(title = "文件名", value = sqlFileName)
                        AppCell(title = "文件大小", value = dbImportFileSize(sqlFileSize), border = false)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                VanNoticeBar(
                    text = "SQL / ZIP 导入会直接执行文件中的 SQL 语句（含 DDL+DML），执行前会自动全库备份。此操作不可逆，请谨慎操作！",
                    color = colors.danger,
                    background = Color.Transparent,
                    leftIcon = "warning-o",
                    wrapable = true,
                )
                Spacer(Modifier.height(16.dp))
            }

            // 导入按钮
            AppButton(
                text = if (format == "sql") "执行 SQL 导入" else "确认导入",
                onClick = {
                    if (format == "json") dialog = "jsonConfirm" else dialog = "sql1"
                },
                type = AppButtonType.Primary,
                size = AppButtonSize.Large,
                block = true,
                round = true,
                loading = loading,
                disabled = !canImport,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
            )

            if (importCompleted) {
                DbGroupTitle("导入结果")
                DbCardGroup {
                    AppCell(
                        title = "状态",
                        rightIcon = { VanTag(text = statusText, type = statusType.toVanTagTypeEx(), size = VanTagSize.Large) },
                    )
                    AppCell(title = "导入时间", value = importTime)

                    if (format == "json" && statusType == "success") {
                        AppCell(title = "导入记录数", value = "$importedCount 条")
                    }
                    if (format == "sql") {
                        val sr = sqlResult
                        val failed = sr?.errors.orEmpty()
                        AppCell(title = "执行语句", value = "${sr?.executedCount ?: 0} 条")
                        if ((sr?.errorCount ?: 0) > 0) {
                            AppCell(
                                title = "失败语句",
                                value = "${sr?.errorCount} 条",
                                valueColor = colors.danger,
                            )
                        }
                        if (failed.isNotEmpty()) {
                            AppCell(
                                title = "失败详情",
                                rightIcon = {
                                    Column(Modifier.width(200.dp)) {
                                        failed.forEach { e ->
                                            Column(Modifier.fillMaxWidth().border(1.dp, colors.border).padding(vertical = 4.dp)) {
                                                FText("第 ${e.index} 条: ${e.message}", 11f, FontWeight.Normal, colors.danger)
                                                FText(e.sql, 10f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 2.dp))
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }

                    AppCell(
                        title = "备份文件",
                        border = backupTime.isEmpty(),
                        rightIcon = {
                            BasicText(
                                backupFile,
                                modifier = Modifier.width(160.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = colors.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            )
                        },
                    )
                    if (backupTime.isNotEmpty()) {
                        AppCell(title = "备份时间", value = backupTime, border = false)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ===== 确认弹窗（JSON 一层；SQL 两层，文案与按钮逐字照抄 web）=====
    VanConfirmDialog(
        show = dialog == "jsonConfirm",
        title = "确认导入",
        message = if (forceClear) {
            "确定要清空 $selectedTable 表后导入 ${jsonRows.size} 条数据吗？"
        } else {
            "确定要向 $selectedTable 表导入 ${jsonRows.size} 条数据吗？"
        },
        onConfirm = { dialog = ""; runJsonImport() },
        onCancel = { dialog = "" },
        onClose = { dialog = "" },
    )
    VanConfirmDialog(
        show = dialog == "sql1",
        title = "⚠️ 确认 SQL 导入",
        message = "即将执行文件「$sqlFileName」中的 SQL 语句（${dbImportFileSize(sqlFileSize)}）。\n\n" +
            "执行前会自动全库备份，但此操作不可逆，请确认文件来源可靠。",
        confirmButtonText = "我已备份，继续",
        onConfirm = { dialog = "sql2" },
        onCancel = { dialog = "" },
        onClose = { dialog = "" },
    )
    VanConfirmDialog(
        show = dialog == "sql2",
        title = "⚠️ 二次确认",
        message = "SQL 将直接修改数据库（包含建表、插数据等）。若执行出错，可使用备份文件恢复。确定继续？",
        confirmButtonText = "确定执行",
        onConfirm = { dialog = ""; runSqlImport() },
        onCancel = { dialog = "" },
        onClose = { dialog = "" },
    )
}

/** web `formatVal`：对象 → JSON 字符串、null → `null`、其余 `String(v)`。 */
private fun jsonValueText(v: JsonElement?): String = when {
    v == null || v.isJsonNull -> "null"
    v.isJsonPrimitive -> v.asString
    else -> v.toString()
}

/** web `formatFileSize`（导入页口径：1024 进制 1 位小数）。 */
private fun dbImportFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var i = 0
    while (size >= 1024 && i < units.lastIndex) { size /= 1024; i++ }
    return "%.1f %s".format(size, units[i])
}

/** SAF Uri → 显示名（Android 无内置取法，需查 `OpenableColumns.DISPLAY_NAME`）。 */
private fun uriDisplayName(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment.orEmpty()

private fun String.toVanTagTypeEx(): VanTagType = when (this) {
    "success" -> VanTagType.Success
    "warning" -> VanTagType.Warning
    "danger" -> VanTagType.Danger
    "primary" -> VanTagType.Primary
    else -> VanTagType.Default
}
