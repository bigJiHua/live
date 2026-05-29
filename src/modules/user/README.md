# 用户管理 API

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/profile` | 更新用户信息 |
| GET | `/settings` | 获取用户设置 |
| PUT | `/settings` | 更新用户设置 |
| POST | `/email/send-code` | 发送邮箱验证码 |
| PUT | `/email/change` | 修改邮箱（需验证码） |
| PUT | `/password/change` | 修改密码（需验证码） |

---

## 接口详情

### 1. 更新用户信息

**路径**：`PUT /api/v1/user/profile`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | ❌ | 昵称，2-10字符 |
| `avatar` | string | ❌ | 头像URL |
| `email` | string | ❌ | 邮箱（格式校验） |

**Joi 校验规则**：
```javascript
{
  username: '可选, 2-10字符',
  avatar: '可选, URI格式',
  email: '可选, 邮箱格式'
}
```

---

### 2. 获取用户设置

**路径**：`GET /api/v1/user/settings`

**认证**：需要 JWT

---

### 3. 更新用户设置

**路径**：`PUT /api/v1/user/settings`

**认证**：需要 JWT

---

### 4. 发送邮箱验证码

**路径**：`POST /api/v1/user/email/send-code`

**认证**：需要 JWT

**中间件**：`emailCodeRateLimit`（频率限制）

**说明**：向当前用户绑定的邮箱发送6位数字验证码，用于后续修改邮箱或密码。

---

### 5. 修改邮箱

**路径**：`PUT /api/v1/user/email/change`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `email` | string | ✅ | 新邮箱地址 |
| `code` | string | ✅ | 6位邮箱验证码 |

---

### 6. 修改密码

**路径**：`PUT /api/v1/user/password/change`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `oldPassword` | string | ✅ | 旧密码，至少6位 |
| `newPassword` | string | ✅ | 新密码，至少6位 |
| `code` | string | ✅ | 6位邮箱验证码 |

---

## 字段说明

### user_info 表（用户相关字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(50) | 主键UUID |
| `username` | varchar(50) | 用户名 |
| `email` | varchar(50) | 邮箱 |
| `avatar` | varchar(255) | 头像URL |
| `login_pwd` | varchar(100) | 登录密码（bcrypt加密） |
| `identity` | varchar(25) | 用户身份 |
| `status` | tinyint | 状态：1正常/0锁定 |

---

## 更新日志

### 5月28日
- 补充完整 API 文档
