/**
 * PIN 码验证管理器
 * 全局单例，处理 PIN 验证弹窗的显示/隐藏、请求重发等逻辑
 */

import axios from 'axios'
import config from './config'

// PIN 码状态码
export const PIN_CODE = {
  NEED_VERIFY: 8303,
  SUCCESS: 8301,
  ERROR: 8302,
  LOCKED: 8304,
}

// 单例状态
let pinResolve = null
let pinReject = null
let isVerifying = false
let pendingRequests = []
let isRetrying = false
let pinDialogInstance = null
let forceLoginFn = null // 由 response.js 注入
let isPageLoad = false // 是否是页面首次加载时触发的验证

/**
 * 注册 PIN 弹窗实例
 */
export function registerPinDialog(instance) {
  pinDialogInstance = instance
}

/**
 * 注销 PIN 弹窗实例
 */
export function unregisterPinDialog() {
  pinDialogInstance = null
}

/**
 * 注册 forceLogin 函数（由 response.js 注入，避免循环依赖）
 */
export function registerForceLogin(fn) {
  forceLoginFn = fn
}

/**
 * 检查 app 根元素下是否只有 PIN 弹窗（无实际页面内容）
 * 如果是，说明是页面首次加载时触发的验证
 */
function checkAppOnlyHasPinOverlay() {
  const app = document.getElementById('app')
  if (!app) return false

  const children = Array.from(app.children)
  if (children.length !== 2) return false

  const hasVanOverlay = children.some(el =>
    el.classList.contains('van-overlay')
  )
  const hasPinKeyboard = children.some(el =>
    el.classList.contains('pin-keyboard-overlay')
  )

  return hasVanOverlay && hasPinKeyboard
}

/**
 * 请求 PIN 验证
 * @param {object} originalRequest - 被拦截的原始请求配置
 * @returns {Promise} 验证成功 resolve，失败/锁定 reject
 */
export function requestPinVerify(originalRequest) {
  pendingRequests.push(originalRequest)

  // 检测是否是页面首次加载时触发的验证
  // 通过检查 app 根元素下是否只有 PIN 弹窗来判断
  const isPageLoadCheck = checkAppOnlyHasPinOverlay()
  if (pendingRequests.length === 1 && isPageLoadCheck) {
    isPageLoad = true
  } else {
    isPageLoad = false
  }

  // 已在验证中，复用当前等待
  if (isVerifying) {
    return new Promise((resolve, reject) => {
      if (pinResolve) pinResolve.__extraResolvers.push({ resolve, reject })
    })
  }

  isVerifying = true

  return new Promise((resolve, reject) => {
    pinResolve = resolve
    pinReject = reject
    pinResolve.__extraResolvers = []

    if (pinDialogInstance) {
      pinDialogInstance.show()
    }
  })
}

/**
 * 提交 PIN 码验证
 * @param {string} pin - 6位 PIN 码
 */
export async function submitPin(pin) {
  if (!pinDialogInstance) return

  // 动态导入避免循环依赖
  const { securityApi } = await import('@/utils/api/security')

  try {
    const res = await securityApi.verifyPin({ pin })
    const data = res.data || res

    if (data.code === PIN_CODE.SUCCESS || data.status === 200) {
      onPinSuccess()
    } else if (data.code === PIN_CODE.ERROR) {
      pinDialogInstance.setError(data.message || 'PIN 码错误')
    } else if (data.code === PIN_CODE.LOCKED) {
      onPinLocked(data.message)
    } else {
      pinDialogInstance.setError(data.message || '验证失败')
    }
  } catch (err) {
    const resData = err.response?.data || err
    if (resData.code === PIN_CODE.ERROR) {
      pinDialogInstance.setError(resData.message || 'PIN 码错误')
    } else if (resData.code === PIN_CODE.LOCKED) {
      onPinLocked(resData.message)
    } else {
      pinDialogInstance.setError(resData.message || '验证失败')
    }
  }
}

/**
 * PIN 验证成功 → 关闭弹窗，重发所有被拦截的请求
 */
async function onPinSuccess() {
  if (pinDialogInstance) pinDialogInstance.hide()

  if (isRetrying) return
  isRetrying = true

  const requests = [...pendingRequests]
  pendingRequests = []
  isVerifying = false

  // resolve 所有等待的 Promise
  if (pinResolve) {
    pinResolve()
    pinResolve.__extraResolvers.forEach(({ resolve }) => resolve())
    pinResolve = null
    pinReject = null
  }

  // 如果是页面首次加载时触发的验证，验证成功后刷新页面
  // 避免页面内容被弹窗遮挡导致空白
  // 在验证成功时检查 app 下是否只有弹窗（此时弹窗已显示）
  const onlyHasPin = checkAppOnlyHasPinOverlay()
  if ((isPageLoad || onlyHasPin) && requests.length > 0) {
    isPageLoad = false
    isRetrying = false
    window.location.reload()
    return
  }

  // 依次重发请求（串行避免并发风暴）
  for (const req of requests) {
    try {
      await axios(req)
    } catch (e) {
      console.error('[PIN] Retry request failed:', e)
    }
  }

  isRetrying = false
}

/**
 * PIN 锁定 → 退出登录
 */
function onPinLocked(message) {
  if (pinDialogInstance) pinDialogInstance.hide()

  isVerifying = false
  pendingRequests = []
  isRetrying = false

  if (pinReject) {
    pinReject(new Error(message || 'PIN 已锁定'))
    pinReject.__extraResolvers?.forEach(({ reject }) => reject(new Error('PIN 已锁定')))
    pinResolve = null
    pinReject = null
  }

  // 退出登录
  if (forceLoginFn) {
    forceLoginFn(message || 'PIN 已锁定，请重新登录')
  } else {
    sessionStorage.clear()
    localStorage.removeItem(config.tokenKey)
    window.location.href = '/login'
  }
}

/**
 * 取消 PIN 验证（用户关闭弹窗）
 */
export function cancelPinVerify() {
  isVerifying = false
  pendingRequests = []
  isRetrying = false

  if (pinReject) {
    pinReject(new Error('PIN 验证已取消'))
    pinReject.__extraResolvers?.forEach(({ reject }) => reject(new Error('PIN 验证已取消')))
    pinResolve = null
    pinReject = null
  }
}

/**
 * 检查是否为 PIN 码相关响应
 */
export function isPinResponse(data) {
  return data && (
    data.code === PIN_CODE.NEED_VERIFY ||
    data.code === PIN_CODE.SUCCESS ||
    data.code === PIN_CODE.ERROR ||
    data.code === PIN_CODE.LOCKED
  )
}

export default {
  registerPinDialog,
  unregisterPinDialog,
  registerForceLogin,
  requestPinVerify,
  submitPin,
  cancelPinVerify,
  isPinResponse,
  PIN_CODE,
}
