# 银行卡管理 API

## 业务规则

### 账单周期计算规则

- **账单周期**：账单日(bill_day)次日 ~ 次月账单日前一天
- **账单月归属**：
  - 消费日期 **<** 账单日 → 当月账单
  - 消费日期 **>** 账单日 → 下月账单（账单日当天仍属当月）
- **还款日**：账单月的下一月 repayDay
- **逾期判定**：当前日期 > repay_date 且 need_repay > 0

### 举例说明

假设账单日=12号，还款日=次月6号：

| 消费日期 | 归属账单月 | 出账日 | 还款日 |
|---------|-----------|--------|--------|
| 4月1日 ~ 4月12日 | 4月 | 4月12日 | 5月6日 |
| 4月13日 ~ 4月30日 | 5月 | 5月12日 | 6月6日 |

## 接口列表

### 卡片管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取卡片列表 |
| GET | `/:id` | 获取卡片详情 |
| POST | `/` | 创建卡片 |
| PUT | `/:id` | 更新卡片 |
| DELETE | `/:id` | 删除卡片 |

### 卡片账单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/bill/` | 获取账单列表 |
| GET | `/bill/:id` | 获取账单详情 |
| GET | `/bill/card/:cardId/latest` | 获取卡片最新账单 |
| POST | `/bill/card/:cardId/rebuild` | 重建账单（从流水重新计算） |
| POST | `/bill/` | 创建账单 |
| PUT | `/bill/:id` | 更新账单 |
| DELETE | `/bill/:id` | 删除账单 |

### 卡片还款

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/repay/list` | 获取还款列表 |
| GET | `/repay/:id` | 获取还款详情 |
| POST | `/repay` | 创建还款记录 |
| DELETE | `/repay/:id` | 删除还款记录 |

---

## ========== 卡片管理 ==========

### 1. 获取卡片列表

**路径**：`GET /api/v1/card/`

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [...]
}
```

---

### 2. 获取卡片详情

**路径**：`GET /api/v1/card/:id`

---

### 3. 创建卡片

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `bank_id` | string | ✅ | 银行ID |
| `card_type` | string | ✅ | 卡类型 |
| `card_level` | string | ❌ | 卡等级，默认普卡 |
| `last4_no` | string | ✅ | 卡号后四位 |
| `card_bin` | string | ✅ | 卡BIN |
| `credit_limit` | number | ❌ | 信用额度（信用卡） |
| `alias` | string | ❌ | 卡别名 |
| `open_date` | string | ✅ | 开卡日期 |
| `expire_date` | string | ✅ | 过期日期 |
| `bill_day` | number | ❌ | 账单日 |
| `repay_day` | number | ❌ | 还款日 |

---

### 4. 更新卡片

**路径**：`PUT /api/v1/card/:id`

**请求体**：同创建，字段可选

---

### 5. 删除卡片

**路径**：`DELETE /api/v1/card/:id`

**说明**：软删除

---

## ========== 卡片账单 ==========

### 账单接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/card/bill/list` | 获取账单列表 |
| GET | `/card/bill/:id` | 获取账单详情 |
| POST | `/card/bill/` | 创建账单 |
| PUT | `/card/bill/:id` | 更新账单 |
| DELETE | `/card/bill/:id` | 删除账单 |

---

## ========== 卡片还款 ==========

### 还款接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/card/repay/list` | 获取还款列表 |
| GET | `/card/repay/:id` | 获取还款详情 |
| POST | `/card/repay/` | 创建还款记录 |
| DELETE | `/card/repay/:id` | 删除还款记录 |

---

## 字段说明

### card_base 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `bank_id` | varchar(255) | 银行ID |
| `card_type` | varchar(20) | 卡类型 |
| `card_level` | varchar(20) | 卡等级 |
| `main_sub` | varchar(10) | 主副卡 |
| `card_org` | varchar(20) | 卡组织 |
| `last4_no` | varchar(10) | 卡号后四位 |
| `card_bin` | varchar(10) | 卡BIN |
| `credit_limit` | decimal(12,2) | 信用额度 |
| `alias` | varchar(50) | 卡别名 |
| `open_date` | varchar(20) | 开卡日期 |
| `expire_date` | varchar(20) | 过期日期 |
| `bill_day` | int | 账单日 |
| `repay_day` | int | 还款日 |
| `status` | varchar(20) | 状态 |
| `is_default` | tinyint | 是否默认 |
| `is_hide` | tinyint | 是否隐藏 |
| `is_deleted` | tinyint | 是否删除 |

### card_bill 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `card_id` | varchar(50) | 关联卡片ID |
| `bill_month` | varchar(7) | 账单月 YYYY-MM |
| `user_id` | varchar(50) | 用户ID |
| `credit_limit` | decimal(12,2) | 信用额度 |
| `avail_limit` | decimal(12,2) | 可用额度 |
| `used_limit` | decimal(12,2) | 已用额度 |
| `bill_amount` | decimal(12,2) | 本期账单 |
| `min_repay` | decimal(12,2) | 最低还款 |
| `need_repay` | decimal(12,2) | 待还金额 |
| `is_overdue` | tinyint | 是否逾期 |
| `overdue_days` | int | 逾期天数 |
| `repay_status` | varchar(20) | 还款状态 |
| `is_deleted` | tinyint | 是否删除 |
