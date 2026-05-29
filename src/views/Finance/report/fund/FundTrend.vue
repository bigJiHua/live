<template>
  <div class="page-fund-trend">
    <div class="filter-bar">
      <div class="fb-row">
        <van-button
          v-for="f in fundList"
          :key="f.id"
          size="small"
          round
          plain
          :type="selectedFund === f.id ? 'primary' : 'default'"
          @click="selectFund(f.id)"
        >
          {{ f.fund_name.slice(0, 4) }}
        </van-button>
      </div>
      <div class="fb-row">
        <van-button
          v-for="r in ranges"
          :key="r.key"
          size="small"
          round
          plain
          :type="range === r.key ? 'primary' : 'default'"
          @click="selectRange(r.key)"
        >
          {{ r.label }}
        </van-button>
      </div>
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <div class="chart-card" v-show="!loading">
      <div class="chart-title">{{ currentFundName }} {{ currentRangeLabel }}收益走势</div>
      <div ref="chartRef1" class="chart-box"></div>
      <div class="chart-refresh">
        <van-button size="small" plain round icon="replay" @click="refreshChart">刷新</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getFundList, getFundHistory } from '@/utils/api/fund'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

const chartRef1 = ref(null)
let chart1 = null

const loading = ref(true)
const fundList = ref([])
const selectedFund = ref('')
const range = ref('3m')

const ranges = [
  { key: '1m', label: '近1月', months: 1 },
  { key: '3m', label: '近3月', months: 3 },
  { key: '6m', label: '近6月', months: 6 },
  { key: '1y', label: '近1年', months: 12 },
  { key: 'all', label: '全部' },
]

const currentFund = computed(() => fundList.value.find(i => i.id === selectedFund.value))
const currentFundName = computed(() => currentFund.value?.fund_name || '全部')
const currentRange = computed(() => ranges.find(item => item.key === range.value) || ranges[1])
const currentRangeLabel = computed(() => currentRange.value.key === 'all' ? '全部' : currentRange.value.label)

const toNumber = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

const buildHistoryParams = () => {
  if (!currentRange.value.months) return {}
  const endDate = dayjs().format('YYYY-MM-DD')
  const startDate = dayjs().subtract(currentRange.value.months, 'month').format('YYYY-MM-DD')
  return { startDate, endDate }
}

const buildRecordPoints = (rows, rangeSummary = {}) => {
  const baseInvest = toNumber(currentFund.value?.base_invest ?? currentFund.value?.invest)
  const capitalBefore = toNumber(rangeSummary.capitalBefore)
  const profitBefore = toNumber(rangeSummary.profitBefore)
  const sortedRows = [...rows].sort((a, b) => String(a.record_date).localeCompare(String(b.record_date)))
  let currentPrincipal = baseInvest + capitalBefore
  let cumulativeProfit = profitBefore

  return sortedRows.map((row) => {
    const dailyProfit = toNumber(row.net_value)
    currentPrincipal += toNumber(row.market_val)
    cumulativeProfit += dailyProfit
    return {
      date: row.record_date,
      profit: Number(cumulativeProfit.toFixed(4)),
      marketVal: Number((currentPrincipal + cumulativeProfit).toFixed(2)),
      hasRecord: true,
    }
  })
}

const buildDisplayPoints = (recordPoints, rangeSummary = {}) => {
  if (recordPoints.length === 0) return []

  const firstPoint = recordPoints[0]
  const lastPoint = recordPoints[recordPoints.length - 1]
  const firstDate = dayjs(firstPoint.date)
  const startDate = rangeSummary.startDate ? dayjs(rangeSummary.startDate) : firstDate
  const endDate = rangeSummary.endDate ? dayjs(rangeSummary.endDate) : dayjs().startOf('day')
  const axisStart = startDate.isBefore(firstDate) ? startDate : firstDate
  const axisEnd = endDate.isAfter(dayjs(lastPoint.date)) ? endDate : dayjs(lastPoint.date)
  const pointMap = new Map(recordPoints.map((point) => [point.date, point]))
  const points = []
  let latestPoint = firstPoint

  for (let cursor = axisStart.clone(); !cursor.isAfter(axisEnd); cursor = cursor.add(1, 'day')) {
    const date = cursor.format('YYYY-MM-DD')
    const recordPoint = pointMap.get(date)
    if (recordPoint) latestPoint = recordPoint
    points.push({
      date,
      profit: latestPoint.profit,
      marketVal: latestPoint.marketVal,
      hasRecord: !!recordPoint,
      isVirtual: !recordPoint,
    })
  }

  return points
}

