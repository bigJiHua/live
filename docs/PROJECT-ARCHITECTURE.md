# APP（vant-android）项目架构与接手指南

> 目的：让新接手的同学在半小时内读懂 `APP/` 原生安卓工程的架构、每个模块与关键文件的职责，并知道如何构建、运行与扩展。
> 最后更新：2026-10-18（**已实测编译通过**，见第 9.3 / 第 10 章；10 月变更：BYO-backend 站点地址、发布签名、版本号转正已并入）。
> **配套文档**：`项目构造书.md`（**接手首选**：文件↔源文件对照表、改造操作手册、注意事项、P0–P3 坑清单）、`vant-components-inventory.md`（Vant 组件复刻清单）、`web-audit-pages.md`（web 原有功能/样式逐页参考，供按页重构 UI）、`workflow.md`（单轮改造工作流）。

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
| versionName | `2.1.0`（versionCode `20100`；对齐生产 H5 套壳 App 的 `webview-APP/manifest.json`） |

> 仓库 `APP/` **本身就是 git 仓库**（`web/`、`api/` 等各自独立）。接手第一步先看 `git status`；**若读写导致乱码或文件缺失，不要擅自 `git` 恢复，先问负责人**。

---

## 3. 目录总览

```
APP/
├── build.gradle.kts          # 根构建：声明 plugin 版本（AGP/Kotlin）
├── settings.gradle.kts       # 模块包含 :vant-ui / :demo / :app + 国内镜像源
├── gradle.properties          # JVM 参数、AndroidX、kotlin 风格
├── gradlew / gradlew.bat      # Gradle 包装器（无本地 Gradle 也能构建）
├── gradle/                    # Gradle wrapper 文件
├── vant-ui/                   # 【模块1】Vant 组件复刻库（纯 Compose，49 个 .kt / ≈7,988 行，其中 Van* 组件 45 类）
├── app/                       # 【模块2】业务 App（com.live.finance，main 下 187 个 .kt / ≈51,321 行）
├── demo/                      # 【模块3】组件可视化演示 App（仅依赖 vant-ui）
└── docs/                      # 文档（本文件 + 组件清单 + web 审计）
```

三个 Gradle 模块通过 `settings.gradle.kts` 的 `include` 引入，业务 `app` 以 `implementation(project(":vant-ui"))` 依赖组件库。

---

## 4. 模块一：vant-ui（组件库）

**职责**：用 Compose 复刻 web 里实际用到的 Vant 4 组件，形成可复用 UI 底座。业务层不应再写底层控件。
**包名**：`com.live.vant`。**形态**：Android Library（`com.android.library`）。
**主题对齐**：`theme/` 下的颜色/尺寸与 `web/style.css` 的 `--van-*` 覆盖**逐一对齐**（主色 `#3a66e0` 等），深色模式同样支持。
**字号例外**：2026-09-18 起全工程文本按「移动端最佳」提档到**全偶数阶梯** `12 / 14 / 16 / 18 / 20 / 24`（对应 web 的 `<12→12`、13→14、14/15→16、16/17→18），`VantDimens` 的 `fontSizeXs/Md/Lg` = 12/16/18 —— 这是**唯一主动偏离「CSS px → sp 1:1」的地方**，依据是审计 M3 type scale、iOS HIG 与国内主流 App（微信/支付宝）后的结论；图标字号、键盘按键与 badge/pivot 类装饰文字一律保持 Vant 出厂值。完整口径与脚本见 `项目构造书.md` §7.9。

### 4.1 文件清单（按分组）

