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
          <span>分类支出排行</span>
          <span class="section-sub">金额</span>
        </div>
        <div class="category-bars">
          <div v-for="item in categoryStats.slice(0, 8)" :key="item.name" class="cat-bar-row">
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
  const expenseItems = filteredBaseList.value.filter((item) => item.direction === 0)
  const map = {}
  expenseItems.forEach((item) => {
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

const maxCategoryAmount = computed(() => {
  return Math.max(...categoryStats.value.map((item) => item.amount), 1)
})

const itemBarWidth = (item) => (item.amount / maxCategoryAmount.value) * 100

const donutData = computed(() => {
  const inc = summary.value.income
  const exp = summary.value.expense
  const total = inc + exp
  if (total === 0) return []
  return [
    { value: inc, name: '收入', itemStyle: { color: '#07c160' } },
    { value: exp, name: '支出', itemStyle: { color: '#ee0a24' } },
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
  background: #f5f6fa;
  padding-bottom: 30px;
}
.month-bar {
  background: #fff;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.month-text {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  cursor: pointer;
}
.month-arrow {
  font-size: 18px;
  color: #1989fa;
  cursor: pointer;
  padding: 4px;
}
.type-tabs {
  display: flex;
  background: #fff;
  padding: 0 16px 12px;
  gap: 8px;
}
.type-tab {
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: #f5f6fa;
  color: #969799;
  cursor: pointer;
}
.type-tab.active {
  background: #1989fa;
  color: #fff;
}
.filter-btn {
  margin-left: auto;
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: #f5f6fa;
  color: #969799;
  cursor: pointer;
  white-space: nowrap;
}
.filter-btn.active {
  background: #f0fff4;
  color: #07c160;
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
  background: #fff;
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
.income-icon { background: #f0fff4; color: #07c160; }
.expense-icon { background: #fff0f0; color: #ee0a24; }
.net-positive { background: #f0fff4; color: #07c160; }
.net-negative { background: #fff0f0; color: #ee0a24; }
.count-icon { background: #f0f5ff; color: #1989fa; }
.sc-body { flex: 1; min-width: 0; }
.sc-label { font-size: 11px; color: #969799; margin-bottom: 2px; }
.sc-value {
  font-size: 16px;
  font-weight: 700;
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sc-value.income { color: #07c160; }
.sc-value.expense { color: #ee0a24; }
.sc-value.count { color: #1989fa; }
.extra-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 8px 16px 0;
}
.extra-card {
  background: #fff;
  border-radius: 10px;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.ec-label { font-size: 11px; color: #969799; margin-bottom: 4px; }
.ec-value { font-size: 15px; font-weight: 700; font-family: 'DIN Alternate', sans-serif; }
.ec-value.income { color: #07c160; }
.ec-value.expense { color: #ee0a24; }
.chart-section {
  background: #fff;
  margin: 10px 16px 0;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-sub {
  font-size: 11px;
  font-weight: 400;
  color: #969799;
}
.category-bars { display: flex; flex-direction: column; gap: 10px; }
.cat-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cat-bar-name {
  font-size: 12px;
  color: #323233;
  width: 50px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-bar-track {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
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
  color: #323233;
  width: 60px;
  text-align: right;
  font-family: 'DIN Alternate', sans-serif;
}
.donut-chart {
  width: 100%;
  height: 200px;
}
</style>