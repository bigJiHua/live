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

    /** 由 ApiClient 调用：触发弹窗并挂起，返回票据（null = 取消/失败）。 */
    suspend fun awaitVerify(challenge: JsonElement?): PinTicket? {
        // 已有进行中的请求则复用（同一时刻只弹一次）
        pending?.let { return it.await() }
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

    /** UI 验证/取消后回调。 */
    fun resolve(ticket: PinTicket?) {
        pending?.complete(ticket)
    }
}
