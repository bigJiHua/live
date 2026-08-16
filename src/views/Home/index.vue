<template>
  <div class="page-home">
    <div class="app-card total-assets-card">
      <div class="header-main">
        <!-- 第一行：预估总资产 + 日期牌 -->
        <div class="header-row-top">
          <div class="label-group">
            <span class="label">预估总资产 (元)</span>
            <van-icon
              :name="showAmount ? 'eye-o' : 'closed-eye'"
              @click="toggleEye"
              class="eye-icon"
            />
          </div>
          <div class="date-badge" @click="goSubPage('todo')">
            <div class="date-month">{{ currentMonth }}月</div>
            <div class="date-day">{{ currentDay }}</div>
          </div>
        </div>
        <!-- 第二行：金额 + 今日收支 -->
        <div class="amount-row">
          <div class="total-amount num-font">
            {{ showAmount ? formatMoney(dashboardData.totalBalance) : "****" }}
          </div>
          <div class="income-expense">
            <div class="income-item">
              <span class="ie-label">今日收入：</span>
              <span class="num-font income-val in"
                >{{ showAmount ? "+" : ""
                }}{{
                  showAmount ? formatMoney(dashboardData.todayIncome) : "****"
                }}</span
              >
            </div>
            <div class="income-item">
              <span class="ie-label">今日支出：</span>
              <span class="num-font income-val out"
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

    <!-- TODO 注释 -->
    <div
      v-if="showDemoInfo"
      style="
        padding: 12px 16px;
        font-size: 13px;
        color: var(--van-orange);
        background: var(--van-orange-bg);
      "
    >
      ！当前项目部署在英国伦敦服务器上，所以数据加载可能会有延迟
    </div>

    <div class="app-card menu-grid-card">
      <app-grid :column-num="4" :border="false" clickable>
        <app-grid-item @click="goSubPage('flow')">
          <template #icon
            ><van-icon name="orders-o" class="grid-icon blue"
          /></template>
          <template #text><span class="grid-text">流水明细</span></template>
        </app-grid-item>
        <app-grid-item @click="goSubPage('credit-center')">
          <template #icon
            ><van-icon name="credit-pay" class="grid-icon orange"
          /></template>
          <template #text><span class="grid-text">信用卡</span></template>
        </app-grid-item>
        <app-grid-item @click="goSubPage('assets-list')">
          <template #icon
            ><van-icon name="gem" class="grid-icon purple"
          /></template>
          <template #text><span class="grid-text">资产结构</span></template>
        </app-grid-item>
        <app-grid-item @click="goSubPage('salary')">
          <template #icon
            ><van-icon name="points" class="grid-icon gold"
          /></template>
          <template #text><span class="grid-text">薪资计算</span></template>
        </app-grid-item>
      </app-grid>
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
          <span v-if="reminderList.length" class="reminder-dot"></span>
          <!-- 直接垂直轮播，去掉 van-notice-bar 外壳（其 content 默认高度/flex 会压塌 swipe 内容导致空白） -->
          <van-swipe
            v-if="reminderList.length"
            vertical
            class="home-reminder-swipe"
            :autoplay="3500"
            :touchable="false"
            :show-indicators="false"
            @click.stop="goSubPage('todo')"
          >
            <van-swipe-item v-for="r in reminderList" :key="r.date + r.content">
              <span class="home-reminder-chip" :class="'lv-' + r.level">{{ r.content }}</span>
            </van-swipe-item>
          </van-swipe>
        </div>
        <div class="info-right">
          <van-icon name="arrow" class="arrow-right" />
        </div>
      </div>
    </div>

    <div class="quick-add-bar">
      <app-button
        type="primary"
        plain
        size="small"
        round
        icon="plus"
        @click="goToAddFlow"
        >快速登记流水</app-button
      >
    </div>

    <van-cell-group inset class="recent-records">
      <template #title>
        <div class="item-title">
          <span> 近期消费 </span>
          <span class="view-detail" @click="goSubPage('flow')">查看明细></span>
        </div>
      </template>
      <app-cell
        v-for="item in recentItems"
        :key="item.id"
        :title="getTitle(item)"
        :label="item.trans_date"
        clickable
        @click="goToDetail(item)"
      >
        <template #value>
          <span class="num-font" :class="getAmountClass(item)">
            {{ getAmountPrefix(item) }}{{ formatMoney(item.amount) }}元
          </span>
        </template>
      </app-cell>
      <app-cell v-if="recentItems.length === 0" title="暂无大额流水" is-link />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from "vue";
