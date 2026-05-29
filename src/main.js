// src/main.js
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

// 1. 引入 Vant 和 Lazyload 插件（全量引入）
import Vant from 'vant';
import { Lazyload } from 'vant';
import 'vant/lib/index.css';

import './assets/css/style.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);
app.use(Vant);

// 4. 初始化路由守卫（需在 router 初始化之后）
import { setupRouterGuard } from "./router/middleware";
setupRouterGuard();

// 3. 注册 Lazyload 插件
app.use(Lazyload, {
  loading: 'https://fastly.jsdelivr.net/npm/@vant/assets/icon-demo.png',
  error: 'https://fastly.jsdelivr.net/npm/@vant/assets/icon-demo.png',
  lazyComponent: true,
});

/**
 * PWA 安装监听
 * 必须挂 window，不能放 Vue ref，否则热更新/路由切换会丢失事件对象
 */
window.__PWA_PROMPT__ = null
window.__PWA_INSTALLED__ =
  window.matchMedia?.('(display-mode: standalone)').matches ||
  window.navigator.standalone === true

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault()
  console.log('PWA 可安装')
  window.__PWA_PROMPT__ = e
})

window.addEventListener('appinstalled', () => {
  console.log('PWA 已安装')
  window.__PWA_PROMPT__ = null
  window.__PWA_INSTALLED__ = true
})

app.mount('#app');