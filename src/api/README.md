# API 路由层

本目录包含所有 API 路由入口文件,负责定义 HTTP 端点并将请求分发到相应的控制器。

## 文件说明

### auth.js
**认证相关路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/auth/register` | 用户注册 | - |
| POST | `/api/auth/login` | 用户登录 | - |
| GET | `/api/auth/me` | 获取当前用户信息 | authGuard |
| POST | `/api/auth/refresh` | 刷新 Token | - |

**示例:**
```javascript
// 用户登录
POST /api/auth/login
Body: { email: "user@example.com", password: "123456" }
Response: { token: "jwt-token", user: {...} }
```

---

### security.js
**安全相关路由 (PIN 码管理)**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/security/pin/verify` | 验证 PIN 码 | authGuard |
| POST | `/api/security/pin/set` | 设置 PIN 码 | authGuard |
| POST | `/api/security/pin/change` | 修改 PIN 码 | authGuard |
| POST | `/api/security/pin/reset` | 重置 PIN 码 | - |

**示例:**
```javascript
// 验证 PIN 码
POST /api/security/pin/verify
Headers: { Authorization: "Bearer <token>" }
Body: { pin: "123456" }
Response: { message: "PIN 码验证成功", verified: true }
```

---

### account.js
**账务管理路由 (需要 PIN 验证)**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| GET | `/api/account/transactions` | 获取账务流水列表 | authGuard + pinLockGuard |
| GET | `/api/account/transactions/:id` | 获取账务流水详情 | authGuard + pinLockGuard |
| POST | `/api/account/transactions` | 创建账务流水 | authGuard + pinLockGuard |
| PUT | `/api/account/transactions/:id` | 更新账务流水 | authGuard + pinLockGuard |
| DELETE | `/api/account/transactions/:id` | 删除账务流水 | authGuard + pinLockGuard |
| GET | `/api/account/categories` | 获取分类列表 | authGuard + pinLockGuard |
| POST | `/api/account/categories` | 创建分类 | authGuard + pinLockGuard |
| PUT | `/api/account/categories/:id` | 更新分类 | authGuard + pinLockGuard |
| DELETE | `/api/account/categories/:id` | 删除分类 | authGuard + pinLockGuard |
| GET | `/api/account/report` | 获取财务报表 | authGuard + pinLockGuard |
| POST | `/api/account/calculate-irr` | 计算 IRR | authGuard + pinLockGuard |

**重要:** 所有账务接口都会先检查 PIN 验证状态,如果未验证会返回 **423** 状态码。

**示例:**
```javascript
// 创建账务流水
POST /api/account/transactions
Headers: { Authorization: "Bearer <token>" }
Body: {
  amount: 100.00,
  type: "expense",
  categoryId: 1,
  description: "午餐",
  date: "2024-03-26"
}
Response: { message: "创建成功", transaction: {...} }
```

---

### user.js
**用户管理路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| PUT | `/api/user/profile` | 更新用户资料 | authGuard |
| POST | `/api/user/password/change` | 修改密码 | authGuard |
| GET | `/api/user/settings` | 获取用户设置 | authGuard |
| PUT | `/api/user/settings` | 更新用户设置 | authGuard |

**示例:**
```javascript
// 更新用户资料
PUT /api/user/profile
Headers: { Authorization: "Bearer <token>" }
Body: { username: "newname" }
Response: { message: "更新成功", user: {...} }
```

## 中间件说明

### authGuard
验证 JWT Token,将用户信息添加到请求对象。

### pinLockGuard
检查会话是否需要 PIN 验证,未验证时返回 423 状态码。

## 状态码说明

- **200**: 成功
- **201**: 创建成功
- **400**: 请求参数错误
- **401**: 未认证或认证失败
- **403**: 权限不足
- **404**: 资源不存在
- **423**: 被锁定 (需要 PIN 验证)
- **500**: 服务器内部错误

## 设计原则

1. **单一职责**: 每个路由文件负责一个功能模块
2. **RESTful 设计**: 遵循 REST API 设计规范
3. **中间件链**: 使用中间件处理认证、验证等通用逻辑
4. **统一响应**: 所有接口返回统一的 JSON 格式
