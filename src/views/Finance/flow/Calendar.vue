<template>
  <div class="page-flow-calendar">
    <!-- 顶部月份选择 -->
    <div class="header">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="month-title" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth + 1 }}月
        <van-icon name="arrow-down" />
      </span>
      <van-icon name="arrow" @click="nextMonth" />
    </div>

    <!-- 顶部统计栏 -->
    <div class="stat-bar">
      <div class="stat-item main">
        <span class="stat-label">月结余</span>
        <span class="stat-value" :class="monthData.balance >= 0 ? 'income' : 'expense'">
          {{ monthData.balance >= 0 ? '+' : '-' }}{{ formatAmount(monthData.balance) }}
        </span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">收入</span>
        <span class="stat-value income">+{{ formatAmount(monthData.income) }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">支出</span>
        <span class="stat-value expense">-{{ formatAmount(monthData.expense) }}</span>
      </div>
    </div>

    <!-- 星期标题 -->
    <div class="weekdays">
      <div v-for="day in weekDays" :key="day" class="weekday">{{ day }}</div>
    </div>

    <!-- 日历主体 -->
    <div class="calendar-grid" v-if="!loading">
      <div
        v-for="(day, index) in calendarDays"
        :key="index"
        class="day-cell"
        :class="getDayCellClass(day)"
        @click="selectDate(day)"
      >
        <span class="day-number">{{ day.day }}</span>
        <!-- 有收支记录 -->
        <div v-if="day.hasRecord" class="day-amounts">
          <span v-if="day.income > 0" class="amount income">+{{ formatAmount(day.income) }}</span>
          <span v-if="day.expense > 0" class="amount expense">-{{ formatAmount(day.expense) }}</span>
        </div>
      </div>
    </div>

    <div class="calendar-loading" v-else>
      <van-loading size="32px">加载中...</van-loading>
    </div>

    <!-- 选中日期的流水列表 -->
    <div class="day-detail" v-if="selectedDate">
      <div class="detail-header">
        <span class="detail-date">{{ formatDetailDate(selectedDate) }}</span>
        <span class="detail-balance" :class="dayDetail.balance >= 0 ? 'income' : 'expense'">
          {{ dayDetail.balance >= 0 ? '+' : '-' }}{{ formatAmount(dayDetail.balance) }}
        </span>
      </div>

      <div class="detail-content">
        <div v-if="dayDetail.income > 0" class="detail-section income-section">
          <div class="section-title">
            <van-icon name="arrow-up" class="income-icon" />
            <span>收入</span>
          </div>
          <div
            v-for="item in dayDetail.incomeList"
            :key="item.id"
            class="detail-item"
            @click="goDetail(item)"
          >
            <div class="item-left">
              <div class="category-icon">
                <van-icon :name="getCategoryIcon(item.category_name)" />
              </div>
              <div class="item-info">
                <div class="item-title">{{ getCategoryName(item) }}</div>
                <div class="item-desc">{{ formatTime(item.create_time) }} · {{ item.pay_method || '-' }}</div>
              </div>
            </div>
            <div class="item-right">
              <span class="amount income">+{{ formatAmount(item.amount) }}</span>
              <van-icon name="arrow" class="arrow-icon" />
            </div>
          </div>
        </div>

        <div v-if="dayDetail.expense > 0" class="detail-section expense-section">
          <div class="section-title">
            <van-icon name="arrow-down" class="expense-icon" />
            <span>支出</span>
          </div>
          <div
            v-for="item in dayDetail.expenseList"
            :key="item.id"
            class="detail-item"
            @click="goDetail(item)"
          >
            <div class="item-left">
              <div class="category-icon">
                <van-icon :name="getCategoryIcon(item.category_name)" />
              </div>
              <div class="item-info">
                <div class="item-title">{{ getCategoryName(item) }}</div>
                <div class="item-desc">{{ formatTime(item.create_time) }} · {{ item.pay_method || '-' }}</div>
              </div>
            </div>
            <div class="item-right">
              <span class="amount expense">-{{ formatAmount(item.amount) }}</span>
              <van-icon name="arrow" class="arrow-icon" />
            </div>
          </div>
        </div>

        <van-empty v-if="dayDetail.income === 0 && dayDetail.expense === 0" description="当日无收支记录" />
        <div v-if="dayDetail.income === 0 && dayDetail.expense === 0" class="add-record-btn">
          <van-button type="primary" size="small" round @click="goAddRecord">
            <van-icon name="plus" /> 立即记账
          </van-button>
        </div>
      </div>
    </div>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        title="选择月份"
        v-model="pickerSelectedValues"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { getAccountList } from "@/utils/api/account";

const router = useRouter();
const weekDays = ["日", "一", "二", "三", "四", "五", "六"];

// 当前年月
const today = dayjs();
const currentYear = ref(today.year());
const currentMonth = ref(today.month());
const showMonthPicker = ref(false);
const pickerSelectedValues = ref([
  `${new Date().getFullYear()}年`,
  `${new Date().getMonth() + 1}月`,
]);
const loading = ref(false);

// 月度数据
const monthData = ref({
  income: 0,
  expense: 0,
  balance: 0,
  daily_list: [],
});

// 选中的日期
const selectedDate = ref(null);

// 月份选择器列
const pickerColumns = computed(() => {
  const years = [];
  const months = [];
  const now = new Date();
  const currentYearVal = now.getFullYear();

  for (let y = currentYearVal - 5; y <= currentYearVal + 5; y++) {
    years.push({ text: `${y}年`, value: `${y}年` });
  }
  for (let m = 1; m <= 12; m++) {
    months.push({ text: `${m}月`, value: `${m}月` });
  }

  return [years, months];
});

// 选择年月
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText) - 1;
  pickerSelectedValues.value = [yearText, monthText];
  showMonthPicker.value = false;
  loadMonthData();
};

