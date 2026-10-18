package com.live.finance.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.scaledSp
import com.live.finance.ui.diary.DiaryListScreen
import com.live.finance.ui.finance.FinanceScreen
import com.live.finance.ui.home.HomeScreen
import com.live.finance.ui.user.UserScreen
import com.live.vant.icon.VanIcon

/**
 * 悬浮岛四入口 —— 逐条对齐 web `layout/MainLayout.vue` 的 `<router-link>` 列表：
 * /home（wap-home-o 首页）、/finance（bill-o 账本）、/diary（notes-o 动态）、/user（user-o 我的）。
 * 注：web `router/map.js` 里 `/home /finance /diary /user` 四条均**未**设 hideTabbar → 四页都常显底部岛。
 */
internal val ISLAND_TABS = listOf(
    "wap-home-o" to "首页",
    "bill-o" to "账本",
    "notes-o" to "动态",
    "user-o" to "我的",
)

/** web `box-shadow: 0 8px 24px rgba(0,0,0,.08)`。 */
private val ISLAND_SHADOW = Color(0x14000000)
private val ISLAND_SHAPE_D = 30.dp // border-radius: 30px

/**
 * 主外壳：内容区 + 悬浮玻璃岛 TabBar（一比一复刻 web 的 `.floating-island-nav`）。
 *
 * 布局对照 web MainLayout：
 *  - `.main-body { height: calc(100vh - 90px) }` —— ⚠ **别照抄成内容区的 `padding(bottom = 90.dp)`**：
 *    web 的页面自己是 `height: 100vh` / `min-height: 100dvh`，会**溢出**这 90px、背景铺满视口；
 *    原生若把内容压扁 90dp，岛一收起就会露出一条空带（深色下即「大黑条」）。详见下方 [Box] 处注释。
 *  - 岛 `position:fixed; bottom:20px; left/right:16px; height:60px; border-radius:30px; padding:0 10px`。
 *  - 各项 `flex:1; height:100%`，图标 22px、文字 11px/500，未选中 `--tabbar-text`、选中 `--theme-primary`。
 *  - 滚动沉浸：**向下浏览时收起**（translateY 120px + opacity 0）、**向上回退时弹起**，transition .3s。
 *    （web 源码注释写作「下滑收起、上滑弹起」，那个「下滑」指的是**页面向下浏览**，不是手指向下滑 ——
 *     ⚠ 两者方向相反，实现见下方 [scrollConnection] 处的符号对照表，别再搞反。）
 *
 * [tabState] 由 `AppNavHost` 持有（`rememberSaveable`）：全局顶栏 [GlobalNavBar] 要按当前 Tab 出标题，
 * 状态必须提到两者共同的上方。
 */
@Composable
fun MainScreen(nav: NavHostController, tabState: MutableIntState) {
    val colors = LocalAppColors.current
    var tab by tabState

    // 系统返回（第三十七轮，负责人口径）：账本/动态/我的 的「最后一层」返回指向首页 ——
    // 任何退出必须先经过首页，真正的退出只在首页发生（「离开确认」弹窗，第三十六轮口径见 HomeExitDialog 注释）。
    // 子页面（独立路由）的系统返回不在此列：它们由 Navigation 逐层 popBackStack，
    // 弹到 MAIN 落在 Tab 根后就归这条规则管。
    // 弹窗开着时返回键由弹窗自己的 window 处理（关闭），不会走到这里。
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler {
        if (tab != 0) tab = 0 else showExitDialog = true
    }

    // 对应 web MainLayout 的 onWindowScroll：向下浏览收起、向上回退弹起（阈值 6px）
    //
    // ⚠⚠ **Compose 的 `consumed.y` 与 web 的 `window.scrollY` 符号相反**，直接照抄 web 的 `delta > 0 → 收起`
    //    会得到完全反向的行为（2026-09-19 真机反馈「底部 Bar 逻辑做反了」）：
    //      | 手势                | 页面位置      | web scrollY | Compose consumed.y | 期望 |
    //      |---|---|---|---|---|
    //      | 手指**上**滑        | 向**下**浏览  | 增大        | **负**             | 收起 |
    //      | 手指**下**滑        | 向**上**回退  | 减小        | **正**             | 弹起 |
    //    符号依据（仓库内两处已验证的实现，别再凭感觉改）：
    //      - `ui/flow/FlowListScreen.kt`：「上滑看后续内容 → consumed.y 为负」，故 `scrolledPx - consumed.y`
    //      - `vant-ui/.../VanPullRefresh.kt`：下拉（= 手指下滑）时 `available.y > 0`
    val tabbarVisible = remember { mutableStateOf(true) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = consumed.y
                if (dy < -6f) tabbarVisible.value = false // 向下浏览 → 收起
                else if (dy > 6f) tabbarVisible.value = true // 向上回退 → 弹起
                return Offset.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage), // 岛所在的下方留白跟随页面底色
    ) {
        // ⚠⚠ **内容区必须是整屏高，不能给底部留 90dp**（2026-09-19 负责人真机反馈
        //   「底部岛收起来的时候，下面有一个大黑条」）。
        //
        //   曾经的错解：照抄 web `.main-body{ height: calc(100vh - 90px) }`，给内容区加
        //   `padding(bottom = 90.dp)`。但 web 那边**页面自己是满高的** ——
        //     `.page-home { min-height: 100vh }`、`.page-finance { height: 100vh }`、
        //     `.page-user { height: 100vh }`、`.page-diary { min-height: 100dvh }`
        //   ⇒ 页面**溢出** `.main-body` 那 90px，背景铺满整个视口，**根本不存在那条空带**。
        //   原生把 `padding` 加在内容区上，等于把整屏**压扁** 90dp：页面背景提前结束，
        //   下面 90dp 只剩 `bgPage`。岛显示时把它盖住看不出来，**岛一收起就露出整条**，
        //   深色主题下就是那条「大黑条」。
        //
        //   正确做法：内容 `fillMaxSize()` 铺满，岛用 `align(BottomCenter)` **绝对定位叠在上面**
        //   （web `.floating-island-nav{ position: fixed; bottom: 20px; z-index: 999 }`）。
        //   页面末尾该留多少让位给岛，由**各页面自己的底部内边距**决定（web 亦然：
        //   `.page-user{ padding-bottom: 30px }`、各页自己的收尾间距），**不要在这里统一压高度**。
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection),
        ) {
            when (tab) {
                0 -> HomeScreen(nav)
                1 -> FinanceScreen(nav)
                2 -> DiaryListScreen(nav)
                else -> UserScreen(nav)
            }
        }

        FloatingIslandBar(
            active = tab,
            onSelect = { tab = it },
            visible = tabbarVisible.value,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        )

        HomeExitDialog(show = showExitDialog, onClose = { showExitDialog = false })
    }
}

