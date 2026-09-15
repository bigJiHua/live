# APP（vant-android）项目架构与接手指南

> 目的：让新接手的同学在半小时内读懂 `APP/` 原生安卓工程的架构、每个模块与关键文件的职责，并知道如何构建、运行与扩展。
> 最后更新：2026-09-15。配套文档：`vant-components-inventory.md`（Vant 组件复刻清单）、`web-audit-pages.md`（web 原有功能/样式逐页参考，供按页重构 UI）。

---

## 1. 这个项目是什么

`APP/` 是 **Gold 财管** 的移动端**纯原生（Jetpack Compose）重写工程**，代号 `vant-android`。

- 同一套产品有三条前端线，别混淆：
  1. `web/`：Vue3 + Vant 的 **Web 前端**（功能最全，是事实上的"真源"）。
  2. `webview-APP/`：Vue3 打包 + 套壳的 **H5 套壳 APP**（不是原生）。
  3. `APP/`（本文档）：用 Compose **1:1 复刻 web 用到的 Vant 组件**，再在其上搭原生财管 App，目标是替换 H5 套壳、拿到原生性能与体验。
- 核心思路：先把 Vant 组件用 Compose 复刻成独立库 `vant-ui`，业务代码只调用 `VanButton`/`VanCell` 等，从而让 UI 结构与 web 高度对齐、便于逐页迁移。

---

## 2. 技术栈与版本

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 1.9.24（JVM target 17） |
| UI | Jetpack Compose BOM 2024.06.00（`composeOptions` 编译器扩展 1.5.14） |
| 构建 | Gradle 8.x（AGP 8.5.2）、Kotlin DSL（`.kts`） |
| Android | compileSdk 34 / minSdk 24 / targetSdk 34 |
| 导航 | `navigation-compose` 2.7.7 |
| 网络 | Retrofit 2.11 + OkHttp 4.12 + Gson 2.11（**未用** Kotlinx Serialization 转换器，避免额外编译器插件） |
| 协程 | `kotlinx-coroutines-android` 1.8.1 |
| 本地存储 | `datastore-preferences` 1.1.1（存 token/指纹/会话，替代 SharedPreferences） |
| 图片 | 由 `vant-ui` 通过 `coil` 传递引入 |
| versionName | `0.1.0-wave0` |

> 仓库 `D:\Code\live` 整体**未纳入 git**，所有代码在磁盘上，接手后第一步建议纳入版本管理（见 §12）。

---

## 3. 目录总览

```
APP/
├── build.gradle.kts          # 根构建：声明 plugin 版本（AGP/Kotlin）
├── settings.gradle.kts       # 模块包含 :vant-ui / :demo / :app + 国内镜像源
├── gradle.properties          # JVM 参数、AndroidX、kotlin 风格
├── gradlew / gradlew.bat      # Gradle 包装器（无本地 Gradle 也能构建）
├── gradle/                    # Gradle wrapper 文件
├── vant-ui/                   # 【模块1】Vant 组件复刻库（纯 Compose，43 个 .kt）
├── app/                       # 【模块2】业务 App（com.live.finance，75 个 .kt）
├── demo/                      # 【模块3】组件可视化演示 App（仅依赖 vant-ui）
└── docs/                      # 文档（本文件 + 组件清单 + web 审计）
```

三个 Gradle 模块通过 `settings.gradle.kts` 的 `include` 引入，业务 `app` 以 `implementation(project(":vant-ui"))` 依赖组件库。

---

## 4. 模块一：vant-ui（组件库）

**职责**：用 Compose 复刻 web 里实际用到的 Vant 4 组件，形成可复用 UI 底座。业务层不应再写底层控件。
**包名**：`com.live.vant`。**形态**：Android Library（`com.android.library`）。
**主题对齐**：`theme/` 下的颜色/尺寸与 `web/style.css` 的 `--van-*` 覆盖**逐一对齐**（主色 `#3a66e0` 等），深色模式同样支持。

### 4.1 文件清单（按分组）

