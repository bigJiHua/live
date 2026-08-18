<p align="center">
  <img src="public/logo.png" width="120" alt="Logo" />
</p>

# Golden Finance — 智慧个人财务管理系统

## 项目简介

Golden Finance 是一款基于 **Vue 3 + Vite + Vant 4** 构建的移动端 PWA 应用，旨在帮助用户高效管理个人财务。本系统提供全面的收支记录、资产管理、预算控制、银行卡管理、工资核算、理财投资追踪、财务报表分析、数据备份恢复等功能，并支持日记记录和待办日程管理。

> **更新日期**: 2026-06-30

## 技术栈

| 类别 | 技术 |
|------|------|
| 前端框架 | Vue 3.5.30 |
| 构建工具 | Vite 8.0.1 |
| UI 组件库 | Vant 4.9.22 |
| 状态管理 | Pinia 3.0.4 |
| 路由管理 | Vue Router 5.0.4 |
| 网络请求 | Axios 1.13.6 |
| 加密库 | Crypto-JS 4.2.0 |
| JWT 解码 | jwt-decode 4.0.0 |
| 日期处理 | Dayjs 1.11.20 |
| 设备识别 | ua-parser-js 2.0.9 |
| 图片压缩 | browser-image-compression 2.0.2 |
| RSA 加密 | jsencrypt 3.5.4 |
| 图表可视化 | ECharts 6.0.0 |
| Excel 导出 | xlsx / xlsx-js-style |
| 富文本编辑 | @wangeditor/editor |
| PWA 支持 | vite-plugin-pwa |

## 功能架构

本系统采用模块化设计，主要功能模块如下：

```
┌─────────────────────────────────────────────────────────────────┐
│                         应用入口层                               │
│                    登录 / 注册 / 首页                            │
└────────────────────────────┬────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   首页模块    │    │   账本模块    │    │   用户模块    │
│   (Home)     │    │   (Finance)   │    │   (User)      │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ • 总资产展示  │    │ • 收支记录    │    │ • 个人资料    │
│ • 今日收支    │    │ • 账户余额    │    │ • 分类管理    │
│ • 快捷功能    │    │ • 流水明细    │    │ • 安全设置    │
│ • 待办提醒    │    │ • 资产管理    │    │ • 应用设置    │
│ • 近期消费    │    │ • 预算管理    │    │ • 资源管理    │
│ • 今日薪酬    │    │ • 固定资产    │    └───────────────┘
└───────────────┘    │ • 财报分析    │
                     │ • 数据管理    │
                     │ • 理财投资    │
                     │ • 固定事件    │
                     │ • 固定支出    │
                     └───────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  银行卡模块   │    │  工资模块     │    │  日记模块     │
│  (BankCard)   │    │   (Work)      │    │   (Diary)     │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ • 借记卡管理  │    │ • 工作信息    │    │ • 发布动态    │
│ • 信用卡管理  │    │ • 工资日历    │    │ • 动态列表    │
│ • 账单管理    │    │ • 薪资统计    │    │ • 动态详情    │
│ • 还款记录    │    │ • 每日工资    │    │              │
└───────────────┘    └───────────────┘    └───────────────┘
                              │
                              ▼
                     ┌───────────────┐
                     │  待办模块     │
                     │   (Todo)      │
                     └───────────────┘
                              │
                              ▼
                     ┌───────────────┐
                     │ • 日历日程    │
                     │ • 待办提醒    │
                     └───────────────┘
```

## 功能模块详解

### 一、首页模块 (Home)

首页是用户进入应用后的主界面，聚合展示了核心财务数据快捷入口，帮助用户快速了解当前财务状况。

#### 1.1 功能特性

| 功能项 | 说明 |
|--------|------|
| **总资产展示** | 显示用户预估总资产金额，支持金额显示/隐藏切换 |
| **今日收支** | 实时展示今日收入和支出金额 |
| **账户概览** | 快速查看借记卡数量、信用卡数量、待还账单金额、本月结余 |
| **快捷入口** | 流水明细、信用卡、资产结构、薪资计算四个快捷功能入口 |
| **今日薪酬** | 展示今日预估薪酬收入 |
| **待办提醒** | 显示最近的一项待办提醒事项，点击可跳转至日历页面 |
| **近期消费** | 展示最近的大额交易流水记录 |

