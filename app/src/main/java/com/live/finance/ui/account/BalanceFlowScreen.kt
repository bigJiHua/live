package com.live.finance.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.live.finance.data.model.Card
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.ui.flow.FlowItemRow
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import java.util.Calendar

@Composable
fun BalanceFlowScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var card by remember { mutableStateOf<Card?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        (graph.card.list(null) as? ApiResult.Ok)?.data?.let { cs ->
            cards = cs
            card = card ?: cs.firstOrNull()
        }
    }
    LaunchedEffect(card?.id) {
        loading = true
        val cid = card?.id
        rows = if (cid == null) emptyList()
        else (when (val r = graph.flow.listByCard(cid)) { is ApiResult.Ok -> r.data ?: emptyList(); else -> emptyList() })
        loading = false
    }

    val groups = remember(rows) { rows.groupBy { it.day }.toList().sortedByDescending { it.first } }

    ScreenScaffold { inner ->
        LazyColumn(modifier = inner) {
            item {
                FText("余额流水", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
                Row(
                    Modifier.fillMaxWidth().background(colors.bgCard).clickable { showPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(card?.displayName ?: "选择账户", 15f, FontWeight.Medium, colors.textPrimary, Modifier.weight(1f))
                    FText("切换 ▾", 12f, color = colors.primary)
                }
                Spacer(Modifier.height(12.dp))
                if (loading) FText("加载中…", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
                if (!loading && rows.isEmpty()) FText("暂无流水", 14f, color = colors.textTertiary, modifier = Modifier.padding(16.dp))
            }
            groups.forEach { (day, list) ->
                item {
                    val inc = list.filter { it.isIncome && it.transferGroupId.isEmpty() }.sumOf { it.amount }
                    val exp = list.filter { !it.isIncome && it.transferGroupId.isEmpty() }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FText(day, 13f, FontWeight.Medium, colors.textSecondary)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            FText("收 ${Money.format(inc)}   支 ${Money.format(exp)}", 12f, color = colors.textTertiary)
                        }
                    }
                }
                items(list) { FlowItemRow(it) {} }
            }
        }
    }

    VanPopup(show = showPicker, onDismissRequest = { showPicker = false }) {
        VanPicker(
            columns = listOf(cards.map { VanPickerOption(it.displayName, it.id) }),
            value = listOf(cards.indexOfFirst { it.id == card?.id }.coerceAtLeast(0)),
            title = "选择账户",
            onConfirm = { idx, _ -> card = cards.getOrNull(idx.firstOrNull() ?: 0); showPicker = false },
            onCancel = { showPicker = false },
        )
    }
}
