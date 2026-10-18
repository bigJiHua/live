/**
 * 自研消息弹窗（Toast）—— 全站替代 Vant 的 `showToast` 系列。
 *
 * ## 为什么不用 Vant 的 Toast
 * 负责人 2026-09-20 给的设计稿是「浅色 tint 卡片 + accent 色圆环图标 + **关闭按钮** +
 * **长信息（标题 + 描述）**」。Vant 的 Toast：
 *   · 没有关闭按钮（只有 `closeOnClick` 整块点击关闭）；
 *   · 没有「标题 + 描述」的结构化两行布局；
 *   · 图标只有 success / fail 两个（warning 得靠 CSS `::before` 补字形）。
 * 三点都补不了 ⇒ 改为自研，样式在 `assets/css/style.css` 的 `.app-toast*`。
 *
 * ## API 与 Vant 保持兼容
 * `showToast / showSuccessToast / showFailToast / showLoadingToast / closeToast /
 *  setToastDefaultOptions` 的签名与选项名都沿用 Vant，所以**调用点一行都不用改**
 * （只改了 import 来源）。
 *
 * 支持的选项：`message` / `title`（有值即走长信息形态）/ `type` / `position`
 * / `duration`（0 = 常驻）/ `onClose` / `forbidClick` / `closeOnClick` / `closable`
 * / `allowHtml`。
 *
 * ## 类型 → 视觉状态
 * ⭐ **五色语义（负责人 2026-09-21 定的口径，取代 09-20 的「info 保持灰」）**：
 *
 * | type | 状态 | 图标 | 何时出现 |
 * |------|------|------|----------|
 * | `success` | success（绿） | ✓ | HTTP 200 / 业务成功 |
 * | `fail` / `error` | fail（红） | ✕ | **经过 API 往返**后业务失败（4xx / 业务体 status≠200） |
 * | `warning` | warning（黄） | 三角 ! | API 往返后的「警告」级（前端显式指定，如 429 限流） |
 * | `info` | info（灰） | 断线 | **拿不到响应**：本地连不上 API，或远端 5xx 故障 |
 * | `text`（默认）/ `html` | notice（蓝） | 无 | **普通信息**（非状态、非错误） |
 * | `loading` | notice（蓝） | 转圈 | 「进行中」，常驻，需 `closeToast()` |
 *
 * ⚠ `type:'info'` 现在**专指灰（系统/网络层）**；普通信息请用默认的纯文字 `showToast`。
 *   改造前 web 侧显式传 `type:'info'` 的调用点有 **0 处**，所以这次语义收窄没有破坏面。
 * ⚠ 错误路径别手写 `showToast(err.message)` —— 那样会落到「蓝＝普通信息」，是错的。
 *   统一走 `utils/request/helpers.js` 的 **`apiErrorToast(err, fallback)`**，
 *   它按「有没有拿到 response / 是不是 5xx」自动分到 红 或 灰。
 * ⚠ 灰/红/黄/绿都带图标与关闭按钮；**蓝（纯文字）不带**（轻量提示，2 秒即走）。
 * ⚠ 单例：新弹窗会顶掉正在显示的那个（与 Vant 一致），被顶掉的那个会触发它自己的 `onClose`。
 */

const ICON_SUCCESS =
  '<svg viewBox="0 0 12 12" fill="none"><polyline points="2,6.2 4.8,9 10,3.2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>'
const ICON_FAIL =
  '<svg viewBox="0 0 12 12" fill="none"><line x1="2.6" y1="2.6" x2="9.4" y2="9.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><line x1="9.4" y1="2.6" x2="2.6" y2="9.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>'
const ICON_WARNING =
  '<svg viewBox="0 0 12 12" fill="none"><path d="M6 2L11 10H1L6 2Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><line x1="6" y1="4.5" x2="6" y2="7.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="6" cy="8.8" r="0.7" fill="currentColor"/></svg>'
const ICON_LOADING =
  '<svg viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="4.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-dasharray="20.7 6.9" transform="rotate(-90 6 6)"/></svg>'
/** 灰（系统/网络层）：wifi 两段弧 + 圆点 + 一条斜杠 = 「断线」 */
const ICON_NETWORK =
  '<svg viewBox="0 0 12 12" fill="none"><path d="M1.4 4.6A6.4 6.4 0 0 1 10.6 4.6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><path d="M3.6 7.1A3.4 3.4 0 0 1 8.4 7.1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="6" cy="9.4" r="0.9" fill="currentColor"/><line x1="1.6" y1="10.4" x2="10.4" y2="1.6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>'
const ICON_CLOSE =
  '<svg viewBox="0 0 10 10" fill="none"><line x1="1.5" y1="1.5" x2="8.5" y2="8.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><line x1="8.5" y1="1.5" x2="1.5" y2="8.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>'

