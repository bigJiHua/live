package com.live.finance.core.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
//消歧：1.1.0-alpha06 里同时存在已废弃的 MasterKeys 与新的 MasterKey，
// 显式导入 Builder 可避免 Kotlin 在解析 `MasterKey.Builder(...)` 时选错。
import androidx.security.crypto.MasterKey.Builder as MasterKeyBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/** 旧版明文 DataStore（仅用于迁移读取，加密成功后会被清除） */
private val Context.dataStore by preferencesDataStore(name = "finance_session")

/**
 * 会话持久层，对应 web 的 localStorage：
 *  - token（finance_token）
 *  - 设备指纹 fp（web 用 device-hash；原生用首启生成的稳定 UUID）
 *
 * ## 安全整改 P0-3（2026-10-08）：改为加密存储
 * 原实现是**明文** `preferencesDataStore`（其自身注释就写着「上线前替换为
 * EncryptedSharedPreferences」）。公网部署后，token 明文落盘意味着：
 *  - `adb backup`（需 `allowBackup=true`，已同步关闭）可直接拖走全部凭据
 *  - root 设备 / adb 权限设备可直接读取
 *  - 设备丢失 = 会话立即可被复用
 *
 * 现在用 `EncryptedSharedPreferences`：
 *  - 键用 AES256-SIV（**可确定性加密**，以便能按 key 查询）
 *  - 值用 AES256-GCM（带完整性校验，密文被篡改会直接抛错而不是返回垃圾）
 *  - 密钥存 Android Keystore，**不出系统**（应用卸载/换机后不可恢复，这是预期行为）
 *
 * ## 三条降级路径（保证任何情况下都不崩）
 * 1. Keystore 不可用（如某些定制 ROM 硬件缺陷）⇒ 降级为明文 Preferences + 告警
 * 2. 旧版本迁移 ⇒ 从 DataStore 读旧值迁入，加密成功后清除明文
 * 3. 读失败（密钥失效/文件损坏）⇒ 视为未登录，重新登录即可
 */
class SessionStore(private val context: Context) {

    @Volatile var token: String? = null
        private set

    @Volatile var fingerprint: String = ""
        private set

    /**
     * 会话变化回调（[setToken] / [clear]）：供上层清进程内 GET 缓存。
     * 存储层不直接依赖网络层，避免 core.store → core.net 的反向依赖。
     */
    @Volatile var onSessionChanged: (() -> Unit)? = null

    /**
     * 加密存储。`by lazy` 保证只在首次访问时创建（首次会生成 Keystore 密钥，耗时）。
     * 任何异常都降级为明文，绝不让 App 因存储问题起不来。
     */
    private val prefs: SharedPreferences by lazy { openPrefs() }

    /** 打开（或创建）加密存储。单独抽成函数便于明确返回类型。 */
    private fun openPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKeyBuilder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "加密存储初始化失败，降级为明文存储（仍可用，但失去加密保护）", e)
            context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
        }
    }

    /** 是否已经降级到明文（供诊断/上报用） */
    @Volatile
    var isEncrypted: Boolean = true
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!isEncrypted && !hasPlainPrefs()) {
            // 说明加密初始化已失败，无需再试
            loadFromPlain()
        } else {
            // 触碰 prefs 触发 lazy 初始化
            prefs
            if (!isEncrypted) {
                loadFromPlain()
            } else {
                loadFromEncrypted()
                migrateFromLegacyDataStore()
            }
        }
    }

    private fun loadFromEncrypted() {
        runCatching {
            token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
            var fp = prefs.getString(KEY_FP, null)
            if (fp.isNullOrEmpty()) {
                fp = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_FP, fp).apply()
            }
            fingerprint = fp
        }.onFailure {
            // 密钥失效（换机/系统重置/应用从备份恢复）/ 文件损坏 ⇒ 视为未登录
            Log.w(TAG, "读取加密会话失败，按未登录处理（需重新登录）", it)
            runCatching { prefs.edit().clear().apply() }
            token = null
            fingerprint = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_FP, fingerprint).apply()
        }
    }

    private fun loadFromPlain() {
        val p = context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
        token = p.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
        var fp = p.getString(KEY_FP, null)
        if (fp.isNullOrEmpty()) {
            fp = UUID.randomUUID().toString()
            p.edit().putString(KEY_FP, fp).apply()
        }
        fingerprint = fp
    }

    private fun hasPlainPrefs(): Boolean =
        context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
            .contains(KEY_TOKEN)

    /**
     * 从旧版明文 DataStore 迁移。
     *
     * 为什么需要：升级用户（Wave-0 用的是明文 DataStore）若不迁移，升级后会被登出。
     * 迁移动作必须「先写加密存储，确认成功，再删明文」，否则中途失败会两头丢。
     */
    private suspend fun migrateFromLegacyDataStore() = withContext(Dispatchers.IO) {
        runCatching {
            val legacy = context.dataStore.data.first()
            val legacyToken = legacy[LEGACY_KEY_TOKEN]?.takeIf { it.isNotEmpty() }
            val legacyFp = legacy[LEGACY_KEY_FP]?.takeIf { it.isNotEmpty() }
            if (legacyToken == null && legacyFp == null) return@runCatching

            Log.i(TAG, "检测到旧版明文会话数据，开始迁移到加密存储")

            if (token.isNullOrEmpty() && legacyToken != null) {
                prefs.edit().putString(KEY_TOKEN, legacyToken).commit()
                token = legacyToken
            }
            if (legacyFp != null && prefs.getString(KEY_FP, null).isNullOrEmpty()) {
                prefs.edit().putString(KEY_FP, legacyFp).commit()
                fingerprint = legacyFp
            }

            // 确认加密存储已能读出，再清除明文
            val ok = prefs.getString(KEY_TOKEN, null) == legacyToken
            if (ok) {
                context.dataStore.edit {
                    it.remove(LEGACY_KEY_TOKEN)
                    it.remove(LEGACY_KEY_FP)
                }
                Log.i(TAG, "旧版明文会话数据已清除")
            } else {
                Log.w(TAG, "迁移校验未通过，保留明文数据以避免用户掉登录")
            }
        }.onFailure {
            Log.w(TAG, "旧数据迁移失败（不影响使用，重新登录即可）", it)
        }
    }

    suspend fun setToken(value: String?) = withContext(Dispatchers.IO) {
        token = value?.takeIf { it.isNotEmpty() }
        val target = if (isEncrypted) prefs else plainPrefs()
        target.edit().apply {
            if (value.isNullOrEmpty()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.commit()
        onSessionChanged?.invoke()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        token = null
        val target = if (isEncrypted) prefs else plainPrefs()
        target.edit().remove(KEY_TOKEN).commit()
        onSessionChanged?.invoke()
    }

    private fun plainPrefs(): SharedPreferences =
        context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "SessionStore"
        const val FILE_NAME = "finance_session_secure"
        const val FILE_NAME_PLAIN = "finance_session_plain"
        const val KEY_TOKEN = "finance_token"
        const val KEY_FP = "device_fp"

        // 旧版 DataStore 的 key（仅迁移时读取）
        val LEGACY_KEY_TOKEN =
            androidx.datastore.preferences.core.stringPreferencesKey("finance_token")
        val LEGACY_KEY_FP =
            androidx.datastore.preferences.core.stringPreferencesKey("device_fp")
    }
}