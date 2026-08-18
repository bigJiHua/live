<template>
  <div class="page-installment">
    <div class="header-card">
      <div class="header-title">创建分期</div>
      <div class="header-sub">信用卡分期 · 默认当月，仅可选今年内月份</div>
    </div>

    <!-- 新建分期 -->
    <div class="section">
      <div class="section-title">新建分期</div>

      <app-field
        v-model="cardLabel"
        label="目标信用卡"
        readonly
        is-link
        placeholder="选择信用卡"
        @click="showCardPicker = true"
      />
      <app-field
        v-model="startMonthLabel"
        label="分期开始月份"
        readonly
        is-link
        placeholder="选择首期归属月份"
        @click="showStartPicker = true"
      />
      <div class="input-cell" @click="openKeyboard('amount')">
        <span class="input-label">分期总额</span>
        <span class="input-value">{{ formAmount || "0.00" }}</span>
        <van-icon name="arrow" class="input-arrow" />
      </div>
      <div class="input-cell" @click="openKeyboard('fee')">
        <span class="input-label">手续费/利息</span>
        <span class="input-value">{{ formFee || "0.0000" }}</span>
        <van-icon name="arrow" class="input-arrow" />
      </div>
      <app-field label="分期期数">
        <div class="period-btns">
          <span
            v-for="p in periodOptions"
            :key="p"
            class="period-btn"
            :class="{ active: formPeriods === p }"
            @click="formPeriods = p"
            >{{ p }}期</span
          >
        </div>
      </app-field>

      <!-- 精度分配选项（行内 label + segment-switch 切换，label 与按钮同行） -->
      <div class="opt-row">
        <span class="opt-label">本金取整</span>
        <div class="segment-switch" role="tablist">
          <button
            class="segment-item"
            :class="{ active: principalMode === 'floor' }"
            @click="principalMode = 'floor'"
          >
            截断
          </button>
          <button
            class="segment-item"
            :class="{ active: principalMode === 'round' }"
            @click="principalMode = 'round'"
          >
            四舍五入
          </button>
          <span class="segment-thumb" :class="principalMode" />
        </div>
      </div>
      <div class="opt-note">
        截断：200/12=16.66（舍）；四舍五入：200/12=16.67（入）
      </div>
      <div class="opt-row">
        <span class="opt-label">尾差位置</span>
        <div class="segment-switch" role="tablist">
          <button
            class="segment-item"
            :class="{ active: tailMode === 'last' }"
            @click="tailMode = 'last'"
          >
            放在末期
          </button>
          <button
            class="segment-item"
            :class="{ active: tailMode === 'first' }"
            @click="tailMode = 'first'"
          >
            放在第一期
          </button>
          <span class="segment-thumb" :class="tailMode === 'last' ? 'last' : 'first'" />
        </div>
      </div>
      <div class="opt-note">
        多出的零头放哪一期：末期（末月多还）或 第一期（首月多还）
      </div>

      <!-- 预览 -->
      <div class="preview-card" v-if="preview.periods > 0">
        <div class="preview-row">
          <span>每期金额</span>
          <span class="preview-amount">￥{{ preview.perPeriod }}</span>
        </div>
        <div class="preview-row">
          <span>总还款额</span>
          <span>￥{{ preview.total }}</span>
        </div>
        <div class="preview-row">
          <span>账单日</span>
          <span>{{ selectedCard ? selectedCard.bill_day + "号" : "-" }}</span>
        </div>
        <div class="preview-row">
          <span>首期还款日</span>
          <span class="preview-date">{{ preview.firstDate }}</span>
        </div>

        <div class="adjust-tip">
          点击每期金额可手动微调（合计不可超过总还款额）
        </div>
        <div class="preview-list">
          <div
            v-for="(d, idx) in preview.dates"
            :key="idx"
            class="preview-item"
          >
            <span class="pi-idx">第{{ idx + 1 }}期</span>
            <span class="pi-date">{{ d }}</span>
            <span class="pi-amount editable" @click="openAdjust(idx)"
              >￥{{ adjustedList[idx]?.toFixed(2) ?? preview.perPeriod }}</span
            >
          </div>
        </div>

        <div class="adjust-summary" :class="{ exceed: exceedLimit }">
          <span>微调后合计</span>
          <span class="as-amount"
            >￥{{ adjustedTotal.toFixed(2) }} / ￥{{ preview.total }}</span
          >
        </div>
        <div class="adjust-warn" v-if="exceedLimit">
          ⚠ 微调后合计已超过分期总额+手续费，请调减
        </div>
      </div>

      <div class="form-actions">
        <app-button
          round
          block
          type="primary"
          :loading="submitting"
          :disabled="!formReady"
          @click="handleSubmit"
          >确认创建分期</app-button
        >
      </div>
    </div>

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model="keyboardValue"
      :show="showKeyboard"
      theme="custom"
      :extra-key="'.'"
      close-button-text="完成"
      :maxlength="keyboardField === 'fee' ? 13 : 12"
      @blur="closeKeyboard"
    />

    <!-- 信用卡选择 -->
    <app-popup v-model:show="showCardPicker" position="bottom" round>
      <van-picker
        title="选择信用卡"
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </app-popup>

    <!-- 分期开始月份 -->
    <app-popup v-model:show="showStartPicker" position="bottom" round class="start-popup">
      <van-picker
        v-model="startPickerSel"
        title="分期开始月份"
        :columns="startColumns"
        @confirm="onStartConfirm"
        @cancel="showStartPicker = false"
      />
    </app-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { showToast, showSuccessToast } from "vant";
