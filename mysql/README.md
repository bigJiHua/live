# MySQL 数据库

本目录包含数据库相关的 SQL 文件和表结构说明。

## 文件说明

### live.sql
**完整的数据库表结构**
### live_old_version.sql
**旧的的数据库表结构**
### live_V1.0.0.sql
**完整的V1.0.0版本数据库表结构，只适配V1.0.0版本！**

`live.sql` 共 **24 张表**：

| 表名 | 说明 |
|------|------|
| `user_info` | 用户信息表 |
| `user_log` | 用户登录日志表 |
| `account` | 记账明细表 |
| `account_balance` | 账户余额表 |
| `account_transfer` | 转账记录表 |
| `asset_register` | 资产登记表 |
| `asset_snapshot` | 资产快照表 |
| `budget` | 预算控制表 |
| `bus_category` | 分类字典表 |
| `bus_fund_history` | 理财净值历史表 |
| `bus_recurring` | 周期性计划任务表 |
| `card_base` | 卡片基础信息表 |
| `card_bill` | 卡片额度账单表 |
| `card_log` | 卡片操作日志表 |
| `card_repay` | 信用卡还款记录表 |
| `device_crypto` | 设备密钥表 |
| `fixed_asset` | 固定资产表 |
| `fund` | 理财产品表 |
| `moment` | 动态/日记表 |
| `security_verify_log` | 安全验证日志表 |
| `sys_attachment` | 文件附件索引表 |
| `todo` | 待办事项表 |
| `work_job` | 工作信息表 |
| `work_salary` | 工作薪酬核算表 |

> `_migrations` 表由 `migrationRunner` 运行时自动创建，不在 `live.sql` 中。

---

## 表结构约定

所有表结构以 `live.sql` 为准，统一规范：

- 主键 `id`：`varchar(50)`，通过 `nanoid` 生成业务 ID
- 外键/关联字段：`varchar(50)`
- 时间字段：`varchar(20)` 存储时间戳字符串
- 软删除：`is_deleted` tinyint(4) DEFAULT 0
- 字符集：`utf8mb4`，排序规则 `utf8mb4_unicode_ci`
- 行格式：`ROW_FORMAT=DYNAMIC`（避免 utf8mb4 索引超限）

详细建表语句请直接查看 `live.sql` 文件。

---

## 迁移脚本

`migrations/` 目录下存放增量迁移脚本，按编号顺序执行：

| 脚本 | 说明 |
|------|------|
| `001_alter_account.sql` | account 表新增 `transfer_group_id`、`reversed_id` 字段及索引 |
| `002_alter_bus_fund_history.sql` | bus_fund_history 表 `fund_id`/`id` 改为 varchar(50) |
| `003_alter_fund.sql` | fund 表 `user_id`/`id` 改为 varchar(50)，新增 `invest` 投入本金字段 |
| `004_alter_bus_recurring.sql` | bus_recurring 表补齐 `remind_days`/`remark`/`month_records`/`update_time`/`is_deleted` |
| `005_alter_id_varchar50.sql` | 18 张表 id 字段统一从 varchar(32) 加长为 varchar(50) |
| `006_alter_moment_visible_type.sql` | moment 表 `visible_type` tinyint(4)→varchar(255) JSON + `_migrations` 表补齐 ROW_FORMAT=DYNAMIC |
| `009_credit_pool.sql` | card_base 补充 `share_pool_id` 列 + 新建 `card_credit_pool` 共享额度池表（信用卡重构补充结构） |
| `010_card_bill_columns.sql` | card_bill 补充 `bill_day` / `repay_day` 冗余列（CreditCore.syncCardBills 写入所需） |
| `011_card_foreign_register.sql` | 新建 `card_foreign_register` 外币消费登记/对账表（痛点4，此前仅靠运行时自愈，现改严格迁移） |
| `012_card_bill_create_time.sql` | card_bill 补充 `create_time` 列（CreditCore.syncCardBills INSERT 写入所需） |
| `013_card_repay_repay_card_id.sql` | card_repay.repay_card_id varchar(20)→varchar(50) |
| `014_card_foreign_register_unique.sql` | card_foreign_register 新增唯一键 `uk_account_id`（防重复登记） |
| `015_card_repay_account_id_nullable.sql` | card_repay.account_id 允许 NULL（本卡还款） |
| `016_credit_pool_credit_report_merged.sql` | card_credit_pool 新增 `credit_report_merged` 信报合一标记（开启后支持合并还款） |

> 以上迁移变更均已合入 `live.sql`，新环境直接导入 `live.sql` 即可。迁移脚本对已升级环境为幂等（重复执行自动跳过）。

---

## 更新日志

### 2026-06-30
- 修正表列表，移除不存在的 `asset`/`app_config`/`login_log`，补充 `asset_register`/`asset_snapshot`/`account_balance`/`work_job`/`user_log`
- 移除过时的 SQL 示例（旧 INT/VARCHAR(32) 格式），改用以 `live.sql` 为准的结构约定

### 5月28日
- 更新 `live.sql` 核心表结构，统一主键和外键 `varchar` 字段长度为 50。
- 新增多张业务表（`fund`、`bus_fund_history`、`bus_recurring`、`account_transfer`、`device_crypto`、`security_verify_log` 等），共计 24 张表。
