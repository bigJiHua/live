# Web 前端功能与样式审计 · 逐页面重构参考

> 用途：为 `APP/`（Jetpack Compose 原生重写）提供「web 原有功能 + 样式」的完整逐页面参考，便于后续按页重构 UI。
> 审计对象：`d:/Code/live/web/`（Vue3 + Vant 4.9.22，全量引入），路由表 `web/src/router/map.js`。
> 审计日期：2026-09-15。共覆盖约 95 个页面 + 18 个通用基础组件。
> 约定：web 已自研一套主题化基础组件（`app-*`）几乎替代 Vant 视觉组件，仅少量借用 `van-icon/password-input/notice-bar/swipe`。所有颜色走 `--theme-*` / `--van-*` CSS 变量，天然支持深色。

---

## 0. 速读指南

- **主题规范**：见 §1（CSS 变量全表，原生侧映射为 `VantColors`/`VantDarkColors` + 自定义 `LocalAppColors`）。
- **通用组件库**：见 §2（base 9 件 + 弹层/键盘/日历/卡组织/银行图标，原生侧对应关系）。
- **逐页面**：按模块 §3–§17，每页含「所属布局 / 功能 / 区块结构 / API / 交互 / 样式要点」。
- **web→原生对照**：见 §18（路由常量映射 + 已实现/占位状态）。
- **横切要点**：见 §19（图表、安全键盘、keep-alive、金额色、脱敏）。

---

## 1. 主题与样式规范（CSS 变量全表）

来源 `web/src/assets/css/style.css`，运行时由 `useUiTheme`（`buildVars`）按系统深色 / 用户选择覆盖。原生侧 `vant-ui` 已 1:1 复刻（primary `#3a66e0` 等）。

### 1.1 核心配色（`:root`）
| 变量 | 值 | 用途 |
|---|---|---|
| `--app-primary` | `#3a66e0` | 品牌主色（别名 `--theme-primary`） |
| `--app-expense` | `#ff9900` | 状态色·支出（亮色+白字） |
| `--app-income` | `#07c160` | 状态色·收入 |
| `--app-bg` / `--app-card-bg` | `#f7f8fa` / `#fff` | 页面底 / 卡片底 |
| `--app-text-main` | `#323233` | 主文字 |
| `--app-radius` | `12px` | 通用圆角 |
| `--app-shadow` | `0 4px 16px rgba(0,0,0,.06)` | 通用阴影 |
| `--app-font-number` | `'DIN Alternate','PingFang SC',…` | 数字字体族 |

### 1.2 语义主题变量
| 变量 | 值 |
|---|---|
| `--theme-primary` / `-grad` / `-light` / `-rgb` | `#3a66e0` / `#2a4fb8` / `#eaf0fd` / `58,102,224` |
| `--theme-secondary` | `#1989fa` |
| `--theme-success` | `#07c160` |
| `--theme-warning` | `#ff976a` |
| `--theme-danger` | `#ee0a24` |
| `--theme-info` | `#969799` |
| 文字衍生（保证对比度） | `--theme-success-text #0a7a45` / `-warning-text #b45309` / `-danger-text #c0102a` / `-info-text #646566` |
| 收支金额文字 | `--money-income #ee0a24` / `--money-income-text #c0102a` / `--money-expense #07c160` / `--money-expense-text #0a7a45` |
| 背景三级 | `--theme-bg-primary #f7f8fa` / `-secondary #fff` / `-tertiary #f2f3f5` |
| 文字三级 | `--theme-text-primary #323233` / `-secondary #646566` / `-tertiary #969799` / `-placeholder #c8c9cc` |
| 边框 | `--theme-border #ebedf0` / `-light #f7f8fa` |

### 1.3 Vant 覆盖（节选关键项）
- 主色链：`--van-primary-color`/`--van-success-color`/`--van-warning-color`/`--van-danger-color`/`--van-info-color` ← 对应 `--theme-*`。
- nav-bar：`--van-nav-bar-title-text-color`/`--van-nav-bar-icon-color` = text-primary；`--van-nav-bar-text-color` = primary。
- tabbar 激活：`--van-tabbar-item-active-color` = primary。
- button 主：`--van-button-primary-background`/`--van-button-primary-border-color` = primary。
- field：`--van-field-label-color`/`--van-field-input-text-color`/`--van-field-placeholder-text-color`。
- cell：`--van-cell-text/label-color`、`--van-cell-background`/`--van-cell-group-background`/`--van-cell-group-title-color`；**折叠面板内容背景** `--van-collapse-item-content-background = --theme-bg-primary`（防深色露白底，关键）。
- popup/dialog/search/number-keyboard/calendar/picker 背景均跟随 `--theme-bg-secondary` 或 `--theme-bg-primary`，遮罩用 `linear-gradient(180deg, var(--theme-bg-primary), transparent)`（深色下必须深色穿透）。

### 1.4 深色模式 `[data-theme-mode="dark"]`
- picker 遮罩改深色渐变；新增业务语义别名：`--van-green/--van-red/--van-blue/--van-danger-grad/--van-orange/--van-purple/--van-gray`、`--van-green-bg/--van-danger-bg/--van-orange-bg/--van-blue-bg`（这些**只在深色定义**，亮色下失效，引用需带 fallback）。
- 兼容别名 `--app-primary = --theme-primary`。

