package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.Category

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

class RemoteCategoryRepository(private val client: ApiClient) : CategoryRepository {
    override suspend fun list(type: String): ApiResult<List<Category>> =
        client.get("/category", mapOf("type" to type)).toResult().map { data ->
            val arr: JsonArray = when (data) {
                is JsonArray -> data
                is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
                else -> JsonArray()
            }
            arr.map { parse(it) }
        }

    override suspend fun create(type: String, name: String, remark: String, iconUrl: String): ApiResult<Category?> {
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("type", type)
            putNullable("iconUrl", iconUrl)
            putNullable("remark", remark)
        }
        return client.post("/category", body).toResult().map { parse(it) }
    }

    override suspend fun update(id: String, name: String, remark: String, iconUrl: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("name", name)
            putNullable("iconUrl", iconUrl)
            putNullable("remark", remark)
        }
        return client.put("/category/$id", body).toResult().map { }
    }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.delete("/category/$id").toResult().map { }

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
