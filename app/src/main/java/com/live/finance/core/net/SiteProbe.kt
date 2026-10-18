package com.live.finance.core.net

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 站点连通性探测（2026-10-17「保存前必须连通」定稿）。
 *
 * ## 为什么需要
 * 用户手输 API 地址后如果填错，旧实现要等到点「立即登录」才暴露问题；更糟的是
 * `AppConfig.url(...).toHttpUrl()` 对非法地址会抛 `IllegalArgumentException`（在 try 之外），
 * 异常穿透协程 ⇒ 登录按钮永远转圈、一句提示都没有。
 * 现在「保存站点」这一步就发一个真实请求，**连不上就不允许保存**，从源头掐断错地址。
 *
 * ## 探测端点
 * `GET {base}/api/health`（Node 与 Go 后端各有一个，见 api/app.js、
 * API_Go/internal/srv/server.go）。选择新增独立端点而不是复用业务接口，原因：
 *  - 不鉴权、不查库、不写库 ⇒ 不会泄露 hasAdmin / 用户数这类信息，也无副作用；
 *  - 不经过 securityCheck / PIN / APP 签名守卫 ⇒ 手机时钟偏差、签名白名单都不会
 *    把「能连上」误判成「连不上」；
 *  - 返回固定 `data.service = "live-finance"` ⇒ 可判定「这个地址确实是本项目的后端」，
 *    而不只是「某个服务器返回了 200」。
 *
 * ⚠ 探测成功只证明「站点可达 + 身份匹配」，不证明数据库可用（登录时才会暴露 DB 故障，
 *   届时 UI 会弹窗提示，而不是静默转圈）。
 *
 * ## 超时
 * 独立短超时（连接 6s / 读 6s / 总 10s）：保存动作必须快速给结论，不能沿用 ApiClient
 * 的 15s（三轮超时叠加会让用户以为卡死）。
 */
object SiteProbe {

    /** 与后端 health 端点约定的服务标识（三处必须一致：Node / Go / 这里）。 */
    const val SERVICE_ID = "live-finance"

    private const val CONNECT_TIMEOUT_S = 6L
    private const val READ_TIMEOUT_S = 6L
    private const val CALL_TIMEOUT_S = 10L

    /**
     * 探测专用 client：短超时 + 关闭自动重试（填错地址时快速失败，别耗两次连接）。
     * 不启用 Pinning —— 用户可能填任意自建站点，锁定构建期域名会直接不可用。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** 探测结果：站点声明的版本（可能为空串，仅供提示/排障，不参与判定）。 */
    data class Info(val version: String)

    /**
     * 探测 [baseUrl]（调用方已通过 [com.live.finance.core.store.SiteStore.normalize] 归一化）。
     *
     * @return [ApiResult.Ok] = 可达且是本项目后端；[ApiResult.Fail] = 不可用（message 可直接展示）
     */
    suspend fun probe(baseUrl: String): ApiResult<Info> = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        val url = "$base/api/health"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val code = resp.code
                if (code !in 200..299) return@withContext ApiResult.Fail(httpMessage(code))
                val obj = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
                    ?: return@withContext ApiResult.Fail("该地址不是本站点服务（返回的不是标准接口响应）")
                val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val data = obj.getAsJsonObject("data")
                val service = data?.get("service")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                if (status != 200 || service != SERVICE_ID) {
                    return@withContext ApiResult.Fail(
                        "该地址不是本站点服务（服务标识不匹配：${service.ifEmpty { "无" }}）",
                    )
                }
                ApiResult.Ok(Info(data?.get("version")?.takeIf { !it.isJsonNull }?.asString.orEmpty()), "站点连接成功")
            }
        } catch (e: java.net.UnknownHostException) {
            ApiResult.Fail("域名解析失败，请检查站点地址是否拼写正确")
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Fail("连接超时，请检查网络或站点是否可访问")
        } catch (e: java.net.ConnectException) {
            ApiResult.Fail("无法连接到服务器，请检查地址与端口是否正确")
        } catch (e: javax.net.ssl.SSLException) {
            ApiResult.Fail("HTTPS 证书校验失败，请检查站点证书是否有效")
        } catch (e: IllegalArgumentException) {
            ApiResult.Fail("站点地址格式不正确，请以 http:// 或 https:// 开头")
        } catch (e: java.io.IOException) {
            ApiResult.Fail("网络异常，请稍后重试（${e.message ?: "连接中断"}）")
        }
    }

    /** HTTP 状态码分档文案：尽量让用户能区分「地址错」「版本旧」「服务挂了」。 */
    private fun httpMessage(code: Int): String = when {
        code == 404 -> "该地址没有健康检查接口（可能是旧版本后端，请先升级站点后端）"
        code == 401 || code == 403 -> "站点拒绝访问（HTTP $code），请检查站点安全配置"
        code == 502 || code == 503 || code == 504 -> "站点暂时不可用（HTTP $code），请确认后端服务已启动"
        code >= 500 -> "站点服务异常（HTTP $code），请稍后重试"
        code >= 400 -> "该地址不是本站点服务（HTTP $code）"
        else -> "站点返回异常（HTTP $code）"
    }
}
