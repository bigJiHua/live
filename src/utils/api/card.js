import request from '@/utils/request'

/**
 * 卡片管理 API
 * 基础路径: /api/card
 */

/**
 * 获取卡片列表
 * @param {object} params - { cardType?, isHide? }
 */
export function getCardList(params) {
  return request({
    url: '/card',
    method: 'GET',
    params
  })
}

/**
 * 获取单个卡片详情
 * @param {string} id - 卡片ID
 */
export function getCardDetail(id) {
  return request({
    url: `/card/${id}`,
    method: 'GET'
  })
}

/**
 * 创建卡片
 * @param {object} data - 卡片数据
 */
export function createCard(data) {
  return request({
    url: '/card',
    method: 'POST',
    data
  })
}

/**
 * 更新卡片
 * @param {string} id - 卡片ID
 * @param {object} data - 更新的字段
 */
export function updateCard(id, data) {
  return request({
    url: `/card/${id}`,
    method: 'PUT',
    data
  })
}

/**
 * 删除卡片
 * @param {string} id - 卡片ID
 */
export function deleteCard(id) {
  return request({
    url: `/card/${id}`,
    method: 'DELETE'
  })
}

/**
 * 卡片账单 API
 * 基础路径: /api/card/bill
 */

/**
 * 获取账单列表
 * @param {object} params - { cardId? }
 */
export function getBillList(params) {
  return request({
    url: '/card/bill',
    method: 'GET',
    params
  })
}

/**
 * 获取单个账单详情
 * @param {string} id - 账单ID
 */
export function getBillDetail(id) {
  return request({
    url: `/card/bill/${id}`,
    method: 'GET'
  })
}

/**
 * 获取卡片最新账单
 * @param {string} cardId - 卡片ID
 */
export function getLatestBill(cardId) {
  return request({
    url: `/card/bill/card/${cardId}/latest`,
    method: 'GET'
  })
}

/**
 * 创建账单
 * @param {object} data - 账单数据
 */
export function createBill(data) {
  return request({
    url: '/card/bill',
    method: 'POST',
    data
  })
}

/**
 * 更新账单
 * @param {string} id - 账单ID
 * @param {object} data - 更新的字段
 */
export function updateBill(id, data) {
  return request({
    url: `/card/bill/${id}`,
    method: 'PUT',
    data
  })
}

/**
 * 删除账单
 * @param {string} id - 账单ID
 */
export function deleteBill(id) {
  return request({
    url: `/card/bill/${id}`,
    method: 'DELETE'
  })
}

/**
 * 还款记录 API
 * 基础路径: /api/card/repay
 */

/**
 * 获取还款记录列表
 * @param {object} params - { cardId?, billId? }
 */
export function getRepayList(params) {
  return request({
    url: '/card/repay',
    method: 'GET',
    params
  })
}

/**
 * 获取单个还款记录
 * @param {string} id - 还款记录ID
 */
export function getRepayDetail(id) {
  return request({
    url: `/card/repay/${id}`,
    method: 'GET'
  })
}

/**
 * 创建还款记录
 * @param {object} data - 还款数据
 */
export function createRepay(data) {
  return request({
    url: '/card/repay',
    method: 'POST',
    data
  })
}

/**
 * 更新还款记录
 * @param {string} id - 还款记录ID
 * @param {object} data - 更新的字段
 */
export function updateRepay(id, data) {
  return request({
    url: `/card/repay/${id}`,
    method: 'PUT',
    data
  })
}

/**
 * 删除还款记录
 * @param {string} id - 还款记录ID
 */
export function deleteRepay(id) {
  return request({
    url: `/card/repay/${id}`,
    method: 'DELETE'
  })
}
