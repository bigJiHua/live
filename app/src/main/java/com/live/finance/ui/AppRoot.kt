package com.live.finance.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.live.finance.ui.auth.LoginScreen
import com.live.finance.ui.error.Error429Screen
import com.live.finance.ui.flow.FlowListScreen
import com.live.finance.ui.main.MainScreen
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastHost
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.theme.VantColors
import com.live.vant.theme.VantDarkColors
import com.live.vant.theme.VantTheme

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val graph = com.live.finance.App.of(context).graph
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val isDark = when (graph.themeMode.value) {
        com.live.finance.core.ThemeMode.Dark -> true
        com.live.finance.core.ThemeMode.Light -> false
        com.live.finance.core.ThemeMode.System -> night == Configuration.UI_MODE_NIGHT_YES
    }
    val colors = if (isDark) VantDarkColors.dark() else VantColors()

    VantTheme(colors = colors) {
        val toast = rememberVanToastController()
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalVanToastController provides toast,
        ) {
            AppNavHost()
            VanToastHost(toast)
            PinGateHost()
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) { LoginScreen(nav) }
        composable(Routes.MAIN) { MainScreen(nav) }
        composable(Routes.FLOW_LIST) { FlowListScreen(nav) }
        composable(Routes.FLOW_CALENDAR) { com.live.finance.ui.flow.CalendarScreen(nav) }
        composable(Routes.CARD) { com.live.finance.ui.bankcard.CardListScreen(nav) }
        composable(Routes.CARD_ADD) { com.live.finance.ui.bankcard.CardEditScreen(nav) }
        composable(Routes.CREDIT_FULL) { com.live.finance.ui.bankcard.CardEditScreen(nav) }
        composable(Routes.CREDIT_FOREIGN_REGISTER) { com.live.finance.ui.bankcard.ForeignScreen(nav) }
        composable(Routes.CARD_EDIT_PAT) { back ->
            com.live.finance.ui.bankcard.CardEditScreen(nav, back.arguments?.getString("id").orEmpty())
        }
        composable(Routes.ACCOUNT_ADD) { com.live.finance.ui.account.AddTransactionScreen(nav) }
        composable(Routes.ASSETS_LIST) { com.live.finance.ui.asset.AssetListScreen(nav) }
        composable(Routes.CATEGORY_MANAGE) { com.live.finance.ui.user.CategoryManageScreen(nav) }
        composable(Routes.APP_SETTINGS) { com.live.finance.ui.user.AppSettingsScreen(nav) }
        composable(Routes.FIXED_ASSET) { com.live.finance.ui.fixed.FixedAssetListScreen(nav) }
        composable(Routes.BUDGET) { com.live.finance.ui.budget.BudgetListScreen(nav) }
        composable(Routes.REPAY_LIST) { com.live.finance.ui.bankcard.RepayListScreen(nav) }
        composable(Routes.BILL_LIST) { com.live.finance.ui.bankcard.BillListScreen(nav) }
        composable(Routes.RECURRING) { com.live.finance.ui.recurring.RecurringListScreen(nav, "固定支出") }
        composable(Routes.EVENTS) { com.live.finance.ui.recurring.RecurringListScreen(nav, "固定事件") }
        composable(Routes.ACCOUNT_STRUCTURE) { com.live.finance.ui.account.AccountStructureScreen(nav) }
        composable(Routes.ACCOUNT_BALANCE_FLOW) { com.live.finance.ui.account.BalanceFlowScreen(nav) }
        composable(Routes.REPORT_STATS_OVERVIEW) { com.live.finance.ui.report.StatsOverviewScreen(nav) }
        composable(Routes.REPORT_MONTHLY_TREND) { com.live.finance.ui.report.MonthlyTrendScreen(nav) }
        composable(Routes.TODO_CALENDAR) { com.live.finance.ui.todo.TodoListScreen(nav) }
        composable(Routes.WORK_JOB_SETTING) { com.live.finance.ui.work.JobListScreen(nav) }
        composable(Routes.DIARY) { com.live.finance.ui.diary.DiaryListScreen(nav) }
        composable(Routes.DIARY_ADD) { com.live.finance.ui.diary.DiaryAddScreen(nav) }
        composable(Routes.DATA_LOGIN_LOG) { com.live.finance.ui.data.LoginLogScreen(nav) }
        composable(Routes.RESOURCE_LIST) { com.live.finance.ui.user.ResourceListScreen(nav) }
        composable(Routes.DATA_MANAGE) { com.live.finance.ui.data.DataManageScreen(nav) }
        composable(Routes.CREDIT_LIMIT_MANAGE) { com.live.finance.ui.bankcard.LimitManageScreen(nav) }
        composable(Routes.CREDIT_INSTALLMENT_LIST) { com.live.finance.ui.bankcard.InstallmentListScreen(nav) }
        composable(Routes.FUND) { com.live.finance.ui.report.FundScreen(nav) }
        composable(Routes.PROFILE_EDIT) { com.live.finance.ui.user.ProfileEditScreen(nav) }
        composable(Routes.PIN_SETUP) { com.live.finance.ui.user.PinSetupScreen(nav, "set") }
        composable(Routes.PIN_MANAGE) { com.live.finance.ui.user.PinSetupScreen(nav, "change") }
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
    val scope = rememberCoroutineScope()

    fun dismiss() { pin = ""; graph.pinCoordinator.resolve(false) }

    VanPopup(show = show, onDismissRequest = { dismiss() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            FText("需要安全验证", 16f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            FText("请输入 6 位 PIN 码以继续该操作", 12f, FontWeight.Normal, colors.textTertiary)
            Spacer(Modifier.height(12.dp))
            VanField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = "PIN", type = VanFieldType.Password, maxlength = 6)
            Spacer(Modifier.height(16.dp))
            Column {
                VanButton(text = "验证", type = VanButtonType.Primary, block = true, loading = verifying, onClick = {
                    if (pin.length != 6) { toast.show("请输入 6 位 PIN"); return@VanButton }
                    verifying = true
                    scope.launch {
                        val ok = graph.security.verifyPin(pin) is ApiResult.Ok
                        verifying = false
                        pin = ""
                        if (!ok) toast.show("PIN 错误")
                        graph.pinCoordinator.resolve(ok)
                    }
                })
                Spacer(Modifier.height(8.dp))
                VanButton(text = "取消", type = VanButtonType.Default, block = true, onClick = { dismiss() })
            }
        }
    }
}
