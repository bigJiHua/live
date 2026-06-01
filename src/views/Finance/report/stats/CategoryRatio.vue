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
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-if="!loading">
      <div class="chart-section" v-if="categoryBreakdown.length > 0">
        <div class="section-title">
          <span>分类占比</span>
          <div class="section-right">
            <span class="section-toggle" @click="toggleAll">{{ allChecked ? '取消全选' : '全选' }}</span>
          </div>
        </div>
        <div class="category-bars" :class="{ expanded: showAll }">
          <div class="cat-bar-list">
            <div v-for="item in categoryBreakdown" :key="item.name"
              :class="['cat-bar-row', { 'off': !checkedSet.has(item.name) }]"
              @click="toggleItem(item.name)">
              <div class="cat-bar-top">
                <span class="cat-bar-dot" :style="{ background: item.color }"></span>
                <span class="cat-bar-name">{{ item.name }}</span>
                <span class="cat-bar-meta">
                  <span class="cat-bar-pct">{{ item.percent }}%</span>
                  <span class="cat-bar-value">¥{{ item.amount >= 1000 ? (item.amount / 10000).toFixed(2) + '万' : formatAmount(item.amount) }}</span>
                </span>
                <span class="cat-bar-arrow" @click.stop="showCategoryItems(item.name)">
                  <van-icon name="arrow" size="12" />
                </span>
              </div>
              <div class="cat-bar-track">
                <div class="cat-bar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
              </div>
            </div>
          </div>
          <div v-if="categoryBreakdown.length > 5" class="cat-bar-expand" @click="showAll = !showAll">
            <span>{{ showAll ? '收起' : `展开全部 (${categoryBreakdown.length}项)` }}</span>
            <van-icon :name="showAll ? 'arrow-up' : 'arrow-down'" size="10" />
          </div>
        </div>

        <!-- 三级环形饼图 - 全屏滑动 -->
        <div class="tier-section" v-if="tiers.length > 0">
          <van-swipe :loop="false" :show-indicators="tiers.length > 1" indicator-color="#1989fa" class="tier-swipe">
            <van-swipe-item v-for="(tier, idx) in tiers" :key="tier.level">
              <div class="tier-card" :class="'tier-' + tier.level">
                <div class="tier-header">
                  <span class="tier-label">{{ tier.label }}</span>
                  <span class="tier-amount">{{ formatAmount(tier.total) }}</span>
                </div>
                <div :ref="el => tierRefs[idx] = el" class="tier-chart"></div>
              </div>
            </van-swipe-item>
          </van-swipe>
        </div>
      </div>

      <van-empty v-if="!loading && categoryBreakdown.length === 0" description="暂无数据" />

      <van-popup v-model:show="showDrawer" position="bottom" round
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
      </van-popup>
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
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import * as echarts from 'echarts'
import { getAccountList } from '@/utils/api/account'

dayjs.locale(zhCn)

const showCalendar = ref(false)
const minDate = new Date(2020, 0, 1)
const maxDate = new Date()
const currentMonth = ref(dayjs().startOf('month'))
const allList = ref([])
const loading = ref(true)
const activeType = ref('all')
const showDrawer = ref(false)
const selectedCategoryName = ref('')
const drawerType = ref('all')
const tierRefs = ref([])
const tierInstances = ref([])

const categoryColors = [
  '#ee0a24', '#ff976a', '#ffb300', '#1989fa',
  '#07c160', '#7232dd', '#00bcd4', '#e91e63',
  '#009688', '#ff5722', '#607d8b', '#795548',
]

const categoryBreakdown = computed(() => {
  const items = activeType.value === 'all' ? allList.value
    : allList.value.filter((item) => item.direction === (activeType.value === 'income' ? 1 : 0))
  const map = {}
  items.forEach((item) => {
    const name = item.category_name || '未分类'
    const amt = parseFloat(item.amount) || 0
    if (!map[name]) map[name] = 0
    map[name] += amt
  })
  const entries = Object.entries(map)
    .map(([name, amount]) => ({ name, amount }))
    .sort((a, b) => b.amount - a.amount)
  const total = entries.reduce((s, e) => s + e.amount, 0)
  if (total === 0) return []
  return entries.map((item, i) => ({
    ...item,
    percent: Math.round((item.amount / total) * 100),
    color: categoryColors[i % categoryColors.length],
  }))
})

