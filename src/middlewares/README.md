# 中间件层 (Middlewares)

中间件用于在请求到达控制器之前执行通用逻辑,如身份验证、权限检查、日志记录等。

## 中间件执行顺序

```
请求 → 中间件1 → 中间件2 → ... → 控制器 → 响应
       ↓
     next()
```

## 文件说明

### authGuard.js
**JWT 认证中间件**

验证请求头中的 JWT Token,并将用户信息添加到请求对象中。

**功能:**
1. 从 `Authorization` 请求头提取 Token
2. 验证 Token 的有效性
3. 将用户信息添加到 `req.userId` 和 `req.userEmail`
4. 处理 Token 过期和无效的情况

**使用方法:**
```javascript
const authGuard = require('../middlewares/authGuard');

// 单个路由使用
router.get('/protected', authGuard, controller.method);

// 路由组使用
router.use(authGuard);
router.get('/profile', controller.getProfile);
```

**请求格式:**
```
Headers:
  Authorization: Bearer <jwt-token>
```

**错误响应:**
- **400**: 未提供 Token
- **401**: Token 无效或已过期

**示例代码:**
```javascript
const authGuard = (req, res, next) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: '未提供认证 Token' });
  }

  const token = authHeader.substring(7);
  const decoded = jwt.verify(token, process.env.JWT_SECRET);

  req.userId = decoded.userId;
  req.userEmail = decoded.email;

  next();
};
```

---

### pinLockGuard.js
**PIN 锁定检查中间件**

检查用户会话是否需要 PIN 码验证,用于保护敏感操作。

**功能:**
1. 检查会话是否已通过 PIN 验证
2. 检查用户是否设置了 PIN 码
3. 如果需要验证,返回 423 状态码
4. 如果不需要,允许请求继续

**423 状态码:**
表示资源已被锁定,需要用户提供额外的凭证 (PIN 码) 才能访问。

**使用方法:**
```javascript
const pinLockGuard = require('../middlewares/pinLockGuard');
const authGuard = require('../middlewares/authGuard');

// 必须先验证身份,再检查 PIN 锁定
router.use(authGuard);
router.use(pinLockGuard);
router.get('/account', accountController.getAccounts);
```

**响应格式:**
```javascript
// 需要验证 PIN 码时
{
  message: "需要验证 PIN 码才能访问此功能",
  code: "PIN_REQUIRED",
  requiresPin: true
}

// 验证通过后,在请求头中传递:
Headers:
  Authorization: Bearer <jwt-token>
  // 会话会标记 pinVerified = true
```

**PIN 验证流程:**
```
1. 用户访问敏感 API (如账务管理)
2. pinLockGuard 检查会话是否已验证 PIN
3. 如果未验证,返回 423 状态码
4. 前端收到 423,引导用户输入 PIN 码
5. 用户调用 POST /api/security/pin/verify 验证 PIN
6. 验证成功后,会话标记为已验证
7. 用户可以正常访问敏感 API
```

**示例代码:**
```javascript
const pinLockGuard = async (req, res, next) => {
  // 检查会话是否已验证
  if (req.session && req.session.pinVerified) {
    return next();
  }

  const user = await User.findById(req.userId);

  // 如果用户没有设置 PIN,跳过验证
  if (!user.pinHash) {
    return next();
  }

  // 返回 423 状态码
  return res.status(423).json({
    message: '需要验证 PIN 码才能访问此功能',
    code: 'PIN_REQUIRED',
    requiresPin: true,
  });
};
```

## 中间件组合使用

多个中间件可以组合使用,按顺序执行:

```javascript
// 示例: 先认证,再检查 PIN,最后检查权限
router.get('/sensitive-route',
  authGuard,           // 1. 验证 JWT
  pinLockGuard,        // 2. 检查 PIN 锁定
  someOtherGuard,      // 3. 其他检查
  controller.method    // 4. 执行控制器
);
```

## 自定义中间件

可以创建自己的中间件:

```javascript
// 示例: 日志中间件
const logger = (req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
};

// 使用
router.use(logger);
```

## 错误处理中间件

错误处理中间件有 4 个参数 (err, req, res, next):

```javascript
const errorHandler = (err, req, res, next) => {
  console.error('Error:', err);
  res.status(err.status || 500).json({
    message: err.message || '服务器内部错误',
  });
};

// 在 app.js 中使用 (必须放在最后)
app.use(errorHandler);
```

## 中间件最佳实践

1. **单一职责**: 每个中间件只负责一个功能
2. **提前返回**: 验证失败时立即返回,不要继续执行
3. **调用 next()**: 验证通过时必须调用 `next()`
4. **异步处理**: 异步中间件需要正确处理 Promise
5. **错误传递**: 使用 `next(err)` 将错误传递给错误处理中间件

## 状态码参考

| 状态码 | 说明 | 使用场景 |
|--------|------|----------|
| 200 | 成功 | 验证通过 |
| 400 | 请求错误 | 缺少必需参数 |
| 401 | 未认证 | Token 无效或缺失 |
| 403 | 禁止访问 | 权限不足 |
| 423 | 被锁定 | 需要 PIN 验证 |
| 500 | 服务器错误 | 中间件内部错误 |
