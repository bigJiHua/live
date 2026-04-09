# API 路由层

本目录包含所有 API 路由入口文件，负责定义 HTTP 端点并将请求分发到相应的控制器。

## 文件说明

### auth.js
**认证相关路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/auth/register` | 用户注册 | - |
| POST | `/api/auth/login` | 用户登录 | - |
| GET | `/api/auth/me` | 获取当前用户信息 | authGuard |
| POST | `/api/auth/refresh` | 刷新 Token | - |

---

### security.js
**安全相关路由 (PIN 码管理)**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/security/pin/verify` | 验证 PIN 码 | authGuard |
| POST | `/api/security/pin/set` | 设置 PIN 码 | authGuard |
| POST | `/api/security/pin/change` | 修改 PIN 码 | authGuard |
| POST | `/api/security/pin/reset` | 重置 PIN 码 | - |

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

---

### user.js
**用户管理路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| PUT | `/api/user/profile` | 更新用户资料 | authGuard |
| POST | `/api/user/password/change` | 修改密码 | authGuard |
| GET | `/api/user/settings` | 获取用户设置 | authGuard |
| PUT | `/api/user/settings` | 更新用户设置 | authGuard |

---

### moment.js
**动态(朋友圈)路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/v1/moment` | 发布动态 | authGuard |
| GET | `/api/v1/moment/list` | 获取动态列表 | authGuard |
| GET | `/api/v1/moment/today` | 获取今日动态(含子动态) | authGuard |
| GET | `/api/v1/moment/:id` | 获取动态详情 | authGuard |
| POST | `/api/v1/moment/batch` | 批量获取子动态详情 | authGuard |
| PUT | `/api/v1/moment/:id` | 更新动态 | authGuard |
| DELETE | `/api/v1/moment/:id` | 删除动态 | authGuard |

---

### upload.js
**文件上传路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| POST | `/api/v1/upload/single` | 单文件上传 | authGuard |
| POST | `/api/v1/upload/multiple` | 多文件上传 | authGuard |
| GET | `/api/v1/upload/list` | 查询附件列表 | authGuard |
| GET | `/api/v1/upload/search` | 搜索附件 | authGuard |
| POST | `/api/v1/upload/:id` | 编辑附件(remark/tags) | authGuard |
| POST | `/api/v1/upload/batch-delete` | 批量删除附件 | authGuard |

**busType 类型**: `post`(动态图片) / `product`(资产图片) / `bank`(银行Icon) / `other`(其他)

---

### database.js
**数据库管理路由**

| 方法 | 路径 | 说明 | 中间件 |
|------|------|------|--------|
| GET | `/api/database/status` | 数据库状态 | - |

---

## 中间件说明

| 中间件 | 说明 |
|--------|------|
| `authGuard` | 验证 JWT Token，将用户信息添加到请求对象 |
| `pinLockGuard` | 检查会话是否需要 PIN 验证，未验证时返回 423 状态码 |

## 状态码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 200 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或认证失败 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 423 | 被锁定 (需要 PIN 验证) |
| 500 | 服务器内部错误 |

## 设计原则

1. **单一职责**: 每个路由文件负责一个功能模块
2. **RESTful 设计**: 遵循 REST API 设计规范
3. **中间件链**: 使用中间件处理认证、验证等通用逻辑
4. **统一响应**: 所有接口返回统一的 JSON 格式
