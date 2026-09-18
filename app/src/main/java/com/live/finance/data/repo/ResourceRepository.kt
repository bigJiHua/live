package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Resource

/** 批量删除结果：成功 / 被业务引用（不可删，后端回 referencedIds）/ 其它失败。 */
sealed interface DeleteOutcome {
    data class Ok(val message: String) : DeleteOutcome
    data class Referenced(val ids: List<String>, val message: String) : DeleteOutcome
    data class Fail(val message: String) : DeleteOutcome
}

/**
 * 文件资源库（对齐 web `utils/api/upload.js`）：
 *  - GET  `/upload/list?busType&busId&limit&offset`
 *  - GET  `/upload/search?type&key&limit&offset`
 *  - POST `/upload/single`（multipart：file + busType + busId + remark）
 *  - POST `/upload/:id`（body `{type:"update", remark, tags}`）
 *  - POST `/upload/batch-delete`（body `{ids}`，**受 pinLockGuard 保护**，需路由令牌）
 */
interface ResourceRepository {
    suspend fun list(busType: String = "post", busId: String = "", limit: Int = 50, offset: Int = 0): ApiResult<List<Resource>>

    suspend fun search(type: String, key: String, limit: Int = 50, offset: Int = 0): ApiResult<List<Resource>>

    suspend fun update(id: String, remark: String, tags: List<String>): ApiResult<Unit>

    suspend fun batchDelete(ids: List<String>): DeleteOutcome

