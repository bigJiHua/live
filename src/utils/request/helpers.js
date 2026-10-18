/**
 * 辅助函数
 */

import { showToast, showFailToast } from '@/utils/toast'

/**
 * 把「被 reject 出来的东西」统一成一个对象，**保证 `error.message` 永远是后端文案**。
 *
 * 为什么必须有它：拦截器 reject 出去的可能是两种完全不同的东西 ——
 *  ① **后端业务体** `{status:400, message:'非法请求！'}`（`core.js` 开了 `validateStatus:()=>true`，
 *     HTTP 400 也走「成功」分支，所以 reject 的是响应体）；
 *  ② **axios 的 AxiosError**（网络异常 / 超时 / `fileRequest.js` 这种没开 validateStatus 的实例），
 *     它的 `.message` 是**英文传输层文案**：`Request failed with status code 400`。
 *
 * 而页面层几乎清一色写 `catch (e) { showToast(e.message || '加载失败') }`
 * ⇒ ②直接透传出去，用户看到的就是 "Request failed with status code 400"，
 * 后端明明给了「非法请求！」却被丢掉。
 *
 * 这里统一成同一形状：**保留原始字段**（`code`/`ismessage`/`data`… 调用点还在用）+
 * 覆盖 `status` / `message`，并优先取 `response.data.message`。
 *
 * ⭐ 2026-09-21 起**额外产出 `kind`**，用来决定弹窗颜色（见 [apiErrorToast]）：
 *   · `'network'`  —— **根本没拿到响应**（本地连不上 / 超时 / DNS / 断网）⇒ **灰**
 *   · `'server'`   —— 拿到了响应但 HTTP **5xx**（远端 API 出故障）⇒ **灰**
 *   · `'business'` —— 其余（4xx / 业务体 status≠200），后端明确告诉了我们原因 ⇒ **红**
 */
export function normalizeApiError(input, fallback = '请求失败') {
  // ① axios 错误对象（有 response 或 config）
  if (input && (input.isAxiosError || input.response || input.config)) {
    const body = input.response?.data
    const bodyObj = body && typeof body === 'object' ? body : null
    const httpStatus = input.response?.status
    // ⚠ 判据是「**有没有 response**」，不是 message 长什么样 ——
    //   连不上时 axios 的 message 是 'Network Error'，但代理/超时场景文案五花八门，
    //   只有 response 的有无是可靠的。
    const kind = !input.response ? 'network' : httpStatus >= 500 ? 'server' : 'business'
    const message =
      (typeof bodyObj?.message === 'string' && bodyObj.message.trim()) ||
      (input.response ? `请求失败（${httpStatus}）` : '网络异常，请稍后重试')
    return {
      ...(bodyObj || {}),
      status: bodyObj?.status ?? httpStatus ?? -1,
      message,
      data: bodyObj?.data ?? null,
      kind,
    }
  }

  // ② 后端业务体 / 其它
  const obj = input && typeof input === 'object' ? input : {}
  const message =
    (typeof obj.message === 'string' && obj.message.trim()) ||
    (typeof input === 'string' && input.trim()) ||
    fallback
  const status = obj.status ?? -1
  // 拿不到 status（本地 `throw new Error(...)` 之类）⇒ 归 'business'（红）。
  // 宁可说「失败」也不要误报成「网络问题」。
  const kind = status === -1 ? 'business' : status >= 500 ? 'server' : 'business'
  return { ...obj, status, message, kind }
}

/**
 * 把「被 reject 出来的东西」**直接弹成颜色正确的消息弹窗** —— 错误路径的唯一入口。
 *
 * 五色语义（负责人 2026-09-21）：
 *   🟢 绿 200/成功 ｜ 🔴 红 API 往返后业务失败 ｜ 🟡 黄 警告（显式指定）
 *   ⚪ 灰 拿不到响应（本地连不上 / 远端 5xx）｜ 🔵 蓝 普通信息
 *
 * 所以 `catch` 里**不要再手写** `showToast(err.message)` —— 那会落到「蓝＝普通信息」，
 * 语义是错的。统一改成：
 *
 * ```js
 * try { await api.save(v) ; showSuccessToast('保存成功') }
 * catch (e) { apiErrorToast(e, '保存失败') }
 * ```
 *
 * ⚠ **黄色（warning）不走这里** —— 它按约定是「前端显式指定」的语义（如限流、额度不足），
 *   知道是警告的场景直接 `showToast({ type: 'warning', message })`。
 *
 * @returns {object} 归一化后的错误对象（需要 `status`/`code` 时可直接用返回值）
 */
export function apiErrorToast(input, fallback = '请求失败') {
  const err = normalizeApiError(input, fallback)
  if (err.kind === 'network' || err.kind === 'server') {
    // 灰：本地连不上 API，或远端 API 出了故障
    showToast({ type: 'info', message: err.message })
  } else {
    // 红：经过 API 往返、后端明确返回的失败
    showFailToast(err.message)
  }
  return err
}

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
