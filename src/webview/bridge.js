/**
 * 离线 WebView 桥（仅供 `vite.config.webview.js` 的离线入口使用）。
 *
 * ## 与线上 web 的关系
 * 线上版走 `@/utils/request` 的 axios；离线版**一个网络请求都不发** ——
 * 对齐 APP 侧 `DiaryRichEditorWebView` 建立的既有范式（`DiaryRichEditorWebView.kt:51-55`
 * 「WebView 只负责渲染，数据出口在原生」）。所有数据由原生 `evaluateJavascript` 推给
 * `window.__cardWebview`，所有交互（跳转/排序/重新取数）通过 `AndroidCardBridge` 回传原生。
 *
 * 这样做同时绕开了离线化的三个大坑：请求鉴权（AES 握手 + httpOnly Cookie）、
 * CSP 只允许 https、以及 `baseURL` 相对路径在 file:// 下失效。
 *
 * ## 两个方向
 * - **Native → JS**：原生调 `window.__cardWebview.applyData(json)` / `.setTheme(json)` / `.setTab(t)`
 *   （必须有 ready 标记，见下）
 * - **JS → Native**：`window.AndroidCardBridge` 上的 `@JavascriptInterface` 方法
 */

/** 桥对象名（必须与 APP 侧 `addJavascriptInterface(bridge, "AndroidCardBridge")` 一致）。 */
const BRIDGE_NAME = 'AndroidCardBridge';

/** JS→原生 的方法集合。桥不存在时全部降级为 no-op（便于在浏览器里单独调试）。 */
function bridge() {
  return window[BRIDGE_NAME] || null;
}

export const native = {
  /** 页面（及主题）就绪：原生收到后立即推数据，并开始接受命令。 */
  ready() {
    bridge()?.ready();
  },
  /** 主动向原生要数据（初次 / 切 tab）。 */
  requestData(tab) {
    bridge()?.requestData(String(tab || ''));
  },
  /**
   * 下拉刷新：**强制**重新取数，不受 tab 切换的 10s 节流限制。
   * 与 requestData 分开而不是加参数，是为了兼容旧版桥（旧页面调用时静默无副作用）。
   */
  refresh(tab) {
    bridge()?.refresh(String(tab || ''));
  },
  /** 跳转（`/card/add`、`/card/edit?id=x`、`/finance/report/card-flow?cardId=x`）。 */
  navigate(path) {
    bridge()?.navigate(String(path || ''));
  },
  /** 拖拽排序提交：`[{ id, sort }]`，原生侧负责调 `PUT /card/sort`。 */
  saveSort(items) {
    try {
      bridge()?.saveSort(JSON.stringify(items || []));
    } catch (e) {
      reportError('saveSort serialize failed: ' + e.message);
    }
  },
  /** 出错上报（进 Logcat，便于真机定位）。 */
  reportError(msg) {
    try {
      bridge()?.onError(String(msg || ''));
    } catch (_) {
      /* 桥不存在时忽略 */
    }
  },
};

/** 全局错误捕获 → 原生（对齐编辑器 `index.html:72-75` 的做法）。 */
export function installGlobalErrorReporter() {
  window.onerror = function (msg, src, line, col) {
    native.reportError(`${msg} @${line}:${col}`);
  };
}

/** 便捷读取原生注入的 API 根地址（只用于把相对图片路径拼成绝对地址）。 */
export function fileBase() {
  return window.__CARD_WEBVIEW_FILE_BASE__ || '';
}