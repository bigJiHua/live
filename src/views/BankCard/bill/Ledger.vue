<template>
  <div class="page-bill-ledger">
    <div class="bill-info-section" v-if="billData.id">
      <van-cell-group inset>
        <app-cell>
          <div class="bill-info-row">
            <div class="bill-info-left">
              <BankIcon :src="getFullUrl(cardBankIcon)" :name="cardBankName" :size="22" class="bank-icon" />
              <div>
                <div class="bill-card-name">{{ billData.card_alias || '信用卡' }}</div>
                <div class="bill-card-last4" v-if="billData.card_last4">**** {{ billData.card_last4 }}</div>
              </div>
            </div>
            <div class="bill-info-right">
              <div class="bill-period">{{ formatDate(billData.bill_start_date) }} ~ {{ formatDate(billData.bill_end_date) }}</div>
              <div class="bill-month-tag">{{ billData.bill_month }} 账单</div>
            </div>
          </div>
        </app-cell>
      </van-cell-group>
    </div>

    <div class="stats-section" v-if="summaryData">
      <van-cell-group inset>
        <app-cell>
          <div class="stats-row">
            <div class="stat-item">
              <div class="stat-label">账单金额</div>
              <div class="stat-value text-expense">{{ formatMoney(billData.bill_amount) }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">流水支出</div>
              <div class="stat-value text-expense">{{ formatMoney(summaryData.expense) }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">流水笔数</div>
              <div class="stat-value">{{ summaryData.count }}</div>
            </div>
          </div>
          <div class="match-tip" v-if="isMatched">
            <van-icon name="success" :color="themeSuccess" /> 流水金额与账单一致
          </div>
          <div class="match-tip mismatch" v-else-if="summaryData.expense > 0">
            <van-icon name="cross" :color="themeDanger" /> 流水与账单金额有差异
          </div>
        </app-cell>
      </van-cell-group>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" :immediate-check="false" @load="loadData">
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
                  {{ item.direction === 1 ? '+' : '-' }}{{ getCurrencySymbol(item.currency) }}{{ formatMoney(item.amount) }}
                </div>
                <div class="amount-cny" v-if="item.currency && item.currency !== 'CNY'">
                  ≈ ¥{{ formatMoney((Number(item.amount) * Number(item.exchange_rate || 0)) / 100) }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <van-empty v-if="!loading && list.length === 0" description="该账单周期内暂无流水记录" />
      </van-list>
    </van-pull-refresh>

    <van-overlay :show="pageLoading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, nextTick } from 'vue'
defineOptions({ name: 'BillLedger' })
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import dayjs from 'dayjs'
import zhCn from 'dayjs/locale/zh-cn'
import { getBillDetail } from '@/utils/api/card'
import { getAccountListByCard } from '@/utils/api/account'
import { categoryApi } from '@/utils/api/category'
import ENV from '@/utils/env'
import { useUiTheme } from '@/composables/useUiTheme'
import BankIcon from '@/components/BankIcon.vue'

dayjs.locale(zhCn)
const router = useRouter()
const route = useRoute()
const { success: themeSuccess, danger: themeDanger } = useUiTheme()
const BASE_URL = ENV.FILE_BASE_URL

const pageLoading = ref(false)
const billData = ref({})
const cardBankIcon = ref('')
const cardBankName = ref('')
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const page = ref(1)
const limit = 50
const summaryData = ref(null)
const savedScrollY = ref(0)

const fullUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  const pureBase = BASE_URL.replace(/\/+$/, '')
  const purePath = path.startsWith('/') ? path : `/${path}`
  return pureBase + purePath
}

const getFullUrl = (path) => fullUrl(path)

const formatDate = (date) => {
  if (!date) return '-'
  return date.split(' ')[0]
}

import { formatMoney } from "@/utils/money";

const formatDateHeader = (date) => {
  if (!date) return ''
  const d = dayjs(date)
  if (d.isSame(dayjs(), 'day')) return '今天'
  if (d.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天'
  return d.format('M月D日 ddd')
}

const formatTime = (ts) => (ts ? dayjs(ts).format('HH:mm') : '')

const getCategoryIcon = (name) => {
  const map = { 餐饮: 'orders-o', 购物: 'shopping-cart-o', 交通: 'logistics', 娱乐: 'gem-o', 工资: 'paid', 还款: 'credit-pay' }
  return map[name] || 'balance-o'
}

const getCategoryName = (item) => {
  return item.category_id === 'CATEGORY_REPAY' ? '信用卡还款' : (item.category_name || '未知分类')
}

// 币种符号（用于流水金额前展示，如 HK$/US$/€）
const getCurrencySymbol = (code) => {
  const symbols = {
    CNY: '¥', USD: '$', EUR: '€', HKD: 'HK$', JPY: '¥',
    GBP: '£', KRW: '₩', TWD: 'NT$'
  }
  return symbols[code] || code || '¥'
}

const isMatched = computed(() => {
  if (!summaryData.value || !billData.value.bill_amount) return false
  return Math.abs(parseFloat(summaryData.value.expense) - parseFloat(billData.value.bill_amount)) < 0.01
})

const groupedList = computed(() => {
  const groups = {}
  list.value.forEach((item) => {
    const date = item.trans_date
    if (date) {
      if (!groups[date]) groups[date] = []
      groups[date].push(item)
    }
  })
  return groups
})

const loadBillDetail = async () => {
  const id = route.query.id
  if (!id) {
    showToast('缺少账单ID')
    router.back()
    return
  }
  try {
    const res = await getBillDetail(id)
    const data = res.data || res || {}
    billData.value = data

    if (data.card_id) {
      const bankId = data.bank_id
      if (bankId) {
        const bankRes = await categoryApi.list('bank')
        const bankList = bankRes.data || bankRes || []
        const bank = bankList.find((b) => b.id === bankId)
        if (bank) {
          cardBankIcon.value = bank.icon_url || bank.iconUrl || ''
          cardBankName.value = bank.name || bank.bank_name || ''
        }
      }
      // 银行名兜底：查不到银行时用卡别名或通用占位，避免图标块空白/问号
      if (!cardBankName.value) {
        cardBankName.value = data.card_alias || '银行'
      }
    }
  } catch (e) {
    showToast('账单加载失败')
    router.back()
  }
}

const loadSummary = async () => {
  if (!billData.value.card_id) return
  try {
    const startDate = formatDate(billData.value.bill_start_date)
    const endDate = formatDate(billData.value.bill_end_date)
    const params = {
      cardId: billData.value.card_id,
      startDate,
      endDate,
      limit: 10000
    }
    const res = await getAccountListByCard(params)
    const data = res.data?.list || res.data || []
    let expense = 0
    let count = 0
    data.forEach((item) => {
      const amt = parseFloat(item.amount) || 0
      if (item.direction === 0) {
        expense += amt
        count++
      }
    })
    summaryData.value = { expense, count }
  } catch (e) {
    summaryData.value = null
  }
}

// 独立的防重标志：与 van-list 的 loading 分离，避免 van-list 预置 loading=true 导致卡加载
const requesting = ref(false)
const loadData = async () => {
  if (!billData.value.card_id) return
  if (requesting.value) return // 防重：并发触发同一请求时跳过
  requesting.value = true
  loading.value = true
  try {
    const startDate = formatDate(billData.value.bill_start_date)
    const endDate = formatDate(billData.value.bill_end_date)
    const params = {
      cardId: billData.value.card_id,
      page: page.value,
      limit,
      startDate,
      endDate
    }
    const res = await getAccountListByCard(params)
    const data = res.data?.list || res.data || []
    const pagination = res.data?.pagination || {}
    if (page.value === 1) list.value = data
    else list.value.push(...data)
    finished.value = data.length < limit || page.value >= (pagination.totalPages || 1)
    if (!finished.value) page.value++
  } catch (e) {
    finished.value = true
    showToast('加载失败')
  } finally {
    requesting.value = false
    loading.value = false
  }
}

const onRefresh = () => {
  page.value = 1
  finished.value = false
  loading.value = true
  Promise.all([loadSummary(), loadData()]).finally(() => {
    loading.value = false
    refreshing.value = false
  })
}

const goDetail = (item) => router.push(`/finance/flow/${item.id}`)

onMounted(async () => {
  pageLoading.value = true
  await loadBillDetail()
  if (billData.value.id) {
    await Promise.all([loadSummary(), loadData()])
  }
  pageLoading.value = false
})

onDeactivated(() => {
  savedScrollY.value = window.scrollY || document.documentElement.scrollTop
})

// keep-alive 首次挂载时 onActivated 也会触发，与 onMounted 重复加载。
// 用首次标志跳过（首次已由 onMounted 负责），仅处理后续返回/切换。
let firstActivated = true
onActivated(() => {
  if (firstActivated) {
    firstActivated = false
    return
  }
  // keep-alive 下切换不同 ?id 时组件复用，需重新加载
  const currentId = route.query.id
  if (currentId && currentId !== billData.value.id) {
    // 切到不同账单 → 重置 + 重拉
    billData.value = {}
    cardBankIcon.value = ''
    list.value = []
    summaryData.value = null
    page.value = 1
    finished.value = false
    loading.value = false
    // 关键：onActivated 是同步触发的，等下一帧再调 API，
    // 否则可能与 van-list 的首次自动 load 时机冲突
    nextTick(() => {
      loadBillDetail()
        .then(() => Promise.all([loadSummary(), loadData()]))
        .catch(() => {})
        .finally(() => {
          loading.value = false
        })
    })
    return
  }
  // 同一账单回到前台 → 恢复滚动位置
  nextTick(() => {
    if (savedScrollY.value > 0) window.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
  })
})
</script>

<style scoped>
.page-bill-ledger {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 20px;
}

.bill-info-section {
  padding: 8px 0;
}

.bill-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.bill-info-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bank-icon {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  object-fit: cover;
}

.bill-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.bill-card-last4 {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
}

.bill-info-right {
  text-align: right;
}

.bill-period {
  font-size: 12px;
  color: var(--theme-text-secondary);
}

.bill-month-tag {
  font-size: 11px;
  color: var(--theme-primary);
  margin-top: 4px;
}

.stats-section {
  padding: 0 0 8px;
}

.stats-row {
  display: flex;
  justify-content: space-around;
  width: 100%;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 16px;
  font-weight: bold;
  font-family: 'DIN Alternate', sans-serif;
}

.text-expense {
  color: var(--van-danger-color, #ee0a24);
}

.text-income {
  color: var(--van-green, #07c160);
}

.match-tip {
  text-align: center;
  font-size: 12px;
  color: var(--van-green, #07c160);
  padding-top: 8px;
}

.match-tip.mismatch {
  color: var(--van-danger-color, #ee0a24);
}

.date-group {
  margin-top: 8px;
}

.date-header {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-primary);
}

.date-items {
  background: var(--theme-bg-secondary);
}

.flow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  margin-left: 16px;
  border-bottom: 1px solid var(--theme-border);
}

.flow-item:last-child {
  border-bottom: none;
}

/* 信用卡还款：置灰 */
.flow-item.repay {
  filter: grayscale(1);
  opacity: 0.55;
}

/* 信用卡还款：置灰 */
.flow-item.repay {
  filter: grayscale(1);
  opacity: 0.55;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.category-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--theme-bg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--theme-primary);
}

.item-info .item-title {
  font-size: 15px;
  color: var(--theme-text-primary);
  font-weight: 500;
}

.item-info .item-desc {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 4px;
}

.item-right {
  text-align: right;
}

.item-right .amount {
  font-size: 16px;
  font-weight: bold;
  font-family: 'DIN Alternate', sans-serif;
}

.item-right .amount-cny {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
  font-family: 'DIN Alternate', sans-serif;
}

.item-right .income {
  color: var(--money-income);
}

.item-right .expense {
  color: var(--theme-text-primary);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
