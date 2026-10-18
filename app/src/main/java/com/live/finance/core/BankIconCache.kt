package com.live.finance.core

import android.content.Context
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 银行 logo 的**内存缓存键策略**。
 *
 * ## 为什么需要单独一套键（2026-10-11 审计）
 *
 * Coil 2 的 `memoryCacheKey` 默认由 `data + size + precision + scale + transformations` 拼成，
 * 其中 `size` 由 `SubcomposeAsyncImage` 按**布局约束**解析。于是同一张 logo：
 *  - 在 `BankIconView` 里出现 12/14/16/18/20/22/24/28/36/40/48dp 共 10+ 种尺寸
 *    ⇒ **10+ 个互不相同的缓存键**，每个都要单独解码、单独占内存。
 *  - 而此前的预热请求**没给 size** ⇒ 解析成 `Size.ORIGINAL` ⇒ 它的键和上面**任何一个都不相等**，
 *    预热写进去的条目是一份**永远无人访问的孤儿**，对界面零贡献。
 *
 * ## 本策略
 *
 * bank logo 是 **SVG 矢量图**，Coil 的 `SvgDecoder` 产出的是 `Svg` 对象而不是位图 ——
 * 同一份 `Svg` 可以被渲染成**任意尺寸且不失真**。所以对 SVG 直接
 * **`memoryCacheKey = url`（不含 size）**：
 *  - 所有尺寸共享**同一个**缓存条目（解码只发生一次）；
 *  - 预热请求与任意尺寸的 UI 请求**键完全相同** ⇒ 预热必然被命中，这正是预热的意义。
 *
 * 非 SVG（webp/png 等位图）**不设固定键**：位图复用错尺寸会糊，
 * 让它继续走 Coil 默认的按 `size` 分键。
 */
object BankIconCache {
    /** URL 是否指向矢量图。 */
    fun isVector(url: String): Boolean =
        url.substringBefore('?').trim().endsWith(".svg", ignoreCase = true)

    /**
     * 矢量图 ⇒ 返回一个**与尺寸无关**的固定键；位图 ⇒ 返回 `null`（用 Coil 默认分键）。
     *
     * 预热与 UI 取图都必须走这个函数，否则两边键对不上、预热永远白做。
     */
    fun memoryCacheKeyOf(url: String): String? =
        if (isVector(url)) url else null

    /** 给请求套上本策略的键（`null` 时不干预）。 */
    fun applyMemoryKey(builder: ImageRequest.Builder, url: String): ImageRequest.Builder {
        val key = memoryCacheKeyOf(url) ?: return builder
        return builder.memoryCacheKey(key).diskCacheKey(url)
    }
}

/**
 * 银行 logo 预热器。
 *
 * ## 与上一版的差别（上一版为什么「几乎无效果」）
 *
 * | 问题 | 上一版 | 本版 |
 * |------|--------|------|
 * | 缓存键 | 不带 size ⇒ `Size.ORIGINAL` ⇒ 键与所有 UI 请求都不相等 | 走 [BankIconCache.applyMemoryKey]，与 UI **同一把键** |
 * | 失败处理 | `warmedBankIcons.add(url)` 先记集合再 `enqueue`，**失败也永久标记**、本进程不再重试 | `onSuccess` 才记入 `succeeded`；`onError`/`onCancel` 释放 `inflight` ⇒ **可重试** |
 * | 提前量 | 只在 `list("bank")` 里触发，而 18 个调用点都是「界面正要画 logo 的那一刻」⇒ 预热与 UI **并发**发出，反而可能下载两次 | 由 [com.live.finance.di.AppGraph] 在 `sessionReady` 之后的后台协程里触发 ⇒ **真正早于**任何界面 |
 *
 * 用法：`warm(urls)` 可重复调用，已成功的不再请求；同一次运行内并发去重。
 */
class BankIconWarmer(private val context: Context) {

    private companion object {
        const val TAG = "BankIconCache"
    }

    /** 预热自己的作用域：`execute` 是挂起调用，得有地方跑；失败不影响后续批次。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val loader: ImageLoader by lazy { Coil.imageLoader(context) }

    /** 已成功落缓存的 URL（进程内不再重复请求）。 */
    private val succeeded: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** 在途 URL，防止并发重复发起。 */
    private val inflight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    var successCount: Int = 0
        private set

    @Volatile
    var failCount: Int = 0
        private set

    /** 预热一批 icon 路径（相对路径或完整 URL 皆可，空值自动忽略）。可重复调用。 */
    fun warm(urls: List<String>) {
        urls.asSequence()
            .filter { !it.isNullOrBlank() }
            .map { AppConfig.fullFileUrl(it.trim()).trim() }
            .filter { it.isNotBlank() }
            .forEach { enqueue(it) }
    }

    /** 预热一批 [com.live.finance.data.model.Category] 的 icon_url。 */
    fun warmCategories(categories: List<com.live.finance.data.model.Category>) {
        warm(categories.map { it.iconUrl })
    }

    private fun enqueue(url: String) {
        if (succeeded.contains(url)) return
        if (!inflight.add(url)) return // 同一次运行内已有在途请求

        val request = BankIconCache.applyMemoryKey(ImageRequest.Builder(context).data(url), url).build()
        // ⚠ Coil 2 的 `ImageLoader.enqueue` 只有单参重载，拿不到结果；要「成功才算数」必须用 suspend 的
        //   `execute`（返回 ImageResult）。这也是本版与上一版的关键差别：上一版 add() 先于发请求、
        //   无从得知成败 ⇒ 失败的 URL 被永久标记「已预热」。
        scope.launch {
            // ⚠ 整体包try-catch：`BankIconWarmer` 的 scope 同样没有
            //   `CoroutineExceptionHandler`，未捕获异常在 Android 上会**直接崩 App**。
            //   而且这里被 18 处界面 + 启动预热共用，一个坏 URL 绝不能连累全局。
            //   连`CancellationException` 一并吞掉（协程取消不该当异常上抛）。
            val ok = try {
                loader.execute(request) is SuccessResult
            } catch (t: Throwable) {
                Log.w(TAG, "预热执行异常: $url (${t.message})")
                false
            }
            inflight.remove(url)
            if (ok) {
                succeeded.add(url) // 只有真的落缓存了才标记「已预热」
                successCount++
                Log.d(TAG, "预热成功: $url")
            } else {
                // 不写 succeeded ⇒ 下次界面触发 / 下次启动时还能重试（断网、局域网友通都是暂时的）
                failCount++
            }
        }
    }
}