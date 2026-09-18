package com.live.finance.data.model

/**
 * 薪资模块数据模型（对应 web Work/Salary* 系列接口返回结构）。
 *
 * 关键约定（与 web 一致）：
 * - 金额字段服务端以 Double 返回；汇总字段以 String（"0.00"）返回，展示时直接 Money.format。
 * - 正式工 status：0=未生成、1=在职/已生成、2=已提交。兼职 status：0=未生成、1=在职/已生成、2=已提交。
 *   web 中 formalJob.status 用"在职/离职"字符串；此处 salaryDay 返回的数字 status 为 1/2 表示是否已建记录。
 */

/** 月度统计 —— 单日条目（对应 web salary_month.daily_list 元素）。 */
data class SalaryMonthDay(
    val date: String = "",
    val formalIncome: Double = 0.0,
    val parttimeIncomes: List<Double> = emptyList(),
) {
    val parttimeTotal: Double get() = parttimeIncomes.sum()
    val parttimeCount: Int get() = parttimeIncomes.count { it != 0.0 }
}

/** 月度统计（对应 web /work/salary/month）。 */
data class SalaryMonth(
    val formalTotal: String = "0.00",
    val parttimeTotal: String = "0.00",
    val totalIncome: String = "0.00",
    val dailyList: List<SalaryMonthDay> = emptyList(),
)

/** 当日正式工资（对应 web salary_day.formal）。 */
data class SalaryFormal(
    val jobId: String = "",
    val id: String = "",
    val company: String = "",
    val status: Int = 0,
    val daySalary: Double = 0.0,
    val social: Double = 0.0,
    val fund: Double = 0.0,
    val tax: Double = 0.0,
    val cut: Double = 0.0,
    val income: Double = 0.0,
)

/** 当日兼职工资（对应 web salary_day.parttimes 元素）。 */
data class SalaryParttime(
    val jobId: String = "",
    val id: String = "",
    val company: String = "",
    val status: Int = 0,
    val hourlyWage: Double = 0.0,
    val workHours: Double = 0.0,
    val daySalary: Double = 0.0,
    val subsidy: Double = 0.0,
    val cut: Double = 0.0,
    val income: Double = 0.0,
)

/** 当日薪酬（对应 web /work/salary/day）。 */
data class SalaryDay(
    val formal: SalaryFormal? = null,
    val parttimes: List<SalaryParttime> = emptyList(),
    val totalIncome: String = "0.00",
)
