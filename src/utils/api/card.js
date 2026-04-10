import request from "@/utils/request";

/**
 * 卡片管理 API
 * 基础路径: /api/v1/card
 */

/**
 * 获取卡片列表
 * @param {object} params - { cardType?, isHide? }
 */
export function getCardList(params) {
  return request.get("/card", { params });
}

/**
 * 获取单个卡片详情
 * @param {string} id - 卡片ID
 */
export function getCardDetail(id) {
  return request.get(`/card/${id}`);
}

/**
 * 创建卡片
 * @param {object} data - 卡片数据
 */
export function createCard(data) {
  return request.post("/card", data);
}

/**
 * 更新卡片
 * @param {string} id - 卡片ID
 * @param {object} data - 更新的字段
 */
export function updateCard(id, data) {
  return request.put(`/card/${id}`, data);
}

/**
 * 删除卡片
 * @param {string} id - 卡片ID
 */
export function deleteCard(id) {
  return request.delete(`/card/${id}`);
}

/**
 * 信用卡账单 API
 * 基础路径: /api/v1/card/bill
 */

/**
 * 获取账单列表
 * @param {object} params - { cardId? }
 */
export function getBillList(params) {
  return request.get("/card/bill", { params });
}

/**
 * 获取单个账单详情
 * @param {string} id - 账单ID
 */
export function getBillDetail(id) {
  return request.get(`/card/bill/${id}`);
}

/**
 * 获取卡片最新账单
 * @param {string} cardId - 卡片ID
 */
export function getLatestBill(cardId) {
  return request.get(`/card/bill/card/${cardId}/latest`);
}

/**
 * 刷新卡片账单
 * @param {string} cardId - 卡片ID
 */
export function rebuildBill(cardId) {
  return request.post(`/card/bill/card/${cardId}/rebuild`);
}

/**
 * 创建账单
 * @param {object} data - 账单数据
 * @param {string} data.cardId - 卡片ID (必填)
 * @param {number} data.creditLimit - 信用额度 (必填)
 * @param {number} data.tempLimit - 临时额度 (可选，默认0)
 * @param {number} data.pointsRate - 积分倍率 (可选，默认1)
 * @param {boolean} data.remindSwitch - 还款提醒开关 (可选，默认true)
 * @param {number} data.remindDays - 提前提醒天数 (可选，默认3)
 *
 * 后端自动计算字段: usedLimit, availLimit, billAmount, minRepay, repaid, needRepay,
 * points, pointsExpire, repayStatus, isOverdue, overdueDays
 */
export function createBill(data) {
  return request.post("/card/bill", data);
}

/**
 * 更新账单
 * @param {string} id - 账单ID
 * @param {object} data - 更新的字段
 * @param {number} data.creditLimit - 信用额度 (更新时同步计算 availLimit)
 * @param {number} data.tempLimit - 临时额度
 * @param {number} data.pointsRate - 积分倍率 (更新时同步计算 points)
 * @param {number} data.repaid - 已还金额 (更新时同步计算 needRepay 和 repayStatus)
 * @param {boolean} data.remindSwitch - 还款提醒开关
 * @param {number} data.remindDays - 提前提醒天数
 */
export function updateBill(id, data) {
  return request.put(`/card/bill/${id}`, data);
}

/**
 * 删除账单
 * @param {string} id - 账单ID
 */
export function deleteBill(id) {
  return request.delete(`/card/bill/${id}`);
}

/**
 * 还款记录 API
 * 基础路径: /api/v1/card/repay
 */

/**
 * 获取还款记录列表
 * @param {object} params - { cardId?, billId? }
 */
export function getRepayList(params) {
  return request.get("/card/repay", { params });
}

/**
 * 获取单个还款记录
 * @param {string} id - 还款记录ID
 */
export function getRepayDetail(id) {
  return request.get(`/card/repay/${id}`);
}

/**
 * 创建还款记录
 * @param {object} data - 还款数据
 */
export function createRepay(data) {
  return request.post("/card/repay", data);
}

/**
 * 更新还款记录
 * @param {string} id - 还款记录ID
 * @param {object} data - 更新的字段
 */
export function updateRepay(id, data) {
  return request.put(`/card/repay/${id}`, data);
}
