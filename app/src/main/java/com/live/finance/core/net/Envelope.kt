package com.live.finance.core.net

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.AppConfig
import com.live.vant.feedback.VanToastController
import com.live.vant.feedback.VanToastType
import com.live.finance.core.net.showFail

/** 统一 Gson 实例（宽松、保 key）。 */
val gson: Gson = Gson()

/**
 * 后端信封（响应为明文 JSON，结构对齐 res.say / res.json）：
 * { "status":200, "message":"...", "data":{...}|[...], "code":8303?, "ismessage":true? }
 * data 既可能是对象（/account、/auth/me）也可能是数组（/card），故用 JsonElement?。
 */
data class Envelope(
    val httpStatus: Int,
    val status: Int,
    val code: Int?,
    val message: String,
    val data: JsonElement?,
    val isMessage: Boolean,
    /** 整个响应体（部分接口把 token 等放在顶层而非 data 内，故保留）。 */
    val root: JsonObject? = null,
    /**
     * **传输层压根没走通**（断网 / DNS / 超时 / TLS）—— 此时 [httpStatus] 与 [status] 都是 `-1` 占位。
     * [toResult] 见到它就返回 [ApiResult.Network] ⇒ 弹窗**灰**。
     *
     * ⚠ 加在末尾（不插中间）—— 项目约定「data class 新字段一律追加末尾」。
     */
    val transportFailed: Boolean = false,
) {
    val isSuccess: Boolean get() = status == 200 || status == 202 || (httpStatus in 200..299 && status == 0)
    val isPinVerify: Boolean get() = code == 8303

    companion object {
        /**
         * 传输层异常 ⇒ 造一个「没拿到响应」的信封。
         *
         * 文案按异常类型分档，比笼统的「网络异常」好定位：
         * 断网/域名解析不了 vs 连上了但超时，用户能采取的动作不一样。
         */
        fun transport(e: Throwable): Envelope = Envelope(
            httpStatus = -1,
            status = -1,
            code = null,
            message = when (e) {
                is java.net.UnknownHostException -> "网络不可用，请检查网络连接"
                is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
                is java.net.ConnectException -> "无法连接到服务器，请稍后重试"
                else -> "网络异常，请稍后重试"
            },
            data = null,
            isMessage = false,
            transportFailed = true,
        )
    }
}

/** 业务调用结果（仓储层对外统一返回）。 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T?, val message: String) : ApiResult<T>
    data class Fail(val message: String, val status: Int = -1) : ApiResult<Nothing>
    object Unauthorized : ApiResult<Nothing>
    data class NeedPin(val message: String, val payload: JsonElement?) : ApiResult<Nothing>
    object Locked : ApiResult<Nothing>
    object RateLimited : ApiResult<Nothing>

    /**
     * **拿不到响应** —— 本地连不上 API（断网 / DNS / 超时），或远端 HTTP **5xx** 故障。
     *
     * ⭐ 与 [Fail] 分开是为了**弹窗颜色**（负责人 2026-09-21 的五色语义）：
     *   [Fail] = 经过 API 往返、后端明确告诉我们原因 ⇒ **红**；
     *   [Network] = 根本没走通 ⇒ **灰**。
     * 早先两者都塞进 `Fail(message="网络异常")`，颜色就分不出来。
     */
    data class Network(val message: String = "网络异常，请稍后重试") : ApiResult<Nothing>
}

/** 信封 → 业务结果（状态分流，对齐 web 响应拦截器）。 */
fun Envelope.toResult(): ApiResult<JsonElement?> = when {
    // ⚠ **传输层失败最先判**：此时 httpStatus/status 都是 -1 占位，
    //   落到下面任何一个分支都会被误判（比如 -1 会掉进最后的 else → 红）。
    transportFailed -> ApiResult.Network(message)
    // ⚠ **PIN 业务码必须先于 401 判**（web `response.js` 就是这个顺序：`isPinResponse(res)` 在
    //   `res.status === 401 → forceLogin` 之前 return）。后端两条链路都会把 status 写成 401：
    //   中间件 `pinSecurityGuard.respond()` 对 8302/8303/8304 一律回 `{code, status:401}`，
    //   而 `/security/pin/verify` 的错误分支回 `{code:8302, status:200}` —— 只有 `code` 可靠。
    //   判错序的后果：取消 PIN / PIN 输错 都被当成会话失效 → 直接踢回登录页。
    code == AppConfig.Pin.NEED_VERIFY -> ApiResult.NeedPin(message, data)
    code == AppConfig.Pin.ERROR -> ApiResult.Fail(message.ifEmpty { "PIN 码错误" }, code)
    code == AppConfig.Pin.LOCKED -> ApiResult.Locked
    code == AppConfig.Pin.SUCCESS -> ApiResult.Ok(data, message)
    // ⚠ **5xx 必须先于下面的 401/423/429 之外的分支判**：远端故障 = 灰（Network），不是红（Fail）。
    //   放在 401 之后（PIN 业务码那段之前）会更糟 —— 后端 PIN 中间件用 401，不是 5xx，所以顺序上
    //   放在 401/423/429 之后、业务 status 之前即可。
    httpStatus >= 500 -> ApiResult.Network(message.ifEmpty { "服务器开小差了，请稍后重试" })
    // 🔄 P2-18（2026-10-08）：nonce 复用（8401）不再折叠成「业务异常」。
    //   正常路径已在 ApiClient 各通道（request/uploadRaw/upload/download/握手）自动重发一次；
    //   能走到这里说明**重发仍被判重放** —— 给明确文案（对齐 web NONCE_REPLAY_FALLBACK_MESSAGE），
    //   用户重试即可，而不是一脸懵的"业务异常"。
    code == AppConfig.Pin.NONCE_REPLAY ->
        ApiResult.Fail(message.ifEmpty { "请求重复提交，请稍后重试" }, 8401)
    httpStatus == 401 || status == 401 -> ApiResult.Unauthorized
    httpStatus == 423 -> ApiResult.Locked
    httpStatus == 429 || status == 429 -> ApiResult.RateLimited
    status == 200 || status == 202 -> ApiResult.Ok(data, message)
    status == 204 || httpStatus == 204 -> ApiResult.Ok(null, message.ifEmpty { "暂无数据" })
    isMessage -> ApiResult.Ok(data, message)
    else -> ApiResult.Fail(message.ifEmpty { "业务异常" }, status)
}

