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

// 自写主题化基础组件（深度定制，纯 token 驱动，替代 Vant 原生组件）
import AppButton from '@/components/base/AppButton.vue'
import AppTag from '@/components/base/AppTag.vue'
import AppCell from '@/components/base/AppCell.vue'
import AppForm from '@/components/base/AppForm.vue'
import AppField from '@/components/base/AppField.vue'
import AppPopup from '@/components/base/AppPopup.vue'
import AppDialog from '@/components/base/AppDialog.vue';
import AppGrid from '@/components/base/AppGrid.vue'
import AppGridItem from '@/components/base/AppGridItem.vue';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);
app.use(Vant);

// 全局注册自写主题化基础组件（<app-*> / <App*> 均可）
app.component('AppButton', AppButton);
app.component('AppTag', AppTag);
app.component('AppCell', AppCell);
app.component('AppForm', AppForm);
app.component('AppField', AppField);
app.component('AppPopup', AppPopup);
app.component('AppDialog', AppDialog);
app.component('AppGrid', AppGrid);
app.component('AppGridItem', AppGridItem);

// 4. 初始化路由守卫（需在 router 初始化之后）
import { setupRouterGuard } from "./router/middleware";
setupRouterGuard();

// 5. 反调试/防篡改防护（仅生产生效：禁右键、禁 DevTools、防 hook 自检）
import { initAntiHijack } from "./utils/security/antiHijack";
initAntiHijack();

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