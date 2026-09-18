package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.core.store.SessionStore
import com.live.finance.data.model.User

interface AuthRepository {
    /**
     * 登录。password 为 `String`（明文/降级）或 `List<String>`（RSA 逐字符密文数组，对齐 web secureOnly）；
     * 后端 `resolvePassword` 自动判读解密。
     */
    suspend fun login(nameOrEmail: String, password: Any): ApiResult<User>
    suspend fun getUserinfo(): ApiResult<User>

    /**
     * 更新资料（PUT /user/profile）。**只支持 username / avatar 两个字段**（后端 controller 只读这两个，
     * 传 null 表示不改），成功返回后端回传的最新用户（信封 data.user）。
     */
    suspend fun updateProfile(username: String? = null, avatar: String? = null): ApiResult<User?>

    /**
     * 发送邮箱验证码（POST /user/email/send-code，body {email, type}）。
     * type = "email" 改邮箱（发到**新**邮箱）；type = "pwd" 改密码（后端取绑定邮箱，忽略 email 内容）。
     */
    suspend fun sendEmailCode(email: String, type: String): ApiResult<Unit>

    /** 修改邮箱（PUT /user/email/change，body {email, code}），成功返回最新用户。 */
    suspend fun changeEmail(email: String, code: String): ApiResult<User?>

    /** 修改登录密码（PUT /user/password/change，body {oldPassword, newPassword, code}）。 */
    suspend fun changePassword(oldPassword: String, newPassword: String, code: String): ApiResult<Unit>

    suspend fun logout()
}

/** Wave-0 默认实现：本地假登录，保证工程可直接启动、可进入主干。 */
class FakeAuthRepository(private val session: SessionStore) : AuthRepository {
    /** 内存态用户：让「改头像 / 改用户名 / 改邮箱」在 fake 变体下也能看到效果。 */
    private var user = User(id = "u_demo", username = "演示用户", email = "demo@example.com")

    /** 假验证码（与 FakeSecurityRepository 的 123456 口径一致）。 */
    private val fakeCode = "123456"

    override suspend fun login(nameOrEmail: String, password: Any): ApiResult<User> {
        val blank = when (password) {
            is String -> password.isBlank()
            is List<*> -> password.isEmpty()
            else -> true
        }
        if (nameOrEmail.isBlank() || blank) return ApiResult.Fail("请输入账号与密码")
        session.setToken("fake-token-${System.currentTimeMillis()}")
        user = user.copy(email = if (nameOrEmail.contains("@")) nameOrEmail else user.email)
        return ApiResult.Ok(user, "登录成功")
    }

    override suspend fun getUserinfo(): ApiResult<User> = ApiResult.Ok(user, "")

    override suspend fun updateProfile(username: String?, avatar: String?): ApiResult<User?> {
        if (username == null && avatar == null) return ApiResult.Fail("没有提供要更新的内容")
        if (username != null) user = user.copy(username = username)
        if (avatar != null) user = user.copy(avatar = avatar)
        return ApiResult.Ok(user, "资料更新成功")
    }

    override suspend fun sendEmailCode(email: String, type: String): ApiResult<Unit> =
        ApiResult.Ok(Unit, "验证码已发送")

    override suspend fun changeEmail(email: String, code: String): ApiResult<User?> {
        if (code != fakeCode) return ApiResult.Fail("验证码错误")
        user = user.copy(email = email)
        return ApiResult.Ok(user, "邮箱修改成功")
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String, code: String): ApiResult<Unit> =
        if (code != fakeCode) ApiResult.Fail("验证码错误") else ApiResult.Ok(Unit, "修改成功")

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

    override suspend fun login(nameOrEmail: String, password: Any): ApiResult<User> {
        if (nameOrEmail.isBlank()) return ApiResult.Fail("请输入账号与密码")
        val body = JsonObject().apply {
            addProperty("nameOrEmail", nameOrEmail)
            when (password) {
                is String -> addProperty("password", password)
                is List<*> -> {
                    if (password.isEmpty()) return ApiResult.Fail("密码不能为空")
                    val arr = JsonArray()
                    password.forEach { arr.add(it.toString()) }
                    add("password", arr)
                }
                else -> return ApiResult.Fail("密码格式异常")
            }
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

    override suspend fun updateProfile(username: String?, avatar: String?): ApiResult<User?> {
        // 只传「要改的字段」：后端 controller 只读 username/avatar，且全空会返回 400
        val body = JsonObject().apply {
            if (username != null) addProperty("username", username)
            if (avatar != null) addProperty("avatar", avatar)
        }
        return client.put("/user/profile", body).toResult().map { userOf(it) }
    }

    override suspend fun sendEmailCode(email: String, type: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("type", type)
        }
        return client.post("/user/email/send-code", body).toResult().map { }
    }

    override suspend fun changeEmail(email: String, code: String): ApiResult<User?> {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("code", code)
        }
        return client.put("/user/email/change", body).toResult().map { userOf(it) }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String, code: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("oldPassword", oldPassword)
            addProperty("newPassword", newPassword)
            addProperty("code", code)
        }
        return client.put("/user/password/change", body).toResult().map { }
    }

    override suspend fun logout() { session.clear() }
}

/** 从信封 data 里取 `data.user`（/user/profile、/user/email/change 都回这个形状）。 */
private fun userOf(data: com.google.gson.JsonElement?): User? {
    val u = (data as? JsonObject)?.get("user") as? JsonObject ?: return null
    return User(
        username = u.get("username")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        email = u.get("email")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        avatar = u.get("avatar")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
    )
}
