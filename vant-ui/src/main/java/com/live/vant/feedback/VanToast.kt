package com.live.vant.feedback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.ToastPalette
import com.live.vant.theme.ToastStateColors
import kotlinx.coroutines.delay

/**
 * 消息弹窗类型 —— **五色语义（负责人 2026-09-21）**：
 *
 * | 枚举 | 色 | 何时用 |
 * |------|----|--------|
 * | [Success] | 🟢 绿 | HTTP 200 / 业务成功 |
 * | [Fail] | 🔴 红 | **经过 API 往返**后业务失败（4xx / 业务体 status≠200） |
 * | [Warning] | 🟡 黄 | API 往返后的「警告」级（**前端显式指定**，如限流） |
 * | [Info] | ⚪ 灰 | **拿不到响应**：本地连不上 API，或远端 5xx 故障 |
 * | [Text] / [Loading] | 🔵 蓝 | **普通信息** / 「进行中」 |
 *
 * ⚠ [Text] 从「灰」改成了「蓝」（2026-09-21）：灰被收窄成 [Info]（系统/网络层）。
 *   错误路径**别直接传 [Text]** —— 用 `ApiResult.failToastType()` 或
 *   `VanToastController.showFail(r, fallback)` 自动分流，否则错误会显示成蓝色。
 */
enum class VanToastType { Text, Success, Fail, Warning, Info, Loading }

/**
 * 弹出位置。**2026-09-20 起全站统一为 [Top]**：消息落在顶部导航栏（nav header）下沿。
 * 枚举保留是为了调用点仍可显式指定。
 */
enum class VanToastPosition { Middle, Top, Bottom }

class VanToastMessage internal constructor(
    val message: String,
    val type: VanToastType,
    val position: VanToastPosition,
    val duration: Long,
    val forbidClick: Boolean,
    val icon: String?,
    /** 非空即走**长信息**形态（标题 + 描述两行），对应 web `showToast({ title, message })` */
    val title: String = "",
)

/**
 * `duration` 的哨兵值：**交给 [defaultDurationOf] 按状态算阶梯时长**（不写死一个统一值）。
 * 调用点想固定时长就显式传具体毫秒（`0` = 常驻）。
 */
const val VAN_TOAST_AUTO_DURATION = -1L

/**
 * 各状态的**默认存活时长**（阶梯，不统一）—— 负责人 2026-09-21 要求，与 web
 * `utils/toast/index.js` 的 `DURATION_OF_STATE` **逐值一致**。
 *
 * 原则：越「扫一眼就够」的越短，越需要用户读 / 做点什么的越长。
 *   · 成功 → **最短 1.5s**：只是确认「刚才那下成了」，挂久了纯挡视线；
 *   · 普通信息（Text，蓝）→ 2s；
 *   · 警告（黄）→ 2.5s：要看清「什么快不行了」；
 *   · 失败（红）→ 3s：要读后端给的原因；
 *   · 系统/网络（Info，灰）→ **最长 3.5s**：用户往往要据此决定「重试还是去查网络」；
 *   · loading → 0（常驻，由代码 `close()` 收尾）。
 *
 * ⚠ 长信息（有 title）额外 **+1.5s** —— 有两行要读。
 */
fun defaultDurationOf(type: VanToastType, isLong: Boolean = false): Long {
    val base = when (type) {
        VanToastType.Success -> 1_500L
        VanToastType.Text -> 2_000L
        VanToastType.Warning -> 2_500L
        VanToastType.Fail -> 3_000L
        VanToastType.Info -> 3_500L
        VanToastType.Loading -> 0L
    }
    return if (base == 0L) 0L else base + if (isLong) 1_500L else 0L
}

/** 控制器：对应 web 函数式 API（showToast / showSuccessToast / showFailToast / showLoadingToast / closeToast） */
class VanToastController internal constructor() {
    internal val current = mutableStateOf<VanToastMessage?>(null)
    internal var seq by mutableStateOf(0L)
        private set

    /** showToast(message) / showToast({ message, title, type, duration ... }) */
    fun show(
        message: String,
        type: VanToastType = VanToastType.Text,
        position: VanToastPosition = VanToastPosition.Top,
        duration: Long = VAN_TOAST_AUTO_DURATION,
        forbidClick: Boolean = false,
        icon: String? = null,
        title: String = "",
    ) {
        // ⚠ 默认不再是一个统一的 2000ms，而是按状态走阶梯（见 defaultDurationOf）
        val real = if (duration >= 0) duration else defaultDurationOf(type, title.isNotEmpty())
        seq++
        current.value = VanToastMessage(message, type, position, real, forbidClick, icon, title)
    }

