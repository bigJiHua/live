/**
 * 离线 WebView 专用入口（仅由 `vite.config.webview.js` 使用，线上 web 版完全不受影响）。
 *
 * ## 与 `src/main.js`（线上入口）的差异，都是离线化必须做的减法
 * - **不引 vue-router**：`file://` 下 `createWebHistory` 必然匹配不到路由而白屏；
 *   离线页没有页面跳转需求，跳转一律通过桥交给原生。
 * - **不引 pinia / stores**：这两个页面不依赖全局 store。
 * - **不引 Lazyload**：其默认图/错误图写死在 `fastly.jsdelivr.net`，离线不可达。
 * - **不引 `initAntiHijack`**：它会禁右键/拦 F12/轮询检测 DevTools，干扰 adb 调试。
 * - **Vant 按需注册**：只注册本页用到的 4 个（Icon/Empty/Overlay/Loading），
 *   线上是 `app.use(Vant)` 全量，离线不需要那部分体积。
 * - **不引 request 层**：数据全部由原生注入（见 `bridge.js`）。
 *
 * ## 原生 → JS 的调用入口
 * 原生在页面就绪后才 `evaluateJavascript`，而这里在 `mount()` 之后才挂
 * `window.__cardWebview`，因此不存在「原生先注入、Vue 还没挂载」的时序问题。
 */
import { createApp } from 'vue'
import { Icon, Empty, Overlay, Loading } from 'vant'
import 'vant/lib/index.css'
import '@/assets/css/style.css'

import CardOffline from './CardOffline.vue'
import { native, installGlobalErrorReporter } from './bridge'

installGlobalErrorReporter()

// 自证身份：html 内联戳与 card.js 构建戳都在，且相等，才说明屏幕上跑的是包里那份新页。
// WebView 的 file:// 缓存可能只换掉其中一份（2026-10-11 就被这个坑过一轮）。
console.log(
  '[page] html=' + (window.__CARD_PAGE_HTML_VER__ || 'NONE(旧html)') +
    ' js=' + (typeof __CARD_PAGE_VER__ !== 'undefined' ? __CARD_PAGE_VER__ : 'NONE(旧js)') +
    ' ua=' + navigator.userAgent.slice(-46)
)

const app = createApp(CardOffline)
app.use(Icon)
app.use(Empty)
app.use(Overlay)
app.use(Loading)

app.mount(document.getElementById('app'))

// 挂载完成即通知原生：原生收到后立刻推数据。
// ⚠ `window.__cardWebview` 由 CardOffline.vue 自己在 onMounted 里注册（组件持有自己的方法，
//   不依赖外部反查实例）；此处只负责发 ready，顺序上 onMounted 已先执行。
// 时序对齐编辑器 assets/editor/index.html:98-103 的 onCreated → onReady。
native.ready()