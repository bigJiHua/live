<template>
  <div class="page-credit-full">
    <!-- 步骤条 -->
    <div class="steps-wrap">
      <div class="step-indicator">{{ currentStep + 1 }} / 4 步</div>
    </div>

    <!-- 步骤内容区域 -->
    <div class="step-content">
      <!-- ========== 步骤1: 卡片基本信息 ========== -->
      <div v-show="currentStep === 0" class="step-panel">
        <div class="form-section">
          <div class="section-title">
            基本信息 <span class="required-hint">*为必填</span>
          </div>
          <van-cell-group inset>
            <app-field
              v-model="formData.bankName"
              label="银行"
              placeholder="请选择银行"
              is-link
              readonly
              @click="showBankPicker = true"
              :rules="[{ required: true, message: '请选择银行' }]"
            />
            <app-field
              v-model="formData.last4No"
              label="卡号后4位"
              placeholder="请输入"
              readonly
              clickable
              @click="openKeyboard('last4No')"
              :rules="[{ required: true, message: '请输入卡号后4位' }]"
            />
            <app-field
              v-model="formData.cardBin"
              label="卡BIN"
              placeholder="卡号前6位"
              readonly
              clickable
              @click="openKeyboard('cardBin')"
              :rules="[{ required: true, message: '请输入卡BIN' }]"
            />
            <app-field
              v-model="formData.openDate"
              label="开卡日期"
              placeholder="请选择"
              is-link
              readonly
              @click="showOpenDatePicker = true"
              :rules="[{ required: true, message: '请选择开卡日期' }]"
            />
            <app-field
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

        <!-- 外观 -->
        <div class="form-section">
          <div class="section-title">卡片外观</div>
          <van-cell-group inset>
            <app-field label="卡片颜色">
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
                <!-- 自定义颜色 -->
                <div class="color-custom">
                  <input
                    type="color"
                    v-model="formData.color"
                    class="color-input"
                    title="自定义颜色"
                  />
                </div>
              </div>
            </app-field>
          </van-cell-group>
          <van-cell-group inset>
            <app-field label="自定义颜色">
              <div class="color-picker">
                <!-- 自定义颜色 -->
                <div class="color-custom">
                  <input
                    type="color"
                    v-model="formData.color"
                    class="color-input"
                    title="自定义颜色"
                  />
                </div>
              </div>
            </app-field>
          </van-cell-group>
        </div>
      </div>

      <!-- ========== 步骤2: 信用卡专属 ========== -->
      <div v-show="currentStep === 1" class="step-panel">
        <div class="form-section">
          <div class="section-title credit-title">
            <van-icon name="star" style="margin-right: 4px" />
            信用卡专属信息 <span class="required-hint">*为必填</span>
          </div>
          <van-cell-group inset>
            <app-field
              v-model="formData.billDay"
              label="账单日"
              placeholder="如：5"
              readonly
              clickable
              @click="openKeyboard('billDay')"
              :rules="[{ required: true, message: '请输入账单日（1-31）' }]"
              suffix="日"
            />
            <app-field
              v-model="formData.repayDay"
              label="还款日"
              placeholder="如：25"
              readonly
              clickable
              @click="openKeyboard('repayDay')"
              :rules="[{ required: true, message: '请输入还款日（1-31）' }]"
              suffix="日"
            />
            <app-field
              v-model="formData.annualFee"
              label="年费"
              placeholder="如：0"
              readonly
              clickable
              @click="openKeyboard('annualFee')"
              :rules="[{ required: true, message: '请输入年费' }]"
              suffix="元"
            />
            <app-field
              v-model="formData.feeFreeRule"
              label="免年费规则"
              placeholder="如：刷6次免年费"
              :rules="[{ required: true, message: '请输入免年费规则' }]"
            />
          </van-cell-group>
        </div>

        <!-- 额度信息 -->
        <div class="form-section">
          <div class="section-title credit-title">
            <van-icon name="card" style="margin-right: 4px" />
            额度信息 <span class="required-hint">*为必填</span>
          </div>
          <van-cell-group inset>
            <app-field
              v-model="formData.creditLimit"
              label="信用额度"
              placeholder="请输入信用额度"
              readonly
              clickable
              @click="openKeyboard('creditLimit')"
              :rules="[{ required: true, message: '请输入信用额度' }]"
              suffix="元"
            />
            <app-field
              v-model="formData.tempLimit"
              label="临时额度"
              placeholder="请输入临时额度"
              readonly
              clickable
              @click="openKeyboard('tempLimit')"
              :rules="[{ required: true, message: '请输入临时额度' }]"
              suffix="元"
            />
          </van-cell-group>
        </div>

        <!-- 积分与提醒设置 -->
        <div class="form-section">
          <div class="section-title">积分与提醒</div>
          <van-cell-group inset>
            <app-field
              v-model="formData.pointsRate"
              label="积分倍率"
              placeholder="如: 1"
              readonly
              clickable
              @click="openKeyboard('pointsRate')"
              suffix="倍"
            />
            <app-field label="还款提醒">
              <van-switch v-model="formData.remindSwitch" size="20" />
            </app-field>
            <app-field
              v-model="formData.remindDays"
              label="提前提醒"
              placeholder="请输入天数"
              readonly
              clickable
              :disabled="!formData.remindSwitch"
              @click="formData.remindSwitch && openKeyboard('remindDays')"
              suffix="天"
            />
          </van-cell-group>
        </div>

        <div class="tip-box">
          <van-icon name="info-o" />
          <span
            >账单日：银行生成账单的日期<br />还款日：必须还清欠款的日期</span
          >
        </div>
      </div>

      <!-- ========== 步骤3: 卡片设置 ========== -->
      <div v-show="currentStep === 2" class="step-panel">
        <div class="form-section">
          <div class="section-title">卡片设置</div>
          <van-cell-group inset>
            <app-field
              v-model="formData.cardLevel"
              label="卡等级"
              placeholder="请选择"
              is-link
              readonly
              @click="showCardLevelPicker = true"
            />
            <app-field
              v-model="formData.mainSub"
              label="主副卡"
              is-link
              readonly
              @click="showMainSubPicker = true"
            />
            <app-field
              v-model="formData.cardOrg"
              label="卡组织"
              is-link
              readonly
              @click="showCardOrgPicker = true"
            />
            <app-field
              v-model="formData.cardLength"
              label="卡号长度"
              placeholder="默认16位"
              readonly
              clickable
              @click="openKeyboard('cardLength')"
            />
            <app-field
              v-model="formData.alias"
              label="卡片别名"
              placeholder="如：工资卡、留学卡"
            />
          </van-cell-group>
        </div>

        <div class="form-section">
          <div class="section-title">状态设置</div>
          <van-cell-group inset>
            <app-field
              v-model="formData.currency"
              label="币种"
              placeholder="默认CNY"
            />
            <app-field
              v-model="formData.status"
              label="卡片状态"
              is-link
              readonly
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
      </div>

      <!-- ========== 步骤4: 确认卡片信息 ========== -->
      <div v-show="currentStep === 3" class="step-panel">
        <div class="form-section">
          <div class="section-title confirm-title">
            <van-icon name="passed" style="margin-right: 4px" />
            请确认卡片信息
          </div>
        </div>

        <!-- 卡片信息确认 -->
        <van-cell-group inset title="卡片信息">
          <app-cell title="银行" :value="formData.bankName || '-'" />
          <app-cell
            title="卡号后4位"
            :value="formData.last4No ? '****' + formData.last4No : '-'"
          />
          <app-cell
            title="卡BIN"
            :value="formData.cardBin ? formData.cardBin + '******' : '-'"
          />
          <app-cell title="开卡日期" :value="formData.openDate || '-'" />
          <app-cell title="过期日期" :value="formData.expireDate || '-'" />
        </van-cell-group>

        <!-- 信用卡专属确认 -->
        <van-cell-group inset title="信用卡专属">
          <app-cell
            title="账单日"
            :value="formData.billDay ? '每月' + formData.billDay + '日' : '-'"
          />
          <app-cell
            title="还款日"
            :value="formData.repayDay ? '每月' + formData.repayDay + '日' : '-'"
          />
          <app-cell
            title="年费"
            :value="formData.annualFee ? formData.annualFee + '元' : '-'"
          />
          <app-cell title="免年费规则" :value="formData.feeFreeRule || '-'" />
          <app-cell
            title="信用额度"
            :value="formData.creditLimit ? formData.creditLimit + '元' : '-'"
          />
          <app-cell
            title="临时额度"
            :value="formData.tempLimit ? formData.tempLimit + '元' : '0元'"
          />
        </van-cell-group>

        <!-- 卡片设置确认 -->
        <van-cell-group inset title="卡片设置">
          <app-cell title="卡片别名" :value="formData.alias || '-'" />
          <app-cell title="卡片状态" :value="formData.status" />
          <app-cell title="默认卡" :value="formData.isDefault ? '是' : '否'" />
        </van-cell-group>

        <div class="tip-box">
          <van-icon name="info-o" />
          <span>确认信息无误后，点击「提交卡片」按钮创建卡片</span>
        </div>
      </div>

    </div>

    <!-- 底部按钮 -->
    <div class="footer-bar">
      <app-button
        v-if="currentStep > 0"
        plain
        class="prev-btn"
        round
        @click="prevStep"
      >
        <van-icon name="arrow-left" /> 上一步
      </app-button>

      <!-- 步骤1-3: 下一步 -->
      <app-button
        v-if="currentStep < 3"
        type="primary"
        class="next-btn"
        round
        @click="nextStep"
      >
        下一步 <van-icon name="arrow" />
      </app-button>

      <!-- 步骤4: 提交卡片 -->
      <app-button
        v-if="currentStep === 3"
        type="primary"
        class="submit-btn"
        round
        :loading="loading"
        :disabled="loading"
        @click="submitCard"
      >
        提交卡片
      </app-button>
    </div>

    <!-- ========== 选择器弹窗 ========== -->
    <!-- 银行选择 -->
    <app-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
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

    <!-- 卡片状态选择 -->
    <app-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
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

    <!-- 账单开始日期 -->
    <app-popup v-model:show="showStartDatePicker" position="bottom">
      <van-date-picker
        v-model="startDate"
        type="date"
        title="选择日期"
        @confirm="onStartDateConfirm"
        @cancel="showStartDatePicker = false"
      />
    </app-popup>

    <!-- 账单结束日期 -->
    <app-popup v-model:show="showEndDatePicker" position="bottom">
      <van-date-picker
        v-model="endDate"
        type="date"
        title="选择日期"
        @confirm="onEndDateConfirm"
        @cancel="showEndDatePicker = false"
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

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model:show="showKeyboard"
      :maxlength="keyboardMaxlength"
      theme="custom"
      close-on-click-outside="true"
      @input="onKeyboardInput"
      @delete="onKeyboardDelete"
      @close="onKeyboardClose"
      @blur="showKeyboard = false"
      :close-button-text="'完成'"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import {
  showToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from "vant";
import { useRouter } from "vue-router";
import { createCard } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import CardOrgIcon from "@/components/BankCard/org/CardOrgIcon.vue";
import { ORG_NAME_MAP } from "@/components/BankCard/org/orgMap.js";

const router = useRouter();
const loading = ref(false);
const currentStep = ref(0);

// ========== 表单数据 ==========
const formData = reactive({
  // 卡片基本信息
  bankId: "",
  bankName: "",
  last4No: "",
  cardBin: "",
  openDate: "",
  expireDate: "",
  color: "#0052cc",

  // 信用卡专属
  billDay: "",
  repayDay: "",
  annualFee: "",
  feeFreeRule: "",

  // 卡片设置
  cardLevel: "白金卡",
  mainSub: "主卡",
  cardOrg: "银联",
  cardLength: "16",
  alias: "",
  currency: "CNY",
  status: "正常",
  isDefault: false,
  isHide: false,
  tag: "",
  remark: "",
  sourceFrom: "手动",

  // 额度信息
  creditLimit: "",
  tempLimit: "",

  // 积分设置
  pointsRate: "1",

  // 附加信息
  points: "",
  remindSwitch: true,
  remindDays: 3,
});

// ========== 计算属性 ==========
const dateRangeText = computed(() => {
  if (formData.billStartDate && formData.billEndDate) {
    return `${formData.billStartDate} ~ ${formData.billEndDate}`;
  }
  return "-";
});

// ========== 颜色选项 ==========
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

// ========== 选择器数据 ==========
const bankColumns = ref([]);
const mainSubColumns = [
  { text: "主卡", value: "主卡" },
  { text: "副卡", value: "副卡" },
];
const cardOrgColumns = [
  { text: "银联", value: "银联" },
  { text: "万事达", value: "万事达" },
  { text: "Visa", value: "Visa" },
  { text: "运通", value: "运通" },
  { text: "大莱", value: "大莱" },
  { text: "JCB", value: "JCB" },
];
const statusColumns = [
  { text: "正常", value: "正常" },
  { text: "挂失", value: "挂失" },
  { text: "注销", value: "注销" },
];

const cardLevelColumns = [
  { text: "普卡", value: "普卡" },
  { text: "金卡", value: "金卡" },
  { text: "白金卡", value: "白金卡" },
  { text: "钻石卡", value: "钻石卡" },
  { text: "黑卡", value: "黑卡" },
  { text: "无限卡", value: "无限卡" },
];

// ========== 日期配置 ==========
const minDate = new Date(2000, 0, 1);
const maxDate = new Date(2050, 11, 31);
// 开卡日期默认今年今日
const _today = new Date();
const openDate = reactive([
  String(_today.getFullYear()),
  String(_today.getMonth() + 1).padStart(2, "0"),
  String(_today.getDate()).padStart(2, "0"),
]);
const expireDate = reactive(["2030", "12"]);
const startDate = reactive(["2024", "01", "01"]);
const endDate = reactive(["2024", "01", "31"]);

// ========== 弹出状态 ==========
const showBankPicker = ref(false);
const showMainSubPicker = ref(false);
const showCardOrgPicker = ref(false);
const showStatusPicker = ref(false);
const showOpenDatePicker = ref(false);
const showExpireDatePicker = ref(false);
const showStartDatePicker = ref(false);
const showEndDatePicker = ref(false);
const showCardLevelPicker = ref(false);

// ========== 验证函数 ==========
const validateLast4No = () => {
  if (formData.last4No && formData.last4No.length !== 4) {
    showToast("卡号后4位必须为4位数字");
    formData.last4No = "";
  }
};

const validateCardBin = () => {
  if (formData.cardBin && formData.cardBin.length < 6) {
    showToast("卡BIN至少需要6位数字");
    formData.cardBin = "";
  }
};

const validateBillDay = () => {
  const day = Number(formData.billDay);
  if (formData.billDay && (day < 1 || day > 31)) {
    showToast("账单日范围为1-31");
    formData.billDay = "";
  }
};

const validateRepayDay = () => {
  const day = Number(formData.repayDay);
  if (formData.repayDay && (day < 1 || day > 31)) {
    showToast("还款日范围为1-31");
    formData.repayDay = "";
  }
};

// 信用额度验证（最大100万，整数）
const validateCreditLimit = () => {
  const val = Number(formData.creditLimit);
  if (formData.creditLimit && val > 1000000) {
    showToast("信用额度不能超过100万");
    formData.creditLimit = "1000000";
  }
};

// 临时额度验证（最大100万，整数）
const validateTempLimit = () => {
  const val = Number(formData.tempLimit);
  if (formData.tempLimit && val > 1000000) {
    showToast("临时额度不能超过100万");
    formData.tempLimit = "1000000";
  }
};

// ========== 数字键盘控制 ==========
const showKeyboard = ref(false);
const currentField = ref("");
const keyboardMaxlength = ref(999);

const fieldConfig = {
  last4No: { maxlength: 4, validate: validateLast4No },
  cardBin: { maxlength: 6, validate: validateCardBin },
  billDay: { maxlength: 2, validate: validateBillDay },
  repayDay: { maxlength: 2, validate: validateRepayDay },
  annualFee: { maxlength: 10, validate: null },
  creditLimit: { maxlength: 7, validate: validateCreditLimit },
  tempLimit: { maxlength: 7, validate: validateTempLimit },
  pointsRate: { maxlength: 3, validate: null },
  remindDays: { maxlength: 2, validate: null },
  cardLength: { maxlength: 2, validate: null },
};

const openKeyboard = (field) => {
  currentField.value = field;
  keyboardMaxlength.value = fieldConfig[field]?.maxlength || 10;
  showKeyboard.value = true;
};

const onKeyboardInput = (value) => {
  const field = currentField.value;
  if (!field) return;

  let newValue = formData[field] + value;
  if (newValue.length > keyboardMaxlength.value) {
    newValue = newValue.slice(0, keyboardMaxlength.value);
  }
  formData[field] = newValue;
};

const onKeyboardDelete = () => {
  const field = currentField.value;
  if (!field) return;
  formData[field] = formData[field].slice(0, -1);
};

const onKeyboardClose = () => {
  showKeyboard.value = false;
  const field = currentField.value;
  if (field && fieldConfig[field]?.validate) {
    fieldConfig[field].validate();
  }
  currentField.value = "";
};

const closeKeyboard = () => {
  setTimeout(() => {
    showKeyboard.value = false;
    currentField.value = "";
  }, 100);
};

// ========== 加载银行列表 ==========
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    const banks = res.data || res || [];
    bankColumns.value = banks
      .filter((bank) => bank.name && bank.name.endsWith("银行"))
      .map((bank) => ({
        text: bank.name,
        value: bank.id,
      }));
  } catch (e) {
    bankColumns.value = [];
  }
};

