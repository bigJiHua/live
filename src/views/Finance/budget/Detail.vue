<template>
  <div class="page-budget-detail">
    <van-loading v-if="loading" class="loading-center" vertical>加载中...</van-loading>

    <div v-else-if="detail" class="detail-content">
      <!-- 头部信息 -->
      <div class="header-card">
        <div class="header-title">
          <span>{{ detail.title }}</span>
          <van-tag :type="getTypeColor(detail.budget_type)" size="medium">
            {{ getTypeName(detail.budget_type) }}
          </van-tag>
        </div>
        <div class="header-route" v-if="detail.route">
          <van-icon name="location-o" /> {{ detail.route }}
        </div>
        <div class="header-date">
          <van-icon name="calendar-o" />
          {{ detail.plan_date }} · {{ detail.cycle }}
        </div>

        <!-- 金额概览 -->
        <div class="amount-overview">
          <div class="amount-item">
            <div class="amount-label">预算</div>
            <div class="amount-value">¥{{ formatAmount(detail.budget_amount) }}</div>
          </div>
          <div class="amount-item">
            <div class="amount-label">已用</div>
            <div class="amount-value used">¥{{ formatAmount(totalExpense) }}</div>
          </div>
          <div class="amount-item">
            <div class="amount-label">剩余</div>
            <div class="amount-value remaining" :class="{ negative: remaining < 0 }">
              ¥{{ formatAmount(remaining) }}
            </div>
          </div>
        </div>

        <!-- 进度 -->
        <div class="progress-section">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :class="{ over: detail.is_over_budget }"
              :style="{ width: progress + '%' }"
            ></div>
          </div>
          <div class="progress-info">
            <span>使用率 {{ progress }}%</span>
            <span v-if="detail.is_over_budget" class="over-warning">已超支</span>
          </div>
        </div>
      </div>

      <!-- ========== 出行预算 ========== -->
      <template v-if="detail.budget_type === '行'">
        <!-- 汇率信息 -->
        <div class="section-card" v-if="exchangeRates.length > 0">
          <div class="section-title">
            <van-icon name="exchange" /> 汇率设置
          </div>
          <div class="rate-tags">
            <span v-for="rate in exchangeRates" :key="rate.currency" class="rate-tag">
              {{ rate.currency }}: {{ rate.value }}
            </span>
          </div>
        </div>

        <!-- 每日明细 -->
        <div class="section-card">
          <div class="section-title">
            <van-icon name="orders-o" /> 每日消费明细
          </div>

          <div v-for="(day, dayIndex) in travelDays" :key="dayIndex" class="day-block">
            <div class="day-header">
              <van-icon name="calendar-o" />
              <span>{{ day.date }}</span>
            </div>

            <div v-if="day.items && day.items.length > 0">
              <div v-for="(item, itemIndex) in day.items" :key="itemIndex" class="expense-item">
                <div class="expense-left">
                  <van-tag :type="getExpenseTypeColor(item.type)" size="small" plain>
                    {{ item.type }}
                  </van-tag>
                  <span class="expense-desc">{{ item.description || '无描述' }}</span>
                </div>
                <div class="expense-right">
                  <span class="expense-amount">
                    {{ item.amount }} {{ item.currency }}
                  </span>
                  <span class="expense-cny" v-if="item.currency !== 'CNY'">
                    ≈ ¥{{ formatAmount(item.cny_amount) }}
                  </span>
                </div>
              </div>
            </div>
            <div v-else class="empty-day">暂无消费记录</div>

            <div class="day-total">
              <span>当天合计</span>
              <span class="day-total-value">¥{{ formatAmount(calcDayTotal(day)) }}</span>
            </div>
          </div>

          <van-empty v-if="travelDays.length === 0" description="暂无每日明细" />
        </div>
      </template>

      <!-- ========== 购物预算 ========== -->
      <template v-if="detail.budget_type === '买'">
        <div class="section-card">
          <div class="section-title">
            <van-icon name="shopping-cart-o" /> 购物清单
          </div>

          <div class="shop-stats">
            <div class="stat-item">
              <span class="stat-label">预计总额</span>
              <span class="stat-value">¥{{ formatAmount(shoppingEstimated) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">实际总额</span>
              <span class="stat-value danger">¥{{ formatAmount(shoppingActual) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">已购/总数</span>
              <span class="stat-value">{{ shoppingPurchased }}/{{ shoppingItems.length }}</span>
            </div>
          </div>

          <div v-for="(item, index) in shoppingItems" :key="index" class="shop-item">
            <div class="shop-item-header">
              <span class="shop-index">{{ index + 1 }}</span>
              <van-tag :type="getPriorityColor(item.priority)" size="small">
                {{ item.priority || '未设置' }}
              </van-tag>
            </div>
            <div class="shop-item-info">
              <div class="shop-name">{{ item.name || '未命名商品' }}</div>
              <div class="shop-detail">
                <span v-if="item.category">{{ item.category }}</span>
                <span v-if="item.shop"> · {{ item.shop }}</span>
              </div>
              <div class="shop-price">
                <span class="price-estimated">预计 ¥{{ formatAmount(item.price) }} × {{ item.quantity || 1 }}</span>
                <span v-if="item.actual_price" class="price-actual">实付 ¥{{ formatAmount(item.actual_price) }}</span>
              </div>
              <div v-if="item.purchase_date" class="shop-date">
                <van-icon name="clock-o" /> {{ item.purchase_date }}
              </div>
              <div v-if="item.notes" class="shop-notes">{{ item.notes }}</div>
            </div>
          </div>

          <van-empty v-if="shoppingItems.length === 0" description="暂无购物清单" />
        </div>
      </template>

      <!-- ========== 餐饮预算 ========== -->
      <template v-if="detail.budget_type === '吃'">
        <div class="section-card">
          <div class="section-title">
            <van-icon name="fire-o" /> 菜单明细
          </div>

          <div class="eat-total">
            <span>实际消费</span>
            <span class="eat-total-value">¥{{ formatAmount(eatActual) }}</span>
          </div>

          <div v-for="(dish, index) in eatDishes" :key="index" class="dish-item">
            <span class="dish-index">{{ index + 1 }}</span>
            <div class="dish-info">
              <div class="dish-name">{{ dish.name || '未命名菜品' }}</div>
              <div class="dish-detail">
                <span v-if="dish.notes">{{ dish.notes }}</span>
              </div>
            </div>
            <div class="dish-price">
              ¥{{ formatAmount(dish.price) }} × {{ dish.quantity || 1 }}
              <span class="dish-subtotal">= ¥{{ formatAmount(calcDishSubtotal(dish)) }}</span>
            </div>
          </div>

          <van-empty v-if="eatDishes.length === 0" description="暂无菜单明细" />
        </div>
      </template>

      <!-- 备注 -->
      <div class="section-card" v-if="detailNotes">
        <div class="section-title">
          <van-icon name="comment-o" /> 备注
        </div>
        <div class="notes-content">{{ detailNotes }}</div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <van-button size="large" round type="primary" @click="goEdit">
          编辑预算
        </van-button>
        <van-button size="large" round type="default" @click="handleDelete" class="delete-btn">
          删除预算
        </van-button>
      </div>
    </div>

    <van-empty v-if="!loading && !detail" description="预算不存在" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getBudget, deleteBudget } from '@/utils/api/budget'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const detail = ref(null)

// 计算属性
const remaining = computed(() => {
  if (!detail.value) return 0
  return parseFloat(detail.value.budget_amount || 0) - totalExpense.value
})

const progress = computed(() => {
  if (!detail.value) return 0
  const budget = parseFloat(detail.value.budget_amount) || 0
  if (budget === 0) return 0
  return Math.min(100, Math.round((totalExpense.value / budget) * 100))
})

// 出行相关
const travelDays = computed(() => detail.value?.budget_details?.days || [])
const exchangeRates = computed(() => detail.value?.budget_details?.exchange_rates || [])

const calcDayTotal = (day) => {
  if (!day.items) return 0
  return day.items.reduce((sum, item) => {
    if (item.currency === 'CNY') return sum + (parseFloat(item.amount) || 0)
    return sum + (parseFloat(item.cny_amount) || 0)
  }, 0)
}

const totalExpense = computed(() => {
  if (detail.value?.budget_type === '行') {
    return travelDays.value.reduce((sum, day) => sum + calcDayTotal(day), 0)
  }
  if (detail.value?.budget_type === '买') {
    return shoppingActual.value
  }
  if (detail.value?.budget_type === '吃') {
    return eatActual.value
  }
  return parseFloat(detail.value?.used_amount) || 0
})

// 购物相关
const shoppingItems = computed(() => detail.value?.budget_details?.items || [])
const shoppingEstimated = computed(() => detail.value?.budget_details?.estimated_total || 0)
const shoppingActual = computed(() => detail.value?.budget_details?.actual_total || 0)
const shoppingPurchased = computed(() => detail.value?.budget_details?.purchased_count || 0)

// 餐饮相关
const eatDishes = computed(() => detail.value?.budget_details?.dishes || [])
const eatActual = computed(() => detail.value?.budget_details?.actual_total || 0)

const calcDishSubtotal = (dish) => {
  return (parseFloat(dish.price) || 0) * (parseInt(dish.quantity) || 1)
}

// 备注
const detailNotes = computed(() => {
  if (detail.value?.budget_type === '行') return ''
  return detail.value?.budget_details?.notes || ''
})

// 工具函数
const formatAmount = (amount) => (parseFloat(amount) || 0).toFixed(2)

const getTypeColor = (type) => {
  const colors = { '买': 'danger', '行': 'primary', '吃': 'warning' }
  return colors[type] || 'default'
}

const getTypeName = (type) => {
  const names = { '买': '购物', '行': '出行', '吃': '餐饮' }
  return names[type] || type
}

const getExpenseTypeColor = (type) => {
  const colors = {
    '行': 'primary',
    '吃': 'danger',
    '喝': 'success',
    '买': 'warning',
    '住': 'purple',
    '玩': 'orange',
  }
  return colors[type] || 'default'
}

const getPriorityColor = (priority) => {
  const colors = { '必买': 'danger', '想要': 'warning', '可选': 'default' }
  return colors[priority] || 'default'
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getBudget(route.params.id)
    detail.value = res.data
  } catch (e) {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

const goEdit = () => {
  const type = detail.value?.budget_type
  if (type === '买') {
    router.push(`/finance/budget/shopping/${route.params.id}`)
  } else if (type === '行') {
    router.push(`/finance/budget/travel/${route.params.id}`)
  } else if (type === '吃') {
    router.push(`/finance/budget/eat/${route.params.id}`)
  }
}

const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '删除后不可恢复，是否继续？'
    })
    await deleteBudget(route.params.id)
    showToast('删除成功')
    router.replace('/finance/budget')
  } catch (e) {
    if (e !== 'cancel') {
      showToast(e.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.van-tag--purple {
  background: #f3e8ff !important;
  color: #9333ea !important;
  border-color: #9333ea !important;
}
.van-tag--orange {
  background: #fff7e6 !important;
  color: #ff8c00 !important;
  border-color: #ff8c00 !important;
}

.page-budget-detail {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.detail-content {
  padding: 12px 16px;
  padding-bottom: 24px;
}

/* 头部卡片 */
.header-card {
  background: linear-gradient(135deg, #1989fa, #1976d2);
  border-radius: 16px;
  padding: 20px;
  color: #fff;
  margin-bottom: 16px;
}

.header-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.header-route {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 4px;
}

.header-date {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  opacity: 0.85;
  margin-bottom: 16px;
}

.amount-overview {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.amount-item {
  text-align: center;
  flex: 1;
}

.amount-label {
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.amount-value {
  font-size: 18px;
  font-weight: 700;
}

.amount-value.used {
  color: #ffd000;
}

.amount-value.remaining {
  color: #90f0c8;
}

.amount-value.remaining.negative {
  color: #ffb3b3;
}

.progress-section {
  margin-top: 8px;
}

.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #90f0c8;
  border-radius: 3px;
  transition: width 0.3s;
}

.progress-fill.over {
  background: #ffb3b3;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.85;
  margin-top: 8px;
}

.over-warning {
  color: #ffd000;
  font-weight: 600;
}

/* 通用卡片 */
.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f2f3f5;
}

.section-title .van-icon {
  color: #1989fa;
}

/* 汇率 */
.rate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rate-tag {
  font-size: 12px;
  color: #646566;
  background: #f7f8fa;
  padding: 4px 10px;
  border-radius: 4px;
}

/* 出行-每日明细 */
.day-block {
  background: #fafafa;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.day-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1989fa;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #ebedf0;
}

.expense-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.expense-item:last-of-type {
  border-bottom: none;
}

.expense-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.expense-desc {
  font-size: 13px;
  color: #323233;
}

.expense-right {
  text-align: right;
}

.expense-amount {
  font-size: 14px;
  font-weight: 600;
  color: #ee0a24;
}

.expense-cny {
  display: block;
  font-size: 11px;
  color: #969799;
}

.empty-day {
  text-align: center;
  color: #969799;
  font-size: 13px;
  padding: 8px 0;
}

.day-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebedf0;
  font-size: 13px;
  color: #646566;
}

.day-total-value {
  font-weight: 600;
  color: #1989fa;
}

/* 购物统计 */
.shop-stats {
  display: flex;
  justify-content: space-between;
  background: #f7f8fa;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.stat-item {
  text-align: center;
  flex: 1;
}

.stat-label {
  display: block;
  font-size: 11px;
  color: #969799;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.stat-value.danger {
  color: #ee0a24;
}

/* 购物项 */
.shop-item {
  background: #fafafa;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
}

.shop-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.shop-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #1989fa;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
}

.shop-item-info {
  padding-left: 28px;
}

.shop-name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 4px;
}

.shop-detail {
  font-size: 12px;
  color: #969799;
  margin-bottom: 6px;
}

.shop-price {
  font-size: 13px;
  margin-bottom: 4px;
}

.price-estimated {
  color: #646566;
}

.price-actual {
  color: #ee0a24;
  font-weight: 600;
  margin-left: 10px;
}

.shop-date {
  font-size: 12px;
  color: #969799;
  display: flex;
  align-items: center;
  gap: 4px;
}

.shop-notes {
  font-size: 12px;
  color: #969799;
  font-style: italic;
  margin-top: 4px;
}

/* 餐饮统计 */
.eat-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff0f0;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.eat-total-value {
  font-size: 18px;
  font-weight: 700;
  color: #ee0a24;
}

/* 菜品项 */
.dish-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.dish-item:last-child {
  border-bottom: none;
}

.dish-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #ee0a24;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.dish-info {
  flex: 1;
}

.dish-name {
  font-size: 14px;
  color: #323233;
  margin-bottom: 2px;
}

.dish-detail {
  font-size: 12px;
  color: #969799;
}

.dish-price {
  font-size: 13px;
  color: #646566;
  text-align: right;
}

.dish-subtotal {
  color: #ee0a24;
  font-weight: 600;
}

/* 备注 */
.notes-content {
  font-size: 14px;
  color: #646566;
  line-height: 1.6;
}

/* 操作按钮 */
.action-buttons {
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.delete-btn {
  color: #ee0a24;
}
</style>