/**
 * 悬浮玻璃岛（web `.floating-island-nav`）。
 *
 * 玻璃效果说明：web 用 `background-color: var(--tabbar-glass-bg)` +
 * `backdrop-filter: blur(15px) saturate(160%)`。
 * **已拍板（2026-09-15）：不做真模糊、不引第三方库**（Compose 无 backdrop-blur 等价物；
 * `Modifier.blur` 只模糊自身内容，haze 需引依赖且 API<31 不生效）。
 *
 * 2026-10-18 负责人反馈「当前 APP 有点太过于透明了，需要毛玻璃效果」：
 * web 同源的 0.62 / 0.55 透明度在原生里没有 backdrop blur 兜底，背后内容会直接透出来，
 * 看着像一层薄纱而不是磨砂玻璃。这里在不改全局 token（保持与 web 参数同源）的前提下，
 * 在组件内做三件事增强「磨砂感」：
 *  1. 底色不透明度抬到 0.88（太透会失去玻璃的实感）；
 *  2. 叠一层自上而下的白色高光渐变（顶部亮、底部透明），模拟磨砂表面的反光；
 *  3. 1px 玻璃描边 + 柔和投影保持（原有效果）。
 * 真 backdrop-blur 仍不做 —— 理由同上，且项目离线编译不能引 haze。
 */
@Composable
private fun FloatingIslandBar(
    active: Int,
    onSelect: (Int) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(ISLAND_SHAPE_D)

    // 磨砂底：沿用主题玻璃色，只把 alpha 提上来（颜色本身仍跟浅色/深色走）
    val glassBase = colors.tabbarGlassBg.copy(alpha = 0.88f)
    // 顶部高光：磨砂玻璃最容易被感知的就是这一道自上而下的柔光
    val glassHighlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.16f),
        0.55f to Color.White.copy(alpha = 0.04f),
        1f to Color.Transparent,
    )

    // transition: transform .3s ease, opacity .3s ease
    val offsetY by animateDpAsState(if (visible) 0.dp else 120.dp, tween(300), label = "islandOffsetY")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "islandAlpha")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .offset(y = offsetY)
            .alpha(alpha)
            .shadow(8.dp, shape, clip = false, ambientColor = ISLAND_SHADOW, spotColor = ISLAND_SHADOW)
            .clip(shape)
            .background(glassBase)
            .background(glassHighlight)
            .border(1.dp, colors.tabbarGlassBorder, shape)
            .padding(horizontal = 10.dp), // padding: 0 10px
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ISLAND_TABS.forEachIndexed { index, (icon, title) ->
            val isActive = index == active
            // transition: color .2s ease（未选中 --tabbar-text，选中 --theme-primary）
            val color by animateColorAsState(
                if (isActive) colors.primary else colors.tabbarText,
                tween(200),
                label = "islandTabColor$index",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f) // flex: 1
                    .fillMaxHeight()
                    .clickable(
                        enabled = visible, // web `.nav-hidden{ pointer-events:none }`：收起时不可点
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) },
            ) {
                VanIcon(
                    name = icon,
                    size = 22.sp, // .nav-icon { font-size: 22px }
                    color = color,
                )
                Spacer(Modifier.height(2.dp)) // .nav-icon { margin-bottom: 2px }
                BasicText(
                    title,
                    style = TextStyle(
                        color = color,
                        fontSize = scaledSp(12f), // .nav-text { font-size: 11px }
                        fontWeight = FontWeight.Medium, // font-weight: 500（行高留默认，对齐 web line-height: normal）
                    ),
                )
            }
        }
    }
}
