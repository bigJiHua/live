<template>
  <div class="page-fund-daily" v-if="!loading">
    <div class="overview-card" v-if="currentFund">
      <div class="oc-row">
        <div class="oc-item"><div class="oc-label">当前本金</div><div class="oc-value">¥{{ formatAmount(currentFund.invest) }}</div></div>
        <div class="oc-item"><div class="oc-label">累计收益</div><div class="oc-value" :class="fundProfit >= 0 ? 'success' : 'danger'">{{ fundProfit >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(fundProfit)) }}</div></div>
        <div class="oc-item"><div class="oc-label">当前市值</div><div class="oc-value primary">¥{{ formatAmount(currentFund.market_val) }}</div></div>
        <div class="oc-item"><div class="oc-label">回报率</div><div class="oc-value" :class="fundProfit >= 0 ? 'success' : 'danger'">{{ fundProfit >= 0 ? '+' : '' }}{{ formatRate(fundRate) }}</div></div>
      </div>
    </div>

    <div class="section-title">选择基金</div>
    <div class="fund-chips">
      <span
        v-for="f in fundList"
        :key="f.id"
        :class="['chip', { active: selectedFund === f.id }]"
        @click="selectFund(f.id)"
      >
        {{ f.fund_name }}
      </span>
    </div>

    <template v-if="currentFund">
      <div class="section-title">每日收益登记</div>

      <!-- 快捷跳转 -->
      <div class="stale-tip">
        <span>📅 快速跳转</span>
        <button class="stale-btn" @click="goToday">今日</button>
        <button class="stale-btn" v-if="latestRecordMonth && !isLatestMonth" @click="goLatestRecordMonth">最新</button>
        <button class="stale-btn" v-if="buyDateMonth && !isBuyMonth" @click="goBuyDateMonth">买入月</button>
      </div>

      <!-- 日历头部 -->
      <div class="cal-header">
        <van-icon name="arrow-left" @click="prevMonth" />
        <span class="cal-month-title">{{ calendarMonthTitle }}</span>
        <van-icon name="arrow" @click="nextMonth" />
      </div>
      <div class="cal-shortcuts">
        <button v-if="latestRecordMonth" type="button" class="cal-shortcut" @click="goLatestRecordMonth">收益月</button>
        <button v-if="buyDateMonth" type="button" class="cal-shortcut" @click="goBuyDateMonth">买入月</button>
      </div>
      <div class="cal-legend">
        <span class="legend-item"><span class="legend-dot normal"></span>当日收益</span>
        <span class="legend-item"><span class="legend-dot buy">🚩</span>基金买入日 / 增持</span>
      </div>
      <div class="cal-weekdays"><span v-for="w in ['一','二','三','四','五','六','日']" :key="w" class="cal-wd">{{ w }}</span></div>
      <div class="cal-grid">
        <span
          v-for="(d, idx) in calDays"
          :key="d.date || `empty-${idx}`"
          class="cal-cell"
          :class="{
            'cal-empty': !d.inMonth,
            'cal-clickable': true,
            'has-buy': d.isBuy,
            'has-record': d.hasRecord && !d.isBuy,
            'is-today': d.isToday
          }"
          @click="openDayPopup(d)"
        >
          <span class="cal-day" :class="{ 'today-badge': d.isToday }">{{ d.day }}</span>
          <span v-if="d.isBuy" class="cal-flag">🚩</span>
          <span v-if="d.earnings !== null" class="cal-earn" :class="d.earnings >= 0 ? 'up' : 'down'">{{ d.earnings >= 0 ? '+' : '' }}{{ d.earningsDisplay }}</span>
        </span>
      </div>
    </template>

    <van-empty v-else description="请先选择基金" />

    <!-- 登记/编辑弹窗 -->
    <app-popup v-model:show="showDayPopup" position="bottom" round teleport="body">
      <div class="day-popup">
        <div class="popup-header">
          <span>{{ popupDate }} {{ isEdit ? '修改记录' : '登记收益' }}</span>
          <van-icon name="cross" @click="showDayPopup = false" />
        </div>
        <div class="popup-body">
          <app-field label="日期" :model-value="popupDate" readonly />
          <app-field v-model="editNet" label="今日收益" placeholder="如 0.19 / -0.30" type="number" clearable :input-attr="{ autocomplete: 'off' }" />
          <app-field v-model="editMkt" label="增持本金" placeholder="如 5000，不增持留空" type="number" clearable :input-attr="{ autocomplete: 'off' }" />
          <div class="form-tip" v-if="!isEdit">今日收益只填当天赚亏金额；增持本金只填新投入的本金。</div>
        </div>
        <div class="popup-actions">
          <app-button v-if="isEdit" plain size="small" type="danger" :loading="deleting" @click="handleDelete">删除</app-button>
          <app-button size="small" type="primary" :loading="saving" @click="handleSave">{{ isEdit ? '保存修改' : '登记' }}</app-button>
        </div>
      </div>
    </app-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getFundList, getMonthlyHistory, addFundHistory, updateFundHistory, deleteFundHistory } from '@/utils/api/fund'
