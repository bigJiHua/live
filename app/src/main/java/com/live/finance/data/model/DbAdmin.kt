package com.live.finance.data.model

import com.google.gson.JsonObject

/**
 * 数据管理（`/data-manager/…`，**管理员专属**）用到的模型。
 * ⚠ 注释里别写带 `斜杠+星号` 的路径通配（Kotlin 块注释**可嵌套**，会把后面的代码全吞进注释）。
 *
 * 字段名严格对齐后端 `api/src/modules/dataManager/{model,controller}`（README 已过时，以代码为准）：
 * - `GET /status` → `[{name, engine, rowCount, dataLength, indexLength, createTime, updateTime, collation, comment}]`
 *   （**camelCase**，不是 README 里的 `table_name/row_count`）
 * - `GET /structure/:t` → `[{field, type, null, key, default, extra}]`（全小写）
 * - `GET /data/:t` → `{data: [行…], total, page, pageSize}`
 */
data class DbTableStatus(
    val name: String = "",
    val engine: String = "",
    val rowCount: Long = 0L,
    val dataLength: Long = 0L,     // 字节
    val indexLength: Long = 0L,
    val createTime: String = "",
    val updateTime: String = "",
    val collation: String = "",
    val comment: String = "",
)

/** 表结构一列（`DESCRIBE` 结果）。 */
data class DbColumn(
    val field: String = "",
    val type: String = "",
    val nullable: String = "",     // 后端键名就叫 `null`
    val key: String = "",
    val defaultValue: String = "",
    val extra: String = "",
)

/** 分页数据（`GET /data/:t?page&pageSize`）。 */
data class DbTablePage(
    val rows: List<JsonObject> = emptyList(),
    val total: Long = 0L,
    val page: Int = 1,
    val pageSize: Int = 100,
) {
    val totalPages: Int get() = if (pageSize <= 0) 1 else ((total + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
}

/** 导出/备份任务（`GET /export/task/:taskId` 与各 202 提交响应）。 */
data class DbExportTask(
    val id: String = "",
    val type: String = "",           // export_table / export_full_database / system_backup
    val status: String = "",         // pending / running / completed / failed / cancelled
    val tableName: String = "",
    val tableSize: Long = 0L,
    val progress: Int = 0,
    val error: String = "",
    val resultFilename: String = "",
    val resultFull: String = "",
    val resultSchemaOnly: String = "",
    val resultSize: Long = 0L,
    val resultDate: String = "",
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
) {
    val finished: Boolean get() = status == "completed" || status == "failed" || status == "cancelled"
}

/** `POST /validate` 的响应（`canImport` 在**响应顶层**，不在 data 内）。 */
data class DbValidateResult(
    val tableName: String = "",
    val tableColumns: List<String> = emptyList(),
    val totalRows: Int = 0,
    val validRows: Int = 0,
    val invalidRows: Int = 0,
    val errors: List<DbValidateError> = emptyList(),
    val canImport: Boolean = false,
)

data class DbValidateError(val row: Int = 0, val errors: List<String> = emptyList())

/** `POST /import` 的响应。 */
data class DbImportResult(
    val importedRows: Int = 0,
    val totalRows: Int = 0,
    val backupFile: String = "",
)

/** `POST /import/sql` 的响应（status 可能是 207 部分失败）。 */
data class DbSqlImportResult(
    val executedCount: Int = 0,
    val errorCount: Int = 0,
    val errors: List<DbSqlImportError> = emptyList(),
    val backupFile: String = "",
    val backupTimestamp: String = "",
) {
    val allOk: Boolean get() = errorCount == 0
}

data class DbSqlImportError(val index: Int = 0, val sql: String = "", val message: String = "")

/**
 * 备份文件（`GET /backups` 行）。
 * 后端字段：`{filename, filepath, size, createdAt(ISO), type: 'sql'|'zip'}`，按 `createdAt` **倒序**。
 */
data class DbBackupFile(
    val filename: String = "",
    val size: Long = 0L,
    val createdAt: String = "",
    val type: String = "",
) {
    val isZip: Boolean get() = type == "zip" || filename.endsWith(".zip")
}

/**
 * 系统备份按日期分组（`GET /backups/system`）：`[{date: 'YYYY-MM-DD', files: [...]}]`，
 * 日期**倒序**、组内文件按 `createdAt` 倒序；组内 `type` 为 `full` / `schema-only`。
 */
data class DbSystemBackupGroup(
    val date: String = "",
    val files: List<DbBackupFile> = emptyList(),
)