### 1.5 通用辅助类（可直接用 class）
- `.app-card`：白底 + radius 12 + shadow。
- `.num-font`：数字字体 + 粗体（`tabular-nums`）。
- 文字色：`.text-expense`/`.text-income`（红收绿支，!important）/`.text-primary`/`.text-secondary`/`.text-tertiary`。
- 背景：`.bg-primary`/`.bg-secondary`/`.bg-tertiary`/`.bg-theme`（主色白字）。`.text-theme`。
- 过渡：`.fade-enter/leave`（opacity 0.25s，配合 `<transition name="fade">`）。
- 隐藏滚动条（`::-webkit-scrollbar{display:none}`）。
- 一体模式 `html[data-theme-mono="1"]`：分类彩色 icon 统一为主题色（保留默认三套浅色主题五颜六色）。
- 数字字体：`formatMoney`/`formatAmount` 支持「亿/万」缩写，负数保留符号；`DIN Alternate`/`DIN Condensed`。

---

## 2. 通用基础组件库（原生重写映射）

web 自研组件位于 `web/src/components/`，Compose 侧 `vant-ui` 已基本对应（见 README 映射表）。

### 2.1 base 9 件（`components/base/`）
| 组件 | props | 原生对应（`vant-ui`） |
|---|---|---|
| `app-button` | type(default/primary/success/warning/danger/text)、plain、size、block、round、disabled、loading、loadingText、nativeType、icon | `VanButton` |
| `app-field` | modelValue、label、type(text/number/tel/password/textarea)、placeholder、disabled、readonly、clearable、required、rules、suffix、rows、maxlength、isLink、rightIcon、passwordVisible、autocomplete | `VanField` |
| `app-cell` | title、value、label、icon、isLink、border、center | `VanCell` |
| `app-tag` | type(primary/success/warning/danger/default)、plain、round、mark、closeable、size、color、textColor | `VanTag` |
| `app-grid` | columnNum(默认4)、border、clickable、gutter | `VanGrid` |
| `app-grid-item` | clickable | `VanGridItem` |
| `app-popup` | show/modelValue、position(bottom/top/left/right/center)、round、overlay、closeable、closeOnClickOverlay、teleport | `VanPopup` |
| `app-form` | showError、validateFirst；暴露 validate()/resetValidation() | `VanForm`（原生用 state+校验 lambda） |
| `app-dialog` | show、title、message、showCancel/ConfirmButton、confirm/cancelButtonText、theme、beforeClose | `VanAlertDialog`/`VanConfirmDialog` |

### 2.2 键盘 / 弹层 / 日历 / 图标
| 组件 | 用途 | 原生注意 |
|---|---|---|
| `FullKeyboard`（`KeyBoard/`） | 全键盘 + canvas 绘制字符 + RSA(`JSEncrypt`) + 防 OCR；`secureOnly` 仅推密文 | 需保留等价安全语义；Compose 自绘键盘 + 密文数组 |
| `SimpleKeyboard` | 简易键盘（无 canvas/RSA），非安全场景 | Compose 简易键盘 |
| `SafeKeyboard`（`KeyBoard/index.vue`） | 数字安全键盘（1-9 随机打乱）+ RSA，用于 PIN | 对应 `VanNumberKeyboard` + PIN 密文 |
| `PinVerifyDialog` | 6 位密码输入 + 安全键盘，提交走 RSA 密文；暴露 show()/hide()/setError() | 原生 PIN 校验弹窗 |
| `CalendarGrid`（`calendar/`） | 通用日历网格（42 格），variant=todo/flow/salary/default，提醒横幅、折叠、今日跳转 | 对应 `VanCalendar` 变体，需重写三种 dataset 渲染 |
| `BankIcon` | 真实 logo 优先，失败回退毛玻璃圆角块 + 银行名首字 | 原生 `AsyncImage` + 首字 fallback |
| `CardOrgIcon`（`BankCard/org/`） | 纯 CSS 绘制卡组织（unionpay/mastercard/visa/amex/diners/jcb），filled/glass 两版 | 原生 `Canvas`/`ImageVector` |
| `CardStack`（`BankCard/`） | 卡牌堆叠，点击放大压暗其余；卡面/银行/尾号/卡组织图标 | 原生自定义卡牌堆叠 |
| `DiaryCard`（`Diary/`） | 日记条目卡（封面/正文两行/心情/追文/作者/定位） | 原生列表项 |

### 2.3 Vant 直接复用组件（仍需原生对应）
`van-nav-bar`、`van-tabbar`、`van-tabs`、`van-picker`、`van-date-picker`、`van-switch`、`van-stepper`、`van-radio-group`、`van-checkbox-group`、`van-number-keyboard`、`van-password-input`、`van-uploader`、`van-search`、`van-popup`、`van-overlay`、`van-loading`、`van-toast`、`van-dialog`、`van-action-sheet`、`van-image-preview`、`van-pull-refresh`、`van-list`、`van-notice-bar`、`van-skeleton`、`van-empty`、`van-circle`、`van-progress`、`van-divider`、`van-row`/`van-col`、`van-icon`、`van-image`、`van-calendar`、`van-swipe`。均已由 `vant-ui` 复刻（详见 `docs/vant-components-inventory.md`）。

---

## 3. 布局与全局壳层

### 3.1 MainLayout（全局壳层，非独立路由）
- 全局 `van-nav-bar`（`fixed`+`placeholder`，title 取路由 `meta.title`，返回键按 `route.meta.hideTabbar` 与是否在主页面显示）。
  - `#left`：返回箭头（arrow-left）+ 首页（wap-home-o）；`#right`：锁（lock，→`handleLockSystem` 弹 `PinVerifyDialog`）+ 全屏（expand）。
