package com.live.finance.ui.asset

import com.live.finance.data.model.AssetDetails
import com.live.finance.data.model.AssetItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 资产结构登记域共享规则（web 真相源：Register.vue / Edit.vue / List.vue / Trend.vue）。
 * 被编辑器屏、登记记录列表、走势页共同使用，避免名称映射/汇率口径分叉。
 */
object AssetShared {

    // ========== 类型表（编辑器 picker） ==========

    /** 境内资产类型（Register/Edit 完全一致 8 项）。 */
    val BALANCE_TYPES = listOf(
        "wechat" to "微信余额",
        "alipay" to "支付宝余额",
        "bank" to "银行活期",
        "wealth" to "理财",
        "fund" to "基金",
        "stock" to "股票",
        "cash" to "现金",
        "other" to "其他",
    )

    /** 负债类型（Register/Edit picker 6 项；名称映射表另含历史银行信用卡枚举）。 */
    val DEBT_TYPES = listOf(
        "CreditCard" to "信用卡",
        "Huabei" to "花呗",
        "Jiebei" to "借呗",
        "JD" to "京东白条",
        "Meituan" to "美团月付",
        "Other" to "其他",
    )

    /** 默认展示的汇率行（与 web 一致：HKD 固定不可删，USD/GBP/EUR 可删）。 */
    val DEFAULT_RATE_CURRENCIES = listOf("HKD", "USD", "GBP", "EUR")

    /** 境外账户类型：永远有「其他港币」，其余「其他XX」按已填汇率动态生成（有值且非 HKD）。 */
    fun offshoreTypes(rates: Map<String, String>): List<Pair<String, String>> =
        buildList {
            add("OtherHKD" to "其他港币")
            rates.forEach { (ccy, v) ->
                if (ccy != "HKD" && v.isNotEmpty()) add("Other$ccy" to "其他$ccy")
            }
        }

    fun offshoreCurrencyOf(type: String): String = type.removePrefix("Other")

    // ========== 名称映射（List/Trend 读历史数据时的全集，比 picker 多） ==========

    private val BALANCE_NAMES = mapOf(
        "wechat" to "微信余额",
        "alipay" to "支付宝余额",
        "bank" to "银行活期",
        "wealth" to "理财",
        "fund" to "基金",
        "stock" to "股票/股市",
        "profit" to "收益",
        "redpacket" to "虚拟红包",
        "cash" to "现金",
        "other" to "其他",
    )

    private val OFFSHORE_NAMES = mapOf(
        "ICBCA" to "工商银行(港)",
        "BOCA" to "中国银行(港)",
        "HSBC" to "汇丰银行(港)",
        "CMBCA" to "招商银行(港)",
        "Wise" to "Wise",
        "ifast" to "iFast",
        "IBKR" to "IBKR",
        "Schwab" to "盈透证券",
        "OtherUSD" to "其他美元",
        "OtherHKD" to "其他港币",
        "OtherGBP" to "其他英镑",
        "OtherEUR" to "其他欧元",
        "Other" to "其他",
    )

    private val DEBT_NAMES = mapOf(
        "ICBC" to "工商银行信用卡",
        "ABC" to "农业银行信用卡",
        "CCB" to "建设银行信用卡",
        "BOC" to "中国银行信用卡",
        "CMBC" to "招商银行信用卡",
        "COMM" to "交通银行信用卡",
        "SPDB" to "浦发银行信用卡",
        "CIB" to "兴业银行信用卡",
        "Huabei" to "花呗",
        "Jiebei" to "借呗",
        "JD" to "京东白条",
        "Meituan" to "美团月付",
        "CreditCard" to "信用卡",
        "Other" to "其他",
    )

    fun balanceName(type: String): String = BALANCE_NAMES[type] ?: type
    fun offshoreName(type: String): String =
        OFFSHORE_NAMES[type] ?: if (type.startsWith("Other")) "其他${type.removePrefix("Other")}" else type
    fun debtName(type: String): String = DEBT_NAMES[type] ?: type

