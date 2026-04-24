# 生活管理系统 API

一个功能完整的生活管理系统后端 API，提供用户认证、账务管理、银行卡管理、PIN 码安全验证等功能。

## 功能特性

- 🔐 用户注册、登录、JWT 认证
- 🔑 PIN 码安全验证（支持 423 状态码锁定机制）
- � 银行卡管理（支持信用卡账单周期计算）
- �💰 账务流水管理（收入/支出）
- 🏦 账户余额管理
- 📊 财务报表生成
- 📈 投资回报率 (IRR) 计算
- 🏷️ 分类管理、银行管理
- � 文件上传
- � 时刻/日记记录
- ✅ 待办日程管理
- 💼 工作与工资管理
- 🏠 固定资产管理
- 📉 预算管理
- �🛡️ 安全中间件（认证、PIN 锁定）

## 项目结构

```
live-api
├─ src
│  ├─ api                    # 路由入口
│  │  └─ index.js            # API 路由注册
│  ├─ modules                # 业务模块（按功能划分）
│  │  ├─ auth/              # 认证模块
│  │  ├─ security/          # 安全模块（PIN码）
│  │  ├─ account/           # 账务模块（流水+余额）
│  │  ├─ category/         # 分类+银行模块
│  │  ├─ card/              # 银行卡模块（卡片+账单+还款）
│  │  ├─ user/              # 用户模块
│  │  ├─ upload/            # 文件上传模块
│  │  ├─ moment/            # 时刻/日记模块
│  │  ├─ asset/             # 资产快照模块
│  │  ├─ todo/              # 待办日程模块
│  │  ├─ work/             # 工作与工资模块
│  │  ├─ fixed_asset/      # 固定资产模块
│  │  └─ budget/            # 预算模块
│  ├─ common                # 公共模块
│  │  ├─ config/           # 配置文件
│  │  ├─ middleware/       # 中间件
│  │  └─ utils/            # 工具类
│  └─ app.js               # Express 实例配置
├─ mysql/                   # 数据库脚本
│  └─ live.sql             # 数据库表结构
├─ public                   # 静态资源
├─ logs                    # 系统日志
├─ .env                    # 环境变量
├─ .env.example            # 环境变量示例
├─ package.json
└─ server.js               # 程序启动入口
```

## API 路由总览

| 前缀 | 模块 | 说明 |
|------|------|------|
| `/api/auth` | auth | 用户认证（注册、登录、Token） |
| `/api/security` | security | 安全设置（PIN码设置/验证/修改） |
| `/api/account` | account | 账务流水管理 |
| `/api/accountBalance` | account | 账户余额管理 |
| `/api/category` | category | 收支分类管理 |
| `/api/bank` | category | 银行分类管理 |
| `/api/card` | card | 银行卡管理 |
| `/api/card/bill` | card | 信用卡账单管理 |
| `/api/card/repay` | card | 信用卡还款管理 |
| `/api/user` | user | 用户管理 |
| `/api/upload` | upload | 文件上传 |
| `/api/moment` | moment | 时刻/日记 |
| `/api/asset` | asset | 资产快照与登记 |
| `/api/todo` | todo | 待办日程 |
| `/api/work` | work | 工作与工资 |
| `/api/fixedAsset` | fixed_asset | 固定资产 |
| `/api/budget` | budget | 预算管理 |

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

### 导入数据库结构

```bash
mysql -u root -p life_manager < mysql/live.sql
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

## 认证机制

所有业务接口都需要 **JWT 认证 + PIN 码验证**。

- JWT Token：用于身份验证
- PIN 码：6位数字，用于保护敏感操作
- 423 状态码：会话被 PIN 锁定，前端需引导用户验证 PIN

### 认证接口 (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/me` | 获取当前用户 |
| POST | `/api/auth/refresh` | 刷新 Token |

### 安全接口 (`/api/security`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/security/pin/set` | 设置 PIN 码 |
| POST | `/api/security/pin/verify` | 验证 PIN 码 |
| POST | `/api/security/pin/change` | 修改 PIN 码 |

## 银行卡业务规则

### 账单周期计算

- **账单周期**：账单日(bill_day)次日 ~ 次月账单日前一天
- **账单月归属**：
  - 消费日期 **<** 账单日 → 当月账单
  - 消费日期 **>** 账单日 → 下月账单（账单日当天仍属当月）
- **还款日**：账单月的下一月 repayDay
- **逾期判定**：当前日期 > repay_date 且 need_repay > 0

### 举例说明

假设账单日=12号，还款日=次月6号：

| 消费日期 | 归属账单月 | 出账日 | 还款日 |
|---------|-----------|--------|--------|
| 4月1日 ~ 4月12日 | 4月 | 4月12日 | 5月6日 |
| 4月13日 ~ 4月30日 | 5月 | 5月12日 | 6月6日 |

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
