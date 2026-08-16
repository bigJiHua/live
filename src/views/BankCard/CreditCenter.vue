<template>
  <div class="page-credit-center">
    <!-- 顶部卡片统计 -->
    <div class="credit-header">
      <div class="header-title">
        <van-icon name="credit-pay" class="title-icon" />
        <span>信用卡专项</span>
      </div>
      <div class="header-stats">
        <div class="stat-item" @click="goTo('/card/credit')">
          <span class="stat-value">{{ cardCount }}</span>
          <span class="stat-label">卡片数量</span>
        </div>
        <div class="stat-item" @click="goTo('/card/bill/list')">
          <span class="stat-value">{{ billCount }}</span>
          <span class="stat-label">待还账单</span>
        </div>
      </div>
    </div>

    <!-- 功能入口 -->
    <div class="function-list">
      <!-- 卡片管理 -->
      <div class="func-section">
        <div class="func-section-title">卡片管理</div>
        <div class="func-card" @click="goTo('/card/credit')">
          <div class="func-icon blue">
            <van-icon name="card" />
          </div>
          <div class="func-info">
            <div class="func-name">我的卡片</div>
            <div class="func-desc">查看所有信用卡</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
      </div>

      <!-- 账单管理 -->
      <div class="func-section">
        <div class="func-section-title">账单管理</div>
        <div class="func-card" @click="goTo('/card/bill/list')">
          <div class="func-icon red">
            <van-icon name="todo-list-o" />
          </div>
          <div class="func-info">
            <div class="func-name">账单列表</div>
            <div class="func-desc">查看所有信用卡账单</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
        <div class="func-card" @click="goTo('/card/bill/add')">
          <div class="func-icon orange">
            <van-icon name="plus" />
          </div>
          <div class="func-info">
            <div class="func-name">添加账单</div>
            <div class="func-desc">新增一笔账单记录</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
      </div>

      <!-- 分期管理 -->
      <div class="func-section">
        <div class="func-section-title">分期管理</div>
        <div class="func-card" @click="goTo('/credit-center/installment')">
          <div class="func-icon purple">
            <van-icon name="plus" />
          </div>
          <div class="func-info">
            <div class="func-name">创建分期</div>
            <div class="func-desc">新建信用卡分期</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
        <div class="func-card" @click="goTo('/credit-center/installment/list')">
          <div class="func-icon purple">
            <van-icon name="bill-o" />
          </div>
          <div class="func-info">
            <div class="func-name">分期列表</div>
            <div class="func-desc">查看所有分期记录</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
      </div>

      <!-- 还款管理 -->
      <div class="func-section">
        <div class="func-section-title">还款管理</div>
        <div class="func-card" @click="goTo('/card/repay/list')">
          <div class="func-icon green">
            <van-icon name="paid" />
          </div>
          <div class="func-info">
            <div class="func-name">还款记录</div>
            <div class="func-desc">查看所有还款记录</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
        <div class="func-card" @click="goTo('/card/repay/add')">
          <div class="func-icon teal">
            <van-icon name="plus" />
          </div>
          <div class="func-info">
            <div class="func-name">添加还款</div>
            <div class="func-desc">记录一笔还款</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
      </div>

      <!-- 额度与外币（重构新增） -->
      <div class="func-section">
        <div class="func-section-title">额度与外币</div>
        <div class="func-card" @click="goTo('/credit/limit-manage')">
          <div class="func-icon gold">
            <van-icon name="gold-coin-o" />
          </div>
          <div class="func-info">
            <div class="func-name">额度与共享池管理</div>
            <div class="func-desc">固定/临时额度独立设置 · 同银行共享额度</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
        <div class="func-card" @click="goTo('/credit/foreign-register')">
          <div class="func-icon gold">
            <van-icon name="exchange" />
          </div>
          <div class="func-info">
            <div class="func-name">外币消费登记对账</div>
            <div class="func-desc">按银行实际结算汇率入账</div>
          </div>
          <van-icon name="arrow" class="func-arrow" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCardList } from '@/utils/api/card'
import { getBillList } from '@/utils/api/card'

const router = useRouter()
const cardCount = ref(0)
const billCount = ref(0)

const goTo = (path) => {
  router.push(path)
}

// 加载统计数据
const loadStats = async () => {
  try {
    // 获取信用卡数量
    const res = await getCardList({ cardType: 'credit' })
    const data = res.data || res || []
    cardCount.value = data.length

    // 获取待还账单数量
    const billRes = await getBillList({})
    const billData = billRes.data || billRes
    const billList = Array.isArray(billData) ? billData : (billData.list || [])
    // 统计未还清的账单数量
    billCount.value = billList.filter(b => Number(b.need_repay || b.needRepay || 0) > 0).length
  } catch (e) {
    cardCount.value = 0
    billCount.value = 0
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.page-credit-center {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding: 16px;
}

.credit-header {
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad));
  border-radius: 20px;
  padding: 24px 20px;
  color: #fff;
  margin-bottom: 20px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
}

.title-icon {
  font-size: 24px;
}

.header-stats {
  display: flex;
  gap: 40px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
}

.stat-label {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 4px;
}

.function-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.func-section {
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  padding: 16px;
}

.func-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 12px;
  padding-left: 4px;
}

.func-card {
  display: flex;
  align-items: center;
  padding: 12px 8px;
  border-radius: 12px;
  transition: background 0.2s;
}

.func-card:active {
  background: var(--theme-bg-primary);
}

.func-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-right: 12px;
}

.func-icon.blue {
  color: var(--van-blue, #1989fa);
}

.func-icon.red {
  color: var(--van-danger-color, #ee0a24);
}

.func-icon.orange {
  color: var(--van-orange, #ff976a);
}

.func-icon.green {
  color: var(--van-green, #07c160);
}

.func-icon.teal {
  color: #009688;
}
.func-icon.purple {
  color: #7232dd;
}
.func-icon.gold {
  color: #ffb300;
}

.func-info {
  flex: 1;
}

.func-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--theme-text-primary);
}

.func-desc {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
}

.func-arrow {
  color: var(--theme-text-tertiary);
  font-size: 16px;
}
</style>
