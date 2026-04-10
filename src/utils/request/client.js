/**
 * 客户端上下文
 * 负责收集设备指纹、网络信息等客户端数据
 */

import { UAParser } from 'ua-parser-js'
import { generateBrowserFingerprint } from '../device-hash'
import { getFullNetworkInfo } from '../network'

// 缓存
let cachedContext = null
let initPromise = null

/**
 * 获取 UA 解析结果
 */
function getUAInfo() {
  const ua = new UAParser().getResult()
  return {
    userAgent: ua.ua,
    osInfo: `${ua.os.name} ${ua.os.version}`,
    browserInfo: `${ua.browser.name} ${ua.browser.version}`,
    deviceModel: ua.device.model || 'Unknown',
    deviceType: ua.device.type || 'desktop',
  }
}

/**
 * 获取视口信息
 */
function getViewportInfo() {
  return {
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    pixelRatio: window.devicePixelRatio,
  }
}

/**
 * 获取浏览器信息
 */
function getBrowserInfo() {
  return {
    language: navigator.language,
    languages: navigator.languages?.join(',') || '',
  }
}

/**
 * 收集完整的客户端上下文
 */
async function collectClientData() {
  const [uaInfo, netInfo] = await Promise.all([
    Promise.resolve(getUAInfo()),
    getFullNetworkInfo().catch(() => ({})),
  ])
  
  const fp = generateBrowserFingerprint()
  const viewport = getViewportInfo()
  const browser = getBrowserInfo()

  return {
    // 网络信息
    loginIp: netInfo.external?.ip || 'unknown',
    loginLocation: [
      netInfo.external?.country || '',
      netInfo.external?.region || '',
      netInfo.external?.city || '',
    ].filter(Boolean).join('-'),
    loginIsp: netInfo.external?.isp || '',
    connectionType: netInfo.local?.connectionType,
    
    // UA 信息
    ...uaInfo,
    
    // 指纹
    fingerprint: fp.hash,
    
    // 视口
    ...viewport,
    
    // 浏览器
    ...browser,
  }
}

/**
 * 获取客户端上下文（单例）
 */
export async function getClientContext() {
  if (cachedContext) return cachedContext
  if (initPromise) return initPromise

  initPromise = (async () => {
    try {
      cachedContext = await collectClientData()
      return cachedContext
    } catch (e) {
      console.error('[ClientContext] Init Failed:', e)
      return {}
    }
  })()

  return initPromise
}

/**
 * 清除缓存
 */
export function clearClientCache() {
  cachedContext = null
  initPromise = null
}

export default { getClientContext, clearClientCache }
