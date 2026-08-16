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
 * 批量更新排序：前端整列 1-N 重排后一次提交
 * @param {Array<{id:string, sort:number}>} items - 顺序数组，sort 为 1-based 位置
 */
export function updateCardSortBatch(items) {
  return request.put("/card/sort", { items });
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
 * 信报合一合并还款：一次性结清共享池内全部卡欠款
 * @param {object} data - { poolId, repayMethod, repayMethodCardId, repayTime, remark }
 */
export function mergeRepay(data) {
  return request.post("/card/repay/merge", data);
}

/**
 * 更新还款记录
 * @param {string} id - 还款记录ID
 * @param {object} data - 更新的字段
 */
export function updateRepay(id, data) {
  return request.put(`/card/repay/${id}`, data);
}

/**
 * 删除还款记录
 * DELETE /api/v1/card/repay/:id
 * @param {string} id - 还款记录ID
 */
export function deleteRepay(id) {
  return request.delete(`/card/repay/${id}`);
}

/**
 * 共享额度池 API（痛点2）
 * 基础路径: /api/v1/card/pool
 */

/** 我的共享池列表 */
export function getCreditPools() {
  return request.get("/card/pool");
}

/** 创建共享池 */
export function createCreditPool(data) {
  return request.post("/card/pool", data);
}

/** 更新共享池 */
export function updateCreditPool(id, data) {
  return request.put(`/card/pool/${id}`, data);
}

/** 删除共享池 */
export function deleteCreditPool(id) {
  return request.delete(`/card/pool/${id}`);
}

/** 卡片归入/移出共享池 */
export function assignCardPool(cardId, poolId) {
  return request.post("/card/pool/assign", { cardId, poolId });
}

/**
 * 外币消费登记/对账 API（痛点4）
 * 基础路径: /api/v1/card/bill/foreign
 */

/** 待对账外币列表（专用登记页） */
export function getForeignPending() {
  return request.get("/card/bill/foreign/pending");
}

/** 全部外币登记列表 */
export function getForeignList() {
  return request.get("/card/bill/foreign/list");
}

/** 历史外币消费流水（扫描账本，含未登记，支持 cardId/startDate/endDate 过滤） */
export function getForeignHistory(params) {
  return request.get("/card/bill/foreign/history", { params });
}

/** 补登记未登记的历史外币消费流水 */
export function registerForeignAccount(data) {
  return request.post("/card/bill/foreign/register", data);
}

/** 外币对账：录入实际汇率/人民币 */
export function reconcileForeign(id, data) {
  return request.put(`/card/bill/foreign/${id}/reconcile`, data);
}

/** 删除外币登记 */
export function deleteForeign(id) {
  return request.delete(`/card/bill/foreign/${id}`);
}

