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
      <div v-for="(day, index) in calendarDays" :key="index" class="day-cell" :class="getDayCellClass(day)"
        @click="selectDate(day)">
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
        <!-- 支出 | 收入 双列 -->
        <div v-if="dayDetail.hasItems" class="flow-columns">
          <div class="flow-col flow-col-expense">
            <div class="col-header expense-header">支出</div>
            <div v-if="dayDetail.expenseItems.length > 0" class="col-items">
              <div v-for="node in dayDetail.expenseItems" :key="node.data.id" class="flow-item flow-item-col"
                @click="goDetail(node.data)">
                <div class="fi-line fi-line1">
                  <span>
                    {{ getCategoryName(node.data) }}
                    <span class="fi-card-type">{{ getCardTypeLabel(node.data) }}</span>
                  </span>
                  <span class="fi-time">{{ formatTime(node.data.create_time) }}</span>
                </div>
                <div class="fi-line fi-line2" :class="{ 'fi-line2-wrap': node.data.amount > 999.99 }">
                  <span class="item-amount expense">-{{ formatAmount(node.data.amount) }}</span>
                  <span class="fi-bank">{{ getCompactBankLabel(node.data) }}</span>
                </div>
              </div>
            </div>
            <div v-else class="col-empty">-</div>
          </div>
          <div class="col-divider"></div>
          <div class="flow-col flow-col-income">
            <div class="col-header income-header">收入</div>
            <div v-if="dayDetail.incomeItems.length > 0" class="col-items">
              <div v-for="node in dayDetail.incomeItems" :key="node.data.id" class="flow-item flow-item-col"
                @click="goDetail(node.data)">
                <div class="fi-line fi-line1">
                  <span>
                    {{ getCategoryName(node.data) }}
                    <span class="fi-card-type">{{ getCardTypeLabel(node.data) }}</span>
                  </span>
                  <span class="fi-time">{{ formatTime(node.data.create_time) }}</span>
                </div>
                <div class="fi-line fi-line2" :class="{ 'fi-line2-wrap': node.data.amount > 999.99 }">
                  <span class="item-amount income">+{{ formatAmount(node.data.amount) }}</span>
                  <span class="fi-bank">{{ getCompactBankLabel(node.data) }}</span>
                </div>
              </div>
            </div>
            <div v-else class="col-empty">-</div>
          </div>
        </div>

        <!-- 转账区域 -->
        <div v-if="dayDetail.transferItems.length > 0" class="transfer-section">
          <div class="transfer-divider">---- 转账 ----</div>
          <div v-for="node in dayDetail.transferItems" :key="'tf-' + node.expense.id" class="transfer-row">
            <div class="tf-line1">
              <span class="tf-time">{{ formatTime(node.expense.create_time) }}</span>
              <span class="tf-label">{{ node.isExplicit ? '转账' : '疑似转账' }}</span>
              <span class="tf-time">{{ formatTime(node.income.create_time) }}</span>
            </div>
            <div class="tf-line2">
              <span class="tf-amount expense" @click="goDetail(node.expense)">-{{ formatAmount(node.expense.amount)
                }}</span>
              <span class="tf-arrow">→</span>
              <span class="tf-amount income" @click="goDetail(node.income)">+{{ formatAmount(node.income.amount)
                }}</span>
            </div>
            <div class="tf-line3">
              <span class="tf-bank">{{ getCompactBankLabel(node.expense) }}</span>
              <span class="tf-bank">{{ getCompactBankLabel(node.income) }}</span>
            </div>
          </div>
        </div>

        <!-- 提现区域 -->
        <div v-if="dayDetail.withdrawalItems.length > 0" class="withdrawal-section">
          <div class="withdrawal-divider">---- 提现 ----</div>
          <div v-for="node in dayDetail.withdrawalItems" :key="'wd-' + node.expense.id" class="withdrawal-row">
            <div class="wd-line1">
              <span class="wd-time">{{ formatTime(node.expense.create_time) }}</span>
              <span class="wd-label">提现</span>
              <span class="wd-time">{{ formatTime(node.income.create_time) }}</span>
            </div>
            <div class="wd-line2">
              <span class="wd-amount expense" @click="goDetail(node.expense)">-{{ formatAmount(node.expense.amount) }}</span>
              <span class="wd-arrow">→</span>
              <span class="wd-amount income" @click="goDetail(node.income)">+{{ formatAmount(node.income.amount) }}</span>
            </div>
            <div class="wd-line3">
              <span class="wd-bank">{{ getCompactBankLabel(node.expense) }}</span>
              <span class="wd-bank">{{ getCompactBankLabel(node.income) }}</span>
            </div>
          </div>
        </div>

        <!-- 冲正区域 -->
        <div v-if="dayDetail.reversalItems.length > 0" class="reversal-section">
          <div class="reversal-divider">---- 冲正 ----</div>
          <div v-for="node in dayDetail.reversalItems" :key="'rv-' + node.expense.id" class="reversal-row">
            <div class="rv-line1">
              <span class="rv-time">{{ formatTime(node.expense.create_time) }}</span>
              <span class="rv-label">冲正</span>
              <span class="rv-time">{{ formatTime(node.income.create_time) }}</span>
            </div>
            <div class="rv-line2">
              <span class="rv-amount expense" @click="goDetail(node.expense)">-{{ formatAmount(node.expense.amount) }}</span>
              <span class="rv-arrow">→</span>
              <span class="rv-amount income" @click="goDetail(node.income)">+{{ formatAmount(node.income.amount) }}</span>
            </div>
            <div class="rv-line3">
              <span class="rv-bank">{{ getCompactBankLabel(node.expense) }}</span>
              <span class="rv-bank">{{ getCompactBankLabel(node.income) }}</span>
            </div>
          </div>
        </div>

        <van-empty v-if="dayDetail.displayList.length === 0" description="当日无收支记录" />
        <div v-if="dayDetail.displayList.length === 0" class="add-record-btn">
          <van-button type="primary" size="small" round @click="goAddRecord">
            <van-icon name="plus" /> 立即记账
          </van-button>
        </div>
      </div>
    </div>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker title="选择月份" v-model="pickerSelectedValues" :columns="pickerColumns" @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, nextTick } from "vue";