#### 1.2 页面交互

- 点击右上角日期卡片可进入日程日历页面
- 点击眼睛图标可切换金额显示/隐藏状态
- 点击卡片区域可快速跳转到对应功能页面
- 点击近期消费记录可查看流水详情

【图片插槽】：首页界面截图

---

### 二、账本/金融模块 (Finance)

账本模块是系统的核心功能区，提供全面的财务收支管理和资产管控能力。

#### 2.1 收支记录

| 功能项 | 说明 |
|--------|------|
| **新增收支** | 支持记录收入/支出，包含金额、分类、支付方式、交易日期等 |
| **流水列表** | 按时间顺序展示所有收支流水，支持筛选和搜索 |
| **日历视图** | 按日历形式展示每日流水情况，直观了解消费分布 |
| **流水详情** | 查看单笔流水的完整信息，支持编辑和删除 |
| **收支统计** | 按月份统计收入支出情况 |

**支持的操作类型：**

- 账户变动
- 信用卡还款
- 收入记录
- 支出消费

**支持的资金流向：**

- 现金
- 微信支付
- 支付宝
- 银行卡

【图片插槽】：收支记录界面截图

#### 2.2 账户余额管理

| 功能项 | 说明 |
|--------|------|
| **系统账户** | 统一管理所有资金账户的余额 |
| **虚拟账户** | 支持设置现金、微信、支付宝等虚拟账户 |
| **余额同步** | 手动同步各账户的当前余额 |
| **账户结构** | 查看完整的账户体系结构 |

**虚拟账户类型：**

| 账户ID | 名称 | 说明 |
|--------|------|------|
| virtual_cash | 现金 | 现金余额 |
| virtual_wx | 微信 | 微信支付余额 |
| virtual_alipay | 支付宝 | 支付宝余额 |
| virtual_bank | 银行卡 | 银行卡余额 |

【图片插槽】：账户余额界面截图

#### 2.3 资产管理

| 功能项 | 说明 |
|--------|------|
| **资产登记** | 登记个人资产信息 |
| **资产列表** | 查看所有已登记的资产记录 |
| **资产详情** | 查看单笔资产的详细信息 |
| **资产编辑** | 修改资产信息 |
| **回收站** | 暂存已删除的资产，支持恢复 |

【图片插槽】：资产管理界面截图

#### 2.4 固定资产管理

| 功能项 | 说明 |
|--------|------|
| **固定资产列表** | 查看所有固定资产 |
| **新增固定资产** | 登记新的固定资产 |
| **资产详情** | 查看固定资产详细信息 |
| **编辑资产** | 修改固定资产信息 |
| **删除资产** | 将资产移至回收站 |
| **回收站管理** | 恢复或彻底删除固定资产 |

**固定资产包含：**

- 房产类（住房、商铺、厂房等）
- 车辆类（汽车、摩托车等）
- 电子设备（手机、电脑等）
- 家具家电
- 其他资产

【图片插槽】：固定资产界面截图

#### 2.5 预算管理

| 功能项 | 说明 |
|--------|------|
| **预算登记** | 创建各类预算计划 |
| **预算列表** | 查看所有预算记录 |
| **预算详情** | 查看预算执行情况 |
| **预算类型** | 购物预算、出行预算、餐饮预算 |

**预算分类：**

| 类型 | 说明 |
|------|------|
| 购物预算 | 双十一、618 等购物节预算 |
| 出行预算 | 旅行、出差交通费用预算 |
| 餐饮预算 | 日常餐饮支出预算 |

【图片插槽】：预算管理界面截图

#### 2.6 流水冲正

| 功能项 | 说明 |
|--------|------|
| **借记卡冲正** | 撤销借记卡的支出/收入记录 |
| **信用卡消费冲正** | 撤销信用卡消费记录 |
| **信用卡还款撤销** | 撤销信用卡还款记录 |

【图片插槽】：流水冲正界面截图

#### 2.7 财务报表

| 功能项 | 说明 |
|--------|------|
| **流水筛选** | 按日期、分类、支付方式进行流水筛选查询 |
| **收支总额与结余** | 按时间段统计收支总额与净结余 |
| **类目消费占比** | 饼图展示各消费类目占比分析 |
| **月度收支趋势** | 折线图展示月度收支变化趋势 |
| **银行卡收支明细** | 按卡统计收支明细 |
| **转账明细查询** | 查看账户间转账记录 |
| **负债统计** | 查看信用卡等负债汇总 |