| 分组（包路径） | 文件 | 对应 Vant 组件 |
|---|---|---|
| `basic/` | `VanButton` `VanCell` `VanCellGroup` `VanDivider` `VanEmpty` `VanImage` `VanLoading` `VanNoticeBar` `VanOverlay` `VanProgress` `VanSkeleton` `VanTag` | Button / Cell / CellGroup / Divider / Empty / Image / Loading / NoticeBar / Overlay / Progress / Skeleton / Tag |
| `feedback/` | `VanActionSheet` `VanDialog` `VanImagePreview` `VanList` `VanPopup` `VanPullRefresh` `VanToast` | ActionSheet / Dialog / ImagePreview / List / Popup / PullRefresh / Toast |
| `form/` | `VanCheckbox` `VanDatePicker` `VanField` `VanNumberKeyboard` `VanPasswordInput` `VanPicker` `VanRadio` `VanSearch` `VanStepper` `VanUploader` | Checkbox / DatePicker / Field / NumberKeyboard / PasswordInput / Picker / Radio / Search / Stepper / Uploader |
| `icon/` | `VanIcon` `VantIconCodes` | Icon（259 个码点字体提取到 `VantIconCodes`） |
| `layout/` | `VanRowCol` | Row / Col 栅格 |
| `nav/` | `VanCollapse` `VanDropdownMenu` `VanNavBar` `VanTabbar` `VanTabs` | Collapse / DropdownMenu / NavBar / Tabbar / Tabs |
| `other/` | `VanCalendar` `VanSwipe` | Calendar / Swipe |
| `theme/` | `VantColors` `VantDimens` `VantTheme` | 颜色/尺寸/主题对象（含 light & dark） |
| （根） | `PathExt.kt` | Compose `Modifier` 扩展工具 |

> 完整组件能力对照见 `docs/vant-components-inventory.md`。

---

## 5. 模块二：app（业务 App）

**包名**：`com.live.finance`。**形态**：Application（`com.android.application`）。
**分层**（清晰的三层 + 横切）：`core`（基础设施）→ `data`（模型/仓库）→ `ui`（屏幕）；`di` 手写依赖注入；`theme` 业务主题。

### 5.1 入口与壳层

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 安卓入口 Activity，setContent 挂 `App` |
| `App.kt` | 应用根 Composable，初始化 `AppGraph`、主题、`AppRoot` 导航 |
| `ui/AppRoot.kt` | **导航装配中心**：`NavHost` 注册所有路由目的地（约 26 个已接 + 大量占位常量） |
| `core/nav/Routes.kt` | 路由常量集中定义（约 90 个，绝大多数暂无对应屏幕） |

### 5.2 core（基础设施）

| 文件 | 职责 |
|---|---|
| `AppConfig.kt` | 全局配置：`useFake`（是否走假数据）、读取 `BuildConfig.BASE_URL`/`USE_FAKE` |
| `ThemeStore.kt` | 主题状态（亮/暗、主色）集中管理，跨屏共享 |
| `PinCoordinator.kt` | PIN 码锁屏协调（与 `SecurityRepository` 配合） |
| `TimeFmt.kt` | 时间格式化工具 |
| `crypto/AesCbc.kt` | AES-256-CBC 加解密（与 web 后端密钥派生一致） |
| `crypto/RsaUtil.kt` | RSA 工具（握手公钥、PIN 密文） |
| `net/ApiClient.kt` | **网络核心**：Retrofit+OkHttp，封装握手、加密信封（`{_p}`）、GET 明文/写操作加密、KEY_ERR/401 自动重握手 |
| `net/HandshakeManager.kt` | 与后端握手流程（拿会话密钥、指纹注册） |
| `net/Envelope.kt` | 前后端标准信封 `{status,message,data}` 解析 |
| `net/DeviceProfile.kt` | 设备指纹（`X-FP-ID` 等）采集 |
| `store/SessionStore.kt` | 登录会话（token/用户态），基于 DataStore |
| `di/AppGraph.kt` | **手写 DI**：按 `useFake` 决定注入 `FakeXxxRepository` 还是 `RemoteXxxRepository` |

