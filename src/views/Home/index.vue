<template>
  <div class="page-home">
    <div class="app-card total-assets-card">
      <!-- 日期牌 -->
      <div class="date-badge" @click="goSubPage('todo')">
        <div class="date-month" @click="goSubPage('todo')">
          {{ currentMonth }}月
        </div>
        <div class="date-day" @click="goSubPage('todo')">{{ currentDay }}</div>
      </div>

      <div class="header-main">
        <div class="label-group">
          <span class="label">预估总资产 (元)</span>
          <van-icon
            :name="showAmount ? 'eye-o' : 'closed-eye'"
            @click="toggleEye"
            class="eye-icon"
          />
        </div>
        <div class="amount-row">
          <div class="total-amount num-font">
            {{ showAmount ? formatMoney(dashboardData.totalBalance) : "****" }}
          </div>
          <div class="income-expense">
            <div class="income-item">
              <span class="ie-label">今日收入</span>
              <span class="num-font text-income"
                >{{ showAmount ? "+" : ""
                }}{{
                  showAmount ? formatMoney(dashboardData.todayIncome) : "****"
                }}</span
              >
            </div>
            <div class="income-item">
              <span class="ie-label">今日支出</span>
              <span class="num-font text-expense"
                >{{ showAmount ? "-" : ""
                }}{{
                  showAmount ? formatMoney(dashboardData.todayExpense) : "****"
                }}</span
              >
            </div>
          </div>
        </div>
      </div>

      <van-row class="asset-details">
        <van-col span="6" class="detail-item" @click="goSubPage('cards')">
          <div class="small-label">借记卡</div>
          <div class="num-font">
            {{ dashboardData.debitCardCount }} <small>张</small>
          </div>
        </van-col>
        <van-col span="6" class="detail-item" @click="goSubPage('credit')">
          <div class="small-label">信用卡</div>
          <div class="num-font">
            {{ dashboardData.creditCardCount }} <small>张</small>
          </div>
        </van-col>
        <van-col span="6" class="detail-item" @click="goSubPage('bill')">
          <div class="small-label">待还账单</div>
          <div class="num-font">
            {{ formatMoney(dashboardData.creditToPay) }}
          </div>
        </van-col>
        <van-col span="6" class="detail-item">
          <div class="small-label">本月结余</div>
          <div class="num-font">
            {{ formatMoney(dashboardData.monthlySurplus) }}
          </div>
        </van-col>
      </van-row>
    </div>

    <div class="app-card menu-grid-card">
      <van-grid :column-num="4" :border="false" clickable>
        <van-grid-item @click="goSubPage('flow')">
          <template #icon
            ><van-icon name="orders-o" class="grid-icon blue"
          /></template>
          <template #text><span class="grid-text">流水明细</span></template>
        </van-grid-item>
        <van-grid-item @click="goSubPage('credit-center')">
          <template #icon
            ><van-icon name="credit-pay" class="grid-icon orange"
          /></template>
          <template #text><span class="grid-text">信用卡</span></template>
        </van-grid-item>
        <van-grid-item @click="goSubPage('assets-list')">
          <template #icon
            ><van-icon name="gem" class="grid-icon purple"
          /></template>
          <template #text><span class="grid-text">资产结构</span></template>
        </van-grid-item>
        <van-grid-item @click="goSubPage('salary')">
          <template #icon
            ><van-icon name="points" class="grid-icon gold"
          /></template>
          <template #text><span class="grid-text">薪资计算</span></template>
        </van-grid-item>
      </van-grid>
    </div>

    <div class="app-card info-card">
      <div class="info-row" @click="goSubPage('salary')">
        <div class="info-left">
          <van-icon name="manager-o" class="info-icon" />
          <span class="info-label">今日预估薪酬</span>
        </div>
        <div class="info-right num-font text-income">
          +{{ formatMoney(dailySalary) }}
        </div>
      </div>

      <van-divider style="margin: 10px 0" />

      <div class="info-row" @click="goSubPage('todo')">
        <div class="info-left">
          <van-icon name="clock-o" class="info-icon" />
          <span class="info-label">待办提醒</span>
          <span v-if="topReminder" class="reminder-dot"></span>
          <span v-if="topReminder" class="reminder-text">{{
            topReminder.content
          }}</span>
        </div>
        <div class="info-right">
          <van-icon name="arrow" class="arrow-right" />
        </div>
      </div>
    </div>

    <van-cell-group inset class="recent-records">
      <template #title>
        <div class="item-title">
          <span> 近期消费 </span>
          <span class="view-detail" @click="goSubPage('flow')">查看明细></span>
        </div>
      </template>
      <van-cell
        v-for="item in recentItems"
        :key="item.id"
        :title="item.pay_type + (item.direction === 1 ? '收入' : '支出')"
        :label="item.trans_date"
        clickable
        @click="goToDetail(item)"
      >
        <template #value>
          <span class="num-font" :class="getAmountClass(item)">
            {{ getAmountPrefix(item) }}{{ formatMoney(item.amount) }}元
          </span>
        </template>
      </van-cell>
      <van-cell v-if="recentItems.length === 0" title="暂无大额流水" is-link />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { getAssetHome } from "@/utils/api/asset";