#### 2.8 理财投资

| 功能项 | 说明 |
|--------|------|
| **理财总览** | 汇总展示所有理财产品市值与收益 |
| **理财登记** | 登记新的理财产品（基金/股票/定期等） |
| **变动走势图** | 可视化展示理财产品净值变化趋势 |
| **每日净值登记** | 记录理财产品每日净值 |
| **收益明细** | 查看每笔理财产品的收益明细 |

#### 2.9 数据管理

| 功能项 | 说明 |
|--------|------|
| **数据库检查** | 检查数据库连接状态与表结构健康度 |
| **导出数据库** | 将数据库导出为 SQL 文件下载 |
| **数据导入** | 支持从 SQL 文件或 JSON 导入数据 |
| **数据库备份** | 创建数据库完整备份 |
| **登录日志** | 查看登录历史记录（非 Demo 模式） |

#### 2.10 固定事件与支出

| 功能项 | 说明 |
|--------|------|
| **固定事件** | 管理周期性固定事件（账单日、还款日等提醒） |
| **固定支出** | 管理周期性固定支出（房租、水电、订阅等） |

---

### 三、银行卡模块 (BankCard)

银行卡模块提供完整的银行卡（借记卡和信用卡）管理功能。

#### 3.1 借记卡管理

| 功能项 | 说明 |
|--------|------|
| **卡片列表** | 查看所有已添加的借记卡 |
| **添加卡片** | 新增借记卡信息 |
| **编辑卡片** | 修改借记卡信息 |
| **卡片流水** | 查看该卡的所有交易流水 |

#### 3.2 信用卡管理

| 功能项 | 说明 |
|--------|------|
| **卡片列表** | 查看所有已添加的信用卡 |
| **添加卡片** | 新增信用卡信息 |
| **编辑卡片** | 修改信用卡信息 |
| **卡片流水** | 查看该卡的所有交易流水 |

#### 3.3 信用卡专项

| 功能项 | 说明 |
|--------|------|
| **信用卡中心** | 集中管理所有信用卡 |
| **全功能录入** | 支持完整的信用卡信息录入 |
| **分期管理** | 创建和管理信用卡分期付款计划 |
| **分期列表** | 查看所有分期记录与还款进度 |

#### 3.4 账单管理

| 功能项 | 说明 |
|--------|------|
| **账单列表** | 查看所有信用卡账单 |
| **账单详情** | 查看账单具体内容 |
| **账单流水明细** | 查看账单关联的消费流水详情 |
| **添加账单** | 新增账单记录 |
| **编辑账单** | 修改账单信息 |

#### 3.5 还款管理

| 功能项 | 说明 |
|--------|------|
| **还款列表** | 查看所有还款记录 |
| **还款详情** | 查看单笔还款详情 |
| **添加还款** | 记录还款信息 |
| **编辑还款** | 修改还款记录 |

【图片插槽】：银行卡管理界面截图

---

### 四、工资模块 (Work)

工资模块帮助用户管理工资收入和考勤相关数据。

#### 4.1 功能特性

| 功能项 | 说明 |
|--------|------|
| **工作信息设置** | 设置公司名称、职位、工资发放日等信息 |
| **工资日历** | 查看按月展示的工资日历视图 |
| **每日工资** | 查看单日工资详情 |
| **月度统计** | 按月统计工资收入情况 |
| **薪资计算** | 自动计算和展示薪资数据 |

**工资构成：**

- 正式工资
- 兼职/副业收入
- 日薪计算

【图片插槽】：工资核算界面截图

---

### 五、日记/动态模块 (Diary)

日记模块允许用户记录生活点滴，分享动态。

#### 5.1 功能特性

| 功能项 | 说明 |
|--------|------|
| **发布动态** | 发布新的日记/动态内容 |
| **动态列表** | 浏览所有动态 |
| **动态详情** | 查看单条动态的完整内容 |
| **图片支持** | 支持上传图片记录生活 |

【图片插槽】：日记动态界面截图

---

