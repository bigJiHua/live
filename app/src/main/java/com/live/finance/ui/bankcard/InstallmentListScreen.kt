package com.live.finance.ui.bankcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.InstallmentMonth
import com.live.finance.data.model.InstallmentRow
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.icon.VanIcon
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import kotlinx.coroutines.launch

/** 期次状态 → (文字色, 背景色)；对应 web `.st-*` 配色（照抄度量）。 */
private fun statusColors(m: InstallmentMonth): Pair<Color, Color> = when {
    m.rawStatus == "entering" -> Color(0xFFFF976A) to Color(0x1FFF976A)
    m.rawStatus == "void" -> Color(0xFF969799) to Color(0x0D000000)
    m.status == "done" -> Color(0xFF07C160) to Color(0x1A07C160)
    m.overdue -> Color(0xFFEE0A24) to Color(0x1AEE0A24)
    m.status == "entered" -> Color(0xFF07C160) to Color(0x1A07C160)
    else -> Color(0xFF1989FA) to Color(0x1A1989FA)
}

/**
 * 分期列表 —— 一比一复刻 web `views/BankCard/InstallmentList.vue`。
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
    var abortMonths by remember { mutableStateOf<List<Triple<String, Double, Pair<Boolean, Boolean>>>> (emptyList()) } // (month, amount, checked, disabled)

    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    fun load() {
        scope.launch {
            when (val r = graph.recurring.installmentRows()) {
                is ApiResult.Ok -> rows = r.data.orEmpty()
                is ApiResult.Fail -> toast(r.message.ifBlank { "加载失败" })
                else -> Unit
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // 头部统计（web stats 原文口径）
    var pendingCount = 0
    var enteredCount = 0
    rows.forEach { item -> item.months.forEach { m ->
        if (m.rawStatus == "pending") pendingCount++
        if (m.rawStatus == "entered" || m.status == "entered") enteredCount++
    } }

    VanPullRefresh(
        state = refreshState,
        isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
        onRefresh = { scope.launch { load(); refreshState.finishRefresh() } },
    ) {
        ScreenScaffold { inner ->
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(inner)) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFEE0A24), Color(0xFFD91A4A)))).padding(20.dp),
                    ) {
                        FText("分期列表", 20f, FontWeight.Bold, Color.White)
                        Spacer(Modifier.height(4.dp))
                        FText("${rows.size}笔分期 · 未入账${pendingCount}期 · 已入账${enteredCount}期", 13f, color = Color.White.copy(alpha = 0.85f))
                    }
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
                                Triple(m.month, m.amount, Pair(canAbort, !canAbort))
                            }
                            showAbort = true
                        },
                    )
                }
                if (!loading && rows.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            VanEmpty(description = "暂无分期记录")
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    val target = abortTarget
    if (showAbort && target != null) {
        VanPopup(show = true, onDismissRequest = { showAbort = false }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colors.bgCard).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FText("中止分期", 16f, FontWeight.SemiBold, colors.textPrimary, modifier = Modifier.weight(1f))
                    VanIcon("cross", size = 18.sp, color = colors.textTertiary, onClick = { showAbort = false })
                }
                Spacer(Modifier.height(8.dp))
                FText(
                    "勾选需中止的期次（默认已选中所有「待入账」期次，可手动增删）。已入账、逾期未还、超过期限的期次不可选中。",
                    12f, color = colors.textTertiary,
                )
                Spacer(Modifier.height(12.dp))
                val checked = abortMonths.count { it.third.first }
                if (abortMonths.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        FText("该分期暂无可中止期次（均已入账/逾期/超期）", 13f, color = colors.textTertiary)
                    }
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        abortMonths.forEach { m ->
                            val (month, amount, flags) = m
                            val (checked0, disabled) = flags
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !disabled) {
                                    abortMonths = abortMonths.map {
                                        if (it.first == month) Triple(it.first, it.second, Pair(!it.third.first, it.third.second)) else it
                                    }
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AbortCheckbox(checked = checked0, enabled = !disabled, onToggle = {
                                    abortMonths = abortMonths.map {
                                        if (it.first == month) Triple(it.first, it.second, Pair(!it.third.first, it.third.second)) else it
                                    }
                                })
                                Spacer(Modifier.width(10.dp))
                                FText(month, 13f, color = colors.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                FText("￥${Money.formatMoney(amount)}", 13f, FontWeight.Medium, colors.textSecondary)
                                Spacer(Modifier.weight(1f))
                                val mm = target.months.firstOrNull { it.month == month }
                                if (mm != null) {
                                    val (c, bg) = statusColors(mm)
                                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                        FText(mm.tagText, 12f, FontWeight.Medium, c)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppButton(
                    text = "确认中止所选期次",
                    onClick = {
                        val months = abortMonths.filter { it.third.first }.map { it.first }
                        if (months.isEmpty()) { toast("请至少选择一个待入账期次"); return@AppButton }
                        scope.launch {
                            when (val r = graph.recurring.abortMonths(target.id, months)) {
                                is ApiResult.Ok -> { toast("已中止${months.size}期"); showAbort = false; load() }
                                is ApiResult.Fail -> toast(r.message.ifBlank { "中止失败" })
                                else -> toast("中止失败")
                            }
                        }
                    },
                    type = AppButtonType.Danger, block = true, disabled = checked == 0,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AbortCheckbox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier.size(18.dp).alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (checked) colors.primary else Color.Transparent)
            .border(1.dp, if (checked) colors.primary else colors.border, CircleShape)
            .clickable(enabled = enabled) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) FText("✓", 11f, FontWeight.Bold, Color.White)
    }
}

/** 单张分期卡（web `.inst-card`；已结束灰显 60% + grayscale）。 */
@Composable
private fun InstallmentCard(item: InstallmentRow, expanded: Boolean, onToggle: () -> Unit, onAbort: () -> Unit) {
    val colors = LocalAppColors.current
    val ended = item.isActive == 0
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (ended) 0.6f else 1f)
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText(item.name, 15f, FontWeight.SemiBold, colors.textPrimary)
                    if (ended) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF969799)).padding(horizontal = 6.dp, vertical = 0.dp)) {
                            FText("已结束", 11f, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                FText("${item.account.cardName} · ${item.repeatCount}期", 12f, color = colors.textTertiary)
                Spacer(Modifier.height(4.dp))
                FText("每期 ￥${Money.formatMoney(item.amount)}（含利息）", 16f, FontWeight.Bold, Color(0xFFEE0A24))
            }
            if (!ended) {
                AppButton(text = "中止分期", onClick = onAbort, type = AppButtonType.Danger, size = AppButtonSize.Mini, plain = true)
                Spacer(Modifier.width(8.dp))
            }
            VanIcon(if (expanded) "arrow-up" else "arrow-down", size = 16.sp, color = colors.textTertiary)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(10.dp))
            InfoLine("分期总额", "￥${Money.formatMoney(item.account.originalAmount)}")
            InfoLine("利息", "￥${Money.formatMoney(item.account.fee)}")
            InfoLine("期数", "${item.repeatCount}期")
            InfoLine("账单日", "${item.account.billingDay}号")
            Spacer(Modifier.height(8.dp))
            FText("各期状态（系统按入账日自动入账）", 12f, FontWeight.Medium, colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            item.months.forEach { m ->
                val (c, bg) = statusColors(m)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(m.month, 13f, color = colors.textPrimary)
                    if (m.planDate.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        FText("${m.planDate.takeLast(5)}入账", 11f, color = colors.textTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    FText("￥${Money.formatMoney(m.amount)}", 13f, FontWeight.Medium, colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        FText(m.tagText, 12f, FontWeight.Medium, c)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        FText(label, 13f, color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        FText(value, 13f, FontWeight.Medium, colors.textPrimary)
    }
}