| 分组（包路径） | 文件 | 对应 Vant 组件 |
|---|---|---|
| `basic/` | `VanButton` `VanCell` `VanCellGroup` `VanDivider` `VanEmpty` `VanImage` `VanLoading` `VanNoticeBar` `VanOverlay` `VanProgress`(含 `VanCircle`) `VanSkeleton` `VanTag` | Button / Cell / CellGroup / Divider / Empty / Image / Loading / NoticeBar / Overlay / Progress / Circle / Skeleton / Tag |
| `feedback/` | `VanActionSheet` `VanDialog`(含 `VanConfirmDialog`/`VanAlertDialog`) `VanImagePreview` `VanList`(含 `VanListFooter`) `VanPopup` `VanPullRefresh` `VanToast`(`VanToastHost`) | ActionSheet / Dialog / ImagePreview / List / Popup / PullRefresh / Toast |
| `form/` | `VanCheckbox` `VanDatePicker`(含 `VanDatetimePicker`) `VanField` `VanNumberKeyboard` `VanPasswordInput` `VanPicker` `VanRadio` `VanSearch` `VanStepper`(含 `VanSwitch` ⚠) `VanUploader`；`VanSafeKeyboard` `VanSecureKeyboard` `VanSimpleKeyboard` | Checkbox / DatePicker / Field / NumberKeyboard / PasswordInput / Picker / Radio / Search / Stepper / Switch / Uploader ／ 另加 3 个自研安全键盘（对应 web `components/KeyBoard/*`） |
| `icon/` | `VanIcon` `VantIconCodes` | Icon（259 个码点字体提取到 `VantIconCodes`） |
| `layout/` | `VanRowCol` | Row / Col 栅格 |
| `nav/` | `VanCollapse`(含 `VanCollapseItem`) `VanDropdownMenu` `VanNavBar` `VanTabbar` `VanTabs` | Collapse / CollapseItem / DropdownMenu / NavBar / Tabbar / Tabs |
| `other/` | `VanCalendar` `VanSwipe` | Calendar / Swipe |
| `theme/` | `VantColors` `VantDimens` `VantTheme` | 颜色/尺寸/主题对象（含 light & dark） |
| （根） | `PathExt.kt` | Compose `Modifier` 扩展工具 |

> 共 **49 个 `.kt` / ≈7,988 行**（其中 `Van*` 组件文件 45 个）。完整组件能力与使用统计见 `docs/vant-components-inventory.md`；逐组件文件↔Vant 对照见 `docs/项目构造书.md` §5.1。
> ⚠ 两个易错点：`VanSwitch` 与 `VanStepper` 同文件、`VanCircle` 与 `VanProgress` 同文件。

---

## 5. 模块二：app（业务 App）

**包名**：`com.live.finance`。**形态**：Application（`com.android.application`）。
**分层**（清晰的三层 + 横切）：`core`（基础设施）→ `data`（模型/仓库）→ `ui`（屏幕）；`di` 手写依赖注入；`theme` 业务主题。

### 5.1 入口与壳层

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 安卓入口 Activity，`setContent { AppRoot() }` |
| `App.kt` | **Application**：创建并持有 `AppGraph`（`App.of(context).graph`），启动时 `graph.init()` |
| `ui/AppRoot.kt` | **导航装配中心**：`NavHost` 注册路由目的地（已接 **80 处**）；同时注入主题 / Toast / PIN 弹窗宿主 |
| `core/nav/Routes.kt` | 路由常量集中定义（85 个，绝大多数已接屏幕） |
| `ui/main/GlobalNavBar.kt` | **全局顶部导航栏**（第七轮）：复刻 web `MainLayout` 的 `van-nav-bar`——标题 / 返回（含 web 的 `cNum` 计数怪癖）/ 回首页 / lock 锁定链路 |
| `core/nav/RouteTitles.kt` | 顶栏标题表（**80 条**，逐条取 web `router/map.js` 的 `meta.title`；含 PIN 设置 mode 与预算编辑态两处按参数分叉） |

### 5.2 core（基础设施）

| 文件 | 职责 |
|---|---|
| `AppConfig.kt` | 全局配置：`useFake`（是否走假数据）、**运行时站点地址**（`baseUrl` / `siteUrl`，由用户在登录页「接入站点」输入并持久化到 `store/SiteStore.kt`）、`fullFileUrl()` 拼接；**不再读 `BuildConfig.BASE_URL`**（10-17 起 BYO-backend） |
| `store/SiteStore.kt` | 站点接入信息持久化（API 地址 / 分享 URL），启动时由 `App.kt` 读回并覆盖 `AppConfig` |
| `ThemeStore.kt` | 主题状态（亮/暗、主色）集中管理，跨屏共享 |
| `PinCoordinator.kt` | PIN 码锁屏协调（与 `SecurityRepository` 配合） |
| `TimeFmt.kt` | 时间格式化工具 |
| `crypto/AesCbc.kt` | AES-256-CBC 加解密（与 web 后端密钥派生一致） |
| `crypto/RsaUtil.kt` | RSA 工具（握手公钥、PIN 密文） |
| `net/ApiClient.kt` | **网络核心**：Retrofit+OkHttp，封装握手、加密信封（`{_p}`）、GET 明文/写操作加密、KEY_ERR/401 自动重握手；**未接入站点时直接守卫拦截**（不把空 baseUrl 丢给 OkHttp） |
| `net/SiteProbe.kt` | 「接入站点」探测（校验用户输入的 API 地址可用性 / 握手可达） |
| `net/ClientContext.kt` | 客户端请求上下文（时间戳 / nonce / 自定义 UA 等公共头） |
| `net/HandshakeManager.kt` | 与后端握手流程（拿会话密钥、指纹注册） |
| `net/Envelope.kt` | 前后端标准信封 `{status,message,data}` 解析 |
| `net/DeviceProfile.kt` | 设备指纹（`X-FP-ID` 等）采集 |
| `security/DeviceRiskInspector.kt` | 设备风险信号采集（上报 `/security/risk-report`） |
| `security/RsaUtil.kt` | 安全键盘字符级 RSA 加解密 |
| `loc/GpsLocation.kt` | 定位（对应 web 的 IP 定位/逆地理能力） |
| `BankIconCache.kt` | 银行图标缓存（列表滚动不重复下载） |
| `store/SessionStore.kt` | 登录会话（token/用户态），基于 DataStore |
| `di/AppGraph.kt` | **手写 DI**：按 `useFake` 决定注入 `FakeXxxRepository` 还是 `RemoteXxxRepository` |

