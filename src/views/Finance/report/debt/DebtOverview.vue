<template>
  <div class="page-debt">
    <!-- 月份切换 -->
    <div class="month-bar">
      <van-icon name="arrow-left" class="month-arrow" @click="prevMonth" />
      <span class="month-text">{{ currentMonthText }}账单</span>
      <van-icon name="arrow" class="month-arrow" @click="nextMonth" />
    </div>

    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-if="!loading">
      <!-- 摘要 -->
      <div class="summary-wrap">
        <div class="sw-left">
          <div class="sw-row">
            <span class="sw-label">本期消费</span>
            <span class="sw-value expense">¥{{ formatAmount(summary.lastMonthSpent) }}</span>
          </div>
          <div class="sw-row">
            <span class="sw-label">未还金额</span>
            <span class="sw-value" :class="summary.unpaid > 0 ? 'expense' : ''">¥{{ formatAmount(summary.unpaid) }}</span>
          </div>
        </div>
        <div class="sw-right">
          <div class="sw-card" style="cursor:pointer" @click="$router.push('/card/credit')">
            <div class="sw-label">持卡</div>
            <div class="sw-value">{{ summary.cardCount }}</div>
          </div>
          <div class="sw-card" style="cursor:pointer" @click="$router.push('/card/bill/list')">
            <div class="sw-label">待还</div>
            <div class="sw-value warning">{{ summary.unpaidCardCount }}</div>
          </div>
        </div>
      </div>

      <!-- 有效期预警 -->
      <div class="section-card" @click="expiryExpanded = !expiryExpanded" style="cursor:pointer">
        <div class="sec-title">
          <span>📅 全部信用卡有效期</span>
          <span class="sec-sub">{{ allCardExpiry.length }}张 <van-icon :name="expiryExpanded ? 'arrow-up' : 'arrow-down'" /></span>
        </div>
        <!-- 折叠态：滚动消息条 -->
        <van-notice-bar v-if="!expiryExpanded" left-icon="volume-o" :scrollable="false">
          <van-swipe vertical class="notice-swipe" :autoplay="4000" :touchable="false" :show-indicators="false">
            <van-swipe-item v-for="card in allCardExpiry" :key="card.id">
              {{ getCardDisplayText(card) }}·{{ formatExpireDate(card.expire_date || card.expireDate) }}
              <span v-if="card.isExpired" style="color:#ee0a24">已过期</span>
              <span v-else-if="card.isExpiring" style="color:#fa8c16">{{ card.monthsLeft }}个月到期</span>
              <span v-else style="color:#07c160">正常</span>
            </van-swipe-item>
          </van-swipe>
        </van-notice-bar>
        <!-- 展开态：完整表格 -->
        <div v-else class="expiry-table" @click.stop>
          <div class="expiry-header">
            <span class="exp-col-name">卡片</span>
            <span class="exp-col-date">有效期</span>
            <span class="exp-col-status">状态</span>
          </div>
          <div v-for="card in allCardExpiry" :key="card.id" class="expiry-row" :class="{ danger: card.isExpired, warn: card.isExpiring }">
            <span class="exp-col-name">{{ getCardDisplayText(card) }}</span>
            <span class="exp-col-date">{{ formatExpireDate(card.expire_date || card.expireDate) }}</span>
            <span class="exp-col-status">
              <span v-if="card.isExpired" class="badge-red">已过期</span>
              <span v-else-if="card.isExpiring" class="badge-orange">{{ card.monthsLeft }}个月后到期</span>
              <span v-else class="badge-green">正常</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 每日信用卡支出日历 -->
      <div class="section-card">
        <div class="sec-title">
          <span>📆 每日信用卡支出</span>
          <span class="sec-sub">{{ currentMonthText }}</span>
        </div>
        <div class="cal-weekdays">
          <span v-for="w in ['一','二','三','四','五','六','日']" :key="w" class="cal-wd">{{ w }}</span>
        </div>
        <div class="cal-grid">
          <span v-for="d in calDays" :key="d.date" class="cal-cell"
            :class="{ 'cal-empty': !d.inMonth, 'cal-today': d.isToday, 'cal-has': d.total > 0, 'cal-selected': d.inMonth && d.date === selectedCalDate }"
            @click="d.inMonth && d.total > 0 ? showDayItems(d.date) : null">
            <span class="cal-day">{{ d.day }}</span>
            <span v-if="d.total > 0" class="cal-amt">¥{{ formatAmount(d.total) }}</span>
          </span>
        </div>
      </div>

      <!-- 支出类别 -->
      <div class="section-card">
        <div class="sec-title">
          <span>{{ calDateLabel }}支出类别</span>
          <span v-if="selectedCalDate" class="sec-sub reset-btn" @click="resetCalDate">重置</span>
        </div>
        <div v-if="categoryStats.length === 0" class="empty-hint">暂无支出</div>
        <div v-for="cat in categoryStats.slice(0, 8)" :key="cat.categoryName" class="cat-row" @click="showCategoryItems(cat.categoryName)">
          <span class="cat-name">{{ cat.categoryName }}</span>
          <div class="cat-bar-track">
            <div class="cat-bar-fill" :style="{ width: cat.pct + '%', background: cat.color }"></div>
          </div>
          <span class="cat-value">¥{{ formatAmount(cat.total) }}</span>
        </div>
      </div>

      <!-- 分类/日流水弹窗 -->
      <van-popup v-model:show="showCatPopup" position="bottom" round>
        <div class="cat-popup">
          <div class="popup-header">
            <span>{{ selectedCatName }} <span class="popup-sub">{{ dayCatItems.length }}笔</span></span>
            <van-icon name="cross" @click="showCatPopup = false" />
          </div>
          <div class="cat-items">
            <div v-for="item in dayCatItems" :key="item.id" class="cat-item" @click="$router.push(`/finance/flow/${item.id}`)">
              <div class="ci-left">
                <div class="ci-title">{{ item.remark || item.category_name }}</div>
                <div class="ci-desc">{{ getCardNameByCardId(item.card_id) }} · {{ dayjs(Number(item.create_time)).format('MM/DD HH:mm') }}</div>
              </div>
              <div class="ci-right expense">-¥{{ formatAmount(item.amount) }}</div>
            </div>
            <div v-if="dayCatItems.length === 0" class="empty-hint">暂无明细</div>
          </div>
        </div>
      </van-popup>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Icon } from 'vant'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import { getCardList, getBillList } from '@/utils/api/card'
