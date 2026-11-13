# 聊天页组件与逻辑边界

## AI 个性化（2026-11-13）

入口为聊天设置中的 **AI 个性化**，独立路由 `/ai/personalization`。页面提供三类管理：

- **记忆**：查看来源与更新时间，自由新增或编辑，停用、删除，以及查看修改记录并恢复版本；也可在聊天中让 AI 保存、修改或忘掉记忆。
- **AI 对我的了解**：根据历史对话生成或增量更新画像，按背景、喜欢、不喜欢、长期目标、沟通偏好、协作习惯分类；AI 推断显示来源依据，用户可确认、修改或删除。
- **自定义规则**：最多 30 条明确要求，每条可编辑或停用，保存后随下一轮对话携带给 AI。使用记忆、使用画像和自动更新画像分别可关闭。本次个性化功能不新增权限配置。

`../Personalization.vue` 负责布局与用户动作；`composables/usePersonalization.js` 管理页面请求、草稿、冲突及结果；`src/utils/aiPersonalization.js` 校验 DTO、记忆回执身份并创建 API，生产适配器为 `src/utils/api/aiPersonalization.js`。后端接口为 `/ai/personalization`、`/ai/personalization/profile/generate`、`/ai/memories` 及文件读取、修改记录与版本恢复入口。

修改使用最新 `revision` 校验。409 冲突只重新读取服务器内容，保留用户输入，待用户核对后显式再次保存；失败不会自动重放写请求或画像生成。切换开关、画像生成或重新读取不会丢失未保存的规则草稿。操作完成后的列表刷新失败单独提示，避免把已经完成的删除或保存说成未执行。记忆读取、修改、删除和恢复回执须匹配请求文件，删除回执还须匹配请求版本。

2026-11-13 实机修复：画像生成 HTTP 等待为 180 秒，普通管理请求仍为 15 秒；后端依据模型配置设置画像调用时限并预留 10 秒处理余量，前端等待额外覆盖排队。前端请求等待不能替代后端时限或实际反代读取上限；生产部署可采用仓库画像端点的 180 秒代理示例。超时后已有画像和输入保留，不自动重发模型请求。

`composables/usePersonalizationBackGuard.js` 仅管理此页编辑与修改记录覆盖层的单一历史哨兵。保存中按返回保留面板与输入，空闲返回关闭覆盖层，手动关闭与卸载清理标记；离页时提示未保存输入。晚到请求结果不能重新回填已卸载页面。

验证包含 `tests/aiPersonalization.test.mjs`（DTO、CAS、禁重放、草稿保留、恢复与卸载）、`tests/aiPersonalizationUi.test.mjs`（实际 SFC 输入和按钮完成记忆、规则、画像操作）、`tests/aiPersonalizationBackGuard.test.mjs`（真实 history 返回行为），均加入 `npm run test:ai`。定向命令：

```sh
node --test tests/aiPersonalization.test.mjs tests/aiPersonalizationUi.test.mjs tests/aiPersonalizationBackGuard.test.mjs
```

个性化专属 14 项通过，与聊天 UI／生命周期联合验证共 57 项通过，前端构建通过；具体记录见 [前端复核摘要](../../../../../temp/api-logs-archive/personalization-frontend-review.md) 和 [2026-11-13 变更记录](../../../../../api/logs/CHANGELOG_2026-11-13.md)。这些结果不代表已运行全部 `test:ai` 套件。

2026-11-12（用户追加）：前端需要看到思考正文，新增 `reasoning_delta` 累计快照，在模型尚未完成时默认展开显示正在生成的思考文本。每帧绑定请求、轮次和单调 sequence；快照替换而非重复追加。临时内容标注尚未最终核验，不写 journal 或历史；终态 `reasoning` 核验文本替换对应轮次，成功封口清理未核验临时内容。中断前已经显示的过程可保留，标明已中断。向上阅读思考内滚区时停止自动跟随，手动展开／收起偏好保持。

2026-11-12：思考与结果显示分离。SSE `reasoning_status` 提前提供真实模型阶段、轮次和耗时，不携带推理正文；`reasoning` 详情继续只接收成功核验后的事件。思考区不再等结果到达才出现，跨轮累计耗时与是否观察到思考保持，停止或失败显示中断。

