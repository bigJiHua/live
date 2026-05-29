# 流水详情/日历 API（桌面端）

> **5月28日 新增模块**：提供按月汇总的流水日历视图和单条流水详情查询。

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/calendar` | 获取月度流水日历 |
| GET | `/:id` | 获取单条流水详情 |

---

## 接口详情

### 1. 获取月度流水日历

**路径**：`GET /api/v1/flow/calendar`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `year` | string | ✅ | 年份，如 `2026` |
| `month` | string | ✅ | 月份，如 `05` |

**响应**：
```json
{
  "status": 200,
  "data": {
    "2026-05-01": { "income": 0, "expense": 150.00 },
    "2026-05-02": { "income": 5000.00, "expense": 0 },
    "2026-05-03": { "income": 0, "expense": 0 },
    "...": "..."
  }
}
```

**说明**：
- 返回当月每一天（有流水的日期）的收入和支出汇总
- `income`：当日 `direction=1` 的金额合计
- `expense`：当日 `direction=0` 的金额合计
- 无流水的日期不会出现在结果中

---

### 2. 获取单条流水详情

**路径**：`GET /api/v1/flow/:id`

**认证**：需要 JWT

**参数**：`id` - 流水记录ID（路径参数）

**响应**：
```json
{
  "status": 200,
  "data": {
    "id": "xxx",
    "user_id": "xxx",
    "card_id": "xxx",
    "category_id": "xxx",
    "direction": 0,
    "amount": 150.00,
    "trans_date": "2026-05-01",
    "remark": "午餐",
    "pay_type": "...",
    "pay_method": "...",
    "create_time": "...",
    "update_time": "..."
  }
}
```

**说明**：返回 `account` 表的完整记录，仅限当前用户。

---

## 数据来源

| 接口 | 来源表 | 查询说明 |
|------|--------|----------|
| 日历 | `account` | `GROUP BY DATE(trans_date)`，按月筛选 |
| 详情 | `account` | 按 `id` 精确查询 |
