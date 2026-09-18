package com.live.finance.data.repo

import com.google.gson.JsonObject
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult

/** PIN 安全（security/pin 系列）。body 经 ApiClient 包成 {data:{...}}，与后端 req.body.data 对齐。 */
interface SecurityRepository {
    suspend fun setPin(pin: String): ApiResult<Unit>
    suspend fun changePin(oldPin: String, newPin: String): ApiResult<Unit>
    suspend fun verifyPin(pin: String): ApiResult<Unit>

    /** 是否已设置 PIN（对应 web `securityApi.checkPin()`，GET /security/pin/check：status 200=已设置，400=未设置）。 */
    suspend fun hasPinSet(): Boolean

    /**
     * 风险路由 PIN 验证（对应 web `securityApi.verifyRoutePin()`，POST /security/pin/route-verify）：
     * body `{pin, challengeId, requestUrl, method}`，challenge 取自 8303 信封的 `data`；
     * 成功返回一次性路由令牌（重发原请求需带 `x-route-verify-token` 头）。
     */
    suspend fun routeVerify(pin: String, challenge: com.google.gson.JsonElement?): ApiResult<com.live.finance.core.PinTicket>
}

class FakeSecurityRepository : SecurityRepository {
    override suspend fun setPin(pin: String): ApiResult<Unit> = ApiResult.Ok(Unit, "PIN 已设置")
    override suspend fun changePin(oldPin: String, newPin: String): ApiResult<Unit> = ApiResult.Ok(Unit, "PIN 已修改")
    override suspend fun verifyPin(pin: String): ApiResult<Unit> =
        if (pin == "123456") ApiResult.Ok(Unit, "验证成功") else ApiResult.Fail("PIN 错误")

    /** 假数据下 PIN 固定为 123456（见上 verifyPin），故视作「已设置」。 */
    override suspend fun hasPinSet(): Boolean = true

    override suspend fun routeVerify(pin: String, challenge: com.google.gson.JsonElement?): ApiResult<com.live.finance.core.PinTicket> =
        if (pin == "123456") ApiResult.Ok(com.live.finance.core.PinTicket("fake-route-token"), "验证成功")
        else ApiResult.Fail("PIN 错误")
}

class RemoteSecurityRepository(private val client: ApiClient) : SecurityRepository {
    override suspend fun setPin(pin: String): ApiResult<Unit> =
        client.post("/security/pin/set", JsonObject().apply { addProperty("pin", pin) }).toResult().map { }

    override suspend fun changePin(oldPin: String, newPin: String): ApiResult<Unit> =
        client.post("/security/pin/change", JsonObject().apply {
            addProperty("oldPin", oldPin); addProperty("newPin", newPin)
        }).toResult().map { }

    override suspend fun verifyPin(pin: String): ApiResult<Unit> =
        client.post("/security/pin/verify", JsonObject().apply { addProperty("pin", pin) }).toResult().map { }

    // 不能走 toResult()：未设置时后端返回 status=400 且带 ismessage=true，会被 toResult 判为 Ok。
    override suspend fun hasPinSet(): Boolean =
        client.get("/security/pin/check").status == AppConfig.ST_OK

    override suspend fun routeVerify(
        pin: String,
        challenge: com.google.gson.JsonElement?,
    ): ApiResult<com.live.finance.core.PinTicket> {
        val c = challenge as? JsonObject ?: return ApiResult.Fail("风险验证参数缺失")
        val body = JsonObject().apply {
            addProperty("pin", pin)
            addProperty("challengeId", c.get("challengeId")?.takeIf { !it.isJsonNull }?.asLong ?: 0L)
            addProperty("requestUrl", c.get("requestUrl")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
            addProperty("method", c.get("method")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
        }
        return client.post("/security/pin/route-verify", body).toResult().map { data ->
            val o = data as? JsonObject
            com.live.finance.core.PinTicket(
                token = o?.get("token")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                headerName = o?.get("headerName")?.takeIf { !it.isJsonNull }?.asString
                    ?: com.live.finance.core.ROUTE_TOKEN_HEADER,
            )
        }
    }
}
