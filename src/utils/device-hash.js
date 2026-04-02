/**
 * 浏览器指纹生成工具
 * 使用 Canvas 生成唯一的设备指纹
 */

// 存储 fingerprint，避免重复计算
let cachedFingerprint = null

/**
 * 生成 Canvas 指纹
 */
export function generateCanvasFingerprint() {
  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')

    // 设置 canvas 尺寸
    canvas.width = 200
    canvas.height = 50

    // 绘制文本
    ctx.textBaseline = 'top'
    ctx.font = '14px Arial'
    ctx.fillStyle = '#f60'
    ctx.fillRect(125, 1, 62, 20)

    ctx.fillStyle = '#069'
    ctx.fillText('Browser Fingerprint 🔐', 2, 15)

    // 绘制复杂图形
    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)'
    ctx.fillText('Fingerprint Test! 🎨', 4, 35)

    // 添加更多细节
    ctx.beginPath()
    ctx.arc(150, 25, 10, 0, 2 * Math.PI)
    ctx.fillStyle = '#ff0000'
    ctx.fill()

    ctx.beginPath()
    ctx.moveTo(170, 15)
    ctx.lineTo(180, 35)
    ctx.lineTo(160, 35)
    ctx.fillStyle = '#0000ff'
    ctx.fill()

    // 获取 base64 编码
    const dataURL = canvas.toDataURL()

    // 计算 hash
    const hash = hashCode(dataURL)

    return {
      dataURL,
      hash,
      raw: dataURL
    }
  } catch (e) {
    console.error('生成 Canvas 指纹失败:', e)
    return {
      dataURL: '',
      hash: 'unknown',
      raw: ''
    }
  }
}

/**
 * 生成综合浏览器指纹
 * 包含多个特征以增加唯一性
 */
export function generateBrowserFingerprint() {
  if (cachedFingerprint) {
    return cachedFingerprint
  }

  try {
    const canvas = generateCanvasFingerprint()

    // 收集浏览器特征
    const features = {
      // Canvas 指纹
      canvas: canvas.hash,

      // 屏幕
      screenResolution: `${window.screen.width}x${window.screen.height}`,
      screenColorDepth: window.screen.colorDepth,
      pixelRatio: window.devicePixelRatio,

      // 时区
      timezoneOffset: new Date().getTimezoneOffset(),
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,

      // 语言
      language: navigator.language,
      languages: navigator.languages ? navigator.languages.join(',') : '',

      // 平台
      platform: navigator.platform,

      // WebGL 指纹
      webgl: getWebGLFingerprint(),

      // 字体
      fonts: getAvailableFonts(),

      // 触摸支持
      touchSupport: 'ontouchstart' in window,

      // Cookie 支持
      cookiesEnabled: navigator.cookieEnabled,

      // Do Not Track
      doNotTrack: navigator.doNotTrack || window.doNotTrack || navigator.msDoNotTrack,

      // 存储支持
      localStorage: !!window.localStorage,
      sessionStorage: !!window.sessionStorage,

      // WebRTC
      webrtc: !!window.RTCPeerConnection,
    }

    // 生成综合 hash
    const fingerprintStr = Object.values(features)
      .filter(Boolean)
      .join('|')
    const fingerprintHash = hashCode(fingerprintStr)

    cachedFingerprint = {
      hash: fingerprintHash,
      features,
      canvas: canvas
    }

    return cachedFingerprint
  } catch (e) {
    console.error('生成浏览器指纹失败:', e)
    return {
      hash: 'unknown',
      features: {},
      canvas: { hash: 'unknown' }
    }
  }
}

/**
 * 获取 WebGL 指纹
 */
function getWebGLFingerprint() {
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl')

    if (!gl) return 'not-supported'

    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info')
    const vendor = debugInfo ? gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) : 'unknown'
    const renderer = debugInfo ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) : 'unknown'

    return `${vendor}|${renderer}`
  } catch (e) {
    return 'error'
  }
}

/**
 * 获取可用字体列表（简化版）
 */
function getAvailableFonts() {
  const testFonts = [
    'Arial', 'Helvetica', 'Times New Roman', 'Courier New', 'Verdana',
    'Georgia', 'Palatino', 'Garamond', 'Bookman', 'Comic Sans MS',
    'Trebuchet MS', 'Arial Black', 'Impact', 'Microsoft YaHei', 'SimSun',
    'SimHei', 'PingFang SC', 'Hiragino Sans GB'
  ]

  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    const available = []
    const testString = 'mmmmmmmmmmlli'
    const testSize = '72px'

    const baselineWidth = ctx.measureText(testString + testString).width

    for (const font of testFonts) {
      ctx.font = `${testSize} '${font}'`
      const width = ctx.measureText(testString + testString).width

      if (width !== baselineWidth) {
        available.push(font)
      }
    }

    return available.join(',')
  } catch (e) {
    return 'error'
  }
}

/**
 * 简单的 hash 函数（djb2 算法）
 */
function hashCode(str) {
  let hash = 5381
  let i = str.length

  while (i) {
    hash = (hash * 33) ^ str.charCodeAt(--i)
  }

  // 转为正数并转为 36 进制字符串
  return Math.abs(hash).toString(36)
}

/**
 * 清除缓存的指纹
 */
export function clearFingerprintCache() {
  cachedFingerprint = null
}
