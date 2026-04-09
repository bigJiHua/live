<template>
  <div class="page-flow-detail">
    <!-- 加载状态 -->
    <van-loading v-if="loading" class="loading-center" vertical>加载中...</van-loading>

    <!-- 详情内容 -->
    <div v-else-if="detail" class="detail-content">
      <!-- 金额卡片 -->
      <div class="amount-card">
        <div class="amount-label">
          {{ detail.direction === 1 ? '收入' : '支出' }}
          <span v-if="showExchange" class="currency-tag">
            {{ getCurrencySymbol(detail.currency) }} {{ detail.amount }}
          </span>
        </div>
        <div class="amount-value" :class="detail.direction === 1 ? 'income' : 'expense'">
          {{ detail.direction === 1 ? '+' : '-' }}¥{{ formatAmount(detail.amount) }}
        </div>
        <div class="exchange-info" v-if="showExchange">
          约 {{ getCurrencySymbol(detail.currency) }}{{ detail.amount }} = ¥{{ formatAmount(detail.amount) }}
        </div>
      </div>

      <!-- 信息列表 -->
      <van-cell-group inset class="info-group">
        <van-cell title="分类">
          <template #value>
            <span>{{ detail.category_name || '未知分类' }}</span>
          </template>
        </van-cell>
        <van-cell title="币种">
          <template #value>
            <span>{{ detail.currency || 'CNY' }}</span>
          </template>
        </van-cell>
        <van-cell v-if="detail.currency && detail.currency !== 'CNY'" title="汇率">
          <template #value>
            <span>{{ detail.exchange_rate || '-' }}</span>
          </template>
        </van-cell>
        <van-cell title="时间">
          <template #value>
            <span>{{ formatDateTime(detail.create_time) }}</span>
          </template>
        </van-cell>
        <van-cell title="交易方式">
          <template #value>
            <span>{{ detail.pay_method || '-' }}</span>
          </template>
        </van-cell>
        <van-cell title="关联卡片">
          <template #value>
            <span>{{ getCardText(detail.card_id) }}</span>
          </template>
        </van-cell>
        <van-cell title="交易日期">
          <template #value>
            <span>{{ detail.trans_date || '-' }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 备注 -->
      <van-cell-group v-if="detail.remark" inset class="info-group">
        <van-cell title="备注">
          <template #value>
            <span class="remark-text">{{ detail.remark }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 提示横幅 -->
      <div class="notice-banner">
        <van-icon name="info-o" />
        <span>如记录有误，请删除后重新添加</span>
      </div>

      <!-- 操作按钮 -->
      <div class="action-btns">
        <van-button type="danger" block round @click="handleDelete">
          删除
        </van-button>
      </div>
    </div>

    <!-- 空状态 -->
    <van-empty v-else description="未找到相关记录" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { getAccountDetail, deleteAccount } from '@/utils/api/account'
import { getCardList } from '@/utils/api/card'

dayjs.locale('zh-cn')

const router = useRouter()
const route = useRoute()

// 状态
const loading = ref(true)
const detail = ref(null)
const cardList = ref([])

// 格式化金额（根据币种和汇率计算）
const formatAmount = (amount) => {
  if (amount === null || amount === undefined) return '0.00'
  const num = Number(amount)
  if (!detail.value?.currency || detail.value.currency === 'CNY') {
    return num.toFixed(2)
  }
  // 外币：金额 * 汇率 / 100
  const rate = Number(detail.value.exchange_rate) || 0
  return (num * rate / 100).toFixed(2)
}

// 获取币种符号
const getCurrencySymbol = (code) => {
  const symbols = {
    CNY: '¥',
    USD: '$',
    EUR: '€',
    HKD: 'HK$',
    JPY: '¥',
    GBP: '£',
    KRW: '₩',
    TWD: 'NT$'
  }
  return symbols[code] || code || '¥'
}

// 是否显示汇率换算
const showExchange = computed(() => {
  return detail.value?.currency && detail.value.currency !== 'CNY'
})

// 格式化日期时间（处理时间戳）
const formatDateTime = (timestamp) => {
  if (!timestamp) return '-'
  const d = dayjs(Number(timestamp))
  if (!d.isValid()) return '-'
  return d.format('YYYY-MM-DD HH:mm:ss')
}

// 获取卡片显示文本
const getCardText = (cardId) => {
  if (!cardId) return '-'
  if (cardId === 'xxxx') return '现金'
  if (cardId === 'yyyy') return '余额'
  // 优先使用返回的别名
  if (detail.value?.card_alias) return detail.value.card_alias
  if (detail.value?.card_last4) return `****${detail.value.card_last4}`
  // 尝试从卡片列表匹配
  const card = cardList.value.find(c => c.id === cardId)
  if (card) {
    return card.alias || card.bank_name || `****${card.last4_no || card.last4No}`
  }
  return cardId
}

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList()
    cardList.value = res.data || res || []
  } catch (e) {
    cardList.value = []
  }
}

// 加载详情
const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getAccountDetail(route.params.id)
    detail.value = res.data || res
  } catch (e) {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

// 删除
const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除这条记录吗？删除后不可恢复。'
    })
    
    await deleteAccount(route.params.id)
    showToast('删除成功')
    router.back()
  } catch (e) {
    if (e !== 'cancel') {
      showToast('删除失败')
    }
  }
}

// 初始化
onMounted(() => {
  loadCardList()
  loadDetail()
})
</script>

<style scoped>
.page-flow-detail {
  min-height: 100vh;
  background: #f7f8fa;
}

.loading-center {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.amount-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  text-align: center;
  color: #fff;
}

.amount-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.currency-tag {
  margin-left: 8px;
  padding: 2px 8px;
  background: rgba(255,255,255,0.2);
  border-radius: 10px;
  font-size: 12px;
}

.exchange-info {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.7;
}

.amount-value {
  font-size: 36px;
  font-weight: bold;
  font-family: 'DIN Alternate', -apple-system, sans-serif;
}

.amount-value.income {
  color: #07c160;
}

.amount-value.expense {
  color: #fff;
}

.info-group {
  margin: 16px;
  border-radius: 12px;
  overflow: hidden;
}

.remark-text {
  color: #646566;
  word-break: break-all;
}

.notice-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 16px;
  padding: 12px 16px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 13px;
  color: #fa8c16;
}

.notice-banner .van-icon {
  font-size: 16px;
}

.action-btns {
  padding: 20px 16px;
  display: flex;
  gap: 12px;
}

.action-btns .van-button {
  flex: 1;
}
</style>
