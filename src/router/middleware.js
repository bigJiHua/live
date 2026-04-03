/**
 * 全局路由守卫中间件
 * 用于验证用户登录状态和用户信息
 */

import router from "./index";
import { useUserStore } from "@/stores/user";
import { authApi } from "@/utils/api/auth";

/**
 * 获取用户信息（优先使用缓存，手动触发或刷新时获取最新数据）
 * @param {boolean} forceRefresh - 是否强制刷新
 */
export const getUserInfo = async (forceRefresh = false) => {
  const userStore = useUserStore();

  // 如果不是强制刷新且已有缓存，则跳过
  if (!forceRefresh && userStore.hasUserInfo) return;

  try {
    const res = await authApi.getUserinfo();
    // 使用后端返回的完整用户数据更新 store
    userStore.setUserInfo(res.data);
  } catch (err) {
    console.error("获取用户信息失败:", err);
  }
};

/**
 * 初始化全局路由守卫
 */
export const setupRouterGuard = () => {
  // 白名单：不需要登录的路由
  const whiteList = ["/login", "/register", "/429"]

  router.beforeEach(async (to, from, next) => {
    const userStore = useUserStore();

    // 判断是否有 token
    const hasToken = !!localStorage.getItem("finance_token");

    // 1. 没有 token，跳转到登录页
    if (!hasToken) {
      if (whiteList.includes(to.path)) {
        // 白名单路由直接放行
        next();
      } else {
        // 记录原本要去的页面，登录后可以跳回来
        next({ name: "Login", query: { redirect: to.fullPath } });
      }
      return;
    }

    // 2. 有 token，但没有用户信息，获取用户数据
    if (!userStore.hasUserInfo) {
      await getUserInfo();
    }

    // 3. 已登录用户访问登录页，跳转到首页
    if (to.path === "/login") {
      next({ name: "Home" });
      return;
    }

    // 4. 其他情况，正常放行
    next();
  });
};
