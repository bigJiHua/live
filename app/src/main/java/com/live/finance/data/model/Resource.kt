package com.live.finance.data.model

/**
 * 上传附件/资源（对应 `GET /upload/list` 行，表 sys_attachment）。
 *
 * ⚠ 后端 list/search 会把 DB 里的 tags JSON 字符串**解析成数组**再返回（见 upload 控制器 list），
 * 故 [tags] 是 `List<String>`；上传接口（/upload/single）返回的 tags 才是原始字符串。
 */
data class Resource(
    val id: String,
    val busType: String = "",
    val busId: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val fileSize: Long = 0,
    val fileExt: String = "",
    val remark: String = "",
    val tags: List<String> = emptyList(),
    val createTime: String = "",
    /** 缩略图（图片为 webp 缩略图；非图片=原图）。 */
    val thumbnail: String = "",
) {
    /** 列表展示用（web `getThumbUrl` = `thumbnail || file_path`）。 */
    val thumbPath: String get() = thumbnail.ifBlank { filePath }

    /** 大图预览用（web preview 数组用 `url || file_path`，两者后端都回 file_path）。 */
    val previewPath: String get() = filePath.ifBlank { thumbnail }
}
