<template>
  <div class="page-report">
    <div class="section">
      <div class="section-title">流水管理</div>
      <div class="menu-card">
        <div class="menu-item" @click="$router.push('/finance/report/flow-filter')">
          <div class="menu-icon"><van-icon name="orders-o" /></div>
          <div class="menu-info">
            <div class="menu-title">流水筛选</div>
            <div class="menu-desc">按时段、分类筛选流水并支持导出 Excel</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/card-flow')">
          <div class="menu-icon"><van-icon name="credit-pay" /></div>
          <div class="menu-info">
            <div class="menu-title">银行卡收支明细</div>
            <div class="menu-desc">按银行卡查询历史收支流水</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/transfer-list')">
          <div class="menu-icon"><van-icon name="exchange" /></div>
          <div class="menu-info">
            <div class="menu-title">转账明细查询</div>
            <div class="menu-desc">查看所有转账记录</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">数据统计</div>
      <div class="menu-card">
        <div class="menu-item" @click="$router.push('/finance/report/stats-overview')">
          <div class="menu-icon"><van-icon name="chart-trending-o" /></div>
          <div class="menu-info">
            <div class="menu-title">收支总额与结余</div>
            <div class="menu-desc">总收入、总支出、净结余速览</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/category-ratio')">
          <div class="menu-icon"><van-icon name="graphic" /></div>
          <div class="menu-info">
            <div class="menu-title">类目消费占比</div>
            <div class="menu-desc">各消费分类占比统计图表</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
        <div class="menu-item" @click="$router.push('/finance/report/monthly-trend')">
          <div class="menu-icon"><van-icon name="bar-chart-o" /></div>
          <div class="menu-info">
            <div class="menu-title">月度收支趋势</div>
            <div class="menu-desc">每月收支变化趋势图表</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">负债统计</div>
      <div class="menu-card">
        <div class="menu-item" @click="$router.push('/finance/report/debt-overview')">
          <div class="menu-icon"><van-icon name="balance-list-o" /></div>
          <div class="menu-info">
            <div class="menu-title">负债总览</div>
            <div class="menu-desc">卡有效期预警、负债明细、支出类别统计</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-title">理财投资</div>
      <div class="menu-card">
        <div class="menu-item" @click="$router.push('/finance/report/fund')">
          <div class="menu-icon"><van-icon name="gold-coin-o" /></div>
          <div class="menu-info">
            <div class="menu-title">理财总览</div>
            <div class="menu-desc">基金持仓、市值、收益概览</div>
          </div>
          <van-icon name="arrow" color="#c8c9cc" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.page-report {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 12px 16px 30px;
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

.menu-item:last-child { border-bottom: none; }

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
</style>