### 5.3 data（模型 + 仓库，双实现）

- `model/`（17 个）：`AssetRegister` `Balance` `Bill` `Card` `Category` `FixedBudget` `Foreign` `Fund` `LoginLog` `Models`(聚合根) `Moment` `Pool` `Recurring` `Repay` `Resource` `Stats` `TodoJob`。
- `repo/`（19 个，每个均 **Fake + Remote 双实现**，靠 `AppGraph` 切换）：`AssetRepository` `AuthRepository` `BalanceRepository` `BillRepository` `CardRepository` `CategoryRepository` `DataRepository` `FixedBudgetRepository` `FlowRepository` `ForeignRepository` `FundRepository` `LoginLogRepository` `MomentRepository` `PoolRepository` `RecurringRepository` `RepayRepository` `ResourceRepository` `SecurityRepository` `TodoWorkRepository`。
- 双实现机制：`AppConfig.useFake == true` 时所有仓库返回本地写死的演示数据（**无需后端即可跑通主干**）；`false` 时走 `ApiClient` 连真后端。

### 5.4 theme

| 文件 | 职责 |
|---|---|
| `theme/AppTheme.kt` | 业务层 Compose `MaterialTheme` 包装，桥接 `vant-ui` 的主题色 |

### 5.5 ui（屏幕，按业务域分包，43 个 .kt）

| 业务域（包） | 屏幕文件 | 路由/状态 |
|---|---|---|
| `auth/` | `LoginScreen` | 登录 ✅ |
| `main/` | `MainScreen` | 主壳（底部 3 Tab：首页/账本/我的）✅ |
| `home/` | `HomeScreen` | 首页 ✅ |
| `finance/` | `FinanceScreen` | 账本主页 ✅ |
| `account/` | `AddTransactionScreen` `AccountStructureScreen` `BalanceFlowScreen` | 记账 ✅ / 账户结构 ✅ / 余额流水 ⬜占位 |
| `flow/` | `FlowListScreen` `FlowDetailScreen` `CalendarScreen` `FlowItemView`(复用项) | 流水列表 ✅ / 详情 ✅ / 日历 ✅ |
| `asset/` | `AssetListScreen` | 资产列表 ✅（登记/编辑/走势占位） |
| `fixed/` | `FixedAssetListScreen` | 固定资产 ✅（回收站/详情/编辑占位） |
| `recurring/` | `RecurringListScreen` | 固定支出 ✅ |
| `budget/` | `BudgetListScreen` | 预算 ✅（类型/明细占位） |
| `report/` | `StatsOverviewScreen` `MonthlyTrendScreen` `FundScreen` | 收支概览 ✅ / 月度趋势 ✅ / 基金 ✅（筛选/卡流水/转账/负债/理财子页占位） |
| `bankcard/` | `CardListScreen` `CardEditScreen` `BillListScreen` `RepayListScreen` `LimitManageScreen` `InstallmentListScreen` `ForeignScreen` | 卡列表 ✅ / 卡编辑 ✅ / 账单 ✅ / 还款 ✅ / 额度 ✅ / 分期列表 ✅ / 外币 ✅（中心/分期创建/账单详情/台账/还款增改占位） |
| `todo/` | `TodoListScreen` | 待办日历 ✅ |
| `work/` | `JobListScreen` | 工作/工资 ✅（日历/日明细/月统计占位） |
| `diary/` | `DiaryListScreen` `DiaryAddScreen` | 动态列表 ✅ / 发布 ⬜占位 |
| `user/` | `UserScreen` `AppSettingsScreen` `CategoryManageScreen` `ProfileEditScreen` `PinSetupScreen` `ResourceListScreen` | 我的 ✅ / 设置 ✅ / 分类管理 ✅ / 资料编辑 ✅ / PIN设置 ⬜ / 资源 ⬜ |
| `data/` | `DataManageScreen` `LoginLogScreen` | 数据管理 ✅ / 登录日志 ✅（校验/导入/导出/备份占位） |
| `error/` | `Error429Screen` | 频率限制页 ✅ |

