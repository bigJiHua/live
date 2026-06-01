<template>
  <div class="page-flow-v2">
    <!-- ====== 顶部月份卡片 ====== -->
    <div class="month-card">
      <div class="month-header">
        <div class="month-picker" @click="showDatePicker = true">
          <span class="month-year">{{ currentYear }}年{{ currentMonth }}月</span>
          <van-icon name="arrow-down" size="16" />
        </div>
        <van-icon name="calendar-o" size="22" color="#1989fa" @click="goCalendar" />
      </div>

      <!-- 统计数字 -->
      <div class="stats-row" v-if="summaryData">
        <div class="stat-kv">
          <span class="sk-label">收入</span>
          <span class="sk-val in">+{{ formatAmount(summaryData.income) }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-kv">
          <span class="sk-label">支出</span>
          <span class="sk-val out">-{{ formatAmount(summaryData.expense) }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-kv">
          <span class="sk-label">结余</span>
          <span class="sk-val" :class="(summaryData.income - summaryData.expense) >= 0 ? 'in' : 'out'">
            {{ (summaryData.income - summaryData.expense) >= 0 ? '+' : '-' }}{{ formatAmount(Math.abs(summaryData.income - summaryData.expense)) }}
          </span>
        </div>
      </div>

      <!-- 迷你进度条：收入 vs 支出 -->
      <div class="mini-bar" v-if="summaryData && (summaryData.income + summaryData.expense) > 0">
        <div class="mb-seg mb-in" :style="{ flex: summaryData.income }" />
        <div class="mb-seg mb-out" :style="{ flex: summaryData.expense }" />
      </div>
    </div>

    <!-- ====== 筛选 Tabs ====== -->
    <div class="tab-bar">
      <div class="tab-item" :class="{ active: filterType === 'all' }" @click="filterType = 'all'; onFilterChange()">全部</div>
      <div class="tab-item" :class="{ active: filterType === 'income' }" @click="filterType = 'income'; onFilterChange()">收入</div>
      <div class="tab-item" :class="{ active: filterType === 'expense' }" @click="filterType = 'expense'; onFilterChange()">支出</div>
    </div>

    <!-- ====== 时间线列表 ====== -->
    <div ref="listScrollRef" class="timeline-scroll">
      <!-- 滚动吸顶日期 -->
      <div v-if="stickyDate" class="sticky-date-bar">
        <span class="sticky-date-dot" :class="isToday(stickyDate) ? 'today' : ''"></span>
        <span class="sticky-date-text">{{ formatDateHeader(stickyDate) }}</span>
      </div>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list v-model:loading="loading" :finished="finished" finished-text="— 已经看到底了 —" @load="loadData">

          <div v-for="(group, date) in groupedList" :key="date" class="day-block">

            <!-- 日期头 -->
            <div class="day-head" :data-date="date">
              <div class="day-dot" :class="isToday(date) ? 'today' : ''" />
              <div class="day-text">
                <span class="day-date">{{ formatDateHeader(date) }}</span>
                <span class="day-weekday">{{ formatWeekday(date) }}</span>
              </div>
              <div class="day-sum">
                <span class="ds-in" v-if="dayIncome(group) > 0">+{{ formatAmount(dayIncome(group)) }}</span>
                <span class="ds-out" v-if="dayExpense(group) > 0">-{{ formatAmount(dayExpense(group)) }}</span>
              </div>
            </div>

            <!-- 条目 -->
            <div class="day-cards">
              <template v-for="item in group" :key="(item.type === 'transfer' || item.type === 'withdrawal' || item.type === 'reversal') ? item.expense.id : item.data.id">

                <!-- === 转账 - 三层布局 + 展开/折叠 === -->
                <div v-if="item.type === 'transfer'" class="transfer-block">
                  <div class="tf-main" @click="toggleTransferExpand(item.expense.id)">
                    <!-- 第一层：时间 · 标签 · 时间 -->
                    <div class="tf-row tf-time-row">
                      <span class="tf-t">{{ formatTime(item.expense.create_time) }}</span>
                      <span class="tf-label">{{ item.isExplicit ? '转账' : '疑似转账' }}</span>
                      <span class="tf-t">{{ formatTime(item.income.create_time) }}</span>
                    </div>
                    <!-- 第二层：金额 + icon + 金额 -->
                    <div class="tf-row tf-amount-row">
                      <span class="tf-amt out">-{{ formatAmount(item.expense.amount) }}</span>
                      <van-icon name="exchange" class="tf-exchange-icon" />
                      <span class="tf-amt in">+{{ formatAmount(item.income.amount) }}</span>
                    </div>
                    <!-- 第三层：银行信息 -->
                    <div class="tf-row tf-bank-row">
                      <span class="tf-bank-name">{{ getCardName(item.expense.card_id) || getCompactCardLabel(item.expense) }}</span>
                      <span class="tf-bank-name">{{ getCardName(item.income.card_id) || getCompactCardLabel(item.income) }}</span>
                    </div>
                  </div>
                  <!-- 展开明细 -->
                  <div v-if="expandedTransferIds.has(item.expense.id)" class="tf-detail">
                    <div class="tf-detail-item" @click.stop="goDetail(item.expense)">
                      <span class="tfd-dot out"></span>
                      <div class="tfd-body">
                        <div class="tfd-cat">{{ getCategoryName(item.expense) }}</div>
                        <div class="tfd-meta">{{ item.expense.pay_method || '-' }} · {{ formatTime(item.expense.create_time) }}</div>
                      </div>
                      <span class="tfd-amt out">-{{ formatAmount(item.expense.amount) }}</span>
                    </div>
                    <div class="tf-detail-item" @click.stop="goDetail(item.income)">
                      <span class="tfd-dot in"></span>
                      <div class="tfd-body">
                        <div class="tfd-cat">{{ getCategoryName(item.income) }}</div>
                        <div class="tfd-meta">{{ item.income.pay_method || '-' }} · {{ formatTime(item.income.create_time) }}</div>
                      </div>
                      <span class="tfd-amt in">+{{ formatAmount(item.income.amount) }}</span>
                    </div>
                  </div>
                </div>

                <!-- === 提现 - 三层布局（同转账）=== -->
                <div v-else-if="item.type === 'withdrawal'" class="paired-block">
                  <div class="pd-main pd-withdrawal" @click="goDetail(item.expense)">
                    <div class="tf-row tf-time-row">
                      <span class="tf-t">{{ formatTime(item.expense.create_time) }}</span>
                      <span class="pd-label">提现</span>
                      <span class="tf-t">{{ formatTime(item.income.create_time) }}</span>
                    </div>
                    <div class="tf-row tf-amount-row">
                      <span class="tf-amt out">-{{ formatAmount(item.expense.amount) }}</span>
                      <span class="tf-exchange-icon" style="font-weight:700">→</span>
                      <span class="tf-amt in">+{{ formatAmount(item.income.amount) }}</span>
                    </div>
                    <div class="tf-row tf-bank-row">
                      <span class="tf-bank-name">{{ getCardName(item.expense.card_id) || getCompactCardLabel(item.expense) }}</span>
                      <span class="tf-bank-name">{{ getCardName(item.income.card_id) || getCompactCardLabel(item.income) }}</span>
                    </div>
                  </div>
                </div>

                <!-- === 冲正 === -->
                <div v-else-if="item.type === 'reversal'" class="paired-block">
                  <div class="pd-main pd-reversal" @click="goDetail(item.expense)">
                    <div class="tf-row tf-time-row">
                      <span class="tf-t">{{ formatTime(item.expense.create_time) }}</span>
                      <span class="tf-label" style="text-decoration:line-through;color:#8e8e93">冲正</span>
                      <span class="tf-t">{{ formatTime(item.income.create_time) }}</span>
                    </div>
                    <div class="tf-row tf-amount-row">
                      <span class="tf-amt out" style="color:#8e8e93">-{{ formatAmount(item.expense.amount) }}</span>
                      <van-icon name="revoke" class="tf-exchange-icon" style="color:#8e8e93" />
                      <span class="tf-amt in" style="color:#8e8e93">+{{ formatAmount(item.income.amount) }}</span>
                    </div>
                    <div class="tf-row tf-bank-row">
                      <span class="tf-bank-name" style="color:#a0a0a4">{{ getCardName(item.expense.card_id) || getCompactCardLabel(item.expense) }}</span>
                      <span class="tf-bank-name" style="color:#a0a0a4">{{ getCardName(item.income.card_id) || getCompactCardLabel(item.income) }}</span>
                    </div>
                  </div>
                </div>

                <!-- === 对外转账 === -->
                <div v-else-if="item.type === 'external-transfer'" class="flow-card" @click="goDetail(item.data)">
                  <div class="fc-badge fc-badge-warn">对外转账</div>
                  <div class="fc-body">
                    <div class="fc-cat">{{ getCategoryName(item.data) }}</div>
                    <div class="fc-meta">
                      {{ item.data.pay_method || "-" }} · {{ formatTime(item.data.create_time) }}
                      <span v-if="item.data.card_id && !['xxxx','yyyy'].includes(item.data.card_id)" class="fc-bank-chip">
                        <van-image v-if="getCardBankIcon(item.data.card_id)" width="12" height="12" :src="getFullUrl(getCardBankIcon(item.data.card_id))" fit="contain" />
                        {{ getCardName(item.data.card_id) }}
                      </span>
                    </div>
                  </div>
                  <div class="fc-amount out">
                    <span v-if="isForeignCurrency(item.data)" class="fc-currency">{{ getCurrencySymbol(item.data.currency) }}</span>
                    {{ formatAmount(item.data.amount) }}
                  </div>
                </div>

                <!-- === 普通流水条目 === -->
                <div v-else class="flow-card" :class="item.data.direction === 1 ? 'fc-income' : 'fc-expense'" @click="goDetail(item.data)">
                  <span class="fc-arrow" :class="item.data.direction === 1 ? 'in' : 'out'">
                    {{ item.data.direction === 1 ? '↓' : '↑' }}
                  </span>
                  <div class="fc-body">
                    <div class="fc-cat">{{ getCategoryName(item.data) }}</div>
                    <div class="fc-meta">
                      {{ item.data.pay_method || "-" }}
                      <span v-if="item.data.card_id && !['xxxx','yyyy'].includes(item.data.card_id)" class="fc-bank-chip">
                        <van-image v-if="getCardBankIcon(item.data.card_id)" width="12" height="12" :src="getFullUrl(getCardBankIcon(item.data.card_id))" fit="contain" />
                        {{ getCardName(item.data.card_id) }}
                      </span>
                    </div>
                  </div>
                  <div class="fc-right">
                    <span class="fc-amount" :class="item.data.direction === 1 ? 'in' : 'out'">
                      <span v-if="isForeignCurrency(item.data)" class="fc-currency">{{ getCurrencySymbol(item.data.currency) }}</span>
                      {{ item.data.direction === 1 ? '+' : '-' }}{{ formatAmount(item.data.amount) }}
                    </span>
                    <span class="fc-time">{{ formatTime(item.data.create_time) }}</span>
                  </div>
                </div>

              </template>
            </div>
          </div>

          <van-empty v-if="!loading && list.length === 0" description="本月暂无流水" />
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- ====== Popups ====== -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-picker v-model="selectedValues" title="选择月份" :columns="pickerColumns" @confirm="onPickerConfirm" @cancel="showDatePicker = false" />
    </van-popup>

    <van-icon v-show="showBackTop" name="back-top" class="back-top" @click="scrollToTop" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onDeactivated, watch, nextTick } from "vue";
defineOptions({ name: 'FinanceFlowList' })
import { useRouter, useRoute } from "vue-router";
import { showToast } from "vant";
import dayjs from "dayjs";
import zhCn from "dayjs/locale/zh-cn";
import { getAccountList, getMonthStats } from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";
import { useFlowSyncStore } from "@/stores/flowSync";

dayjs.locale(zhCn);
const router = useRouter();
const route = useRoute();
const flowSync = useFlowSyncStore();

const BASE_URL = ENV.FILE_BASE_URL;

// 筛选状态
const filterType = ref("all");
const showDatePicker = ref(false);

// 当前年月（URL 参数记忆，无参数默认当月）
const now = dayjs();
const currentYear = ref(Number(route.query.year) || now.year());
const currentMonth = ref(Number(route.query.month) || now.month() + 1);
const selectedValues = ref([`${currentYear.value}年`, `${currentMonth.value}月`]);

// 列表状态
const list = ref([]);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const page = ref(1);
const limit = 20;
const summaryData = ref(null);
const cardList = ref([]);
const bankList = ref([]);

// 转账展开状态
const expandedTransferIds = ref(new Set());
const showBackTop = ref(false)
const savedScrollY = ref(0)
const listScrollRef = ref(null)
const stickyDate = ref(null)

const toggleTransferExpand = (expenseId) => {
  const s = new Set(expandedTransferIds.value);
  if (s.has(expenseId)) s.delete(expenseId);
  else s.add(expenseId);
  expandedTransferIds.value = s;
};

const scrollToTop = () => {
  listScrollRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

const onScroll = () => {
  showBackTop.value = (listScrollRef.value?.scrollTop || 0) > 400

  // 检测当前可见的日期头（用于吸顶）
  const container = listScrollRef.value
  if (!container) return
  const headers = container.querySelectorAll('.day-head')
  let current = null
  for (const h of headers) {
    const rect = h.getBoundingClientRect()
    const containerRect = container.getBoundingClientRect()
    if (rect.top <= containerRect.top + 10) {
      current = h.getAttribute('data-date')
    } else {
      break
    }
  }
  stickyDate.value = current
}

onMounted(() => {
  listScrollRef.value?.addEventListener('scroll', onScroll)
  categoryApi.list("bank").then((res) => (bankList.value = res.data || res || [])).catch(() => {});
  getCardList().then((res) => (cardList.value = res.data || []));
  loadSummary();
  loadData();
});

// 离开时记住滚动位置
onDeactivated(() => {
  savedScrollY.value = listScrollRef.value?.scrollTop || window.scrollY || 0
})

// keep-alive 激活时：URL 有参数就恢复，无参数就重置为当前月
onActivated(() => {
  if (route.query.year && route.query.month) {
    const y = Number(route.query.year)
    const m = Number(route.query.month)
    if (y !== currentYear.value || m !== currentMonth.value) {
      currentYear.value = y
      currentMonth.value = m
      selectedValues.value = [`${y}年`, `${m}月`]
      onRefresh()
    }
  } else {
    const now = dayjs()
    if (now.year() !== currentYear.value || now.month() + 1 !== currentMonth.value) {
      currentYear.value = now.year()
      currentMonth.value = now.month() + 1
      selectedValues.value = [`${now.year()}年`, `${now.month() + 1}月`]
      onRefresh()
    }
  }

  // 同步 Detail 变更（原地 patch，避免触发全量渲染丢滚动位置）
  const changes = flowSync.consumeChanges();
  const ids = Object.keys(changes);
  if (ids.length > 0) {
    list.value.forEach(item => {
      const patch = changes[item.id];
      if (patch) Object.assign(item, patch);
    });
  }

  // 恢复滚动位置
  nextTick(() => {
    if (savedScrollY.value > 0) {
      listScrollRef.value?.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
    }
  })
});

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

// --- 修正 Picker 数据格式 ---
const pickerColumns = computed(() => {
  const currentYearVal = dayjs().year();
  const years = [];
  for (let i = currentYearVal - 10; i <= currentYearVal + 2; i++) {
    years.push({ text: `${i}年`, value: `${i}年` });
  }
  const months = Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: `${i + 1}月`,
  }));
  return [years, months];
});

