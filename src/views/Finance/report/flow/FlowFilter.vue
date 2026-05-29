<template>
  <div class="page-flow-filter">
    <!-- 紧凑筛选栏 -->
    <div class="filter-grid">
      <!-- 日期范围：van-calendar 连选 -->
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

      <!-- 类型/分类/方式：一行三个 -->
      <div class="row-three">
        <div class="tri-item" @click="showDirPicker = true">
          <div class="filter-label">收支</div>
          <div class="filter-value">{{ dirText }}</div>
        </div>
        <div class="tri-item" @click="showCatPicker = true">
          <div class="filter-label">分类</div>
          <div class="filter-value">{{ selectedCatName }}</div>
        </div>
        <div class="tri-item" @click="showMethodPicker = true">
          <div class="filter-label">方式</div>
          <div class="filter-value">{{ selectedMethod || '不限' }}</div>
        </div>
      </div>
    </div>

    <!-- 按钮 -->
    <div class="btn-group">
      <van-button type="primary" block round :loading="searchLoading" @click="handleSearch">查询</van-button>
      <van-button v-if="searched && resultList.length > 0" plain block round style="margin-top:8px" @click="handleExport">导出 Excel</van-button>
    </div>

    <!-- 结果 -->
    <div v-if="searched" class="result-area">
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
        <template v-for="node in displayList" :key="node.type === 'flow' ? node.data.id : 'tf-' + node.expense.id">
          <!-- 普通流水 -->
          <div
            v-if="node.type === 'flow'"
            class="flow-item"
            @click="$router.push(`/finance/flow/${node.data.id}`)"
          >
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
          <!-- 疑似转账：合并一行，左出右入 -->
          <div v-else class="transfer-row">
            <div class="transfer-header">☍ 转账</div>
            <div class="transfer-body">
              <div class="transfer-side">
                <div class="transfer-amount expense">-{{ formatAmount(node.expense.amount) }}</div>
                <div class="transfer-bank">{{ getBankCardLabel(node.expense) || '余额' }}</div>
                <div class="transfer-time">{{ formatDate(node.expense.trans_date || node.expense.transDate) }}</div>
              </div>
              <div class="transfer-arrow">→</div>
              <div class="transfer-side">
                <div class="transfer-amount income">+{{ formatAmount(node.income.amount) }}</div>
                <div class="transfer-bank">{{ getBankCardLabel(node.income) || '银行卡' }}</div>
                <div class="transfer-time">{{ formatDate(node.income.trans_date || node.income.transDate) }}</div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Pickers -->
    <van-calendar v-model:show="showCalendar" type="range" :min-date="minDate" :max-date="maxDate" @confirm="onDateConfirm" />
    <van-popup v-model:show="showDirPicker" position="bottom" round>
      <van-picker
        :columns="dirOptions"
        @confirm="onDirConfirm"
        @cancel="showDirPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showCatPicker" position="bottom" round>
      <van-picker
        :columns="catColumns"
        @confirm="onCatConfirm"
        @cancel="showCatPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showMethodPicker" position="bottom" round>
      <van-picker
        :columns="methodColumns"
        @confirm="onMethodConfirm"
        @cancel="showMethodPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import * as XLSX from 'xlsx-js-style'
import { getAccountList } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'

const startDate = ref(dayjs().startOf('month').format('YYYY-MM-DD'))
const endDate = ref(dayjs().format('YYYY-MM-DD'))
const minDate = new Date(2020, 0, 1)
const maxDate = new Date(dayjs().add(1, 'year').format('YYYY'), 11, 31)
const direction = ref('')
const selectedCat = ref(null)
const selectedMethod = ref('')
const showCalendar = ref(false)
const showDirPicker = ref(false)
const showCatPicker = ref(false)
const showMethodPicker = ref(false)
const categories = ref([])
const bankList = ref([])
const cardList = ref([])

const allData = ref([])
const searchLoading = ref(false)
const searched = ref(false)

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

// ── 疑似转账检测（beta）──
const displayList = computed(() => {
  const items = resultList.value

  // 第一趟：找所有转账配对
  const pairs = []
  const usedIncomeIds = new Set()

  items.forEach((item) => {
    const isExpense = item.direction === 0 || item.direction === 'expense' || item.direction === 2
    if (!isExpense) return
    const date = (item.trans_date || item.transDate || '').slice(0, 10)
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false
      const isInc = inc.direction === 1 || inc.direction === 'income'
      const incDate = (inc.trans_date || inc.transDate || '').slice(0, 10)
      return isInc && incDate === date &&
        Math.abs(Number(item.amount || 0) - Number(inc.amount || 0)) < 0.01 &&
        (item.card_id || item.cardId || 'none') !== (inc.card_id || inc.cardId || 'none2')
    })
    if (match) {
      pairs.push({ expense: item, income: match })
      usedIncomeIds.add(match.id)
    }
  })

  // 第二趟：构建展示列表，跳过已配对的
  const expenseIds = new Set(pairs.map(p => p.expense.id))
  const incomeIds = new Set(pairs.map(p => p.income.id))
  const list = []
  let pairIndex = 0

  items.forEach((item) => {
    if (expenseIds.has(item.id)) {
      list.push({ type: 'transfer', ...pairs[pairIndex] })
      pairIndex++
    } else if (!incomeIds.has(item.id)) {
      list.push({ type: 'flow', data: item })
    }
  })

  return list
})

