<template>
  <div class="page-flow-filter">
    <!-- Layer 1: 日期范围 + 查询 -->
    <div class="top-row">
      <div class="date-range" @click="showCalendar = true">
        <div class="date-item date-start">
          <div class="filter-label">开始</div>
          <div class="filter-value">{{ startDate }}</div>
        </div>
        <div class="date-sep">至</div>
        <div class="date-item date-end">
          <div class="filter-label">结束</div>
          <div class="filter-value">{{ endDate }}</div>
        </div>
      </div>
      <van-button type="primary" round :loading="searchLoading" @click="handleSearch" class="btn-search">查询</van-button>
    </div>

    <!-- Layer 2: 收支/分类/方式 -->
    <div class="row-three">
      <div class="tri-item" @click="showDirPicker = true">
        <div class="filter-label">收支 <van-icon name="arrow-down" /></div>
        <div class="filter-value">{{ dirText }}</div>
      </div>
      <div class="tri-item" @click="showCatPicker = true">
        <div class="filter-label">分类 <van-icon name="arrow-down" /></div>
        <div class="filter-value">{{ selectedCatName }}</div>
      </div>
      <div class="tri-item" @click="showMethodPicker = true">
        <div class="filter-label">方式 <van-icon name="arrow-down" /></div>
        <div class="filter-value">{{ selectedMethod || '不限' }}</div>
      </div>
    </div>

    <!-- Layer 3: 跳至 + 导出 -->
    <div v-if="searched && availableDates.length > 0" class="bottom-row">
      <div class="qd-group">
        <span class="qd-label">跳至</span>
        <div class="qd-picker" @click="openQuickPicker">
          {{ quickDate ? formatQuickDate(quickDate) : '选择日期' }}
          <van-icon name="arrow-down" />
        </div>
        <div v-if="quickDate" class="qd-reset" @click="quickDate = null">全部</div>
      </div>
      <van-button v-if="resultList.length > 0" plain round @click="handleExport" class="btn-export">导出 Excel</van-button>
    </div>

    <!-- 结果 -->
    <div v-if="searched" ref="listRef" class="result-area">
      <div class="result-summary">
        <span>共 {{ resultList.length }} 条</span>
        <span v-if="totalIncome || totalExpense" class="summary-amount">
          <span class="income">收入 +{{ formatAmount(totalIncome) }}</span>
          <span class="sep">|</span>
          <span class="expense">支出 -{{ formatAmount(totalExpense) }}</span>
        </span>
      </div>

      <van-empty v-if="resultList.length === 0" description="未找到匹配记录" />

      <div v-else class="flow-list">
        <template v-for="(group, date) in groupedDisplayList" :key="date">
          <div class="date-header" :data-date="date">{{ formatDateHeader(date) }}</div>
          <template v-for="node in group" :key="node.type === 'flow' ? node.data.id : 'pair-' + node.expense.id">
            <!-- 普通流水 -->
            <div v-if="node.type === 'flow'" class="flow-item" @click="$router.push(`/finance/flow/${node.data.id}`)">
              <div class="item-left">
                <div class="item-cat">{{ node.data.category_name || node.data.categoryName || node.data.pay_type || node.data.payType || '未分类' }}</div>
                <div class="item-desc">{{ getBankCardLabel(node.data) || payMethodLabel(node.data.pay_method || node.data.payMethod) }}</div>
              </div>
              <div class="item-right">
                <div class="item-amount" :class="(node.data.direction === 1 || node.data.direction === 'income') ? 'income' : 'expense'">
                  {{ node.data.direction === 1 || node.data.direction === 'income' ? '+' : '-' }}{{ formatAmount(node.data.amount) }}
                </div>
                <div class="item-date">{{ formatDate(node.data.trans_date || node.data.transDate) }}</div>
              </div>
            </div>
            <!-- 转账 -->
            <div v-else-if="node.type === 'transfer'" class="transfer-row">
              <div class="transfer-header">{{ node.isExplicit ? '转账' : '疑似转账' }}</div>
              <div class="transfer-body">
                <div class="transfer-side" @click="$router.push(`/finance/flow/${node.expense.id}`)">
                  <div class="transfer-amount expense">-{{ formatAmount(node.expense.amount) }}</div>
                  <div class="transfer-bank">{{ getBankCardLabel(node.expense) || '余额' }}</div>
                </div>
                <div class="transfer-arrow">→</div>
                <div class="transfer-side" @click="$router.push(`/finance/flow/${node.income.id}`)">
                  <div class="transfer-amount income">+{{ formatAmount(node.income.amount) }}</div>
                  <div class="transfer-bank">{{ getBankCardLabel(node.income) || '银行卡' }}</div>
                </div>
              </div>
            </div>
            <!-- 提现 -->
            <div v-else-if="node.type === 'withdrawal'" class="withdrawal-row">
              <div class="withdrawal-header">提现</div>
              <div class="withdrawal-body">
                <div class="withdrawal-side" @click="$router.push(`/finance/flow/${node.expense.id}`)">
                  <div class="withdrawal-amount expense">-{{ formatAmount(node.expense.amount) }}</div>
                  <div class="withdrawal-bank">{{ getBankCardLabel(node.expense) || '余额' }}</div>
                </div>
                <div class="withdrawal-arrow">→</div>
                <div class="withdrawal-side" @click="$router.push(`/finance/flow/${node.income.id}`)">
                  <div class="withdrawal-amount income">+{{ formatAmount(node.income.amount) }}</div>
                  <div class="withdrawal-bank">{{ getBankCardLabel(node.income) || '银行卡' }}</div>
                </div>
              </div>
            </div>
            <!-- 冲正 -->
            <div v-else-if="node.type === 'reversal'" class="reversal-row">
              <div class="reversal-header">冲正</div>
              <div class="reversal-body">
                <div class="reversal-side" @click="$router.push(`/finance/flow/${node.expense.id}`)">
                  <div class="reversal-amount expense">-{{ formatAmount(node.expense.amount) }}</div>
                  <div class="reversal-bank">{{ getBankCardLabel(node.expense) || '信用卡' }}</div>
                </div>
                <div class="reversal-arrow">→</div>
                <div class="reversal-side" @click="$router.push(`/finance/flow/${node.income.id}`)">
                  <div class="reversal-amount income">+{{ formatAmount(node.income.amount) }}</div>
                  <div class="reversal-bank">{{ getBankCardLabel(node.income) || '现金/余额' }}</div>
                </div>
              </div>
            </div>
          </template>
        </template>
      </div>
    </div>

    <!-- Pickers -->
    <van-calendar v-model:show="showCalendar" type="range" :min-date="minDate" :max-date="maxDate" @confirm="onDateConfirm" />
    <van-popup v-model:show="showDirPicker" position="bottom" round>
      <van-picker :columns="dirOptions" @confirm="onDirConfirm" @cancel="showDirPicker = false" />
    </van-popup>
    <van-popup v-model:show="showCatPicker" position="bottom" round>
      <div class="category-popup">
        <div class="popup-header">
          <span>选择分类</span>
          <van-icon name="cross" @click="showCatPicker = false" />
        </div>
        <div class="category-list">
          <div class="category-item" :class="{ active: !selectedCat }" @click="onCatSelect(null)">
            <span class="category-name">不限</span>
            <van-icon v-if="!selectedCat" name="success" color="#07c160" />
          </div>
          <div v-for="cat in displayCategories" :key="cat.id" class="category-item" :class="{ active: selectedCat?.value === cat.id }" @click="onCatSelect(cat)">
            <span class="category-name">{{ cat.name }}</span>
            <van-icon v-if="selectedCat?.value === cat.id" name="success" color="#07c160" />
          </div>
        </div>
      </div>
    </van-popup>
    <van-popup v-model:show="showMethodPicker" position="bottom" round>
      <div class="category-popup">
        <div class="popup-header">
          <span>选择方式</span>
          <van-icon name="cross" @click="showMethodPicker = false" />
        </div>
        <div class="category-list">
          <div v-for="m in methodOptions" :key="m.value" class="category-item" :class="{ active: selectedMethod === m.value }" @click="onMethodSelect(m.value)">
            <span class="category-name">{{ m.text }}</span>
            <van-icon v-if="selectedMethod === m.value" name="success" color="#07c160" />
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 返回顶部 -->
    <van-icon v-show="showBackTop" name="back-top" class="back-top-btn" @click="scrollToTop" />

    <!-- 日期跳段选择器 -->
    <van-popup v-model:show="showQuickPicker" position="bottom" round>
      <van-picker v-model="quickPickerSelected" title="跳转至" :columns="quickPickerColumns" @confirm="onQuickDateConfirm" @cancel="showQuickPicker = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated, onDeactivated, nextTick } from 'vue'
