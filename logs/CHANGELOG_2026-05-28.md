# 变更日志

## 2026-05-28

> 基于 Git 提交 `874d366`「修复已知问题」及当日的文档补全工作。

### 🆕 新增模块（代码层）

| 模块 | 路由前缀 | 说明 |
|------|----------|------|
| `bankCategory` | `/api/v1/bank-category` | 桌面端独立银行分类管理，使用 `bus_category` 表 `type='bank'` 数据 |
| `dashboard` | `/api/v1/dashboard` | 桌面端仪表盘 v1（精简）/ v2（完整含预算+待办） |
| `flow` | `/api/v1/flow` | 流水日历（按月汇总）+ 单条流水详情 |
| `resource` | `/api/v1/resource` | 桌面端文件资源管理（列表 + 硬删除） |

### 🆕 新增模块（文档补全）

以下模块代码先前已存在，本次补充完整 API 文档：

| 模块 | 路由前缀 | 说明 |
|------|----------|------|
| `fund` | `/api/v1/fund` | 理财投资（基金 CRUD + 净值历史管理，10 个接口） |
| `recurring` | `/api/v1/recurring` | 固定周期支出提醒（7 个接口） |
| `dataManager` | `/api/v1/data-manager` | 数据备份/导出/JSON导入/SQL导入（16 个接口） |
| `security` | `/api/v1/security` | PIN 码管理（状态/验证/设置/修改/重置/风险路由验证，6 个接口） |
| `user` | `/api/v1/user` | 用户信息/设置/邮箱验证码/修改邮箱/修改密码（6 个接口） |

### ❌ 删除

| 文件 | 说明 |
|------|------|
| `src/common/controller/financeController.js` | 已被各业务模块独立 Controller 取代 |
| `src/common/model/transaction.js` | 已被各业务模块独立 Model 取代 |

### 🔧 修改

| 模块/文件 | 变更内容 |
|-----------|----------|
| `src/modules/account/api/index.js` | 新增 `PUT /:id` 更新收支、`POST /:id/reverse/transfer` 转账冲正、`GET /transfer/list` 转账列表 |
| `src/modules/upload/model/attachment.js` | 上传响应新增 `busId` 字段返回 |
| `src/modules/work/api/index.js` | 安全策略调整：移除全局 `pinLockGuard`，仅在 `DELETE /job/:id` 和 `DELETE /salary` 单独施加 PIN 验证 |
| `src/modules/work/README.md` | `id`/`job_id` 字段类型从 `varchar(32)` 统一为 `varchar(50)` |
| `src/common/config/db.js` | 连接池启用 `enableKeepAlive`，提升长连接稳定性 |
| `src/common/middleware/authSecurityData.js` | AES-CBC 解密中间件稳定性修复 |
| `src/common/middleware/pinLockGuard.js` | PIN Token 过期判定 + 风险路由校验逻辑修复 |
| `src/common/middleware/pinSecurityGuard.js` | 滑动窗口频率限制参数调整 |
| `src/common/middleware/errorMiddleware.js` | 统一错误响应格式 |
| `src/common/utils/idUtils.js` | ID 生成策略统一：`userId()` 50位 / `shortId()` 47位 / `billId()` 50位 |
| `src/init/index.js` | Schema 同步健壮性增强 |
| `src/init/migrationRunner.js` | 新增增量迁移追踪（`_migrations` 表） |
| `src/init/schemaSync.js` | 新增 Schema 同步（备份 + 对比 + 修复） |
| `mysql/live.sql` | 更新至 24 张表，统一 `varchar(50)` |
| `src/modules/asset/model/` | 重构 register/snapshot 模型导出 |
| `src/modules/card/controller/billController.js` | 账单重建逻辑修复 |
| `src/modules/card/model/bill.js` | 账单 Model 重构优化 |
| `src/modules/category/model/bank.js` | 银行分类 CRUD 逻辑统一 |
| 其他业务模块 | account/asset/auth/budget/category/fixed_asset/moment/todo/upload Controller/Model 层优化 |

### 📝 文档

| 文件 | 说明 |
|------|------|
| 各模块 `README.md` | 补充/更新至 32 个文档，含完整接口详情 |
| `.gitignore` | 新增 `.init.flag` 忽略规则 |

### 📊 统计

- 提交涉及 **76 个文件**，**+7500 / -2130 行**
- 项目共 **24 张表**、**21 个路由前缀**、**32 个 README 文档**
