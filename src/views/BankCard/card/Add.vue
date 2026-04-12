<template>
  <div class="page-add-card">
    <van-form @submit="onSubmit">
      <!-- 基本信息 -->
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
            clickable
            @click="showBankPicker = true"
            :rules="[{ required: true, message: '请选择银行' }]"
          />
          <van-field
            v-model="formData.last4No"
            label="卡号后4位"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('last4No')"
            :rules="[{ required: true, message: '请输入4位卡号' }]"
          />
          <van-field
            v-model="formData.cardBin"
            label="卡BIN"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardBin')"
            :rules="[{ required: true, message: '请输入6位卡BIN' }]"
          />
          <van-field
            v-model="formData.openDate"
            label="开卡日期"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showOpenDatePicker = true"
            :rules="[{ required: true, message: '请选择开卡日期' }]"
          />
          <van-field
            v-model="formData.expireDate"
            label="过期日期"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showExpireDatePicker = true"
          />
        </van-cell-group>
      </div>

      <!-- 选填信息 -->
      <div class="form-section">
        <div class="section-title">选填信息</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.cardLevel"
            label="卡等级"
            placeholder="默认普卡"
            @click-right-icon="showCardLevelPicker = true"
          >
            <template #right-icon>
              <van-icon name="arrow-down" />
            </template>
          </van-field>
          <van-field
            v-model="formData.mainSub"
            label="主副卡"
            is-link
            readonly
            clickable
            @click="showMainSubPicker = true"
          />
          <van-field
            v-model="formData.cardOrg"
            label="卡组织"
            placeholder="请选择或输入"
            @click-right-icon="showCardOrgPicker = true"
          >
            <template #right-icon>
              <van-icon name="arrow-down" />
            </template>
          </van-field>
          <van-field
            v-model="formData.cardLength"
            label="卡号长度"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardLength')"
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
        <van-cell-group inset> </van-cell-group>

        <!-- 颜色选择 -->
        <div class="color-section">
          <div class="color-picker-row">
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
            <div class="custom-color">
              <input
                ref="colorInputRef"
                type="color"
                v-model="formData.color"
                class="color-input-hidden"
              />
              <div class="color-picker-btn" @click="colorInputRef?.click()">
                <van-icon name="edit" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 设置 -->
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
            clickable
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
          保存借记卡
        </van-button>
      </div>
    </van-form>

    <!-- 银行选择 -->
    <van-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
      />
    </van-popup>

    <!-- 卡等级选择 -->
    <van-popup v-model:show="showCardLevelPicker" position="bottom">
      <van-picker
        :columns="cardLevelColumns"
        @confirm="onCardLevelConfirm"
        @cancel="showCardLevelPicker = false"
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

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model:show="showKeyboard"
      :maxlength="keyboardMaxlength"
      theme="custom"
      close-on-click-outside
      @input="onKeyboardInput"
      @delete="onKeyboardDelete"
      @close="onKeyboardClose"
      @blur="showKeyboard = false"
      close-button-text="完成"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  showToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from "vant";
import { createCard } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";

const router = useRouter();
const loading = ref(false);
const bankName = ref("");

// 表单数据（借记卡）
const formData = reactive({
  cardType: "debit",
  bankId: "",
  last4No: "",
  cardBin: "",
  openDate: "",
  expireDate: "",
  sourceFrom: "手动",
  cardLevel: "普卡",
  mainSub: "主卡",
  cardOrg: "银联",
  cardLength: "19",
  alias: "",
  cardImg: "",
  currency: "CNY",
  status: "正常",
  isDefault: false,
  isHide: false,
  sort: 99,
  tag: "",
  remark: "",
  color: "#0052cc",
});

// 颜色选项（丰富）
const colorOptions = [
  { value: "#0052cc", label: "蓝色" },
  { value: "#07c160", label: "绿色" },
  { value: "#ee0a24", label: "红色" },
  { value: "#ff976a", label: "橙色" },
  { value: "#7232dd", label: "紫色" },
  { value: "#1a1a1a", label: "黑色" },
  { value: "#c01d24", label: "深红" },
  { value: "#1b4f9a", label: "深蓝" },
  { value: "#f0c987", label: "金色" },
  { value: "#ffffff", label: "白色" },
  { value: "#9c27b0", label: "紫色" },
  { value: "#00bcd4", label: "青色" },
];

