import request from '@/utils/request'

/**
 * 待办事项 API
 * 基础路径: /api/v1/todo
 */

/**
 * 创建待办
 * @param {object} data - { content, happen_date?, event_type?, priority?, need_remind?, remind_time?, is_recurring?, remark? }
 */
export function createTodo(data) {
  return request.post('/todo', data)
}

/**
 * 获取待办列表
 * @param {object} params - { page?, limit?, happen_date?, event_type?, status? }
 */
export function getTodoList(params) {
  return request.get('/todo/list', { params })
}

/**
 * 获取日历月视图数据
 * @param {object} params - { year, month }
 */
export function getCalendarMonth(params) {
  return request.get('/todo/calendar/month', { params })
}

/**
 * 获取日历年统计数据
 * @param {object} params - { year }
 */
export function getCalendarYear(params) {
  return request.get('/todo/calendar/year', { params })
}

/**
 * 获取即将提醒的待办
 * @param {object} params - { scope? } scope=all 获取30天周期
 */
export function getReminders(params) {
  return request.get('/todo/reminders', { params })
}

/**
 * 获取逾期待办列表
 */
export function getOverdueList() {
  return request.get('/todo/overdue')
}

/**
 * 获取待办详情
 * @param {string} id - 待办ID
 */
export function getTodoDetail(id) {
  return request.get(`/todo/${id}`)
}

/**
 * 更新待办
 * @param {string} id - 待办ID
 * @param {object} data - 更新的字段
 */
export function updateTodo(id, data) {
  return request.put(`/todo/${id}`, data)
}

/**
 * 删除待办
 * @param {string} id - 待办ID
 */
export function deleteTodo(id) {
  return request.delete(`/todo/${id}`)
}

/**
 * 批量更新状态
 * @param {object} data - { ids: string[], status: number }
 */
export function batchUpdateStatus(data) {
  return request.post('/todo/batch/status', data)
}