defineOptions({ name: 'FinanceReportFlowFilter' })
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import { getAccountList } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'
import { useFlowSyncStore } from '@/stores/flowSync'

const router = useRouter()
const route = useRoute()
const flowSync = useFlowSyncStore()

const listRef = ref(null)

const startDate = ref(route.query.start || dayjs().startOf('month').format('YYYY-MM-DD'))
const endDate = ref(route.query.end || dayjs().format('YYYY-MM-DD'))
const minDate = new Date(2020, 0, 1)
const maxDate = new Date(dayjs().add(1, 'year').format('YYYY'), 11, 31)
const direction = ref('')
const selectedCat = ref(null)
const selectedMethod = ref('')
const showCalendar = ref(false)
const showDirPicker = ref(false)
const showCatPicker = ref(false)
const showMethodPicker = ref(false)
const incomeCategories = ref([])
const expenseCategories = ref([])
const bankList = ref([])
const cardList = ref([])

const displayCategories = computed(() => {
  if (direction.value === '1') return incomeCategories.value
  if (direction.value === '0') return expenseCategories.value
  return [...incomeCategories.value, ...expenseCategories.value]
})

const allData = ref([])
const searchLoading = ref(false)
const searched = ref(false)
const showBackTop = ref(false)
const savedScrollY = ref(0)

