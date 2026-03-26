import request from '@/utils/request'

/**
 * 安全相关 API (security.js) - PIN 码管理
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | POST | `/api/security/pin/verify` | 验证 PIN 码 | authGuard |
 * | POST | `/api/security/pin/set` | 设置 PIN 码 | authGuard |
 * | POST | `/api/security/pin/change` | 修改 PIN 码 | authGuard |
 * | POST | `/api/security/pin/reset` | 重置 PIN 码 | - |
 */
export const securityApi = {
  // 验证 PIN 码
  verifyPin(data) {
    return request.post('/security/pin/verify', data)
  },

  // 设置 PIN 码
  setPin(data) {
    return request.post('/security/pin/set', data)
  },

  // 修改 PIN 码
  changePin(data) {
    return request.post('/security/pin/change', data)
  },

  // 重置 PIN 码
  resetPin(data) {
    return request.post('/security/pin/reset', data)
  },
}
