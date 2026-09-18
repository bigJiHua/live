package com.live.finance.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.MonthStats
import com.live.finance.theme.AppTokens
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.SemanticRole
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.Money
import com.live.finance.ui.common.MoneyColor
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.WebShadow
import com.live.finance.ui.common.cssShadow
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.feedback.VanPopupPosition
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import java.util.Calendar

/**
 * 账本首页 —— 逐像素对齐 web `web/src/views/Finance/index.vue`（+ 全局 `style.css` 的 `.app-card`）。
 *
 * 结构（自上而下，与 web DOM 同序）：
 *  1. `.app-card.finance-header`：月份选择器（`van-picker` 触发）→ 本月总支出/总收入（`van-row` 两列各 50%）
 *     → 结余 + 「记一笔」按钮（`app-button` plain/small/primary/icon=plus）。
 *  2. `.app-card.menu-grid-wrapper`：标题「财务把控中心」+ `app-grid`（3 列、13 项、无行间距、末行左对齐补齐）。
 *  3. `app-popup`（bottom + round）内嵌 `van-picker`（年份 2020~今年+5、月份 1~12，双向滚动选择）。
 *
 * 只读接口：`GET /api/v1/account/stats/month?year=&month=`（web `getMonthStats`，`web/src/utils/api/account.js`），
 * 原生经 `graph.flow.monthStats(year, month)` → `RemoteFlowRepository` → `ApiClient.get`。
 */
