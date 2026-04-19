# 工资管理 API

## 接口列表

### 工作信息 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/job/list` | 获取工作列表 |
| POST | `/job` | 创建工作 |
| PUT | `/job/:id` | 更新工作 |
| DELETE | `/job/:id` | 删除工作 |

### 工资管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/salary/day` | 获取某天工资 |
| POST | `/salary` | 保存工资 |
| GET | `/salary/month` | 按月统计工资 |
| DELETE | `/salary` | 删除工资记录 |

---

## 接口详情

### ========== 工作信息 ==========

### 1. 获取工作列表

**路径**：`GET /api/v1/work/job/list`

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "UUID",
      "job_type": "formal",
      "company": "公司名称",
      "job_color": "#07c160",
      "status": 1,
      "join_date": "2026-01-01",
      "leave_date": null,
      "pay_day": 15,
      "base_salary": "8000.00",
      "base_work_days": 22,
      "hourly_wage": "0.00",
      "subsidy_meal": "0.00",
      "subsidy_traffic": "0.00",
      "subsidy_post": "0.00",
      "social": "0.00",
      "fund": "0.00",
      "tax_rate": "0.00",
      "remark": null,
      "create_at": "2026-04-19 00:00:00"
    }
  ]
}
```

---

### 2. 创建工作

**路径**：`POST /api/v1/work/job`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `job_type` | string | ✅ | 工作类型：`formal`（正式工）/ `parttime`（兼职） |
| `company` | string | ✅ | 公司名称 |
| `status` | number | ✅ | 状态：1（在职）/ 0（离职） |
| `join_date` | string | ✅ | 入职日期 `yyyy-MM-dd` |
| `base_salary` | number | ⚠️ | 月基本工资（正式工必填） |
| `base_work_days` | number | ⚠️ | 月应出勤天数（正式工必填，默认22） |
| `hourly_wage` | number | ⚠️ | 时薪（兼职必填） |
| `pay_day` | number | ❌ | 发薪日（默认15） |
| `job_color` | string | ❌ | 颜色，默认 `#07c160` |
| `leave_date` | string | ❌ | 离职日期 |
| `subsidy_meal` | number | ❌ | 每月餐补 |
| `subsidy_traffic` | number | ❌ | 每月交通补贴 |
| `subsidy_post` | number | ❌ | 每月岗位补贴 |
| `social` | number | ❌ | 每月社保扣款 |
| `fund` | number | ❌ | 每月公积金扣款 |
| `tax_rate` | number | ❌ | 预估税率% |
| `remark` | string | ❌ | 备注，最大50字 |

---

### 3. 更新工作

**路径**：`PUT /api/v1/work/job/:id`

**请求体**：同创建，字段可选

**说明**：更新正式工离职日期时，自动删除离职日之后的薪酬记录

---

### 4. 删除工作

**路径**：`DELETE /api/v1/work/job/:id`

**说明**：软删除

---

### ========== 工资管理 ==========

### 5. 获取某天工资

