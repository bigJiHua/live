# 银行分类管理 API（桌面端）

> **5月28日 新增模块**：独立银行分类管理路由，与 category 模块中的 `/bank` 路由并行运行，使用 `bus_category` 表中 `type='bank'` 的数据。

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取银行列表 |
| POST | `/` | 创建银行 |
| PUT | `/:id` | 更新银行 |
| DELETE | `/:id` | 删除银行 |

---

## 接口详情

### 1. 获取银行列表

**路径**：`GET /api/v1/bank-category/`

**认证**：需要 JWT

**响应**：
```json
{
  "status": 200,
  "data": [
    {
      "id": "BC_xxxxx",
      "user_id": "xxx",
      "name": "招商银行",
      "icon_url": "",
      "remark": "",
      "type": "bank",
      "sort": 1,
      "is_deleted": 0
    }
  ]
}
```

---

### 2. 创建银行

**路径**：`POST /api/v1/bank-category/`

**认证**：需要 JWT

**请求体** `req.body`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 银行名称 |
| `iconUrl` | string | ❌ | 图标URL |
| `remark` | string | ❌ | 备注 |
| `sort` | number | ❌ | 排序，默认99 |

---

### 3. 更新银行

**路径**：`PUT /api/v1/bank-category/:id`

**认证**：需要 JWT

**请求体** `req.body`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ❌ | 银行名称 |
| `iconUrl` | string | ❌ | 图标URL |
| `remark` | string | ❌ | 备注 |
| `sort` | number | ❌ | 排序 |

---

### 4. 删除银行

**路径**：`DELETE /api/v1/bank-category/:id`

**认证**：需要 JWT + PIN 验证（pinLockGuard）

**说明**：软删除

---

## 字段说明

使用 `bus_category` 表，`type = 'bank'` 筛选：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键，格式 `BC_xxxxx` |
| `user_id` | varchar(50) | 用户ID |
| `name` | varchar(50) | 银行名称 |
| `icon_url` | varchar(255) | 图标URL |
| `remark` | varchar(50) | 备注 |
| `type` | varchar(20) | 固定为 `bank` |
| `sort` | int | 排序 |
| `is_deleted` | tinyint | 是否删除 |
