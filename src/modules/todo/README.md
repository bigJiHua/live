# 待办日程 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取待办列表 |
| GET | `/:id` | 获取待办详情 |
| POST | `/` | 创建待办 |
| PUT | `/:id` | 更新待办 |
| DELETE | `/:id` | 删除待办 |

---

## 接口详情

### 1. 获取待办列表

**路径**：`GET /api/v1/todo/`

**参数**（Query）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | ❌ | 状态：待完成/已完成/逾期 |

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [
    {
      "id": "UUID",
      "content": "会议",
      "event_type": "日程",
      "happen_date": "2026-04-19",
      "status": "待完成",
      "priority": 2,
      "need_remind": 0,
      "remark": "备注"
    }
  ]
}
```

---

### 2. 获取待办详情

**路径**：`GET /api/v1/todo/:id`

---

### 3. 创建待办

**路径**：`POST /api/v1/todo/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | ✅ | 事件内容 |
| `event_type` | string | ❌ | 事件类型：日程/生日/纪念日/倒数日 |
| `happen_date` | string | ✅ | 发生日期 `yyyy-MM-dd` |
| `priority` | number | ❌ | 优先级：1高/2中/3低，默认2 |
| `need_remind` | number | ❌ | 是否提醒：0否/1是 |
| `remind_time` | string | ❌ | 提醒时间戳 |
| `is_recurring` | number | ❌ | 是否每年重复（生日/纪念日） |
| `remark` | string | ❌ | 详细备注 |

---

### 4. 更新待办

**路径**：`PUT /api/v1/todo/:id`

**请求体**：同创建，字段可选

---

### 5. 删除待办

**路径**：`DELETE /api/v1/todo/:id`

**说明**：软删除

---

## 字段说明

### todo 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `content` | varchar(255) | 事件内容 |
| `event_type` | varchar(20) | 事件类型 |
| `happen_date` | varchar(20) | 发生日期 |
| `status` | varchar(20) | 状态 |
| `priority` | tinyint | 优先级：1高/2中/3低 |
| `need_remind` | tinyint | 是否提醒 |
| `remind_time` | varchar(20) | 提醒时间戳 |
| `is_recurring` | tinyint | 是否每年重复 |
| `remark` | varchar(100) | 备注 |
| `create_time` | varchar(20) | 创建时间 |
| `update_time` | varchar(20) | 修改时间 |
| `is_deleted` | tinyint | 是否删除 |
