<template>
  <div ref="rootEl" class="page-assets-trend">
    <!-- 类型切换 -->
    <div class="type-tabs">
      <span
        v-for="tab in tabs"
        :key="tab.key"
        :class="['type-tab', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </span>
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-if="!loading">
      <!-- 总资产走势 -->
      <template v-if="activeTab === 'total'">
        <div class="chart-section">
          <div ref="totalChartRef" class="chart"></div>
        </div>
      </template>

      <!-- 境内资产走势 -->
      <template v-else-if="activeTab === 'balance'">
        <div class="chart-section" v-if="seriesMap.balance.length > 0">
          <div ref="balanceChartRef" class="chart"></div>
        </div>
        <van-empty v-else description="暂无境内资产登记" />
      </template>

      <!-- 境外资产走势 -->
      <template v-else-if="activeTab === 'offshore'">
        <div class="chart-section" v-if="seriesMap.offshore.length > 0">
          <div ref="offshoreChartRef" class="chart"></div>
        </div>
        <van-empty v-else description="暂无境外资产登记" />
      </template>

      <!-- 信用卡欠款走势 -->
      <template v-else-if="activeTab === 'debt'">
        <div class="chart-section" v-if="seriesMap.debt.length > 0">
          <div ref="debtChartRef" class="chart"></div>
        </div>
        <van-empty v-else description="暂无负债登记" />
      </template>

      <!-- 顶部日期栏：左按钮贴左 / 日期居中 / 右按钮贴右 -->
      <div v-if="dates.length > 0" class="quote-bar">
        <div class="quote-date">
          <button class="nav-btn" :disabled="activeIndex <= 0" @click="prevPoint" aria-label="上一期">
            <van-icon name="arrow-left" />
          </button>
          <span class="quote-date-text">{{ currentDate }}</span>
          <button class="nav-btn" :disabled="activeIndex >= dates.length - 1" @click="nextPoint" aria-label="下一期">
            <van-icon name="arrow" />
          </button>
        </div>
        <div class="quote-items" v-if="quoteItems.length > 0">
          <div v-for="(q, i) in quoteItems" :key="i" class="quote-item">
            <span class="q-label">{{ q.label }}</span>
            <span class="q-value" :class="{ neg: q.value < 0 }">{{ q.text }}</span>
          </div>
        </div>
      </div>

      <!-- 图表下方：高密度详情列表（数据与上一次登记对比升降） -->
      <div v-if="dates.length > 0" class="detail-list">
        <div class="dl-head">
          <span>项目</span>
          <span class="dl-head-r">
            <span class="dl-compare">较上期</span>
            金额（{{ activeTab === 'offshore' ? 'CNY' : '¥' }}）
          </span>
        </div>
        <div class="dl-body" v-if="detailRows.length > 0">
          <div
            v-for="(row, i) in detailRows"
            :key="i"
            class="dl-row"
          >
            <span class="dl-dot" :style="{ background: row.color }"></span>
            <span class="dl-name">{{ row.name }}</span>
            <span class="dl-value" :class="{ neg: row.value < 0 }">
              {{ row.value == null ? '--' : (activeTab === 'offshore' ? '' : '¥') + formatDetail(row.value) }}
            </span>
            <span class="dl-diff" v-if="row.diff !== null">
              <span class="dl-arrow" :class="row.diff > 0 ? 'up' : 'down'">
                {{ row.diff > 0 ? '↑' : '↓' }}
              </span>
              <span class="dl-diff-num" :class="row.diff > 0 ? 'up' : 'down'">
                {{ formatDiff(Math.abs(row.diff)) }}
              </span>
            </span>
          </div>
        </div>
        <div class="dl-body empty" v-else>该日期暂无数据</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { getRegisterList } from '@/utils/api/asset'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, DataZoomComponent, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const loading = ref(true)
const activeTab = ref('total')
const list = ref([])

const tabs = [
  { key: 'total', label: '总资产' },
  { key: 'balance', label: '境内资产' },
  { key: 'offshore', label: '境外资产' },
  { key: 'debt', label: '信用卡/负债' },
]

// 名称映射（与 List.vue 保持一致）
const getBalanceName = (key) => {
  const names = {
    wechat: '微信余额',
    alipay: '支付宝余额',
    bank: '银行活期',
    wealth: '理财',
    fund: '基金',
    stock: '股票/股市',
    profit: '收益',
    redpacket: '虚拟红包',
    cash: '现金',
    other: '其他',
  }
  return names[key] || key
}

