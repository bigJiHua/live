package com.live.finance.core.net

import android.util.LruCache

/**
 * 进程内 GET 响应缓存（**仅内存，不落盘**）。
 *
 * 目的：首页/流水/账单/卡片等页面每次重新进入都会重发同一批 GET；Web 靠 keep-alive +
 * 浏览器缓存规避，原生没有。这里在传输层补一层 TTL 缓存：
 *  - TTL 内命中直接返回上次的 [Envelope]，不发请求；
 *  - 只缓存成功信封；写请求成功、登出/401/锁定、切站点时整体清空；
 *  - 单条超过 [MAX_ENTRY_BYTES] 不缓存（如统计页一次拉 1 万条），防止内存膨胀。
 *
 * ⚠ 之所以不做后台静默刷新：后台 GET 仍会走 ApiClient 的 401/8303 回调，
 *   可能在用户无感知时触发登出或 PIN 流程。过期就同步重拉，语义最简单。
 */
object ApiGetCache {
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024

    data class Entry(
        val envelope: Envelope,
        val at: Long,
        val ttlMs: Long,
        val bytes: Int,
    )

    private val lru = object : LruCache<String, Entry>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Entry): Int = value.bytes.coerceAtLeast(1)
    }

    fun get(key: String): Entry? = lru.get(key)

    fun put(key: String, envelope: Envelope, ttlMs: Long) {
        // 空响应不进缓存：避免一次失败/空数据被当成 5 分钟快照，页面一直空白。
        if (envelope.data == null) return
        val bytes = estimateBytes(envelope)
        if (bytes > MAX_ENTRY_BYTES) return
        lru.put(key, Entry(envelope, System.currentTimeMillis(), ttlMs, bytes))
    }

    fun clear() {
        lru.evictAll()
    }

    /** 仅用于调试日志：当前占用字节数。 */
    fun sizeBytes(): Int = lru.size()

    private fun estimateBytes(envelope: Envelope): Int {
        // root 含整个响应体（data 也在里面），优先按它估；UTF-16 每字符约 2 字节。
        val chars = (envelope.root?.toString()?.length ?: 0)
            .coerceAtLeast(envelope.data?.toString()?.length ?: 0)
        if (chars > MAX_ENTRY_BYTES / 2) return MAX_ENTRY_BYTES + 1 // 防 Int 溢出，同时拒绝超大条目
        return chars * 2 + 256
    }
}