import { getAccountList } from '@/utils/api/account'
import { categoryApi } from '@/utils/api/category'

dayjs.locale(zhCn)

const loading = ref(true)
const allCards = ref([])
const bankList = ref([])
const billList = ref([])
const creditExpenseList = ref([])
const showCatPopup = ref(false)
const selectedCatName = ref('')

// 信用卡ID集合
const creditCardIds = computed(() =>
  new Set(allCards.value.filter((c) => c.card_type === 'credit').map((c) => c.id))
)

// 月份状态
const currentMonth = ref(dayjs().startOf('month'))
const expiryExpanded = ref(false)

const currentMonthText = computed(() => currentMonth.value.format('YYYY年MM月'))
const billMonth = computed(() => currentMonth.value.format('YYYY-MM'))
const monthStart = computed(() => currentMonth.value.format('YYYY-MM-DD'))
const monthEnd = computed(() => currentMonth.value.endOf('month').format('YYYY-MM-DD'))

const prevMonth = () => { currentMonth.value = currentMonth.value.subtract(1, 'month'); loadData() }
const nextMonth = () => { currentMonth.value = currentMonth.value.add(1, 'month'); loadData() }

const formatAmount = (v) => {
  if (!v && v !== 0) return '0.00'
  const num = Number(v)
  const isNegative = num < 0
  const abs = Math.abs(num)

  let result
  if (abs >= 100000000) {
    // 亿：保留3位小数，整数部分不进位
    const yi = abs / 100000000
    const intPart = Math.floor(yi)
    let decPart = Math.round((yi - intPart) * 1000)
    if (decPart >= 1000) decPart = 999 // 不允许进位到整数
    result = intPart + '.' + String(decPart).padStart(3, '0') + '亿'
  } else if (abs >= 10000) {
    // 万：保留3位小数，整数部分不进位
    const wan = abs / 10000
    const intPart = Math.floor(wan)
    let decPart = Math.round((wan - intPart) * 1000)
    if (decPart >= 1000) decPart = 999
    result = intPart + '.' + String(decPart).padStart(3, '0') + '万'
  } else {
    // 千以内：保留2位小数，整数部分不进位
    const intPart = Math.floor(abs)
    let decPart = Math.round((abs - intPart) * 100)
    if (decPart >= 100) decPart = 99
    result = intPart + '.' + String(decPart).padStart(2, '0')
  }
  return (isNegative ? '-' : '') + result
}