/**
 * type → CSS 状态类后缀。
 *
 * ⭐ 2026-09-21 五色语义：`text`/`html`/`loading` → **notice（蓝，普通信息）**；
 * `info` → **info（灰，系统/网络层）**。这两档别再合并。
 */
const STATE_OF = {
  text: 'notice',
  html: 'notice',
  loading: 'notice',
  info: 'info',
  success: 'success',
  fail: 'fail',
  error: 'fail',
  warning: 'warning',
}

/**
 * type → 图标 SVG。
 * ⚠ `info`（灰）用**断线**字形而不是「信息 i」—— 它现在专指网络/系统故障，是错误态。
 */
const ICON_OF = {
  success: ICON_SUCCESS,
  fail: ICON_FAIL,
  error: ICON_FAIL,
  warning: ICON_WARNING,
  loading: ICON_LOADING,
  info: ICON_NETWORK,
}

/** 只有这几种类型渲染圆环图标；纯文字 / html（蓝）不带图标 */
const WITH_ICON = new Set(['success', 'fail', 'error', 'warning', 'loading', 'info'])

/**
 * 默认是否显示关闭按钮。
 * ⚠ 只有**带状态图标**的提示（成功 / 失败 / 警告 / 系统灰）才给 ×：
 *   纯文字提示是全站最高频的一类（470+ 处）、大多 2 秒就消失，挂个 × 是纯噪音；
 *   loading 由代码 `closeToast()` 收尾，也不需要用户点。
 *   调用点显式传 `closable: true` 仍可强制显示。
 */
const WITH_CLOSE = new Set(['success', 'fail', 'error', 'warning', 'info'])


const HIDE_MS = 200

const DEFAULTS = {
  type: 'text',
  position: 'top',
  // ⚠ 这只是**最后兜底**。真实默认时长走 `DURATION_OF_STATE` 的阶梯（见下），
  //   不再「所有状态统一 2 秒」。
  duration: 2000,
  forbidClick: false,
  closeOnClick: false,
}

/**
 * 各状态的**默认存活时长**（阶梯，不统一）—— 负责人 2026-09-21 要求。
 *
 * 原则：越「扫一眼就够」的越短，越需要用户读 / 做点什么的越长。
 *   · 成功 → **最短 1.5s**：只是确认「刚才那下成了」，挂久了纯挡视线；
 *   · 普通信息（蓝）→ 2s；
 *   · 警告（黄）→ 2.5s：要看清「什么快不行了」；
 *   · 失败（红）→ 3s：要读后端给的原因；
 *   · 系统/网络（灰）→ **最长 3.5s**：用户往往要据此决定「重试还是去查网络」；
 *   · loading → 0（常驻，由代码 `closeToast()` 收尾）。
 *
 * ⚠ 长信息（带 `title`）额外 **+1.5s** —— 有两行要读。
 * ⚠ 调用点**显式传了 `duration`** 就完全听调用点的，这套只是默认值。
 */
const DURATION_OF_STATE = {
  success: 1500,
  notice: 2000,
  warning: 2500,
  fail: 3000,
  info: 3500,
}
const LONG_BONUS = 1500

let defaults = { ...DEFAULTS }
let rootEl = null
let blockerEl = null
/** 当前显示的弹窗（单例） */
let current = null

function ensureRoot(position) {
  if (!rootEl) {
    rootEl = document.createElement('div')
    document.body.appendChild(rootEl)
  }
  rootEl.className = 'app-toast-root app-toast-root--' + position
  return rootEl
}

function ensureBlocker() {
  if (!blockerEl) {
    blockerEl = document.createElement('div')
    blockerEl.className = 'app-toast-blocker'
    blockerEl.style.display = 'none'
    document.body.appendChild(blockerEl)
  }
  return blockerEl
}

function setBlocking(on) {
  if (on) ensureBlocker().style.display = 'block'
  else if (blockerEl) blockerEl.style.display = 'none'
}

function normalize(input, forcedType) {
  const raw =
    typeof input === 'string' || typeof input === 'number'
      ? { message: String(input) }
      : { ...(input || {}) }

  const type = forcedType || raw.type || defaults.type
  const state = STATE_OF[type] || 'notice'
  const isLong = !!(raw.title != null && String(raw.title))
  // duration：**显式给了就用给的**（0 = 常驻）；否则走阶梯默认（见 DURATION_OF_STATE）。
  // ⚠ loading 与 notice 共用一个 state，但时长语义完全不同 ⇒ 这里按 type 单独判常驻。
  const rawDuration = raw.duration !== undefined ? Number(raw.duration) : undefined
  const ladder =
    type === 'loading'
      ? 0
      : (DURATION_OF_STATE[state] ?? defaults.duration) + (isLong ? LONG_BONUS : 0)
  const duration = Number.isFinite(rawDuration) ? rawDuration : ladder

  return {
    message: raw.message != null ? String(raw.message) : '',
    title: raw.title != null ? String(raw.title) : '',
    type,
    position: raw.position || defaults.position,
    duration,
    forbidClick: raw.forbidClick != null ? !!raw.forbidClick : defaults.forbidClick,
    closeOnClick: raw.closeOnClick != null ? !!raw.closeOnClick : defaults.closeOnClick,
    closable: raw.closable != null ? !!raw.closable : WITH_CLOSE.has(type),
    allowHtml: raw.allowHtml === true || type === 'html',
    onClose: typeof raw.onClose === 'function' ? raw.onClose : null,
  }
}

