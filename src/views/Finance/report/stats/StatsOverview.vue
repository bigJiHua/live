<template>
  <div class="page-stats">
    <div class="month-bar">
      <van-icon name="arrow-left" class="month-arrow" @click="prevMonth" />
      <span class="month-text" @click="showCalendar = true">{{ currentMonthText }}</span>
      <van-icon name="arrow" class="month-arrow" @click="nextMonth" />
    </div>

    <div class="type-tabs">
      <span v-for="t in typeOptions" :key="t.value"
        :class="['type-tab', { active: activeType === t.value }]"
        @click="activeType = t.value">
        {{ t.label }}
      </span>
      <span class="filter-btn" :class="{ active: excludeTransfer }" @click="excludeTransfer = !excludeTransfer">
        {{ excludeTransfer ? '已剔除转账' : '已包含转账' }}
      </span>
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-if="!loading">
      <div class="summary-cards">
        <div class="summary-card">
          <div class="sc-icon income-icon"><van-icon name="arrow-down" /></div>
          <div class="sc-body">
            <div class="sc-label">收入</div>
            <div class="sc-value income">+{{ formatAmount(summary.income) }}</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="sc-icon expense-icon"><van-icon name="arrow-up" /></div>
          <div class="sc-body">
            <div class="sc-label">支出</div>
            <div class="sc-value expense">-{{ formatAmount(summary.expense) }}</div>
          </div>
        </div>
        <div class="summary-card">
          <div class="sc-icon" :class="summary.net >= 0 ? 'net-positive' : 'net-negative'">
            <van-icon name="chart-trending-o" />
          </div>
          <div class="sc-body">
            <div class="sc-label">结余</div>
            <div class="sc-value" :class="summary.net >= 0 ? 'income' : 'expense'">
            {{ summary.net >= 0 ? '+' : '' }}{{ formatAmount(summary.net) }}
            </div>
          </div>
        </div>
        <div class="summary-card">
          <div class="sc-icon count-icon"><van-icon name="notes-o" /></div>
          <div class="sc-body">
            <div class="sc-label">笔数</div>
            <div class="sc-value count">{{ summary.count }}</div>
          </div>
        </div>
      </div>

      <div class="extra-cards">
        <div class="extra-card">
          <div class="ec-label">日均收入</div>
          <div class="ec-value income">+{{ formatAmount(dailyIncome) }}</div>
        </div>
        <div class="extra-card">
          <div class="ec-label">日均支出</div>
          <div class="ec-value expense">-{{ formatAmount(dailyExpense) }}</div>
        </div>
        <div class="extra-card">
          <div class="ec-label">最大单笔收入</div>
          <div class="ec-value income">+{{ formatAmount(maxIncome) }}</div>
        </div>
        <div class="extra-card">
          <div class="ec-label">最大单笔支出</div>
          <div class="ec-value expense">-{{ formatAmount(maxExpense) }}</div>
        </div>
      </div>

      <div class="chart-section" v-if="categoryStats.length > 0">
        <div class="section-title">
          <span>{{ categorySectionTitle }}</span>
          <span class="section-sub">点击分类查看明细</span>
        </div>
        <div class="category-bars">
          <div v-for="item in categoryStats.slice(0, 8)" :key="item.name" class="cat-bar-row" @click="showCategoryItems(item.name)">
            <span class="cat-bar-name">{{ item.name }}</span>
            <div class="cat-bar-track">
              <div class="cat-bar-fill" :style="{ width: itemBarWidth(item) + '%', background: item.color }"></div>
            </div>
            <span class="cat-bar-value">{{ formatAmount(item.amount) }}</span>
          </div>
        </div>
      </div>

      <div class="chart-section" v-if="donutData.length > 0">
        <div class="section-title">
          <span>收支占比</span>
        </div>
        <div ref="donutRef" class="donut-chart"></div>
      </div>
    </template>

    <app-popup v-model:show="showDrawer" position="bottom" round
      :style="{ maxHeight: '75vh', minHeight: '35vh' }">
      <div class="drawer-header">
        <span class="drawer-title">{{ selectedCategoryName }}</span>
        <van-icon name="cross" class="drawer-close" @click="showDrawer = false" />
      </div>
      <div class="drawer-type-tabs">
        <span v-for="t in drawerTypeOptions" :key="t.value"
          :class="['drawer-type-tab', { active: drawerType === t.value }]"
          @click="drawerType = t.value">
          {{ t.label }}
        </span>
      </div>
      <div class="drawer-list">
        <div v-for="item in filteredDrawerItems" :key="item.id || item._key" class="drawer-item">
          <div class="drawer-item-icon" :class="item.direction === 1 ? 'icon-income' : 'icon-expense'">
            <van-icon :name="item.direction === 1 ? 'arrow-down' : 'arrow-up'" />
          </div>
          <div class="drawer-item-body">
            <div class="drawer-item-top">
              <span class="drawer-item-cat">{{ item.category_name }}</span>
              <span class="drawer-item-amount" :class="item.direction === 1 ? 'income' : 'expense'">
                {{ item.direction === 1 ? '+' : '-' }}{{ formatAmount(item.amount) }}
              </span>
            </div>
            <div class="drawer-item-bottom">
              <span class="drawer-item-date">{{ item.trans_date }}</span>
              <span class="drawer-item-remark">{{ item.remark || '无备注' }}</span>
            </div>
          </div>
        </div>
        <div v-if="filteredDrawerItems.length === 0" class="drawer-empty">暂无记录</div>
      </div>
    </app-popup>

    <van-calendar
      v-model:show="showCalendar"
      type="single"
      :min-date="minDate"
      :max-date="maxDate"
