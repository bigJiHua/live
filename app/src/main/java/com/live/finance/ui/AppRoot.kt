package com.live.finance.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.resolveTokens
import com.live.finance.ui.auth.LoginScreen
import com.live.finance.ui.error.Error429Screen
import com.live.finance.ui.flow.FlowListScreen
import com.live.finance.ui.main.MainScreen
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastHost
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.theme.VantTheme
import androidx.core.view.WindowCompat

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = com.live.finance.App.of(context).graph
    val systemDark = isSystemInDarkTheme()
    // 「预设 + 系统深浅」→ 完整 token 集（= web useUiTheme 的 buildVars + applyTheme）
    val choice = graph.themeChoice.value
    val tokens = remember(choice, systemDark) { resolveTokens(choice, systemDark) }

    // 系统栏图标明暗 + 窗口底色（对应 web `html{background}` + `<meta name="theme-color">`，
    // 避免深色主题下状态栏图标取色相反、overscroll 露白）
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !tokens.isDark
            isAppearanceLightNavigationBars = !tokens.isDark
        }
        window.setBackgroundDrawable(ColorDrawable(tokens.bgPage.toArgb()))
    }

    VantTheme(colors = tokens.vant) {
        val toast = rememberVanToastController()
        CompositionLocalProvider(
            LocalAppColors provides tokens.vant,
            LocalAppTokens provides tokens,
            LocalVanToastController provides toast,
        ) {
            // 等 session.load() 完成后再合成导航，避免首帧 token 还没读出 → 误判为未登录
            if (graph.sessionReady.value) {
                AppNavHost()
            } else {
                Box(Modifier.fillMaxSize().background(tokens.bgPage))
            }
            VanToastHost(toast)
            PinGateHost()
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    // 启动首屏：持久 token 存在即直接进入主页面（对应 web 信任 localStorage 登录态），否则登录页。
    val graph = App.of(LocalContext.current).graph
    val startDestination = if (graph.session.token.isNullOrEmpty()) Routes.LOGIN else Routes.MAIN
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.LOGIN) { LoginScreen(nav) }
        composable(Routes.MAIN) { MainScreen(nav) }
        composable(Routes.FLOW_LIST) { FlowListScreen(nav) }
        composable(Routes.FLOW_CALENDAR) { com.live.finance.ui.flow.CalendarScreen(nav) }
        composable(Routes.CARD) { com.live.finance.ui.bankcard.CardManageScreen(nav, "debit") }
        composable(Routes.CARD_DEBIT) { com.live.finance.ui.bankcard.CardManageScreen(nav, "debit") }
        composable(Routes.CARD_CREDIT) { com.live.finance.ui.bankcard.CardManageScreen(nav, "credit") }
        composable(Routes.CARD_ADD) { com.live.finance.ui.bankcard.CardAddScreen(nav) }
        composable(Routes.CREDIT_FULL) { com.live.finance.ui.bankcard.CreditFullScreen(nav) }
        composable(Routes.CREDIT_FOREIGN_REGISTER) { com.live.finance.ui.bankcard.ForeignScreen(nav) }
        composable(Routes.CARD_EDIT_PAT) { back ->
            com.live.finance.ui.bankcard.CardEditScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(Routes.ACCOUNT_ADD) { com.live.finance.ui.account.AddTransactionScreen(nav) }
        composable(Routes.ACCOUNT_QUICK_ADD) { com.live.finance.ui.account.QuickAddScreen(nav) }
        composable(Routes.ASSETS_LIST) { com.live.finance.ui.asset.AssetListScreen(nav) }
        // 资产结构登记（新增；?copyId= 为「追加/复制继续」从历史记录复制明细）
        composable(
            route = "${Routes.ASSETS_REGISTER}?copyId={copyId}",
            arguments = listOf(androidx.navigation.navArgument("copyId") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.asset.AssetEditorScreen(
                nav, edit = false,
                copyFromId = back.arguments?.getString("copyId")?.ifEmpty { null },
            )
        }
        // 资产登记编辑（web /finance/assets/edit?id=）
        composable(
            route = "${Routes.ASSETS_EDIT}?id={id}",
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.asset.AssetEditorScreen(
                nav, edit = true,
                recordId = back.arguments?.getString("id")?.ifEmpty { null },
            )
        }
        // 资产走势
        composable(Routes.ASSETS_TREND) { com.live.finance.ui.asset.AssetTrendScreen(nav) }
        composable(Routes.CATEGORY_MANAGE) { com.live.finance.ui.user.CategoryManageScreen(nav) }
        composable(Routes.APP_SETTINGS) { com.live.finance.ui.user.AppSettingsScreen(nav) }
        composable(Routes.FIXED_ASSET) { com.live.finance.ui.fixed.FixedAssetListScreen(nav) }
        // 固定资产：详情 / 编辑 / 回收站（web `finance/fixed-asset/{detail, edit}/:id` + `/recycle`）
        composable(
            route = Routes.FIXED_ASSET_DETAIL,
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.fixed.FixedAssetDetailScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(
            route = Routes.FIXED_ASSET_EDIT,
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.fixed.FixedAssetEditScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(Routes.FIXED_ASSET_RECYCLE) { com.live.finance.ui.fixed.FixedAssetRecycleBinScreen(nav) }
        composable(Routes.BUDGET) { com.live.finance.ui.budget.BudgetListScreen(nav) }
        // 预算：类型选择 + 三类型表单（?id= 为编辑模式）+ 详情
        composable(Routes.BUDGET_TYPE_SELECT) { com.live.finance.ui.budget.BudgetTypeSelectScreen(nav) }
        composable(
            route = "${Routes.BUDGET_SHOPPING}?id={id}",
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { e -> com.live.finance.ui.budget.BudgetFormScreen(nav, "买", e.arguments?.getString("id")?.ifEmpty { null }) }
        composable(
            route = "${Routes.BUDGET_TRAVEL}?id={id}",
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { e -> com.live.finance.ui.budget.BudgetFormScreen(nav, "行", e.arguments?.getString("id")?.ifEmpty { null }) }
        composable(
            route = "${Routes.BUDGET_EAT}?id={id}",
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { e -> com.live.finance.ui.budget.BudgetFormScreen(nav, "吃", e.arguments?.getString("id")?.ifEmpty { null }) }
        composable(
            route = Routes.BUDGET_DETAIL,
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { e -> com.live.finance.ui.budget.BudgetDetailScreen(nav, e.arguments?.getString("id").orEmpty()) }
        composable(Routes.REPAY_LIST) { com.live.finance.ui.bankcard.RepayListScreen(nav) }
        composable("${Routes.REPAY_DETAIL}?id={id}", arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" })) { e ->
            com.live.finance.ui.bankcard.RepayDetailScreen(nav, e.arguments?.getString("id").orEmpty())
        }
        composable(
            "${Routes.REPAY_ADD}?billId={billId}&mergePoolId={mergePoolId}",
            arguments = listOf(
                androidx.navigation.navArgument("billId") { defaultValue = "" },
                androidx.navigation.navArgument("mergePoolId") { defaultValue = "" },
            ),
        ) { e ->
            com.live.finance.ui.bankcard.RepayAddScreen(
                nav,
                e.arguments?.getString("billId").orEmpty(),
                e.arguments?.getString("mergePoolId").orEmpty(),
            )
        }
        composable("${Routes.REPAY_EDIT}?id={id}", arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" })) { e ->
            com.live.finance.ui.bankcard.RepayEditScreen(nav, e.arguments?.getString("id").orEmpty())
        }
        composable(Routes.CREDIT_CENTER) { com.live.finance.ui.bankcard.CreditCenterScreen(nav) }
        composable(Routes.CREDIT_INSTALLMENT) { com.live.finance.ui.bankcard.InstallmentCreateScreen(nav) }
        composable(Routes.BILL_LIST) { com.live.finance.ui.bankcard.BillListScreen(nav) }
        composable("${Routes.BILL_DETAIL}?id={id}", arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" })) { e ->
            com.live.finance.ui.bankcard.BillDetailScreen(nav, e.arguments?.getString("id").orEmpty())
        }
        composable("${Routes.BILL_LEDGER}?id={id}", arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" })) { e ->
            com.live.finance.ui.bankcard.BillLedgerScreen(nav, e.arguments?.getString("id").orEmpty())
        }
        composable(Routes.RECURRING) { com.live.finance.ui.recurring.RecurringListScreen(nav, "recurring") }
        composable(Routes.EVENTS) { com.live.finance.ui.recurring.RecurringListScreen(nav, "event") }
        composable(Routes.ACCOUNT_STRUCTURE) { com.live.finance.ui.account.AccountStructureScreen(nav) }
        composable(Routes.ACCOUNT_BALANCE_FLOW) { com.live.finance.ui.account.BalanceFlowScreen(nav) }
        // 卡收支流水（web /finance/report/card-flow?cardId=，Structure/CardManage 入口）
        composable(
            route = "${Routes.REPORT_CARD_FLOW}?cardId={cardId}",
            arguments = listOf(androidx.navigation.navArgument("cardId") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.account.CardFlowScreen(
                nav, back.arguments?.getString("cardId").orEmpty(),
            )
        }
        composable(Routes.REPORT) { com.live.finance.ui.report.ReportCenterScreen(nav) }
        composable(Routes.REPORT_STATS_OVERVIEW) { com.live.finance.ui.report.StatsOverviewScreen(nav) }
        composable(Routes.REPORT_MONTHLY_TREND) { com.live.finance.ui.report.MonthlyTrendScreen(nav) }
        // 报表四子页：流水筛选 / 类目占比 / 转账明细 / 负债总览
        composable(Routes.REPORT_FLOW_FILTER) { com.live.finance.ui.report.FlowFilterScreen(nav) }
        composable(Routes.REPORT_CATEGORY_RATIO) { com.live.finance.ui.report.CategoryRatioScreen(nav) }
        composable(Routes.REPORT_TRANSFER_LIST) { com.live.finance.ui.report.TransferListScreen(nav) }
        composable(Routes.REPORT_DEBT_OVERVIEW) { com.live.finance.ui.report.DebtOverviewScreen(nav) }
        composable(Routes.TODO_CALENDAR) { com.live.finance.ui.todo.TodoCalendarScreen(nav) }
        composable(Routes.WORK_JOB_SETTING) { com.live.finance.ui.work.JobListScreen(nav) }
        composable(Routes.WORK_SALARY_CALENDAR) { com.live.finance.ui.work.SalaryCalendarScreen(nav) }
        composable("${Routes.WORK_SALARY_DAY}?date={date}") { com.live.finance.ui.work.SalaryDayScreen(nav) }
        composable("${Routes.WORK_SALARY_STAT}?year={year}&month={month}") { com.live.finance.ui.work.SalaryStatScreen(nav) }
        composable(Routes.DIARY) { com.live.finance.ui.diary.DiaryListScreen(nav) }
        composable(Routes.DIARY_ADD) { com.live.finance.ui.diary.DiaryAddScreen(nav) }
        // 动态详情（web `/diary/detail?id=...`；列表卡片点击进入）
        composable(
            route = "${Routes.DIARY_DETAIL}?id={id}",
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { back ->
            com.live.finance.ui.diary.DiaryDetailScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(Routes.DATA_LOGIN_LOG) { com.live.finance.ui.data.LoginLogScreen(nav) }
        // 数据管理子页：检查 / 导出 / 导入 / 备份（web `finance/data/{check,export,import,backup}`）
        composable(Routes.DATA_CHECK) { com.live.finance.ui.data.DbCheckScreen(nav) }
        composable(Routes.DATA_EXPORT) { com.live.finance.ui.data.DbExportScreen(nav) }
        composable(Routes.DATA_BACKUP) { com.live.finance.ui.data.DbBackupScreen(nav) }
        composable(Routes.DATA_IMPORT) { com.live.finance.ui.data.DbImportScreen(nav) }
        // 文件资源：类型入口 + 列表（web `/user/resource-list?type=post|product|bank|other`）
        composable(Routes.RESOURCE_MANAGE) { com.live.finance.ui.user.ResourceManageScreen(nav) }
        composable(
            route = "${Routes.RESOURCE_LIST}?type={type}",
            arguments = listOf(androidx.navigation.navArgument("type") { defaultValue = "other" }),
        ) { back ->
            com.live.finance.ui.user.ResourceListScreen(nav, back.arguments?.getString("type") ?: "other")
        }
        // 银行分类管理（复用分类管理屏，初始筛选项为「银行」）
        composable(Routes.BANK_CATEGORY_MANAGE) { com.live.finance.ui.user.CategoryManageScreen(nav, "bank") }
        composable(Routes.DATA_MANAGE) { com.live.finance.ui.data.DataManageScreen(nav) }
        composable(Routes.CREDIT_LIMIT_MANAGE) { com.live.finance.ui.bankcard.LimitManageScreen(nav) }
        composable(Routes.CREDIT_INSTALLMENT_LIST) { com.live.finance.ui.bankcard.InstallmentListScreen(nav) }
        composable(Routes.FUND) { com.live.finance.ui.report.FundScreen(nav) }
        composable(Routes.FUND_REGISTER) { com.live.finance.ui.report.FundRegisterScreen(nav) }
        composable(Routes.FUND_DAILY) { com.live.finance.ui.report.FundDailyScreen(nav) }
        composable(Routes.FUND_TREND) { com.live.finance.ui.report.FundTrendScreen(nav) }
        composable(Routes.FUND_EARNINGS) { com.live.finance.ui.report.FundEarningsScreen(nav) }
        composable(Routes.PROFILE_EDIT) { com.live.finance.ui.user.ProfileEditScreen(nav) }
        // PIN 码设置/修改：对齐 web `/user/pin-setup?mode=new|modify`
        composable(
            route = "${Routes.PIN_SETUP}?mode={mode}",
            arguments = listOf(androidx.navigation.navArgument("mode") { defaultValue = "new" }),
        ) { back ->
            com.live.finance.ui.user.PinSetupScreen(nav, back.arguments?.getString("mode") ?: "new")
        }
        // PIN 码管理（状态卡 + 设置/修改/关闭）
        composable(Routes.PIN_MANAGE) { com.live.finance.ui.user.PinManageScreen(nav) }
        composable("finance/flow/{id}") { back ->
            // 详情屏后续按 flow 域补，这里先占位保证导航闭环
            com.live.finance.ui.flow.FlowDetailScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(Routes.ERROR_429) { Error429Screen(nav) }
    }
}

@Composable
private fun PinGateHost() {
    val graph = App.of(LocalContext.current).graph
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val show by graph.pinCoordinator.show.collectAsState()
    var pin by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun dismiss() { pin = ""; errorText = ""; graph.pinCoordinator.resolve(null) }

    VanPopup(show = show, onDismissRequest = { dismiss() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            FText("需要安全验证", 16f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            FText("请输入 6 位 PIN 码以继续该操作", 12f, FontWeight.Normal, colors.textTertiary)
            Spacer(Modifier.height(12.dp))
            VanField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); errorText = "" }, label = "PIN", type = VanFieldType.Password, maxlength = 6)
            if (errorText.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FText(errorText, 12f, FontWeight.Normal, colors.danger)
            }
            Spacer(Modifier.height(16.dp))
            Column {
                VanButton(text = "验证", type = VanButtonType.Primary, block = true, loading = verifying, onClick = {
                    if (pin.length != 6) { toast.show("请输入 6 位 PIN"); return@VanButton }
                    verifying = true
                    scope.launch {
                        // 对齐 web pin.js：route_verify 走 /pin/route-verify（拿一次性令牌），其余走 /pin/verify
                        val challenge = graph.pinCoordinator.challenge
                        val actionType = (challenge as? com.google.gson.JsonObject)
                            ?.get("action_type")?.takeIf { !it.isJsonNull }?.asString
                        val ticket: com.live.finance.core.PinTicket?
                        if (actionType == "route_verify") {
                            ticket = when (val r = graph.security.routeVerify(pin, challenge)) {
                                is ApiResult.Ok -> r.data
                                else -> null
                            }
                        } else {
                            ticket = if (graph.security.verifyPin(pin) is ApiResult.Ok) {
                                com.live.finance.core.PinTicket()
                            } else null
                        }
                        verifying = false
                        pin = ""
                        if (ticket == null) {
                            // 失败保持弹窗打开并显示错误（对齐 web PinVerifyDialog）
                            errorText = "PIN 验证失败，请重试"
                        } else {
                            errorText = ""
                            graph.pinCoordinator.resolve(ticket)
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                VanButton(text = "取消", type = VanButtonType.Default, block = true, onClick = { dismiss() })
            }
        }
    }
}