defineOptions({ name: "Home" });
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
const reminderList = ref([]); // 当月所有未完成待办（与日历横幅同数据源）
const todaySalaryData = ref(null); // 今日薪酬数据
const showDemoInfo = import.meta.env.VITE_APP_DEMO === "true";

// 日期
const now = new Date();
const currentYear = ref(now.getFullYear());
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

// 首页待办提醒：今日±3天窗口（与日历横幅同 API，仅参数不同）
const getReminderLevel = (item) => {
  if (!item.happen_date) return "red";
  const d = new Date(item.happen_date);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((d - today) / 86400000);
  if (diff <= 0) return "red";        // 今天及以前：未完成
  if (diff >= 10) return "green";
  if (diff >= 5) return "yellow";
  return "red";
};
const loadReminder = async () => {
  try {
    // 首页待办提醒 = 今日前后三天窗口（days），并显式传北京时间年月给后端，避免 UTC 默认月错算
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth() + 1;
    const res = await getReminders({ days: 3, year: y, month: m });
    const list = (res.data || []).filter(r => r.content && r.content !== "1");
    reminderList.value = list.map(r => ({
      content: r.content,
      date: r.happen_date,
      level: getReminderLevel(r),
    }));
  } catch (e) {
    console.error("加载提醒失败", e);
    reminderList.value = [];
  }
};

// 今日预估薪酬
const dailySalary = computed(() => {
  if (todaySalaryData.value) {
    // 从 API 获取今日薪酬
    let total = 0;
    if (todaySalaryData.value.formal?.income) {
      total += parseFloat(todaySalaryData.value.formal.income) || 0;
    }
    if (todaySalaryData.value.parttimes) {
      todaySalaryData.value.parttimes.forEach((p) => {
        total += parseFloat(p.income) || 0;
      });
    }
    return total;
  }
  // 兜底：按月收入估算
  const today = new Date();
  const daysInMonth = new Date(
    today.getFullYear(),
    today.getMonth() + 1,
    0,
  ).getDate();
  const day = today.getDate();
  return (dashboardData.monthIncome / daysInMonth) * day || 0;
});

