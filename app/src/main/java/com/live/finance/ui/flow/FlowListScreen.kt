package com.live.finance.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.vant.feedback.VanList
import com.live.vant.feedback.VanListFooter
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun FlowListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val repo = App.of(LocalContext.current).graph.flow
    val cal = remember { Calendar.getInstance() }
    val year = remember { cal.get(Calendar.YEAR) }
    val month = remember { cal.get(Calendar.MONTH) + 1 }

    val refreshState = rememberVanPullRefreshState()
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val limit = 20

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        val p = if (reset) 1 else page
        rows = when (val r = repo.list(year, month, page = p, limit = limit)) {
            is ApiResult.Ok -> {
                val data = r.data ?: emptyList()
                if (data.size < limit) finished = true
                if (reset) data else rows + data
            }
            else -> rows
        }
        page = p + 1
        loading = false
    }

    LaunchedEffect(Unit) { load(reset = true) }

    val groups = remember(rows) { com.live.finance.ui.common.groupFlowsByDay(rows) }

    VanPullRefresh(state = refreshState, onRefresh = { scope.launch { load(true); refreshState.finishRefresh() } }) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = inner) {
                item {
                    FText("流水明细 · ${year}年${month}月", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                    if (rows.isEmpty() && !loading) FText("本月暂无记录", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                }
                groups.forEach { day ->
                    item { DayHeader(day, colors) }
                    items(day.cells) { cell ->
                        FlowCellRow(cell) { row -> if (row.transferGroupId.isEmpty()) nav.navigate(Routes.flowDetail(row.id)) }
                    }
                }
                item { VanList(state = listState, loading = loading, finished = finished, onLoad = { scope.launch { load(false) } }) }
                item { VanListFooter(loading = loading, finished = finished, finishedText = "没有更多了") }
            }
        }
    }
}

@Composable
private fun DayHeader(day: com.live.finance.ui.common.FlowDay, colors: com.live.vant.theme.VantColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(day.date, 13f, FontWeight.Medium, colors.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            FText("收 ${Money.format(day.income)}    支 ${Money.format(day.expense)}", 12f, FontWeight.Normal, colors.textTertiary)
        }
    }
}