> 已接 ~27 屏 / 占位 ~63 路由。完整 web→APP 路由对照见 `docs/web-audit-pages.md` §18。

---

## 6. 模块三：demo（组件演示）

**包名**：`com.live.vant.demo`。独立可运行 App，**仅依赖 `vant-ui`**，不含业务逻辑；用于预览每个复刻组件的视觉与交互、验证主题。不参与业务构建，接手时可作为"组件是否画对"的快速验证场。

---

## 7. 数据与网络架构

### 7.1 双数据源（fake / prod）
- `app/build.gradle.kts` 定义两个 **productFlavor**：`fake`（本地假数据，无需后端）和 `prod`（连真后端）。
- `fake`：`BASE_URL=http://10.0.2.2:666/`（模拟器访问本机后端）、`USE_FAKE=true`。
- `prod`：`BASE_URL=http://192.168.0.103:666/`（**局域网后端，上线前需改 `https://live.jihau.top`**）、`USE_FAKE=false`。
- Android Studio 的 **Build Variants** 面板可切换 `appFake` / `appProd`。

### 7.2 网络协议（与 web 后端等价）
`ApiClient` + `HandshakeManager` 实现：
1. **握手**：启动时与后端完成密钥协商，拿到会话密钥 + 设备指纹（`X-FP-ID`）。
2. **加密信封**：写操作请求体用 AES-256-CBC 加密后包进 `{_p}`；GET 走明文 `data`。
3. **错误处理**：后端返回密钥错误/401 时自动重握手重试；限流 429 由 `Error429Screen` 承接（真机未联调验证）。
4. **信封解析**：`Envelope` 把后端 `{status,message,data}` 标准结构映射为 Kotlin 结果类型。
> 加密细节（AES 密钥派生、RSA 公钥）在 `core/crypto/`，需与 `api/` 后端保持一致，改动须同步后端。

---

## 8. 导航与路由

- `ui/AppRoot.kt`：`NavHost(startDestination = LOGIN)`，注册已实现的 composable 目的地；占位路由常量已在 `Core/nav/Routes.kt` 定义但多数未挂 composable（点菜单会空实现或留 `TODO`）。
- 登录成功后跳 `MainScreen`（底部 3 Tab：首页/账本/我的）。
- 新增页面标准动作：① 在 `Routes.kt` 加常量 → ② 写 `XxxScreen.kt` → ③ 在 `AppRoot.kt` 的 `NavHost` 注册 `composable(route){ XxxScreen(nav) }` → ④ 在调用处 `nav.navigate(Routes.XXX)`。

---

## 9. 构建与运行

### 9.1 环境要求
- **Android SDK**（compileSdk 34 对应）、JDK 17。
- Android Studio（推荐，便于用 Build Variants 切 flavor、预览 Compose）。
- 联网拉依赖（已配阿里云镜像，见 `settings.gradle.kts`）。

### 9.2 命令
```bash
# 用包装器构建（无需预装 Gradle）
./gradlew :app:assembleFakeDebug      # 假数据 debug 包，可离线跑
./gradlew :app:assembleProdDebug      # 连真后端 debug 包
./gradlew :demo:assembleDebug         # 组件演示包
./gradlew :app:installFakeDebug        # 构建并安装到设备
```

### 9.3 关键坑点（接手必读）
1. **从未在本机 assemble 验证过**：当前环境无 Android SDK，仅用自定义 Kotlin 编译器 harness 做了类型检查（0 错误）。首次交付前务必在 Android Studio 真机/模拟器跑一次 `:demo` 与 `:app` 的 fake 变体，确认可编译可启动。
2. **prod BASE_URL 是局域网地址**：上线前改 `https://live.jihau.top`（并确认 HTTPS + 加密隧道）。
3. **`D:\Code\live` 不是 git 仓库**：整目录未纳入版本管理，先 `git init`/并入现有仓库再动手，避免丢失。
4. **DataStore 存储**：会话 token 在 `core/store/SessionStore.kt`，卸载重装即清空；调试时别只清 app 缓存。

