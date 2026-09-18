package com.live.finance.data.model

/** 动态图片（后端 `img_url` 数组元素 `{url, thumbnail}`；兼容纯字符串元素）。 */
data class MomentImage(val url: String = "", val thumbnail: String = "") {
    /** 列表缩略图（web `getThumbUrl` 取 `thumbnail || file_path`）。 */
    val thumb: String get() = thumbnail.ifBlank { url }
}

/**
 * 动态/日记（`GET /moment/list` 行、`GET /moment/:id` 详情、`POST /moment/batch` 追文行）。
 *
 * ⚠ 三个易踩点：
 *  1. `create_time` 是**毫秒时间戳字符串**（后端 `Date.now().toString()`），不是 `yyyy-MM-dd HH:mm:ss`。
 *  2. 列表接口的 `content` 已被后端**去 HTML 标签 + 截断 20 字**；详情接口给的是完整 HTML。
 *  3. `children` 是**追文 id 数组**，内容要另发 `POST /moment/batch {ids}` 取（→ [childrenData]，按时间升序）。
 */
data class Moment(
    val id: String,
    val content: String = "",
    val images: List<MomentImage> = emptyList(),
    val mood: String = "",
    val locationName: String = "",
    /** 毫秒时间戳字符串（可能为空）。 */
    val createTime: String = "",
    val childrenIds: List<String> = emptyList(),
    val childrenData: List<Moment> = emptyList(),
    /** 可见性 vt：0=仅自己、1=已分享（web `isShared = visible_type.vt === 1`）。 */
    val vt: Int = 0,
    val vs: Int = 0,
    val pw: String = "",
) {
    val coverImage: String get() = images.firstOrNull()?.url.orEmpty()
    val imageCount: Int get() = images.size
    val childrenCount: Int get() = childrenIds.size
}

/**
 * 后端详情/列表的 `content` 可能是 HTML（web 用 `v-html` 渲染）。Compose 没有等价能力，
 * 这里做「去标签 + 常见实体还原 + `<br>`/`</p>` 转换行」的纯文本降级，保证不显示尖括号。
 */
fun htmlToPlain(html: String): String {
    if (html.isBlank()) return ""
    return html
        .replace(Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</\\s*(p|div|li|h[1-6])\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .trim()
}
