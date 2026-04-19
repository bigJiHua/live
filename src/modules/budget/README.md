# 预算管理 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 获取所有预算 |
| GET | `/stats` | 获取预算统计 |
| GET | `/:id` | 获取单个预算详情 |
| POST | `/` | 创建预算 |
| PUT | `/:id` | 更新预算 |
| DELETE | `/:id` | 删除预算（硬删除） |

---

## 接口详情

### 1. 获取所有预算

**路径**：`GET /api/v1/budget/list`

**参数**：无

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "UUID",
      "user_id": "用户ID",
      "title": "五一出行预算",
      "route": "深圳-广州",
      "budget_type": "行",
      "budget_amount": "3000.00",
      "used_amount": "500.00",
      "budget_details": {},
      "cycle": "月",
      "plan_date": "2026-05-01",
      "is_over_budget": 0,
      "is_excute": 0,
      "create_time": "2026-04-19",
      "update_time": "2026-04-19"
    }
  ]
}
```

---

### 2. 获取预算统计

**路径**：`GET /api/v1/budget/stats`

**参数**：无

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "budget_type": "吃",
      "total_budget": 2000,
      "total_used": 500,
      "remaining": 1500,
      "usage_rate": 25
    }
  ]
}
```

---

### 3. 获取单个预算

**路径**：`GET /api/v1/budget/:id`

**参数**：`id` - 预算UUID（路径参数）

**响应**：同 list 单条数据

---

### 4. 创建预算

**路径**：`POST /api/v1/budget/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | ✅ | 预算标题，最大50字符 |
| `budget_type` | string | ✅ | 预算类型：`吃` / `买` / `行` |
| `route` | string | ⚠️ | 路线（`行`类型时必填） |
| `budget_amount` | number | ✅ | 预算金额，必须 > 0 |
| `cycle` | string | ✅ | 周期：`月` / `季` / `年` |
| `plan_date` | string | ✅ | 预计日期，格式 `yyyy-MM-dd` |
| `budget_details` | object | ❌ | 明细JSON |

**示例**：
```json
{
  "title": "五一出行预算",
  "budget_type": "行",
  "route": "深圳-广州",
  "budget_amount": 3000,
  "cycle": "月",
  "plan_date": "2026-05-01",
  "budget_details": {
    "purpose": "五一假期出行"
  }
}
```

---

### 5. 更新预算

**路径**：`PUT /api/v1/budget/:id`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | ❌ | 预算标题 |
| `route` | string | ❌ | 路线 |
| `budget_type` | string | ❌ | 预算类型 |
| `budget_amount` | number | ❌ | 预算金额 |
| `used_amount` | number | ❌ | 已使用金额 |
| `budget_details` | object | ❌ | 明细JSON |
| `cycle` | string | ❌ | 周期 |
| `plan_date` | string | ❌ | 预计日期 |
| `is_over_budget` | number | ❌ | 是否超支（0/1） |
| `is_excute` | number | ❌ | 是否执行（0/1） |

---

### 6. 删除预算

**路径**：`DELETE /api/v1/budget/:id`

**参数**：`id` - 预算UUID（路径参数）

**说明**：硬删除，不可恢复

**响应**：
```json
{
  "status": 200,
  "message": "删除成功"
}
```

---

## 字段说明

| 字段 | 数据库类型 | 说明 |
|------|-----------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `title` | varchar(50) | 预算标题 |
| `route` | varchar(50) | 路线（出行计划时必填） |
| `budget_type` | varchar(20) | 类型：吃/买/行 |
| `budget_amount` | decimal(12,2) | 预算金额 |
| `used_amount` | decimal(12,2) | 已使用金额 |
| `budget_details` | varchar(512) | 明细JSON |
| `cycle` | varchar(20) | 周期：月/季/年 |
| `plan_date` | varchar(20) | 预计日期 |
| `is_over_budget` | tinyint | 是否超支（0/1） |
| `is_excute` | int | 是否执行（0/1） |
| `create_time` | varchar(20) | 创建时间 |
| `update_time` | varchar(20) | 修改时间 |
| `is_deleted` | tinyint | 是否删除（0/1） |

---

## Joi 验证规则

### 创建规则（createSchema）

```javascript
{
  title:          '必填, 最大50字符',
  budget_type:    '必填, 吃/买/行',
  budget_amount:  '必填, 正数',
  cycle:          '必填, 月/季/年',
  plan_date:      '必填, yyyy-MM-dd',
  route:          '选填',
  budget_details:  '选填'
}
```

### 更新规则（updateSchema）

所有字段可选，支持部分更新。
