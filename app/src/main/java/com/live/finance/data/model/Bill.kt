package com.live.finance.data.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 信用卡账单（对应 GET /card/bill 行；后端 JOIN card_base 带出 card_alias/card_last4/currency/
 * repay_day/bill_day/annual_fee/fee_free_rule，并计算 repay_date_calc/is_overdue_calc/overdue_days_calc）。
 *
 * 状态口径以 **web `bill/List.vue: getBillStatus`** 为准（本地按 bill_day/repay_day 现算），
 * 与后端 is_overdue_calc 在「repay_day=29~31 遇短月」时略有差异——复刻以 web 为准。
 */
data class Bill(
    val id: String,
    val cardId: String = "",
    val billMonth: String = "",          // YYYY-MM
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val creditLimit: Double = 0.0,
    val availLimit: Double = 0.0,
    val usedLimit: Double = 0.0,
    val tempLimit: Double = 0.0,
    val billStartDate: String = "",
    val billEndDate: String = "",
    val billAmount: Double = 0.0,
    val minRepay: Double = 0.0,
    val repaid: Double = 0.0,
    val needRepay: Double = 0.0,
    val points: Double = 0.0,
    val pointsRate: Double = 1.0,
    val remindSwitch: Boolean = true,
    val remindDays: Int = 3,
    val cardAlias: String = "",
    val cardLast4: String = "",
    val currency: String = "CNY",
    val annualFee: Double = 0.0,
    val feeFreeRule: String = "",
    /** 后端计算列 is_overdue_calc（web 本地算，此值仅备参考）。 */
    val isOverdue: Boolean = false,
    val overdueDays: Int = 0,
    /** web 模板可能读到的字段（后端 card_bill 无此列，线上恒为空）。 */
    val repayStatus: String = "",
    val pointsExpire: String = "",
) {
    val cardLabel: String
        get() = cardAlias.ifBlank { if (cardLast4.isNotBlank()) "银行卡($cardLast4)" else "信用卡" }

    /** 「账单日」= bill_month 的 bill_day 号（web：dayjs(`${billMonth}-${pad(billDay,2)}`)）。 */
    val billDate: LocalDate?
        get() = parseMonthDay(billMonth, billDay)

    /** 「还款日」= 账单日所在月的**次月** repay_day 号；dayjs `.date(n)` 溢出到下月，此处同口径。 */
    val repayDate: LocalDate?
        get() = billDate?.let { bd -> withDayRollover(bd.plusMonths(1), repayDay) }

    /** web getBillStatus 的 `type`：danger / success / warning / default / ''。 */
    val statusType: String get() = status().first

    /** web getBillStatus 的 `text`：已逾期N天 / 已还清 / 待还款 / 未出账 / 正常。 */
    val statusText: String get() = status().second

    /** web getBillStatus 的 `extra`：如 `3天后还款日` / `已出账2天` / `出账5天·还款20天`。 */
    val statusExtra: String get() = status().third

    private fun status(): Triple<String, String, String> {
        val bd = billDate ?: return Triple("", "正常", "")
        val rd = repayDate ?: return Triple("", "正常", "")
        val today = LocalDate.now()
        val daysToBill = ChronoUnit.DAYS.between(today, bd)
        val daysToRepay = ChronoUnit.DAYS.between(today, rd)
        val daysAfterBill = ChronoUnit.DAYS.between(bd, today)

        // ① 已逾期：超过还款日且仍有欠款
        if (needRepay > 0 && today.isAfter(rd)) {
            return Triple("danger", "已逾期${ChronoUnit.DAYS.between(rd, today)}天", "")
        }
        // ② 已过账单日（已出账）
        if (!today.isBefore(bd)) {
            if (needRepay == 0.0 || repayStatus == "已还清") {
                return Triple("success", "已还清", if (daysAfterBill > 0) "已出账${daysAfterBill}天" else "")
            }
            return Triple("warning", "待还款", if (daysToRepay >= 0) "${daysToRepay}天后还款日" else "")
        }
        // ③ 未到账单日（未出账）
        if (needRepay > 0) {
            return Triple("default", "未出账", "出账${daysToBill}天·还款${daysToRepay}天")
        }
        // ④ 默认
        return Triple("", "正常", "")
    }

    companion object {
        /** `YYYY-MM` + 日 → LocalDate（非法 / 天数<=0 返回 null）。 */
        fun parseMonthDay(month: String, day: Int): LocalDate? {
            if (month.length < 7 || day <= 0) return null
            return runCatching {
                val y = month.substring(0, 4).toInt()
                val m = month.substring(5, 7).toInt()
                LocalDate.of(y, m, 1).plusDays((day - 1).toLong())
            }.getOrNull()
        }

        /** dayjs `.date(n)` 语义：n 超出当月天数则溢出到下月。 */
        fun withDayRollover(monthFirst: LocalDate, day: Int): LocalDate =
            monthFirst.withDayOfMonth(1).plusDays((day - 1).toLong())
    }
}
