<template>
  <div class="page-add-card">
    <app-form @submit="onSubmit">
      <!-- 基本信息 -->
      <div class="form-section">
        <div class="section-title">
          基本信息
          <span class="required-hint">*为必填项</span>
        </div>
        <van-cell-group inset>
          <app-field
            v-model="bankName"
            label="银行"
            placeholder="请选择银行"
            is-link
            readonly
            clickable
            @click="showBankPicker = true"
            :rules="[{ required: true, message: '请选择银行' }]"
          />
          <app-field
            v-model="formData.cardOrg"
            label="卡组织"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showCardOrgPicker = true"
            :rules="[{ required: true, message: '请选择卡组织' }]"
          />
          <app-field
            v-model="formData.cardLength"
            label="卡号长度"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showCardLengthPicker = true"
            :rules="[{ required: true, message: '请选择卡号长度' }]"
          />
          <app-field
            v-model="formData.cardBin"
            label="卡号前位"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardBin')"
            :rules="[{ required: true, message: '请输入卡BIN' }]"
          />
          <app-field
            v-model="formData.last4No"
            label="卡号后4位"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('last4No')"
            :rules="[{ required: true, message: '请输入4位卡号' }]"
          />
          <app-field
            v-model="formData.openDate"
            label="开卡日期"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showOpenDatePicker = true"
            :rules="[{ required: true, message: '请选择开卡日期' }]"
          />
          <app-field
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
          <app-field
            v-model="formData.cardLevel"
            label="卡等级"
            placeholder="默认普卡"
            @click-right-icon="showCardLevelPicker = true"
          >
            <template #right-icon>
              <van-icon name="arrow" />
            </template>
          </app-field>
          <app-field
            v-model="formData.mainSub"
            label="主副卡"
            is-link
            readonly
            clickable
            @click="showMainSubPicker = true"
          />
          <app-field
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
          <app-field
            v-model="formData.currency"
            label="币种"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showCurrencyPicker = true"
          />
          <app-field
            v-model="formData.status"
            label="卡片状态"
            is-link
            readonly
            clickable
            @click="showStatusPicker = true"
          />
          <app-field name="isDefault" label="设为默认卡">
            <van-switch v-model="formData.isDefault" size="20" />
          </app-field>
          <app-field name="isHide" label="隐藏卡片">
            <van-switch v-model="formData.isHide" size="20" />
          </app-field>
        </van-cell-group>
      </div>

      <!-- 备注 -->
      <div class="form-section">
        <div class="section-title">备注</div>
        <van-cell-group inset>
          <app-field
            v-model="formData.tag"
            label="标签"
            placeholder="如：日常消费、出国使用"
          />
          <app-field
            v-model="formData.remark"
            label="备注"
            type="textarea"
            rows="2"
            placeholder="其他备注信息"
          />
        </van-cell-group>
      </div>

      <div class="submit-btn-wrap">
        <app-button
          type="primary"
          block
          round
          native-type="submit"
          :loading="loading"
          :disabled="loading"
        >
          保存借记卡
        </app-button>
      </div>
    </app-form>

    <!-- 银行选择 -->
    <app-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
      />
    </app-popup>

    <!-- 卡等级选择 -->
    <app-popup v-model:show="showCardLevelPicker" position="bottom">
      <van-picker
        :columns="cardLevelColumns"
        @confirm="onCardLevelConfirm"
        @cancel="showCardLevelPicker = false"
      />
    </app-popup>

    <!-- 主副卡选择 -->
    <app-popup v-model:show="showMainSubPicker" position="bottom">
      <van-picker
        :columns="mainSubColumns"
        @confirm="onMainSubConfirm"
        @cancel="showMainSubPicker = false"
      />
    </app-popup>

    <!-- 卡组织选择（左侧带 org 图标） -->
    <app-popup v-model:show="showCardOrgPicker" position="bottom" round>
      <div class="org-picker">
        <div class="org-picker-title">选择卡组织</div>
        <div
          class="org-picker-item"
          v-for="col in cardOrgColumns"
          :key="col.value"
          @click="onCardOrgConfirm(col)"
        >
          <CardOrgIcon :org="ORG_NAME_MAP[col.value]" small :filled="true" />
          <span class="org-picker-label">{{ col.text }}</span>
          <van-icon
            v-if="formData.cardOrg === col.value"
            name="success"
            class="org-picker-check"
          />
        </div>
      </div>
    </app-popup>

    <!-- 卡号长度选择 -->
    <app-popup v-model:show="showCardLengthPicker" position="bottom">
      <van-picker
        title="选择卡号长度"
        :columns="cardLengthColumns"
        @confirm="onCardLengthConfirm"
        @cancel="showCardLengthPicker = false"
      />
    </app-popup>

    <!-- 卡片状态选择 -->
    <app-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </app-popup>

    <!-- 币种选择 -->
    <app-popup v-model:show="showCurrencyPicker" position="bottom">
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
      />
    </app-popup>

    <!-- 开卡日期 -->
    <app-popup v-model:show="showOpenDatePicker" position="bottom">
      <van-date-picker
        v-model="openDate"
        type="date"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onOpenDateConfirm"
        @cancel="showOpenDatePicker = false"
      />
    </app-popup>

    <!-- 过期日期 -->
    <app-popup v-model:show="showExpireDatePicker" position="bottom">
      <van-date-picker
        v-model="expireDate"
        type="year-month"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onExpireDateConfirm"
        @cancel="showExpireDatePicker = false"
      />
    </app-popup>

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
import CardOrgIcon from "@/components/BankCard/org/CardOrgIcon.vue";
import { ORG_NAME_MAP } from "@/components/BankCard/org/orgMap.js";

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