@Composable
fun FinanceScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph

    val now = remember { Calendar.getInstance() }
    val yearList = remember { (2020..now.get(Calendar.YEAR) + 5).toList() }  // web pickerColumns: 2020 ~ 今年+5
    val monthList = remember { (1..12).toList() }
    val columns = remember {
        listOf(
            yearList.map { VanPickerOption("${it}年") },
            monthList.map { VanPickerOption("${it}月") },
        )
    }

    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var stats by remember { mutableStateOf(MonthStats()) }
    var pickerShown by remember { mutableStateOf(false) }
    var pickerValue by remember { mutableStateOf(listOf(0, 0)) }

    // web: onMounted → loadMonthStats()，onPickerConfirm 后再次 loadMonthStats()（切月即重取）
    LaunchedEffect(year, month) {
        when (val r = graph.flow.monthStats(year, month)) {
            is ApiResult.Ok -> stats = r.data ?: MonthStats()
            // 下面三个分支对齐 web 全局响应拦截器 `utils/request/interceptors/response.js`：
            ApiResult.Unauthorized -> {   // 业务 401 → forceLogin()
                toast.show("登录已失效，请重新登录")
                graph.auth.logout()
                nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
            }
            ApiResult.RateLimited -> nav.navigate(Routes.ERROR_429)  // 429 → window.location.href = "/429"
            else -> toast.show("加载失败")  // 其余（含 Fail）→ web loadMonthStats 的 catch 分支
        }
    }

    ScreenScaffold { inner ->
        Column(
            inner
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),  // web .page-finance { padding: 8px 16px }
        ) {
            // ───────── ① 月度收支头卡（web .app-card.finance-header） ─────────
            WebCard(
                shadow = WebShadow(0f, 4f, 12f, Color.Black.copy(alpha = 0.03f)),  // web box-shadow 覆盖值
                contentPadding = PaddingValues(20.dp),
            ) {
                // 月份选择器：14px 次级色 + 数字 num-font（粗体）+ arrow-down（1em，继承 14px）
                Row(
                    Modifier
                        .padding(bottom = 15.dp)  // web margin-bottom: 15px
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            pickerValue = listOf(
                                (year - yearList.first()).coerceIn(0, yearList.lastIndex),
                                (month - 1).coerceIn(0, monthList.lastIndex),
                            )
                            pickerShown = true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = monthLabel(year, month),
                        style = TextStyle(color = colors.textSecondary, fontSize = 14.sp),
                    )
                    Spacer(Modifier.width(4.dp))  // web gap: 4px
                    VanIcon(name = "arrow-down", size = 14.sp, color = colors.textSecondary)
                }

                // van-row + 两个 span=12 的 van-col（无 gutter → 各占 50%）
                // 金额缩略一律走共享件 Money.abbrev（= web formatAmount 同一算法：≥万/亿 缩写 + 2~3 位小数、无千分位）
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        FText("本月总支出：", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(bottom = 4.dp))
                        FText(Money.abbrev(stats.expense), 24f, FontWeight.Bold, MoneyColor.expense)  // --money-expense
                    }
                    Column(Modifier.weight(1f)) {
                        FText("本月总收入：", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(bottom = 4.dp))
                        FText(Money.abbrev(stats.income), 24f, FontWeight.Bold, MoneyColor.income)  // --money-income
                    }
                }

                // .header-footer { margin-top:18px; padding-top:15px; space-between; align-center }
                Row(
                    Modifier.fillMaxWidth().padding(top = 33.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row {
                        FText("结余：", 12f, FontWeight.Normal, colors.textTertiary, Modifier.alignByBaseline())
                        // .net-assets .val：14px / 500（覆盖 num-font 的 bold）
                        FText("￥${Money.abbrev(stats.balance)}", 14f, FontWeight.Medium, colors.textPrimary, Modifier.alignByBaseline())
                    }
                    // app-button plain size=small type=primary icon=plus + .quick-add-btn（0 15px / 32 / radius 8）
                    AppButton(
                        text = "记一笔",
                        type = AppButtonType.Primary,
                        plain = true,
                        size = AppButtonSize.Small,
                        icon = "plus",
                        height = 32.dp,       // .quick-add-btn { height: 32px }
                        hPadding = 15.dp,     // .quick-add-btn { padding: 0 15px }
                        cornerRadius = 8.dp,  // .quick-add-btn { border-radius: 8px }
                        onClick = { nav.navigate(Routes.ACCOUNT_ADD) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))  // web .finance-header { margin-bottom: 16px }

            // ───────── ② 财务把控中心（web .app-card.menu-grid-wrapper） ─────────
            WebCard(
                shadow = WebShadow(0f, 4f, 16f, Color.Black.copy(alpha = 0.06f)),  // web --app-shadow
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            ) {
                FText(
                    "财务把控中心", 14f, FontWeight.Bold, colors.textPrimary,
                    Modifier.padding(start = 10.dp).padding(bottom = 15.dp),
                )
                // app-grid：repeat(3, minmax(0,1fr))，无 gap；13 项 → 4 整行 + 末行 1 项（左对齐）
                financeGrid(tokens).chunked(3).forEach { rowCells ->
                    Row(Modifier.fillMaxWidth()) {
                        rowCells.forEach { cell ->
                            GridCell(
                                entry = cell,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // 目标屏未落地的项（信用卡专项/资产结构登记）不跳转、不点崩
                                    if (nav.graph.findNode(cell.route) != null) nav.navigate(cell.route)
                                    else toast.show("该页面尚未实现")
                                },
                            )
                        }
                        repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))  // web .menu-grid-wrapper { margin-bottom: 16px }
        }
    }

    // ───────── ③ 月份选择弹层（app-popup position=bottom round + van-picker） ─────────
    VanPopup(
        show = pickerShown,
        onDismissRequest = { pickerShown = false },
        position = VanPopupPosition.Bottom,
        round = true,
    ) {
        VanPicker(
            columns = columns,
            value = pickerValue,
            onValueChange = { pickerValue = it },
            onConfirm = { idx, _ ->
                year = yearList.getOrElse(idx.getOrElse(0) { 0 }) { year }
                month = monthList.getOrElse(idx.getOrElse(1) { 0 }) { month }
                pickerShown = false
            },
            onCancel = { pickerShown = false },
            title = "选择月份",
        )
    }
}

// ───────────────────────────────── 九宫格数据（顺序/图标/颜色逐条对齐 web 模板） ─────────────────────────────────

/** 一项宫格：文案 + van-icon 名 + 图标色（已按 mono 规则解析）+ 目标路由。 */
private data class GridEntry(val label: String, val icon: String, val color: Color, val route: String)

