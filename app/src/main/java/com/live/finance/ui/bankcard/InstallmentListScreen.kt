package com.live.finance.ui.bankcard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.core.net.failToastType
import com.live.finance.data.model.InstallmentMonth
import com.live.finance.data.model.InstallmentRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.VanGrayFixed
import com.live.finance.theme.vanBlue
import com.live.finance.theme.vanRed
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.grayscale
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.VanToastType
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.form.VanCheckbox
import com.live.vant.icon.VanIcon
import com.live.vant.theme.VantColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 中止弹窗里的一行（month, amount, 是否勾选, 是否禁用）。 */
private data class AbortRow(val month: String, val amount: Double, val checked: Boolean, val disabled: Boolean)

/**
 * 期次状态 → (文字色, 背景色)；对应 web `InstallmentList.vue` 的 `.ms-status.st-*` / `.ai-tag.tag-*`。
 *
 * 文字色用的是 **`:root` 里的 Vant 官方别名**（两模式都跟主题），**不是**业务那套 `--van-green`：
 *   `st-pending`  `var(--van-blue, #1989fa)`          → 浅色 #1989fa / 深色 --theme-primary（[blue] 传入）
 *   `st-entering` `var(--van-warning-color, #ff976a)` → --theme-warning
 *   `st-entered`  `var(--van-success-color, #07c160)` → --theme-success
 *   `st-overdue`  `var(--van-danger-color, #ee0a24)`  → --theme-danger
 *   `st-done`     `var(--van-success-color, #07c160)` → --theme-success
 *   `st-void`     `var(--theme-text-tertiary)`
 * 背景色全是页面里写死的**裸 rgba**，两模式不变。
 */
private fun statusColors(m: InstallmentMonth, colors: VantColors, blue: Color): Pair<Color, Color> = when {
    m.rawStatus == "entering" -> colors.warning to Color(0x1FFF976A) // rgba(255,151,106,.12)
    m.rawStatus == "void" -> colors.textTertiary to Color(0x0D000000) // rgba(0,0,0,.05)
    m.status == "done" -> colors.success to Color(0x1A07C160) // rgba(7,193,96,.1)
    m.overdue -> colors.danger to Color(0x1AEE0A24) // rgba(238,10,36,.1)
    m.status == "entered" -> colors.success to Color(0x1A07C160)
    else -> blue to Color(0x1A1989FA) // rgba(25,137,250,.1)
}

/**
 * 分期列表 —— 一比一复刻 web `views/BankCard/InstallmentList.vue`。
 *
 * 布局口径（照 web 样式逐条对）：
 *  - `.page-inst-list{padding:12px 16px 100px}` → 首块 `top=12` + 左右 16、末尾 `Spacer(100.dp)`。
 *  - `.header-card{border-radius:12px; padding:20px; margin-bottom:16px}`，渐变 `--van-danger-color → --van-danger-grad`。
 *  - `.inst-card{border-radius:8px; padding:14px; margin-bottom:8px}`；已结束的卡片
 *    `.inst-card.ended{opacity:.6; filter:grayscale(1)}`。
 *  - `.inst-amount-row` 是**三段不同字号**：`每期`(13px→14, text-primary) + `￥金额`(16px→18, 700, danger)
 *    + `（含利息）`(11px→12, text-tertiary)，`gap:2px`。
 *  - `.ms-row` / `.info-row` / `.abort-item` 的间距与配色见下方各处注释。
 *
 * 「结清」由系统按 plan_date 自动入账，本页只提供**中止**（多选期次）与查看，无编辑/删除入口。
 */
