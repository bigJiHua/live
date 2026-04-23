/**
 * 请求拦截器
 */

import config from '../config'
import { getClientContext } from '../client'
import { getAesKey } from '../handshake'
import { encrypt } from '../crypto'

/**
 * 构建请求拦截器
 * @param {Function} router - 路由实例
 */
export function createRequestInterceptor(router) {
  return async (requestConfig) => {
    // 获取客户端上下文
    const deviceData = await getClientContext()
    const fp = deviceData.fingerprint

    // 1. 注入安全 Headers
    requestConfig.headers['Content-Type'] = 'application/json'
    requestConfig.headers['X-Requested-With'] = 'XMLHttpRequest'
    requestConfig.headers['x-client-timestamp'] = Date.now()
    requestConfig.headers['x-fingerprint-hash'] = fp
    requestConfig.headers['x-user-agent-custom'] = deviceData.userAgent
    requestConfig.headers['x-device-model'] = deviceData.deviceModel
    requestConfig.headers['x-os-name'] = deviceData.osInfo
    requestConfig.headers['x-device-type'] = deviceData.deviceType

    // 2. 注入 Token
    const token = localStorage.getItem(config.tokenKey)
    if (token) {
      requestConfig.headers['Authorization'] = `Bearer ${token}`
    }

    // 3. 握手接口跳过加密
    if (requestConfig.url.includes(config.handshakeUrl)) {
      return requestConfig
    }

    // 3.1 跳过加密的接口列表
    if (config.skipEncryptionUrls?.some(url => requestConfig.url.includes(url))) {
      return requestConfig
    }

    // 4. 写操作需要加密
    const isWriteOperation = config.encryptedMethods.includes(
      requestConfig.method?.toLowerCase()
    )

    if (isWriteOperation) {
      // 构建加密载荷
      const rawData = {
        ...deviceData,
        data: requestConfig.data,
        path: window.location.pathname,
      }

      // 获取 AES Key
      const aesKey = await getAesKey(fp, deviceData)

      if (aesKey) {
        requestConfig.data = {
          _p: encrypt(rawData, aesKey),
        }
        requestConfig.headers['X-FP-ID'] = fp
      }
    }

    return requestConfig
  }
}

export default { createRequestInterceptor }
