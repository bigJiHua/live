import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'ui-theme-choice'

// 浅色 / 深色 各自的"状态文字衍生色"（保证白底/深底可读，满足对比度 WCAG）
const STATUS_TEXT = {
  light: { success: '#0a7a45', warning: '#b45309', danger: '#c0102a', info: '#646566' },
  dark: { success: '#5fd6a0', warning: '#f0b267', danger: '#ff6b6b', info: '#a0a0a0' },
}
// 浅色 / 深色 各自的"状态组件色"（tag/circle 背景，亮色 + 白字）
const STATUS_COMP = {
  light: { success: '#07c160', warning: '#ff976a', danger: '#ee0a24', info: '#969799' },
  dark: { success: '#2f9b70', warning: '#c9a05a', danger: '#e5484d', info: '#7a7a7a' },
}
// 悬浮 TabBar 玻璃变量（仅用于底部 TabBar，符合"玻璃拟态只用在 TabBar"）
const TABBAR = {
  light: { glassBg: 'rgba(255,255,255,0.62)', glassBorder: 'rgba(0,0,0,0.06)', text: '#7d7e80' },
  dark: { glassBg: 'rgba(18,18,22,0.55)', glassBorder: 'rgba(255,255,255,0.12)', text: '#b0b0b0' },
}

// ===== 预设方案列表（新增配色只需往这里 push 一套）=====
// mode: 'light' | 'dark'；bg/text/border/status/statusText 可局部覆盖
export const THEME_PRESETS = [
  { key: 'green', name: '绿', preview: 'linear-gradient(135deg,#07c160,#06ad56)', mode: 'light', primary: '#07c160', grad: '#06ad56' },
  { key: 'blue', name: '蓝', preview: 'linear-gradient(135deg,#1989fa,#1976d2)', mode: 'light', primary: '#1989fa', grad: '#1976d2' },
  { key: 'indigo', name: '靛', preview: 'linear-gradient(135deg,#3a66e0,#2a4fb8)', mode: 'light', primary: '#3a66e0', grad: '#2a4fb8' },
  { key: 'blackgold', name: '黑金', preview: 'linear-gradient(135deg,#C9A86A,#a8863f)', mode: 'dark', primary: '#C9A86A', grad: '#a8863f' },
  { key: 'mono', name: '素', preview: 'linear-gradient(135deg,#1a1a1a,#000)', mode: 'light', primary: '#1a1a1a', grad: '#000000',
    bg: { primary: '#f4f4f5', secondary: '#ffffff', tertiary: '#ececec' },
    text: { primary: '#1a1a1a', secondary: '#595959', tertiary: '#8c8c8c', placeholder: '#bfbfbf' },
    border: '#e0e0e0',
    status: { success: '#1a1a1a', warning: '#8c8c8c', danger: '#c0392b', info: '#8c8c8c' },
    statusText: { success: '#1a1a1a', warning: '#8c8c8c', danger: '#c0392b', info: '#8c8c8c' } },
  { key: 'navygold', name: '藏青金', preview: 'linear-gradient(135deg,#0c1626,#C9A86A)', mode: 'dark', primary: '#C9A86A', grad: '#a8863f',
    bg: { primary: '#0c1626', secondary: '#13233b', tertiary: '#1d3150' },
    text: { primary: '#eaf0fb', secondary: '#a9b8d0', tertiary: '#7d8fae', placeholder: '#54688c' },
    border: '#24395c',
    status: { success: '#C9A86A', warning: '#c9a05a', danger: '#e5484d', info: '#7d8fae' },
    statusText: { success: '#e6c98a', warning: '#e0b878', danger: '#ff8a8a', info: '#9fb0cc' } },
  { key: 'titanium', name: '钛金属', preview: 'linear-gradient(135deg,#232326,#d8d8dc)', mode: 'dark', primary: '#d8d8dc', grad: '#b9b9be', buttonPrimaryText: '#1a1a1a',
    bg: { primary: '#161618', secondary: '#232326', tertiary: '#2e2e32' },
    text: { primary: '#f2f2f4', secondary: '#aeaeb2', tertiary: '#8a8a90', placeholder: '#5a5a5e' },
    border: 'rgba(255,255,255,0.08)',
    status: { success: '#d8d8dc', warning: '#c9a05a', danger: '#e5484d', info: '#8a8a90' },
    statusText: { success: '#e6e6ea', warning: '#e0b878', danger: '#ff8a8a', info: '#b6b6ba' } },
  { key: 'emerald', name: '墨玉绿', preview: 'linear-gradient(135deg,#0a1812,#3fbf8f)', mode: 'dark', primary: '#3fbf8f', grad: '#2f9b70',
    bg: { primary: '#0a1812', secondary: '#0f2419', tertiary: '#16331f' },
    text: { primary: '#e6f5ec', secondary: '#a4c9b1', tertiary: '#7ba88f', placeholder: '#4d6b58' },
    border: '#1f3a28',
    status: { success: '#3fbf8f', warning: '#c9a05a', danger: '#e5484d', info: '#7ba88f' },
    statusText: { success: '#7fe0b5', warning: '#e0b878', danger: '#ff8a8a', info: '#a8d4bb' } },
  { key: 'wise', name: '悠森绿', preview: 'linear-gradient(135deg,#163300,#9FE870)', mode: 'dark', primary: '#9FE870', grad: '#4a7c2c', buttonPrimaryText: '#163300',
    bg: { primary: '#163300', secondary: '#1e4200', tertiary: '#29520e' },
    text: { primary: '#f2f9ea', secondary: '#b6d8a0', tertiary: '#8ab06e', placeholder: '#57733f' },
    border: '#2c5014',
    status: { success: '#9FE870', warning: '#c9a05a', danger: '#e5484d', info: '#8ab06e' },
    statusText: { success: '#c4f0a0', warning: '#e0b878', danger: '#ff8a8a', info: '#b6d8a0' } },
  { key: 'burgundy', name: '酒红香槟', preview: 'linear-gradient(135deg,#1f0810,#C9A86A)', mode: 'dark', primary: '#C9A86A', grad: '#a8863f',
    bg: { primary: '#1f0810', secondary: '#2e0c18', tertiary: '#3d1222' },
    text: { primary: '#f7e9ec', secondary: '#cda6b1', tertiary: '#a07d89', placeholder: '#66414c' },
    border: '#40182a',
    status: { success: '#C9A86A', warning: '#c9a05a', danger: '#e5484d', info: '#a07d89' },
    statusText: { success: '#e6c98a', warning: '#e0b878', danger: '#ff8a8a', info: '#d3aab4' } },
]

