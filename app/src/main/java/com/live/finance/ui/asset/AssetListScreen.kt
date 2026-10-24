package com.live.finance.ui.asset

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.AssetRegister
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.chart.MiniChart
import com.live.finance.theme.VanGreenFixed
import com.live.finance.theme.VanGrayFixed
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanCollapse
import com.live.vant.nav.VanCollapseItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/** mini 折线主色（web 硬编码 #3b82f6，不跟随主题）。 */
private val CHART_BLUE = Color(0xFF3B82F6)
/** web `.offshore-amount .convert { color: var(--van-green) }` 硬编码绿。 */

/**
 * 登记记录（web `views/Finance/assets/List.vue`）：
 * 顶部 mini 总资产走势 → 记录卡（日期头 + 最新/历史标签 + 三总额 + 折叠明细 + 操作）
 * → 底部固定「新增登记」。删除走后端 pinLockGuard（8303 由 ApiClient 全局承接）。
 */
@Composable
fun AssetListScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val repo = App.of(LocalContext.current).graph.asset
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<AssetRegister>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val expanded = remember { mutableStateListOf<String>() }
    var deleteTarget by remember { mutableStateOf<AssetRegister?>(null) }
    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    suspend fun load() {
        loading = true
        rows = when (val r = repo.list()) {
            is ApiResult.Ok -> r.data ?: emptyList()
            else -> { toast.showFail(r, "加载失败"); emptyList() }
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    // 同日取最新（create_time 最大）后按日升序 —— 与 web miniTrend 口径一致
    val miniPoints = remember(rows) {
        rows.groupBy { it.registerDate }
            .mapValues { (_, list) -> list.maxByOrNull { it.createTime.toLongOrNull() ?: 0L }!! }
            .toList()
            .sortedBy { it.first }
            .map { it.first to it.second.totalBalance }
    }

    fun edit(r: AssetRegister) { nav.navigate("${Routes.ASSETS_EDIT}?id=${r.id}") }
    fun copyNew(r: AssetRegister) { nav.navigate("${Routes.ASSETS_REGISTER}?copyId=${r.id}") }

    VanPullRefresh(
        state = refreshState,
        isChildAtTop = {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        },
        onRefresh = {
            ApiGetCache.clear() // 下拉刷新必须绕过 GET 缓存
            scope.launch { load(); refreshState.finishRefresh() }
        },
    ) {
        ScreenScaffold { inner ->
            Box(inner) {
                if (!loading && rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VanEmpty(description = "暂无登记记录")
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(bottom = 86.dp),
                ) {
                    if (miniPoints.isNotEmpty()) {
                        item {
                            MiniTrendCard(
                                points = miniPoints,
                                onGoTrend = { nav.navigate(Routes.ASSETS_TREND) },
                            )
                        }
                    }
                    itemsIndexed(rows, key = { _, r -> r.id }) { index, r ->
                        RecordCard(
                            r = r,
                            latest = index == 0,
                            expanded = expanded.contains(r.id),
                            onToggle = {
                                if (expanded.contains(r.id)) expanded.remove(r.id) else expanded.add(r.id)
                            },
                            onEdit = { edit(r) },
                            onCopy = { copyNew(r) },
                            onDelete = { deleteTarget = r },
                        )
                    }
                }
                // 底部固定「新增登记」
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(colors.bgPage).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    AppButton(
                        text = "新增登记",
                        onClick = { nav.navigate(Routes.ASSETS_REGISTER) },
                        type = AppButtonType.Primary, size = AppButtonSize.Large,
                        block = true, round = true, icon = "plus",
                        height = 50.dp, cornerRadius = 25.dp,
                    )
                }
            }
        }
    }

    // 删除确认（红色确认，对齐 web showConfirmDialog confirmButtonColor=danger）
    val target = deleteTarget
    VanConfirmDialog(
        show = target != null,
        title = "确认删除",
        message = "确定要删除这条登记记录吗？",
        confirmButtonText = "删除",
        onConfirm = {
            val r = target
            deleteTarget = null
            if (r != null) {
                scope.launch {
                    when (val res = repo.delete(r.id)) {
                        is ApiResult.Ok -> { toast.show("删除成功", VanToastType.Success); load() }
                        is ApiResult.Unauthorized -> nav.navigate(Routes.LOGIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                        is ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)
                        is ApiResult.Fail -> toast.show(res.message.ifEmpty { "删除失败" }, VanToastType.Fail)
                        else -> toast.showFail(res, "删除失败")
                    }
                }
            }
        },
        onCancel = { deleteTarget = null },
        onClose = { deleteTarget = null },
    )
}

