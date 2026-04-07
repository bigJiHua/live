<template>
  <div class="page-repay-add">


    <van-form @submit="onSubmit" ref="formRef">
      <!-- 卡片和账单选择 -->
      <div class="form-section">
        <div class="section-title">关联信息</div>
        <van-cell-group inset>
          <van-field
            v-model="selectedCardName"
            name="cardId"
            label="卡片"
            placeholder="请选择卡片"
            is-link
            readonly
            @click="showCardPicker = true"
            :rules="[{ required: true, message: '请选择卡片' }]"
          />
          <van-field
            v-model="selectedBillName"
            name="billId"
            label="账单"
            placeholder="可选"
            is-link
            readonly
            @click="showBillPicker = true"
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
            placeholder="请输入还款金额"
            type="number"
            :rules="[{ required: true, message: '请输入还款金额' }]"
          />
          <van-field
            name="repayMethod"
            label="还款方式"
            :rules="[{ required: true, message: '请选择还款方式' }]"
          >
            <template #input>
              <van-radio-group v-model="formData.repayMethod" direction="horizontal">
                <van-radio name="转账">转账</van-radio>
                <van-radio name="自动扣款">自动扣款</van-radio>
                <van-radio name="柜台还款">柜台</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field
            v-model="formData.repayTime"
            name="repayTime"
            label="还款时间"
            placeholder="请选择"
            is-link
            readonly
            @click="showDateTimePicker = true"
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
          保存还款记录
        </van-button>
      </div>
    </van-form>

    <!-- 卡片选择 -->
    <van-popup v-model:show="showCardPicker" position="bottom">
      <van-picker
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </van-popup>

    <!-- 账单选择 -->
    <van-popup v-model:show="showBillPicker" position="bottom">
      <van-picker
        :columns="billColumns"
        @confirm="onBillConfirm"
        @cancel="showBillPicker = false"
      />
    </van-popup>

    <!-- 日期时间选择 -->
    <van-popup v-model:show="showDateTimePicker" position="bottom">
      <van-datetime-picker
        v-model="currentDate"
        type="datetime"
        title="选择日期时间"
        @confirm="onDateTimeConfirm"
        @cancel="showDateTimePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { showToast, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { createRepay, getCardList, getBillList } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const formRef = ref(null);
const loading = ref(false);
const cardList = ref([]);
const billList = ref([]);
const selectedCardName = ref("");
const selectedBillName = ref("");
const showCardPicker = ref(false);
const showBillPicker = ref(false);
const showDateTimePicker = ref(false);
const currentDate = ref(new Date());

const formData = reactive({
  cardId: "",
  billId: "",
  repayAmount: "",
  repayMethod: "转账",
  repayTime: "",
  remark: "",
});

// 卡片选择列
const cardColumns = computed(() => {
  return cardList.value.map(card => ({
    text: `${card.alias || card.bank_name || '卡片'} **** ${card.last4_no || card.last4No || '****'}`,
    value: card.id
  }));
});

// 账单选择列
const billColumns = computed(() => {
  const cols = [{ text: "不关联账单", value: "" }];
  billList.value.forEach(bill => {
    cols.push({
      text: `${bill.card_alias || '账单'} ¥${bill.bill_amount}`,
      value: bill.id
    });
  });
  return cols;
});

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList({ cardType: "credit" });
    cardList.value = res.data || res || [];

    // 如果有预选的卡片ID
    const cardId = route.query.cardId;
    if (cardId) {
      formData.cardId = cardId;
      const card = cardList.value.find(c => c.id === cardId);
      if (card) {
        selectedCardName.value = `${card.alias || card.bank_name || '卡片'} **** ${card.last4_no || card.last4No || '****'}`;
        // 加载该卡片的账单
        loadBillList(cardId);
      }
    }
  } catch (error) {
    // 忽略错误
  }
};

// 加载账单列表
const loadBillList = async (cardId) => {
  try {
    const res = await getBillList({ cardId });
    billList.value = (res.data || res || []).filter(bill => bill.need_repay > 0);

    // 如果有预选的账单ID
    const billId = route.query.billId;
    if (billId) {
      formData.billId = billId;
      const bill = billList.value.find(b => b.id === billId);
      if (bill) {
        selectedBillName.value = `${bill.card_alias || '账单'} ¥${bill.bill_amount}`;
      }
    }
  } catch (error) {
    // 忽略错误
  }
};

// 卡片选择确认
const onCardConfirm = ({ selectedOptions }) => {
  formData.cardId = selectedOptions[0].value;
  selectedCardName.value = selectedOptions[0].text;
  formData.billId = "";
  selectedBillName.value = "";
  showCardPicker.value = false;
  // 加载该卡片的账单
  loadBillList(formData.cardId);
};

// 账单选择确认
const onBillConfirm = ({ selectedOptions }) => {
  formData.billId = selectedOptions[0].value;
  selectedBillName.value = selectedOptions[0].text;
  showBillPicker.value = false;
};

// 日期时间确认
const onDateTimeConfirm = (value) => {
  const date = new Date(value);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  formData.repayTime = `${y}-${m}-${d} ${h}:${min}:${s}`;
  showDateTimePicker.value = false;
};

// 返回
const onClickLeft = () => {
  router.back();
};

// 提交
const onSubmit = async () => {
  try {
    loading.value = true;
    showLoadingToast({ message: "保存中...", forbidClick: true });

    const submitData = {
      cardId: formData.cardId,
      billId: formData.billId || null,
      repayAmount: formData.repayAmount ? Number(formData.repayAmount) : null,
      repayMethod: formData.repayMethod,
      repayTime: formData.repayTime,
      remark: formData.remark || null,
    };

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
  loadCardList();
  // 初始化还款时间
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  const h = String(now.getHours()).padStart(2, '0');
  const min = String(now.getMinutes()).padStart(2, '0');
  const s = String(now.getSeconds()).padStart(2, '0');
  formData.repayTime = `${y}-${m}-${d} ${h}:${min}:${s}`;
});
</script>

<style scoped>
.page-repay-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.page-header {
  background: #fff;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 16px 16px 8px;
}

.submit-btn-wrap {
  margin: 32px 16px;
}
</style>