const filterNumber = (value) => {
  return value.replace(/\D/g, "");
};

const onCardLevelConfirm = ({ selectedOptions }) => {
  formData.cardLevel = selectedOptions[0].value;
  showCardLevelPicker.value = false;
};

const onBankConfirm = ({ selectedOptions }) => {
  const selected = selectedOptions[0];
  formData.bankId = selected.value;
  formData.bankName = selected.text;
  showBankPicker.value = false;
};

const onMainSubConfirm = ({ selectedOptions }) => {
  formData.mainSub = selectedOptions[0].value;
  showMainSubPicker.value = false;
};

const onCardOrgConfirm = (col) => {
  formData.cardOrg = col.value;
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

const onStartDateConfirm = ({ selectedValues }) => {
  formData.billStartDate = selectedValues.join("-");
  showStartDatePicker.value = false;
};

const onEndDateConfirm = ({ selectedValues }) => {
  formData.billEndDate = selectedValues.join("-");
  showEndDatePicker.value = false;
};

// ========== 步骤控制 ==========
const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
};

const nextStep = () => {
  if (currentStep.value === 0) {
    if (
      !formData.bankId ||
      !formData.last4No ||
      !formData.cardBin ||
      !formData.openDate ||
      !formData.expireDate
    ) {
      showToast("请填写完整的卡片基本信息");
      return;
    }
    validateLast4No();
    validateCardBin();
    if (!formData.last4No || !formData.cardBin) return;
  }

  if (currentStep.value === 1) {
    if (
      !formData.billDay ||
      !formData.repayDay ||
      !formData.annualFee ||
      !formData.feeFreeRule ||
      !formData.creditLimit ||
      !formData.tempLimit
    ) {
      showToast("请填写完整的信用卡专属信息（含额度）");
      return;
    }
    validateBillDay();
    validateRepayDay();
    if (!formData.billDay || !formData.repayDay) return;
  }

  if (currentStep.value < 3) {
    currentStep.value++;
  }
};

