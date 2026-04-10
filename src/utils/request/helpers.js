/**
 * 辅助函数
 */

/**
 * 判断是否为写操作
 */
export function isWriteOperation(method) {
  return ['post', 'put', 'delete', 'patch'].includes(method?.toLowerCase())
}

/**
 * 判断是否需要加密
 */
export function needsEncryption(method) {
  return isWriteOperation(method)
}

/**
 * 深拷贝
 */
export function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj))
}

/**
 * 防抖
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 节流
 */
export function throttle(fn, delay = 300) {
  let last = 0
  return function (...args) {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn.apply(this, args)
    }
  }
}

/**
 * 睡眠函数
 */
export function sleep(ms = 1000) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 判断对象是否为空
 */
export function isEmpty(obj) {
  if (obj === null || obj === undefined) return true
  if (typeof obj === 'string') return obj.trim() === ''
  if (Array.isArray(obj)) return obj.length === 0
  if (typeof obj === 'object') return Object.keys(obj).length === 0
  return false
}

/**
 * 简易日志
 */
export const logger = {
  info: (...args) => console.log('[INFO]', ...args),
  warn: (...args) => console.warn('[WARN]', ...args),
  error: (...args) => console.error('[ERROR]', ...args),
  debug: (...args) => {
    if (import.meta.env.DEV) console.log('[DEBUG]', ...args)
  },
}

export default {
  isWriteOperation,
  needsEncryption,
  deepClone,
  debounce,
  throttle,
  sleep,
  isEmpty,
  logger,
}
