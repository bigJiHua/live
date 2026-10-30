package com.live.finance.core.net

import android.util.LruCache
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内 GET 响应缓存（**仅内存，不落盘**）。
 *
 * 目的：首页/流水/账单/卡片等页面每次重新进入都会重发同一批 GET；Web 靠 keep-alive +
 * 浏览器缓存规避，原生没有。这里在传输层补一层 TTL 缓存：
 *  - TTL 内命中直接返回上次的 [Envelope]，不发请求；
 *  - **过期（STALE）先返回旧快照，同时由 [ApiClient] 触发一次后台静默刷新（SWR，2026-10-27 批次B）**：
 *    single-flight 同 key 只允许一个在飞（[markRefreshing]），完成后 [broadcast] 广播 key
 *    （页面可选监听做原地更新），失败则 [extendGrace] 延长旧快照宽限；
 *  - 只缓存成功信封；写请求成功、登出/401/锁定、切站点时整体清空；
 *  - 单条超过 [MAX_ENTRY_BYTES] 不缓存（如统计页一次拉 1 万条），防止内存膨胀。
 *
 * ⚠ 2026-10-22 曾因此处顾虑「后台 GET 走 401/8303 回调会在用户无感知时弹登出/PIN」而拒绝后台刷新；
 *   批次B 以 [ApiClient.execute] 的 silent 通道解决（silent 下 401/8303/8304 只吞不弹），顾虑解除。
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

    /** SWR single-flight 占位集合（ApiClient.launchStaleRefresh 抢占/释放）。 */
    private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * STALE 刷新完成广播（值 = cacheKey，含站点|token|path|query）。
     * 页面**可选**收集做原地更新；不收集也无功能损失（下次进页自然拿到新快照）。
     */
    val staleBroadcast = MutableSharedFlow<String>(extraBufferCapacity = 16)

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

    /** single-flight 抢占：true=抢到（本次由我刷）；false=同 key 已有刷新在飞。 */
    fun markRefreshing(key: String): Boolean = refreshing.add(key)

    fun unmarkRefreshing(key: String) {
        refreshing.remove(key)
    }

    fun broadcast(key: String) {
        staleBroadcast.tryEmit(key)
    }

    /**
     * 刷新失败时给旧快照延长宽限：把 at 回拨使「剩余新鲜度 = graceMs」，
     * 期间再次 STALE 命中不会重新触发刷新（防后端故障时每次进页都打网络）。
     */
    @Synchronized
    fun extendGrace(key: String, graceMs: Long) {
        val e = lru.get(key) ?: return
        lru.put(key, e.copy(at = System.currentTimeMillis() - e.ttlMs + graceMs))
    }

    private fun estimateBytes(envelope: Envelope): Int {
        // root 含整个响应体（data 也在里面），优先按它估；UTF-16 每字符约 2 字节。
        val chars = (envelope.root?.toString()?.length ?: 0)
            .coerceAtLeast(envelope.data?.toString()?.length ?: 0)
        if (chars > MAX_ENTRY_BYTES / 2) return MAX_ENTRY_BYTES + 1 // 防 Int 溢出，同时拒绝超大条目
        return chars * 2 + 256
    }
}