- 内容区：`router-view` + `keep-alive :include` + `transition name="fade"`（out-in）。
- 底部 **悬浮岛式 Tabbar**（手写 `nav.floating-island-nav`，非 `van-tabbar`）：毛玻璃 `backdrop-filter:blur(15px)`，4 项 `/home`(wap-home-o 首页)、`/finance`(bill-o 账本)、`/diary`(notes-o 动态)、`/user`(user-o 我的)；`.router-link-active` 用主色。`hideTabbar` 路由整块隐藏；下滑 6px 收起岛。
- keep-alive 缓存名单（组件 name）：FinanceFlowList、FinanceFlowCalendar、FinanceReportFlowFilter、FinanceReportCardFlow、FinanceReportMonthlyTrend、FinanceReportDebtOverview、CardFlowList、BillLedger、BankCardDebit、BankCardCredit、BillList。
- **重写注意**：底部 Tabbar 为手写悬浮岛，原生需自绘 `NavigationBar`/悬浮 `Row`；keep-alive 映射为 `rememberSaveable`/ViewModel 状态保持。

### 3.2 /login — 登录（独立全屏）
- 头部：logo + 「Gold 财管」+ 副标题。
- 表单：`app-form` > `van-cell-group inset`；账号 `app-field`（readonly，点击弹 `FullKeyboard`）；密码 `app-field`（readonly，canvas 绘制 `●` 掩码防 F12 泄露，眼睛切换本地显明文）。
- 提交：`app-button`(round,block,primary,loading) 长度校验 6–30 才允许。
- 安全键盘弹层：`FullKeyboard`（RSA 公钥就绪后弹起；密码 secure 模式明文不进 DOM）。
- API：`authApi.login({nameOrEmail,password})`（密码可为 RSA 密文数组或明文兜底）；`getRsaPublicKey` 握手。Token 存 `localStorage.finance_token`。
- 样式：容器 `100dvh`；键盘唤起收起页脚；激活态 `border-color:primary` + inset 2px 环；`.kb-up` translateY cubic-bezier(.32,.72,.4,1) .28s。

### 3.3 /429 — 频率限制错误（独立全屏）
- 纯展示：红黑闪烁背景（`@keyframes bgFlash` `#ff0000↔#b10e0e`）+ 警示文案。无 API。重写建议保留警示语义但接入主题或维持高对比红黑。

### 3.4 /share/diary/detail — 动态分享详情（独立公开页）
- 状态机：加载中（`van-loading`）/ 错误（`van-empty`+重取）/ 密码模式（`van-password-input` 6 位 + `van-number-keyboard`）/ 正常内容。
- 内容：头像 + 作者 + 时间 + mood 标签 + 正文（`v-html`）+ 图片 3 列网格（`van-image` + `van-image-preview`）+ 位置。
- API：`GET /api/v1/share/${token}`（200/403/410/404 分流）；`POST /api/v1/share/password`（429 防爆锁 / 403 密码错）。图片基址 `ENV.FILE_BASE_URL`。

---

## 4. 首页与账本入口

### 4.1 /home — 首页（Tabbar 主页面）
- 总资产卡（主色→主色渐变白字）：总资产 / 今日收入·支出（点眼睛显隐）、4 项明细（借记卡/信用卡/待还账单/本月结余，前 3 可点）、日期牌。
- 演示提示条（env 控制）。
- 功能九宫格（`app-grid` 4 列）：流水明细/信用卡/资产结构/薪资计算。
- 信息卡：今日预估薪酬 + 待办提醒轮播（`van-swipe` vertical autoplay 3500ms，chip 按 lv-red/yellow/green 配色）。
- 快速登记按钮（`app-button` plain small round +）。
- 近期消费列表（`van-cell-group inset`，按金额色 `text-income/text-expense`，点跳详情）。
- API：`getAssetHome()`（首页仪表盘）、`getReminders({days:3})`、`getSalaryDay({work_date})`。
- 样式：总资产卡渐变 + shadow `rgba(var(--theme-primary-rgb),.25)`；金额 `.num-font`；**注意本页收入=红(`--van-danger-color`)、支出=绿(`--money-expense`)**，与财务直觉相反，重写需确认统一。

### 4.2 /finance — 账本（Tabbar 主页面）
- 财务头卡（白卡 radius 20）：月份选择器（`van-picker`）+ 本月总支出/总收入（`num-font`）+ 结余 + 「记一笔」按钮。
- 功能九宫格（`app-grid` 3 列，12+ 项）：系统账户余额/流水明细/固定资产/信用卡专项/信用卡账单/还款记录/资产结构登记/登记记录/预算表/固定事件/报表/理财投资/数据管理。
- API：`getMonthStats({year,month})`。
- 样式：头卡 shadow；总支出 `var(--money-expense)`、总收入 `var(--money-income)`；grid-icon 语义色（blue/green/orange/red/purple/cyan/teal/pink/gold/gray）。

---

## 5. 记账 / 账户（Finance/account）

### 5.1 /finance/add — 新增收支明细（Add.vue）
- 支出/收入分段（`VanButton` danger/success）；`VanField` 金额(Number)/分类(只读→`VanPicker`)/账户(只读→`VanPicker`)/备注；交易日期（今天）。
- 提交：`graph.flow.create(NewFlow)`；`resolveAccount` 对齐 web buildCardId（现金→xxxx、余额→yyyy，其余真实卡 id）。
- API：`category.list('income'/'expense')`、`card.list(null)`、`account/debit`(支出)/`account/credit`(收入)。

### 5.2 /finance/quick-add — 快速登记（QuickAdd.vue）
- 简化登记入口（native `ACCOUNT_QUICK_ADD` 占位未接）。

