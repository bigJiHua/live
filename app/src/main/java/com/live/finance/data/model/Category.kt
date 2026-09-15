package com.live.finance.data.model

/** 分类（对应 GET /category?type=expense/income/asset/fixed/bank 行）。 */
data class Category(
    val id: String,
    val name: String,
    val type: String = "",
    val icon: String = "",
)
