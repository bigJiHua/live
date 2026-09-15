package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Moment

interface MomentRepository {
    suspend fun list(limit: Int = 20): ApiResult<List<Moment>>
    /** 发布动态：POST /moment {content, mood, images:[url]}。 */
    suspend fun create(content: String, mood: String, images: List<String> = emptyList()): ApiResult<Unit>
}

class FakeMomentRepository : MomentRepository {
    private val seed = mutableListOf(
        Moment("m1", "今天天气不错，去公园走了走。", mood = "😊", createTime = "2026-09-14 18:20:00"),
        Moment("m2", "把年度预算重新梳理了一遍，感觉清晰多了。", mood = "🙂", createTime = "2026-09-12 22:10:00"),
    )
    override suspend fun list(limit: Int): ApiResult<List<Moment>> = ApiResult.Ok(seed, "")
    override suspend fun create(content: String, mood: String, images: List<String>): ApiResult<Unit> {
        seed.add(0, Moment("m${System.currentTimeMillis()}", content, imgUrl = images.firstOrNull() ?: "", mood = mood,
            createTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())))
        return ApiResult.Ok(Unit, "已发布")
    }
}

class RemoteMomentRepository(private val client: ApiClient) : MomentRepository {
    override suspend fun list(limit: Int): ApiResult<List<Moment>> =
        client.get("/moment/list", mapOf("page" to "1", "pageSize" to limit.toString())).toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .map { el -> val o = el.asJsonObject; Moment(
                    id = text(o, "id"), content = text(o, "content"), imgUrl = firstUrl(o.get("img_url")),
                    mood = text(o, "mood"), location = text(o, "location"), createTime = text(o, "create_time"),
                ) }
        }

    override suspend fun create(content: String, mood: String, images: List<String>): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("content", content)
            addProperty("mood", mood)
            add("images", JsonArray().apply { images.forEach { add(it) } })
        }
        return client.post("/moment", body).toResult().map { }
    }
    // 容错取文本：原始类型直接取；对象尝试 name/text/content；数组取不到则空，绝不抛。
    private fun text(o: JsonObject, k: String): String {
        val e = o.get(k) ?: return ""
        return when {
            e.isJsonNull -> ""
            e.isJsonPrimitive -> e.asString
            e.isJsonObject -> {
                val ob = e.asJsonObject
                (ob.get("name") ?: ob.get("text") ?: ob.get("content"))?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString ?: ""
            }
            else -> ""
        }
    }

    // img_url 可能是数组/对象/字符串，取第一个可访问路径。
    private fun firstUrl(e: com.google.gson.JsonElement?): String {
        if (e == null || e.isJsonNull) return ""
        return when {
            e.isJsonPrimitive -> e.asString
            e.isJsonArray -> { val a = e.asJsonArray; if (a.size() > 0) text0(a[0]) else "" }
            e.isJsonObject -> text(e.asJsonObject, "url").ifEmpty { text(e.asJsonObject, "path") }
            else -> ""
        }
    }
    private fun text0(e: com.google.gson.JsonElement?): String =
        if (e != null && e.isJsonPrimitive) e.asString else ""
}
