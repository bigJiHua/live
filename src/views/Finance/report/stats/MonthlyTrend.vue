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
      <span class="chart-toggle" @click="toggleChart">
        <van-icon :name="chartMode === 'bar' ? 'chart-trending-o' : 'bar-chart-o'" />
        {{ chartMode === 'bar' ? '换曲线' : '换柱状' }}
      </span>
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-if="!loading">
      <div class="chart-section" v-if="dailyTrend.length > 0">
        <div class="section-title">
          <span>每日趋势</span>
          <span class="section-sub" v-if="showBoth">
            <span class="legend-income"></span>收入
            <span class="legend-expense"></span>支出
          </span>
        </div>
        <div ref="dailyChartRef" class="daily-chart"></div>
      </div>

      <van-empty v-if="!loading && dailyTrend.length === 0" description="暂无数据" />

      <!-- 选中日期的流水明细 -->
      <div v-if="selectedDayItems.length > 0" class="day-detail-section">
        <div class="section-title day-detail-title">
          <span>{{ selectedDate }} 流水明细</span>
          <van-icon name="cross" class="close-detail" @click="clearSelectedDay" />
        </div>
        <div class="day-items">
          <div
            v-for="item in selectedDayItems"
            :key="item.id"
            class="day-item"
            @click="goDetail(item)"
          >
            <div class="di-left">
              <div class="di-cat">{{ getCategoryName(item) }}</div>
              <div class="di-desc">
                <van-image
                  v-if="getCardBankIcon(item.card_id)"
                  width="14"
                  height="14"
                  :src="getFullUrl(getCardBankIcon(item.card_id))"
                  fit="contain"
                  class="card-tag-icon"
                />
                {{ getCardInfoText(item) }}
              </div>
            </div>
            <div class="di-right">
              <div class="di-amount" :class="item.direction === 1 ? 'income' : 'expense'">
                {{ item.direction === 1 ? '+' : '-' }}{{ formatAmount(item.amount) }}
              </div>
              <div class="di-time">{{ formatTime(item.create_time) }}</div>
            </div>
          </div>
        </div>
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
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import { getAccountList } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'
import ENV from '@/utils/env'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

dayjs.locale(zhCn)
echarts.use([BarChart, LineChart, DataZoomComponent, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()

const BASE_URL = ENV.FILE_BASE_URL

const showCalendar = ref(false)
const minDate = new Date(2020, 0, 1)
const maxDate = new Date()
const currentMonth = ref(dayjs().startOf('month'))
const allList = ref([])
const loading = ref(true)
const activeType = ref('all')
const excludeTransfer = ref(false)
const chartMode = ref('bar')
const dailyChartRef = ref(null)
let dailyChart = null

const toggleChart = () => {
  chartMode.value = chartMode.value === 'bar' ? 'line' : 'bar'
}

const cardList = ref([])
const bankList = ref([])

const getFullUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  const pureBase = BASE_URL.replace(/\/+$/, '')
  const purePath = path.startsWith('/') ? path : `/${path}`
  return pureBase + purePath
}

const getBankInfo = (bankId) => {
  return bankList.value.find((b) => b.id === bankId) || null
}

const getCardTypeLabel = (cardType) => {
  const map = { credit: '信用卡', debit: '借记卡', virtual_cash: '现金', virtual_balance: '余额' }
  return map[cardType] || cardType || ''
}

const getCardBankIcon = (id) => {
  if (!id || id === 'xxxx' || id === 'yyyy') return ''
  const card = cardList.value.find((c) => c.id === id)
  if (!card) return ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? getBankInfo(bankId) : null
  return bank?.icon_url || bank?.iconUrl || ''
}

const getCardInfoText = (item) => {
  if (!item.card_id) return item.pay_method || '-'
  if (item.card_id === 'xxxx') return `现金`
  if (item.card_id === 'yyyy') return `余额`
  const card = cardList.value.find((c) => c.id === item.card_id)
  if (!card) return item.pay_method || '-'
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? getBankInfo(bankId) : null
  const bankName = bank?.name || card.alias || card.bank_name || ''
  const cardTypeLabel = getCardTypeLabel(card.cardType || card.card_type)
  const last4 = card.last4No || card.last4_no || ''
  if (bankName && cardTypeLabel && last4) return `${bankName} ${cardTypeLabel} ${last4}`
  if (bankName && last4) return `${bankName} ${last4}`
  return bankName || cardTypeLabel || last4 || item.pay_method || '-'
}

// 按日期分组的原始流水（供柱状图点击下钻）
const dayItemsMap = ref({})
const selectedDate = ref('')
const selectedDayItems = computed(() => {
  if (!selectedDate.value || !dayItemsMap.value[selectedDate.value]) return []
  let items = dayItemsMap.value[selectedDate.value]
  // 按剔除转账过滤
  if (excludeTransfer.value) {
    items = items.filter((item) => item.pay_type !== '转账')
  }
  // 按当前 activeType 过滤
  if (activeType.value !== 'all') {
    items = items.filter((item) => item.direction === (activeType.value === 'income' ? 1 : 0))
  }
  return items
})

const getCategoryName = (item) => {
  if (item.category_id === 'CATEGORY_REPAY') return '信用卡还款'
  return item.category_name || '未知分类'
}

const clearSelectedDay = () => { selectedDate.value = '' }
const goDetail = (item) => router.push(`/finance/flow/${item.id}`)

const formatTime = (ts) => (ts ? dayjs(Number(ts)).format('HH:mm') : '')

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '收入', value: 'income' },
  { label: '支出', value: 'expense' },
]