### 5.3 /finance/structure — 系统账户余额（Structure.vue）
- 现金/余额/各卡账户余额结构展示（native `ACCOUNT_STRUCTURE` 已接 `AccountStructureScreen`）。

### 5.4 /finance/balance-flow — 余额流水明细（BalanceFlow.vue）
- 余额账户流水（native `ACCOUNT_BALANCE_FLOW` 占位）。

---

## 6. 资产（Finance/assets）

### 6.1 Register / Edit / List / Trend
- **List**（`ASSETS_LIST` 已接 `AssetListScreen`）：资产登记记录列表。
- **Register**：资产结构登记表单（native `ASSETS_REGISTER` 占位）。
- **Edit**：编辑资产（native `ASSETS_EDIT` 占位）。
- **Trend**：资产走势图——**用图表库**（web 侧见 §19 图表清单；原生需对应 `Chart` 组件）。native `ASSETS_TREND` 占位。

---

## 7. 流水（Finance/flow）

### 7.1 /finance/flow — 流水明细（List.vue，keepAlive）
- 按日分组 `LazyColumn`：日期头（收/支小计）+ 流水项（分类名 + 卡别名后四位 + 方向色金额 + 币种 + 时间）。转账标「转账」chip。
- API：`account`(GET, 按月 startDate~endDate, page/limit)。
- native `FLOW_LIST` 已接 `FlowListScreen`（实现完整）。

### 7.2 /finance/flow/calendar — 每日流水（Calendar.vue，keepAlive）
- 日历选日 → 当日流水（native `FLOW_CALENDAR` 已接 `CalendarScreen`）。

### 7.3 /finance/flow/:id — 收支详情（Detail.vue）
- 展示字段 + **冲正按钮**：`getReverseType` 对 `category_id==='installment'` 返回 null（分期不可前端冲正，需去分期列表中止 + 账单手动冲正）。转账记录提示去日历查看。
- native `finance/flow/{id}` 已接 `FlowDetailScreen`。

---

## 8. 固定支出 / 事件 / 固定资产

### 8.1 /finance/recurring — 固定支出（List.vue）
- 固定支出列表（native `RECURRING` 已接 `RecurringListScreen`）。

### 8.2 /finance/events — 固定事件（Index.vue）
- 固定事件列表（native `EVENTS` 已接，复用 `RecurringListScreen("固定事件")`）。

### 8.3 /finance/fixed-asset — 固定资产（List/RecycleBin/Detail/Edit）
- List（`FIXED_ASSET` 已接 `FixedAssetListScreen`）：固定资产列表。
- RecycleBin 回收站、Detail 详情、Edit 编辑——native `FIXED_ASSET_RECYCLE`/`fixedAssetDetail`/`fixedAssetEdit` 均为占位。

---

## 9. 报表中心（Finance/report）

### 9.1 /finance/report — 报表中心（Report.vue）
- 纯菜单导航：流水管理（流水筛选/卡收支/转账）/ 数据统计（收支总额/类目占比/月度趋势）/ 负债统计 / 理财投资。无图表。

### 9.2 flow 子页
- **flow-filter**（流水筛选）：日期区间 + 收支方向 + 分类 + 支付方式筛选；转账/提现/冲正配对识别；**Excel 导出**（xlsx-js-style 前端生成）；结果分组列表。API：`getAccountList`、`getCardList`、`categoryApi.list`。keep-alive 恢复滚动 + `useFlowSyncStore` 同步。
- **card-flow**（银行卡收支明细）：选卡 + 月份 tabs（全部/收入/支出）+ 汇总 + 按日分组列表（还款置灰）。API：`getAccountListByCard`。native `REPORT_CARD_FLOW` 占位。
- **transfer-list**（转账明细）：分页卡片列表（从/到卡 + 金额 + 备注）。native `REPORT_TRANSFER_LIST` 占位。

### 9.3 stats 子页（**均用 echarts**）
- **stats-overview**（收支总额与结余，`REPORT_STATS_OVERVIEW` 已接 `StatsOverviewScreen`）：4 汇总卡 + 分类条形（自绘）+ **环形饼图（echarts Pie）** + 明细抽屉。
- **category-ratio**（类目消费占比）：分类勾选过滤 + **三级可滑动环形饼图（echarts，van-swipe）**。native 占位。
- **monthly-trend**（月度收支趋势）：**柱状/曲线切换（echarts Bar/Line + dataZoom）**，点击柱下钻当日。native 占位。

### 9.4 /finance/report/debt-overview — 负债总览
- 信用卡账单概览 + 有效期预警 + **自绘每日信用卡支出日历**（红标）+ 类别条形。无图表库。native 占位。

### 9.5 fund 理财（Fund/FundRegister/FundTrend/FundDaily/FundEarnings）
- **Fund**（`FUND` 已接 `FundScreen`）：持仓总览 4 卡（持有数/本金/市值/累计收益）+ 操作入口 + 持仓列表。**收益为正用红、为负用绿**（反直觉）。
- **FundRegister**：基金增改删 + 表单（名称/购入日/份额/本金）。
- **FundTrend**：**双线折线（echarts）** 累计收益 + 当前市值，范围按钮组。
- **FundDaily / FundEarnings**：**自绘收益日历**（标当日收益、买入旗），点日弹窗登记/修改「今日收益 + 增持本金」。
- native `FUND_REGISTER/TREND/DAILY/EARNINGS` 占位。

---

## 10. 预算（Finance/budget）

