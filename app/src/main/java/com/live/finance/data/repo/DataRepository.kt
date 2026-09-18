package com.live.finance.data.repo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.live.finance.core.net.ApiClient
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.map
import com.live.finance.core.net.toResult
import com.live.finance.data.model.DbBackupFile
import com.live.finance.data.model.DbColumn
import com.live.finance.data.model.DbExportTask
import com.live.finance.data.model.DbImportResult
import com.live.finance.data.model.DbSqlImportError
import com.live.finance.data.model.DbSqlImportResult
import com.live.finance.data.model.DbSystemBackupGroup
import com.live.finance.data.model.DbTablePage
import com.live.finance.data.model.DbTableStatus
import com.live.finance.data.model.DbValidateError
import com.live.finance.data.model.DbValidateResult

/**
 * 数据管理仓储 —— 对应 `api/src/modules/dataManager`（**整模块管理员专属**：
 * `router.use(authGuard, requireAdmin)`，非 admin 的账号一律 403「无权限访问数据管理功能」）。
 *
 * 契约要点：
 * - `GET /tables` → `string[]`；`GET /status` → 表状态（**camelCase**）；`GET /structure/:t`（全小写键）；
 *   `GET /data/:t?page&pageSize` → `{data, total, page, pageSize}`。
 * - 导出类（`/export/table/:t`、`/export/full`、`POST /backups/system`）**都是异步任务**：立即回 **202 + `{taskId,status}`**，
 *   随后靠 `GET /export/task/:taskId` 轮询（`DELETE` 同路径取消，**受 PIN 保护**）。
 * - `POST /validate` 的 `canImport` 在**响应顶层**（不在 data 内）。
 * - `POST /import/sql`（multipart `file`）在**部分失败时回 207**，`data` 里有逐条错误；该接口**受 PIN 保护**。
 * - `GET /backups` → `[{filename,size,createdAt,type}]`（createdAt 倒序）；
 *   `GET /backups/system` → `[{date, files:[...]}]`（日期倒序）；删备份 **受 PIN 保护**；下载走 `GET /download/:filename`。
 */
interface DataRepository {
    // ===== 表信息 =====
    suspend fun tables(): ApiResult<List<String>>
    suspend fun tableStatus(): ApiResult<List<DbTableStatus>>
    suspend fun structure(table: String): ApiResult<List<DbColumn>>
    suspend fun tableData(table: String, page: Int, pageSize: Int): ApiResult<DbTablePage>

    // ===== 导出（异步任务）=====
    suspend fun exportTable(table: String, includeData: Boolean): ApiResult<DbExportTask>
    suspend fun exportFull(includeData: Boolean): ApiResult<DbExportTask>
    suspend fun taskStatus(taskId: String): ApiResult<DbExportTask>
    suspend fun cancelTask(taskId: String): ApiResult<Unit>

    // ===== 导入 =====
    suspend fun validateImport(table: String, rows: List<JsonObject>): ApiResult<DbValidateResult>
    suspend fun importData(table: String, rows: List<JsonObject>, forceClear: Boolean): ApiResult<DbImportResult>
    suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<DbSqlImportResult>

    // ===== 备份 =====
    suspend fun backups(): ApiResult<List<DbBackupFile>>
    suspend fun systemBackups(): ApiResult<List<DbSystemBackupGroup>>
    suspend fun createSystemBackup(): ApiResult<DbExportTask>
    suspend fun deleteBackup(filename: String): ApiResult<Unit>
    suspend fun downloadBackup(filename: String): ApiResult<ByteArray>
}

class FakeDataRepository : DataRepository {
    private val tableNames = listOf("user_info", "account", "card_base", "flow_detail", "todo_item", "moment")
    private val statusSeed = listOf(
        DbTableStatus("user_info", "InnoDB", 3, 16384, 0, "2026-08-01 10:00:00", "2026-09-17 09:12:00", "utf8mb4_general_ci", "用户信息"),
        DbTableStatus("account", "InnoDB", 1250, 262144, 16384, "2026-08-01 10:00:00", "2026-09-17 09:20:00", "utf8mb4_general_ci", "账户"),
        DbTableStatus("card_base", "InnoDB", 18, 32768, 16384, "2026-08-01 10:00:00", "2026-09-16 21:02:00", "utf8mb4_general_ci", "银行卡"),
        DbTableStatus("flow_detail", "InnoDB", 20480, 4194304, 1048576, "2026-08-01 10:00:00", "2026-09-17 08:55:00", "utf8mb4_general_ci", "流水明细"),
        DbTableStatus("todo_item", "InnoDB", 96, 49152, 16384, "2026-08-01 10:00:00", "2026-09-15 18:30:00", "utf8mb4_general_ci", ""),
        DbTableStatus("moment", "InnoDB", 42, 65536, 16384, "2026-08-01 10:00:00", "2026-09-14 22:11:00", "utf8mb4_general_ci", "动态"),
    )

