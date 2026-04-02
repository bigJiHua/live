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

// 2. 初始化主题
import { initTheme } from './utils/theme';
initTheme();

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

app.mount('#app');