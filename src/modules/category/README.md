# 分类管理 API

## 接口列表

### 收支分类

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取分类列表 |
| GET | `/:id` | 获取分类详情 |
| POST | `/` | 创建分类 |
| PUT | `/:id` | 更新分类 |
| DELETE | `/:id` | 删除分类 |

### 银行分类

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/bank/list` | 获取银行列表 |
| GET | `/bank/:id` | 获取银行详情 |
| POST | `/bank/` | 创建银行 |
| PUT | `/bank/:id` | 更新银行 |
| DELETE | `/bank/:id` | 删除银行 |

---

## ========== 收支分类 ==========

### 1. 获取分类列表

**路径**：`GET /api/v1/category/`

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | ❌ | 类型：income/expense/asset/fixed |

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "UUID",
      "name": "餐饮",
      "type": "expense",
      "icon_url": "...",
      "remark": "餐饮消费",
      "sort": 1
    }
  ]
}
```

---

### 2. 获取分类详情

**路径**：`GET /api/v1/category/:id`

---

### 3. 创建分类

**路径**：`POST /api/v1/category/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 分类名称 |
| `type` | string | ✅ | 类型：income/expense/asset/fixed |
| `icon_url` | string | ❌ | 图标URL |
| `remark` | string | ❌ | 说明 |
| `sort` | number | ❌ | 排序，默认99 |

---

### 4. 更新分类

**路径**：`PUT /api/v1/category/:id`

**请求体**：同创建，字段可选

---

### 5. 删除分类

**路径**：`DELETE /api/v1/category/:id`

**说明**：软删除

---

## ========== 银行分类 ==========

### 银行接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/category/bank/list` | 获取银行列表 |
| GET | `/category/bank/:id` | 获取银行详情 |
| POST | `/category/bank/` | 创建银行 |
| PUT | `/category/bank/:id` | 更新银行 |
| DELETE | `/category/bank/:id` | 删除银行 |

### 创建银行请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 银行名称 |
| `icon_url` | string | ❌ | 图标URL |

---

## 字段说明

### bus_category 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `name` | varchar(50) | 分类名称 |
| `type` | varchar(20) | 类型：income/expense/asset/fixed |
| `icon_url` | varchar(255) | 图标URL |
| `remark` | varchar(50) | 说明 |
| `sort` | int | 排序 |
| `is_deleted` | tinyint | 是否删除 |
