package com.live.finance.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.themeStore by preferencesDataStore(name = "finance_theme")

enum class ThemeMode { System, Light, Dark }

/** 主题模式持久层（对应 web useUiTheme 的 data-theme-mode）。 */
class ThemeStore(private val context: Context) {
    private val key = stringPreferencesKey("theme_mode")

    suspend fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(context.themeStore.data.first()[key] ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)

    suspend fun save(mode: ThemeMode) {
        context.themeStore.edit { it[key] = mode.name }
    }
}
