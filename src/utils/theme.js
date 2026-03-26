/**
 * 主题管理工具
 * 用于动态修改应用的全局配色方案
 */

// 预设主题配置
export const THEME_PRESETS = {
  default: {
    name: '默认绿',
    colors: {
      primary: '#07c160',
      primaryLight: '#e6f9ed',
      secondary: '#1989fa',
      secondaryLight: '#ecf5fe',
      success: '#07c160',
      warning: '#ff976a',
      danger: '#ee0a24',
      info: '#969799',
      bgPrimary: '#f7f8fa',
      bgSecondary: '#ffffff',
      bgTertiary: '#f2f3f5',
      textPrimary: '#323233',
      textSecondary: '#646566',
      textTertiary: '#969799',
      textPlaceholder: '#c8c9cc',
      border: '#ebedf0',
      borderLight: '#f7f8fa',
    },
  },
  blue: {
    name: '商务蓝',
    colors: {
      primary: '#1989fa',
      primaryLight: '#ecf5fe',
      secondary: '#07c160',
      secondaryLight: '#e6f9ed',
      success: '#07c160',
      warning: '#ff976a',
      danger: '#ee0a24',
      info: '#969799',
      bgPrimary: '#f2f6fc',
      bgSecondary: '#ffffff',
      bgTertiary: '#ecf5fe',
      textPrimary: '#323233',
      textSecondary: '#646566',
      textTertiary: '#969799',
      textPlaceholder: '#c8c9cc',
      border: '#ebedf0',
      borderLight: '#f2f6fc',
    },
  },
  purple: {
    name: '紫罗兰',
    colors: {
      primary: '#7232dd',
      primaryLight: '#f3eef9',
      secondary: '#1989fa',
      secondaryLight: '#ecf5fe',
      success: '#07c160',
      warning: '#ff976a',
      danger: '#ee0a24',
      info: '#969799',
      bgPrimary: '#f8f6fc',
      bgSecondary: '#ffffff',
      bgTertiary: '#f3eef9',
      textPrimary: '#323233',
      textSecondary: '#646566',
      textTertiary: '#969799',
      textPlaceholder: '#c8c9cc',
      border: '#ebedf0',
      borderLight: '#f8f6fc',
    },
  },
  orange: {
    name: '活力橙',
    colors: {
      primary: '#ff976a',
      primaryLight: '#fff3eb',
      secondary: '#1989fa',
      secondaryLight: '#ecf5fe',
      success: '#07c160',
      warning: '#ff976a',
      danger: '#ee0a24',
      info: '#969799',
      bgPrimary: '#fff9f5',
      bgSecondary: '#ffffff',
      bgTertiary: '#fff3eb',
      textPrimary: '#323233',
      textSecondary: '#646566',
      textTertiary: '#969799',
      textPlaceholder: '#c8c9cc',
      border: '#ebedf0',
      borderLight: '#fff9f5',
    },
  },
  dark: {
    name: '暗夜模式',
    colors: {
      primary: '#07c160',
      primaryLight: 'rgba(7, 193, 96, 0.15)',
      secondary: '#1989fa',
      secondaryLight: 'rgba(25, 137, 250, 0.15)',
      success: '#07c160',
      warning: '#ff976a',
      danger: '#ee0a24',
      info: '#969799',
      bgPrimary: '#1a1a1a',
      bgSecondary: '#2a2a2a',
      bgTertiary: '#3a3a3a',
      textPrimary: '#ffffff',
      textSecondary: '#d0d0d0',
      textTertiary: '#999999',
      textPlaceholder: '#666666',
      border: '#3a3a3a',
      borderLight: '#2a2a2a',
    },
  },
}

// localStorage 键名
const STORAGE_KEY = 'app_theme_config'

// 应用主题到 CSS 变量
export function applyTheme(colors) {
  const root = document.documentElement

  // 设置所有主题变量
  root.style.setProperty('--theme-primary', colors.primary)
  root.style.setProperty('--theme-primary-light', colors.primaryLight)
  root.style.setProperty('--theme-secondary', colors.secondary)
  root.style.setProperty('--theme-secondary-light', colors.secondaryLight)
  root.style.setProperty('--theme-success', colors.success)
  root.style.setProperty('--theme-warning', colors.warning)
  root.style.setProperty('--theme-danger', colors.danger)
  root.style.setProperty('--theme-info', colors.info)
  root.style.setProperty('--theme-bg-primary', colors.bgPrimary)
  root.style.setProperty('--theme-bg-secondary', colors.bgSecondary)
  root.style.setProperty('--theme-bg-tertiary', colors.bgTertiary)
  root.style.setProperty('--theme-text-primary', colors.textPrimary)
  root.style.setProperty('--theme-text-secondary', colors.textSecondary)
  root.style.setProperty('--theme-text-tertiary', colors.textTertiary)
  root.style.setProperty('--theme-text-placeholder', colors.textPlaceholder)
  root.style.setProperty('--theme-border', colors.border)
  root.style.setProperty('--theme-border-light', colors.borderLight)

  // 同时更新原有变量以保持兼容
  root.style.setProperty('--app-primary', colors.primary)
  root.style.setProperty('--app-expense', colors.warning)
  root.style.setProperty('--app-income', colors.success)
  root.style.setProperty('--app-bg', colors.bgPrimary)
  root.style.setProperty('--app-card-bg', colors.bgSecondary)
  root.style.setProperty('--app-text-main', colors.textPrimary)
}

// 保存主题配置到 localStorage
export function saveTheme(themeId) {
  localStorage.setItem(STORAGE_KEY, themeId)
}

// 从 localStorage 加载主题配置
export function loadTheme() {
  const themeId = localStorage.getItem(STORAGE_KEY)
  return themeId || 'default'
}

// 应用预设主题
export function applyThemePreset(themeId) {
  const preset = THEME_PRESETS[themeId]
  if (preset) {
    applyTheme(preset.colors)
    saveTheme(themeId)
    return preset
  }
  return null
}

// 应用自定义主题颜色
export function applyCustomTheme(colors) {
  applyTheme(colors)
  saveTheme('custom')
  return colors
}

// 获取当前主题
export function getCurrentTheme() {
  const themeId = loadTheme()
  if (themeId === 'custom') {
    // 从 localStorage 读取自定义颜色
    const customColors = localStorage.getItem('app_custom_colors')
    if (customColors) {
      try {
        return { id: 'custom', name: '自定义', colors: JSON.parse(customColors) }
      } catch (e) {
        console.error('解析自定义主题失败:', e)
      }
    }
    return THEME_PRESETS.default
  }
  return { id: themeId, ...THEME_PRESETS[themeId] || THEME_PRESETS.default }
}

// 初始化主题（应用启动时调用）
export function initTheme() {
  const theme = getCurrentTheme()
  applyTheme(theme.colors)
  return theme
}

// 保存自定义主题颜色
export function saveCustomTheme(colors) {
  localStorage.setItem('app_custom_colors', JSON.stringify(colors))
  applyTheme(colors)
  saveTheme('custom')
}
