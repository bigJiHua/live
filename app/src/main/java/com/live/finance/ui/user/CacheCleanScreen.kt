package com.live.finance.ui.user

import android.content.Context
import android.webkit.WebView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.Coil
import com.live.finance.core.net.ApiGetCache
import com.live.finance.data.local.DiaryListCache
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanToastType
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** 一次扫描得到的缓存占用（字节）。 */
private data class CacheUsage(
    val image: Long = 0L,
    /** WebView / 其余运行期临时文件（cacheDir 总量减去 Coil 图片缓存）。 */
    val web: Long = 0L,
    val diary: Long = 0L,
    val total: Long = 0L,
)

/**
 * 空间清理（2026-10-22，原生特有）。
 *
 * 只清缓存，不动登录态/站点/业务数据：
 *  - Coil 图片缓存（内存 + 磁盘）
 *  - WebView 资源缓存（`clearCache(true)`）
 *  - `cacheDir` 里的其余临时文件
 *  - 动态列表本地快照（[DiaryListCache]，加密文件）
 *
 * ⚠ `WebView.clearCache(true)` 是**整个 App** 的 WebView 资源缓存；清完卡片页/日记编辑器的图片
 *   会在下次打开时重新下载，所以确认框必须明确告知「下次可能更慢」。
 */
@Composable
fun CacheCleanScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()

    var usage by remember { mutableStateOf(CacheUsage()) }
    var scanning by remember { mutableStateOf(true) }
    var clearing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    suspend fun refreshUsage() {
        scanning = true
        usage = withContext(Dispatchers.IO) { scanUsage(ctx) }
        scanning = false
    }

    LaunchedEffect(Unit) { refreshUsage() }

    fun clearAll() {
        if (clearing) return
        clearing = true
        scope.launch {
            try {
                ApiGetCache.clear()
                withContext(Dispatchers.IO) {
                    DiaryListCache.clear(ctx)
                    val loader = Coil.imageLoader(ctx)
                    loader.memoryCache?.clear()
                    runCatching { loader.diskCache?.clear() }
                }
                // WebView 的 HTTP 缓存只能通过 WebView 实例清；必须在主线程创建/销毁。
                withContext(Dispatchers.Main) {
                    runCatching {
                        val wv = WebView(ctx)
                        try {
                            wv.clearCache(true)
                        } finally {
                            wv.destroy()
                        }
                    }
                }
                // cacheDir 剩下的图片/网页/临时文件（filesDir 的业务数据不在其中）。
                withContext(Dispatchers.IO) {
                    ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
                }
                refreshUsage()
                toast.show("缓存已清理", VanToastType.Success)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                toast.show("清理失败：${t.message ?: "请稍后重试"}", VanToastType.Fail)
            } finally {
                clearing = false
                showConfirm = false
            }
        }
    }

    ScreenScaffold { inner ->
        Column(
            inner
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ===== 总览 =====
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard)
                    .padding(16.dp),
            ) {
                FText("本地缓存占用", 14f, FontWeight.Normal, colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(formatBytes(usage.total), 30f, FontWeight.Bold, tokens.primary)
                    if (scanning) {
                        Spacer(Modifier.size(8.dp))
                        VanLoading(size = 16.sp, color = tokens.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                FText(
                    "包含图片、网页资源和动态数据缓存；清理后首次加载可能较慢。",
                    12f,
                    FontWeight.Normal,
                    colors.textTertiary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 明细 =====
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgCard),
            ) {
                CacheRow(
                    icon = "photo-o",
                    title = "图片缓存",
                    subtitle = "Coil 磁盘缓存；清后卡面/银行 logo 会重新下载",
                    bytes = usage.image,
                )
                RowDivider()
                CacheRow(
                    icon = "desktop-o",
                    title = "网页/临时缓存",
                    subtitle = "WebView 资源与运行期临时文件",
                    bytes = usage.web,
                )
                RowDivider()
                CacheRow(
                    icon = "notes-o",
                    title = "动态数据缓存",
                    subtitle = "动态列表本地快照；清后下次进入会重新拉取",
                    bytes = usage.diary,
                )
            }

            Spacer(Modifier.height(16.dp))

            FText(
                "只清理缓存，不会删除登录状态、站点设置和账务数据。",
                12f,
                FontWeight.Normal,
                colors.textTertiary,
                Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            AppButton(
                text = "一键清理缓存",
                type = AppButtonType.Primary,
                block = true,
                round = true,
                loading = clearing,
                disabled = scanning,
                onClick = { showConfirm = true },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    VanConfirmDialog(
        show = showConfirm,
        title = "清理缓存",
        message = "清理后，图片、网页资源和动态数据会重新加载，下次进入可能会比较慢。\n\n" +
            "不会删除登录状态、站点设置和账务数据。确定清理吗？",
        confirmButtonText = "清理",
        onConfirm = { clearAll() },
        onCancel = { showConfirm = false },
        onClose = { showConfirm = false },
    )
}

@Composable
private fun CacheRow(icon: String, title: String, subtitle: String, bytes: Long) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VanIcon(name = icon, size = 18.sp, color = colors.primary)
        Column(Modifier.weight(1f)) {
            FText(title, 16f, FontWeight.Normal, colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            FText(subtitle, 12f, FontWeight.Normal, colors.textTertiary)
        }
        FText(formatBytes(bytes), 14f, FontWeight.Medium, colors.textSecondary)
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.border))
}

/** 扫描：Coil 用 ImageLoader.diskCache.size，其余按目录/文件实际长度。 */
private fun scanUsage(ctx: Context): CacheUsage {
    val cacheTotal = dirSize(ctx.cacheDir)
    val image = runCatching { Coil.imageLoader(ctx).diskCache?.size }.getOrNull()
        ?: dirSize(File(ctx.cacheDir, "image_cache"))
    // WebView 的 HTTP 缓存在 filesDir/app_webview 下（不在 cacheDir）；单独算，避免漏报。
    val webViewCache = dirSize(File(ctx.filesDir, "app_webview/Default/HTTP Cache"))
    val diary = DiaryListCache.sizeBytes(ctx)
    return CacheUsage(
        image = image.coerceIn(0L, cacheTotal),
        web = (cacheTotal - image).coerceAtLeast(0L) + webViewCache,
        diary = diary,
        total = cacheTotal + webViewCache + diary,
    )
}

private fun dirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    return runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value < 10.0) {
        String.format(Locale.US, "%.1f %s", value, units[index])
    } else {
        String.format(Locale.US, "%.0f %s", value, units[index])
    }
}