### 10.1 List（预算列表，`BUDGET` 已接 `BudgetListScreen`）
- 顶部渐变统计卡（总预算/支出/剩余 + 进度条）+ `van-tabs`(全部/出行/购物/餐饮) + 预算卡列表（每卡进度、超支标红）+ 固定底部「登记预算」。

### 10.2 TypeSelect / Shopping / Travel / Eat / Detail
- **TypeSelect**：购物/出行/餐饮入口网格。
- **Shopping**：购物预算表单（标题/日期/周期/总预算 + 商品明细增删 + 汇总）。
- **Travel**：出行预算表单（**多币种汇率管理** CNY/USD/EUR/GBP/HKD，实时外币→CNY 换算 `金额×汇率÷100`）。
- **Eat**：餐饮预算表单（菜单明细 + 汇总）。
- **Detail**：按类型展示不同明细 + 进度 + 编辑/删除。
- 全部 native `BUDGET_TYPE_SELECT/SHOPPING/TRAVEL/EAT`/`budgetDetail` 占位。
- 周期色条：日绿/周蓝/月橙/季紫/年红；超支 `progress-fill.over` 红渐变。

---

## 11. 数据管理（Finance/data）

### 11.1 DataManage → check/export/import/backup/login-log
- **DataManage**：功能菜单（检查/导出/导入/备份/登录日志，登录日志仅非 demo 显示）。
- **DbCheck**：触发检查，展示连接状态/总行数/表状态（原生 `<table>` 横向滚动）。
- **DbExport**：选全部/单表 + 包含数据开关，**异步任务轮询进度**（`van-progress` + 5s 轮询）+ 下载（Blob）。
- **DbImport**：JSON(单表)/SQL·ZIP(全库) 导入，文件预览 + 校验 + 二次确认 + 结果。
- **DbBackup**：系统备份（完整+结构，异步进度）+ 手动导出记录（下载/删除）。
- **LoginLog**：登录记录列表，前端异常检测（新设备/异地/跨境/2h 频切）打标签。
- 全部 native `DATA_MANAGE/CHECK/EXPORT/IMPORT/BACKUP/LOGIN_LOG` 占位。

---

## 12. 动态 / 日记（Diary）

### 12.1 /diary — 动态（Tabbar 主页面）
- 双列瀑布流（`van-pull-refresh` + `van-list` 分页），`DiaryCard`；回顶按钮；悬浮发布按钮（`/diary/add`）。API：`momentApi.list`。
- native `DIARY` 已接 `DiaryListScreen`。

### 12.2 /diary/add — 发布动态
- **快速（纯文本）/ 精准（富文本 WangEditor）双模式切换**；心情/图片(≤9/20)/位置元信息；发布前二次确认。
- 图片：`van-uploader` + `browser-image-compression` 压缩上传。API：`uploadApi.list/single`、`momentApi.create`。

### 12.3 /diary/detail — 动态详情
- 正文 + 追文时间轴（可删单条）；分享（密码/公共链接，10-72h 有效）；删除带 **10 秒确认倒计时**防误删。API：`momentApi.getOne/batchDetail/delete/update(share)`。
- native `DIARY_ADD`/`DIARY_DETAIL` 占位。

---

## 13. /todo/calendar — 日程日历
- `CalendarGrid variant="todo"` + 选中日事件列表（分期专区 + 计划）；新增/编辑/删除待办。
- 事件类型 radio（日程/生日/纪念/倒数）、优先级 radio（高/中/低）、每年重复 switch、提醒 switch + 提前天数 stepper。
- 提醒级别红/黄/绿（按距今天数着色，闪烁）。API：`getCalendarMonth`、`getTodoList`、`create/update/deleteTodo`、`getReminders`、`updateRecurringMonthStatus`。
- native `TODO_CALENDAR` 已接 `TodoListScreen`。

---

## 14. 个人中心 / 用户（User）

### 14.1 /user — 我的（Tabbar 主页面）
- 资料卡（头像/昵称/邮箱 → `/profile-edit`）+ 安全与隐私（PIN 开关 + PIN 管理）+ 系统管理（应用设置/文件资源管理）+ 退出登录。
- API：`authApi.getUserinfo()`、`securityApi.checkPin()`。native `USER` 已接 `UserScreen`（PIN/报表/数据管理菜单为 TODO 空实现）。

### 14.2 PinManage / PinSetup — PIN 管理 / 设置
- **PinManage**：状态卡 + 设置/修改/关闭入口；关闭需 `SafeKeyboard` 6 位验证（`changePin` 传 `newPin:'000000'`）。
- **PinSetup**：分步向导（new 2 步 / modify 3 步），`van-password-input` + `SafeKeyboard`；强校验 `/^\d{6}$/` 拒绝 6 位相同数字；提交锁防连点。
- API：`securityApi.checkPin/setPin/verifyPin/changePin`。native `PIN_MANAGE/PIN_SETUP` 占位。

### 14.3 ResourceManage / ResourceList — 文件资源管理
- Manage：按 busType 跳 ResourceList（动态图片/资产图片/银行 Icon/其他）。
- List：`van-search` + 多选批量删除 + 上传（`van-uploader` + 压缩）+ 编辑信息 + 预览。API：`uploadApi.list/search/multiple/update/batchDelete`。native 占位。

### 14.4 AppSettings — 应用设置
- 分类设置（收支/银行分类入口）、UI 主题选择（useUiTheme presets）、收支颜色切换（useMoneyColor red-in/red-out）、PWA 安装检测（诊断/日志）、demo 预览入口。
- native `APP_SETTINGS` 已接 `AppSettingsScreen`。