@Composable
fun InstallmentListScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val graph = App.of(context).graph
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    var rows by remember { mutableStateOf<List<InstallmentRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf("") }
    var showAbort by remember { mutableStateOf(false) }
    var abortTarget by remember { mutableStateOf<InstallmentRow?>(null) }
    var abortMonths by remember { mutableStateOf<List<AbortRow>>(emptyList()) }

    val vanToast = LocalVanToastController.current
    fun toast(m: String, type: VanToastType = VanToastType.Text) = vanToast.show(m, type)

    /** web 有两条不同文案：`loadData` 用「加载失败」、`onRefresh` 用「刷新失败」。 */
    /** @return 本次请求的 Job —— 下拉刷新要 join() 完再收刷新头。 */
    fun load(failText: String): Job {
        return scope.launch {
            when (val r = graph.recurring.installmentRows()) {
                is ApiResult.Ok -> rows = r.data.orEmpty()
                is ApiResult.Fail -> toast(r.message.ifBlank { failText }, VanToastType.Fail)
                else -> Unit
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load("加载失败") }

    // 头部统计（web stats 原文口径）
    var pendingCount = 0
    var enteredCount = 0
    rows.forEach { item -> item.months.forEach { m ->
        if (m.rawStatus == "pending") pendingCount++
        if (m.rawStatus == "entered" || m.status == "entered") enteredCount++
    } }

    VanPullRefresh(
        state = refreshState,
        successText = "刷新成功",
        isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
        onRefresh = { ApiGetCache.clear(); scope.launch { load("刷新失败").join(); refreshState.finishRefresh() } },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                item {
                    // .header-card：左右 16、上 12（页面 padding）、margin-bottom 16
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(colors.danger, vanRed(Color(0xFFD91A4A)))))
                            .padding(20.dp),
                    ) {
                        FText("分期列表", 20f, FontWeight.Bold, Color.White)
                        Spacer(Modifier.height(6.dp)) // .header-sub{ margin-top: 6px }
                        FText("${rows.size}笔分期 · 未入账${pendingCount}期 · 已入账${enteredCount}期", 14f, color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                items(rows.size, key = { rows[it].id }) { i ->
                    InstallmentCard(
                        item = rows[i],
                        expanded = expandedId == rows[i].id,
                        onToggle = { expandedId = if (expandedId == rows[i].id) "" else rows[i].id },
                        onAbort = {
                            val item = rows[i]
                            abortTarget = item
                            abortMonths = item.months.map { m ->
                                val entered = m.status == "entered" || m.status == "done" || m.overdue
                                val expired = m.rawStatus == "void"
                                val canAbort = (m.rawStatus == "pending" || m.rawStatus == "entering") && !entered && !expired
                                AbortRow(m.month, m.amount, checked = canAbort, disabled = !canAbort)
                            }
                            showAbort = true
                        },
                    )
                }
                if (!loading && rows.isEmpty()) {
                    // web `<van-empty description="暂无分期记录" />`：Vant 自带 padding，**不要**再套自定义间距
                    item { VanEmpty(description = "暂无分期记录") }
                }
                item { Spacer(Modifier.height(100.dp)) } // .page-inst-list{ padding-bottom: 100px }
            }
        }
    }

    val target = abortTarget
    if (showAbort && target != null) {
        val panelMax = (LocalConfiguration.current.screenHeightDp * 0.7f).dp // .abort-panel{ max-height: 70vh }
        VanPopup(show = true, onDismissRequest = { showAbort = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard)) {
                // .abort-panel{ padding:16px 16px 24px; max-height:70vh; overflow-y:auto } —— **整个面板滚动**
                Column(
                    Modifier.fillMaxWidth().heightIn(max = panelMax).verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FText("中止分期", 18f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.weight(1f))
                        VanIcon("cross", size = 18.sp, color = colors.textTertiary, onClick = { showAbort = false })
                    }
                    Spacer(Modifier.height(8.dp)) // .abort-tip{ margin: 8px 0 12px }
                    FText(
                        "勾选需中止的期次（默认已选中所有「待入账」期次，可手动增删）。已入账、逾期未还、超过期限的期次不可选中。",
                        12f, color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (abortMonths.isEmpty()) {
                        VanEmpty(description = "该分期暂无可中止期次（均已入账/逾期/超期）")
                    } else {
                        // .abort-list{ display:flex; flex-direction:column; gap:4px }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            abortMonths.forEach { row ->
                                val toggle = {
                                    abortMonths = abortMonths.map {
                                        if (it.month == row.month) it.copy(checked = !it.checked) else it
                                    }
                                }
                                // .abort-item.is-disabled{ opacity:.55 } —— web 是整行统一 0.55。
                                // ⚠ 不能把 alpha 加在整行上：`VanCheckbox` 在 disabled 时**自己**还会再乘 0.5，
                                //    叠起来只有 0.275（明显偏淡）。故勾选框交给它自己置灰，文字/标签各自乘 0.55。
                                val dim = if (row.disabled) 0.55f else 1f
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable(enabled = !row.disabled) { toggle() }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    VanCheckbox(
                                        checked = row.checked,
                                        onCheckedChange = { toggle() },
                                        disabled = row.disabled,
                                        iconSize = 20, // van-checkbox 默认 20px（不是 18）
                                    )
                                    Spacer(Modifier.width(8.dp)) // .abort-item{ gap: 8px }
                                    FText(row.month, 14f, FontWeight.Medium, colors.textPrimary, Modifier.alpha(dim)) // .ai-month
                                    Spacer(Modifier.width(8.dp))
                                    FText("￥${Money.formatMoney(row.amount)}", 14f, color = colors.danger, modifier = Modifier.alpha(dim)) // .ai-amount（danger）
                                    Spacer(Modifier.weight(1f)) // .ai-tag{ margin-left: auto }
                                    target.months.firstOrNull { it.month == row.month }?.let { mm ->
                                        val (c, bg) = statusColors(mm, colors, vanBlue())
                                        Box(Modifier.alpha(dim)) { StatusTag(mm.tagText, c, bg) }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp)) // .abort-actions{ margin-top: 16px }
                    AppButton(
                        text = "确认中止所选期次",
                        onClick = {
                            val months = abortMonths.filter { it.checked }.map { it.month }
                            if (months.isEmpty()) { toast("请至少选择一个待入账期次"); return@AppButton }
                            scope.launch {
                                when (val r = graph.recurring.abortMonths(target.id, months)) {
                                    // web `confirmAbort`：按后端返回的 aborted/skipped/finished 组合文案
                                    is ApiResult.Ok -> {
                                        val a = r.data
                                        val aborted = a?.aborted.orEmpty()
                                        val skipped = a?.skipped.orEmpty()
                                        when {
                                            aborted.isEmpty() && skipped.isEmpty() -> toast("未选中可中止期次")
                                            aborted.isNotEmpty() && skipped.isNotEmpty() ->
                                                toast("已中止${aborted.size}期；${skipped.size}期跳过：${skipped.joinToString("；") { it.second }}")
                                            aborted.isNotEmpty() ->
                                                toast(if (a?.finished == true) "已中止全部${aborted.size}期，分期结束" else "已中止${aborted.size}期")
                                            else -> toast("未能中止：${skipped.joinToString("；") { it.second }}")
                                        }
                                        showAbort = false; load("加载失败")
                                    }
                                    is ApiResult.Fail -> toast(r.message.ifBlank { "中止失败" }, VanToastType.Fail)
                                    else -> toast(r.failMessage("中止失败"), r.failToastType())
                                }
                            }
                        },
                        type = AppButtonType.Danger, block = true, disabled = abortMonths.none { it.checked },
                    )
                }
            }
        }
    }
}

