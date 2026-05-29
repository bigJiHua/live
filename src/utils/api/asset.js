import request from '@/utils/request'

/**
 * 资产 API
 * 基础路径: /api/v1/asset
 */

/**
 * 获取首页资产数据
 * 返回: {
 *   total_asset: number,          // 总资产（快照）
 *   credit_debt: number,          // 信用卡总欠款（快照）
 *   total_balance: number,        // 最终总资产余额（快照）
 *   record_date: string,          // 快照记录日期 YYYY-MM-DD
 *   record_time: string,          // 快照记录时间
 *   debitCardCount: number,       // 借记卡数量
 *   creditCardCount: number,      // 信用卡数量
 *   creditDebt: number,           // 实时信用卡代还金额
 *   monthIncome: number,          // 本月收入
 *   monthExpense: number,         // 本月支出
 *   monthBalance: number,         // 本月结余
 *   todayIncome: number,          // 今日收入
 *   todayExpense: number,         // 今日支出
 *   largeTransactions: Array<{    // 近期大额流水
 *     id, card_id, direction, amount, category_id,
 *     pay_type, trans_date, remark, create_time, type
 *   }>,
 *   pendingBills: Array<{         // 待还账单明细
 *     id, card_id, card_alias, card_last4, bill_month,
 *     bill_amount, min_repay, repaid, need_repay,
 *     repay_status, is_overdue, overdue_days, bill_end_date
 *   }>
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

/**
 * 获取单条登记记录
 * @param {string} id - 记录ID
 */
export function getAssetRegister(id) {
  return request.get(`/asset/register/${id}`)
}
