package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Resource

interface ResourceRepository {
    /** busType 为空 → 全部（走 /upload/search 需 type，故默认列 post 图片）。 */
    suspend fun list(busType: String = "post", busId: String = "", limit: Int = 50, offset: Int = 0): ApiResult<List<Resource>>
}

class FakeResourceRepository : ResourceRepository {
    override suspend fun list(busType: String, busId: String, limit: Int, offset: Int): ApiResult<List<Resource>> =
        ApiResult.Ok(
            listOf(
                Resource("a1", "post", "m1", "IMG_0001.jpg", "/uploads/2026/09/IMG_0001.jpg", 253000, "jpg", "公园散步", "[\"日常\"]", "2026-09-14 18:20:00"),
                Resource("a2", "post", "m2", "screenshot.png", "/uploads/2026/09/screenshot.png", 88000, "png", "记账截图", "[\"账单\"]", "2026-09-12 22:10:00"),
            ), ""
        )
}

class RemoteResourceRepository(private val client: ApiClient) : ResourceRepository {
    override suspend fun list(busType: String, busId: String, limit: Int, offset: Int): ApiResult<List<Resource>> {
        val q = buildMap {
            put("busType", busType); if (busId.isNotEmpty()) put("busId", busId)
            put("limit", limit.toString()); put("offset", offset.toString())
        }
        return client.get("/upload/list", q).toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Resource(
                    id = str(o, "id"), busType = str(o, "bus_type"), busId = str(o, "bus_id"),
                    fileName = str(o, "file_name"), filePath = str(o, "file_path"),
                    fileSize = runCatching { o.get("file_size").asLong }.getOrDefault(0L),
                    fileExt = str(o, "file_ext"), remark = str(o, "remark"), tags = str(o, "tags"),
                    createTime = str(o, "create_time"),
                ) }
        }
    }
    private fun str(o: JsonObject, k: String): String {
        val e = o.get(k) ?: return ""
        if (e.isJsonNull) return ""
        if (e.isJsonPrimitive) return e.asString
        return if (e.isJsonObject) {
            val ob = e.asJsonObject
            (ob.get("name") ?: ob.get("text"))?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        } else ""
    }
}
