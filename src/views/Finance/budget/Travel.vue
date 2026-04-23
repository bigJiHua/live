<template>
  <div class="page-budget-travel">
    <div class="page-content">
      <div class="info-card">
        <div class="card-header">
          <van-icon name="location-o" class="header-icon" />
          <span class="header-title">行程概览</span>
        </div>
        <van-cell-group :border="false">
          <van-field
            v-model="formData.title"
            label="行程标题"
            placeholder="如：香港3天2晚"
            label-width="70px"
            required
          />
          <van-field
            v-model="formData.route"
            label="路线规划"
            placeholder="深圳 → 香港"
            label-width="70px"
            required
          />
          <div class="date-row">
            <van-field
              v-model="formData.start_date"
              label="出发日期"
              type="date"
              class="flex-1"
            />
            <van-field
              v-model="formData.end_date"
              label="返程日期"
              type="date"
              class="flex-1"
            />
          </div>
          <van-field
            v-model="formData.budget_amount"
            label="总预算"
            type="number"
            placeholder="0.00"
            label-width="70px"
            required
          >
            <template #extra><span class="unit-text">CNY</span></template>
          </van-field>
          <van-field
            v-model="formData.cycle"
            label="预算周期"
            readonly
            placeholder="请选择"
            @click="showCyclePicker = true"
          >
            <template #extra><van-icon name="arrow-down" /></template>
          </van-field>
        </van-cell-group>

        <div class="exchange-rates-section">
          <div class="sub-title">常用汇率 (1外币 = ? CNY)</div>
          <div class="rate-grid">
            <div
              v-for="rate in exchangeRates"
              :key="rate.currency"
              class="rate-tag"
            >
              <span class="currency-name">{{ rate.currency }}</span>
              <input
                v-model="rate.value"
                type="number"
                class="rate-mini-input"
                @input="refreshAllCNY"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="summary-card">
        <div class="summary-main">
          <div class="summary-item">
            <span class="label">总预算</span>
            <span class="value">¥{{ formatAmount(totalBudget) }}</span>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <span class="label">已规划支出</span>
            <span class="value danger"
              >¥{{ formatAmount(totalCNYExpense) }}</span
            >
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <span class="label">剩余结余</span>
            <span class="value" :class="balance >= 0 ? 'success' : 'danger'">
              ¥{{ formatAmount(balance) }}
            </span>
          </div>
        </div>

        <div class="currency-tags" v-if="getTotalCurrencyDetail().length">
          <div
            v-for="(item, idx) in getTotalCurrencyDetail()"
            :key="idx"
            class="c-tag"
          >
            {{ item.amount }} {{ item.currency }}
          </div>
        </div>

        <div class="progress-container">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: progressPercent + '%' }"
            ></div>
          </div>
          <span class="progress-text">预算进度 {{ progressPercent }}%</span>
        </div>
      </div>

      <div class="days-container">
        <div class="section-top">
          <span class="title">消费明细清单</span>
          <van-button
            size="small"
            type="primary"
            round
            icon="plus"
            @click="addDay"
            >增加天数</van-button
          >
        </div>

        <div
          v-for="(day, dayIndex) in days"
          :key="dayIndex"
          class="day-block"
        >
          <div class="day-header">
            <div class="day-info" @click="editDayDate(dayIndex)">
              <van-icon name="calendar-o" />
              <span class="date-text">{{ day.date || "未设置日期" }}</span>
              <van-icon name="arrow" size="12" color="#969799" />
            </div>
            <van-icon
              name="delete-o"
              class="delete-day"
              @click="removeDay(dayIndex)"
            />
          </div>

          <div class="expense-items">
            <div
              v-for="(item, itemIndex) in day.items"
              :key="itemIndex"
              class="item-row"
            >
              <div class="item-main">
                <div class="line-one">
                  <van-tag
                    :type="getTypeColor(item.type)"
                    size="medium"
                    round
                    @click="showTypePicker(dayIndex, itemIndex)"
                  >
                    {{ item.type || "类型" }} <van-icon name="arrow-down" />
                  </van-tag>
                  <input
                    v-model="item.description"
                    placeholder="备注(如：晚餐)"
                    class="desc-input"
                  />
                  <van-icon
                    name="clear"
                    color="#ebedf0"
                    @click="removeItem(dayIndex, itemIndex)"
                  />
                </div>
                <div class="line-two">
                  <div class="amount-box">
                    <input
                      v-model="item.amount"
                      type="number"
                      placeholder="0.00"
                      class="amount-input"
                      @input="calcItemCNY(item)"
                    />
                    <div
                      class="currency-unit"
                      @click="showCurrencyPicker(dayIndex, itemIndex)"
                    >
                      {{ item.currency }} <van-icon name="arrow-down" />
                    </div>
                  </div>
                  <div class="cny-convert" v-if="item.currency !== 'CNY'">
                    ≈ ¥{{ formatAmount(item.cny_amount || 0) }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="add-item-bar" @click="addItem(dayIndex)">
            <van-icon name="add-o" /> 记一笔
          </div>

          <div class="day-summary">
            <span class="label">当日小计:</span>
            <span class="val">¥{{ formatAmount(dayTotal(day)) }}</span>
          </div>
        </div>

        <van-empty
          v-if="days.length === 0"
          description="暂无行程规划"
        />
      </div>

      <div class="remark-card">
        <van-field
          v-model="formData.notes"
          type="textarea"
          placeholder="有什么需要特别注意的？（例如：签证准备、小费习惯等）"
          rows="2"
          autosize
          label="备忘录"
        />
      </div>

      <div class="bottom-action">
        <van-button
          block
          round
          type="primary"
          size="large"
          :loading="saving"
          @click="submit"
        >
          {{ isEdit ? "保存更新" : "立即创建预算" }}
        </van-button>
      </div>
    </div>

    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        title="选择日期"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showTypePopup" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="typeColumns"
        @confirm="onTypeConfirm"
        @cancel="showTypePopup = false"
      />
    </van-popup>
    <van-popup v-model:show="showCurrencyPopup" position="bottom" round>
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPopup = false"
      />
    </van-popup>
    <van-popup v-model:show="showCyclePicker" position="bottom" round>
      <van-picker
        title="选择周期"
        :columns="cycleColumns"
        @confirm="onCycleConfirm"
        @cancel="showCyclePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showToast, showSuccessToast } from "vant";
import { createBudget, updateBudget, getBudget } from "@/utils/api/budget";

const router = useRouter();
const route = useRoute();
const isEdit = computed(() => !!route.params.id);

const formRef = ref(null);
const saving = ref(false);
const showDatePicker = ref(false);
const showTypePopup = ref(false);
const showCurrencyPopup = ref(false);
const showCyclePicker = ref(false);
const tempDayIndex = ref(0);
const tempItemIndex = ref(0);

const typeColumns = [
  { text: "行", value: "行" },
  { text: "吃", value: "吃" },
  { text: "喝", value: "喝" },
  { text: "买", value: "买" },
  { text: "住", value: "住" },
  { text: "玩", value: "玩" },
  { text: "其他", value: "其他" },
];

const currencyColumns = [
  { text: "人民币", value: "CNY" },
  { text: "港币", value: "HKD" },
  { text: "美元", value: "USD" },
  { text: "欧元", value: "EUR" },
  { text: "英镑", value: "GBP" },
  { text: "日元", value: "JPY" },
];

const cycleColumns = [
  { text: '月', value: '月' },
  { text: '季', value: '季' },
  { text: '年', value: '年' },
]

// 默认汇率配置
const defaultExchangeRates = [
  { currency: "HKD", value: "0.92" },
  { currency: "USD", value: "7.25" },
  { currency: "EUR", value: "7.80" },
  { currency: "GBP", value: "9.00" },
  { currency: "JPY", value: "0.048" },
];

const formData = ref({
  title: "",
  route: "",
  start_date: "",
  end_date: "",
  budget_amount: "",
  cycle: "",
  plan_date: "",
  notes: "",
  budget_details: {
    days: [],
    exchange_rates: [...defaultExchangeRates],
  },
});

// 获取汇率
const getRate = (currency) => {
  const rates = formData.value.budget_details?.exchange_rates || [];
  const rateItem = rates.find((r) => r.currency === currency);
  return rateItem ? parseFloat(rateItem.value) || 0 : 0;
};

const getTypeColor = (type) => {
  const colors = {
    行: "primary",
    吃: "danger",
    喝: "success",
    买: "warning",
    住: "primary",
    玩: "purple",
  };
  return colors[type] || "default";
};

const calcItemCNY = (item) => {
  const amount = parseFloat(item.amount) || 0;
  if (item.currency === "CNY") {
    item.cny_amount = amount;
  } else {
    item.cny_amount = amount * getRate(item.currency);
  }
};

const refreshAllCNY = () => {
  const days = formData.value.budget_details?.days || [];
  days.forEach((day) => {
    (day.items || []).forEach((item) => calcItemCNY(item));
  });
};

// 快捷访问 days
const days = computed(() => formData.value.budget_details?.days || []);
const exchangeRates = computed(() => formData.value.budget_details?.exchange_rates || []);

const dayTotal = (day) =>
  (day.items || []).reduce((sum, item) => sum + (item.cny_amount || 0), 0);

const getTotalCurrencyDetail = () => {
  const map = {};
  days.value.forEach((day) => {
    (day.items || []).forEach((item) => {
      if (!item.amount) return;
      map[item.currency] = (map[item.currency] || 0) + parseFloat(item.amount);
    });
  });
  return Object.keys(map).map((k) => ({
    currency: k,
    amount: map[k].toFixed(2),
  }));
};

const totalCNYExpense = computed(() =>
  days.value.reduce((sum, day) => sum + dayTotal(day), 0)
);
const totalBudget = computed(
  () => parseFloat(formData.value.budget_amount) || 0
);
const balance = computed(() => totalBudget.value - totalCNYExpense.value);
const progressPercent = computed(() => {
  if (totalBudget.value === 0) return 0;
  return Math.min(
    100,
    Math.round((totalCNYExpense.value / totalBudget.value) * 100)
  );
});

const formatAmount = (val) =>
  Number(val).toFixed(2);

const addDay = () => {
  tempDayIndex.value = days.value.length;
  showDatePicker.value = true;
};

const removeDay = (index) => formData.value.budget_details.days.splice(index, 1);
const editDayDate = (index) => {
  tempDayIndex.value = index;
  showDatePicker.value = true;
};

const onDateConfirm = ({ selectedValues }) => {
  const dateStr = selectedValues.join("-");
  const targetDays = formData.value.budget_details.days;
  if (targetDays[tempDayIndex.value]) {
    targetDays[tempDayIndex.value].date = dateStr;
  } else {
    targetDays.push({ date: dateStr, items: [] });
  }
  showDatePicker.value = false;
};

const addItem = (dayIndex) => {
  formData.value.budget_details.days[dayIndex].items.push({
    type: "吃",
    description: "",
    amount: "",
    currency: "CNY",
    cny_amount: 0,
  });
};

const removeItem = (dayIndex, itemIndex) =>
  formData.value.budget_details.days[dayIndex].items.splice(itemIndex, 1);

const showTypePicker = (dIdx, iIdx) => {
  tempDayIndex.value = dIdx;
  tempItemIndex.value = iIdx;
  showTypePopup.value = true;
};

const onTypeConfirm = ({ selectedOptions }) => {
  formData.value.budget_details.days[tempDayIndex.value].items[tempItemIndex.value].type =
    selectedOptions[0].value;
  showTypePopup.value = false;
};

const showCurrencyPicker = (dIdx, iIdx) => {
  tempDayIndex.value = dIdx;
  tempItemIndex.value = iIdx;
  showCurrencyPopup.value = true;
};

const onCurrencyConfirm = ({ selectedOptions }) => {
  const item =
    formData.value.budget_details.days[tempDayIndex.value].items[tempItemIndex.value];
  item.currency = selectedOptions[0].value;
  calcItemCNY(item);
  showCurrencyPopup.value = false;
};

const onCycleConfirm = ({ selectedOptions }) => {
  formData.value.cycle = selectedOptions[0].value;
  showCyclePicker.value = false;
};

const loadData = async () => {
  if (!route.params.id) return;
  try {
    const res = await getBudget(route.params.id);
    const data = res.data;
    formData.value = {
      title: data.title || "",
      route: data.route || "",
      start_date: data.start_date || "",
      end_date: data.end_date || "",
      budget_amount: data.budget_amount || "",
      notes: data.notes || "",
      budget_details: data.budget_details || {
        days: [],
        exchange_rates: [...defaultExchangeRates],
      },
    };
    refreshAllCNY();
  } catch (e) {
    showToast("加载失败");
  }
};

const submit = async () => {
  if (!formData.value.title || !formData.value.budget_amount)
    return showToast("请完善基本信息");
  saving.value = true;
  try {
    const payload = {
      title: formData.value.title,
      route: formData.value.route,
      budget_type: "行",
      budget_amount: parseFloat(formData.value.budget_amount),
      cycle: formData.value.cycle || "月",
      plan_date: formData.value.plan_date || formData.value.start_date,
      notes: formData.value.notes,
      budget_details: formData.value.budget_details,
      total_expense: parseFloat(totalCNYExpense.value.toFixed(2)),
    };
    isEdit.value
      ? await updateBudget(route.params.id, payload)
      : await createBudget(payload);
    showSuccessToast("保存成功");
    router.replace("/finance/budget");
  } catch (e) {
    showToast(e.message || "保存失败");
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  if (isEdit.value) loadData();
});
</script>

<style scoped>
.page-budget-travel {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}
.page-content {
  padding: 12px;
}

/* 基础信息卡片 */
.info-card {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}
.header-icon {
  color: #1989fa;
  font-size: 18px;
}
.header-title {
  font-weight: 600;
  font-size: 15px;
}
.date-row {
  display: flex;
  border-top: 1px solid #f2f3f5;
}
.flex-1 {
  flex: 1;
}
.unit-text {
  color: #969799;
  font-size: 12px;
}

.exchange-rates-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebedf0;
}
.sub-title {
  font-size: 12px;
  color: #969799;
  margin-bottom: 8px;
}
.rate-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rate-tag {
  display: flex;
  align-items: center;
  background: #f2f3f5;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.currency-name {
  color: #646566;
  margin-right: 4px;
  border-right: 1px solid #ddd;
  padding-right: 4px;
}
.rate-mini-input {
  width: 45px;
  border: none;
  background: transparent;
  color: #1989fa;
  font-weight: bold;
  text-align: center;
}