const getOffshoreName = (key) => {
  const names = {
    ICBCA: '工商银行(港)',
    BOCA: '中国银行(港)',
    HSBC: '汇丰银行(港)',
    CMBCA: '招商银行(港)',
    Wise: 'Wise',
    ifast: 'iFast',
    IBKR: 'IBKR',
    Schwab: '盈透证券',
    OtherUSD: '其他美元',
    OtherHKD: '其他港币',
    OtherGBP: '其他英镑',
    OtherEUR: '其他欧元',
    Other: '其他',
  }
  return names[key] || key
}

const getDebtName = (key) => {
  const names = {
    ICBC: '工商银行信用卡',
    ABC: '农业银行信用卡',
    CCB: '建设银行信用卡',
    BOC: '中国银行信用卡',
    CMBC: '招商银行信用卡',
    COMM: '交通银行信用卡',
    SPDB: '浦发银行信用卡',
    CIB: '兴业银行信用卡',
    Huabei: '花呗',
    Jiebei: '借呗',
    JD: '京东白条',
    Meituan: '美团月付',
    CreditCard: '信用卡',
    Other: '其他',
  }
  return names[key] || key
}

// 取一项的名称（优先 customName，否则类型名）
const getItemName = (item, category) => {
  if (item.customName) return item.customName
  if (category === 'balance') return getBalanceName(item.type)
  if (category === 'offshore') return getOffshoreName(item.type)
  if (category === 'debt') return getDebtName(item.type)
  return item.type
}

// 项的稳定 key：优先 customName（自定义名），否则 type 粗类型
// 说明：
// - 用 id 会导致同一逻辑项（如"兴业银行"）因跨期 id 变化而分裂成多条线、中间日期丢失 → 已弃用。
// - 优先 customName 能把同名不同 id 的项正确合并为一条完整时间线（银行/境外项都带 customName）。
// - 不能只用 type：境外/负债大量项共用同一粗类型（如 OtherHKD），若都用 type 会被错误合并成一条线。
const getItemKey = (item) =>
  item.customName || item.type || 'unknown'

// 外币折合人民币（rate = 100外币 = ? CNY）
const convertOffshore = (amount, currency, rates) => {
  if (!rates || !currency) return Number(amount) || 0
  const rate = rates[currency]
  if (rate === null || rate === undefined || rate === '') return Number(amount) || 0
  return Math.round((Number(amount) * Number(rate)) / 100 * 100) / 100
}

// 统一日期转时间戳数值：兼容 YYYY-MM-DD 与时间戳（秒/毫秒）
const toTimeNum = (raw) => {
  if (raw === null || raw === undefined || raw === '') return 0
  const s = String(raw)
  if (/^\d+$/.test(s)) {
    let ts = Number(s)
    if (ts < 1e12) ts *= 1000 // 秒 → 毫秒
    return ts
  }
  const t = new Date(s).getTime()
  return isNaN(t) ? 0 : t
}

// 统一日期格式化：兼容 YYYY-MM-DD 与时间戳（毫秒）
const formatDate = (raw) => {
  if (raw === null || raw === undefined || raw === '') return '-'
  const s = String(raw)
  // 纯数字（时间戳），可能为秒或毫秒
  if (/^\d+$/.test(s)) {
    let ts = Number(s)
    if (ts < 1e12) ts *= 1000 // 秒 → 毫秒
    const d = new Date(ts)
    if (isNaN(d.getTime())) return s
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }
  return s
}

// 按时间升序（register_date 升序，同日再按 create_time）
// 必须用 toTimeNum 统一解析：register_date 可能是时间戳或 YYYY-MM-DD，直接 new Date() 会解析错乱导致轴乱序
// 同一日历日（如两次 04-29 登记）只保留当天最新一条，避免 x 轴重复日期
const chronological = computed(() => {
  const sorted = [...list.value].sort((a, b) => {
    const d = toTimeNum(a.register_date) - toTimeNum(b.register_date)
    if (d !== 0) return d
    return (Number(a.create_time) || 0) - (Number(b.create_time) || 0)
  })
  const byDay = new Map()
  sorted.forEach((r) => byDay.set(formatDate(r.register_date), r))
  return [...byDay.values()].sort(
    (a, b) => toTimeNum(a.register_date) - toTimeNum(b.register_date)
  )
})