import { showConfirmDialog, showToast } from 'vant'
import dayjs from 'dayjs'

const trimDecimal = (value, digits = 3) => {
  const fixed = Number(value).toFixed(digits)
  return fixed.replace(/\.?0+$/, '')
}
const formatAmount = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  const sign = n < 0 ? '-' : ''
  const abs = Math.abs(n)
  if (abs >= 100000000) return `${sign}${trimDecimal(abs / 100000000)}亿`
  if (abs >= 10000000) return `${sign}${trimDecimal(abs / 10000000)}千万`
  if (abs >= 10000) return `${sign}${trimDecimal(abs / 10000)}万`
  return `${sign}${trimDecimal(abs)}`
}
const toNumber = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}
const formatRate = (v) => `${trimDecimal(Number(v || 0))}%`

const parseFundDate = (value) => {
  const raw = String(value || '').trim()
  if (/^\d{8}$/.test(raw)) return raw.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3')
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw
  return ''
}

const loading = ref(true)
const fundList = ref([])
const selectedFund = ref('')
const historyList = ref([])
const currentFund = computed(() => fundList.value.find(f => f.id === selectedFund.value))
const fundProfit = computed(() => toNumber(currentFund.value?.profit_delta ?? currentFund.value?.net_value))
const fundRate = computed(() => {
  const invest = toNumber(currentFund.value?.invest)
  return invest > 0 ? (fundProfit.value / invest) * 100 : 0
})

// 日历
const calendarMonth = ref(dayjs().startOf('month'))
const calendarMonthTitle = computed(() => calendarMonth.value.format('YYYY年M月'))
const today = dayjs().format('YYYY-MM-DD')
const globalLatestDate = ref('') // 后端返回的全局最新记录日期（跨月）
const latestRecordMonth = computed(() => globalLatestDate.value ? dayjs(globalLatestDate.value).startOf('month') : null)
const isLatestMonth = computed(() => {
  if (!latestRecordMonth.value) return false
  return calendarMonth.value.isSame(latestRecordMonth.value, 'month')
})
const buyDateMonth = computed(() => {
  const buyDateStr = parseFundDate(currentFund.value?.buy_date)
  return buyDateStr ? dayjs(buyDateStr).startOf('month') : null
})
const isBuyMonth = computed(() => {
  if (!buyDateMonth.value) return false
  return calendarMonth.value.isSame(buyDateMonth.value, 'month')
})

const focusCalendarMonth = () => {
  if (latestRecordMonth.value) {
    calendarMonth.value = latestRecordMonth.value
    return
  }
  if (buyDateMonth.value) {
    calendarMonth.value = buyDateMonth.value
  }
}

const prevMonth = () => { calendarMonth.value = calendarMonth.value.subtract(1, 'month'); loadHistory() }
const nextMonth = () => { calendarMonth.value = calendarMonth.value.add(1, 'month'); loadHistory() }
const goLatestRecordMonth = () => { if (latestRecordMonth.value) { calendarMonth.value = latestRecordMonth.value; loadHistory() } }
const goBuyDateMonth = () => { if (buyDateMonth.value) { calendarMonth.value = buyDateMonth.value; loadHistory() } }
const goToday = () => { calendarMonth.value = dayjs().startOf('month'); loadHistory() }

// 弹窗
const showDayPopup = ref(false)
const popupDate = ref('')
const popupRecordId = ref(null)
const editNet = ref('')
const editMkt = ref('')
const saving = ref(false)
const deleting = ref(false)
const isEdit = computed(() => !!popupRecordId.value)

const openDayPopup = (day) => {
  if (!day.inMonth) return
  popupDate.value = day.date

  // 检查是否已有记录
  const existing = historyList.value
    .filter(h => h.record_date === day.date)
    .sort((a, b) => Number(b.create_time || 0) - Number(a.create_time || 0))[0]

  if (existing) {
    popupRecordId.value = existing.id
    editNet.value = existing.net_value ?? '0'
    editMkt.value = existing.market_val ?? '0'
  } else {
    popupRecordId.value = null
    editNet.value = ''
    editMkt.value = ''
  }
  showDayPopup.value = true
}

