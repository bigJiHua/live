/**
 * 登录态管理（安全整改 P0-5 / P1-16 / P1-17，2026-10-08）
 *
 * ## 为什么需要这个模块
 * 改造前：web 把 JWT 存在 `localStorage['finance_token']`，每个请求由 JS 读出来注入
 * `Authorization: Bearer`。⇒ 任意 XSS 都能 `localStorage.getItem('finance_token')`
 * 把令牌读走并外传，等于**会话完全可被窃取**。
 *
 * 改造后：JWT 由服务端下发给 **httpOnly Cookie**，浏览器自动携带、**JS 读不到**。
 * 于是「JS 如何知道当前是否已登录」就成了新问题 —— 这正是本模块要解决的。
 *
 * ## 方案：非敏感标记 + 服务端探测
 * | 存储 | 内容 | 为什么安全 |
 * |---|---|---|
 * | httpOnly Cookie（浏览器管理） | 真实 JWT | JS 读不到，XSS 窃取无效 |
 * | `sessionStorage['finance_auth']` | 仅 `{ ok: 1, at: 时间戳 }` | **不含任何凭证**，泄露了也无法冒用 |
 *
 * 判定登录态的依据是「标记存在」+「必要时用 `/auth/me` 向服务端确认」，
 * 而**不是**「JS 能读到 token」。
 *
 * ## 生命周期
 * - 登录成功 → `markAuthed()`
 * - 登出 / 401 → `clearAuth()`
 * - **页面刷新**：sessionStorage 保留 ⇒ 直接放行，无需请求
 * - **关闭标签页再打开**：标记丢失 ⇒ 首次调用 `ensureSession()` 时用 `/auth/me` 探测，
 *   Cookie 仍有效（默认 7 天）就能恢复标记，用户**无需重新登录**
 *
 * ## 与服务端的关系
 * 服务端 `authGuard` 是**双通道**：Cookie（web）或 Bearer（APP / webview-APP）。
 * 本模块只管 web 侧；原生端不读 localStorage，不受影响。
 */

const MARK_KEY = 'finance_auth';

/**
 * 🔇 静默探测 /auth/me（2026-10-12 冲突修复②）
 *
 * ## 为什么必须用原生 fetch、不走主 request 实例
 * 主实例（request/core.js）的响应拦截器把业务 401 一律当作「登录失效」
 * 执行 forceLogin（toast + 清会话 + 整页跳转）。
 * 而本函数的 401 是**正常的探测答案**（未登录 / Cookie 已过期），
 * 不该引发任何跳转 —— 否则「未登录访问受保护页」时：
 * 探测 401 → forceLogin 硬跳 /login 与路由守卫的软导航 replace("/login?redirect=x")
 * 竞争，URL 在两者之间变化 ⇒ 页面反复整页刷新（401 风暴的根源）。
 *
 * ## 为什么用原生 fetch 而不是 axios
 * 1. 本模块保持**零依赖**（本文件的设计约定）：冒烟测试用原生 Node ESM
 *    直接加载本文件，任何 npm 包/相对路径导入都会让测试解析失败。
 * 2. fetch 的失败天然不进 axios 拦截器：401 / 网络错误都只是安静的
 *    `false`，由调用方（路由守卫）决定去留。
 *
 * 路径写死 `/api/v1`：与 `request/config.js` 的 baseURL 一致（该文件同样
 * 是写死的相对路径，走 vite 代理 / 生产同源，两处需同步改）。
 *
 * @returns {Promise<object|null>} 后端响应体；失败返回 null
 */
export async function probeMe() {
  try {
    // 安全请求头三件套（🔄 P1-16 修复补漏）：securityCheck 要求
    //   x-requested-with=XMLHttpRequest，且 .env REQUIRE_CLIENT_NONCE=true 时缺失 nonce 直接 400。
    //   原实现只带 Accept ⇒ 探测恒被 400 ⇒ ensureSession 永远 false，
    //   「关标签页重开后凭 Cookie 恢复登录态」完全失效（见 api.md §8.2 实测）。
    //   本文件约定零依赖（可被 Node ESM 直接加载做冒烟测试），故内联生成，
    //   不 import securityHeaders.js。
    const nonce =
      typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
        ? crypto.randomUUID().replace(/-/g, "")
        : `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 14)}`;
    // AbortSignal.timeout 旧浏览器不支持 ⇒ 条件降级为无超时（fetch 自身受浏览器超时管控）
    const timeoutOption =
      typeof AbortSignal !== "undefined" && AbortSignal.timeout
        ? { signal: AbortSignal.timeout(15000) }
        : {};
    const res = await fetch('/api/v1/auth/me', {
      credentials: 'include', // 同源默认也带 Cookie，显式写出语义
      headers: {
        Accept: 'application/json',
        'X-Requested-With': 'XMLHttpRequest',
        'x-client-timestamp': String(Date.now()),
        'x-client-nonce': nonce,
      },
      ...timeoutOption,
    });
    return (await res.json().catch(() => null)) || null;
  } catch {
    return null;
  }
}