// 日期原始值（用于 x 轴 data、indexOf 匹配、buildSeries 聚合键）
const dates = computed(() => chronological.value.map((r) => r.register_date))

// 总资产走势数据
const totalTrend = computed(() =>
  chronological.value.map((r) => ({ date: r.register_date, value: Number(r.total_balance) || 0 }))
)

// 详情区当前选中的日期索引（默认定位到最新一天）
const activeIndex = ref(0)

// 当前选中日期
const currentDate = computed(() => {
  if (dates.value.length === 0) return '-'
  const idx = Math.min(activeIndex.value, dates.value.length - 1)
  return formatDate(dates.value[idx])
})

// 当前选中日期的各 series 数值（用于下方高密度详情列表），带对应曲线颜色
// 与「上一次登记」（idx-1）对比，生成涨跌额 diff 与涨跌幅 pct（↑红涨 ↓绿跌）
const detailRows = computed(() => {
  const idx = activeIndex.value
  if (dates.value.length === 0 || idx < 0 || idx >= dates.value.length) return []
  const prevIdx = idx - 1
  if (activeTab.value === 'total') {
    const v = totalTrend.value[idx]?.value
    const pv = prevIdx >= 0 ? totalTrend.value[prevIdx]?.value : null
    const { diff, pct } = buildDiff(v, pv)
    return [{
      name: '总资产（净资产）',
      value: v == null ? null : Number(v),
      color: PALETTE[0],
      diff,
      pct,
    }]
  }
  const rows = []
  const series = seriesMap.value[activeTab.value] || []
  series.forEach((s, si) => {
    const cur = s.data[idx]
    const prev = prevIdx >= 0 ? s.data[prevIdx] : null
    const { diff, pct } = buildDiff(cur, prev)
    rows.push({
      name: s.name,
      value: cur,
      color: PALETTE[si % PALETTE.length],
      diff,
      pct,
    })
  })
  // 有值的项放前面，当前日期没有值的项沉到最底部
  return rows.sort((a, b) => {
    const av = a.value != null ? 1 : 0
    const bv = b.value != null ? 1 : 0
    return bv - av
  })
})

// 顶部指标概览：总资产时显示净资产/资产合计/欠款；其余显示当前 tab 各项数值
const quoteItems = computed(() => {
  const idx = activeIndex.value
  if (dates.value.length === 0 || idx < 0 || idx >= dates.value.length) return []
  const r = chronological.value[idx]
  if (!r) return []
  if (activeTab.value === 'total') {
    return [
      { label: '净资产', value: Number(r.total_balance) || 0 },
      { label: '资产合计', value: Number(r.total_asset) || 0 },
      { label: '信用卡欠款', value: Number(r.credit_debt) || 0 },
    ].map((x) => ({ ...x, text: '¥' + formatDetail(x.value) }))
  }
  // 其他 tab：取当前各项（可能有省略），横向高亮展示
  return detailRows.value.slice(0, 4).map((row) => ({
    label: row.name,
    value: row.value,
    text: (row.value == null ? '--' : (activeTab.value === 'offshore' ? '' : '¥') + formatDetail(row.value)),
  }))
})

const formatDetail = (v) => {
  if (v === null || v === undefined) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 计算与「上一次登记」（idx-1）的涨跌额与涨跌幅
const buildDiff = (cur, prev) => {
  if (cur == null || prev == null) return { diff: null, pct: null }
  const diff = Math.round((Number(cur) - Number(prev)) * 100) / 100
  const pct = prev !== 0 ? (Number(cur) - Number(prev)) / Math.abs(Number(prev)) * 100 : null
  return { diff, pct }
}

const formatDiff = (v) => {
  if (v === null || v === undefined) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 让图表同步到选中点：选中日不在可视窗口内时自动滚动 dataZoom 让其可见；并固定竖线高亮
// 读图表实时 dataZoom（getOption）判断窗口，兼容用户手动拖拽滑块
const syncChart = (idx, forceScroll = false) => {
  const chart = charts[activeTab.value]
  if (!chart) return
  const len = dates.value.length
  if (len <= 0) return
  const dz = chart.getOption().dataZoom
  const hasZoom = Array.isArray(dz) && dz.length > 0
  let needScroll = forceScroll
  if (hasZoom && len > 6) {
    const o = dz[0] || {}
    const s = o.startValue ?? 0
    const e = o.endValue ?? len - 1
    if (idx < s || idx > e) needScroll = true
  }
  if (needScroll && len > 6) {
    const span = Math.min(6, len)
    let start = Math.max(0, idx - Math.floor(span / 2))
    let end = Math.min(len - 1, start + span - 1)
    start = Math.max(0, end - span + 1)
    chart.dispatchAction({ type: 'dataZoom', startValue: start, endValue: end })
  }
  chart.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: idx })
}

