<template>
  <div class="page-add-card">
    <van-form @submit="onSubmit">
      <!-- 卡类型切换 -->
      <div class="form-section" v-if="!formData.cardType">
        <div class="section-title">选择卡类型</div>
        <van-cell-group inset>
          <van-cell
            title="借记卡"
            label="储蓄卡、工资卡等"
            clickable
            @click="selectCardType('debit')"
          >
            <template #icon>
              <van-icon name="card" size="24" style="margin-right: 12px" />
            </template>
            <template #right-icon>
              <van-radio :name="'debit'" />
            </template>
          </van-cell>
          <van-cell
            title="信用卡"
            label="贷记卡、准贷记卡等"
            clickable
            @click="selectCardType('credit')"
          >
            <template #icon>
              <van-icon
                name="credit-pay"
                size="24"
                style="margin-right: 12px"
              />
            </template>
            <template #right-icon>
              <van-radio :name="'credit'" />
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <!-- 已选择卡类型后显示表单 -->
      <template v-if="formData.cardType">
        <!-- 通用必填信息 -->
        <div class="form-section">
          <div class="section-title">
            基本信息
            <span class="required-hint">*为必填项</span>
          </div>
          <van-cell-group inset>
            <van-field
              v-model="bankName"
              label="银行"
              placeholder="请选择银行"
              is-link
              readonly
              @click="showBankPicker = true"
              :rules="[{ required: true, message: '请选择银行' }]"
            />
            <van-field
              v-model="formData.last4No"
              label="卡号后4位"
              placeholder="请输入"
              maxlength="4"
              type="number"
              :rules="[{ required: true, message: '请输入卡号后4位' }]"
            />
            <van-field
              v-model="formData.cardBin"
              label="卡BIN"
              placeholder="卡号前6位"
              maxlength="6"
              type="number"
              :rules="[{ required: true, message: '请输入卡BIN' }]"
            />
            <van-field
              v-model="formData.openDate"
              label="开卡日期"
              placeholder="请选择"
              is-link
              readonly
              @click="showOpenDatePicker = true"
              :rules="[{ required: true, message: '请选择开卡日期' }]"
            />
            <van-field
              v-model="formData.expireDate"
              label="过期日期"
              placeholder="请选择"
              is-link
              readonly
              @click="showExpireDatePicker = true"
              :rules="[{ required: true, message: '请选择过期日期' }]"
            />
          </van-cell-group>
        </div>

        <!-- 信用卡专属必填 -->
        <div class="form-section credit-required" v-if="isCreditCard">
          <div class="section-title">
            <van-icon name="star" style="margin-right: 4px" />
            信用卡专属必填
          </div>
          <van-cell-group inset>
            <van-field
              v-model="formData.billDay"
              label="账单日"
              placeholder="如：5"
              type="number"
              :rules="[{ required: true, message: '请输入账单日' }]"
            />
            <van-field
              v-model="formData.repayDay"
              label="还款日"
              placeholder="如：25"
              type="number"
              :rules="[{ required: true, message: '请输入还款日' }]"
            />
            <van-field
              v-model="formData.annualFee"
              label="年费"
              placeholder="如：0"
              type="number"
              :rules="[{ required: true, message: '请输入年费' }]"
            />
            <van-field
              v-model="formData.feeFreeRule"
              label="免年费规则"
              placeholder="如：刷6次免年费"
              :rules="[{ required: true, message: '请输入免年费规则' }]"
            />
          </van-cell-group>
        </div>

        <!-- 通用选填信息 -->
        <div class="form-section">
          <div class="section-title">选填信息</div>
          <van-cell-group inset>
            <van-field
              v-model="formData.cardLevel"
              label="卡等级"
              placeholder="如：金卡、白金卡（默认普卡）"
            />
            <van-field
              v-model="formData.mainSub"
              label="主副卡"
              is-link
              readonly
              @click="showMainSubPicker = true"
            />
            <van-field
              v-model="formData.cardOrg"
              label="卡组织"
              is-link
              readonly
              @click="showCardOrgPicker = true"
            />
            <van-field
              v-model="formData.cardLength"
              label="卡号长度"
              placeholder="默认19位"
              type="number"
            />
            <van-field
              v-model="formData.alias"
              label="卡片别名"
              placeholder="如：工资卡、留学卡"
            />
          </van-cell-group>
        </div>

        <!-- 外观 -->
        <div class="form-section">
          <div class="section-title">外观</div>
          <van-cell-group inset>
            <van-field label="卡片颜色">
              <template #input>
                <div class="color-picker">
                  <div
                    v-for="color in colorOptions"
                    :key="color.value"
                    class="color-option"
                    :class="{ active: formData.color === color.value }"
                    :style="{ background: color.value }"
                    @click="formData.color = color.value"
                  >
                    <van-icon
                      v-if="formData.color === color.value"
                      name="success"
                      color="#fff"
                    />
                  </div>
                </div>
              </template>
            </van-field>
          </van-cell-group>
        </div>

        <!-- 基本设置 -->
        <div class="form-section">
          <div class="section-title">设置</div>
          <van-cell-group inset>
            <van-field
              v-model="formData.currency"
              label="币种"
              placeholder="默认CNY"
            />
            <van-field
              v-model="formData.status"
              label="卡片状态"
              is-link
              readonly
              @click="showStatusPicker = true"
            />
            <van-field name="isDefault" label="设为默认卡">
              <template #input>
                <van-switch v-model="formData.isDefault" size="20" />
              </template>
            </van-field>
            <van-field name="isHide" label="隐藏卡片">
              <template #input>
                <van-switch v-model="formData.isHide" size="20" />
              </template>
            </van-field>
          </van-cell-group>
        </div>

        <!-- 备注 -->
        <div class="form-section">
          <div class="section-title">备注</div>
          <van-cell-group inset>
            <van-field
              v-model="formData.tag"
              label="标签"
              placeholder="如：日常消费、出国使用"
            />
            <van-field
              v-model="formData.remark"
              label="备注"
              type="textarea"
              rows="2"
              placeholder="其他备注信息"
            />
          </van-cell-group>
        </div>

        <div class="submit-btn-wrap">
          <van-button
            type="primary"
            block
            round
            native-type="submit"
            :loading="loading"
            :disabled="loading"
          >
            保存{{ isCreditCard ? "信用卡" : "借记卡" }}
          </van-button>
          <van-button
            plain
            block
            round
            @click="formData.cardType = ''"
            v-if="!isEdit"
          >
            重新选择卡类型
          </van-button>
        </div>
      </template>
    </van-form>

    <!-- 银行选择 -->
    <van-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
      />
    </van-popup>

    <!-- 主副卡选择 -->
    <van-popup v-model:show="showMainSubPicker" position="bottom">
      <van-picker
        :columns="mainSubColumns"
        @confirm="onMainSubConfirm"
        @cancel="showMainSubPicker = false"
      />
    </van-popup>

    <!-- 卡组织选择 -->
    <van-popup v-model:show="showCardOrgPicker" position="bottom">
      <van-picker
        :columns="cardOrgColumns"
        @confirm="onCardOrgConfirm"
        @cancel="showCardOrgPicker = false"
      />
    </van-popup>

    <!-- 卡片状态选择 -->
    <van-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </van-popup>

    <!-- 开卡日期 -->
    <van-popup v-model:show="showOpenDatePicker" position="bottom">
      <van-date-picker
        v-model="openDate"
        type="date"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onOpenDateConfirm"
        @cancel="showOpenDatePicker = false"
      />
    </van-popup>

    <!-- 过期日期 -->
    <van-popup v-model:show="showExpireDatePicker" position="bottom">
      <van-date-picker
        v-model="expireDate"
        type="year-month"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onExpireDateConfirm"
        @cancel="showExpireDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast, showLoadingToast, closeToast } from "vant";
