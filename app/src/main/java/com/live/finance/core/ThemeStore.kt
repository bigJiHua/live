package com.live.finance.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.live.finance.theme.FONT_LEVEL_DEFAULT
import com.live.finance.theme.MONEY_RED_IN
import com.live.finance.theme.ThemePresets
import com.live.vant.theme.HapticStrength
import kotlinx.coroutines.flow.first

private val Context.themeStore by preferencesDataStore(name = "finance_theme")

/**
 * 主题偏好（前两项对应 web 的两条 localStorage）：
 *  - `ui-theme-choice` → [choice]：`system` 或预设 key（green/blue/indigo/mono/blackgold/navygold/titanium/emerald/wise/burgundy）
 *  - `money-color-mode` → [moneyColorMode]：`red-in`（默认）/ `red-out`
 *  - [fontLevel]：文字大小档位 1…5（默认 3 = 标准 = 现状 -2sp），**原生特有**（web 无此设置，也没有对应的 localStorage 键）
 *  - [haptics]：键盘按键震动，**原生特有**（全 web grep 无 `navigator.vibrate` / 任何触感代码）
 *  - [clickHaptics]：全局操作点击震动，**原生特有**（构造书 §7.13 预留的「另一条设置」）
 *  - [hapticStrength]：震动效果档 `light` / `medium` / `heavy`（默认 medium），键盘与全局点击共用
 */
data class ThemePrefs(
    val choice: String,
    val moneyColorMode: String,
    val fontLevel: Int,
    val haptics: Boolean,
    val clickHaptics: Boolean,
    val hapticStrength: String,
)

/** 主题偏好持久层。四者皆为**本地**设置（前两项 web 同样只落 localStorage，其余原生特有；都无接口）。 */
class ThemeStore(private val context: Context) {
    private val keyChoice = stringPreferencesKey("theme_choice")
    private val keyMoneyMode = stringPreferencesKey("money_color_mode")
    private val keyFontLevel = intPreferencesKey("font_level")
    private val keyHaptics = booleanPreferencesKey("key_haptics")
    private val keyClickHaptics = booleanPreferencesKey("click_haptics")
    private val keyHapticStrength = stringPreferencesKey("haptic_strength")

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
            fontLevel = prefs[keyFontLevel] ?: FONT_LEVEL_DEFAULT,
            haptics = prefs[keyHaptics] ?: true,
            clickHaptics = prefs[keyClickHaptics] ?: true,
            hapticStrength = prefs[keyHapticStrength] ?: HapticStrength.MEDIUM.key,
        )
    }

    suspend fun saveChoice(choice: String) {
        context.themeStore.edit { it[keyChoice] = choice }
    }

    suspend fun saveMoneyColorMode(mode: String) {
        context.themeStore.edit { it[keyMoneyMode] = mode }
    }

    suspend fun saveFontLevel(level: Int) {
        context.themeStore.edit { it[keyFontLevel] = level }
    }

    suspend fun saveHaptics(on: Boolean) {
        context.themeStore.edit { it[keyHaptics] = on }
    }

    suspend fun saveClickHaptics(on: Boolean) {
        context.themeStore.edit { it[keyClickHaptics] = on }
    }

    suspend fun saveHapticStrength(strength: String) {
        context.themeStore.edit { it[keyHapticStrength] = strength }
    }
}
