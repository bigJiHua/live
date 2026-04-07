<template>
  <div class="page-bill-add">


    <van-form @submit="onSubmit" ref="formRef">
      <!-- 卡片选择 -->
      <div class="form-section">
        <div class="section-title">关联卡片</div>
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
        </van-cell-group>
      </div>

      <!-- 额度信息 -->
      <div class="form-section">
        <div class="section-title">额度信息</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.creditLimit"
            name="creditLimit"
            label="信用额度"
            placeholder="请输入信用额度"
            type="number"
            :rules="[{ required: true, message: '请输入信用额度' }]"
          />
          <van-field
            v-model="formData.availLimit"
            name="availLimit"
            label="可用额度"
            placeholder="请输入可用额度"
            type="number"
          />
          <van-field
            v-model="formData.usedLimit"
            name="usedLimit"
            label="已用额度"
            placeholder="请输入已用额度"
            type="number"
          />
        </van-cell-group>
      </div>

      <!-- 账单周期 -->
      <div class="form-section">
        <div class="section-title">账单周期</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.billStartDate"
            name="billStartDate"
            label="账单开始"
            placeholder="请选择"
            is-link
            readonly
            @click="showStartDatePicker = true"
            :rules="[{ required: true, message: '请选择账单开始日期' }]"
          />
          <van-field
            v-model="formData.billEndDate"
            name="billEndDate"
            label="账单结束"
            placeholder="请选择"
            is-link
            readonly
            @click="showEndDatePicker = true"
            :rules="[{ required: true, message: '请选择账单结束日期' }]"
          />
        </van-cell-group>
      </div>

      <!-- 账单金额 -->
      <div class="form-section">
        <div class="section-title">账单金额</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.billAmount"
            name="billAmount"
            label="本期账单"
            placeholder="请输入账单金额"
            type="number"
            :rules="[{ required: true, message: '请输入账单金额' }]"
          />
          <van-field
            v-model="formData.minRepay"
            name="minRepay"
            label="最低还款"
            placeholder="请输入最低还款额"
            type="number"
          />
          <van-field
            v-model="formData.repaid"
            name="repaid"
            label="已还金额"
            placeholder="请输入已还金额"
            type="number"
          />
          <van-field
            v-model="formData.needRepay"
            name="needRepay"
            label="待还金额"
            placeholder="请输入待还金额"
            type="number"
          />
        </van-cell-group>
      </div>

      <!-- 附加信息 -->
      <div class="form-section">
        <div class="section-title">附加信息</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.tempLimit"
            name="tempLimit"
            label="临时额度"
            placeholder="请输入临时额度"
            type="number"
          />
          <van-field
            v-model="formData.points"
            name="points"
            label="积分"
            placeholder="请输入积分"
            type="number"
          />
          <van-field
            v-model="formData.pointsExpire"
            name="pointsExpire"
            label="积分到期"
            placeholder="请选择"
            is-link
            readonly
            @click="showPointsExpirePicker = true"
          />
        </van-cell-group>
      </div>

      <!-- 还款状态 -->
      <div class="form-section">
        <div class="section-title">还款状态</div>
        <van-cell-group inset>
          <van-field name="repayStatus" label="还款状态">
            <template #input>
              <van-radio-group v-model="formData.repayStatus" direction="horizontal">
                <van-radio name="未还">未还</van-radio>
                <van-radio name="部分还款">部分还款</van-radio>
                <van-radio name="已还清">已还清</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field name="isOverdue" label="是否逾期">
            <template #input>
              <van-switch v-model="formData.isOverdue" size="20" />
            </template>
          </van-field>
          <van-field
            v-model="formData.overdueDays"
            name="overdueDays"
            label="逾期天数"
            placeholder="请输入逾期天数"
            type="number"
            :disabled="!formData.isOverdue"
          />
        </van-cell-group>
      </div>

      <!-- 提醒设置 -->
      <div class="form-section">
        <div class="section-title">提醒设置</div>
        <van-cell-group inset>
          <van-field name="remindSwitch" label="提醒开关">
            <template #input>
              <van-switch v-model="formData.remindSwitch" size="20" />
            </template>
          </van-field>
          <van-field
            v-model="formData.remindDays"
            name="remindDays"
            label="提前天数"
            placeholder="请输入提前提醒天数"
            type="number"
            :disabled="!formData.remindSwitch"
          />
        </van-cell-group>
      </div>

      <div class="submit-btn-wrap">
        <van-button type="primary" block round native-type="submit" :loading="loading" :disabled="loading">
          保存账单
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

    <!-- 账单开始日期 -->
    <van-popup v-model:show="showStartDatePicker" position="bottom">
      <van-date-picker
        v-model="startDate"
        type="date"
        title="选择日期"
        @confirm="onStartDateConfirm"
        @cancel="showStartDatePicker = false"
      />
    </van-popup>

    <!-- 账单结束日期 -->
    <van-popup v-model:show="showEndDatePicker" position="bottom">
      <van-date-picker
        v-model="endDate"
        type="date"
        title="选择日期"
        @confirm="onEndDateConfirm"
        @cancel="showEndDatePicker = false"
      />
    </van-popup>

    <!-- 积分到期日期 -->
    <van-popup v-model:show="showPointsExpirePicker" position="bottom">
      <van-date-picker
        v-model="pointsExpireDate"
        type="date"
        title="选择日期"
        @confirm="onPointsExpireConfirm"
        @cancel="showPointsExpirePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { showToast, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { createBill, getCardList } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const formRef = ref(null);
