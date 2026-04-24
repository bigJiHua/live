# 中间件层 (Middleware)

本目录包含应用程序的公共中间件。

## 文件说明

| 文件 | 说明 |
|------|------|
| `authGuard.js` | JWT 认证中间件 |
| `pinLockGuard.js` | PIN 锁定检查中间件 |
| `pinSecurityGuard.js` | PIN 码安全验证中间件 |
| `accountLockCheck.js` | 账户锁定检查中间件 |
| `permissionGuard.js` | 权限检查中间件 |
| `authSecurityData.js` | 数据安全解密中间件 |
| `emailCodeRateLimit.js` | 邮箱验证码频率限制中间件 |
| `validate.js` | 参数验证中间件 |
| `errorMiddleware.js` | 错误处理中间件 |
| `securityCheck.js` | 安全检查中间件（阻止非浏览器请求） |

---

## 中间件说明

### authGuard.js
**JWT 认证中间件**

验证请求头中的 JWT Token，并将用户信息添加到请求对象中。

**功能：**
1. 从 `Authorization` 请求头提取 Token
2. 验证 Token 的有效性
3. 将用户信息添加到 `req.userId`
4. 处理 Token 过期和无效的情况

**请求格式：**
```
Headers:
  Authorization: Bearer <jwt-token>
```

**错误响应：**
- **401**: Token 无效或已过期

---

### pinLockGuard.js
**PIN 锁定检查中间件**

检查用户会话是否需要 PIN 码验证，用于保护敏感操作。

**功能：**
1. 检查会话是否已通过 PIN 验证
2. 检查用户是否设置了 PIN 码
3. 如果需要验证，返回 423 状态码
4. 如果不需要，允许请求继续

**423 状态码：** 表示资源已被锁定，需要用户提供额外的凭证 (PIN 码) 才能访问。

---

### pinSecurityGuard.js
**PIN 码安全验证中间件**

验证用户输入的 PIN 码是否正确。

---

### accountLockCheck.js
**账户锁定检查中间件**

防止暴力破解账户，基于 IP 和用户 ID 的请求频率限制。

**功能：**
- 记录请求频率
- 检测异常请求
- 锁定账户或 IP

---

### permissionGuard.js
**权限检查中间件**

检查用户权限。

---

### authSecurityData.js
**数据安全解密中间件**

解密前端传来的加密数据。

---

### emailCodeRateLimit.js
**邮箱验证码频率限制中间件**

限制邮箱验证码发送频率。

**功能：**
- 记录发送次数
- 限制发送频率
- 防止滥用

---

### validate.js
**参数验证中间件**

验证请求参数。

---

### errorMiddleware.js
**错误处理中间件**

统一处理错误和 404。

---

### securityCheck.js
**安全检查中间件**

阻止非浏览器请求访问 API，保护接口不被爬虫、脚本等工具滥用。

**功能：**
- 检查 User-Agent 请求头
- 阻止已知爬虫工具（Postman、curl、Python requests 等）
- 允许正常浏览器访问

**被阻止的 User-Agent：**
- python-requests, curl, wget
- postman, insomnia, httpie
- axios, node-fetch, got, undici

---

## 中间件执行顺序

```
请求 → authGuard → pinLockGuard → ... → 控制器 → 响应
       ↓
     next()
```

## 状态码参考

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 200 | 成功 | 验证通过 |
| 400 | 请求错误 | 缺少必需参数 |
| 401 | 未认证 | Token 无效或已过期 |
| 403 | 权限不足 | 无权访问 |
| 423 | 锁定 | 需要 PIN 验证 |
| 500 | 服务器错误 | 内部错误 |