const getCardDisplayText = (card) => {
  if (!card) return ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  const bankName = bank?.name || card.alias || card.bank_name || ''
  const last4 = card.last4_no || card.last4No || card.card_last4 || ''
  if (bankName && last4) return `${bankName} ${last4}`
  if (bankName) return bankName
  if (last4) return `****${last4}`
  return card.alias || card.bank_name || card.id
}

// ===== 摘要（直接使用当月账单） =====
const summary = computed(() => {
  let lastMonthSpent = 0
  let unpaid = 0
  let unpaidCardCount = 0

  billList.value.forEach((b) => {
    const need = parseFloat(b.need_repay) || 0
    const billAmt = parseFloat(b.bill_amount) || 0
    lastMonthSpent += billAmt
    unpaid += need
    if (need > 0) unpaidCardCount++
  })

  return {
    lastMonthSpent,
    unpaid,
    cardCount: billList.value.length,
    unpaidCardCount,
  }
})

// ===== 全部信用卡有效期 =====
const allCardExpiry = computed(() => {
  const today = dayjs()
  const threeMonthsLater = today.add(3, 'month')
  return allCards.value
    .filter((c) => c.card_type === 'credit')
    .map((c) => {
      const expireStr = c.expire_date || c.expireDate
      if (!expireStr) return { ...c, expireDateObj: null, isExpired: false, isExpiring: false, monthsLeft: '-' }
      const expire = dayjs(expireStr, ['YYYY-MM-DD', 'YYYY-MM'])
      if (!expire.isValid()) return { ...c, expireDateObj: null, isExpired: false, isExpiring: false, monthsLeft: '-' }
      const monthsLeft = expire.diff(today, 'month')
      return {
        ...c,
        expireDateObj: expire,
        isExpired: expire.isBefore(today, 'month'),
        isExpiring: expire.isBefore(threeMonthsLater, 'month') && !expire.isBefore(today, 'month'),
        monthsLeft: monthsLeft >= 0 ? monthsLeft : 0,
      }
    })
    .sort((a, b) => {
      if (!a.expireDateObj) return 1
      if (!b.expireDateObj) return -1
      return a.expireDateObj.valueOf() - b.expireDateObj.valueOf()
    })
})

const formatExpireDate = (dateStr) => {
  if (!dateStr) return ''
  const d = dayjs(dateStr, ['YYYY-MM-DD', 'YYYY-MM'])
  return d.isValid() ? d.format('YYYY/MM') : dateStr
}

// ===== 每日信用卡支出日历 =====
const calDays = computed(() => {
  const month = currentMonth.value
  const daysInMonth = month.daysInMonth()
  const firstDay = month.startOf('month').day() // 0=Sun
  // dayjs: 0=Sun, 1=Mon, ... 6=Sat → 转为 周一=0
  const offset = firstDay === 0 ? 6 : firstDay - 1

  // 按日期汇总信用卡支出
  const dayMap = {}
  creditExpenseList.value.forEach((item) => {
    if (item.direction !== 0) return
    const date = item.trans_date
    if (!date) return
    dayMap[date] = (dayMap[date] || 0) + (parseFloat(item.amount) || 0)
  })

  const today = dayjs().format('YYYY-MM-DD')
  const cells = []

  // 上月填充空白
  for (let i = 0; i < offset; i++) {
    cells.push({ date: '', day: '', inMonth: false })
  }
  for (let d = 1; d <= daysInMonth; d++) {
    const date = month.date(d).format('YYYY-MM-DD')
    cells.push({ date, day: d, inMonth: true, isToday: date === today, total: dayMap[date] || 0 })
  }
  return cells
})

