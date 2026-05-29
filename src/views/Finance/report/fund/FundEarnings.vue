<template>
  <div class="page-fund-earnings" v-if="!loading">
    <div class="overview-card">
      <div class="oc-row">
        <div class="oc-item"><div class="oc-label">当前本金</div><div class="oc-value">¥{{ formatAmount(totalInvested) }}</div></div>
        <div class="oc-item"><div class="oc-label">当前市值</div><div class="oc-value primary">¥{{ formatAmount(totalMarketVal) }}</div></div>
        <div class="oc-item"><div class="oc-label">累计收益</div><div class="oc-value" :class="totalEarnings >= 0 ? 'success' : 'danger'">{{ totalEarnings >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(totalEarnings)) }}</div></div>
        <div class="oc-item"><div class="oc-label">总回报率</div><div class="oc-value" :class="totalRate >= 0 ? 'success' : 'danger'">{{ totalRate >= 0 ? '+' : '' }}{{ formatRate(totalRate) }}</div></div>
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

    <div v-if="currentFund" class="earning-card">
      <div class="ec-header">
        <div class="ec-title">
          <span class="ec-name">{{ currentFund.fund_name }}</span>
          <span class="ec-company">{{ currentFund.fund_company || '基金公司未填' }}</span>
        </div>
        <div class="ec-rate" :class="getFundRate(currentFund) >= 0 ? 'success' : 'danger'">
          {{ formatRate(getFundRate(currentFund)) }}
        </div>
      </div>
      <div class="ec-body">
        <div class="ec-profit-block">
          <span class="ec-label">累计收益</span>
          <span class="ec-profit-main" :class="getFundProfit(currentFund) >= 0 ? 'success' : 'danger'">
            {{ getFundProfit(currentFund) >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(getFundProfit(currentFund))) }}
          </span>
        </div>
        <div class="ec-metrics">
          <div class="ec-metric">
            <span class="ec-label">当前市值</span>
            <span class="ec-value">¥{{ formatAmount(getFundMarketVal(currentFund)) }}</span>
          </div>
          <div class="ec-metric">
            <span class="ec-label">当前本金</span>
            <span class="ec-value">¥{{ formatAmount(currentFund.invest) }}</span>
          </div>
          <div class="ec-metric">
            <span class="ec-label">初始本金</span>
            <span class="ec-value">¥{{ formatAmount(currentFund.base_invest ?? currentFund.invest) }}</span>
          </div>
          <div class="ec-metric">
            <span class="ec-label">持有份额</span>
            <span class="ec-value">{{ safeNum(currentFund.share) }}</span>
          </div>
        </div>
        <div class="ec-meta">
          <span>购入 {{ formatFundDate(currentFund.buy_date) }}</span>
          <span v-if="currentFund.sell_org">{{ currentFund.sell_org }}</span>
          <span v-if="currentFund.fund_account">账户 {{ currentFund.fund_account }}</span>
        </div>
      </div>
      <div class="ec-bar">
        <div class="ec-bar-track">
          <div class="ec-bar-fill" :class="getFundRate(currentFund) >= 0 ? 'success' : 'danger'" :style="{ width: Math.min(Math.abs(getFundRate(currentFund)), 30) + '%' }"></div>
        </div>
      </div>
    </div>

    <div class="section-title">每日收益日历</div>
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
          'cal-clickable': d.hasRecord,
          'has-buy': d.isBuy,
          'has-record': d.hasRecord && !d.isBuy
        }"
        @click="d.hasRecord ? openDayDetail(d) : null"
      >
        <span class="cal-day">{{ d.day }}</span>
        <span v-if="d.isBuy" class="cal-flag">🚩</span>
        <span v-if="d.earnings !== null" class="cal-earn" :class="d.earnings >= 0 ? 'up' : 'down'">{{ d.earnings >= 0 ? '+' : '' }}{{ d.earningsDisplay }}</span>
      </span>
    </div>

    <van-popup v-model:show="showDayPopup" position="bottom" round>
      <div class="day-popup" v-if="selectedRecord">
        <div class="popup-header">
          <span>{{ selectedRecord.record_date }} 记录</span>
          <van-icon name="cross" @click="showDayPopup = false" />
        </div>
        <div class="popup-body">
          <van-field label="日期" :model-value="selectedRecord.record_date" readonly />
          <van-field v-model="editNet" label="今日收益" placeholder="如 0.19 / -0.30" type="number" clearable :input-attr="{ autocomplete: 'off' }" />
          <van-field v-model="editMkt" label="增持本金" placeholder="如 5000，不增持填 0" type="number" clearable :input-attr="{ autocomplete: 'off' }" />
        </div>
        <div class="popup-actions">
          <van-button plain size="small" type="danger" :loading="deleting" @click="handleDelete">删除</van-button>
          <van-button size="small" type="primary" :loading="saving" @click="handleUpdate">保存修改</van-button>
        </div>
      </div>
    </van-popup>

    <van-empty v-if="fundList.length === 0" description="暂无数据" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getFundList, getFundHistory, updateFundHistory, deleteFundHistory } from '@/utils/api/fund'
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
const safeNum = (v, digits = 2) => {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(digits) : '0.00'
}
const toNumber = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

