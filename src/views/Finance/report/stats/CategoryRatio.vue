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
          <span class="section-sub">点击分类查看明细</span>
        </div>
        <div class="category-bars">
          <div v-for="item in categoryBreakdown" :key="item.name" class="cat-bar-row" @click="showCategoryItems(item.name)">
            <div class="cat-bar-label">
              <span class="cat-bar-dot" :style="{ background: item.color }"></span>
              <span class="cat-bar-name">{{ item.name }}</span>
            </div>
            <div class="cat-bar-track">
              <div class="cat-bar-fill" :style="{ width: item.percent + '%', background: item.color }"></div>
            </div>
            <div class="cat-bar-value">{{ formatAmount(item.amount) }}</div>
            <div class="cat-bar-pct">{{ item.percent }}%</div>
          </div>
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
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
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
.category-bars { display: flex; flex-direction: column; gap: 10px; }
.cat-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.cat-bar-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #323233;
  width: 76px;
  flex-shrink: 0;
}
.cat-bar-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cat-bar-name {
  min-width: 0;
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
.cat-bar-pct {
  font-size: 11px;
  color: #969799;
  width: 36px;
  text-align: right;
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
</style>