/** 读取标记（不含凭证，仅状态） */
function readMark() {
  try {
    const raw = sessionStorage.getItem(MARK_KEY);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    return obj && obj.ok ? obj : null;
  } catch {
    return null;
  }
}

/**
 * 标记为已登录。
 * @param {object} [info] 可选的用户信息，仅用于首屏避免闪烁（不含 token）
 */
export function markAuthed(info) {
  try {
    sessionStorage.setItem(MARK_KEY, JSON.stringify({
      ok: 1,
      at: Date.now(),
      // 只存展示用的非敏感字段
      username: info?.username || '',
    }));
  } catch (e) {
    //隐私模式/存储被禁用时降级：标记写不进去也不影响 Cookie 鉴权
    console.warn('[authState] 无法写入登录标记（不影响鉴权）:', e?.message);
  }
}

/** 清除登录标记（登出 / 401） */
export function clearAuth() {
  try {
    sessionStorage.removeItem(MARK_KEY);
  } catch {
    /* ignore */
  }
  // ⚠ 兼容清理：改造前的旧版本把 token 存在这里，若还在必须删掉，
  //    否则「登出后 token 仍留在磁盘」的老问题会一直存在。
  try {
    localStorage.removeItem('finance_token');
  } catch {
    /* ignore */
  }
}

/** 是否有登录标记（同步判断，供路由守卫用） */
export function hasAuthMark() {
  return !!readMark();
}

/**
 * 确保登录态有效：标记丢失时用 `/auth/me` 探测一次。
 * 之所以需要：关闭标签页后 sessionStorage 会清空，但 httpOnly Cookie 还在。
 *
 * 🔄 2026-10-12 修复②：fetchMe 缺省改用本模块的 probeMe（裸 axios 静默探测）。
 * 此前调用方注入的是主 request 实例的 authApi.getUserinfo —— 401 会被
 * 响应拦截器当成「登录失效」触发 forceLogin 整页跳转，与路由守卫的软导航
 * 竞争导致疯狂刷新。现在不传 fetchMe 就走静默通道；仍保留注入口以备特殊场景。
 *
 * @param {object} [opts]
 * @param {() => Promise<any>} [opts.fetchMe] 自定义探测方法（缺省 probeMe）
 * @returns {Promise<boolean>} 是否已登录
 */
export async function ensureSession({ fetchMe } = {}) {
  if (hasAuthMark()) return true;
  try {
    const me = typeof fetchMe === 'function' ? await fetchMe() : await probeMe();
    if (me && (me.status === 200 || me.status === 202) && me.data) {
      markAuthed(me.data);
      return true;
    }
    return false;
  } catch {
    // 网络错误等 ⇒ 保守视为未登录，交给路由守卫跳登录页
    return false;
  }
}

export const AUTH_MARK_KEY = MARK_KEY;

/**
 * 读取**遗留**的 localStorage token（安全整改 P0-5 之后的兼容兜底）。
 *
 * ## 为什么还需要它
 * 改造后 web 正常路径**不应该**再有 localStorage token（真实凭证在 httpOnly Cookie）。
 * 但仍保留这个读取，原因有两个：
 * 1. **平滑升级**：用户浏览器里可能还留着旧版本写入的 token，
 *    升级后的第一次请求若不带上，会直接 401把人踢回登录页。
 * 2. **降级兜底**：若某些部署没能下发 Cookie（如反代没透传 Set-Cookie），
 *    仍能用旧 token 工作，不至于全站瘫痪。
 *
 * ⚠ 它**不是**安全兜底而是可用性兜底：一旦 XSS 能执行，它依然可被读取
 *   ——所以每次登录/登出都会顺手清理它（见 clearAuth），把暴露窗口压到最小。
 *
 * @returns {string|null}
 */
export function getLegacyToken() {
  try {
    return localStorage.getItem('finance_token') || null;
  } catch {
    return null;
  }
}

export default {
  markAuthed,
  clearAuth,
  hasAuthMark,
  ensureSession,
  getLegacyToken,
  AUTH_MARK_KEY,
};