const isBlank = (value) => value === undefined || value === null || String(value).trim() === ''
const normalizeNumber = (value) => isBlank(value) ? '0' : value

const handleSave = async () => {
  if (isBlank(editNet.value) && isBlank(editMkt.value)) {
    showToast('今日收益和增持本金至少填写一项')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateFundHistory(popupRecordId.value, {
        netValue: normalizeNumber(editNet.value),
        marketVal: normalizeNumber(editMkt.value)
      })
      showToast('修改成功')
    } else {
      await addFundHistory(selectedFund.value, {
        netValue: normalizeNumber(editNet.value),
        marketVal: normalizeNumber(editMkt.value),
        recordDate: popupDate.value,
      })
      showToast('登记成功')
    }
    showDayPopup.value = false
    await loadFundList()
    await loadHistory()
  } catch (e) {
    showToast(e?.message || e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = () => {
  if (!popupRecordId.value) return
  showConfirmDialog({ title: '确认删除', message: `确定删除 ${popupDate.value} 的记录？` })
    .then(async () => {
      deleting.value = true
      try {
        await deleteFundHistory(popupRecordId.value)
        showToast('删除成功')
        showDayPopup.value = false
        await loadFundList()
        await loadHistory()
      } catch (e) {
        showToast('删除失败')
      } finally {
        deleting.value = false
      }
    }).catch(() => {})
}

// 加载数据
const loadHistory = async () => {
  if (!selectedFund.value) return
  try {
    const y = calendarMonth.value.year()
    const m = calendarMonth.value.month() + 1
    const res = await getMonthlyHistory(selectedFund.value, y, m)
    historyList.value = res.data?.records || []
    if (res.data?.latestRecordDate) globalLatestDate.value = res.data.latestRecordDate
  } catch (e) {
    historyList.value = []
  }
}

const loadFundList = async () => {
  const res = await getFundList()
  fundList.value = res.data?.list || []
  if (!fundList.value.some(f => f.id === selectedFund.value)) {
    selectedFund.value = fundList.value[0]?.id || ''
  }
}

const selectFund = async (id) => {
  if (!id || id === selectedFund.value) return
  selectedFund.value = id
  await loadHistory()
}

// 日历格
const calDays = computed(() => {
  const month = calendarMonth.value.startOf('month')
  const daysInMonth = month.daysInMonth()
  const firstDay = month.day()
  const offset = firstDay === 0 ? 6 : firstDay - 1

  const dateMap = {}
  historyList.value.forEach((h) => {
    if (!h.record_date) return
    const exist = dateMap[h.record_date]
    if (!exist || Number(h.create_time || 0) > Number(exist.time || 0)) {
      dateMap[h.record_date] = {
        earnings: toNumber(h.net_value),
        capitalDelta: toNumber(h.market_val),
        time: h.create_time,
      }
    }
  })

  const buyDateStr = parseFundDate(currentFund.value?.buy_date)
  const cells = []
  for (let i = 0; i < offset; i++) {
    cells.push({ date: '', day: '', inMonth: false, earnings: null, earningsDisplay: '', hasRecord: false, isBuy: false, isToday: false })
  }
  for (let d = 1; d <= daysInMonth; d++) {
    const date = month.date(d).format('YYYY-MM-DD')
    const record = dateMap[date]
    const earnings = record ? record.earnings : null
    const absE = Math.abs(earnings || 0)
    cells.push({
      date,
      day: d,
      inMonth: true,
      earnings,
      earningsDisplay: earnings === null ? '' : formatAmount(absE),
      hasRecord: !!record,
      isBuy: buyDateStr === date || Math.abs(record?.capitalDelta || 0) > 0.005,
      isToday: date === today,
    })
  }
  return cells
})

onMounted(async () => {
  try {
    await loadFundList()
    if (selectedFund.value) await loadHistory()
  } catch (e) {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-fund-daily { min-height: 100vh; background: var(--theme-bg-primary); padding: 12px 16px 30px; }
.overview-card { background: var(--theme-bg-secondary); border-radius: 10px; padding: 12px 6px; margin-bottom: 20px; }
.oc-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: stretch; }
.oc-item { min-width: 0; text-align: center; padding: 0 5px; border: 1px solid var(--theme-border); }
.oc-item:last-child { border-right: 0; }
.oc-label { font-size: 10px; color: var(--theme-text-tertiary); margin-bottom: 4px; line-height: 1.15; white-space: nowrap; }
.oc-value { max-width: 100%; overflow: hidden; text-overflow: ellipsis; font-size: 14px; line-height: 1.2; font-weight: 700; font-family: 'DIN Alternate', sans-serif; white-space: nowrap; }
.oc-value.primary { color: var(--theme-primary); }
.oc-value.success { color: var(--van-danger-color, #ee0a24); }
.oc-value.danger { color: var(--van-green, #07c160); }
.section-title { font-size: 15px; font-weight: 600; color: var(--theme-text-primary); margin-bottom: 10px; padding-left: 2px; }
.fund-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.chip { font-size: 12px; padding: 6px 12px; border-radius: 16px; background: var(--theme-bg-tertiary); color: var(--theme-text-secondary); cursor: pointer; white-space: nowrap; }
.chip.active { background: var(--theme-primary); color: #fff; }

.stale-tip { display: flex; align-items: center; gap: 8px; background: var(--van-orange-bg); border: 1px solid rgba(255,151,106,0.4); border-radius: 8px; padding: 10px 14px; margin-bottom: 12px; font-size: 13px; color: var(--van-orange); }
.stale-btn { border: 1px solid var(--van-orange); background: var(--theme-bg-secondary); color: var(--van-orange); border-radius: 14px; padding: 3px 12px; font-size: 13px; font-weight: 600; cursor: pointer; }
.stale-btn:active { background: var(--van-orange); color: #fff; }

.cal-header { display: flex; align-items: center; justify-content: center; gap: 22px; margin: -2px 0 8px; color: var(--theme-text-primary); }
.cal-header .van-icon { font-size: 18px; color: var(--theme-text-secondary); cursor: pointer; padding: 4px; }
.cal-month-title { font-size: 14px; font-weight: 600; min-width: 90px; text-align: center; }
.cal-shortcuts { display: flex; justify-content: center; gap: 8px; margin: 0 0 8px; }
.cal-shortcut { border: 1px solid var(--theme-border); background: var(--theme-bg-secondary); color: var(--theme-text-primary); border-radius: 14px; padding: 4px 12px; font-size: 12px; line-height: 1.4; cursor: pointer; }
.cal-shortcut:active { background: var(--van-blue-bg, #f0f5ff); border-color: var(--van-blue, #1989fa); color: var(--theme-primary); }
.cal-weekdays { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; font-size: 11px; color: var(--theme-text-tertiary); padding: 0 0 6px; }
.cal-wd { padding: 4px 0; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.cal-cell { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 48px; border-radius: 6px; position: relative; }
.cal-empty { background: transparent; pointer-events: none; }
.cal-cell.has-buy { background: var(--van-orange-bg); }
.cal-cell.has-record:not(.has-buy) { background: var(--theme-bg-tertiary); }
.cal-cell.is-today { box-shadow: inset 0 0 0 1.5px var(--theme-primary); }
.cal-day { font-size: 12px; color: var(--theme-text-primary); font-weight: 500; }
.today-badge { color: var(--theme-primary); font-weight: 700; }
.cal-earn { font-size: 10px; margin-top: 2px; font-family: 'DIN Alternate', sans-serif; }
.cal-earn.up { color: var(--van-danger-color, #ee0a24); }
.cal-earn.down { color: var(--van-green, #07c160); }
.cal-flag { font-size: 12px; line-height: 1; margin-top: 1px; position: absolute; top: 2px; right: 3px; }
.cal-clickable { cursor: pointer; }
.cal-clickable:active { background: var(--theme-primary-light) !important; }
.cal-legend { display: flex; gap: 14px; margin-bottom: 8px; padding-left: 2px; font-size: 11px; color: var(--theme-text-secondary); }
.legend-item { display: flex; align-items: center; gap: 4px; }
.legend-dot { display: inline-block; width: 14px; text-align: center; font-size: 11px; }
.legend-dot.normal { width: 8px; height: 8px; border-radius: 50%; background: var(--theme-bg-tertiary); }

.day-popup { padding: 16px; }
.popup-header { display: flex; justify-content: space-between; align-items: center; font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.popup-header .van-icon { font-size: 20px; color: var(--theme-text-tertiary); cursor: pointer; }
.popup-body { margin-bottom: 12px; }
.form-tip { font-size: 11px; color: var(--van-orange); margin-top: 8px; background: var(--van-orange-bg); padding: 8px 10px; border-radius: 6px; line-height: 1.5; }
.popup-actions { display: flex; gap: 12px; justify-content: flex-end; }
</style>
