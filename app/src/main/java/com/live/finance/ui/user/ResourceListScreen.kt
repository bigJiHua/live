package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Resource
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.1fMB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.0fKB", bytes / 1000.0)
    else -> "${bytes}B"
}

@Composable
fun ResourceListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.resource
    var rows by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list(busType = "")) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("文件资源", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无资源", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { res ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanImage(
                        src = AppConfig.fullFileUrl(res.filePath),
                        modifier = Modifier.size(56.dp),
                        width = 56.dp, height = 56.dp, radius = 6.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        FText(res.fileName.ifBlank { "未命名" }, 14f, FontWeight.Medium, colors.textPrimary)
                        FText("${fmtSize(res.fileSize)} · ${res.fileExt} · ${com.live.finance.core.TimeFmt.date(res.createTime)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                        if (res.remark.isNotBlank()) FText(res.remark, 12f, color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
