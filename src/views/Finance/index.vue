<template>
  <div class="page-finance">
    <div class="app-card finance-header">
      <div class="month-selector" @click="showMonthPicker = true">
        <span class="num-font">{{ currentYear }}</span
        >年<span class="num-font">{{ currentMonth }}</span
        >月
        <van-icon name="arrow-down" />
      </div>

      <van-row class="total-info">
        <van-col span="12" class="info-item">
          <div class="label">本月总支出</div>
          <div class="amount text-expense num-font">
            {{ formatAmount(stats.expense) }}
          </div>
        </van-col>
        <van-col span="12" class="info-item">
          <div class="label">本月总收入</div>
          <div class="amount text-income num-font">
            {{ formatAmount(stats.income) }}
          </div>
        </van-col>
      </van-row>

      <div class="header-footer">
        <div class="net-assets">
          <span class="label">结余：</span>
          <span class="val num-font">￥{{ formatAmount(stats.balance) }}</span>
        </div>
        <van-button
          plain
          size="small"
          type="primary"
          class="quick-add-btn"
          icon="plus"
          @click="$router.push('/finance/add')"
        >
          记一笔
        </van-button>
      </div>
    </div>

    <div class="app-card menu-grid-wrapper">
      <div class="grid-section-title">财务把控中心</div>
      <van-grid :column-num="3" :border="false" clickable>
        <van-grid-item @click="goFunction('assets')">
          <template #icon
            ><van-icon name="gem" class="grid-icon purple"
          /></template>
          <template #text><span class="grid-text">系统账户余额</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('flow')">
          <template #icon
            ><van-icon name="orders-o" class="grid-icon blue"
          /></template>
          <template #text><span class="grid-text">流水明细</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('assets-list')">
          <template #icon
            ><van-icon name="todo-list-o" class="grid-icon gray"
          /></template>
          <template #text><span class="grid-text">登记记录</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('credit')">
          <template #icon
            ><van-icon name="credit-pay" class="grid-icon orange"
          /></template>
          <template #text><span class="grid-text">信用卡专项</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('bill-list')">
          <template #icon
            ><van-icon name="todo-list-o" class="grid-icon red"
          /></template>
          <template #text><span class="grid-text">信用卡账单</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('repay-list')">
          <template #icon
            ><van-icon name="paid" class="grid-icon green"
          /></template>
          <template #text><span class="grid-text">还款记录</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('assets-reg')">
          <template #icon
            ><van-icon name="location-o" class="grid-icon teal"
          /></template>
          <template #text><span class="grid-text">资产结构登记</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('fixed-assets')">
          <template #icon
            ><van-icon name="shop-o" class="grid-icon teal"
          /></template>
          <template #text><span class="grid-text">固定资产</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('super-calc')" class="special-item">
          <template #icon
            ><van-icon name="points" class="grid-icon gold"
          /></template>
          <template #text
            ><span class="grid-text font-bold">超级计算</span></template
          >
        </van-grid-item>
        <van-grid-item @click="goFunction('budget')">
          <template #icon
            ><van-icon name="balance-list" class="grid-icon cyan"
          /></template>
          <template #text><span class="grid-text">预算表</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('report')">
          <template #icon
            ><van-icon name="chart-trending-o" class="grid-icon blue"
          /></template>
          <template #text><span class="grid-text">报表</span></template>
        </van-grid-item>
        <van-grid-item @click="goFunction('invest')">
          <template #icon
            ><van-icon name="gold-coin-o" class="grid-icon gold"
          /></template>
          <template #text><span class="grid-text">理财预期</span></template>
        </van-grid-item>
      </van-grid>
    </div>
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        title="选择月份"
        v-model="selectedValues"
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
import { showToast } from "vant";
import { getMonthStats } from "@/utils/api/account";

const router = useRouter();
const showMonthPicker = ref(false);

const now = new Date();
const currentYear = ref(now.getFullYear());
const currentMonth = ref(now.getMonth() + 1);
const selectedValues = ref([
  `${now.getFullYear()}年`,
  `${now.getMonth() + 1}月`,
]);

const stats = ref({ expense: 0, income: 0, balance: 0 });

const formatAmount = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  const num = Number(amount);
  // 超过1万显示为万
  if (num >= 10000) {
    return (num / 10000).toFixed(2) + "万";
  }
  return num.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const pickerColumns = computed(() => {
  const years = [];
  const startYear = 2020;
  const endYear = new Date().getFullYear() + 5;
  for (let i = startYear; i <= endYear; i++) {
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
  showMonthPicker.value = false;
  loadMonthStats();
};

const loadMonthStats = async () => {
  try {
    const res = await getMonthStats({
      year: currentYear.value,
      month: currentMonth.value,
    });

    if (res.data) {
      stats.value = {
        expense: res.data.expense || 0,
        income: res.data.income || 0,
        balance: res.data.balance || 0,
      };
    }
  } catch (e) {
    showToast("加载失败");
  }
};

const goFunction = (type) => {
  const routes = {
    flow: "/finance/flow",
    credit: "/credit-center",
    "bill-list": "/card/bill/list",
    "repay-list": "/card/repay/list",
    assets: "/finance/structure",
    "assets-reg": "/finance/assets/register",
    "assets-list": "/finance/assets/list",
    report: "/finance/report",
    budget: "/finance/budget",
    invest: "/finance/invest",
  };
  if (routes[type]) router.push(routes[type]);
};

onMounted(() => loadMonthStats());
</script>

<style scoped>
.page-finance {
  padding: 8px 16px;
}
.finance-header {
  background: white;
  padding: 20px;
  border-radius: 20px;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}
.month-selector {
  font-size: 14px;
  color: #646566;
  margin-bottom: 15px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.info-item .label {
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}
.info-item .amount {
  font-size: 22px;
  font-weight: bold;
}
.header-footer {
  margin-top: 18px;
  padding-top: 15px;
  border-top: 1px dashed #f2f3f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.net-assets .label {
  font-size: 12px;
  color: #969799;
}
.net-assets .val {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}
.quick-add-btn {
  padding: 0 15px;
  height: 32px;
  border-radius: 8px;
}
.menu-grid-wrapper {
  background: white;
  padding: 16px 8px;
  border-radius: 20px;
  margin-bottom: 16px;
}
.grid-section-title {
  font-size: 14px;
  font-weight: bold;
  padding-left: 10px;
  margin-bottom: 15px;
  color: #323233;
}
.grid-icon {
  font-size: 26px;
  margin-bottom: 8px;
}
.grid-text {
  font-size: 13px;
  color: #646566;
}
.font-bold {
  font-weight: bold;
  color: #323233;
}
.blue {
  color: #1989fa;
}
.green {
  color: #07c160;
}
.orange {
  color: #ff976a;
}
.red {
  color: #ee0a24;
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
.special-item {
  background: #fff9e6;
  border-radius: 12px;
}
.footer-tips {
  text-align: center;
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 30px;
  padding-bottom: 20px;
}
.text-income {
  color: #07c160;
}
.text-expense {
  color: #ee0a24;
}
</style>