const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText);
  selectedValues.value = [yearText, monthText];
  showDatePicker.value = false;
  router.replace({ query: { ...route.query, year: currentYear.value, month: currentMonth.value } });
  onRefresh();
};

const formatAmount = (amount) => (amount ? Number(amount).toFixed(2) : "0.00");

// 获取币种符号
const getCurrencySymbol = (code) => {
  const symbols = {
    CNY: "¥",
    USD: "$",
    EUR: "€",
    HKD: "HK$",
    JPY: "¥",
    GBP: "£",
    KRW: "₩",
    TWD: "NT$",
  };
  return symbols[code] || code || "";
};

// 是否为外币
const isForeignCurrency = (item) => {
  return item.currency && item.currency !== "CNY";
};

const formatDateHeader = (date) => {
  if (!date) return "";
  const d = dayjs(date);
  if (d.isSame(dayjs(), "day")) return "今天";
  if (d.isSame(dayjs().subtract(1, "day"), "day")) return "昨天";
  return d.format("M月D日 ddd");
};

const formatTime = (ts) => (ts ? dayjs(Number(ts)).format("HH:mm") : "");

// 新模板用到的辅助函数
const isToday = (date) => dayjs(date).isSame(dayjs(), "day")
const formatWeekday = (date) => {
  if (!date) return ""
  const week = ["周日","周一","周二","周三","周四","周五","周六"]
  return week[dayjs(date).day()]
}
const dayIncome = (group) => group.reduce((s, i) => {
  if (i.type === 'flow' && (i.data.direction === 1 || i.data.direction === 'income')) return s + Number(i.data.amount || 0)
  return s
}, 0)
const dayExpense = (group) => group.reduce((s, i) => {
  if (i.type === 'flow' && (i.data.direction === 0 || i.data.direction === 'expense' || i.data.direction === 2)) return s + Number(i.data.amount || 0)
  if (i.type === 'external-transfer') return s + Number(i.data.amount || 0)
  return s
}, 0)
const getCompactCardLabel = (item) => {
  const cardId = item.card_id || item.cardId
  if (cardId === 'yyyy') return '余额'
  if (cardId === 'xxxx') return '现金'
  const card = cardList.value.find(c => c.id === cardId)
  if (!card) return item.pay_method || ''
  const last4 = card.card_last4 || card.last4_no || card.last4No || ''
  return last4 ? last4 : (card.alias || '')
}

