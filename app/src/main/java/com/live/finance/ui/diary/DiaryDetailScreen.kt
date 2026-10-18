package com.live.finance.ui.diary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.Moment
import com.live.finance.data.model.User
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanGreen
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanActionSheet
import com.live.vant.feedback.VanAlertDialog
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanImagePreview
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanSwitch
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/** 分享链接的站点域名。
 *
 * 取值来自 [AppConfig.siteUrl]，对齐 web `src/utils/env.js` 的 `SITE_URL`。
 * 🚫 2026-10-17：不再有 `http://localhost` 兜底 —— 值只来自「设置 → 分享 URL」的本机保存项，
 *   未填写时为空串；分享入口在点击时已用 `SiteStore.shareUrl() == null` 阻断。
 *   公网分发应引导用户填 **https** 地址。安全整改 P2-21。 */
private val SITE_URL: String get() = AppConfig.siteUrl

/** web `Diary/Detail.vue:759/896` `.delete-btn`：
 *  `color: var(--van-danger-color)` —— **`--van-danger-color` 在 `:root`** → 两模式 = --theme-danger，用 `colors.danger`。
 *  `background: var(--van-danger-bg, #fff0f0)` —— 深色 #fff0f0、浅色 fallback 也正好是 #fff0f0 → 两模式同值。 */
private val VAN_DANGER_BG = Color(0xFFFFF0F0)


private val DURATION_OPTIONS = listOf(1 to "1 小时", 6 to "6 小时", 12 to "12 小时", 24 to "24 小时", 48 to "48 小时", 72 to "72 小时")

/**
 * 动态详情 —— 一比一复刻 web `views/Diary/Detail.vue`。
 *
 * 结构：主贴（作者头 / 正文 / 九宫格图 / 底部位置 + 分享/删除）→ 追文时间轴（点 + 竖线）→ 发布浮钮；
 * 交互：删除二次确认（10 秒倒计时才可点确认，web `app-dialog` 的 confirm-button-disabled）→
 *      分享弹窗（开关 + 有效时间 + 已分享时展示密码与复制按钮）→ 分享结果弹窗 → 图片预览。
 *
 * ⚠ 正文/追文是后端 HTML，web 用 `v-html`；Compose 无等价渲染，这里降级为 [htmlToPlain] 纯文本。
 */