### 5.3 data（模型 + 仓库，双实现）

- `model/`（**21 个**）：`AssetRegister` `Balance` `Bill` `Card` `Category` `DbAdmin` `FixedBudget` `Foreign` `Fund` `GeoPlace` `Installment` `LoginLog` `Models`(聚合根) `Moment` `Pool` `Recurring` `Repay` `Resource` `Salary` `Stats` `TodoJob`。
- `repo/`（**20 个**，每个均 **Fake + Remote 双实现**，靠 `AppGraph` 切换）：`AssetRepository` `AuthRepository` `BalanceRepository` `BillRepository` `CardRepository` `CategoryRepository` `DataRepository` `FixedBudgetRepository` `FlowRepository` `ForeignRepository` `FundRepository` `GeoRepository` `LoginLogRepository` `MomentRepository` `PoolRepository` `RecurringRepository` `RepayRepository` `ResourceRepository` `SecurityRepository` `TodoWorkRepository`。
- 双实现机制：`AppConfig.useFake == true` 时所有仓库返回本地写死的演示数据（无需后端即可跑通主干）；`false` 时走 `ApiClient` 连真后端。**注意：第八轮起两个 flavor 的 `USE_FAKE` 都是 `false`，即默认全走真实接口，Fake 分支保留备用。**

### 5.4 theme

| 文件 | 职责 |
|---|---|
| `theme/ThemePreset.kt` | 10 套主题预设（对齐 web `useUiTheme.js`） |
| `theme/AppTokens.kt` | 解析后的 token 集（颜色/字号/圆角/阴影），`resolveTokens(choice, isDark)` |
| `theme/MoneyColors.kt` | 收支金额配色模式（对齐 web `useMoneyColor.js`） |
| `theme/AppTheme.kt` | 业务层 Compose `MaterialTheme` 包装，桥接 `vant-ui` 的主题色 |

### 5.5 ui（屏幕，按业务域分包，**118 个 .kt / ≈43,150 行**）

