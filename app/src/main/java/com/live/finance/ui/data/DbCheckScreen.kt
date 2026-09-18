package com.live.finance.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.DbTableStatus
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据库检查 —— 一比一复刻 web `views/Finance/data/DbCheck.vue`。
 *
 * 结构：头部卡（`passed` 48 主色 + 标题 20/bold + 副标题 14 tertiary，居中、上下 30、圆角 16）→
 * 「开始检查」大圆角满宽主色按钮 → 检查结果卡（4 行：连接状态 / 数据完整性[`app-tag size=large`] /
 * 总行数（**千分位** + ` 行`）/ 检查时间）→ 「数据库表状态」折叠面板（**默认展开**，横向可滚动的 5 列表格：
 * 表名[等宽 11px] 引擎 行数[右对齐] 大小[右对齐] 备注[tertiary]，表头底 `bg-tertiary`，单元格四边 1px 线）→
 * 「检查说明」卡（两行带 label 的说明）。
 *
 * 行为（照抄）：点按钮后**并发**取 `GET /tables` + `GET /status`；
 * 状态列表非空 → 连接`正常`/完整性`完整 (N张表)`（success），为空 → `无数据`/`空库`（warning）；
 * 抛错 → `失败`/`异常`（danger）并把错误文案 toast 出来。
 * ⚠ 该接口**仅管理员可用**，普通账号会拿到 403「无权限访问数据管理功能」→ 会走 danger 分支如实提示。
 */