// 上月
const prevMonth = () => {
  if (currentMonth.value === 0) {
    currentMonth.value = 11;
    currentYear.value--;
  } else {
    currentMonth.value--;
  }
  loadMonthData();
};

// 下月
const nextMonth = () => {
  if (currentMonth.value === 11) {
    currentMonth.value = 0;
    currentYear.value++;
  } else {
    currentMonth.value++;
  }
  loadMonthData();
};

// 格式化金额
const formatAmount = (amount) => {
  if (!amount && amount !== 0) return "0.00";
  const num = parseFloat(amount) || 0;
  return Math.abs(num).toFixed(2);
};

// 格式化时间
const formatTime = (ts) => (ts ? dayjs(Number(ts)).format("HH:mm") : "");

// 格式化选中日期显示
const formatDetailDate = (date) => {
  const weekDayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  const day = dayjs(date);
  return `${day.month() + 1}月${day.date()}日 ${weekDayNames[day.day()]}`;
};

// 获取分类图标
const getCategoryIcon = (name) => {
  const map = {
    餐饮: "orders-o",
    购物: "shopping-cart-o",
    交通: "logistics",
    娱乐: "gem-o",
    工资: "paid",
  };
  return map[name] || "balance-o";
};

// 获取分类名称
const getCategoryName = (item) => {
  if (item.category_id === "CATEGORY_REPAY") {
    return "信用卡还款";
  }
  return item.category_name || "未知分类";
};

// 日历天列表
const calendarDays = computed(() => {
  const days = [];
  const firstDay = dayjs()
    .year(currentYear.value)
    .month(currentMonth.value)
    .date(1);
  const daysInMonth = firstDay.daysInMonth();
  const startWeekday = firstDay.day();
  const todayStr = dayjs().format("YYYY-MM-DD");

  // 空白填充
  for (let i = 0; i < startWeekday; i++) {
    days.push({ day: null, empty: true });
  }

  // 日期
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = firstDay.date(d).format("YYYY-MM-DD");
    const dayRecord = monthData.value.daily_list.find((item) => item.date === dateStr);
    const isToday = dateStr === todayStr;
    const isSelected = dateStr === selectedDate.value;

    // 当日收支
    const income = dayRecord ? parseFloat(dayRecord.income) || 0 : 0;
    const expense = dayRecord ? parseFloat(dayRecord.expense) || 0 : 0;

    days.push({
      day: d,
      date: dateStr,
      isToday,
      isSelected,
      income,
      expense,
      hasRecord: income > 0 || expense > 0,
    });
  }
  return days;
});

// 获取日期格子样式
const getDayCellClass = (day) => {
  if (!day.day) return "empty";
  const classes = [];

  if (day.isToday) classes.push("today");
  if (day.isSelected) classes.push("selected");
  if (day.hasRecord) {
    if (day.income > 0 && day.expense > 0) {
      classes.push("has-both");
    } else if (day.income > 0) {
      classes.push("has-income");
    } else if (day.expense > 0) {
      classes.push("has-expense");
    }
  }

  return classes.join(" ");
};

// 选择日期
const selectDate = (day) => {
  if (!day?.date || day.empty) return;
  selectedDate.value = day.date;
};

// 选中日期的流水详情
const dayDetail = computed(() => {
  if (!selectedDate.value) {
    return { income: 0, expense: 0, balance: 0, incomeList: [], expenseList: [] };
  }

  const dayRecord = monthData.value.daily_list.find(
    (item) => item.date === selectedDate.value
  );

  if (!dayRecord) {
    return { income: 0, expense: 0, balance: 0, incomeList: [], expenseList: [] };
  }

  const incomeList = dayRecord.items.filter((item) => item.direction === 1);
  const expenseList = dayRecord.items.filter((item) => item.direction === 0);
  const income = incomeList.reduce((sum, item) => sum + parseFloat(item.amount) || 0, 0);
  const expense = expenseList.reduce((sum, item) => sum + parseFloat(item.amount) || 0, 0);

  return {
    income,
    expense,
    balance: income - expense,
    incomeList,
    expenseList,
  };
});

