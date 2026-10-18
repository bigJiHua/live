package com.live.finance.ui.bankcard

/**
 * 卡片页离线 WebView 的链路打点出口（`[链路N]` / `[注入回执]` / `[探针]` / `[console]`）。
 *
 * 只写 Logcat。原先还往一份内存 ring buffer 里塞同样的行、由屏幕右上角的悬浮助手显示，
 * 2026-10-11 页面定案后按负责人要求撤掉（界面上不留调试痕迹）；组件留在
 * `temp/备份/CardDebugOverlay.kt`，要恢复就照它把缓冲和挂载点加回来。
 *
 * 无状态，任意线程可调。
 */
object CardDebugLog {
    fun i(msg: String) = android.util.Log.i(TAG, msg)
    fun w(msg: String) = android.util.Log.w(TAG, msg)
    fun e(msg: String) = android.util.Log.e(TAG, msg)
}
