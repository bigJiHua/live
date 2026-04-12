import request from '@/utils/request'

/**
 * 资产 API
 * 基础路径: /api/v1/asset
 */

/**
 * 获取首页资产数据
 * 返回: {
 *   total_asset, credit_debt, total_balance,
 *   debitCardCount, creditCardCount, creditDebt,
 *   monthIncome, monthExpense, monthBalance,
 *   largeTransactions: [{ id, direction, amount, category_id, pay_type, trans_date, remark, create_time, type }]
 * }
 */
export function getAssetHome() {
  return request.get('/asset/home')
}

/**
 * 获取系统快照列表
 * @param {object} params - { limit?, startDate?, endDate? }
 */
export function getSnapshotList(params) {
  return request.get('/asset/snapshot/list', { params })
}

/**
 * 获取用户登记列表
 * @param {object} params - { startDate?, endDate? }
 */
export function getRegisterList(params) {
  return request.get('/asset/register/list', { params })
}

/**
 * 手动登记资产
 * @param {object} data - { total_asset, credit_debt, total_balance, asset_details?, remark? }
 */
export function createAssetRegister(data) {
  return request.post('/asset/register', data)
}

/**
 * 更新登记记录
 * @param {string} id - 记录ID
 * @param {object} data - 更新的字段
 */
export function updateAssetRegister(id, data) {
  return request.put(`/asset/register/${id}`, data)
}

/**
 * 删除登记记录
 * @param {string} id - 记录ID
 */
export function deleteAssetRegister(id) {
  return request.delete(`/asset/register/${id}`)
}