    override suspend fun tables(): ApiResult<List<String>> = ApiResult.Ok(tableNames, "")

    override suspend fun tableStatus(): ApiResult<List<DbTableStatus>> = ApiResult.Ok(statusSeed, "")

    override suspend fun structure(table: String): ApiResult<List<DbColumn>> = ApiResult.Ok(
        listOf(
            DbColumn("id", "varchar(32)", "NO", "PRI", "", ""),
            DbColumn("user_id", "varchar(32)", "NO", "MUL", "", ""),
            DbColumn("amount", "decimal(12,2)", "YES", "", "0.00", ""),
            DbColumn("remark", "varchar(255)", "YES", "", JsonNull.INSTANCE.toString(), ""),
            DbColumn("create_time", "bigint(20)", "YES", "", "0", ""),
        ), ""
    )

    override suspend fun tableData(table: String, page: Int, pageSize: Int): ApiResult<DbTablePage> {
        val all = (1..37).map { i ->
            JsonObject().apply {
                addProperty("id", "row_$i")
                addProperty("user_id", "u_001")
                addProperty("amount", i * 12.5)
                addProperty("remark", "示例数据 $i")
                addProperty("create_time", 1757000000000L + i)
            }
        }
        val from = ((page - 1) * pageSize).coerceIn(0, all.size)
        val to = (from + pageSize).coerceAtMost(all.size)
        return ApiResult.Ok(DbTablePage(all.subList(from, to), all.size.toLong(), page, pageSize), "")
    }

    private val tasks = HashMap<String, DbExportTask>()

    override suspend fun exportTable(table: String, includeData: Boolean): ApiResult<DbExportTask> =
        newTask("export_table", table)

    override suspend fun exportFull(includeData: Boolean): ApiResult<DbExportTask> =
        newTask("export_full_database", "")

    override suspend fun taskStatus(taskId: String): ApiResult<DbExportTask> =
        tasks[taskId]?.let { ApiResult.Ok(it, "") } ?: ApiResult.Fail("任务不存在或已过期")

    override suspend fun cancelTask(taskId: String): ApiResult<Unit> {
        tasks[taskId]?.let { tasks[taskId] = it.copy(status = "cancelled") }
        return ApiResult.Ok(Unit, "任务已取消")
    }

    private fun newTask(type: String, table: String): ApiResult<DbExportTask> {
        val id = "task_${System.currentTimeMillis()}"
        val filename = if (type == "export_full_database") "20260917-093000-full-backup.sql.zip" else "20260917-093000-$table.sql"
        val t = DbExportTask(id = id, type = type, status = "completed", tableName = table, progress = 100, resultFilename = filename, resultSize = 262144)
        tasks[id] = t
        return ApiResult.Ok(t, "任务已提交")
    }

    override suspend fun validateImport(table: String, rows: List<JsonObject>): ApiResult<DbValidateResult> = ApiResult.Ok(
        DbValidateResult(
            tableName = table,
            tableColumns = listOf("id", "user_id", "amount", "remark", "create_time"),
            totalRows = rows.size,
            validRows = rows.size,
            invalidRows = 0,
            canImport = true,
        ), ""
    )

    override suspend fun importData(table: String, rows: List<JsonObject>, forceClear: Boolean): ApiResult<DbImportResult> =
        ApiResult.Ok(DbImportResult(rows.size, rows.size, "20260917-093000-$table.sql"), "数据导入成功")

    override suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<DbSqlImportResult> =
        ApiResult.Ok(DbSqlImportResult(executedCount = 42, errorCount = 0, backupFile = "20260917-093000-full-backup.sql.zip", backupTimestamp = "2026-09-17 09:30:00"), "SQL 导入成功(模拟)")

    override suspend fun backups(): ApiResult<List<DbBackupFile>> = ApiResult.Ok(
        listOf(
            DbBackupFile("20260917-093000-full-backup.sql.zip", 524288, "2026-09-17T09:30:00.000Z", "zip"),
            DbBackupFile("20260916-210200-account.sql", 32768, "2026-09-16T21:02:00.000Z", "sql"),
        ), ""
    )

    override suspend fun systemBackups(): ApiResult<List<DbSystemBackupGroup>> = ApiResult.Ok(
        listOf(
            DbSystemBackupGroup(
                "2026-09-17",
                listOf(
                    DbBackupFile("20260917-093000-system-backup-full.sql", 524288, "2026-09-17T09:30:00.000Z", "full"),
                    DbBackupFile("20260917-093001-system-backup-schema-only.sql", 65536, "2026-09-17T09:30:01.000Z", "schema-only"),
                ),
            ),
            DbSystemBackupGroup(
                "2026-09-16",
                listOf(DbBackupFile("20260916-210200-system-backup-full.sql", 512000, "2026-09-16T21:02:00.000Z", "full")),
            ),
        ), ""
    )

