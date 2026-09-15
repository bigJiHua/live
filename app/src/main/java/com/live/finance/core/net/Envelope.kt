package com.live.finance.core.net

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

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
) {
    val isSuccess: Boolean get() = status == 200 || status == 202 || (httpStatus in 200..299 && status == 0)
    val isPinVerify: Boolean get() = code == 8303
}

/** 业务调用结果（仓储层对外统一返回）。 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T?, val message: String) : ApiResult<T>
    data class Fail(val message: String, val status: Int = -1) : ApiResult<Nothing>
    object Unauthorized : ApiResult<Nothing>
    data class NeedPin(val message: String, val payload: JsonElement?) : ApiResult<Nothing>
    object Locked : ApiResult<Nothing>
    object RateLimited : ApiResult<Nothing>
}

/** 信封 → 业务结果（状态分流，对齐 web 响应拦截器）。 */
fun Envelope.toResult(): ApiResult<JsonElement?> = when {
    httpStatus == 401 || status == 401 -> ApiResult.Unauthorized
    httpStatus == 423 || code == 8304 -> ApiResult.Locked
    httpStatus == 429 || status == 429 -> ApiResult.RateLimited
    code == 8303 -> ApiResult.NeedPin(message, data)
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