### 六、待办/日程模块 (Todo)

待办模块提供日程管理和提醒功能。

#### 6.1 功能特性

| 功能项 | 说明 |
|--------|------|
| **日历视图** | 以日历形式展示待办事项 |
| **待办提醒** | 设置事项提醒 |
| **优先级设置** | 支持设置事项优先级 |
| **日期筛选** | 按日期筛选待办事项 |

【图片插槽】：日程日历界面截图

---

### 七、用户模块 (User)

用户模块提供个人资料管理、系统设置和安全相关功能。

#### 7.1 个人中心

| 功能项 | 说明 |
|--------|------|
| **资料展示** | 查看个人基本信息 |
| **资料编辑** | 修改个人资料 |

#### 7.2 分类管理

| 功能项 | 说明 |
|--------|------|
| **收支分类** | 管理收入/支出的分类 |
| **银行分类** | 管理银行卡所属银行分类 |

**分类类型：**

- 收入分类（工资奖金、投资收益、兼职收入等）
- 支出分类（餐饮、交通、购物、住房等）
- 银行分类（工商银行、建设银行、招商银行等）

#### 7.3 安全设置

| 功能项 | 说明 |
|--------|------|
| **PIN 码设置** | 设置 6 位数字 PIN 码 |
| **PIN 码管理** | 修改或删除 PIN 码 |
| **安全验证** | 使用 PIN 码验证身份 |

#### 7.4 应用设置

| 功能项 | 说明 |
|--------|------|
| **主题设置** | 应用外观配置 |
| **通知设置** | 消息通知配置 |
| **数据管理** | 数据备份/导出等 |

#### 7.5 资源管理

| 功能项 | 说明 |
|--------|------|
| **文件管理** | 管理上传的图片等资源 |
| **资源列表** | 查看所有已上传资源 |
| **资源清理** | 删除不需要的资源文件 |

【图片插槽】：用户中心界面截图

---

### 八、认证模块 (Auth)

认证模块处理用户身份验证相关功能。

#### 8.1 功能特性

| 功能项 | 说明 |
|--------|------|
| **用户登录** | 使用账号密码登录 |
| **用户注册** | 注册新账户 |
| **身份验证** | 验证用户身份 |
| **错误处理** | 处理登录异常（如 429 请求过于频繁） |

【图片插槽】：登录界面截图

---

## 页面路由结构

