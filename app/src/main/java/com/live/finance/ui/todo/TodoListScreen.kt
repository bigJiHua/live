package com.live.finance.ui.todo

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
import com.live.finance.data.model.Todo
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText

@Composable
fun TodoListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.todo
    var rows by remember { mutableStateOf<List<Todo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("待办日程", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无待办", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { t ->
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText(t.content, 15f, FontWeight.Medium,
                            if (t.status == "已完成") colors.textTertiary else colors.textPrimary)
                        FText("${t.happenDate.ifBlank { "—" }} · ${t.eventType}", 11f, color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                    }
                    FText(if (t.priority.isNotBlank()) priorityLabel(t.priority) else t.status, 12f, FontWeight.Normal,
                        if (t.status == "已完成") colors.textTertiary else colors.primary)
                }
                Spacer(Modifier.height(0.5.dp))
            }
        }
    }
}

private fun priorityLabel(p: String): String = when (p) {
    "1", "高", "high" -> "高优"; "2", "中", "medium" -> "中"; "3", "低", "low" -> "低"; else -> p
}
