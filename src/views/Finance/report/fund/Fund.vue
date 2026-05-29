<template>
  <div class="page-fund" v-if="!loading">
    <div class="summary-cards">
      <div class="sc-item">
        <div class="sc-label">持有基金</div>
        <div class="sc-value">{{ fundList.length }}支</div>
      </div>
      <div class="sc-item">
        <div class="sc-label">当前本金</div>
        <div class="sc-value">¥{{ formatAmount(totalInvested) }}</div>
      </div>
      <div class="sc-item">
        <div class="sc-label">当前市值</div>
        <div class="sc-value primary">¥{{ formatAmount(totalMarketVal) }}</div>
      </div>
      <div class="sc-item">
        <div class="sc-label">累计收益</div>
        <div class="sc-value" :class="totalEarnings >= 0 ? 'success' : 'danger'">
          {{ totalEarnings >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(totalEarnings)) }}
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">操作</div>
      <div class="menu-card">
        <div class="menu-item" @click="$router.push('/finance/report/fund/register')">
          <div class="menu-icon"><van-icon name="edit" /></div>
          <div class="menu-info">
            <div class="menu-title">理财登记</div>
            <div class="menu-desc">新增 / 编辑 / 删除持有的基金</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/fund/daily')">
          <div class="menu-icon">
            <van-icon name="edit" style="color: #07c160" />
          </div>
          <div class="menu-info">
            <div class="menu-title">每日收益登记</div>
            <div class="menu-desc">记录今日收益和增持本金</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/fund/trend')">
          <div class="menu-icon"><van-icon name="chart-trending-o" /></div>
          <div class="menu-info">
            <div class="menu-title">变化走势图</div>
            <div class="menu-desc">查看累计收益和当前市值变化</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/fund/earnings')">
          <div class="menu-icon"><van-icon name="gold-coin-o" /></div>
          <div class="menu-info">
            <div class="menu-title">收益明细</div>
            <div class="menu-desc">每支基金的收益详情与回报率</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">持仓概览</div>
      <div class="fund-list" v-if="fundList.length > 0">
        <div
          v-for="f in fundList"
          :key="f.id"
          class="fund-card"
          @click="$router.push('/finance/report/fund/trend')"
        >
          <div class="fc-top">
            <div class="fc-name">{{ f.fund_name }}</div>
            <div class="fc-amount">¥{{ formatAmount(getFundMarketVal(f)) }}</div>
          </div>
          <div class="fc-mid">
            <span class="fc-company">{{ f.fund_company }}</span>
            <span class="fc-rate" :class="getFundRate(f) >= 0 ? 'success' : 'danger'">
              {{ getFundRate(f) >= 0 ? '+' : '' }}{{ formatRate(getFundRate(f)) }}
            </span>
          </div>
          <div class="fc-bottom">
            <span>份额 {{ safeNum(f.share) }}</span>
            <span class="fc-profit" :class="getFundProfit(f) >= 0 ? 'success' : 'danger'">收益 {{ getFundProfit(f) >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(getFundProfit(f))) }}</span>
            <span>购入 {{ f.buy_date || '-' }}</span>
          </div>
        </div>
      </div>
      <van-empty v-else description="暂无基金数据，点上方「理财登记」添加" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getFundList } from '@/utils/api/fund'
import { showToast } from 'vant'

const loading = ref(true)
const fundList = ref([])

const formatAmount = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  const sign = n < 0 ? '-' : ''
  const abs = Math.abs(n)
  const units = [
    { value: 100000000, label: '亿' },
    { value: 10000000, label: '千万' },
    { value: 10000, label: '万' },
  ]
  const unit = units.find(item => abs >= item.value)
  if (!unit) return `${sign}${String(Math.trunc(abs))}`
  return `${sign}${(abs / unit.value).toFixed(3).replace(/\.?0+$/, '')}${unit.label}`
}

const safeNum = (v, digits = 2) => {
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(digits) : '0.00'
}

const toNumber = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

const getFundMarketVal = (f) => toNumber(f?.market_val)
const getFundProfit = (f) => toNumber(f?.profit_delta ?? f?.net_value)
const getFundRate = (f) => {
  const invest = toNumber(f?.invest)
  return invest > 0 ? Math.round((getFundProfit(f) / invest) * 10000) / 100 : 0
}
const formatRate = (v) => `${Number(v || 0).toFixed(2)}%`

const totalInvested = computed(() =>
  fundList.value.reduce((s, f) => s + toNumber(f.invest), 0)
)
const totalMarketVal = computed(() =>
  fundList.value.reduce((s, f) => s + getFundMarketVal(f), 0)
)
const totalEarnings = computed(() =>
  fundList.value.reduce((s, f) => s + getFundProfit(f), 0)
)

onMounted(async () => {
  try {
    const res = await getFundList()
    fundList.value = res.data?.list || []
  } catch (e) {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-fund {
  background: #f7f8fa;
  padding: 15px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 20px;
}
.sc-item {
  background: #fff;
  border-radius: 10px;
  min-width: 0;
  padding: 10px 4px;
  text-align: center;
}
.sc-label {
  font-size: 10px;
  color: #969799;
  margin-bottom: 4px;
  white-space: nowrap;
}
.sc-value {
  font-size: 15px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: clip;
}
.sc-value.primary {
  color: #1989fa;
}
.sc-value.success {
  color: #ee0a24;
}
.sc-value.danger {
  color: #07c160;
}

.section {
  margin-bottom: 20px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 10px;
  padding-left: 2px;
}

.menu-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f0f5ff;
  color: #1989fa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.menu-info {
  flex: 1;
}
.menu-title {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}
.menu-desc {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.fund-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 40vh;
  overflow-y: scroll;
}
.fund-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  cursor: pointer;
}
.fc-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.fc-name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}
.fc-amount {
  font-size: 16px;
  font-weight: 700;
  color: #1989fa;
  font-family: 'DIN Alternate', sans-serif;
  white-space: nowrap;
}
.fc-mid {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.fc-company {
  font-size: 11px;
  color: #969799;
}
.fc-rate {
  font-size: 14px;
  font-weight: 600;
  font-family: 'DIN Alternate', sans-serif;
}
.fc-rate.success {
  color: #ee0a24;
}
.fc-rate.danger {
  color: #07c160;
}
.fc-profit.success {
  color: #ee0a24;
}
.fc-profit.danger {
  color: #07c160;
}
.fc-bottom {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #969799;
}
</style>
