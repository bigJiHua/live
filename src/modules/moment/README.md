# 动态/日记 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取动态列表 |
| GET | `/:id` | 获取动态详情 |
| POST | `/` | 发布动态 |
| PUT | `/:id` | 更新动态 |
| DELETE | `/:id` | 删除动态 |

---

## 接口详情

### 1. 获取动态列表

**路径**：`GET /api/v1/moment/`

**响应**：
```json
{
  "status": 200,
  "message": "查询成功",
  "data": [...]
}
```

---

### 2. 获取动态详情

**路径**：`GET /api/v1/moment/:id`

---

### 3. 发布动态

**路径**：`POST /api/v1/moment/`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | ✅ | 内容 |
| `img_url` | string | ❌ | 图片URL |
| `mood` | string | ❌ | 心情 |
| `location` | string | ❌ | 位置 |
| `visible_type` | number | ❌ | 可见性：0仅自己/1公开 |

---

### 4. 更新动态

**路径**：`PUT /api/v1/moment/:id`

**请求体**：同发布，字段可选

---

### 5. 删除动态

**路径**：`DELETE /api/v1/moment/:id`

**说明**：软删除

---

## 字段说明

### moment 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `user_id` | varchar(50) | 用户ID |
| `content` | text | 内容 |
| `img_url` | varchar(255) | 图片URL |
| `mood` | varchar(50) | 心情 |
| `location` | varchar(255) | 位置 |
| `visible_type` | tinyint | 可见性：0仅自己/1公开 |
| `parent_id` | varchar(50) | 父ID |
| `create_time` | varchar(20) | 发布时间 |
| `update_time` | varchar(20) | 修改时间 |
| `is_deleted` | tinyint | 是否删除 |
