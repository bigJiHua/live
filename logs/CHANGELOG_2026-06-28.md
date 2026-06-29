# 变更日志

## 2026-06-28（lock-system 锁定状态判断 + PIN 验证闭环修复）

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `src/modules/auth/controller/index.js` | `lockSystem()` 锁定检查加上 `action_type = 'lock'` 条件，从 `WHERE pin_status = 0` 改为 `WHERE action_type = 'lock' AND pin_status = 0`，与 `pinSecurityGuard` 分支1.5 的判断逻辑保持一致 |
| `src/common/middleware/pinSecurityGuard.js` | (1) 新增 `/lock-system` 路径绕过规则，避免锁定请求被 PIN 守卫拦截；(2) 分支1.5 中对风险路由验证目标放行给 `pinLockGuard`；(3) `markSuccess` 增加系统软锁解除逻辑；(4) 30秒放行窗口增加重置机制 |
| `src/modules/security/controller/index.js` | `verifyRoutePin` 验证成功时同步解除系统软锁定（`action_type='lock'` 记录） |

### ✅ 修复明细

| 问题 | 修复 |
|------|------|
| 调用 `/auth/lock-system` 提示"系统已经是锁定状态"但实际未锁定 | `lockSystem` 控制器查询条件与 `pinSecurityGuard` 守卫的条件不一致：控制器匹配任意 `pin_status=0` 记录，守卫仅匹配 `action_type='lock' && pin_status=0`。加上 `action_type = 'lock'` 过滤修复 |
| 设置 PIN 的用户锁定接口被 `pinSecurityGuard` 拦截 | `pinSecurityGuard` 在 Branch 2 中会拦截无 `pin_status=1` 记录的请求并返回"请输入 PIN"，导致 `lockSystem` 控制器无法到达。新增 `/lock-system` 跳过规则修复 |
| 风险路由触发验证时返回通用 8303 覆盖 route_verify 的 challengeId | 分支1.5 中 `routeVerifyTarget` 命中时直接放行给 `pinLockGuard`，让它返回带 `challengeId` 的 8303 |
| 普通 PIN 验证后系统软锁无法解除导致死循环 | `markSuccess` 增加更新：`action_type='lock' AND pin_status=0` 的记录同时标记为已解锁 |
| 风险路由 PIN 验证成功时系统软锁未解除 | `verifyRoutePin` 验证成功后追加 SQL：`UPDATE security_verify_log SET pin_status=1, remark='系统已解锁' WHERE user_id=? AND action_type='lock' AND pin_status=0` |
| 30秒放行窗口的 `count` 累加导致旧会话污染新会话 | 在 `verifiedMap` 检查时增加判断：超过 30 秒则重置 `count=0` 和 `verifiedAt=now` |

### 🔒 安全

- 锁定判断逻辑收敛为统一条件：`action_type = 'lock' AND pin_status = 0`
- `/lock-system` 路由仍需 `authGuard` 认证，仅跳过 `pinSecurityGuard` 的全局 PIN 拦截
- 系统软锁解除仅发生在 `verifyPin` 或 `verifyRoutePin` 验证成功后（不会绕过验证）
- 风险路由验证仍需在 challenge 5 分钟有效期内完成

### 📌 完整验证闭环

| 场景 | 流程 | 结果 |
|------|------|------|
| 锁定后普通请求 | 8303 "操作受限" → 用户输入 PIN → `markSuccess` 清除 lock 记录 | ✓ 解除锁定 |
| 锁定后风险路由 | `pinLockGuard` 创建 challenge → 8303 带 challengeId → 用户输入 PIN → 清除 lock + 创建 token | ✓ 解除锁定 |
| 锁定后再次调用 lock-system | 控制器检查发现已存在 lock 记录 → 返回"系统已经是锁定状态" | ✓ 幂等 |
| 30秒内高频请求 | `verifiedMap` 重新计数（防止旧会话污染） | ✓ 不会立即重验 |

### 📊 统计

- 修改文件：3（auth/controller、middleware/pinSecurityGuard、security/controller）
- 问题修复：6（查询条件、路由拦截、风险路由挑战、普通 PIN 解锁、风险路由解锁、放行窗口）

---

