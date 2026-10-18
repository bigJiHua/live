package com.live.finance.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.vanDangerBg
import com.live.finance.theme.vanGreen
import com.live.finance.theme.vanGreenBg
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.scaledSp
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadowCircle
import com.live.finance.ui.flow.FText
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 日历变体（对齐 web `components/calendar/CalendarGrid.vue` 的 `variant`）：
 *  - [Todo]：事件点 / 小飞机 / 提醒脉动（待办页用）；
 *  - [Flow]：当日收/支金额块（每日流水页用，`+收` 红 / `-支` 绿）；
 *  - [Salary]：当日「¥正式」`--dc-primary` / 「¥兼职」`--van-orange`，工作日无收入时显示「计薪」
 *    （工资日历页用；金额走 `abbrMoney` 万缩写）。
 */
enum class CalendarVariant { Todo, Flow, Salary }

/**
 * 单行（一周）高度。web 是 `grid-template-rows: repeat(6, 80px)`。
 *
 * ⚠ 原生**曾写死 80dp 却装不下**：web 的金额块 `.dc-amts` 是 `min-height:26px` + `line-height:1.15`；
 * 原生只写了 `min-height 26dp`，**漏了 line-height**，Compose 默认行高更大约 15dp/行 ⇒ 两行≈33dp，
 * 加上 48dp 日号圆与 5dp 外边距 ≈ 86dp > 80dp ⇒ **最下面那行金额被裁掉一半**（2026-09-19 真机反馈）。
 * 这里改为**单一常量**：改这一个值即可同时同步「展开态总高（×6）」与「折叠态上移偏移」，避免三处写死漏改。
 *
 * ⚠ 2026-09-20 真机反馈后**按变体分档**（原来三个变体共用 88dp）：
 *  - **Todo** 只有日号圆（48dp）⇒ **80dp**，与 web 一致（88 让整块偏高、上下留白过多）
 *  - **Flow** 是「日号 + 收/支两行金额」⇒ **92dp**（88 时折叠窗口只剩 ~6dp 余量，金额贴边被指"高度不足"）
 *  - **Salary** 是「日号 + 正式/兼职金额」⇒ 88dp
 */
private val DC_ROW_H = 88.dp            // salary
private val DC_ROW_H_TODO = 80.dp
private val DC_ROW_H_FLOW = 92.dp

/** 日历某天标记（对应 web `CalendarGrid` 的 `dataset[date]`）。 */
data class DayMark(
    val eventCount: Int = 0,
    val overdue: Boolean = false,
    val airplane: Boolean = false,
    /** red / yellow / green（无 = 不闪烁） */
    val reminder: String? = null,
    /** flow 变体：当日收入合计（web `dataset[date].income`）。 */
    val income: Double = 0.0,
    /** flow 变体：当日支出合计（web `dataset[date].expense`）。 */
    val expense: Double = 0.0,
    /** salary 变体：当日正式工收入（web `dataset[date].formalIncome`）。 */
    val formalIncome: Double = 0.0,
    /** salary 变体：当日兼职收入合计（web `dataset[date].parttimeTotal`）。 */
    val parttimeTotal: Double = 0.0,
    /** salary 变体：当天落在在职区间内（web `isWorkingDay`）。 */
    val isWorkingDay: Boolean = false,
    /** salary 变体：非工作日（web `notWorking` → 整格 40% 透明、点击不跳转）。 */
    val notWorking: Boolean = false,
)

/** 提醒横幅条目（对应 web `reminderBanner`）。 */
data class ReminderChip(val date: String, val level: String, val content: String)

/** web `--van-orange`（浅/深两模式同值，用于 lv-yellow 与兼职金额）。 */
private val VanOrange = Color(0xFFFF976A)