---

## 10. 当前进度（Wave-0 主干）

**已完成（真实代码、非桩）**
- 组件库 `vant-ui`：45 类组件 + 7 类函数式 API 全部复刻，主题对齐 web。
- 业务骨架：导航闭环、登录、主壳 3 Tab。
- 已接屏幕：流水(列表/详情/日历)、记账、卡列表/编辑、账单、还款、额度、分期列表、外币、资产、固定资产、预算、报表概览/月度趋势/基金、待办、工作、动态列表、用户/设置/分类/资料、数据管理/登录日志、429 页。
- 网络层：握手 + AES 加密信封 + 双 flavor 切换，协议与 web 后端等价。

**缺失 / 风险**
- 二级页面大量占位：报表明细（分类占比/卡流水/转账/负债）、预算类型（购物/旅行/餐饮）、数据管理（校验/导入/导出/备份）、PIN 管理/设置、资源库、信用卡中心/分期创建、账单详情/台账、还款增改、日记发布/详情、工资日历/日明细/月统计。
- 加密握手/限流/指纹**未与真后端联调**（协议实现齐全但未经验证）。
- 无自动化测试、无 lint/CI。
- 与安全键盘/PIN 前端骨架一致，但端到端安全链路未验。

---

## 11. 如何扩展（常用任务模板）

**A. 新增一个业务页面**
1. `core/nav/Routes.kt` 加 `const val XXX = "xxx"`。
2. 在 `ui/<域>/` 新建 `XxxScreen.kt`（用 `Van*` 组件拼装，参考同域已有屏幕）。
3. `ui/AppRoot.kt` 注册 `composable(Routes.XXX){ XxxScreen(navController) }`。
4. 调用处 `nav.navigate(Routes.XXX)`。

**B. 接入真实数据**
- 若已有对应仓库（如 `FlowRepository`），在 `AppGraph.kt` 已有 Fake/Remote 双实现；切 `prod` flavor 即走 Remote。
- 新增业务：① `data/model/` 加数据类 → ② `data/repo/` 加 `XxxRepository`（实现 `FakeXxxRepository` + `RemoteXxxRepository`）→ ③ `di/AppGraph.kt` 按 `useFake` 注入 → ④ `ui` 屏幕 `LaunchedEffect` 调用。

**C. 新增/微调组件**
- 在 `vant-ui` 对应分组加 `VanXxx.kt`，主题色必须用 `theme/VantColors` 里的 token（保持深色一致）。改完需同步确认 `web/style.css` 是否有对应 `--van-*`（避免出现 web 没有的硬编码色）。

---

## 12. 上线前清单（建议拍板）

- [ ] 纳入 git 版本管理（`D:\Code\ive` 父目录或单独仓库）。
- [ ] Android Studio 真机 `assembleFakeDebug` + `assembleProdDebug` 验证可编译可启动。
- [ ] prod `BASE_URL` 改 `https://live.jihau.top`，确认 HTTPS + 加密隧道。
- [ ] 与真后端联调：握手、AES 信封、限流 429、PIN/安全键盘。
- [ ] 补占位路由（优先级建议：报表明细 > PIN/安全域 > 数据管理 > 日记二级 > 工资）。
- [ ] 与 `webview-APP` 路线二选一或明确并存策略。

---

## 13. 相关文档索引

| 文档 | 内容 |
|---|---|
| `docs/PROJECT-ARCHITECTURE.md`（本文件） | 架构、模块、文件/文件夹全介绍、构建运行、扩展指引 |
| `docs/vant-components-inventory.md` | `vant-ui` 复刻的 Vant 组件能力清单（45 类 + 函数式 API） |
| `docs/web-audit-pages.md` | web 原有功能/样式逐页面参考（约 95 页 + 组件 + 主题变量 + web→APP 路由对照），重构 UI 的规格书 |
