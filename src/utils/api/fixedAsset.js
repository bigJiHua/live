import request from '@/utils/request'
import fileRequest from '@/utils/fileRequest'

/**
 * 固定资产 API
 * 基础路径: /api/v1/fixedAsset
 */

/**
 * 触发折旧巡检
 * POST /api/v1/fixedAsset/depreciate
 */
export function triggerDeprecInspection() {
  return request.post('/fixedAsset/depreciate')
}

/**
 * 获取资产列表（自动触发折旧巡检）
 * GET /api/v1/fixedAsset/list
 */
export function getFixedAssetList() {
  return request.get('/fixedAsset/list')
}

/**
 * 获取回收站列表
 * GET /api/v1/fixedAsset/recycle-bin
 */
export function getRecycleBinList() {
  return request.get('/fixedAsset/recycle-bin')
}

/**
 * 获取单个资产详情
 * GET /api/v1/fixedAsset/:id
 */
export function getFixedAsset(id) {
  return request.get(`/fixedAsset/${id}`)
}

/**
 * 创建固定资产
 * POST /api/v1/fixedAsset/
 * @param {object} data - {
 *   info: string,              [必填] 资产名称
 *   tag: string,               [必填] 品类
 *   img_url: string,           [必填] 图片URL
 *   buy_price: number,         [必填] 购买价
 *   secondhand_price?: number,  [选填] 二手市场价
 *   use_years: number,        [必填] 预计使用年限
 *   residual_rate: number,     [必填] 残值率（0-100）
 *   buy_date: string,          [必填] 购买日期 yyyy-MM-dd
 *   status?: string,           [选填] 状态，默认 using
 * }
 */
export function createFixedAsset(data) {
  return request.post('/fixedAsset/', data)
}

/**
 * 更新固定资产
 * PUT /api/v1/fixedAsset/:id
 * 可修改字段：info, tag, img_url, now_val, secondhand_price, use_years, residual_rate, scrap_date, status
 */
export function updateFixedAsset(id, data) {
  return request.put(`/fixedAsset/${id}`, data)
}

/**
 * 变更资产状态
 * PUT /api/v1/fixedAsset/:id/status
 * @param {string} id - 资产ID
 * @param {string} status - using/scrapped/sold/lost
 */
export function changeAssetStatus(id, status) {
  return request.put(`/fixedAsset/${id}/status`, { status })
}

/**
 * 删除资产（软删除）
 * DELETE /api/v1/fixedAsset/:id
 */
export function deleteFixedAsset(id) {
  return request.delete(`/fixedAsset/${id}`)
}

/**
 * 恢复资产
 * PUT /api/v1/fixedAsset/restore/:id
 */
export function restoreFixedAsset(id) {
  return request.put(`/fixedAsset/restore/${id}`)
}

/**
 * 永久删除
 * DELETE /api/v1/fixedAsset/permanent/:id
 */
export function permanentlyDeleteFixedAsset(id) {
  return request.delete(`/fixedAsset/permanent/${id}`)
}

/**
 * 获取资产图片列表
 * GET /api/v1/upload/list?busType=product&limit=100&offset=0
 */
export function getAssetImageList(limit = 100, offset = 0) {
  return request.get('/upload/list', {
    params: { busType: 'product', limit, offset }
  })
}

/**
 * 上传资产图片
 * POST /api/v1/upload/multiple
 * @param {File} file - 图片文件
 * @param {string} remark - 图片说明
 */
export function uploadAssetImage(file, remark = 'logo资产') {
  const formData = new FormData()
  formData.append('files', file)
  formData.append('busType', 'product')
  formData.append('remark', remark)
  return fileRequest.post('/upload/multiple', formData)
}
