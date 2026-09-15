package com.live.finance.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "finance_session")

/**
 * 会话持久层，对应 web 的 localStorage：
 *  - token（finance_token）
 *  - 设备指纹 fp（web 用 device-hash；原生用首启生成的稳定 UUID）
 * Wave-0 用明文 Preferences 即可跑通；上线前替换为 EncryptedSharedPreferences（见风险文档 E 节）。
 */
class SessionStore(private val context: Context) {

    private val keyToken = stringPreferencesKey("finance_token")
    private val keyFp = stringPreferencesKey("device_fp")

    @Volatile var token: String? = null
        private set
    @Volatile var fingerprint: String = ""
        private set

    suspend fun load() {
        val prefs = context.dataStore.data.first()
        token = prefs[keyToken]?.takeIf { it.isNotEmpty() }
        var fp = prefs[keyFp]
        if (fp.isNullOrEmpty()) {
            fp = UUID.randomUUID().toString()
            context.dataStore.edit { it[keyFp] = fp!! }
        }
        fingerprint = fp!!
    }

    suspend fun setToken(value: String?) {
        token = value?.takeIf { it.isNotEmpty() }
        context.dataStore.edit {
            if (value.isNullOrEmpty()) it.remove(keyToken) else it[keyToken] = value
        }
    }

    suspend fun clear() {
        token = null
        context.dataStore.edit { it.remove(keyToken) }
    }
}
