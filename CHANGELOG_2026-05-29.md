# 变更日志

## 2026-05-29

### 🆕 新增

| 页面/功能 | 路由 | 说明 |
|-----------|------|------|
| 账单流水明细 | `/card/bill/ledger` | 按账单周期展示该卡消费流水明细，支持与账单金额比对校验 |
| 明细跳转按钮 | 账单列表卡片 | 每个卡片尾号右侧新增【明细】按钮，直接跳转至流水明细页 |

### 🔧 修改

| 页面/文件 | 变更内容 |
|-----------|----------|
| `src/views/BankCard/bill/List.vue` | 卡片尾号右侧新增【明细】按钮，点击跳转 `/card/bill/ledger?id=xxx` |
| `src/views/BankCard/bill/Detail.vue` | 操作区新增"查看流水明细"按钮，跳转至流水明细页 |
| `src/views/BankCard/bill/Ledger.vue` | 移除顶部导航栏（由 layout 提供），移除全部/支出/收入筛选 tabs |
| `src/views/Finance/report/flow/CardFlow.vue` | 卡片选择排除信用卡（仅展示借记卡）；支持 `?cardId=` 查询参数自动选中卡片 |
| `src/views/Finance/account/Structure.vue` | 银行卡列表每项添加 `>` 箭头，点击跳转至 `/finance/report/card-flow?cardId=xxx` |
| `src/router/map.js` | 新增 `/card/bill/ledger` 路由 |

### 📊 统计

- 新增 1 个页面组件、1 条路由
- 修改 5 个页面/组件文件

---

## 2026-05-29（晚间 — 日记功能 WYSIWYG 升级）

### 🆕 新增

| 功能 | 说明 |
|------|------|
| 精准模式富文本编辑 | 日记发布页新增**快速/精准**双模式，精准模式集成 WangEditor v5 富文本编辑器 |
| 工具栏「更多」下拉 | 精准模式工具栏右侧「更多▼」按钮，点击弹出 7 个二级工具（标题/引用/代码块/分割线/待办/撤销/重做） |
| HTML 内容提取 | 精准模式发布时自动从 HTML 中提取 `<img>` URL 写入 `images` 字段，保证后端图片关联不丢失 |

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `web/package.json` | 新增依赖 `@wangeditor/editor@^5.1.23` + `@wangeditor/editor-for-vue@^5.1.12` |
| `web/src/views/Diary/Add.vue` | **重写**：NavBar 标题区增加快速/精准胶囊切换；精准模式集成 WangEditor（加粗/斜体/下划线/删除线/字号/行高/列表/清格式/表情 + 更多▼）；图片插入改为 `dangerouslyInsertHtml` 并校验 URL；快速↔精准模式互相迁移内容（防 XSS 转义）；防重复提交守卫；图片并发上传；多图缓存；模式分离 MAX_SELECT（9/20）；`100vh` → `100dvh`；`:has()` → Vue 动态 class；iOS 滚动优化；编辑器高度 inline style 锁死 400px；placeholder/段落/slate-editor 边距对齐修复；容器 `flex-shrink: 0` 去空白 |
| `web/src/views/Diary/Detail.vue` | 追文列表 `{{ }}` → `v-html` 渲染精准模式富文本；新增 `.append-content :deep(img/p/blockquote)` 样式；`100vh` → `100dvh` |
| `web/src/views/Diary/index.vue` | `100vh` → `100dvh` 防止移动端地址栏抖动 |
| `api/src/modules/moment/controller/index.js` | `list` 接口截取 20 字前先 `replace(/<[^>]*>/g, '')` 去 HTML 标签 |

### 🔧 修复的关键 Bug

| Bug | 修复方式 |
|------|----------|
| `editor.insertImg is not a function` | 改为 `editor.dangerouslyInsertHtml()`（WangEditor v5 无此 API） |
| `isEmptyHtml` 正则漏掉 `<br>` | 改用 DOM `createElement('div')` + `textContent` 判空 |
| 编辑区域高度 < 300px 警告 | inline style `height:400px` + CSS `height`（非 `min-height`）让子元素 `height:100%` 可继承 |
| 光标与 placeholder 文字错位 | placeholder/scroll/p/slate-editor 三处 `line-height:1.6` + `margin/padding:0` 严格对齐 |
| `v-show` 隐藏时初始化工具栏宽度为 0 | 改为 `v-if` 按需创建/销毁 |
| 精准模式空内容 `<p><br></p>` 存入数据库 | 发布前 `isEmptyHtml` 归一化为空字符串 |
| 精准模式图片关联信息丢失 | `extractImagesFromHtml()` 提取 URL 写入 `images` 字段 |
| 切换模式内容丢失 | 精准↔快速双向迁移，快速→精准做 `escapeHtml` 防 XSS |
| 图片上传预览瞬间消失 | `uploadQueue` 延迟 300ms 清空 |
| `:has()` 在部分安卓 WebView 不兼容 | 改为 Vue 动态 class |
| 工具栏 `fontSize`/`emotion`/`lineHeight` 在 dropdown 内面板冲突 | 移至主工具栏，dropdown 只保留简单 toggle 工具 |
| 移动端 `100vh` 地址栏变化抖动 | 三文件全部改为 `100dvh` |
| 精准模式编辑器容器 `flex:1` 撑出大段空白 | 改为 `flex-shrink:0` |

