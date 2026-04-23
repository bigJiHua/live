import request from "@/utils/request";

/**
 * 认证相关 API (auth.js)
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | POST | `/api/v1/auth/register` | 用户注册 | - |
 * | POST | `/api/v1/auth/login` | 用户登录 | - |
 * | GET | `/api/v1/auth/me` | 获取当前用户信息 | authGuard |
 * | POST | `/api/v1/auth/refresh` | 刷新 Token | - |
 * | GET | `/api/v1/auth/check-admin` | 检查管理员是否存在 | - |
 * | POST | `/api/v1/auth/admin-register` | 管理员注册（仅首次） | - |
 * | POST | `/api/v1/auth/forgot-password` | 忘记密码 | - |
 * | POST | `/api/v1/auth/reset-password` | 重置密码 | - |
 */
export const authApi = {
  // 用户注册
  register(data) {
    return request.post("/auth/register", data);
  },

  // 用户登录
  login(data) {
    return request.post("/auth/login", data);
  },

  // 获取当前用户信息
  getUserinfo() {
    return request.get("/auth/me");
  },

  // 刷新 Token
  refreshToken(data) {
    return request.post("/auth/refresh", data);
  },

  // 检查管理员是否存在
  checkAdmin() {
    return request.get("/auth/check-admin");
  },

  // 管理员注册（仅首次初始化时可用）
  adminRegister(data) {
    return request.post("/auth/admin-register", data);
  },
  // 忘记密码
  forgotPassword(data) {
    return request.post("/auth/forgot-password", data);
  },

  // 重置密码
  resetPassword(data) {
    return request.post("/auth/reset-password", data);
  },

  // 更新用户资料（用户名、头像）
  updateProfile(data) {
    return request.put("/user/profile", data);
  },

  // 发送邮箱验证码
  sendEmailCode(data) {
    return request.post("/user/email/send-code", data);
  },

  // 修改邮箱
  updateEmail(data) {
    return request.put("/user/email/change", data);
  },

  // 修改登录密码（通过验证码）
  changePassword(data) {
    return request.put("/user/password/change", data);
  },

  // 锁定系统
  lockSystem() {
    return request.post("/auth/lock-system");
  },
};
