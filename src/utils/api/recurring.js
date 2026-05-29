import request from '@/utils/request'

/**
 * 固定周期支出提醒 API
 * 基础路径: /api/v1/recurring
 */

export function getRecurringList(params) {
  return request.get('/recurring/list', { params })
}

export function getRecurringSummary(params) {
  return request.get('/recurring/summary', { params })
}

export function createRecurring(data) {
  return request.post('/recurring', data)
}

export function updateRecurring(id, data) {
  return request.put(`/recurring/${id}`, data)
}

export function deleteRecurring(id) {
  return request.delete(`/recurring/${id}`)
}

export function updateRecurringMonthStatus(id, data) {
  return request.put(`/recurring/${id}/month-status`, data)
}