// ECharts 环形饼图 - 每个 Tier 独立渲染
const renderTierCharts = () => {
  const data = tiers.value
  tierInstances.value.forEach(inst => inst?.dispose?.())
  tierInstances.value = []
  nextTick(() => {
    data.forEach((tier, idx) => {
      const el = tierRefs.value[idx]
      if (!el) return
      const inst = echarts.init(el)
      tierInstances.value.push(inst)
      inst.setOption({
        tooltip: {
          trigger: 'item',
          confine: true,
          extraCssText: 'max-width: 160px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;',
          formatter: '{b}: ¥{c} ({d}%)'
        },
        series: [{
          type: 'pie',
          radius: ['40%', '62%'],
          center: ['50%', '48%'],
          itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: tier.categories.map(c => ({
            name: c.name,
            value: Number(c.amount.toFixed(2)),
            itemStyle: { color: c.color }
          })),
        }]
      })
    })
  })
}

// 分类勾选控制
const showAll = ref(false)
const checked = ref([])
const checkedSet = computed(() => new Set(checked.value))
const allChecked = computed(() => checked.value.length === categoryBreakdown.value.length)
const toggleAll = () => {
  if (allChecked.value) {
    checked.value = []
  } else {
    checked.value = categoryBreakdown.value.map(c => c.name)
  }
}
const toggleItem = (name) => {
  const idx = checked.value.indexOf(name)
  if (idx === -1) {
    checked.value.push(name)
  } else {
    checked.value.splice(idx, 1)
  }
}

// 过滤后的分项（用于饼图和占比计算）
const filteredBreakdown = computed(() => {
  const raw = categoryBreakdown.value
  const checkedSet = new Set(checked.value)
  const filtered = raw.filter(c => checkedSet.has(c.name))
  const total = filtered.reduce((s, c) => s + c.amount, 0)
  if (total === 0) return []
  return filtered.map(c => ({
    ...c,
    filteredPercent: Math.round((c.amount / total) * 100)
  }))
})

// 大中小后缀（跟随收入/支出筛选联动）
const tierSuffix = computed(() => {
  if (activeType.value === 'income') return '收入'
  if (activeType.value === 'expense') return '支出'
  return ''
})

// 三级分类（按占比分布：≥5%=大额，2%~5%=中等，<2%=小额）
const tiers = computed(() => {
  const items = filteredBreakdown.value
  const suffix = tierSuffix.value
  if (items.length === 0) return []
  const result = [
    { level: 'big', label: `大额${suffix}`, total: 0, categories: [] },
    { level: 'mid', label: `中等${suffix}`, total: 0, categories: [] },
    { level: 'small', label: `小额${suffix}`, total: 0, categories: [] },
  ]
  items.forEach(c => {
    const pct = c.filteredPercent
    if (pct >= 5) {
      result[0].categories.push(c); result[0].total += c.amount
    } else if (pct >= 2) {
      result[1].categories.push(c); result[1].total += c.amount
    } else {
      result[2].categories.push(c); result[2].total += c.amount
    }
  })
  return result.filter(t => t.categories.length > 0)
})

watch(tiers, () => renderTierCharts(), { immediate: true, deep: true })

const typeOptions = [
  { label: '全部', value: 'all' },
  { label: '收入', value: 'income' },
  { label: '支出', value: 'expense' },
]

const drawerTypeOptions = [
  { label: '全部', value: 'all' },
  { label: '收入', value: 'income' },
  { label: '支出', value: 'expense' },
]

const currentMonthText = computed(() => currentMonth.value.format('YYYY年MM月'))

const formatAmount = (v) => (v ? Number(v).toFixed(2) : '0.00')

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

