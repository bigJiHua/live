package com.live.finance.core

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.live.finance.BuildConfig

/**
 * 离线 WebView 的**安装级缓存闸门**。
 *
 * 背景：卡片页/日记编辑器原先在 `loadUrl` 前无条件 `clearCache(true)`，本意是防止
 * `file:///android_asset` 的旧 index.html / card.js 被 WebView 缓存后，升级 APK 仍跑旧页。
 * 但 `clearCache(true)` 清的是**整个 App** 的 WebView 资源缓存，连 `/api/public` 的卡面、
 * 银行 logo、正文内联图一起清掉 ⇒ 每次进页面都重新下载。
 *
 * 改为「本次安装」做闸门：
 *  - 每次安装/升级 APK 后第一次打开 WebView：清一次；
 *  - 同一次安装内反复进出页面：保留 HTTP 图片缓存；
 *  - 不用 versionCode：debug 包 versionCode 经常不变，改了 assets 重装仍会跑旧页。
 *
 * ⚠ 不要改回无条件 `clearCache`，也不要开 `LOAD_NO_CACHE`（本仓库有过 file:// 资产被整页拦掉的血泪）。
 */
object WebViewAssetCache {
    private const val TAG = "WebViewAssetCache"
    private const val PREFS = "webview_asset_cache"
    private const val KEY = "cleared_install_token"

    /** 每次安装只清一次；同一次安装内直接返回，保住图片缓存。 */
    fun clearOncePerInstall(context: Context, webView: WebView) {
        val token = installToken(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY, -1L) == token) return
        webView.clearCache(true)
        // 用 commit 而不是 apply：闸门必须在这次 loadUrl 前真正落到磁盘，
        // 否则进程被回收后下次打开会再次清空 WebView 图片缓存。
        prefs.edit().putLong(KEY, token).commit()
        Log.i(TAG, "安装/升级后首次打开 WebView，清一次资源缓存 token=$token")
    }

    /** 本次安装的唯一标识：优先 lastUpdateTime（每次重装/升级都会变），失败回退 versionCode。 */
    private fun installToken(context: Context): Long = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime.takeIf { it > 0L } ?: info.versionCode.toLong()
    } catch (_: Throwable) {
        BuildConfig.VERSION_CODE.toLong()
    }
}