/* 汇总卡片 */
.summary-card {
  background: linear-gradient(135deg, #1989fa, #0570db);
  border-radius: 12px;
  padding: 16px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.2);
  margin-bottom: 16px;
}
.summary-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}
.summary-item .label {
  font-size: 11px;
  opacity: 0.8;
  margin-bottom: 4px;
}
.summary-item .value {
  font-size: 15px;
  font-weight: bold;
}
.summary-item .value.danger {
  color: #ffe58f;
}
.summary-item .value.success {
  color: #b7eb8f;
}
.summary-divider {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.2);
}
.currency-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.c-tag {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.15);
  padding: 2px 6px;
  border-radius: 10px;
}
.progress-container {
  margin-top: 8px;
}
.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}
.progress-fill {
  height: 100%;
  background: #fff;
  transition: width 0.3s;
}
.progress-text {
  font-size: 10px;
  opacity: 0.8;
}

/* 每日明细 */
.section-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-top .title {
  font-weight: 600;
  color: #323233;
}
.day-block {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
}
.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  border-bottom: 1px solid #f7f8fa;
  padding-bottom: 8px;
}
.day-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #1989fa;
  font-weight: 600;
}
.delete-day {
  color: #ee0a24;
  font-size: 16px;
}

/* 消费项重构 */
.item-row {
  background: #fcfcfc;
  border: 1px solid #f2f3f5;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
}
.line-one {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.desc-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
}
.line-two {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.amount-box {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #ebedf0;
  border-radius: 4px;
  overflow: hidden;
}
.amount-input {
  width: 80px;
  border: none;
  padding: 4px 8px;
  font-weight: bold;
  color: #ee0a24;
}
.currency-unit {
  font-size: 12px;
  padding: 4px 8px;
  background: #f7f8fa;
  color: #1989fa;
  border-left: 1px solid #ebedf0;
}
.cny-convert {
  font-size: 12px;
  color: #969799;
  font-style: italic;
}

.add-item-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px;
  color: #1989fa;
  border: 1px dashed #1989fa;
  border-radius: 8px;
  margin: 8px 0;
  font-size: 13px;
}
.day-summary {
  text-align: right;
  font-size: 13px;
  border-top: 1px solid #f7f8fa;
  padding-top: 8px;
}
.day-summary .label {
  color: #969799;
}
.day-summary .val {
  color: #1989fa;
  font-weight: bold;
  margin-left: 4px;
}

.remark-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
}
.bottom-action {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  z-index: 10;
}
</style>