const dirOptions = [
  { text: '不限', value: '' },
  { text: '支出', value: '0' },
  { text: '收入', value: '1' },
]

const dirText = computed(() => dirOptions.find(d => d.value === direction.value)?.text || '不限')
const selectedCatName = computed(() => selectedCat.value?.text || selectedCat.value?.name || '不限')

const catColumns = computed(() => [
  { text: '不限', value: '' },
  ...categories.value.map(c => ({ text: c.name, value: c.id })),
])

const methodColumns = computed(() => [
  { text: '不限', value: '' },
  ...[
    { text: '现金', value: '现金' },
    { text: '余额', value: '余额' },
    { text: '微信支付', value: '微信支付' },
    { text: '支付宝', value: '支付宝' },
    { text: '借记卡', value: '借记卡' },
    { text: '信用卡', value: '信用卡' },
  ],
])

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
  fetchData()
}

const onDirConfirm = ({ selectedOptions }) => {
  direction.value = selectedOptions[0].value
  showDirPicker.value = false
}

const onCatConfirm = ({ selectedOptions }) => {
  selectedCat.value = selectedOptions[0]
  showCatPicker.value = false
}

const onMethodConfirm = ({ selectedOptions }) => {
  selectedMethod.value = selectedOptions[0].value
  showMethodPicker.value = false
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

const handleSearch = () => fetchData()

const handleExport = () => {
  if (resultList.value.length === 0) return

  const green = { font: { color: { rgb: '07c160' }, bold: true } }
  const red = { font: { color: { rgb: 'ee0a24' }, bold: true } }
  const headerStyle = { font: { bold: true, sz: 12 }, fill: { fgColor: { rgb: 'f0f0f0' } } }

  const makeSheet = (records, sheetName) => {
    const sheetData = [
      ['日期', '分类', '类型', '金额', '银行/卡号'].map(h => ({ v: h, s: headerStyle })),
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
    ws['!cols'] = [{ wch: 14 }, { wch: 12 }, { wch: 8 }, { wch: 14 }, { wch: 12 }, { wch: 12 }]
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
    const [catRes, bankRes, cardRes] = await Promise.all([
      categoryApi.list('expense'),
      categoryApi.list('bank'),
      getCardList(),
    ])
    categories.value = catRes.data || catRes || []
    bankList.value = bankRes.data || bankRes || []
    cardList.value = cardRes.data || cardRes || []
  } catch (e) {
    categories.value = []
  }
})
</script>

<style scoped>
.page-flow-filter { min-height: 100vh; background: #f7f8fa; padding: 10px 16px; }

/* ── 紧凑筛选 ── */
.filter-grid {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 日期范围：左中右连选 */
.date-range {
  display: flex;
  align-items: stretch;
  gap: 0;
  cursor: pointer;
}
.date-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: #f7f8fa;
}
.date-start { border-top-right-radius: 0; border-bottom-right-radius: 0; }
.date-end { border-top-left-radius: 0; border-bottom-left-radius: 0; }
.date-sep {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  font-size: 12px;
  color: #969799;
  background: #f7f8fa;
}

/* 收支/分类/方式：一行三个 */
.row-three {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}
.tri-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
  padding: 8px 6px;
  border-radius: 8px;
  background: #f7f8fa;
  text-align: center;
  align-items: center;
}

.filter-label { font-size: 11px; color: #969799; }
.filter-value { font-size: 13px; color: #323233; font-weight: 500; }

/* ── 按钮 ── */
.btn-group { padding: 10px 0; }

/* ── 结果 ── */
.result-area { margin-top: 8px; }
.result-summary {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 2px; font-size: 13px; color: #646566;
}
.summary-amount { font-size: 12px; }
.summary-amount .sep { color: #c8c9cc; margin: 0 6px; }
.flow-list { padding-bottom: 40px; }
.flow-item {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; padding: 10px 14px; margin-top: 6px; border-radius: 8px; cursor: pointer;
}
.item-left { flex: 1; min-width: 0; }
.item-cat { font-size: 14px; color: #323233; }
.item-desc { font-size: 11px; color: #969799; margin-top: 2px; }
.item-amount { font-size: 15px; font-weight: 600; white-space: nowrap; text-align: right; }
.item-amount.income { color: #07c160; }
.item-amount.expense { color: #ee0a24; }
.item-date { font-size: 11px; color: #969799; text-align: right; margin-top: 4px; }
.summary-amount .income { color: #07c160; }
.summary-amount .expense { color: #ee0a24; }

/* ── 疑似转账（beta）── */
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
.transfer-amount { font-size: 16px; font-weight: 700; }
.transfer-amount.income { color: #07c160; }
.transfer-amount.expense { color: #ee0a24; }
.transfer-bank { font-size: 12px; color: #323233; }
.transfer-time { font-size: 11px; color: #969799; }
.transfer-arrow {
  font-size: 18px;
  color: #1989fa;
  padding: 0 10px;
  flex-shrink: 0;
}
</style>
