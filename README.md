# Golden Finance API — 生活管理系统后端

> **更新日期**: 2026-06-30

为 Golden Finance 移动端 PWA 应用提供 RESTful API 支持，涵盖用户认证、财务管理、银行卡、工资核算、理财投资、数据备份等完整功能。

## 功能特性

| 模块 | 功能 |
|------|------|
| 🔐 认证 | 用户注册、登录、JWT 认证、Token 刷新、系统锁定 |
| 🔑 安全 | PIN 码安全验证（423 锁定机制）、预握手 AES 密钥交换、多层请求校验 |
| 💰 账务 | 收支流水管理（收入/支出/转账/信用卡消费/还款）、流水冲正、账户余额 |
| 💳 银行卡 | 借记卡/信用卡管理、账单周期计算、还款管理、分期管理 |
| 📊 报表 | 流水筛选、收支统计、类目占比、月度趋势、负债统计 |
| 📈 理财 | 理财产品登记、净值追踪、收益明细、变动走势 |
| 🏷️ 分类 | 收支分类管理、银行分类管理 |
| ✅ 待办 | 日历日程、提醒管理 |
| 💼 工资 | 工作信息、工资日历、薪资统计、日薪计算 |
| 🏠 资产 | 资产快照登记、固定资产管理 |
| 📉 预算 | 购物/出行/餐饮预算管理 |
| 📁 上传 | 文件/图片上传（Sharp 处理，WebP + PNG 双格式） |
| 📓 日记 | 动态发布/编辑/分享（支持公开链接访问） |
| 📦 数据管理 | 数据库检查、备份、导出、导入、登录日志 |
| 🔁 周期支出 | 固定周期支出提醒 |
| 🌍 地理位置 | IP 定位、逆地理编码代理 |
| 🔗 分享 | 动态公开分享（免登录访问） |

## 项目结构

```
api/
├── app.js                       # Express 应用实例（中间件 + 路由挂载 + 启动逻辑）
├── package.json
├── .env / .env.example          # 环境变量配置
├── jest.config.js               # Jest 测试配置
│
├── src/
│   ├── api/                     # 路由层
│   │   ├── index.js             # API 路由统一注册（24 个子路由）
│   │   └── database.js          # 数据库只读管理路由（管理员）
│   │
│   ├── modules/                 # 业务模块（按功能划分，每个模块含 api/controller/model/rules）
│   │   ├── auth/                # 认证模块（注册/登录/Token/用户信息/用户模型/登录日志）
│   │   ├── security/            # 安全模块（PIN 码设置/验证/修改/锁定）
│   │   ├── account/             # 账务模块
│   │   │   ├── api/             # 路由（流水 index + 余额 balance）
│   │   │   ├── controller/      # 控制器（流水/信用卡/借记卡/余额）
│   │   │   ├── model/           # 数据模型（流水/信用卡/借记卡/余额）
│   │   │   ├── rules/           # 校验规则
│   │   │   └── service/         # 结算服务
│   │   ├── category/            # 分类管理（收支分类 + 银行分类）
│   │   ├── bankCategory/        # 银行分类管理（桌面端独立模块）
│   │   ├── card/                # 银行卡模块（卡片 + 账单 + 还款 + 操作日志）
│   │   ├── user/                # 用户模块
│   │   ├── upload/              # 文件上传模块（Multer + Sharp 图片处理）
│   │   ├── moment/              # 时刻/日记模块
│   │   ├── asset/               # 资产快照与登记模块
│   │   ├── todo/                # 待办日程模块
│   │   ├── work/                # 工作与工资模块
│   │   ├── fixed_asset/         # 固定资产模块（含回收站）
│   │   ├── budget/              # 预算管理模块
│   │   ├── fund/                # 理财投资模块
│   │   ├── recurring/           # 周期支出提醒模块
│   │   ├── dataManager/         # 数据管理模块（备份/导出/导入）
│   │   ├── dashboard/           # 仪表盘模块（桌面端）【未启用】
│   │   ├── resource/            # 文件资源管理模块
│   │   ├── flow/                # 流水详情/日历模块
│   │   ├── geo/                 # 地理位置代理模块
│   │   └── share/               # 公开分享模块
│   │
│   ├── common/                  # 公共模块
│   │   ├── config/
│   │   │   ├── db.js            # MySQL 连接池（mysql2/promise，连接数 10）
│   │   │   └── jwt.js           # JWT 配置
│   │   ├── middleware/           # 中间件（8 个）
│   │   │   ├── securityCheck.js # 安全请求校验（头校验/时间戳反重放/IP/指纹/签名）
│   │   │   ├── authGuard.js     # JWT 认证守卫
│   │   │   ├── pinSecurityGuard.js # PIN 安全守卫（拦截锁定会话）
│   │   │   ├── pinLockGuard.js  # PIN 失败次数锁定
│   │   │   ├── authSecurityData.js # 前端加密数据解密
│   │   │   ├── accountLockCheck.js # 账户锁定检查
│   │   │   ├── emailCodeRateLimit.js # 邮箱验证码频率限制
│   │   │   ├── errorMiddleware.js # 统一错误处理（404 + 500）
│   │   │   └── validate.js      # Joi 校验中间件
│   │   └── utils/               # 工具
│   │       ├── crypto.js        # 加密工具
│   │       ├── idUtils.js       # ID 生成
│   │       ├── mailer.js        # Nodemailer 邮件发送
│   │       └── shareToken.js    # 分享 Token 生成
│   │
│   └── init/                    # 系统初始化
│       ├── index.js             # 初始化入口（全量/增量两种模式）
│       ├── autoDatabase.js      # 数据库自动创建
│       ├── schemaSync.js        # 表结构同步
│       └── migrationRunner.js   # 增量迁移执行器
│
├── mysql/                       # 数据库
│   ├── live.sql                 # 完整数据库结构（当前版本）
│   ├── live_V1.0.0.sql          # V1.0.0 基线
│   ├── live_old_version.sql     # 旧版本备份
│   ├── migrate_visible_type_v2.sql # 迁移脚本
│   └── migrations/              # 增量迁移脚本目录
│
├── data/                        # 数据存储
│   ├── uploads/                 # 上传文件
│   └── sql/                     # SQL 数据文件
│
├── public/                      # 静态资源（管理后台页面）
│   ├── admin-register.html/js   # 管理员注册页面
│   ├── database.html/js         # 数据库管理面板
│   ├── database-init.js         # 数据库初始化脚本
│   └── uploads/                 # 公开上传资源
│
├── tests/                       # 测试
│   ├── setup.js                 # 测试环境配置
│   ├── README.md                # 测试说明文档
│   └── unit/                    # 单元测试
│
├── scripts/                     # 脚本目录
└── logs/                        # 变更日志
    ├── CHANGELOG_2026-05-28.md
    ├── CHANGELOG_2026-05-29.md
    ├── CHANGELOG_2026-05-31.md
    └── CHANGELOG_2026-06-28.md
```

