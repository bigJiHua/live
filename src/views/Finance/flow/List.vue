<template>
  <div class="page-flow-list">
    <div class="filter-bar">
      <van-tabs v-model:active="filterType" shrink @change="onFilterChange">
        <van-tab title="全部" name="all" />
        <van-tab title="收入" name="income" />
        <van-tab title="支出" name="expense" />
      </van-tabs>
      <div class="filter-right">
        <div class="date-filter" @click="showDatePicker = true">
          <span>{{ dateRangeText }}</span>
          <van-icon name="arrow-down" />
        </div>
        <van-icon name="calendar-o" class="calendar-icon" @click="goCalendar" />
      </div>
    </div>

    <div class="stats-summary" v-if="summaryData">
      <div class="summary-item">
        <span class="label">收入</span>
        <span class="value income"
          >+{{ formatAmount(summaryData.income) }}</span
        >
      </div>
      <div class="summary-item">
        <span class="label">支出</span>
        <span class="value expense"
          >-{{ formatAmount(summaryData.expense) }}</span
        >
      </div>
      <div class="summary-item">
        <span class="label">笔数</span>
        <span class="value">{{ list.length }}</span>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadData"
      >
        <div
          v-for="(group, date) in groupedList"
          :key="date"
          class="date-group"
        >
          <div class="date-header">{{ formatDateHeader(date) }}</div>
          <div class="date-items">
            <template v-for="item in group" :key="item.type === 'transfer' ? item.expense.id : item.data.id">
              <!-- 转账合并展示 -->
              <div v-if="item.type === 'transfer'" class="transfer-item">
                <div class="flow-item transfer-flow" @click="toggleTransferExpand(item.expense.id)">
                  <div class="item-left">
                    <div class="category-icon transfer-icon-bg">
                      <van-icon name="exchange" />
                    </div>
                    <div class="item-info">
                      <div class="item-title">{{ item.isExplicit ? '转账' : '疑似转账' }}</div>
                      <div class="item-desc">
                        {{ getCardDisplay(item.expense) }} → {{ getCardDisplay(item.income) }}
                      </div>
                      <div class="transfer-times-inline">
                        <span class="time-out">{{ formatTime(item.expense.create_time) }}</span>
                        <span class="time-sep">/</span>
                        <span class="time-in">{{ formatTime(item.income.create_time) }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="item-right">
                    <div class="transfer-amounts">
                      <span class="amount-tag expense">出 {{ formatAmount(item.expense.amount) }}</span>
                      <span class="amount-tag income">进 {{ formatAmount(item.income.amount) }}</span>
                      <van-icon :name="expandedTransferIds.has(item.expense.id) ? 'arrow-up' : 'arrow-down'" size="12" style="color:#969799;margin-left:4px" />
                    </div>
                  </div>
                </div>

                <!-- 展开态：原始两笔明细（复用 flow-item 样式） -->
                <div v-if="expandedTransferIds.has(item.expense.id)" class="transfer-detail">
                  <div class="flow-item" @click.stop="goDetail(item.expense)">
                    <div class="item-left">
                      <div class="category-icon">
                        <van-icon :name="getCategoryIcon(item.expense.category_name)" />
                      </div>
                      <div class="item-info">
                        <div class="item-title">{{ getCategoryName(item.expense) }}</div>
                        <div class="item-desc">
                          {{ item.expense.pay_method || '-' }} ·
                          {{ formatTime(item.expense.create_time) }}
                        </div>
                        <div v-if="item.expense.card_id && !['xxxx','yyyy'].includes(item.expense.card_id)" class="card-tag-inline">
                          <van-image v-if="getCardBankIcon(item.expense.card_id)" width="14" height="14" :src="getFullUrl(getCardBankIcon(item.expense.card_id))" fit="contain" class="card-tag-icon" />
                          <span>{{ getCardName(item.expense.card_id) }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="item-right">
                      <div class="amount expense">-{{ formatAmount(item.expense.amount) }}</div>
                    </div>
                  </div>
                  <div class="flow-item" @click.stop="goDetail(item.income)">
                    <div class="item-left">
                      <div class="category-icon">
                        <van-icon :name="getCategoryIcon(item.income.category_name)" />
                      </div>
                      <div class="item-info">
                        <div class="item-title">{{ getCategoryName(item.income) }}</div>
                        <div class="item-desc">
                          {{ item.income.pay_method || '-' }} ·
                          {{ formatTime(item.income.create_time) }}
                        </div>
                        <div v-if="item.income.card_id && !['xxxx','yyyy'].includes(item.income.card_id)" class="card-tag-inline">
                          <van-image v-if="getCardBankIcon(item.income.card_id)" width="14" height="14" :src="getFullUrl(getCardBankIcon(item.income.card_id))" fit="contain" class="card-tag-icon" />
                          <span>{{ getCardName(item.income.card_id) }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="item-right">
                      <div class="amount income">+{{ formatAmount(item.income.amount) }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 对外转账单笔展示 -->
              <div
                v-else-if="item.type === 'external-transfer'"
                class="flow-item external-transfer"
                @click="goDetail(item.data)"
              >
                <div class="item-left">
                  <div class="category-icon transfer-icon">
                    <van-icon name="exchange" />
                  </div>
                  <div class="item-info">
                    <div class="item-title">
                      <van-tag type="warning" size="small" style="margin-right:4px">对外转账</van-tag>
                      {{ getCategoryName(item.data) }}
                    </div>
                    <div class="item-desc">
                      {{ item.data.pay_method || "-" }} ·
                      {{ formatTime(item.data.create_time) }}
                    </div>
                    <div
                      v-if="item.data.card_id && !['xxxx', 'yyyy'].includes(item.data.card_id)"
                      class="card-tag-inline"
                    >
                      <van-image
                        v-if="getCardBankIcon(item.data.card_id)"
                        width="14"
                        height="14"
                        :src="getFullUrl(getCardBankIcon(item.data.card_id))"
                        fit="contain"
                        class="card-tag-icon"
                      />
                      <span>{{ getCardName(item.data.card_id) }}</span>
                    </div>
                  </div>
                </div>
                <div class="item-right">
                  <div class="amount expense">
                    <span v-if="isForeignCurrency(item.data)" class="currency-tag">
                      {{ getCurrencySymbol(item.data.currency) }}
                    </span>
                    -{{ formatAmount(item.data.amount) }}
                  </div>
                </div>
              </div>

              <!-- 普通流水 -->
              <div
                v-else
                class="flow-item"
                @click="goDetail(item.data)"
              >
                <div class="item-left">
                  <div class="category-icon">
                    <van-icon :name="getCategoryIcon(item.data.category_name)" />
                  </div>
                  <div class="item-info">
                    <div class="item-title">
                      {{ getCategoryName(item.data) }}
                    </div>
                    <div class="item-desc">
                      {{ item.data.pay_method || "-" }} ·
                      {{ formatTime(item.data.create_time) }}
                    </div>
                    <div
                      v-if="item.data.card_id && !['xxxx', 'yyyy'].includes(item.data.card_id)"
                      class="card-tag-inline"
                    >
                      <van-image
                        v-if="getCardBankIcon(item.data.card_id)"
                        width="14"
                        height="14"
                        :src="getFullUrl(getCardBankIcon(item.data.card_id))"
                        fit="contain"
                        class="card-tag-icon"
                      />
                      <span>{{ getCardName(item.data.card_id) }}</span>
                    </div>
                  </div>
                </div>
                <div class="item-right">
                  <div
                    class="amount"
                    :class="item.data.direction === 1 ? 'income' : 'expense'"
                  >
                    <span v-if="isForeignCurrency(item.data)" class="currency-tag">
                      {{ getCurrencySymbol(item.data.currency) }}
                    </span>
                    {{ item.data.direction === 1 ? "+" : "-"
                    }}{{ formatAmount(item.data.amount) }}
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <van-empty
          v-if="!loading && list.length === 0"
          description="暂无流水记录"
        />
      </van-list>
    </van-pull-refresh>

    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-picker
        v-model="selectedValues"
        title="选择月份"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import dayjs from "dayjs";
import zhCn from "dayjs/locale/zh-cn";
import { getAccountList, getMonthStats } from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";

dayjs.locale(zhCn);
const router = useRouter();

const BASE_URL = ENV.FILE_BASE_URL;

// 筛选状态
const filterType = ref("all");
const showDatePicker = ref(false);

// 当前年月
const now = dayjs();
const currentYear = ref(now.year());
const currentMonth = ref(now.month() + 1);
// Vant 4 必须绑定 v-model 数组来控制选中项
const selectedValues = ref([`${now.year()}年`, `${now.month() + 1}月`]);

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

const toggleTransferExpand = (expenseId) => {
  const s = new Set(expandedTransferIds.value);
  if (s.has(expenseId)) s.delete(expenseId);
  else s.add(expenseId);
  expandedTransferIds.value = s;
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

const dateRangeText = computed(
  () => `${currentYear.value}年${currentMonth.value}月`
);

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
  selectedValues.value = [yearText, monthText]; // 同步选中状态
  showDatePicker.value = false;
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

// 获取分类名称（处理特殊分类）
const getCategoryName = (item) => {
  if (item.category_id === "CATEGORY_REPAY") {
    return "信用卡还款";
  }
  return item.category_name || "未知分类";
};

// 获取卡片简略显示名（转账概览用）
const getCardDisplay = (item) => {
  const cardId = item.card_id;
  if (!cardId || cardId === 'xxxx') return '现金';
  if (cardId === 'yyyy') return '余额';
  const card = cardList.value.find((c) => c.id === cardId);
  if (!card) return item.pay_method || '未知';
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  const alias = bank?.name || card.alias || card.bank_name || '';
  const last4 = card.card_last4 || card.last4_no || card.last4No || '';
  if (alias && last4) return `${alias} ${last4}`;
  if (alias) return alias;
  if (last4) return last4;
  return '未知';
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

// ── 转账检测 + 分组输出（watch list 每次全量重算）──
const _processList = (items) => {
  // 第一趟：找配对
  const pairs = [];
  const usedExpenseIds = new Set();
  const usedIncomeIds = new Set();

  const addPair = (expense, income, isExplicit) => {
    pairs.push({ expense, income, isExplicit });
    usedExpenseIds.add(expense.id);
    usedIncomeIds.add(income.id);
  };

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

  items.forEach((item) => {
    if (usedExpenseIds.has(item.id)) return;
    if (item.category_id === 'CATEGORY_REPAY') return;
    const isExpense = item.direction === 0 || item.direction === 'expense'
    if (!isExpense) return;
    const date = (item.trans_date || '').slice(0, 10);
    const match = items.find((inc) => {
      if (inc.id === item.id || usedIncomeIds.has(inc.id)) return false;
      if (inc.category_id === 'CATEGORY_REPAY') return false;
      const isInc = inc.direction === 1 || inc.direction === 'income';
      if (!isInc) return false;
      const incDate = (inc.trans_date || '').slice(0, 10);
      return incDate === date &&
        Math.abs(Number(item.amount || 0) - Number(inc.amount || 0)) < 0.01 &&
        (item.card_id || 'none') !== (inc.card_id || 'none2')
    });
    if (match) {
      addPair(item, match, item.pay_type === '转账' && match.pay_type === '转账')
    }
  });

  // 第二趟：构建展示项
  const expenseIds = new Set(pairs.map(p => p.expense.id));
  const incomeIds = new Set(pairs.map(p => p.income.id));
  const processed = [];
  items.forEach((item) => {
    if (expenseIds.has(item.id)) {
      const pair = pairs.find(p => p.expense.id === item.id);
      if (pair) processed.push({ type: 'transfer', ...pair });
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
    const date = item.type === 'transfer'
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

onMounted(() => {
  categoryApi.list("bank").then((res) => (bankList.value = res.data || res || [])).catch(() => {});
  getCardList().then((res) => (cardList.value = res.data || []));
  loadSummary();
  loadData();
});

const goDetail = (item) => router.push(`/finance/flow/${item.id}`);

const goCalendar = () => router.push("/finance/flow/calendar");
</script>

<style scoped>
/* 保持你原有的样式，建议添加以下微调 */
.flow-item:last-child {
  border-bottom: none;
}
.card-tag {
  display: inline-block;
  padding: 0 4px;
  background: #f0f0f0;
  border-radius: 2px;
}

.page-flow-list {
  min-height: 100vh;
  background: #f7f8fa;
}

.filter-bar {
  background: #fff;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.date-filter {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #646566;
  padding: 6px 10px;
  background: #f7f8fa;
  border-radius: 4px;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.calendar-icon {
  font-size: 20px;
  color: #1989fa;
  padding: 4px;
}

.stats-summary {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: #fff;
  margin: 8px 16px;
  border-radius: 12px;
}

.summary-item {
  text-align: center;
}

.summary-item .label {
  font-size: 12px;
  color: #969799;
  display: block;
  margin-bottom: 4px;
}

.summary-item .value {
  font-size: 16px;
  font-weight: bold;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.summary-item .income {
  color: #07c160;
}

.summary-item .expense {
  color: #ee0a24;
}

.date-group {
  margin-top: 8px;
}

.date-header {
  padding: 8px 16px;
  font-size: 13px;
  color: #646566;
  background: #f7f8fa;
}

.date-items {
  background: #fff;
}

.flow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  margin-left: 16px;
  border-bottom: 1px solid #f2f2f2;
}

.flow-item:last-child {
  border-bottom: none;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.category-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #1989fa;
}

.item-info .item-title {
  font-size: 15px;
  color: #323233;
  font-weight: 500;
}

.item-info .item-desc {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}

.item-right {
  text-align: right;
}

.item-right .amount {
  font-size: 16px;
  font-weight: bold;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.currency-tag {
  font-size: 12px;
  margin-right: 2px;
  color: #969799;
}

.item-right .income {
  color: #07c160;
}

.item-right .expense {
  color: #323233;
}

.card-tag {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.card-tag-inline {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
  background: #f7f8fa;
  border-radius: 3px;
  padding: 1px 5px;
}

.card-tag-icon {
  border-radius: 2px;
  flex-shrink: 0;
}

/* ── 转账合并展示 ── */
.transfer-item {
  position: relative;
}
.transfer-item::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #1989fa;
  border-radius: 2px;
}
.transfer-flow {
  background: #f0f7ff;
  margin: 12px 0 12px 16px;
}
.transfer-icon-bg {
  background: #e6f2ff !important;
  color: #1989fa !important;
}
.transfer-times-inline {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 2px;
}
.transfer-times-inline .time-out { color: #969799; }
.transfer-times-inline .time-sep { margin: 0 4px; color: #dcdee0; }
.transfer-times-inline .time-in { color: #969799; }

.transfer-amounts {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.amount-tag {
  font-size: 12px;
  font-weight: 600;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}
.amount-tag.expense { color: #ee0a24; }
.amount-tag.income { color: #07c160; }

/* 展开后明细 */
.transfer-detail {
  margin: 0 16px 0 28px;
}
.transfer-detail .flow-item {
  margin-left: 0 !important;
  padding: 10px 14px;
  background: #fff;
  border-radius: 8px;
  margin-top: 6px;
}
/* 对外转账 */
.external-transfer .transfer-icon {
  background: #fff7e6;
  color: #ff976a;
}
</style>