const scrollToTop = () => {
  if (listRef.value) listRef.value.scrollTo({ top: 0, behavior: 'smooth' })
}

const onScroll = () => {
  if (listRef.value) showBackTop.value = listRef.value.scrollTop > 400
}

const resultList = computed(() => {
  let list = allData.value
  if (direction.value !== '') {
    list = list.filter(i => i.direction === parseInt(direction.value) || i.direction === direction.value)
  }
  if (selectedCat.value?.value) {
    const catId = selectedCat.value.value
    list = list.filter(i => i.categoryId === catId || i.category_id === catId)
  }
  if (selectedMethod.value) {
    const m = selectedMethod.value
    list = list.filter(i => (i.pay_method || i.payMethod) === m)
  }
  return list
})

// 获取时间戳用于时间接近判断
const getTimestamp = (item) => {
  // 优先使用 create_time
  if (item.create_time) return Number(item.create_time)
  if (item.createdAt) return Number(item.createdAt)
  // 其次尝试从 trans_date 解析完整时间
  const transTime = item.trans_date || item.transDate
  if (transTime && transTime.includes(' ')) {
    return dayjs(transTime).valueOf()
  }
  return null
}

// 判断是否为虚拟卡
const isVirtual = (cardId) => {
  return cardId === 'yyyy' || cardId === 'xxxx'
}

// 判断是否为信用卡（基于 account_type 或 cardList 中的 card_type）
const isCreditCard = (item) => {
  // 优先通过 item 自身的 account_type 判断
  const acctType = item.account_type || item.accountType
  if (acctType === 'credit') return true
  // 其次通过 card_id 查找 cardList
  const cardId = item.card_id || item.cardId
  if (!cardId) return false
  const card = cardList.value.find(c => c.id === cardId)
  return card?.card_type === 'credit' || card?.cardType === 'credit'
}

