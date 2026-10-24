package com.live.finance.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.live.finance.data.model.DiaryListSnapshot
import java.io.File

/**
 * 动态列表的**持久化本地缓存**（2026-10-22）。
 *
 * 动态页没有编辑功能，列表很少变；负责人明确要求「加载过的数据直接存在本地，别每次重拉」。
 * - 加密落盘（`EncryptedFile`，与 SessionStore 同一个 Keystore master key），不写明文日记内容；
 * - 只缓存第一页（[DiaryListSnapshot.rows]），分页仍走网络；
 * - [TTL_MS] 内直接读快照，过期后由页面**静默**刷新（不显示整页 loading）；
 * - 发布/删除、登录/登出、切站点、空间清理都会清掉它；
 * - `scope = 站点|token` 防换账号后读到上一个账号的动态。
 */
object DiaryListCache {
    /** 30 分钟。超过后页面先显示旧快照，再后台静默刷新；下拉刷新始终强制拉网络。 */
    const val TTL_MS = 30 * 60_000L

    private const val FILE_NAME = "diary_list_cache.enc"
    private val gson = Gson()

    /** 读取快照；scope 不匹配/文件损坏/解密失败一律返回 null（并尝试删除坏文件）。 */
    @Synchronized
    fun load(ctx: Context, scope: String): DiaryListSnapshot? {
        val file = cacheFile(ctx)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val snap = encrypted(ctx).openFileInput().bufferedReader(Charsets.UTF_8).use {
                gson.fromJson(it, DiaryListSnapshot::class.java)
            }
            if (snap == null || snap.scope != scope) null else snap
        }.onFailure {
            runCatching { file.delete() }
        }.getOrNull()
    }

    /** 写快照；失败静默（缓存是锦上添花，不能让页面挂掉）。 */
    @Synchronized
    fun save(ctx: Context, snapshot: DiaryListSnapshot) {
        runCatching {
            encrypted(ctx).openFileOutput().bufferedWriter(Charsets.UTF_8).use {
                gson.toJson(snapshot, it)
            }
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { cacheFile(ctx).delete() }
    }

    fun sizeBytes(ctx: Context): Long = cacheFile(ctx).takeIf { it.exists() }?.length() ?: 0L

    private fun cacheFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    private fun encrypted(ctx: Context): EncryptedFile {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            ctx,
            cacheFile(ctx),
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }
}
