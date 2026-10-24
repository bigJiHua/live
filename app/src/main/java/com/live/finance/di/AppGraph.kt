package com.live.finance.di

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.live.finance.core.AppConfig
import com.live.finance.core.BankIconWarmer
import com.live.finance.core.ThemeStore
import com.live.finance.theme.FONT_LEVEL_DEFAULT
import com.live.finance.theme.FONT_LEVEL_MAX
import com.live.finance.theme.FONT_LEVEL_MIN
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.ThemePresets
import com.live.finance.ui.common.MoneyColor
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiGetCache
import com.live.finance.core.net.ApiResult
import com.live.finance.data.local.DiaryListCache
import com.live.finance.core.glass.BarGlassSpec
import com.live.finance.core.net.ClientContext
import com.live.finance.core.net.DeviceProfile
import com.live.finance.core.store.CardPageStyle
import com.live.finance.core.store.CardStyleStore
import com.live.finance.core.store.GlassStore
import com.live.finance.core.store.SessionStore
import com.live.finance.data.repo.AuthRepository
import com.live.finance.data.repo.AssetRepository
import com.live.finance.data.repo.BudgetRepository
import com.live.finance.data.repo.CardRepository
import com.live.finance.data.repo.CategoryRepository
import com.live.finance.data.repo.FakeAuthRepository
import com.live.finance.data.repo.FakeAssetRepository
import com.live.finance.data.repo.FakeBudgetRepository
import com.live.finance.data.repo.FakeCardRepository
import com.live.finance.data.repo.FakeCategoryRepository
import com.live.finance.data.repo.FakeFixedAssetRepository
import com.live.finance.data.repo.FakeFlowRepository
import com.live.finance.data.repo.FixedAssetRepository
import com.live.finance.data.repo.FlowRepository
import com.live.finance.data.repo.RepayRepository
import com.live.finance.data.repo.FakeRepayRepository
import com.live.finance.data.repo.RecurringRepository
import com.live.finance.data.repo.FakeRecurringRepository
import com.live.finance.data.repo.BalanceRepository
import com.live.finance.data.repo.FakeBalanceRepository
import com.live.finance.data.repo.BillRepository
import com.live.finance.data.repo.FakeBillRepository
import com.live.finance.data.repo.RemoteBillRepository
import com.live.finance.data.repo.RemoteBalanceRepository
import com.live.finance.data.repo.RemoteRecurringRepository
import com.live.finance.data.repo.TodoRepository
import com.live.finance.data.repo.FakeTodoRepository
import com.live.finance.data.repo.RemoteTodoRepository
import com.live.finance.data.repo.WorkRepository
import com.live.finance.data.repo.FakeWorkRepository
import com.live.finance.data.repo.MomentRepository
import com.live.finance.data.repo.FakeMomentRepository
import com.live.finance.data.repo.RemoteMomentRepository
import com.live.finance.data.repo.RemoteWorkRepository
import com.live.finance.data.repo.PoolRepository
import com.live.finance.data.repo.FakePoolRepository
import com.live.finance.data.repo.RemotePoolRepository
import com.live.finance.data.repo.FundRepository
import com.live.finance.data.repo.FakeFundRepository
import com.live.finance.data.repo.RemoteFundRepository
import com.live.finance.data.repo.SecurityRepository
import com.live.finance.data.repo.FakeSecurityRepository
import com.live.finance.data.repo.RemoteSecurityRepository
import com.live.vant.theme.HapticStrength
import com.live.finance.data.repo.LoginLogRepository
import com.live.finance.data.repo.FakeLoginLogRepository
import com.live.finance.data.repo.RemoteLoginLogRepository
import com.live.finance.data.repo.ForeignRepository
import com.live.finance.data.repo.FakeForeignRepository
import com.live.finance.data.repo.RemoteForeignRepository
import com.live.finance.data.repo.ResourceRepository
import com.live.finance.data.repo.FakeResourceRepository
import com.live.finance.data.repo.RemoteResourceRepository
import com.live.finance.data.repo.GeoRepository
import com.live.finance.data.repo.FakeGeoRepository
import com.live.finance.data.repo.RemoteGeoRepository
import com.live.finance.data.repo.DataRepository
import com.live.finance.data.repo.FakeDataRepository
import com.live.finance.data.repo.RemoteDataRepository
import com.live.finance.data.repo.RemoteRepayRepository
import com.live.finance.data.repo.RemoteAuthRepository
import com.live.finance.data.repo.RemoteAssetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.live.finance.data.repo.RemoteBudgetRepository
import com.live.finance.data.repo.RemoteCardRepository
import com.live.finance.data.repo.RemoteCategoryRepository
import com.live.finance.data.repo.RemoteFixedAssetRepository
import com.live.finance.data.repo.RemoteFlowRepository

