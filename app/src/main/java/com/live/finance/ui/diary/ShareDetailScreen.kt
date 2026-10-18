package com.live.finance.ui.diary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.data.model.MomentShareDetail
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.VanImagePreview
import com.live.vant.form.VanKeyboardTheme
import com.live.vant.form.VanNumberKeyboard
import com.live.vant.form.VanPasswordInput
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * 公开分享页 —— 一比一复刻 web `views/Diary/ShareDetail.vue`（挂 `Routes.DIARY_SHARE`）。
 *
 * 与 web 的逐项对照：
 *  - 两种模式由 query 决定：`?token=` → token 模式；`?id=` → 密码模式；都没有 → 空态「链接无效，缺少参数」。
 *  - **无顶栏**（`RouteTitles.NO_BAR` 已含 `DIARY_SHARE`，与 web 一致：它不在 `MainLayout` 内）。
 *  - 空态按后端状态码分图：403/410 → `network`、404 → `search`、其他 → `error`；只有 catch 与其它错误可「重新获取」。
 *  - 密码模式：6 位数字键盘，点「完成」时不足 6 位 → 红框 + 「请输入完整密码」并重新聚焦；
 *    403 → 后端文案（含「还剩 N 次机会」）+ 清空；429 → 后端文案（含冷却秒数）+ 收起键盘。
 *  - 公开页面**不请求任何用户数据**：头像用本地灰底人形（web 是内联 SVG data URI，原生等价物）。
 *  - 429 防爆锁定：收起键盘 + 后端文案（本身已含「请 N 秒后再试」），**到点自动重新弹键盘**
 *    （web `setTimeout(() => pwFocused = true, (retryAfter || 120) * 1000)`）。
 *    冷却期间点卡片也能立刻唤起键盘 —— 这与 web 的 `@focus="pwFocused = true"` 一致，web 的锁同样是软的；
 *    真正的硬锁在后端（`PW_LIMIT.lockoutMs`），重复提交只会再吃一次 429。
 *
 * ⚠ 只补了「自动恢复」，**没有**加可见的倒计时秒数：web 没有这个元素，加它就不是 1:1 了。
 */
@Composable
fun ShareDetailScreen(
    token: String,
    id: String,
    onBack: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val scope = rememberCoroutineScope()

    val mode = when {
        token.isNotBlank() -> "token"
        id.isNotBlank() -> "password"
        else -> null
    }

    var loading by remember { mutableStateOf(mode == "token") }
    var data by remember { mutableStateOf<MomentShareDetail?>(null) }
    var errorImage by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var canRetry by remember { mutableStateOf(false) }

    // 密码模式
    var password by remember { mutableStateOf("") }
    var pwFocused by remember { mutableStateOf(true) }
    var pwError by remember { mutableStateOf("") }

    // 图片预览
    var showPreview by remember { mutableStateOf(false) }
    var previewIdx by remember { mutableStateOf(0) }

    /** 按后端状态码落到对应的空态（与 web `loadShare` 的分支逐条对应）。 */
    fun applyError(status: Int, message: String) {
        when (status) {
            403, 410 -> {
                errorImage = "network"
                errorMsg = message.ifBlank { "分享链接已过期" }
                canRetry = false
            }
            404 -> {
                errorImage = "search"
                errorMsg = "内容不存在或已删除"
                canRetry = false
            }
            else -> {
                errorImage = if (status < 0) "network" else "error"
                errorMsg = message.ifBlank { if (status < 0) "网络异常，请重试" else "加载失败" }
                canRetry = true
            }
        }
    }

    suspend fun loadShare() {
        if (token.isBlank()) {
            errorImage = "error"; errorMsg = "链接无效"; canRetry = false
            return
        }
        loading = true
        errorImage = ""
        val r = graph.moment.viewShare(token)
        if (r.isOk && r.detail != null) data = r.detail else applyError(r.status, r.message)
        loading = false
    }

    suspend fun submitPassword() {
        val r = graph.moment.verifySharePassword(id, password)
        when {
            r.isOk && r.detail != null -> {
                pwError = ""
                data = r.detail
            }
            r.status == 429 -> {
                pwError = r.message.ifBlank { "尝试次数过多，请稍后再试" }
                pwFocused = false
                // web：`setTimeout(() => { pwFocused = true }, (json.retryAfter || 120) * 1000)`
                // —— 冷却到点自动重新弹键盘。这里用 delay 等价实现（离开页面时随 scope 取消，比 web 的游离 timer 更干净）。
                delay((r.retryAfterSec.takeIf { it > 0 } ?: 120) * 1000L)
                pwFocused = true
            }
            r.status == 403 -> {
                pwError = r.message.ifBlank { "密码错误" }
                password = ""
            }
            else -> applyError(r.status, r.message)
        }
    }

    LaunchedEffect(Unit) {
        when (mode) {
            "token" -> loadShare()
            "password" -> Unit
            else -> {
                errorImage = "error"
                errorMsg = "链接无效，缺少参数"
                canRetry = false
            }
        }
    }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            when {
                // 1. token 模式加载中
                mode == "token" && loading -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 120.dp),
                    contentAlignment = Alignment.Center,
                ) { VanLoading() }

                // 2. 错误空态
                errorImage.isNotEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(top = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VanEmpty(
                        image = errorImage,
                        description = errorMsg,
                        bottomContent = {
                            if (canRetry) {
                                Spacer(Modifier.height(8.dp))
                                VanButton(
                                    text = "重新获取",
                                    round = true,
                                    onClick = {
                                        errorImage = ""
                                        if (mode == "token") {
                                            loading = true
                                        }
                                    },
                                )
                            }
                        },
                    )
                }

                // 3. 密码输入
                mode == "password" && data == null -> Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.bgCard)
                                // 429 收起键盘后，点卡片可立刻唤起（web 的 `@focus="pwFocused = true"` 同样能立刻唤起）
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) { pwFocused = true }
                                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            VanIcon(name = "lock", size = 40.sp, color = colors.primary)
                            Spacer(Modifier.height(12.dp))
                            FText("需要访问密码", 18f, FontWeight.Bold, colors.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            FText(
                                "请输入分享者提供的 6 位数字密码", 14f,
                                FontWeight.Normal, colors.textTertiary,
                                modifier = Modifier.padding(bottom = 24.dp),
                            )
                            VanPasswordInput(
                                value = password,
                                length = 6,
                                focused = pwFocused,
                                gutter = 16.dp,
                                errorInfo = pwError.ifEmpty { null },
                            )
                        }
                    }
                    // 与 web 一致：**点「完成」才提交**（输满 6 位不自动提交，用户仍可改/删）。
                    // 429 时键盘收起，冷却到点由 `submitPassword` 里的 delay 自动恢复；期间点卡片也能立刻唤起。
                    VanNumberKeyboard(
                        show = pwFocused,
                        text = password,
                        onTextChange = { if (it.length <= 6) { password = it; pwError = "" } },
                        theme = VanKeyboardTheme.Custom,
                        extraKey = listOf("."),
                        maxLength = 6,
                        closeButtonText = "完成",
                        onClose = {
                            if (password.length < 6) {
                                pwError = "请输入完整密码"
                                pwFocused = true
                            } else {
                                pwError = ""
                                scope.launch { submitPassword() }
                            }
                        },
                        onBlur = { pwFocused = false },
                    )
                }

                // 4. 内容
                data != null -> ShareContent(
                    detail = data!!,
                    onPreview = { i -> previewIdx = i; showPreview = true },
                )
            }

            VanImagePreview(
                show = showPreview,
                images = data?.images?.map { AppConfig.fullFileUrl(it.url) } ?: emptyList(),
                onClose = { showPreview = false },
                startPosition = previewIdx,
                closeable = true,
            )
        }
    }
}