// 跳转到流水详情
const goDetail = (item) => {
  router.push(`/finance/flow/${item.id}`);
};

// 跳转到记账页面
const goAddRecord = () => {
  router.push('/finance/add');
};

// 加载月度数据
const loadMonthData = async () => {
  loading.value = true;
  try {
    const startDate = dayjs()
      .year(currentYear.value)
      .month(currentMonth.value)
      .date(1)
      .format("YYYY-MM-DD");
    const endDate = dayjs()
      .year(currentYear.value)
      .month(currentMonth.value)
      .date(1)
      .endOf("month")
      .format("YYYY-MM-DD");

    const res = await getAccountList({
      startDate,
      endDate,
      limit: 1000, // 获取当月所有数据
    });

    const list = res.data?.list || res.data || [];

    // 按日期分组
    const dailyMap = {};
    list.forEach((item) => {
      const date = item.trans_date;
      if (!dailyMap[date]) {
        dailyMap[date] = {
          date,
          income: 0,
          expense: 0,
          items: [],
        };
      }
      if (item.direction === 1) {
        dailyMap[date].income += parseFloat(item.amount) || 0;
      } else {
        dailyMap[date].expense += parseFloat(item.amount) || 0;
      }
      dailyMap[date].items.push(item);
    });

    const daily_list = Object.values(dailyMap);
    const totalIncome = daily_list.reduce((sum, d) => sum + d.income, 0);
    const totalExpense = daily_list.reduce((sum, d) => sum + d.expense, 0);

    monthData.value = {
      income: totalIncome,
      expense: totalExpense,
      balance: totalIncome - totalExpense,
      daily_list,
    };
  } catch (e) {
    console.error("加载月度数据失败", e);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadMonthData();
  // 默认选中今天
  selectedDate.value = dayjs().format("YYYY-MM-DD");
});
</script>

<style scoped>
.page-flow-calendar {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #fff;
}

.header .van-icon {
  font-size: 18px;
  padding: 6px;
  color: #1989fa;
}

.month-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 统计栏 */
.stat-bar {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 16px;
  margin-bottom: 8px;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-item.main {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: #969799;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}

.stat-value.income {
  color: #ee0a24;
}

.stat-value.expense {
  color: #07c160;
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: #ebedf0;
}

/* 星期 */
.weekdays {
  display: flex;
  background: #fff;
  padding: 12px 0;
  border-bottom: 1px solid #f2f3f5;
}

.weekday {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: #969799;
}

/* 日历 */
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: #fff;
}

.day-cell {
  width: 100%;
  height: calc(100vw / 7);
  max-height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  gap: 2px;
}

.calendar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: #fff;
}

.day-cell.empty {
  pointer-events: none;
}

.day-number {
  font-size: 15px;
  color: #323233;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-cell.today .day-number {
  background: #1989fa;
  color: #fff;
  border-radius: 50%;
}

.day-cell.selected .day-number {
  background: rgba(25, 137, 250, 0.2);
  color: #1989fa;
  border-radius: 4px;
}

.day-amounts {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.amount {
  font-size: 10px;
  font-weight: 500;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amount.income {
  color: #ee0a24;
}

.amount.expense {
  color: #07c160;
}

/* 颜色区分 */
.day-cell.has-income .day-number {
  color: #ee0a24;
}

.day-cell.has-expense .day-number {
  color: #07c160;
}

/* 选中日期详情 */
.day-detail {
  margin: 12px 16px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f2f3f5;
}

.detail-date {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}

.detail-balance {
  font-size: 14px;
  font-weight: 600;
}

.detail-balance.income {
  color: #ee0a24;
}

.detail-balance.expense {
  color: #07c160;
}

.detail-content {
  max-height: 400px;
  overflow-y: auto;
}

.detail-section {
  padding: 12px 16px;
}

.detail-section.income-section {
  border-bottom: 1px solid #f2f3f5;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #969799;
  margin-bottom: 8px;
}

.income-icon {
  color: #ee0a24;
}

.expense-icon {
  color: #07c160;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  cursor: pointer;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.category-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #1989fa;
}

.item-info .item-title {
  font-size: 14px;
  color: #323233;
}

.item-info .item-desc {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-right .amount {
  font-size: 15px;
  font-weight: 600;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.item-right .arrow-icon {
  color: #969799;
  font-size: 14px;
}

/* 立即记账按钮 */
.add-record-btn {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.add-record-btn .van-button {
  background: #1989fa;
  border: none;
}

.add-record-btn .van-icon {
  margin-right: 4px;
}
</style>
