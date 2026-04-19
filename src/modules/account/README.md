# 账务收支 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取收支列表 |
| GET | `/list` | 根据卡片获取流水 |
| GET | `/:id` | 获取单条收支详情 |
| POST | `/` | 创建收支记录 |
| PATCH | `/:id/remark` | 修改备注 |
| POST | `/:id/reverse/debit` | 冲正借记卡流水 |
| POST | `/:id/reverse/credit-expense` | 冲正信用卡消费 |
| POST | `/:id/reverse/credit-repay` | 冲正信用卡还款 |
| GET | `/stats/month` | 获取本月收支统计 |
| GET | `/stats/all` | 获取全量统计 |

---

## 接口详情

### 1. 获取收支列表

**路径**：`GET /api/v1/account/`

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | number | ❌ | 页码，默认1 |
| `limit` | number | ❌ | 每页数量，默认20 |
| `direction` | number | ❌ | 收支方向：0支出/1收入 |
| `categoryId` | string | ❌ | 分类ID |
| `payMethod` | string | ❌ | 支付方式 |
| `startDate` | string | ❌ | 开始日期 `yyyy-MM-dd` |
| `endDate` | string | ❌ | 结束日期 `yyyy-MM-dd` |

**响应**：
```json
{
  "status": 200,
  "message": "获取成功",
  "data": {
    "list": [...],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 100,
      "totalPages": 5
    }
  }
}
```

---

### 2. 根据卡片获取流水

**路径**：`GET /api/v1/account/list`

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cardId` | string | ✅ | 卡片ID |
| `page` | number | ❌ | 页码，默认1 |
| `limit` | number | ❌ | 每页数量，默认20 |
| `direction` | number | ❌ | 收支方向 |
| `startDate` | string | ❌ | 开始日期 |
| `endDate` | string | ❌ | 结束日期 |

---

### 3. 获取单条收支详情

**路径**：`GET /api/v1/account/:id`

**参数**：`id` - 记录ID（路径参数）

---

### 4. 创建收支记录

**路径**：`POST /api/v1/account/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `direction` | number | ✅ | 收支方向：0支出/1收入 |
| `categoryId` | string | ✅ | 分类ID |
| `payType` | string | ✅ | 支出类型 |
| `payMethod` | string | ✅ | 支付方式 |
| `amount` | number | ✅ | 金额 |
| `transDate` | string | ✅ | 收支日期 `yyyy-MM-dd` |
| `cardId` | string | ✅ | 关联卡片ID |
| `currency` | string | ❌ | 币种，默认CNY |
| `exchangeRate` | number | ❌ | 汇率，默认1 |
| `remark` | string | ❌ | 备注 |

---

### 5. 修改备注

**路径**：`PATCH /api/v1/account/:id/remark`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `remark` | string | ✅ | 新备注 |

---

### 6. 冲正流水

#### 借记卡冲正
**路径**：`POST /api/v1/account/:id/reverse/debit`

#### 信用卡消费冲正
**路径**：`POST /api/v1/account/:id/reverse/credit-expense`

#### 信用卡还款冲正
**路径**：`POST /api/v1/account/:id/reverse/credit-repay`

---

### 7. 本月收支统计

**路径**：`GET /api/v1/account/stats/month`

**响应**：
```json
{
  "status": 200,
  "data": {
    "income": 10000,
    "expense": 5000,
    "balance": 5000
  }
}
```

---

### 8. 全量统计

**路径**：`GET /api/v1/account/stats/all`

**响应**：
```json
{
  "status": 200,
  "data": {
    "totalAssets": 50000,
    "totalDebt": 10000,
    "cardCount": 3,
    "cards": [...]
  }
}
```

---

## 字段说明

### account 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `card_id` | varchar(32) | 关联卡片ID |
| `reversed_id` | varchar(32) | 冲正流水ID |
| `category_id` | varchar(50) | 分类ID |
| `direction` | tinyint | 方向：1收入/0支出 |
| `pay_type` | varchar(50) | 支出类型 |
| `pay_method` | varchar(50) | 支付方式 |
| `account_type` | varchar(20) | 账户类型 |
| `amount` | decimal(12,2) | 金额 |
| `currency` | varchar(10) | 币种 |
| `exchange_rate` | decimal(10,4) | 汇率 |
| `trans_date` | varchar(20) | 收支日期 |
| `remark` | varchar(255) | 备注 |
| `create_time` | varchar(20) | 创建时间 |
| `update_time` | varchar(20) | 修改时间 |
| `is_deleted` | tinyint | 是否删除 |
