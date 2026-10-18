package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.core.BankIconWarmer
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Category
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 分类（对齐 web `utils/api/category.js` + 后端 `modules/category`）：
 *  - `GET    /category?type=income|expense|asset|fixed|bank` → data 数组（id/name/type/icon_url/remark/sort）
 *  - `POST   /category`     body `{name, type, iconUrl, remark}`（Joi 认 `iconUrl`，允许 null/""）
 *  - `PUT    /category/:id` body `{name, iconUrl, remark}`
 *  - `DELETE /category/:id` **受 pinLockGuard 保护**（删除要走 PIN，8303 → ApiResult.NeedPin）
 *
 * 另注：`type=bank` 与其余四种走同一个控制器；且该分支会**幂等初始化默认银行**、非 bank 分支会幂等初始化默认分类。
 */
interface CategoryRepository {
    suspend fun list(type: String): ApiResult<List<Category>>

    /**
     * 新建（回传后端创建的整行 —— 对齐 web `const newItem = await apiCreateCategory(); push(newItem)`）。
     * [remark] / [iconUrl] 为空时按 web 语义提交 `null`（`formData.xxx.trim() || null`）。
     */
    suspend fun create(type: String, name: String, remark: String, iconUrl: String): ApiResult<Category?>

    suspend fun update(id: String, name: String, remark: String, iconUrl: String): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeCategoryRepository : CategoryRepository {
    private val seed = mutableListOf(
        Category("cat_food", "餐饮", "expense", remark = "早餐/午餐/外卖"),
        Category("cat_traffic", "交通", "expense", remark = "地铁/打车/加油"),
        Category("cat_shop", "购物", "expense"),
        Category("cat_home", "居家", "expense", remark = "水电/物业"),
        Category("cat_salary", "工资", "income", remark = "每月固定"),
        Category("cat_bonus", "奖金", "income"),
        Category("cat_invest", "理财收益", "income"),
        Category("cat_cash", "现金资产", "asset"),
        Category("cat_car", "家用车", "fixed"),
        Category("bank_icbc", "工商银行", "bank"),
        Category("bank_cmb", "招商银行", "bank"),
    )

    override suspend fun list(type: String): ApiResult<List<Category>> =
        ApiResult.Ok(seed.filter { it.type == type }, "")

    override suspend fun create(type: String, name: String, remark: String, iconUrl: String): ApiResult<Category?> {
        val row = Category("cat_${System.currentTimeMillis()}", name, type, iconUrl, remark)
        seed.add(row)
        return ApiResult.Ok(row, "创建成功")
    }

    override suspend fun update(id: String, name: String, remark: String, iconUrl: String): ApiResult<Unit> {
        val idx = seed.indexOfFirst { it.id == id }
        if (idx >= 0) seed[idx] = seed[idx].copy(name = name, remark = remark, iconUrl = iconUrl)
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "删除成功")
    }
}