// ── 转账/提现检测（优化版）──
const displayList = computed(() => {
  const items = resultList.value
  const usedExpenseIds = new Set()
  const usedIncomeIds = new Set()
  const pairs = []  // { expense, income, isExplicit, isWithdrawal }

  const getDate = (item) => (item.trans_date || item.transDate || '').slice(0, 10)
  const getCard = (item) => item.card_id || item.cardId || ''
  const getCategory = (item) => (item.pay_type || item.payType || item.category_name || item.categoryName || '')
  
  // 基础匹配条件：日期相同、金额相等、币种一致、卡片不同
  const baseMatch = (exp, inc) =>
    getDate(exp) === getDate(inc) &&
    Number(exp.amount || 0) === Number(inc.amount || 0) &&
    (exp.currency || 'CNY') === (inc.currency || 'CNY') &&
    getCard(exp) !== getCard(inc)

  // 第0趟：信用卡支出 + 现金/余额收入 + 收入类别为"冲正" → 冲正
  // （冲正优先于转账检测，避免被误配对为转账）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return
    if (!isCreditCard(item)) return  // 支出方必须是信用卡
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      if (!(inc.direction === 1 || inc.direction === 'income')) return false
      if (!baseMatch(item, inc)) return false
      // 收入方为现金或余额
      const incomeCard = getCard(inc)
      if (!isVirtual(incomeCard)) return false
      // 收入类别为冲正
      const incCat = inc.pay_type || inc.payType || inc.category_name || inc.categoryName || ''
      return incCat === '冲正'
    })
    if (match) {
      pairs.push({ expense: item, income: match, isExplicit: true, isWithdrawal: false, isReversal: true })
      usedExpenseIds.add(item.id)
      usedIncomeIds.add(match.id)
    }
  })

  // 第一趟：双方分类均为"转账" → 确诊转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return
    if (isCreditCard(item)) return  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      if (!(inc.direction === 1 || inc.direction === 'income')) return false
      if (!baseMatch(item, inc)) return false
      return getCategory(item) === '转账' && getCategory(inc) === '转账'
    })
    if (match) {
      pairs.push({ expense: item, income: match, isExplicit: true, isWithdrawal: false })
      usedExpenseIds.add(item.id)
      usedIncomeIds.add(match.id)
    }
  })

  // 第二趟：支出"其他支出" + 收入"其他收入" → 疑似转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return
    if (isCreditCard(item)) return  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      if (!(inc.direction === 1 || inc.direction === 'income')) return false
      if (!baseMatch(item, inc)) return false
      return getCategory(item) === '其他支出' && getCategory(inc) === '其他收入'
    })
    if (match) {
      pairs.push({ expense: item, income: match, isExplicit: false, isWithdrawal: false })
      usedExpenseIds.add(item.id)
      usedIncomeIds.add(match.id)
    }
  })

  // 第三趟：其余满足基础条件的 → 疑似转账（排除余额→银行卡 和 信用卡支出方）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return
    const expenseCard = getCard(item)
    const isExpenseVirtual = isVirtual(expenseCard)
    if (isCreditCard(item)) return  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      if (!(inc.direction === 1 || inc.direction === 'income')) return false
      if (!baseMatch(item, inc)) return false
      const incomeCard = getCard(inc)
      // 余额→银行卡方向排除（归入提现）
      if (isExpenseVirtual && !isVirtual(incomeCard)) return false
      return true
    })
    if (match) {
      pairs.push({ expense: item, income: match, isExplicit: false, isWithdrawal: false })
      usedExpenseIds.add(item.id)
      usedIncomeIds.add(match.id)
    }
  })

  // 第四趟：余额→银行卡（同一天同金额，时间接近）→ 提现
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return
    const expenseCard = getCard(item)
    if (expenseCard !== 'yyyy') return  // 仅限余额卡片
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      if (!(inc.direction === 1 || inc.direction === 'income')) return false
      if (!baseMatch(item, inc)) return false
      const incomeCard = getCard(inc)
      if (isVirtual(incomeCard)) return false  // 收入方不能是虚拟卡
      
      // 时间接近判断（5分钟内）
      const t1 = getTimestamp(item)
      const t2 = getTimestamp(inc)
      if (t1 && t2) {
        return Math.abs(t1 - t2) <= 300000  // 5分钟
      }
      // 没有时间信息时，默认认为是提现（降级处理）
      return true
    })
    if (match) {
      pairs.push({ expense: item, income: match, isExplicit: false, isWithdrawal: true })
      usedExpenseIds.add(item.id)
      usedIncomeIds.add(match.id)
    }
  })

  // 构建展示列表
  const list = []
  const pairMap = {}
  const pairedIncomeIds = new Set()
  pairs.forEach((p, i) => {
    pairMap[p.expense.id] = i
    pairedIncomeIds.add(p.income.id)
  })
  
  items.forEach((item) => {
    const idx = pairMap[item.id]
    if (idx !== undefined) {
      // 支出方被处理，只处理一次（按支出方展示）
      if (item.direction === 0 || item.direction === 'expense' || item.direction === 2) {
        const p = pairs[idx]
        list.push({ 
          type: p.isReversal ? 'reversal' : (p.isWithdrawal ? 'withdrawal' : 'transfer'), 
          ...p 
        })
      }
    } else if (!pairedIncomeIds.has(item.id)) {
      // 未被配对的收入方或独立流水
      list.push({ type: 'flow', data: item })
    }
  })

  return list
})

// ── 日期跳段 ──
const quickDate = ref(null)
const showQuickPicker = ref(false)
const quickPickerSelected = ref([])

