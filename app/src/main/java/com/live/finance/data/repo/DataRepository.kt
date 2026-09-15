package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult

interface DataRepository {
    /** 数据总览状态（原始 data JSON 字符串，供页面直接展示/解析）。 */
    suspend fun status(): ApiResult<String>
    suspend fun backups(): ApiResult<List<String>>
    /** 导出全库：返回 JSON 文本（data 部分）。 */
    suspend fun exportFull(): ApiResult<String>
    /** 下载备份文件（返回字节）。 */
    suspend fun downloadBackup(filename: String): ApiResult<ByteArray>
    /** SAF 选文件后导入：POST /data-manager/import/sql (multipart file，pinLockGuard)。 */
    suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<Unit>
}

class FakeDataRepository : DataRepository {
    override suspend fun status(): ApiResult<String> = ApiResult.Ok("{\"tables\":6,\"ok\":true}", "")
    override suspend fun backups(): ApiResult<List<String>> = ApiResult.Ok(listOf("system_20260914.sql"), "")
    override suspend fun exportFull(): ApiResult<String> = ApiResult.Ok("{\"exported\":\"demo\"}", "")
    override suspend fun downloadBackup(filename: String): ApiResult<ByteArray> = ApiResult.Ok(ByteArray(0), "")
    override suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<Unit> = ApiResult.Ok(Unit, "导入成功(模拟)")
}

class RemoteDataRepository(private val client: ApiClient) : DataRepository {
    override suspend fun status(): ApiResult<String> =
        client.get("/data-manager/status").toResult().map { json -> json?.toString() ?: "" }

    override suspend fun backups(): ApiResult<List<String>> =
        client.get("/data-manager/backups").toResult().map { data ->
            (when (data) { is JsonArray -> data; is JsonObject -> data.getAsJsonArray("list") ?: JsonArray(); else -> JsonArray() })
                .mapNotNull { el ->
                    if (!el.isJsonObject) el.asText()
                    else { val o = el.asJsonObject; (o.get("filename") ?: o.get("name") ?: o.get("file"))?.takeIf { !it.isJsonNull }?.asString }
                }
        }

    override suspend fun exportFull(): ApiResult<String> =
        client.get("/data-manager/export/full").toResult().map { json -> json?.toString() ?: "" }

    override suspend fun downloadBackup(filename: String): ApiResult<ByteArray> =
        client.download("/data-manager/download/$filename")

    override suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<Unit> =
        client.uploadRaw("/data-manager/import/sql", "file", fileName, "application/octet-stream", bytes).map { }
}

private fun JsonElement.asText(): String = if (isJsonPrimitive) asString else toString()
