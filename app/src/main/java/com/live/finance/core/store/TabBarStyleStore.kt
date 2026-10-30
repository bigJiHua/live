package com.live.finance.core.store

import android.content.Context

/**
 * 底部 Tab 组件选择持久层（2026-10-23）。
 *
 * `false` = 经典悬浮岛玻璃；`true` = 全新独立 Liquid Glass 底部 Tab 组件
 * （`ui/main/NewLiquidGlassTabBar.kt`，与经典版不共用绘制代码）。
 * 实验室开关即时生效并立即落盘（与「是否因滚动而收起」同一交互口径）。
 */
class TabBarStyleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): Boolean = prefs.getBoolean(KEY_USE_NEW, false)

    fun save(useNew: Boolean) {
        prefs.edit().putBoolean(KEY_USE_NEW, useNew).apply()
    }

    private companion object {
        const val FILE_NAME = "finance_tab_bar"
        const val KEY_USE_NEW = "use_new_tab_bar"
    }
}
