package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Category

interface CategoryRepository {
    /** type: income/expense/asset/fixed/bank */
    suspend fun list(type: String): ApiResult<List<Category>>
    suspend fun create(name: String, type: String): ApiResult<Unit>
    suspend fun update(id: String, name: String): ApiResult<Unit>
    suspend fun delete(id: String): ApiResult<Unit>
}

class FakeCategoryRepository : CategoryRepository {
    private val seed = mutableListOf(
        Category("cat_food", "餐饮", "expense"),
        Category("cat_traffic", "交通", "expense"),
        Category("cat_shop", "购物", "expense"),
        Category("cat_home", "居家", "expense"),
        Category("cat_salary", "工资", "income"),
        Category("cat_bonus", "奖金", "income"),
        Category("cat_invest", "理财收益", "income"),
    )
    override suspend fun list(type: String): ApiResult<List<Category>> =
        ApiResult.Ok(seed.filter { it.type == type }, "")

    override suspend fun create(name: String, type: String): ApiResult<Unit> {
        seed.add(Category("cat_${System.currentTimeMillis()}", name, type))
        return ApiResult.Ok(Unit, "创建成功")
    }
    override suspend fun update(id: String, name: String): ApiResult<Unit> {
        val idx = seed.indexOfFirst { it.id == id }
        if (idx >= 0) seed[idx] = seed[idx].copy(name = name)
        return ApiResult.Ok(Unit, "更新成功")
    }
    override suspend fun delete(id: String): ApiResult<Unit> {
        seed.removeAll { it.id == id }
        return ApiResult.Ok(Unit, "删除成功")
    }
}

class RemoteCategoryRepository(private val client: ApiClient) : CategoryRepository {
    override suspend fun list(type: String): ApiResult<List<Category>> =
        client.get("/category", mapOf("type" to type)).toResult().map { data ->
            val arr: JsonArray = when (data) {
                is JsonArray -> data
                is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
                else -> JsonArray()
            }
            arr.map {
                val o = it.asJsonObject
                Category(
                    id = if (o.has("id") && !o.get("id").isJsonNull) o.get("id").asString else "",
                    name = if (o.has("name") && !o.get("name").isJsonNull) o.get("name").asString else "",
                    type = if (o.has("type") && !o.get("type").isJsonNull) o.get("type").asString else "",
                    icon = if (o.has("icon_url") && !o.get("icon_url").isJsonNull) o.get("icon_url").asString else "",
                )
            }
        }

    override suspend fun create(name: String, type: String): ApiResult<Unit> {
        val body = JsonObject().apply { addProperty("name", name); addProperty("type", type) }
        return client.post("/category", body).toResult().map { }
    }

    override suspend fun update(id: String, name: String): ApiResult<Unit> {
        val body = JsonObject().apply { addProperty("name", name) }
        return client.put("/category/$id", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/category/$id").toResult().map { }
}
