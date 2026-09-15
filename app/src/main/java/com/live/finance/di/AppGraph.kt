package com.live.finance.di

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.live.finance.core.AppConfig
import com.live.finance.core.ThemeMode
import com.live.finance.core.ThemeStore
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.DeviceProfile
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
import com.live.finance.data.repo.LoginLogRepository
import com.live.finance.data.repo.FakeLoginLogRepository
import com.live.finance.data.repo.RemoteLoginLogRepository
import com.live.finance.data.repo.ForeignRepository
import com.live.finance.data.repo.FakeForeignRepository
import com.live.finance.data.repo.RemoteForeignRepository
import com.live.finance.data.repo.ResourceRepository
import com.live.finance.data.repo.FakeResourceRepository
import com.live.finance.data.repo.RemoteResourceRepository
import com.live.finance.data.repo.DataRepository
import com.live.finance.data.repo.FakeDataRepository
import com.live.finance.data.repo.RemoteDataRepository
import com.live.finance.data.repo.RemoteRepayRepository
import com.live.finance.data.repo.RemoteAuthRepository
import com.live.finance.data.repo.RemoteAssetRepository
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
    val client = ApiClient(device, session)

    /** PIN 拦截协调器：任意写请求遇 8303 时弹窗验证后重发。 */
    val pinCoordinator = com.live.finance.core.PinCoordinator().also {
        client.pinGate = { it.awaitVerify() }
    }

    val auth: AuthRepository =
        if (AppConfig.useFake) FakeAuthRepository(session) else RemoteAuthRepository(client, session)
    val flow: FlowRepository =
        if (AppConfig.useFake) FakeFlowRepository() else RemoteFlowRepository(client)
    val card: CardRepository =
        if (AppConfig.useFake) FakeCardRepository() else RemoteCardRepository(client)
    val category: CategoryRepository =
        if (AppConfig.useFake) FakeCategoryRepository() else RemoteCategoryRepository(client)
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

    val themeStore = ThemeStore(context)
    val themeMode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.System)

    suspend fun init() {
        session.load()
        themeMode.value = themeStore.load()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        themeStore.save(mode)
    }
}
