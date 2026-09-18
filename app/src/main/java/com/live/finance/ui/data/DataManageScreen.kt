package com.live.finance.ui.data

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon

/**
 * 数据管理首页 —— 一比一复刻 web `views/Finance/data/DataManage.vue`。
 *
 * 只有一个区块「数据管理功能」+ 一张菜单卡，5 个入口（图标/标题/描述/颜色逐项照抄）：
 * 数据库检查(`search`, 主色) · 导出数据库/表(`notes-o`, #07c160) · 数据导入(`add-o`, #fa8c16) ·
 * 数据库备份(`records`, #07c160) · 查看登录日志(`manager-o`, #ee0a24)。
 *
 * ⚠ web 的两处「照抄实际渲染」：
 *  1. `.menu-item{border:1px solid var(--theme-border)}` 写的是**四边**（不是 border-bottom），
 *     只有 `:last-child` 去掉 bottom → 相邻两项之间是**两条 1px 线**（≈2px 粗），首项还有上边线；原生按此绘制。
 *  2. `.menu-icon` 只设了 `color` **没有背景色** → 那是个 40×40 的**透明**占位盒（不是彩色底块），图标 20px 居中。
 *  3. 右侧箭头的颜色是 `color="#c8c9cc"` **硬编码**（不走主题），深色下也是这个灰。
 *
 * ⚠ 后端整模块是**管理员专属**（`api/src/modules/dataManager/api/index.js: router.use(authGuard, requireAdmin)`），
 *   非 admin 账号进各子页会拿到 403「无权限访问数据管理功能」——子页需如实提示，不要伪装成"加载失败"。
 *
 * 未做（记档）：web 的「查看登录日志」入口带 `v-if="!isDemo"`（仅 VITE_APP_DEMO 时隐藏）——原生无该开关，恒显示。
 */
@Composable
fun DataManageScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val entries = listOf(
        DataEntry("数据库检查", "检查数据库连接状态和完整性", "search", colors.primary, Routes.DATA_CHECK),
        DataEntry("导出数据库/表", "导出数据库或指定表数据", "notes-o", DEEP_GREEN, Routes.DATA_EXPORT),
        DataEntry("数据导入", "从文件导入数据到数据库", "add-o", ORANGE, Routes.DATA_IMPORT),
        DataEntry("数据库备份", "查看和管理已导出的备份文件", "records", DEEP_GREEN, Routes.DATA_BACKUP),
        DataEntry("查看登录日志", "近期登录记录和设备信息", "manager-o", colors.danger, Routes.DATA_LOGIN_LOG),
    )

    ScreenScaffold { inner ->
        Column(
            inner
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
        ) {
            // .section-title{font-size 15; weight 600; primary; mb 10; padding-left 2}
            FText("数据管理功能", 15f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(start = 2.dp, bottom = 10.dp))
            // .menu-card{bg secondary; radius 10; overflow hidden}
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgCard),
            ) {
                entries.forEachIndexed { i, e ->
                    MenuItem(e, isLast = i == entries.lastIndex) { nav.navigate(e.route) }
                }
            }
        }
    }
}

private val DEEP_GREEN = Color(0xFF07C160)     // web `var(--van-green, #07c160)`（Vant 调色板色，非主题色）
private val ORANGE = Color(0xFFFA8C16)         // web 硬编码 `#fa8c16`
private val ARROW_GREY = Color(0xFFC8C9CC)     // web `<van-icon color="#c8c9cc">` 硬编码

private data class DataEntry(
    val title: String,
    val desc: String,
    val icon: String,
    val color: Color,
    val route: String,
)

@Composable
private fun MenuItem(e: DataEntry, isLast: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // web：每项四边 1px 描边，仅末项去掉 bottom（故相邻两项间是 2 条 1px 线）
            .drawBehind {
                val w = 1.dp.toPx()
                val line = colors.border
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), w)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), w)
                drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), w)
                if (!isLast) drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), w)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // `.menu-icon`：40×40 圆角 10 + 20px 彩色图标（**无背景色**）
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            VanIcon(name = e.icon, size = 20.sp, color = e.color)
        }
        Column(Modifier.weight(1f)) {
            FText(e.title, 14f, FontWeight.Medium, colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            FText(e.desc, 11f, FontWeight.Normal, colors.textTertiary)
        }
        Spacer(Modifier.width(0.dp))
        VanIcon(name = "arrow", size = 16.sp, color = ARROW_GREY)
    }
}
