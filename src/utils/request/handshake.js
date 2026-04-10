/**
 * 握手 & AES Key 管理
 * 处理与后端的安全握手和密钥管理
 */

import axios from 'axios'
import config from './config'

// 状态
let sessionAesKey = null
let handshakePromise = null

/**
 * 执行握手请求
 */
async function fetchHandshakeKey(fp, deviceData) {
  const now = Date.now()
  
  const response = await axios.get(`${config.baseURL}${config.handshakeUrl}`, {
    params: { fp },
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
      'x-client-timestamp': now,
      'x-fingerprint-hash': fp,
      'x-user-agent-custom': deviceData.userAgent,
      'x-device-model': deviceData.deviceModel,
      'x-os-name': deviceData.osInfo,
      'x-device-type': deviceData.deviceType,
    },
  })

  if (response.data?.status === 200) {
    return response.data.key
  }
  
  throw new Error('Handshake Failed')
}

/**
 * 执行握手（带锁，防止并发）
 */
async function doHandshake(fp, deviceData) {
  if (handshakePromise) return handshakePromise

  handshakePromise = (async () => {
    try {
      const key = await fetchHandshakeKey(fp, deviceData)
      
      // 存储到 sessionStorage
      sessionStorage.setItem(`${config.aesKeyPrefix}${fp}`, key)
      sessionAesKey = key
      
      return key
    } finally {
      handshakePromise = null
    }
  })()

  return handshakePromise
}

/**
 * 获取 AES Key（自动维护生命周期）
 */
export async function getAesKey(fp, deviceData) {
  // 1. 内存中已有
  if (sessionAesKey) return sessionAesKey
  
  // 2. sessionStorage 中读取
  const storedKey = sessionStorage.getItem(`${config.aesKeyPrefix}${fp}`)
  if (storedKey) {
    sessionAesKey = storedKey
    return sessionAesKey
  }
  
  // 3. 执行握手
  return doHandshake(fp, deviceData)
}

/**
 * 清除 Key
 */
export function clearAesKey() {
  sessionAesKey = null
  handshakePromise = null
}

/**
 * 检查是否有可用 Key
 */
export function hasAesKey() {
  return !!sessionAesKey
}

export default {
  getAesKey,
  clearAesKey,
  hasAesKey,
}
