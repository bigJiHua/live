package com.live.finance.core

import com.google.gson.JsonElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** 后端 `pinLockGuard.HEADER_TOKEN`：风险路由验证通过后重发请求要带的头。 */
const val ROUTE_TOKEN_HEADER = "x-route-verify-token"

/**
 * PIN 验证票据（对应 web `pin.js` 的 verifyResult）：
 *  - [token] 为 null = 普通 PIN 验证（如 lock-system），重发请求不带额外头；
 *  - [token] 非空 = 风险路由验证（`action_type=route_verify`），重发请求需带 `[headerName]: token`。
 */
data class PinTicket(val token: String? = null, val headerName: String = ROUTE_TOKEN_HEADER)

/**
 * PIN 拦截协调器：任意请求返回 8303(需验证) 时挂起当前调用并通知 UI 弹 PIN 框，
 * 用户验证通过 → 返回 [PinTicket] 由 ApiClient 重发原请求；取消/失败 → 返回 null（原样返回 8303 结果）。
 *
 * ⚠ 两种 8303 要分开处理（对齐 web `utils/request/pin.js`）：
 *  - 信封 `data.action_type == "route_verify"` → 走 `POST /security/pin/route-verify`（带 challengeId/requestUrl/method），
 *    成功后拿一次性 token 重发；
 *  - 其它（如 lock-system 的系统锁）→ 走 `POST /security/pin/verify`，重发不带 token。
 *
 * 单实例挂在 AppGraph；ApiClient 通过 pinGate 回调进入；AppRoot 的弹窗 collect show。
 */
class PinCoordinator {
    /** 非 null 表示当前需要弹 PIN 框。 */
    val show = MutableStateFlow(false)

    /** 本次 8303 的挑战数据（信封 data），供 UI 调 routeVerify 使用。 */
    var challenge: JsonElement? = null
        private set

    private var pending: CompletableDeferred<PinTicket?>? = null

    /** web `pin.js` 的 `lastSuccessAt`：上次验证成功时刻，用于 30 秒冷却窗口。 */
    private var lastSuccessAt = 0L

    /** web `requestPinVerify` 里 `challenge.action_type === 'route_verify'` 的判定。 */
    private fun isRoute(c: JsonElement?): Boolean =
        (c as? com.google.gson.JsonObject)
            ?.get("action_type")?.takeIf { !it.isJsonNull }?.asString == "route_verify"

    /** web `PIN_CODE` 的冷却窗口口径：验证成功后 30 秒内的 8303 静默放行，不再弹窗。 */
    private val cooldownMs = 30_000L

    /**
     * 由 ApiClient 调用：触发弹窗并挂起，返回票据（null = 取消/失败/被拒）。
     *
     * @param skipCooldown 跳过冷却捷径。用于「走冷却静默重发后服务端仍回 8303」的场景
     *   （如系统刚被锁定、后端已清 verifiedMap）⇒ 必须回到正常弹窗，否则调用方拿到 8303 却无提示。
     *   对齐 web `requestPinVerify(originalRequest, verifyData, skipCooldown)`。
     *
     * 三条 web 语义（`requestPinVerify`）：
     *  1. **冷却窗口**：无进行中的验证且距上次成功 < 30s → 不弹窗，直接当作已通过（后端 30s 内也放行）；
     *  2. **类型冲突**：已有验证在跑、而这次的风险路由/普通验证与它不同类 → 直接拒（web「已有 PIN 验证正在进行」）；
     *  3. **复用**：同类且已在验证中 → 挂在同一个等待上（同一时刻只弹一次框）。
     */
    suspend fun awaitVerify(challenge: JsonElement?, skipCooldown: Boolean = false): PinTicket? {
        val route = isRoute(challenge)
        if (!skipCooldown && pending == null && lastSuccessAt > 0 &&
            System.currentTimeMillis() - lastSuccessAt < cooldownMs
        ) {
            return PinTicket(if (route) "" else null)
        }
        pending?.let { p ->
            if (isRoute(this.challenge) != route) return null
            return p.await()
        }
        this.challenge = challenge
        val d = CompletableDeferred<PinTicket?>()
        pending = d
        show.value = true
        val r = d.await()
        pending = null
        this.challenge = null
        show.value = false
        return r
    }

    /**
     * 主动弹窗要求解锁（对齐 web `showPinDialog()`）。
     *
     * 用于「没有挂起请求」的场景：点击顶栏锁定后，后端不会再借 lock-system 触发弹窗
     * （该路径已排除出 pinGate 队列），必须自己弹一次，否则用户锁定后看不到任何解锁入口。
     * 强制跳过冷却窗口 —— 刚锁定时 lastSuccessAt 可能仍是新鲜的，走冷却就不会弹框。
     */
    suspend fun requestUnlock(): PinTicket? = awaitVerify(null, skipCooldown = true)

    /**
     * UI 验证/取消后回调。票据非空 = 验证通过，此时起算 web 的 30 秒冷却窗口。
     *
     * ⚠ 票据为 null（用户取消 / 会话终止）时必须**清零** `lastSuccessAt`：
     * 对齐 web `cancelPinVerify()` 的 `lastSuccessAt = 0`。否则取消之后 30 秒内的请求
     * 仍会命中冷却捷径静默重发，用户点了取消却看到数据照常加载、且没有任何提示。
     */
    fun resolve(ticket: PinTicket?) {
        lastSuccessAt = if (ticket != null) System.currentTimeMillis() else 0L
        pending?.complete(ticket)
    }
}