import { createCard } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";

const router = useRouter();
const loading = ref(false);
const bankName = ref("");
const isEdit = ref(false);

// 计算属性
const isCreditCard = computed(() => formData.cardType === "credit");

// 表单数据
const formData = reactive({
  cardType: "",
  bankId: "",
  last4No: "",
  cardBin: "",
  openDate: "",
  expireDate: "",
  sourceFrom: "手动",
  // 选填字段（带默认值）
  cardLevel: "",
  mainSub: "主卡",
  cardOrg: "银联",
  cardLength: "19",
  alias: "",
  cardImg: "",
  currency: "CNY",
  status: "正常",
  billDay: "",
  repayDay: "",
  isDefault: false,
  isHide: false,
  sort: 99,
  tag: "",
  remark: "",
  color: "#0052cc",
  annualFee: "",
  feeFreeRule: "",
});

// 颜色选项
const colorOptions = [
  { value: "#0052cc", label: "蓝色" },
  { value: "#07c160", label: "绿色" },
  { value: "#ee0a24", label: "红色" },
  { value: "#ff976a", label: "橙色" },
  { value: "#7232dd", label: "紫色" },
  { value: "#1a1a1a", label: "黑色" },
  { value: "#c01d24", label: "深红" },
  { value: "#1b4f9a", label: "深蓝" },
];

// 银行列表（从 API 加载）
const bankColumns = ref([]);

