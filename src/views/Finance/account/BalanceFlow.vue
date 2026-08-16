<template>
  <div class="page-balance-flow">
    <!-- 顶部：左侧 月份/余额/¥，右侧 支出/收入/净值（标签固定） -->
    <div class="overview-card">
      <div class="overview-top">
        <div class="overview-left">
          <div class="overview-month" @click="showMonthPicker = true">
            <span>{{ currentYear }}年{{ currentMonth }}月</span>
            <van-icon name="arrow-down" size="12" />
          </div>
          <div class="overview-label">余额</div>
          <div class="overview-balance">¥{{ formatCompact(totalBalance) }}</div>
        </div>
        <div class="overview-right">
          <div class="ov-row">
            <span class="ov-label">支出</span>
            <span class="ov-val expense">{{ sign(monthExpense) }}{{ formatCompact(Math.abs(monthExpense)) }}</span>
          </div>
          <div class="ov-row">
            <span class="ov-label">收入</span>
            <span class="ov-val income">+{{ formatCompact(monthIncome) }}</span>
          </div>
          <div class="ov-row">
            <span class="ov-label">净值</span>
            <span class="ov-val net" :class="monthBalance >= 0 ? 'income' : 'expense'">{{ sign(monthBalance) }}{{ formatCompact(Math.abs(monthBalance)) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 按日分组的父节点列表 -->
    <div class="day-list" v-if="dayGroups.length">
      <div v-for="group in dayGroups" :key="group.date" class="day-group" @click="toggleDay(group)">
        <!-- 日父节点 -->
        <div class="day-node">
          <div class="day-node-left">
            <div class="day-title-row">
              <span class="day-date">{{ formatDay(group.date) }}</span>
              <span class="day-week">{{ weekOf(group.date) }}</span>
            </div>
            <span class="day-balance" :class="group.balance >= 0 ? 'income' : 'expense'">
              {{ group.balance >= 0 ? '+' : '-' }}{{ formatAmount(Math.abs(group.balance)) }}
            </span>
          </div>
          <div class="day-node-right">
            <div class="day-stack">
              <span class="day-expense">-{{ formatAmount(group.expense) }}</span>
              <span class="day-income">+{{ formatAmount(group.income) }}</span>
            </div>
            <van-icon :name="expandedDates.has(group.date) ? 'arrow-up' : 'arrow-down'" class="day-arrow" />
          </div>
        </div>
        <!-- 展开：左右支出/收入对账（无支出/收入则单侧显示） -->
        <div v-show="expandedDates.has(group.date)" class="day-detail">
          <div class="flow-columns">
            <div v-if="group.expenseItems.length > 0" class="flow-col flow-col-expense">
              <div class="col-header expense-header">支出</div>
              <div class="col-items">
                <div v-for="node in group.expenseItems" :key="node.id" class="flow-item flow-item-col"
                  :class="{ repay: node.category_id === 'CATEGORY_REPAY' }" @click="goDetail(node)">
                  <div class="fi-line fi-line1">
                    <span>
                      {{ getCategoryName(node) }}
                      <span class="fi-card-type">{{ getCardTypeLabel(node) }}</span>
                    </span>
                    <span class="fi-time">{{ formatTime(node.create_time) }}</span>
                  </div>
                  <div class="fi-line fi-line2">
                    <span class="item-amount expense">-{{ formatAmount(node.amount) }}</span>
                    <span class="fi-bank">{{ getCompactBankLabel(node) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="col-divider" v-if="group.expenseItems.length > 0 && group.incomeItems.length > 0"></div>
            <div v-if="group.incomeItems.length > 0" class="flow-col flow-col-income">
              <div class="col-header income-header">收入</div>
              <div class="col-items">
                <div v-for="node in group.incomeItems" :key="node.id" class="flow-item flow-item-col"
                  :class="{ repay: node.category_id === 'CATEGORY_REPAY' }" @click="goDetail(node)">
                  <div class="fi-line fi-line1">
                    <span>
                      {{ getCategoryName(node) }}
                      <span class="fi-card-type">{{ getCardTypeLabel(node) }}</span>
                    </span>
                    <span class="fi-time">{{ formatTime(node.create_time) }}</span>
                  </div>
                  <div class="fi-line fi-line2">
                    <span class="item-amount income">+{{ formatAmount(node.amount) }}</span>
                    <span class="fi-bank">{{ getCompactBankLabel(node) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && !dayGroups.length" description="本月暂无余额流水明细" />

    <!-- 月份选择 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        :columns="pickerColumns"
        :model-value="pickerSelectedValues"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
        :show-toolbar="true"
        title="选择月份"
      />
    </van-popup>

    <van-overlay :show="loading">
      <div class="flex-center"><van-loading /></div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showToast } from "vant";
import dayjs from "dayjs";
import { getAccountListByCard, getBalanceList } from "@/utils/api/account";
import ENV from "@/utils/env";

defineOptions({ name: "BalanceFlow" });

const BASE_URL = ENV.FILE_BASE_URL;
const route = useRoute();
const router = useRouter();

// 余额账户 card_id（与 Structure.vue 虚拟账户配置一致）
const BALANCE_CARD_ID = "yyyy";

const now = dayjs();
const currentYear = ref(now.year());
const currentMonth = ref(now.month() + 1);
const showMonthPicker = ref(false);
const loading = ref(false);
const items = ref([]);
const totalBalance = ref(0); // 余额账户当前总余额
// 展开状态独立管理（避免 dayGroups 是 computed 导致 expanded 修改不生效）
const expandedDates = ref(new Set());

// 月份选择
const pickerColumns = computed(() => {
  const years = [];
  const months = [];
  const cy = now.year();
  for (let y = cy - 5; y <= cy + 5; y++) years.push({ text: `${y}年`, value: `${y}年` });
  for (let m = 1; m <= 12; m++) months.push({ text: `${m}月`, value: `${m}月` });
  return [years, months];
});
const pickerSelectedValues = ref([`${now.year()}年`, `${now.month() + 1}月`]);

const onPickerConfirm = ({ selectedOptions }) => {
  const yText = selectedOptions[0].text;
  const mText = selectedOptions[1].text;
  currentYear.value = parseInt(yText);
  currentMonth.value = parseInt(mText);
  pickerSelectedValues.value = [yText, mText];
  showMonthPicker.value = false;
  loadData();
};

// 数据加载：按余额账户 + 目标月
const loadData = async () => {
  loading.value = true;
  try {
    const startDate = dayjs()
      .year(currentYear.value)
      .month(currentMonth.value - 1)
      .date(1)
      .format("YYYY-MM-DD");
    const endDate = dayjs()
      .year(currentYear.value)
      .month(currentMonth.value - 1)
      .date(1)
      .endOf("month")
      .format("YYYY-MM-DD");
    const [flowRes, balRes] = await Promise.all([
      getAccountListByCard({
        cardId: BALANCE_CARD_ID,
        startDate,
        endDate,
        limit: 10000,
      }),
      getBalanceList().catch(() => ({ data: [] })),
    ]);
    items.value = flowRes.data?.list || flowRes.data || [];
    const balList = balRes.data || [];
    const balAccount = balList.find((a) => a.card_id === BALANCE_CARD_ID || a.cardId === BALANCE_CARD_ID);
    totalBalance.value = Number(balAccount ? balAccount.balance : 0) || 0;
    // 底部默认展开所有日期
    expandedDates.value = new Set(items.value.map((i) => (i.trans_date || i.transDate || "").slice(0, 10)).filter(Boolean));
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 按日分组（expanded 状态不放进 computed，改由 expandedDates 单独管理）
const dayGroups = computed(() => {
  const map = new Map();
  items.value.forEach((item) => {
    const date = (item.trans_date || item.transDate || "").slice(0, 10);
    if (!date) return;
    if (!map.has(date)) {
      map.set(date, { date, income: 0, expense: 0, incomeItems: [], expenseItems: [] });
    }
    const g = map.get(date);
    if (isIncome(item)) {
      g.income += Number(item.amount) || 0;
      g.incomeItems.push(item);
    } else {
      g.expense += Number(item.amount) || 0;
      g.expenseItems.push(item);
    }
  });
  return Array.from(map.values())
    .map((g) => ({ ...g, balance: g.income - g.expense }))
    .sort((a, b) => b.date.localeCompare(a.date));
});

const monthIncome = computed(() => dayGroups.value.reduce((s, g) => s + g.income, 0));
const monthExpense = computed(() => dayGroups.value.reduce((s, g) => s + g.expense, 0));
const monthBalance = computed(() => monthIncome.value - monthExpense.value);

// 工具函数
const isIncome = (item) => Number(item.direction) === 1;
const formatAmount = (val) => {
  const v = Number(val) || 0;
  return v.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};
// 数字超限缩略：≥1万 → X.XX万；≥1亿 → X.XX亿
const formatCompact = (val) => {
  const v = Number(val) || 0;
  const abs = Math.abs(v);
  if (abs >= 100000000) return (v / 100000000).toFixed(2) + "亿";
  if (abs >= 10000) return (v / 10000).toFixed(2) + "万";
  return formatAmount(v);
};
// 符号前缀：负 → -，正/零 → +
const sign = (val) => (Number(val) >= 0 ? "+" : "-");
const formatTime = (ts) => (ts ? dayjs(Number(ts)).format("HH:mm") : "");
const formatDay = (date) => {
  const d = dayjs(date);
  return `${d.month() + 1}月${d.date()}日`;
};
const weekOf = (date) => {
  const names = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
  return names[dayjs(date).day()];
};
const getCategoryName = (item) => {
  if (item.category_id === "CATEGORY_REPAY") return "信用卡还款";
  return item.category_name || item.categoryName || item.pay_type || item.payType || "未分类";
};
const getCardTypeLabel = (item) => {
  const t = item.account_type || item.accountType;
  if (t === "credit") return "信用卡";
  return "";
};
const getCompactBankLabel = (item) => {
  // 余额账户自身不显示卡名；若是跨卡流水，显示对方卡尾号
  const cardId = item.card_id || item.cardId;
  if (cardId === BALANCE_CARD_ID || !cardId) return "余额";
  return `****${String(cardId).slice(-4)}`;
};
const toggleDay = (group) => {
  const next = new Set(expandedDates.value);
  if (next.has(group.date)) next.delete(group.date);
  else next.add(group.date);
  expandedDates.value = next;
};
const goDetail = (item) => {
  const id = item.id || item.account_id;
  if (id) router.push(`/finance/flow/${id}`);
  else showToast("无详情");
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.page-balance-flow {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 20px;
}
.overview-card {
  margin: 12px 16px 4px;
  padding: 14px 16px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.overview-top {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
}
.overview-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
}
.overview-month {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  cursor: pointer;
}
.overview-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}
.overview-balance {
  font-size: 22px;
  font-weight: 700;
  font-family: "DIN Alternate", -apple-system, sans-serif;
  line-height: 1;
  color: var(--theme-text-primary);
}
.overview-right {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}
.ov-row { display: flex; align-items: center; gap: 8px; }
/* 标签固定宽度，数值不挤动标签位置 */
.ov-label { font-size: 12px; color: var(--theme-text-tertiary); flex: 0 0 32px; text-align: left; }
.ov-val { font-size: 14px; font-weight: 600; font-family: "DIN Alternate", -apple-system, sans-serif; text-align: right; }
.ov-val.income { color: var(--money-income); }
.ov-val.expense { color: var(--money-expense); }
.ov-val.net { color: var(--theme-text-primary); }
.ov-val.net.income { color: var(--money-income); }
.ov-val.net.expense { color: var(--money-expense); }

.day-list { padding: 0 16px; }
.day-group {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.day-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  cursor: pointer;
}
.day-node:active { background: var(--theme-bg-primary); }
.day-node-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}
.day-title-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.day-date { font-size: 15px; font-weight: 600; color: var(--theme-text-primary); }
.day-week { font-size: 12px; color: var(--theme-text-tertiary); }
.day-node-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.day-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.day-expense { color: var(--money-expense); font-size: 13px; }
.day-income { color: var(--money-income); font-size: 13px; }
.day-balance { font-size: 13px; font-weight: 600; font-family: "DIN Alternate", sans-serif; }
.day-balance.income { color: var(--money-income); }
.day-balance.expense { color: var(--money-expense); }
.day-arrow { color: var(--theme-text-tertiary); }

/* 左右对账（仿 flow/calendar day-detail） */
.day-detail { border-top: 1px solid var(--theme-border); }
.flow-columns {
  display: flex;
  align-items: stretch;
  padding: 8px 0;
}
.flow-col { flex: 1; padding: 0 10px; min-width: 0; }
.col-header { font-size: 12px; font-weight: 600; margin-bottom: 8px; }
.expense-header { color: var(--money-expense); }
.income-header { color: var(--money-income); }
.col-divider { width: 1px; background: var(--theme-border); margin: 0 2px; }
.col-empty { text-align: center; color: var(--theme-text-tertiary); font-size: 12px; padding: 12px 0; }
.flow-item {
  background: var(--theme-bg-primary);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 6px;
  cursor: pointer;
}
.flow-item:active { opacity: 0.7; }
.flow-item.repay { background: rgba(255, 151, 106, 0.08); }
.fi-line { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.fi-line1 { font-size: 13px; color: var(--theme-text-primary); margin-bottom: 3px; }
.fi-line2 { font-size: 13px; }
.fi-time { font-size: 11px; color: var(--theme-text-tertiary); }
.fi-card-type { font-size: 11px; color: var(--theme-primary); }
.fi-bank { font-size: 11px; color: var(--theme-text-tertiary); }
.item-amount { font-weight: 600; font-family: "DIN Alternate", sans-serif; }
.item-amount.expense { color: var(--money-expense); }
.item-amount.income { color: var(--money-income); }

.flex-center { display: flex; height: 100%; align-items: center; justify-content: center; }
</style>
