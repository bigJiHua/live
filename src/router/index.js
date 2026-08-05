import { createRouter, createWebHistory } from "vue-router";
import { showToast } from "vant";
import RouterMap from "@/router/map";
import { ENV } from "@/utils/env";

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
  public: ["/login", "/register", "/429", "/share/diary/detail", "/demo/ui", "/demo/keyboard"],
};

const LOGIN_PATH = "/login";
const HOME_PATH = "/";

router.beforeEach((to, from) => {
  document.title = to.meta.title || "Golden Finance";

  // 🚨 Demo 模式开关：非 demo 环境下，所有 /demo 开头的页面一律不展示，
  // 重定向回首页（置于白名单判断之前，避免被白名单放行）。
  const isDemo = import.meta.env.VITE_APP_DEMO === "true";
  if (!isDemo && to.path.startsWith("/demo")) {
    return { path: HOME_PATH, replace: true };
  }

  const token = localStorage.getItem("finance_token");

  // 🚀 白名单直接放行
  if (
    whiteList.public.includes(to.path) ||
    whiteList.public.includes(to.name)
  ) {
    return true;
  }

  // 🚨 没 token：强制踢去登录（移动端最稳）
  if (!token) {
    // 防止重复 toast
    if (from.name !== "Login") {
      showToast({
        message: "请先登录",
        position: "top",
      });
    }

    // ❗关键：避免死循环
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

  // 🚨 已登录却访问登录页 → 直接踢回首页
  if (to.path === LOGIN_PATH) {
    return {
      path: HOME_PATH,
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
