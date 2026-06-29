# 变更日志

## 2026-05-31（信用卡冲正 + 账单一致性修复）

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `src/modules/account/model/index.js` | 信用卡消费冲正：补全账单周期校验（billDay检查、账单存在检查、同周期还款拦截）；核心操作加事务+`SELECT FOR UPDATE`防并发重复冲正；`update()`对信用卡支出改金额/日期时事务内回滚旧账单+同步新账单 |
| `src/modules/account/model/index.js` | 信用卡还款撤销冲正：INSERT+DELETE+card_repay操作包裹事务+`SELECT FOR UPDATE`防并发；rebuildBillFromAccount 保持在事务外（全量重建不宜锁表） |
| `src/modules/card/model/bill.js` | `rollbackRepay` 补齐 `executor = db` 参数，与 `rollbackExpense` 对称支持外部传入事务连接 |

### ✅ 修复明细

| 问题 | 修复 |
|------|------|
| 还款检查跨周期误拦 | 仅检查原消费所属 `bill_month` 的还款记录，不再查全卡历史 |
| 账单不存在 → 冲正静默跳过 | 冲正前校验账单存在性；`update()` 中 rollbackExpense 失败则事务整体回滚 |
| 并发冲正 TOCTOU 竞态 | 事务内 `SELECT ... FOR UPDATE` 行锁 + 重新校验 `reversed_id` |
| `update()` 改信用卡金额后账单不同步 | 事务内 rollbackExpense 旧 → UPDATE 流水 → syncFromExpense 新 |
| `rollbackRepay` 缺少事务支持 | 新增可选 `executor` 参数，与事务内调用对齐 |
| `reverseCreditExpenseById` 返回已删除记录 | 改为返回新建冲正记录 ID |

### 🔒 安全

- 信用卡冲正：`card_type !== 'credit'` 提前拦截
- 信用卡冲正：`direction !== 0`（非支出流水）提前拦截
- 还款撤销：`category_id !== 'CATEGORY_REPAY'` 提前拦截
- 借记卡路径完全不受影响（`update()` 仅在 `account_type === 'credit' && direction === 0` 走事务分支）

### 📊 统计

- 修改文件：3（account/model、card/model/bill、account/controller）
- 新增校验：card_type、direction、billDay、账单存在、同周期还款、FOR UPDATE 行锁
- 问题修复：6