// 主副卡
const mainSubColumns = [
  { text: "主卡", value: "主卡" },
  { text: "副卡", value: "副卡" },
];

// 卡组织
const cardOrgColumns = [
  { text: "银联", value: "银联" },
  { text: "Visa", value: "Visa" },
  { text: "Mastercard", value: "Mastercard" },
  { text: "American Express", value: "American Express" },
  { text: "JCB", value: "JCB" },
];

// 卡片状态
const statusColumns = [
  { text: "正常", value: "正常" },
  { text: "挂失", value: "挂失" },
  { text: "注销", value: "注销" },
];

// 日期选择
const minDate = new Date(2000, 0, 1);
const maxDate = new Date(2050, 11, 31);
const openDate = reactive(["2024", "01", "01"]);
const expireDate = reactive(["2030", "12"]);

// 弹出状态
const showBankPicker = ref(false);
const showMainSubPicker = ref(false);
const showCardOrgPicker = ref(false);
const showStatusPicker = ref(false);
const showOpenDatePicker = ref(false);
const showExpireDatePicker = ref(false);

// 选择卡类型
const selectCardType = (type) => {
  formData.cardType = type;
};

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    const banks = res.data || res || [];
    bankColumns.value = banks.map((bank) => ({
      text: bank.name,
      value: bank.id,
      iconUrl: bank.icon_url || bank.iconUrl || "",
    }));
  } catch (e) {
    bankColumns.value = [];
  }
};

// 选择器确认
const onBankConfirm = ({ selectedOptions }) => {
  const selected = selectedOptions[0];
  formData.bankId = selected.value;
  bankName.value = selected.text;
  showBankPicker.value = false;
};

const onMainSubConfirm = ({ selectedOptions }) => {
  formData.mainSub = selectedOptions[0].value;
  showMainSubPicker.value = false;
};

const onCardOrgConfirm = ({ selectedOptions }) => {
  formData.cardOrg = selectedOptions[0].value;
  showCardOrgPicker.value = false;
};

const onStatusConfirm = ({ selectedOptions }) => {
  formData.status = selectedOptions[0].value;
  showStatusPicker.value = false;
};

const onOpenDateConfirm = ({ selectedValues }) => {
  formData.openDate = selectedValues.join("-");
  showOpenDatePicker.value = false;
};

const onExpireDateConfirm = ({ selectedValues }) => {
  formData.expireDate = selectedValues.join("-");
  showExpireDatePicker.value = false;
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

    // 构建提交数据
    const submitData = {
      bankId: formData.bankId,
      cardType: formData.cardType,
      last4No: formData.last4No,
      cardBin: formData.cardBin,
      openDate: formData.openDate,
      expireDate: formData.expireDate,
      sourceFrom: formData.sourceFrom,
    };

    // 选填字段（只有填了才传）
    if (formData.cardLevel) submitData.cardLevel = formData.cardLevel;
    if (formData.mainSub) submitData.mainSub = formData.mainSub;
    if (formData.cardOrg) submitData.cardOrg = formData.cardOrg;
    if (formData.cardLength)
      submitData.cardLength = String(formData.cardLength);
    if (formData.alias) submitData.alias = formData.alias;
    if (formData.currency) submitData.currency = formData.currency;
    if (formData.status) submitData.status = formData.status;
    if (formData.tag) submitData.tag = formData.tag;
    if (formData.remark) submitData.remark = formData.remark;
    if (formData.color) submitData.color = formData.color;
    if (formData.sort) submitData.sort = formData.sort;
    submitData.isDefault = formData.isDefault;
    submitData.isHide = formData.isHide;

    // 信用卡专属必填字段
    if (isCreditCard.value) {
      submitData.billDay = Number(formData.billDay);
      submitData.repayDay = Number(formData.repayDay);
      submitData.annualFee = Number(formData.annualFee);
      submitData.feeFreeRule = formData.feeFreeRule;
    } else {
      // 借记卡可选填
      if (formData.billDay) submitData.billDay = Number(formData.billDay);
      if (formData.repayDay) submitData.repayDay = Number(formData.repayDay);
      if (formData.annualFee) submitData.annualFee = Number(formData.annualFee);
      if (formData.feeFreeRule) submitData.feeFreeRule = formData.feeFreeRule;
    }

    await createCard(submitData);

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
  loadBankList();
});
</script>

<style scoped>
.page-add-card {
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
  display: flex;
  align-items: center;
}

.required-hint {
  font-size: 12px;
  color: #ee0a24;
  margin-left: 8px;
}

.credit-required .section-title {
  color: #ee0a24;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.color-option {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.color-option:active {
  transform: scale(0.9);
}

.color-option.active {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px currentColor;
}

.submit-btn-wrap {
  margin: 32px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
