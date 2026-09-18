package com.live.finance.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.ThemePresets
import kotlinx.coroutines.flow.first

private val Context.themeStore by preferencesDataStore(name = "finance_theme")

/**
 * 主题偏好（对应 web 的两条 localStorage）：
 *  - `ui-theme-choice` → [choice]：`system` 或预设 key（green/blue/indigo/mono/blackgold/navygold/titanium/emerald/wise/burgundy）
 *  - `money-color-mode` → [moneyColorMode]：`red-in`（默认）/ `red-out`
 */
data class ThemePrefs(val choice: String, val moneyColorMode: String)

/** 主题偏好持久层。两者皆为**本地**设置（web 同样只落 localStorage，无接口）。 */
class ThemeStore(private val context: Context) {
    private val keyChoice = stringPreferencesKey("theme_choice")
    private val keyMoneyMode = stringPreferencesKey("money_color_mode")

    /** 旧版（只有浅/深/跟随系统三值时）落库的键，用于一次性迁移。 */
    private val keyLegacyMode = stringPreferencesKey("theme_mode")

    suspend fun load(): ThemePrefs {
        val prefs = context.themeStore.data.first()
        val choice = prefs[keyChoice] ?: when (prefs[keyLegacyMode]) {
            // 迁移：旧的「浅色/深色」映射到 web 的系统默认方案（浅=靛、深=黑金）
            "Light" -> ThemePresets.DEFAULT_LIGHT
            "Dark" -> ThemePresets.DEFAULT_DARK
            else -> ThemePresets.CHOICE_SYSTEM
        }
        return ThemePrefs(
            choice = choice,
            moneyColorMode = prefs[keyMoneyMode] ?: MONEY_RED_IN,
        )
    }

    suspend fun saveChoice(choice: String) {
        context.themeStore.edit { it[keyChoice] = choice }
    }

    suspend fun saveMoneyColorMode(mode: String) {
        context.themeStore.edit { it[keyMoneyMode] = mode }
    }
}
