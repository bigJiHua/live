<template>
  <div class="page-flow-v2">
    <!-- ====== 顶部月份卡片 ====== -->
    <div class="month-card">
      <div class="month-header">
        <div class="month-picker" @click="showDatePicker = true">
          <span class="month-year">{{ currentYear }}年{{ currentMonth }}月</span>
          <van-icon name="arrow-down" size="16" />
        </div>
        <van-icon name="calendar-o" size="22" :color="'var(--theme-primary)'" @click="goCalendar" />
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
    <div ref="listScrollRef" class="timeline-scroll" @scroll="onScroll">
      <van-pull-refresh v-model="refreshing" :disabled="!isCurrentMonth" @refresh="onRefresh">
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

                <!-- === 提现 - 三层布局 + 展开/折叠（复用 transfer 展开逻辑）=== -->
                <div v-else-if="item.type === 'withdrawal'" class="paired-block">
                  <div class="pd-main pd-withdrawal" @click="toggleTransferExpand(item.expense.id)">
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
                  <!-- 展开明细（与 transfer 块相同的子列表结构） -->
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

                <!-- === 冲正 - 三层布局 + 展开/折叠（复用 transfer 展开逻辑）=== -->
                <div v-else-if="item.type === 'reversal'" class="paired-block">
                  <div class="pd-main pd-reversal" @click="toggleTransferExpand(item.expense.id)">
                    <div class="tf-row tf-time-row">
                      <span class="tf-t">{{ formatTime(item.expense.create_time) }}</span>
                      <span class="tf-label" style="text-decoration:line-through;color:var(--theme-text-tertiary)">冲正</span>
                      <span class="tf-t">{{ formatTime(item.income.create_time) }}</span>
                    </div>
                    <div class="tf-row tf-amount-row">
                      <span class="tf-amt out" style="color:var(--theme-text-tertiary)">-{{ formatAmount(item.expense.amount) }}</span>
                      <van-icon name="revoke" class="tf-exchange-icon" style="color:var(--theme-text-tertiary)" />
                      <span class="tf-amt in" style="color:var(--theme-text-tertiary)">+{{ formatAmount(item.income.amount) }}</span>
                    </div>
                    <div class="tf-row tf-bank-row">
                      <span class="tf-bank-name" style="color:var(--theme-text-tertiary)">{{ getCardName(item.expense.card_id) || getCompactCardLabel(item.expense) }}</span>
                      <span class="tf-bank-name" style="color:var(--theme-text-tertiary)">{{ getCardName(item.income.card_id) || getCompactCardLabel(item.income) }}</span>
                    </div>
                  </div>
                  <!-- 展开明细（与 transfer 块相同的子列表结构） -->
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

                <!-- === 对外转账 / 给我转账（单边 pay_type='转账' 流水） -->
                <!--    视觉：普通 flow-card + 右上角徽标（不用虚线框 / 三层布局） -->
                <!--    对外转账：支出方向，pay_type='转账' 无配对 → 支出视角 -->
                <!--    给我转账：收入方向，pay_type='转账' 无配对 → 收入视角 -->
                <div v-else-if="item.type === 'external-transfer' || item.type === 'incoming-transfer'" class="flow-card" :class="isRepay(item.data) ? 'fc-repay' : ''" @click="goDetail(item.data)">
                  <div :class="item.type === 'incoming-transfer' ? 'fc-badge fc-badge-income' : 'fc-badge fc-badge-warn'">
                    {{ item.type === 'incoming-transfer' ? '给我转账' : '对外转账' }}
                  </div>
                  <div class="fc-body">
                    <div class="fc-cat">{{ getCategoryName(item.data) }}</div>
                    <div class="fc-meta">
                      {{ item.data.pay_method || "-" }} · {{ formatTime(item.data.create_time) }}
                      <span v-if="item.data.card_id && !['xxxx','yyyy'].includes(item.data.card_id)" class="fc-bank-chip">
                        <BankIcon :src="getFullUrl(getCardBankIcon(item.data.card_id))" :name="getCardBankName(item.data.card_id)" :size="12" />
                        {{ getCardName(item.data.card_id) }}
                      </span>
                    </div>
                  </div>
                  <div :class="item.data.direction === 1 ? 'fc-amount in' : 'fc-amount out'">
                    <span v-if="isForeignCurrency(item.data)" class="fc-currency">{{ getCurrencySymbol(item.data.currency) }}</span>
                    {{ item.data.direction === 1 ? '+' : '-' }}{{ formatAmount(item.data.amount) }}
                  </div>
                </div>

                <!-- === 普通流水条目 === -->
                <div v-else class="flow-card" :class="[item.data.direction === 1 ? 'fc-income' : 'fc-expense', isRepay(item.data) ? 'fc-repay' : '']" @click="goDetail(item.data)">
                  <span class="fc-arrow" :class="item.data.direction === 1 ? 'in' : 'out'">
                    {{ item.data.direction === 1 ? '↓' : '↑' }}
                  </span>
                  <div class="fc-body">
                    <div class="fc-cat">{{ getCategoryName(item.data) }}</div>
                    <div class="fc-meta">
                      {{ item.data.pay_method || "-" }}
                      <span v-if="item.data.card_id && !['xxxx','yyyy'].includes(item.data.card_id)" class="fc-bank-chip">
                        <BankIcon :src="getFullUrl(getCardBankIcon(item.data.card_id))" :name="getCardBankName(item.data.card_id)" :size="12" />
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
    <app-popup v-model:show="showDatePicker" position="bottom" round>
      <van-picker v-model="selectedValues" title="选择月份" :columns="pickerColumns" @confirm="onPickerConfirm" @cancel="showDatePicker = false" />
    </app-popup>

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
import BankIcon from "@/components/BankIcon.vue";

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

