package com.live.finance.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.live.vant.feedback.VanToastPosition
import com.live.vant.feedback.VanToastType
import com.live.finance.ui.main.PinVerifyDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.live.finance.App
import com.live.finance.ui.main.GlobalNavBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.theme.LocalFontOffsetSp
import com.live.finance.theme.fontOffsetSp
import com.live.finance.theme.resolveTokens
import com.live.finance.theme.vantDimensWithFontOffset
import com.live.finance.ui.auth.LoginScreen
import com.live.finance.ui.error.Error429Screen
import com.live.finance.ui.flow.FlowListScreen
import com.live.finance.ui.main.MainScreen
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.theme.HapticStrength
import com.live.vant.theme.LocalClickHapticEnabled
import com.live.vant.theme.LocalHapticStrength
import com.live.vant.theme.LocalKeyHapticEnabled
import com.live.vant.theme.globalClickHaptic
import com.live.vant.feedback.VanToastHost
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.theme.VantTheme
import androidx.core.view.WindowCompat

/**
 * 系统「字体大小」设置的收敛区间（见构造书 7.9 末尾「字号与设备」）。
 *
 * 本工程所有文本用 **sp**，而 sp 会整体跟随系统字体大小（部分机型可达 1.3x）。
 * 本项目是按 web CSS px 1:1 复刻版式，图表轴标签 / 固定宽度胶囊 / 键盘按键 /
 * badge 等「字号 = 容器几何」的地方（7.9 的排除表）在大缩放下**必然叠字或撑破**
 * ⇒ 同一套字号在不同机型上观感不一致（负责人真机反馈「有些适配有些不适配」）。
 *
 * 这里把 fontScale 夹到 [0.85, 1.15]：
 *  - 上限 1.15：保留一定的无障碍放大能力，同时保证版式不散；
 *  - 下限 0.85：避免用户把系统字体调到「小」时文字过小看不清。
 * ⚠ 只夹 **fontScale**（文字），**density 保持原值**（布局仍按设备密度缩放），
 *   否则会连 dp 尺寸一起改，版式反而不随屏幕走。
 */
private const val MIN_FONT_SCALE = 0.85f
private const val MAX_FONT_SCALE = 1.15f

/**
 * 页面转场时长（ms）—— 微信 / iOS 式「推入推出」。
 * ⚠ 动画（[AppNavHost] 的 NavHost 四条过渡）与转场拦截层**共用这一个常量**，改时长只改这里。
 *   曾经拦截层写死 `delay(300)` 且注释以为是「Navigation 默认 300ms slide」，而 Navigation Compose
 *   2.7.7 的真实默认是 **700ms fade**（反编译 `NavHostKt$NavHost$3/$4/$8` 确认）⇒ 拦截层提前 400ms
 *   撤除，旧页面在那 400ms 里照样能点。
 */
private const val NAV_ANIM_MS = 300

/**
 * 转场视差分母：**旧页**只位移新页的 `1 / NAV_PARALLAX_DIV`。
 * ⚠ 必须 ≥ 1（旧页位移 ≤ 新页位移），否则旧页会先于新页让开、屏上露出一条底色缝。
 * ⚠ 改成 1 = 完全无视差（旧页纹丝不动，新页整张盖上去），最干脆但少一点层次。
 */
