# 生活管理系统 API

一个功能完整的生活管理系统后端 API，提供用户认证、账务管理、PIN 码安全验证等功能。

## 功能特性

- 🔐 用户注册、登录、JWT 认证
- 🔑 PIN 码安全验证（支持 423 状态码锁定机制）
- 💰 账务流水管理（收入/支出）
- 📊 财务报表生成
- 📈 投资回报率 (IRR) 计算
- 🏷️ 分类管理
- 📧 邮件验证功能
- 🛡️ 安全中间件（认证、PIN 锁定）

## 项目结构

```
Finance_API
├─ src
│  ├─ api                  # 路由入口
│  │  ├─ auth.js           # 认证路由
│  │  ├─ security.js       # 安全路由（PIN码验证）
│  │  ├─ account.js        # 账务路由
│  │  └─ user.js           # 用户路由
│  ├─ controllers          # 控制器层
│  │  ├─ authController.js
│  │  ├─ financeController.js
│  │  └─ securityController.js
│  ├─ middlewares          # 中间件
│  │  ├─ authGuard.js      # JWT 验证
│  │  └─ pinLockGuard.js   # PIN 锁定检查（返回423状态码）
│  ├─ models               # 数据模型
│  │  ├─ User.js
│  │  ├─ Transaction.js
│  │  └─ Category.js
│  ├─ services             # 业务逻辑层
│  │  └─ financeService.js
│  ├─ utils                # 工具类
│  │  ├─ crypto.js
│  │  └─ mailer.js
│  └─ config               # 配置文件
│     ├─ db.js
│     └─ jwt.js
├─ public                  # 静态资源
├─ logs                    # 系统日志
├─ tests                   # 测试文件
├─ .env                    # 环境变量
├─ .env.example            # 环境变量示例
├─ app.js                  # Express 实例配置
└─ server.js               # 程序启动入口
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 配置环境变量

复制 `.env.example` 为 `.env` 并修改相关配置：

```bash
cp .env.example .env
```

主要配置项：
- `DB_HOST`: 数据库主机
- `DB_USER`: 数据库用户名
- `DB_PASSWORD`: 数据库密码
- `DB_NAME`: 数据库名称
- `JWT_SECRET`: JWT 密钥
- `EMAIL_USER`: 邮箱用户名
- `EMAIL_PASS`: 邮箱密码（建议使用应用专用密码）

### 创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE life_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 启动服务

开发模式：
```bash
npm run dev
```

生产模式：
```bash
npm start
```

服务将在 `http://localhost:3000` 启动。

## API 文档

### 认证接口 (`/api/auth`)

#### 注册
```
POST /api/auth/register
Body: { username, email, password }
```

#### 登录
```
POST /api/auth/login
Body: { email, password }
Response: { token, user }
```

#### 获取当前用户
```
GET /api/auth/me
Headers: Authorization: Bearer <token>
```

#### 刷新 Token
```
POST /api/auth/refresh
Body: { token }
```

### 安全接口 (`/api/security`)

#### 设置 PIN 码
```
POST /api/security/pin/set
Headers: Authorization: Bearer <token>
Body: { pin, confirmPassword }
```

#### 验证 PIN 码
```
POST /api/security/pin/verify
Headers: Authorization: Bearer <token>
Body: { pin }
```

#### 修改 PIN 码
```
POST /api/security/pin/change
Headers: Authorization: Bearer <token>
Body: { oldPin, newPin, confirmPassword }
```

### 账务接口 (`/api/account`)

所有账务接口都需要 JWT 认证 + PIN 码验证。如果会话未验证 PIN 码，将返回 423 状态码。

#### 获取账务流水列表
```
GET /api/account/transactions?page=1&limit=20&type=income
Headers: Authorization: Bearer <token>
```

#### 创建账务流水
```
POST /api/account/transactions
Headers: Authorization: Bearer <token>
Body: { amount, type, categoryId, description, date }
```

#### 获取分类列表
```
GET /api/account/categories?type=income
Headers: Authorization: Bearer <token>
```

#### 创建分类
```
POST /api/account/categories
Headers: Authorization: Bearer <token>
Body: { name, type, color, icon }
```

#### 获取财务报表
```
GET /api/account/report?startDate=2024-01-01&endDate=2024-12-31
Headers: Authorization: Bearer <token>
```

#### 计算 IRR
```
POST /api/account/calculate-irr
Headers: Authorization: Bearer <token>
Body: { cashFlows: [-10000, 3000, 4000, 5000, 6000] }
```

### 用户接口 (`/api/user`)

#### 更新用户资料
```
PUT /api/user/profile
Headers: Authorization: Bearer <token>
Body: { username }
```

#### 修改密码
```
POST /api/user/password/change
Headers: Authorization: Bearer <token>
Body: { oldPassword, newPassword }
```

## 技术栈

- **框架**: Express.js
- **数据库**: MySQL (mysql2)
- **认证**: JWT (jsonwebtoken)
- **加密**: bcryptjs
- **安全**: Helmet, CORS
- **日志**: Morgan
- **邮件**: Nodemailer
- **环境变量**: dotenv

## 安全特性

1. **JWT 认证**: 使用 JWT Token 进行用户身份验证
2. **PIN 码保护**: 敏感操作需要额外验证 6 位 PIN 码
3. **423 状态码**: 会话被 PIN 锁定时返回 423 状态码，前端需要处理并引导用户验证 PIN
4. **密码加密**: 使用 bcryptjs 加密存储密码
5. **安全头**: 使用 Helmet 添加安全相关的 HTTP 头

## 数据库表结构

### users 表
- id: 用户 ID
- username: 用户名
- email: 邮箱
- password: 密码哈希
- pin_hash: PIN 码哈希
- settings: 用户设置（JSON）
- email_verified: 邮箱是否已验证

### categories 表
- id: 分类 ID
- user_id: 所属用户 ID
- name: 分类名称
- type: 类型（income/expense）
- color: 颜色
- icon: 图标

### transactions 表
- id: 流水 ID
- user_id: 所属用户 ID
- amount: 金额
- type: 类型（income/expense）
- category_id: 所属分类 ID
- description: 描述
- date: 日期

## 开发说明

### PIN 码验证流程

1. 用户登录成功后，访问敏感 API（如账务管理）
2. 如果用户设置了 PIN 码且会话未验证，返回 423 状态码
3. 前端收到 423 状态码后，引导用户输入 PIN 码
4. 用户输入 PIN 码，调用 `/api/security/pin/verify` 接口
5. 验证成功后，会话标记为已验证，可以正常访问敏感 API

### IRR 计算

使用牛顿迭代法计算内部收益率 (IRR)：

```javascript
// 现金流数组：负数表示投资，正数表示收益
const cashFlows = [-10000, 3000, 4000, 5000, 6000];
// 调用计算接口，返回 IRR 百分比
```

## License

MIT
