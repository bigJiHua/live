package com.live.finance.ui.diary

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
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Moment
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType

@Composable
fun DiaryListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.moment
    var rows by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        rows = when (val r = repo.list()) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() }
        loading = false
    }
    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText("动态", 18f, FontWeight.Bold, colors.textPrimary, Modifier.weight(1f))
                    VanButton(text = "发布", type = VanButtonType.Primary, onClick = { nav.navigate(Routes.DIARY_ADD) })
                }
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp))
                if (!loading && rows.isEmpty()) FText("还没有动态", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            items(rows) { m ->
                Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FText(m.dateLabel, 12f, FontWeight.Normal, colors.textTertiary, Modifier.weight(1f))
                        if (m.mood.isNotBlank()) FText(m.mood, 14f, FontWeight.Normal, colors.textPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    FText(m.content.ifBlank { "（无文字）" }, 15f, FontWeight.Normal, colors.textPrimary)
                    if (m.location.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        FText("📍 ${m.location}", 11f, color = colors.textTertiary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
