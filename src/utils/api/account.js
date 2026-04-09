import request from '@/utils/request'

/**
 * 收支记录 API
 * 基础路径: /api/v1/account
 */

/**
 * 获取收支列表
 * @param {object} params - { page, limit, direction?, categoryId?, payMethod?, startDate?, endDate? }
 */
export function getAccountList(params) {
  return request.get('/account', { params })
}

/**
 * 获取单条收支详情
 * @param {string} id - 记录ID
 */
export function getAccountDetail(id) {
  return request.get(`/account/${id}`)
}

/**
 * 创建收支记录
 * @param {object} data - { direction, categoryId, payType, payMethod, amount, transDate, cardId, ... }
 */
export function createAccount(data) {
  return request.post('/account', data)
}

/**
 * 更新收支记录
 * @param {string} id - 记录ID
 * @param {object} data - 更新的字段
 */
export function updateAccount(id, data) {
  return request.put(`/account/${id}`, data)
}

/**
 * 删除收支记录
 * @param {string} id - 记录ID
 */
export function deleteAccount(id) {
  return request.delete(`/account/${id}`)
}

/**
 * 获取本月收支统计
 * @param {object} params - { year?, month?, type? }
 * @param {string} params.type - summary(返回总数) / detail(返回总数+明细)
 */
export function getMonthStats(params) {
  return request.get('/account/stats/month', { params })
}

/**
 * ========== 账户余额管理 API ==========
 * 基础路径: /api/v1/accountBalance
 */

// 虚拟账户ID常量
export const VIRTUAL_ACCOUNTS = {
  CASH: 'virtual_cash',      // 现金
  WX: 'virtual_wx',          // 微信
  ALIPAY: 'virtual_alipay',  // 支付宝
  BANK: 'virtual_bank'       // 银行卡（通用）
}

/**
 * 获取所有账户余额（包括虚拟账户）
 */
export function getBalanceList() {
  return request.get('/accountBalance')
}

/**
 * 获取单张卡/账户余额
 * @param {string} cardId - 账户ID
 */
export function getBalanceById(cardId) {
  return request.get(`/accountBalance/${cardId}`)
}

/**
 * 手动同步余额
 * @param {string} cardId - 账户ID
 * @param {number} balance - 余额
 */
export function upsertBalance(cardId, balance) {
  return request.post('/accountBalance/upsert', { card_id: cardId, balance })
}

/**
 * 初始化虚拟账户
 * @param {object} data - { virtual_cash?, virtual_wx?, virtual_alipay?, virtual_bank? }
 */
export function initVirtualAccounts(data) {
  return request.post('/accountBalance/init-virtual', data)
}

/**
 * 更新虚拟账户余额
 * @param {object} data - { virtual_cash?, virtual_wx?, virtual_alipay?, virtual_bank? }
 */
export function updateVirtualAccounts(data) {
  return request.post('/accountBalance/update-virtual', data)
}