// 银行列表
const bankColumns = ref([]);

// 卡等级选项（借记卡）
const cardLevelColumns = [
  { text: "普卡", value: "普卡" },
  { text: "金卡", value: "金卡" },
  { text: "白金卡", value: "白金卡" },
  { text: "钻石卡", value: "钻石卡" },
  { text: "黑金卡", value: "黑金卡" },
];

// 主副卡
const mainSubColumns = [
  { text: "主卡", value: "主卡" },
  { text: "副卡", value: "副卡" },
];

// 卡组织
const cardOrgColumns = [
  { text: "银联", value: "银联" },
  { text: "万事达", value: "万事达" },
  { text: "Visa", value: "Visa" },
  { text: "Amex", value: "Amex" },
  { text: "大莱", value: "大莱" },
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
const showCardLevelPicker = ref(false);
const showMainSubPicker = ref(false);
const showCardOrgPicker = ref(false);
const showStatusPicker = ref(false);
const showOpenDatePicker = ref(false);
const showExpireDatePicker = ref(false);
const showColorPicker = ref(false);
const colorInputRef = ref(null);

// 数字键盘控制
const showKeyboard = ref(false);
const currentField = ref("");
const keyboardMaxlength = ref(999);

// 验证卡号后4位
const validateLast4No = () => {
  if (formData.last4No && formData.last4No.length !== 4) {
    showToast("卡号后4位必须为4位数字");
    formData.last4No = "";
  }
};

// 验证卡BIN
const validateCardBin = () => {
  if (formData.cardBin && formData.cardBin.length < 6) {
    showToast("卡BIN必须至少6位数字");
    formData.cardBin = "";
  }
};

// 字段配置
const fieldConfig = {
  last4No: { maxlength: 4, validate: validateLast4No },
  cardBin: { maxlength: 6, validate: validateCardBin },
  cardLength: { maxlength: 2, validate: null },
};

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
  let newValue = formData[field] + value;
  if (newValue.length > keyboardMaxlength.value) {
    newValue = newValue.slice(0, keyboardMaxlength.value);
  }
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
  const field = currentField.value;
  if (field && fieldConfig[field]?.validate) {
    fieldConfig[field].validate();
  }
};

// 关闭键盘（用于失焦时）
const closeKeyboard = () => {
  showKeyboard.value = false;
};

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    const banks = res.data || res || [];
    bankColumns.value = banks
      .filter((bank) => bank.name && bank.name.endsWith("银行"))
      .map((bank) => ({
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

const onCardLevelConfirm = ({ selectedOptions }) => {
  formData.cardLevel = selectedOptions[0].value;
  showCardLevelPicker.value = false;
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
  if (formData.last4No && formData.last4No.length !== 4) {
    showToast("卡号后4位必须为4位数字");
    return;
  }
  if (formData.cardBin && formData.cardBin.length < 6) {
    showToast("卡BIN必须至少6位数字");
    return;
  }

  try {
    loading.value = true;
    showLoadingToast({ message: "保存中...", forbidClick: true });

    const submitData = {
      bankId: formData.bankId,
      cardType: formData.cardType,
      last4No: formData.last4No,
      cardBin: formData.cardBin,
      openDate: formData.openDate,
      expireDate: formData.expireDate,
      sourceFrom: formData.sourceFrom,
    };

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

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 12px 16px 8px;
  display: flex;
  align-items: center;
}

.required-hint {
  font-size: 12px;
  color: #ee0a24;
  margin-left: 8px;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.color-section {
  background: #fff;
  padding: 12px 16px;
}

.color-picker-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
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
  border: 1px solid #ebedf0;
  flex-shrink: 0;
}

.color-option:active {
  transform: scale(0.9);
}

.color-option.active {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px currentColor;
}

.custom-color {
  display: flex;
  align-items: center;
  position: relative;
}

.color-input-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.color-picker-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f7f8fa;
  border: 1px dashed #dcdee0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #969799;
}

.submit-btn-wrap {
  margin: 32px 16px;
}
</style>