/**
 * web 模板顺序：系统账户余额→流水明细→固定资产→信用卡专项→信用卡账单→还款记录→资产结构登记→登记记录→预算表→固定事件→报表→理财投资→数据管理。
 * 图标色统一走 [AppTokens.semanticIcon]：默认三套浅色主题保留彩色，其余主题（mono）统一成主色 —— 对齐 web `html[data-theme-mono="1"]`。
 */
private fun financeGrid(tokens: AppTokens): List<GridEntry> = listOf(
    GridEntry("系统账户余额", "gem", tokens.semanticIcon(SemanticRole.Purple), Routes.ACCOUNT_STRUCTURE),        // /finance/structure
    GridEntry("流水明细", "orders-o", tokens.semanticIcon(SemanticRole.Blue), Routes.FLOW_LIST),               // /finance/flow
    GridEntry("固定资产", "shop-o", tokens.semanticIcon(SemanticRole.Teal), Routes.FIXED_ASSET),               // /finance/fixed-asset
    GridEntry("信用卡专项", "credit-pay", tokens.semanticIcon(SemanticRole.Orange), Routes.CREDIT_CENTER),      // /credit-center（原生占位）
    GridEntry("信用卡账单", "todo-list-o", tokens.semanticIcon(SemanticRole.Red), Routes.BILL_LIST),            // /card/bill/list
    GridEntry("还款记录", "paid", tokens.semanticIcon(SemanticRole.Green), Routes.REPAY_LIST),                  // /card/repay/list
    GridEntry("资产结构登记", "location-o", tokens.semanticIcon(SemanticRole.Teal), Routes.ASSETS_REGISTER),     // /finance/assets/register（原生占位）
    GridEntry("登记记录", "todo-list-o", tokens.semanticIcon(SemanticRole.Gray), Routes.ASSETS_LIST),           // /finance/assets/list
    GridEntry("预算表", "balance-list", tokens.semanticIcon(SemanticRole.Cyan), Routes.BUDGET),                 // /finance/budget
    GridEntry("固定事件", "clock-o", tokens.semanticIcon(SemanticRole.Pink), Routes.EVENTS),                    // /finance/events
    GridEntry("报表", "chart-trending-o", tokens.semanticIcon(SemanticRole.Blue), Routes.REPORT),               // /finance/report 报表中心入口页
    GridEntry("理财投资", "gold-coin-o", tokens.semanticIcon(SemanticRole.Gold), Routes.FUND),                  // /finance/report/fund
    GridEntry("数据管理", "notes-o", tokens.semanticIcon(SemanticRole.Gold), Routes.DATA_MANAGE),               // /finance/data
)

// ───────────────────────────────── 复刻用的基础件 ─────────────────────────────────

/** web `.app-card`：卡片底色 + radius 20 + box-shadow（阴影走共享件 `cssShadow`）。 */
@Composable
private fun WebCard(
    shadow: WebShadow,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .cssShadow(radius = 20.dp, shadow = shadow)
            .background(colors.bgCard, RoundedCornerShape(20.dp))
            .padding(contentPadding),
        content = content,
    )
}

/** web `app-grid-item`：padding 12px 4px；图标 26px；按下态 = 三级底 + opacity .7 + radius 10。 */
@Composable
private fun GridCell(entry: GridEntry, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))  // .is-clickable { border-radius: 10px }
            .background(if (pressed) colors.bgThird else Color.Transparent)
            .alpha(if (pressed) 0.7f else 1f)  // :active { opacity: .7 }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // .grid-icon 26px + margin-bottom 8，叠加 .app-grid-item__icon 的 margin-bottom 8 → 合计 16
        VanIcon(
            name = entry.icon,
            size = 26.sp,
            color = entry.color,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FText(entry.label, 13f, FontWeight.Normal, colors.textSecondary)
    }
}

/** 「2026年9月」：数字用 num-font（粗体），年月二字常规字重。 */
private fun monthLabel(year: Int, month: Int): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(year.toString()) }
    append("年")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(month.toString()) }
    append("月")
}
