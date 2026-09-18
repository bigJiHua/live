package com.live.finance.ui.report

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.TransferRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import kotlin.math.abs

/**
 * 转账明细查询（web `views/Finance/report/flow/TransferList.vue`）：
 * 笔数摘要 + 分页列表（from → to 卡名/尾号 + 金额/日期/备注）。
 */
@Composable
fun TransferListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph

    var loading by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    val rows = remember { androidx.compose.runtime.mutableStateListOf<TransferRow>() }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    val listState = rememberLazyListState()
    val needLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= rows.size - 4 && !finished && !requesting
        }
    }

    LaunchedEffect(Unit) {
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
    }

    fun cardText(cardId: String): String {
        if (cardId.isEmpty()) return "-"
        if (cardId == "xxxx") return "现金"
        if (cardId == "yyyy") return "余额"
        val card = cards.firstOrNull { it.id == cardId } ?: return cardId
        val bank = banks.firstOrNull { it.id == card.bankId }
        val name = bank?.name?.ifEmpty { null } ?: card.alias.ifEmpty { card.bankName }
        val last4 = card.last4
        return when {
            name.isNotEmpty() && last4.isNotEmpty() -> "$name $last4"
            name.isNotEmpty() -> name
            last4.isNotEmpty() -> "****$last4"
            else -> cardId
        }
    }

    suspend fun loadPage(page: Int) {
        when (val r = graph.flow.transferList(page, 20)) {
            is ApiResult.Ok -> {
                val (t, list) = r.data ?: (0 to emptyList())
                if (page == 1) { rows.clear(); rows.addAll(list) } else rows.addAll(list)
                total = t
                finished = list.size < 20
            }
            else -> finished = true
        }
    }

    LaunchedEffect(Unit) {
        requesting = true
        loadPage(1)
        requesting = false; loading = false
    }
    LaunchedEffect(needLoadMore) {
        if (needLoadMore) {
            requesting = true
            loadPage(rows.size / 20 + 1)
            requesting = false
        }
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            com.live.vant.nav.VanNavBar(title = "转账明细查询", leftArrow = true, onClickLeft = { nav.popBackStack() })
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (rows.isNotEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("笔数", 12f, color = colors.textTertiary)
                                FText("$total", 16f, FontWeight.Bold, colors.textPrimary)
                            }
                        }
                    }
                    items(rows, key = { it.id }) { item ->
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText(cardText(item.fromCardId), 14f, color = colors.textPrimary,
                                    modifier = Modifier.weight(1f))
                                VanIcon("arrow", size = 14.sp, color = colors.textTertiary)
                                FTextA(cardText(item.toCardId), 14f, colors.textPrimary,
                                    Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText("-¥${abbrevTransfer(item.amount)}", 16f, FontWeight.Bold, MoneyColor.expense)
                                FText(item.transDate, 12f, color = colors.textTertiary)
                            }
                            if (item.remark.isNotEmpty() && item.remark != "转账") {
                                Spacer(Modifier.height(6.dp))
                                FText(item.remark, 11f, color = colors.textTertiary)
                            }
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            FText(
                                when {
                                    rows.isEmpty() && !requesting -> ""
                                    finished -> "没有更多了"
                                    else -> "加载中…"
                                },
                                12f, color = colors.textTertiary,
                            )
                        }
                        if (rows.isEmpty() && !requesting) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                VanEmpty(description = "暂无转账记录")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** web TransferList formatAmount：≥1万 → x.xxx万，否则 2 位小数（均为正数展示）。 */
internal fun abbrevTransfer(v: Double): String {
    val a = abs(v)
    return if (a >= 10000) String.format(java.util.Locale.US, "%.3f万", a / 10000)
    else String.format(java.util.Locale.US, "%.2f", a)
}

/** FText 的对齐版。 */
@Composable
private fun FTextA(
    text: String, sizeSp: Float, color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
) = androidx.compose.foundation.text.BasicText(
    text, modifier,
    androidx.compose.ui.text.TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight, textAlign = textAlign),
)