## API 路由总览

所有 API 挂载在 `/api/v1` 下，需经过 `securityCheck` 中间件校验（公开分享和静态资源除外）。

| 前缀 | 模块 | 主要功能 |
|------|------|---------|
| `/api/v1/auth` | auth | 注册、登录、获取用户信息、Token 刷新、系统锁定 |
| `/api/v1/security` | security | PIN 码设置/验证/修改/删除 |
| `/api/v1/account` | account | 收支流水 CRUD、冲正、转账 |
| `/api/v1/accountBalance` | account | 账户余额管理（虚拟账户同步） |
| `/api/v1/category` | category | 收支分类 CRUD |
| `/api/v1/bank` | category | 银行分类 CRUD |
| `/api/v1/bank-category` | bankCategory | 银行分类管理（桌面端独立） |
| `/api/v1/card` | card | 银行卡 CRUD、卡片流水查询 |
| `/api/v1/card/bill` | card | 信用卡账单 CRUD、账单流水明细 |
| `/api/v1/card/repay` | card | 信用卡还款 CRUD |
| `/api/v1/user` | user | 用户资料 CRUD、头像更新 |
| `/api/v1/upload` | upload | 文件/图片上传（支持多文件） |
| `/api/v1/moment` | moment | 动态/日记 CRUD |
| `/api/v1/asset` | asset | 资产快照与登记 |
| `/api/v1/todo` | todo | 待办日程 CRUD |
| `/api/v1/work` | work | 工作信息、工资日历、薪资统计、日薪 |
| `/api/v1/fixedAsset` | fixed_asset | 固定资产 CRUD、回收站 |
| `/api/v1/budget` | budget | 购物/出行/餐饮预算管理 |
| `/api/v1/fund` | fund | 理财产品登记、净值追踪、收益明细 |
| `/api/v1/recurring` | recurring | 周期固定支出提醒 |
| `/api/v1/flow` | flow | 流水详情查询、日历聚合 |
| `/api/v1/data-manager` | dataManager | 数据库检查/备份/导出/导入、登录日志 |
| `/api/v1/resource` | resource | 文件资源列表管理 |
| `/api/v1/geo` | geo | IP 定位、逆地理编码代理 |
| `/api/v1/share` | share | 公开分享查看（免登录，白名单放行） |
| `/api/v1/dashboard` | dashboard | 仪表盘（桌面端）【已注释未启用】 |
| `/api/v1/database` | database | 数据库只读查询（管理员） |
| `/api/public` | 静态资源 | 公开静态文件服务（上传资源） |

