package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Moment
import com.live.finance.data.model.MomentImage

/** 分页结果（后端 `data = {list, total, page, pageSize, totalPages}`）。 */
data class MomentPage(val list: List<Moment>, val total: Int, val totalPages: Int)

/** 分享结果。⚠ 后端把 `share` 放在**响应顶层**（不在 data 内），故原生读 `Envelope.root`。 */
data class MomentShare(val token: String = "", val password: String = "")

/**
 * 动态/日记（对齐 web `utils/api/moment.js` + 后端 `modules/moment`）：
 *  - `GET    /moment/list?page&pageSize` → `data{list,total,totalPages}`（content 已去标签+截断 20 字）
 *  - `GET    /moment/:id`               → `data` 单条（content 是完整 HTML，含 `children[]` 追文 id）
 *  - `POST   /moment/batch {ids}`       → `data` 数组（追文详情，**按 create_time 升序**）
 *  - `POST   /moment`                   → 发布（**今日第一条自动成为今日主贴**，后续自动挂到它下面）
 *  - `PUT    /moment/:id {shareAction}` → 分享开关/刷新 token（回包 `share` 在**顶层**）
 *  - `DELETE /moment/:id`               → 软删除（父帖删除时后端会把第一个子帖升级为父）
 */
interface MomentRepository {
    suspend fun list(page: Int = 1, pageSize: Int = 10): ApiResult<MomentPage>

    suspend fun getOne(id: String): ApiResult<Moment?>

    suspend fun batchDetail(ids: List<String>): ApiResult<List<Moment>>

    /** 发布：`images` 传**相对路径**（后端包成 `[{url}]`），`locationName` 空则不传位置。 */
    suspend fun create(
        content: String,
        images: List<String> = emptyList(),
        mood: String = "",
        locationName: String = "",
    ): ApiResult<Unit>

    /** [action]：`open`（回 token+password）/ `close` / `token`（仅刷新 token，密码不变）。 */
    suspend fun share(id: String, action: String, duration: Int = 1): ApiResult<MomentShare?>

    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeMomentRepository : MomentRepository {

    private fun ts(minutesAgo: Long): String = (System.currentTimeMillis() - minutesAgo * 60_000L).toString()

    private val seed = mutableListOf(
        Moment(
            id = "m1",
            content = "今天天气不错，去公园走了走。",
            images = listOf(MomentImage("https://fastly.jsdelivr.net/npm/@vant/assets/apple-1.jpeg")),
            mood = "开心",
            locationName = "杭州市西湖区",
            createTime = ts(30),
            childrenIds = listOf("m1c1"),
        ),
        Moment(
            id = "m1c1",
            content = "补一张傍晚的照片。",
            images = listOf(MomentImage("https://fastly.jsdelivr.net/npm/@vant/assets/apple-2.jpeg")),
            createTime = ts(10),
        ),
        Moment(
            id = "m2",
            content = "把年度预算重新梳理了一遍，感觉清晰多了。",
            mood = "平静",
            createTime = ts(60 * 26),
        ),
    )

    override suspend fun list(page: Int, pageSize: Int): ApiResult<MomentPage> {
        val parents = seed.filter { it.id.length <= 2 || !it.id.contains("c") }
        val from = ((page - 1) * pageSize).coerceAtLeast(0)
        val slice = parents.drop(from).take(pageSize)
        return ApiResult.Ok(MomentPage(slice, parents.size, (parents.size + pageSize - 1) / pageSize), "")
    }

    override suspend fun getOne(id: String): ApiResult<Moment?> =
        ApiResult.Ok(seed.firstOrNull { it.id == id }, "")

    override suspend fun batchDetail(ids: List<String>): ApiResult<List<Moment>> =
        ApiResult.Ok(seed.filter { ids.contains(it.id) }.sortedBy { it.createTime }, "")

    override suspend fun create(content: String, images: List<String>, mood: String, locationName: String): ApiResult<Unit> {
        seed.add(
            0,
            Moment(
                id = "m${System.currentTimeMillis()}",
                content = content,
                images = images.map { MomentImage(it) },
                mood = mood,
                locationName = locationName,
                createTime = System.currentTimeMillis().toString(),
            ),
        )
        return ApiResult.Ok(Unit, "发布成功")
    }

    override suspend fun share(id: String, action: String, duration: Int): ApiResult<MomentShare?> {
        val i = seed.indexOfFirst { it.id == id }
        if (i < 0) return ApiResult.Fail("动态不存在")
        return when (action) {
            "close" -> {
                seed[i] = seed[i].copy(vt = 0, pw = "")
                ApiResult.Ok(null, "分享已关闭")
            }
            else -> {
                val pw = seed[i].pw.ifBlank { (100000..999999).random().toString() }
                val token = "fake_token_${System.currentTimeMillis()}"
                seed[i] = seed[i].copy(vt = 1, pw = pw)
                ApiResult.Ok(MomentShare(token, pw), if (action == "open") "分享已开启" else "Token 已生成")
            }
        }
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "删除成功")
    }
}

class RemoteMomentRepository(private val client: ApiClient) : MomentRepository {

