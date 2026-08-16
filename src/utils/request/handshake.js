/**
 * 握手 & AES Key 管理
 * 处理与后端的安全握手和密钥管理
 */

import axios from 'axios'
import config from './config'

// 状态
let sessionAesKey = null
let sessionRsaPublicKey = null
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
    // 同时缓存 RSA 公钥（安全键盘字符级加密使用）
    if (response.data.rsaPublicKey) {
      sessionRsaPublicKey = response.data.rsaPublicKey
      sessionStorage.setItem(`${config.aesKeyPrefix}rsa_${fp}`, response.data.rsaPublicKey)
    }
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
  sessionRsaPublicKey = null
  handshakePromise = null
}

/**
 * 检查是否有可用 Key
 */
export function hasAesKey() {
  return !!sessionAesKey
}

/**
 * 获取 RSA 公钥（安全键盘字符级加密），自动触发/复用握手
 * @param {object} [ctx] - 可选 { fingerprint, ...deviceData }；未传时仅读缓存
 */
export async function getRsaPublicKey(ctx) {
  // 1. 内存中已有
  if (sessionRsaPublicKey) return sessionRsaPublicKey
  // 2. sessionStorage 中已有（本次会话握手缓存）
  if (typeof window !== "undefined") {
    const fp = window.sessionStorage.getItem("fp") || ""
    const stored = fp
      ? sessionStorage.getItem(`${config.aesKeyPrefix}rsa_${fp}`)
      : null
    if (stored) {
      sessionRsaPublicKey = stored
      return sessionRsaPublicKey
    }
  }
  // 3. 有上下文则主动触发一次握手（确保公钥在键盘输入前就绪）
  if (ctx?.fingerprint) {
    await doHandshake(ctx.fingerprint, ctx)
    return sessionRsaPublicKey || null
  }
  return sessionRsaPublicKey || null
}

export default {
  getAesKey,
  clearAesKey,
  hasAesKey,
  getRsaPublicKey,
}
