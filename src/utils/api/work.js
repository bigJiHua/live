import request from '@/utils/request'

/**
 * 工作信息 API
 * 基础路径: /api/v1/work
 */

/**
 * 获取工作列表
 * 返回: { formal: {...}, parttimes: [...] }
 */
export function getJobList() {
  return request.get('/work/job/list')
}

/**
 * 创建工作
 * @param {object} data - 工作信息
 * job_type: 'formal' | 'parttime'
 */
export function saveJob(data) {
  return request.post('/work/job', data)
}

/**
 * 更新工作
 * @param {string} id - 工作ID
 * @param {object} data - 更新的数据
 */
export function updateJob(id, data) {
  return request.put(`/work/job/${id}`, data)
}

/**
 * 删除工作
 * @param {string} id - 工作ID
 */
export function deleteJob(id) {
  return request.delete(`/work/job/${id}`)
}

/**
 * 获取某天工资
 * @param {object} params - { work_date: 'YYYY-MM-DD', work_hours?: JSON字符串 }
 */
export function getSalaryDay(params) {
  return request.get('/work/salary/day', { params })
}

/**
 * 保存当日工资
 * @param {object} data - { job_id, work_date, work_hours, cut, subsidy_meal, subsidy_traffic, subsidy_post, status, remark }
 */
export function saveSalaryDay(data) {
  return request.post('/work/salary', data)
}

/**
 * 按月统计工资
 * @param {object} params - { year, month }
 */
export function getSalaryMonth(params) {
  return request.get('/work/salary/month', { params })
}

/**
 * 删除工资记录
 * @param {object} data - { job_id, work_date }
 */
export function deleteSalaryDay(data) {
  return request.delete('/work/salary', { data })
}
