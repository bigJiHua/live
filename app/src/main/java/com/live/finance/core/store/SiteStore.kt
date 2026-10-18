package com.live.finance.core.store

import android.content.Context

/**
 * 站点地址持久层（登录页「站点」输入框 + 设置页「基础API设定」的后备存储）。
 *
 * 用途：分发适配——APK 打一份包，用户填入自己部署站点的两个地址：
 *  - API 地址（[current]）：后端服务地址，网络层每请求动态读；
 *  - 分享 URL（[shareUrl]）：**已部署的 Web 端**地址——APP 分享生成的是网页链接，
 *    落地页只有 web 端有；未填写时 APP 内分享功能被阻断（弹提示引导去设置）。
 *
 * ## 为什么用普通 SharedPreferences（而非 SessionStore 的加密存储）
 * 站点 URL 不是凭证：它决定「请求发到哪」，不包含任何密钥/token。
 * root 设备能改这个值，效果等同于用户自己输入另一个地址，无越权收益。
 * 因此不引入 Keystore 开销，普通 SP 即可（与 ThemeStore 一致）。
 *
 * ## 生效链路
 * `App.onCreate` 启动时读出 → 分别覆盖 `AppConfig.baseUrl` / `AppConfig.siteUrl`；
 * 登录页/设置页保存后即时同步（两者均为 @Volatile，读取方每请求/每次拼接触发）。
 */
class SiteStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 当前保存的 API 站点 URL；null = 未保存过（用构建期默认 [com.live.finance.core.AppConfig.baseUrl]）。 */
    fun current(): String? = prefs.getString(KEY_SITE, null)?.takeIf { it.isNotBlank() }

    /** 保存 API 站点 URL（调用方需先过 [isValidSite] 校验）。 */
    fun save(url: String) {
        prefs.edit().putString(KEY_SITE, url).apply()
    }

    /** 清除保存的 API 站点 URL，回退构建期默认值。 */
    fun clear() {
        prefs.edit().remove(KEY_SITE).apply()
    }

    /**
     * 分享 URL（Web 端地址）；null = 未填写 ⇒ APP 分享功能阻断（见 DiaryDetailScreen 入口校验）。
     * 与 [current] 相互独立：API 与 Web 可能不同域（如 API 在子域/反代路径）。
     */
    fun shareUrl(): String? = prefs.getString(KEY_SHARE_SITE, null)?.takeIf { it.isNotBlank() }

    /** 保存分享 URL（Web 端地址；调用方需先过 [normalize] 校验）。 */
    fun saveShareUrl(url: String) {
        prefs.edit().putString(KEY_SHARE_SITE, url).apply()
    }

    /** 清除分享 URL（分享功能随之不可用，直到重新填写）。 */
    fun clearShareUrl() {
        prefs.edit().remove(KEY_SHARE_SITE).apply()
    }

    companion object {
        private const val FILE_NAME = "finance_site"
        private const val KEY_SITE = "api_site_url"
        private const val KEY_SHARE_SITE = "share_site_url"

        /**
         * 站点地址合法性校验（登录页即存即用，非法不落盘）。
         *
         * 规则（用户 2026-10-14 定稿）：**只做最低限度校验**——
         *  1. 非空（trim 后）；
         *  2. 以 `http://` 或 `https://` 开头（http 系均可，不强制 https，
         *     适配内网/自建 http 部署；明文风险由网络安全配置与部署方自担）。
         *
         * 不做正则、不校验域名后缀/端口/路径——任意形态的 API 地址都允许输入，
         * 能否连通由请求结果说话（登录失败会给 toast）。
         *
         * @return true = 合法
         */
        fun isValidSite(raw: String): Boolean = normalize(raw) != null

        /**
         * 归一化站点地址；非法返回 null。
         *
         * 归一化规则：
         *  1. trim 空白、去掉结尾 `/`（与 `AppConfig.url()` 的 trimEnd('/') 口径一致）；
         *  2. 去掉误粘贴的接口前缀 —— 用户常把文档里的 `https://x.com/api` 或
         *     `https://x.com/api/v1` 整段复制进来。`AppConfig.url()` 会自动补 `/api/v1`，
         *     不剥掉就会拼成 `/api/v1/api/v1/...` ⇒ 全部 404。
         */
        fun normalize(raw: String): String? {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return null
            val lower = s.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
            for (suffix in listOf("/api/v1", "/api")) {
                if (s.endsWith(suffix, ignoreCase = true)) {
                    s = s.dropLast(suffix.length).trimEnd('/')
                    break
                }
            }
            return s.ifEmpty { null }
        }
    }
}
