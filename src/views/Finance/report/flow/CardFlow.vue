<template>
  <div class="page-card-flow">
    <div class="card-selector">
      <div class="selector-label">请选择银行卡</div>
      <app-cell is-link @click="showCardPicker = true" :border="false">
        <template #value>
          <span v-if="selectedCard" class="card-value">
            <BankIcon
              :src="getFullUrl(getCardBankIcon(selectedCard))"
              :name="getCardBankName(selectedCard)"
              :size="16" class="card-value-icon"
            />
            <span>{{ getCardDisplayText(selectedCard) }}</span>
          </span>
          <span v-else class="placeholder">请选择银行卡</span>
        </template>
      </app-cell>
    </div>

    <app-popup v-model:show="showCardPicker" position="bottom" round>
      <div class="card-picker-popup">
        <div class="popup-header">
          <span>选择银行卡或信用卡</span>
          <van-icon name="cross" @click="showCardPicker = false" />
        </div>
        <van-search v-model="cardSearchKey" placeholder="搜索银行名称或尾号" show-action @cancel="cardSearchKey = ''" />
        <div class="card-list">
          <div v-for="card in filteredCards" :key="card.id" class="card-list-item"
            :class="{ active: selectedCard?.id === card.id }" @click="onCardSelect(card)">
            <BankIcon :src="getFullUrl(getCardBankIcon(card))" :name="getCardBankName(card)"
              :size="20" class="card-list-icon" />
            <span class="card-list-text">{{ getCardDisplayText(card) }}</span>
            <van-icon v-if="selectedCard?.id === card.id" name="success" :color="'var(--theme-success)'" />
          </div>
          <div v-if="filteredCards.length === 0" class="empty-tip">未找到匹配的银行卡</div>
        </div>
      </div>
    </app-popup>

    <template v-if="selectedCard">
      <div class="filter-bar">
        <van-tabs v-model:active="filterType" shrink @change="onFilterChange">
          <van-tab title="全部" name="all" />
          <van-tab title="收入" name="income" />
          <van-tab title="支出" name="expense" />
        </van-tabs>
        <div class="filter-right">
          <div class="date-filter" @click="showDatePicker = true">
            <span>{{ dateRangeText }}</span>
            <van-icon name="arrow-down" />
          </div>
        </div>
      </div>

      <div class="stats-summary" v-if="summaryData">
        <div class="summary-item">
          <span class="label">收入</span>
          <span class="value income">+{{ formatAmount(summaryData.income) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">支出</span>
          <span class="value expense">-{{ formatAmount(summaryData.expense) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">笔数</span>
          <span class="value">{{ list.length }}</span>
        </div>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadData">
          <div v-for="(group, date) in groupedList" :key="date" class="date-group">
            <div class="date-header">{{ formatDateHeader(date) }}</div>
            <div class="date-items">
              <div v-for="item in group" :key="item.id" class="flow-item" :class="{ repay: item.category_id === 'CATEGORY_REPAY' }" @click="goDetail(item)">
                <div class="item-left">
                  <div class="category-icon"><van-icon :name="getCategoryIcon(item.category_name)" /></div>
                  <div class="item-info">
                    <div class="item-title">{{ getCategoryName(item) }}</div>
                    <div class="item-desc">{{ item.pay_method || '-' }} · {{ formatTime(item.create_time) }}</div>
                  </div>
                </div>
                <div class="item-right">
                  <div class="amount" :class="item.direction === 1 ? 'income' : 'expense'">
                    <span v-if="isForeignCurrency(item)" class="currency-tag">{{ getCurrencySymbol(item.currency) }}</span>
                    {{ item.direction === 1 ? '+' : '-' }}{{ formatAmount(item.amount) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <van-empty v-if="!loading && list.length === 0" description="暂无流水记录" />
        </van-list>
      </van-pull-refresh>

      <app-popup v-model:show="showDatePicker" position="bottom" round>
        <van-picker v-model="selectedValues" title="选择月份" :columns="pickerColumns"
          @confirm="onPickerConfirm" @cancel="showDatePicker = false" />
      </app-popup>
    </template>

    <van-empty v-else description="请选择上方的银行卡或信用卡查看流水" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, nextTick } from 'vue'
defineOptions({ name: 'FinanceReportCardFlow' })
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import { getAccountListByCard } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'
import ENV from '@/utils/env'
import { useFlowSyncStore } from '@/stores/flowSync'
import BankIcon from '@/components/BankIcon.vue'

dayjs.locale(zhCn)
const router = useRouter()
const route = useRoute()
const flowSync = useFlowSyncStore()
const BASE_URL = ENV.FILE_BASE_URL

const allCards = ref([])
const bankList = ref([])
const selectedCard = ref(null)
const showCardPicker = ref(false)
const cardSearchKey = ref('')
const availableCards = computed(() => allCards.value.filter((c) => c.card_type === 'debit'))
const filteredCards = computed(() => {
  const key = cardSearchKey.value.toLowerCase().trim()
  if (!key) return availableCards.value
  return availableCards.value.filter((card) => {
    const text = getCardDisplayText(card).toLowerCase()
    const last4 = (card.last4_no || card.last4No || card.card_last4 || '').toLowerCase()
    return text.includes(key) || last4.includes(key)
  })
})
const onCardSelect = (card) => {
  selectedCard.value = card; showCardPicker.value = false; cardSearchKey.value = ''; onRefresh()
}

const getCardDisplayText = (card) => {
  if (!card) return ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  const bankName = bank?.name || card.alias || card.bank_name || ''
  const last4 = card.last4_no || card.last4No || card.card_last4 || ''
  if (bankName && last4) { const suffix = card.card_type === 'credit' ? ' (信用卡)' : ''; return `${bankName} ${last4}${suffix}` }
  if (bankName) { const suffix = card.card_type === 'credit' ? ' (信用卡)' : ''; return `${bankName}${suffix}` }
  if (last4) return `****${last4}`
  return card.alias || card.bank_name || card.id
}
const getCardBankIcon = (card) => {
  if (!card) return ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  return bank?.icon_url || bank?.iconUrl || ''
}
const getCardBankName = (card) => {
  if (!card) return ''
  const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  return bank?.name || bank?.bank_name || card.bank_name || ''
}
const getFullUrl = (path) => {
  if (!path) return ''; if (path.startsWith('http')) return path
  const pureBase = BASE_URL.replace(/\/+$/, ''); const purePath = path.startsWith('/') ? path : `/${path}`
  return pureBase + purePath
}

const filterType = ref('all')
const showDatePicker = ref(false)
const now = dayjs()
const currentYear = ref(Number(route.query.year) || now.year())
const currentMonth = ref(Number(route.query.month) || now.month() + 1)
const selectedValues = ref([`${currentYear.value}年`, `${currentMonth.value}月`])
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const savedScrollY = ref(0)
const page = ref(1)
const limit = 20
const summaryData = ref(null)

const dateRangeText = computed(() => `${currentYear.value}年${currentMonth.value}月`)
const pickerColumns = computed(() => {
  const y = dayjs().year(); const years = []
  for (let i = y - 10; i <= y + 2; i++) years.push({ text: `${i}年`, value: `${i}年` })
  const months = Array.from({ length: 12 }, (_, i) => ({ text: `${i + 1}月`, value: `${i + 1}月` }))
  return [years, months]
})

const formatAmount = (v) => (v ? Number(v).toFixed(2) : '0.00')
const getCurrencySymbol = (code) => ({ CNY: '¥', USD: '$', EUR: '€', HKD: 'HK$', JPY: '¥', GBP: '£', KRW: '₩', TWD: 'NT$' }[code] || code || '')
const isForeignCurrency = (item) => item.currency && item.currency !== 'CNY'
const formatDateHeader = (date) => {
  if (!date) return ''
  const d = dayjs(date)
  if (d.isSame(dayjs(), 'day')) return '今天'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  return d.format('M月D日 ddd')
}
const formatTime = (ts) => (ts ? dayjs(Number(ts)).format('HH:mm') : '')
const getCategoryIcon = (name) => ({ 餐饮: 'orders-o', 购物: 'shopping-cart-o', 交通: 'logistics', 娱乐: 'gem-o', 工资: 'paid' }[name] || 'balance-o')
const getCategoryName = (item) => item.category_id === 'CATEGORY_REPAY' ? '信用卡还款' : (item.category_name || '未知分类')

const groupedList = computed(() => {
  const groups = {}
  list.value.forEach((item) => { const date = item.trans_date; if (date) { if (!groups[date]) groups[date] = []; groups[date].push(item) } })
  return groups
})

const loadSummary = async () => {
  if (!selectedCard.value) return
  try {
    const params = { cardId: selectedCard.value.id, startDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`).startOf('month').format('YYYY-MM-DD'), endDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`).endOf('month').format('YYYY-MM-DD'), limit: 10000 }
    const res = await getAccountListByCard(params)
    const data = res.data?.list || res.data || []; let income = 0, expense = 0
    data.forEach((item) => { const amt = parseFloat(item.amount) || 0; if (item.direction === 1) income += amt; else expense += amt })
    summaryData.value = { income, expense }
  } catch (e) { summaryData.value = null }
}

const loadData = async () => {
  if (refreshing.value || !selectedCard.value) return; loading.value = true
  try {
    const params = { cardId: selectedCard.value.id, page: page.value, limit, startDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`).startOf('month').format('YYYY-MM-DD'), endDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`).endOf('month').format('YYYY-MM-DD') }
    if (filterType.value !== 'all') params.direction = filterType.value === 'income' ? 1 : 0
    const res = await getAccountListByCard(params)
    const data = res.data?.list || res.data || []; const pagination = res.data?.pagination || {}
    if (page.value === 1) list.value = data; else list.value.push(...data)
    finished.value = data.length < limit || page.value >= (pagination.totalPages || 1)
    if (!finished.value) page.value++
  } catch (e) { finished.value = true; showToast('加载失败') }
  finally { loading.value = false }
}

const onRefresh = () => { page.value = 1; finished.value = false; loading.value = true; Promise.all([loadSummary(), loadData()]).finally(() => { loading.value = false; refreshing.value = false }) }
const onFilterChange = () => onRefresh()
const onPickerConfirm = ({ selectedOptions }) => {
  currentYear.value = parseInt(selectedOptions[0].text)
  currentMonth.value = parseInt(selectedOptions[1].text)
  selectedValues.value = [selectedOptions[0].text, selectedOptions[1].text]
  showDatePicker.value = false
  router.replace({ query: { ...route.query, year: currentYear.value, month: currentMonth.value } })
  onRefresh()
}
const goDetail = (item) => router.push(`/finance/flow/${item.id}`)

onMounted(async () => {
  try {
    const [cardRes, bankRes] = await Promise.all([getCardList(), categoryApi.list('bank')])
    allCards.value = cardRes.data || cardRes || []; bankList.value = bankRes.data || bankRes || []

    const cardId = route.query.cardId
    if (cardId) {
      const card = allCards.value.find((c) => c.id === cardId)
      if (card) onCardSelect(card)
    }
  } catch (e) { allCards.value = []; bankList.value = [] }
})

// keep-alive 激活时：URL 有参数就恢复，无参数就重置为当前月
onActivated(() => {
  // 0. 优先级最高：检测路由 cardId 是否变化（不同卡需要重新加载）
  // 场景：从"系统账户余额"选 B 卡跳过来时 selectedCard 仍是 A
  const routeCardId = route.query.cardId
  if (routeCardId && (!selectedCard.value || selectedCard.value.id !== routeCardId)) {
    const card = allCards.value.find((c) => c.id === routeCardId)
    if (card) {
      onCardSelect(card)  // 内部会调 onRefresh() 重置分页 + 拉新数据
    } else {
      // allCards 还没加载完（理论上 onMounted 已先于 onActivated 触发）
      // 等下一轮：保留 onActivated 后续逻辑，等 allCards 加载完成后 onCardSelect 会触发
    }
  }

  if (route.query.year && route.query.month) {
    const y = Number(route.query.year)
    const m = Number(route.query.month)
    if (y !== currentYear.value || m !== currentMonth.value) {
      currentYear.value = y
      currentMonth.value = m
      selectedValues.value = [`${y}年`, `${m}月`]
      onRefresh()
    }
  } else {
    const now = dayjs()
    if (now.year() !== currentYear.value || now.month() + 1 !== currentMonth.value) {
      currentYear.value = now.year()
      currentMonth.value = now.month() + 1
      selectedValues.value = [`${now.year()}年`, `${now.month() + 1}月`]
      onRefresh()
    }
  }

  // 同步 Detail 变更（原地 patch）
  const changes = flowSync.consumeChanges();
  const ids = Object.keys(changes);
  if (ids.length > 0) {
    list.value.forEach(item => {
      const patch = changes[item.id];
      if (patch) Object.assign(item, patch);
    });
  }

  // 恢复滚动位置
  nextTick(() => {
    if (savedScrollY.value > 0) window.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
  })
})

onDeactivated(() => {
  savedScrollY.value = window.scrollY || document.documentElement.scrollTop
})
</script>

<style scoped>
.page-card-flow { min-height: 100vh; background: var(--theme-bg-primary); }
.card-selector { background: var(--theme-bg-secondary); padding: 14px 16px 4px; display: flex; align-items: center; gap: 8px; }
.selector-label { font-size: 13px; color: var(--theme-text-tertiary); white-space: nowrap; }
.card-value { display: inline-flex; align-items: center; gap: 4px; font-size: 14px; color: var(--theme-text-primary); max-width: 100%; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-value-icon { border-radius: 2px; flex-shrink: 0; }
.placeholder { color: var(--theme-text-tertiary); font-size: 14px; }
.card-picker-popup { max-height: 70vh; display: flex; flex-direction: column; }
.popup-header { display: flex; justify-content: space-between; align-items: center; padding: 16px; border: 1px solid var(--theme-border); font-size: 16px; font-weight: 600; }
.popup-header .van-icon { font-size: 20px; color: var(--theme-text-tertiary); }
.card-list { flex: 1; overflow-y: auto; max-height: 400px; }
.card-list-item { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border: 1px solid var(--theme-border); cursor: pointer; }
.card-list-item:active { background: var(--theme-bg-primary); }
.card-list-item.active { background: rgba(7,193,96,0.08); }
.card-list-icon { border-radius: 3px; flex-shrink: 0; }
.card-list-text { flex: 1; min-width: 0; font-size: 14px; color: var(--theme-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.empty-tip { text-align: center; padding: 30px; color: var(--theme-text-tertiary); font-size: 14px; }
.filter-bar { background: var(--theme-bg-secondary); padding: 12px 16px; display: flex; align-items: center; gap: 12px; }
.date-filter { display: flex; align-items: center; gap: 4px; font-size: 13px; color: var(--theme-text-secondary); padding: 6px 10px; background: var(--theme-bg-primary); border-radius: 4px; }
.filter-right { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.stats-summary { display: flex; justify-content: space-around; padding: 16px; background: var(--theme-bg-secondary); margin: 8px 16px; border-radius: 12px; }
.summary-item { text-align: center; }
.summary-item .label { font-size: 12px; color: var(--theme-text-tertiary); display: block; margin-bottom: 4px; }
.summary-item .value { font-size: 16px; font-weight: bold; font-family: 'DIN Alternate', sans-serif; }
.summary-item .income { color: var(--van-green, #07c160); }
.summary-item .expense { color: var(--van-danger-color, #ee0a24); }
.date-group { margin-top: 8px; }
.date-header { padding: 8px 16px; font-size: 13px; color: var(--theme-text-secondary); background: var(--theme-bg-primary); }
.date-items { background: var(--theme-bg-secondary); }
.flow-item { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; margin-left: 16px; border-bottom: 1px solid var(--theme-border); }
.flow-item:last-child { border-bottom: none; }

/* 信用卡还款：置灰 */
.flow-item.repay { filter: grayscale(1); opacity: 0.55; }
.item-left { display: flex; align-items: center; gap: 12px; }
.category-icon { width: 40px; height: 40px; border-radius: 50%; background: var(--theme-bg-primary); display: flex; align-items: center; justify-content: center; font-size: 20px; color: var(--theme-primary); }
.item-info .item-title { font-size: 15px; color: var(--theme-text-primary); font-weight: 500; }
.item-info .item-desc { font-size: 12px; color: var(--theme-text-tertiary); margin-top: 4px; }
.item-right { text-align: right; }
.item-right .amount { font-size: 16px; font-weight: bold; font-family: 'DIN Alternate', sans-serif; }
.currency-tag { font-size: 12px; margin-right: 2px; color: var(--theme-text-tertiary); }
.item-right .income { color: var(--van-green, #07c160); }
.item-right .expense { color: var(--theme-text-primary); }
</style>
