<template>
  <div class="page-bill-edit">

    <van-skeleton title :row="10" v-if="loading" />

    <van-form @submit="onSubmit" ref="formRef" v-if="!loading && billData.id">
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
          />
          <van-field
            v-model="formData.billEndDate"
            name="billEndDate"
            label="账单结束"
            placeholder="请选择"
            is-link
            readonly
            @click="showEndDatePicker = true"
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
          保存修改
        </van-button>
        <van-button plain block round type="danger" @click="handleDelete">
          删除账单
        </van-button>
      </div>
    </van-form>

    <!-- 日期选择 -->
    <van-popup v-model:show="showStartDatePicker" position="bottom">
      <van-date-picker
        v-model="startDate"
        type="date"
        title="选择日期"
        @confirm="onStartDateConfirm"
        @cancel="showStartDatePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showEndDatePicker" position="bottom">
      <van-date-picker
        v-model="endDate"
        type="date"
        title="选择日期"
        @confirm="onEndDateConfirm"
        @cancel="showEndDatePicker = false"
      />
    </van-popup>

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
import { ref, reactive, onMounted } from "vue";
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { getBillDetail, updateBill, deleteBill } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const billData = ref({});
const showStartDatePicker = ref(false);
const showEndDatePicker = ref(false);
const showPointsExpirePicker = ref(false);
const startDate = reactive(["2024", "01", "01"]);
const endDate = reactive(["2024", "01", "31"]);
const pointsExpireDate = reactive(["2025", "12", "31"]);

const formData = reactive({
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

// 加载账单详情
const loadBillDetail = async () => {
  const id = route.query.id;
  if (!id) {
    showToast("缺少账单ID");
    router.back();
    return;
  }

  loading.value = true;
  try {
    const res = await getBillDetail(id);
    billData.value = res.data || res || {};

    const data = billData.value;
    formData.creditLimit = data.credit_limit ?? "";
    formData.availLimit = data.avail_limit ?? "";
    formData.usedLimit = data.used_limit ?? "";
    formData.billStartDate = data.bill_start_date?.split(" ")[0] || "";
    formData.billEndDate = data.bill_end_date?.split(" ")[0] || "";
    formData.billAmount = data.bill_amount ?? "";
    formData.minRepay = data.min_repay ?? "";
    formData.repaid = data.repaid ?? "";
    formData.needRepay = data.need_repay ?? "";
    formData.tempLimit = data.temp_limit ?? "";
    formData.points = data.points ?? "";
    formData.pointsExpire = data.points_expire?.split(" ")[0] || "";
    formData.repayStatus = data.repay_status || "未还";
    formData.isOverdue = data.is_overdue || false;
    formData.overdueDays = data.overdue_days;
    formData.remindSwitch = data.remind_switch ?? true;
    formData.remindDays = data.remind_days ?? 3;

    // 处理日期选择器
    if (formData.billStartDate) {
      const parts = formData.billStartDate.split("-");
      startDate[0] = parts[0];
      startDate[1] = parts[1];
      startDate[2] = parts[2];
    }
    if (formData.billEndDate) {
      const parts = formData.billEndDate.split("-");
      endDate[0] = parts[0];
      endDate[1] = parts[1];
      endDate[2] = parts[2];
    }
    if (formData.pointsExpire) {
      const parts = formData.pointsExpire.split("-");
      pointsExpireDate[0] = parts[0];
      pointsExpireDate[1] = parts[1];
      pointsExpireDate[2] = parts[2];
    }
  } catch (error) {
    showToast(error.message || "加载失败");
  } finally {
    loading.value = false;
  }
};

// 日期确认
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

    const submitData = {};
    if (formData.creditLimit !== "") submitData.creditLimit = Number(formData.creditLimit);
    if (formData.availLimit !== "") submitData.availLimit = Number(formData.availLimit);
    if (formData.usedLimit !== "") submitData.usedLimit = Number(formData.usedLimit);
    if (formData.billStartDate) submitData.billStartDate = formData.billStartDate;
    if (formData.billEndDate) submitData.billEndDate = formData.billEndDate;
    if (formData.billAmount !== "") submitData.billAmount = Number(formData.billAmount);
    if (formData.minRepay !== "") submitData.minRepay = Number(formData.minRepay);
    if (formData.repaid !== "") submitData.repaid = Number(formData.repaid);
    if (formData.needRepay !== "") submitData.needRepay = Number(formData.needRepay);
    if (formData.tempLimit !== "") submitData.tempLimit = Number(formData.tempLimit);
    if (formData.points !== "") submitData.points = Number(formData.points);
    if (formData.pointsExpire) submitData.pointsExpire = formData.pointsExpire;
    submitData.repayStatus = formData.repayStatus;
    submitData.isOverdue = formData.isOverdue;
    if (formData.overdueDays !== null) submitData.overdueDays = Number(formData.overdueDays);
    submitData.remindSwitch = formData.remindSwitch;
    submitData.remindDays = Number(formData.remindDays);

    await updateBill(billData.value.id, submitData);

    closeToast();
    showToast({ message: "保存成功", onClose: () => router.back() });
  } catch (error) {
    closeToast();
    showToast(error.message || "保存失败");
  } finally {
    loading.value = false;
  }
};

// 删除
const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: "删除确认",
      message: "确定要删除这条账单吗？",
      confirmButtonColor: "#ee0a24",
    });

    showLoadingToast({ message: "删除中...", forbidClick: true });
    await deleteBill(billData.value.id);

    closeToast();
    showToast({ message: "删除成功", onClose: () => router.back() });
  } catch (error) {
    if (error !== "cancel") {
      showToast(error.message || "删除失败");
    }
  }
};

onMounted(() => {
  loadBillDetail();
});
</script>

<style scoped>
.page-bill-edit {
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
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