function buildEl(o) {
  const state = STATE_OF[o.type] || 'notice'
  const withIcon = WITH_ICON.has(o.type)
  const isLong = !!o.title

  const el = document.createElement('div')
  el.className = 'app-toast app-toast--' + state
  if (o.type === 'loading') el.classList.add('app-toast--loading')
  if (isLong) el.classList.add('app-toast--long')
  if (!withIcon && !o.closable) el.classList.add('app-toast--plain')
  el.setAttribute('role', 'alert')

  if (withIcon) {
    const icon = document.createElement('span')
    icon.className = 'app-toast__icon'
    icon.innerHTML = ICON_OF[o.type] || ICON_FAIL
    el.appendChild(icon)
  }

  const fill = (node, text) => {
    if (o.allowHtml) node.innerHTML = text
    else node.textContent = text
  }

  if (isLong) {
    const content = document.createElement('div')
    content.className = 'app-toast__content'
    const title = document.createElement('span')
    title.className = 'app-toast__title'
    title.textContent = o.title
    content.appendChild(title)
    if (o.message) {
      const desc = document.createElement('span')
      desc.className = 'app-toast__desc'
      fill(desc, o.message)
      content.appendChild(desc)
    }
    el.appendChild(content)
  } else if (o.message) {
    const text = document.createElement('span')
    text.className = 'app-toast__text'
    fill(text, o.message)
    el.appendChild(text)
  }

  if (o.closable) {
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.className = 'app-toast__close'
    btn.setAttribute('aria-label', '关闭')
    btn.innerHTML = ICON_CLOSE
    btn.addEventListener('click', (e) => {
      e.stopPropagation()
      closeToast()
    })
    el.appendChild(btn)
  }

  return el
}

/** 移除当前弹窗。`animate=false` 用于「被新弹窗顶掉」的场景，避免闪一下。 */
function destroyCurrent(animate = true) {
  const c = current
  if (!c) return
  current = null
  if (c.timer) clearTimeout(c.timer)
  if (c.forbidClick) setBlocking(false)

  const finish = () => {
    if (c.el.parentNode) c.el.parentNode.removeChild(c.el)
    if (c.onClose) c.onClose()
  }

  if (animate) {
    c.el.classList.add('is-hiding')
    setTimeout(finish, HIDE_MS)
  } else {
    finish()
  }
}

/**
 * showToast(message) / showToast({ message, type, duration, position, onClose, ... })
 * @returns {{ close: () => void }} 与 Vant 一样返回可手动关闭的实例
 */
export function showToast(input, forcedType) {
  if (typeof document === 'undefined') return { close() {} }
  const o = normalize(input, forcedType)

  // 单例：先顶掉上一个（不播动画）
  destroyCurrent(false)

  const root = ensureRoot(o.position)
  const el = buildEl(o)
  root.appendChild(el)

  const instance = { close: () => closeToast() }
  current = {
    el,
    timer: null,
    onClose: o.onClose,
    forbidClick: o.forbidClick,
    close: instance.close,
    // 归一化后的**实际存活时长**（调试 / 自检用；`0` = 常驻）。
    // 暴露出来是为了能直接断言「阶梯时长生效了」，不用去 mock setTimeout。
    duration: o.duration,
  }

  if (o.forbidClick) setBlocking(true)

  if (o.closeOnClick) {
    el.addEventListener('click', (e) => {
      if (e.target.closest && e.target.closest('.app-toast__close')) return
      closeToast()
    })
  }

  if (o.duration > 0) {
    current.timer = setTimeout(() => {
      if (current && current.el === el) closeToast()
    }, o.duration)
  }

  return instance
}

export function showSuccessToast(input) {
  return showToast(input, 'success')
}

export function showFailToast(input) {
  return showToast(input, 'fail')
}

/** 常驻（duration 默认 0），需 `closeToast()` 收尾 —— 与 Vant 一致 */
export function showLoadingToast(input) {
  return showToast(input, 'loading')
}

export function closeToast() {
  destroyCurrent(true)
}

/** main.js 用它兜底全站默认位置 */
export function setToastDefaultOptions(options) {
  Object.assign(defaults, options || {})
}

export function resetToastDefaultOptions() {
  defaults = { ...DEFAULTS }
}

export default {
  showToast,
  showSuccessToast,
  showFailToast,
  showLoadingToast,
  closeToast,
  setToastDefaultOptions,
  resetToastDefaultOptions,
}
