import request from '@/utils/request'

/**
 * 账务管理 API (account.js) - 需要 PIN 验证
 *
 * | 方法 | 路径 | 说明 | 中间件 |
 * |------|------|------|--------|
 * | GET | `/api/v1/account/transactions` | 获取账务流水列表 | authGuard + pinLockGuard |
 * | GET | `/api/v1/account/transactions/:id` | 获取账务流水详情 | authGuard + pinLockGuard |
 * | POST | `/api/v1/account/transactions` | 创建账务流水 | authGuard + pinLockGuard |
 * | PUT | `/api/v1/account/transactions/:id` | 更新账务流水 | authGuard + pinLockGuard |
 * | DELETE | `/api/v1/account/transactions/:id` | 删除账务流水 | authGuard + pinLockGuard |
 * | GET | `/api/v1/account/categories` | 获取分类列表 | authGuard + pinLockGuard |
 * | POST | `/api/v1/account/categories` | 创建分类 | authGuard + pinLockGuard |
 * | PUT | `/api/v1/account/categories/:id` | 更新分类 | authGuard + pinLockGuard |
 * | DELETE | `/api/v1/account/categories/:id` | 删除分类 | authGuard + pinLockGuard |
 * | GET | `/api/v1/account/report` | 获取财务报表 | authGuard + pinLockGuard |
 * | POST | `/api/v1/account/calculate-irr` | 计算 IRR | authGuard + pinLockGuard |
 *
 * **重要**: 所有账务接口都会先检查 PIN 验证状态，如果未验证会返回 423 状态码。
 */
export const accountApi = {
  // 获取账务流水列表
  getTransactions(params) {
    return request.get('/account/transactions', { params })
  },

  // 获取账务流水详情
  getTransactionDetail(id) {
    return request.get(`/account/transactions/${id}`)
  },

  // 创建账务流水
  createTransaction(data) {
    return request.post('/account/transactions', data)
  },

  // 更新账务流水
  updateTransaction(id, data) {
    return request.put(`/account/transactions/${id}`, data)
  },

  // 删除账务流水
  deleteTransaction(id) {
    return request.delete(`/account/transactions/${id}`)
  },

  // 获取分类列表
  getCategories() {
    return request.get('/account/categories')
  },

  // 创建分类
  createCategory(data) {
    return request.post('/account/categories', data)
  },

  // 更新分类
  updateCategory(id, data) {
    return request.put(`/account/categories/${id}`, data)
  },

  // 删除分类
  deleteCategory(id) {
    return request.delete(`/account/categories/${id}`)
  },

  // 获取财务报表
  getReport(params) {
    return request.get('/account/report', { params })
  },

  // 计算 IRR
  calculateIRR(data) {
    return request.post('/account/calculate-irr', data)
  },
}