const currentMonthText = computed(() => currentMonth.value.format('YYYY年MM月'))

const formatAmount = (v) => {
  if (!v && v !== 0) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 3 })
}

const formatChartAmount = (value) => {
  const num = Number(value || 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 3 })
}

const formatChartLabelAmount = (value) => {
  const amount = Number(value || 0)
  if (amount <= 0) return ''
  if (amount >= 10000) {
    const wan = (amount / 10000).toFixed(2)
    return `${wan}万`
  }
  return amount.toLocaleString('zh-CN', { maximumFractionDigits: 3 })
}

const showBoth = computed(() => activeType.value === 'all')

// 基础过滤：剔除转账明细
const filteredBaseList = computed(() => {
  if (!excludeTransfer.value) return allList.value
  return allList.value.filter((item) => item.pay_type !== '转账')
})

const dailyTrend = computed(() => {
  const items = activeType.value === 'all' ? filteredBaseList.value
    : filteredBaseList.value.filter((item) => item.direction === (activeType.value === 'income' ? 1 : 0))

  const dayMap = {}
  items.forEach((item) => {
    const date = item.trans_date
    if (!date) return
    if (!dayMap[date]) dayMap[date] = { income: 0, expense: 0 }
    const amt = parseFloat(item.amount) || 0
    if (item.direction === 1) dayMap[date].income += amt
    else dayMap[date].expense += amt
  })

  const sorted = Object.entries(dayMap)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, val]) => ({ date, ...val }))

  if (sorted.length === 0) return []

  return sorted.map((d) => ({
    ...d,
    shortLabel: dayjs(d.date).format('D'),
  }))
})

const getDailyChartOption = () => {
  const data = dailyTrend.value
  const showCount = 5
  const isLine = chartMode.value === 'line'
  const isSingle = activeType.value !== 'all'

  const seriesBar = (name, key, color) => ({
    name,
    type: 'bar',
    barWidth: '35%',
    barGap: '15%',
    data: data.map((d) => d[key]),
    itemStyle: { color, borderRadius: [4, 4, 0, 0] },
    label: {
      show: true,
      position: 'top',
      fontFamily: '"DIN Alternate", sans-serif',
      fontSize: 10,
      color: '#323233',
      formatter: (params) => formatChartLabelAmount(params.value),
    },
  })

  const seriesLine = (name, key, color) => ({
    name,
    type: 'line',
    data: data.map((d) => d[key]),
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    lineStyle: { width: 2, color },
    itemStyle: { color },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: color + '33' },
        { offset: 1, color: color + '05' },
      ]),
    },
    label: { show: false },
  })

  const makeSeries = (name, key, color) =>
    isLine ? seriesLine(name, key, color) : seriesBar(name, key, color)

  const series = isSingle
    ? [makeSeries(
        activeType.value === 'income' ? '收入' : '支出',
        activeType.value === 'income' ? 'income' : 'expense',
        activeType.value === 'income' ? '#07c160' : '#ee0a24'
      )]
    : [
        makeSeries('收入', 'income', '#07c160'),
        makeSeries('支出', 'expense', '#ee0a24'),
      ]

  return {
    color: ['#07c160', '#ee0a24'],
    tooltip: {
      trigger: 'axis',
      enterable: true,
      axisPointer: { type: isLine ? 'cross' : 'shadow' },
      formatter: (params) => {
        const items = Array.isArray(params) ? params : [params]
        const date = data[items[0]?.dataIndex]?.date || ''
        const rows = items.map((item) => `${item.marker}${item.seriesName}: ${formatChartAmount(item.value)}`)
        const dateKey = data[items[0]?.dataIndex]?.date || ''
        return `<div style="font-size:13px;font-weight:600;margin-bottom:4px">${date}</div>`
          + rows.join('<br/>')
          + `<div style="margin-top:8px;border-top:1px solid #eee;padding-top:6px;text-align:center">
              <a data-date="${dateKey}" style="color:#1989fa;font-size:12px;text-decoration:none;cursor:pointer">📋 查看当日流水</a>
             </div>`
      },
    },
    legend: isSingle ? undefined : {
      data: ['收入', '支出'],
      bottom: 2,
      left: 'center',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { fontSize: 11, color: '#969799' },
    },
    grid: { top: 36, right: 10, bottom: data.length > showCount ? 62 : 28, left: 46, containLabel: false },
    xAxis: {
      type: 'category',
      data: data.map((item) => item.shortLabel),
      axisTick: { alignWithLabel: true },
      axisLine: { lineStyle: { color: '#dcdee0' } },
      axisLabel: { color: '#969799', fontSize: 10, interval: 0 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
      axisLabel: { color: '#969799', fontSize: 10, formatter: (value) => formatChartLabelAmount(value) },
    },
    dataZoom: data.length > showCount ? [{
      type: 'slider',
      startValue: data.length - showCount,
      endValue: data.length - 1,
      minValueSpan: showCount,
      maxValueSpan: showCount,
      height: 20,
      bottom: 32,
      borderColor: '#dcdee0',
      fillerColor: 'rgba(25,137,250,0.12)',
      handleStyle: { show: false },
      textStyle: { fontSize: 10, color: '#969799' },
    }] : undefined,
    series,
  }
}