    override suspend fun createSystemBackup(): ApiResult<DbExportTask> = newTask("system_backup", "")

    override suspend fun deleteBackup(filename: String): ApiResult<Unit> = ApiResult.Ok(Unit, "删除成功")

    override suspend fun downloadBackup(filename: String): ApiResult<ByteArray> =
        ApiResult.Ok("-- demo backup $filename".toByteArray(), "")
}

class RemoteDataRepository(private val client: ApiClient) : DataRepository {

    override suspend fun tables(): ApiResult<List<String>> =
        client.get("/data-manager/tables").toResult().map { data ->
            (data as? JsonArray)?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()
        }

    override suspend fun tableStatus(): ApiResult<List<DbTableStatus>> =
        client.get("/data-manager/status").toResult().map { data ->
            arr(data).map { el -> val o = el.asJsonObject
                DbTableStatus(
                    name = str(o, "name"), engine = str(o, "engine"), rowCount = lng(o, "rowCount"),
                    dataLength = lng(o, "dataLength"), indexLength = lng(o, "indexLength"),
                    createTime = str(o, "createTime"), updateTime = str(o, "updateTime"),
                    collation = str(o, "collation"), comment = str(o, "comment"),
                )
            }
        }

    override suspend fun structure(table: String): ApiResult<List<DbColumn>> =
        client.get("/data-manager/structure/$table").toResult().map { data ->
            arr(data).map { el -> val o = el.asJsonObject
                DbColumn(
                    field = str(o, "field"), type = str(o, "type"), nullable = str(o, "null"),
                    key = str(o, "key"), defaultValue = str(o, "default"), extra = str(o, "extra"),
                )
            }
        }

    override suspend fun tableData(table: String, page: Int, pageSize: Int): ApiResult<DbTablePage> =
        client.get("/data-manager/data/$table", mapOf("page" to "$page", "pageSize" to "$pageSize"))
            .toResult().map { data ->
                val o = data as? JsonObject
                DbTablePage(
                    rows = o?.getAsJsonArray("data")?.mapNotNull { it as? JsonObject } ?: emptyList(),
                    total = o?.let { lng(it, "total") } ?: 0L,
                    page = o?.let { lng(it, "page").toInt() } ?: page,
                    pageSize = o?.let { lng(it, "pageSize").toInt() } ?: pageSize,
                )
            }

    override suspend fun exportTable(table: String, includeData: Boolean): ApiResult<DbExportTask> =
        client.get("/data-manager/export/table/$table", mapOf("includeData" to "$includeData"))
            .toResult().map { taskOf(it) }

    override suspend fun exportFull(includeData: Boolean): ApiResult<DbExportTask> =
        client.get("/data-manager/export/full", mapOf("includeData" to "$includeData"))
            .toResult().map { taskOf(it) }

    override suspend fun taskStatus(taskId: String): ApiResult<DbExportTask> =
        client.get("/data-manager/export/task/$taskId").toResult().map { taskOf(it) }

    override suspend fun cancelTask(taskId: String): ApiResult<Unit> =
        client.delete("/data-manager/export/task/$taskId").toResult().map { }

    override suspend fun validateImport(table: String, rows: List<JsonObject>): ApiResult<DbValidateResult> {
        val body = JsonObject().apply {
            addProperty("tableName", table)
            add("data", JsonArray().apply { rows.forEach { add(it) } })
        }
        val env = client.post("/data-manager/validate", body)
        // ⚠ `canImport` 在响应**顶层**，toResult() 只保留 data → 需读 env.root
        val canImport = runCatching {
            env.root?.get("canImport")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        }.getOrDefault(false)
        return when (val r = env.toResult()) {
            is ApiResult.Ok -> {
                val d = r.data as? JsonObject
                ApiResult.Ok(
                    DbValidateResult(
                        tableName = d?.let { str(it, "tableName") } ?: table,
                        tableColumns = d?.getAsJsonArray("tableColumns")?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList(),
                        totalRows = d?.let { lng(it, "totalRows").toInt() } ?: 0,
                        validRows = d?.let { lng(it, "validRows").toInt() } ?: 0,
                        invalidRows = d?.let { lng(it, "invalidRows").toInt() } ?: 0,
                        errors = d?.getAsJsonArray("errors")?.mapNotNull { e ->
                            (e as? JsonObject)?.let { eo ->
                                DbValidateError(
                                    row = lng(eo, "row").toInt(),
                                    errors = eo.getAsJsonArray("errors")?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList(),
                                )
                            }
                        } ?: emptyList(),
                        canImport = canImport,
                    ),
                    r.message,
                )
            }
            is ApiResult.NeedPin -> r
            is ApiResult.Fail -> r
            else -> ApiResult.Fail("校验数据失败")
        }
    }