const onCancel = async () => {
  if (hasData.value) {
    try {
      await showConfirmDialog({
        title: "提示",
        message: "当前有未保存的数据，确定要取消吗？",
      });
      router.back();
    } catch {}
  } else {
    router.back();
  }
};

const onSaveDraft = () => {
  try {
    localStorage.setItem("creditCardDraft", JSON.stringify(formData));
    showToast("草稿已保存");
  } catch (e) {
    showToast("保存草稿失败");
  }
};

const loadDraft = () => {
  try {
    const draft = localStorage.getItem("creditCardDraft");
    if (draft) {
      const data = JSON.parse(draft);
      Object.assign(formData, data);
      showToast("已加载草稿");
    }
  } catch (e) {}
};

const hasData = computed(() => {
  return (
    formData.bankId ||
    formData.last4No ||
    formData.creditLimit ||
    formData.billAmount
  );
});

const submitCard = async () => {
  try {
    loading.value = true;
    showLoadingToast({ message: "创建卡片中...", forbidClick: true });

    const cardData = {
      bankId: formData.bankId,
      cardType: "credit",
      last4No: formData.last4No,
      cardBin: formData.cardBin,
      openDate: formData.openDate,
      expireDate: formData.expireDate,
      sourceFrom: formData.sourceFrom,
      billDay: Number(formData.billDay),
      repayDay: Number(formData.repayDay),
      annualFee: Number(formData.annualFee),
      feeFreeRule: formData.feeFreeRule,
      cardLevel: formData.cardLevel || "白金卡",
      mainSub: formData.mainSub,
      cardOrg: formData.cardOrg,
      cardLength: formData.cardLength || "16",
      alias: formData.alias,
      currency: formData.currency,
      status: formData.status,
      isDefault: formData.isDefault,
      isHide: formData.isHide,
      tag: formData.tag,
      remark: formData.remark,
      color: formData.color,
      // 额度信息（信用卡专属）
      creditLimit: Number(formData.creditLimit),
      tempLimit: formData.tempLimit ? Number(formData.tempLimit) : 0,
      pointsRate: formData.pointsRate ? Number(formData.pointsRate) : 1,
      remindSwitch: formData.remindSwitch,
      remindDays: formData.remindDays ? Number(formData.remindDays) : 3,
    };

    const cardRes = await createCard(cardData);

    if (cardRes.status === 201 || cardRes.status === 200) {
      localStorage.removeItem("creditCardDraft");
      closeToast();
      showToast({
        message: "创建成功！",
        onClose: () => {
          router.replace("/card");
        },
      });
    } else {
      closeToast();
      showToast("创建失败，请重试");
    }
  } catch (error) {
    closeToast();
    showToast(error.message || "创建失败，请重试");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadBankList();
  loadDraft();
});
</script>