const quickPickerColumns = computed(() => {
  const dates = availableDates.value
  if (dates.length === 0) return [[{ text: '暂无数据', value: '' }]]

  const minD = dayjs(dates[0])
  const maxD = dayjs(dates[dates.length - 1])
  const sel = quickPickerSelected.value

  // 年份列
  const years = []
  for (let y = minD.year(); y <= maxD.year(); y++) {
    years.push({ text: `${y}年`, value: String(y) })
  }

  // 月份列
  const sy = Number(sel[0]) || minD.year()
  const smin = sy === minD.year() ? minD.month() + 1 : 1
  const smax = sy === maxD.year() ? maxD.month() + 1 : 12
  const months = []
  for (let m = smin; m <= smax; m++) {
    months.push({ text: `${m}月`, value: String(m) })
  }

  // 日期列
  const sm = Number(sel[1]) || smin
  let dmin = 1, dmax = 31
  if (sy === minD.year() && sm === minD.month() + 1) dmin = Math.max(dmin, minD.date())
  if (sy === maxD.year() && sm === maxD.month() + 1) dmax = Math.min(dmax, maxD.date())
  const daysInMonth = dayjs(`${sy}-${String(sm).padStart(2, '0')}-01`).daysInMonth()
  dmax = Math.min(dmax, daysInMonth)
  const days = []
  for (let d = dmin; d <= dmax; d++) {
    days.push({ text: `${d}日`, value: String(d) })
  }

  return [years, months, days]
})

const openQuickPicker = () => {
  const cols = quickPickerColumns.value
  if (cols[0]?.length) {
    quickPickerSelected.value = [
      cols[0][0].value,
      cols[1]?.[0]?.value || '1',
      cols[2]?.[0]?.value || '1',
    ]
  }
  showQuickPicker.value = true
}

const onQuickDateConfirm = ({ selectedIndexes }) => {
  const cols = quickPickerColumns.value
  const y = cols[0]?.[selectedIndexes[0]]?.value
  const m = cols[1]?.[selectedIndexes[1]]?.value
  const d = cols[2]?.[selectedIndexes[2]]?.value
  if (y && m && d) {
    quickDate.value = dayjs(`${y}-${m}-${d}`).format('YYYY-MM-DD')
  }
  showQuickPicker.value = false
}

