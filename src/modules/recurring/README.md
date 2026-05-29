# 固定周期支出提醒 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 获取固定支出列表 |
| GET | `/summary` | 获取月度统计 |
| GET | `/:id` | 获取单项详情 |
| POST | `/` | 创建固定支出 |
| PUT | `/:id` | 更新固定支出 |
| PUT | `/:id/month-status` | 更新月度完成状态 |
| DELETE | `/:id` | 删除固定支出 |

---

## 接口详情

### 1. 获取固定支出列表

**路径**：`GET /api/v1/recurring/list`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `month` | string | ❌ | 月份 `YYYY-MM`，传入后附带月度状态信息 |
| `includeInactive` | string | ❌ | 是否包含已停用项，`"1"` 或 `"true"` |

**响应**（带 month 参数）：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "xxx",
      "name": "房租",
      "amount": 3000.00,
      "category_id": "xxx",
      "category_name": "住房",
      "account_id": "xxx",
      "account_name": "招行储蓄卡",
      "account_last4": "1234",
      "cycle": "month",
      "day_of_cycle": 5,
      "remind_days": 3,
      "is_active": 1,
      "remark": "",
      "month": "2026-05",
      "happen_date": "2026-05-05",
      "month_status": "pending",
      "month_amount": 3000.00,
      "is_done": false,
      "is_skipped": false,
      "is_due": true
    }
  ]
}
```

---

### 2. 获取月度统计

**路径**：`GET /api/v1/recurring/summary`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `month` | string | ✅ | 月份 `YYYY-MM` |

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": {
    "month": "2026-05",
    "total": 5,
    "done": 2,
    "pending": 3,
    "totalAmount": 8000.00,
    "doneAmount": 3500.00,
    "pendingAmount": 4500.00,
    "categoryStats": [
      {
        "category_id": "xxx",
        "category_name": "住房",
        "amount": 3000.00,
        "count": 1
      }
    ]
  }
}
```

---

### 3. 获取单项详情

**路径**：`GET /api/v1/recurring/:id`

**认证**：需要 JWT

---

### 4. 创建固定支出

**路径**：`POST /api/v1/recurring/`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 支出名称 |
| `amount` | number | ✅ | 金额，必须 > 0 |
| `cycle` | string | ❌ | 周期，当前仅支持 `month`，默认 month |
| `day_of_cycle` | number | ❌ | 周期日（几号），默认1，范围1-31 |
| `category_id` | string | ❌ | 分类ID |
| `account_id` | string | ❌ | 关联卡片ID |
| `remind_days` | number | ❌ | 提前提醒天数，默认0 |
| `remark` | string | ❌ | 备注 |
| `is_active` | number | ❌ | 是否启用：0停用/1启用，默认1 |

**说明**：
- 创建时自动计算下次应发生的日期（`next_date`）
- `month_records` 初始化为空对象 `{}`

---

### 5. 更新固定支出

**路径**：`PUT /api/v1/recurring/:id`

**认证**：需要 JWT

**请求体** `req.body.data`：所有字段可选，支持部分更新。

---

### 6. 更新月度完成状态

**路径**：`PUT /api/v1/recurring/:id/month-status`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `month` | string | ✅ | 月份 `YYYY-MM` |
| `status` | string | ✅ | 状态：`pending`/`done`/`skipped` |
| `amount` | number | ❌ | 本月实际金额（不传则用默认金额） |
| `remark` | string | ❌ | 备注 |

**说明**：
- `done` 状态会自动记录完成时间
- 月度记录存储在 `month_records` JSON 字段中

---

### 7. 删除固定支出

**路径**：`DELETE /api/v1/recurring/:id`

**认证**：需要 JWT + PIN 验证（pinLockGuard）

**说明**：软删除

---

## 数据结构

### 月度记录（month_records JSON）

```json
{
  "2026-05": {
    "status": "done",
    "amount": 3000.00,
    "remark": "",
    "remind_time": null,
    "done_time": "1748467200000"
  },
  "2026-06": {
    "status": "pending",
    "amount": 3000.00,
    "remark": "",
    "remind_time": null,
    "done_time": null
  }
}
```

### bus_recurring 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `name` | varchar(100) | 支出名称 |
| `amount` | decimal(12,2) | 金额 |
| `category_id` | varchar(50) | 分类ID |
| `account_id` | varchar(50) | 关联卡片ID |
| `cycle` | varchar(20) | 周期 |
| `day_of_cycle` | smallint | 周期日 |
| `next_date` | varchar(20) | 下次发生日期 |
| `remind_days` | int | 提前提醒天数 |
| `month_records` | json | 月度状态记录 |
| `remark` | varchar(255) | 备注 |
| `is_active` | tinyint | 是否启用 |
| `create_time` | varchar(20) | 创建时间 |
| `update_time` | varchar(20) | 更新时间 |
| `is_deleted` | tinyint | 是否删除 |

---

## 更新日志

### 5月28日
- 补充完整 API 文档