// ================= 记录卡 =================

@Composable
private fun RecordCard(
    r: AssetRegister,
    latest: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard),
    ) {
        // 日期头
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DateLine("合计日期", AssetShared.formatMillis(r.createTime.ifEmpty { r.registerTime }))
                DateLine("最后更新日期", AssetShared.formatMillis(r.updateTime))
            }
            com.live.vant.basic.VanTag(
                text = if (latest) "最新" else "历史",
                type = if (latest) com.live.vant.basic.VanTagType.Success else com.live.vant.basic.VanTagType.Default,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        // 三总额
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                FText("总资产", 16f, color = colors.textSecondary)
                FText("¥${abbrev2(r.totalBalance)}", 24f, FontWeight.Bold, colors.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FText("资产合计", 12f, color = colors.textTertiary)
                    FText("¥${abbrev2(r.totalAsset)}", 16f, FontWeight.Medium, colors.textPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FText("信用卡欠款", 12f, color = colors.textTertiary)
                    FText("-¥${abbrev2(r.creditDebt)}", 16f, FontWeight.Medium, colors.danger)
                }
            }
        }

        // 折叠明细
        VanCollapse(activeNames = if (expanded) listOf("d") else emptyList(),
            onActiveNamesChange = { onToggle() }) {
            VanCollapseItem(name = "d", title = "查看明细", icon = "orders-o") {
                DetailContent(r)
            }
        }

        // 操作按钮
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (latest) {
                // web .record-actions：三按钮内容宽、居中、gap 8（非等分拉伸）
                AppButton("编辑", onEdit, type = AppButtonType.Primary, round = true)
                Spacer(Modifier.width(8.dp))
                AppButton("追加", onCopy, plain = true, round = true)
                Spacer(Modifier.width(8.dp))
                AppButton("删除", onDelete, type = AppButtonType.Danger, plain = true, round = true)
            } else {
                AppButton("复制继续", onCopy, plain = true, round = true, size = AppButtonSize.Small)
            }
        }
    }
}

@Composable
private fun DateLine(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        VanIcon("clock-o", size = 12.sp, color = colors.textTertiary)
        FText("$label：", 12f, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
        FText(value, 12f, color = colors.textPrimary)
    }
}