// 跳至日期后自动滚动到对应位置
watch(quickDate, () => {
  nextTick(() => {
    if (!listRef.value) return
    const date = quickDate.value
    if (!date) {
      listRef.value.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    const el = listRef.value.querySelector(`.date-header[data-date="${date}"]`)
    if (el) {
      const top = el.offsetTop - listRef.value.offsetTop
      listRef.value.scrollTo({ top, behavior: 'smooth' })
    } else {
      listRef.value.scrollTo({ top: 0, behavior: 'smooth' })
    }
  })
})

const availableDates = computed(() => {
  const dates = new Set()
  resultList.value.forEach(item => {
    dates.add((item.trans_date || item.transDate || '').slice(0, 10))
  })
  return [...dates].sort()
})

// 按快跳日期过滤后的展示列表
const filteredDisplayList = computed(() => displayList.value)

const groupedDisplayList = computed(() => {
  const groups = {}
  filteredDisplayList.value.forEach((node) => {
    let date = ''
    if (node.type === 'flow') {
      date = (node.data.trans_date || node.data.transDate || '').slice(0, 10)
    } else {
      date = (node.expense.trans_date || node.expense.transDate || '').slice(0, 10)
    }
    if (date) {
      if (!groups[date]) groups[date] = []
      groups[date].push(node)
    }
  })
  return groups
})

const dirOptions = [
  { text: '不限', value: '' },
  { text: '支出', value: '0' },
  { text: '收入', value: '1' },
]

const methodOptions = [
  { text: '不限', value: '' },
  { text: '现金', value: '现金' },
  { text: '余额', value: '余额' },
  { text: '微信支付', value: '微信支付' },
  { text: '支付宝', value: '支付宝' },
  { text: '借记卡', value: '借记卡' },
  { text: '信用卡', value: '信用卡' },
]

const dirText = computed(() => dirOptions.find(d => d.value === direction.value)?.text || '不限')
const selectedCatName = computed(() => selectedCat.value?.text || selectedCat.value?.name || '不限')

const onCatSelect = (cat) => {
  selectedCat.value = cat ? { text: cat.name, value: cat.id } : null
  showCatPicker.value = false
}

const onMethodSelect = (val) => {
  selectedMethod.value = val
  showMethodPicker.value = false
}

const payMethodLabel = (v) => {
  if (!v) return ''
  if (typeof v === 'string') return v
  return v
}

const getBankCardLabel = (item) => {
  const cardId = item.card_id || item.cardId
  if (cardId === 'yyyy') return '余额'
  if (cardId === 'xxxx') return '现金'
  if (!cardId) return (item.card_last4 || item.cardLast4) || ''
  const card = cardList.value.find((c) => c.id === cardId)
  if (!card) return (item.card_last4 || item.cardLast4) || ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  const bankName = bank?.name || card.alias || card.bank_name || ''
  const last4 = card.card_last4 || card.last4_no || card.last4No || item.card_last4 || item.cardLast4 || ''
  if (bankName && last4) return `${bankName} ${last4}`
  if (bankName) return bankName
  if (last4) return last4
  return ''
}

const formatAmount = (v) => {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

const formatDate = (v) => {
  if (!v) return ''
  return dayjs(v).format('MM-DD')
}

const formatQuickDate = (d) => {
  if (!d) return ''
  return dayjs(d).format('YYYY/M/D')
}

const formatDateHeader = (date) => {
  if (!date) return ''
  const d = dayjs(date)
  if (d.isSame(dayjs(), 'day')) return '今天'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  return d.format('YYYY年M月D日')
}

const totalIncome = computed(() =>
  resultList.value
    .filter(i => i.direction === 1 || i.direction === 'income')
    .reduce((s, i) => s + Number(i.amount || 0), 0)
)

const totalExpense = computed(() =>
  resultList.value
    .filter(i => i.direction === 0 || i.direction === 'expense' || i.direction === 2)
    .reduce((s, i) => s + Number(i.amount || 0), 0)
)

const onDateConfirm = (dates) => {
  startDate.value = dayjs(dates[0]).format('YYYY-MM-DD')
  endDate.value = dayjs(dates[1]).format('YYYY-MM-DD')
  showCalendar.value = false
  quickDate.value = null
  router.replace({ query: { ...route.query, start: startDate.value, end: endDate.value } })
  fetchData()
}

const onDirConfirm = ({ selectedOptions }) => {
  direction.value = selectedOptions[0].value
  selectedCat.value = null
  showDirPicker.value = false
}

const fetchData = async () => {
  searched.value = true
  searchLoading.value = true

  try {
    const params = { page: 1, limit: 1000, startDate: startDate.value, endDate: endDate.value }
    const res = await getAccountList(params)
    const list = res.data?.list ?? res.data ?? []
    allData.value = Array.isArray(list) ? list : []
  } catch (e) {
    showToast('查询失败')
  } finally {
    searchLoading.value = false
  }
}

const handleSearch = () => {
  quickDate.value = null
  router.replace({ query: { ...route.query, start: startDate.value, end: endDate.value } })
  fetchData()
}

const handleExport = async () => {
  if (resultList.value.length === 0) return
  const XLSX = await import('xlsx-js-style')

  const green = { font: { color: { rgb: '07c160' }, bold: true } }
  const red = { font: { color: { rgb: 'ee0a24' }, bold: true } }
  const headerStyle = { font: { bold: true, sz: 12 }, fill: { fgColor: { rgb: 'f0f0f0' } } }

  const makeSheet = (records, sheetName) => {
    const sheetData = [
      ['日期', '分类', '类型', '金额', '支付方式', '银行/卡号'].map(h => ({ v: h, s: headerStyle })),
    ]
    records.forEach((item) => {
      const isIncome = item.direction === 1 || item.direction === 'income'
      const color = isIncome ? green : red
      sheetData.push([
        { v: (item.trans_date || item.transDate || '').slice(0, 10) },
        { v: item.category_name || item.categoryName || '未分类' },
        { v: isIncome ? '收入' : '支出', s: color },
        { v: Number(item.amount || 0), s: { ...color, numFmt: '#,##0.00' } },
        { v: item.pay_method || item.payMethod || '' },
        { v: getBankCardLabel(item) },
      ])
    })
    const ws = XLSX.utils.aoa_to_sheet(sheetData)
    ws['!cols'] = [{ wch: 14 }, { wch: 12 }, { wch: 8 }, { wch: 14 }, { wch: 10 }, { wch: 12 }]
    return ws
  }

  const incomeList = resultList.value.filter(i => i.direction === 1 || i.direction === 'income')
  const expenseList = resultList.value.filter(i => i.direction === 0 || i.direction === 'expense' || i.direction === 2)

  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, makeSheet(resultList.value, '流水明细'), '流水明细')
  XLSX.utils.book_append_sheet(workbook, makeSheet(incomeList, '收入'), '收入')
  XLSX.utils.book_append_sheet(workbook, makeSheet(expenseList, '支出'), '支出')
  XLSX.writeFile(workbook, `流水筛选_${startDate.value}_${endDate.value}.xlsx`)
}

onMounted(async () => {
  fetchData()
  try {
    const [incomeRes, expenseRes, bankRes, cardRes] = await Promise.all([
      categoryApi.list('income'),
      categoryApi.list('expense'),
      categoryApi.list('bank'),
      getCardList(),
    ])
    incomeCategories.value = incomeRes.data || incomeRes || []
    expenseCategories.value = expenseRes.data || expenseRes || []
    bankList.value = bankRes.data || bankRes || []
    cardList.value = cardRes.data || cardRes || []
  } catch (e) {
    incomeCategories.value = []
    expenseCategories.value = []
  }
  nextTick(() => {
    if (listRef.value) listRef.value.addEventListener('scroll', onScroll)
  })
})

onDeactivated(() => {
  if (listRef.value) savedScrollY.value = listRef.value.scrollTop
  if (listRef.value) listRef.value.removeEventListener('scroll', onScroll)
})

onActivated(() => {
  // URL 日期范围同步
  if (route.query.start && route.query.end) {
    if (route.query.start !== startDate.value || route.query.end !== endDate.value) {
      startDate.value = route.query.start
      endDate.value = route.query.end
    }
  } else {
    const defStart = dayjs().startOf('month').format('YYYY-MM-DD')
    const defEnd = dayjs().format('YYYY-MM-DD')
    if (defStart !== startDate.value || defEnd !== endDate.value) {
      startDate.value = defStart
      endDate.value = defEnd
    }
  }

  // 同步 Detail 变更
  const changes = flowSync.consumeChanges();
  if (Object.keys(changes).length > 0) {
    allData.value = allData.value.map(item => {
      const patch = changes[item.id];
      return patch ? { ...item, ...patch } : item;
    });
  }

  nextTick(() => {
    if (savedScrollY.value > 0 && listRef.value) listRef.value.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
    if (listRef.value) listRef.value.addEventListener('scroll', onScroll)
  })
})
</script>

<style scoped>
.page-flow-filter {
  height: 90vh;
  overflow: hidden;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
}

/* Layer 1: 日期范围 + 查询 */
.top-row {
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin: 2px 8px;
}

.date-range {
  flex: 2;
  display: flex;
  align-items: stretch;
  gap: 0;
  cursor: pointer;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #f2f3f5;
}

/* 日期项 */
.date-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 4px 8px;
  background: #f7f8fa;
}