## 认证与安全体系

### 认证流程

```
客户端                          服务端
  │                               │
  ├── POST /auth/handshake ──────►│ 交换 AES 密钥（预握手）
  │◄─────── { aesKey, iv } ──────┤
  │                               │
  ├── POST /auth/login (AES加密)─►│ 安全头校验 → JWT 签发
  │◄─────── { token } ────────────┤
  │                               │
  ├── 业务请求 (JWT + 安全头) ────►│ authGuard → pinSecurityGuard
  │◄─────── 数据 ──────────────────┤
  │                               │
  │  (若返回 423)                 │
  ├── POST /security/pin/verify ─►│ PIN 验证解锁
  │◄─────── 200 OK ───────────────┤
```

### 安全特性

| 层级 | 机制 | 说明 |
|------|------|------|
| 传输加密 | AES 预握手 | 首次请求交换 AES 密钥，后续 POST/PUT/DELETE 自动加解密 |
| 请求校验 | securityCheck | 校验 X-Requested-With、时间戳（±2min）、IP 合法性、指纹一致性、MD5 签名 |
| 身份认证 | JWT Token | Bearer Token，authGuard 中间件自动验证 |
| 敏感操作 | PIN 码 | 6 位数字 PIN 码二次验证，失败锁定 |
| 会话锁定 | 423 状态码 | 手动锁定/自动锁定后返回 423，前端引导 PIN 验证 |
| 密码存储 | bcryptjs | 单向哈希加密存储 |
| 安全头 | Helmet | 自动添加安全相关 HTTP 头 |
| CORS | 白名单 | 仅允许配置的前端域名来源 |
| 输入校验 | Joi | 请求参数 Schema 校验 |
| 并发保护 | 事务 + 行锁 | `SELECT ... FOR UPDATE` 防止竞态（冲正/还款） |

### 认证接口 (`/api/v1/auth`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/auth/handshake` | 否 | 预握手，交换 AES 密钥 |
| POST | `/auth/register` | 否 | 用户注册 |
| POST | `/auth/login` | 否 | 用户登录 |
| GET | `/auth/me` | JWT | 获取当前用户信息 |
| POST | `/auth/refresh` | JWT | 刷新 Token |
| POST | `/auth/lock-system` | JWT | 锁定当前会话 |