import dayjs from "dayjs";
import { getCardList } from "@/utils/api/card";
import { createRecurring } from "@/utils/api/recurring";

const cards = ref([]);
const submitting = ref(false);
const router = useRouter();
const showCardPicker = ref(false);
const selectedCard = ref(null);

const cardLabel = computed(() => {
  if (!selectedCard.value) return "";
  const c = selectedCard.value;
  return `${c.alias || c.bank_name || ""} (尾号${c.last4_no || ""}) · 账单日${c.bill_day || "?"}号`;
});

const cardColumns = computed(() =>
  cards.value.map((c) => ({
    text: `${c.alias || c.bank_name} 尾号${c.last4_no} · 账单${c.bill_day || "?"}号`,
    value: c.id,
  })),
);

// 分期期数选项：三三三原则（3 的倍数），最大 60 期
const periodOptions = [3, 6, 9, 12, 18, 24, 30, 36, 48, 60];

const formAmount = ref("");
const formFee = ref("");
const formPeriods = ref(3);

// 分期开始月份：默认今年今月，仅限今年内选择（用于补登/测试）
const startMonth = ref(dayjs().date(1));
const showStartPicker = ref(false);
const curYear = dayjs().year();
const curMonth = dayjs().month() + 1;
// 年/月双列（van-picker 多列格式：二维数组 [[年...],[月...]]），年列仅今年
const startColumns = computed(() => {
  const years = [{ text: `${curYear}年`, value: curYear }];
  const months = [];
  for (let m = 1; m <= 12; m++) months.push({ text: `${m}月`, value: m });
  return [years, months];
});
// picker 默认选中今年今月
const startPickerSel = ref([curYear, curMonth]);

const startMonthLabel = computed(() => startMonth.value.format("YYYY年MM月"));

const onStartConfirm = () => {
  const [y, m] = startPickerSel.value;
  startMonth.value = dayjs(`${y}-${String(m).padStart(2, "0")}-01`);
  showStartPicker.value = false;
};

// 精度分配开关（默认：本金截断 + 尾差放末期）
const principalMode = ref("floor"); // 'floor' 截断 | 'round' 四舍五入
const tailMode = ref("last"); // 'last' 末期 | 'first' 第一期

// 逐期微调金额（默认跟随均分方案，可手动改）
const adjustedList = ref([]);
const editIdx = ref(-1);

// 数字键盘
const showKeyboard = ref(false);
const keyboardField = ref("amount"); // 'amount' | 'fee' | 'adjust'
const keyboardValue = ref("");

const openKeyboard = (field) => {
  keyboardField.value = field;
  keyboardValue.value = field === "amount" ? formAmount.value : formFee.value;
  showKeyboard.value = true;
};

const openAdjust = (idx) => {
  keyboardField.value = "adjust";
  editIdx.value = idx;
  keyboardValue.value = String(adjustedList.value[idx] ?? "");
  showKeyboard.value = true;
};

// 键盘输入实时同步到表单 ref
watch(keyboardValue, (val) => {
  const clean = String(val).replace(/[^\d.]/g, "");
  if (keyboardField.value === "amount") {
    formAmount.value = clean;
  } else if (keyboardField.value === "fee") {
    formFee.value = clean;
  }
  // adjust 分支在 closeKeyboard 落库，避免输入过程中实时覆盖
});