    /** 长信息形态：标题 + 描述（web `showToast({ title, message })`） */
    fun long(
        title: String,
        message: String,
        type: VanToastType = VanToastType.Text,
        duration: Long = VAN_TOAST_AUTO_DURATION,
    ) = show(message, type, duration = duration, title = title)

    fun success(message: String) = show(message, VanToastType.Success)
    fun fail(message: String) = show(message, VanToastType.Fail)
    fun warning(message: String) = show(message, VanToastType.Warning)

    /**
     * 灰 = 系统/网络层（拿不到响应）。
     * 一般由 `ApiResult.failToastType()` / `showFail(r, fallback)` 自动分流，
     * 这里留一个手动入口以备特殊场景。
     */
    fun info(message: String) = show(message, VanToastType.Info)

    /** showLoadingToast：loading 类型默认不自动消失（duration=0） */
    fun loading(message: String = "", forbidClick: Boolean = true) =
        show(message, VanToastType.Loading, duration = 0, forbidClick = forbidClick)

    /** closeToast */
    fun close() { current.value = null }
}

val LocalVanToastController = staticCompositionLocalOf { VanToastController() }

@Composable
fun rememberVanToastController(): VanToastController =
    remember { VanToastController() }

/**
 * Toast 渲染宿主。用法与 web 端函数式 API 对应：
 * ```
 * val rootToast = rememberVanToastController()
 * VantTheme { CompositionLocalProvider(LocalVanToastController provides rootToast) {
 *     App()
 *     VanToastHost(rootToast)
 * } }
 * // 任意子组件内：
 * val toast = LocalVanToastController.current
 * toast.success("提交成功")   // ← showSuccessToast
 * ```
 *
 * 形态（2026-09-20 负责人给的设计稿，与 web `.app-toast*` 一致）：
 *  **浅色 tint 卡片 + accent 色圆环图标 + 关闭按钮**，支持「标题 + 描述」长信息；
 *  深浅主题各一套配色（[ToastPalette.Light] / [ToastPalette.Dark]，跟着 `LocalVantColors` 走）。
 *
 * ⚠ **2026-10-14 起不再挂在 Dialog 窗口上**（负责人反馈「弹提示期间页面点不动，像带了遮罩」）：
 *   宿主是**主窗口内的一层覆盖层** —— 调用点把它放进页面所在的那个容器（AppRoot 的整屏 Box，
 *   或任意 Box）即可。默认没有任何 pointerInput ⇒ 只是「看得见、点得透」，与 web
 *   `pointer-events:none` 同义；只有 `forbidClick`（如 loading）才挂透明拦截层吞点击。
 */