新回复使用 `useReplyReveal` 的独立展示缓冲，正文从上到下渐进展开，40ms 更新、最长6秒，可点击“立即显示全部”；历史回复、减少动态效果偏好直接完整显示。权威正文、复制、存档、回执与请求忙碌状态仍完整且及时。Mermaid和图表在必要内容完整后展示，避免未闭合代码块闪动。展示进度经 `revealProgress/revealComplete` 通知 viewport：底部跟随小步推进，向上滚动停止自动跟随；结束公告等展示完成后发布。

显示缓冲独立于网络请求，重复done/同步同文不重播，文本修正、重试、切会话、隐藏及离页清理计时；事件携带原请求身份，旧动画不能释放新请求的展示状态。相关测试包括 `aiReplyReveal`、`aiReplyViewport`、`aiThinkingLifecycle` 与真实 SFC UI。

2026-11-12（追加172纠偏，提示词 **v4.26**）：消息正文图表与业务 AiChart 卡片并存。
`AssistantMessage.vue` 的 messageSegments 提取 Markdown `mermaid` 围栏交给 `AiMermaid.vue`，
支持七种结构图及 `xychart(-beta)` / `pie` 数值图，正文数值图标注“AI 正文图表”。
`aiMermaid.js` 兼容裸 xychart、移除配置指令、lazy 加载 `mermaid@10.9.3`，使用 strict 配置；
SVG 经 `sanitizeMermaidSvg` 唯一出口消毒。未完成轮次、不支持图型及渲染错误保留原代码；
done 与历史重新挂载按相同管道渲染。业务 chart 继续走原 AiChart/ECharts 与真实数据绑定。
原“仅结构图、正文 xychart/pie 禁止”已被用户本轮要求覆盖；定向回归41/41，浏览器证据见
[当日变更记录](../../../../../api/logs/CHANGELOG_2026-11-12.md)。

`../Assistant.vue` 负责布局、展示组件接线和页面生命周期，业务逻辑由
`useAssistantController.js` 组装。2026-11-11 拆分后父页为 118 行，
保留此前三个展示组件，并增加十个职责独立的 composables；最大请求模块为 337 行。
这里记录的是维护边界，不代表已经测得运行性能提升。

## 目录与入口

| 文件/目录 | 职责 |
| --- | --- |
| components/ | 页面专属的消息、输入和历史展示组件 |
| useAssistantController.js | 唯一组装入口；创建共享状态、注入回调、初始化及释放 effectScope |
| assistantServices.js | 生产 API、纯工具、存储和 Vant 适配器；测试可注入替代服务 |
| composables/createAssistantState.js | 每页唯一共享状态图及全局准入 computed |
| composables/useAssistantProfile.js | 连接资料、模型选择和思考偏好 |
| composables/useAssistantImages.js | 附件、同意记忆、图片预览及内存释放 |
| composables/useAssistantActivities.js | 阶段/工具活动、核验后的思考、展开偏好和计时 |
| composables/useAssistantViewport.js | 滚动、发送位置聚焦、窄屏监听及回复公告 |
| composables/useAssistantArtifacts.js | 业务结果、多卡合并、金融候选及草稿兼容动作 |
| composables/useAssistantAuthorization.js | 会话授权、revision、有效期及授权后继续原轮次 |
| composables/useAssistantTurn.js | OCR/SSE、发送、停止、回执、轮询、重试和独立重问 |
| composables/useAssistantJournal.js | URL、文字草稿、轮次留档、历史恢复及回执补齐 |
| composables/useAssistantSync.js | 当前可见会话订阅、快照/实体合并及同步游标 |
| composables/useAssistantConversation.js | 能力加载、新建、历史列表、恢复、删除和设置重载 |

通用业务卡继续放在 `src/components/ai/`。现有 SSE、journal、消息合并与
`createConversationSync` 等工具继续复用 `src/utils/` 中的实现。

## 状态、依赖与生命周期

`createAssistantState` 只创建一份 `messages`、`conversationId`、输入、附件、授权和忙碌状态。
`transferBusy`、`busy`、`settingsUiBusy`、`allowed` 等全局闸门由同一状态图派生。
功能模块只接收所需的共享 refs，不各自复制会话、消息或忙碌状态；
模块内部可以拥有请求控制器、revision、同步游标、timer 等属于本职责的运行资源。

各功能模块使用统一签名 `useAssistantX({ state, services, runtime, actions })`，
并显式解构自己的 refs、服务与回调。`runtime.isActive()` 检查页面有效性，
`runtime.nextId()` 分配页面内消息身份；跨模块动作由 controller 注入，
功能模块不 import 同级功能模块，避免循环 import 和隐式共享闭包。