import { getReminders } from "@/utils/api/todo";
import { getSalaryDay } from "@/utils/api/work";

const router = useRouter();

const dashboardData = reactive({
  totalAsset: 0,
  totalBalance: 0,
  debitCardCount: 0,
  creditCardCount: 0,
  creditToPay: 0,
  monthlySurplus: 0,
  monthIncome: 0,
  monthExpense: 0,
  todayIncome: 0,
  todayExpense: 0,
});

const loading = ref(false);
const recentItems = ref([]);
const showAmount = ref(true);
const topReminder = ref(null); // 最重要的1条提醒
const todaySalaryData = ref(null); // 今日薪酬数据

// 日期
const now = new Date();
const currentMonth = ref(now.getMonth() + 1);
const currentDay = ref(now.getDate());

// 加载首页数据
const loadHomeData = async () => {
  loading.value = true;
  try {
    const res = await getAssetHome();
    const data = res.data || res;

    dashboardData.totalAsset = data.total_asset || 0;
    dashboardData.totalBalance = data.total_balance || 0;
    dashboardData.creditToPay = data.credit_debt || data.creditDebt || 0;

    dashboardData.debitCardCount = data.debitCardCount || 0;
    dashboardData.creditCardCount = data.creditCardCount || 0;
    dashboardData.monthlySurplus = data.monthBalance || 0;
    dashboardData.monthIncome = data.monthIncome || 0;
    dashboardData.monthExpense = data.monthExpense || 0;
    dashboardData.todayIncome = data.todayIncome || 0;
    dashboardData.todayExpense = data.todayExpense || 0;

    recentItems.value = data.largeTransactions || [];
  } catch (e) {
    console.error("加载首页数据失败", e);
  } finally {
    loading.value = false;
  }
};

// 加载待办提醒（使用默认参数，获取3-5天内的提醒）
const loadReminder = async () => {
  try {
    const res = await getReminders(); // 不传参数，获取默认提醒
    const list = res.data || [];
    if (list.length > 0) {
      // 排序：时间升序（越接近今天越前）> 优先等级（1最高）
      const sorted = [...list].sort((a, b) => {
        const dateA = new Date(a.happen_date);
        const dateB = new Date(b.happen_date);
        if (dateA.getTime() !== dateB.getTime()) {
          return dateA.getTime() - dateB.getTime(); // 时间升序
        }
        return (a.priority || 3) - (b.priority || 3); // 优先级高的在前
      });
      topReminder.value = sorted[0];
    } else {
      topReminder.value = null;
    }
  } catch (e) {
    console.error("加载提醒失败", e);
    topReminder.value = null;
  }
};

// 今日预估薪酬
const dailySalary = computed(() => {
  if (todaySalaryData.value) {
    // 从 API 获取今日薪酬
    let total = 0
    if (todaySalaryData.value.formal?.income) {
      total += parseFloat(todaySalaryData.value.formal.income) || 0
    }
    if (todaySalaryData.value.parttimes) {
      todaySalaryData.value.parttimes.forEach(p => {
        total += parseFloat(p.income) || 0
      })
    }
    return total
  }
  // 兜底：按月收入估算
  const today = new Date();
  const daysInMonth = new Date(
    today.getFullYear(),
    today.getMonth() + 1,
    0
  ).getDate();
  const day = today.getDate();
  return (dashboardData.monthIncome / daysInMonth) * day || 0;
});

// 加载今日薪酬
const loadTodaySalary = async () => {
  try {
    const today = new Date().toISOString().split('T')[0];
    const res = await getSalaryDay({ work_date: today });
    todaySalaryData.value = res.data || null;
  } catch (e) {
    console.error('加载今日薪酬失败', e);
    todaySalaryData.value = null;
  }
};

const toggleEye = () => {
  showAmount.value = !showAmount.value;
};

const goSubPage = (page) => {
  const routes = {
    cards: "/card/debit",
    credit: "/card/credit",
    bill: "/card/bill/list",
    flow: "/finance/flow",
    "credit-center": "/credit-center",
    "assets-list": "/finance/assets/list",
    salary: "/work/salary-calendar",
    todo: "/todo/calendar",
  };
  if (routes[page]) router.push(routes[page]);
};

