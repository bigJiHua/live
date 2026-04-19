# 固定资产管理 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 获取所有资产（自动触发折旧巡检） |
| GET | `/:id` | 获取单个资产详情 |
| POST | `/` | 创建资产 |
| PUT | `/:id` | 更新资产 |
| DELETE | `/:id` | 删除资产 |
| POST | `/depreciate` | 手动触发折旧巡检 |

---

## 接口详情

### 1. 获取所有资产

**路径**：`GET /api/v1/fixedAsset/list`

**说明**：获取所有资产，**自动触发折旧巡检**，返回最新折旧数据

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "UUID",
      "info": "MacBook Pro 14寸",
      "tag": "电脑",
      "img_url": "/uploads/xxx.webp",
      "buy_price": "8999.00",
      "now_val": "8549.05",
      "secondhand_price": "6000.00",
      "use_years": "3",
      "use_months": 36,
      "residual_rate": "5.00",
      "residual_val": "449.95",
      "deprec_method": "straight",
      "month_deprec": "237.47",
      "total_deprec": "474.95",
      "buy_date": "2026-04-01",
      "last_deprec_date": "2026-04-19",
      "scrap_date": null,
      "create_time": "2026-04-19",
      "deprec_finished": 0,
      "status": "using",
      "months_used": 0,
      "years_used": 0,
      "depreciable_amount": "8549.05"
    }
  ]
}
```

---

### 2. 获取单个资产

**路径**：`GET /api/v1/fixedAsset/:id`

**参数**：`id` - 资产UUID（路径参数）

**响应**：同 list 单条数据

---

### 3. 创建资产

**路径**：`POST /api/v1/fixedAsset/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `info` | string | ✅ | 资产名称 |
| `tag` | string | ✅ | 品类 |
| `img_url` | string | ✅ | 图片URL |
| `buy_price` | number | ✅ | 购买价，必须 > 0 |
| `use_years` | number | ✅ | 预计使用年限（年） |
| `residual_rate` | number | ✅ | 残值率（0~100） |
| `buy_date` | string | ✅ | 购买日期 `yyyy-MM-dd` |
| `secondhand_price` | number | ❌ | 二手市场价 |
| `status` | string | ❌ | 状态，默认 `using` |

**示例**：
```json
{
  "info": "MacBook Pro 14寸",
  "tag": "电脑",
  "img_url": "/uploads/xxx.webp",
  "buy_price": 8999,
  "use_years": 3,
  "residual_rate": 5,
  "buy_date": "2026-04-19",
  "secondhand_price": 6000
}
```

---

### 4. 更新资产

**路径**：`PUT /api/v1/fixedAsset/:id`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `info` | string | ❌ | 资产名称 |
| `tag` | string | ❌ | 品类 |
| `img_url` | string | ❌ | 图片URL |
| `now_val` | number | ❌ | 当前账面价值 |
| `secondhand_price` | number | ❌ | 二手市场价 |
| `use_years` | number | ❌ | 预计使用年限 |
| `residual_rate` | number | ❌ | 残值率 |
| `scrap_date` | string | ❌ | 报废日期 |
| `status` | string | ❌ | 状态 |

---

### 5. 删除资产

**路径**：`DELETE /api/v1/fixedAsset/:id`

**参数**：`id` - 资产UUID（路径参数）

**说明**：软删除

---

### 6. 手动折旧巡检

**路径**：`POST /api/v1/fixedAsset/depreciate`

**说明**：手动触发所有资产的折旧计算

---

## 折旧计算逻辑

### 核心公式

| 字段 | 计算公式 |
|------|----------|
| `use_months` | use_years × 12 |
| `residual_val` | buy_price × residual_rate / 100 |
| `month_deprec` | (buy_price - residual_val) / use_months |
| `total_deprec` | 累计月折旧（自动累加） |
| `now_val` | buy_price - total_deprec |

### 折旧巡检规则

- 自动计算 `last_deprec_date` 到今天的月份差
- 自动补提折旧
- 折旧完毕时自动锁定 `now_val = residual_val`

---

## 字段说明

| 字段 | 数据库类型 | 说明 |
|------|-----------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `info` | varchar(255) | 资产名称 |
| `tag` | varchar(50) | 品类 |
| `img_url` | varchar(512) | 图片URL |
| `buy_price` | decimal(12,2) | 购买价 |
| `now_val` | decimal(12,2) | 当前账面价值 |
| `secondhand_price` | decimal(12,2) | 二手市场价 |
| `use_years` | varchar(50) | 预计使用年限 |
| `use_months` | smallint | 总月数（自动计算） |
| `residual_rate` | decimal(4,2) | 残值率% |
| `residual_val` | decimal(12,2) | 残值 |
| `deprec_method` | varchar(20) | 折旧方法 |
| `month_deprec` | decimal(12,2) | 月折旧额 |
| `total_deprec` | decimal(12,2) | 累计折旧 |
| `buy_date` | varchar(20) | 购买日期 |
| `last_deprec_date` | varchar(20) | 上次折旧日期 |
| `scrap_date` | varchar(20) | 报废日期 |
| `create_time` | varchar(20) | 创建时间 |
| `deprec_finished` | tinyint | 折旧是否完结（0/1） |
| `status` | varchar(20) | 状态 |
| `is_deleted` | tinyint | 是否删除 |
