import { createRouter, createWebHistory } from "vue-router";
import { showToast } from "vant";
import RouterMap from "@/router/map";
import { ENV } from "@/utils/env";

const router = createRouter({
  history: createWebHistory("/"),
  routes: RouterMap,
});

const whiteList = {
  public: ["/login", "/register", "/429"],
};

const LOGIN_PATH = "/login";
const HOME_PATH = "/";

router.beforeEach((to, from) => {
  document.title = to.meta.title || "Golden Finance";

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
