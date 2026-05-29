# 仪表盘 API（桌面端）

> **5月28日 新增模块**：为桌面端提供首页数据聚合视图，包含 v1（精简版）和 v2（完整版）两个版本。

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 仪表盘 v1（精简版） |
| GET | `/v2` | 仪表盘 v2（完整版，含预算+待办） |

---

## 接口详情

### 1. 仪表盘 v1（精简版）

**路径**：`GET /api/v1/dashboard/`

**认证**：需要 JWT

**响应**：
```json
{
  "status": 200,
  "data": {
    "totalBalance": 50000.00,
    "todayIncome": 500.00,
    "todayExpense": 200.00,
    "monthIncome": 10000.00,
    "monthExpense": 5000.00,
    "monthlySurplus": 5000.00,
    "debitCardCount": 2,
    "creditCardCount": 3,
    "creditToPay": 8000.00,
    "recentRecords": [
      {
        "id": "xxx",
        "direction": 0,
        "amount": 100.00,
        "category_name": "餐饮",
        "trans_date": "2026-05-28"
      }
    ]
  }
}
```

**返回字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalBalance` | number | 账户总余额 |
| `todayIncome` | number | 今日收入合计 |
| `todayExpense` | number | 今日支出合计 |
| `monthIncome` | number | 本月收入合计 |
| `monthExpense` | number | 本月支出合计 |
| `monthlySurplus` | number | 本月结余（收入-支出） |
| `debitCardCount` | number | 借记卡数量 |
| `creditCardCount` | number | 信用卡数量 |
| `creditToPay` | number | 信用卡待还总额 |
| `recentRecords` | array | 最近10条收支流水 |

---

### 2. 仪表盘 v2（完整版）

**路径**：`GET /api/v1/dashboard/v2`

**认证**：需要 JWT

**响应**：在 v1 基础上新增：
```json
{
  "status": 200,
  "data": {
    "...": "...（v1 所有字段）",
    "budgets": [
      {
        "id": "xxx",
        "title": "月度餐饮预算",
        "budget_type": "吃",
        "budget_amount": 2000.00,
        "used_amount": 500.00
      }
    ],
    "todos": [
      {
        "id": "xxx",
        "content": "交房租",
        "happen_date": "2026-05-31",
        "status": "待完成",
        "priority": 2
      }
    ]
  }
}
```

**新增字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `budgets` | array | 所有预算列表（按创建时间倒序） |
| `todos` | array | 待完成日程列表（按日期升序，最多10条） |

---

## 数据来源

| 数据项 | 来源表 | 查询说明 |
|--------|--------|----------|
| 总余额 | `account_balance` | `SUM(balance)` |
| 今日收支 | `account` | `trans_date = TODAY` |
| 本月收支 | `account` | `trans_date LIKE 'YYYY-MM%'` |
| 卡片统计 | `card_base` | 按 `card_type` 分组计数 |
| 待还金额 | `card_bill` | `SUM(need_repay) WHERE need_repay > 0` |
| 最近流水 | `account` + `bus_category` | 按日期倒序，LIMIT 10 |
| 预算列表 | `budget` | 全部有效预算 |
| 待办列表 | `todo` | `status = '待完成'`, LIMIT 10 |
