/**
 * 安全请求头工具
 * 用于通过 securityCheck 中间件的最小头集合
 *
 * 注意：geo/network 在登录前调用，不能带 auth token
 */

import { getLegacyToken } from './authState';

/**
 * 生成客户端一次性随机数（供后端做「同一请求只能用一次」的防重放校验）
 * 优先 crypto.randomUUID；退化到 getRandomValues；最后才是非安全兜底。
 * @returns {string} 32 位十六进制字符串
 */
export function createClientNonce() {
  if (typeof crypto !== "undefined") {
    if (typeof crypto.randomUUID === "function") {
      return crypto.randomUUID().replace(/-/g, "")
    }
    if (typeof crypto.getRandomValues === "function") {
      const buf = new Uint8Array(16)
      crypto.getRandomValues(buf)
      return Array.from(buf, (b) => b.toString(16).padStart(2, "0")).join("")
    }
  }
  // 兜底：仅保证请求能发出，不具密码学强度（后端只校验唯一性，不校验强度）
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 14)}`
}

/** 读取请求头（兼容 axios 的 AxiosHeaders 与普通对象） */
function getHeader(headers, key) {
  if (!headers) return undefined
  if (typeof headers.get === "function") return headers.get(key)
  return headers[key]
}

/** 写入请求头（兼容 axios 的 AxiosHeaders 与普通对象） */
function setHeader(headers, key, value) {
  if (typeof headers.set === "function") headers.set(key, value)
  else headers[key] = value
}

/**
 * 最小安全请求头（无需登录的场景）
 */
export function getSecurityHeaders() {
  return {
    "X-Requested-With": "XMLHttpRequest",
    "x-client-timestamp": Date.now(),
    "x-client-nonce": createClientNonce(),
  };
}

/**
 * 带 token 的安全请求头（用于登录后的接口）
 *
 * 🔒 安全整改 P0-5（2026-10-08）：不再从 localStorage 读 JWT。
 *   凭证已改为 httpOnly Cookie（浏览器自动携带），这里读不到也不需要读。
 *   仅当存在**遗留** token 时才注入 Authorization（平滑升级 / 降级兜底，
 *   详见 utils/authState.js 的 getLegacyToken 注释）。
 */
export function getAuthSecurityHeaders() {
  const legacyToken = getLegacyToken();
  return {
    ...getSecurityHeaders(),
    ...(legacyToken ? { Authorization: `Bearer ${legacyToken}` } : {}),
  };
}

/**
 * 刷新请求头中的「易失性」安全字段。
 *
 * 使用场景：PIN 验证通过后重发被拦截的原请求。原配置里的字段是首次发出时生成的：
 *  - `x-client-timestamp`：等待用户输入 PIN 可能超过后端 ±2 分钟窗口 → 必须刷新；
 *  - `x-client-nonce`：一次性，复用会被后端判定为重放 → 必须重新生成。
 *
 * ⚠ 将来若启用 `x-sign`（签名串含 timestamp），此处必须同步重算签名。
 *
 * @param {object} headers 请求头（原地修改）
 * @returns {object} 同一个 headers 引用
 */
export function refreshVolatileSecurityHeaders(headers) {
  if (!headers) return headers
  if (getHeader(headers, "x-client-timestamp") != null) {
    setHeader(headers, "x-client-timestamp", Date.now())
  }
  if (getHeader(headers, "x-client-nonce") != null) {
    setHeader(headers, "x-client-nonce", createClientNonce())
  }
  return headers
}