/**
 * 日历网格（todo 变体）—— 一比一复刻 web `src/components/calendar/CalendarGrid.vue` 在
 * `variant="todo"` + `card=false` + `show-header=false` + `collapsible` 下的形态：
 *
 *  1. **提醒横幅**（可折叠，默认展开）：bell + 「YYYY年M月提醒」+「N个」+ 右侧动作区（眼睛 + 折叠箭头）；
 *     展开时是 12px chip 换行列表（红/黄/绿按等级配色），折叠时是单条竖排轮播（web `van-swipe vertical`，3500ms）。
 *  2. **星期行**：15px/600，周末（日/六）用主色 70% 透明。
 *  3. **日网格**：6 行 × 80px；日号是 48×48 圆、19px/500；
 *     - 今日 = 主色实心圆 + 白字 700 + `0 3px 12px rgba(primary,.25)`；
 *     - 选中（非今日）= 透明底 + 主色字 700 + 内描边 2.5px + `0 2px 8px rgba(primary,.25)`；
 *     - 今日且选中 = 白内描边 + 主色外圈；
 *     - 事件点（6px 主色 / 逾期红）与小飞机（✈️ 11px/pacity .5）贴在格底 3px；
 *     - 提醒日按等级**脉动**（红 1s / 黄 1.5s / 绿 2s：数字染色 700 + 背景与内描边同步呼吸），
 *       但当"当前正停在今天"时今日格不闪（web `.dc.at-today`）。
 *  4. **折叠条**：箭头 + 收起/展开，13px/500 主色；折叠时高度 80px 并把网格上移到选中行。
 *  5. **右下角「今」圆环**：选中他日时出现，40×40、2px 主色描边、`0 4px 12px rgba(primary,.35)`。
 */
