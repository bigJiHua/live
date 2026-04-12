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
          <div class="func-icon">
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
  background: #f7f8fa;
  padding: 16px;
}

.credit-header {
  background: linear-gradient(135deg, #ee0a24 0%, #b80c19 100%);
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
  background: #fff;
  border-radius: 16px;
  padding: 16px;
}

.func-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
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
  background: #f7f8fa;
}

.func-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #1989fa;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-right: 12px;
}

.func-icon.red {
  background: #ee0a24;
}

.func-icon.orange {
  background: #ff976a;
}

.func-icon.green {
  background: #07c160;
}

.func-icon.teal {
  background: #009688;
}

.func-info {
  flex: 1;
}

.func-name {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
}

.func-desc {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
}

.func-arrow {
  color: #c8c9cc;
  font-size: 16px;
}
</style>
