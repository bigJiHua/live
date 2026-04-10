<template>
  <div class="page-repay-add">
    <van-form @submit="onSubmit" ref="formRef">
      <!-- 关联信息（只读展示） -->
      <div class="form-section">
        <div class="section-title">关联信息</div>
        <van-cell-group inset>
          <van-field
            v-model="selectedCardName"
            label="信用卡"
            readonly
            class="readonly-field"
          />
          <van-field
            v-model="selectedBillName"
            label="账单"
            readonly
            class="readonly-field"
          />
        </van-cell-group>
      </div>

      <!-- 还款信息 -->
      <div class="form-section">
        <div class="section-title">还款信息</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.repayAmount"
            name="repayAmount"
            label="还款金额"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('repayAmount')"
            :rules="[{ required: true, message: '请输入还款金额' }]"
          >
            <template #button>元</template>
          </van-field>
          <van-field
            name="repayMethod"
            label="还款方式"
            readonly
            clickable
            is-link
            :model-value="getRepayMethodText(formData.repayMethod)"
            @click="showMethodPicker = true"
            :rules="[{ required: true, message: '请选择还款方式' }]"
          />
          <!-- 还款银行卡选择（当选择bank_card时显示） -->
          <van-field
            v-if="formData.repayMethod === 'bank_card'"
            name="repayMethodCardId"
            label="还款银行卡"
            readonly
            clickable
            is-link
            :model-value="getDebitCardName()"
            placeholder="请选择还款银行卡"
            @click="showDebitCardPicker = true"
            :rules="[{ required: true, message: '请选择还款银行卡' }]"
          />
          <van-field
            v-model="formData.repayTime"
            name="repayTime"
            label="还款时间"
            placeholder="请选择"
            is-link
            readonly
            @click="showDatePicker = true"
            :rules="[{ required: true, message: '请选择还款时间' }]"
          />
        </van-cell-group>
      </div>

      <!-- 备注 -->
      <div class="form-section">
        <div class="section-title">备注</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.remark"
            name="remark"
            label="备注"
            type="textarea"
            rows="2"
            placeholder="可选填写备注信息"
          />
        </van-cell-group>
      </div>

      <div class="submit-btn-wrap">
        <van-button type="primary" block round native-type="submit" :loading="loading" :disabled="loading">
          立即还款
        </van-button>
      </div>
    </van-form>

    <!-- 还款方式选择 -->
    <van-popup v-model:show="showMethodPicker" position="bottom">
      <van-picker
        title="选择还款方式"
        :columns="methodColumns"
        @confirm="onMethodConfirm"
        @cancel="showMethodPicker = false"
      />
    </van-popup>

    <!-- 借记卡选择 -->
    <van-popup v-model:show="showDebitCardPicker" position="bottom">
      <van-picker
        title="选择还款银行卡"
        :columns="debitCardColumns"
        @confirm="onDebitCardConfirm"
        @cancel="showDebitCardPicker = false"
      />
    </van-popup>

    <!-- 日期选择 -->
    <van-popup v-model:show="showDatePicker" position="bottom">
      <van-date-picker
        v-model="currentDate"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 数字键盘 -->
    <van-number-keyboard
      :show="showKeyboard"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      @blur="showKeyboard = false"
      @input="onKeyboardInput"
      @delete="onKeyboardDelete"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { showToast, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { createRepay, getBillDetail, getBillList, getCardList } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const formRef = ref(null);
const loading = ref(false);
const billData = ref(null);
const selectedCardName = ref("");
const selectedBillName = ref("");
const showMethodPicker = ref(false);
const showDebitCardPicker = ref(false);
const showDatePicker = ref(false);
const showKeyboard = ref(false);

// 借记卡列表
const debitCardList = ref([]);

// 数字键盘控制
const currentField = ref("");
const keyboardMaxlength = ref(10);

// 日期
const minDate = new Date(2020, 0, 1);
const maxDate = new Date();
const currentDate = ref(["2026", "04", "09"]);

// 还款方式选项
const repayMethods = [
  { text: "现金还款", value: "cash" },
  { text: "借记卡还款", value: "bank_card" },
  { text: "余额还款", value: "balance" }
];

const methodColumns = repayMethods.map(m => ({ text: m.text, value: m.value }));

// 获取还款方式文本
const getRepayMethodText = (value) => {
  const method = repayMethods.find(m => m.value === value);
  return method ? method.text : "请选择";
};

// 获取借记卡名称
const getDebitCardName = () => {
  if (!formData.repayMethodCardId) return "";
  const card = debitCardList.value.find(c => c.id === formData.repayMethodCardId);
  if (!card) return "";
  return `借记卡 ****${card.last4_no || card.last4No || card.card_last4 || "****"}`;
};

// 借记卡列
const debitCardColumns = computed(() => {
  return debitCardList.value.map(card => ({
    text: `借记卡 ****${card.last4_no || card.last4No || card.card_last4 || "****"}`,
    value: card.id
  }));
});

// 字段配置
const fieldConfig = {
  repayAmount: { maxlength: 10 }
};

const formData = reactive({
  cardId: "",
  billId: "",
  repayAmount: "0", // 默认0
  repayMethod: "cash", // 默认现金
  repayMethodCardId: "",
  repayTime: "",
  billMonth: "",
  remark: ""
});

// 欠款金额
const oweAmount = ref(0);

// 打开数字键盘
const openKeyboard = (field) => {
  currentField.value = field;
  keyboardMaxlength.value = fieldConfig[field]?.maxlength || 10;
  showKeyboard.value = true;
};

// 数字键盘输入
const onKeyboardInput = (value) => {
  const field = currentField.value;
  if (!field) return;
  
  let current = formData[field];
  let newValue;
  
  if (value === '.') {
    // 小数点：只能有一个
    if (current.includes('.')) return;
    newValue = current + '.';
  } else {
    newValue = current + value;
    
    // 金额格式处理
    if (current.includes('.')) {
      const [, decimal] = current.split('.');
      // 小数点后最多2位
      if (decimal && decimal.length >= 2) return;
    }
    
    // 处理 0 开头的数字（如 01 -> 1）
    if (current === '0' && /^\d$/.test(value)) {
      newValue = value;
    }
  }
  
  if (newValue.length > keyboardMaxlength.value) return;
  formData[field] = newValue;
};

// 数字键盘删除
const onKeyboardDelete = () => {
  const field = currentField.value;
  if (!field) return;
  formData[field] = formData[field].slice(0, -1);
};

// 数字键盘关闭
const onKeyboardClose = () => {
  showKeyboard.value = false;
};

// 加载借记卡列表
const loadDebitCards = async () => {
  try {
    const res = await getCardList();
    const allCards = res.data || res || [];
    // 只获取借记卡
    debitCardList.value = allCards.filter(card => card.card_type !== 'credit');
  } catch (error) {
    debitCardList.value = [];
  }
};

// 加载账单数据
const loadBillData = async () => {
  try {
    const billIdParam = route.query.billId;

    // 直接通过billId获取账单详情
    if (billIdParam) {
      const res = await getBillDetail(billIdParam);
      const bill = res.data || res;
      if (bill) {
        billData.value = bill;
        formData.billId = bill.id;
        formData.cardId = bill.card_id; // 从账单数据获取卡片ID
        oweAmount.value = Number(bill.need_repay) || 0;
        formData.billMonth = bill.bill_month || "";
        // 显示卡片信息和欠款金额
        const cardName = bill.card_alias || "信用卡";
        const cardNo = bill.card_last4 ? `****${bill.card_last4}` : "";
        selectedCardName.value = cardNo ? `${cardName} ${cardNo}` : cardName;
        selectedBillName.value = `欠款 ¥${oweAmount.value.toFixed(2)}`;
      }
    }
  } catch (error) {
    console.error("加载账单失败", error);
  }
};

// 还款方式确认
const onMethodConfirm = ({ selectedOptions }) => {
  formData.repayMethod = selectedOptions[0].value;
  // 选择非银行卡时清空银行卡选择
  if (formData.repayMethod !== "bank_card") {
    formData.repayMethodCardId = "";
  }
  showMethodPicker.value = false;
};

// 借记卡确认
const onDebitCardConfirm = ({ selectedOptions }) => {
  formData.repayMethodCardId = selectedOptions[0].value;
  showDebitCardPicker.value = false;
};

// 日期确认
const onDateConfirm = ({ selectedValues }) => {
  formData.repayTime = selectedValues.join("-");
  showDatePicker.value = false;
};

// 提交
const onSubmit = async () => {
  try {
    // 验证欠款
    if (oweAmount.value <= 0) {
      return showToast("该账单无欠款，无需还款");
    }

    const repayAmount = Number(formData.repayAmount) || 0;
    if (repayAmount <= 0) {
      return showToast("请输入还款金额");
    }
    if (repayAmount > oweAmount.value) {
      return showToast(`还款金额不能超过欠款 ¥${oweAmount.value.toFixed(2)}`);
    }

    if (formData.repayMethod === "bank_card" && !formData.repayMethodCardId) {
      return showToast("请选择还款银行卡");
    }

    loading.value = true;
    showLoadingToast({ message: "保存中...", forbidClick: true });

    const submitData = {
      cardId: formData.cardId,
      billId: formData.billId,
      repayAmount: Number(formData.repayAmount) || 0,
      repayMethod: formData.repayMethod,
      repayTime: formData.repayTime,
    };

    // 银行卡还款需要传repayMethodCardId
    if (formData.repayMethod === "bank_card") {
      submitData.repayMethodCardId = formData.repayMethodCardId;
    }

    // 账单月份（可选）
    if (formData.billMonth) {
      submitData.billMonth = formData.billMonth;
    }

    // 备注（可选）
    if (formData.remark) {
      submitData.remark = formData.remark;
    }

    await createRepay(submitData);

    closeToast();
    showToast({ message: "添加成功", onClose: () => router.back() });
  } catch (error) {
    closeToast();
    showToast(error.message || "添加失败");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadBillData();
  loadDebitCards();
  // 初始化还款时间
  const now = new Date();
  currentDate.value = [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0")
  ];
  formData.repayTime = currentDate.value.join("-");
});
</script>

<style scoped>
.page-repay-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 16px 16px 8px;
}

.readonly-field {
  background: #f7f8fa;
}

.submit-btn-wrap {
  margin: 32px 16px;
}
</style>
