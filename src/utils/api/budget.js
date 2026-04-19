import request from "@/utils/request";

/**
 * ============================================
 * 预算管理 API
 * 基础路径: /api/v1/budget
 * ============================================
 */

/**
 * 获取所有预算
 * GET /api/v1/budget/list
 */
export function getBudgetList() {
  return request.get("/budget/list");
}

/**
 * 获取预算统计
 * GET /api/v1/budget/stats
 */
export function getBudgetStats() {
  return request.get("/budget/stats");
}

/**
 * 获取单个预算
 * GET /api/v1/budget/:id
 * @param {string} id - 预算ID
 */
export function getBudget(id) {
  return request.get(`/budget/${id}`);
}

/**
 * 创建预算
 * POST /api/v1/budget/
 * @param {object} data
 */
export function createBudget(data) {
  return request.post("/budget/", data);
}

/**
 * 更新预算
 * PUT /api/v1/budget/:id
 * @param {string} id - 预算ID
 * @param {object} data - 可更新字段
 */
export function updateBudget(id, data) {
  return request.put(`/budget/${id}`, data);
}

/**
 * 删除预算（硬删除）
 * DELETE /api/v1/budget/:id
 * @param {string} id - 预算ID
 */
export function deleteBudget(id) {
  return request.delete(`/budget/${id}`);
}
