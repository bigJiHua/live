import { ref, watch } from 'vue'

const STORAGE_KEY = 'money-color-mode'

// 收支金额文字颜色模式：
//  'red-in'（默认）：红收绿支（收入红色、支出绿色）
//  'red-out'：红出绿收（支出红色、收入绿色）
const INCOME_GREEN = '#07c160'
const INCOME_GREEN_TEXT = '#0a7a45'
const EXPENSE_RED = '#ee0a24'
const EXPENSE_RED_TEXT = '#c0102a'

function resolveColors(mode) {
  if (mode === 'red-out') {
    return {
      '--money-income': INCOME_GREEN,
      '--money-income-text': INCOME_GREEN_TEXT,
      '--money-expense': EXPENSE_RED,
      '--money-expense-text': EXPENSE_RED_TEXT,
    }
  }
  return {
    '--money-income': EXPENSE_RED,
    '--money-income-text': EXPENSE_RED_TEXT,
    '--money-expense': INCOME_GREEN,
    '--money-expense-text': INCOME_GREEN_TEXT,
  }
}

// 模块级单例：全应用共享同一份收支颜色设置
const mode = ref(
  (typeof localStorage !== 'undefined')
    ? (localStorage.getItem(STORAGE_KEY) || 'red-in')
    : 'red-in'
)

function applyMoneyColor() {
  if (typeof document === 'undefined') return
  const vars = resolveColors(mode.value)
  const root = document.documentElement
  for (const [k, v] of Object.entries(vars)) {
    root.style.setProperty(k, v)
  }
}

applyMoneyColor()

let watchRegistered = false
function ensureWatch() {
  if (watchRegistered) return
  watchRegistered = true
  watch(mode, (val) => {
    try { localStorage.setItem(STORAGE_KEY, val) } catch (e) {}
    applyMoneyColor()
  })
}

export function useMoneyColor() {
  ensureWatch()
  return {
    mode,
    isRedOut: () => mode.value === 'red-out',
    setRedIn: () => { mode.value = 'red-in' },
    setRedOut: () => { mode.value = 'red-out' },
    setMode: (m) => { mode.value = (m === 'red-in' ? 'red-in' : 'red-out') },
    applyMoneyColor,
  }
}

export function getMoneyColors() {
  return resolveColors(mode.value)
}