<style scoped>
.page-credit-full {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 80px;
}

.steps-wrap {
  background: var(--theme-bg-secondary);
  padding: 16px;
  display: flex;
  justify-content: center;
}

.step-indicator {
  font-size: 16px;
  color: var(--van-green, #07c160);
  font-weight: 600;
}

.step-content {
  padding: 12px 0;
}

.step-panel {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.form-section {
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  padding: 12px 16px 8px;
  display: flex;
  align-items: center;
}

.credit-title {
  color: var(--van-danger-color, #ee0a24);
}

.confirm-title {
  color: var(--van-green, #07c160);
}

.required-hint {
  font-size: 12px;
  color: var(--van-danger-color, #ee0a24);
  margin-left: 8px;
}

.tip-box {
  display: flex;
  align-items: flex-start;
  background: var(--theme-bg-primary);
  margin: 12px 16px;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  line-height: 1.6;
}

.tip-box .van-icon {
  margin-right: 8px;
  margin-top: 2px;
  color: var(--theme-primary);
}

.tip-box.tip-highlight {
  background: var(--van-orange-bg, #fff7e6);
  border: 1px solid rgba(255, 151, 106, 0.4);
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

.color-custom {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  padding: 0;
  overflow: hidden;
}

.color-input::-webkit-color-swatch-wrapper {
  padding: 0;
}

.color-input::-webkit-color-swatch {
  border: 1px solid var(--theme-border);
  border-radius: 50%;
}

/* 底部按钮 */
.footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--theme-bg-secondary);
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom));
  display: flex;
  gap: 12px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.footer-bar .van-button {
  flex: 1;
  height: 44px;
}

.prev-btn {
  flex: 0 0 120px;
}

.next-btn,
.submit-btn {
  flex: 1;
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