const renderDailyChart = async () => {
  await nextTick()
  if (!dailyChartRef.value || dailyTrend.value.length === 0) {
    if (dailyChart) {
      dailyChart.dispose()
      dailyChart = null
    }
    return
  }
  if (!dailyChart) {
    dailyChart = echarts.init(dailyChartRef.value)
    // 监听 tooltip 内"查看当日流水"的点击（只绑一次）
    dailyChartRef.value.addEventListener('click', (e) => {
      const target = e.target
      if (target?.tagName === 'A' && target.dataset?.date) {
        selectedDate.value = target.dataset.date
      }
    })
  }
  // 柱状图点击 → 展示当日流水（每次渲染重新绑定）
  dailyChart.off('click')
  dailyChart.on('click', (params) => {
    const idx = params.dataIndex
    const date = dailyTrend.value[idx]?.date
    if (date) selectedDate.value = date
  })
  dailyChart.setOption(getDailyChartOption(), true)
  dailyChart.resize()
}

const resizeDailyChart = () => {
  dailyChart?.resize()
}

const loadData = async () => {
  // 销毁图表实例（loading 时 v-if 会移除图表 DOM，必须先 dispose）
  if (dailyChart) {
    dailyChart.dispose()
    dailyChart = null
  }
  loading.value = true
  try {
    const params = { limit: 10000 }
    params.startDate = currentMonth.value.format('YYYY-MM-DD')
    params.endDate = currentMonth.value.endOf('month').format('YYYY-MM-DD')
    const res = await getAccountList(params)
    const raw = res.data?.list || res.data || []
    allList.value = raw

    // 按日期分组存储原始流水（供柱状图点击下钻）
    const map = {}
    raw.forEach((item) => {
      const date = item.trans_date
      if (!date) return
      if (!map[date]) map[date] = []
      map[date].push(item)
    })
    dayItemsMap.value = map
    selectedDate.value = ''
  } catch (e) {
    console.error('加载趋势数据失败', e)
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

watch([dailyTrend, activeType, chartMode], renderDailyChart, { flush: 'post' })

onMounted(async () => {
  window.addEventListener('resize', resizeDailyChart)
  categoryApi.list('bank').then((res) => (bankList.value = res.data || res || [])).catch(() => {})
  getCardList().then((res) => (cardList.value = res.data || []))
  await loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeDailyChart)
  if (dailyChart) {
    dailyChart.dispose()
    dailyChart = null
  }
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
.chart-toggle {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 14px;
  background: #f0f4ff;
  color: #1989fa;
  cursor: pointer;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 3px;
}
.page-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
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
  margin-bottom: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-sub {
  font-size: 11px;
  font-weight: 400;
  color: #969799;
  display: flex;
  align-items: center;
  gap: 8px;
}
.legend-income,
.legend-expense {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.legend-income { background: #07c160; }
.legend-expense { background: #ee0a24; }
.daily-chart {
  width: 100%;
  height: 290px;
}

.day-detail-section {
  background: #fff;
  margin: 10px 16px 0;
  border-radius: 12px;
  padding: 0 0 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.day-detail-title {
  padding: 14px 16px 10px;
  margin-bottom: 0;
}
.close-detail {
  font-size: 18px;
  color: #969799;
  cursor: pointer;
  padding: 4px;
}
.day-items {
  border-top: 1px solid #f5f5f5;
}
.day-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.day-item:last-child { border-bottom: none; }
.di-cat { font-size: 14px; color: #323233; }
.di-desc {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}
.card-tag-icon {
  flex-shrink: 0;
  border-radius: 2px;
}
.di-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.di-amount {
  font-size: 14px;
  font-weight: 600;
  font-family: 'DIN Alternate', sans-serif;
  line-height: 1;
}
.di-amount.income { color: #07c160; }
.di-amount.expense { color: #ee0a24; }
.di-time {
  font-size: 10px;
  color: #c8c9cc;
  font-family: 'DIN Alternate', sans-serif;
}
</style>