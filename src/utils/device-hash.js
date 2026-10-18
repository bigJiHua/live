/**
 * 浏览器指纹生成工具
 * 使用 Canvas 生成唯一的设备指纹
 *
 * ## 安全整改 P2-16（2026-10-08）
 * 1. **hash 算法从 djb2 换成 SHA-256 + 盐**：
 *    djb2 输出是 36 进制短串（约 6~7 字符），特征串结构固定（`canvas|分辨率|时区|...`），
 *    攻击者可离线批量枚举常见浏览器配置反推出指纹（彩虹表），
 *    进而**伪造**任意目标用户的 `x-fingerprint-hash`。
 *    SHA-256 + 应用级盐让枚举成本不可行。盐在前端代码中公开 ——
 *    它不是秘密，防的是批量反推而非伪造请求本身（伪造由 nonce/签名类措施管）。
 * 2. **失败降级不再返回 'unknown'**：
 *    旧逻辑在 Canvas 被禁用/异常时返回固定值 'unknown'，
 *    所有失败用户**共享同一指纹** ⇒ 风控会把他们聚合成"同一台设备"（误杀放大器）。
 *    现改为失败时生成**会话级随机指纹**（crypto.getRandomValues，存 sessionStorage）：
 *    - 会话内稳定（同会话多次请求指纹一致，风控可关联）
 *    - 用户之间互不相同（不再聚合误杀）
 *    - 与真实设备指纹可区分（`fp-` 前缀），服务端如需可单独统计降级率
 */

import CryptoJS from 'crypto-js'

// 存储 fingerprint，避免重复计算
let cachedFingerprint = null

/** 应用级盐（见文件头说明 1）；`import.meta.env?.`：非 vite 环境（单测）下 env 为 undefined */
const FP_SALT = import.meta.env?.VITE_FP_SALT || 'live-fp-salt-v1'

/** 会话级随机指纹的 sessionStorage 键 */
const SESSION_FP_KEY = '__fp_session_fallback'

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

    // 计算 hash（P2-16：SHA-256 + 盐）
    const hash = sha256Salted(dataURL)

    return {
      dataURL,
      hash,
      raw: dataURL
    }
  } catch (e) {
    console.error('生成 Canvas 指纹失败:', e)
    return {
      dataURL: '',
      hash: sessionFallbackFingerprint(), // P2-16：会话随机指纹，不再共享 'unknown'
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

    // 生成综合 hash（P2-16：SHA-256 + 盐）
    // 🐛 2026-10-08 复审修复：djb2→SHA-256 替换时遗漏了 features 的序列化语句，
    //   旧变量名 fingerprintStr 悬空 ⇒ 每次调用抛 ReferenceError 被下方 catch 吞掉，
    //   所有用户永久走随机降级指纹（风控关联失效）。现提取为可独立单测的纯函数。
    const fingerprintStr = serializeFeatures(features)
    const fingerprintHash = sha256Salted(fingerprintStr)

    cachedFingerprint = {
      hash: fingerprintHash,
      features,
      canvas: canvas
    }

    return cachedFingerprint
  } catch (e) {
    console.error('生成浏览器指纹失败:', e)
    const fallback = sessionFallbackFingerprint() // P2-16：见文件头说明 2
    return {
      hash: fallback,
      features: {},
      canvas: { hash: fallback }
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
 * SHA-256 + 盐（安全整改 P2-16）
 *
 * 替代上面的 djb2：djb2 输出空间太小且可离线枚举反推（见文件头说明 1）。
 * 保留原函数仅因 `hashCode(dataURL)` 曾被外部脚本/调试用法引用过 ——
 * 实际上本模块内部已全部改用 sha256Salted，此函数可在后续版本删除。
 *
 * 输出取前 32 个十六进制字符（128 bit），足够指纹用途且日志友好。
 */
/**
 * 特征序列化（P2-16 复审补充，2026-10-08）
 *
 * 稳定序列化：键按插入顺序（features 构造顺序固定）→ JSON。
 * 提取为纯函数便于单测 —— 之前 djb2→SHA-256 替换时漏掉序列化语句，
 * 导致 fingerprintStr 悬空（ReferenceError 被降级 catch 吞掉），教训：
 * **替换算法时输出路径必须有一条端到端测试，不能只测降级路径。**
 * @param {Record<string, unknown>} features
 * @returns {string}
 */
export function serializeFeatures(features) {
  return JSON.stringify(features)
}

function sha256Salted(str) {
  const raw = CryptoJS.SHA256(`${FP_SALT}|${str}`).toString(CryptoJS.enc.Hex)
  return raw.slice(0, 32)
}

/**
 * 生成会话级随机降级指纹（安全整改 P2-16，见文件头说明 2）
 *
 * Canvas 被禁 / 特征采集异常时使用：随机但会话内稳定，用户间互不相同。
 * `fp-` 前缀便于服务端区分「真实指纹」与「降级指纹」。
 */
function sessionFallbackFingerprint() {
  try {
    const existing = sessionStorage.getItem(SESSION_FP_KEY)
    if (existing) return existing
  } catch {
    /* sessionStorage 不可用（隐私模式等）⇒ 每次生成新的，至少不共享 */
  }

  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  const rand = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  const fp = `fp-${rand}`

  try {
    sessionStorage.setItem(SESSION_FP_KEY, fp)
  } catch {
    /* 存不进去就让它每次变化 —— 仍优于所有用户共享 'unknown' */
  }
  return fp
}

/**
 * 清除缓存的指纹
 */
export function clearFingerprintCache() {
  cachedFingerprint = null
}
