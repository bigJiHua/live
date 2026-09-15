package com.live.finance.data.model

/** 上传附件/资源（对应 GET /upload/list 行，表 attachment）。 */
data class Resource(
    val id: String,
    val busType: String = "",
    val busId: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val fileSize: Long = 0,
    val fileExt: String = "",
    val remark: String = "",
    val tags: String = "",
    val createTime: String = "",
)