/** 内容区（web `.content-box`）：头像/作者/时间/mood + 正文 + 3 列图片 + 位置 + 过期提示。 */
@Composable
private fun ShareContent(detail: MomentShareDetail, onPreview: (Int) -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgCard)
            .verticalScroll(rememberScrollState()),
    ) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 公开页不请求用户数据 → 本地灰底人形（web 的内联 SVG 默认头像的等价物）
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.bgThird),
                contentAlignment = Alignment.Center,
            ) {
                VanIcon(name = "user-o", size = 20.sp, color = colors.textTertiary)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                FText(detail.authorName, 16f, FontWeight.SemiBold, colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                FText(formatShareTime(detail.createTime), 12f, FontWeight.Normal, colors.textTertiary)
            }
            if (detail.mood.isNotBlank()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(colors.primaryLight)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    VanIcon(name = "smile-o", size = 12.sp, color = colors.primary)
                    FText(detail.mood, 12f, FontWeight.Normal, colors.primary)
                }
            }
        }

        // 正文（web `.body-text{font-size:17px;line-height:1.7}`）
        RichContent(
            html = detail.content,
            baseSizeSp = 17f,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )

        // 图片九宫格（web `grid-template-columns: 1fr 1fr 1fr; gap:4px`）
        if (detail.images.isNotEmpty()) {
            val rows = detail.images.chunked(3)
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { i, _ ->
                            val index = detail.images.indexOf(row[i])
                            VanImage(
                                src = AppConfig.fullFileUrl(row[i].url),
                                fit = ContentScale.Crop,
                                radius = 8.dp,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                onClick = { onPreview(index) },
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // 位置
        if (detail.locationName.isNotBlank()) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VanIcon(name = "location-o", size = 14.sp, color = colors.textTertiary)
                FText(detail.locationName, 14f, FontWeight.Normal, colors.textTertiary)
            }
        }

        // 过期提示（web `.footer`）
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            FText("分享链接 ${expireText(detail.expireAt)}", 12f, FontWeight.Normal, colors.textTertiary)
        }
    }
}

/** web `formatTime`：`YYYY-MM-DD HH:mm`（create_time 是毫秒时间戳字符串）。 */
private fun formatShareTime(ts: String): String {
    val ms = ts.toLongOrNull() ?: return ""
    val c = Calendar.getInstance().apply { time = Date(ms) }
    val mm = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val dd = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mi = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "${c.get(Calendar.YEAR)}-$mm-$dd $hh:$mi"
}

/** web `expireText`：长期有效 / 已过期 / N天后过期 / N小时后过期 / N分钟后过期。 */
private fun expireText(expireAt: String): String {
    if (expireAt.isBlank()) return "长期有效"
    val target = runCatching {
        java.time.Instant.parse(expireAt).toEpochMilli()
    }.getOrNull() ?: return "长期有效"
    val diff = target - System.currentTimeMillis()
    if (diff <= 0) return "已过期"
    val hours = diff / 3_600_000
    val mins = (diff % 3_600_000) / 60_000
    return when {
        hours > 24 -> "${hours / 24}天后过期"
        hours > 0 -> "${hours}小时后过期"
        else -> "${mins}分钟后过期"
    }
}