// 加载今日薪酬
const loadTodaySalary = async () => {
  try {
    const today = new Date().toISOString().split("T")[0];
    const res = await getSalaryDay({ work_date: today });
    todaySalaryData.value = res.data || null;
  } catch (e) {
    console.error("加载今日薪酬失败", e);
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

const goToAddFlow = () => {
  router.push("/finance/quick-add");
};

const formatMoney = (val) => {
  const v = Number(val || 0);
  const isNegative = v < 0;
  const abs = Math.abs(v);
  let result;
  if (abs >= 100000000) {
    const yi = abs / 100000000;
    const intPart = Math.floor(yi);
    let decPart = Math.round((yi - intPart) * 1000);
    if (decPart >= 1000) decPart = 999;
    result = intPart + "." + String(decPart).padStart(3, "0") + "亿";
  } else if (abs >= 10000) {
    const wan = abs / 10000;
    const intPart = Math.floor(wan);
    let decPart = Math.round((wan - intPart) * 1000);
    if (decPart >= 1000) decPart = 999;
    result = intPart + "." + String(decPart).padStart(3, "0") + "万";
  } else {
    const intPart = Math.floor(abs);
    let decPart = Math.round((abs - intPart) * 100);
    if (decPart >= 100) decPart = 99;
    result = intPart + "." + String(decPart).padStart(2, "0");
  }
  return (isNegative ? "-" : "") + result;
};

// 近期消费标题
const getTitle = (item) => {
  return (item.pay_type || "") + (item.direction === 1 ? "收入" : "支出");
};

// 金额颜色：收入绿、支出红
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
  background-color: var(--theme-bg-primary);
  min-height: 100vh;
}

/* 总资产卡片 */
.total-assets-card {
  background: linear-gradient(
    135deg,
    var(--theme-primary),
    var(--theme-primary-grad)
  );
  color: white;
  padding: 1.6rem 1.2rem;
  border-radius: 1.4rem;
  margin-bottom: 1rem;
  box-shadow: 0 0.6rem 1.4rem rgba(var(--theme-primary-rgb), 0.25);
  position: relative;
  overflow: hidden;
}

/* 右上角日期牌 — 正常流，由 header-main flex 推到右侧 */
.date-badge {
  position: relative;
  width: 2.4rem;
  height: 2.4rem;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 0.4rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

/* 扩大点击热区（视觉不变，可点范围四周外扩） */
.date-badge::before {
  content: "";
  position: absolute;
  top: -0.6rem;
  right: -0.6rem;
  bottom: -0.6rem;
  left: -0.6rem;
}

.date-month {
  width: 100%;
  height: 0.9rem;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 0.4rem 0.4rem 0 0;
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

.header-main {
  /* 两行垂直堆叠 */
}

/* 第一行：预估总资产标签 + 日期牌 */
.header-row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  font-weight: 700;
  letter-spacing: 0.05rem;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.income-expense {
  font-size: 0.82rem;
}

/* label 固定左侧，金额靠右，上下两行对齐 */
.income-item {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 0.25rem;
}

.ie-label {
  opacity: 0.75;
  font-size: 0.6rem;
  flex-shrink: 0;
}

/* 今日收支金额：固定红绿，不跟随主题 */
.income-val {
  font-weight: 600;
  margin-left: auto;
  white-space: nowrap;
}

.income-val.in {
  color: var(--money-income);
}

.income-val.out {
  color: var(--money-expense);
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
  background: var(--theme-bg-secondary);
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
  color: var(--theme-text-secondary);
}

.blue {
  color: var(--theme-primary);
}

.green {
  color: var(--van-green, #07c160);
}

.orange {
  color: var(--van-orange, #ff976a);
}

.red {
  color: var(--van-danger-color, #ee0a24);
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
  background: var(--theme-bg-secondary);
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
  color: var(--theme-primary);
}

.info-label {
  font-size: 0.9rem;
  color: var(--theme-text-primary);
}

.arrow-right {
  color: var(--theme-text-tertiary);
  font-size: 0.8rem;
}

.reminder-dot {
  width: 6px;
  height: 6px;
  background: var(--van-danger-color, #ee0a24);
  border-radius: 50%;
  margin-left: 4px;
}

.home-reminder-swipe {
  flex: 1;
  min-width: 0;
  margin-left: 6px;
  height: 22px;
  line-height: 22px;
  overflow: hidden;
}
.home-reminder-chip { font-size: 0.8rem; color: var(--theme-text-tertiary); }
.home-reminder-chip.lv-red { color: var(--van-danger-color, #ee0a24); }
.home-reminder-chip.lv-yellow { color: #ff976a; }
.home-reminder-chip.lv-green { color: var(--van-success-color, #07c160); }
.home-reminder-chip b { font-weight: 600; margin-left: 4px; }

/* 颜色 */
.text-income {
  font-weight: bold;
  color: var(--van-danger-color, #ee0a24) !important;
}

.text-expense {
  color: var(--van-green, #07c160) !important;
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

.quick-add-bar {
  margin: 12px 16px;
  display: flex;
}

.quick-add-bar .app-btn {
  flex: 1;
  width: 100%;
}

.recent-records {
  background: var(--theme-bg-secondary);
  border-radius: 1.2rem;
  overflow: hidden;
}

.view-detail {
  font-size: 0.8rem;
  color: var(--theme-primary);
  margin-top: 0.5rem;
  text-align: center;
}
</style>
