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
        <div v-if="dayDetail.displayList.length > 0" class="flow-list">
          <template
            v-for="node in dayDetail.displayList"
            :key="node.type === 'flow' ? node.data.id : 'tf-' + node.expense.id"
          >
            <div
              v-if="node.type === 'flow'"
              class="flow-item"
              @click="goDetail(node.data)"
            >
              <div class="item-left">
                <div class="item-cat">{{ getCategoryName(node.data) }}</div>
                <div class="item-desc">{{ getFlowItemDesc(node.data) }}</div>
              </div>
              <div class="item-right">
                <div
                  class="item-amount"
                  :class="isIncome(node.data) ? 'income' : 'expense'"
                >
                  {{ isIncome(node.data) ? '+' : '-' }}{{ formatAmount(node.data.amount) }}
                </div>
                <div class="item-date">{{ formatTime(node.data.create_time) }}</div>
              </div>
            </div>

            <div v-else class="transfer-row">
              <div class="transfer-header">{{ node.isExplicit ? '转账' : '疑似转账' }}</div>
              <div class="transfer-body">
                <div class="transfer-side">
                  <div class="transfer-amount expense">-{{ formatAmount(node.expense.amount) }}</div>
                  <div class="transfer-bank">{{ getBankCardLabel(node.expense) || '余额' }}</div>
                  <div class="transfer-time">{{ formatTime(node.expense.create_time) }}</div>
                </div>
                <div class="transfer-arrow">→</div>
                <div class="transfer-side">
                  <div class="transfer-amount income">+{{ formatAmount(node.income.amount) }}</div>
                  <div class="transfer-bank">{{ getBankCardLabel(node.income) || '银行卡' }}</div>
                  <div class="transfer-time">{{ formatTime(node.income.create_time) }}</div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <van-empty v-else description="当日无收支记录" />
        <div v-if="dayDetail.displayList.length === 0" class="add-record-btn">
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
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";

const BASE_URL = ENV.FILE_BASE_URL;

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

// 卡片和银行列表
const cardList = ref([]);
const bankList = ref([]);

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
const formatAmount = (val) => {
  if (!val && val !== 0) return '0.00'
  const v = Number(val)
  const abs = Math.abs(v)
  let result
  if (abs >= 100000000) {
    const yi = abs / 100000000
    const intPart = Math.floor(yi)
    let decPart = Math.round((yi - intPart) * 1000)
    if (decPart >= 1000) decPart = 999
    result = intPart + '.' + String(decPart).padStart(3, '0') + '亿'
  } else if (abs >= 10000) {
    const wan = abs / 10000
    const intPart = Math.floor(wan)
    let decPart = Math.round((wan - intPart) * 1000)
    if (decPart >= 1000) decPart = 999
    result = intPart + '.' + String(decPart).padStart(3, '0') + '万'
  } else {
    const intPart = Math.floor(abs)
    let decPart = Math.round((abs - intPart) * 100)
    if (decPart >= 100) decPart = 99
    result = intPart + '.' + String(decPart).padStart(2, '0')
  }
  return result
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
  return item.category_name || item.categoryName || item.pay_type || item.payType || "未知分类";
};

const isIncome = (item) => item.direction === 1 || item.direction === "income";

const isExpense = (item) =>
  item.direction === 0 || item.direction === "expense" || item.direction === 2;

const getItemDate = (item) => (item.trans_date || item.transDate || "").slice(0, 10);

const processDailyDisplayList = (items) => {
  const pairs = [];
  const usedExpenseIds = new Set();
  const usedIncomeIds = new Set();

  const addPair = (expense, income, isExplicit) => {
    pairs.push({ expense, income, isExplicit });
    usedExpenseIds.add(expense.id);
    usedIncomeIds.add(income.id);
  };

  const transferGroups = items.reduce((map, item) => {
    if (item.category_id === "CATEGORY_REPAY" || !item.transfer_group_id) return map;
    if (!map[item.transfer_group_id]) map[item.transfer_group_id] = [];
    map[item.transfer_group_id].push(item);
    return map;
  }, {});

  Object.values(transferGroups).forEach((group) => {
    const expense = group.find((item) => isExpense(item));
    const income = group.find((item) => isIncome(item));
    if (expense && income) addPair(expense, income, true);
  });

  items.forEach((item) => {
    if (usedExpenseIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    const date = getItemDate(item);
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      return (
        getItemDate(inc) === date &&
        Math.abs(Number(item.amount || 0) - Number(inc.amount || 0)) < 0.01 &&
        (item.card_id || item.cardId || "none") !== (inc.card_id || inc.cardId || "none2")
      );
    });
    if (match) addPair(item, match, item.pay_type === "转账" && match.pay_type === "转账");
  });

  const expenseIds = new Set(pairs.map((pair) => pair.expense.id));
  const incomeIds = new Set(pairs.map((pair) => pair.income.id));
  const processed = [];

  items.forEach((item) => {
    if (expenseIds.has(item.id)) {
      const pair = pairs.find((p) => p.expense.id === item.id);
      if (pair) processed.push({ type: "transfer", ...pair });
    } else if (!incomeIds.has(item.id)) {
      processed.push({ type: "flow", data: item });
    }
  });

  return processed;
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
    return { income: 0, expense: 0, balance: 0, items: [], displayList: [] };
  }

  const dayRecord = monthData.value.daily_list.find(
    (item) => item.date === selectedDate.value
  );

  if (!dayRecord) {
    return { income: 0, expense: 0, balance: 0, items: [], displayList: [] };
  }

  const items = dayRecord.items || [];
  const income = items
    .filter((item) => isIncome(item))
    .reduce((sum, item) => sum + (parseFloat(item.amount) || 0), 0);
  const expense = items
    .filter((item) => isExpense(item))
    .reduce((sum, item) => sum + (parseFloat(item.amount) || 0), 0);

  return {
    income,
    expense,
    balance: income - expense,
    items,
    displayList: processDailyDisplayList(items),
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
      limit: 10000, // 获取当月所有数据
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
  loadCardAndBankData();
  loadMonthData();
  // 默认选中今天
  selectedDate.value = dayjs().format("YYYY-MM-DD");
});