@Composable
fun TodoCalendarGrid(
    year: Int,
    month: Int,                     // 1-12
    selectedDate: String,
    dataset: Map<String, DayMark>,
    reminderBanner: List<ReminderChip> = emptyList(),
    onSelect: (String) -> Unit,
    onGoToday: () -> Unit,
    /** todo（事件点/小飞机/提醒）或 flow（当日收/支金额）。 */
    variant: CalendarVariant = CalendarVariant.Todo,
    collapsible: Boolean = true,
    defaultExpanded: Boolean = true,
    reminderAction: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    var expanded by remember { mutableStateOf(defaultExpanded) }
    var reminderExpanded by remember { mutableStateOf(true) }

    val today = remember { LocalDate.now().toString() }
    val showTodayJump = selectedDate.isNotBlank() && selectedDate != today

    // 42 格（web：月初补空 + 月末补到 42）
    val cells = remember(year, month, selectedDate) {
        val first = LocalDate.of(year, month, 1)
        val daysInMonth = first.lengthOfMonth()
        val startDow = first.dayOfWeek.value % 7      // 周日=0
        val out = mutableListOf<CalendarCell>()
        repeat(startDow) { out.add(CalendarCell(0, "", false, false, 0)) }
        for (d in 1..daysInMonth) {
            val date = first.withDayOfMonth(d).toString()
            val row = (startDow + d - 1) / 7
            out.add(CalendarCell(d, date, date == today, date == selectedDate, row))
        }
        while (out.size < 42) out.add(CalendarCell(0, "", false, false, 0))
        out
    }
    // web：折叠窗口显示【选中日所在行】，无选中 → row 0（首行恒有 1-7 号，绝不空白）
    val selectedRow = cells.firstOrNull { it.selected && it.day > 0 }?.row ?: 0
    val collapsed = collapsible && !expanded
    // 行高按变体取（见 DC_ROW_H 上方注释）：改动这一个值即同时同步「展开总高 ×6」与「折叠上移偏移」
    val rowH = when (variant) {
        CalendarVariant.Todo -> DC_ROW_H_TODO
        CalendarVariant.Flow -> DC_ROW_H_FLOW
        CalendarVariant.Salary -> DC_ROW_H
    }
    val targetHeight = if (collapsed) rowH else rowH * 6      // 折叠=1 行 / 展开=6 行
    val bodyHeight by animateDpAsState(targetHeight, tween(360), label = "dcBody")
    // web `translateY(${gridOffset}px)` 的 px 是 CSS 像素（≈dp）；Compose 的 translationY 是【物理像素】，
    // 必须做 density 换算，否则位移不足、折叠窗口停在错误的行 → 日号被裁掉（「日期不见了」的根因）。
    val density = LocalDensity.current
    val gridOffset by animateFloatAsState(
        if (collapsed) with(density) { -(rowH * selectedRow).toPx() } else 0f, tween(360), label = "dcOffset",
    )

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // ── ① 提醒横幅 ──
            // ⚠⚠ 2026-10-12 负责人报的 bug：原来整行被 `if (reminderBanner.isNotEmpty())`
            //    包着 ⇒ **本月无提醒时，铃铛/年月/个数/眼睛/收起整行消失**，
            //    而行内的「眼睛」（小飞机开关）是切飞机视图的唯一入口 ⇒ 无提醒月份直接点不到。
            //    （web 原版 CalendarGrid.vue:14 也是 `v-if="... && reminderBanner.length"` 整行隐藏，
            //    但那是功能缺陷，不复刻。）改为**恒显**：空列表时标题显示「0个」、
            //    chip 区与轮播都降级为「暂无提醒」，收起箭头照常可点。
            // ⚠ 仅 todo 变体显示（web `v-if="variant === 'todo' && ..."`）：flow / salary
            //   两个日历共用本组件但不传 reminderBanner，不能把它们也带出这条横幅。
            if (variant == CalendarVariant.Todo) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable { reminderExpanded = !reminderExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VanIcon(name = "bell", size = 18.sp, color = tokens.primary)   // web .dc-reminder-head .van-icon
                    // ⚠ 标题**不走 §7.9 阶梯**（web 14px ⇒ 应是 16f），真机反馈「本月提醒」几个字太大，
                    //   负责人要求独立 **-2sp** ⇒ 14f。（同页其它文字仍按阶梯，勿一并改）
                    if (reminderExpanded) {
                        FText("${year}年${month}月提醒", 14f, FontWeight.SemiBold, colors.textPrimary)
                        FText("${reminderBanner.size}个", 12f, FontWeight.Normal, colors.textTertiary)
                    } else {
                        FText("本月提醒：", 14f, FontWeight.SemiBold, colors.textPrimary)
                        ReminderTicker(reminderBanner)
                    }
                    Spacer(Modifier.weight(1f))
                    // web .dc-reminder-actions：插槽（眼睛）+ 折叠箭头
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // ⚠ 间距从 2dp 拉到 8dp：眼睛外面套着 32dp 点击盒（图标本身才 20sp），
                        //   盒子左右各留 ~6dp 透明边。间距太小时，点箭头容易先命中眼睛盒的边缘 ⇒ 误触切换小飞机。
                        //   8dp 拉开后，两者的**实际可点边界**之间约有 14dp 空隙。
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reminderAction?.invoke()
                        // web 是 20px，真机反馈「和左侧铃铛不一样大、点击面太小易误触」
                        // ⇒ 折叠箭头与铃铛统一 18.sp（眼睛见宿主页 reminderAction，同为 18.sp + 32dp 点击区）
                        VanIcon(
                            name = if (reminderExpanded) "arrow-up" else "arrow-down",
                            size = 18.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
                if (reminderExpanded) {
                    if (reminderBanner.isEmpty()) {
                        FText(
                            "暂无提醒", 12f, FontWeight.Normal, colors.textTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        ReminderChipFlow(reminderBanner, Modifier.padding(top = 8.dp))
                    }
                }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))   // web border-bottom
            }

            // ── ② 星期行 ──
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { i, w ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FText(
                            w, 16f, FontWeight.SemiBold,
                            if (i == 0 || i == 6) tokens.primary.copy(alpha = 0.7f) else colors.textTertiary,
                        )
                    }
                }
            }

            // ── ③ 日网格（高度可折叠 + 上移到选中行） ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bodyHeight)
                    .clipToBounds()
                    .padding(
                        start = 8.dp, end = 8.dp,
                        top = if (collapsed) 0.dp else 5.dp,
                        bottom = if (collapsed) 0.dp else 5.dp,
                    ),
            ) {
                // web 机制（.dc-grid/.dc-body）：网格恒定 6×80 完整渲染，窗口 overflow:hidden + translateY。
                // wrapContentHeight(unbounded) 让固定 80dp 的行不受窗口高度 coerce——否则收起窗口 80dp
                // 会把第 2 行起的行压成 0 高（「收起后整行日期消失」的真正根因）。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = gridOffset },
                ) {
                    cells.chunked(7).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(rowH)) {   // web grid-template-rows: repeat(6, 80px)
                            row.forEach { cell ->
                                DayCell(
                                    cell = cell,
                                    mark = if (cell.date.isNotBlank()) dataset[cell.date] else null,
                                    atToday = selectedDate == today,
                                    variant = variant,
                                    onClick = { if (cell.date.isNotBlank()) onSelect(cell.date) },
                                )
                            }
                        }
                    }
                }
            }

            // ── ④ 折叠条 ──
            if (collapsible) {
                // web `.dc-toggle:active{ opacity: .6 }`
                val togglePress = remember { MutableInteractionSource() }
                val togglePressed by togglePress.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = togglePress,
                            indication = null,          // web 无水波纹
                        ) { expanded = !expanded }
                        .graphicsLayer { alpha = if (togglePressed) 0.6f else 1f }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VanIcon(
                        name = if (expanded) "arrow-up" else "arrow-down",
                        size = 16.sp, color = tokens.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    FText(if (expanded) "收起" else "展开", 14f, FontWeight.Medium, tokens.primary)
                }
            }
        }

        // ── ⑤ 右下角「今」圆环 ──
        if (showTodayJump) {
            // web `.dc-today-btn:active{ transform: scale(.88) }`
            val todayPress = remember { MutableInteractionSource() }
            val todayPressed by todayPress.collectIsPressedAsState()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp)
                    .cssShadowCircle(WebShadow(0f, 4f, 12f, tokens.primary.copy(alpha = 0.35f)))
                    .size(40.dp)
                    .background(colors.bgCard, CircleShape)
                    .border(2.dp, tokens.primary, CircleShape)
                    .graphicsLayer {
                        scaleX = if (todayPressed) 0.88f else 1f
                        scaleY = if (todayPressed) 0.88f else 1f
                    }
                    .clickable(
                        interactionSource = todayPress,
                        indication = null,
                        onClick = onGoToday,
                    ),
            ) {
                FText("今", 16f, FontWeight.Bold, tokens.primary)
            }
        }
    }
}