    fun itemName(item: AssetItem, category: String): String =
        item.customName.ifEmpty {
            when (category) {
                "balance" -> balanceName(item.type)
                "offshore" -> offshoreName(item.type)
                "debt" -> debtName(item.type)
                else -> item.type
            }
        }

    // ========== 汇率（每 100 外币 = ? CNY） ==========

    fun hasRate(rates: Map<String, String>, currency: String?): Boolean {
        if (currency.isNullOrEmpty()) return false
        val v = rates[currency]
        return !v.isNullOrEmpty()
    }

    /** amount × rate ÷ 100，保留 2 位小数（四舍五入到分，对齐 web `Math.round(x*100)/100`）。 */
    fun toCny(amount: Double, currency: String?, rates: Map<String, String>): Double {
        if (!hasRate(rates, currency)) return amount
        val rate = rates!![currency]!!.toDoubleOrNull() ?: return amount
        return (amount * rate / 100.0 * 100).roundToLong() / 100.0
    }

    // ========== 输入过滤（逐字对齐 web formatter） ==========

    private fun filterDecimal(raw: String, maxInt: Int, maxFrac: Int, cap: Double): String {
        var s = raw.replace(Regex("[^\\d.]"), "")
        s = s.replace(Regex("\\.{2,}"), ".")
        s = s.replace(".", "#").replace(".", "").replace("#", ".")
        s = Regex("^(\\d+)(\\.\\d{0,$maxFrac})?.*$").replace(s) { it.groupValues[1] + (it.groupValues[2]) }
        val v = s.toDoubleOrNull()
        if (v != null && v > cap) return String.format(Locale.US, "%.${maxFrac}f", cap)
        // 整数位超长截断
        val parts = s.split(".")
        val intPart = if (parts[0].length > maxInt) parts[0].take(maxInt) else parts[0]
        return if (parts.size > 1) "$intPart.${parts[1]}" else intPart
    }

    /** 金额：整数 ≤9 位、小数 ≤3 位、上限 999999999.999（web formatAmountInput）。 */
    fun filterAmount(raw: String): String = filterDecimal(raw, 9, 3, 999999999.999)

    /** 汇率：整数 ≤3、小数 ≤4、上限 999.9999（web formatRate）。 */
    fun filterRate(raw: String): String = filterDecimal(raw, 3, 4, 999.9999)

    /** 币种代码：纯字母大写（web formatCurrencyCode）。 */
    fun filterCurrencyCode(raw: String): String =
        raw.replace(Regex("[^a-zA-Z]"), "").uppercase()

    // ========== 合计 ==========

    fun balanceTotal(d: AssetDetails?): Double =
        d?.balance?.sumOf { it.amount } ?: 0.0

    fun offshoreTotal(d: AssetDetails?): Double =
        d?.offshore?.sumOf { toCny(it.amount, it.currency, d.exchangeRates) } ?: 0.0

    fun debtTotal(d: AssetDetails?): Double =
        d?.debt?.sumOf { it.amount } ?: 0.0

    /** 境内 + 境外折 CNY − 负债（web finalBalance，Edit 额外 round 2）。 */
    fun netBalance(d: AssetDetails?): Double =
        balanceTotal(d) + offshoreTotal(d) - debtTotal(d)

    // ========== 时间戳格式化（List.vue：create_time/update_time 是毫秒/秒数字串） ==========

    private val DT_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** 毫秒（或秒）时间戳字符串 → yyyy-MM-dd HH:mm:ss；空/非法 → "-"。 */
    fun formatMillis(ts: String?): String {
        if (ts.isNullOrEmpty()) return "-"
        val n = ts.toLongOrNull() ?: return ts.take(19)
        val ms = if (n < 1_000_000_000_000L) n * 1000 else n
        return DT_FMT.format(Date(ms))
    }
}