class RemoteCategoryRepository(
    private val client: ApiClient,
    private val bankIconWarmer: BankIconWarmer,
) : CategoryRepository {

    /**
     * 进程内缓存 + 并发合并（对齐 web「切页面别重复打同一个接口」的口径）。
     *
     * ## 为什么要做（2026-10-11 审计）
     *
     * `list()` 有 **18 个调用点**（流水/账单/账户/报表…全都拉 `type=bank` 这一份字典），
     * 而 `ApiClient` 是**零缓存、零并发合并**的（`http.newCall(...).execute()` 每次都真的发），
     * 于是 N 个界面 = N 个一模一样的 `GET /category?type=bank`。
     * web 侧靠 `<keep-alive>` 让页面实例常驻、`onMounted` 只跑一次来规避；原生没有 keep-alive，
     * 所以在仓储层补一份进程内缓存。
     *
     * ## 为什么用 Mutex 而不是 single-flight 的 `Deferred`
     *
     * `Deferred` 方案里「发起者」协程被取消（用户切走页面）会把结果一起带崩，连带坑掉所有
     * 等在它上面的调用方。`Mutex.withLock` 则是「后到的协程排队 → 第 1 个拉完写缓存 → 后面直接从缓存读」，
     * 取消只影响自己，且天然达到合并效果。
     */
    private data class CacheEntry(val data: List<Category>, val at: Long)

    private companion object {
        /** 字典是低频参照数据，60s 足够；与 web `REFRESH_INTERVAL=10s` 同量级且更保守。 */
        const val TTL_MS = 60_000L
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun list(type: String): ApiResult<List<Category>> {
        // 快路径：命中新鲜缓存直接返回，连锁都不进
        fresh(type)?.let { return ApiResult.Ok(it, "") }

        val lock = locks.getOrPut(type) { Mutex() }
        return lock.withLock {
            // 慢路径：排队期间前一个已把缓存写好了 ⇒ 这里不再发请求（这就是并发合并）
            fresh(type)?.let { return@withLock ApiResult.Ok(it, "") }
            fetchAndCache(type)
        }
    }

    private fun fresh(type: String): List<Category>? {
        val hit = cache[type] ?: return null
        return if (System.currentTimeMillis() - hit.at < TTL_MS) hit.data else null
    }

    private suspend fun fetchAndCache(type: String): ApiResult<List<Category>> {
        // ⚠ 整体包try-catch：本方法有 18 个调用点，且**全都是 `LaunchedEffect` 里的协程**，
        //   异常一旦穿透就会崩 App（协程异常走全局处理器）。且这里有 Mutex 排队，
        //   一个请求抛异常会让所有等待方一起失败。所以**只允许返回 ApiResult，不允许抛**。
        //   `ApiClient` 自己只捕 IOException，JSON 解析等异常会到这里。
        val result = try {
            client.get("/category", mapOf("type" to type)).toResult().map { data ->
                val arr: JsonArray = when (data) {
                    is JsonArray -> data
                    is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
                    else -> JsonArray()
                }
                arr.map { parse(it) }
            }
        } catch (t: Throwable) {
            return ApiResult.Fail(t.message ?: "分类加载失败")
        }
        if (result is ApiResult.Ok) {
            result.data?.let { cache[type] = CacheEntry(it, System.currentTimeMillis()) }
            // 银行 logo 预热：`type=bank` 时把全部 logo 灌进 Coil 缓存。
            // 注意这只是**兜底**——真正的提前量由 `AppGraph` 在 `sessionReady` 之后的后台协程里做。
            // 实现在 BankIconWarmer/BankIconCache：SVG 走与尺寸无关的固定 memoryCacheKey，
            // 与 BankIconView 取图用**同一把键**，所以这里再触发时基本都是内存命中、几乎零成本。
            if (type == "bank") result.data?.let { bankIconWarmer.warmCategories(it) }
        }
        return result
    }

    /** 写操作后让缓存失效，避免用户刚改完的名字/图标还要等 TTL 才生效。 */
    private fun invalidate(type: String) {
        cache.remove(type)
    }

    override suspend fun create(type: String, name: String, remark: String, iconUrl: String): ApiResult<Category?> {
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("type", type)
            putNullable("iconUrl", iconUrl)
            putNullable("remark", remark)
        }
        val res = client.post("/category", body).toResult().map { parse(it) }
        if (res is ApiResult.Ok) invalidate(type)
        return res
    }

    override suspend fun update(id: String, name: String, remark: String, iconUrl: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("name", name)
            putNullable("iconUrl", iconUrl)
            putNullable("remark", remark)
        }
        val res = client.put("/category/$id", body).toResult().map { }
        // update/delete 只有 id、拿不到 type ⇒ 清全部（分类数量很少，代价可忽略）
        if (res is ApiResult.Ok) cache.clear()
        return res
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        val res = client.delete("/category/$id").toResult().map { }
        if (res is ApiResult.Ok) cache.clear()
        return res
    }

    /** 响应行 → 模型（`icon_url` 优先，兼容 `iconUrl`；缺字段一律空串，不抛）。 */
    private fun parse(el: JsonElement?): Category {
        if (el == null || !el.isJsonObject) return Category("", "")
        val o = el.asJsonObject
        return Category(
            id = str(o, "id"),
            name = str(o, "name"),
            type = str(o, "type"),
            iconUrl = str(o, "icon_url").ifBlank { str(o, "iconUrl") },
            remark = str(o, "remark"),
        )
    }

    private fun str(o: JsonObject, k: String): String {
        val e = o.get(k) ?: return ""
        if (e.isJsonNull || !e.isJsonPrimitive) return ""
        return e.asString
    }

    /** 空串按 web 语义发 null（后端 Joi `.allow(null, "")`）。 */
    private fun JsonObject.putNullable(key: String, value: String) {
        if (value.isBlank()) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }
}
