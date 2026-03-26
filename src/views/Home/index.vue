<template>
  <div class="page-home">
    <div class="app-card total-assets-card">
      <div class="header-main">
        <div class="label-group">
          <span class="label">预估总资产 (元)</span>
          <van-icon :name="showAmount ? 'eye-o' : 'closed-eye'" @click="toggleEye" class="eye-icon" />
        </div>
        <div class="total-amount num-font">
          {{ showAmount ? formatMoney(dashboardData.totalBalance) : '****' }}
        </div>
      </div>

      <van-row class="asset-details">
        <van-col span="8" class="detail-item" @click="goSubPage('cards')">
          <div class="small-label">银行卡</div>
          <div class="num-font">{{ dashboardData.cardCount }} <small>张</small></div>
        </van-col>
        <van-col span="8" class="detail-item" @click="goSubPage('credit')">
          <div class="small-label">待还信用卡</div>
          <div class="num-font text-warning">{{ formatMoney(dashboardData.creditToPay) }}</div>
        </van-col>
        <van-col span="8" class="detail-item">
          <div class="small-label">本月结余</div>
          <div class="num-font">{{ formatMoney(dashboardData.monthlySurplus) }}</div>
        </van-col>
      </van-row>
    </div>

    <div class="app-card menu-grid-card">
      <van-grid :column-num="4" :border="false">
        <van-grid-item icon="chart-trending-o" text="报表" @click="goFunction('report')" />
        <van-grid-item icon="balance-list-o" text="预算表" @click="goFunction('budget')" />
        <van-grid-item icon="gold-coin-o" text="理财预期" @click="goFunction('invest')" />
        <van-grid-item icon="todo-list-o" text="薪资计算" @click="goFunction('salary')" />
      </van-grid>
    </div>

    <div class="app-card info-card">
      <div class="info-row" @click="goFunction('salary-detail')">
        <div class="info-left">
          <van-icon name="manager-o" class="info-icon" />
          <span class="info-label">今日预估薪酬</span>
        </div>
        <div class="info-right num-font text-income">
          +{{ formatMoney(dashboardData.dailySalary) }}
        </div>
      </div>
      
      <van-divider style="margin: 10px 0" />

      <div class="info-row" @click="goSubPage('todo')">
        <div class="info-left">
          <van-icon name="clock-o" class="info-icon" />
          <span class="info-label">待办提醒</span>
        </div>
        <div class="info-right">
          <van-tag type="danger" round>{{ dashboardData.todoCount }} 条未处理</van-tag>
          <van-icon name="arrow" class="arrow-right" />
        </div>
      </div>
    </div>

    <van-cell-group inset class="recent-records">
      <template #title>最近大额流水</template>
      <van-cell v-for="item in recentItems" :key="item.id" :title="item.title" :label="item.time">
        <template #value>
          <span class="num-font" :class="item.type === 'exp' ? 'text-expense' : 'text-income'">
            {{ item.type === 'exp' ? '-' : '+' }}{{ formatMoney(item.amount) }}
          </span>
        </template>
      </van-cell>
    </van-cell-group>

    <div style="height: 50px"></div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';

// 接口数据预留 (1.1, 1.3, 1.4)
const dashboardData = reactive({
  totalBalance: 128450.60, // 总资产
  cardCount: 5,           // 银行卡数量
  creditToPay: 3200.00,   // 信用卡待还
  monthlySurplus: 5260.40, // 本月结余
  dailySalary: 450.00,    // 今日日薪
  todoCount: 3            // 待办数
});

const recentItems = ref([
  { id: 1, title: '投资回报-天天盈', time: '今天 09:00', amount: 12.50, type: 'inc' },
  { id: 2, title: '固定资产折旧-电脑', time: '昨天 23:59', amount: 8.33, type: 'exp' }
]);

const showAmount = ref(true);

// 方法接口预留
const toggleEye = () => { showAmount.value = !showAmount.value; };

// 1.2 功能跳转逻辑
const goFunction = (type) => { console.log('跳转至功能模块:', type); };

// 1.1 分支入口跳转
const goSubPage = (page) => { console.log('跳转至子管理页:', page); };

// 格式化金额
const formatMoney = (val) => Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2 });
</script>

<style scoped>
.page-home {
  padding: 4px 12px;
}

.total-assets-card {
  background: linear-gradient(135deg, #1989fa, #0570db);
  color: white;
  padding: 24px 20px;
  border-radius: 20px;
  margin-bottom: 16px;
  box-shadow: 0 8px 20px rgba(25, 137, 250, 0.3);
}

.header-main {
  margin-bottom: 24px;
}

.label-group {
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0.85;
  font-size: 14px;
}

.total-amount {
  font-size: 38px;
  margin-top: 8px;
  font-weight: 600;
}

.asset-details {
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  padding-top: 16px;
  text-align: center;
}

.small-label {
  font-size: 11px;
  opacity: 0.7;
  margin-bottom: 4px;
}

.detail-item .num-font {
  font-size: 16px;
}

.menu-grid-card {
  padding: 8px 0;
  border-radius: 16px;
  margin-bottom: 16px;
}

.info-card {
  background: white;
  padding: 16px;
  border-radius: 16px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.info-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.info-icon {
  font-size: 20px;
  color: #1989fa;
}

.info-label {
  font-size: 15px;
  color: #323233;
}

.text-warning { color: #ffe1e1; } /* 信用卡待还建议浅色区分 */
.text-income { color: #07c160; font-weight: bold; }
.text-expense { color: #ee0a24; }

.recent-records {
  margin: 0 -12px; /* 抵消 page-home 的 padding，让标题对齐边缘 */
}
</style>