import request from '@/utils/request'

/**
 * 用户管理 API (user.js)
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | PUT | `/api/user/profile` | 更新用户资料 | authGuard |
 * | POST | `/api/user/password/change` | 修改密码 | authGuard |
 * | GET | `/api/user/settings` | 获取用户设置 | authGuard |
 * | PUT | `/api/user/settings` | 更新用户设置 | authGuard |
 */
export const userApi = {
  // 更新用户资料
  updateProfile(data) {
    return request.put('/user/profile', data)
  },

  // 修改密码
  changePassword(data) {
    return request.post('/user/password/change', data)
  },

  // 获取用户设置
  getSettings() {
    return request.get('/user/settings')
  },

  // 更新用户设置
  updateSettings(data) {
    return request.put('/user/settings', data)
  },
}
