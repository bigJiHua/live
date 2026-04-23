/**
 * 请求配置
 */

export const config = {
  /** API 基础路径 */
  baseURL: '/api/v1',
  
  /** 请求超时时间 (ms) */
  timeout: 15000,
  
  /** 携带 Cookie */
  withCredentials: true,
  
  /** Token Key */
  tokenKey: 'finance_token',
  
  /** AES Key 存储前缀 */
  aesKeyPrefix: 'aes_',
  
  /** 握手接口 */
  handshakeUrl: '/auth/handshake',
  
  /** 需要加密的方法 */
  encryptedMethods: ['post', 'put', 'delete'],

  /** 跳过加密的接口 */
  skipEncryptionUrls: ['/auth/lock-system'],
}

export default config