// 日历选择联动
const selectedCalDate = ref('')
const calDateLabel = computed(() => selectedCalDate.value ? `📊 ${selectedCalDate.value}` : '📊 本月总')
const resetCalDate = () => { selectedCalDate.value = '' }

const showDayItems = (date) => {
  selectedCalDate.value = date
}
// 复用 catItems：当日/分类明细
const dayCatItems = computed(() => {
  if (!showCatPopup.value || !selectedCatName.value) return []
  // 日历点击→当日明细
  if (/^\d{4}-\d{2}-\d{2}$/.test(selectedCatName.value)) {
    return creditExpenseList.value.filter((item) => item.trans_date === selectedCatName.value && item.direction === 0)
  }
  // 分类点击→按当日+分类过滤（如果选了日期）或全月分类
  return creditExpenseList.value.filter((item) => {
    const matchCat = (item.category_name || '未分类') === selectedCatName.value
    const matchDate = selectedCalDate.value ? item.trans_date === selectedCalDate.value : true
    return matchCat && matchDate && item.direction === 0
  })
})
const showCategoryItems = (name) => {
  selectedCatName.value = name
  showCatPopup.value = true
}
const getCardNameByCardId = (id) => {
  if (!id) return ''
  const card = allCards.value.find((c) => c.id === id)
  if (!card) return id
  return getCardDisplayText(card)
}

// ===== 信用卡支出类别 =====
const categoryColors = [
  '#ee0a24', '#ff976a', '#ffb300', '#1989fa',
  '#07c160', '#7232dd', '#00bcd4', '#e91e63',
]
const categoryStats = computed(() => {
  const map = {}
  const source = selectedCalDate.value
    ? creditExpenseList.value.filter((item) => item.trans_date === selectedCalDate.value)
    : creditExpenseList.value
  source.forEach((item) => {
    if (item.direction !== 0) return // 只统计支出
    const name = item.category_name || '未分类'
    const amt = parseFloat(item.amount) || 0
    if (!map[name]) map[name] = 0
    map[name] += amt
  })
  const entries = Object.entries(map).map(([name, total]) => ({ categoryName: name, total }))
  const maxAmount = Math.max(...entries.map((c) => c.total), 1)
  return entries
    .map((c, i) => ({ ...c, color: categoryColors[i % categoryColors.length], pct: (c.total / maxAmount) * 100 }))
    .sort((a, b) => b.total - a.total)
})

