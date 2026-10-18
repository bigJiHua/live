package com.live.vant.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.basic.VanLoading
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.fontSp
import com.live.vant.theme.vantDimens
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **1:1 复刻** Vant `van-pull-refresh`（源码基线 `web/node_modules/vant/es/pull-refresh/PullRefresh.mjs`
 * + `index.css`；web 项目 9 处使用）。
 *
 * ## 一比一对照表
 *
 * | Vant（PullRefresh.mjs / index.css） | 本实现 |
 * |---|---|
 * | `.van-pull-refresh{overflow:hidden}` | 根 Box `Modifier.clipToBounds()` |
 * | `.van-pull-refresh__track{position:relative;height:100%;transition-property:transform}` | 内层 Box `fillMaxSize()` + `graphicsLayer{translationY}` |
 * | `__head{position:absolute;height:50px;line-height:50px;text-align:center;transform:translateY(-100%)}` | 头部 Box **固定** `headHeight.dp` 高 + `offset(y=-headHeight.dp)` + 居中 |
 * | `ease(distance)`（>pullDistance 折半、>2× 四分之一） | [ease] 同式，入参 **px** |
 * | `setStatus(distance,isLoading)` 状态机 | [VanPullRefreshState.setStatus] |
 * | `isTouchable()`：`status!==loading && !==success && !disabled` | [VanPullRefreshState.isTouchable] —— **加载中/成功提示期间完全忽略新手势**（用户反馈的「下拉打断」根因） |
 * | `onTouchStart`→`checkPosition`：`getScrollTop()===0` 锁定 `reachTop` | `onPostScroll`：`consumed.y==0 && available.y>0 && isChildAtTop()` 才接管 |
 * | `onTouchMove`：`deltaY>=0 && touch.isVertical()`（`getDirection(offsetX,offsetY)`） | `rawY` 累计 + `accX/accY` 方向判定 |
 * | `onTouchEnd`：loosing ⇒ `setStatus(headHeight,true)`+emit refresh；否则 `setStatus(0)` | [VanPullRefreshState.onRelease] |
 * | `state.duration`：拖拽中 `0`、松手/v-model 变化 `animationDuration(300)` | `duration` + `animateFloatAsState(tween(duration))` |
 * | `watch(modelValue)` 中 `false` ⇒ 有 success-text 就 `showSuccessTip()`（500ms）否则 `setStatus(0)` | [VanPullRefreshState.finishRefresh] |
 * | `--van-pull-refresh-head-font-size: var(--van-font-size-md)`=14px | 14.sp |
 * | `--van-pull-refresh-head-text-color: var(--van-text-color-2)`=#969799 | `LocalVantColors.textTertiary` |
 * | `--van-pull-refresh-loading-icon-size: 16px` | `VanLoading(size=16.sp)`（横向 Row，`padding-start 8dp`=`--van-padding-xs`） |
 *
 * ## ⚠ 三个必须遵守的约束
 *
 * 1. **调用方必须传真实的 [isChildAtTop]**（如 `{ listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }`
 *    或 `{ scrollState.value == 0 }`）。用默认 `{ true }` 会在列表**任意位置**接管下拉，
 *    表现为「往下拖不动列表、只会触发刷新」（2026-09-17 动态列表页踩过）。
 * 2. **阈值单位**：Vant 的 `headHeight=50` 是 **CSS px ≈ dp**，而 Compose 的 `Offset.y` 是 **物理 px**，
 *    必须经 `LocalDensity` 换算。旧实现拿像素直接和 50 比，在高密度屏上「轻轻一拖就触发刷新」
 *    （用户反馈「不灵敏 / 不可控」主因之一）。
 * 3. **不要用 `onPreFling` 吞掉惯性**：只有本次手势被下拉接管时才吸收速度，否则照常放行，
 *    不然列表的 fling 惯性滚动会整体失效（旧实现 `return Velocity.Zero` 的 bug）。
 */
class VanPullRefreshState internal constructor() {
    /** Vant `state.distance`（已过 [ease] 阻尼），单位 **px**。 */
    internal var distance by mutableFloatStateOf(0f)