/** 状态小标签（web `.ms-status` / `.ai-tag`：`padding:1px 6px; border-radius:4px; font-size:12px; weight:500`）。 */
@Composable
private fun StatusTag(text: String, color: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        FText(text, 12f, FontWeight.Medium, color)
    }
}

/** 单张分期卡（web `.inst-card`）。 */
@Composable
private fun InstallmentCard(item: InstallmentRow, expanded: Boolean, onToggle: () -> Unit, onAbort: () -> Unit) {
    val colors = LocalAppColors.current
    val ended = item.isActive == 0
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp).padding(bottom = 8.dp) // .inst-card{ margin-bottom: 8px }
            .grayscale(ended)
            .alpha(if (ended) 0.6f else 1f)
            .clip(RoundedCornerShape(8.dp)) // border-radius: 8px
            .background(colors.bgCard)
            .padding(14.dp), // padding: 14px
    ) {
        // .inst-head{ display:flex; justify-content:space-between; align-items:center }
        Row(Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(item.name, 16f, FontWeight.SemiBold, colors.textPrimary) // .inst-name{15px→16; 600}
                    if (ended) {
                        Spacer(Modifier.width(6.dp)) // margin-left: 6px
                        EndedBadge()
                    }
                }
                Spacer(Modifier.height(2.dp)) // .inst-meta{ margin-top: 2px }
                FText("${item.account.cardName} · ${item.repeatCount}期", 12f, color = colors.textTertiary)
                Spacer(Modifier.height(6.dp)) // .inst-amount-row{ margin-top: 6px }
                // .inst-amount-row：三段不同字号，gap: 2px
                Row(verticalAlignment = Alignment.Bottom) {
                    FText("每期", 14f, color = colors.textPrimary) // 13px→14
                    Spacer(Modifier.width(2.dp))
                    FText("￥${Money.formatMoney(item.amount)}", 18f, FontWeight.Bold, colors.danger) // 16px→18; 700
                    Spacer(Modifier.width(2.dp))
                    FText("（含利息）", 12f, color = colors.textTertiary) // 11px→12
                }
            }
            // .inst-actions{ gap: 12px }
            if (!ended) {
                AppButton(text = "中止分期", onClick = onAbort, type = AppButtonType.Danger, size = AppButtonSize.Mini, plain = true)
                Spacer(Modifier.width(12.dp))
            }
            VanIcon(if (expanded) "arrow-up" else "arrow-down", size = 16.sp, color = colors.textTertiary)
        }

        if (expanded) {
            // .expand-body{ margin-top: 4px } + `<van-divider/>` 的 margin: 16px 0
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(16.dp))
            // .info-row：两端对齐、padding 6px 0、14px→16、**text-secondary**（label 与值同色同字重）
            InfoLine("分期总额", "￥${Money.formatMoney(item.account.originalAmount)}")
            InfoLine("利息", "￥${Money.formatMoney(item.account.fee)}")
            InfoLine("期数", "${item.repeatCount}期")
            InfoLine("账单日", "${item.account.billingDay}号")

            Spacer(Modifier.height(12.dp)) // .months-section{ margin-top: 12px }
            // .ms-title{ 13px→14; 600; text-secondary; margin-bottom: 6px }
            FText("各期状态（系统按入账日自动入账）", 14f, FontWeight.SemiBold, colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            item.months.forEach { m -> MonthRow(m) }
        }
    }
}

