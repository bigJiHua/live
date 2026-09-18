package com.live.finance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.AssetHome
import com.live.finance.data.model.Todo
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.SemanticRole
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun HomeScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val graph = App.of(ctx).graph
    val cal = remember { Calendar.getInstance() }
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)

    var data by remember { mutableStateOf<AssetHome?>(null) }
    var reminders by remember { mutableStateOf<List<Todo>>(emptyList()) }
    var salary by remember { mutableStateOf(0.0) }
    var showAmount by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        data = (graph.asset.home() as? ApiResult.Ok)?.data
        reminders = (graph.todo.reminders(cal.get(Calendar.YEAR), month) as? ApiResult.Ok)?.data ?: emptyList()
        val today = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), month, day)
        salary = (graph.work.daySalary(today) as? ApiResult.Ok)?.data ?: 0.0
    }

    Box(Modifier.fillMaxSize().background(colors.bgPage)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 13.dp, vertical = 6.dp)) {
            TotalAssetsCard(colors, data, showAmount, month, day,
                onEye = { showAmount = !showAmount },
                onDate = { nav.navigate(Routes.TODO_CALENDAR) },
                onCards = { nav.navigate(Routes.CARD) },
                onCredit = { nav.navigate(Routes.CREDIT_CENTER) },
                onBill = { nav.navigate(Routes.BILL_LIST) },
            )
            Spacer(Modifier.height(16.dp))

            MenuGridCard(colors, onFlow = { nav.navigate(Routes.FLOW_LIST) }, onCredit = { nav.navigate(Routes.CREDIT_CENTER) },
                onAssets = { nav.navigate(Routes.ASSETS_LIST) }, onSalary = { nav.navigate(Routes.WORK_JOB_SETTING) })
            Spacer(Modifier.height(16.dp))

            InfoCard(
                colors = colors,
                salary = salary,
                // web：`(res.data || []).filter(r => r.content && r.content !== "1")` —— 「1」是出差占位标记，不上横幅
                reminders = reminders.filter { it.content.isNotBlank() && it.content != "1" },
                // web：薪酬行 → /work/salary-calendar；未注册时退到已落地的薪资设置页，不点崩
                onSalary = {
                    nav.navigate(
                        if (nav.graph.findNode(Routes.WORK_SALARY_CALENDAR) != null) Routes.WORK_SALARY_CALENDAR
                        else Routes.WORK_JOB_SETTING,
                    )
                },
                // web：待办提醒整行 → /todo/calendar（含右侧 > 引导）
                onTodo = { nav.navigate(Routes.TODO_CALENDAR) },
            )
            Spacer(Modifier.height(12.dp))

            Box(Modifier.padding(horizontal = 16.dp)) {
                // web `Home/index.vue`：app-button primary plain round small icon=plus → `goToAddFlow()` = /finance/quick-add
                AppButton(
                    text = "快速登记流水",
                    type = AppButtonType.Primary,
                    plain = true,
                    round = true,
                    size = AppButtonSize.Small,
                    icon = "plus",
                    block = true,
                    onClick = { nav.navigate(Routes.ACCOUNT_QUICK_ADD) },
                )
            }
            Spacer(Modifier.height(12.dp))

            RecentRecords(colors, data?.recent ?: emptyList(),
                onDetail = { id -> nav.navigate(Routes.flowDetail(id)) }, onViewAll = { nav.navigate(Routes.FLOW_LIST) })
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TotalAssetsCard(
    colors: com.live.vant.theme.VantColors, data: AssetHome?, showAmount: Boolean,
    month: Int, day: Int, onEye: () -> Unit, onDate: () -> Unit, onCards: () -> Unit, onCredit: () -> Unit, onBill: () -> Unit,
) {
    val grad = Brush.linearGradient(listOf(colors.primary, colors.primaryGrad))  // web: 主色 → --theme-primary-grad
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.4.dp)).background(grad)   // web .total-assets-card radius 1.4rem
            .padding(horizontal = 19.2.dp, vertical = 25.6.dp),                      // web padding 1.6rem 1.2rem
    ) {
        // 行1：预估总资产 + 日期牌（web .header-row-top：space-between + 垂直居中）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // web .label-group：opacity .9 + font-size .85rem + gap .5rem
                FText("预估总资产 (元)", 13.6f, FontWeight.Normal, Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.width(8.dp))
                // web .eye-icon：font-size 1rem
                VanIcon(name = if (showAmount) "eye-o" else "closed-eye", size = 16.sp, color = Color.White, onClick = onEye)
            }
            DateBadge(month = month, day = day, onClick = onDate)
        }
        // 行2：金额 + 今日收支（web .amount-row：space-between + align-items flex-end；两行之间**无**额外间距）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            // web .total-amount：2.2rem / 700 / letter-spacing .05rem
            FText(if (showAmount) Money.abbrev(data?.totalBalance ?: 0.0) else "****", 35.2f, FontWeight.Bold, Color.White,
                modifier = Modifier)
            Column(horizontalAlignment = Alignment.End) {
                // web .income-val.in = --money-income、.out = --money-expense（跟随收支颜色设置）
                TodayLine("今日收入：", if (showAmount) "+" + Money.abbrev(data?.todayIncome ?: 0.0) else "****", MoneyColor.income)
                Spacer(Modifier.height(4.dp))   // web .income-item { margin-bottom: .25rem }
                TodayLine("今日支出：", if (showAmount) "-" + Money.abbrev(data?.todayExpense ?: 0.0) else "****", MoneyColor.expense)
            }
        }
        // 明细四栏（web .asset-details：border-top 1px rgba(255,255,255,.15) + padding-top 1rem）
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            DetailCol("借记卡", "${data?.debitCardCount ?: 0} 张", Modifier.weight(1f).clickable(onClick = onCards))
            DetailCol("信用卡", "${data?.creditCardCount ?: 0} 张", Modifier.weight(1f).clickable(onClick = onCredit))
            DetailCol("待还账单", Money.abbrev(data?.creditDebt ?: 0.0), Modifier.weight(1f).clickable(onClick = onBill))
            DetailCol("本月结余", Money.abbrev(data?.monthlySurplus ?: 0.0), Modifier.weight(1f))
        }
    }
}

