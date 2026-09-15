package com.live.finance.data.model

/** 待办（对应 GET /todo/list 行）。 */
data class Todo(
    val id: String,
    val content: String = "",
    val eventType: String = "",
    val happenDate: String = "",
    val status: String = "待完成",
    val priority: String = "",
    val needRemind: Boolean = false,
)

/** 工作信息（对应 GET /work/job/list 行）。 */
data class Job(
    val id: String,
    val jobType: String = "",
    val company: String = "",
    val status: String = "",
    val joinDate: String = "",
    val leaveDate: String = "",
    val payDay: Int = 0,
    val baseSalary: Double = 0.0,
    val hourlyWage: Double = 0.0,
)
