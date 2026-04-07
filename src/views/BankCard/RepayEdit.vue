<template>
  <div class="page-repay-edit">

    <van-skeleton title :row="8" v-if="loading" />

    <van-form @submit="onSubmit" ref="formRef" v-if="!loading && repayData.id">
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
          <van-field name="repayMethod" label="还款方式">
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
          保存修改
        </van-button>
        <van-button plain block round type="danger" @click="handleDelete">
          删除还款记录
        </van-button>
      </div>
    </van-form>

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
import { ref, reactive, onMounted } from "vue";
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { getRepayDetail, updateRepay, deleteRepay } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const repayData = ref({});
const showDateTimePicker = ref(false);
const currentDate = ref(new Date());

const formData = reactive({
  repayAmount: "",
  repayMethod: "转账",
  repayTime: "",
  remark: "",
});

// 加载还款详情
const loadRepayDetail = async () => {
  const id = route.query.id;
  if (!id) {
    showToast("缺少还款记录ID");
    router.back();
    return;
  }

  loading.value = true;
  try {
    const res = await getRepayDetail(id);
    repayData.value = res.data || res || {};

    const data = repayData.value;
    formData.repayAmount = data.repay_amount ?? "";
    formData.repayMethod = data.repay_method || "转账";
    formData.repayTime = data.repay_time?.replace("T", " ").split(".")[0] || "";
    formData.remark = data.remark || "";

    // 设置日期时间选择器的初始值
    if (formData.repayTime) {
      currentDate.value = new Date(formData.repayTime.replace(" ", "T"));
    }
  } catch (error) {
    showToast(error.message || "加载失败");
  } finally {
    loading.value = false;
  }
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
      repayAmount: formData.repayAmount ? Number(formData.repayAmount) : null,
      repayMethod: formData.repayMethod,
      repayTime: formData.repayTime,
      remark: formData.remark || null,
    };

    await updateRepay(repayData.value.id, submitData);

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
      message: "确定要删除这条还款记录吗？",
      confirmButtonColor: "#ee0a24",
    });

    showLoadingToast({ message: "删除中...", forbidClick: true });
    await deleteRepay(repayData.value.id);

    closeToast();
    showToast({ message: "删除成功", onClose: () => router.back() });
  } catch (error) {
    if (error !== "cancel") {
      showToast(error.message || "删除失败");
    }
  }
};

onMounted(() => {
  loadRepayDetail();
});
</script>

<style scoped>
.page-repay-edit {
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