/**
 * 手写依赖图（Wave-0 不引 Hilt，减少编译面）。
 * useFake=true 时仓储走本地假数据，工程可直接启动；改 AppConfig.baseUrl/useFake 即切真实后端。
 */
class AppGraph(context: Context) {
    val session = SessionStore(context)
    val device = DeviceProfile(session)
    val client = ApiClient(device, session, ClientContext(device))

    /** 会话级握手管理（AES 信封密钥 + RSA 安全键盘公钥），供登录安全键盘逐字符加密取公钥。 */
    val handshake = client.handshake

    /** PIN 拦截协调器：任意写请求遇 8303 时弹窗验证后重发。 */
    val pinCoordinator = com.live.finance.core.PinCoordinator().also {
        // 入参为该次 8303 的挑战数据（route_verify 场景 UI 需要 challengeId/requestUrl/method），
        // 第二参数 skipCooldown：冷却静默重发仍被拦截时强制回到弹窗流程
        client.pinGate = { challenge, skipCooldown -> it.awaitVerify(challenge, skipCooldown) }
    }

    /**
     * 会话被终止（401 或 PIN 8304 锁定）时由 [ApiClient.onSessionBlocked] 写入提示文案，
     * 由 `ui/AppRoot.kt` 消费：toast + `session.clear()` + 回登录页（web `forceLogin`，带防重入）。
     * 用状态而非直接操作导航，是因为 ApiClient 不持有 NavHostController。
     */
    val sessionBlocked = mutableStateOf<String?>(null)
    init {
        // 会话变化（登录 setToken / 登出 clear）⇒ 进程内 GET 快照与动态本地快照全部失效，避免换账号后串数据。
        session.onSessionChanged = {
            ApiGetCache.clear()
            DiaryListCache.clear(context)
        }
        client.onSessionBlocked = { msg ->
            ApiGetCache.clear()
            if (sessionBlocked.value == null) sessionBlocked.value = msg
        }
    }

    /**
     * 应用级后台作用域：只跑「不阻塞首屏」的活（当前只有 bank logo 预热）。
     * 用 [SupervisorJob] 是为了单个预热任务挂掉不影响协程作用域本身。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 银行 logo 预热器：进程内单例，持有「已成功」集合。 */
    val bankIconWarmer = BankIconWarmer(context)

    val auth: AuthRepository =
        if (AppConfig.useFake) FakeAuthRepository(session) else RemoteAuthRepository(client, session)
    val flow: FlowRepository =
        if (AppConfig.useFake) FakeFlowRepository() else RemoteFlowRepository(client)
    val card: CardRepository =
        if (AppConfig.useFake) FakeCardRepository() else RemoteCardRepository(client)
    val category: CategoryRepository =
        if (AppConfig.useFake) FakeCategoryRepository() else RemoteCategoryRepository(client, bankIconWarmer)
    val asset: AssetRepository =
        if (AppConfig.useFake) FakeAssetRepository() else RemoteAssetRepository(client)
    val fixed: FixedAssetRepository =
        if (AppConfig.useFake) FakeFixedAssetRepository() else RemoteFixedAssetRepository(client)
    val budget: BudgetRepository =
        if (AppConfig.useFake) FakeBudgetRepository() else RemoteBudgetRepository(client)
    val repay: RepayRepository =
        if (AppConfig.useFake) FakeRepayRepository() else RemoteRepayRepository(client)
    val recurring: RecurringRepository =
        if (AppConfig.useFake) FakeRecurringRepository() else RemoteRecurringRepository(client)
    val balance: BalanceRepository =
        if (AppConfig.useFake) FakeBalanceRepository() else RemoteBalanceRepository(client)
    val bill: BillRepository =
        if (AppConfig.useFake) FakeBillRepository() else RemoteBillRepository(client)
    val todo: TodoRepository =
        if (AppConfig.useFake) FakeTodoRepository() else RemoteTodoRepository(client)
    val work: WorkRepository =
        if (AppConfig.useFake) FakeWorkRepository() else RemoteWorkRepository(client)
    val moment: MomentRepository =
        if (AppConfig.useFake) FakeMomentRepository() else RemoteMomentRepository(client)
    val pool: PoolRepository =
        if (AppConfig.useFake) FakePoolRepository() else RemotePoolRepository(client)
    val fund: FundRepository =
        if (AppConfig.useFake) FakeFundRepository() else RemoteFundRepository(client)
    val security: SecurityRepository =
        if (AppConfig.useFake) FakeSecurityRepository() else RemoteSecurityRepository(client)
    val loginLog: LoginLogRepository =
        if (AppConfig.useFake) FakeLoginLogRepository() else RemoteLoginLogRepository(client)
    val foreign: ForeignRepository =
        if (AppConfig.useFake) FakeForeignRepository() else RemoteForeignRepository(client)
    val resource: ResourceRepository =
        if (AppConfig.useFake) FakeResourceRepository() else RemoteResourceRepository(client)
    val data: DataRepository =
        if (AppConfig.useFake) FakeDataRepository() else RemoteDataRepository(client)
    val geo: GeoRepository =
        if (AppConfig.useFake) FakeGeoRepository() else RemoteGeoRepository(client)

