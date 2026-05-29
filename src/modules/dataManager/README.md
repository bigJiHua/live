# 数据管理 API

## 接口列表

### 数据查询与导出

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tables` | 获取所有表名列表 |
| GET | `/status` | 获取所有表状态（行数等） |
| GET | `/structure/:tableName` | 获取指定表结构 |
| GET | `/data/:tableName` | 分页获取指定表数据 |
| GET | `/export/table/:tableName` | 导出单表（SQL格式） |
| GET | `/export/full` | 全库备份导出 |

### 数据导入

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/validate` | 校验导入数据格式 |
| POST | `/import` | 导入JSON数据 |
| POST | `/import/sql` | 上传SQL/ZIP文件导入 |

### 备份管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/backups` | 获取备份列表 |
| POST | `/backups/system` | 创建系统备份 |
| GET | `/backups/system` | 获取系统备份列表 |
| DELETE | `/backups/:filename` | 删除备份文件 |
| GET | `/download/:filename` | 下载备份文件 |

### 导出任务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/export/task/:taskId` | 查询导出任务状态 |
| DELETE | `/export/task/:taskId` | 取消导出任务 |

---

## 接口详情

### 1. 获取所有表名

**路径**：`GET /api/v1/data-manager/tables`

**认证**：需要 JWT

**响应**：
```json
{
  "status": 200,
  "data": ["user_info", "account", "card_base", "..."]
}
```

---

### 2. 获取表状态

**路径**：`GET /api/v1/data-manager/status`

**认证**：需要 JWT

**响应**：
```json
{
  "status": 200,
  "data": [
    { "table_name": "account", "row_count": 1500, "data_size": "1.2MB" }
  ]
}
```

---

### 3. 获取表结构

**路径**：`GET /api/v1/data-manager/structure/:tableName`

**认证**：需要 JWT

**参数**：`tableName` - 表名（路径参数）

---

### 4. 分页获取表数据

**路径**：`GET /api/v1/data-manager/data/:tableName`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | number | ❌ | 页码，默认1 |
| `pageSize` | number | ❌ | 每页数量，默认100 |

---

### 5. 导出单表

**路径**：`GET /api/v1/data-manager/export/table/:tableName`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `includeData` | string | ❌ | 是否包含数据，默认 `"true"` |

**说明**：大表自动转为异步后台导出，返回 `status: 202` 和 `taskId`

---

### 6. 全库备份导出

**路径**：`GET /api/v1/data-manager/export/full`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `format` | string | ❌ | 导出格式：`sql`（默认）/ `zip` |
| `includeData` | string | ❌ | 是否包含数据，默认 `"true"` |

**说明**：大数据库自动转为后台异步导出

---

### 7. 校验导入数据

**路径**：`POST /api/v1/data-manager/validate`

**认证**：需要 JWT

**请求体** `req.body`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `tableName` | string | ✅ | 目标表名 |
| `data` | array | ✅ | 数据数组，每项为对象 |

**响应**：
```json
{
  "status": 200,
  "data": {
    "tableName": "account",
    "tableColumns": ["id", "user_id", "..."],
    "totalRows": 10,
    "validRows": 8,
    "invalidRows": 2,
    "errors": [
      { "row": 3, "errors": ["缺少字段: amount"] }
    ]
  },
  "canImport": false
}
```

---

### 8. 导入JSON数据

**路径**：`POST /api/v1/data-manager/import`

**认证**：需要 JWT

**请求体** `req.body`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `tableName` | string | ✅ | 目标表名 |
| `data` | array | ✅ | 数据数组 |
| `forceClear` | boolean | ❌ | 是否先清空表再导入，默认 false |

**说明**：
- 导入前自动备份目标表
- 仅导入目标表存在的字段，忽略多余字段
- `forceClear=true` 时会先 TRUNCATE

---

### 9. 上传SQL/ZIP导入

**路径**：`POST /api/v1/data-manager/import/sql`

**认证**：需要 JWT

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | ✅ | `.sql` 或 `.zip` 文件，最大100MB |

**说明**：
- ZIP 中包含的 `.sql` 文件会被自动提取
- 导入前执行全库备份
- 逐句执行 SQL，失败不中断，返回成功/失败统计

---

### 10. 备份管理

**获取备份列表**：`GET /api/v1/data-manager/backups`

**创建系统备份**：`POST /api/v1/data-manager/backups/system`

**获取系统备份列表**：`GET /api/v1/data-manager/backups/system`

**删除备份**：`DELETE /api/v1/data-manager/backups/:filename`（需要 PIN 验证）

**下载备份**：`GET /api/v1/data-manager/download/:filename`

---

### 11. 导出任务管理

**查询任务状态**：`GET /api/v1/data-manager/export/task/:taskId`

**取消任务**：`DELETE /api/v1/data-manager/export/task/:taskId`（需要 PIN 验证）

---

## 安全说明

| 操作 | 认证要求 |
|------|----------|
| 查询/导出 | JWT |
| 导入数据 | JWT |
| 删除备份 | JWT + PIN |
| 取消导出任务 | JWT + PIN |

---

## 更新日志

### 5月28日
- 补充完整 API 文档