const loading = ref(true)
const fundList = ref([])
const selectedFund = ref('')
const historyList = ref([])
const currentFund = computed(() => fundList.value.find(f => f.id === selectedFund.value))
const calendarMonth = ref(dayjs().startOf('month'))
const calendarMonthTitle = computed(() => calendarMonth.value.format('YYYY年M月'))
const latestRecord = computed(() => historyList.value.length > 0 ? historyList.value[historyList.value.length - 1] : null)
const latestRecordMonth = computed(() => latestRecord.value?.record_date ? dayjs(latestRecord.value.record_date).startOf('month') : null)
const buyDateMonth = computed(() => {
  const buyDateStr = parseFundDate(currentFund.value?.buy_date)
  return buyDateStr ? dayjs(buyDateStr).startOf('month') : null
})

const totalInvested = computed(() => fundList.value.reduce((s, f) => s + toNumber(f.invest), 0))
const totalMarketVal = computed(() => fundList.value.reduce((s, f) => s + getFundMarketVal(f), 0))
const totalEarnings = computed(() => fundList.value.reduce((s, f) => s + getFundProfit(f), 0))
const totalRate = computed(() => totalInvested.value > 0 ? (totalEarnings.value / totalInvested.value) * 100 : 0)

const getFundMarketVal = (f) => toNumber(f?.market_val)
const getFundProfit = (f) => toNumber(f?.profit_delta ?? f?.net_value)
const getFundRate = (f) => {
  const invest = toNumber(f?.invest)
  return invest > 0 ? (getFundProfit(f) / invest) * 100 : 0
}
const formatRate = (v) => `${trimDecimal(Number(v || 0))}%`

const parseFundDate = (value) => {
  const raw = String(value || '').trim()
  if (/^\d{8}$/.test(raw)) return raw.replace(/^(\d{4})(\d{2})(\d{2})$/, '$1-$2-$3')
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw
  return ''
}
const formatFundDate = (value) => parseFundDate(value) || '-'

const focusCalendarMonth = () => {
  if (latestRecordMonth.value) {
    calendarMonth.value = latestRecordMonth.value
    return
  }
  if (buyDateMonth.value) {
    calendarMonth.value = buyDateMonth.value
  }
}

const prevMonth = () => {
  calendarMonth.value = calendarMonth.value.subtract(1, 'month')
}

const nextMonth = () => {
  calendarMonth.value = calendarMonth.value.add(1, 'month')
}

const goLatestRecordMonth = () => {
  if (latestRecordMonth.value) calendarMonth.value = latestRecordMonth.value
}

const goBuyDateMonth = () => {
  if (buyDateMonth.value) calendarMonth.value = buyDateMonth.value
}

const showDayPopup = ref(false)
const selectedRecord = ref(null)
const editNet = ref('')
const editMkt = ref('')
const saving = ref(false)
const deleting = ref(false)

const openDayDetail = (day) => {
  const record = historyList.value.find(h => h.record_date === day.date)
  if (!record) return
  selectedRecord.value = record
  editNet.value = record.net_value ?? '0'
  editMkt.value = record.market_val ?? '0'
  showDayPopup.value = true
}

const isBlank = (value) => value === undefined || value === null || String(value).trim() === ''
const normalizeNumber = (value) => isBlank(value) ? '0' : value

