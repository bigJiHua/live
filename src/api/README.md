# API 路由层

本目录包含所有 API 路由入口文件，负责定义 HTTP 端点并将请求分发到相应的控制器。

## 路由注册

所有路由统一在 `src/api/index.js` 中注册，最终 API 前缀为 `/api/v1`。

## 完整路由清单

| 前缀 | 文件 | 模块 | 说明 |
|------|------|------|------|
| `/api/v1/auth` | auth | 认证模块 | 用户注册、登录、Token |
| `/api/v1/security` | security | 安全模块 | PIN码设置/验证/修改 |
| `/api/v1/account` | account | 账务模块 | 收支流水管理 |
| `/api/v1/accountBalance` | account | 账务模块 | 账户余额管理 |
| `/api/v1/category` | category | 分类模块 | 收支分类管理 |
| `/api/v1/bank` | category | 分类模块 | 银行分类管理 |
| `/api/v1/card` | card | 银行卡模块 | 卡片管理 |
| `/api/v1/card/bill` | card | 银行卡模块 | 信用卡账单管理 |
| `/api/v1/card/repay` | card | 银行卡模块 | 信用卡还款管理 |
| `/api/v1/user` | user | 用户模块 | 用户管理 |
| `/api/v1/database` | database | 数据库模块 | 数据库状态查询 |
| `/api/v1/upload` | upload | 上传模块 | 文件上传 |
| `/api/v1/moment` | moment | 时刻模块 | 动态/日记 |
| `/api/v1/asset` | asset | 资产模块 | 资产快照与登记 |
| `/api/v1/todo` | todo | 待办模块 | 待办日程 |
| `/api/v1/work` | work | 工作模块 | 工作与工资 |
| `/api/v1/fixedAsset` | fixed_asset | 固定资产模块 | 固定资产管理 |
| `/api/v1/budget` | budget | 预算模块 | 预算管理 |

## 认证机制

除 `/api/v1/auth` 和 `/api/v1/security` 部分接口外，其他所有接口都需要：

1. **JWT Token** 验证 - 通过 `authGuard` 中间件
2. **PIN 码验证** - 通过 `pinLockGuard` 中间件

### 中间件说明

| 中间件 | 说明 |
|--------|------|
| `authGuard` | 验证 JWT Token，将用户信息添加到请求对象 |
| `pinLockGuard` | 检查会话是否需要 PIN 验证，未验证时返回 423 状态码 |

### 状态码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
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
