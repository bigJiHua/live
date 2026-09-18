package com.live.finance.data.model

/**
 * 银行卡/账户（对应 GET /card 行；后端 LEFT JOIN bus_category(type=bank) 带出 bank_name）。
 *
 * 注意（后端 findAll 的字段裁剪，见 api/src/modules/card/model/index.js）：
 * - cardType=credit 时返回：card_bin/card_length/last4_no/alias/open_date/expire_date/card_org/tag/
 *   bill_day/repay_day/credit_limit/temp_limit/points_rate/share_pool_id/card_img/color + bank_name；
 * - cardType=debit 时返回：card_bin/card_length/last4_no/alias/open_date/expire_date/card_org/tag/
 *   card_img/color + bank_name（无额度/账单日等字段）；
 * - 未筛选时按 credit 字段集返回。
 * 故所有「未返回」字段一律走默认值，UI 需容忍空值。
 */
data class Card(
    val id: String,
    val cardType: String = "debit",          // debit / credit（另有 virtual_cash=xxxx / virtual_balance=yyyy）
    val bankId: String = "",
    val bankName: String = "",
    val alias: String = "",
    val last4: String = "",
    val cardBin: String = "",
    val cardOrg: String = "",
    val cardLevel: String = "",
    val mainSub: String = "主卡",
    val cardLength: String = "19",
    val cardImg: String = "",
    val color: String = "",
    val currency: String = "CNY",
    val status: String = "正常",
    val isDefault: Boolean = false,
    val isHide: Boolean = false,
    /** 排序位（1-N；后端 findAll 未 SELECT 此列，列表响应通常为空，靠 sort 接口写入）。 */
    val sort: Int = 99,
    val tag: String = "",
    val remark: String = "",
    val sourceFrom: String = "",
    val openDate: String = "",
    val expireDate: String = "",
    // ===== 信用卡专属 =====
    val billDay: Int = 0,
    val repayDay: Int = 0,
    val creditLimit: Double = 0.0,
    val tempLimit: Double = 0.0,
    val pointsRate: Double = 1.0,
    val sharePoolId: String = "",
    val annualFee: Double = 0.0,
    val feeFreeRule: String = "",
) {
    val isCredit: Boolean get() = cardType == "credit"
    val isVirtual: Boolean get() = cardType.startsWith("virtual_")

    /** 「别名 || 银行名(尾号xxxx)」——对齐 web `alias || 银行名(尾号) ` 兜底。 */
    val displayName: String
        get() = alias.ifBlank {
            val b = bankName.ifBlank { "未命名卡" }
            if (last4.isNotBlank()) "$b($last4)" else b
        }

    /** 卡面主色（后端 color 形如 `#0052cc`），空则给主题蓝兜底。 */
    val displayColor: String get() = color.ifBlank { "#0052cc" }

    /**
     * 脱敏卡号（对齐 web CardStack.formatCardNo）：BIN 之后补 `*` 至 (cardLength-4)，末尾接后 4 位。
     * 例：cardBin=622202 cardLength=19 last4=0088 → `622202***********0088`（13 个星）。
     */
    val maskedNo: String
        get() {
            if (last4.isBlank() && cardBin.isBlank()) return "****"
            val total = cardLength.toIntOrNull()?.takeIf { it in 8..25 } ?: 19
            val stars = (total - cardBin.length - last4.length).coerceAtLeast(0)
            return cardBin + "*".repeat(stars) + last4
        }

    /** 按每 4 位分组（web 卡面展示用 `6222 0212 **** **** 0088` 风格）。 */
    val groupedNo: String
        get() = maskedNo.chunked(4).joinToString(" ")
}
