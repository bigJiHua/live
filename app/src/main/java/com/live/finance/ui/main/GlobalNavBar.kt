package com.live.finance.ui.main

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import com.live.finance.App
import com.live.finance.core.nav.RouteTitles
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastPosition
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanNavBar
import com.live.vant.feedback.VanToastType

/**
 * 全局顶部导航栏 —— 一比一复刻 web `layout/MainLayout.vue` 的 `<van-nav-bar fixed placeholder>`。
 *
 * 与 web 的逐项对照：
 *  - `:title="pageTitle"`（route.meta.title，缺失兜底「人生记录」）→ [RouteTitles.title]；
 *    四个 Tab 页在原生是 `Routes.MAIN` 单目的地内的局部状态，标题按 [mainTab] 序号取。
 *  - `#left`：`arrow-left`（仅非 Tab 根页显示）+ `wap-home-o`（常显，`router.replace('/home')`）；
 *    `.van-nav-bar__left{gap:8px;padding:8px 14px}`、`.nav-left-icon{font-size:20px;padding:10px}`
 *    → `Row(spacedBy 8.dp)` + `VanIcon(size 20.sp, padding 10.dp)`；Vant 默认的 `__left` 内边距 16dp 由
 *    `VanNavBar` 提供（web 覆写成 14dp，2dp 差已记入构造书 5.6）。
 *  - `#right`：只有 `lock`。`expand`（全屏切换）是浏览器 `requestFullscreen`，安卓无等价物 → 不画，
 *    已按「平台不适用」记档（构造书 5.6）。
 *  - `showBackButton`：`!["/home","/finance","/diary","/user"].includes(route.path)` → 原生即「非 MAIN 目的地」。
 *  - `onBack` 的 `cNum` 计数：**不实现**（web 死代码）。`let cNum = ref(0)` 是 Ref 对象，`cNum++` → NaN，
 *    `NaN >= 3` 恒 false ⇒ `router.push('/')` 永不执行，web 返回的实际行为 = 纯 `router.back()`。
 *  - 锁定：`authApi.lockSystem()` → `POST /auth/lock-system`（该路径在 `AppConfig.SKIP_ENCRYPT_URLS` 内，明文发送）。
 *    prod 变体下后端回 `{code:8303,...}`，`ApiClient` 的 pinGate 会在调用期间弹出 PIN 验证框、验证通过后带票重发；
 *    web 在拦截器弹框之后又由 MainLayout 手动 `pinVerifyRef.show()` 弹了**第二次**，原生不重复这一次。
 */
@Composable
fun GlobalNavBar(nav: NavHostController, mainTab: MutableIntState) {
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination?.route
    // web：/login、/429、/share/diary/detail 挂在 MainLayout 之外，无顶栏
    if (!RouteTitles.hasBar(dest)) return

    val colors = LocalAppColors.current
    val graph = App.of(LocalContext.current).graph
    val toast = LocalVanToastController.current
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onTabRoot = dest?.substringBefore('?') == Routes.MAIN

    fun goHome() {
        mainTab.intValue = 0
        if (!nav.popBackStack(Routes.MAIN, inclusive = false)) {
            nav.navigate(Routes.MAIN) { launchSingleTop = true }
        }
    }

    VanNavBar(
        title = if (onTabRoot) ISLAND_TABS[mainTab.intValue].second else RouteTitles.title(dest, entry?.arguments),
        leftArrow = false,
        leftSlot = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp), // gap: 8px
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 逐字复刻 web `#left` 的 `v-if="showBackButton"`：四个 Tab 根页**不画也不占位**，
                // 首页图标直接贴左缘（web 实测中心 x ≈ 34px）。（2026-09-18 负责人要求：与 web 一致，
                // 不再保留「返回键恒定槽位」——那会把首页图标推到 84dp，看起来偏中间。）
                if (!onTabRoot) {
                    NavHot("arrow-left", colors.textPrimary) {
                        // ⚠ **不实现计数**：web `MainLayout.onBack` 的 `let cNum = ref(0)` 是 Ref **对象**，
                        //   `cNum++` 走 ToNumeric → NaN，`NaN >= 3` 恒为 false ⇒ 那句 `router.push('/')`
                        //   是**永不执行的死代码**，web 返回的实际行为就是纯 `router.back()`。
                        //   之前按字面复刻成 Int 计数，反而做出「点满 3 次永久跳主页」这种 web 没有的行为。
                        //   同类判定见构造书 §7.8 的死代码清单（`LoginLog.vue` 的「疑似异地登录」等）。
                        // 走系统返回派发而不是直接 pop：页面内若有 BackHandler（如快速登记向导的步进回退、
                        // 卡包实验室的收起面板），顶栏箭头必须与系统返回同语义；无人拦截时才由 NavHost 退栈。
                        if (backDispatcher != null) backDispatcher.onBackPressed() else nav.popBackStack()
                    }
                }
                NavHot("wap-home-o", colors.textPrimary) { goHome() }
            }
        },
        rightSlot = {
            NavHot("lock", colors.textPrimary) {
                scope.launch {
                    val r = graph.auth.lockSystem()
                    when (r) {
                        // 锁定生效的标志恰恰是 `code:8303`：控制器插一条 pin_status=0 的锁记录并要求
                        // 下次操作验证 PIN。若此前 pinGate 已弹框验证过，重发会再次拿到 8303
                        // （verify 成功已把旧记录置 1，重发又新插一条）—— 都算锁定成功，不是失败。
                        is ApiResult.Ok, is ApiResult.NeedPin -> {
                            toast.show("系统已锁定", VanToastType.Fail, position = VanToastPosition.Top)
                            // 锁定成功后必须立刻弹窗要求解锁，不能等下次请求 —— 否则用户停在当前页
                            // 就没有任何解锁入口（web MainLayout 的 showPinDialog 同理）。
                            // ⚠ 原先只有 fake 变体补弹：因为 prod 下 lock-system 的 8303 会由 pinGate
                            //   触发弹窗；现在 lock-system 已排除出 pinGate 队列（避免"输对 PIN 又被
                            //   重新锁上"），prod 也必须主动弹。
                            graph.pinCoordinator.requestUnlock()
                        }
                        is ApiResult.Fail ->
                            toast.show(r.message.ifEmpty { "锁定失败" }, VanToastType.Fail, position = VanToastPosition.Top)
                        else -> toast.show("锁定失败", VanToastType.Fail, position = VanToastPosition.Top)
                    }
                }
            }
        },
    )
}

/**
 * 顶栏图标按钮 —— 40×40 **热区**（web `.nav-left-icon{font-size:20px;padding:10px}`）。
 *
 * ⚠ 不能用 `VanIcon(modifier = Modifier.padding(10.dp), onClick = …)` 代替：`VanIcon` 内部是
 * `modifier.then(Modifier.clickable{})`，padding 落在 clickable **外侧**，命中区只剩 20dp 字形，
 * 剩下的 10dp 环不响应点击 —— 表现就是「明明点在图标上却没反应」。
 *
 * 无涟漪：Vant 靠 `:active` 变色，与工程既有口径一致（`indication = null`）。
 */
@Composable
private fun NavHot(name: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() },
    ) {
        VanIcon(name = name, size = 20.sp, color = color)
    }
}
