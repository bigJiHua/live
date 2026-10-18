/**
 * 会话级缓存清理
 *
 * ## 背景（安全整改 P0-8，2026-10-08）
 * 旧版 `vite.config.js` 曾把 `/api/v1/` 的响应按 NetworkFirst 缓存进
 * Cache Storage（`cacheName: 'api-cache'`，24 小时 / 100 条）。
 * 那是**财务隐私数据**（流水、账单、余额），会明文落在浏览器磁盘上；
 * 而原登出与 401 流程都不清它 ⇒ 用户点了「退出登录」，数据仍留在本机可被读取。
 *
 * 该配置现已移除（API 一律走网络），但**已安装过旧版本的浏览器**里
 * 仍可能残留 `api-cache`，所以登出 / 401 时仍需主动清理一次。
 */

/** 判断某个 cache 是否为「API 数据缓存」（排除 Workbox 预缓存的静态资源） */
function isApiDataCache(cacheName) {
  return /api/i.test(cacheName) && !/precache/i.test(cacheName);
}

/**
 * 清除 API 响应缓存（登出 / 401 时调用）。
 * 静默失败：清理缓存失败不应阻断登出流程。
 * @returns {Promise<string[]>} 被删除的 cacheName 列表
 */
export async function clearAuthCache() {
  if (typeof caches === 'undefined' || !caches?.keys) return [];
  try {
    const names = await caches.keys();
    const targets = names.filter(isApiDataCache);
    await Promise.all(targets.map((name) => caches.delete(name)));
    if (targets.length) {
      console.info('[session] 已清理 API 缓存:', targets.join(', '));
    }
    return targets;
  } catch (err) {
    console.warn('[session] 清理 API 缓存失败（可忽略）:', err);
    return [];
  }
}

import { clearAuth } from '@/utils/authState';

/**
 * 统一清理客户端会话痕迹（安全整改 P1-17，2026-10-08）
 *
 * ## 为什么要有这个函数
 * 改造前各处登出只清一样东西（`finance_token`），遗漏了：
 *  - `sessionStorage` 里的 **AES 会话密钥**（`handshake.js:60`）—— 密钥留在本机可被读取
 *  - Cache Storage 里的 **API 响应**（P0-8 加的 `api-cache`）
 *  - 登录标记
 *  退出登录后这些都还留在浏览器里，属于会话残留。
 *
 * ## 注意
 * **httpOnly Cookie 无法被 JS 删除** —— 它由服务端在 `/auth/logout` 里清。
 * 所以调用方**必须先调后端 logout**，再调本函数清本地痕迹。
 */
export async function clearSession({ callServer = true } = {}) {
  // 1) 通知服务端清 Cookie（必须在最前，否则 Cookie 已被本地处理掉就验不出身份）
  if (callServer) {
    try {
      const { authApi } = await import('@/utils/api/auth');
      await authApi.logout();
    } catch (e) {
      // 网络失败也要继续清本地，否则用户会卡在"半登录"状态
      console.warn('[session] 调用后端登出失败，继续清理本地痕迹:', e?.message);
    }
  }

  // 2) 登录标记 +遗留 token（clearAuth 内部会顺带删 localStorage['finance_token']）
  clearAuth();

  // 3) 内存里的用户信息
  try {
    const { useUserStore } = await import('@/stores/user');
    const store = useUserStore();
    store.clearUserInfo();
    store.setPinState(false, false);
  } catch {
    /* store 未初始化时忽略 */
  }

  // 4) AES 会话密钥等敏感会话数据
  try {
    sessionStorage.clear();
  } catch (e) {
    console.warn('[session] 清理 sessionStorage 失败:', e?.message);
  }

  // 5) API 响应缓存（P0-8）
  await clearAuthCache();
}

export default { clearAuthCache, clearSession };