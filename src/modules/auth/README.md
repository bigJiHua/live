# 认证模块 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | 用户注册 |
| POST | `/login` | 用户登录 |
| POST | `/logout` | 用户登出 |
| GET | `/user` | 获取用户信息 |
| PUT | `/user` | 更新用户信息 |
| PUT | `/password` | 修改密码 |
| PUT | `/pin` | 设置/修改PIN码 |
| GET | `/log` | 获取登录日志 |

---

## 接口详情

### 1. 用户注册

**路径**：`POST /api/auth/register`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | ✅ | 用户名 |
| `email` | string | ✅ | 邮箱 |
| `login_pwd` | string | ✅ | 登录密码 |
| `pin_code` | string | ❌ | PIN码 |

---

### 2. 用户登录

**路径**：`POST /api/v1/auth/login`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | ✅ | 用户名 |
| `login_pwd` | string | ✅ | 登录密码 |
| `pin_code` | string | ❌ | PIN码（已设置时必填） |

---

### 3. 用户登出

**路径**：`POST /api/v1/auth/logout`

---

### 4. 获取用户信息

**路径**：`GET /api/v1/auth/user`

---

### 5. 更新用户信息

**路径**：`PUT /api/v1/auth/user`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | ❌ | 用户名 |
| `email` | string | ❌ | 邮箱 |
| `avatar` | string | ❌ | 头像URL |

---

### 6. 修改密码

**路径**：`PUT /api/v1/auth/password`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `old_password` | string | ✅ | 旧密码 |
| `new_password` | string | ✅ | 新密码 |

---

### 7. 设置/修改PIN码

**路径**：`PUT /api/v1/auth/pin`

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `old_pin` | string | ❌ | 旧PIN码（修改时必填） |
| `new_pin` | string | ✅ | 新PIN码 |

---

### 8. 获取登录日志

**路径**：`GET /api/v1/auth/log`

**响应**：
```json
{
  "status": 200,
  "data": [...]
}
```

---

## 字段说明

### user_info 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(32) | 主键UUID |
| `username` | varchar(50) | 用户名 |
| `email` | varchar(50) | 邮箱 |
| `avatar` | varchar(255) | 头像URL |
| `login_pwd` | varchar(100) | 登录密码（加密） |
| `identity` | varchar(25) | 用户身份 |
| `pin_code` | varchar(100) | PIN码（加密） |
| `status` | tinyint | 状态：1正常/0锁定 |
| `create_time` | varchar(20) | 创建时间 |
| `update_time` | varchar(20) | 修改时间 |
| `is_deleted` | tinyint | 是否删除 |

### user_log 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int | 主键自增 |
| `user_id` | varchar(50) | 用户ID |
| `type` | varchar(20) | 操作类型 |
| `login_ip` | varchar(255) | 登录IP |
| `login_location` | varchar(255) | 地理位置 |
| `status` | tinyint | 状态：1成功/0失败 |
| `login_time` | varchar(20) | 登录时间 |