## 2026-06-28（数据导出异步任务化 + 自动下载移除）

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `api/src/modules/dataManager/service/exportTask.js` | 完全重写异步任务管理器。去掉 `isLargeTable` 判断，所有导出/备份统一走异步；新增 `hasRunningTask(type)` / `getRunningTask(type)` 做并发保护；新增 `createSystemBackupTask()` 把系统备份也纳入任务队列；ZIP 压缩逻辑从 `BackupService` 整合进来；用 `archiver('zip', opts)` 新 API |
| `api/src/modules/dataManager/controller/index.js` | 重写 `exportSingleTable` / `exportFullDatabase` / `createSystemBackup` 三个方法：先检查并发，返回 202 + taskId，立即响应不阻塞；`getExportTaskStatus` 返回结构增加 `type` 字段 |
| `web/src/views/Finance/data/DbExport.vue` | 改为完全异步模式：点击提交后立刻拿到 taskId → 5 秒轮询 → 完成后切到结果区由用户手动下载。修复 `isAsyncTask` 未切回 `false` 导致结果区不显示的 bug；`manualRefreshStatus` 重写避免 `stopPolling → startPolling → stopPolling` 三连调用；`handleDownload` 加 `res.data instanceof Blob` 兼容和 `setTimeout(revoke, 1000)` 防止浏览器下载失败；模板 `v-if="exportCompleted && !isAsyncTask"` 改为 `v-if="exportCompleted"` 让进度区和结果区不再互斥 |
| `web/src/views/Finance/data/DbBackup.vue` | 系统备份从同步改为异步轮询；备份进行时按钮 disabled 防重入；新增进度条 + "刷新备份列表" 按钮；`onUnmounted` 清理定时器 |

### ✅ 修复明细

| 问题 | 修复 |
|------|------|
| 生产环境点"完整备份"返回 nginx 404 | 后端 `mysqldump` 同步执行导致 nginx `proxy_read_timeout` 超时连接断开。改为接收请求立即返回 taskId，后台异步执行 |
| 异步模式下前端不知道何时完成 | 5 秒轮询 `GET /export/task/:taskId`，状态机为 `pending → running → completed/failed/cancelled` |
| 用户疯狂点"确认导出"会启动多个 mysqldump 进程把数据库打死 | 后端 `hasRunningTask(type)` 检查同类型任务，命中则复用现有 taskId 返回 `reuseExisting: true` |
| 轮询太频繁影响性能 | 2 秒 → 5 秒轮询；提供"手动刷新"按钮兜底 |
| `startPolling` 完成时 `isAsyncTask` 没切回 `false` | 三个完成分支（completed/failed/cancelled）都加 `isAsyncTask.value = false` |
| `manualRefreshStatus` 里 `stopPolling → startPolling → stopPolling` 三连调用没意义 | 改为直接读取 task 状态，命中终态则执行对应分支；非终态则只 toast 当前进度 |
| 自动触发下载体验不佳（用户没准备好就被打断） | 移除所有 `setTimeout(handleDownload, 500)` 自动下载；toast 文案改为"导出完成，请点击下载"；移除 `autoDownload` 状态变量和模板里的开关 |
| `handleDownload` 用 `URL.revokeObjectURL` 太快可能让浏览器下载失败 | `setTimeout(() => revoke, 1000)` 延迟释放 |

### 🏗️ 架构变更

```
之前：前端请求 → 后端同步 mysqldump（阻塞，nginx 超时 → 404）
现在：前端请求 → 后端立即返回 taskId（HTTP 202）→ 后台异步执行 → 前端 5 秒轮询 → 完成后手动触发下载
```

**任务状态机**（后端权威，前端信任）：

```
pending  →  running  →  completed   (成功，task.result 有 filename)
                  ↘   failed      (失败，task.error 有错误信息)
                  ↘   cancelled   (用户主动取消)
```

**前端 UI 状态**（4 种）：

| 状态 | `isAsyncTask` | `exportCompleted` | 模板显示 |
|------|---------------|-------------------|----------|
| 初始 | false | false | 表单（导出按钮可点） |
| 提交后等待 | true | false | 进度区（"后台导出中" + 进度条 + 手动刷新） |
| 完成/失败/取消 | false | true | 结果区（文件名/大小/时间/下载链接） |
| 再次提交 | 重置 false | 重置 false | 回到初始 |

### 🔒 安全

