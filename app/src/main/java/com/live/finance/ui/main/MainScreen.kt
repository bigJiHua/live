package com.live.finance.ui.main

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
private val ISLAND_TABS = listOf(
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
 *  - `.main-body { height: calc(100vh - 90px) }` → 内容区底部预留 90dp（岛高 60 + 底距 20 + 间隙 10）。
 *  - 岛 `position:fixed; bottom:20px; left/right:16px; height:60px; border-radius:30px; padding:0 10px`。
 *  - 各项 `flex:1; height:100%`，图标 22px、文字 11px/500，未选中 `--tabbar-text`、选中 `--theme-primary`。
 *  - 滚动沉浸：下滑收起（translateY 120px + opacity 0）、上滑弹起，transition .3s。
 */
@Composable
fun MainScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    // ⚠ 必须 `rememberSaveable`（不能用 `remember`）：
    // 四个 tab 是**本目的地内部的局部状态**，push 二级页面时 MainScreen 会离开组合，
    // 普通 `remember` 不随目的地进后台栈保存 → 返回时被重置为 0，表现为「从二级页返回总是回首页」。
    // Navigation-Compose 会把每个目的地包在 SaveableStateProvider 里，故 rememberSaveable 能原样恢复。
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 对应 web MainLayout 的 onWindowScroll：下滑收起、上滑弹起（阈值 6px）
    val tabbarVisible = remember { mutableStateOf(true) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = consumed.y
                if (dy > 6f) tabbarVisible.value = false
                else if (dy < -6f) tabbarVisible.value = true
                return Offset.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage), // 岛所在的下方留白跟随页面底色
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp) // .main-body = 100vh - 90px
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
    }
}

/**
 * 悬浮玻璃岛（web `.floating-island-nav`）。
 *
 * 玻璃效果说明：web 用 `background-color: var(--tabbar-glass-bg)` +
 * `backdrop-filter: blur(15px) saturate(160%)`。
 * **已拍板（2026-09-15）：不做真模糊、不引第三方库**（Compose 无 backdrop-blur 等价物；
 * `Modifier.blur` 只模糊自身内容，haze 需引依赖且 API<31 不生效）。
 * 故只同步**视觉参数**：用 web 同源的半透明玻璃色（浅 `rgba(255,255,255,.62)` /
 * 深 `rgba(18,18,22,.55)`）+ 1px 玻璃描边 + 柔和投影；
 * 又因内容区已上抬 90dp（`.main-body`），岛后方是页面底色，去掉 blur 后观感与 web 基本一致。
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
            .background(colors.tabbarGlassBg)
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
                        fontSize = 11.sp, // .nav-text { font-size: 11px }
                        fontWeight = FontWeight.Medium, // font-weight: 500（行高留默认，对齐 web line-height: normal）
                    ),
                )
            }
        }
    }
}
