<template>
  <div class="page-transfer-list">
    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>
    <template v-if="!loading">
      <div class="stats-summary" v-if="list.length > 0">
        <div class="summary-item"><span class="label">笔数</span><span class="value">{{ total }}</span></div>
      </div>
      <van-list v-model:loading="listLoading" :finished="finished" finished-text="没有更多了" @load="loadData">
        <div v-for="item in list" :key="item.id" class="transfer-item">
          <div class="ti-header">
            <span class="ti-from">{{ getCardText(item.from_card_id) }}</span>
            <van-icon name="arrow" class="ti-arrow" />
            <span class="ti-to">{{ getCardText(item.to_card_id) }}</span>
          </div>
          <div class="ti-body">
            <span class="ti-amount expense">-¥{{ formatAmount(item.amount) }}</span>
            <span class="ti-date">{{ item.trans_date }}</span>
          </div>
          <div class="ti-remark" v-if="item.remark && item.remark !== '转账'">{{ item.remark }}</div>
        </div>
        <van-empty v-if="!listLoading && list.length === 0" description="暂无转账记录" />
      </van-list>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getTransferList } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'

const loading = ref(true)
const listLoading = ref(false)
const finished = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const limit = 20
const allCards = ref([])
const bankList = ref([])

const formatAmount = (v) => { if (!v && v !== 0) return '0.00'; const abs = Math.abs(Number(v)); if (abs >= 10000) return (abs / 10000).toFixed(3) + '万'; return abs.toFixed(2) }
const getCardText = (cardId) => {
  if (!cardId) return '-'; if (cardId === 'xxxx') return '现金'; if (cardId === 'yyyy') return '余额'
  const card = allCards.value.find((c) => c.id === cardId)
  if (!card) return cardId; const bankId = card.bank_id || card.bankId
  const bank = bankId ? bankList.value.find((b) => b.id === bankId) : null
  const bankName = bank?.name || card.alias || card.bank_name || ''; const last4 = card.last4_no || card.last4No || card.card_last4 || ''
  if (bankName && last4) return `${bankName} ${last4}`; if (bankName) return bankName; if (last4) return `****${last4}`
  return card.alias || card.bank_name || cardId
}
const loadData = async () => {
  if (page.value === 1) listLoading.value = true
  try {
    const res = await getTransferList({ page: page.value, limit }); const data = res.data || {}; const rows = data.list || []
    if (page.value === 1) list.value = rows; else list.value.push(...rows)
    total.value = data.total || rows.length; finished.value = rows.length < limit; if (!finished.value) page.value++
  } catch (e) { finished.value = true } finally { listLoading.value = false; loading.value = false }
}
onMounted(async () => {
  const [cardRes, bankRes] = await Promise.all([getCardList().catch(() => ({ data: [] })), categoryApi.list('bank').catch(() => ({ data: [] }))])
  allCards.value = cardRes.data || cardRes || []; bankList.value = bankRes.data || bankRes || []; loadData()
})
</script>

<style scoped>
.page-transfer-list { min-height: 100vh; background: #f7f8fa; }
.page-loading { display: flex; justify-content: center; padding: 60px 0; }
.stats-summary { display: flex; justify-content: center; padding: 12px 16px; }
.summary-item { text-align: center; }
.summary-item .label { font-size: 12px; color: #969799; display: block; }
.summary-item .value { font-size: 16px; font-weight: 700; color: #323233; }
.transfer-item { background: #fff; margin: 8px 16px; border-radius: 12px; padding: 14px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.04); }
.ti-header { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #323233; margin-bottom: 8px; }
.ti-arrow { font-size: 14px; color: #c8c9cc; }
.ti-body { display: flex; justify-content: space-between; align-items: center; }
.ti-amount { font-size: 16px; font-weight: 700; font-family: 'DIN Alternate', sans-serif; }
.ti-amount.expense { color: #ee0a24; }
.ti-date { font-size: 12px; color: #969799; }
.ti-remark { font-size: 11px; color: #969799; margin-top: 6px; }
</style>