// monochrome：切换非默认（彩色）主题后，分类彩色图标/文字统一为主题色，保证一体整洁
// 默认三套浅色主题（绿/蓝/靛蓝）保留五颜六色 icon；其余（深色/特殊）统一主题色
const COLOR_ICON_THEMES = ['green', 'blue', 'indigo']
THEME_PRESETS.forEach((p) => {
  if (p.monochrome === undefined) p.monochrome = !COLOR_ICON_THEMES.includes(p.key)
})

// 色系标识（green/blue/red/gold/neutral），供设置页按色系排序分组展示
const THEME_HUE = {
  green: 'green', blue: 'blue', indigo: 'blue', blackgold: 'gold',
  mono: 'neutral', navygold: 'blue', titanium: 'neutral',
  emerald: 'green', wise: 'green', burgundy: 'red',
}
THEME_PRESETS.forEach((p) => {
  p.hue = THEME_HUE[p.key] || 'neutral'
})

// 把 #rrggbb 解析成 "r, g, b"，供 rgba(var(--theme-primary-rgb), a) 半透明场景用
function hexToRgb(hex) {
  const h = (hex || '#000000').replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  const r = parseInt(full.substring(0, 2), 16) || 0
  const g = parseInt(full.substring(2, 4), 16) || 0
  const b = parseInt(full.substring(4, 6), 16) || 0
  return `${r}, ${g}, ${b}`
}

