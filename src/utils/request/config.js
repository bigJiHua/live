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

  /** 跳过加密的接口
   *  - /auth/lock-system、/data-manager/import/sql：后端直接读 req.body 顶层（明文约定）
   *  - /auth/refresh（🔄 2026-10-12 修复）：后端 refreshToken 读 req.body.token（顶层），
   *    而加密链路解密后业务体在 req.body.data.token ⇒ 加密必 400「Token 不能为空」
   *    （api.md §8.1 F-A 实测复现）。改走明文后后端原路径可用。
   *    token 本身已由服务端签名为 JWT，明文传输的机密性由 TLS 保证（与登录接口同级）。
   */
  skipEncryptionUrls: ['/auth/lock-system', '/data-manager/import/sql', '/auth/refresh'],
}

export default config
