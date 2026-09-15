package com.live.finance.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 后端 `create_time/update_time/repay_time/login_time` 等存的是**毫秒时间戳字符串**（`String(Date.now())`），
 * 不是 "yyyy-MM-dd HH:mm:ss"。这里统一按 epoch-ms 解析；传入非纯数字则退回原样截断（兼容假数据/已是字符串日期）。
 */
object TimeFmt {
    fun hm(ts: String): String {
        val ms = ts.toLongOrNull() ?: return ts.substringAfter(' ').take(5)
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))
    }

    fun dateTime(ts: String): String {
        val ms = ts.toLongOrNull() ?: return ts.take(16)
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
    }

    fun date(ts: String): String {
        val ms = ts.toLongOrNull() ?: return ts.take(10)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    }
}