| 路径 | 组件 | 说明 |
|------|------|------|
| `/login` | Login.vue | 登录页面 |
| `/429` | error/429.vue | 请求频繁错误页面 |
| `/share/diary/detail` | Diary/ShareDetail.vue | 公开分享页（无需登录） |
| `/` | MainLayout.vue | 主布局容器（含底部导航） |
| `/home` | Home/index.vue | 首页 |
| `/finance` | Finance/index.vue | 账本主页 |
| `/finance/add` | Finance/account/Add.vue | 新增收支 |
| `/finance/quick-add` | Finance/account/QuickAdd.vue | 快速登记 |
| `/finance/structure` | Finance/account/Structure.vue | 系统账户余额 |
| `/finance/flow` | Finance/flow/List.vue | 流水明细 |
| `/finance/flow/calendar` | Finance/flow/Calendar.vue | 每日流水日历 |
| `/finance/flow/:id` | Finance/flow/Detail.vue | 流水详情 |
| `/finance/events` | Finance/events/Index.vue | 固定事件 |
| `/finance/recurring` | Finance/recurring/List.vue | 固定支出 |
| `/finance/assets/register` | Finance/assets/Register.vue | 资产登记 |
| `/finance/assets/edit` | Finance/assets/Edit.vue | 编辑资产登记 |
| `/finance/assets/list` | Finance/assets/List.vue | 资产列表 |
| `/finance/fixed-asset` | Finance/fixedAsset/List.vue | 固定资产 |
| `/finance/fixed-asset/recycle` | Finance/fixedAsset/RecycleBin.vue | 回收站 |
| `/finance/fixed-asset/detail/:id` | Finance/fixedAsset/Detail.vue | 资产详情 |
| `/finance/fixed-asset/edit/:id` | Finance/fixedAsset/Edit.vue | 编辑资产 |
| `/finance/budget` | Finance/budget/List.vue | 预算列表 |
| `/finance/budget/type-select` | Finance/budget/TypeSelect.vue | 预算类型选择 |
| `/finance/budget/shopping` | Finance/budget/Shopping.vue | 购物预算 |
| `/finance/budget/travel` | Finance/budget/Travel.vue | 出行预算 |
| `/finance/budget/eat` | Finance/budget/Eat.vue | 餐饮预算 |
| `/finance/budget/detail/:id` | Finance/budget/Detail.vue | 预算详情 |
| `/finance/report` | Finance/report/Report.vue | 财务报表 |
| `/finance/report/flow-filter` | Finance/report/flow/FlowFilter.vue | 流水筛选 |
| `/finance/report/stats-overview` | Finance/report/stats/StatsOverview.vue | 收支总额与结余 |
| `/finance/report/category-ratio` | Finance/report/stats/CategoryRatio.vue | 类目消费占比 |
| `/finance/report/monthly-trend` | Finance/report/stats/MonthlyTrend.vue | 月度收支趋势 |
| `/finance/report/card-flow` | Finance/report/flow/CardFlow.vue | 银行卡收支明细 |
| `/finance/report/transfer-list` | Finance/report/flow/TransferList.vue | 转账明细查询 |
| `/finance/report/debt-overview` | Finance/report/debt/DebtOverview.vue | 负债统计 |
| `/finance/report/fund` | Finance/report/fund/Fund.vue | 理财总览 |
| `/finance/report/fund/register` | Finance/report/fund/FundRegister.vue | 理财登记 |
| `/finance/report/fund/trend` | Finance/report/fund/FundTrend.vue | 变动走势图 |
| `/finance/report/fund/daily` | Finance/report/fund/FundDaily.vue | 每日净值登记 |
| `/finance/report/fund/earnings` | Finance/report/fund/FundEarnings.vue | 收益明细 |
| `/finance/data` | Finance/data/DataManage.vue | 数据管理 |
| `/finance/data/check` | Finance/data/DbCheck.vue | 数据库检查 |
| `/finance/data/export` | Finance/data/DbExport.vue | 导出数据库 |
| `/finance/data/import` | Finance/data/DbImport.vue | 数据导入 |
| `/finance/data/backup` | Finance/data/DbBackup.vue | 数据库备份 |
| `/finance/data/login-log` | Finance/data/LoginLog.vue | 登录日志 |
| `/diary` | Diary/index.vue | 动态列表 |
| `/diary/add` | Diary/Add.vue | 发布动态 |
| `/diary/detail` | Diary/Detail.vue | 动态详情 |
| `/todo/calendar` | Todo/Calendar.vue | 日历日程 |
| `/user` | User/index.vue | 个人中心 |
| `/user/profile-edit` | User/ProfileEdit.vue | 编辑资料（独立路径） |
| `/user/pin-setup` | User/security/PinSetup.vue | 设置 PIN 码 |
| `/user/pin-manage` | User/security/PinManage.vue | PIN 码管理 |
| `/user/category-manage` | User/category/CategoryManage.vue | 分类管理 |
| `/user/bank-category-manage` | User/category/BankCategoryManage.vue | 银行分类管理 |
| `/user/app-settings` | User/settings/AppSettings.vue | 应用设置 |
| `/user/resource-manage` | User/resource/ResourceManage.vue | 资源管理 |
| `/user/resource-list` | User/resource/ResourceList.vue | 资源列表 |
| `/card` | BankCard/index.vue | 银行卡管理（Tab 切换） |
| `/card/debit` | BankCard/debit.vue | 借记卡列表 |
| `/card/credit` | BankCard/credit.vue | 信用卡列表 |
| `/card/add` | BankCard/card/Add.vue | 添加卡片 |
| `/card/edit` | BankCard/card/Edit.vue | 编辑卡片 |
| `/card/flow` | BankCard/card/flow/List.vue | 卡片流水 |
| `/credit-center` | BankCard/CreditCenter.vue | 信用卡专项 |
| `/credit-center/installment` | BankCard/Installment.vue | 创建分期 |
| `/credit-center/installment/list` | BankCard/InstallmentList.vue | 分期列表 |
| `/credit-full` | BankCard/credit/AddFull.vue | 信用卡全功能录入 |
| `/card/bill/list` | BankCard/bill/List.vue | 账单列表 |
| `/card/bill/detail` | BankCard/bill/Detail.vue | 账单详情 |
| `/card/bill/ledger` | BankCard/bill/Ledger.vue | 账单流水明细 |
| `/card/bill/edit` | BankCard/bill/Edit.vue | 编辑账单 |
| `/card/repay/list` | BankCard/repay/List.vue | 还款列表 |
| `/card/repay/detail` | BankCard/repay/Detail.vue | 还款详情 |
| `/card/repay/add` | BankCard/repay/Add.vue | 添加还款 |
| `/card/repay/edit` | BankCard/repay/Edit.vue | 编辑还款 |
| `/work/job-setting` | Work/JobSetting.vue | 工作信息设置 |
| `/work/salary-calendar` | Work/SalaryCalendar.vue | 工资日历 |
| `/work/salary-day` | Work/SalaryDay.vue | 每日工资 |
| `/work/salary-stat` | Work/SalaryStat.vue | 月度统计 |

