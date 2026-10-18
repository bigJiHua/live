/**
 * PIN 码验证管理器
 * 全局单例，处理 PIN 验证弹窗的显示/隐藏、请求重发等逻辑
 */

import config from './config'
import { refreshVolatileSecurityHeaders } from '../securityHeaders'
// 安全整改 P2-16：PIN 重发通道自带 8401 一次性自愈
// （这两处走裸全局 axios，没有响应拦截器 —— 8401 会被当成普通响应）
import { axiosWithNonceRetry } from './nonceRetry'

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
let verifyContext = null
let lastSuccessAt = 0 // 上次 PIN 验证成功时间戳（防并发 8303 竞态）

const ROUTE_VERIFY_ACTION = 'route_verify'

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
export function requestPinVerify(originalRequest, verifyData = {}, skipCooldown = false) {
  const actionType = verifyData?.action_type || verifyData?.actionType
  const isRouteVerify = actionType === ROUTE_VERIFY_ACTION
  const currentIsRouteVerify = verifyContext?.action_type === ROUTE_VERIFY_ACTION

  // 冷却窗口保护：验证成功后 30 秒内再收到 8303 → 静默重发，不再弹窗
  const cooldownMs = 30000
  if (!skipCooldown && !isVerifying && lastSuccessAt > 0 && (Date.now() - lastSuccessAt < cooldownMs)) {
    return new Promise((resolve, reject) => {
      const retryConfig = { ...originalRequest }
      delete retryConfig.__pinVerify
      // 重发前刷新时间戳与一次性随机数：
      // 时间戳可能已超出后端 ±2 分钟窗口；nonce 复用会被判定为重放而直接 400。
      refreshVolatileSecurityHeaders(retryConfig.headers)
      // P2-16：改用自带 8401 自愈的请求 —— 裸 axios 下重发被判重放时
      // 调用方拿到 {code:8401} 会当成普通成功（与 8303 误判同类问题）。
      axiosWithNonceRetry(retryConfig)
        .then(res => {
          const data = res.data || res
          // ⚠ 这里用的是裸 axios，没有响应拦截器：后端再回 8303 也会被当成普通成功 resolve，
          // 调用方拿到 {code:8303} 却以为成功（页面静默空白 / 或误渲染）。
          // 重发仍被拦截时，必须回到正常弹窗流程，不能再走冷却捷径。
          if (isPinResponse(data)) {
            requestPinVerify(originalRequest, verifyData, true).then(resolve, reject)
          } else {
            resolve(data)
          }
        })
        .catch(err => reject(err))
    })
  }

  if (isVerifying && currentIsRouteVerify !== isRouteVerify) {
    return Promise.reject(new Error('已有 PIN 验证正在进行'))
  }

  if (isRouteVerify && pendingRequests.length > 0) {
    const hasSameChallenge = pendingRequests.some(req =>
      req.__pinVerify?.action_type === ROUTE_VERIFY_ACTION &&
      req.__pinVerify?.challengeId === verifyData.challengeId
    )
    if (!hasSameChallenge) {
      return Promise.reject(new Error('已有风险操作正在验证'))
    }

    if (isVerifying) {
      return new Promise((resolve, reject) => {
        if (pinResolve) pinResolve.__extraResolvers.push({ resolve, reject })
      })
    }
  }

  pendingRequests.push({
    ...originalRequest,
    __pinVerify: verifyData,
  })

  if (isRouteVerify && !verifyContext) {
    verifyContext = verifyData
  }

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
 * @param {string|object} pin - 6位 PIN 码字符串；或 { pin: <6位字符串|RSA密文数组> } 对象
 */
export async function submitPin(pin) {
  if (!pinDialogInstance) return

  // 动态导入避免循环依赖
  const { securityApi } = await import('@/utils/api/security')

  try {
    const isRouteVerify = verifyContext?.action_type === ROUTE_VERIFY_ACTION
    const pinValue = typeof pin === 'object' && pin !== null ? pin.pin : pin
    const payload = isRouteVerify
      ? {
          pin: pinValue,
          challengeId: verifyContext.challengeId,
          requestUrl: verifyContext.requestUrl,
          method: verifyContext.method,
        }
      : { pin: pinValue }

    const res = isRouteVerify
      ? await securityApi.verifyRoutePin(payload)
      : await securityApi.verifyPin(payload)

    // 🛡️ 只认 8301（成功码），不认 status===200：
    //    后端 verifyPin/verifyRoutePin 的失败分支也是 {code:8302|8304, status:200}
    //    （HTTP 200 + body 业务码）。旧判据 `code===8301 || status===200` 会把
    //    PIN 输错/锁定误判为成功（目前靠响应拦截器 handlePinResponse 先行拦截才未触发，
    //    属脆弱设计 —— 一旦后端调整状态码即失效）。见 api.md §8.6 F-F。
    if (res.code === PIN_CODE.SUCCESS) {
      onPinSuccess(res.data || res)
    } else if (res.code === PIN_CODE.ERROR) {
      pinDialogInstance.setError(res.message || 'PIN 码错误')
    } else if (res.code === PIN_CODE.LOCKED) {
      onPinLocked(res.message)
    } else {
      pinDialogInstance.setError(res.message || '验证失败')
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
async function onPinSuccess(verifyResult = {}) {
  if (pinDialogInstance) pinDialogInstance.hide()

  // 记录验证成功时间，启动冷却窗口保护
  lastSuccessAt = Date.now()

  if (isRetrying) return
  isRetrying = true

  const requests = [...pendingRequests]
  pendingRequests = []
  isVerifying = false
  verifyContext = null

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

  // 并发重发请求（🔄 2026-10-12 优化）：原为串行 for-await，
  // N 个挂起请求耗时线性叠加（2Mbps 弱网下页面解冻慢）。
  // 改为限流并发：每次 attempt 都会经 refreshVolatileSecurityHeaders 换新 nonce，
  // 并发安全；上限 3 避免打爆后端连接与 PIN 风控窗口。
  const RETRY_CONCURRENCY = 3
  const queue = [...requests]
  const worker = async () => {
    while (queue.length > 0) {
      const req = queue.shift()
      try {
        const pinVerify = req.__pinVerify
        delete req.__pinVerify

        if (pinVerify?.action_type === ROUTE_VERIFY_ACTION && verifyResult.token) {
          req.headers = {
            ...(req.headers || {}),
            [verifyResult.headerName || 'x-route-verify-token']: verifyResult.token,
          }
        }

        // 重发前刷新时间戳与一次性随机数（同上：避免超窗 / 被判定为重放）
        refreshVolatileSecurityHeaders(req.headers)

        // P2-16：同样改用自带 8401 自愈的请求
        await axiosWithNonceRetry(req)
      } catch (e) {
        console.error('[PIN] Retry request failed:', e)
      }
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(RETRY_CONCURRENCY, queue.length) }, worker)
  )

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
  verifyContext = null

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
  verifyContext = null
  // 取消 ≠ 已验证：清掉冷却窗口，否则随后 30 秒内的 8303 会走"静默重发"捷径，
  // 用户点了取消却看到数据照常加载（且没有任何提示）。清掉后后续请求会正常弹窗。
  lastSuccessAt = 0

  if (pinReject) {
    pinReject(new Error('PIN 验证已取消'))
    pinReject.__extraResolvers?.forEach(({ reject }) => reject(new Error('PIN 验证已取消')))
    pinResolve = null
    pinReject = null
  }
}

/**
 * 主动弹出 PIN 验证窗
 * 用于「没有挂起请求」的场景（如点击"锁定系统"后立即要求解锁）。
 * 与 requestPinVerify 的区别：不入 pendingRequests、不设 pinResolve，
 * 纯解锁用途；用户输入正确 PIN 后由 submitPin → onPinSuccess 关窗 + 解锁。
 */
export function showPinDialog() {
  if (pinDialogInstance) pinDialogInstance.show()
  return Promise.resolve()
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
  showPinDialog,
  isPinResponse,
  PIN_CODE,
}