@Composable
fun VanToastHost(controller: VanToastController = LocalVanToastController.current) {
    val message = controller.current.value
    if (message == null) return

    val c = LocalVantColors.current
    LaunchedEffect(controller.seq) {
        if (message.duration > 0) {
            delay(message.duration)
            if (controller.current.value === message) controller.close()
        }
    }

    // ⚠ **不再用 Dialog 窗口承载**（负责人 2026-10-14：弹提示期间页面点不动，像带了遮罩）：
    //   系统 Dialog 是**独立且 touch-modal 的窗口** —— 即便把 dim 关成 0（没有视觉遮罩），
    //   窗口本身仍会吞掉全屏触摸 ⇒ 提示显示期间底下的页面全点不了。
    //   web 端 `.app-toast-root` 是 `pointer-events:none`，只有 `forbidClick` 才额外挂一层
    //   透明拦截层（`.app-toast-blocker`）。这里与 web 对齐：直接作为**主窗口内的覆盖层**渲染 ——
    //   没有 pointerInput 时天然不拦触摸；`forbidClick` 时才由 [swallowClicksModifier] 吞点击。
    Box(
        Modifier
            .fillMaxSize()
            .then(if (message.forbidClick) swallowClicksModifier() else Modifier),
        contentAlignment = when (message.position) {
            VanToastPosition.Top -> Alignment.TopCenter
            VanToastPosition.Bottom -> Alignment.BottomCenter
            else -> Alignment.Center
        },
    ) {
        // 入场动画：首帧 visible=false，下一帧置 true ⇒ 走 enter 过渡
        //
        // ⚠ 与 web 的**已知近似**（有意保留，别当成 bug 去"修"）：
        //   web `app-toast-in 0.24s cubic-bezier(.4,0,.2,1)` = 位移 -8px **且** 缩放 0.96 → 1，一条动画管两者；
        //   web 收起 `.is-hiding 0.2s` = 位移 -6px **且** 缩放 0.96。
        //   这里只做了「淡入 + 下滑 -1/4 卡高」（约 -11dp，接近 -8px）与「纯淡出」，
        //   没做 scale。差异在 4% 缩放量级，肉眼基本不可辨，故不引入 `scaleIn/scaleOut`
        //   去精确复刻（要复刻得把 dp→px 在 density 里先算好，得不偿失）。
        //   若哪天真要逐帧对齐，见 web `style.css` 的 `@keyframes app-toast-in` 与 `.app-toast.is-hiding`。
        var shown by remember(controller.seq) { mutableStateOf(false) }
        LaunchedEffect(controller.seq) { shown = true }

        androidx.compose.animation.AnimatedVisibility(
            visible = shown,
            enter = androidx.compose.animation.fadeIn(tween(200)) +
                androidx.compose.animation.slideInVertically(tween(240)) { -it / 4 },
            exit = androidx.compose.animation.fadeOut(tween(200)),
            modifier = when (message.position) {
                // 顶部：状态栏安全区 + 顶栏高度 46dp + 8dp 间距 = 54dp
                // （web `--toast-top-offset: calc(var(--nav-top-inset) + 46px + 8px)`；
                //   原生顶栏是 `VanNavBar` 的 `statusBarsPadding().height(46.dp)`）
                // ⚠ 覆盖层已落在**主窗口内**：非 edge-to-edge 时内容本就内缩于状态栏，
                //   `statusBarsPadding()` 自然为 0；若将来开了 edge-to-edge，它会给出真实
                //   安全区高度 —— 两种情形下的最终落点一致（都是「顶栏下沿 + 8dp」）。
                VanToastPosition.Top -> Modifier.statusBarsPadding().padding(top = 54.dp)
                VanToastPosition.Bottom -> Modifier.navigationBarsPadding().padding(bottom = 24.dp)
                else -> Modifier
            },
        ) {
            ToastCard(message, c.toast, onClose = { controller.close() })
        }
    }
}

/**
 * 消息卡片 —— 「浅色 tint 底 + accent 色圆环图标 + 关闭按钮」，对应 web `.app-toast`。
 *
 * 底色是**竖向渐变**（[ToastStateColors.bgTop] → [ToastStateColors.bgBottom]），
 * 正文用 palette 的常规文字色，只有图标与圆环用 accent 色。
 */