// ===== 由主题对象生成完整 CSS 变量集（注入到 :root）=====
// 同时导出，供 demo UiShowcase 复用，保证与正式主题变量一致
export function buildVars(t) {
  const dark = t.mode === 'dark'
  const bg = t.bg || (dark
    ? { primary: '#0f0f12', secondary: '#1a1a1e', tertiary: '#26262b' }
    : { primary: '#f7f8fa', secondary: '#ffffff', tertiary: '#f2f3f5' })
  const text = t.text || (dark
    ? { primary: '#f0f0f0', secondary: '#b0b0b0', tertiary: '#8a8a8a', placeholder: '#5a5a5a' }
    : { primary: '#323233', secondary: '#646566', tertiary: '#969799', placeholder: '#c8c9cc' })
  const border = t.border || (dark ? 'rgba(255,255,255,0.08)' : '#ebedf0')
  const statusComp = t.status || STATUS_COMP[dark ? 'dark' : 'light']
  const statusText = t.statusText || STATUS_TEXT[dark ? 'dark' : 'light']
  const btnPrimaryText = t.buttonPrimaryText || (dark ? '#1a1a1a' : '#ffffff')
  const navBg = dark ? bg.primary : bg.secondary
  const tab = TABBAR[dark ? 'dark' : 'light']

  return {
    '--theme-primary': t.primary,
    '--theme-primary-grad': t.grad || t.primary,
    '--theme-primary-rgb': hexToRgb(t.primary),
    '--theme-primary-light': dark ? 'rgba(255,255,255,.08)' : '#eef3ff',
    '--van-primary-color': t.primary,
    '--van-nav-bar-text-color': t.primary,
    '--van-tabbar-item-active-color': t.primary,
    '--van-tabs-nav-background': navBg,
    '--van-tab-text-color': text.secondary,
    '--van-tab-active-text-color': t.primary,
    '--van-tabs-bottom-bar-color': t.primary,
    '--van-switch-on-background': t.primary,
    '--van-button-primary-background': t.primary,
    '--van-button-primary-border-color': t.primary,
    '--van-button-primary-color': btnPrimaryText,
    '--van-nav-bar-icon-color': dark ? t.primary : text.primary,
    '--van-nav-bar-title-text-color': text.primary,
    '--van-nav-bar-background': navBg,
    '--van-cell-background': bg.secondary,
    '--van-cell-group-background': bg.primary,
    '--van-cell-text-color': text.primary,
    '--van-cell-label-color': text.tertiary,
    '--van-cell-group-title-color': text.tertiary,
    '--van-tag-default-background': bg.tertiary,
    '--van-tag-default-color': text.secondary,
    '--van-field-label-color': text.secondary,
    '--van-field-input-text-color': text.primary,
    '--van-field-placeholder-text-color': text.placeholder,
    '--van-divider-content-left-color': text.tertiary,
    '--van-divider-content-right-color': text.tertiary,
    '--van-button-default-background': bg.secondary,
    '--van-button-default-color': text.primary,
    '--van-button-default-border-color': border,
    // 边框/分割线（hairline）随主题，避免深色主题下 nav-bar 等出现刺眼的浅色底边
    '--van-border-color': border,
    '--van-hairline-color': border,
    '--theme-bg-primary': bg.primary,
    '--theme-bg-secondary': bg.secondary,
    '--theme-bg-tertiary': bg.tertiary,
    '--app-bg': bg.primary,
    '--app-card-bg': bg.secondary,
    '--app-text-main': text.primary,
    '--theme-border': border,
    '--theme-text-primary': text.primary,
    '--theme-text-secondary': text.secondary,
    '--theme-text-tertiary': text.tertiary,
    '--theme-text-placeholder': text.placeholder,
    '--theme-success': statusComp.success,
    '--theme-warning': statusComp.warning,
    '--theme-danger': statusComp.danger,
    '--theme-info': statusComp.info,
    '--van-success-color': statusComp.success,
    '--van-warning-color': statusComp.warning,
    '--van-danger-color': statusComp.danger,
    '--van-info-color': statusComp.info,
    '--theme-success-text': statusText.success,
    '--theme-warning-text': statusText.warning,
    '--theme-danger-text': statusText.danger,
    '--theme-info-text': statusText.info,
    '--tabbar-glass-bg': tab.glassBg,
    '--tabbar-glass-border': tab.glassBorder,
    '--tabbar-text': tab.text,
  }
}