@Composable
fun DiaryDetailScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current

    var loading by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Moment?>(null) }
    var children by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var childrenLoading by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf<User?>(null) }

    // 删除确认（type = main/child；倒计时 10s 后才可确认）
    var showDelete by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf("") }
    var deleteMain by remember { mutableStateOf(true) }
    var deleteChildId by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(0) }

    // 图片预览
    var showPreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableStateOf(0) }

    // 分享
    var showShare by remember { mutableStateOf(false) }
    var showShareResult by remember { mutableStateOf(false) }
    var showShareMissing by remember { mutableStateOf(false) }
    var shareOn by remember { mutableStateOf(false) }
    var shareLoading by remember { mutableStateOf(false) }
    var shareDuration by remember { mutableStateOf(1) }
    var showDuration by remember { mutableStateOf(false) }
    var shareToken by remember { mutableStateOf("") }
    var sharePassword by remember { mutableStateOf("") }

    // 分享 URL（Web 端地址）本地存储：未填写时分享功能阻断（弹提示引导去设置）
    val siteStore = remember { com.live.finance.core.store.SiteStore(ctx) }

    val isShared = (detail?.vt ?: 0) == 1

    suspend fun loadChildren(ids: List<String>) {
        childrenLoading = true
        when (val r = graph.moment.batchDetail(ids)) {
            is ApiResult.Ok -> children = r.data.orEmpty().sortedBy { it.createTime }
            else -> children = emptyList()
        }
        childrenLoading = false
    }

    suspend fun loadDetail() {
        loading = true
        when (val r = graph.moment.getOne(id)) {
            is ApiResult.Ok -> {
                detail = r.data
                val ids = r.data?.childrenIds.orEmpty()
                if (ids.isNotEmpty()) loadChildren(ids) else children = emptyList()
            }
            else -> toast.showFail(r, "加载失败")
        }
        loading = false
    }

    LaunchedEffect(id) {
        (graph.auth.getUserinfo() as? ApiResult.Ok)?.data?.let { user = it }
        if (id.isBlank()) {
            toast.show("参数错误", VanToastType.Fail)
            nav.popBackStack()
        } else {
            loadDetail()
        }
    }

    // 删除确认倒计时（web startConfirmCountdown：10 → 0，每秒 -1）
    LaunchedEffect(showDelete) {
        if (showDelete) {
            countdown = 10
            while (countdown > 0) {
                delay(1000)
                countdown -= 1
            }
        }
    }

    fun copyToClipboard(text: String, msg: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("diary", text))
        toast.show(msg)
    }

    fun openDeleteDialog(isMain: Boolean, childId: String?, message: String) {
        deleteMain = isMain
        deleteChildId = childId
        deleteMessage = message
        showDelete = true
    }

    fun doDelete() {
        if (countdown > 0) return
        showDelete = false
        scope.launch {
            val targetId = if (deleteMain) detail?.id.orEmpty() else deleteChildId.orEmpty()
            when (val r = graph.moment.delete(targetId)) {
                is ApiResult.Ok -> {
                    toast.show("删除成功", VanToastType.Success)
                    if (deleteMain) {
                        nav.popBackStack()
                    } else {
                        detail?.let { d -> if (d.childrenIds.isNotEmpty()) loadChildren(d.childrenIds) }
                    }
                }
                is ApiResult.Fail -> toast.show(r.message, VanToastType.Fail)
                else -> toast.showFail(r, "删除失败")
            }
        }
    }

    fun doOpenShare() {
        shareLoading = true
        scope.launch {
            when (val r = graph.moment.share(detail?.id.orEmpty(), "open", shareDuration)) {
                is ApiResult.Ok -> {
                    val s = r.data
                    if (s != null) {
                        shareToken = s.token
                        sharePassword = s.password
                        detail = detail?.copy(vt = 1, pw = s.password)
                        showShare = false
                        showShareResult = true
                    } else {
                        toast.show("开启分享失败", VanToastType.Fail)
                    }
                }
                is ApiResult.Fail -> toast.show("开启分享失败", VanToastType.Fail)
                else -> toast.showFail(r, "开启分享失败")
            }
            shareLoading = false
        }
    }

    fun doCloseShare() {
        shareLoading = true
        scope.launch {
            when (val r = graph.moment.share(detail?.id.orEmpty(), "close")) {
                is ApiResult.Ok -> {
                    detail = detail?.copy(vt = 0, pw = "")
                    showShare = false
                    toast.show("分享已关闭")
                }
                is ApiResult.Fail -> toast.show("关闭分享失败", VanToastType.Fail)
                else -> toast.showFail(r, "关闭分享失败")
            }
            shareLoading = false
        }
    }

    fun copyExistingLink(asToken: Boolean) {
        val d = detail ?: return
        if (asToken) {
            toast.show("正在生成链接...")
            scope.launch {
                when (val r = graph.moment.share(d.id, "token", 1)) {
                    is ApiResult.Ok -> {
                        val t = r.data?.token.orEmpty()
                        if (t.isNotEmpty()) {
                            copyToClipboard("$SITE_URL/share/diary/detail?token=$t", "公共链接已复制")
                        } else {
                            toast.show("生成失败", VanToastType.Fail)
                        }
                    }
                    else -> toast.showFail(r, "生成链接失败")
                }
            }
        } else {
            val pw = d.pw.ifBlank { "***" }
            copyToClipboard("链接：$SITE_URL/share/diary/detail?id=${d.id}\n密码：$pw", "密码链接+密码已复制")
        }
    }

    /** web `formatTime`：<1min 刚刚 / <1h N分钟前 / <24h N小时前 / 否则 M-D HH:mm。 */
    fun relativeTime(ts: String): String {
        val ms = ts.toLongOrNull() ?: return ""
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            else -> {
                val c = Calendar.getInstance().apply { time = Date(ms) }
                val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
                "${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)} $hh:$mm"
            }
        }
    }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    VanLoading()
                }

                detail == null -> VanEmpty(description = "内容不存在")

                else -> {
                    val d = detail!!
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp),
                    ) {
                        // ===== 主贴（.main-moment{bg-secondary; padding-bottom:10}）=====
                        Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(bottom = 10.dp)) {
                            // 作者头（padding 16 / gap 12）
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 后端 moment 行没有 author/avatar → 与 web 兜底链一致，落在登录用户
                                val avatar = user?.avatar.orEmpty()
                                if (avatar.isNotBlank()) {
                                    VanImage(src = AppConfig.fullFileUrl(avatar), width = 48.dp, height = 48.dp, round = true, showError = false)
                                } else {
                                    Box(Modifier.size(48.dp).background(colors.bgThird, CircleShape))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    FText(
                                        user?.username.orEmpty().ifBlank { "用户" },
                                        18f, FontWeight.SemiBold, colors.textPrimary,
                                    )
                                    FText(relativeTime(d.createTime), 12f, FontWeight.Normal, colors.textTertiary)
                                }
                                if (d.mood.isNotBlank()) {
                                    Row(
                                        Modifier
                                            .background(tokens.primaryLight, RoundedCornerShape(100.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        VanIcon(name = "smile-o", size = 12.sp, color = colors.primary)
                                        Spacer(Modifier.width(2.dp))
                                        FText(d.mood, 12f, FontWeight.Normal, colors.primary)
                                    }
                                }
                            }
                            // 正文（web `.content-text{padding:0 16 16; font-size:17px; line-height:1.6}`；`p` 间距 8）
                            // web 用 `v-html` 渲染 HTML → 原生走轻量富文本渲染器（加粗/引用/列表/代码块/分割线）
                            RichContent(
                                html = d.content,
                                baseSizeSp = 17f,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                paraGap = 8.dp,
                            )
                            // 图片九宫格（3 列 gap 4 / padding 0 16 16）
                            if (d.images.isNotEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                    d.images.chunked(3).forEach { row ->
                                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                            row.forEachIndexed { i, img ->
                                                Box(
                                                    Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            previewImages = d.images.map { AppConfig.fullFileUrl(it.url) }
                                                            previewIndex = d.images.indexOf(img).coerceAtLeast(0)
                                                            showPreview = true
                                                        },
                                                ) {
                                                    VanImage(
                                                        src = AppConfig.fullFileUrl(img.url),
                                                        modifier = Modifier.fillMaxSize(),
                                                        fit = ContentScale.Crop,
                                                        showError = false,
                                                    )
                                                }
                                                if (i != row.lastIndex) Spacer(Modifier.width(4.dp))
                                            }
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                            // 底部：位置 + 分享/删除（padding 0 16 16）
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (d.locationName.isNotBlank()) {
                                        VanIcon(name = "location-o", size = 12.sp, color = colors.textTertiary)
                                        Spacer(Modifier.width(2.dp))
                                        BasicText(
                                            d.locationName,
                                            maxLines = 1,
                                            style = TextStyle(color = colors.textTertiary, fontSize = scaledSp(12f)),
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // .share-btn
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.bgThird)
                                            .clickable {
                                                // 分享 URL（Web 端）未填写 ⇒ 阻断并引导去设置
                                                // （分享链接是网页链接，落地页只有 web 端有，2026-10-14）
                                                if (siteStore.shareUrl() == null) {
                                                    showShareMissing = true
                                                } else {
                                                    shareOn = isShared
                                                    shareDuration = 1
                                                    showShare = true
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        VanIcon(name = "share-o", size = 13.sp, color = colors.textSecondary)
                                        Spacer(Modifier.width(4.dp))
                                        FText(if (isShared) "关闭分享" else "分享", 14f, FontWeight.Normal, colors.textSecondary)
                                    }
                                    // .delete-btn
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VAN_DANGER_BG)
                                            .clickable { openDeleteDialog(true, null, "确定要删除这条动态吗？") }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        VanIcon(name = "delete-o", size = 13.sp, color = colors.danger)
                                        Spacer(Modifier.width(4.dp))
                                        FText("删除", 14f, FontWeight.Normal, colors.danger)
                                    }
                                }
                            }
                        }

                        // ===== 追文（.append-section{padding:20 16}）=====
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                FText("追文", 18f, FontWeight.SemiBold, colors.textPrimary)
                                FText("${children.size} 条今日追文", 12f, FontWeight.Normal, colors.textTertiary)
                            }
                            when {
                                childrenLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    VanLoading()
                                }

                                children.isEmpty() -> Column(
                                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    VanIcon(name = "edit", size = 36.sp, color = Color(0xFFE0E0E0))
                                    Spacer(Modifier.height(8.dp))
                                    FText("暂无补充内容", 16f, FontWeight.Normal, colors.textSecondary)
                                }

                                else -> children.forEachIndexed { idx, child ->
                                    Row(Modifier.fillMaxWidth()) {
                                        // 时间轴：点 + 竖线
                                        Column(
                                            Modifier.width(12.dp).padding(top = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            // .dot{8×8 主色 + 2px bg-secondary 描边 + 外圈 2px bg-tertiary}
                                            Box(
                                                Modifier.size(12.dp).background(colors.bgThird, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    Modifier
                                                        .size(8.dp)
                                                        .background(colors.primary, CircleShape)
                                                        .border(2.dp, colors.bgCard, CircleShape),
                                                )
                                            }
                                            if (idx != children.lastIndex) {
                                                Box(
                                                    Modifier
                                                        .width(2.dp)
                                                        .weight(1f)
                                                        .padding(top = 4.dp)
                                                        .background(colors.bgThird),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .padding(bottom = 10.dp)
                                                .background(colors.bgThird, RoundedCornerShape(12.dp))
                                                .padding(14.dp),
                                        ) {
                                            // 追文正文（web `.append-content{font-size:15px; line-height:1.6}`；`p` 间距 6）
                                            RichContent(
                                                html = child.content,
                                                baseSizeSp = 15f,
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                                paraGap = 6.dp,
                                            )
                                            if (child.images.isNotEmpty()) {
                                                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                                                    child.images.chunked(3).forEach { r2 ->
                                                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                                            r2.forEachIndexed { i, img ->
                                                                Box(
                                                                    Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f)
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                        .clickable {
                                                                            previewImages = child.images.map { AppConfig.fullFileUrl(it.url) }
                                                                            previewIndex = child.images.indexOf(img).coerceAtLeast(0)
                                                                            showPreview = true
                                                                        },
                                                                ) {
                                                                    VanImage(
                                                                        src = AppConfig.fullFileUrl(img.url),
                                                                        modifier = Modifier.fillMaxSize(),
                                                                        fit = ContentScale.Crop,
                                                                        showError = false,
                                                                    )
                                                                }
                                                                if (i != r2.lastIndex) Spacer(Modifier.width(4.dp))
                                                            }
                                                            repeat(3 - r2.size) { Spacer(Modifier.weight(1f)) }
                                                        }
                                                    }
                                                }
                                            }
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    FText(relativeTime(child.createTime), 12f, FontWeight.Normal, colors.textTertiary)
                                                    if (child.locationName.isNotBlank()) {
                                                        Spacer(Modifier.width(4.dp))
                                                        FText("· ${child.locationName}", 12f, FontWeight.Normal, colors.textTertiary)
                                                    }
                                                }
                                                Row(
                                                    Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(VAN_DANGER_BG)
                                                        .clickable { openDeleteDialog(false, child.id, "确定要删除这条追文吗？") }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                ) { VanIcon(name = "delete-o", size = 12.sp, color = colors.danger) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 发布浮钮（.add-diary-btn：fixed 居中 / bottom 20 / 50×50 圆形主色）
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(50.dp)
                    .cssShadowCircle(WebShadow(0f, 4f, 12f, Color.Black.copy(alpha = 0.15f)))
                    .clip(CircleShape)
                    .background(tokens.primary)
                    .clickable { nav.navigate(com.live.finance.core.nav.Routes.DIARY_ADD) },
                contentAlignment = Alignment.Center,
            ) { VanIcon(name = "plus", size = 24.sp, color = Color.White) }
        }
    }

    // 说明：web 的 `.add-diary-btn` 是 `position:fixed; bottom:20px`，本屏是独立全屏路由，
    // 没有底部悬浮岛，故直接用 Box 的 bottom padding 定位（视口位置与 web 一致）。

    VanImagePreview(
        show = showPreview,
        images = previewImages,
        startPosition = previewIndex,
        closeable = true,
        onClose = { showPreview = false },
    )

    // 删除确认（web app-dialog：倒计时未结束时确认键禁用，文案「N秒后确认」）
    VanConfirmDialog(
        show = showDelete,
        title = "确认删除",
        message = deleteMessage,
        confirmButtonText = if (countdown > 0) "${countdown}秒后确认" else "确认",
        onConfirm = { doDelete() },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )

    // 分享 URL 未填写提示（分享链接是网页链接，需已部署的 Web 端才能打开）
    VanAlertDialog(
        show = showShareMissing,
        title = "无法分享",
        message = "请先在「我的 → 应用设置 → 基础API设定」中填写已部署的 Web 端分享 URL，才能使用分享功能。",
        confirmButtonText = "知道了",
        onConfirm = { showShareMissing = false },
        onClose = { showShareMissing = false },
    )

    // 分享弹窗（app-popup position=bottom round，padding 20 16）
    VanPopup(show = showShare, onDismissRequest = { showShare = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                FText(if (isShared) "管理分享" else "开启分享", 18f, FontWeight.SemiBold, colors.textPrimary)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText("分享状态", 16f, FontWeight.Normal, colors.textPrimary)
                VanSwitch(
                    checked = shareOn,
                    onCheckedChange = { shareOn = it },
                    size = 22, // size="22px"
                    activeColor = vanGreen(),
                )
            }
            // 分享依赖 Web 端提示（2026-10-14）：分享落地页只有 web 端有，外链是网页链接——
            // 接收方必须能访问「已部署的 Web 端」才能打开；没部署 web 则外链打不开。
            FText(
                "分享链接为网页链接：接收方需访问已部署的 Web 端才能打开；未部署 Web 端则外链无法打开。",
                12f, FontWeight.Normal, Color(0xFFFF976A),
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            )
            if (shareOn && !isShared) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border)
                        .clickable { showDuration = true }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FText("有效时间", 16f, FontWeight.Normal, colors.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FText(
                            DURATION_OPTIONS.firstOrNull { it.first == shareDuration }?.second ?: "1 小时",
                            16f, FontWeight.Normal, colors.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        VanIcon(name = "arrow", size = 12.sp, color = colors.primary)
                    }
                }
                FText(
                    "不选择默认 1 小时后过期",
                    12f, FontWeight.Normal, Color(0xFFFF976A),
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (isShared && shareOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FText("当前密码", 16f, FontWeight.Normal, colors.textPrimary)
                    FText(detail?.pw.orEmpty().ifBlank { "***" }, 18f, FontWeight.SemiBold, vanGreen())
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(
                        text = "密码访问（复制链接）",
                        onClick = { copyExistingLink(false) },
                        type = AppButtonType.Primary, size = AppButtonSize.Small,
                        plain = true, round = true, block = true,
                    )
                    AppButton(
                        text = "公共访问（复制链接）",
                        onClick = { copyExistingLink(true) },
                        type = AppButtonType.Primary, size = AppButtonSize.Small,
                        round = true, block = true,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = "取消",
                    onClick = { showShare = false },
                    type = AppButtonType.Default, round = true, block = true, plain = true,
                )
                if (shareOn && !isShared) {
                    AppButton(
                        text = "确认并开启分享",
                        onClick = { doOpenShare() },
                        type = AppButtonType.Primary, round = true, block = true, loading = shareLoading,
                    )
                }
                if (isShared && !shareOn) {
                    AppButton(
                        text = "确认关闭分享",
                        onClick = { doCloseShare() },
                        type = AppButtonType.Danger, round = true, block = true, loading = shareLoading,
                    )
                }
            }
        }
    }

    VanActionSheet(
        show = showDuration,
        onDismissRequest = { showDuration = false },
        actions = DURATION_OPTIONS.map { com.live.vant.feedback.VanAction(it.second) },
        onSelect = { _, index ->
            shareDuration = DURATION_OPTIONS[index].first
            showDuration = false
        },
    )

    // 分享结果弹窗
    VanPopup(show = showShareResult, onDismissRequest = { showShareResult = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VanIcon(name = "success", size = 40.sp, color = vanGreen())
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FText("分享已开启", 18f, FontWeight.SemiBold, colors.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                FText("密码：$sharePassword", 16f, FontWeight.Normal, colors.textSecondary)
            }
            // 同分享弹窗：外链依赖 Web 端部署（未部署则接收方打不开）
            FText(
                "外链需接收方访问已部署的 Web 端打开；未部署 Web 端则外链无法打开。",
                12f, FontWeight.Normal, Color(0xFFFF976A),
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    text = "密码访问（复制链接）",
                    onClick = { copyToClipboard("链接：$SITE_URL/share/diary/detail?id=${detail?.id.orEmpty()}\n密码：$sharePassword", "密码链接+密码已复制") },
                    type = AppButtonType.Primary, plain = true, round = true, block = true,
                )
                AppButton(
                    text = "公共访问（复制链接）",
                    onClick = { copyToClipboard("$SITE_URL/share/diary/detail?token=$shareToken", "公共链接已复制") },
                    type = AppButtonType.Primary, round = true, block = true,
                )
                AppButton(
                    // 原生增强：web 复制链接后要去浏览器看，这里直接在 App 内打开分享页
                    // （`Routes.diaryShare` 二选一：有 token 走公共模式，否则走密码模式）
                    text = "预览分享页",
                    onClick = {
                        val route = if (shareToken.isNotBlank()) {
                            com.live.finance.core.nav.Routes.diaryShare(token = shareToken)
                        } else {
                            com.live.finance.core.nav.Routes.diaryShare(id = detail?.id.orEmpty())
                        }
                        runCatching { nav.navigate(route) }
                        showShareResult = false
                    },
                    type = AppButtonType.Default, round = true, block = true, plain = true,
                )
                AppButton(
                    text = "关闭",
                    onClick = { showShareResult = false },
                    type = AppButtonType.Default, size = AppButtonSize.Small, plain = true,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    // 防止 offset import 未用（浮钮用 padding 定位）
}
