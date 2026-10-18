package com.live.finance.data.model

/**
 * 周期记账 / 固定事件 / 固定支出（对应 GET /recurring/list 行，后端已 JOIN category_name/account_name）。
 * web 真相源：views/Finance/events/Index.vue（固定事件）与 views/Finance/recurring/List.vue（固定支出）。
 * cycle 取值：month / year（web 表单 value；历史数据可能存 monthly/yearly，cycleLabel 兼容两种）。
 *
 * 字段命名对齐后端：
 *  - repeat_count  → [repeatCount]（分期总期数）
 *  - month_record  → [monthRecord]（选中月份的单条记录，**单数**，固定支出页「编辑当月」读取 remark）
 *  - month_records → [monthRecords]（全量期次明细，**复数**，固定事件页「月度处理记录」用）
 */
data class Recurring(
    val id: String,
    val name: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val categoryName: String = "",
    val accountId: String = "",
    val accountName: String = "",
    val accountLast4: String = "",
    val cycle: String = "",          // month / year
    val dayOfCycle: Int = 0,
    val monthOfCycle: Int = 0,
    val endDate: String = "",
    val remark: String = "",
    val isActive: Boolean = true,
    val status: String = "",
    /** 分期总期数（后端 repeat_count）；非分期为 0。 */
    val repeatCount: Int = 0,
    /** 选中月份的期状态（固定支出页用）：pending/done/skipped（分期另有 entered/void）。 */
    val monthStatus: String = "",
    val monthAmount: Double = 0.0,
    val happenDate: String = "",     // 选中月份的实际发生日
    /** 选中月份的单条记录（后端 month_record 单数）。 */
    val monthRecord: RecurringMonth? = null,
    /** 全量期次明细（固定事件页展开用），键为 yyyy-MM。 */
    val monthRecords: Map<String, RecurringMonth> = emptyMap(),
) {

    val cycleLabel: String get() = when (cycle) {
        "month", "monthly" -> if (dayOfCycle > 0) "每月${dayOfCycle}号" else "每月"
        "year", "yearly" -> if (monthOfCycle > 0) "每年${monthOfCycle}月" else "每年"
        "week", "weekly" -> "每周${dayOfCycle}号"
        else -> cycle.ifBlank { "周期" }
    }

    /**
     * 分期已完成期数 —— 严格对齐 web recurring/List.vue 的 `doneCount()`：
     * 只统计 `status === 'done'`，**不含** `entered`。分期期次入账后写的是 `entered`，
     * 因此分期行会显示「第 0/N 期」——这是 web 自身口径，按「照抄不统一」处理（见构造书坑位表）。
     */
    val doneCount: Int get() = monthRecords.values.count { it.status == "done" }

    /** web `isInstallment(item)`：category_id === 'installment'，或 account_id 解析出 type === 'installment'。 */
    val isInstallment: Boolean
        get() = categoryId == "installment" ||
            runCatching {
                com.google.gson.JsonParser.parseString(accountId).asJsonObject.get("type")?.asString == "installment"
            }.getOrDefault(false)
}

/** 单期明细（month_records 内一条 / month_record 单条）。 */
data class RecurringMonth(
    val status: String = "",         // pending / done / skipped（分期另有 entered / void）
    val planDate: String = "",       // 计划日
    val occurDate: String = "",      // 实际发生日
    val amount: Double = 0.0,
    val actualAmount: Double = 0.0,
    val remark: String = "",         // 本期备注（固定支出「编辑当月」回填）
) {
    val done: Boolean get() = status == "entered" || status == "done"
    val skipped: Boolean get() = status == "skipped"
}

/**
 * 中止分期期次的返回（`POST /recurring/:id/abort` → `{ aborted, skipped, finished }`）。
 * `skipped` = 月 → 原因（后端 `[{month, reason}]`）。
 */
data class InstallmentAbortResult(
    val aborted: List<String> = emptyList(),
    val skipped: List<Pair<String, String>> = emptyList(),
    val finished: Boolean = false,
)

/** GET /recurring/summary 返回。 */
data class RecurringSummary(
    val totalAmount: Double = 0.0,
    val total: Int = 0,
    val pending: Int = 0,
    val categoryStats: List<RecurringCatStat> = emptyList(),
)

data class RecurringCatStat(
    val categoryId: String = "",
    val categoryName: String = "",
    val count: Int = 0,
    val amount: Double = 0.0,
)