// ===== 单例状态（模块级，全应用共享）=====
const choice = ref(
  (typeof localStorage !== 'undefined')
    ? (localStorage.getItem(STORAGE_KEY) || 'system')
    : 'system'
)
const isDark = ref(false)
let mql = null
if (typeof window !== 'undefined' && window.matchMedia) {
  mql = window.matchMedia('(prefers-color-scheme: dark)')
  isDark.value = !!mql.matches
}

// 实际生效的方案 key：系统默认时，浅色→靛蓝，深色→黑金
const activeKey = computed(() => {
  if (choice.value && choice.value !== 'system') return choice.value
  return isDark.value ? 'blackgold' : 'indigo'
})

function applyTheme() {
  if (typeof document === 'undefined') return
  const t = THEME_PRESETS.find((p) => p.key === activeKey.value) || THEME_PRESETS[2]
  const vars = buildVars(t)
  const root = document.documentElement
  for (const [k, v] of Object.entries(vars)) {
    root.style.setProperty(k, v)
  }
  root.setAttribute('data-theme', t.key)
  root.setAttribute('data-theme-mode', t.mode)
  root.setAttribute('data-theme-mono', t.monochrome ? '1' : '0')
  // 浏览器地址栏/标签栏 + html 根背景跟随主题，避免 overscroll/下拉露白撞色
  // 深色主题用最深背景，浅色主题用卡片白，保证与页面一体
  const themeColor = t.mode === 'dark' ? vars['--theme-bg-primary'] : vars['--theme-bg-secondary']
  root.style.background = themeColor
  let meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', themeColor)
  } else {
    meta = document.createElement('meta')
    meta.setAttribute('name', 'theme-color')
    meta.setAttribute('content', themeColor)
    document.head.appendChild(meta)
  }
}

// 系统深色模式变化 → 重新计算（仅当处于"系统默认"时生效）
if (mql && mql.addEventListener) {
  mql.addEventListener('change', (e) => {
    isDark.value = e.matches
    applyTheme()
  })
}

// 首次应用（模块加载即生效，确保首屏正确）
applyTheme()

let watchRegistered = false
function ensureWatch() {
  if (watchRegistered) return
  watchRegistered = true
  // 用户选择变化 → 持久化 + 应用（在 setup 上下文注册，避免 Vue 警告）
  watch(choice, (val) => {
    try { localStorage.setItem(STORAGE_KEY, val) } catch (e) {}
    applyTheme()
  })
}

export function useUiTheme() {
  ensureWatch()
  const getPrimary = () => THEME_PRESETS.find(t => t.key === choice.value)?.primary || THEME_PRESETS[0].primary
  const primary = ref(getPrimary())
  watch(choice, () => { primary.value = getPrimary() })
  const getSemantic = (k) => THEME_PRESETS.find(t => t.key === choice.value)?.semantic?.[k] || THEME_PRESETS[0].semantic?.[k]
  const success = ref(getSemantic('success'))
  const danger = ref(getSemantic('danger'))
  const warning = ref(getSemantic('warning'))
  const info = ref(getSemantic('info'))
  watch(choice, () => { success.value = getSemantic('success'); danger.value = getSemantic('danger'); warning.value = getSemantic('warning'); info.value = getSemantic('info') })
  return {
    choice,
    isDark,
    presets: THEME_PRESETS,
    activeKey,
    primary,
    success,
    danger,
    warning,
    info,
    setChoice: (k) => { choice.value = k },
    applyTheme,
  }
}
