# 资产快照与登记 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取资产列表 |
| GET | `/:id` | 获取资产详情 |
| POST | `/` | 创建资产登记 |
| PUT | `/:id` | 更新资产登记 |
| DELETE | `/:id` | 删除资产登记 |

---

## 接口详情

### 1. 获取资产列表

**路径**：`GET /api/v1/asset/`

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [...]
}
```

---

### 2. 获取资产详情

**路径**：`GET /api/v1/asset/:id`

---

### 3. 创建资产登记

**路径**：`POST /api/v1/asset/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 资产名称 |
| `amount` | number | ✅ | 资产数额 |
| `type` | string | ✅ | 类型：资产/欠款 |
| `create_time` | string | ✅ | 登记日期 `yyyy-MM-dd` |

---

### 4. 更新资产登记

**路径**：`PUT /api/v1/asset/:id`

**请求体**：同创建，字段可选

---

### 5. 删除资产登记

**路径**：`DELETE /api/v1/asset/:id`

**说明**：软删除

---

## 字段说明

### asset 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | int | 用户ID |
| `name` | varchar(100) | 项目名称 |
| `amount` | decimal(12,2) | 数额 |
| `type` | varchar(20) | 类型：资产/欠款 |
| `create_time` | varchar(20) | 登记日期 |
| `update_time` | varchar(20) | 修改日期 |
| `is_deleted` | tinyint | 是否删除 |

### asset_register 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `total_asset` | decimal(14,2) | 各类资产合计 |
| `credit_debt` | decimal(14,2) | 信用卡总欠款 |
| `total_balance` | decimal(14,2) | 最终总资产余额 |
| `asset_details` | json | 资产明细 |
| `register_date` | varchar(10) | 登记日期 |
| `register_time` | varchar(20) | 登记时间 |
| `is_deleted` | tinyint | 是否删除 |

### asset_snapshot 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `total_asset` | decimal(14,2) | 各类资产合计 |
| `credit_debt` | decimal(14,2) | 信用卡总欠款 |
| `total_balance` | decimal(14,2) | 最终总资产余额 |
| `record_date` | varchar(10) | 记录日期 |
| `record_time` | varchar(20) | 记录时间 |
| `create_time` | varchar(20) | 创建时间 |
| `is_deleted` | tinyint | 是否删除 |