const loadData = async () => {
  loading.value = true
  try {
    const params = { limit: 10000 }
    params.startDate = currentMonth.value.format('YYYY-MM-DD')
    params.endDate = currentMonth.value.endOf('month').format('YYYY-MM-DD')
    const res = await getAccountList(params)
    allList.value = res.data?.list || res.data || []
  } catch (e) {
    console.error('加载分类统计失败', e)
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

// 数据变化时自动全选 & 收起列表
watch(categoryBreakdown, (val) => {
  checked.value = val.map(c => c.name)
  showAll.value = false
}, { immediate: true })

onMounted(async () => {
  await loadData()
})
</script>

<style scoped>
.page-stats {
  min-height: 100vh;
  background: #f5f6fa;
  padding-bottom: 30px;
}
.chart-section {
  background: #fff;
  margin: 0 16px;
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
.category-bars { display: flex; flex-direction: column; gap: 0; }
.cat-bar-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 280px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  transition: max-height 0.3s ease;
}
.category-bars.expanded .cat-bar-list {
  max-height: none;
  overflow-y: visible;
}
.cat-bar-row {
  padding: 8px 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, opacity 0.2s;
}
.cat-bar-row:active { background: #f7f8fa; }
.cat-bar-row.off {
  opacity: 0.35;
  pointer-events: auto;
}
.cat-bar-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}
.cat-bar-dot {
  width: 8px;
  height: 8px;
  min-width: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cat-bar-row.off .cat-bar-dot {
  background: #c8c9cc !important;
}
.cat-bar-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cat-bar-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-shrink: 0;
}
.cat-bar-pct {
  font-size: 11px;
  color: #969799;
  min-width: 26px;
  text-align: right;
}
.cat-bar-value {
  font-size: 12px;
  font-weight: 600;
  color: #323233;
  font-family: 'DIN Alternate', sans-serif;
  min-width: 52px;
  text-align: right;
}
.cat-bar-track {
  height: 5px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}
.cat-bar-row.off .cat-bar-track {
  background: #f5f6f7;
}
.cat-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.cat-bar-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  color: #c8c9cc;
}
.cat-bar-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: #1989fa;
  padding: 10px 0 4px;
  cursor: pointer;
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
  color: #969799;
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
  background: #f5f6fa;
  color: #969799;
  cursor: pointer;
}
.drawer-type-tab.active {
  background: #1989fa;
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
  border-bottom: 1px solid #f5f6fa;
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
.icon-income { background: #f0fff4; color: #07c160; }
.icon-expense { background: #fff0f0; color: #ee0a24; }
.drawer-item-body { flex: 1; min-width: 0; }
.drawer-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.drawer-item-cat {
  font-size: 13px;
  font-weight: 500;
  color: #323233;
}
.drawer-item-amount {
  font-size: 14px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
}
.drawer-item-amount.income { color: #07c160; }
.drawer-item-amount.expense { color: #ee0a24; }
.drawer-item-bottom {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: #969799;
  margin-top: 3px;
}
.drawer-empty {
  text-align: center;
  color: #969799;
  font-size: 13px;
  padding: 30px 0;
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
.page-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

/* 三级饼图 - 全屏滑动 */
.tier-section {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f0;
}
.tier-swipe {
  border-radius: 10px;
  overflow: hidden;
}
.tier-card {
  padding: 14px;
  box-sizing: border-box;
}
.tier-big { background: rgba(238,10,36,0.04); border: 1px solid rgba(238,10,36,0.12); border-radius: 0; }
.tier-mid { background: rgba(255,179,0,0.04); border: 1px solid rgba(255,179,0,0.14); border-radius: 0; }
.tier-small { background: rgba(25,137,250,0.04); border: 1px solid rgba(25,137,250,0.12); border-radius: 0; }
.tier-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;
}
.tier-label {
  font-size: 14px;
  font-weight: 700;
  color: #323233;
}
.tier-amount {
  font-size: 14px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
  color: #323233;
}
.tier-chart {
  width: 100%;
  height: 240px;
}
</style>