// 用按钮精确切换选中点，并让图表 dataZoom 窗口随日期自动进退 + 高亮
const prevPoint = () => {
  if (activeIndex.value <= 0) return
  activeIndex.value--
  syncChart(activeIndex.value)
}

const nextPoint = () => {
  if (activeIndex.value >= dates.value.length - 1) return
  activeIndex.value++
  syncChart(activeIndex.value)
}

// 构建分类各项走势
const buildSeries = (category) => {
  const keyNameMap = {}
  const valueMap = {} // key -> { date: value }
  chronological.value.forEach((r) => {
    const rates = r.asset_details?.exchangeRates || {}
    const arr = r.asset_details?.[category] || []
    ;(Array.isArray(arr) ? arr : []).forEach((item) => {
      const key = getItemKey(item)
      const name = getItemName(item, category)
      keyNameMap[key] = name
      let val = Number(item.amount) || 0
      if (category === 'offshore') {
        val = convertOffshore(item.amount, item.currency, rates)
      }
      if (!valueMap[key]) valueMap[key] = {}
      // 同名项同一日期若有多笔则累加（如同名多 id、同一天登记多笔），避免覆盖丢失
      valueMap[key][r.register_date] = (valueMap[key][r.register_date] || 0) + val
    })
  })
  // 按 key 名排序，保证展示稳定
  const keys = Object.keys(valueMap).sort()
  return keys.map((key) => ({
    name: keyNameMap[key] || key,
    data: dates.value.map((d) => valueMap[key][d] ?? null),
  }))
}

const seriesMap = computed(() => ({
  balance: buildSeries('balance'),
  offshore: buildSeries('offshore'),
  debt: buildSeries('debt'),
}))

// ========== 图表渲染 ==========
const totalChartRef = ref(null)
const balanceChartRef = ref(null)
const offshoreChartRef = ref(null)
const debtChartRef = ref(null)

const charts = {}

const PALETTE = [
  '#3b82f6', '#07c160', '#ee0a24', '#f59e0b', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1',
  '#14b8a6', '#eab308', '#d946ef', '#22c55e', '#0ea5e9',
]

const formatLabel = (value) => {
  const num = Number(value) || 0
  const abs = Math.abs(num)
  if (abs >= 1e8) return (num / 1e8).toFixed(1) + '亿'
  if (abs >= 1e4) return (num / 1e4).toFixed(1) + '万'
  return String(Math.round(num))
}