@Composable
fun DbCheckScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var checkCompleted by remember { mutableStateOf(false) }
    var checkTime by remember { mutableStateOf("") }
    var connText by remember { mutableStateOf("") }
    var connType by remember { mutableStateOf("") }
    var integrityText by remember { mutableStateOf("") }
    var integrityType by remember { mutableStateOf("") }
    var totalRows by remember { mutableStateOf(0L) }
    var tables by remember { mutableStateOf<List<DbTableStatus>>(emptyList()) }
    var expanded by remember { mutableStateOf(true) }   // van-collapse v-model 默认 ["tables"]

    fun handleCheck() {
        loading = true
        checkCompleted = false
        scope.launch {
            val listRes = graph.data.tables()
            val statusRes = graph.data.tableStatus()
            val list = (listRes as? ApiResult.Ok)?.data.orEmpty()
            val status = (statusRes as? ApiResult.Ok)?.data.orEmpty()
            val err = (statusRes as? ApiResult.Fail)?.message ?: (listRes as? ApiResult.Fail)?.message

            if (err != null && status.isEmpty()) {
                // web catch 分支：失败 / 异常 + toast 错误文案
                connType = "danger"; connText = "失败"
                integrityType = "danger"; integrityText = "异常"
                checkCompleted = true
                toast.show(err)
            } else {
                tables = status
                checkTime = SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.US).format(Date())
                totalRows = status.sumOf { it.rowCount }
                if (status.isNotEmpty()) {
                    connType = "success"; connText = "正常"
                    integrityType = "success"; integrityText = "完整 (${status.size}张表)"
                } else {
                    connType = "warning"; connText = "无数据"
                    integrityType = "warning"; integrityText = "空库"
                }
                // `list` 仅用于 count 兜底（web `list.length || status.length`），页面表格用 status
                if (list.isEmpty() && status.isEmpty()) {
                    integrityText = "空库"
                }
                checkCompleted = true
                toast.success("检查完成")
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
            // .page-header{center; padding 30/0; bg secondary; radius 16; mb 20}
            Column(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.bgCard)
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VanIcon(name = "passed", size = 48.sp, color = colors.primary)
                    Spacer(Modifier.height(12.dp))
                    FText("数据库检查", 20f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
                    FText("检查数据库连接状态和数据完整性", 14f, FontWeight.Normal, colors.textTertiary)
                }
            }

            // .action-section{margin-bottom 20}
            AppButton(
                text = "开始检查",
                onClick = { handleCheck() },
                type = AppButtonType.Primary,
                size = AppButtonSize.Large,
                block = true,
                round = true,
                loading = loading,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            if (checkCompleted) {
                DbSectionTitle("检查结果")
                CardGroup {
                    AppCell(
                        title = "连接状态",
                        rightIcon = { VanTag(text = connText, type = connType.toVanTagType(), size = VanTagSize.Large) },
                    )
                    AppCell(
                        title = "数据完整性",
                        rightIcon = { VanTag(text = integrityText, type = integrityType.toVanTagType(), size = VanTagSize.Large) },
                    )
                    AppCell(title = "总行数", value = "%,d 行".format(totalRows))
                    AppCell(title = "检查时间", value = checkTime, border = false)
                }
                Spacer(Modifier.height(20.dp))
            }

            if (tables.isNotEmpty()) {
                // 「数据库表状态」折叠面板（默认展开）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgCard),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VanIcon(name = "records-o", size = 16.sp, color = colors.textPrimary, modifier = Modifier.padding(end = 10.dp))
                        FText("数据库表状态", 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
                        VanIcon(
                            name = "arrow", size = 16.sp, color = colors.textTertiary,
                            modifier = Modifier.rotate(if (expanded) 90f else 0f),
                        )
                    }
                    if (expanded) {
                        DbTableGrid(tables)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            DbSectionTitle("检查说明", topSpacingZero = true)
            CardGroup {
                AppCell(title = "连接状态", label = "验证数据库服务器是否可达")
                AppCell(title = "数据完整性", label = "检查数据表是否存在且可正常读写", border = false)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** `.section-title{padding 8/16; 13px; tertiary; weight 500; mb 8}`。 */
@Composable
private fun DbSectionTitle(text: String, topSpacingZero: Boolean = false) {
    val colors = LocalAppColors.current
    FText(
        text, 13f, FontWeight.Medium, colors.textTertiary,
        Modifier.padding(start = 16.dp, end = 16.dp, top = if (topSpacingZero) 0.dp else 8.dp, bottom = 8.dp),
    )
}

/** `van-cell-group inset .app-card`：圆角 **12**（本页 `.app-card` 覆写）、左右内缩 16、overflow hidden。 */
@Composable
private fun CardGroup(content: @Composable () -> Unit) {
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
 * 表状态表格（web `.db-table`：`min-width:480px` + 横向滚动；表头 `bg-tertiary`；
 * 单元格 `border:1px`、`padding 10/8`（表头）/`9/8`（数据）；表名等宽 11px；行数·大小右对齐；备注 tertiary 11px）。
 *
 * ⚠ 列宽：web 是「固定 px + 表格 min-width 480」的 CSS 表格，实际会被拉到 480 并按比例分配 ——
 *   原生按 90:52:48:56:80 的比例在 480dp 总宽内还原。
 */
@Composable
private fun DbTableGrid(tables: List<DbTableStatus>) {
    val colors = LocalAppColors.current
    val totalW = 480.dp
    val ratios = listOf(90f, 52f, 48f, 56f, 80f)
    val sum = ratios.sum()
    val widths = ratios.map { (totalW.value * it / sum).dp }

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(totalW)) {
            // thead
            Row(Modifier.fillMaxWidth().background(colors.bgThird)) {
                listOf("表名", "引擎", "行数", "大小", "备注").forEachIndexed { i, h ->
                    TableCell(
                        text = h, width = widths[i], header = true,
                        align = if (i == 2 || i == 3) TextAlign.End else TextAlign.Start,
                        mono = i == 0,
                    )
                }
            }
            tables.forEach { t ->
                Row(Modifier.fillMaxWidth()) {
                    TableCell(t.name, widths[0], mono = true)
                    TableCell(t.engine, widths[1])
                    TableCell("%,d".format(t.rowCount), widths[2], align = TextAlign.End)
                    TableCell(formatSize(t.dataLength), widths[3], align = TextAlign.End)
                    TableCell(t.comment.ifBlank { "-" }, widths[4], dim = true)
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    header: Boolean = false,
    align: TextAlign = TextAlign.Start,
    mono: Boolean = false,
    dim: Boolean = false,
) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .width(width)
            .border(1.dp, colors.border)
            .padding(horizontal = 8.dp, vertical = if (header) 10.dp else 9.dp),
    ) {
        BasicText(
            text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = when {
                    header -> colors.textPrimary
                    dim -> colors.textTertiary
                    else -> colors.textSecondary
                },
                fontSize = if (mono) 11.sp else 12.sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                textAlign = align,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** web `formatSize`：1024 进制、1 位小数；0/空 → `0 B`。 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var i = 0
    while (size >= 1024 && i < units.lastIndex) {
        size /= 1024
        i++
    }
    return "%.1f %s".format(size, units[i])
}

/** web `app-tag` 的 type → [VanTagType]。 */
private fun String.toVanTagType(): VanTagType = when (this) {
    "success" -> VanTagType.Success
    "warning" -> VanTagType.Warning
    "danger" -> VanTagType.Danger
    "primary" -> VanTagType.Primary
    else -> VanTagType.Default
}
