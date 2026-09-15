package com.live.finance.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * PIN 拦截协调器：当任意请求返回 8303(需验证) 时，挂起当前调用并通知 UI 弹 PIN 框，
 * 用户验证通过 → 重发原请求；取消/失败 → 原样返回 8303 结果。
 *
 * 单实例挂在 AppGraph；ApiClient 通过 pinGate 回调进入；AppRoot 的弹窗 collect show。
 */
class PinCoordinator {
    /** 非 null 表示当前需要弹 PIN 框。 */
    val show = MutableStateFlow(false)
    private var pending: CompletableDeferred<Boolean>? = null

    /** 由 ApiClient 调用：触发弹窗并挂起，返回是否验证通过。 */
    suspend fun awaitVerify(): Boolean {
        // 已有进行中的请求则复用
        pending?.let { return it.await() }
        val d = CompletableDeferred<Boolean>()
        pending = d
        show.value = true
        val r = d.await()
        pending = null
        show.value = false
        return r
    }

    /** UI 验证/取消后回调。 */
    fun resolve(ok: Boolean) {
        pending?.complete(ok)
    }
}