const buildOption = (xData, series) => {
  const many = xData.length > 6
  // 垂直布局：图表区 → 图例 → dataZoom（自下而上），避免互相重叠
  const legendBottom = many ? 34 : 4
  const dataZoomBottom = many ? 3 : undefined
  // grid 底部预留：many(有滑块)时留图例+滑块空间；否则仅图例
  const gridBottom = many ? 54 : (series.length > 1 ? 40 : 26)
  return {
    color: PALETTE,
    // tooltip 用原生 axis + snap：竖线自动吸附到最近登记日，与下方详情（按同索引）天然对齐；
    // 监听 showTip 事件同步索引，移动端点击/拖动同样生效，无需手动像素换算（避免错位/无竖线）。
    tooltip: {
      trigger: 'axis',
      triggerOn: 'mousemove|click',
      axisPointer: { type: 'line', snap: true, lineStyle: { color: '#3b82f6', type: 'dashed', width: 1.5 } },
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.1)',
      padding: [4, 8],
      textStyle: { color: '#323233', fontSize: 11 },
      formatter: (params) => {
        const p = Array.isArray(params) ? params[0] : params
        return p ? formatDate(p.axisValue) : ''
      },
    },
    legend: {
      type: 'scroll',
      bottom: legendBottom,
      left: 'center',
      icon: 'circle',
      itemWidth: 7,
      itemHeight: 7,
      itemGap: 8,
      pageIconSize: 10,
      textStyle: { fontSize: 10, color: '#969799' },
    },
    // 去掉多余 margin：left 收紧 + containLabel 让 y 轴标签自适应不浪费左侧空间
    grid: {
      top: 8,
      right: 8,
      bottom: gridBottom,
      left: 4,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      axisTick: { alignWithLabel: true },
      axisLine: { lineStyle: { color: '#dcdee0' } },
      axisLabel: {
        color: '#969799',
        fontSize: 10,
        interval: many ? 'auto' : 0,
        rotate: many ? 30 : 0,
        hideOverlap: true,
        formatter: (v) => formatDate(v),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitNumber: 4,
      splitLine: { lineStyle: { type: 'solid', color: '#eceef1', width: 1 } },
      axisLine: { show: false },
      axisLabel: { color: '#969799', fontSize: 10, formatter: (v) => formatLabel(v) },
    },
    dataZoom: many
      ? [{
          type: 'slider',
          startValue: xData.length - 6,
          endValue: xData.length - 1,
          minValueSpan: 1,
          height: 22,
          bottom: dataZoomBottom,
          borderColor: 'transparent',
          backgroundColor: 'rgba(0,0,0,0.04)',
          fillerColor: 'rgba(25,137,250,0.16)',
          // 显示左右两端蓝色手柄，用于拖拽左右边界选定区间；
          // 隐藏中间移动手柄（moveHandleSize:0）弱化整条平移，引导用户用两端选区
          handleSize: '120%',
          handleStyle: { show: true, color: '#1989fa', borderColor: '#1989fa', borderWidth: 1 },
          moveHandleSize: 0,
          showDetail: false,
          textStyle: { fontSize: 9, color: '#969799' },
        }]
      : undefined,
    series: series.map((s) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      connectNulls: true,
      data: s.data,
      lineStyle: { width: 2 },
      // 禁用 series 的 emphasis focus 凸显——否则悬停/点击时聚焦单项会干扰竖线跟随
      emphasis: { scale: false },
      select: { disabled: true },
      areaStyle: {
        opacity: 0.08,
      },
    })),
  }
}

const ensureChart = (refName, refValue) => {
  if (!refValue) return
  if (charts[refName] && charts[refName].__el !== refValue) {
    charts[refName].dispose()
    delete charts[refName]
  }
  if (!charts[refName]) {
    charts[refName] = echarts.init(refValue)
    charts[refName].__el = refValue
  }
}

// 绑定图表交互 → 联动下方详情区
// 用原生 tooltip(axis + snap) 渲染竖线，并监听 showTip 事件把当前类别索引同步给下方详情。
// 竖线由 ECharts 内部按 snap 吸附到最近登记日，与下方详情（同一 activeIndex）天然对齐；
// 移动端点击/拖动均触发 showTip，无需手动 convertFromPixel（之前坐标换算在移动端错位、且竖线被关掉）。
const bindPointer = (chart) => {
  if (!chart) return
  chart.off('showTip')
  chart.on('showTip', (params) => {
    const p = Array.isArray(params) ? params[0] : params
    const idx = p && p.dataIndex
    if (typeof idx === 'number' && idx >= 0 && idx < dates.value.length) {
      activeIndex.value = idx
    }
  })
}

const renderAll = async () => {
  await nextTick()
  // 总资产
  if (activeTab.value === 'total' && totalChartRef.value) {
    ensureChart('total', totalChartRef.value)
    charts.total.setOption(buildOption(
      totalTrend.value.map((d) => d.date),
      [{ name: '总资产', data: totalTrend.value.map((d) => d.value) }]
    ), true)
    bindPointer(charts.total)
  }
  // 境内资产
  if (activeTab.value === 'balance' && balanceChartRef.value) {
    ensureChart('balance', balanceChartRef.value)
    charts.balance.setOption(buildOption(dates.value, seriesMap.value.balance), true)
    bindPointer(charts.balance)
  }
  // 境外资产
  if (activeTab.value === 'offshore' && offshoreChartRef.value) {
    ensureChart('offshore', offshoreChartRef.value)
    charts.offshore.setOption(buildOption(dates.value, seriesMap.value.offshore), true)
    bindPointer(charts.offshore)
  }
  // 信用卡欠款
  if (activeTab.value === 'debt' && debtChartRef.value) {
    ensureChart('debt', debtChartRef.value)
    charts.debt.setOption(buildOption(dates.value, seriesMap.value.debt), true)
    bindPointer(charts.debt)
  }
  // 定位到最新一天，并让图表 dataZoom 窗口对齐、高亮最新点
  if (dates.value.length > 0) {
    activeIndex.value = dates.value.length - 1
    await nextTick()
    syncChart(activeIndex.value)
  }
}

