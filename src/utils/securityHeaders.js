/**
 * 安全请求头工具
 * 用于绕过 securityCheck 中间件的最小头集合
 * 注意：geo/network 在登录前调用，不能带 auth token
 */
export function getSecurityHeaders() {
  return {
    "X-Requested-With": "XMLHttpRequest",
    "x-client-timestamp": Date.now(),
  };
}

/**
 * 带 token 的安全请求头（用于登录后的接口）
 */
export function getAuthSecurityHeaders() {
  const token = localStorage.getItem("finance_token");
  return {
    ...getSecurityHeaders(),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}
