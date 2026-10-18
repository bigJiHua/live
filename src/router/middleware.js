/**
 * 登录态恢复（安全整改 P1-16，2026-10-08）
 *
 * ## 为什么不再做路由拦截
 * 改造前这里注册了**第二个** `router.beforeEach`，与 `router/index.js` 的守卫
 * 重复拦截，且两处白名单不一致（`/demo/*` 只在 index.js 里）⇒ 同一路由在两个守卫中
 * 判定可能相反，且会触发两次 `getUserInfo`。现已把**路由拦截统一收敛到 `index.js`**，
 * 本模块只负责「恢复登录态 + 加载用户信息」，职责单一。
 *
 * ## 现在做什么
 * httpOnly Cookie 里的 JWT，JS 读不到；`sessionStorage` 的登录标记在
 * **关闭标签页后**会丢失，而 Cookie 仍然有效（默认 7 天）。
 * 本模块在应用启动时探测一次 `/auth/me`：Cookie 有效就恢复标记，
 * 用户**无需重新登录**。
 */

import { useUserStore } from "@/stores/user";
import { authApi } from "@/utils/api/auth";
import { hasAuthMark, ensureSession, probeMe } from "@/utils/authState";

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
 * 应用启动时恢复登录态。
 *
 * 场景：用户关掉标签页后 sessionStorage 被清空，但 httpOnly Cookie 还有效。
 * 这里探测一次，避免用户被无端踢回登录页。
 *
 * @returns {Promise<boolean>} 是否已恢复出登录态
 */
export const restoreSession = async () => {
  // 快路径：标记还在（同一标签页刷新）
  if (hasAuthMark()) return true;

  // 慢路径：靠 httpOnly Cookie 静默探测（🔄 修复②：probeMe 裸 axios 绕开拦截器，
  // 未登录的 401 不再触发 forceLogin 整页跳转；探测成功顺带填 store 免首屏再请求）
  const ok = await ensureSession({
    fetchMe: async () => {
      const me = await probeMe();
      if (me?.status === 200 && me?.data) {
        useUserStore().setUserInfo(me.data);
      }
      return me;
    },
  });

  if (ok) {
    console.info("[auth] 已通过 Cookie 恢复登录态");
  }
  return ok;
};

/**
 * 初始化（保持原有导出名，避免 main.js 改动）
 * ⚠ 不再注册路由守卫 —— 拦截已统一到 `router/index.js`。
 */
export const setupRouterGuard = () => {
  restoreSession().catch((e) =>
    console.warn("[auth] 恢复登录态失败（将按未登录处理）:", e?.message)
  );
};