// 卡组织有效期（年）
const cardOrgValidity = {
  '银联': 10,
  '万事达': 8,
  'Visa': 5,
  '运通': 5,
  '大莱': 5,
  'JCB': 5,
};

// 卡组织BIN前缀（用户只需输入的位数）
const cardOrgBinPrefix = {
  '银联': { prefix: '62', needInput: 4 },      // 62xxxx，用户输入4位
  '万事达': { prefix: '53', needInput: 4 },     // 53xxx，用户输入4位
  'Visa': { prefix: '4', needInput: 5 },        // 4xxxx，用户输入5位
  '运通': { prefix: '37', needInput: 4 },        // 37xxx，用户输入4位
  '大莱': { prefix: '36', needInput: 4 },         // 36xxx，用户输入4位
  'JCB': { prefix: '35', needInput: 4 },        // 35xxx，用户输入4位
};

// 卡组织卡号长度
const cardOrgLength = {
  '银联': 19,
  '万事达': 16,
  'Visa': 16,
  '运通': 15,
  '大莱': 16,
  'JCB': 16,
};

// 卡组织
const cardOrgColumns = [
  { text: "银联", value: "银联" },
  { text: "万事达", value: "万事达" },
  { text: "Visa", value: "Visa" },
  { text: "运通", value: "运通" },
  { text: "大莱", value: "大莱" },
  { text: "JCB", value: "JCB" },
];

const cardLengthColumns = [
  { text: "15位", value: 15 },
  { text: "16位", value: 16 },
  { text: "19位", value: 19 },
];

// 计算到期日期
const calculateExpireDate = (openDateStr, cardOrg) => {
  const validityYears = cardOrgValidity[cardOrg] || 5;
  const [year, month] = openDateStr.split('-');
  const openYear = parseInt(year);
  const openMonth = parseInt(month);

  let expireYear = openYear + validityYears;
  let expireMonth = openMonth;

  return [String(expireYear), String(expireMonth).padStart(2, '0')];
};

// 卡片状态
const statusColumns = [
  { text: "正常", value: "正常" },
  { text: "挂失", value: "挂失" },
  { text: "注销", value: "注销" },
];

