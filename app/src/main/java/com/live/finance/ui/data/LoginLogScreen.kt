package com.live.finance.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.LoginLog
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun LoginLogScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.loginLog
    var rows by remember { mutableStateOf<List<LoginLog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("登录日志", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无记录", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { l ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText("${l.location.ifBlank { "未知" }} · ${l.ip}", 14f, FontWeight.Medium, colors.textPrimary)
                        FText("${l.os} ${l.browser} ${l.device}  ${com.live.finance.core.TimeFmt.dateTime(l.loginTime)}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                        if (!l.success && l.error.isNotBlank()) FText("失败：${l.error}", 11f, color = colors.danger)
                    }
                    FText(if (l.success) "成功" else "失败", 12f, FontWeight.Medium, if (l.success) colors.success else colors.danger)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}