.date-start {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.date-end {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.date-sep {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  font-size: 12px;
  color: #969799;
  background: #f7f8fa;
}

/* Layer 2: 收支/分类/方式 */
.row-three {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  margin: 2px 8px;
}

.tri-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
  cursor: pointer;
  padding: 4px 2px;
  border-radius: 8px;
  background: #d0d0d02b;
  text-align: center;
  align-items: center;
}

.filter-label {
  font-size: 11px;
  color: #969799;
}

.filter-value {
  font-size: 13px;
  color: #323233;
  font-weight: 500;
}

.btn-search {
  flex: 1;
  flex-shrink: 0;
  height: auto;
  align-self: stretch;
  padding: 0 16px;
  font-size: 13px;
  border-radius: 10px;
}

.btn-export {
  flex-shrink: 0;
  font-size: 12px;
  padding: 0 12px;
  height: 32px;
  border-radius: 8px;
}

/* ── 结果 ── */
.result-area {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 16px;
  margin-top: 0;
}

.result-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: 13px;
  color: #646566;
}

/* Layer 3: 跳至 + 导出 */
.bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px;
}

.qd-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qd-label {
  font-size: 15px;
  font-weight: 700;
  color: #323233;
  flex-shrink: 0;
}

.qd-picker {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  background: #f2f3f5;
  font-size: 15px;
  font-weight: 700;
  color: #1989fa;
  cursor: pointer;
  flex-shrink: 0;
}

