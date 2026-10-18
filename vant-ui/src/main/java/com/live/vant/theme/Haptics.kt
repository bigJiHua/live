package com.live.vant.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import java.util.concurrent.atomic.AtomicLong

/**
 * 「全局操作点击震动」开关（设置页「按键震动」区第二张卡），由 `:app` 的 `AppRoot` 注入；默认开。
 * 与 [LocalKeyHapticEnabled]（键盘）互相独立；判定都住在 local 里，组件不自己读设置。
 */
val LocalClickHapticEnabled = staticCompositionLocalOf { false }

/**
 * 震动效果档（设置页「震动效果」三选一），键盘触感与全局点击触感**共用**；由 `:app` 的 `AppRoot` 注入。
 */
val LocalHapticStrength = staticCompositionLocalOf { HapticStrength.MEDIUM }

/**
 * 震动效果三档。[primary] 是该档下发的主触感常量，[fallback] 是 ROM 没给主常量映射振动效果时的替补
 * （`performHapticFeedback` 返回 false 才试）。常量是编译期内联的 int：KEYBOARD_PRESS 虽是 API 27
 * 才加入，但 minSdk 24 上运行时只是「不认识 → 返回 false → 走替补」，不会崩（见构造书 §7.13 三级降级）。
 *
 *  - 轻 = `VIRTUAL_KEY`（软键轻触）
 *  - 中 = `KEYBOARD_PRESS`（= 第三十轮键盘震动的原手感，所以默认档是「中」）
 *  - 重 = `LONG_PRESS`
 */
enum class HapticStrength(val key: String, val primary: Int, val fallback: Int) {
    LIGHT("light", HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.CLOCK_TICK),
    MEDIUM("medium", HapticFeedbackConstants.KEYBOARD_PRESS, HapticFeedbackConstants.VIRTUAL_KEY),
    HEAVY("heavy", HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.VIRTUAL_KEY);

    companion object {
        fun resolve(key: String?): HapticStrength = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * 同一次点击的防双震时间戳：根层「全局点击」探针跑在 `Initial` pass（父先于子），而键盘按键的
 * `haptic()` 在 clickable 的 `onClick`（`Main` pass，子先于父）里触发 —— 同一次按键抬起，两个来源
 * 相隔不到 1ms。先到者落时间戳，后到者在这个窗口内直接跳过；50ms 远小于人手两次独立点按的间隔
 * （约 150ms+），不会吞掉合法的连续震动。
 */
private val lastHapticAt = AtomicLong(0L)
private const val HAPTIC_DEDUP_MS = 50L

/**
 * 全 App 触感的唯一出口（键盘 + 全局点击共用）。仍走 `View.performHapticFeedback`
 * （不需要 `VIBRATE` 权限、跟随系统「触摸反馈」开关，见构造书 §7.13），
 * 在原三级降级上把主常量换成档位对应的 [HapticStrength.primary]。
 */
internal fun View.performAppHaptic(strength: HapticStrength) {
    val now = SystemClock.uptimeMillis()
    val last = lastHapticAt.get()
    if (now - last < HAPTIC_DEDUP_MS) return
    lastHapticAt.set(now)
    if (!performHapticFeedback(strength.primary)) {
        if (!performHapticFeedback(strength.fallback)) {
            context.hostActivity()
                ?.window?.decorView
                ?.performHapticFeedback(strength.primary)
        }
    }
}

/**
 * 根层「全局点击」探针：挂在 `AppRoot` 整屏 `Box` 上，观察每一个**点按**（按下后未超出触摸斜率、
 * 未落入第二根手指、最终抬起）并在抬起时给一次触感。观察用的 `Initial` pass 父先于子、且**不消费
 * 任何事件** ⇒ 所有 clickable / 开关 / Tab 的行为完全不受影响。
 *
 * 判定与 `detectTapGestures` 同语义但**必须自实现**：clickable 自己会在 `Main` pass 消费抬起事件，
 * 直接用 `waitForUpOrCancellation` 会把每一次真点击都误判为「被取消」；而 `Initial` pass 看不到子代
 * 的滚动消费，只能自己比 [PointerInputScope.viewConfiguration.touchSlop] 来排除滚动/滑动。
 *
 * 刻意**不做**长按超时：长按列表项弹菜单也是一次「操作」，抬起时同样震一下。
 * 覆盖范围是主 window：Compose `Dialog`（弹窗）是独立 window，探针看不到 —— 弹窗内只有键盘触感。
 */
fun Modifier.globalClickHaptic(view: View, strength: HapticStrength): Modifier = pointerInput(view, strength) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downPos = down.position
        var isTap = true
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val changes = event.changes
            if (changes.fastAll { it.changedToUpIgnoreConsumed() }) break
            if (changes.size > 1 || changes.fastAny { (it.position - downPos).getDistance() > viewConfiguration.touchSlop }) {
                isTap = false
                break
            }
        }
        if (isTap) view.performAppHaptic(strength)
    }
}

/** 沿 `ContextWrapper` 链往上找宿主 Activity（`Dialog` 里的 `view.context` 仍是宿主 Activity）。 */
internal fun Context.hostActivity(): Activity? {
    var c: Context = this
    while (true) {
        if (c is Activity) return c
        val wrapper = c as? ContextWrapper ?: return null
        c = wrapper.baseContext
    }
}