const closeKeyboard = () => {
  let v = keyboardValue.value.replace(/[^\d.]/g, "");
  const parts = v.split(".");
  if (keyboardField.value === "fee") {
    if (parts[0].length > 8) parts[0] = parts[0].slice(0, 8);
    if (parts[1]?.length > 4) parts[1] = parts[1].slice(0, 4);
    if (Number(v) > 99999999.9999) {
      parts[0] = "99999999";
      parts[1] = "9999";
    }
    v = parts.join(".");
    formFee.value = v;
  } else if (keyboardField.value === "adjust") {
    if (parts[0].length > 8) parts[0] = parts[0].slice(0, 8);
    if (parts[1]?.length > 2) parts[1] = parts[1].slice(0, 2);
    const num = Math.min(Number(v) || 0, 99999999.99);
    if (editIdx.value >= 0) {
      adjustedList.value[editIdx.value] = Math.round(num * 100) / 100;
    }
  } else {
    if (parts[0].length > 8) parts[0] = parts[0].slice(0, 8);
    if (parts[1]?.length > 2) parts[1] = parts[1].slice(0, 2);
    if (Number(v) > 99999999.99) {
      parts[0] = "99999999";
      parts[1] = "99";
    }
    v = parts.join(".");
    formAmount.value = v;
  }
  showKeyboard.value = false;
};

// 分期每期金额精度分配（本金 / 利息分离计算，与银行口径一致）：
//  - 本金(前 N-1 期) = floor 或 round(本金/期数, 2)，由 principalMode 控制
//  - 利息(前 N-1 期) = round(利息/期数, 2)
//  - 每期 = 当期本金 + 当期利息；零头尾差放末期或第一期，由 tailMode 控制
//  - 保证 Σ每期 精确 = 本金 + 利息
// 例：浦发 200 本金 + 8.62 利息 = 208.62，分 12 期（截断+末期）
//   → 前 11 期 16.66+0.72=17.38，尾期 16.74+0.70=17.44，合计精确 208.62。
function buildInstallmentSchedule(
  principal,
  fee,
  periods,
  principalMode = "floor",
  tailMode = "last",
) {
  const p = Number(principal) || 0;
  const f = Number(fee) || 0;
  const n = Math.max(1, Number(periods) || 1);
  const total = p + f;

  const principalPer =
    principalMode === "round"
      ? Math.round((p / n) * 100) / 100
      : Math.floor((p / n) * 100) / 100;
  const feePer = Math.round((f / n) * 100) / 100;
  const sumPer = Math.round((principalPer + feePer) * 100) / 100;

  // 尾差 = 总额 - 前 N-1 期标准之和，放到末期或第一期
  const tail = Math.round((total - sumPer * (n - 1)) * 100) / 100;

  const schedule = new Array(n).fill(sumPer);
  if (tailMode === "last") schedule[n - 1] = tail;
  else schedule[0] = tail;
  return { schedule, total, standard: schedule[0] };
}

const preview = computed(() => {
  const periods = Number(formPeriods.value || 0);
  const amount = Number(formAmount.value || 0);
  const fee = Number(formFee.value || 0);
  if (!periods || !amount)
    return {
      periods: 0,
      perPeriod: "0.00",
      total: "0.00",
      firstDate: "-",
      dates: [],
      schedule: [],
    };

  const { schedule, total, standard } = buildInstallmentSchedule(
    amount,
    fee,
    periods,
    principalMode.value,
    tailMode.value,
  );
  const billingDay = selectedCard.value?.bill_day || 1;

  // 首期 = 所选开始月份账单日+1（默认下月；可往前选用于补登）
  const firstMonth = startMonth.value;
  const safeDay = Math.min(billingDay + 1, firstMonth.daysInMonth());
  const firstDate = firstMonth.date(safeDay);

  const dates = [];
  for (let i = 0; i < periods; i++) {
    const d = firstDate.add(i, "month");
    const maxDay = d.daysInMonth();
    const day = Math.min(safeDay, maxDay);
    dates.push(d.date(day).format("YYYY-MM-DD"));
  }

  return {
    periods,
    perPeriod: standard.toFixed(2),
    total: total.toFixed(2),
    firstDate: firstDate.format("YYYY-MM-DD"),
    dates,
    schedule,
  };
});

