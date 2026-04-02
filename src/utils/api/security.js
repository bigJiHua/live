import request from "@/utils/request";

/**
 * 安全相关 API (security.js) - PIN 码管理
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | GET | `/api/v1/security/pin/check` | 检查 PIN 设置状态 | authGuard |
 * | POST | `/api/v1/security/pin/verify` | 验证 PIN 码 | authGuard |
 * | POST | `/api/v1/security/pin/set` | 设置 PIN 码 | authGuard |
 * | POST | `/api/v1/security/pin/change` | 修改 PIN 码 | authGuard |
 * | POST | `/api/v1/security/pin/reset` | 重置 PIN 码 | authGuard |
 */
export const securityApi = {
  // 检查 PIN 设置状态
  checkPin() {
    return request.get("/security/pin/check");
  },

  // 验证 PIN 码
  verifyPin(data) {
    return request.post("/security/pin/verify", data);
  },

  // 设置 PIN 码
  setPin(data) {
    return request.post("/security/pin/set", data);
  },

  // 修改 PIN 码
  changePin(data) {
    return request.post("/security/pin/change", data);
  },

  // 重置 PIN 码
  resetPin(data) {
    return request.post("/security/pin/reset", data);
  },
};
