package com.live.finance.data.model

/**
 * 分类（对应 `GET /category?type=` 行，表 bus_category）。
 *
 * ⚠ 字段名两端不一致：**请求体**用 `iconUrl`（后端 Joi 只认这个名）。
 *  **响应**用 `icon_url`（DB 列名直出）—— 别混，混了就静默丢图标。
 */
data class Category(
    val id: String,
    val name: String,
    val type: String = "",
    /** 响应字段 `icon_url`。 */
    val iconUrl: String = "",
    /** 说明（列表卡片第二行）。 */
    val remark: String = "",
)