const currencyColumns = [
  { text: "CNY 人民币", value: "CNY" },
  { text: "USD 美元", value: "USD" },
  { text: "HKD 港币", value: "HKD" },
];

// 日期选择
const minDate = new Date(2000, 0, 1);
const maxDate = new Date(2050, 11, 31);
const getCurrentDate = () => {
  const now = new Date();
  return [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0")
  ];
};
const openDate = reactive(getCurrentDate());
const expireDate = reactive(["2030", "12"]);

// 弹出状态
const showBankPicker = ref(false);
const showCardLevelPicker = ref(false);
const showMainSubPicker = ref(false);
const showCardOrgPicker = ref(false);
const showCardLengthPicker = ref(false);
const showStatusPicker = ref(false);
const showCurrencyPicker = ref(false);
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
  const binConfig = cardOrgBinPrefix[formData.cardOrg] || { prefix: '', needInput: 6 };
  const expectedLength = binConfig.prefix.length + binConfig.needInput;
  if (formData.cardBin && formData.cardBin.length < expectedLength) {
    showToast(`卡BIN必须至少${expectedLength}位数字`);
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

const onCardOrgConfirm = (col) => {
  formData.cardOrg = col.value;
  showCardOrgPicker.value = false;
  const binConfig = cardOrgBinPrefix[formData.cardOrg] || { prefix: '', needInput: 6 };
  formData.cardBin = binConfig.prefix;
  formData.cardLength = cardOrgLength[formData.cardOrg] || 16;
  fieldConfig.cardBin.maxlength = binConfig.prefix.length + binConfig.needInput;
  if (formData.openDate) {
    const [expireYear, expireMonth] = calculateExpireDate(formData.openDate, formData.cardOrg);
    expireDate[0] = expireYear;
    expireDate[1] = expireMonth;
    formData.expireDate = `${expireYear}-${expireMonth}`;
  }
  showCardOrgPicker.value = false;
};

const onCardLengthConfirm = ({ selectedOptions }) => {
  formData.cardLength = selectedOptions[0].value;
  showCardLengthPicker.value = false;
};

const onStatusConfirm = ({ selectedOptions }) => {
  formData.status = selectedOptions[0].value;
  showStatusPicker.value = false;
};

const onCurrencyConfirm = ({ selectedOptions }) => {
  formData.currency = selectedOptions[0].value;
  showCurrencyPicker.value = false;
};

const onOpenDateConfirm = ({ selectedValues }) => {
  formData.openDate = selectedValues.join("-");
  const [expireYear, expireMonth] = calculateExpireDate(formData.openDate, formData.cardOrg);
  expireDate[0] = expireYear;
  expireDate[1] = expireMonth;
  formData.expireDate = `${expireYear}-${expireMonth}`;
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
      expireDate: formData.expireDate ? `${formData.expireDate}-01` : '',
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
  background: var(--theme-bg-primary);
  padding-bottom: 40px;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  padding: 12px 16px 8px;
  display: flex;
  align-items: center;
}

.required-hint {
  font-size: 12px;
  color: var(--van-danger-color, #ee0a24);
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
  background: var(--theme-bg-secondary);
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
  border: 1px solid var(--theme-border);
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
  background: var(--theme-bg-primary);
  border: 1px dashed var(--theme-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--theme-text-tertiary);
}

.submit-btn-wrap {
  margin: 32px 16px;
}

/* 卡组织选择器：左侧 org 图标 + 文字 */
.org-picker {
  background: var(--theme-bg-primary);
  padding: 8px 0 16px;
  max-height: 70vh;
  overflow-y: auto;
}
.org-picker-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
  padding: 12px 0 8px;
}
.org-picker-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 20px;
  cursor: pointer;
}
.org-picker-item:active {
  background: var(--theme-bg-secondary);
}
.org-picker-label {
  flex: 1;
  font-size: 15px;
  color: var(--theme-text-primary);
}
.org-picker-check {
  color: var(--theme-primary);
  font-size: 18px;
}
</style>