controller 在独立 `effectScope` 中创建状态和全部模块，然后安装 journal 与 sync 的 watch。
journal watch 必须在 `loadCapabilities` 修改会话身份之前安装，使 URL、旧草稿刷新、
新草稿恢复和授权刷新按原顺序发生。页面 mounted 时调用 controller.initialize。

离页时 controller.dispose 先使 runtime 失效，再调用资源所属模块的清理入口：
中止在途请求、停止回执轮询和同步订阅、清理授权/活动计时、关闭图片预览、
移除窄屏/visibility/pagehide 监听、停止 journal watch 并刷新文字草稿，最后停止 effectScope。
晚到的异步结果还须核验会话身份、revision、消息存在性或请求身份，不能重新覆盖当前对话。

## 展示与业务约束

| 组件 | 职责 | 与协调层交互 |
| --- | --- | --- |
| AssistantMessage.vue | 消息、图片、思考、论据、授权/错误状态及操作 | props 接收消息与准入结果；emit 重问、重试、回执、授权和活动展开 |
| AssistantComposer.vue | 输入、附件、同意提示、模型选择、停止/发送及回底 | v-model 更新编辑内容；emit 意图；暴露 resizeInput/clearFile |
| AssistantHistory.vue | 历史抽屉、标题搜索、日期分组、当前标记 | v-model 控制显隐；emit 新建、恢复和删除 |

展示组件不发 AI 请求，不修改会话身份或业务产物。请求 handler 必须再次核验准入，
不能只依赖按钮禁用。消息详情和论据展开留在消息组件；活动展开偏好记录在消息中，
重复封口不得覆盖用户选择。各组件维护自己的 scoped 样式，继承聊天页主题变量。

移动端断点为 `max-width: 600px`：Enter 换行，点击发送提交；桌面 Enter 发送，
Shift+Enter 换行，IME 组合期间不发送。流式期间可编辑下一轮输入，独立重问不消费草稿或附件。
未确认终态的请求只读取原 requestId 回执，不得自动重发 AI 请求。

2026-11-13 重启恢复：只读回执最多 30 次，每次最多等待 12 秒，查询间隔 3 秒，覆盖后端 45 秒进程租约及恢复时间。刷新恢复失败或服务 5xx 后也继续只读核对；自动查询结束保留手动「立即读取」入口。同步已确认进程中断时失效旧流所有权并释放发送锁。后端读取中断回执时按持久活动原话与原请求摘要补齐失败历史，之后「重试此问题」带 `retryOf` 在原会话继续一次；已提交结果直接取回，供应商被断开的生成流不能从进程内存恢复。

图片仅保存在内存，不写 journal。新建/恢复成功、设置重载、删除当前会话及离页释放图片；
创建/恢复失败、删除其他历史或重开当前会话保留当前图片。
旧金融候选兼容动作保留在 artifacts 模块，本次没有新增对应 UI 入口。

## 验证与遗留验收

验证命令为 `npm run test:ai`；本轮不运行 build。
`tests/aiAgent.test.mjs` 已从父页脚本抽取迁为真实 controller 导入和显式服务注入，
原有 62 项行为断言保留，当前 aiAgent 75/75、真实 SFC UI 16/16、生命周期 7/7；完整套件 217/217 全通过。
此前 A/B/C 交付的完整套件基线为 192/192，不能将历史结果当成本轮最终复测。
`tests/aiAssistantUi.test.mjs` 挂载实际 SFC，验证输入、组件事件、Markdown 与历史展示。

NVDA 实际发声、物理手机软键盘与中文候选确认仍需人工验收；
C4 历史列表最后一句摘要依赖后端列表接口扩展，单列后续任务。
本轮计划和验证记录见 [模块化执行计划](../../../../../api/logs/PLAN_AI_CHAT_LOGIC_MODULARIZATION.md)
与 [执行日记](../../../../../api/logs/CHANGELOG_2026-11-11_AI_CHAT_LOGIC_MODULARIZATION.md)。

本轮同时收口两个异步边界：手动回执与本地草稿结果晚到时核对当前会话和消息身份；
设置重载与远端删除通过 invalidateRequests 失效旧请求。请求忙状态与聚焦由所属 run 释放，
旧文字/OCR transport 即使忽略 abort 并迟到返回，也不会清掉新请求状态。