@Composable
private fun ToastCard(
    message: VanToastMessage,
    palette: ToastPalette,
    onClose: () -> Unit,
) {
    val state = when (message.type) {
        VanToastType.Success -> palette.success
        VanToastType.Warning -> palette.warning
        VanToastType.Fail -> palette.danger
        // 灰 = 系统/网络层（拿不到响应）
        VanToastType.Info -> palette.info
        // 蓝 = 普通信息 / 进行中（Text 与 Loading 都走它）
        else -> palette.notice
    }
    val withIcon = message.type != VanToastType.Text
    val isLong = message.title.isNotEmpty()
    // ⚠ 只有**带状态图标**的提示（成功 / 失败 / 警告 / 系统灰）给关闭按钮：
    //   纯文字提示是全站最高频的一类、大多 2 秒就消失，挂个 ✕ 是纯噪音；
    //   loading 由代码 `close()` 收尾，也不需要用户点。（与 web `WITH_CLOSE` 对齐）
    val closable = withIcon && message.type != VanToastType.Loading
    val shape = RoundedCornerShape(8.dp)

    // ⚠ **卡片宽度贴内容**（负责人 2026-09-21：「成功消息占满整个宽度？不是吧？」）：
    //   只给**上限**，不要给下限 —— 有 `min` 且文字短时，多出来的那段空白会落在 × 后面，
    //   既显得卡片空荡、× 也没贴到右边；去掉 `min` 后卡片宽度 = 内容自然宽，
    //   × 自然就落在卡片右缘（配合下面 `.weight(fill = false)`）。
    // ⚠ 另一半坑：文字**不要**用 `weight(1f, fill = true)` —— 加权 + fill 会让 Row 直接
    //   吃满父容器（屏宽 ~390dp）⇒ 卡片变成整屏宽。正是这次被骂的那个现象。
    val widthModifier = if (isLong) {
        Modifier.widthIn(max = 480.dp)
    } else {
        Modifier.widthIn(max = 420.dp)
    }

    Row(
        verticalAlignment = if (isLong) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .then(widthModifier)
            // ⚠ 必须显式传投影色：默认 `DefaultShadowColor` 是纯黑，
            //   浅色下比 web 重、深色下几乎看不见（web 深色是 rgba(0,0,0,.42)）。
            .shadow(8.dp, shape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
            .background(
                Brush.verticalGradient(listOf(state.bgTop, state.bgBottom)),
                shape,
            )
            .border(1.dp, palette.innerHairline, shape)
            // 内边距逐值对齐 web：短信息 `padding: 12px 14px`；
            // 长信息 `padding: 14px 14px 14px 16px`（**左边多 2px**，给图标留呼吸位）
            .padding(
                start = if (isLong) 16.dp else 14.dp,
                end = 14.dp,
                top = if (isLong) 14.dp else 12.dp,
                bottom = if (isLong) 14.dp else 12.dp,
            ),
    ) {
        if (withIcon) {
            ToastIcon(message.type, state)
        }

        if (isLong) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(start = if (withIcon) 10.dp else 0.dp)
                    // ⚠ **必须 `fill = false`**：`fill = true` 会让加权子项吃掉全部剩余空间，
                    //   Row 宽度直接顶到父容器（屏宽）⇒ 卡片整屏宽（2026-09-21 被骂的那个现象）。
                    //   `false` 时卡片宽度 = 内容自然宽（上限见 widthModifier），
                    //   而因为没有 `min`，内容右侧不会有空白 ⇒ × 天然贴在卡片右缘。
                    .weight(1f, fill = false),
            ) {
                BasicText(
                    message.title,
                    style = TextStyle(
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (message.message.isNotEmpty()) {
                    BasicText(
                        message.message,
                        style = TextStyle(
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                    )
                }
            }
        } else if (message.message.isNotEmpty()) {
            BasicText(
                message.message,
                style = TextStyle(
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier
                    .padding(start = if (withIcon) 10.dp else 0.dp)
                    // 同长信息：`fill = false`，卡片贴内容、× 贴右缘
                    .weight(1f, fill = false),
            )
        }

        if (closable) {
            // ⚠ 关闭按钮的点击区必须自己撑起来：图标本身只有 10dp，直接给 padding 会被
            //   `clickable` 盖在外面（点空白不触发）。用 24dp 的 Box 包一层最稳。
            //
            // ⚠ **× 的字形要与前面的状态图标「居中对齐」**（负责人 2026-09-21）：
            //   · 短信息：整行是 `Alignment.CenterVertically`，图标(20dp) 与这个 Box(24dp)
            //     都以行中线为准 ⇒ 字形自然对齐，不用管。
            //   · 长信息：整行是 `Alignment.Top`，两者都顶到行首 ⇒ 图标中心在 10dp、
            //     Box 中心在 12dp，**差 2dp**。用 `offset(y = -2.dp)` 把 Box 上提 2dp 找平。
            //     这里只能用 offset 不能用 padding —— 负 padding 不合法，
            //     而 offset 不改测量尺寸，不会把行高顶大。
            Box(
                modifier = Modifier
                    // 与文字的间隙对齐 web 的**视觉**间隙（不是盒间距）：
                    //   web = gap(10) + margin-left(4) + 字形居中留白((20-10)/2=5) = 19px
                    //   原生 = P + ((24-10)/2 = 7) ⇒ P = 12dp
                    .padding(start = 12.dp)
                    .offset(y = if (isLong) (-2).dp else 0.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(palette.textTertiary)
            }
        }
    }
}

/** 关闭按钮的 ✕（10×10 视框，两笔，圆头） */
@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val k = size.minDimension / 10f
        val w = 1.6f * k
        drawLine(
            color = color,
            start = Offset(1.5f * k, 1.5f * k),
            end = Offset(8.5f * k, 8.5f * k),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(8.5f * k, 1.5f * k),
            end = Offset(1.5f * k, 8.5f * k),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 20dp 圆环图标：极浅 accent 底 + accent 描边 + 居中字形。
 * 字形按 12×12 视框绘制（与 web 端 SVG 的 viewBox 一致），整体缩放到 11dp。
 */
@Composable
private fun ToastIcon(type: VanToastType, state: ToastStateColors) {
    if (type == VanToastType.Loading) {
        // ⚠ 只有 loading 才建无限动画；其他类型不建，避免无谓的常驻动画
        val transition = rememberInfiniteTransition(label = "toastSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "toastAngle",
        )
        ToastIconCanvas(type, state, angle)
    } else {
        ToastIconCanvas(type, state, 0f)
    }
}

@Composable
private fun ToastIconCanvas(type: VanToastType, state: ToastStateColors, angle: Float) {
    Canvas(Modifier.size(20.dp).rotate(angle)) {
        val d = size.minDimension
        val ring = 1.dp.toPx()
        drawCircle(color = state.accentSoft, radius = d / 2f)
        drawCircle(
            color = state.accent,
            radius = d / 2f - ring / 2f,
            style = Stroke(width = ring),
        )

        val glyph = 11.dp.toPx()
        val k = glyph / 12f
        withTransform({
            translate((d - glyph) / 2f, (d - glyph) / 2f)
            scale(k, k, pivot = Offset.Zero)
        }) {
            // 以下坐标全部在 12×12 的「字形空间」里，线宽也按该空间给（会被 scale 一起放大）
            val c = state.accent
            when (type) {
                VanToastType.Success -> {
                    val p = Path().apply {
                        moveTo(2f, 6.2f)
                        lineTo(4.8f, 9f)
                        lineTo(10f, 3.2f)
                    }
                    drawPath(p, c, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                VanToastType.Fail -> {
                    drawLine(c, Offset(2.6f, 2.6f), Offset(9.4f, 9.4f), 2f, StrokeCap.Round)
                    drawLine(c, Offset(9.4f, 2.6f), Offset(2.6f, 9.4f), 2f, StrokeCap.Round)
                }

                VanToastType.Warning -> {
                    val p = Path().apply {
                        moveTo(6f, 2f)
                        lineTo(11f, 10f)
                        lineTo(1f, 10f)
                        close()
                    }
                    drawPath(p, c, style = Stroke(1.4f, join = StrokeJoin.Round))
                    drawLine(c, Offset(6f, 4.5f), Offset(6f, 7.2f), 1.8f, StrokeCap.Round)
                    drawCircle(c, radius = 0.7f, center = Offset(6f, 8.8f))
                }

                VanToastType.Loading -> {
                    // 缺口圆弧：与 web `.app-toast--loading` 的 `stroke-dasharray="20.7 6.9"` 视觉一致。
                    // ⚠ 20.7 / (2π×4.4 ≈ 27.65) = **75%** ⇒ 必须 270°，不是 95°。
                    //   （2026-09-20 那次同步把 web 改成了 270°，原生这边漏改，一直停在 95°。）
                    drawArc(
                        color = c,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(1.6f, 1.6f),
                        size = Size(8.8f, 8.8f),
                        style = Stroke(1.8f, cap = StrokeCap.Round),
                    )
                }

                // 灰 = 系统/网络层：wifi 两段弧 + 圆点 + 一条斜杠（「断线」）。
                // 坐标/线宽照抄 web 的 `ICON_NETWORK`（12×12 视框），弧的 start/sweep 由 SVG 的
                // `A rx ry ...` 反算：弧 1 圆心(6, 9.05) r=6.4；弧 2 圆心(6, 9.51) r=3.4。
                VanToastType.Info -> {
                    drawArc(
                        color = c,
                        startAngle = -136f,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = Offset(-0.4f, 2.65f),
                        size = Size(12.8f, 12.8f),
                        style = Stroke(1.5f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = c,
                        startAngle = -134.9f,
                        sweepAngle = 89.8f,
                        useCenter = false,
                        topLeft = Offset(2.6f, 6.11f),
                        size = Size(6.8f, 6.8f),
                        style = Stroke(1.5f, cap = StrokeCap.Round),
                    )
                    drawCircle(c, radius = 0.9f, center = Offset(6f, 9.4f))
                    drawLine(c, Offset(1.6f, 10.4f), Offset(10.4f, 1.6f), 1.5f, StrokeCap.Round)
                }

                else -> {
                    // 兜底（正常到不了这里：Text 不带图标，其余类型都有分支）
                    drawLine(c, Offset(6f, 5.2f), Offset(6f, 9.2f), 2f, StrokeCap.Round)
                    drawCircle(c, radius = 1.1f, center = Offset(6f, 3f))
                }
            }
        }
    }
}

/** forbid-click：吞掉遮罩区域内的所有点击（对应 web 的 `.app-toast-blocker`） */
private fun swallowClicksModifier(): Modifier = Modifier.pointerInput(Unit) {
    detectTapGestures { /* 消费所有点击 */ }
}
