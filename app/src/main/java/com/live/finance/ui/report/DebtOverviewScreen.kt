package com.live.finance.ui.report

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Bill
import com.live.finance.data.model.Card
import com.live.finance.data.model.Category
import com.live.finance.data.model.FlowRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private val CAT_COLORS = listOf(
    0xFFEE0A24, 0xFFFF976A, 0xFFFFB300, 0xFF1989FA,
    0xFF07C160, 0xFF7232DD, 0xFF00BCD4, 0xFFE91E63,
).map { Color(it) }

private data class CatStatRow(val name: String, val total: Double, val color: Color, val pct: Float)

/**
 * 负债总览（web `views/Finance/report/debt/DebtOverview.vue`）：
 * 月份切换 → 摘要（本期消费/未还 + 持卡/待还入口）→ 信用卡有效期（折叠通知/展开表格）→
 * 每日信用卡支出日历（点选日期联动）→ 支出类别 Top8（点开明细弹层）。
 */
@Composable
fun DebtOverviewScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph

    var month by remember { mutableStateOf(YearMonth.now()) }
    var loading by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var banks by remember { mutableStateOf<List<Category>>(emptyList()) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var creditExpenses by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var expiryExpanded by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf("") }
    var popupTitle by remember { mutableStateOf("") }
    var popupItems by remember { mutableStateOf<List<FlowRow>>(emptyList()) }
    var showPopup by remember { mutableStateOf(false) }

    fun cardText(card: Card?): String {
        card ?: return ""
        val bank = banks.firstOrNull { it.id == card.bankId }
        val name = bank?.name?.ifEmpty { null } ?: card.alias.ifEmpty { card.bankName }
        return when {
            name.isNotEmpty() && card.last4.isNotEmpty() -> "${name} ${card.last4}"
            name.isNotEmpty() -> name
            card.last4.isNotEmpty() -> "****${card.last4}"
            else -> card.id
        }
    }

    LaunchedEffect(month) {
        loading = true
        cards = (graph.card.list(null) as? ApiResult.Ok)?.data ?: emptyList()
        banks = (graph.category.list("bank") as? ApiResult.Ok)?.data ?: emptyList()
        bills = (graph.bill.list(null, "%04d-%02d".format(month.year, month.monthValue)) as? ApiResult.Ok)?.data ?: emptyList()
        val creditIds = cards.filter { it.isCredit }.map { it.id }.toSet()
        creditExpenses = if (creditIds.isEmpty()) emptyList()
        else ((graph.flow.listRange(month.atDay(1).toString(), month.atEndOfMonth().toString(), limit = 10000)
            as? ApiResult.Ok)?.data ?: emptyList()).filter { it.cardId in creditIds }
        loading = false
    }

    // 摘要（web summary）
    val lastMonthSpent = bills.sumOf { it.billAmount }
    val unpaid = bills.sumOf { it.needRepay }
    val unpaidCardCount = bills.count { it.needRepay > 0 }

    // 有效期（web allCardExpiry）
    data class ExpiryRow(val card: Card, val expire: LocalDate?, val expired: Boolean, val expiring: Boolean, val monthsLeft: Int)
    val today = LocalDate.now()
    val expiryRows = cards.filter { it.isCredit }.map { c ->
        val e = runCatching { LocalDate.parse(c.expireDate) }.getOrNull()
            ?: runCatching { YearMonth.parse(c.expireDate.take(7)).atEndOfMonth() }.getOrNull()
        ExpiryRow(
            c, e,
            expired = e != null && e.isBefore(today.withDayOfMonth(1)),
            expiring = e != null && !e.isBefore(today.withDayOfMonth(1)) && e.isBefore(today.plusMonths(3)),
            monthsLeft = if (e == null) 0 else kotlin.math.max(0, (e.year - today.year) * 12 + (e.monthValue - today.monthValue)),
        )
    }.sortedBy { it.expire?.toEpochDay() ?: Long.MAX_VALUE }

    // 日历（web calDays：周一开头）
    val dayTotals = remember(creditExpenses) {
        creditExpenses.filter { it.direction != 1 }.groupBy { it.day }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
    }
    val calCells = remember(month, dayTotals, today) {
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value + 6) % 7 // Mon=0
        buildList {
            repeat(offset) { add(Triple("", 0.0, false)) }
            (1..month.lengthOfMonth()).forEach { d ->
                val date = month.atDay(d)
                add(Triple(date.toString(), dayTotals[date.toString()] ?: 0.0, date == today))
            }
        }
    }

    // 类别（web categoryStats：选中日期 or 全月）
    val catStats = remember(creditExpenses, selectedDate) {
        val src = if (selectedDate.isEmpty()) creditExpenses else creditExpenses.filter { it.day == selectedDate }
        val map = LinkedHashMap<String, Double>()
        src.filter { it.direction != 1 }.forEach { m ->
            map[m.categoryName] = (map[m.categoryName] ?: 0.0) + m.amount
        }
        val max = (map.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        map.entries.sortedByDescending { it.value }.take(8)
            .mapIndexed { i, (name, total) -> CatStatRow(name, total, CAT_COLORS[i % CAT_COLORS.size], (total / max * 100).toFloat()) }
    }

    fun openPopup(title: String, items: List<FlowRow>) {
        popupTitle = title; popupItems = items; showPopup = true
    }

    ScreenScaffold { inner ->
        Column(inner.fillMaxSize()) {
            com.live.vant.nav.VanNavBar(title = "负债统计", leftArrow = true, onClickLeft = { nav.popBackStack() })
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("加载中…", 14f, color = colors.textTertiary)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
                    // 月份切换
                    Row(Modifier.fillMaxWidth().background(colors.bgCard).padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        VanIcon("arrow-left", size = 18.sp, color = colors.primary,
                            modifier = Modifier.clickable { month = month.minusMonths(1) })
                        FText("${month.year}年${month.monthValue.toString().padStart(2, '0')}月账单", 16f,
                            FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                        VanIcon("arrow", size = 18.sp, color = colors.primary,
                            modifier = Modifier.clickable { month = month.plusMonths(1) })
                    }

                    // 摘要
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText("本期消费", 12f, color = colors.textTertiary)
                                FText("¥${Money.format(lastMonthSpent)}", 16f, FontWeight.Bold, MoneyColor.expense)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText("未还金额", 12f, color = colors.textTertiary)
                                FText("¥${Money.format(unpaid)}", 16f, FontWeight.Bold,
                                    if (unpaid > 0) MoneyColor.expense else colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                            .clickable { nav.navigate(Routes.CARD_CREDIT) }.padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("持卡", 10f, color = colors.textTertiary)
                                FText("${bills.size}", 16f, FontWeight.Bold, colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                            .clickable { nav.navigate(Routes.BILL_LIST) }.padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("待还", 10f, color = colors.textTertiary)
                                FText("$unpaidCardCount", 16f, FontWeight.Bold, Color(0xFFFA8C16))
                            }
                        }
                    }

                    // 有效期
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                        .clickable { expiryExpanded = !expiryExpanded }.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText("📅 全部信用卡有效期", 14f, FontWeight.SemiBold, colors.textPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText("${expiryRows.size}张 ", 11f, color = colors.textTertiary)
                                VanIcon(if (expiryExpanded) "arrow-up" else "arrow-down", size = 11.sp, color = colors.textTertiary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (expiryExpanded) {
                            // 表格
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                FText("卡片", 12f, color = colors.textTertiary, modifier = Modifier.weight(1f))
                                FTextA("有效期", 12f, colors.textTertiary, Modifier.width(72.dp), textAlign = TextAlign.Center)
                                FTextA("状态", 12f, colors.textTertiary, Modifier.width(84.dp), textAlign = TextAlign.End)
                            }
                            expiryRows.forEach { row ->
                                val rowBg = when {
                                    row.expired -> Color(0x1AEE0A24)
                                    row.expiring -> Color(0x1AFA8C16)
                                    else -> Color.Transparent
                                }
                                Row(Modifier.fillMaxWidth().background(rowBg).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    FText(cardText(row.card), 12f, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                    FTextA(
                                        row.expire?.let { "%04d/%02d".format(it.year, it.monthValue) } ?: row.card.expireDate,
                                        12f, colors.textTertiary, Modifier.width(72.dp), textAlign = TextAlign.Center,
                                    )
                                    Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
                                        when {
                                            row.expired -> ExpiryBadge("已过期", Color(0xFFFFEBE8), Color(0xFFEE0A24))
                                            row.expiring -> ExpiryBadge("${row.monthsLeft}个月后到期", Color(0xFFFFF7E6), Color(0xFFFA8C16))
                                            else -> ExpiryBadge("正常", Color(0xFFE8F8EE), Color(0xFF07C160))
                                        }
                                    }
                                }
                            }
                        } else if (expiryRows.isNotEmpty()) {
                            // 折叠态：最近一张
                            val first = expiryRows.first()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText(cardText(first.card), 13f, color = colors.textPrimary)
                                val expire = first.expire?.let { "·%04d/%02d".format(it.year, it.monthValue) } ?: ""
                                FText(" $expire ", 13f, color = colors.textTertiary)
                                when {
                                    first.expired -> FText("已过期", 13f, FontWeight.SemiBold, Color(0xFFEE0A24))
                                    first.expiring -> FText("${first.monthsLeft}个月到期", 13f, FontWeight.SemiBold, Color(0xFFFA8C16))
                                    else -> FText("正常", 13f, FontWeight.SemiBold, Color(0xFF07C160))
                                }
                            }
                        }
                    }

                    // 每日信用卡支出日历
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText("📆 每日信用卡支出", 14f, FontWeight.SemiBold, colors.textPrimary)
                            FText("${month.year}年${month.monthValue.toString().padStart(2, '0')}月", 11f, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                FTextA(it, 11f, colors.textTertiary, Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        // 6 行 7 列网格
                        repeat((calCells.size + 6) / 7) { rowIdx ->
                            Row(Modifier.fillMaxWidth()) {
                                (0 until 7).forEach { colIdx ->
                                    val idx = rowIdx * 7 + colIdx
                                    if (idx < calCells.size) {
                                        val (date, total, isToday) = calCells[idx]
                                        val selected = date.isNotEmpty() && date == selectedDate
                                        Column(
                                            Modifier.weight(1f).aspectRatio(0.9f).padding(1.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        selected -> colors.primary
                                                        isToday -> Color(0xFFF0F5FF)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable(enabled = date.isNotEmpty() && total > 0) { selectedDate = date },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            if (date.isNotEmpty()) {
                                                FText(date.takeLast(2), 14f, FontWeight.Medium,
                                                    if (selected) Color.White else if (isToday) colors.primary else colors.textPrimary)
                                                if (total > 0) {
                                                    FText("¥${abbrevTransfer(total)}", 9f,
                                                        color = if (selected) Color.White else colors.danger,
                                                        modifier = Modifier.padding(top = 2.dp))
                                                }
                                            }
                                        }
                                    } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // 支出类别
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText(if (selectedDate.isEmpty()) "📊 本月支出类别" else "📊 $selectedDate 支出类别",
                                14f, FontWeight.SemiBold, colors.textPrimary)
                            if (selectedDate.isNotEmpty()) {
                                FText("重置", 11f, color = colors.primary,
                                    modifier = Modifier.clickable { selectedDate = "" })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (catStats.isEmpty()) {
                            FTextA("暂无支出", 13f, colors.textTertiary,
                                Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center)
                        }
                        catStats.forEach { cat ->
                            Row(Modifier.fillMaxWidth().clickable {
                                openPopup(cat.name, creditExpenses.filter {
                                    it.direction != 1 && it.categoryName == cat.name &&
                                        (selectedDate.isEmpty() || it.day == selectedDate)
                                })
                            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText(cat.name, 12f, color = colors.textPrimary,
                                    modifier = Modifier.width(72.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.bgThird)) {
                                    Box(Modifier.fillMaxWidth(cat.pct / 100f).height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(cat.color))
                                }
                                FTextA("¥${Money.format(cat.total)}", 11f, colors.textPrimary,
                                    Modifier.width(76.dp), weight = FontWeight.Medium, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }

    // 分类/当日明细弹层
    if (showPopup) {
        VanPopup(show = true, onDismissRequest = { showPopup = false }, position = VanPopupPosition.Bottom, round = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        FText(popupTitle, 15f, FontWeight.SemiBold, colors.textPrimary)
                        FText("  ${popupItems.size}笔", 12f, color = colors.textTertiary)
                    }
                    VanIcon("cross", size = 18.sp, color = colors.textTertiary,
                        modifier = Modifier.clickable { showPopup = false })
                }
                Column(Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState())) {
                    if (popupItems.isEmpty()) {
                        FTextA("暂无明细", 13f, colors.textTertiary,
                            Modifier.fillMaxWidth().padding(vertical = 30.dp), textAlign = TextAlign.Center)
                    }
                    popupItems.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (item.id.isNotEmpty()) { showPopup = false; nav.navigate(Routes.flowDetail(item.id)) }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                FText(item.remark.ifEmpty { item.categoryName }, 14f, color = colors.textPrimary)
                                FText("${cardText(cards.firstOrNull { it.id == item.cardId })} · ${item.time}", 11f,
                                    color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                            }
                            FText("-¥${Money.format(item.amount)}", 14f, FontWeight.SemiBold, MoneyColor.expense)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiryBadge(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        FText(text, 10f, color = fg)
    }
}

/** FText 的对齐版（web 表格列 .exp-col-* 等需要 textAlign 的场景）。 */
@Composable
private fun FTextA(
    text: String, sizeSp: Float, color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) = androidx.compose.foundation.text.BasicText(
    text, modifier,
    androidx.compose.ui.text.TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight, textAlign = textAlign),
)