// 是否当月：仅当月才提供下拉刷新（其他月份下拉刷新没意义——下拉只拉今天，历史月份无"今天"）
const isCurrentMonth = computed(() => {
  const today = dayjs();
  return currentYear.value === today.year() && currentMonth.value === today.month() + 1;
});

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
  categoryApi.list("bank").then((res) => (bankList.value = res.data || res || [])).catch(() => {});
  getCardList().then((res) => (cardList.value = res.data || []));
  loadSummary();
  loadData();
});

// 离开时记住滚动位置
onDeactivated(() => {
  savedScrollY.value = listScrollRef.value?.scrollTop || window.scrollY || 0
})

// keep-alive 激活时：URL 有参数就恢复，无参数就整月重置为当前月
onActivated(() => {
  // 0. 优先级最高：Add 提交/删除成功 → 整月重载
  if (flowSync.consumeListRefresh()) {
    reloadAll()
    // 整页刷新后，Detail 变更即便有也无意义（数据是新的），清掉避免脏数据
    flowSync.consumeChanges()
    nextTick(() => {
      // 整页刷新后不恢复滚动位置，回到顶部
      listScrollRef.value?.scrollTo({ top: 0, behavior: 'instant' })
    })
    return
  }

  if (route.query.year && route.query.month) {
    const y = Number(route.query.year)
    const m = Number(route.query.month)
    if (y !== currentYear.value || m !== currentMonth.value) {
      currentYear.value = y
      currentMonth.value = m
      selectedValues.value = [`${y}年`, `${m}月`]
      reloadAll()
    }
  } else {
    const now = dayjs()
    if (now.year() !== currentYear.value || now.month() + 1 !== currentMonth.value) {
      currentYear.value = now.year()
      currentMonth.value = now.month() + 1
      selectedValues.value = [`${now.year()}年`, `${now.month() + 1}月`]
      reloadAll()
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
  reloadAll();
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

// 是否为信用卡还款（用于流水卡片置灰，资金转移非实际消费）
const isRepay = (item) => item && item.category_id === "CATEGORY_REPAY";

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

// 获取卡片银行名称（用于图标加载失败时的首字兜底）
const getCardBankName = (id) => {
  if (!id) return "";
  const card = cardList.value.find((c) => c.id === id);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  return bank?.name || bank?.bank_name || card.bank_name || "";
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
    if (expense && income) {
      // 识别"提现"：支出方是余额卡(yyyy) + 收入方是实体卡 → 走 withdrawal 绿色虚线
      // 识别"冲正"：后端冲正会同时写 transfer_group_id + reversed_id，需要识别
      const isWithdrawal = getCard(expense) === 'yyyy' && !isVirtual(getCard(income));
      const isReversal = !!(expense.reversed_id || income.reversed_id);
      if (isReversal) {
        addPair(expense, income, true, false, true);
      } else if (isWithdrawal) {
        addPair(expense, income, true, true, false);
      } else {
        addPair(expense, income, true);
      }
    }
  });

  // 第1.5趟：冲正 - 信用卡支出 + 现金/余额收入 + 同额度 + 时间接近 → 冲正
  // 业务背景：当前没有冲正页面/接口触发，所以"冲正"通常是手工记账
  // 模式：信用卡支出 (-N) + 现金/余额收入 (+N) 视为冲正
  // 强信号：收入方 pay_type === '冲正' → 直接锁定为冲正（不受其他条件限制）
  // 兜底：未指定 pay_type='冲正' 时，依赖 时间接近（5分钟内） + 同额度配对
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
      // 收入方为现金或余额（xxxx=现金，yyyy=余额）
      if (!isVirtual(getCard(inc))) return false;
      // 强信号：收入方分类为"冲正" → 直接锁定（即使时间不接近也认）
      const incCat = inc.pay_type || inc.category_name || '';
      if (incCat === '冲正') return true;
      // 否则要求时间接近（5 分钟内）— 避免"工资到账"等定期入账被误判为冲正
      const t1 = getTimestamp(item);
      const t2 = getTimestamp(inc);
      if (t1 && t2 && Math.abs(t1 - t2) > 300000) return false;
      return true;
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
      // 单边未被配对的：按方向 + pay_type 分类
      if (item.pay_type === '转账') {
        // 收入方向 + pay_type='转账' → 给我转账（单边收到转账，无对应支出方）
        //   case A: 余额转账（交易方式=余额 + 卡片=余额）
        //   case B: 银行卡转账（找不到同额度支出方）
        if (item.direction === 1 || item.direction === 'income') {
          processed.push({ type: 'incoming-transfer', data: item, income: item });
        } else {
          // 支出方向 + pay_type='转账' → 对外转账
          processed.push({ type: 'external-transfer', data: item });
        }
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

// 整月分页加载：用于首屏 onMounted、van-list 触底分页、reloadAll
// 守卫：若正在下拉刷新（refreshing=true）则跳过，避免与下拉刷新产生请求竞态
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

// 下拉刷新：只拉"今天"的流水 + 顶部月度统计
// 设计：用户从顶层下拉时，最关心的是"今天有没有新增"，不要把整月都重拉一遍
//   - 整月数据已经分页加载过了，重拉会浪费请求 + 可能让用户丢滚动位置
//   - 拉回来的"今天"数据按 id 原地 patch/insert 到 list.value（裸流水数组，由 watch 转 groupedList）
//   - 不重置 page/finished 分页状态
const loadTodayOnly = async () => {
  const today = dayjs().format("YYYY-MM-DD");
  const params = {
    page: 1,
    limit: 100,           // 当天一般不会超过 100 条，够用
    startDate: today,
    endDate: today,
  };
  if (filterType.value !== "all") {
    params.direction = filterType.value === "income" ? 1 : 0;
  }

  const res = await getAccountList(params);
  const todayList = res.data?.list || res.data || [];

  if (todayList.length === 0) {
    // 今天没有流水：把本地 list 中"今天"的旧条目清掉（可能用户在别处删除了）
    list.value = list.value.filter(
      (item) => (item.trans_date || "").slice(0, 10) !== today
    );
    return;
  }

  // 按 id 建索引，便于 patch / 判重
  const todayMap = new Map(todayList.map((it) => [it.id, it]));
  const todayIds = new Set(todayMap.keys());

  // 1) 已存在但被修改的"今天"条目：原地 patch（保留响应式引用，不触发 watch 重排）
  list.value.forEach((item) => {
    const d = (item.trans_date || "").slice(0, 10);
    if (d !== today) return;
    const fresh = todayMap.get(item.id);
    if (fresh) Object.assign(item, fresh);
  });

  // 2) 服务端有但本地没有的：插到 list 头部（按时间倒序，新的在前面）
  const existingIds = new Set(list.value.map((it) => it.id));
  const newOnes = todayList.filter((it) => !existingIds.has(it.id));
  if (newOnes.length > 0) {
    list.value = [...newOnes, ...list.value];
  }

  // 3) 本地有但服务端没有的"今天"条目：用户在别处删除了，从 list 移除
  list.value = list.value.filter((item) => {
    const d = (item.trans_date || "").slice(0, 10);
    if (d !== today) return true;
    return todayIds.has(item.id);
  });
};

// 整月重载：用于月份切换 / 筛选 tab 切换 / Add 提交后回列表 / 进入页面时月份变化
// 与 onRefresh（下拉刷新）语义不同：这里必须重置分页状态、丢滚动位置
const reloadAll = async () => {
  page.value = 1;
  finished.value = false;
  await Promise.all([loadSummary(), loadData()]);
};

// 下拉刷新：用户从顶部下拉触发
// 注意：van-pull-refresh 会把 refreshing 置 true 并显示顶部 loading，
// 我们拉完数据后再置回 false，否则动画不会结束
const onRefresh = async () => {
  // 顶部 loading 动画由 van-pull-refresh 通过 v-model="refreshing" 控制，
  // 不要再去动 loading（loading 是 van-list 底部分页 loading 的状态）
  try {
    // 只刷新：1) 顶部月度统计；2) 今天这天的流水
    // 不重置 page/finished，不影响分页状态，避免把用户已经看到的整月历史全清掉
    await Promise.all([loadSummary(), loadTodayOnly()]);
  } finally {
    // 等 DOM 更新完再关闭下拉动画，避免指示器瞬闪
    nextTick(() => {
      refreshing.value = false;
    });
  }
};

const onFilterChange = () => reloadAll();

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
  background: var(--theme-bg-primary);
  overflow: hidden;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
}

/* ── 月份卡片 ── */
.month-card {
  margin: 10px 12px 8px;
  background: var(--theme-bg-secondary);
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
  color: var(--theme-text-primary);
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
  color: var(--theme-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.sk-val {
  font-size: 19px;
  font-weight: 700;
  color: var(--theme-text-primary);
  font-family: "DIN Alternate", "SF Mono", monospace;
}
.sk-val.in { color: var(--van-green, #07c160); }
.sk-val.out { color: var(--van-danger-color, #ee0a24); }
.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--theme-border);
}

/* ── 迷你进度条 ── */
.mini-bar {
  display: flex;
  height: 4px;
  border-radius: 2px;
  overflow: hidden;
  margin-top: 12px;
  background: var(--theme-bg-tertiary);
}
.mb-seg { min-width: 4px; transition: flex 0.4s ease; }
.mb-in { background: var(--van-green, #07c160); }
.mb-out { background: var(--van-danger-color, #ee0a24); }

/* ── Tab 筛选 ── */
.tab-bar {
  display: flex;
  margin: 0 12px 8px;
  background: var(--theme-bg-secondary);
  border-radius: 5px;
  padding: 3px;
}
.tab-item {
  flex: 1;
  text-align: center;
  padding: 7px 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text-tertiary);
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-item.active {
  background: var(--theme-primary);
  color: #fff;
  box-shadow: 0 2px 6px rgba(var(--theme-primary-rgb), 0.25);
}

/* ── 时间线滚动区 ── */
.timeline-scroll {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 12px;
  position: relative;
}

/* van-pull-refresh 的 overflow: hidden 会创建不可滚动的 scroll container，
   导致内部 day-head 的 position: sticky 失效。
   此处覆盖 overflow 让 sticky 能穿透到 .timeline-scroll 吸顶。
   下拉刷新效果不受影响（仅 touchmove 控制） */
.timeline-scroll :deep(.van-pull-refresh) {
  overflow: visible;
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
  background: var(--theme-bg-primary);
}
.day-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--theme-text-placeholder);
  flex-shrink: 0;
  border: 2px solid var(--theme-bg-primary);
}
.day-dot.today {
  background: var(--theme-primary);
  box-shadow: 0 0 0 3px rgba(var(--theme-primary-rgb), 0.18);
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
  color: var(--theme-text-primary);
}
.day-weekday {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}
.day-sum {
  display: flex;
  gap: 8px;
  font-size: 12px;
  font-weight: 600;
}
.ds-in { color: var(--van-green, #07c160); }
.ds-out { color: var(--van-danger-color, #ee0a24); }

/* ── 条目卡片容器 ── */
.day-cards {
  margin-left: 5px;
  padding-left: 14px;
}

/* ── 普通流水卡片 ── */
.flow-card {
  display: flex;
  align-items: center;
  background: var(--theme-bg-secondary);
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

/* 信用卡还款：置灰（资金转移，非实际消费） */
.flow-card.fc-repay {
  filter: grayscale(1);
  opacity: 0.55;
}
/* 非默认（深色）主题：不置灰，改为普通白色文字 */
html[data-theme-mono="1"] .flow-card.fc-repay {
  filter: none;
  opacity: 1;
}
html[data-theme-mono="1"] .flow-card.fc-repay .fc-cat,
html[data-theme-mono="1"] .flow-card.fc-repay .fc-meta,
html[data-theme-mono="1"] .flow-card.fc-repay .fc-time,
html[data-theme-mono="1"] .flow-card.fc-repay .fc-bank-chip {
  color: #fff;
}

.fc-arrow {
  font-size: 18px;
  font-weight: 700;
  margin-right: 10px;
  flex-shrink: 0;
  width: 22px;
  text-align: center;
}
.fc-arrow.in { color: var(--van-green, #07c160); }
.fc-arrow.out { color: var(--van-danger-color, #ee0a24); }

.fc-body {
  flex: 1;
  min-width: 0;
}
.fc-cat {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text-primary);
  margin-bottom: 3px;
}
.fc-meta {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.fc-bank-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: var(--theme-bg-primary);
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 10px;
  color: var(--theme-text-secondary);
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
.fc-amount.in { color: var(--van-green, #07c160); }
.fc-amount.out { color: var(--van-danger-color, #ee0a24); }
.fc-time {
  font-size: 10px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
}
.fc-currency { font-size: 15px; font-weight: 500; margin-right: 1px; }

.fc-badge {
  position: absolute;
  top: -1px;
  right: 10px;
  font-size: 9px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 0 5px 0 5px;
}
.fc-badge-warn { background: var(--van-orange-bg, #fff7e6); color: var(--van-orange, #ff976a); }
.fc-badge-income { background: var(--van-green-bg, #e8f9ee); color: var(--van-green, #07c160); }

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
  background: rgba(var(--theme-primary-rgb), 0.04);
  border: 2px dashed var(--theme-primary);
}
/* 提现：绿 */
.pd-withdrawal {
  background: rgba(7,193,96,0.05);
  border: 2px dashed var(--van-green);
}
/* 冲正：灰 */
.pd-reversal {
  background: var(--theme-bg-tertiary);
  border: 2px dashed var(--theme-border);
  opacity: 0.85;
}

.tf-row { display: flex; align-items: center; }

/* 第一层：时间 + 标签 + 时间 */
.tf-time-row {
  justify-content: space-between;
}
.tf-t { font-size: 11px; color: var(--theme-text-tertiary); }
.tf-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-primary);
}
.pd-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--van-green);
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
.tf-amt.out { color: var(--van-danger-color, #ee0a24); }
.tf-amt.in { color: var(--van-green, #07c160); }
.tf-exchange-icon {
  font-size: 18px;
  color: var(--theme-primary);
  flex-shrink: 0;
}

/* 第三层：银行名 */
.tf-bank-row {
  justify-content: space-between;
}
.tf-bank-name {
  font-size: 11px;
  color: var(--theme-text-secondary);
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
  background: var(--theme-bg-secondary);
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
.tfd-dot.out { background: var(--van-danger-color, #ee0a24); }
.tfd-dot.in { background: var(--van-green, #07c160); }
.tfd-body {
  flex: 1;
  min-width: 0;
}
.tfd-cat { font-size: 13px; color: var(--theme-text-primary); font-weight: 500; }
.tfd-meta { font-size: 11px; color: var(--theme-text-tertiary); margin-top: 1px; }
.tfd-amt {
  font-size: 15px;
  font-weight: 600;
  font-family: "DIN Alternate", "SF Mono", monospace;
  margin-left: 8px;
  white-space: nowrap;
}
.tfd-amt.out { color: var(--van-danger-color, #ee0a24); }
.tfd-amt.in { color: var(--van-green, #07c160); }

/* ── 返回顶部 ── */
.back-top {
  position: fixed;
  right: 16px;
  bottom: 60px;
  width: 40px;
  height: 40px;
  background: var(--theme-bg-secondary);
  border-radius: 50%;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--theme-primary);
  z-index: 999;
}

</style>