/**
 * 右上角日期牌 —— 逐值复刻 web `.date-badge`：
 *  - 牌：`2.4rem`(38.4) 正方、`border-radius .4rem`(6.4)、底 `rgba(255,255,255,.15)`
 *  - 月带：高 `0.9rem`(14.4)、底 `rgba(255,255,255,.25)`、**上两角 6.4 圆角**、字 `0.5rem`(8) 700
 *  - 日号：占余下高度居中、字 `0.9rem`(14.4) 700
 *  - 文本显式关闭 `includeFontPadding` 并用 `Trim.Both`：浏览器行盒不含字体上下留白，
 *    否则同一字号在 Compose 里会整体偏下若干像素（这就是此前"看着没对齐"的根因）。
 */
@Composable
private fun DateBadge(month: Int, day: Int, onClick: () -> Unit) {
    val tight = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.4.dp)
            .clip(RoundedCornerShape(6.4.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.4.dp)
                    .background(
                        Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(topStart = 6.4.dp, topEnd = 6.4.dp),
                    ),
            ) {
                BasicText(
                    "${month}月",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = tight,
                    ),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BasicText(
                    "$day",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.4.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = tight,
                    ),
                )
            }
        }
    }
}

@Composable private fun TodayLine(label: String, value: String, color: Color) {
    // web .income-item：space-between + baseline 对齐 + gap 8px；`.ie-label` 9.6、金额 13.12/600
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FText(label, 9.6f, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
        FText(value, 13.12f, FontWeight.SemiBold, color)
    }
}

@Composable private fun DetailCol(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FText(label, 9.6f, FontWeight.Normal, Color.White.copy(alpha = 0.8f))   // web .small-label .6rem
        Spacer(Modifier.height(4.8.dp))                                        // web margin-bottom .3rem
        FText(value, 15.2f, FontWeight.SemiBold, Color.White)                  // web .95rem
    }
}

@Composable
private fun MenuGridCard(
    colors: com.live.vant.theme.VantColors,
    onFlow: () -> Unit, onCredit: () -> Unit, onAssets: () -> Unit, onSalary: () -> Unit,
) {
    // 语义彩色图标统一走 token：mono 主题下自动收敛成主色（web html[data-theme-mono="1"]）
    val tokens = LocalAppTokens.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.2.dp)).background(colors.bgCard).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            GridItem("流水明细", "orders-o", tokens.semanticIcon(SemanticRole.Blue), Modifier.weight(1f), onClick = onFlow)
            GridItem("信用卡", "credit-pay", tokens.semanticIcon(SemanticRole.Orange), Modifier.weight(1f), onClick = onCredit)
            GridItem("资产结构", "gem", tokens.semanticIcon(SemanticRole.Purple), Modifier.weight(1f), onClick = onAssets)
            GridItem("薪资计算", "points", tokens.semanticIcon(SemanticRole.Gold), Modifier.weight(1f), onClick = onSalary)
        }
    }
}

@Composable private fun GridItem(text: String, icon: String, iconColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VanIcon(name = icon, size = 26.sp, color = iconColor)
        Spacer(Modifier.height(8.dp))
        FText(text, 13f, FontWeight.Normal, LocalAppColors.current.textSecondary)
    }
}

/**
 * 信息卡（web `.app-card.info-card`：radius 1.2rem + padding 1rem；两行 `.info-row` 各 padding .4rem 0，
 * 行间是 `<van-divider style="margin:10px 0">`）。待办提醒行按 web 带右侧 `>` 引导并整行可点。
 */