@Composable
private fun DetailContent(r: AssetRegister) {
    val colors = LocalAppColors.current
    val d = r.details
    Column(Modifier.padding(vertical = 8.dp)) {
        if (d != null) {
            if (d.balance.isNotEmpty()) {
                // web `assets/List.vue:731` `.region-domestic { border-left-color: var(--van-green) }`
                // （境外是 `--theme-primary`）。⚠ 这里 **没写 fallback**，浅色模式下变量不存在 →
                // `border-left-color` 取初始值 currentColor（即随文字色），属 web 的隐性表现差异；
                // 原生按视觉意图写死 #07c160（= 深色的 --theme-success 默认值）。
                DetailSection("境内资产", "总计 ¥${abbrev2(AssetShared.balanceTotal(d))}",
                    barColor = VanGreenFixed) {
                    d.balance.forEach { item ->
                        DetailRow(
                            name = item.customName.ifEmpty { AssetShared.balanceName(item.type) },
                            value = "¥${abbrev2(item.amount)}",
                        )
                    }
                }
            }
            if (d.offshore.isNotEmpty()) {
                DetailSection("境外资产", "折合 ¥${abbrev2(AssetShared.offshoreTotal(d))}",
                    barColor = colors.primary) {
                    d.offshore.forEach { item ->
                        val valid = AssetShared.hasRate(d.exchangeRates, item.currency)
                        val cny = AssetShared.toCny(item.amount, item.currency, d.exchangeRates)
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            FText(item.customName.ifEmpty { AssetShared.offshoreName(item.type) },
                                14f, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Row {
                                FText("${item.currency} ${abbrev2(item.amount)}",
                                    14f, color = colors.textSecondary)
                                // web `assets/List.vue:782` `.offshore-amount .convert { color: var(--van-green) }`
                                // ⚠ 同样**无 fallback**：浅色下 `color` 是可继承属性 → 取**继承值**（≈文字色）。
                                //   原生按意图写死 #07c160。
                                if (valid) FText("  ≈ ¥${abbrev2(cny)}",
                                    14f, color = VanGreenFixed)
                            }
                        }
                    }
                }
            }
            if (d.debt.isNotEmpty()) {
                DetailSection("信用卡欠款", "欠款 ¥${abbrev2(AssetShared.debtTotal(d))}",
                    barColor = colors.danger) {
                    d.debt.forEach { item ->
                        DetailRow(
                            name = item.customName.ifEmpty { AssetShared.debtName(item.type) },
                            value = "¥${abbrev2(item.amount)}",
                            valueColor = colors.danger,
                        )
                    }
                }
            }
            if (d.exchangeRates.isNotEmpty()) {
                DetailSectionTitle("登记汇率（100外币 = ? 人民币）")
                d.exchangeRates.forEach { (ccy, rate) ->
                    if (rate.isNotEmpty()) {
                        DetailRow(name = ccy, value = "100 $ccy ≈ ¥${abbrev2(rate.toDoubleOrNull() ?: 0.0)}")
                    }
                }
            }
            if (r.remark.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    FText("备注：", 12f, color = colors.textTertiary)
                    FText(r.remark, 12f, color = colors.textSecondary)
                }
            }
        } else {
            FText("无明细数据", 14f, color = colors.textTertiary)
        }
    }
}

@Composable
private fun DetailSection(title: String, total: String, barColor: Color, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.bgPage)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(16.dp).background(barColor))
            FText(title, 16f, FontWeight.Bold, colors.textPrimary, modifier = Modifier.weight(1f).padding(start = 6.dp))
            FText(total, 14f, FontWeight.SemiBold, colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    val colors = LocalAppColors.current
    FText(text, 12f, color = colors.textTertiary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DetailRow(name: String, value: String, valueColor: Color = LocalAppColors.current.textSecondary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FText(name, 14f, color = LocalAppColors.current.textSecondary,
            modifier = Modifier.weight(1f).padding(end = 12.dp))
        FText(value, 14f, color = valueColor)
    }
}

// ================= mini 走势 =================

@Composable
private fun MiniTrendCard(points: List<Pair<String, Double>>, onGoTrend: () -> Unit) {
    val colors = LocalAppColors.current
    val latest = points.lastOrNull()?.second ?: 0.0
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(colors.bgCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FText("总资产走势", 16f, FontWeight.SemiBold, colors.textPrimary)
            FText("最新 ¥${abbrev2(latest)}", 14f, FontWeight.SemiBold, colors.primary,
                modifier = Modifier.weight(1f).padding(start = 12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { onGoTrend() }
                    .background(colors.bgThird).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VanIcon("chart-trending-o", size = 12.sp, color = colors.primary)
                FText("查看趋势", 12f, FontWeight.SemiBold, colors.primary,
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
        MiniChart(
            points = points.map { it.second },
            // web List.vue 的 xAxis.formatter 是 `v => v`，data 就是完整日期串 miniDates
            // ⇒ 原生此前把 date 丢了，X 轴画的是数值序号，与 web 观感完全不一致
            dates = points.map { it.first },
            color = CHART_BLUE,
            showArea = true,
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )
    }
}

/** web List 专用缩写：2 位小数「万亿/亿/万」，否则千分位 2 位（List.vue 本地 formatAmount）。 */
internal fun abbrev2(v: Double): String {
    val a = abs(v)
    val body = when {
        a >= 1e12 -> String.format(java.util.Locale.US, "%.2f万亿", v / 1e12)
        a >= 1e8 -> String.format(java.util.Locale.US, "%.2f亿", v / 1e8)
        a >= 1e4 -> String.format(java.util.Locale.US, "%.2f万", v / 1e4)
        else -> String.format(java.util.Locale.US, "%,.2f", v)
    }
    return body
}
