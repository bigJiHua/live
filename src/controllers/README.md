# 控制器层 (Controllers)

控制器层负责处理业务逻辑,接收请求参数,调用服务和模型层,并返回响应结果。

## 设计原则

1. **请求处理**: 处理 HTTP 请求,提取和验证参数
2. **业务协调**: 协调服务层和模型层的调用
3. **响应格式化**: 将处理结果格式化为统一的 JSON 响应
4. **错误处理**: 捕获并处理异常,返回友好的错误信息

## 文件说明

### authController.js
**认证控制器**

处理用户认证相关的业务逻辑。

**主要方法:**
- `register()` - 用户注册
  - 检查邮箱是否已存在
  - 加密密码 (bcrypt)
  - 创建用户记录
  - 发送验证邮件

- `login()` - 用户登录
  - 验证邮箱和密码
  - 生成 JWT Token
  - 返回用户信息和 Token

- `getCurrentUser()` - 获取当前用户信息
- `refreshToken()` - 刷新 Token
- `updateProfile()` - 更新用户资料
- `changePassword()` - 修改密码
- `getSettings()` - 获取用户设置
- `updateSettings()` - 更新用户设置

**使用示例:**
```javascript
// 在路由中使用
router.post('/login', authController.login);

// 控制器内部示例
async login(req, res) {
  const { email, password } = req.body;
  const user = await User.findByEmail(email);
  const isValid = await bcrypt.compare(password, user.password);
  const token = jwt.sign({ userId: user.id }, secret);
  res.json({ token, user });
}
```

---

### financeController.js
**财务控制器**

处理账务管理相关的业务逻辑。

**主要方法:**
- `getTransactions()` - 获取账务流水列表 (支持分页和过滤)
- `getTransactionById()` - 获取账务流水详情
- `createTransaction()` - 创建账务流水
- `updateTransaction()` - 更新账务流水
- `deleteTransaction()` - 删除账务流水
- `getCategories()` - 获取分类列表
- `createCategory()` - 创建分类
- `updateCategory()` - 更新分类
- `deleteCategory()` - 删除分类
- `getFinanceReport()` - 获取财务报表
- `calculateIRR()` - 计算投资回报率 (IRR)

**使用示例:**
```javascript
// 获取账务流水 (支持分页和过滤)
GET /api/account/transactions?page=1&limit=20&type=income&categoryId=1

// 计算投资回报率
POST /api/account/calculate-irr
Body: { cashFlows: [-10000, 3000, 4000, 5000, 6000] }
Response: { irr: 12.5, message: "内部收益率为 12.50%" }
```

---

### securityController.js
**安全控制器**

处理 PIN 码管理和安全相关的业务逻辑。

**主要方法:**
- `verifyPin()` - 验证 PIN 码
  - 验证 PIN 码是否正确
  - 标记会话已通过验证

- `setPin()` - 设置 PIN 码
  - 验证 PIN 格式 (6位数字)
  - 确认两次输入一致
  - 加密并存储

- `changePin()` - 修改 PIN 码
  - 验证旧 PIN 码
  - 验证新 PIN 格式
  - 更新 PIN 码

- `resetPin()` - 重置 PIN 码
  - 验证邮箱验证码
  - 重置为新的 PIN 码

- `sendVerificationCode()` - 发送验证码 (邮箱验证)

**使用示例:**
```javascript
// 设置 PIN 码
POST /api/security/pin/set
Headers: { Authorization: "Bearer <token>" }
Body: { pin: "123456", confirmPassword: "123456" }
Response: { message: "PIN 码设置成功", hasPin: true }

// 验证 PIN 码
POST /api/security/pin/verify
Headers: { Authorization: "Bearer <token>" }
Body: { pin: "123456" }
Response: { message: "PIN 码验证成功", verified: true }
```

## 统一响应格式

所有控制器都遵循统一的响应格式:

### 成功响应
```javascript
{
  message: "操作成功",
  data: { ... }  // 可选
}
```

### 错误响应
```javascript
{
  message: "错误描述",
  error: "详细错误信息 (开发环境)"
}
```

### 分页响应
```javascript
{
  data: [...],
  pagination: {
    page: 1,
    limit: 20,
    total: 100,
    totalPages: 5
  }
}
```

## 错误处理

控制器使用 try-catch 捕获异常:

```javascript
async method(req, res) {
  try {
    // 业务逻辑
    res.json({ message: "成功", data: result });
  } catch (error) {
    console.error('操作错误:', error);
    res.status(500).json({
      message: '操作失败',
      error: error.message
    });
  }
}
```

## 访问用户信息

在需要认证的路由中,可以通过 `authGuard` 中间件获取用户信息:

```javascript
// authGuard 中间件会将用户信息添加到请求对象
req.userId  // 用户 ID
req.userEmail  // 用户邮箱
```

## 与其他层的关系

```
API 路由 → 控制器 → 服务层/模型层 → 数据库
           ↓
      验证参数
           ↓
      协调业务
           ↓
      格式化响应
```