// 加载卡片和银行数据
const loadCardAndBankData = async () => {
  try {
    const [cardRes, bankRes] = await Promise.all([
      getCardList(),
      categoryApi.list("bank")
    ]);
    cardList.value = cardRes.data || cardRes || [];
    bankList.value = bankRes.data || bankRes || [];
  } catch (e) {
    console.error("加载卡片银行数据失败:", e);
  }
};

// 获取完整 URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 根据 bank_id 获取银行信息
const getBankInfo = (bankId) => {
  return bankList.value.find((b) => b.id === bankId) || null;
};

// 获取卡片关联的银行图标
const getCardBankIcon = (cardId) => {
  if (!cardId || cardId === "xxxx" || cardId === "yyyy") return "";
  const card = cardList.value.find((c) => c.id === cardId);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  if (!bankId) return "";
  const bank = getBankInfo(bankId);
  return bank?.icon_url || bank?.iconUrl || bank?.icon || bank?.image || "";
};

// 获取卡片显示信息（银行名称 + 卡尾号）
const getCardText = (cardId) => {
  if (!cardId) return "-";
  if (cardId === "xxxx") return "现金";
  if (cardId === "yyyy") return "余额";
  const card = cardList.value.find((c) => c.id === cardId);
  if (!card) return "-";
  const bankId = card.bank_id || card.bankId;
  const bank = getBankInfo(bankId);
  const bankName = bank?.name || card.alias || card.bank_name || "";
  const last4 = card.last4_no || card.last4No || card.card_last4 || "";
  if (bankName && last4) {
    return `${bankName} · ${last4}`;
  } else if (bankName) {
    return bankName;
  } else if (last4) {
    return last4;
  }
  return "-";
};

const getBankCardLabel = (item) => {
  const cardId = item.card_id || item.cardId;
  if (cardId === "yyyy") return "余额";
  if (cardId === "xxxx") return "现金";
  if (!cardId) return item.card_last4 || item.cardLast4 || "";
  const cardText = getCardText(cardId);
  return cardText === "-" ? item.card_last4 || item.cardLast4 || "" : cardText;
};

const getFlowItemDesc = (item) => {
  const cardLabel = getBankCardLabel(item);
  const method = item.pay_method || item.payMethod || "";
  if (cardLabel && method) return `${cardLabel} · ${method}`;
  return cardLabel || method || "-";
};
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
  background: #e8f4ff;
  color: #1989fa;
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
  padding: 10px 12px 14px;
}

.flow-list {
  padding-bottom: 4px;
}

.item-left {
  flex: 1;
  min-width: 0;
}

.flow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 10px 14px;
  margin-top: 6px;
  border-radius: 8px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.item-cat {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}

.item-desc {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-right {
  flex-shrink: 0;
  text-align: right;
  margin-left: 10px;
}

.item-amount {
  font-size: 15px;
  font-weight: 600;
  font-family: "DIN Alternate", -apple-system, sans-serif;
  white-space: nowrap;
}

.item-amount.income {
  color: #07c160;
}

.item-amount.expense {
  color: #ee0a24;
}

.item-date {
  font-size: 11px;
  color: #969799;
  margin-top: 4px;
}

.transfer-row {
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px dashed #1989fa;
  border-radius: 10px;
  background: #f0f7ff;
}

.transfer-header {
  font-size: 11px;
  color: #1989fa;
  font-weight: 600;
  text-align: center;
  margin-bottom: 8px;
}

.transfer-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.transfer-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.transfer-amount {
  font-size: 16px;
  font-weight: 700;
}

.transfer-amount.income {
  color: #07c160;
}

.transfer-amount.expense {
  color: #ee0a24;
}

.transfer-bank {
  max-width: 100%;
  font-size: 12px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transfer-time {
  font-size: 11px;
  color: #969799;
}

.transfer-arrow {
  font-size: 18px;
  color: #1989fa;
  padding: 0 10px;
  flex-shrink: 0;
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
