package com.live.finance.data.repo

import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.core.store.SessionStore
import com.live.finance.data.model.User

interface AuthRepository {
    suspend fun login(nameOrEmail: String, password: String): ApiResult<User>
    suspend fun getUserinfo(): ApiResult<User>
    suspend fun updateProfile(username: String, avatar: String? = null): ApiResult<Unit>
    suspend fun logout()
}

/** Wave-0 默认实现：本地假登录，保证工程可直接启动、可进入主干。 */
class FakeAuthRepository(private val session: SessionStore) : AuthRepository {
    override suspend fun login(nameOrEmail: String, password: String): ApiResult<User> {
        if (nameOrEmail.isBlank() || password.isBlank()) return ApiResult.Fail("请输入账号与密码")
        session.setToken("fake-token-${System.currentTimeMillis()}")
        return ApiResult.Ok(User(id = "u_demo", username = "演示用户", email = nameOrEmail), "登录成功")
    }

    override suspend fun getUserinfo(): ApiResult<User> =
        ApiResult.Ok(User(id = "u_demo", username = "演示用户", email = "demo@example.com"), "")

    override suspend fun updateProfile(username: String, avatar: String?): ApiResult<Unit> = ApiResult.Ok(Unit, "已更新")

    override suspend fun logout() { session.clear() }
}

/**
 * 真实实现：走 ApiClient。契约对照 web auth.js + 后端 auth/controller：
 *  - 登录 POST /auth/login，body = { nameOrEmail, password }（password 传明文即可，后端 resolvePassword 对字符串原样放行）；
 *    成功信封 { message, token, status:200 } —— token 在顶层，故从 env.root 读取。
 *  - 用户信息 GET /auth/me，data = { username, email(脱敏), avatar, identity }。
 */
class RemoteAuthRepository(
    private val client: ApiClient,
    private val session: SessionStore,
) : AuthRepository {

    override suspend fun login(nameOrEmail: String, password: String): ApiResult<User> {
        if (nameOrEmail.isBlank() || password.isBlank()) return ApiResult.Fail("请输入账号与密码")
        val body = JsonObject().apply {
            addProperty("nameOrEmail", nameOrEmail)
            addProperty("password", password)
        }
        val env = client.post("/auth/login", body)
        val result = env.toResult().map { User() }
        if (result is ApiResult.Ok) {
            // 登录成功信封把 token 放在顶层，且用户字段需另由 /auth/me 获取
            env.root?.get("token")?.asString?.takeIf { it.isNotEmpty() }?.let { session.setToken(it) }
        }
        return result
    }

    override suspend fun getUserinfo(): ApiResult<User> =
        client.get("/auth/me").toResult().map { data ->
            val o = data as? JsonObject
            User(
                username = o?.get("username")?.asString.orEmpty(),
                email = o?.get("email")?.asString.orEmpty(),
                avatar = o?.get("avatar")?.asString.orEmpty(),
            )
        }

    override suspend fun updateProfile(username: String, avatar: String?): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("username", username)
            if (avatar != null) addProperty("avatar", avatar)
        }
        return client.put("/user/profile", body).toResult().map { }
    }

    override suspend fun logout() { session.clear() }
}