---

## API 模块结构

系统 API 封装在 `src/utils/api/` 目录下，采用统一的加密请求封装：

| 模块 | 文件 | 功能 |
|------|------|------|
| 认证 | auth.js | 登录、注册、身份验证、Token 刷新、系统锁定 |
| 安全 | security.js | PIN 码设置、验证和修改 |
| 用户 | user.js | 用户资料管理 |
| 账户 | account.js | 收支记录、余额管理、流水冲正 |
| 资产 | asset.js | 资产快照与登记管理 |
| 分类 | category.js | 收支分类与银行分类管理 |
| 上传 | upload.js | 文件上传 |
| 预算 | budget.js | 预算管理 |
| 银行卡 | card.js | 银行卡管理 |
| 固定资产 | fixedAsset.js | 固定资产管理 |
| 待办 | todo.js | 待办事项管理 |
| 日记 | moment.js | 日记动态 |
| 工资 | work.js | 工作信息与工资核算 |
| 理财 | fund.js | 理财产品管理 |
| 数据管理 | dataManager.js | 数据库备份/导出/导入 |
| 周期支出 | recurring.js | 固定周期支出提醒 |

---

## 项目目录结构

```
web/
├── public/                     # 静态资源
│   ├── favicon.ico             # 网站图标
│   ├── logo.png                # Logo 图片
│   ├── manifest.json           # PWA 清单
│   └── icons/                  # PWA 图标（192/512）
├── src/                        # 源代码目录
│   ├── assets/                 # 资源文件
│   │   ├── css/                # 样式文件
│   │   │   └── style.css       # 全局样式
│   │   └── icon/               # 图标资源
│   ├── components/             # 公共组件
│   │   ├── Diary/              # 日记组件
│   │   │   └── DiaryCard.vue   # 日记卡片组件
│   │   ├── KeyBoard/           # 自定义数字键盘
│   │   │   └── index.vue
│   │   └── PinVerifyDialog.vue # PIN 验证弹窗（全局挂载）
│   ├── layout/                 # 布局组件
│   │   └── MainLayout.vue      # 主布局（顶部导航+底部悬浮岛）
│   ├── router/                 # 路由配置
│   │   ├── index.js            # 路由入口 + beforeEach 守卫
│   │   ├── map.js              # 路由映射表（80+ 路由）
│   │   └── middleware.js       # 路由中间件（用户信息预加载）
│   ├── stores/                 # Pinia 状态管理
│   │   ├── user.js             # 用户状态（信息/Token/PIN）
│   │   └── flowSync.js         # 流水数据同步（详情→列表无感更新）
│   ├── utils/                  # 工具函数
│   │   ├── api/                # API 接口封装（17 个模块）
│   │   ├── request/            # 加密请求封装
│   │   │   ├── interceptors/   # 请求/响应拦截器
│   │   │   ├── client.js       # 客户端上下文
│   │   │   ├── config.js       # 基础配置（baseURL/timeout/加密控制）
│   │   │   ├── core.js         # Axios 核心实例
│   │   │   ├── crypto.js       # AES 加密工具
│   │   │   ├── handshake.js    # 预握手 & AES Key 交换
│   │   │   ├── helpers.js      # 辅助函数
│   │   │   ├── index.js        # 模块入口
│   │   │   └── pin.js          # PIN 对话框管理
│   │   ├── geo/                # 地理位置工具（模块化）
│   │   │   ├── index.js        # 统一入口
│   │   │   ├── amap.js         # 高德地图 API
│   │   │   ├── browser.js      # 浏览器原生定位
│   │   │   ├── ip.js           # IP 定位
│   │   │   └── permission.js   # 定位权限管理
│   │   ├── aes.js              # AES 加密
│   │   ├── device-hash.js      # 设备指纹生成
│   │   ├── env.js              # 环境变量辅助
│   │   ├── fileRequest.js      # 文件上传请求封装
│   │   ├── geolocation.js      # 地理位置（旧版兼容）
│   │   ├── index.js            # 统一导出
│   │   ├── network.js          # 网络工具
│   │   └── securityHeaders.js  # 安全请求头构建
│   ├── views/                  # 页面视图
│   │   ├── Auth/               # 认证（Login, register）
│   │   ├── BankCard/           # 银行卡（debit/credit/card/bill/repay/Installment）
│   │   ├── Diary/              # 日记（列表/发布/详情/分享）
│   │   ├── Finance/            # 账本（account/flow/assets/fixedAsset/budget/report/data/events/recurring）
│   │   ├── Home/               # 首页
│   │   ├── Todo/               # 待办日历
│   │   ├── User/               # 用户（个人中心/安全/分类/资源/设置）
│   │   ├── Work/               # 工资（JobSetting/SalaryCalendar/SalaryDay/SalaryStat）
│   │   └── error/              # 错误页面（429）
│   ├── App.vue                 # 根组件（PIN 对话框全局挂载）
│   └── main.js                 # 入口（Vant/Pinia/Router/PWA/路由守卫）
├── .env                        # 环境变量（VITE_API_BASE_URL, VITE_PORT 等）
├── .env.example                # 环境变量示例
├── .gitignore
├── index.html                  # HTML 入口
├── jsconfig.json               # JS 路径别名（@ → src/）
├── package.json                # 依赖配置
├── vite.config.js              # Vite 构建配置（PWA/代理/别名）
└── yarn.lock                   # 依赖锁定文件
```