### 14.5 CategoryManage / BankCategoryManage — 分类管理
- 4 tab（支出/收入/资产/固定资产）卡片网格 + 增删改 + 图标选择上传。Bank 版用 `BankIcon` + 单类型。
- API：`categoryApi.list/create/update/delete`、`uploadApi.*`。native `CATEGORY_MANAGE` 已接 `CategoryManageScreen`；`BANK_CATEGORY_MANAGE` 占位。

### 14.6 /profile-edit — 编辑资料
- 头像/用户名/邮箱/登录密码；所有弹窗输入框 readonly + 统一内置 `SimpleKeyboard`（光标跟随 + 键盘避让）。邮箱验证码 60s 倒计时。改密成功后清 token 回登录。
- API：`authApi.getUserinfo/updateProfile/sendEmailCode/changePassword`。 native `PROFILE_EDIT` 已接 `ProfileEditScreen`。

---

## 15. 银行卡 / 信用卡（BankCard）

### 15.1 /card — 父布局（index.vue）
- 顶栏（标题 + 分段切换 借记卡/信用卡 + 预览脱敏 toggle + 排序 toggle）+ `<router-view>`（debit/credit 子路由）。inject 共享状态（脱敏/排序/滚动收起）。
- native：APP `CardListScreen` 为单列表 + 筛选（储蓄卡/信用卡），等价合并。

### 15.2 debit / credit — 卡列表
- `CardStack` 卡牌堆叠（预览脱敏、拖拽排序、选中查消费明细）；借记卡主色 `#4A90E2`、信用卡 `--van-danger-color #ee0a24`。
- API：`getCardList({cardType})`、`updateCardSortBatch`、`getCardsFlowStats`(近6月)。
- native `CARD_DEBIT/CARD_CREDIT` 占位（CardListScreen 已接 `CARD`）。

### 15.3 /credit-center — 信用卡专项中心
- 渐变头卡（卡片数→/card/credit、待还→/card/bill/list）+ 四分组入口（卡片管理/账单还款/分期/额度外币）。native `CREDIT_CENTER` 占位。

### 15.4 分期（Installment / InstallmentList / AddFull）
- **Installment**（创建分期）：目标卡 + 开始月份(今年内) + 入账归属(本月/次月) + 首期入账日(账单日前2天) + 总额 + 手续费 + 期数(3/6/9/12/18/24/30/36/48/60) + 本金取整(floor/round) + 尾差(末期/首期) + **逐期微调预览**（`buildInstallmentSchedule` 保证 Σ=本金+利息）。
- **InstallmentList**（`CREDIT_INSTALLMENT_LIST` 已接 `InstallmentListScreen`）：各期状态标签。
  - 状态色：**pending 蓝** `rgba(25,137,250,.1)` / **entering 橙** `#ff976a` / **entered 绿** `#07c160` / **overdue 红** `#ee0a24` / **done 绿** / **void 灰** `rgba(0,0,0,.05)`；已结束卡 `grayscale(1) opacity .6`。
- **AddFull**（信用卡全功能录入）：4 步向导（基本信息/专属+额度/卡片设置/确认），草稿本地缓存。native `CREDIT_INSTALLMENT/CREDIT_FULL` 占位。

### 15.5 /credit/limit-manage — 额度与共享池（`CREDIT_LIMIT_MANAGE` 已接 `LimitManageScreen`）
- 两 Tab：卡片额度（按 `share_pool_id` 分组）/ 共享额度池；编辑固额/临额、批量归池（同银行才可）。API：`getCardList`、`updateCard`、`getCreditPools`、`create/update/deleteCreditPool`、`assignCardPool`。

### 15.6 /credit/foreign-register — 外币对账
- 三 Tab（待对账/全部/历史）；实际汇率(每100外币)录入，`previewRmb=原币×汇率/100`；`reconcileForeign` 同步账单。native `CREDIT_FOREIGN_REGISTER` 占位。

### 15.7 /card/add / /card/edit — 卡片增改
- Add（借记卡单页，卡组织联动 BIN 前缀/长度/过期推算）。Edit（顶部卡预览；信用卡账单日/还款日有流水则锁定；删卡确认）。native `CARD_ADD`/`CARD_EDIT_PAT` 已接 `CardEditScreen`。

### 15.8 /finance/report/card-flow — 卡片流水（card/flow/List.vue）
- 某卡按月流水 + 统计；keep-alive + `useFlowSyncStore` 同步。native `CARD_FLOW` 占位。

### 15.9 账单（bill/List / Detail / Ledger）
- **List**（`BILL_LIST` 已接 `BillListScreen`）：按待还月份 + 卡筛选，按银行折叠；本地算状态（逾期/待还/已还清/未出账）；信报合一池合并还款；外币待对账提示。
- **Detail**：单账单完整信息 + 查看流水/添加还款/删除。
- **Ledger**：账单周期流水按日分组 + 匹配校验 + 外币折算。
- native `BILL_DETAIL/BILL_LEDGER` 占位。

### 15.10 还款（repay/List / Detail / Add / Edit）
- **List**（`REPAY_LIST` 已接 `RepayListScreen`）：按卡 + 月份筛选；撤销(`reverseCreditRepay`)/再次还款。
- **Detail**：还款金额大图 + 关联信息 + 方式 + 备注。
- **Add**：单卡还款 / 信报合一**合并还款**(`mergeRepay`)；金额/方式(cash/balance/bank_card)/卡/时间；一键全额。
- **Edit**：改金额/方式/时间/备注 + 删除。
- native `REPAY_DETAIL/ADD/EDIT` 占位。

