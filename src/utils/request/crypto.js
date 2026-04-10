/**
 * 加密工具
 * 封装 AES 加密逻辑
 */

import { aesUtil } from '../aes'

/**
 * 加密数据
 * @param {Object} data - 要加密的数据
 * @param {string} key - AES 密钥
 * @returns {string} 加密后的数据
 */
export function encrypt(data, key) {
  if (!key) return data
  
  const payload = typeof data === 'string' ? data : JSON.stringify(data)
  return aesUtil.encrypt(payload, key)
}

/**
 * 解密数据
 * @param {string} encryptedData - 加密的数据
 * @param {string} key - AES 密钥
 * @returns {Object} 解密后的数据
 */
export function decrypt(encryptedData, key) {
  if (!key) return encryptedData
  
  try {
    const decrypted = aesUtil.decrypt(encryptedData, key)
    return JSON.parse(decrypted)
  } catch (e) {
    console.error('[Crypto] Decrypt Failed:', e)
    return encryptedData
  }
}

export default {
  encrypt,
  decrypt,
}
