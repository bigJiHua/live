import request from '@/utils/request'

/**
 * 数据管理 API
 * 基础路径: /api/v1/data-manager
 */

/**
 * 获取数据库表名列表
 */
export function getTableList() {
  return request.get('/data-manager/tables')
}

/**
 * 获取数据库表状态
 * @returns {Promise} 返回表状态列表（行数/大小/引擎等）
 */
export function getTableStatus() {
  return request.get('/data-manager/status')
}

/**
 * 获取指定表的字段结构
 * @param {string} tableName - 表名
 */
export function getTableStructure(tableName) {
  return request.get(`/data-manager/structure/${tableName}`)
}

/**
 * 分页获取表数据
 * @param {string} tableName - 表名
 * @param {object} params - { page?, pageSize? }
 */
export function getTableData(tableName, params) {
  return request.get(`/data-manager/data/${tableName}`, { params })
}

/**
 * 导出单张表
 * @param {string} tableName - 表名
 * @param {object} params - { includeData?: 'true'|'false' }
 */
export function exportSingleTable(tableName, params = {}) {
  return request.get(`/data-manager/export/table/${tableName}`, { params })
}

/**
 * 导出整个数据库
 * @param {object} params - { format?: 'sql'|'zip', includeData?: 'true'|'false' }
 */
export function exportFullDatabase(params = {}) {
  return request.get('/data-manager/export/full', { params })
}

/**
 * 校验导入数据
 * @param {object} data - { tableName, data: [] }
 */
export function validateImportData(data) {
  return request.post('/data-manager/validate', data)
}

/**
 * 导入数据
 * @param {object} data - { tableName, data: [], forceClear?: boolean }
 */
export function importData(data) {
  return request.post('/data-manager/import', data)
}

/**
 * SQL / ZIP 全库导入
 * @param {FormData} formData - 包含 file 字段的 FormData
 */
export function importSql(formData) {
  return request.post('/data-manager/import/sql', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000,
  })
}

/**
 * 获取备份文件列表
 */
export function getBackupList() {
  return request.get('/data-manager/backups')
}

/**
 * 创建系统备份
 * @param {object} params - { includeData?: 'true'|'false' }
 */
export function createSystemBackup(params = {}) {
  return request.post('/data-manager/backups/system', null, { params })
}

/**
 * 获取系统备份列表（按日期分组）
 */
export function getSystemBackups() {
  return request.get('/data-manager/backups/system')
}

/**
 * 删除备份文件
 * @param {string} filename - 文件名
 */
export function deleteBackup(filename) {
  return request.delete(`/data-manager/backups/${filename}`)
}

/**
 * 下载备份文件
 * @param {string} filename - 文件名
 */
export function downloadBackup(filename) {
  return request.get(`/data-manager/download/${filename}`, {
    responseType: 'blob'
  })
}

/**
 * 获取导出任务状态
 * @param {string} taskId - 任务ID
 */
export function getExportTaskStatus(taskId) {
  return request.get(`/data-manager/export/task/${taskId}`)
}

/**
 * 取消导出任务
 * @param {string} taskId - 任务ID
 */
export function cancelExportTask(taskId) {
  return request.delete(`/data-manager/export/task/${taskId}`)
}

export default {
  getTableList,
  getTableStatus,
  getTableStructure,
  getTableData,
  exportSingleTable,
  exportFullDatabase,
  validateImportData,
  importData,
  importSql,
  getBackupList,
  createSystemBackup,
  getSystemBackups,
  deleteBackup,
  downloadBackup,
  getExportTaskStatus,
  cancelExportTask
}