@confirm="onCalendarConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { getMoneyColors } from '@/composables/useMoneyColor'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getAccountList } from '@/utils/api/account'

dayjs.locale(zhCn)
echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const showCalendar = ref(false)
const minDate = new Date(2020, 0, 1)
const maxDate = new Date()
const currentMonth = ref(dayjs().startOf('month'))
const allList = ref([])
const loading = ref(true)
const activeType = ref('all')
const excludeTransfer = ref(false)
const donutRef = ref(null)
let donutChart = null

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '收入', value: 'income' },
  { label: '支出', value: 'expense' },
]

const currentMonthText = computed(() => currentMonth.value.format('YYYY年MM月'))
const startDate = computed(() => currentMonth.value.format('YYYY-MM-DD'))
const endDate = computed(() => currentMonth.value.endOf('month').format('YYYY-MM-DD'))

const formatAmount = (v) => {
  if (!v && v !== 0) return '0.00'
  const num = Number(v)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 3 })
}

// 基础过滤：剔除转账明细
const filteredBaseList = computed(() => {
  if (!excludeTransfer.value) return allList.value
  return allList.value.filter((item) => item.pay_type !== '转账')
})

const filteredList = computed(() => {
  if (activeType.value === 'all') return filteredBaseList.value
  if (activeType.value === 'income') return filteredBaseList.value.filter((item) => item.direction === 1)
  if (activeType.value === 'expense') return filteredBaseList.value.filter((item) => item.direction === 0)
  return filteredBaseList.value
})

const summary = computed(() => {
  let income = 0, expense = 0
  filteredList.value.forEach((item) => {
    const amt = parseFloat(item.amount) || 0
    if (item.direction === 1) income += amt
    else expense += amt
  })
  return { income, expense, net: income - expense, count: filteredList.value.length }
})

const dailyIncome = computed(() => {
  const daysInMonth = currentMonth.value.daysInMonth()
  return summary.value.income / daysInMonth
})

const dailyExpense = computed(() => {
  const daysInMonth = currentMonth.value.daysInMonth()
  return summary.value.expense / daysInMonth
})

const maxIncome = computed(() => {
  return Math.max(...filteredBaseList.value.filter((item) => item.direction === 1).map((item) => parseFloat(item.amount) || 0), 0)
})

const maxExpense = computed(() => {
  return Math.max(...filteredBaseList.value.filter((item) => item.direction === 0).map((item) => parseFloat(item.amount) || 0), 0)
})

const categoryColors = [
  '#ee0a24', '#ff976a', '#ffb300', '#1989fa',
  '#07c160', '#7232dd', '#00bcd4', '#e91e63',
  '#009688', '#ff5722', '#607d8b', '#795548',
]