const formatMoney = (val) =>
  Number(val || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

// 流水类型文字
const getTransText = (type) => {
  console.log(type);

  const map = {
    repay: "信用卡还款",
    account: "账户变动",
    income: "收入",
    expense: "支出",
  };
  return map[type] || "交易记录";
};

// 颜色 + 符号
const getAmountClass = (item) => {
  return item.direction === 1 ? "text-income" : "text-expense";
};
const getAmountPrefix = (item) => {
  return item.direction === 1 ? "+" : "-";
};
const goToDetail = (item) => {
  router.push(`/finance/flow/${item.id}`);
};

onMounted(() => {
  loadHomeData();
  loadReminder();
  loadTodaySalary();
});
</script>

<style scoped>
.page-home {
  padding: 0.4rem 0.8rem;
  background-color: #f7f8fa;
  min-height: 100vh;
}

/* 总资产卡片 */
.total-assets-card {
  background: linear-gradient(135deg, #4e7af5, #3a66e0);
  color: white;
  padding: 1.6rem 1.2rem;
  border-radius: 1.4rem;
  margin-bottom: 1rem;
  box-shadow: 0 0.6rem 1.4rem rgba(58, 102, 224, 0.25);
  position: relative;
  overflow: hidden;
}

/* 右上角日期牌 */
.date-badge {
  position: absolute;
  top: 1rem;
  right: 1rem;
  width: 2.4rem;
  height: 2.4rem;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 0.4rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
  overflow: hidden;
}
.date-month {
  width: 100%;
  height: 0.9rem;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.5rem;
  font-weight: bold;
}
.date-day {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  font-weight: bold;
}

.label-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  opacity: 0.9;
  font-size: 0.85rem;
}

.eye-icon {
  font-size: 1rem;
  cursor: pointer;
}

.total-amount {
  font-size: 2.2rem;
  margin-top: 0.5rem;
  font-weight: 700;
  letter-spacing: 0.05rem;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 0.5rem;
}

.income-expense {
  font-size: 0.7rem;
  text-align: right;
}

.income-item {
  margin-bottom: 0.2rem;
}

.ie-label {
  opacity: 0.75;
  font-size: 0.55rem;
}

.asset-details {
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  padding-top: 1rem;
  text-align: center;
}

.detail-item {
  cursor: pointer;
}

.small-label {
  font-size: 0.6rem;
  opacity: 0.8;
  margin-bottom: 0.3rem;
}

.detail-item .num-font {
  font-size: 0.95rem;
  font-weight: 600;
}

/* 菜单 */
.menu-grid-card {
  background: #fff;
  border-radius: 1.2rem;
  padding: 0.5rem 0;
  margin-bottom: 1rem;
}

.grid-icon {
  font-size: 26px;
  margin-bottom: 8px;
}
.grid-text {
  font-size: 13px;
  color: #646566;
}
.blue {
  color: #1989fa;
}
.green {
  color: #07c160;
}
.orange {
  color: #ff976a;
}
.red {
  color: #ee0a24;
}
.purple {
  color: #7232dd;
}
.cyan {
  color: #00bcd4;
}
.teal {
  color: #009688;
}
.pink {
  color: #e91e63;
}
.gold {
  color: #ffb300;
}

/* 信息卡片 */
.info-card {
  background: #fff;
  border-radius: 1.2rem;
  padding: 1rem;
  margin-bottom: 1rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.4rem 0;
  cursor: pointer;
}

.info-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.info-icon {
  font-size: 1.2rem;
  color: #4e7af5;
}

.info-label {
  font-size: 0.9rem;
  color: #333;
}

.arrow-right {
  color: #999;
  font-size: 0.8rem;
}

.reminder-dot {
  width: 6px;
  height: 6px;
  background: #ee0a24;
  border-radius: 50%;
  margin-left: 4px;
}

.reminder-text {
  font-size: 0.8rem;
  color: #969799;
  margin-left: 6px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 颜色 */
.text-income {
  font-weight: bold;
  color: #ee0a24 !important;
}
.text-expense {
  color: #07c160 !important;
}

/* 流水 */
.item-title {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  flex-wrap: nowrap;
  justify-content: space-between;
  align-items: center;
}
.recent-records {
  background: #fff;
  border-radius: 1.2rem;
  overflow: hidden;
}
.view-detail {
  font-size: 0.8rem;
  color: #0a4ba8;
  margin-top: 0.5rem;
  text-align: center;
}
</style>