**路径**：`GET /api/v1/work/salary/day`

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `work_date` | string | ✅ | 日期 `yyyy-MM-dd` |
| `work_hours` | string | ❌ | 兼职工时，JSON格式 `{"job_id": hours}` |

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": {
    "formal": {
      "id": "UUID",
      "job_id": "工作ID",
      "job_type": "formal",
      "work_date": "2026-04-19",
      "day_salary": 363.64,
      "subsidy": 0,
      "social": 0,
      "fund": 0,
      "tax": 0,
      "cut": 0,
      "income": 363.64,
      "status": 1,
      "is_calculated": false
    },
    "parttimes": [
      {
        "job_id": "兼职ID",
        "job_type": "parttime",
        "hourly_wage": 25,
        "work_hours": 4,
        "day_salary": 100,
        "income": 100
      }
    ],
    "total_income": 463.64
  }
}
```

---

### 6. 保存工资

**路径**：`POST /api/v1/work/salary`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `job_id` | string | ✅ | 工作ID |
| `work_date` | string | ✅ | 日期 `yyyy-MM-dd` |
| `work_hours` | number | ⚠️ | 工作时长（兼职必填） |
| `cut` | number | ❌ | 扣除项 |
| `subsidy` | number | ❌ | 补贴（覆盖默认值） |
| `status` | number | ❌ | 状态：0（待确认）/ 1（已确认） |
| `remark` | string | ❌ | 备注 |

**说明**：
- 保存后自动补充本月缺失的正式工日薪
- 范围：当月1日 ~ 插入日期

---

### 7. 按月统计

**路径**：`GET /api/v1/work/salary/month`

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `year` | string | ✅ | 年份 `2026` |
| `month` | string | ✅ | 月份 `04` |

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": {
    "year": "2026",
    "month": "04",
    "formal_total": 7272.80,
    "parttime_total": 500.00,
    "total_income": 7772.80,
    "daily_list": [
      {
        "date": "2026-04-01",
        "formal": { ... },
        "parttimes": [],
        "day_total": 363.64
      }
    ]
  }
}
```

---

### 8. 删除工资

**路径**：`DELETE /api/v1/work/salary`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `work_date` | string | ✅ | 日期 `yyyy-MM-dd` |

**说明**：硬删除该日期的所有工资记录

---

## 字段说明

### work_job 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `job_type` | varchar(20) | 工作类型 |
| `company` | varchar(100) | 公司名称 |
| `job_color` | varchar(20) | 显示颜色 |
| `status` | tinyint | 状态：1在职/0离职 |
| `join_date` | varchar(20) | 入职日期 |
| `leave_date` | varchar(20) | 离职日期 |
| `pay_day` | tinyint | 发薪日 |
| `base_salary` | decimal(12,2) | 月基本工资 |
| `base_work_days` | int | 月应出勤天数 |
| `hourly_wage` | decimal(12,2) | 兼职时薪 |
| `subsidy_meal` | decimal(12,2) | 每月餐补 |
| `subsidy_traffic` | decimal(12,2) | 每月交通补贴 |
| `subsidy_post` | decimal(12,2) | 每月岗位补贴 |
| `social` | decimal(12,2) | 每月社保 |
| `fund` | decimal(12,2) | 每月公积金 |
| `tax_rate` | decimal(10,2) | 预估税率% |
| `remark` | varchar(50) | 备注 |
| `is_deleted` | tinyint | 是否删除 |
| `create_at` | timestamp | 创建时间 |

### work_salary 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `job_id` | varchar(32) | 关联工作ID |
| `job_type` | varchar(20) | 工作类型 |
| `work_date` | varchar(20) | 日期文本 |
| `work_date_dt` | date | 标准日期 |
| `work_hours` | decimal(8,2) | 工作时长（兼职） |
| `day_salary` | decimal(12,2) | 当日基本工资 |
| `subsidy` | decimal(12,2) | 当日补贴 |
| `social` | decimal(12,2) | 当日社保 |
| `fund` | decimal(12,2) | 当日公积金 |
| `tax` | decimal(12,2) | 当日税额 |
| `cut` | decimal(12,2) | 扣除项 |
| `income` | decimal(12,2) | 实得收入 |
| `status` | tinyint | 状态 |
| `remark` | varchar(50) | 备注 |
| `is_deleted` | tinyint | 是否删除 |
| `update_at` | timestamp | 更新时间 |

---

## 计算规则

### 正式工日薪
```
日薪 = base_salary / base_work_days
日社保 = social / base_work_days
日公积金 = fund / base_work_days
日税额 = (日薪 + 补贴) × tax_rate / 100
实得 = 日薪 + 补贴 - 日社保 - 日公积金 - 日税额 - 扣除
```

**注意**：`subsidy_meal`、`subsidy_traffic`、`subsidy_post` 是每月固定补贴，不计入每日工资，应在发薪日单独发放。

### 兼职工日薪
```
日薪 = hourly_wage × work_hours
实得 = 日薪 + 补贴 - 扣除
```
