# 文件资源管理 API（桌面端）

> **5月28日 新增模块**：为桌面端提供已上传文件的列表查询和删除管理功能。

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取文件列表（分页） |
| DELETE | `/:id` | 删除文件 |

---

## 接口详情

### 1. 获取文件列表

**路径**：`GET /api/v1/resource/`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | number | ❌ | 页码，默认1 |
| `pageSize` | number | ❌ | 每页数量，默认20 |

**响应**：
```json
{
  "status": 200,
  "data": {
    "list": [
      {
        "id": "xxx",
        "fileName": "photo_2026.jpg",
        "filePath": "/uploads/xxx.webp",
        "fileSize": "204800",
        "fileExt": "webp",
        "createTime": "2026-05-28 10:00:00"
      }
    ],
    "total": 50,
    "page": 1,
    "pageSize": 20
  }
}
```

**返回字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 附件ID |
| `fileName` | string | 原始文件名 |
| `filePath` | string | 存储路径 |
| `fileSize` | string | 文件大小（字节） |
| `fileExt` | string | 文件扩展名 |
| `createTime` | string | 上传时间 |
| `total` | number | 文件总数 |
| `page` | number | 当前页码 |
| `pageSize` | number | 每页数量 |

---

### 2. 删除文件

**路径**：`DELETE /api/v1/resource/:id`

**认证**：需要 JWT + PIN 验证（pinLockGuard）

**说明**：硬删除（`DELETE FROM attachment`），不可恢复。

**响应**：
```json
{
  "status": 200,
  "message": "删除成功"
}
```

---

## 数据来源

数据读取自 `attachment` 表（即 `sys_attachment`），按 `create_time` 倒序排列。

### sys_attachment 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `user_id` | varchar(255) | 用户ID |
| `file_name` | varchar(255) | 原始文件名 |
| `file_path` | varchar(255) | 存储路径 |
| `file_size` | varchar(50) | 文件大小 |
| `file_type` | varchar(50) | MIME类型 |
| `create_time` | varchar(20) | 上传时间 |