/** 对 Ok 的 data 做类型转换，其余透传（非 Ok 均为 ApiResult<Nothing>，向上协变安全）。 */
@Suppress("UNCHECKED_CAST")
inline fun <R> ApiResult<JsonElement?>.map(transform: (JsonElement?) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Ok -> try {
            ApiResult.Ok(transform(data), message)
        } catch (e: Exception) {
            // 单行数据形状异常（对象/数组当字符串取等）不再冒泡到进程崩溃
            ApiResult.Fail("数据解析失败：${e.message}")
        }
        else -> this as ApiResult<R>
    }

/**
 * **非 Ok 结果的用户可读文案** —— 页面层 `when` 的兜底分支统一走这里，别写死。
 *
 * 背景（2026-09-20 负责人反馈「后端给了 message，提示里却是笼统的失败文案」）：
 * 页面层大量写成
 * ```
 * when (val r = repo.xxx()) {
 *     is ApiResult.Ok -> ...
 *     else -> toast.show("加载失败")   // ✗ 后端 message 被这句盖掉
 * }
 * ```
 * 后端 400 `{"status":400,"message":"非法请求！"}` 明明带了原因，用户看到的却是「加载失败」。
 * 改成 `else -> toast.showFail(r, "加载失败")` 后：
 *  - [ApiResult.Fail] 有后端文案 → 原样展示；没有（后端只回了「业务异常」这类占位）→ 用 [fallback]；
 *  - [ApiResult.Unauthorized] / [Locked] / [RateLimited] / [NeedPin] 这类**没有后端文案**的分支，
 *    给固定中文，避免退化成「加载失败」这种无法定位的提示；
 *  - **颜色也一起对了**（见 [failToastType] / [showFail]）：网络/5xx 灰，业务失败红。
 *
 * ⚠ 2026-09-21 起**不要**再写 `toast.show(r.failMessage("加载失败"))` ——
 *   那个不带 type ⇒ 默认 `VanToastType.Text` ⇒ **蓝色**（Text 现在是「普通信息」）。
 *   统一用 `toast.showFail(r, "加载失败")`。
 */
fun ApiResult<*>.failMessage(fallback: String = "操作失败"): String = when (this) {
    is ApiResult.Ok -> ""
    is ApiResult.Fail -> message.takeIf { it.isNotBlank() && it !in PLACEHOLDER_MESSAGES } ?: fallback
    is ApiResult.Network -> message.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
    ApiResult.Unauthorized -> "登录已失效，请重新登录"
    is ApiResult.NeedPin -> message.takeIf { it.isNotBlank() } ?: "需要安全验证"
    ApiResult.Locked -> "已锁定，请重新登录"
    ApiResult.RateLimited -> "操作过于频繁，请稍后再试"
}

/**
 * 非 Ok 结果对应的**弹窗颜色**（负责人 2026-09-21 的五色语义）。
 *
 *   🟢 绿 200/成功 ｜ 🔴 红 API 往返后业务失败 ｜ 🟡 黄 警告（限流 429，负责人 2026-09-21 口径的举例场景）
 *   ⚪ 灰 拿不到响应（[ApiResult.Network]：本地连不上 / 远端 5xx）｜ 🔵 蓝 普通信息
 *
 * ⚠ 除限流外，黄色仍是「前端显式指定」的语义；web 端 429 直接跳 `/429` 页、无 toast 路径。
 */
fun ApiResult<*>.failToastType(): VanToastType = when (this) {
    is ApiResult.Network -> VanToastType.Info // 灰
    ApiResult.RateLimited -> VanToastType.Warning // 黄：限流（web 同场景跳 /429 页）
    else -> VanToastType.Fail // 红
}

/**
 * 页面层 `when` 兜底分支的**一行入口**：文案 + 颜色一次给对，别再手写两件事。
 *
 * ```kotlin
 * when (val r = graph.bill.detail(id)) {
 *     is ApiResult.Ok -> ...
 *     else -> toast.showFail(r, "加载失败")   // 后端 message 透传；网络/5xx 自动变灰
 * }
 * ```
 *
 * ⚠ 可空结果（`runCatching{...}.getOrNull()`）要自己兜：
 *   `toast.showFail(r ?: return, "加载失败")` 或 `r?.let { toast.showFail(it, "加载失败") }`。
 */
fun VanToastController.showFail(r: ApiResult<*>, fallback: String = "操作失败") {
    show(r.failMessage(fallback), r.failToastType())
}

/** 后端在「没给具体原因」时回的占位文案，等同于空。 */
private val PLACEHOLDER_MESSAGES = setOf("业务异常", "请求失败", "网络异常", "操作失败")