---

## 16. 工作 / 工资（Work）

### 16.1 JobSetting — 工作信息设置
- 正式工（公司/月工资/出勤/日薪/在职离职）+ 兼职（单位/时薪/状态）；弹窗增删改。日薪前端算 `base_salary/base_work_days`(默认22)。API：`/work/job/list`、`/work/job`(POST/PUT/DELETE)。

### 16.2 SalaryCalendar — 工资日历（`CalendarGrid variant="salary"`）
- 月收入统计栏（总/正式蓝/兼职橙）+ 日历：**计薪日**显示「正式 ¥x(主色) + 兼职 ¥x(橙)」；纯计薪日灰字「计薪」；非计薪日置灰不可点；今天绿实心圆。点日 → `/work/salary-day?date=`。API：`/work/salary/month`、`/work/job/list`。

### 16.3 SalaryDay — 每日工资明细
- 正式工卡片（日薪/社保/公积金/个税/其他扣款/实收 `day_salary−social−fund−tax−cut`）+ N 个兼职卡片；提交正式工资 / 删除整日。API：`/work/salary/day`、`/work/salary`(POST)、`/work/salary`(DELETE)。

### 16.4 SalaryStat — 月度统计
- **未用任何图表**：渐变汇总卡 + 每日明细列表（计薪日数 + 每日金额）。API：`/work/salary/month`。
- native `WORK_SALARY_CALENDAR/DAY/STAT` 占位。

---

## 17. Demo 页（演示 / 设计系统 catalog）

- **UiShowcase**：Vant 组件 + 主题 token 活体展示台 + 悬浮调色器实时改主色/玻璃拟态（`buildVars`）。
- **CalendarDemo**：自研小米风日历（todo/flow/salary 三变体）。
- **KeyboardDemo**：安全键盘随主题配色。
- **BankOrgDemo**：6 卡组织毛玻璃/品牌色 Icon（**硬编码深蓝渐变**，演示专用）。
- 这些页纯前端，原生侧 `vant-ui` Demo 模块已可对应，不作为业务重构范围。

---

## 18. Web → 原生 APP 路由对照表

`APP/` 路由常量见 `app/src/main/java/com/live/finance/core/nav/Routes.kt`；导航注册见 `ui/AppRoot.kt`。状态：✅ 已接屏幕 / ⬜ 占位未接。

| Web 路由 | APP 常量 | 状态 |
|---|---|---|
| /login | `LOGIN` | ✅ |
| /429 | `ERROR_429` | ✅ |
| /share/diary/detail | `DIARY_SHARE` | ⬜ |
| /home | `TAB_HOME` | ✅(tab) |
| /finance | `TAB_FINANCE` | ✅(tab) |
| /finance/add | `ACCOUNT_ADD` | ✅ |
| /finance/quick-add | `ACCOUNT_QUICK_ADD` | ⬜ |
| /finance/structure | `ACCOUNT_STRUCTURE` | ✅ |
| /finance/balance-flow | `ACCOUNT_BALANCE_FLOW` | ⬜ |
| /finance/assets/list | `ASSETS_LIST` | ✅ |
| /finance/assets/register | `ASSETS_REGISTER` | ⬜ |
| /finance/assets/edit | `ASSETS_EDIT` | ⬜ |
| /finance/assets/trend | `ASSETS_TREND` | ⬜ |
| /finance/flow | `FLOW_LIST` | ✅ |
| /finance/flow/calendar | `FLOW_CALENDAR` | ✅ |
| /finance/flow/:id | `flowDetail(id)` | ✅ |
| /finance/events | `EVENTS` | ✅ |
| /finance/recurring | `RECURRING` | ✅ |
| /finance/fixed-asset | `FIXED_ASSET` | ✅ |
| /finance/fixed-asset/recycle | `FIXED_ASSET_RECYCLE` | ⬜ |
| /finance/fixed-asset/detail/:id | `fixedAssetDetail(id)` | ⬜ |
| /finance/fixed-asset/edit/:id | `fixedAssetEdit(id)` | ⬜ |
| /finance/report | `REPORT` | ⬜ |
| /finance/report/flow-filter | `REPORT_FLOW_FILTER` | ⬜ |
| /finance/report/stats-overview | `REPORT_STATS_OVERVIEW` | ✅ |
| /finance/report/category-ratio | `REPORT_CATEGORY_RATIO` | ⬜ |
| /finance/report/monthly-trend | `REPORT_MONTHLY_TREND` | ⬜ |
| /finance/report/card-flow | `REPORT_CARD_FLOW` | ⬜ |
| /finance/report/transfer-list | `REPORT_TRANSFER_LIST` | ⬜ |
| /finance/report/debt-overview | `REPORT_DEBT_OVERVIEW` | ⬜ |
| /finance/report/fund | `FUND` | ✅ |
| /finance/report/fund/register | `FUND_REGISTER` | ⬜ |
| /finance/report/fund/trend | `FUND_TREND` | ⬜ |
| /finance/report/fund/daily | `FUND_DAILY` | ⬜ |
| /finance/report/fund/earnings | `FUND_EARNINGS` | ⬜ |
| /finance/budget | `BUDGET` | ✅ |
| /finance/budget/type-select | `BUDGET_TYPE_SELECT` | ⬜ |
| /finance/budget/shopping | `BUDGET_SHOPPING` | ⬜ |
| /finance/budget/travel | `BUDGET_TRAVEL` | ⬜ |
| /finance/budget/eat | `BUDGET_EAT` | ⬜ |
| /finance/budget/detail/:id | `budgetDetail(id)` | ⬜ |
| /finance/data | `DATA_MANAGE` | ⬜ |
| /finance/data/check | `DATA_CHECK` | ⬜ |
| /finance/data/export | `DATA_EXPORT` | ⬜ |
| /finance/data/import | `DATA_IMPORT` | ⬜ |
| /finance/data/backup | `DATA_BACKUP` | ⬜ |
| /finance/data/login-log | `DATA_LOGIN_LOG` | ⬜ |
| /diary | `DIARY` | ✅(tab) |
| /diary/add | `DIARY_ADD` | ⬜ |
| /diary/detail | `DIARY_DETAIL` | ⬜ |
| /todo/calendar | `TODO_CALENDAR` | ✅ |
| /user | `USER` | ✅(tab) |
| /user/pin-manage | `PIN_MANAGE` | ⬜ |
| /user/pin-setup | `PIN_SETUP` | ⬜ |
| /user/resource-manage | `RESOURCE_MANAGE` | ⬜ |
| /user/resource-list | `RESOURCE_LIST` | ⬜ |
| /user/app-settings | `APP_SETTINGS` | ✅ |
| /user/category-manage | `CATEGORY_MANAGE` | ✅ |
| /user/bank-category-manage | `BANK_CATEGORY_MANAGE` | ⬜ |
| /card | `CARD` | ✅ |
| /card/debit | `CARD_DEBIT` | ⬜(合并入 CardListScreen) |
| /card/credit | `CARD_CREDIT` | ⬜(合并入 CardListScreen) |
| /card/add | `CARD_ADD` | ✅ |
| /card/edit/:id | `CARD_EDIT_PAT` | ✅ |
| /card/flow | `CARD_FLOW` | ⬜ |
| /credit-center | `CREDIT_CENTER` | ⬜ |
| /credit-center/installment | `CREDIT_INSTALLMENT` | ⬜ |
| /credit-center/installment/list | `CREDIT_INSTALLMENT_LIST` | ✅ |
| /credit-full | `CREDIT_FULL` | ⬜ |
| /credit/limit-manage | `CREDIT_LIMIT_MANAGE` | ✅ |
| /credit/foreign-register | `CREDIT_FOREIGN_REGISTER` | ⬜ |
| /card/bill/list | `BILL_LIST` | ✅ |
| /card/bill/detail | `BILL_DETAIL` | ⬜ |
| /card/bill/ledger | `BILL_LEDGER` | ⬜ |
| /card/repay/list | `REPAY_LIST` | ✅ |
| /card/repay/detail | `REPAY_DETAIL` | ⬜ |
| /card/repay/add | `REPAY_ADD` | ⬜ |
| /card/repay/edit | `REPAY_EDIT` | ⬜ |
| /profile-edit | `PROFILE_EDIT` | ✅ |
| /work/job-setting | `WORK_JOB_SETTING` | ✅ |
| /work/salary-calendar | `WORK_SALARY_CALENDAR` | ⬜ |
| /work/salary-day | `WORK_SALARY_DAY` | ⬜ |
| /work/salary-stat | `WORK_SALARY_STAT` | ⬜ |

