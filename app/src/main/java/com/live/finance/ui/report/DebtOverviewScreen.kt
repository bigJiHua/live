package com.live.finance.ui.report

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.live.finance.theme.scaledSp
import com.live.finance.theme.vanDangerBg
import com.live.finance.theme.vanGreen
import com.live.finance.theme.vanGreenBg
import com.live.finance.theme.vanOrange
import com.live.finance.theme.vanOrangeBg
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.finance.ui.common.YearMonthSaver
import com.live.vant.basic.VanNoticeBar
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

    // keep-alive：本页会跳「流水详情」，返回时普通 remember 会丢 ⇒ 月份跳回当月
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
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
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("加载中…", 16f, color = colors.textTertiary)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {
                    // 月份切换
                    Row(Modifier.fillMaxWidth().background(colors.bgCard).padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        VanIcon("arrow-left", size = 18.sp, color = colors.primary,
                            modifier = Modifier.clickable { month = month.minusMonths(1) })
                        // web `.month-text{font-size:16px; font-weight:600}` → 按 §7.9 阶梯 16px ⇒ **18f**（不是 16f）
                        FText("${month.year}年${month.monthValue.toString().padStart(2, '0')}月账单", 18f,
                            FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                        VanIcon("arrow", size = 18.sp, color = colors.primary,
                            modifier = Modifier.clickable { month = month.plusMonths(1) })
                    }

                    // 摘要 —— 负责人 2026-09-20：「本期消费/未还金额 这块因文字大小超高，与右侧的持卡/代还
                    //   item 高度不一致」。web 是 `.sw-left`（两行）与 `.sw-card`（label+value 两行）并排，
                    //   靠 `.sw-row` 的 stretch 等高 ⇒ 原生这里给 Row 取 `IntrinsicSize.Max`、右侧两张卡
                    //   `fillMaxHeight()`，两侧就永远一样高（不再受字号影响）。
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        // web `.sw-left{padding:12px 14px; gap:10px}`（旧实现四边都是 14）
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(colors.bgCard).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText("本期消费", 12f, color = colors.textTertiary)
                                FText("¥${Money.format(lastMonthSpent)}", 18f, FontWeight.Bold, MoneyColor.expense)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                FText("未还金额", 12f, color = colors.textTertiary)
                                FText("¥${Money.format(unpaid)}", 18f, FontWeight.Bold,
                                    if (unpaid > 0) MoneyColor.expense else colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // web `.sw-card{padding:12px 10px; min-width:54px}`（旧实现 padding 12/12、缺 min-width 已补；
                        //   `.sw-card .sw-label{font-size:10px}` 按 §7.9 ⇒ 12f）
                        Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                            .clickable { nav.navigate(Routes.CARD_CREDIT) }
                            .padding(horizontal = 10.dp, vertical = 12.dp).widthIn(min = 54.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("持卡", 12f, color = colors.textTertiary)
                                FText("${bills.size}", 18f, FontWeight.Bold, colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                            .clickable { nav.navigate(Routes.BILL_LIST) }
                            .padding(horizontal = 10.dp, vertical = 12.dp).widthIn(min = 54.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("待还", 12f, color = colors.textTertiary)
                                // web `.sw-value.warning{color:var(--van-orange, #fa8c16)}` —— 本页 fallback 是 **#fa8c16**（不是 #ff976a）
                                FText("$unpaidCardCount", 18f, FontWeight.Bold, vanOrange(Color(0xFFFA8C16)))
                            }
                        }
                    }

                    // 有效期
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard)
                        .clickable { expiryExpanded = !expiryExpanded }.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText("📅 全部信用卡有效期", 16f, FontWeight.SemiBold, colors.textPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FText("${expiryRows.size}张 ", 12f, color = colors.textTertiary)
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
                                // web `DebtOverview.vue:593-594` `.expiry-row.danger/.warn`
                                // → `var(--van-danger-bg)` / `var(--van-orange-bg)`，**没写 fallback**：
                                //   浅色模式下变量不存在 → 声明失效 → 背景取初始值 **transparent**；
                                //   深色才是 #fff0f0 / rgba(255,151,106,.12)。
                                val rowBg = when {
                                    row.expired -> vanDangerBg(Color.Transparent)
                                    row.expiring -> vanOrangeBg(Color.Transparent)
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
                                            // web `DebtOverview.vue:578-580` `.badge-{red,orange,green}`：
                                            //   red    bg `var(--van-danger-bg, #ffebe8)`  fg `var(--van-danger-color)`（:root → 跟主题）
                                            //   orange bg `var(--van-orange-bg, #fff7e6)`  fg `var(--van-orange, #fa8c16)`
                                            //   green  bg `var(--van-green-bg, #f0fff4)`   fg `var(--van-green, #07c160)`
                                            // ⚠ `--van-*-bg` 只在深色有定义 → 浅色用行内 fallback（#ffebe8/#fff7e6/#f0fff4），
                                            //   深色才是 #fff0f0 / rgba(255,151,106,.12) / #f0f9eb；前景色同理（#fa8c16 vs #ff976a）。
                                            row.expired -> ExpiryBadge("已过期", vanDangerBg(Color(0xFFFFEBE8)), colors.danger)
                                            // web `.badge-orange{background:var(--van-orange-bg,#fff7e6); color:var(--van-orange,#fa8c16)}` —— 本页 fallback #fa8c16
                                            row.expiring -> ExpiryBadge("${row.monthsLeft}个月后到期", vanOrangeBg(Color(0xFFFFF7E6)), vanOrange(Color(0xFFFA8C16)))
                                            else -> ExpiryBadge("正常", vanGreenBg(Color(0xFFF0FFF4)), vanGreen())
                                        }
                                    }
                                }
                            }
                        } else if (expiryRows.isNotEmpty()) {
                            // 折叠态 = web `<van-notice-bar left-icon="volume-o" :scrollable="false">` 内套
                            //   `<van-swipe vertical :autoplay="4000" :touchable="false" :show-indicators="false">`
                            //   （`DebtOverview.vue:44-51`）⇒ 一条 40dp 橙底横幅，每 4 秒**竖向**换一张卡。
                            //   负责人说的「缺少下方的滚动栏」就是这条轮播：原生此前是静态渲染 `first()` 一条，
                            //   既没有 notice-bar 的外壳（橙底 + volume-o 图标），也不轮播。
                            var swipeIdx by remember { mutableIntStateOf(0) }
                            LaunchedEffect(expiryRows.size) {
                                swipeIdx = 0
                                while (expiryRows.size > 1) {
                                    kotlinx.coroutines.delay(4000)
                                    swipeIdx = (swipeIdx + 1) % expiryRows.size
                                }
                            }
                            // 外壳仍是 `van-notice-bar`（左 `volume-o` + 40dp 橙底），轮播内容放它的 content 槽里
                            VanNoticeBar(leftIcon = "volume-o", scrollable = false) {
                            // van-swipe 的切换是**竖向位移动画**（`transform: translate3d(0,-h,0)`，默认 300ms ease），
                            //   不是文本直接替换。负责人 2026-09-20：「滚动并非滚动，而是刷新数据」——
                            //   上一版只换了 idx（瞬间换字）⇒ 观感像数据刷新，这里补上上下滑入滑出。
                            androidx.compose.animation.AnimatedContent(
                                targetState = swipeIdx,
                                transitionSpec = {
                                    androidx.compose.animation.slideInVertically(
                                        androidx.compose.animation.core.tween(300),
                                    ) { it } + androidx.compose.animation.fadeIn(
                                        androidx.compose.animation.core.tween(300),
                                    ) togetherWith androidx.compose.animation.slideOutVertically(
                                        androidx.compose.animation.core.tween(300),
                                    ) { -it } + androidx.compose.animation.fadeOut(
                                        androidx.compose.animation.core.tween(300),
                                    )
                                },
                                label = "notice-swipe",
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.height(40.dp),   // web `.notice-swipe{height:40px;line-height:40px}`
                            ) { idx ->
                                val row = expiryRows.getOrElse(idx) { expiryRows.first() }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FText(cardText(row.card), 14f, color = colors.textPrimary)
                                    val expire = row.expire?.let { "·%04d/%02d".format(it.year, it.monthValue) } ?: ""
                                    FText(" $expire ", 14f, color = colors.textTertiary)
                                    // ⚠ 折叠态走的是 notice-bar 里的**行内裸 hex**
                                    //   （`DebtOverview.vue:48-50`：`style="color:#ee0a24/#fa8c16/#07c160"`），
                                    //   与上面展开态的 `.badge-*` CSS 变量是**两套配色**，别统一。
                                    when {
                                        row.expired -> FText("已过期", 14f, FontWeight.SemiBold, Color(0xFFEE0A24))
                                        row.expiring -> FText("${row.monthsLeft}个月到期", 14f, FontWeight.SemiBold, Color(0xFFFA8C16))
                                        else -> FText("正常", 14f, FontWeight.SemiBold, Color(0xFF07C160))
                                    }
                                }
                            }
                            }   // VanNoticeBar 的 content 槽
                        }
                    }

                    // 每日信用卡支出日历
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FText("📆 每日信用卡支出", 16f, FontWeight.SemiBold, colors.textPrimary)
                            FText("${month.year}年${month.monthValue.toString().padStart(2, '0')}月", 12f, color = colors.textTertiary)
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
                                                FText(date.takeLast(2), 16f, FontWeight.Medium,
                                                    if (selected) Color.White else if (isToday) colors.primary else colors.textPrimary)
                                                if (total > 0) {
                                                    FText("¥${abbrevTransfer(total)}", 12f,
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
                            FText(if (selectedDate.isEmpty()) "📊 本月总支出类别" else "📊 $selectedDate 支出类别",
                                16f, FontWeight.SemiBold, colors.textPrimary)
                            if (selectedDate.isNotEmpty()) {
                                FText("重置", 12f, color = colors.primary,
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
                        FText(popupTitle, 16f, FontWeight.SemiBold, colors.textPrimary)
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
                                FText(item.remark.ifEmpty { item.categoryName }, 16f, color = colors.textPrimary)
                                FText("${cardText(cards.firstOrNull { it.id == item.cardId })} · ${item.time}", 12f,
                                    color = colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
                            }
                            FText("-¥${Money.format(item.amount)}", 16f, FontWeight.SemiBold, MoneyColor.expense)
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
        FText(text, 12f, color = fg)
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
    androidx.compose.ui.text.TextStyle(color = color, fontSize = scaledSp(sizeSp), fontWeight = weight, textAlign = textAlign),
)
