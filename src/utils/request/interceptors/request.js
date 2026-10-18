/**
 * 请求拦截器
 */

import config from '../config'
import { getClientContext } from '../client'
import { getAesKey } from '../handshake'
import { encrypt } from '../crypto'
import { createClientNonce } from '../../securityHeaders'
import { getLegacyToken } from '@/utils/authState'

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
    const isFormData = requestConfig.data instanceof FormData
    if (!isFormData) {
      requestConfig.headers['Content-Type'] = 'application/json'
    }
    requestConfig.headers['X-Requested-With'] = 'XMLHttpRequest'
    requestConfig.headers['x-client-timestamp'] = Date.now()
    // 一次性随机数：后端据此拒绝「窗口期内原样重放」的请求。
    // ⚠ PIN 验证后重发原请求时必须重新生成，见 securityHeaders.refreshVolatileSecurityHeaders
    requestConfig.headers['x-client-nonce'] = createClientNonce()
    requestConfig.headers['x-fingerprint-hash'] = fp
    requestConfig.headers['x-user-agent-custom'] = deviceData.userAgent
    requestConfig.headers['x-device-model'] = deviceData.deviceModel
    requestConfig.headers['x-os-name'] = deviceData.osInfo
    requestConfig.headers['x-device-type'] = deviceData.deviceType

    // 2. 注入 Token
    // 🔒 安全整改 P0-5（2026-10-08）：**不再**从 localStorage 读 JWT 注入 Bearer。
    //    真实凭证改由服务端下发 httpOnly Cookie，浏览器自动携带、JS 读不到
    //    ⇒ 任意 XSS 都偷不走会话令牌。
    //
    //    兼容说明：`APP`（原生 Kotlin）与 `webview-APP` 仍走 Bearer 通道，
    //    服务端 `authGuard` 是双通道（Cookie 或 Bearer 都认），两端不受影响。
    //    `config.withCredentials: true`（config.js:13）保证 Cookie 会随请求发出。
    //
    //    降级兜底：若因故内存里仍有 token（例如旧版本遗留），照旧注入，
    //    保证任何环境下都不会因为缺 Authorization 而全站 401。
    const legacyToken = getLegacyToken();
    if (legacyToken) {
      requestConfig.headers['Authorization'] = `Bearer ${legacyToken}`;
    }

    // 3. FormData 跳过所有加密逻辑
    if (isFormData) {
      return requestConfig
    }

    // 3.1 握手接口跳过加密
    if (requestConfig.url.includes(config.handshakeUrl)) {
      return requestConfig
    }

    // 3.2 跳过加密的接口列表
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