    override suspend fun list(page: Int, pageSize: Int): ApiResult<MomentPage> =
        client.get("/moment/list", mapOf("page" to page.toString(), "pageSize" to pageSize.toString()))
            .toResult().map { data ->
                val o = data as? JsonObject
                val arr = o?.getAsJsonArray("list") ?: (data as? JsonArray) ?: JsonArray()
                MomentPage(
                    list = arr.map { parseMoment(it) },
                    total = int(o, "total"),
                    totalPages = int(o, "totalPages").let {
                        if (it > 0) it else {
                            val t = int(o, "total")
                            val ps = int(o, "pageSize").let { p -> if (p > 0) p else pageSize }
                            if (t > 0) (t + ps - 1) / ps else 0
                        }
                    },
                )
            }

    override suspend fun getOne(id: String): ApiResult<Moment?> =
        client.get("/moment/$id").toResult().map { data -> (data as? JsonObject)?.let { parseMoment(it) } }

    override suspend fun batchDetail(ids: List<String>): ApiResult<List<Moment>> {
        val body = JsonObject().apply { add("ids", JsonArray().apply { ids.forEach { add(it) } }) }
        return client.post("/moment/batch", body).toResult().map { data ->
            val arr = when (data) {
                is JsonArray -> data
                is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
                else -> JsonArray()
            }
            arr.map { parseMoment(it) }.sortedBy { it.createTime }
        }
    }

    override suspend fun create(
        content: String,
        images: List<String>,
        mood: String,
        locationName: String,
    ): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("content", content) // 快速模式纯文本；精准模式是 HTML（原生降级为纯文本）
            add("images", JsonArray().apply { images.forEach { add(JsonObject().apply { addProperty("url", it) }) } })
            if (mood.isNotBlank()) addProperty("mood", mood)
            if (locationName.isNotBlank()) {
                add("location", JsonObject().apply { addProperty("name", locationName) })
            }
            add("visibleType", JsonObject().apply { addProperty("vt", 0); addProperty("vs", 0); addProperty("pw", 0) })
        }
        return client.post("/moment", body).toResult().map { }
    }

    override suspend fun share(id: String, action: String, duration: Int): ApiResult<MomentShare?> {
        val body = JsonObject().apply {
            addProperty("shareAction", action)
            addProperty("shareDuration", duration)
        }
        val env = client.put("/moment/$id", body)
        if (env.status != 200) return ApiResult.Fail(env.message.ifBlank { "分享操作失败" }, env.status)
        // `share` 在响应顶层（controller 直接 res.json({share})），不在 data 内
        val share = env.root?.getAsJsonObject("share")
        return ApiResult.Ok(
            share?.let {
                MomentShare(
                    token = str(it, "token"),
                    password = str(it, "password"),
                )
            },
            env.message,
        )
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/moment/$id").toResult().map { }

    // ───────────────────────── 解析 ─────────────────────────

    /** 单行 → 模型（`img_url`/`mood`/`location`/`visible_type` 后端已 JSON.parse 成对象/数组）。 */
    private fun parseMoment(el: JsonElement): Moment {
        val o = el.asJsonObject
        return Moment(
            id = str(o, "id"),
            content = str(o, "content"),
            images = parseImages(o.get("img_url")),
            mood = when (val m = o.get("mood")) {
                null -> ""
                else -> if (m.isJsonNull) "" else if (m.isJsonPrimitive) m.asString else str(m.asJsonObject, "name")
            },
            locationName = when (val l = o.get("location")) {
                null -> ""
                else -> when {
                    l.isJsonNull -> ""
                    l.isJsonPrimitive -> l.asString
                    l.isJsonObject -> str(l.asJsonObject, "name")
                    else -> ""
                }
            },
            createTime = str(o, "create_time"),
            childrenIds = when (val c = o.get("children")) {
                null -> emptyList()
                else -> when {
                    c.isJsonNull -> emptyList()
                    c.isJsonArray -> c.asJsonArray.mapNotNull { e -> if (e.isJsonPrimitive) e.asString else null }
                    c.isJsonPrimitive -> c.asString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    else -> emptyList()
                }
            },
            vt = int(o.get("visible_type")?.takeIf { it.isJsonObject }?.asJsonObject, "vt"),
            vs = int(o.get("visible_type")?.takeIf { it.isJsonObject }?.asJsonObject, "vs"),
            pw = str(o.get("visible_type")?.takeIf { it.isJsonObject }?.asJsonObject, "pw"),
        )
    }

    /** `img_url` → 图片数组（元素可能是 `{url,thumbnail}` 或纯字符串）。 */
    private fun parseImages(e: JsonElement?): List<MomentImage> {
        if (e == null || e.isJsonNull) return emptyList()
        if (!e.isJsonArray) return emptyList()
        return e.asJsonArray.mapNotNull { item ->
            when {
                item.isJsonPrimitive -> MomentImage(item.asString)
                item.isJsonObject -> {
                    val url = str(item.asJsonObject, "url").ifBlank { str(item.asJsonObject, "file_path") }
                    if (url.isEmpty()) null else MomentImage(url, str(item.asJsonObject, "thumbnail"))
                }
                else -> null
            }
        }
    }

    private fun str(o: JsonObject?, k: String): String {
        val e = o?.get(k) ?: return ""
        if (e.isJsonNull || !e.isJsonPrimitive) return ""
        return e.asString
    }

    private fun int(o: JsonObject?, k: String): Int {
        val e = o?.get(k) ?: return 0
        if (e.isJsonNull || !e.isJsonPrimitive) return 0
        return runCatching { e.asInt }.getOrDefault(0)
    }
}
