package com.live.finance.ui.common

import com.live.finance.data.model.FlowRow

/** 流水按日分组 + 转账配对（对齐 web flow 逻辑的稳妥子集）。 */
sealed interface FlowCell {
    data class Single(val row: FlowRow) : FlowCell
    /** 转账：出账/入账合并为一行（可能只有出账=对外转账）。 */
    data class Transfer(val out: FlowRow?, val income: FlowRow?, val amount: Double, val date: String, val time: String) : FlowCell
}

data class FlowDay(val date: String, val cells: List<FlowCell>, val income: Double, val expense: Double)

const val REPAY_CATEGORY = "CATEGORY_REPAY"

/**
 * 输入原始流水 → 按日分组。规则：
 * - 有 transfer_group_id 的成对(支+收)合并为一个 Transfer 单元格；配对行不计入日收/支。
 * - CATEGORY_REPAY(信用卡还款)不计入支出。
 * - 日收/支 = 非转账、非还款的收入/支出合计。
 */
fun groupFlowsByDay(rows: List<FlowRow>): List<FlowDay> {
    val byDay = rows.groupBy { it.day }
    return byDay.entries.sortedByDescending { it.key }.map { (day, dayRows) ->
        val consumedGroups = HashSet<String>()
        val cells = ArrayList<FlowCell>()
        // 先处理成对转账
        dayRows.filter { it.transferGroupId.isNotEmpty() }.groupBy { it.transferGroupId }.forEach { (gid, group) ->
            if (gid in consumedGroups) return@forEach
            consumedGroups.add(gid)
            val out = group.firstOrNull { !it.isIncome }
            val inc = group.firstOrNull { it.isIncome }
            val amount = (out ?: inc)?.amount ?: 0.0
            val t = (out ?: inc)?.time ?: ""
            cells.add(FlowCell.Transfer(out, inc, amount, day, t))
        }
        // 再处理普通单笔记账（时间倒序）
        dayRows.filter { it.transferGroupId.isEmpty() }
            .sortedByDescending { it.createTime }
            .forEach { cells.add(FlowCell.Single(it)) }

        val normal = dayRows.filter { it.transferGroupId.isEmpty() }
        val income = normal.filter { it.isIncome }.sumOf { it.amount }
        val expense = normal.filter { !it.isIncome && it.categoryId != REPAY_CATEGORY }.sumOf { it.amount }
        FlowDay(day, cells, income, expense)
    }
}

private val CURRENCY_SYMBOLS = mapOf(
    "CNY" to "¥", "¥" to "¥", "USD" to "$", "HKD" to "HK$", "JPY" to "¥",
    "EUR" to "€", "GBP" to "£", "KRW" to "₩", "TWD" to "NT$", "AUD" to "A$", "CAD" to "C$",
)

fun currencySymbol(code: String): String = CURRENCY_SYMBOLS[code.uppercase()] ?: code

/** 是否「信用卡还款」记录（web 显示为「信用卡还款」并置灰、不计支出）。 */
fun isRepay(row: FlowRow): Boolean = row.categoryId == REPAY_CATEGORY || row.categoryName == "信用卡还款"
