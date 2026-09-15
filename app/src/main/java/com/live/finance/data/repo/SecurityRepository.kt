package com.live.finance.data.repo

import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult

/** PIN 安全（security/pin 系列）。body 经 ApiClient 包成 {data:{...}}，与后端 req.body.data 对齐。 */
interface SecurityRepository {
    suspend fun setPin(pin: String): ApiResult<Unit>
    suspend fun changePin(oldPin: String, newPin: String): ApiResult<Unit>
    suspend fun verifyPin(pin: String): ApiResult<Unit>
}

class FakeSecurityRepository : SecurityRepository {
    override suspend fun setPin(pin: String): ApiResult<Unit> = ApiResult.Ok(Unit, "PIN 已设置")
    override suspend fun changePin(oldPin: String, newPin: String): ApiResult<Unit> = ApiResult.Ok(Unit, "PIN 已修改")
    override suspend fun verifyPin(pin: String): ApiResult<Unit> =
        if (pin == "123456") ApiResult.Ok(Unit, "验证成功") else ApiResult.Fail("PIN 错误")
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
}