// 均分方案变化时，重置逐期微调列表
watch(
  () => [preview.value.total, preview.value.schedule.join(",")],
  () => {
    adjustedList.value = [...preview.value.schedule];
  },
  { immediate: true },
);

const adjustedTotal = computed(() =>
  adjustedList.value.reduce((s, v) => s + (Number(v) || 0), 0),
);
// 浮点容差：微调后合计不得超过 总还款额（本金+手续费）+ 0.005
const exceedLimit = computed(() => {
  const cap = Number(preview.value.total) + 0.005;
  return adjustedTotal.value > cap;
});

// 表单是否就绪：卡片已选 + 总额>0 + 期数≥2 + 不超额。未就绪时提交按钮灰度禁用
const formReady = computed(() => {
  return (
    !!selectedCard.value &&
    Number(formAmount.value) > 0 &&
    Number(formPeriods.value) >= 2 &&
    !exceedLimit.value
  );
});

const loadData = async () => {
  try {
    const cardRes = await getCardList({ cardType: "credit" });
    cards.value = (cardRes.data || []).filter((c) => c.bill_day);
  } catch (e) {
    showToast(e.message || "加载失败");
  }
};

const onCardConfirm = ({ selectedOptions }) => {
  const cardId = selectedOptions?.[0]?.value;
  selectedCard.value = cards.value.find((c) => c.id === cardId) || null;
  showCardPicker.value = false;
};

const handleSubmit = async () => {
  if (!selectedCard.value) return showToast("请选择信用卡");
  if (!formAmount.value || Number(formAmount.value) <= 0)
    return showToast("请输入分期总额");
  if (!formPeriods.value || Number(formPeriods.value) < 2)
    return showToast("期数至少2期");
  if (exceedLimit.value) return showToast("微调后合计不能超过总还款额");

  const card = selectedCard.value;
  const billingDay = card.bill_day || 1;
  const schedule = adjustedList.value.length
    ? adjustedList.value
    : preview.value.schedule;
  const perPeriodAmount = schedule[0]; // 每期标准金额（列表/日历展示用）

  const accountInfo = JSON.stringify({
    type: "installment",
    card_id: card.id,
    card_name: `${card.alias || card.bank_name || ""}(尾号${card.last4_no || ""})`,
    billing_day: billingDay,
    start_month: startMonth.value.format("YYYY-MM"),
    original_amount: Number(formAmount.value),
    fee: Number(formFee.value || 0),
    total_periods: Number(formPeriods.value),
  });

  const monthRecords = {};
  preview.value.dates.forEach((d, i) => {
    const month = d.substring(0, 7);
    monthRecords[month] = {
      status: "pending",
      amount: schedule[i] ?? perPeriodAmount,
      remark: "",
      remind_time: null,
      done_time: null,
    };
  });

  const billingDayPlus1 = Math.min(billingDay + 1, 28);

  submitting.value = true;
  try {
    await createRecurring({
      name: `${card.alias || card.bank_name}分期`,
      amount: perPeriodAmount,
      category_id: "installment",
      account_id: accountInfo,
      cycle: "month",
      day_of_cycle: billingDayPlus1,
      month_records: monthRecords,
      repeat_count: Number(formPeriods.value),
      remark: `总额${formAmount.value} 手续费${formFee.value || 0} ${formPeriods.value}期 本金${principalMode.value === "floor" ? "截断" : "四舍五入"} 尾差${tailMode.value === "last" ? "末期" : "第一期"}`,
      is_active: 1,
    });
    showSuccessToast("分期创建成功");
    router.push("/credit-center/installment/list");
  } catch (e) {
    showToast(e.message || "创建失败");
  } finally {
    submitting.value = false;
  }
};

onMounted(() => loadData());
</script>