/** 内部格子模型。 */
private data class CalendarCell(
    val day: Int,
    val date: String,
    val isToday: Boolean,
    val selected: Boolean,
    val row: Int,
)

@Composable
private fun RowScope.DayCell(
    cell: CalendarCell,
    mark: DayMark?,
    atToday: Boolean,
    variant: CalendarVariant,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val level = mark?.reminder
    // web：停在「今天」视图时今日格不闪烁（.dc.at-today）
    val pulsing = level != null && !(cell.isToday && atToday)
    val (bgAlpha, ringAlpha) = pulseAlphas(level ?: "red", pulsing)
    val levelColor = when (level) {
        "yellow" -> VanOrange
        "green" -> Color(0xFF07C160)
        else -> Color(0xFFEE0A24)
    }
    // web `.dc-cell:active:not(.empty){ transform: scale(.85) }`
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            // ⚠ 必须撑满行高：web `.dc-cell` 在 grid 行高（80px）里 `justify-content:center` ⇒ 内容**垂直居中**。
            //   少了这一行，Box 高度只剩内容高（Todo 仅 48dp 的圆）+ Row 默认 Top 对齐 ⇒ 圆**贴行顶**，上方 0 余量，
            //   于是折叠后窗口上沿直接把今日的 12dp 外发光切掉（「圆圈上方被遮挡」的根因）。
            //   撑满后 `contentAlignment = Center` 才真正生效，圆回到行中央（上下各 16dp），发光也装得下。
            //   附带把点击热区从 48dp 修正为整行（web 也是整格可点）。
            .fillMaxHeight()
            // salary 变体：非工作日整格 40% 透明（web `.dc-cell.not-working{opacity:.4}`）
            .alpha(if (variant == CalendarVariant.Salary && mark?.notWorking == true) 0.4f else 1f)
            .graphicsLayer(
                scaleX = if (pressed && cell.day > 0) 0.85f else 1f,
                scaleY = if (pressed && cell.day > 0) 0.85f else 1f,
            )
            .clickable(
                enabled = cell.day > 0,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 日号（48×48 圆）
            // web `.dc-num{ transition: background .2s ease, color .2s ease, box-shadow .2s ease }`
            // ⚠ 脉动态的呼吸由 pulseAlphas 自己驱动，不能再叠 animateColorAsState（两者会互相打断），
            //   故只在非脉动分支启用过渡。
            val staticBg = if (cell.isToday) tokens.primary else Color.Transparent
            val animBg by animateColorAsState(staticBg, tween(200), label = "dcNumBg")
            val staticFg = when {
                cell.isToday -> Color.White
                cell.selected -> tokens.primary
                else -> colors.textPrimary
            }
            val animFg by animateColorAsState(staticFg, tween(200), label = "dcNumFg")
            val numBg = if (pulsing) levelColor.copy(alpha = bgAlpha) else animBg
            // web：`.selected` 的 `color: primary` 会赢过提醒色的 `color`（keyframes 只动 background/box-shadow，不动 color）
            // ⇒ **闪烁日被选中时数字变主色**，这里是"到底选没选中"的第二重提示（第一重是下面的外圈）。
            val numColor = if (pulsing && !cell.selected) levelColor else animFg
            // web：`.dc.at-today` 下今日格的提醒动画被关掉，且字重回落 500（不是 700）
            val numWeight = when {
                pulsing -> FontWeight.Bold
                cell.isToday && level != null && atToday -> FontWeight.Medium
                cell.isToday || cell.selected -> FontWeight.Bold
                else -> FontWeight.Medium
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .let {
                        // web 阴影：今日 `0 3px 12px`、今日+选中 `0 4px 16px`、选中 `0 2px 8px`（均 rgba(primary,.25)）
                        // ⚠ 必须用圆形纯阴影 cssShadowCircle：方形 cssShadow 垫层在圆形/透明背景上会露出黑块
                        //   （「选中日背景发黑」的根因），且选中格背景本就透明。
                        when {
                            pulsing -> it
                            cell.isToday && cell.selected ->
                                it.cssShadowCircle(WebShadow(0f, 4f, 16f, tokens.primary.copy(alpha = 0.25f)))
                            cell.isToday -> it.cssShadowCircle(WebShadow(0f, 3f, 12f, tokens.primary.copy(alpha = 0.25f)))
                            // ⚠ 选中（非今日）**不能**用 cssShadowCircle：它画的是 BlurMaskFilter 的**实心**模糊圆，
                            //   而该格背景是 `Color.Transparent` ⇒ 圆内会漏出 ≈25% 主色填充，看起来像"糊掉的主色圆饼"。
                            //   web 的 CSS 外阴影会被 border-box **裁掉**，圆内什么都不画，只有 inset 2.5px 那圈细环。
                            cell.selected -> it
                            else -> it
                        }
                    }
                    .background(numBg, CircleShape)
                    .then(
                        when {
                            // ⚠ 闪烁日（提醒脉动）**被选中**时：呼吸环会被"选中"语义盖过去就看不见选中态了
                            //   （真机反馈「点了那天根本不知道有没有选中」）。
                            //   故**呼吸环保留**，再在它外面加一圈**主色选中环**——与「今日+选中」的白内/主色外同思路，
                            //   保证"在闪"和"被选中"两个状态能同时看出来。
                            pulsing && cell.selected -> Modifier.drawBehind {
                                val r = size.minDimension / 2f
                                drawCircle(
                                    color = levelColor.copy(alpha = ringAlpha),
                                    radius = r,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                                drawCircle(
                                    color = tokens.primary,
                                    radius = r + 2.5.dp.toPx(),
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }
                            // 提醒脉动：内描边同步呼吸
                            pulsing -> Modifier.border(2.dp, levelColor.copy(alpha = ringAlpha), CircleShape)
                            // 今日且选中：白内圈 + 主色外圈
                            cell.isToday && cell.selected -> Modifier.drawBehind {
                                // web `box-shadow: inset 0 0 0 2.5px #fff, 0 0 0 3px primary`：
                                // 白环在 48 圆**内侧**、主色环在**外侧**，两环必须分离绘制 ——
                                // 链式 .border() 会在同一 outline 上居中描边并互相覆盖（主色外环只剩约 0.25dp）。
                                val r = size.minDimension / 2f
                                drawCircle(
                                    color = tokens.primary,
                                    radius = r + 1.5.dp.toPx(),
                                    center = center,
                                    style = Stroke(width = 3.dp.toPx()),
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = r - 1.25.dp.toPx(),
                                    center = center,
                                    style = Stroke(width = 2.5.dp.toPx()),
                                )
                            }
                            // 选中（非今日）：内描边 + 主题色字（web 明确「不填充背景」）
                            cell.selected -> Modifier.border(2.5.dp, tokens.primary, CircleShape)
                            cell.isToday -> Modifier
                            else -> Modifier
                        },
                    ),
            ) {
                if (cell.day > 0) {
                    FText("${cell.day}", 19f, numWeight, numColor)
                }
            }

            // ── flow 变体：当日收/支金额块（web `.dc-amts`：gap 1、margin 2.5 0、min-height 26、11px/600）──
            if (variant == CalendarVariant.Flow) {
                val inc = mark?.income ?: 0.0
                val exp = mark?.expense ?: 0.0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 26.dp)
                        .padding(vertical = 2.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),   // web `.dc-amts { gap: 1px }`
                ) {
                    if (inc > 0) {
                        // web `abbrMoney`（不是 Home 的 formatMoney）：≥10000 → 2 位小数「万」、整数原样
                        BasicText(
                            "+" + calendarAbbr(inc),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,          // web `text-overflow: ellipsis`
                            style = TextStyle(
                                color = MoneyColor.income, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold,
                                // web `.dc-amt{ font-size:11px→12; line-height:1.15 }`。⚠ 必须显式给 line-height —— 不给 Compose 默认行高更高，两行就顶出 80dp 的行高 ⇒ 金额被裁一半。
                                lineHeight = scaledSp(13.8f),                  // 12 × 1.15
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                ),
                                textAlign = TextAlign.Center,          // web `.dc-amts { align-items: center }`
                            ),
                        )
                    }
                    if (exp > 0) {
                        BasicText(
                            "-" + calendarAbbr(exp),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = MoneyColor.expense, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold,
                                lineHeight = scaledSp(13.8f),                  // 12 × 1.15（web）
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                ),
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }

            // ── salary 变体（web `.dc-amt.formal/.parttime` + `.dc-working`）──
            if (variant == CalendarVariant.Salary) {
                val formalInc = mark?.formalIncome ?: 0.0
                val ptTotal = mark?.parttimeTotal ?: 0.0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 26.dp)
                        .padding(vertical = 2.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 金额统一走 `calendarAbbr`（= web `abbrMoney`；flow 变体同款，别用 Money.abbrev）
                    if (formalInc > 0) {
                        BasicText(
                            "¥" + calendarAbbr(formalInc),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = tokens.primary, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        )
                    }
                    if (ptTotal > 0) {
                        BasicText(
                            "¥" + calendarAbbr(ptTotal),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = VanOrange, fontSize = scaledSp(12f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        )
                    }
                    // 在职但当天还没生成工资记录 → 「计薪」
                    if (mark?.isWorkingDay == true && !mark.notWorking && formalInc == 0.0 && ptTotal == 0.0) {
                        FText("计薪", 12f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }
        }

        // ── todo 变体：事件点 / 小飞机（web：position absolute bottom 3px）──
        if (variant == CalendarVariant.Todo && cell.day > 0) {
            when {
                mark?.airplane == true -> Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                ) { FText("✈️", 15f, FontWeight.Normal, colors.textTertiary) }
                (mark?.eventCount ?: 0) > 0 -> Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(6.dp)
                        .background(if (mark?.overdue == true) colors.danger else tokens.primary, CircleShape),
                )
            }
        }
    }
}

/**
 * web `utils/abbrMoney.js`（**日历网格专用**，与 `Money.abbrev` 不同）：
 * `abs >= 10000` → `(n/10000).toFixed(2) + "万"`（2 位小数）;整数原样输出；其余 `round(2 位)` 去尾零。
 * 例：12345 → `1.23万`（`Money.abbrev` 会给 `1.235万`）、8000 → `8000`（`Money.abbrev` 会给 `8000.00`）。
 */
private fun calendarAbbr(v: Double): String {
    val abs = kotlin.math.abs(v)
    if (abs >= 10000) return String.format(java.util.Locale.US, "%.2f", v / 10000) + "万"
    if (v == kotlin.math.floor(v)) return v.toLong().toString()
    val r = kotlin.math.round(v * 100) / 100
    return r.toString()
}

/** 提醒脉动：返回 (背景 alpha, 内描边 alpha)，按等级用不同周期（红 1s / 黄 1.5s / 绿 2s）。 */
@Composable
private fun pulseAlphas(level: String, enabled: Boolean): Pair<Float, Float> {
    val duration = when (level) {
        "yellow" -> 1500
        "green" -> 2000
        else -> 1000
    }
    val transition = rememberInfiniteTransition(label = "dcPulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
        label = "dcPulseP",
    )
    if (!enabled) return 0f to 0f
    return when (level) {
        "green" -> lerp(0.30f, 0.06f, p) to lerp(0.60f, 0.16f, p)
        else -> lerp(0.32f, 0.08f, p) to lerp(0.65f, 0.20f, p)
    }
}

/** 展开态：换行 chip 列表（web `.dc-reminder-list`：gap 8 + 12px chip + 等级配色）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChipFlow(list: List<ReminderChip>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        list.forEach { chip -> ReminderChipView(chip) }
    }
}

/** 折叠态：单条轮播（web `van-swipe vertical :autoplay="3500"`）。空列表降级为「暂无提醒」（不再抛异常）。 */
@Composable
private fun ReminderTicker(list: List<ReminderChip>) {
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(list.size) {
        if (list.size > 1) while (true) { delay(3500); idx = (idx + 1) % list.size }
    }
    Box(Modifier.padding(start = 6.dp)) {
        if (list.isEmpty()) {
            FText("暂无提醒", 12f, FontWeight.Normal, LocalAppColors.current.textTertiary)
        } else {
            ReminderChipView(list.getOrElse(idx) { list.first() })
        }
    }
}

@Composable
private fun ReminderChipView(chip: ReminderChip) {
    // web `CalendarGrid.vue:427-429` `.dc-reminder-chip.lv-{red,yellow,green}`：
    //   red    bg `var(--van-danger-bg, rgba(238,10,36,.1))`   fg `var(--van-danger-color)` = --theme-danger（:root，两模式）
    //   yellow bg `var(--van-orange-bg, rgba(255,151,106,.12))` fg `var(--van-orange)` = #ff976a（两模式同值）
    //   green  bg `var(--van-green-bg, rgba(7,193,96,.1))`      fg `var(--van-green, #07c160)`
    // ⚠ `--van-*-bg` **只在深色模式**有定义 → 浅色走行内那个 rgba fallback，深色才是 #f0f9eb/#fff0f0/…
    // ⚠ 别和下面格子里的「提醒闪烁」搞混：那个走 `.dc-cell.reminder-*` 的**裸 hex**
    //   （web 注释：「保证任意主题下醒目」），是有意不跟主题的。
    val c0 = LocalAppColors.current
    val (bg, fg) = when (chip.level) {
        "yellow" -> VanOrange.copy(alpha = 0.12f) to VanOrange
        "green" -> vanGreenBg(Color(0x1A07C160)) to vanGreen()
        else -> vanDangerBg(Color(0x1AEE0A24)) to c0.danger
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FText(chip.content, 12f, FontWeight.Medium, fg)
        // web：`{{ r.date.slice(5) }}`（MM-DD），<b> 加粗
        FText(chip.date.drop(5), 12f, FontWeight.Bold, fg)
    }
}