defineOptions({ name: 'FinanceFlowCalendar' })
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

  const addPair = (expense, income, isExplicit, isWithdrawal = false, isReversal = false) => {
    pairs.push({ expense, income, isExplicit, isWithdrawal, isReversal });
    usedExpenseIds.add(expense.id);
    usedIncomeIds.add(income.id);
  };

  // 辅助函数
  const getDate = (item) => (item.trans_date || item.transDate || "").slice(0, 10);
  const getCard = (item) => item.card_id || item.cardId || '';
  const isVirtual = (cardId) => cardId === 'yyyy' || cardId === 'xxxx';

  // 判断是否为信用卡
  const isCreditCard = (item) => {
    const acctType = item.account_type || item.accountType;
    if (acctType === 'credit') return true;
    const cardId = item.card_id || item.cardId;
    if (!cardId) return false;
    const card = cardList.value.find(c => c.id === cardId);
    return card?.card_type === 'credit' || card?.cardType === 'credit';
  };

  // 获取时间戳（用于时间接近判断）
  const getTimestamp = (item) => {
    if (item.create_time) return Number(item.create_time);
    if (item.createdAt) return Number(item.createdAt);
    const transTime = item.trans_date || item.transDate;
    if (transTime && transTime.includes(' ')) return dayjs(transTime).valueOf();
    return null;
  };

  // 基础匹配：日期相同、金额相等、币种一致、卡片不同
  const baseMatch = (exp, inc) =>
    getDate(exp) === getDate(inc) &&
    Number(exp.amount || 0) === Number(inc.amount || 0) &&
    (exp.currency || 'CNY') === (inc.currency || 'CNY') &&
    getCard(exp) !== getCard(inc);

  // 第1趟：transfer_group_id 匹配（后端明确分组）
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

  // 第1.5趟：信用卡支出 + 现金/余额收入 + 收入类别为"冲正" → 冲正
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    if (!isCreditCard(item)) return;  // 支出方必须是信用卡
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      if (!baseMatch(item, inc)) return false;
      // 收入方为现金或余额
      if (!isVirtual(getCard(inc))) return false;
      // 收入类别为冲正
      const incCat = inc.pay_type || inc.payType || inc.category_name || inc.categoryName || '';
      return incCat === '冲正';
    });
    if (match) addPair(item, match, true, false, true);
  });

  // 第2趟：双方分类均为"转账" → 确诊转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      if (!baseMatch(item, inc)) return false;
      const expCat = item.pay_type || item.payType || item.category_name || item.categoryName || '';
      const incCat = inc.pay_type || inc.payType || inc.category_name || inc.categoryName || '';
      return expCat === '转账' && incCat === '转账';
    });
    if (match) addPair(item, match, true);
  });

  // 第3趟：支出"其他支出" + 收入"其他收入" → 疑似转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      if (!baseMatch(item, inc)) return false;
      const expCat = item.pay_type || item.payType || item.category_name || item.categoryName || '';
      const incCat = inc.pay_type || inc.payType || inc.category_name || inc.categoryName || '';
      return expCat === '其他支出' && incCat === '其他收入';
    });
    if (match) addPair(item, match, false);
  });

  // 第4趟：其余满足基础条件的 → 疑似转账（排除余额→银行卡方向 和 信用卡支出方）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    const expenseCard = getCard(item);
    const isExpenseVirtual = isVirtual(expenseCard);
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      if (!baseMatch(item, inc)) return false;
      // 余额→银行卡方向排除（归入提现）
      if (isExpenseVirtual && !isVirtual(getCard(inc))) return false;
      return true;
    });
    if (match) {
      const bothTransfer = (item.pay_type === "转账" || item.payType === "转账") &&
                           (match.pay_type === "转账" || match.payType === "转账");
      addPair(item, match, bothTransfer);
    }
  });

  // 第5趟：余额→银行卡（同天同金额，时间接近）→ 提现
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === "CATEGORY_REPAY") return;
    if (!isExpense(item)) return;
    const expenseCard = getCard(item);
    if (expenseCard !== 'yyyy') return;  // 仅限余额卡片
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === "CATEGORY_REPAY") return false;
      if (!isIncome(inc)) return false;
      if (!baseMatch(item, inc)) return false;
      if (isVirtual(getCard(inc))) return false;  // 收入方不能是虚拟卡
      // 时间接近判断（5分钟内）
      const t1 = getTimestamp(item);
      const t2 = getTimestamp(inc);
      if (t1 && t2) return Math.abs(t1 - t2) <= 300000;
      // 没有时间信息时，默认认为是提现（降级处理）
      return true;
    });
    if (match) addPair(item, match, false, true);
  });

  const expenseIds = new Set(pairs.map((pair) => pair.expense.id));
  const incomeIds = new Set(pairs.map((pair) => pair.income.id));
  const processed = [];

  items.forEach((item) => {
    if (expenseIds.has(item.id)) {
      const pair = pairs.find((p) => p.expense.id === item.id);
      if (pair) {
        processed.push({
          type: pair.isReversal ? "reversal" : (pair.isWithdrawal ? "withdrawal" : "transfer"),
          ...pair
        });
      }
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
    return { income: 0, expense: 0, balance: 0, items: [], displayList: [], expenseItems: [], incomeItems: [], transferItems: [], withdrawalItems: [], reversalItems: [], hasItems: false };
  }

  const dayRecord = monthData.value.daily_list.find(
    (item) => item.date === selectedDate.value
  );

  if (!dayRecord) {
    return { income: 0, expense: 0, balance: 0, items: [], displayList: [], expenseItems: [], incomeItems: [], transferItems: [], withdrawalItems: [], reversalItems: [], hasItems: false };
  }

  const items = dayRecord.items || [];
  const income = items
    .filter((item) => isIncome(item))
    .reduce((sum, item) => sum + (parseFloat(item.amount) || 0), 0);
  const expense = items
    .filter((item) => isExpense(item))
    .reduce((sum, item) => sum + (parseFloat(item.amount) || 0), 0);

  const displayList = processDailyDisplayList(items)
  const expenseItems = displayList.filter(n => n.type === 'flow' && isExpense(n.data))
  const incomeItems = displayList.filter(n => n.type === 'flow' && isIncome(n.data))
  const transferItems = displayList.filter(n => n.type === 'transfer')
  const withdrawalItems = displayList.filter(n => n.type === 'withdrawal')
  const reversalItems = displayList.filter(n => n.type === 'reversal')

  return {
    income,
    expense,
    balance: income - expense,
    items,
    displayList,
    expenseItems,
    incomeItems,
    transferItems,
    withdrawalItems,
    reversalItems,
    hasItems: expenseItems.length > 0 || incomeItems.length > 0,
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

const savedScrollY = ref(0)

onMounted(() => {
  loadCardAndBankData();
  loadMonthData();
  // 默认选中今天
  selectedDate.value = dayjs().format("YYYY-MM-DD");
});

onDeactivated(() => {
  savedScrollY.value = window.scrollY || document.documentElement.scrollTop
})

onActivated(() => {
  nextTick(() => {
    if (savedScrollY.value > 0) window.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
  })
})

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

// 获取卡类型标签
const getCardTypeLabel = (item) => {
  const at = item.account_type || item.accountType || "";
  const map = { debit: "借记卡", credit: "信用卡", virtual_balance: "", virtual_cash: "现金" };
  return map[at] || "";
};

// 获取紧凑版银行标签（长银行名缩写，确保尾号可见）
const getCompactBankLabel = (item) => {
  const cardId = item.card_id || item.cardId;
  if (cardId === "yyyy") return "余额";
  if (cardId === "xxxx") return "现金";
  if (!cardId) return item.card_last4 || item.cardLast4 || "";
  const card = cardList.value.find((c) => c.id === cardId);
  if (!card) return item.card_last4 || item.cardLast4 || "";
  const bankId = card.bank_id || card.bankId;
  const bank = getBankInfo(bankId);
  const bankName = bank?.name || card.alias || card.bank_name || "";
  const last4 = card.last4_no || card.last4No || card.card_last4 || "";
  const shortName = bankName.length > 6 ? bankName.slice(0, 6) : bankName;
  if (shortName && last4) return `${shortName} ${last4}`;
  return shortName || last4 || "-";
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
  padding: 10px 12px 14px;
  overflow-y: auto;
}

/* ── 双列布局 ── */
.flow-columns {
  display: flex;
  gap: 0;
  align-items: flex-start;
  max-height: 50vh;
}

.flow-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 50vh;
}

.col-items {
  height: 100%;
  overflow-y: scroll;
}

.col-header {
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  padding: 6px 0;
  border-radius: 6px;
}

.expense-header {
  background: #fff2f0;
  color: #ee0a24;
}

.income-header {
  background: #f0fff5;
  color: #07c160;
}

.col-divider {
  width: 1px;
  align-self: stretch;
  background: #ebedf0;
  margin: 0 8px;
}

.col-empty {
  text-align: center;
  color: #c8c9cc;
  padding: 16px 0;
  font-size: 13px;
}

/* 列内流水项 */
.flow-item-col {
  background: #f7f8fa;
  padding: 6px 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 5px;
}

.fi-line {
  display: flex;
  align-items: center;
}

.fi-line1 {
  font-size: 12px;
  color: #323233;
  justify-content: space-between;
  margin-bottom: 2px;
}

.fi-time {
  color: #969799;
}

.fi-line2 {
  justify-content: space-between;
  align-items: baseline;
  overflow: hidden;
}

.fi-bank {
  font-size: 10px;
  color: #646566;
  flex-shrink: 0;
  text-align: right;
}

.fi-card-type {
  font-size: 9px;
  color: #969799;
  margin-left: 2px;
}

.flow-item-col .item-amount {
  font-size: 17px;
  font-weight: 600;
  font-family: "DIN Alternate", -apple-system, sans-serif;
  white-space: nowrap;
}

.fi-line2-wrap {
  flex-wrap: wrap;
}

.fi-line2-wrap .fi-bank {
  width: 100%;
  text-align: right;
}

.flow-item-col .item-amount.income {
  color: #07c160;
}

.flow-item-col .item-amount.expense {
  color: #ee0a24;
}

/* ── 转账区域 ── */
.transfer-section {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.transfer-divider {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #1989fa;
  font-weight: 500;
  margin-bottom: 6px;
  letter-spacing: 2px;
}

.transfer-row {
  width: calc(50% - 3px);
  margin-top: 0;
  padding: 6px 8px;
  border: 1px dashed #1989fa;
  border-radius: 10px;
  background: #f0f7ff;
  box-sizing: border-box;
}

.tf-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.tf-label {
  font-size: 11px;
  color: #1989fa;
  font-weight: 600;
}

.tf-time {
  font-size: 10px;
  color: #969799;
}

.tf-line2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.tf-amount {
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.tf-amount.expense {
  color: #ee0a24;
}

.tf-amount.income {
  color: #07c160;
}

.tf-arrow {
  font-size: 16px;
  color: #1989fa;
  flex-shrink: 0;
}

.tf-line3 {
  display: flex;
  justify-content: space-between;
}

.tf-bank {
  font-size: 10px;
  color: #646566;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 提现区域 ── */
.withdrawal-section {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.withdrawal-divider {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #2e7d32;
  font-weight: 500;
  margin-bottom: 6px;
  letter-spacing: 2px;
}

.withdrawal-row {
  width: calc(50% - 3px);
  margin-top: 0;
  padding: 6px 8px;
  border: 1px solid #c8e6c9;
  border-radius: 10px;
  background: #e8f5e9;
  box-sizing: border-box;
}

.wd-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.wd-label {
  font-size: 11px;
  color: #2e7d32;
  font-weight: 600;
}

.wd-time {
  font-size: 10px;
  color: #969799;
}

.wd-line2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.wd-amount {
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.wd-amount.expense {
  color: #2e7d32;
}

.wd-amount.income {
  color: #2e7d32;
}

.wd-arrow {
  font-size: 16px;
  color: #2e7d32;
  flex-shrink: 0;
}

.wd-line3 {
  display: flex;
  justify-content: space-between;
}

.wd-bank {
  font-size: 10px;
  color: #388e3c;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── 冲正区域（灰色，表示已撤销/无效）── */
.reversal-section {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.reversal-divider {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: #969799;
  font-weight: 500;
  margin-bottom: 6px;
  letter-spacing: 2px;
}

.reversal-row {
  width: calc(50% - 3px);
  margin-top: 0;
  padding: 6px 8px;
  border: 1px dashed #c8c9cc;
  border-radius: 10px;
  background: #f5f5f5;
  box-sizing: border-box;
  opacity: 0.72;
}

.rv-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.rv-label {
  font-size: 11px;
  color: #969799;
  font-weight: 600;
}

.rv-time {
  font-size: 10px;
  color: #c8c9cc;
}

.rv-line2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.rv-amount {
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.rv-amount.expense {
  color: #969799;
}

.rv-amount.income {
  color: #969799;
}

.rv-arrow {
  font-size: 16px;
  color: #c8c9cc;
  flex-shrink: 0;
}

.rv-line3 {
  display: flex;
  justify-content: space-between;
}

.rv-bank {
  font-size: 10px;
  color: #c8c9cc;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
