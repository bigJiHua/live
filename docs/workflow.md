好，停。下面是我**每一轮（自你给方向到交付）实际走的工作流**，以及每步用的确切命令/依据。

## 单轮工作流

**1. 定契约（先读，不猜）**
- 目标：改/加某个域前，先 `grep/awk` 读三处对齐——
  - web 调用：`web/src/utils/api/*.js`（路径、参数、请求/响应字段）
  - 后端真相：`api/src/modules/<域>/api|controller|model`（真实路由、`req.body.data` 读法、SELECT 别名/计算列、枚举）
  - 有时用子代理批量比对 `web` 页 ↔ 原生屏，产出差异清单（就是刚才的 C 阶段）
- 目的：避免我踩过的那两类坑——**包名放错**、**后端 JSON 列当字符串解析崩**。

**2. 落地代码（分层 + 分包规范）**
- 分包铁律（已入记忆）：`data/model/X.kt`(包 `data.model`) 与 `data/repo/XRepository.kt`(包 `data.repo`) **分开两文件**。
- 每加一个域：`ApiXxx 接口 + FakeXxxRepository + RemoteXxxRepository` → `AppGraph` 注册 `val xxx`（`useFake` 决定）→ 建 Screen → `AppRoot` 注册路由 → 入口(首页/账本/我的 MenuRow 或卡片页按钮)。
- 复用件：网络 `ApiClient`、`Envelope/ApiResult`、`Money/MoneyColor/TimeFmt/FlowGrouping`、`vant-ui` 的 `Van*` 组件（用前先 grep 真实签名）。

**3. 编译验证（每批改完就跑）**
```javascript
GRADLE_USER_HOME=D:/Google/Android-gradle cmd //c "gradlew.bat :app:assembleProdDebug :app:assembleFakeDebug --console=plain" > /d/Code/live/temp/appbuildNN.log 2>&1
```
- 后台跑 + 落盘日志；`sleep` 后用 `grep -nE "^e: |BUILD SUCCESSFUL|BUILD FAILED" <log>` 看结果。
- `GRADLE_USER_HOME` 指你本机已缓存的 Gradle home（零下载），镜像已在 `settings.gradle.kts` 配好。

**4. 看错改错（迭代到绿）**
- 读 `e:` 行（Kotlin 编译器精确到文件:行:列）。
- 修完**只重编 prod+fake 两变体**确认无回归 → 停，不打包（你自己 Run）。

**5. 记录/交付**
- 每若干批改完，追加一条到 `APP/docs/07-变更核实清单.md`（改了什么、对应接口、编译是否过）。⚠ 路径已变更：原 `rebuild-app-Docs/07-变更核实清单.md` 目录**已不存在**（现为 `APP/docs/`）。
- 关键非显然事实存进 memory（如分包规范、JSON 列防御解析、AES 协议）。

## 关键约束
- **我从不自己 Run 或装手机**（无 Android 运行环境），只做「编译通过 + 两变体出 APK 在 `app/build/outputs/apk/…`」，**真机验证与打包由你做**。
- **不臆造接口**：拿不准的路由/字段就去 `api/` 核实（我因此删过几个我先前误加的假接口：`accountBalance/upsert`、`account DELETE`、非分期的 `recurring/enter`）。
- **web 不重构**（真相源，只读比对）；`vant-ui` 底座尽量不动（除非真机不可编译，如之前那个桌面-only `Path(String)`）。
- **2026-10-17 起新增约束**：APK 内**不得注入任何后端地址/分享域名**（BYO-backend，地址由用户在登录页「接入站点」输入）；release 包签名步骤见 `D:\Code\live\APP-RELEASE-SIGNING.md`（正式版：`Docs/docs/app/release-signing.md`），不要把口令写进入库的 `gradle.properties`。

> 流程本身可按需调整（例如只编单变体提速）；调整后同步更新本文件，不要留在临时对话里。