**已接 27 处 / 占位约 63 处**（含大量报表/预算类型/数据管理/PIN/资源/工资/日记二级页）。

---

## 19. 重构横切要点

1. **图表**：`StatsOverview`/`CategoryRatio`/`MonthlyTrend`/`FundTrend` 用 **echarts**（core 按需引入）；其余（资产走势/工资/收益日历/负债日历）为 CSS/原生自绘。原生侧需引入 `charts`/自绘 Canvas，或先以列表+卡片替代。
2. **安全键盘 / PIN**：`FullKeyboard`/`SimpleKeyboard`/`SafeKeyboard` + `PinVerifyDialog` 涉及 RSA(`JSEncrypt`) 与 canvas 防 OCR；Login 密码 canvas 掩码。原生需保留同等安全语义（密文数组、防截屏）。
3. **keep-alive / 状态同步**：FlowFilter/CardFlow/MonthlyTrend/DebtOverview/BillList/Ledger/Debit/Credit 用 `onActivated` 恢复滚动 + `useFlowSyncStore` 同步；原生映射 `rememberSaveable`/ViewModel + 共享 Flow 存储。
4. **金额配色反直觉**：Home 收入=红、支出=绿；Finance 总支出=红、总收入=绿；**Fund 收益正=红、负=绿**。统一规则需向产品确认（默认红收绿支）。
5. **图片 URL**：`ENV.FILE_BASE_URL` + 相对路径（`getFullUrl`/`getThumbUrl`）应在数据层统一封装。
6. **脱敏 / 安全**：debit/credit 预览模式 BIN 前3+随机、尾号随机；Edit 卡号 `****`；金额 `formatMoney` 两位。
7. **主题**：所有 `var(--theme-*)/var(--van-*)` 统一收集为 Compose 颜色板（`VantColors`/`VantDarkColors` + `LocalAppColors`），含 primary/grad/bg(三级)/text(三级)/danger/success/warning/border/shadow；深色下 `--van-*` 别名（green-bg 等）仅在深色定义，引用带 fallback。
8. **组件替换速查**：`van-pull-refresh`+`van-list`→`PullToRefresh`+`LazyColumn` 分页；`van-password-input`→6 格 PIN；`van-action-sheet/popup/dialog`→`ModalBottomSheet`/`AlertDialog`；`van-tabs`→`ScrollableTabRow`；`CalendarGrid`→自研日历；`app-*`→`vant-ui` 对应件。