    /**
     * 单文件上传（web 用 /upload/multiple 一次传多个；原生按单文件循环，
     * 因 ApiClient 的 multipart 不支持重复表单键 `tags[]`）：先上传，再按需补一次 `/upload/:id` 写入标签。
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        busType: String,
        remark: String,
        tags: List<String>,
    ): ApiResult<Unit>
}

class FakeResourceRepository : ResourceRepository {
    private val seed = mutableListOf(
        Resource("a1", "post", "m1", "IMG_0001.jpg", "/uploads/post/IMG_0001.jpg", 253000, "jpg", "公园散步", listOf("日常"), "2026-09-14 18:20:00", "/uploads/temp/thumbs/post/IMG_0001.webp"),
        Resource("a2", "post", "m2", "screenshot.png", "/uploads/post/screenshot.png", 88000, "png", "记账截图", listOf("账单", "随手记"), "2026-09-12 22:10:00", "/uploads/temp/thumbs/post/screenshot.webp"),
    )

    override suspend fun list(busType: String, busId: String, limit: Int, offset: Int): ApiResult<List<Resource>> =
        ApiResult.Ok(seed.filter { busType.isEmpty() || it.busType == busType }, "")

    override suspend fun search(type: String, key: String, limit: Int, offset: Int): ApiResult<List<Resource>> =
        ApiResult.Ok(seed.filter { (type.isEmpty() || it.busType == type) && (it.remark.contains(key) || it.tags.any { t -> t.contains(key) }) }, "")

    override suspend fun update(id: String, remark: String, tags: List<String>): ApiResult<Unit> {
        val i = seed.indexOfFirst { it.id == id }
        if (i >= 0) seed[i] = seed[i].copy(remark = remark, tags = tags)
        return ApiResult.Ok(Unit, "更新成功")
    }

    override suspend fun batchDelete(ids: List<String>): DeleteOutcome {
        seed.removeAll { ids.contains(it.id) }
        return DeleteOutcome.Ok("批量删除成功")
    }

    override suspend fun upload(
        bytes: ByteArray, fileName: String, mime: String, busType: String, remark: String, tags: List<String>,
    ): ApiResult<Unit> {
        seed.add(0, Resource("u${System.currentTimeMillis()}", busType, "", fileName, "/uploads/$busType/$fileName", bytes.size.toLong(), "jpg", remark, tags, "", ""))
        return ApiResult.Ok(Unit, "上传成功")
    }
}

class RemoteResourceRepository(private val client: ApiClient) : ResourceRepository {

    override suspend fun list(busType: String, busId: String, limit: Int, offset: Int): ApiResult<List<Resource>> {
        val q = buildMap {
            put("busType", busType); if (busId.isNotEmpty()) put("busId", busId)
            put("limit", limit.toString()); put("offset", offset.toString())
        }
        return client.get("/upload/list", q).toResult().map { parseList(it) }
    }

    override suspend fun search(type: String, key: String, limit: Int, offset: Int): ApiResult<List<Resource>> {
        val q = buildMap {
            put("type", type); put("key", key)
            put("limit", limit.toString()); put("offset", offset.toString())
        }
        return client.get("/upload/search", q).toResult().map { parseList(it) }
    }

    override suspend fun update(id: String, remark: String, tags: List<String>): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("type", "update")
            addProperty("remark", remark)
            add("tags", JsonArray().apply { tags.forEach { add(it) } })
        }
        return client.post("/upload/$id", body).toResult().map { }
    }

    override suspend fun batchDelete(ids: List<String>): DeleteOutcome {
        val body = JsonObject().apply {
            add("ids", JsonArray().apply { ids.forEach { add(it) } })
        }
        val env = client.post("/upload/batch-delete", body)
        if (env.status == AppConfig.ST_OK || env.status == AppConfig.ST_ACCEPT) {
            return DeleteOutcome.Ok(env.message.ifBlank { "批量删除成功" })
        }
        // 用户取消 PIN 验证（8303）→ 静默返回，不弹错误（对齐 web「PIN 验证已取消」分支）
        if (env.code == 8303 || env.status == AppConfig.ST_UNAUTH) return DeleteOutcome.Fail("")
        // 被业务引用时后端返回 400 + referencedIds（web 读 e.response.data.referencedIds）
        val refs = (env.root?.get("referencedIds") ?: (env.data as? JsonObject)?.get("referencedIds"))
            ?.takeIf { !it.isJsonNull && it.isJsonArray }
            ?.asJsonArray?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }
            ?: emptyList()
        return if (refs.isNotEmpty()) {
            DeleteOutcome.Referenced(refs, env.message.ifBlank { "部分文件无法删除" })
        } else {
            DeleteOutcome.Fail(env.message.ifBlank { "删除失败" })
        }
    }

    override suspend fun upload(
        bytes: ByteArray, fileName: String, mime: String, busType: String, remark: String, tags: List<String>,
    ): ApiResult<Unit> {
        val form = buildMap {
            put("busType", busType)
            if (remark.isNotEmpty()) put("remark", remark)
        }
        val up = client.uploadRaw("/upload/single", "file", fileName, mime, bytes, form)
        return when (up) {
            is ApiResult.Ok -> {
                val id = up.data?.get("id")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                if (tags.isEmpty() || id.isEmpty()) {
                    ApiResult.Ok(Unit, up.message)
                } else {
                    // 标签单独补写（multipart 无法重复 tags[] 键）
                    when (val r = update(id, remark, tags)) {
                        is ApiResult.Ok -> ApiResult.Ok(Unit, "上传成功")
                        is ApiResult.Fail -> r
                        else -> ApiResult.Fail("标签写入失败")
                    }
                }
            }
            is ApiResult.Fail -> up
            else -> ApiResult.Fail("上传失败")
        }
    }

    /** list/search 的 data 是数组（兼容 data.list 包装）。 */
    private fun parseList(data: com.google.gson.JsonElement?): List<Resource> {
        val arr = when (data) {
            is JsonArray -> data
            is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
            else -> JsonArray()
        }
        return arr.map { el ->
            val o = el.asJsonObject
            Resource(
                id = str(o, "id"),
                busType = str(o, "bus_type"),
                busId = str(o, "bus_id"),
                fileName = str(o, "file_name"),
                filePath = str(o, "file_path"),
                fileSize = runCatching { o.get("file_size")?.asLong ?: 0L }.getOrDefault(0L),
                fileExt = str(o, "file_ext").trimStart('.'),
                remark = str(o, "remark"),
                tags = strList(o, "tags"),
                createTime = str(o, "create_time"),
                thumbnail = str(o, "thumbnail"),
            )
        }
    }

    private fun str(o: JsonObject, k: String): String {
        val e = o.get(k) ?: return ""
        if (e.isJsonNull || !e.isJsonPrimitive) return ""
        return e.asString
    }

    /** 后端 tags 是数组；容错：字符串则按逗号切。 */
    private fun strList(o: JsonObject, k: String): List<String> {
        val e = o.get(k) ?: return emptyList()
        if (e.isJsonNull) return emptyList()
        if (e.isJsonArray) return e.asJsonArray.mapNotNull { it.takeIf { x -> !x.isJsonNull }?.asString }.filter { it.isNotBlank() }
        if (e.isJsonPrimitive) return e.asString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return emptyList()
    }
}
