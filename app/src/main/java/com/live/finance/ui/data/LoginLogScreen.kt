package com.live.finance.ui.data

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.TimeFmt
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.LoginLog
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.icon.VanIcon

/**
 * 登录日志 —— 一比一复刻 web `views/Finance/data/LoginLog.vue`。
 *
 * 结构：页内容 `padding 12/16/30` → 列表头（`近期登录记录` 15/600 + `app-tag plain small` `N 条`）→
 * 加载态（spinner 24 + 文案，上下留 60）→ 空态（`van-empty 暂无登录记录`）→ 日志卡列表（gap 8）：
 * 卡片 = 白卡 radius 8 + `padding 10/12` + **左侧 3px 状态色竖条**（成功绿 `#07c160` / 失败红 `#ee0a24`）；
 * 卡内 = 头行（类型徽章 + 可疑标签 … 右侧时间 `yyyy-MM-dd HH:mm`）+ 正文（IP · 位置 · 运营商 / 系统 · 浏览器 · 设备 /
 * User-Agent 两行截断 / 失败原因行）。
 *
 * ⚠ 两处「照抄 web 实际渲染」：
 *  1. 设备行图标 web 写的是 `computer-o`，而 **Vant 4.9.22 图标字体里没有该图标**（已正则遍历 `vant/lib/index.css` 证实）
 *     → 实际渲染**无图标**（只留 `margin-right:3px` 的空位），原生同样不画图标；
 *  2. 失败原因行背景是 `var(--van-danger-bg)`，该变量**未定义**且无 fallback → 声明无效 → **背景透明**，原生不画底。
 *
 * 可疑标记算法见 [markSuspicious]（异地/跨境/2 小时跨省频切；「新设备」分支因后端不返回 fingerprint 而恒不触发）。
 */
@Composable
fun LoginLogScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.loginLog
    var rows by remember { mutableStateOf<List<LoginLog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data.orEmpty(); else -> emptyList() }
        loading = false
    }
    val suspicious = remember(rows) { markSuspicious(rows) }

    ScreenScaffold { inner ->
        Column(
            inner
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
        ) {
            // .list-header{justify space-between; mb 12}
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText("近期登录记录", 15f, FontWeight.SemiBold, colors.textPrimary)
                VanTag(text = "${rows.size} 条", plain = true, size = VanTagSize.Small)
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    VanLoading(size = 24.sp, text = "加载中...")
                }

                rows.isEmpty() -> VanEmpty(description = "暂无登录记录")

                else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { log -> LogCard(log, suspicious[log.id]) }
                }
            }
        }
    }
}

// 类型徽章底色（web：`.log-type.type-*`，其中 logout 是 `var(--van-gray,#969799)` 的 fallback）
private val TYPE_LOGIN_BG = Color(0xFF07C160)
private val TYPE_LOGOUT_BG = Color(0xFF969799)

@Composable
private fun LogCard(log: LoginLog, suspicion: Suspicion?) {
    val colors = LocalAppColors.current
    val accent = if (log.failed) colors.danger else TYPE_LOGIN_BG
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgCard)
            // `border-left: 3px solid`（在圆角内绘制）
            .drawBehind { drawRect(color = accent, size = Size(3.dp.toPx(), size.height)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            // .log-header{margin-bottom 6}
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TypeBadge(log)
                    if (suspicion != null) {
                        VanTag(
                            text = suspicionLabel(suspicion),
                            type = suspicionTagType(suspicion).toVanTagType(),
                            size = VanTagSize.Small,
                            plain = true,
                        )
                    }
                }
                FText(TimeFmt.dateTime(log.timeValue), 11f, FontWeight.Normal, colors.textTertiary)
            }

            // .log-body{font-size 12; line-height 1.6}
            // 第一行：IP · 位置 ·（运营商，非"未知"才显示）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                VanIcon(name = "location-o", size = 12.sp, color = colors.textTertiary, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(3.dp))
                BasicText(
                    buildAnnotatedString {
                        append(log.loginIp.ifBlank { "未知IP" })
                        withStyle(SpanStyle(color = colors.textTertiary)) { append(" · ") }
                        append(log.loginLocation.ifBlank { "未知位置" })
                        if (log.loginIsp.isNotEmpty() && log.loginIsp != "未知") {
                            withStyle(SpanStyle(color = colors.textTertiary)) { append(" · ") }
                            withStyle(SpanStyle(color = colors.textTertiary)) { append(log.loginIsp) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, lineHeight = 19.2.sp),
                )
            }

            // 第二行：系统 · 浏览器 · 设备（web 无图标：`computer-o` 不存在）
            if (log.osInfo.isNotEmpty() || log.deviceModel.isNotEmpty() || log.browserInfo.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
                    Spacer(Modifier.width(15.dp))   // 对齐上一行的图标 + 间距占位
                    BasicText(
                        buildAnnotatedString {
                            append(log.osInfo)
                            if (log.browserInfo.isNotEmpty()) {
                                withStyle(SpanStyle(color = colors.textTertiary)) { append(" · ") }
                                append(log.browserInfo)
                            }
                            if (log.deviceModel.isNotEmpty()) {
                                withStyle(SpanStyle(color = colors.textTertiary)) { append(" · ") }
                                withStyle(SpanStyle(color = colors.primary)) { append(log.deviceModel) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, lineHeight = 19.2.sp),
                    )
                }
            }

            // User-Agent：10px、两行截断
            if (log.userAgent.isNotEmpty()) {
                BasicText(
                    log.userAgent,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp),
                )
            }

            // 失败原因（背景透明：web 的 `--van-danger-bg` 未定义）
            if (log.failed && log.errorMessage.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VanIcon(name = "warning-o", size = 12.sp, color = colors.danger)
                    FText(log.errorMessage, 11f, FontWeight.Normal, colors.danger)
                }
            }
        }
    }
}

/** `.log-type`：11px/600、`padding 1px 8px`、radius 3、白字、底色按类型。 */
@Composable
private fun TypeBadge(log: LoginLog) {
    val colors = LocalAppColors.current
    val bg = when (log.type) {
        "logout" -> TYPE_LOGOUT_BG
        "refresh" -> colors.primary
        "failed" -> colors.danger
        else -> TYPE_LOGIN_BG
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        FText(log.typeLabel, 11f, FontWeight.SemiBold, Color.White)
    }
}

/** web `suspiciousType` → [VanTagType]。 */
private fun String.toVanTagType(): VanTagType = when (this) {
    "danger" -> VanTagType.Danger
    "warning" -> VanTagType.Warning
    "success" -> VanTagType.Success
    "primary" -> VanTagType.Primary
    else -> VanTagType.Default
}