    val themeStore = ThemeStore(context)

    /** 主题方案（对应 web `localStorage['ui-theme-choice']`：`system` 或预设 key）。 */
    val themeChoice: MutableState<String> = mutableStateOf(ThemePresets.CHOICE_SYSTEM)

    /** 收支颜色模式（对应 web `localStorage['money-color-mode']`）。 */
    val moneyColorMode: MutableState<String> = mutableStateOf(MONEY_RED_IN)

    /** 文字大小档位（**原生特有**，web 无此项）：[FONT_LEVEL_MIN]…[FONT_LEVEL_MAX]，默认三档。 */
    val fontLevel: MutableState<Int> = mutableStateOf(FONT_LEVEL_DEFAULT)

    /** 键盘按键震动（**原生特有**，web 全仓无触感代码）：默认开；关时四个键盘的按键不再回调触感。 */
    val haptics: MutableState<Boolean> = mutableStateOf(true)

    /** 全局操作点击震动（**原生特有**，§7.13 预留的「另一条设置」）：根层探针，App 内所有点按抬起时
     *  给一次短震（Compose 弹窗的独立 window 除外）；与键盘触感经时间戳去重，同一次点击只震一下。 */
    val clickHaptics: MutableState<Boolean> = mutableStateOf(true)

    /** 震动效果档（**原生特有**）：`light` / `medium` / `heavy`，键盘与全局点击共用（默认 medium = 原手感）。 */
    val hapticStrength: MutableState<String> = mutableStateOf(HapticStrength.MEDIUM.key)

    /** 底部悬浮岛玻璃参数的持久层（2026-10-19，`finance_glass` SharedPreferences）。 */
    val glassStore = GlassStore(context)

    /** 卡片页形态的持久层（2026-10-22，`finance_card_style` SharedPreferences）。 */
    val cardStyleStore = CardStyleStore(context)

    /** 我的卡片呈现形态：`Module` = 卡片模组页，`Wallet` = 卡包页。默认卡片模组。 */
    val cardPageStyle: MutableState<CardPageStyle> = mutableStateOf(CardPageStyle.Module)

    /**
     * 底部悬浮岛玻璃参数 —— **单一事实来源**（2026-10-19）：
     * 实验室调参实时改这里（不落盘）⇒ MainScreen 立即用新值渲染；
     * 点「保存设定」才 [persistBarGlass] 落盘，重启后 [init] 读回。
     */
    val barGlass: MutableState<BarGlassSpec> = mutableStateOf(BarGlassSpec())

    /** 启动就绪标记：session.load()（读 DataStore 持久 token）与主题加载完成后置 true，
     *  AppRoot 据此再合成 NavHost 决定首屏——避免首帧合成时 token 尚未从 DataStore 读出。 */
    val sessionReady: MutableState<Boolean> = mutableStateOf(false)