const loading = ref(false);
const cardList = ref([]);
const selectedCardName = ref("");

const formData = reactive({
  cardId: "",
  creditLimit: "",
  availLimit: "",
  usedLimit: "",
  billStartDate: "",
  billEndDate: "",
  billAmount: "",
  minRepay: "",
  repaid: "",
  needRepay: "",
  tempLimit: "",
  points: "",
  pointsExpire: "",
  repayStatus: "未还",
  isOverdue: false,
  overdueDays: null,
  remindSwitch: true,
  remindDays: 3,
});

// 卡片选择列
const cardColumns = computed(() => {
  return cardList.value.map(card => ({
    text: `${card.alias || card.bank_name || '卡片'} **** ${card.last4_no || card.last4No || '****'}`,
    value: card.id
  }));
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
  showCardPicker.value = false;
};

// 日期选择
const showCardPicker = ref(false);
const showStartDatePicker = ref(false);
const showEndDatePicker = ref(false);
const showPointsExpirePicker = ref(false);
const startDate = reactive(["2024", "01", "01"]);
const endDate = reactive(["2024", "01", "31"]);
const pointsExpireDate = reactive(["2025", "12", "31"]);

const onStartDateConfirm = ({ selectedValues }) => {
  formData.billStartDate = selectedValues.join("-");
  showStartDatePicker.value = false;
};

const onEndDateConfirm = ({ selectedValues }) => {
  formData.billEndDate = selectedValues.join("-");
  showEndDatePicker.value = false;
};

const onPointsExpireConfirm = ({ selectedValues }) => {
  formData.pointsExpire = selectedValues.join("-");
  showPointsExpirePicker.value = false;
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
      creditLimit: formData.creditLimit ? Number(formData.creditLimit) : null,
      availLimit: formData.availLimit ? Number(formData.availLimit) : null,
      usedLimit: formData.usedLimit ? Number(formData.usedLimit) : null,
      billStartDate: formData.billStartDate,
      billEndDate: formData.billEndDate,
      billAmount: formData.billAmount ? Number(formData.billAmount) : null,
      minRepay: formData.minRepay ? Number(formData.minRepay) : null,
      repaid: formData.repaid ? Number(formData.repaid) : null,
      needRepay: formData.needRepay ? Number(formData.needRepay) : null,
      tempLimit: formData.tempLimit ? Number(formData.tempLimit) : null,
      points: formData.points ? Number(formData.points) : null,
      pointsExpire: formData.pointsExpire || null,
      repayStatus: formData.repayStatus,
      isOverdue: formData.isOverdue,
      overdueDays: formData.overdueDays ? Number(formData.overdueDays) : null,
      remindSwitch: formData.remindSwitch,
      remindDays: formData.remindDays ? Number(formData.remindDays) : null,
    };

    await createBill(submitData);

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
});
</script>

<style scoped>
.page-bill-add {
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