/** web `.ended-badge`：`padding:0 6px; font-size:11px→12; line-height:16px; border-radius:8px; background: var(--van-gray-6,#969799)`。 */
@Composable
private fun EndedBadge() {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(VanGrayFixed)
            .padding(horizontal = 6.dp).height(16.dp), // line-height: 16px → 固定行高盒
        contentAlignment = Alignment.Center,
    ) { FText("已结束", 12f, color = Color.White) }
}

/** web `.ms-row`：`display:flex; align-items:center; gap:8px; padding:6px 0; font-size:13px`。 */
@Composable
private fun MonthRow(m: InstallmentMonth) {
    val colors = LocalAppColors.current
    val (c, bg) = statusColors(m, colors, vanBlue())
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .ms-date{ color:text-primary; font-weight:500; min-width:64px }
        FText(m.month, 14f, FontWeight.Medium, colors.textPrimary, Modifier.widthIn(min = 64.dp))
        if (m.planDate.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            // .ms-plan{ font-size:11px→12; color:text-tertiary; padding:1px 5px; border-radius:4px; background: rgba(0,0,0,.04) }
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x0A000000)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                FText("${m.planDate.takeLast(5)}入账", 12f, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.weight(1f))
        // .ms-amount{ color: var(--van-danger-color); font-weight:500 }
        FText("￥${Money.formatMoney(m.amount)}", 14f, FontWeight.Medium, colors.danger)
        Spacer(Modifier.width(8.dp))
        StatusTag(m.tagText, c, bg)
    }
}

/** web `.info-row{ display:flex; justify-content:space-between; padding:6px 0; font-size:14px; color:text-secondary }`。 */
@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FText(label, 16f, color = colors.textSecondary) // 14px→16
        FText(value, 16f, color = colors.textSecondary)
    }
}