const categoryStats = computed(() => {
  let items
  if (activeType.value === 'income') {
    items = filteredBaseList.value.filter((item) => item.direction === 1)
  } else {
    // 支出（含全部时默认展示支出排行）
    items = filteredBaseList.value.filter((item) => item.direction === 0)
  }
  const map = {}
  items.forEach((item) => {
    const name = item.category_name || '未分类'
    const amt = parseFloat(item.amount) || 0
    if (!map[name]) map[name] = 0
    map[name] += amt
  })
  return Object.entries(map).map(([name, amount], i) => ({
    name, amount,
    color: categoryColors[i % categoryColors.length],
  })).filter((item) => item.amount > 0).sort((a, b) => b.amount - a.amount)
})

const categorySectionTitle = computed(() =>
  activeType.value === 'income' ? '分类收入排行' : '分类支出排行'
)

// 分类明细弹窗
const showDrawer = ref(false)
const selectedCategoryName = ref('')
const drawerType = ref('all')
const drawerTypeOptions = [
  { label: '全部', value: 'all' },
  { label: '收入', value: 'income' },
  { label: '支出', value: 'expense' },
]

const filteredDrawerItems = computed(() => {
  const items = allList.value.filter((item) => {
    const catMatch = (item.category_name || '未分类') === selectedCategoryName.value
    if (!catMatch) return false
    if (drawerType.value === 'all') return true
    if (drawerType.value === 'income') return item.direction === 1
    if (drawerType.value === 'expense') return item.direction === 0
    return true
  })
  return items.map((item, idx) => ({
    ...item,
    _key: item.id || `${item.trans_date}_${idx}_${Math.random()}`,
  }))
})

const showCategoryItems = (name) => {
  selectedCategoryName.value = name
  drawerType.value = 'all'
  showDrawer.value = true
}

const maxCategoryAmount = computed(() => {
  return Math.max(...categoryStats.value.map((item) => item.amount), 1)
})

const itemBarWidth = (item) => (item.amount / maxCategoryAmount.value) * 100

const donutData = computed(() => {
  const inc = summary.value.income
  const exp = summary.value.expense
  const total = inc + exp
  if (total === 0) return []
  const mc = getMoneyColors()
  return [
    { value: inc, name: '收入', itemStyle: { color: mc['--money-income'] } },
    { value: exp, name: '支出', itemStyle: { color: mc['--money-expense'] } },
  ]
})

const loadData = async () => {
  loading.value = true
  try {
    const params = { limit: 10000 }
    params.startDate = currentMonth.value.format('YYYY-MM-DD')
    params.endDate = currentMonth.value.endOf('month').format('YYYY-MM-DD')
    const res = await getAccountList(params)
    allList.value = res.data?.list || res.data || []
  } catch (e) {
    console.error('加载统计数据失败', e)
  } finally {
    loading.value = false
  }
}

const prevMonth = () => {
  currentMonth.value = currentMonth.value.subtract(1, 'month')
  loadData()
}

const nextMonth = () => {
  currentMonth.value = currentMonth.value.add(1, 'month')
  loadData()
}

const onCalendarConfirm = (date) => {
  currentMonth.value = dayjs(date).startOf('month')
  showCalendar.value = false
  loadData()
}

const renderDonutChart = async () => {
  await nextTick()
  if (!donutRef.value || donutData.value.length === 0) {
    if (donutChart) { donutChart.dispose(); donutChart = null }
    return
  }
  if (!donutChart) donutChart = echarts.init(donutRef.value)
  donutChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const val = Number(params.value).toFixed(3).replace(/\.?0+$/, '')
        return `${params.name}: ¥${val} (${params.percent}%)`
      },
    },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}', fontSize: 12 },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: donutData.value,
    }],
  }, true)
  donutChart.resize()
}

const resizeDonut = () => donutChart?.resize()

watch([activeType, currentMonth, allList, excludeTransfer], renderDonutChart, { flush: 'post' })

onMounted(() => {
  window.addEventListener('resize', resizeDonut)
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeDonut)
  if (donutChart) { donutChart.dispose(); donutChart = null }
})
</script>

