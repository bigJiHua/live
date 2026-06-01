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

    <div ref="listScrollRef" class="list-scroll">
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
            <template v-for="item in group" :key="(item.type === 'transfer' || item.type === 'withdrawal') ? item.expense.id : item.data.id">
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

              <!-- 提现合并展示 -->
              <div v-else-if="item.type === 'withdrawal'" class="withdrawal-item">
                <div class="flow-item withdrawal-flow">
                  <div class="item-left">
                    <div class="category-icon withdrawal-icon-bg">
                      <van-icon name="gold-coin-o" />
                    </div>
                    <div class="item-info">
                      <div class="item-title">提现</div>
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
                    </div>
                  </div>
                </div>
              </div>

              <!-- 冲正合并展示 -->
              <div v-else-if="item.type === 'reversal'" class="reversal-item">
                <div class="flow-item reversal-flow">
                  <div class="item-left">
                    <div class="category-icon reversal-icon-bg">
                      <van-icon name="revoke" />
                    </div>
                    <div class="item-info">
                      <div class="item-title">冲正</div>
                      <div class="item-desc" style="text-decoration: line-through;">
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
    </div>

    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-picker
        v-model="selectedValues"
        title="选择月份"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 返回顶部 -->
    <van-icon
      v-show="showBackTop"
      name="back-top"
      class="back-top-btn"
      @click="scrollToTop"
    />
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
    const date = (item.type === 'transfer' || item.type === 'withdrawal')
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
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  overflow: hidden;
}

.list-scroll {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.filter-bar {
  background: #fff;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  padding: 10px 0;
  background: #fff;
  margin: 0 0 8px 0;
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
  /* margin-left: 16px; */
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

/* ── 提现合并展示 ── */
.withdrawal-item {
  position: relative;
}
.withdrawal-item::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #2e7d32;
  border-radius: 2px;
}
.withdrawal-flow {
  background: #e8f5e9;
  margin: 12px 0 12px 16px;
}
.withdrawal-icon-bg {
  background: #c8e6c9 !important;
  color: #2e7d32 !important;
}

/* ── 冲正合并展示（灰色，表示已撤销/无效）── */
.reversal-item {
  position: relative;
}
.reversal-item::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #c8c9cc;
  border-radius: 2px;
}
.reversal-flow {
  background: #f5f5f5;
  margin: 12px 0 12px 16px;
  opacity: 0.72;
}
.reversal-icon-bg {
  background: #e8e8e8 !important;
  color: #969799 !important;
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

/* 返回顶部 */
.back-top-btn {
  position: fixed;
  right: 20px;
  bottom: 80px;
  width: 44px;
  height: 44px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #1989fa;
  z-index: 1000;
  cursor: pointer;
  transition: opacity 0.3s;
}
.back-top-btn:active {
  opacity: 0.7;
}
</style>