### 安全接口 (`/api/v1/security`)

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/security/pin/set` | JWT | 设置 PIN 码 |
| POST | `/security/pin/verify` | JWT | 验证 PIN 码 |
| POST | `/security/pin/change` | JWT | 修改 PIN 码 |

## 银行卡业务规则

### 账单周期计算

- **账单周期**: 账单日(bill_day)次日 ~ 次月账单日前一天
- **账单月归属**:
  - 消费日期 **≤** 账单日 → 当月账单（账单日当天仍属当月）
  - 消费日期 **>** 账单日 → 下月账单（开启新周期）
- **还款日**: 账单月的下一月 repayDay
- **逾期判定**: 当前日期 > repay_date 且 need_repay > 0

### 信用卡冲正规则

- 消费冲正: 原支出 → 冲正收入 + 恢复账单额度
- 还款撤销: 原还款 → 冲正收入（恢复余额）+ 软删除还款记录 + 全量重建账单
- 账单周期隔离: 冲正/还款检查仅限同一 `bill_month`，不跨周期误拦
- 并发保护: 事务内 `SELECT ... FOR UPDATE` 行锁，防止重复冲正

### 举例说明（账单日=12号，还款日=次月6号）

| 消费日期 | 归属账单月 | 出账日 | 还款日 |
|---------|-----------|--------|--------|
| 4月1日 ~ 4月12日 | 4月 | 4月12日 | 5月6日 |
| 4月13日 ~ 4月30日 | 5月 | 5月12日 | 6月6日 |

## 技术栈

| 类别 | 技术 | 用途 |
|------|------|------|
| 框架 | Express 4.19.2 | HTTP 服务框架 |
| 数据库 | MySQL (mysql2/promise) | 连接池，连接数 10 |
| 认证 | jsonwebtoken 9.0.2 | JWT Token 签发与验证 |
| 密码加密 | bcryptjs 2.4.3 | 单向哈希密码存储 |
| 会话 | express-session | Session 管理 |
| 安全头 | helmet 7.1.0 | HTTP 安全头 |
| 跨域 | cors 2.8.5 | 白名单 CORS 配置 |
| 日志 | morgan 1.10.0 | HTTP 请求日志 |
| 校验 | joi 17.9.2 | 请求参数 Schema 校验 |
| 文件上传 | multer 1.4.5 | 多文件上传中间件 |
| 图片处理 | sharp 0.33.0 | WebP 转换 + 缩略图生成 |
| 环境变量 | dotenv 16.4.5 | .env 配置加载 |
| 邮件 | nodemailer 6.9.13 | 邮件发送 |
| 数据库备份 | mysqldump 3.2.0 | 数据库导出 |
| 压缩 | adm-zip / archiver | ZIP 归档 |
| 测试 | Jest 29.7.0 | 单元测试 / 集成测试 |
| 热重载 | nodemon 3.1.0 | 开发模式自动重启 |
| 其他 | nanoid, uuid, crypto-js, dayjs, axios | ID 生成、加密、日期处理 |

## 快速开始

### 安装依赖

```bash
npm install
```

### 配置环境变量

```bash
cp .env.example .env
```

主要配置项:

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `PORT` | 服务端口 | 3001 |
| `NODE_ENV` | 运行环境 | development |
| `DB_HOST` | 数据库主机 | localhost |
| `DB_PORT` | 数据库端口 | 3306 |
| `DB_USER` | 数据库用户 | root |
| `DB_PASSWORD` | 数据库密码 | (空) |
| `DB_NAME` | 数据库名 | live |
| `JWT_SECRET` | JWT 签名密钥 | (必填) |
| `FRONTEND_URL` | 前端地址 | (必填，用于 CORS) |
| `APP_SECURITY_SALT` | 请求签名盐 | (必填) |
| `INIT_ENABLE` | 启用全量初始化 | false |
| `INIT_SKIP` | 跳过初始化 | false |

### 创建数据库

```sql
CREATE DATABASE live DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 导入数据库结构

```bash
mysql -u root -p live < mysql/live.sql
```

### 启动服务

```bash
# 开发模式（nodemon 热重载）
npm run dev

# 生产模式
npm start
```

服务启动后自动执行:
1. 数据库连接检测（输出版本和服务器时间）
2. 系统初始化（增量迁移 / INIT_ENABLE=true 时全量）
3. 监听端口输出启动信息

默认访问: `http://localhost:3001/api/v1`

### 运行测试

```bash
npm test                # 运行全部测试
npm run test:unit       # 仅单元测试
npm run test:integration # 仅集成测试
npm run test:coverage   # 生成覆盖率报告
```

## 系统初始化机制

系统启动时通过 `src/init/` 模块自动处理数据库初始化:

| 模式 | 环境变量 | 行为 |
|------|---------|------|
| 增量 | 默认 | 执行 `migrations/` 目录下未执行过的迁移脚本 |
| 全量 | `INIT_ENABLE=true` | 先执行全量 SQL 备份，再同步表结构，最后跑迁移 |
| 跳过 | `INIT_SKIP=true` | 完全跳过初始化，用于排查问题 |

## 代码架构约定

### 模块分层

每个业务模块遵循统一的三层结构:

```
modules/{模块名}/
├── api/index.js        # 路由注册（Express Router）
├── controller/index.js  # 控制器（请求处理、参数提取、响应格式化）
├── model/index.js       # 数据模型（SQL 查询、事务管理）
└── rules/index.js       # 校验规则（Joi Schema）
```

### 统一响应格式

所有接口使用 `res.say(message, statusCode)` 方法统一响应:

```json
{
  "status": 200,
  "message": "操作成功",
  "data": { ... }
}
```

错误时 `message` 为错误描述字符串，生产环境 5xx 错误统一脱敏为 "服务器内部错误"。

---

## 更新日志

| 日期 | 内容 |
|------|------|
| 2026-06-28 | [多项模块优化与修复](logs/CHANGELOG_2026-06-28.md) |
| 2026-05-31 | [信用卡冲正 + 账单一致性修复](logs/CHANGELOG_2026-05-31.md) |
| 2026-05-29 | [定位模块 + 邮箱脱敏](logs/CHANGELOG_2026-05-29.md) |
| 2026-05-28 | [新增模块 + 文档补全](logs/CHANGELOG_2026-05-28.md) |
