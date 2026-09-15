package com.live.finance.data.model

/** 动态/日记（对应 GET /moment/list 行）。 */
data class Moment(
    val id: String,
    val content: String = "",
    val imgUrl: String = "",
    val mood: String = "",
    val location: String = "",
    val createTime: String = "",
) {
    val dateLabel: String get() = com.live.finance.core.TimeFmt.dateTime(createTime)
}