| 业务域（包） | 屏幕 / 文件 | 状态 |
|---|---|---|
| `auth/` | `LoginScreen` | ✅ |
| `main/` | `MainScreen`（主壳 + 底部玻璃岛 Tabbar）、`GlobalNavBar`（全局顶栏，挂在 `AppRoot` 的 `Column` 顶部）、`PinVerifyDialog`、`HomeExitDialog` | ✅ |
| `home/` | `HomeScreen`（首页看板、今日预估薪酬卡、待办提醒入口） | ✅ |
| `finance/` | `FinanceScreen`（账本主页） | ✅ |
| `account/` | `AddTransactionScreen` `QuickAddScreen` `AccountStructureScreen` `BalanceFlowScreen` `CardFlowScreen` `RecordShared` | ✅ 全部 |
| `flow/` | `FlowListScreen` `FlowDetailScreen` `CalendarScreen` `FlowItemView` `FlowCards` | ✅ |
| `asset/` | `AssetListScreen` `AssetEditorScreen`(登记/编辑两用) `AssetTrendScreen` `AssetShared` | ✅ |
| `fixed/` | `FixedAssetListScreen` `FixedAssetDetailScreen` `FixedAssetEditScreen` `FixedAssetRecycleBinScreen` `FixedAssetForm` `FixedAssetImagePicker` `FixedFormat` | ✅ |
| `recurring/` | `RecurringListScreen`（固定支出 + 固定事件两用） | ✅ |
| `budget/` | `BudgetListScreen` `BudgetTypeSelectScreen` `BudgetFormScreen`(买/行/吃) `BudgetDetailScreen` `BudgetShared` | ✅ |
| `report/` | `ReportCenterScreen` `StatsOverviewScreen` `MonthlyTrendScreen` `CategoryRatioScreen` `FlowFilterScreen` `TransferListScreen` `DebtOverviewScreen` + 理财子域 `FundScreen`(总览) `FundRegisterScreen` `FundTrendScreen` `FundDailyScreen` `FundEarningsScreen` + `FundUi` `FundCalendar`（共 14 个文件） | ✅ |
| `bankcard/` | `CardManageScreen` `CardAddScreen` `CardEditScreen` `CreditFullScreen` `CreditCenterScreen` `InstallmentCreateScreen` `InstallmentListScreen` `LimitManageScreen` `ForeignScreen` `BillListScreen` `BillDetailScreen` `BillLedgerScreen` `RepayListScreen` `RepayDetailScreen` `RepayAddScreen` `RepayEditScreen` + `CardFormShared` `CardStackView` `CardOrgBadge` + 卡页 WebView `CardWebScreen` `CardWebView` `CardDebugLog`（共 22 个文件；原 `CardListScreen` 已删，功能由 `CardManageScreen` 承载） | ✅ |
| `todo/` | `TodoCalendarGrid`（Todo / 流水 / 工资三处共享） `TodoCalendarScreen` | ✅ |
| `work/` | `JobListScreen` `SalaryCalendarScreen` `SalaryDayScreen` `SalaryStatScreen` | ✅ |
| `diary/` | `DiaryListScreen` `DiaryAddScreen` `DiaryDetailScreen` `DiaryRichEditorWebView` `RichHtml` `ShareDetailScreen`（公开分享页，已落地） | ✅ |
| `user/` | `UserScreen` `AppSettingsScreen` `CategoryManageScreen` `ProfileEditScreen` `PinManageScreen` `PinSetupScreen` `ResourceManageScreen` `ResourceListScreen` | ✅ |
| `data/` | `DataManageScreen` `DbCheckScreen` `DbExportScreen` `DbImportScreen` `DbBackupScreen` `LoginLogScreen` `LoginAnomaly` | ✅ |
| `error/` | `Error429Screen` | ✅ |
| `common/` | `Ui`(ScreenScaffold + 模糊/YearMonthSaver 等公共件) `AppButton` `AppCell` `AppField` `AppTag` `AppDialogShell` `BankIconView` `CssShadow` `Money` `Format` `FlowGrouping` `Upload` | 通用件（12 个） |
| `common/chart/` | `BarChart` `LineChart` `DonutChart` `DualAxisChart` `MiniChart` `ChartUtils` | 自绘图表（替代 web 的 echarts，6 个） |

> **路由实况**：`Routes.kt` 有 85 个常量，`AppRoot.kt` 已注册 **80 处 `composable(...)`**（web 侧 `router` 现有 96 条 `path:` 声明，含 redirect）；**未接仅 `CARD_FLOW`**（web 自身死页面，见 `项目构造书.md` 附录判定）。`DIARY_SHARE` 已有 `ui/diary/ShareDetailScreen.kt` 落地。
> **逐页 web ↔ 原生对照见 `docs/项目构造书.md` 第 5 章**；完整路由对照另见 `docs/web-audit-pages.md` §18。

---

## 6. 模块三：demo（组件演示）

**包名**：`com.live.vant.demo`。独立可运行 App，**仅依赖 `vant-ui`**，不含业务逻辑；用于预览每个复刻组件的视觉与交互、验证主题。不参与业务构建，接手时可作为"组件是否画对"的快速验证场。

---

## 7. 数据与网络架构