- 并发保护：同类型任务同时只跑一个，避免 `mysqldump` 进程并发把数据库打死
- 文件下载仍走 `authGuard` 鉴权，文件名校验防止路径穿越（`..` / `/` / `\`）
- 系统备份任务仍受每日 3 次上限保护（任务内执行时检查并标记 FAILED）

### 📊 统计

- 修改文件：4（1 后端 service、1 后端 controller、2 前端页面）
- 新增 API：0（复用现有 `task/:taskId` 状态查询接口）
- 性能优化：轮询 2s → 5s，请求量降低 60%
- 问题修复：8（生产 404、轮询机制、并发保护、UI 状态、自动下载、UI 互斥、清理逻辑、下载失败）

---

## 2026-06-28（流水列表 keep-alive 数据更新冲突修复）

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `web/src/layout/MainLayout.vue` | `cachedPages` 移除 `Home` / `Finance`（两个被缓存的 Tabbar 主页 `onActivated` 不刷新数据，导致返回时显示陈旧数据）。添加注释明确"include 匹配的是组件 name（来自 `defineOptions`）而非路由 name" |
| `web/src/views/Home/index.vue` | 清理已失效的 `onActivated` / `onDeactivated` / `nextTick` 导入与 `savedScrollY` 变量（已退出 keep-alive 缓存，这些钩子不再触发，属于死代码） |
| `web/src/stores/flowSync.js` | 新增 `needsRefresh` state + `markListRefresh()` / `consumeListRefresh()` action。解决"Detail 走 patch 通道"和"Add 走整页刷新通道"的冲突：用同一个 store 区分两种信号 |
| `web/src/views/Finance/flow/List.vue` | `onActivated` 优先消费 `needsRefresh` 信号：命中则 `onRefresh()` 整页重载并回到顶部展示新增数据；未命中保持原"URL 参数同步 + 原地 patch + 恢复滚动位置"逻辑 |
| `web/src/views/Finance/account/Add.vue` | 引入 `useFlowSyncStore`，提交成功后 `markListRefresh()` 打标，再 `router.back()` 返回列表 |
| `web/src/views/BankCard/card/flow/List.vue` | 同步 `onActivated` 消费 `needsRefresh` 机制（`Add.vue` 已统一打标，无需修改）。整页刷新用 `loadList(true)`，明细修改用原地 patch |

### ✅ 修复明细

| 问题 | 修复 |
|------|------|
| `/home` 切到其他页面录入数据后返回，首页数据不更新 | `Home` 移出 `cachedPages`，从其他页返回时组件重新挂载触发 `onMounted` → `loadHomeData` / `loadReminder` / `loadTodaySalary` 全部重新拉取 |
| `/finance`（账本主页）从 `/finance/flow` 等页返回永远显示初始月度数据 | `Finance` 移出 `cachedPages`，从其他页返回强制重载 |
| 路由 `name: "FinanceFlow"` 与 `cachedPages` 里的 `"FinanceFlowList"`（组件名）命名不一致，靠记忆维护容易静默失效 | 在 `cachedPages` 旁加注释固化"组件 name vs 路由 name"区别，并标注 `FinanceFlowList 实际是组件 name，对应路由 name = FinanceFlow`，避免后续维护踩坑 |
| `/finance/flow` 从 `/finance/add` 提交成功返回后看不到刚录入的流水（list 不刷新） | 引入 `flowSync.needsRefresh` 信号。`Add.vue` 提交成功后 `markListRefresh()`；`List.vue` 的 `onActivated` 优先 `consumeListRefresh()`，命中则 `onRefresh()` 整页重载，回到顶部展示新数据 |
| 与"从明细返回要保留滚动位置和 patch 变更"的需求冲突 | 用 `consumeListRefresh()` 区分两种入口：(1) needsRefresh=true 走整页刷新（Add 提交）；(2) needsRefresh=false 走原地 patch + 恢复滚动（Detail 修改）。两套逻辑在同一 `onActivated` 中互斥 |
| `/card/flow`（卡片流水）也有同样冲突，但 `Add.vue` 没有专属路径 | 复用同一信号：Add.vue 提交后 `markListRefresh()` 已经被全局化，`CardFlowList.onActivated` 同步消费即可，无需在 `Add.vue` 加分支判断 |

### 🏗️ 架构变更

```
之前：流水列表 keep-alive 缓存
   Detail 修改 → flowSync.changes[id] = patch → 列表 onActivated → consumeChanges 原地 patch
   Add 提交    → （无信号）→ 列表 onActivated → 无任何操作 → 显示旧数据 ❌

现在：流水列表 keep-alive 缓存 + needsRefresh 双通道
   Detail 修改 → flowSync.changes[id] = patch                              → 列表 patch
   Add 提交    → flowSync.needsRefresh = true                              → 列表 onRefresh
   列表 onActivated 优先级：
     1) needsRefresh=true  → onRefresh() 整页重载，回顶部
     2) needsRefresh=false → 原 URL 参数同步 + 原地 patch + 恢复滚动