private const val NAV_PARALLAX_DIV = 3

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = com.live.finance.App.of(context).graph
    val systemDark = isSystemInDarkTheme()
    // 「预设 + 系统深浅」→ 完整 token 集（= web useUiTheme 的 buildVars + applyTheme）
    val choice = graph.themeChoice.value
    val tokens = remember(choice, systemDark) { resolveTokens(choice, systemDark) }

    // 「文字大小」档位（原生设置，五档、每档 ±1sp）→ sp 偏移，业务文字与底座字号 token 各叠一次
    val fontOffset = fontOffsetSp(graph.fontLevel.value)
    val vantDimens = remember(fontOffset) { vantDimensWithFontOffset(fontOffset) }

    // 系统栏图标明暗 + 窗口底色（对应 web `html{background}` + `<meta name="theme-color">`，
    // 避免深色主题下状态栏图标取色相反、overscroll 露白）
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // 状态栏透明（负责人 2026-09-21 拍板，内容不占用状态栏区域）：非 edge-to-edge 下
        // 透出窗口底色 = tokens.bgPage（随主题换色），不是把内容顶进状态栏
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !tokens.isDark
            isAppearanceLightNavigationBars = !tokens.isDark
        }
        window.setBackgroundDrawable(ColorDrawable(tokens.bgPage.toArgb()))
    }

    // 把系统字体缩放收敛到稳定区间后，再往下提供（一次设置，全 App 生效）
    val baseDensity = LocalDensity.current
    val fontScale = baseDensity.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    val stableDensity = remember(baseDensity.density, fontScale) {
        Density(density = baseDensity.density, fontScale = fontScale)
    }

    VantTheme(colors = tokens.vant, dimens = vantDimens) {
        val toast = rememberVanToastController()
        CompositionLocalProvider(
            LocalAppColors provides tokens.vant,
            LocalAppTokens provides tokens,
            LocalVanToastController provides toast,
            // 「文字大小」档位的 sp 偏移（[fontOffsetSp]）：`FText` 与上面那份字号 token 都吃它。
            LocalFontOffsetSp provides fontOffset,
            // 「按键震动」开关：四个键盘的按键经 `rememberKeyHaptic()` 读它（关=空动作）。
            LocalKeyHapticEnabled provides graph.haptics.value,
            // 「全局操作点击震动」开关 + 「震动效果」档：探针挂在下方整屏 Box（见 globalClickHaptic）。
            LocalClickHapticEnabled provides graph.clickHaptics.value,
            LocalHapticStrength provides HapticStrength.resolve(graph.hapticStrength.value),
            // 收敛后的系统字体缩放（见 [MIN_FONT_SCALE] 处注释）。放在最内层 ⇒ 业务与
            // `vant-ui` 组件拿到的都是同一个稳定 fontScale。
            LocalDensity provides stableDensity,
        ) {
            // 转场拦截层必须盖住**整屏**（含顶栏），所以状态提到这一层、层也画在这一层：
            // 原来它挂在 NavHost 同级、只覆盖内容区 ⇒ 转场那 300ms 里顶栏的返回/首页/锁定仍可点，
            // 能在动画没走完时又叠一次导航 —— 叠出来的画面正是「看见上一个页面的旧内容」。
            val navTransitioning = remember { mutableStateOf(false) }
            // 「全局操作点击震动」探针挂在整屏 Box 上：观察（不消费）每个点按的抬起，
            // 覆盖 NavHost 全部页面 + 顶栏；不消费事件 ⇒ 不影响任何 clickable 的行为。
            // Compose Dialog（弹窗）是独立 window，探针够不到 —— 弹窗内只有键盘触感。
            val clickHapticModifier = if (graph.clickHaptics.value) {
                Modifier.globalClickHaptic(view, HapticStrength.resolve(graph.hapticStrength.value))
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(clickHapticModifier)) {
                // 等 session.load() 完成后再合成导航，避免首帧 token 还没读出 → 误判为未登录
                if (graph.sessionReady.value) {
                    AppNavHost(navTransitioning)
                } else {
                    Box(Modifier.fillMaxSize().background(tokens.bgPage))
                }
                // 转场期间全局透明拦截层（画在 toast / PIN 框之下，不影响它们显示）
                if (navTransitioning.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                    )
                }
                // Toast 覆盖层与页面**同属一个主窗口**（不再是独立 Dialog 窗口 ——
                // 那种窗口会吞掉全屏触摸，见 `VanToastHost` 注释）；画在转场拦截层之上。
                VanToastHost(toast)
            }
            PinVerifyDialog()
        }
    }
}