    suspend fun init() {
        session.load()
        val prefs = themeStore.load()
        themeChoice.value = prefs.choice
        moneyColorMode.value = prefs.moneyColorMode
        fontLevel.value = prefs.fontLevel.coerceIn(FONT_LEVEL_MIN, FONT_LEVEL_MAX)
        haptics.value = prefs.haptics
        clickHaptics.value = prefs.clickHaptics
        hapticStrength.value = HapticStrength.resolve(prefs.hapticStrength).key
        barGlass.value = glassStore.load()       // 悬浮岛玻璃参数（保存过的才覆盖默认值）
        cardPageStyle.value = cardStyleStore.load() // 卡片页形态（默认卡片模组）
        MoneyColor.apply(prefs.moneyColorMode)   // 首帧前先落到全局收支配色
        sessionReady.value = true

        // 银行 logo 预热：**首帧渲染完成之后**才发起，才有真正的「提前量」。
        // 此前预热只挂在 `category.list("bank")` 上，而它的 18 个调用点全都是
        // `LaunchedEffect` 里「界面正要画 logo 的那一刻」⇒ 预热请求与 UI 请求并发发出，
        // 反而可能让同一张 logo 下载两次（且键还对不上，见 BankIconCache）。
        //
        // ⚠ **必须整体包 try-catch**：`appScope` 的 `SupervisorJob()` 没装
        //   `CoroutineExceptionHandler`，所以 launch 体内任何未捕获异常都会走全局处理器
        //   ⇒ **Android 上直接崩 App**。而 `ApiClient` 只捕 `IOException`，
        //   JSON 解析 / Mutex / Coil 的异常都会穿透到这里。
        //   预热是纯「锦上添花」，绝不该有能力打垮启动流程。
        appScope.launch {
            try {
                if (category.list("bank") is ApiResult.Ok) {
                    Log.i("BankIconCache", "启动预热完成：成功 ${bankIconWarmer.successCount} / 失败 ${bankIconWarmer.failCount}")
                }
            } catch (t: Throwable) {
                // ⚠ 连 CancellationException 也要吞：否则协程取消会当异常上抛
                Log.w("BankIconCache", "启动预热异常（已忽略，不影响 App）: ${t.message}")
            }
        }
    }

    suspend fun setThemeChoice(key: String) {
        themeChoice.value = key
        themeStore.saveChoice(key)
    }

    suspend fun setMoneyColorMode(mode: String) {
        moneyColorMode.value = mode
        themeStore.saveMoneyColorMode(mode)
        MoneyColor.apply(mode)
    }

    /**
     * 实时更新悬浮岛玻璃参数（**不落盘**）：实验室每次调参都调它，真实栏立即生效。
     * 重启后回到上次「保存设定」的值。
     */
    fun setBarGlass(spec: BarGlassSpec) {
        barGlass.value = spec.clamped()
    }

    /** 保存设定：把当前悬浮岛玻璃参数写入本地（重启仍生效）。 */
    fun persistBarGlass() {
        glassStore.save(barGlass.value)
    }

    /** 卡片页形态：内存即时更新（下一次进入我的卡片生效）。 */
    fun setCardPageStyle(style: CardPageStyle) {
        cardPageStyle.value = style
    }

    /** 卡片页形态：点「保存设定」时落盘（重启仍生效）。 */
    fun persistCardPageStyle() {
        cardStyleStore.save(cardPageStyle.value)
    }

    /** 切「文字大小」档位；越界一律夹到 [FONT_LEVEL_MIN]…[FONT_LEVEL_MAX]。 */
    suspend fun setFontLevel(level: Int) {
        val v = level.coerceIn(FONT_LEVEL_MIN, FONT_LEVEL_MAX)
        fontLevel.value = v
        themeStore.saveFontLevel(v)
    }

    /** 开关「按键震动」（四个键盘的按键触感）。 */
    suspend fun setHaptics(on: Boolean) {
        haptics.value = on
        themeStore.saveHaptics(on)
    }

    /** 开关「全局操作点击震动」（根层探针，App 内所有点按）。 */
    suspend fun setClickHaptics(on: Boolean) {
        clickHaptics.value = on
        themeStore.saveClickHaptics(on)
    }

    /** 调「震动效果」（轻/中/重，键盘与全局点击共用）。 */
    suspend fun setHapticStrength(strength: HapticStrength) {
        hapticStrength.value = strength.key
        themeStore.saveHapticStrength(strength.key)
    }
}
