# 理财投资管理 API

## 接口列表

### 基金管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 获取基金列表 |
| GET | `/:id` | 获取基金详情 |
| POST | `/` | 创建基金 |
| PUT | `/:id` | 更新基金 |
| DELETE | `/:id` | 删除基金 |

### 净值历史

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/:id/history` | 获取基金净值历史 |
| GET | `/history/all` | 获取全部基金净值历史 |
| POST | `/:id/history` | 添加净值记录 |
| PUT | `/history/:id` | 更新净值记录 |
| DELETE | `/history/:id` | 删除净值记录 |

---

## 接口详情

### 1. 获取基金列表

**路径**：`GET /api/v1/fund/list`

**认证**：需要 JWT

**响应**：
```json
{
  "status": 200,
  "message": "获取成功",
  "data": {
    "list": [
      {
        "id": "xxx",
        "fund_name": "沪深300ETF",
        "share": "1000.00",
        "fund_account": "招商银行",
        "trade_account": "天天基金",
        "sell_org": "华夏基金",
        "fund_company": "华夏基金",
        "buy_date": "2026-01-15",
        "net_value": "500.0000",
        "invest": "10200.00",
        "market_val": "10700.00",
        "rate": "4.90%",
        "create_time": "..."
      }
    ]
  }
}
```

**说明**：返回聚合数据，`net_value`（累计收益）、`invest`（当前本金）、`market_val`（当前市值）、`rate`（收益率）均通过 `bus_fund_history` 汇总计算得出。

---

### 2. 获取基金详情

**路径**：`GET /api/v1/fund/:id`

**认证**：需要 JWT

**参数**：`id` - 基金ID（路径参数）

---

### 3. 创建基金

**路径**：`POST /api/v1/fund/`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fundName` | string | ✅ | 基金名称 |
| `share` | number | ✅ | 持有份额，必须 > 0 |
| `invest` | number | ✅ | 初始本金，必须 > 0 |
| `buyDate` | string | ✅ | 购入日期 `yyyy-MM-dd` |
| `fundAccount` | string | ❌ | 基金账户 |
| `tradeAccount` | string | ❌ | 交易平台 |
| `sellOrg` | string | ❌ | 销售机构 |
| `fundCompany` | string | ❌ | 基金公司，默认"未填写" |
| `netValue` | string/number | ❌ | 累计收益，默认0 |
| `marketVal` | string/number | ❌ | 初始市值，默认等于本金 |
| `rate` | string | ❌ | 收益率，默认"0.00%" |

---

### 4. 更新基金

**路径**：`PUT /api/v1/fund/:id`

**认证**：需要 JWT

**请求体** `req.body.data`：所有字段可选，支持部分更新。

可更新字段：`fundName`, `share`, `fundAccount`, `tradeAccount`, `sellOrg`, `fundCompany`, `buyDate`, `netValue`, `invest`, `marketVal`, `rate`

---

### 5. 删除基金

**路径**：`DELETE /api/v1/fund/:id`

**认证**：需要 JWT + PIN 验证（pinLockGuard）

**说明**：软删除

---

### 6. 获取净值历史

**路径**：`GET /api/v1/fund/:id/history`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `limit` | number | ❌ | 返回条数限制 |
| `startDate` | string | ❌ | 开始日期 |
| `endDate` | string | ❌ | 结束日期 |

**响应**：
```json
{
  "status": 200,
  "data": {
    "list": [
      {
        "id": "xxx",
        "fund_id": "xxx",
        "net_value": "100.00",
        "market_val": "500.00",
        "record_date": "2026-05-01",
        "create_time": "..."
      }
    ],
    "range": {
      "startDate": "2026-01-01",
      "endDate": "2026-05-28",
      "profitBefore": 500.00,
      "capitalBefore": 0
    }
  }
}
```

---

### 7. 获取全部净值历史

**路径**：`GET /api/v1/fund/history/all`

**认证**：需要 JWT

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `limit` | number | ❌ | 默认180条 |

---

### 8. 添加净值记录

**路径**：`POST /api/v1/fund/:id/history`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `recordDate` | string | ✅ | 记录日期 `yyyy-MM-dd` |
| `netValue` | number | ⚠️ | 今日收益（与 marketVal 至少填一项） |
| `marketVal` | number | ⚠️ | 增持本金（与 netValue 至少填一项） |

**说明**：
- 同一基金同一日期仅允许一条记录
- 写入后自动同步更新基金主表的累计数据

---

### 9. 更新净值记录

**路径**：`PUT /api/v1/fund/history/:id`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `netValue` | number | ❌ | 今日收益 |
| `marketVal` | number | ❌ | 增持本金 |

---

### 10. 删除净值记录

**路径**：`DELETE /api/v1/fund/history/:id`

**认证**：需要 JWT + PIN 验证（pinLockGuard）

**说明**：硬删除，删除后自动同步基金主表数据

---

## 计算规则

### 聚合公式

| 指标 | 公式 |
|------|------|
| 当前本金 | `invest + SUM(market_val)`（净值历史中的累计增持本金） |
| 当前市值 | 当前本金 + 累计收益 |
| 累计收益 | `SUM(net_value)`（净值历史中累计收益汇总） |
| 收益率 | `(累计收益 / 当前本金) × 100%` |

### 遗留数据兼容

如果历史记录中 `market_val = invest` 且 `ABS(net_value) > ABS(invest) × 0.5`，识别为旧版快照数据，自动按新规则解析。

---

## 字段说明

### fund 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `fund_name` | varchar(100) | 基金名称 |
| `share` | decimal(16,4) | 持有份额 |
| `fund_account` | varchar(50) | 基金账户 |
| `trade_account` | varchar(50) | 交易平台 |
| `sell_org` | varchar(50) | 销售机构 |
| `fund_company` | varchar(50) | 基金公司 |
| `buy_date` | varchar(20) | 购入日期 |
| `net_value` | decimal(16,4) | 累计收益 |
| `invest` | decimal(12,2) | 初始本金 |
| `market_val` | decimal(12,2) | 当前市值 |
| `rate` | varchar(10) | 收益率 |
| `create_time` | varchar(20) | 创建时间 |
| `is_deleted` | tinyint | 是否删除 |

### bus_fund_history 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `fund_id` | varchar(50) | 关联基金ID |
| `net_value` | decimal(16,4) | 当日收益 |
| `market_val` | decimal(12,2) | 当日增持本金 |
| `record_date` | varchar(20) | 记录日期 |
| `create_time` | varchar(20) | 创建时间 |

---

## 更新日志

### 5月28日
- 补充完整 API 文档