---

## 功能关联性说明

### 7.1 核心功能关联

```
                    ┌─────────────────┐
                    │     首页       │
                    │  (总览入口)     │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   账本模块    │◄──►│  银行卡模块   │◄──►│   工资模块    │
│               │    │               │    │               │
│ • 收支记录    │    │ • 借记卡      │    │ • 薪资计算    │
│ • 流水明细    │───►│ • 信用卡      │    │               │
│ • 资产结构    │    │ • 账单还款    │    │               │
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   用户模块      │
                    │                 │
                    │ • 个人资料      │
                    │ • 安全设置      │
                    │ • 分类管理      │
                    └─────────────────┘
```

### 7.2 数据流关联

| 数据类型 | 创建/来源 | 关联模块 | 使用场景 |
|----------|-----------|----------|----------|
| 收支记录 | 账本新增 | 首页、流水列表、统计 | 展示今日收支、总资产计算 |
| 银行卡 | 银行卡管理 | 首页、账单管理 | 展示卡数量、待还金额 |
| 账单 | 账单管理 | 首页、还款管理 | 待还金额计算 |
| 资产 | 资产管理 | 首页 | 总资产计算 |
| 工资 | 工资核算 | 首页 | 今日薪酬展示 |
| 待办 | 待办管理 | 首页 | 待办提醒展示 |
| 动态 | 日记发布 | 日记列表 | 动态浏览 |
| 理财 | 理财登记 | 理财总览、首页 | 投资资产估值 |
| 数据备份 | 数据管理 | 本地存储 | 数据安全保障 |

### 7.3 业务逻辑关联

1. **首页数据聚合**
   - 总资产 = 账户余额 + 资产登记 + 固定资产
   - 待还账单 = 信用卡账单未还金额
   - 今日收支 = 当日流水汇总
   - 今日薪酬 = 工资日薪资 + 兼职收入

2. **流水与账户联动**
   - 记录支出时，自动扣减对应账户余额
   - 记录收入时，自动增加对应账户余额
   - 信用卡还款时，恢复可用额度