### 7.1 双数据源（fake / prod）与「用户自带后端」（BYO-backend）
- **2026-09-18 第八轮起：两个变体都走真实接口**（`fake` / `prod` 的 `USE_FAKE` 均为 `false`）；`FakeXxxRepository` 全部保留作离线兜底（把某 flavor 的 `USE_FAKE` 改回 `true` 即可）。
- **2026-10-17 起：APK 内不再带任何后端地址** —— `API_BASE_URL` / `SITE_URL` 这类 `buildConfigField` 已彻底移除（`app/build.gradle.kts` 有显式禁令注释）。站点地址由用户在登录页「**接入站点**」输入，经 `SiteProbe` 探测后持久化到 `store/SiteStore.kt`，启动时由 `App.kt` 读回覆盖 `AppConfig.baseUrl` / `siteUrl`；未接入时 `ApiClient` 直接守卫拦截，分享入口阻断（`DiaryDetailScreen`）。
- 因此两个 **productFlavor** 不再靠地址区分，差异仅为包名后缀与版本号后缀：`fake` → `applicationIdSuffix = ".fake"` + `versionNameSuffix = "-fake"`；`prod` → 正式包名/版本。
- 可选的安全项：`gradle.properties` 的 `API_HOST`（证书域名，留空则 networkSecurityConfig 不做域名白名单）与 `API_PINNED_SHA256`（证书指纹，Let's Encrypt 请留空）；`BuildConfig.ENABLE_PINNING` 由「指纹是否非空」推导（`enablePinning = apiPinnedSha.isNotBlank()`）。
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
./gradlew :app:assembleFakeDebug      # fake 变体（包名 .fake 后缀；地址由用户在登录页接入）
./gradlew :app:assembleProdDebug      # prod 变体（正式包名 2.1.0）
./gradlew :app:assembleProdRelease    # 正式 release 包（需配签名，见 docs/docs/app/release-signing.md）
./gradlew :demo:assembleDebug         # 组件演示包
./gradlew :app:installFakeDebug        # 构建并安装到设备
```

### 9.3 关键坑点（接手必读）
1. **构建环境（2026-09-18 实测通过）**：JDK 17（本机 `D:\Java_JDK`）+ Android SDK 34（`local.properties` 的 `sdk.dir`）+ `GRADLE_USER_HOME=D:/Google/Android-gradle`（离线缓存，须显式设置）。
   实测结果：`:vant-ui:compileDebugKotlin`、`:app:compileProdDebugKotlin`、`:app:compileFakeDebugKotlin` 强制全量重编 **0 错误 / BUILD SUCCESSFUL**；三个包（prod、fake、demo）均已产出 APK。命令与判读方式见 `docs/项目构造书.md` 第 0 章与第 11 章。
2. **后端地址是运行时输入**（BYO-backend，2026-10-17 起）：APK 内不再注入 `BASE_URL`/`SITE_URL`，不要再把地址写回 `build.gradle.kts`；联调时在登录页「接入站点」填地址即可。HTTPS 与证书锁定走 `API_HOST` / `API_PINNED_SHA256`（留空即关闭域名白名单与 Pinning）。
3. **`APP/` 已纳入 git**（仓库根即 `APP/`；`web/`、`api/` 等是各自独立的仓库/目录）。动手前先看工作树状态；**若读写导致乱码或文件缺失，不要擅自 `git` 恢复，先问负责人**。
4. **DataStore 存储**：会话 token 在 `core/store/SessionStore.kt`，卸载重装即清空；调试时别只清 app 缓存。
5. **并行构建会撞产物竞态**：与 Android Studio / 其他会话同时跑 Gradle，可能报 `dexBuilder…desugar_graph…系统找不到指定的路径`（常伴 `Detected multiple Kotlin daemon sessions`）——**属环境竞态而非代码错误**，错峰重跑即绿。

---

## 10. 当前进度（2026-10-18 核对）

**已完成（真实代码、非桩）**
- 组件库 `vant-ui`：**49 个 `.kt`（其中 `Van*` 组件 45 类）** + 7 类函数式 API，另加 3 个自研安全键盘；主题（10 预设 + 收支色 + 明暗）对齐 web。
- 业务骨架：单 Activity + Navigation 闭环、登录（安全键盘 / 掩码 / 闪烁光标）**+ 登录页「接入站点」**（BYO-backend）、主壳 3 Tab（底部玻璃岛）。
- **已接 80 处路由目的地**（`AppRoot.kt` 的 `composable(...)`；web 侧 `router` 现有 96 条 `path:` 声明，含 redirect），覆盖 14 个业务域：流水(列表/详情/日历)、记账/快速登记/账户结构/余额流水/卡流水、资产(列表/登记/编辑/走势)、固定资产(列表/详情/编辑/回收站)、固定支出/固定事件、预算(列表/类型选择/三类型表单/详情)、报表(中心/概览/月度趋势/类目占比/流水筛选/转账/负债)、理财(总览/登记/走势/日结/收益)、银行卡(管理/新增/编辑/信用卡全功能/中心/分期/额度/外币)、账单(列表/详情/台账)、还款(列表/详情/增改)、待办日历、工作薪酬(设置/日历/日明细/月统计)、动态(列表/发布/详情/分享详情)、我的/资料编辑/分类管理/PIN 管理/资源管理/应用设置、数据管理(首页 + 5 子页)/登录日志、429 页。
- 网络层：握手 + AES 加密信封 + 双 flavor 切换，协议与 web 后端等价；PIN `8303` 拦截链路（route-verify 令牌重发）已落地；**站点地址运行时输入 + 可选证书锁定**（10-17）。
- 规模：`app/src/main` **187 个 `.kt` / ≈51,321 行**（`ui/` 118 个 / ≈43,150 行，含 6 个自绘图表）；`data/model` 21、`data/repo` 20；`vant-ui` ≈7,988 行；`demo` 2 个。

**实测结论（截至 2026-10-18）**
- 编译验证持续通过（最近轮次均 `BUILD SUCCESSFUL`、0 条 `e:`）；历史实测：强制全量重编 0 错误（2026-09-18，2m30s，44 个任务真实执行）。
- 现有产物（`app/build/outputs/apk/`）：`app-prod-release.apk`（**10-18，6.37 MB，已签名**）、`app-prod-debug.apk`（10-17，19.75 MB）、`app-fake-debug.apk`（10-12，17.81 MB）。
- ✅ **编译与打包已验证；真机安装与运行验证仍由使用者完成。**

**缺失 / 风险**
- 未接路由 1 条：`CARD_FLOW`（web 自身死页面，见 `项目构造书.md` 附录判定；另有 `Routes.CARD_*` 三常量共用 `CardManageScreen`）。
- 非 1:1 复用 1 处：银行分类管理复用分类管理屏（web 是卡片网格 + 银行图标上传）。
- 结构性差距：报表选月用 `VanPicker` 而非月历弹层；日历网格缺「账 / 还」角标；图表（web 用 echarts）为自绘（`ui/common/chart/`）。
- 端到端安全链路（PIN 全链路、429 限流兜底、设备指纹漂移）建议上线前完整走一遍。
- 无业务自动化测试（`tests/` 下无 `.kt`）、无 lint/CI。

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

## 12. 上线前清单

- [x] 纳入 git 版本管理（`APP/` 仓库；当前 HEAD 见 `git log`）。
- [x] `:app:assembleFakeDebug` + `:app:assembleProdDebug` 编译通过（2026-09-18 起持续通过）。
- [x] **版本号转正**：`versionName 2.1.0` / `versionCode 20100`（对齐 H5 套壳包的版本线）。
- [x] **发布签名**：`release.jks` 与 `gradle.properties` 的 `RELEASE_KEYSTORE`/口令（本机或 `~/.gradle/gradle.properties`，**不入库**）；已产出 `app-prod-release.apk`（10-18）。步骤见 `D:\Code\live\APP-RELEASE-SIGNING.md`（正式版：`Docs/docs/app/release-signing.md`）。
- [x] **站点地址改为运行时输入**（BYO-backend）：APK 内不含任何后端地址与分享域名。
- [ ] 混淆规则（R8/ProGuard）核对：确认不破坏 Gson 模型与反射字段。
- [ ] 真机安装运行验证（`prod` release 包 + `fake` 调试包各一遍）。
- [ ] 与真后端联调：接入站点 → 握手、AES 信封、限流 429、PIN / 安全键盘全链路。
- [ ] 可选安全项按需配置：`API_HOST`（域名锁定）、`API_PINNED_SHA256`（仅自签/固定 CA 才填）。
- [ ] 与 `webview-APP` 路线二选一或明确并存策略。

---

## 13. 相关文档索引

| 文档 | 内容 |
|---|---|
| `docs/项目构造书.md` | **接手首选**：完整施工图 + 交接书（文件↔源文件对照表、改造操作手册、注意事项、P0–P3 坑清单、域契约速查、上线清单） |
| `docs/PROJECT-ARCHITECTURE.md`（本文件） | 架构、模块、文件/文件夹全介绍、构建运行、扩展指引 |
| `docs/vant-components-inventory.md` | `vant-ui` 复刻的 Vant 组件能力清单（45 类 `Van*` + 函数式 API） |
| `docs/web-audit-pages.md` | web 原有功能/样式逐页面参考（逐页 + 组件 + 主题变量 + web→APP 路由对照），重构 UI 的规格书 |
