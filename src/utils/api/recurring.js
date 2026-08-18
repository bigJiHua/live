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

export function getInstallments() {
  return request.get('/recurring/installments')
}

// 分期入账：触发月份直接入账（后端校验账单周期 + 防重复）
export function enterInstallment(id, month) {
  return request.post(`/recurring/${id}/enter`, { month })
}

// 分期中止：仅未入账月份可中止
export function abortInstallment(id, months) {
  return request.post(`/recurring/${id}/abort`, { months })
}
