package com.live.finance.core.store

import android.content.Context

/** 卡片页呈现形态：卡片模组（CardLab）或卡包（Wallet）。 */
enum class CardPageStyle(val key: String, val label: String) {
    Module("module", "卡片模组"),
    Wallet("wallet", "卡包");

    companion object {
        fun fromKey(key: String?): CardPageStyle =
            values().firstOrNull { it.key == key } ?: Module
    }
}

/**
 * 卡片页形态的持久层（2026-10-22）。
 *
 * 与 `GlassStore` 同模式：实验室里选好风格后点「保存设定」才落盘；
 * 正式「我的卡片」路由由 `CardPageHost` 读 [CardPageStyle] 决定渲染哪套页面。
 */
class CardStyleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): CardPageStyle = CardPageStyle.fromKey(prefs.getString(KEY_STYLE, null))

    fun save(style: CardPageStyle) {
        prefs.edit().putString(KEY_STYLE, style.key).apply()
    }

    private companion object {
        const val FILE_NAME = "finance_card_style"
        const val KEY_STYLE = "card_page_style"
    }
}