const handleUpdate = async () => {
  if (!selectedRecord.value) return
  if (isBlank(editNet.value) && isBlank(editMkt.value)) {
    showToast('今日收益和增持本金至少填写一项')
    return
  }
  saving.value = true
  try {
    await updateFundHistory(selectedRecord.value.id, {
      netValue: normalizeNumber(editNet.value),
      marketVal: normalizeNumber(editMkt.value)
    })
    showToast('修改成功')
    showDayPopup.value = false
    await loadFundList()
    await loadHistory()
  } catch (e) {
    showToast(e?.message || e?.response?.data?.message || '修改失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = () => {
  if (!selectedRecord.value) return
  showConfirmDialog({ title: '确认删除', message: `确定删除 ${selectedRecord.value.record_date} 的记录？` })
    .then(async () => {
      deleting.value = true
      try {
        await deleteFundHistory(selectedRecord.value.id)
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

const loadHistory = async () => {
  if (!selectedFund.value) return
  try {
    const res = await getFundHistory(selectedFund.value, 365)
    historyList.value = res.data?.list || []
    focusCalendarMonth()
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
    cells.push({ date: '', day: '', inMonth: false, earnings: null, earningsDisplay: '', hasRecord: false, isBuy: false })
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
.page-fund-earnings { min-height: 100vh; background: #f7f8fa; padding: 12px 16px 30px; }
.overview-card { background: #fff; border-radius: 10px; padding: 12px 6px; margin-bottom: 20px; }
.oc-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); align-items: stretch; }
.oc-item { min-width: 0; text-align: center; padding: 0 5px; border-right: 1px solid #f0f0f0; }
.oc-item:last-child { border-right: 0; }
.oc-label { font-size: 10px; color: #969799; margin-bottom: 4px; line-height: 1.15; white-space: nowrap; }
.oc-value { max-width: 100%; overflow: hidden; text-overflow: ellipsis; font-size: 14px; line-height: 1.2; font-weight: 700; font-family: 'DIN Alternate', sans-serif; white-space: nowrap; }
.oc-value.primary { color: #1989fa; }
.oc-value.success { color: #ee0a24; }
.oc-value.danger { color: #07c160; }
.section-title { font-size: 15px; font-weight: 600; color: #323233; margin-bottom: 10px; padding-left: 2px; }
.fund-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.chip { font-size: 12px; padding: 6px 12px; border-radius: 16px; background: #f5f6fa; color: #646566; cursor: pointer; white-space: nowrap; }
.chip.active { background: #1989fa; color: #fff; }
.earning-card { background: #fff; border-radius: 10px; padding: 14px 16px; margin-bottom: 12px; }
.ec-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.ec-title { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.ec-name { font-size: 15px; line-height: 1.35; font-weight: 600; color: #323233; word-break: break-all; }
.ec-company { font-size: 11px; color: #969799; }
.ec-rate { flex: 0 0 auto; min-width: 58px; padding: 3px 8px; border-radius: 999px; font-size: 13px; line-height: 1.3; text-align: center; font-family: 'DIN Alternate', sans-serif; font-weight: 700; background: #f7f8fa; }
.ec-rate.success { color: #ee0a24; background: #fff1f0; }
.ec-rate.danger { color: #07c160; background: #eefaf3; }
.ec-body { display: flex; flex-direction: column; gap: 12px; }
.ec-profit-block { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding-bottom: 2px; }
.ec-label { font-size: 11px; color: #969799; line-height: 1.2; }
.ec-profit-main { font-family: 'DIN Alternate', sans-serif; font-size: 24px; line-height: 1; font-weight: 700; white-space: nowrap; }
.ec-profit-main.success { color: #ee0a24; }
.ec-profit-main.danger { color: #07c160; }
.ec-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 12px; }
.ec-metric { min-width: 0; padding: 9px 10px; border-radius: 8px; background: #f7f8fa; }
.ec-value { display: block; margin-top: 4px; font-size: 15px; line-height: 1.2; color: #323233; font-family: 'DIN Alternate', sans-serif; font-weight: 700; word-break: break-all; }
.ec-meta { display: flex; flex-wrap: wrap; gap: 6px; color: #646566; font-size: 11px; line-height: 1.2; }
.ec-meta span { padding: 4px 7px; border-radius: 999px; background: #f7f8fa; }
.ec-bar { margin-top: 2px; }
.ec-bar-track { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
.ec-bar-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.ec-bar-fill.success { background: #ee0a24; }
.ec-bar-fill.danger { background: #07c160; }
.cal-header { display: flex; align-items: center; justify-content: center; gap: 22px; margin: -2px 0 8px; color: #323233; }
.cal-header .van-icon { font-size: 18px; color: #646566; cursor: pointer; padding: 4px; }
.cal-month-title { font-size: 14px; font-weight: 600; min-width: 90px; text-align: center; }
.cal-shortcuts { display: flex; justify-content: center; gap: 8px; margin: 0 0 8px; }
.cal-shortcut { border: 1px solid #dcdfe6; background: #fff; color: #323233; border-radius: 14px; padding: 4px 12px; font-size: 12px; line-height: 1.4; cursor: pointer; }
.cal-shortcut:active { background: #f0f5ff; border-color: #1989fa; color: #1989fa; }
.cal-weekdays { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; font-size: 11px; color: #969799; padding: 0 0 6px; }
.cal-wd { padding: 4px 0; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.cal-cell { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 48px; border-radius: 6px; position: relative; }
.cal-empty { background: transparent; }
.cal-cell.has-buy { background: #fff8e6; }
.cal-cell.has-record:not(.has-buy) { background: #fafafa; }
.cal-day { font-size: 12px; color: #323233; font-weight: 500; }
.cal-earn { font-size: 10px; margin-top: 2px; font-family: 'DIN Alternate', sans-serif; }
.cal-earn.up { color: #ee0a24; }
.cal-earn.down { color: #07c160; }
.cal-flag { font-size: 12px; line-height: 1; margin-top: 1px; position: absolute; top: 2px; right: 3px; }
.cal-clickable { cursor: pointer; }
.cal-clickable:active { background: #f0f5ff !important; }
.cal-legend { display: flex; gap: 14px; margin-bottom: 8px; padding-left: 2px; font-size: 11px; color: #646566; }
.legend-item { display: flex; align-items: center; gap: 4px; }
.legend-dot { display: inline-block; width: 14px; text-align: center; font-size: 11px; }
.legend-dot.normal { width: 8px; height: 8px; border-radius: 50%; background: #e8e8e8; }
.day-popup { padding: 16px; }
.popup-header { display: flex; justify-content: space-between; align-items: center; font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.popup-header .van-icon { font-size: 20px; color: #969799; cursor: pointer; }
.popup-body { margin-bottom: 12px; }
.popup-actions { display: flex; gap: 12px; justify-content: flex-end; }
</style>