```

**信号消费契约**（`flowSync` store）：

| Action | 调用方 | 语义 |
|--------|--------|------|
| `recordChange(id, patch)` | `Detail.vue` 保存时 | 记录某条流水的字段变更 |
| `consumeChanges()` | `List.vue` / `CardFlowList.vue` 的 `onActivated` | 取走所有 patch 并清空，原地 patch 到本地 list |
| `markListRefresh()` | `Add.vue` 提交成功 | 标记列表需要整页刷新（一次有效） |
| `consumeListRefresh()` | `List.vue` / `CardFlowList.vue` 的 `onActivated` | 取走刷新标记，返回 boolean |

### 🔒 安全

- `needsRefresh` 是一次性信号：`consumeListRefresh()` 消费后立即置 `false`，不会污染下一次进入
- 整页刷新分支显式 `consumeChanges()` 清空 patch：避免"刚拉回来的新数据被旧 patch 覆盖"
- `clear()` action 同时清空 `changes` 和 `needsRefresh`，作为异常场景兜底

### 📊 统计

- 修改文件：6（1 layout、1 主页、1 store、1 列表页、1 Add 页、1 卡片流水页）
- 新增 store action：2（`markListRefresh` / `consumeListRefresh`）
- 新增 store state：1（`needsRefresh`）
- 问题修复：5（首页数据、账本数据、命名冲突、Add 后列表不刷新、CardFlowList 同步）
- 保留行为：Detail 返回时滚动位置 + patch 变更同步（不受影响）

---

## 2026-06-28（流水列表 6 种状态块全景改造）

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `web/src/views/Finance/flow/List.vue` | (1) 修复提现永远进不到 green 样式的 bug：第 1 趟 `transfer_group_id` 配对时识别 `isWithdrawal=yyyy→卡`；(2) 识别 `isReversal=reversed_id` 字段；(3) 冲正放宽为双层判断（pay_type='冲正' 强信号 + 5 分钟时间接近兜底）；(4) 新增 `incoming-transfer` 类型（收入 + pay_type='转账' 单边 → 蓝虚线"给我转账"块）；(5) `external-transfer` 保留为支出单边（卡片+橙徽标"对外转账"）；(6) 提现/冲正 改为和 transfer 一样的点击展开/收起子列表（不再盲目跳详情）；(7) 下拉刷新拆为 `loadTodayOnly`（只拉今天，patch 到 list 头部）+ `loadSummary`（顶部月度统计），不影响整月分页状态；(8) `onRefresh` 触发时受 `isCurrentMonth` 限制（仅当月可下拉，非当月 `:disabled`） |
| `web/src/views/Finance/report/flow/FlowFilter.vue` | 同步 List.vue 的全部配对判断逻辑：(1) 新增第 0 趟 `transfer_group_id` 配对（识别 isWithdrawal/isReversal）；(2) 重写第 1 趟冲正为双层判断（pay_type='冲正' 强信号 + 5 分钟兜底）；(3) 全部 5 趟配对 + `match` 内 `find` 统一加 `CATEGORY_REPAY` 排除 |

### ✅ 修复明细

| 问题 | 修复 |
|------|------|
| 余额提现的 6 笔流水被第 1 趟 `transfer_group_id` 配对后一律归为 `transfer`（"转账"），提现绿色虚线视觉永远进不到 | 第 1 趟配对时增加 `isWithdrawal = getCard(expense) === 'yyyy' && !isVirtual(getCard(income))` 识别，按 `pair.isWithdrawal` 渲染为 `withdrawal` 绿色虚线 |
| 后端 `reverseTransfer` 接口创建的冲正（写 `transfer_group_id` + `reversed_id`）被第 1 趟配对为 `transfer`，绕过了原有第 1.5 趟"冲正"识别 | 第 1 趟配对时增加 `isReversal = !!(expense.reversed_id \|\| income.reversed_id)` 识别，按 `pair.isReversal` 渲染为 `reversal` 灰色虚线 |
| 第 1.5 趟冲正要求 `inc.pay_type === '冲正'` 才能识别，但手工记账不会主动选这个分类，导致大量真实冲正漏判 | 改成双层判断：(1) `incCat === '冲正'` 为强信号直接锁定；(2) 兜底依赖"同额度 + 信用卡支出 + 虚拟卡收入 + 时间接近（5 分钟内）" |
| 工资入账 5000 + 信用卡还款 5000 同日同额度被识别为"冲正"（误判） | 5 分钟时间接近限制过滤掉工资等定期入账（强信号 pay_type='冲正' 不受时间限制） |
| 提现/冲正块点击直接 `goDetail(item.expense)` 跳到支出方详情页，看不到收入方 | 改为 `toggleTransferExpand(item.expense.id)` 点击展开/收起子列表，子项点击才进详情，复用现有 `expandedTransferIds` Set |
| 非当月（如查看 2026-05）时下拉刷新拉的是今天的数据，与历史月份无关，造成无意义请求 | `van-pull-refresh` 加 `:disabled="!isCurrentMonth"` 绑定：当前年月等于今日年月才可下拉 |
| 下拉刷新拉整月分页数据（`loadData(true)`），破坏用户已经浏览过的整月历史 | 拆为 `loadTodayOnly()`（只拉今天）+ `loadSummary()`（顶部月度统计），原地 patch `list.value`；不重置 page/finished |
| `loadData` 在下拉刷新期间被 `if (refreshing.value) return` 误伤（下拉刷新触发时 `refreshing=true` 已被 `van-pull-refresh` 置上，导致 `loadData` 直接 return） | 移除 `isRefresh` 参数（之前误判为下拉刷新专属），`loadData` 始终受 `refreshing` 守卫保护；下拉刷新改走 `loadTodayOnly` 完全独立的逻辑 |
| `onRefresh` 错误地同时控制 `loading` 和 `refreshing` 两个状态，与 `van-list` 内部管理的 `loading` 冲突 | `onRefresh` 只控制 `refreshing` 一个状态；`Promise.all().finally()` 用 `nextTick` 包住 `refreshing=false`，等 DOM 更新再关闭下拉指示器避免瞬闪 |
| `Finance/report/flow/FlowFilter.vue` 与 `Finance/flow/List.vue` 配对规则不同步，提现/冲正识别不一致 | FlowFilter 全部 5 趟配对按 List.vue 同步，新增 `transfer_group_id` 配对 + 冲正双层判断 + 5 分钟时间限制 + `CATEGORY_REPAY` 统一排除 |

### 🏗️ 6 种状态块全景

| `type` | 触发条件 | 视觉 | 标签 | 点击行为 |
|--------|----------|------|------|----------|
| `transfer` | 第 1-4 趟配对成功 | 蓝虚线三层 | "转账" / "疑似转账"（按 isExplicit）| 展开/收起子列表 |
| `withdrawal` | 提现：余额卡(yyyy)→实体卡，group_id 配对 | 绿虚线三层 | "提现" | 展开/收起子列表 |
| `reversal` | 冲正：信用卡-N + 虚拟卡+N（同额度）| 灰虚线三层 | "冲正" | 展开/收起子列表 |
| `external-transfer` | 支出 + `pay_type='转账'` + 单边 | 普通卡片 + 橙徽标（右上角）| "对外转账" | 跳详情 |
| `incoming-transfer` | 收入 + `pay_type='转账'` + 单边 | 普通卡片 + 绿徽标（右上角）| "给我转账" | 跳详情 |
| `flow` | 其他单条流水 | 普通卡片 | — | 跳详情 |

**配对 5 趟优先级**（按顺序执行）：

```
第 0/1 趟：transfer_group_id 配对（后端明确分组）
   ├── reversed_id 命中 → reversal（灰虚线）
   ├── yyyy→实体卡     → withdrawal（绿虚线）
   └── 普通转账        → transfer（蓝虚线）