const resizeAll = () => {
  Object.values(charts).forEach((c) => c?.resize())
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getRegisterList()
    list.value = res.data || res || []
  } catch (e) {
    console.error('加载资产趋势失败', e)
  } finally {
    loading.value = false
  }
}

watch(activeTab, renderAll)

let ro = null
const rootEl = ref(null)

onMounted(async () => {
  // 观察页面根容器尺寸变化（竖屏/横屏、路由切换等），变化时 resize 当前图表，充分适配移动端
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(() => resizeAll())
    ro.observe(rootEl.value)
  } else {
    window.addEventListener('resize', resizeAll)
  }
  await loadData()
  await renderAll()
})

onUnmounted(() => {
  if (ro) {
    ro.disconnect()
    ro = null
  } else {
    window.removeEventListener('resize', resizeAll)
  }
  Object.values(charts).forEach((c) => c?.dispose())
  for (const k in charts) delete charts[k]
})
</script>

<style scoped>
.page-assets-trend {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 30px;
}

.type-tabs {
  display: flex;
  background: var(--theme-bg-secondary);
  padding: 12px 16px;
  gap: 8px;
  position: sticky;
  top: 0;
  z-index: 10;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.type-tab {
  font-size: 12px;
  padding: 5px 14px;
  border-radius: 14px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-tertiary);
  cursor: pointer;
  white-space: nowrap;
}

.type-tab.active {
  background: var(--theme-primary);
  color: #fff;
}

.page-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.chart-section {
  background: var(--theme-bg-secondary);
  margin: 0;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}

.chart {
  width: 100%;
  height: 40vh;
  min-height: 240px;
  max-height: 420px;
}

/* ===== 顶部指标概览条（同花顺风格） ===== */
.quote-bar {
  background: var(--theme-bg-secondary);
  border-top: 1px solid var(--theme-border);
  padding: 6px 10px 8px;
}

.quote-date {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px;
  margin-bottom: 6px;
}

.nav-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--theme-border);
  background: var(--theme-bg-tertiary);
  color: var(--theme-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.nav-btn:active {
  opacity: 0.7;
}

.nav-btn:disabled {
  opacity: 0.35;
  color: var(--theme-text-tertiary);
}

.quote-date-text {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  letter-spacing: 0.5px;
}

.quote-items {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.quote-items::-webkit-scrollbar {
  display: none;
}

.quote-item {
  flex: 1;
  min-width: 0;
  background: var(--theme-bg-tertiary);
  border-radius: 6px;
  padding: 4px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.q-label {
  font-size: 10px;
  color: var(--theme-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.q-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-primary);
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  white-space: nowrap;
}

.q-value.neg {
  color: var(--theme-danger-color);
}

/* ===== 高密度详情列表 ===== */
.detail-list {
  background: var(--theme-bg-secondary);
  border-top: 1px solid var(--theme-border);
}

.dl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  border-bottom: 1px solid var(--theme-border);
}

.dl-head-r {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dl-compare {
  font-size: 10px;
  color: var(--theme-text-tertiary);
}

.dl-body {
  max-height: 38vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.dl-body.empty {
  padding: 20px 0;
  text-align: center;
  color: var(--theme-text-tertiary);
  font-size: 12px;
}

.dl-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--theme-border);
}

.dl-row:last-child {
  border-bottom: none;
}

.dl-row:active {
  background: var(--theme-bg-tertiary);
}

.dl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dl-name {
  flex: 1;
  font-size: 12px;
  color: var(--theme-text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dl-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-primary);
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  white-space: nowrap;
}

.dl-value.neg {
  color: var(--theme-danger-color);
}

/* 较上期升降箭头 + 涨跌额（↑红涨 ↓绿跌） */
.dl-diff {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 52px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.dl-arrow {
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

.dl-arrow.up,
.dl-diff-num.up {
  color: var(--theme-danger-color);
}

.dl-arrow.down,
.dl-diff-num.down {
  color: var(--van-green, #07c160);
}

.dl-diff-num {
  font-size: 12px;
  font-weight: 600;
  font-family: 'DIN Alternate', -apple-system, sans-serif;
  white-space: nowrap;
}
</style>
