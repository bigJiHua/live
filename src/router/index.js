import { createRouter, createWebHistory } from "vue-router";
import { showToast } from '@/utils/toast';
import RouterMap from "@/router/map";
import { ENV } from "@/utils/env";
// 安全整改 P0-5/P1-16：登录态判定（httpOnly Cookie 时代，JS 读不到 token）
import { hasAuthMark, ensureSession } from "@/utils/authState";

const router = createRouter({
  history: createWebHistory("/"),
  routes: RouterMap,
  // 返回上一页时恢复 window 滚动位置（列表类页面滚动记忆，替代已取消的 keep-alive）。
  // savedPosition 仅在浏览器前进/后退（pop）导航时由 vue-router 提供；
  // 用 rAF 轮询等待异步数据渲染出足够高度，避免滚动被内容不足截断。
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return new Promise((resolve) => {
        let tries = 0;
        const tryScroll = () => {
          if (document.documentElement.scrollHeight >= savedPosition.top || tries > 60) {
            resolve(savedPosition);
          } else {
            tries++;
            requestAnimationFrame(tryScroll);
          }
        };
        tryScroll();
      });
    }
    return { top: 0 };
  },
});

const whiteList = {
  // ✅ P1-16：合并了两个守卫的白名单（原先 `index.js` 有 `/demo/*` 而 `middleware.js` 没有，
  //    导致同一路由在两个守卫里判定不一致）。现以本表为唯一来源。
  //    `middleware.js` 已改为只做「登录态恢复」，不再重复拦截。
  public: ["/login", "/register", "/429", "/share/diary/detail", "/demo/ui", "/demo/keyboard"],
};

const LOGIN_PATH = "/login";
const HOME_PATH = "/";

router.beforeEach(async (to, from) => {
  document.title = to.meta.title || "Golden Finance";

  // 🚨 Demo 模式开关：非 demo 环境下，所有 /demo 开头的页面一律不展示，
  // 重定向回首页（置于白名单判断之前，避免被白名单放行）。
  const isDemo = import.meta.env.VITE_APP_DEMO === "true";
  if (!isDemo && to.path.startsWith("/demo")) {
    return { path: HOME_PATH, replace: true };
  }

  // 🚀 白名单直接放行（不需要登录）
  // 🔄 修复④（2026-10-12）：「已登录访问登录页 → 踢回首页」原先写在守卫末尾，
  // 但 /login 在白名单里此处早已 return true ⇒ 永不可达的死代码。上移到放行前。
  if (
    whiteList.public.includes(to.path) ||
    whiteList.public.includes(to.name)
  ) {
    // 已登录（同标签页标记仍在）却访问登录页 ⇒ 直接回首页
    if (to.path === LOGIN_PATH && hasAuthMark()) {
      return { path: HOME_PATH, replace: true };
    }
    return true;
  }

  // 🔒 安全整改 P0-5/P1-16：登录态判定不再读 localStorage 的 JWT。
  //   httpOnly Cookie 里的token JS 读不到，所以用「非敏感标记 + /auth/me 探测」。
  //   hasAuthMark() 快路径覆盖「页面刷新」（sessionStorage 仍在）；
  //   关掉标签页再打开时标记会丢，这里用 Cookie 探测恢复，用户无需重新登录。
  let authed = hasAuthMark();
  if (!authed) {
    // 🔄 修复②（2026-10-12）：不传 fetchMe ⇒ 走 authState 内部的静默探测
    // （裸 axios，绕开响应拦截器）。未登录的 401 是正常探测答案，
    // 不能再经主实例触发 forceLogin 整页跳转与守卫软导航竞争。
    authed = await ensureSession();
  }

  // 🚨 未登录：强制踢去登录
  if (!authed) {
    // 防止重复 toast
    if (from.name !== "Login") {
      showToast({
        message: "请先登录",
        position: "top",
      });
    }

    // ❗ 关键：避免死循环
    if (to.path === LOGIN_PATH) {
      return true;
    }

    // 👉 带 redirect 回跳
    return {
      path: LOGIN_PATH,
      query: {
        redirect: to.fullPath,
      },
      replace: true,
    };
  }

  // 🚨 路由不存在
  if (to.matched.length === 0) {
    showToast({
      message: "页面不存在，已返回首页",
      position: "top",
    });

    return {
      path: HOME_PATH,
      replace: true,
    };
  }

  return true;
});

export default router;
