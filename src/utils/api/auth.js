import request from '@/utils/request'

/**
 * 认证相关 API (auth.js)
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | POST | `/api/auth/register` | 用户注册 | - |
 * | POST | `/api/auth/login` | 用户登录 | - |
 * | GET | `/api/auth/me` | 获取当前用户信息 | authGuard |
 * | POST | `/api/auth/refresh` | 刷新 Token | - |
 */
export const authApi = {
  // 用户注册
  register(data) {
    return request.post('/auth/register', data)
  },

  // 用户登录
  login(data) {
    return request.post('/auth/login', data)
  },

  // 获取当前用户信息
  getMe() {
    return request.get('/auth/me')
  },

  // 刷新 Token
  refreshToken(data) {
    return request.post('/auth/refresh', data)
  },
}