第 1 趟：冲正兜底
   ├── pay_type='冲正' 强信号（直接锁定）
   └── 同额度+信用卡+虚拟卡+5分钟 → reversal

第 2 趟：双方 pay_type='转账' → transfer
第 3 趟：支出"其他支出" + 收入"其他收入" → 疑似 transfer
第 4 趟：兜底（同额度+不同卡，排除 yyyy→卡）→ 疑似 transfer
第 5 趟：yyyy→实体卡 同额度（兜底无 group_id 的提现）→ withdrawal
```

### 🔒 兼容性

- 6 种状态块判定对所有存量流水**完全向后兼容**：未配对的单边流水仍走 `flow` 普通卡片展示
- `expandedTransferIds` Set 同时被 transfer/withdrawal/reversal 三种 type 复用，存储 `item.expense.id` 即可
- `isCurrentMonth` 实时响应 `currentYear`/`currentMonth` 变化，月份切换自动启用/禁用下拉刷新
- `loadTodayOnly` 仅 patch 当天流水，不影响其他日期数据，**保留用户滚动位置**

### 📊 统计

- 修改文件：2（List.vue 主战场，FlowFilter.vue 同步）
- 新增 type：1（`incoming-transfer`）
- 冲正判断：1 → 2（增加 pay_type 强信号 + 5 分钟时间限制）
- 下拉刷新：拉整月 → 只拉今天
- 配对块点击行为：3 → 1（transfer/withdrawal/reversal 统一为展开/收起）
- 同步页：1（FlowFilter；Calendar 暂未同步，按用户要求）