const calcRange = (arr) => {
  const values = arr.filter(v => Number.isFinite(Number(v)))
  if (values.length === 0) return { min: 0, max: 1 }
  let min = Math.floor(Math.min(...values))
  let max = Math.ceil(Math.max(...values))
  if (min === max) { min -= 1; max += 1 }
  return { min, max }
}

const disposeChart = () => {
  if (chart1) {
    chart1.dispose()
    chart1 = null
  }
}

const loadChart = async () => {
  if (!selectedFund.value) return
  loading.value = true
  try {
    const params = buildHistoryParams()
    const res = await getFundHistory(selectedFund.value, params)
    const rows = res.data?.list || []
    const rangeSummary = res.data?.range || {}

    if (rows.length <= 0) {
      disposeChart()
      loading.value = false
      showToast('当前周期暂无登记记录')
      return
    }

    const recordPoints = buildRecordPoints(rows, rangeSummary)
    const chartPoints = buildDisplayPoints(recordPoints, rangeSummary)
    const dates = chartPoints.map(r => r.date)
    const profits = chartPoints.map(r => r.profit)
    const marketVals = chartPoints.map(r => r.marketVal)
    const profitRange = calcRange(chartPoints.map(r => r.profit))
    const mktRange = calcRange(chartPoints.map(r => r.marketVal))

    // 先让卡片显示，保证容器有正确的尺寸
    loading.value = false
    await nextTick()

    disposeChart()
    if (!chartRef1.value) return
    chart1 = echarts.init(chartRef1.value)

    chart1.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['登记收益', '当前市值'], bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: '3%', right: '4%', bottom: '20%', top: '8%', containLabel: true },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10, rotate: 45, interval: dates.length > 60 ? Math.floor(dates.length / 8) : 'auto' } },
      yAxis: [
        { type: 'value', name: '收益', min: profitRange.min, max: profitRange.max, axisLabel: { fontSize: 10, formatter: (v) => v.toFixed(2) } },
        { type: 'value', name: '市值', min: mktRange.min, max: mktRange.max, axisLabel: { fontSize: 10, formatter: (v) => Math.abs(v) >= 10000 ? (v / 10000).toFixed(2) + '万' : v.toFixed(2) } },
      ],
      dataZoom: [{ type: 'inside', start: 0, end: 100 }],
      series: [
        { name: '登记收益', type: 'line', smooth: true, data: profits, symbol: 'none', yAxisIndex: 0, lineStyle: { width: 2 } },
        { name: '当前市值', type: 'line', smooth: true, data: marketVals, symbol: 'none', yAxisIndex: 1, lineStyle: { type: 'dashed', width: 2 } },
      ]
    })
  } catch (e) {
    loading.value = false
    showToast(e?.message || '加载失败')
  }
}

const selectFund = async (id) => {
  if (!id || id === selectedFund.value) return
  selectedFund.value = id
  await loadChart()
}

const selectRange = async (key) => {
  if (!key || key === range.value) return
  range.value = key
  await loadChart()
}

const refreshChart = () => {
  loadChart()
}

onMounted(async () => {
  try {
    const res = await getFundList()
    fundList.value = res.data?.list || []
    if (fundList.value.length > 0) {
      selectedFund.value = fundList.value[0].id
    }
  } catch (e) {
    showToast(e?.message || '加载失败')
  } finally {
    loading.value = false
    await nextTick()
    if (selectedFund.value) await loadChart()
  }
})

onBeforeUnmount(() => {
  disposeChart()
})
</script>

<style scoped>
.page-fund-trend { min-height: 100vh; background: #f7f8fa; padding: 12px 16px 30px; }
.page-loading { display: flex; justify-content: center; padding: 60px 0; }

.filter-bar { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
.fb-row { display: flex; gap: 6px; flex-wrap: wrap; }

.chart-card { background: #fff; border-radius: 10px; padding: 14px; margin-bottom: 12px; }
.chart-title { font-size: 14px; font-weight: 600; color: #323233; margin-bottom: 8px; }
.chart-box { width: 100%; height: 300px; }
.chart-refresh { display: flex; justify-content: center; padding-top: 10px; }
</style>