### 📊 统计

- 修改 5 个文件
- 新增 2 个 npm 依赖
- Add.vue 从简单 textarea 重构为双模式富文本编辑器

---

## 2026-05-29（深夜 — 定位模块重构）

### 🏗️ 架构变更

| 变更 | 说明 |
|------|------|
| **前端 Key 清退** | `VITE_IPINFO_TOKEN` / `VITE_AMAP_KEY` 从 `web/.env` 移除，全部收口到后端 `api/.env` |
| **后端代理层** | 新建 `api/src/modules/geo/` 模块，提供 `/geo/network`、`/geo/ip`、`/geo/regeo` 三个代理端点 |
| **定位模块拆分** | `utils/geolocation.js` (271行) → `utils/geo/` 目录（permission / browser / amap / ip / index，5文件） |
| **IP 定位走后端** | 原前端直调 `api.ip.sb`/`ipinfo.io` → 全部通过后端代理，无 CORS/Key 泄露风险 |

### 🆕 新增

| 文件 | 说明 |
|------|------|
| `web/src/utils/geo/index.js` | 定位模块统一入口，输出 `getBrowserLocation` / `getAmapLocation` / `getIpLocation` / `getFullLocation` |
| `web/src/utils/geo/permission.js` | GPS 权限检测 + `safeRequestGps()`（先查 `permissions.query`，denied 不调 GPS，避免 Chrome 自动拉黑） |
| `web/src/utils/geo/browser.js` | 精确定位：纯浏览器 GPS，0 后端请求 |
| `web/src/utils/geo/amap.js` | 高德定位：GPS → 后端 `/geo/regeo`（高德逆地理）→ 省市区格式 |
| `web/src/utils/geo/ip.js` | 大致位置：后端 `/geo/ip` 代理（Amap IP → ip.sb → ipinfo.io 逐级回退），不依赖 GPS |
| `web/src/utils/securityHeaders.js` | 安全请求头工具（`X-Requested-With` + `x-client-timestamp`），供裸 axios 调用绕过 `securityCheck` |
| `web/src/utils/request/client.js` | 客户端上下文新增 `loginLat` / `loginLng`（静默 GPS 采集） |
| `api/src/modules/geo/api/index.js` | 地理位置代理路由：`/network`（无需鉴权）、`/ip` + `/regeo`（需 authGuard） |
| `api/src/modules/geo/controller/index.js` | 代理控制器：ip.sb→ipinfo 回退、高德 IP/逆地理、内网 IP 过滤、矩形→中心点计算、省市区拼接 |

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `web/src/utils/network.js` | **重写**：静默 GPS 优先（一次即停、denied 记忆）+ 后端 `/geo/network` 代理回退 |
| `web/src/utils/fileRequest.js` | 客户端上下文新增 `login_lat` / `login_lng` |
| `web/src/utils/env.js` | 移除 `IPINFO_TOKEN` / `AMAP_KEY` 导出 |
| `web/src/views/Diary/Add.vue` | 位置选择器新增"高德定位"第三选项；`getFullLocation`/`getLocationOptions` → 逐一定位函数 |
| `web/.env` | 移除 `VITE_IPINFO_TOKEN` / `VITE_AMAP_KEY` |
| `web/.env.example` | 同上 |
| `api/.env` | 新增 `AMAP_KEY` / `IPINFO_TOKEN` |
| `api/.env.example` | 同上 |
| `api/src/api/index.js` | 注册 `/geo` 路由 |
| `api/src/modules/auth/controller/index.js` | `getCurrentUser` 邮箱脱敏：`ab****cd@xxx.com`（≤3字符不脱敏） |
| `api/package.json` | 新增依赖 `axios` |

### ❌ 删除

| 文件 | 原因 |
|------|------|
| `web/src/utils/geolocation.js` | 拆分为 `geo/` 模块目录 |

### 📊 统计

- 新增 6 个前端文件 + 2 个后端模块文件
- 修改 10 个文件
- 删除 1 个文件
- 新增 1 个 npm 依赖（backend axios）
- 定位策略：GPS 单次调用、IP 永远兜底、IP/GPS 互不绑架
