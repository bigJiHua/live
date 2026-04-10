<template>
  <div class="page-flow-list">
    <div class="filter-bar">
      <van-tabs v-model:active="filterType" shrink @change="onFilterChange">
        <van-tab title="全部" name="all" />
        <van-tab title="收入" name="income" />
        <van-tab title="支出" name="expense" />
      </van-tabs>
      <div class="date-filter" @click="showDatePicker = true">
        <span>{{ dateRangeText }}</span>
        <van-icon name="arrow-down" />
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
            <div
              v-for="item in group"
              :key="item.id"
              class="flow-item"
              @click="goDetail(item)"
            >
              <div class="item-left">
                <div class="category-icon">
                  <van-icon :name="getCategoryIcon(item.category_name)" />
                </div>
                <div class="item-info">
                  <div class="item-title">
                    {{ getCategoryName(item) }}
                  </div>
                  <div class="item-desc">
                    {{ item.pay_method || "-" }} ·
                    {{ formatTime(item.create_time) }}
                  </div>
                </div>
              </div>
              <div class="item-right">
                <div
                  class="amount"
                  :class="item.direction === 1 ? 'income' : 'expense'"
                >
                  <span v-if="isForeignCurrency(item)" class="currency-tag">
                    {{ getCurrencySymbol(item.currency) }}
                  </span>
                  {{ item.direction === 1 ? "+" : "-"
                  }}{{ formatAmount(item.amount) }}
                </div>
                <div
                  v-if="
                    item.card_id && !['xxxx', 'yyyy'].includes(item.card_id)
                  "
                  class="card-tag"
                >
                  {{ getCardName(item.card_id) }}
                </div>
              </div>
            </div>
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
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";
import { getAccountList, getMonthStats } from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";

dayjs.locale("zh-cn");
const router = useRouter();

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
    CNY: '¥',
    USD: '$',
    EUR: '€',
    HKD: 'HK$',
    JPY: '¥',
    GBP: '£',
    KRW: '₩',
    TWD: 'NT$'
  }
  return symbols[code] || code || ''
}

// 是否为外币
const isForeignCurrency = (item) => {
  return item.currency && item.currency !== 'CNY'
}

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
  if (item.category_id === 'CATEGORY_REPAY') {
    return '信用卡还款';
  }
  return item.category_name || '未知分类';
};

const getCardName = (id) => {
  const card = cardList.value.find((c) => c.id === id);
  return card ? card.alias || card.bank_name : "";
};

const groupedList = computed(() => {
  const groups = {};
  list.value.forEach((item) => {
    const date = item.trans_date;
    if (date) {
      if (!groups[date]) groups[date] = [];
      groups[date].push(item);
    }
  });
  return groups;
});

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

    if (page.value === 1) list.value = data;
    else list.value.push(...data);

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
  getCardList().then((res) => (cardList.value = res.data || []));
  loadSummary();
  loadData();
});

const goDetail = (item) => router.push(`/finance/flow/${item.id}`);
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
</style>