<style scoped>
.page-stats {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 30px;
}
.month-bar {
  background: var(--theme-bg-secondary);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.month-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  cursor: pointer;
}
.month-arrow {
  font-size: 18px;
  color: var(--theme-primary);
  cursor: pointer;
  padding: 4px;
}
.type-tabs {
  display: flex;
  background: var(--theme-bg-secondary);
  padding: 0 16px 12px;
  gap: 8px;
}
.type-tab {
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-tertiary);
  cursor: pointer;
}
.type-tab.active {
  background: var(--theme-primary);
  color: #fff;
}
.filter-btn {
  margin-left: auto;
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-tertiary);
  cursor: pointer;
  white-space: nowrap;
}
.filter-btn.active {
  background: var(--van-green-bg, #f0fff4);
  color: var(--van-green, #07c160);
}
.page-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.summary-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px 16px 0;
}
.summary-card {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.sc-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.income-icon { background: var(--van-green-bg, #f0fff4); color: var(--van-green, #07c160); }
.expense-icon { background: var(--van-danger-bg, #fff0f0); color: var(--van-danger-color, #ee0a24); }
.net-positive { background: var(--van-green-bg, #f0fff4); color: var(--van-green, #07c160); }
.net-negative { background: var(--van-danger-bg, #fff0f0); color: var(--van-danger-color, #ee0a24); }
.count-icon { background: #f0f5ff; color: var(--theme-primary); }
.sc-body { flex: 1; min-width: 0; }
.sc-label { font-size: 11px; color: var(--theme-text-tertiary); margin-bottom: 2px; }
.sc-value {
  font-size: 16px;
  font-weight: 700;
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sc-value.income { color: var(--money-income); }
.sc-value.expense { color: var(--money-expense); }
.sc-value.count { color: var(--theme-primary); }
.extra-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 8px 16px 0;
}
.extra-card {
  background: var(--theme-bg-secondary);
  border-radius: 10px;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.ec-label { font-size: 11px; color: var(--theme-text-tertiary); margin-bottom: 4px; }
.ec-value { font-size: 15px; font-weight: 700; font-family: 'DIN Alternate', sans-serif; }
.ec-value.income { color: var(--money-income); }
.ec-value.expense { color: var(--money-expense); }
.chart-section {
  background: var(--theme-bg-secondary);
  margin: 10px 16px 0;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--theme-text-tertiary);
}
.category-bars { display: flex; flex-direction: column; gap: 10px; }
.cat-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.cat-bar-row:active {
  opacity: 0.6;
}
.cat-bar-name {
  font-size: 12px;
  color: var(--theme-text-primary);
  width: 50px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-bar-track {
  flex: 1;
  height: 8px;
  background: var(--theme-bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
}
.cat-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}
.cat-bar-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-primary);
  width: 60px;
  text-align: right;
  font-family: 'DIN Alternate', sans-serif;
}
.donut-chart {
  width: 100%;
  height: 200px;
}
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 8px;
  font-size: 16px;
  font-weight: 600;
}
.drawer-close {
  font-size: 18px;
  color: var(--theme-text-tertiary);
  cursor: pointer;
}
.drawer-type-tabs {
  display: flex;
  padding: 0 16px 12px;
  gap: 8px;
}
.drawer-type-tab {
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-tertiary);
  cursor: pointer;
}
.drawer-type-tab.active {
  background: var(--theme-primary);
  color: #fff;
}
.drawer-list {
  padding: 0 16px 20px;
  max-height: 55vh;
  overflow-y: auto;
}
.drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid var(--theme-border);
}
.drawer-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.icon-income { background: var(--van-green-bg, #f0fff4); color: var(--van-green, #07c160); }
.icon-expense { background: var(--van-danger-bg, #fff0f0); color: var(--van-danger-color, #ee0a24); }
.drawer-item-body { flex: 1; min-width: 0; }
.drawer-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.drawer-item-cat {
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-primary);
}
.drawer-item-amount {
  font-size: 14px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
}
.drawer-item-amount.income { color: var(--money-income); }
.drawer-item-amount.expense { color: var(--money-expense); }
.drawer-item-bottom {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 3px;
}
.drawer-empty {
  text-align: center;
  color: var(--theme-text-tertiary);
  font-size: 13px;
  padding: 30px 0;
}
</style>