@Composable
private fun AppNavHost(transitioning: MutableState<Boolean>) {
    val nav = rememberNavController()
    // 转场期间全局拦截交互，避免旧页面在新页滑入的过程中仍响应点击。
    // ⚠ 时长与动画**同源**（[NAV_ANIM_MS]）：曾经这里写死 `delay(300)` 并注释成「Navigation 默认
    //   300ms slide」，而真实默认是 700ms fade ⇒ 拦截层提前 400ms 撤除、旧页面在那 400ms 里照样能点。
    //   2026-09-21 随转场改造收敛到同一常量，别再改回字面量。
    val currentEntry by nav.currentBackStackEntryAsState()
    var hasNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(currentEntry) {
        if (hasNavigated) {
            transitioning.value = true
            delay(NAV_ANIM_MS.toLong())
            transitioning.value = false
        } else {
            hasNavigated = true
        }
    }
    // 启动首屏：持久 token 存在即直接进入主页面（对应 web 信任 localStorage 登录态），否则登录页。
    val graph = App.of(LocalContext.current).graph
    // ⚠ **只取初值**（无 key 的 `remember{}`），不能直接读 `graph.session.token` 作响应式输入：
    //   Navigation Compose 2.7.7 内部是 `remember(route, startDestination, builder){createGraph}`
    //   （反编译 NavHostKt 确认）—— startDestination 一变就**重建 graph、整体重置返回栈**到新首屏。
    //   logout / 401 强登都会 `session.clear()` 置空 token ⇒ 若让 startDestination 跟着变 "main"→"login"，
    //   叠加各自的显式 `navigate(LOGIN)`，登录页会被**连续合成两次**（第二次重新走一遍滑入动画）——
    //   正是「退出登录后 login 界面闪烁两次」。回登录页只交给显式 navigate 这一条路。
    // 🛡 2026-10-17：构建期已无默认站点。若本地既没有 token、也没有已接入站点，
    //   一律回登录页（由登录页首帧自动弹「站点设置」）。否则老 token 会让 App 直接进主页，
    //   而所有请求都因 baseUrl 为空失败，用户根本找不到「接入站点」入口。
    val startDestination = remember {
        if (graph.session.token.isNullOrEmpty() || !AppConfig.hasSite) Routes.LOGIN else Routes.MAIN
    }
    // 四个 Tab 的当前序号：提到 NavHost 上方，让全局顶栏能随 Tab 出标题（首页/账本/动态/我的）。
    // ⚠ 保持 `rememberSaveable`：push 二级页时 MainScreen 会离开组合，进程重建时也要能恢复原 Tab。
    val mainTab = rememberSaveable { mutableIntStateOf(0) }
    // web `forceLogin`（401 与 PIN 8304 的统一出口，见 ApiClient.onSessionBlocked）：
    // toast(Top, 800ms) → 清 token → 回登录页；`AppGraph` 只在首次写入，故天然防重入。
    // 已在登录页时只提示不导航，避免把用户刚输入的账号/密码冲掉（web 是整页跳转所以无此问题）。
    val blockedMsg = graph.sessionBlocked.value
    val toast = LocalVanToastController.current
    val uiScope = rememberCoroutineScope()
    LaunchedEffect(blockedMsg) {
        if (blockedMsg != null) {
            // ⚠ 类型必须给 **Fail（红）**：这是「会话被终止」的业务失败，不给类型会落到
            //   `VanToastType.Text` ⇒ 显示成**蓝色「普通信息」**，语义全错。
            // ⚠ 时长也别写死 800ms —— 太短，「登录已失效，请重新登录」根本读不完；
            //   交给 `VanToastController` 的阶梯默认（Fail = 3s）。
            toast.show(blockedMsg, VanToastType.Fail, position = VanToastPosition.Top)
            // 会话终止时先把 PIN 框收掉（否则 8304 登出后弹窗会留在登录页之上）
            graph.pinCoordinator.resolve(null)
            uiScope.launch { graph.session.clear() }
            if (nav.currentDestination?.route != Routes.LOGIN) {
                nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
            }
            graph.sessionBlocked.value = null
        }
    }
    // 对应 web MainLayout：顶栏在 router-view 之上（`van-nav-bar fixed placeholder` 会占位，不覆盖内容）
    Column(Modifier.fillMaxSize()) {
        GlobalNavBar(nav, mainTab)
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                // ── 页面转场：微信 / iOS 式「推入推出」（2026-09-21 负责人要求） ─────────────────
                // 诉求原话：「退出再进入一个新的页面时候，我还能见到上一个页面的旧内容……
                //   我要像微信一样，返回就是返回，进入就是进入，很干脆很直接」。
                // 做法：**不透明整屏位移**取代淡化 —— 进入时新页从右侧整屏滑入、盖住旧页，旧页只做
                //   1/3 视差左移；返回时反向（当前页向右滑出，露出下面那页）。两页全程不透明 ⇒ 不叠影。
                // ⚠ **不能用 fade**：淡化期间两页是半透明叠加的，旧页内容会透出来 —— 那正是要修的现象。
                //   （原实现没设这四项，吃的是 Navigation Compose 2.7.7 的默认值 `fadeIn/fadeOut(tween(700))`，
                //   前进与返回完全一样、无方向感，且长达 700ms。）
                // ⚠ 位移量恒满足「旧页位移(1/3) ≤ 新页位移(1)」⇒ 屏上任意时刻都被两页铺满、不露底色。
                //   视差若要调大，别让 [NAV_PARALLAX_DIV] < 1。
                // ⚠ 这是**主动偏离 web**：web 是 `MainLayout.vue` 的 `<transition name="fade" mode="out-in">`
                //   + `style.css` 的 `.fade-*-active{transition: opacity .25s}`（先淡出 250ms、再淡入 250ms，
                //   共 500ms 且中间有一段空白）。已按用户口径记档，见 docs/项目构造书.md。
                enterTransition = {
                    slideInHorizontally(tween(NAV_ANIM_MS)) { it }
                },
                exitTransition = {
                    slideOutHorizontally(tween(NAV_ANIM_MS)) { -it / NAV_PARALLAX_DIV }
                },
                popEnterTransition = {
                    slideInHorizontally(tween(NAV_ANIM_MS)) { -it / NAV_PARALLAX_DIV }
                },
                popExitTransition = {
                    slideOutHorizontally(tween(NAV_ANIM_MS)) { it }
                },
            ) {
        composable(Routes.LOGIN) { LoginScreen(nav, mainTab) }
        composable(Routes.MAIN) { MainScreen(nav, mainTab) }
        composable(Routes.FLOW_LIST) { FlowListScreen(nav) }
        composable(Routes.FLOW_CALENDAR) { com.live.finance.ui.flow.CalendarScreen(nav) }
        // 银行卡管理：2026-10-11 起改为**离线 WebView** 版（原生 CardManageScreen 已备份到
        //   temp/备份/CardManageScreen-native-20261011/，源码仍在仓库里，未删）。
        //   换掉的原因：原生用 Column+forEach 全量渲染卡堆（CardStackView.kt:79-83，无虚拟化），
        //   每张卡一个模糊 shadow + 两个 SubcomposeAsyncImage 子组合，切 tab 时 N 张卡同时进动画 ⇒ 卡顿。
        //   WebView 版走 web 同一份视觉稿，视觉 1:1，且省掉原生那套重实现。
        composable(Routes.CARD) { com.live.finance.ui.bankcard.CardWebScreen(nav, "debit") }
        composable(Routes.CARD_DEBIT) { com.live.finance.ui.bankcard.CardWebScreen(nav, "debit") }
        composable(Routes.CARD_CREDIT) { com.live.finance.ui.bankcard.CardWebScreen(nav, "credit") }
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
        // 卡收支流水（web `/finance/report/card-flow?cardId=&year=&month=`，Structure/CardManage 入口）
        //   year/month 是可选入参：web `Number(route.query.year) || now.year()` ⇒ 缺省/0 落回本月。
        composable(
            route = "${Routes.REPORT_CARD_FLOW}?cardId={cardId}&year={year}&month={month}",
            arguments = listOf(
                androidx.navigation.navArgument("cardId") { defaultValue = "" },
                androidx.navigation.navArgument("year") {
                    type = androidx.navigation.NavType.IntType; defaultValue = 0
                },
                androidx.navigation.navArgument("month") {
                    type = androidx.navigation.NavType.IntType; defaultValue = 0
                },
            ),
        ) { back ->
            com.live.finance.ui.account.CardFlowScreen(
                nav, back.arguments?.getString("cardId").orEmpty(),
                back.arguments?.getInt("year") ?: 0,
                back.arguments?.getInt("month") ?: 0,
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
        // 公开分享页（web `/share/diary/detail?token=…|?id=…`）：两种模式二选一，
        // ⚠ 无顶栏（`RouteTitles.NO_BAR`）、不依赖登录态，与 web 一样挂在 MainLayout 之外。
        composable(
            route = "${Routes.DIARY_SHARE}?token={token}&id={id}",
            arguments = listOf(
                androidx.navigation.navArgument("token") { defaultValue = "" },
                androidx.navigation.navArgument("id") { defaultValue = "" },
            ),
        ) { back ->
            com.live.finance.ui.diary.ShareDetailScreen(
                token = back.arguments?.getString("token").orEmpty(),
                id = back.arguments?.getString("id").orEmpty(),
            )
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
        } // NavHost：目的地表见上方缩进块（未随本次外壳改动重新缩进，避免 190 行无关 diff）
    } // Box = 导航容器（转场拦截层已上移到 AppRoot，见那里的注释）
} // Column = 全局顶栏 + 导航容器
}