    /** Vant `state.status`。 */
    internal var status by mutableStateOf(PullStatus.Normal)

    /** Vant `state.duration`（ms）：拖拽中 `0`，松手 / v-model 变化时 `animationDuration`。 */
    internal var duration by mutableIntStateOf(0)

    /** 由 [VanPullRefresh] 注入：是否配置了 `success-text`（决定 finishRefresh 走哪条分支）。 */
    internal var hasSuccessTip by mutableStateOf(false)

    // —— 手势账本（跨事件累计） ——
    /** 本次手势是否已由下拉刷新接管（对应 Vant 的 `reachTop`，一旦成立在本手势内锁定）。 */
    internal var owning by mutableStateOf(false)
    /** 原始纵向累计位移（Vant `touch.deltaY`），px。 */
    internal var rawY = 0f
    /** 方向判定累计（Vant `touch.offsetX/offsetY` + `getDirection`）。 */
    internal var accX = 0f
    internal var accY = 0f

    /** v-model 语义：当前是否正在刷新。 */
    val isLoading: Boolean get() = status == PullStatus.Loading

    /** 与 [isLoading] 同义，便于业务侧按语义取名。 */
    val isRefreshing: Boolean get() = isLoading

    /**
     * 业务加载完成后调用 —— 对应 Vant `watch(modelValue)` 中 `value === false` 的分支：
     * 传了 `success-text` 就停留在成功文案 `success-duration` 后收起，否则**立即收起、不留提示**
     * （本工程仅 `BankCard/InstallmentList.vue` 显式传了「刷新成功」）。
     */
    fun finishRefresh() {
        duration = ANIMATION_DURATION
        if (hasSuccessTip) {
            status = PullStatus.Success
        } else {
            distance = 0f
            status = PullStatus.Normal
        }
    }

    internal fun setStatus(dist: Float, isLoading: Boolean = false, pullDistancePx: Float) {
        distance = dist
        status = when {
            isLoading -> PullStatus.Loading
            dist <= 0f -> PullStatus.Normal
            dist < pullDistancePx -> PullStatus.Pulling
            else -> PullStatus.Loosing
        }
    }

    /** Vant `isTouchable()`：加载中 / 成功提示期间 / disabled 时完全不接受新手势。 */
    internal fun isTouchable(disabled: Boolean): Boolean =
        !disabled && status != PullStatus.Loading && status != PullStatus.Success

    /** Vant `onTouchEnd`。 */
    internal fun onRelease(headHeightPx: Float, pullDistancePx: Float) {
        if (!owning) {
            // ⚠ 松手时只要还有位移就必须归位。Vant 的 `onTouchEnd` 不区分"这次手势是不是我们抢到的"，
            //   只要 `reachTop && deltaY` 就 `setStatus(0)`（PullRefresh.mjs:142-153）。
            //   原来这里只 `resetGesture()` 就 return ⇒ 手指往回推时 `onPreScroll` 把 owning 置了 false，
            //   此后 `distance` 冻结在非 0、刷新头文案常驻、内容卡在偏移位置 ——
            //   负责人反馈「下拉刷新功能怪异、无法正常丝滑的下拉刷新」的机制就在这里。
            if (status != PullStatus.Loading && distance > 0f) {
                duration = ANIMATION_DURATION
                setStatus(0f, pullDistancePx = pullDistancePx)
            }
            resetGesture()
            return
        }
        owning = false
        duration = ANIMATION_DURATION
        if (status == PullStatus.Loosing) {
            setStatus(headHeightPx, isLoading = true, pullDistancePx = pullDistancePx)
        } else {
            setStatus(0f, pullDistancePx = pullDistancePx)
        }
        resetGesture()
    }

    internal fun resetGesture() {
        owning = false
        rawY = 0f
        accX = 0f
        accY = 0f
    }

    enum class PullStatus { Normal, Pulling, Loosing, Loading, Success }