const loadData = async () => {
  loading.value = true
  try {
    const [cardRes, bankRes] = await Promise.all([
      getCardList(),
      categoryApi.list('bank').catch(() => ({ data: [] })),
    ])
    allCards.value = cardRes.data || cardRes || []
    bankList.value = bankRes.data || bankRes || []

    // 获取当月信用卡账单（按 billMonth 筛选）
    const billRes = await getBillList({ billMonth: billMonth.value }).catch(() => ({ data: [] }))
    billList.value = billRes.data || billRes || []

    // 获取当月信用卡支出流水
    const creditIds = new Set(allCards.value.filter((c) => c.card_type === 'credit').map((c) => c.id))
    if (creditIds.size > 0) {
      const acctRes = await getAccountList({
        limit: 10000,
        startDate: monthStart.value,
        endDate: monthEnd.value,
      })
      const raw = acctRes.data?.list || acctRes.data || []
      creditExpenseList.value = raw.filter((item) => creditIds.has(item.card_id))
    }
  } catch (e) {
    console.error('加载负债数据失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-debt {
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
}
.month-arrow {
  font-size: 18px;
  color: #1989fa;
  cursor: pointer;
  padding: 4px;
}
.page-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

/* 摘要卡片 */
.summary-wrap {
  display: flex;
  gap: 8px;
  margin: 10px 16px 0;
}
.sw-left {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sw-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sw-label { font-size: 12px; color: #969799; }
.sw-value {
  font-size: 16px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
}
.sw-value.expense { color: #ee0a24; }
.sw-value.warning { color: #fa8c16; }
.sw-right {
  display: flex;
  gap: 6px;
}
.sw-card {
  background: #fff;
  border-radius: 12px;
  padding: 12px 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 54px;
}
.sw-card .sw-label { font-size: 10px; }

.notice-swipe {
  height: 40px;
  line-height: 40px;
}

/* 日历 */
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 11px;
  color: #969799;
  padding: 0 0 6px;
}
.cal-wd { padding: 4px 0; }
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.cal-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 58px;
  border-radius: 6px;
  cursor: default;
  position: relative;
}
.cal-empty { background: transparent; cursor: default; }
.cal-today { background: #f0f5ff; }
.cal-selected { background: #1989fa !important; }
.cal-selected .cal-day { color: #fff; font-weight: 700; }
.cal-selected .cal-amt { color: #fff; }
.cal-has { cursor: pointer; }
.cal-has:active { background: #e8f0fe; }
.cal-day {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}
.cal-today .cal-day {
  color: #1989fa;
  font-weight: 700;
}
.cal-amt {
  font-size: 10px;
  color: #ee0a24;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 48px;
}

/* 通用区块 */
.section-card {
  background: #fff;
  margin: 10px 16px 0;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.sec-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sec-sub { font-size: 11px; font-weight: 400; color: #969799; }
.reset-btn { color: #1989fa; cursor: pointer; }
.empty-hint {
  text-align: center;
  padding: 20px 0;
  color: #969799;
  font-size: 13px;
}

/* 预警行 */
.warn-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.warn-row:last-child { border-bottom: none; }
.warn-row.danger { background: #fff0f0; margin: 0 -16px; padding: 10px 16px; }
.warn-name { font-size: 13px; color: #323233; }
.warn-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  white-space: nowrap;
}
.badge-red { background: #ffebe8; color: #ee0a24; }
.badge-orange { background: #fff7e6; color: #fa8c16; }
.badge-green { background: #f0fff4; color: #07c160; }

.expiry-table {
  font-size: 12px;
}
.expiry-header, .expiry-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}
.expiry-header { color: #969799; font-weight: 500; }
.expiry-row:last-child { border-bottom: none; }
.expiry-row.danger { background: #fff0f0; margin: 0 -16px; padding: 8px 16px; }
.expiry-row.warn { background: #fffcf0; margin: 0 -16px; padding: 8px 16px; }
.exp-col-name { flex: 1; color: #323233; }
.exp-col-date { width: 72px; text-align: center; color: #969799; }
.exp-col-status { width: 80px; text-align: right; }
.exp-col-status .badge-red, .exp-col-status .badge-orange, .exp-col-status .badge-green {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  white-space: nowrap;
}

/* 类别 */
.cat-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.cat-name {
  font-size: 12px;
  color: #323233;
  width: 54px;
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
.cat-value {
  font-size: 11px;
  font-weight: 500;
  color: #323233;
  width: 62px;
  text-align: right;
  font-family: 'DIN Alternate', sans-serif;
}

/* 分类流水弹窗 */
.cat-popup {
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}
.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
}
.popup-header .van-icon {
  font-size: 20px;
  color: #969799;
  cursor: pointer;
}
.popup-sub { font-size: 12px; font-weight: 400; color: #969799; }
.cat-items { flex: 1; overflow-y: auto; }
.cat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.cat-item:active { background: #f7f8fa; }
.ci-title { font-size: 14px; color: #323233; }
.ci-desc { font-size: 11px; color: #969799; margin-top: 2px; }
.ci-right { font-size: 14px; font-weight: 600; font-family: 'DIN Alternate', sans-serif; }
.ci-right.expense { color: #ee0a24; }
</style>
