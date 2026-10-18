package com.live.vant.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

/**
 * 动画缓动 token（**常量**，与主题无关 —— 主题相关的时长放在 [VantDimens] 的 `duration*` 字段）。
 *
 * 与 web 对照：web 全站过渡曲线统一为 `cubic-bezier(.4,0,.2,1)`，原生这里用 [StandardEasing]
 * 对齐它，避免各界面各自 new 一个 `CubicBezierEasing(0.4f,0f,0.2f,1f)`（既重复又容易写错参数）。
 *
 * 用法：`tween(vantDimens.durationBase, easing = MotionTokens.StandardEasing)`。
 */
object MotionTokens {
    /** 标准缓动 = `cubic-bezier(.4,0,.2,1)` = Compose 的 [FastOutSlowInEasing]。web 全站过渡曲线。 */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** 强调缓动 = `cubic-bezier(.05,.7,.1,1)`（强调型入场 / 大位移）。 */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 减速缓动（仅出场用）= `cubic-bezier(0,0,.2,1)`。 */
    val DecelerateEasing: Easing = FastOutLinearInEasing

    /** 加速缓动（仅入场用）= `cubic-bezier(.4,0,1,1)`。 */
    val AccelerateEasing: Easing = LinearOutSlowInEasing
}