    internal companion object {
        /** Vant `animationDuration` 默认 300ms。 */
        const val ANIMATION_DURATION = 300

        /** Vant `successDuration` 默认 **500ms**（旧实现误用 1200ms）。 */
        const val SUCCESS_DURATION = 500L

        /**
         * Loading 态兜底超时（**Vant 没有这个概念，属安卓侧防御**）：
         * 业务侧忘记 `finishRefresh()` / 请求卡死时，强制收起刷新头，避免永久卡住。
         */
        const val LOADING_TIMEOUT_MS = 20_000L
    }
}

/** Vant `ease(distance)`：超过 `pullDistance` 后分段阻尼（折半 → 四分之一）。入参/返回均为 **px**。 */
private fun ease(distance: Float, pullDistance: Float): Float {
    var d = distance
    if (d > pullDistance) {
        d = if (d < pullDistance * 2) {
            pullDistance + (d - pullDistance) / 2
        } else {
            pullDistance * 1.5f + (d - pullDistance * 2) / 4
        }
    }
    return d.roundToInt().toFloat()
}

@Composable
fun rememberVanPullRefreshState(): VanPullRefreshState = remember { VanPullRefreshState() }

@Composable
fun VanPullRefresh(
    state: VanPullRefreshState = rememberVanPullRefreshState(),
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    successText: String? = null,
    pullingText: String = "下拉即可刷新...",
    loosingText: String = "释放即可刷新...",
    loadingText: String = "加载中...",
    disabled: Boolean = false,
    /** Vant `head-height`，默认 50（CSS px ⇒ dp）。 */
    headHeight: Int = 50,
    /** Vant `pull-distance`；`null` 表示与 [headHeight] 相同。 */
    pullDistance: Int? = null,
    /** Vant `success-duration`（ms）。 */
    successDuration: Long = VanPullRefreshState.SUCCESS_DURATION,
    /** Vant `animation-duration`（ms）。 */
    animationDuration: Int = VanPullRefreshState.ANIMATION_DURATION,
    isChildAtTop: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val c = LocalVantColors.current
    val density = LocalDensity.current

    // ⚠ 阈值必须把 dp 换算成 px：Vant 的 50 是 CSS px，Compose 的 Offset.y 是物理 px。
    val headHeightPx = with(density) { headHeight.dp.toPx() }
    val pullDistancePx = with(density) { (pullDistance ?: headHeight).dp.toPx() }

    // 通过 rememberUpdatedState 传给长期存活的 connection / pointerInput，避免捕获过期值
    val curChildAtTop = rememberUpdatedState(isChildAtTop)
    val curDisabled = rememberUpdatedState(disabled)
    val curHeadH = rememberUpdatedState(headHeightPx)
    val curPullD = rememberUpdatedState(pullDistancePx)

    LaunchedEffect(successText) { state.hasSuccessTip = !successText.isNullOrEmpty() }

    // status ⇒ Loading：通知业务加载（Vant `nextTick(() => emit("refresh"))`）
    LaunchedEffect(state.status) {
        if (state.status == VanPullRefreshState.PullStatus.Loading) {
            onRefresh()
            // ⚠ 兜底：Vant 没有超时概念，但业务侧**必须**在数据回来后调 [VanPullRefreshState.finishRefresh]。
            //   一旦忘记回调（或请求卡死、协程被取消），刷新头会**永久卡在「加载中」**——
            //   这是「下拉刷新抽风」最难排查的一种。这里超时强制收起兜底。
            //   正常流程下状态早就变 Normal ⇒ 本 LaunchedEffect 被重启、`delay` 自动取消，不会误伤。
            delay(VanPullRefreshState.LOADING_TIMEOUT_MS)
            if (state.status == VanPullRefreshState.PullStatus.Loading) state.finishRefresh()
        }
    }

    // status ⇒ Success：停留 success-duration 后收起（Vant `showSuccessTip`）
    LaunchedEffect(state.status) {
        if (state.status == VanPullRefreshState.PullStatus.Success) {
            delay(successDuration)
            state.duration = animationDuration
            state.setStatus(0f, pullDistancePx = curPullD.value)
        }
    }

    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 未接管本次手势 → 一律不消费，让子列表正常滚动
                if (!state.owning) return Offset.Zero
                if (!state.isTouchable(curDisabled.value)) {
                    state.owning = false
                    return Offset.Zero
                }

                state.accX += abs(available.x)
                state.accY += abs(available.y)

                if (available.y >= 0f) {
                    // Vant：deltaY >= 0 才更新 distance
                    state.rawY += available.y
                    state.duration = 0
                    state.setStatus(ease(state.rawY, curPullD.value), pullDistancePx = curPullD.value)
                    return available.copy(x = 0f)
                }
                // 手指回推（deltaY < 0）：Vant 不 preventDefault，把事件交还容器滚动，distance 冻结
                state.owning = false
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (state.owning) return Offset.Zero          // 已在 onPreScroll 消费

                if (available.y <= 0f) {
                    // 上滑 / 静止：清空方向累计（近似 Vant 每次 touchstart 的 `touch.reset()`）
                    state.accX = 0f
                    state.accY = 0f
                    return Offset.Zero
                }
                state.accX += abs(available.x)
                state.accY += abs(available.y)

                if (!state.isTouchable(curDisabled.value)) return Offset.Zero
                if (consumed.y != 0f) return Offset.Zero      // 子级还在消费 ⇒ 没到顶
                if (!curChildAtTop.value()) return Offset.Zero // Vant reachTop
                // 方向判定（Vant `getDirection(offsetX, offsetY)` ⇒ 只有纵向时才 vertical）
                if (state.accX > state.accY) return Offset.Zero

                state.owning = true
                state.rawY = available.y
                state.duration = 0
                state.setStatus(ease(state.rawY, curPullD.value), pullDistancePx = curPullD.value)
                return available.copy(x = 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // ⚠ 只有本次手势被下拉接管时才吸收速度；否则放行，保证列表惯性滚动正常
                if (!state.owning) return Velocity.Zero
                state.onRelease(curHeadH.value, curPullD.value)
                return available
            }
        }
    }

    // 拖拽结束（手指抬起 / 取消）：NestedScroll 只在「甩动」时回调，慢速松手不触发，
    // 故用 pointerInput 监听 Release/Exit 兜底（不消费任何事件，不影响滚动）。
    val releaseTracker = Modifier.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                // 任一指针抬起 ⇒ 手势结束（用 `pressed` 判定而非 PointerEventType，兼容性更好）
                if (event.changes.any { !it.pressed }) {
                    state.onRelease(curHeadH.value, curPullD.value)
                }
            }
        }
    }

    // Vant：`state.duration` 为 0 时拖拽跟手、为 animationDuration 时平滑回弹
    val animatedDistance by animateFloatAsState(
        targetValue = state.distance,
        animationSpec = tween(durationMillis = if (state.duration <= 0) 0 else state.duration),
        label = "van-pull-refresh-distance",
    )

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()                 // .van-pull-refresh{overflow:hidden}
            .nestedScroll(connection)
            .then(releaseTracker)
            .then(modifier),
    ) {
        // —— .van-pull-refresh__track ——
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = animatedDistance }) {
            // —— .van-pull-refresh__head：absolute + translateY(-100%)，高度**固定** headHeight ——
            if (state.status != VanPullRefreshState.PullStatus.Normal) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headHeight.dp)
                        .offset(y = -headHeight.dp),
                ) {
                    when (state.status) {
                        VanPullRefreshState.PullStatus.Loading -> VanLoading(
                            size = 16.sp,                       // --van-pull-refresh-loading-icon-size
                            text = loadingText,
                            textColor = c.textTertiary,
                        )
                        VanPullRefreshState.PullStatus.Success -> HeadText(successText.orEmpty(), c.textTertiary)
                        VanPullRefreshState.PullStatus.Loosing -> HeadText(loosingText, c.textTertiary)
                        else -> HeadText(pullingText, c.textTertiary)
                    }
                }
            }
            content()
        }
    }
}

/** `--van-pull-refresh-head`：font-size = `--van-font-size-md`(14px)、color = `--van-text-color-2`、居中。 */
@Composable
private fun HeadText(text: String, color: Color) {
    if (text.isEmpty()) return
    BasicText(
        text,
        style = TextStyle(color = color, fontSize = vantDimens.fontSp(14f), textAlign = TextAlign.Center),
    )
}