// 获取分类名称（处理特殊分类）
const getCategoryName = (item) => {
  if (item.category_id === "CATEGORY_REPAY") {
    return "信用卡还款";
  }
  return item.category_name || "未知分类";
};

const getCardName = (id) => {
  if (!id) return "";
  if (id === "xxxx") return "现金";
  if (id === "yyyy") return "余额";
  const card = cardList.value.find((c) => c.id === id);
  if (!card) return id;
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  const bankName = bank?.name || card.alias || card.bank_name || "";
  const last4 = card.card_last4 || card.last4_no || card.last4No || "";
  if (bankName && last4) return `${bankName} ${last4}`;
  if (bankName) return bankName;
  if (last4) return last4;
  return card.alias || card.bank_name || id;
};

// 获取卡片银行图标
const getCardBankIcon = (id) => {
  if (!id || id === "xxxx" || id === "yyyy") return "";
  const card = cardList.value.find((c) => c.id === id);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  return bank?.icon_url || bank?.iconUrl || "";
};

// ── 转账/提现检测 + 分组输出（watch list 每次全量重算）──
const _processList = (items) => {
  const pairs = [];
  const usedExpenseIds = new Set();
  const usedIncomeIds = new Set();

  const addPair = (expense, income, isExplicit, isWithdrawal = false, isReversal = false) => {
    pairs.push({ expense, income, isExplicit, isWithdrawal, isReversal });
    usedExpenseIds.add(expense.id);
    usedIncomeIds.add(income.id);
  };

  // 辅助函数
  const getDate = (item) => (item.trans_date || '').slice(0, 10);
  const getCard = (item) => item.card_id || '';
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
    if (item.category_id === 'CATEGORY_REPAY' || !item.transfer_group_id) return map;
    if (!map[item.transfer_group_id]) map[item.transfer_group_id] = [];
    map[item.transfer_group_id].push(item);
    return map;
  }, {});

  Object.values(transferGroups).forEach((group) => {
    const expense = group.find((item) => item.direction === 0 || item.direction === 'expense');
    const income = group.find((item) => item.direction === 1 || item.direction === 'income');
    if (expense && income) addPair(expense, income, true);
  });

  // 第1.5趟：信用卡支出 + 现金/余额收入 + 收入类别为"冲正" → 冲正
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return;
    if (!isCreditCard(item)) return;  // 支出方必须是信用卡
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      if (!(inc.direction === 1 || inc.direction === 'income')) return false;
      if (!baseMatch(item, inc)) return false;
      // 收入方为现金或余额
      if (!isVirtual(getCard(inc))) return false;
      // 收入类别为冲正
      const incCat = inc.pay_type || inc.category_name || '';
      return incCat === '冲正';
    });
    if (match) addPair(item, match, true, false, true);
  });

  // 第2趟：双方分类均为"转账" → 确诊转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return;
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      if (!(inc.direction === 1 || inc.direction === 'income')) return false;
      if (!baseMatch(item, inc)) return false;
      const expCat = item.pay_type || item.category_name || '';
      const incCat = inc.pay_type || inc.category_name || '';
      return expCat === '转账' && incCat === '转账';
    });
    if (match) addPair(item, match, true);
  });

  // 第3趟：支出"其他支出" + 收入"其他收入" → 疑似转账（支出方不能为信用卡）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return;
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      if (!(inc.direction === 1 || inc.direction === 'income')) return false;
      if (!baseMatch(item, inc)) return false;
      const expCat = item.pay_type || item.category_name || '';
      const incCat = inc.pay_type || inc.category_name || '';
      return expCat === '其他支出' && incCat === '其他收入';
    });
    if (match) addPair(item, match, false);
  });

  // 第4趟：其余满足基础条件的 → 疑似转账（排除余额→银行卡方向 和 信用卡支出方）
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return;
    const expenseCard = getCard(item);
    const isExpenseVirtual = isVirtual(expenseCard);
    if (isCreditCard(item)) return;  // 信用卡不归入转账
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      if (!(inc.direction === 1 || inc.direction === 'income')) return false;
      if (!baseMatch(item, inc)) return false;
      // 余额→银行卡方向排除（归入提现）
      if (isExpenseVirtual && !isVirtual(getCard(inc))) return false;
      return true;
    });
    if (match) {
      const bothTransfer = (item.pay_type === '转账' && match.pay_type === '转账');
      addPair(item, match, bothTransfer);
    }
  });

  // 第5趟：余额→银行卡（同天同金额，时间接近）→ 提现
  items.forEach((item) => {
    if (usedExpenseIds.has(item.id) || usedIncomeIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    if (!(item.direction === 0 || item.direction === 'expense' || item.direction === 2)) return;
    const expenseCard = getCard(item);
    if (expenseCard !== 'yyyy') return;  // 仅限余额卡片
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      if (!(inc.direction === 1 || inc.direction === 'income')) return false;
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

  // 构建展示项
  const expenseIds = new Set(pairs.map(p => p.expense.id));
  const incomeIds = new Set(pairs.map(p => p.income.id));
  const processed = [];
  items.forEach((item) => {
    if (expenseIds.has(item.id)) {
      const pair = pairs.find(p => p.expense.id === item.id);
      if (pair) {
        processed.push({
          type: pair.isReversal ? 'reversal' : (pair.isWithdrawal ? 'withdrawal' : 'transfer'),
          ...pair
        });
      }
    } else if (!incomeIds.has(item.id)) {
      if (item.pay_type === '转账') {
        processed.push({ type: 'external-transfer', data: item });
      } else {
        processed.push({ type: 'flow', data: item });
      }
    }
  });

  // 按日期分组
  const groups = {};
  processed.forEach((item) => {
    const date = (item.type === 'transfer' || item.type === 'withdrawal' || item.type === 'reversal')
      ? (item.expense.trans_date || '').slice(0, 10)
      : (item.data.trans_date || '').slice(0, 10);
    if (date) {
      if (!groups[date]) groups[date] = [];
      groups[date].push(item);
    }
  });
  return groups;
};

const groupedList = ref({});
watch(list, (items) => {
  groupedList.value = _processList(items);
}, { immediate: true });

const loadSummary = async () => {
  try {
    const res = await getMonthStats({
      year: currentYear.value,
      month: currentMonth.value,
    });
    if (res.data) {
      summaryData.value = {
        income: res.data.income || 0,
        expense: res.data.expense || 0,
      };
    }
  } catch (e) {
    summaryData.value = null;
  }
};

const loadData = async () => {
  if (refreshing.value) return;
  loading.value = true;

  try {
    const params = {
      page: page.value,
      limit,
      startDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`)
        .startOf("month")
        .format("YYYY-MM-DD"),
      endDate: dayjs(`${currentYear.value}-${currentMonth.value}-01`)
        .endOf("month")
        .format("YYYY-MM-DD"),
    };

    if (filterType.value !== "all") {
      params.direction = filterType.value === "income" ? 1 : 0;
    }

    const res = await getAccountList(params);
    const data = res.data?.list || res.data || [];
    const pagination = res.data?.pagination || {};

    if (page.value === 1) {
      list.value = data;
    } else {
      const seenIds = new Set(list.value.map((item) => item.id));
      list.value = [...list.value, ...data.filter((item) => !seenIds.has(item.id))];
    }

    finished.value =
      data.length < limit || page.value >= (pagination.totalPages || 1);
    if (!finished.value) page.value++;
  } catch (e) {
    finished.value = true;
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

const onRefresh = () => {
  page.value = 1;
  finished.value = false;
  loading.value = true;
  Promise.all([loadSummary(), loadData()]).finally(() => {
    loading.value = false;
    refreshing.value = false;
  });
};

const onFilterChange = () => onRefresh();

const goDetail = (item) => router.push(`/finance/flow/${item.id}`);

const goCalendar = () => router.push("/finance/flow/calendar");
</script>

<style scoped>
/* ================================================================
   FLOW LIST V2 — 时间线 + 卡片全新设计
   ================================================================ */

.page-flow-v2 {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  overflow: hidden;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
}

/* ── 月份卡片 ── */
.month-card {
  margin: 10px 12px 8px;
  background: #fff;
  border-radius: 5px;
  padding: 16px 18px 14px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.04);
}
.month-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.month-picker {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.month-year {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
}

/* ── 统计行 ── */
.stats-row {
  display: flex;
  align-items: center;
  gap: 0;
}
.stat-kv {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.sk-label {
  font-size: 11px;
  color: #969799;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.sk-val {
  font-size: 19px;
  font-weight: 700;
  color: #323233;
  font-family: "DIN Alternate", "SF Mono", monospace;
}
.sk-val.in { color: #07c160; }
.sk-val.out { color: #ee0a24; }
.stat-divider {
  width: 1px;
  height: 32px;
  background: #ebedf0;
}

/* ── 迷你进度条 ── */
.mini-bar {
  display: flex;
  height: 4px;
  border-radius: 2px;
  overflow: hidden;
  margin-top: 12px;
  background: #f2f3f5;
}
.mb-seg { min-width: 4px; transition: flex 0.4s ease; }
.mb-in { background: #07c160; }
.mb-out { background: #ee0a24; }

/* ── Tab 筛选 ── */
.tab-bar {
  display: flex;
  margin: 0 12px 8px;
  background: #fff;
  border-radius: 5px;
  padding: 3px;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 7px 0;
  font-size: 13px;
  font-weight: 500;
  color: #969799;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-item.active {
  background: #1989fa;
  color: #fff;
  box-shadow: 0 2px 6px rgba(25,137,250,0.25);
}

/* ── 时间线滚动区 ── */
.timeline-scroll {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 12px;
  position: relative;
}

/* ── 日期区块 ── */
.day-block {
  margin-bottom: 6px;
}
.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px 6px;
  position: sticky;
  top: 0;
  z-index: 5;
  background: #f0f2f5;
}
.day-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dcdee0;
  flex-shrink: 0;
  border: 2px solid #f0f2f5;
}
.day-dot.today {
  background: #1989fa;
  box-shadow: 0 0 0 3px rgba(25,137,250,0.18);
}

/* 滚动吸顶日期 */
.sticky-date-bar {
  position: sticky;
  top: 0;
  z-index: 99;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 6px 6px;
  margin: 0 -12px;
  background: #f0f2f5;
  pointer-events: none;
}
.sticky-date-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dcdee0;
  flex-shrink: 0;
  border: 2px solid #f0f2f5;
}
.sticky-date-dot.today {
  background: #1989fa;
  box-shadow: 0 0 0 3px rgba(25,137,250,0.18);
}
.sticky-date-text {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.day-text {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.day-date {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.day-weekday {
  font-size: 12px;
  color: #969799;
}
.day-sum {
  display: flex;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
}
.ds-in { color: #07c160; }
.ds-out { color: #ee0a24; }

/* ── 条目卡片容器 ── */
.day-cards {
  margin-left: 5px;
  border-left: 1.5px solid #ebedf0;
  padding-left: 14px;
}

/* ── 普通流水卡片 ── */
.flow-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 5px;
  padding: 12px 14px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: transform 0.12s, box-shadow 0.12s;
  position: relative;
}
.flow-card:active {
  transform: scale(0.985);
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.fc-arrow {
  font-size: 18px;
  font-weight: 700;
  margin-right: 10px;
  flex-shrink: 0;
  width: 22px;
  text-align: center;
}
.fc-arrow.in { color: #07c160; }
.fc-arrow.out { color: #ee0a24; }

.fc-body {
  flex: 1;
  min-width: 0;
}
.fc-cat {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
  margin-bottom: 3px;
}
.fc-meta {
  font-size: 11px;
  color: #969799;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.fc-bank-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: #f7f8fa;
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 10px;
  color: #646566;
  margin-left: 2px;
}
.fc-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 10px;
  flex-shrink: 0;
}
.fc-amount {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  font-family: "DIN Alternate", "SF Mono", monospace;
}
.fc-amount.in { color: #07c160; }
.fc-amount.out { color: #ee0a24; }
.fc-time {
  font-size: 10px;
  color: #c8c9cc;
  margin-top: 2px;
}
.fc-currency { font-size: 11px; font-weight: 500; margin-right: 1px; }

.fc-badge {
  position: absolute;
  top: -1px;
  right: 10px;
  font-size: 9px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 0 5px 0 5px;
}
.fc-badge-warn { background: #fff7e6; color: #ff976a; }

/* ── 配对区块（转账 / 提现 / 冲正）── */
.paired-block, .transfer-block {
  margin-bottom: 8px;
}
.pd-main, .tf-main {
  border-radius: 5px;
  padding: 5px 14px;
  cursor: pointer;
  transition: transform 0.12s;
}


.pd-main:active, .tf-main:active { transform: scale(0.985); }

/* 转账：蓝 */
.tf-main {
  background: rgba(25,137,250,0.04);
  border: 2px dashed #1989fa;
}
/* 提现：绿 */
.pd-withdrawal {
  background: rgba(46,125,50,0.04);
  border: 2px dashed #2e7d32;
}
/* 冲正：灰 */
.pd-reversal {
  background: rgba(0,0,0,0.03);
  border: 2px dashed rgba(0,0,0,0.16);
  opacity: 0.85;
}

.tf-row { display: flex; align-items: center; }

/* 第一层：时间 + 标签 + 时间 */
.tf-time-row {
  justify-content: space-between;
}
.tf-t { font-size: 11px; color: #969799; }
.tf-label {
  font-size: 11px;
  font-weight: 600;
  color: #1989fa;
}
.pd-label {
  font-size: 11px;
  font-weight: 600;
  color: #2e7d32;
}

/* 第二层：金额 + icon + 金额 */
.tf-amount-row {
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.tf-amt {
  font-size: 19px;
  font-weight: 700;
  font-family: "DIN Alternate", "SF Mono", monospace;
}
.tf-amt.out { color: #ee0a24; }
.tf-amt.in { color: #07c160; }
.tf-exchange-icon {
  font-size: 18px;
  color: #1989fa;
  flex-shrink: 0;
}

/* 第三层：银行名 */
.tf-bank-row {
  justify-content: space-between;
}
.tf-bank-name {
  font-size: 11px;
  color: #646566;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 展开明细 */
.tf-detail {
  margin: 0 0 0 14px;
}
.tf-detail-item {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 5px;
  padding: 10px 12px;
  margin-top: 4px;
  cursor: pointer;
}
.tfd-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 10px;
  flex-shrink: 0;
}
.tfd-dot.out { background: #ee0a24; }
.tfd-dot.in { background: #07c160; }
.tfd-body {
  flex: 1;
  min-width: 0;
}
.tfd-cat { font-size: 13px; color: #323233; font-weight: 500; }
.tfd-meta { font-size: 11px; color: #969799; margin-top: 1px; }
.tfd-amt {
  font-size: 15px;
  font-weight: 600;
  font-family: "DIN Alternate", "SF Mono", monospace;
  margin-left: 8px;
  white-space: nowrap;
}
.tfd-amt.out { color: #ee0a24; }
.tfd-amt.in { color: #07c160; }

/* ── 返回顶部 ── */
.back-top {
  position: fixed;
  right: 16px;
  bottom: 60px;
  width: 40px;
  height: 40px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #1989fa;
  z-index: 999;
}

</style>