@Composable
private fun InfoCard(
    colors: com.live.vant.theme.VantColors,
    salary: Double,
    reminders: List<Todo>,
    onSalary: () -> Unit,
    onTodo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.2.dp)).background(colors.bgCard).padding(16.dp)) {
        // 行1：今日预估薪酬（web `.info-row` → /work/salary-calendar；金额用 `.text-income` = --van-danger-color）
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSalary).padding(vertical = 6.4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VanIcon(name = "manager-o", size = 19.2.sp, color = colors.primary)   // web .info-icon 1.2rem
                Spacer(Modifier.width(8.dp))                                          // web .info-left gap .5rem
                FText("今日预估薪酬", 14.4f, FontWeight.Normal, colors.textPrimary)      // web .info-label .9rem
            }
            FText("+${Money.abbrev(salary)}", 14.4f, FontWeight.Bold, colors.danger)
        }
        Spacer(Modifier.height(10.dp))                    // web <van-divider style="margin: 10px 0">
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border))
        Spacer(Modifier.height(10.dp))
        // 行2：待办提醒（web `.info-row` → /todo/calendar）：左 icon + 标题 + 红点 + 轮播 chip，右 `>` 引导
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onTodo).padding(vertical = 6.4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                VanIcon(name = "clock-o", size = 19.2.sp, color = colors.primary)
                Spacer(Modifier.width(8.dp))
                FText("待办提醒", 14.4f, FontWeight.Normal, colors.textPrimary)
                if (reminders.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    // web .reminder-dot：6px 圆点，色 = --van-danger-color（不是收支色，深色主题下会变浅红）
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.danger))
                    Spacer(Modifier.width(6.dp))          // web .home-reminder-swipe { margin-left: 6px }
                    ReminderMarquee(reminders)
                }
            }
            // web `.info-right` 的 `van-icon.arrow-right`：font-size .8rem、色 text-tertiary
            VanIcon(name = "arrow", size = 12.8.sp, color = colors.textTertiary)
        }
    }
}

/**
 * 待办提醒竖排轮播（web `van-swipe vertical :autoplay="3500" :touchable="false"`，
 * 容器高 22px、chip 字号 .8rem，颜色按 `getReminderLevel` 分级）。
 */
@Composable
private fun ReminderMarquee(reminders: List<Todo>) {
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(reminders.size) {
        if (reminders.size > 1) while (true) { delay(3500); idx = (idx + 1) % reminders.size }
    }
    val colors = LocalAppColors.current
    val r = reminders.getOrElse(idx) { reminders.first() }
    val color = when (reminderLevel(r.happenDate)) {
        "green" -> colors.success                       // web .lv-green = --van-success-color
        "yellow" -> Color(0xFFFF976A)                   // web .lv-yellow = --van-orange（两模式同值）
        else -> colors.danger                           // web .lv-red = --van-danger-color
    }
    Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        FText(r.content, 12.8f, FontWeight.Normal, color)
    }
}

/**
 * web `Home/index.vue` 的 `getReminderLevel`：
 * 今天及以前（`diff <= 0`）→ red、`diff >= 10` → green、`diff >= 5` → yellow、其余 → red。
 */
private fun reminderLevel(happenDate: String): String {
    val d = runCatching { LocalDate.parse(happenDate.take(10)) }.getOrNull() ?: return "red"
    val diff = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        diff <= 0 -> "red"
        diff >= 10 -> "green"
        diff >= 5 -> "yellow"
        else -> "red"
    }
}

@Composable
private fun RecentRecords(
    colors: com.live.vant.theme.VantColors, items: List<com.live.finance.data.model.FlowRow>,
    onDetail: (String) -> Unit, onViewAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.2.dp)).background(colors.bgCard)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FText("近期消费", 15f, FontWeight.Bold, colors.textPrimary)
            // web .view-detail { font-size: .8rem; color: var(--theme-primary) }
            FText("查看明细>", 12.8f, FontWeight.Normal, colors.primary, modifier = Modifier.clickable(onClick = onViewAll))
        }
        if (items.isEmpty()) {
            FText("暂无大额流水", 14f, FontWeight.Normal, colors.textTertiary, Modifier.padding(16.dp))
        } else {
            items.forEachIndexed { i, it ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDetail(it.id) }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FText((it.categoryName) + if (it.isIncome) "收入" else "支出", 15f, FontWeight.Normal, colors.textPrimary)
                        Spacer(Modifier.height(3.dp))
                        FText(it.transDate, 12f, FontWeight.Normal, colors.textTertiary)
                    }
                    // web：`.text-income` 硬编码 --van-danger-color、`.text-expense` 硬编码 --van-green（不跟随收支颜色设置）
                    FText(
                        (if (it.isIncome) "+" else "-") + Money.format(it.amount) + "元",
                        15f, FontWeight.SemiBold, if (it.isIncome) colors.danger else colors.success,
                    )
                }
                if (i != items.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).padding(start = 16.dp).background(colors.border))
            }
        }
    }
}