.qd-reset {
  font-size: 15px;
  font-weight: 700;
  color: #323233;
  cursor: pointer;
  flex-shrink: 0;
}

.summary-amount {
  font-size: 12px;
}

.summary-amount .sep {
  color: #c8c9cc;
  margin: 0 6px;
}

.flow-list {
  padding-bottom: 40px;
}

/* ── 分类/方式平铺弹窗 ── */
.category-popup {
  padding: 16px;
  max-height: 60vh;
  overflow-y: auto;
}

.category-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.category-item:active {
  background: #f7f8fa;
}

.category-item.active {
  border-color: #07c160;
  background: #f0fff5;
}

.category-item .van-icon-success {
  position: absolute;
  top: 4px;
  right: 4px;
  font-size: 14px;
}

.category-name {
  font-size: 12px;
  color: #323233;
  text-align: center;
  line-height: 1.3;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.popup-header .van-icon {
  font-size: 20px;
  color: #969799;
  cursor: pointer;
}

.flow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 10px 14px;
  margin-top: 6px;
  border-radius: 8px;
  cursor: pointer;
}

.item-left {
  flex: 1;
  min-width: 0;
}

.item-cat {
  font-size: 14px;
  color: #323233;
}

.item-desc {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.item-amount {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  text-align: right;
}

.item-amount.income {
  color: #07c160;
}

.item-amount.expense {
  color: #ee0a24;
}

.item-date {
  font-size: 11px;
  color: #969799;
  text-align: right;
  margin-top: 4px;
}

.summary-amount .income {
  color: #07c160;
}

.summary-amount .expense {
  color: #ee0a24;
}

/* ── 日隔断 ── */
.date-header {
  padding: 2px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  position: sticky;
  top: 0;
  z-index: 5;
  background: #f7f8fa;
}

/* ── 转账样式 ── */
.transfer-row {
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px dashed #1989fa;
  border-radius: 10px;
  background: #f0f7ff;
}

.transfer-header {
  font-size: 11px;
  color: #1989fa;
  font-weight: 600;
  text-align: center;
  margin-bottom: 8px;
}

.transfer-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.transfer-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.transfer-amount {
  font-size: 16px;
  font-weight: 700;
}

.transfer-amount.income {
  color: #07c160;
}

.transfer-amount.expense {
  color: #ee0a24;
}

.transfer-bank {
  font-size: 12px;
  color: #323233;
}

.transfer-arrow {
  font-size: 18px;
  color: #1989fa;
  padding: 0 10px;
  flex-shrink: 0;
}

/* ── 提现样式 ── */
.withdrawal-row {
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid #c8e6c9;
  border-radius: 10px;
  background: #e8f5e9;
}

.withdrawal-header {
  font-size: 11px;
  color: #2e7d32;
  font-weight: 600;
  text-align: center;
  margin-bottom: 8px;
}

.withdrawal-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.withdrawal-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.withdrawal-amount {
  font-size: 16px;
  font-weight: 700;
}

.withdrawal-amount.income {
  color: #2e7d32;
}

.withdrawal-amount.expense {
  color: #2e7d32;
}

.withdrawal-bank {
  font-size: 12px;
  color: #388e3c;
}

.withdrawal-arrow {
  font-size: 18px;
  color: #2e7d32;
  padding: 0 10px;
  flex-shrink: 0;
}

/* ── 冲正样式（灰色，表示已撤销/无效）── */
.reversal-row {
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px dashed #c8c9cc;
  border-radius: 10px;
  background: #f5f5f5;
  opacity: 0.72;
}

.reversal-header {
  font-size: 11px;
  color: #969799;
  font-weight: 600;
  text-align: center;
  margin-bottom: 8px;
}

.reversal-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reversal-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.reversal-amount {
  font-size: 16px;
  font-weight: 700;
}

.reversal-amount.income {
  color: #969799;
}

.reversal-amount.expense {
  color: #969799;
}

.reversal-bank {
  font-size: 12px;
  color: #c8c9cc;
}

.reversal-arrow {
  font-size: 18px;
  color: #c8c9cc;
  padding: 0 10px;
  flex-shrink: 0;
}

/* 返回顶部 */
.back-top-btn {
  position: fixed;
  right: 16px;
  bottom: 60px;
  width: 40px;
  height: 40px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #1989fa;
  z-index: 999;
  cursor: pointer;
}

.back-top-btn:active {
  opacity: 0.7;
}
</style>