<style scoped>
.page-installment {
  min-height: 100vh;
  padding: 12px 16px 100px;
  background: var(--theme-bg-primary);
}
.header-card {
  background: linear-gradient(
    135deg,
    var(--van-danger-color, #ee0a24) 0%,
    var(--van-danger-grad, #d91a4a) 100%
  );
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  color: #fff;
}
.header-title {
  font-size: 20px;
  font-weight: 700;
}
.header-sub {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 6px;
}

.section {
  margin-bottom: 16px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-tertiary);
  margin-bottom: 8px;
  padding-left: 4px;
}

.input-cell {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: var(--theme-bg-secondary);
}
.input-label {
  flex-shrink: 0;
  width: 80px;
  font-size: 14px;
  color: var(--theme-text-primary);
}
.input-value {
  flex: 1;
  text-align: right;
  font-size: 16px;
  font-weight: 600;
  color: var(--van-danger-color, #ee0a24);
}
.input-arrow {
  margin-left: 8px;
  color: var(--theme-text-tertiary);
  font-size: 14px;
}

.period-btns {
    display: flex;
    gap: 2px;
    flex-wrap: wrap;
    justify-content: flex-start;
    padding: 4px 0;
    align-items: center;
}
.period-btn {
  padding: 4px 14px;
  border-radius: 14px;
  font-size: 13px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-secondary);
  transition: all 0.2s;
}
.period-btn.active {
  background: var(--van-danger-color, #ee0a24);
  color: #fff;
  font-weight: 600;
}

/* 精度选项行：label 与切换控件同行 */
.opt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}
.opt-label {
  font-size: 14px;
  color: var(--theme-text-primary);
  font-weight: 500;
}

/* segment-switch 分段切换（适配主题，浅色药丸 + 滑动高亮块） */
.segment-switch {
  position: relative;
  display: inline-flex;
  padding: 5px;
  background: var(--theme-bg-tertiary, #f0f1f3);
  border-radius: 30px;
}
.segment-item {
  position: relative;
  z-index: 2;
  flex: 0 0 100px;
  width: 100px;
  border: none;
  background: transparent;
  padding: 5px 0;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 24px;
  transition: color 0.28s ease;
}
.segment-item.active {
  color: #fff;
}
.segment-thumb {
  position: absolute;
  z-index: 1;
  top: 5px;
  bottom: 5px;
  left: 5px;
  width: 100px;
  border-radius: 24px;
  background: var(--theme-primary, #ee0a24);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}
.segment-thumb.floor,
.segment-thumb.last {
  transform: translateX(0);
}
.segment-thumb.round,
.segment-thumb.first {
  transform: translateX(100px);
}
.opt-note {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 4px;
  line-height: 1.4;
}

.preview-card {
  background: var(--theme-bg-secondary);
  border-radius: 8px;
  padding: 14px;
  margin-top: 12px;
}
.preview-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
  color: var(--theme-text-primary);
}
.preview-amount {
  color: var(--van-danger-color, #ee0a24);
  font-weight: 700;
}
.preview-date {
  color: var(--van-green, #07c160);
  font-weight: 600;
}
.adjust-tip {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin: 8px 0 2px;
}
.preview-list {
  margin-top: 6px;
  padding-top: 8px;
  max-height: 320px;
  overflow-y: auto;
}
.preview-item {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
  color: var(--theme-text-secondary);
}
.pi-idx {
  color: var(--theme-text-primary);
  font-weight: 500;
}
.pi-date {
  color: var(--van-green, #07c160);
}
.pi-amount {
  color: var(--van-danger-color, #ee0a24);
}
.pi-amount.editable {
  text-decoration: underline dotted;
  cursor: pointer;
}

.adjust-summary {
  display: flex;
  justify-content: space-between;
  padding: 8px 0 2px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--theme-text-primary);
  border-top: 1px dashed var(--theme-border, #eee);
}
.as-amount {
  color: var(--van-danger-color, #ee0a24);
  font-weight: 600;
}
.adjust-summary.exceed .as-amount {
  color: var(--van-danger-color, #ee0a24);
}
.adjust-warn {
  font-size: 12px;
  color: var(--van-danger-color, #ee0a24);
  margin-top: 4px;
}

.form-actions {
  margin-top: 16px;
}

/* 分期开始月份选择器 · 主题化适配 */
.start-popup :deep(.van-picker__toolbar),
.start-popup :deep(.van-datetime-picker__toolbar) {
  height: 44px;
}
.start-popup :deep(.van-picker__title) {
  color: var(--theme-text-primary);
  font-weight: 600;
}
.start-popup :deep(.van-picker__confirm) {
  color: var(--theme-primary, #ee0a24);
  font-weight: 600;
}
.start-popup :deep(.van-picker__cancel) {
  color: var(--theme-text-tertiary);
}
.start-popup :deep(.van-picker__frame),
.start-popup :deep(.van-datetime-picker__frame) {
  border-color: var(--theme-primary, #ee0a24);
}
.start-popup :deep(.van-picker-column__item--selected) {
  color: var(--theme-primary, #ee0a24);
  font-weight: 600;
}
</style>
