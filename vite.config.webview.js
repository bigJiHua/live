import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 每次构建生成一个时间戳，同时打进 html（内联脚本）和 card.js（define）。
 * 页面启动时由 main.js 回读两个值 —— 因为 WebView 的 file:// 缓存可能只换掉其中一份，
 * 真机出现过「包里是新的、屏幕上跑的是旧的」从而白查一轮（2026-10-11）。
 * 日志里 `html=NONE` ⇒ 跑的是旧 html；没有 `[page]` 这行 ⇒ 跑的是旧 card.js。
 */
const BUILD_STAMP = new Date().toISOString().replace('T', ' ').slice(0, 19)

/**
 * 离线 WebView 专用构建（`npm run build:webview`）。
 *
 * 与 `vite.config.js`（线上）的差异，每一条都对应一个「不改就白屏」的坑：
 *
 * 1. **`base: './'`**（相对路径）
 *    线上默认 `/`，产物 index.html 里是 `/assets/xxx.js`。离线从
 *    `file:///android_asset/card/index.html` 打开，绝对路径会去找
 *    `file:///android_asset/assets/xxx.js` —— 恰好能命中，但**换目录就崩**，
 *    而且 `/favicon.ico`、`/registerSW.js` 一样会 404 报噪。改成相对路径一劳永逸。
 *
 * 2. **不引 VitePWA**
 *    Service Worker 在 file:// 下不工作，且 `injectRegister: 'script'` 会往 index.html
 *    注入 `/registerSW.js` 的绝对路径脚本，404 报噪。离线用不上 PWA。
 *
 * 3. **`rollupOptions.input` 指向 `src/webview/main.js`**
 *    走专用入口：那里不引 router/pinia/request 层，只注册本页用到的 4 个 Vant 组件。
 *
 * 4. **`outDir: 'dist-webview'`**
 *    不覆盖线上 `dist`。
 *
 * 5. **CSS 全部内联（`assetsInlineLimit` 设大 + 单文件产物由文件名决定）**
 *    见下方 `build.rollupOptions.output.assetFileNames`：把 js/css 固定命名，
 *    避免每次构建 hash 变化导致 APP 侧 assets 难以增量同步。
 *
 * ⚠ 产物需**手动拷贝**到 `../APP/app/src/main/assets/card/`（沿用
 *   `assets/editor/` 的既有做法：仓库内直接提交静态产物，不在 gradle 里配拷贝任务）。
 */
export default defineConfig({
  base: './',
  plugins: [
    vue(),
    /**
     * 把产物 HTML 里的 `<script type="module" crossorigin>` 改成 `<script defer>`。
     *
     * ⚠⚠ 两段历史，改这里前必读：
     * 1. **为什么去掉 module**（2026-10-11）：`file:///android_asset/` 在 Chromium 里 origin 是
     *    `null`，module 脚本走 CORS 检查被**直接拒绝执行** ⇒ JS 永不运行。
     *    （富文本编辑器那页没事，是因为它用的是普通 `<script src>` 的 UMD 包。）
     * 2. **为什么必须补 defer**（同日第二轮真机抓到）：module 脚本**天然 defer**——放 head 也
     *    会等 DOM 解析完再执行。上面的替换去掉 `type="module"` 时若不补 `defer`，
     *    经典脚本在 head 里**同步执行**，此刻 `#app` 尚不存在 ⇒
     *    `app.mount(getElementById('app'))` 收到 null 直接抛 TypeError，
     *    `native.ready()` 永不执行 ⇒ 原生 loading + boot-hint 双常驻、后台零请求。
     *    （`define` 里 NODE_ENV=production 关掉了 Vue 开发期警告，故 console 无提示。）
     *    `defer` 对带 src 的经典脚本 = 「等 DOM 解析完、DOMContentLoaded 前按序执行」，语义与
     *    module 对 DOM 的时序保证一致，且不触发 CORS。
     */
    {
      name: 'offline-file-protocol-fix',
      transformIndexHtml: {
        order: 'post',
        handler(html) {
          return html
            .replace(/<script type="module" crossorigin /g, '<script defer ')
            .replace(/<script type="module" /g, '<script defer ')
            .replace(/<link rel="stylesheet" crossorigin /g, '<link rel="stylesheet" ')
            .replace(
              '</head>',
              `    <script>window.__CARD_PAGE_HTML_VER__='${BUILD_STAMP}'</script>\n  </head>`,
            )
        },
      },
    },
  ],
  // ⚠ 不复制 `public/`：里面有 logo.png(1.4MB) + icons/ + favicon.ico + manifest.json，
  //   离线页一个都用不到（图标全是 Vant 的 base64 内联 / CardOrgIcon 纯 CSS），
  //   跟进来会让 APK 白涨约 1.5MB。
  publicDir: false,
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  define: {
    // 离线页面里没有 process.env，挡掉 vant / vue-router 之类可能引用的 Node 常量。
    'process.env.NODE_ENV': '"production"',
    // ⚠⚠ 不要加 `__VUE_OPTIONS_API__: 'false'`（2026-10-12 教训）：这个常量会把 Vue 的
    //   选项 API 整体树摇掉，而 vuedraggable（排序拖拽）是纯选项 API 组件 ——
    //   computed/mounted 全被丢 ⇒ realList 恒 undefined（列表渲染 0 项）、
    //   mounted 不跑（Sortable 不初始化、拖不动）。表现就是「点排序列表不出来」。
    __VUE_PROD_DEVTOOLS__: 'false',
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false',
    // card.js 自己的构建戳（与 html 内联的那个比对，见 BUILD_STAMP 注释）
    __CARD_PAGE_VER__: JSON.stringify(BUILD_STAMP),
  },
  build: {
    outDir: 'dist-webview',
    emptyOutDir: true,
    target: 'es2019',
    // 关掉 sourcemap：SW4 -g 一份 map 对离线包没有意义，且会显著增大体积。
    sourcemap: false,
    cssCodeSplit: false,
    assetsInlineLimit: 1024 * 1024,
    rollupOptions: {
      input: resolve(__dirname, 'webview.html'),
      output: {
        // ⚠⚠ **必须是 iife，不能是 ES module**（2026-10-11 真机踩到）：
        //   vite 默认给离线页生成 `<script type="module" crossorigin src="./card.js">`，
        //   而 `file:///android_asset/` 在 Chromium 里 origin 是 `null`，
        //   ES module 会被 CORS 检查**直接拒绝执行** ⇒ JS 永不运行 ⇒
        //   `native.ready()` 不来 → 原生 loading 常驻 + 页面 boot-hint 常驻（两个 loading 叠加），
        //   且数据全靠桥注入 ⇒ 后台一个请求都没有。
        //   （富文本编辑器那页没事，是因为它用的是普通 `<script src>` 的 UMD 包。）
        //   iife + 单内联 chunk ⇒ 产物变成经典脚本，无 module、无 CORS。
        format: 'iife',
        inlineDynamicImports: true,
        entryFileNames: 'card.js',
        chunkFileNames: 'card-[name].js',
        assetFileNames: 'card.[ext]',
      },
    },
  },
})