3. **预算执行监控**
   - 支出记录时检查预算剩余
   - 接近或超出预算时提醒用户

---

## 安全特性

### 请求层安全

- **预握手（Handshake）**：首次请求前与后端交换 AES 密钥，实现端到端加密
- **AES 加密传输**：POST/PUT/DELETE 请求体自动 AES 加密
- **RSA 加密**：敏感信息（密码等）使用 RSA 非对称加密
- **JWT Token**：登录后使用 JWT 进行身份认证
- **安全请求头**：自动附加 X-Requested-With, X-Client-Timestamp, X-Fingerprint-Hash 等安全头
- **重放攻击防护**：请求附带时间戳，服务端校验 2 分钟时效
- **请求签名**：MD5 盐签名防篡改

### 用户层安全

- **PIN 码二次验证**：敏感操作需要 6 位数字 PIN 码验证
- **设备指纹识别**：基于设备特征生成唯一指纹，绑定会话
- **会话锁定**：支持手动锁定系统，需要 PIN 验证解锁

### 隐私保护

- 首页金额显示/隐藏切换
- 密码输入安全保护
- 敏感操作确认弹窗

---

## 快速开始

### 安装依赖

```bash
# 使用 yarn（推荐）
yarn install

# 或使用 npm
npm install
```

### 开发模式

```bash
# 启动开发服务器（默认端口 5173，可在 .env 中通过 VITE_PORT 修改）
yarn dev
```

开发服务器会自动代理 `/api/v1` 请求到后端（配置在 `.env` 的 `VITE_API_BASE_URL`）。

### 构建生产版本

```bash
# 构建生产包
yarn build

# 预览生产包
yarn preview
```

构建产物输出到 `dist/` 目录，包含 PWA Service Worker。

### 环境配置

复制 `.env.example` 为 `.env` 并配置：

```env
# API 后端地址
VITE_API_BASE_URL=http://localhost:3001

# 开发服务器端口
VITE_PORT=5173

# 应用标题和描述
VITE_APP_TITLE=Golden Finance
VITE_APP_DESCRIPTION=个人财务管理工具

# Demo 模式开关（true=隐藏登录日志等敏感页面）
VITE_APP_DEMO=false
```

---

## 开发指南

### 添加新页面

1. 在 `src/views/` 目录下创建页面组件
2. 在 `src/router/map.js` 中配置路由
3. 页面组件使用 Vant 组件库进行开发

### API 接口封装

在 `src/utils/api/` 目录下创建新的 API 模块：

```javascript
import request from '@/utils/request'

// GET 请求
export function fetchData(params) {
  return request.get('/some-endpoint', { params })
}

// POST/PUT/DELETE 请求体会自动 AES 加密
export function createData(data) {
  return request.post('/some-endpoint', data)
}
```

### 状态管理

使用 Pinia（Options API 风格）在 `src/stores/` 目录下管理状态：

```javascript
import { defineStore } from 'pinia'

export const useExampleStore = defineStore('example', {
  state: () => ({
    items: [],
    loading: false,
  }),
  getters: {
    itemCount: (state) => state.items.length,
  },
  actions: {
    async fetchItems() {
      this.loading = true
      try {
        // ...
      } finally {
        this.loading = false
      }
    },
  },
})
```

### 添加新路由

1. 在 `src/views/` 目录下创建页面组件（使用 `defineOptions({ name: 'YourPage' })` 设置组件名）
2. 在 `src/router/map.js` 中配置路由，设置 `meta.title` 和 `meta.hideTabbar`
3. 如需缓存，将组件 name 添加到 `MainLayout.vue` 的 `cachedPages` 数组

---

## 页面预览

以下是系统中各主要页面的预览效果：

【图片插槽】：首页预览图

【图片插槽】：账本页面预览图

【图片插槽】：银行卡管理预览图

【图片插槽】：工资核算预览图

【图片插槽】：个人中心预览图

---

## 更新日志

详见 `logs/` 目录下的 CHANGELOG 文件及 API 项目日志。

## 版本信息

| 版本 | 日期 | 说明 |
|------|------|------|
| 0.0.1 | 2026-06 | 当前开发版本，含 80+ 路由、17 个 API 模块、完整安全体系 |

---

## 许可证

本项目仅供学习和个人财务管理使用。