    override suspend fun importData(table: String, rows: List<JsonObject>, forceClear: Boolean): ApiResult<DbImportResult> {
        val body = JsonObject().apply {
            addProperty("tableName", table)
            add("data", JsonArray().apply { rows.forEach { add(it) } })
            addProperty("forceClear", forceClear)
        }
        return client.post("/data-manager/import", body).toResult().map { data ->
            val o = data as? JsonObject
            DbImportResult(
                importedRows = o?.let { lng(it, "importedRows").toInt() } ?: 0,
                totalRows = o?.let { lng(it, "totalRows").toInt() } ?: 0,
                backupFile = o?.let { str(it, "backupFile") } ?: "",
            )
        }
    }

    override suspend fun importSql(bytes: ByteArray, fileName: String): ApiResult<DbSqlImportResult> =
        // 后端在部分失败时回 207；Envelope.toResult() 对 207 会走 isMessage 分支（带 message+data）→ 仍能取到统计
        client.uploadRaw("/data-manager/import/sql", "file", fileName, "application/octet-stream", bytes).map { data ->
            // ⚠ `ApiResult<JsonElement?>.map` 的入参是 JsonElement?（不是 JsonObject?），需显式转
            val o = data as? JsonObject
            DbSqlImportResult(
                executedCount = o?.let { lng(it, "executedCount").toInt() } ?: 0,
                errorCount = o?.let { lng(it, "errorCount").toInt() } ?: 0,
                errors = o?.getAsJsonArray("errors")?.mapNotNull { e ->
                    (e as? JsonObject)?.let { eo ->
                        DbSqlImportError(lng(eo, "index").toInt(), str(eo, "sql"), str(eo, "message"))
                    }
                } ?: emptyList(),
                backupFile = o?.let { str(it, "backupFile") } ?: "",
                backupTimestamp = o?.let { str(it, "backupTimestamp") } ?: "",
            )
        }

    override suspend fun backups(): ApiResult<List<DbBackupFile>> =
        client.get("/data-manager/backups").toResult().map { data -> arr(data).map { backupOf(it.asJsonObject) } }

    override suspend fun systemBackups(): ApiResult<List<DbSystemBackupGroup>> =
        client.get("/data-manager/backups/system").toResult().map { data ->
            arr(data).map { el -> val o = el.asJsonObject
                DbSystemBackupGroup(
                    date = str(o, "date"),
                    files = arr(o.get("files")).map { backupOf(it.asJsonObject) },
                )
            }
        }

    override suspend fun createSystemBackup(): ApiResult<DbExportTask> =
        client.post("/data-manager/backups/system", JsonObject()).toResult().map { taskOf(it) }

    override suspend fun deleteBackup(filename: String): ApiResult<Unit> =
        client.delete("/data-manager/backups/$filename").toResult().map { }

    override suspend fun downloadBackup(filename: String): ApiResult<ByteArray> =
        client.download("/data-manager/download/$filename")

    /** 202 提交响应与任务详情共用一个解析（只有 `taskId` / `id` 两种键名）。 */
    private fun taskOf(data: JsonElement?): DbExportTask {
        val o = data as? JsonObject ?: return DbExportTask()
        val result = o.get("result")?.takeIf { !it.isJsonNull } as? JsonObject
        return DbExportTask(
            id = str(o, "id").ifEmpty { str(o, "taskId") },
            type = str(o, "type"),
            status = str(o, "status"),
            tableName = str(o, "tableName"),
            tableSize = lng(o, "tableSize"),
            progress = lng(o, "progress").toInt(),
            error = str(o, "error"),
            resultFilename = result?.let { str(it, "filename") } ?: "",
            resultFull = result?.let { str(it, "full") } ?: "",
            resultSchemaOnly = result?.let { str(it, "schemaOnly") } ?: "",
            resultSize = result?.let { lng(it, "size") } ?: 0L,
            resultDate = result?.let { str(it, "date") } ?: "",
            createdAt = lng(o, "createdAt"),
            startedAt = lng(o, "startedAt"),
            completedAt = lng(o, "completedAt"),
        )
    }

    private fun backupOf(o: JsonObject) = DbBackupFile(
        filename = str(o, "filename"),
        size = lng(o, "size"),
        createdAt = str(o, "createdAt"),
        type = str(o, "type"),
    )
}

// ---- 本域宽松解析助手 ----
private fun str(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""

private fun lng(o: JsonObject, k: String) = runCatching {
    if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asLong else 0L
}.getOrDefault(0L)

private fun arr(data: JsonElement?): JsonArray = when (data) {
    is JsonArray -> data
    is JsonObject -> data.getAsJsonArray("list") ?: JsonArray()
    else -> JsonArray()
}
