<template>
  <div class="page-assets-register">
    <!-- 最终余额卡片 -->
    <div class="balance-card">
      <div class="balance-label">最终净资产</div>
      <div class="balance-value">
        <span class="currency">¥</span>
        <span class="amount">{{ formatAmount(finalBalance) }}</span>
      </div>
    </div>

    <!-- 余额区域 -->
    <div class="asset-section">
      <div class="section-header">
        <span class="section-title">境内资产</span>
        <van-icon
          name="plus"
          class="add-icon"
          @click="openAddPopup('balance')"
        />
      </div>
      <div class="section-content">
        <div v-if="balanceList.length === 0" class="empty-tip">
          暂无登记，点击 + 添加
        </div>
        <div v-else class="item-list">
          <div
            v-for="item in balanceList"
            :key="item.id"
            class="asset-item"
            @click="editBalance(item.id)"
          >
            <div class="item-info">
              <span class="item-name">{{
                item.customName || getBalanceName(item.type)
              }}</span>
              <span class="item-remark" v-if="item.remark">{{
                item.remark
              }}</span>
            </div>
            <div class="item-amount">¥{{ formatAmount(item.amount) }}</div>
            <div v-if="item.amount === 0" class="zero-warning">该项数据金额为0 请检查</div>
          </div>
        </div>
      </div>
      <div class="section-total">
        <span>境内资产小计</span>
        <span class="total-value">¥{{ formatAmount(balanceTotal) }}</span>
      </div>
    </div>

    <!-- 境外资产区域 -->
    <div class="asset-section">
      <div class="section-header">
        <span class="section-title">境外资产</span>
        <van-icon
          name="plus"
          class="add-icon"
          @click="openAddPopup('offshore')"
        />
      </div>

      <div class="exchange-rate-section">
        <div class="rate-title">实时汇率（100外币 = ? 人民币）</div>
        <div class="rate-grid">
          <div
            v-for="rate in exchangeRateList"
            :key="rate.currency"
            class="rate-item"
          >
            <span>{{ rate.currency }}</span>
            <van-field
              v-model="rate.value"
              type="number"
              placeholder="0.0000"
              :formatter="formatRate"
              @input="onRateChange"
            />
            <van-icon
              v-if="rate.currency !== 'HKD'"
              name="cross"
              class="rate-delete"
              @click="deleteRate(rate.currency)"
            />
          </div>
          <div class="rate-item add-rate" @click="showAddRatePopup = true">
            <van-icon name="plus" />
          </div>
        </div>
      </div>

      <div class="section-content">
        <div v-if="offshoreList.length === 0" class="empty-tip">
          暂无登记，点击 + 添加
        </div>
        <div v-else class="item-list">
          <div
            v-for="item in offshoreList"
            :key="item.id"
            class="asset-item"
            @click="editOffshore(item.id)"
          >
            <div class="item-info">
              <span class="item-name">{{
                item.customName || getOffshoreName(item.type)
              }}</span>
              <span class="item-currency">
                {{ item.currency }} {{ formatAmount(item.amount) }}
                <span class="convert" v-if="hasValidRate(item.currency)">
                  ≈ ¥{{
                    formatAmount(convertToCNY(item.amount, item.currency))
                  }}
                </span>
              </span>
            </div>
            <div class="item-amount danger" v-if="hasValidRate(item.currency)">
              ¥{{ formatAmount(convertToCNY(item.amount, item.currency)) }}
            </div>
            <div class="item-amount" v-else>
              {{ item.currency }} {{ formatAmount(item.amount) }}
            </div>
            <div v-if="item.amount === 0" class="zero-warning">该项数据金额为0 请检查</div>
          </div>
        </div>
      </div>
      <div class="section-total">
        <span>境外资产小计</span>
        <span class="total-value">≈ ¥{{ formatAmount(offshoreTotal) }}</span>
      </div>
    </div>

    <!-- 负债区域 -->
    <div class="asset-section">
      <div class="section-header">
        <span class="section-title">负债</span>
        <van-icon
          name="plus"
          class="add-icon"
          @click="openAddPopup('debt')"
        />
      </div>
      <div class="section-content">
        <div v-if="debtList.length === 0" class="empty-tip">
          暂无登记，点击 + 添加
        </div>
        <div v-else class="item-list">
          <div
            v-for="item in debtList"
            :key="item.id"
            class="asset-item"
            @click="editDebt(item.id)"
          >
            <div class="item-info">
              <span class="item-name">{{
                item.customName || getDebtName(item.type)
              }}</span>
              <span class="item-remark" v-if="item.remark">{{
                item.remark
              }}</span>
            </div>
            <div class="item-amount danger">¥{{ formatAmount(item.amount) }}</div>
            <div v-if="item.amount === 0" class="zero-warning">该项数据金额为0 请检查</div>
          </div>
        </div>
      </div>
      <div class="section-total">
        <span>负债小计</span>
        <span class="total-value danger">¥{{ formatAmount(debtTotal) }}</span>
      </div>
    </div>

    <!-- 提交按钮 -->
    <div class="submit-section">
      <van-button
        type="primary"
        block
        round
        size="large"
        :loading="loading"
        @click="handleSubmit"
      >
        确认保存资产登记
      </van-button>
    </div>

    <!-- 余额弹窗 -->
    <van-popup v-model:show="showBalancePopup" position="bottom" round>
      <div class="add-popup">
        <div class="popup-header">
          <span>{{ editBalanceId ? "编辑境内资产" : "添加境内资产" }}</span>
          <van-icon name="cross" @click="showBalancePopup = false" />
        </div>
        <div class="popup-form">
          <van-field
            v-model="balanceForm.customName"
            label="自定义名称"
            placeholder="选填"
          />
          <van-field
            v-model="balanceForm.type"
            is-link
            readonly
            label="类型"
            placeholder="选择类型"
            @click="showBalanceTypePicker = true"
          />
          <van-field
            v-model.number="balanceForm.amount"
            type="number"
            label="金额"
            placeholder="输入人民币金额"
            :formatter="formatAmountInput"
          />
          <van-field
            v-model="balanceForm.remark"
            label="备注"
            placeholder="选填"
          />
          <div class="delete-row" v-if="editBalanceId">
            <van-button block round type="danger" @click="deleteBalance"
              >删除</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button block round @click="showBalancePopup = false"
            >取消</van-button
          >
          <van-button block round type="primary" @click="handleAddBalance"
            >确定</van-button
          >
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showBalanceTypePicker" position="bottom">
      <van-picker
        title="选择类型"
        :columns="balanceTypeColumns"
        @confirm="onBalanceTypeConfirm"
        @cancel="showBalanceTypePicker = false"
      />
    </van-popup>

    <!-- 添加自定义汇率弹窗 -->
    <van-popup v-model:show="showAddRatePopup" position="bottom" round>
      <div class="custom-currency-popup">
        <div class="popup-header">
          <span class="popup-title">添加常用汇率</span>
        </div>
        <van-cell-group inset>
          <van-field
            v-model="newCustomRate.code"
            label="币种代码"
            placeholder="如：KRW"
            label-width="70px"
            :formatter="formatCurrencyCode"
          />
          <van-field
            v-model="newCustomRate.value"
            label="汇率"
            placeholder="100外币 = ? CNY"
            label-width="70px"
            type="number"
            :formatter="formatRate"
          />
        </van-cell-group>
        <div class="popup-actions">
          <van-button block round @click="showAddRatePopup = false">取消</van-button>
          <van-button block round type="primary" @click="addCustomRate">确定</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showOffshoreTypePicker" position="bottom">
      <van-picker
        title="选择境外账户"
        :columns="offshoreTypeColumns"
        @confirm="onOffshoreTypeConfirm"
        @cancel="showOffshoreTypePicker = false"
      />
    </van-popup>

    <!-- 境外资产弹窗 -->
    <van-popup v-model:show="showOffshorePopup" position="bottom" round>
      <div class="add-popup">
        <div class="popup-header">
          <span>{{ editOffshoreId ? "编辑境外资产" : "添加境外资产" }}</span>
          <van-icon name="cross" @click="showOffshorePopup = false" />
        </div>
        <div class="popup-form">
          <van-field
            v-model="offshoreForm.customName"
            label="自定义名称"
            placeholder="选填"
          />
          <van-field
            v-model="offshoreForm.type"
            is-link
            readonly
            label="账户类型"
            placeholder="选择类型"
            @click="showOffshoreTypePicker = true"
          />
          <van-field
            v-model.number="offshoreForm.amount"
            type="number"
            label="金额"
            placeholder="输入外币金额"
            :formatter="formatAmountInput"
          />
          <van-field
            v-model="offshoreForm.currency"
            label="币种"
            disabled
            placeholder="自动带出"
          />
          <div v-if="editOffshoreId" class="delete-row">
            <van-button block round type="danger" @click="deleteOffshore"
              >删除</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button block round @click="showOffshorePopup = false"
            >取消</van-button
          >
          <van-button block round type="primary" @click="handleAddOffshore"
            >确定</van-button
          >
        </div>
      </div>
    </van-popup>

    <!-- 负债弹窗 -->
    <van-popup v-model:show="showDebtPopup" position="bottom" round>
      <div class="add-popup">
        <div class="popup-header">
          <span>{{ editDebtId ? "编辑负债" : "添加负债" }}</span>
          <van-icon name="cross" @click="showDebtPopup = false" />
        </div>
        <div class="popup-form">
          <van-field
            v-model="debtForm.customName"
            label="自定义名称"
            placeholder="选填"
          />
          <van-field
            v-model="debtForm.type"
            is-link
            readonly
            label="类型"
            placeholder="选择卡/账户"
            @click="showDebtTypePicker = true"
          />
          <van-field
            v-model.number="debtForm.amount"
            type="number"
            label="欠款金额"
            placeholder="请输入金额"
            :formatter="formatAmountInput"
          />
          <van-field
            v-model="debtForm.remark"
            label="备注"
            placeholder="选填"
          />
          <div class="delete-row" v-if="editDebtId">
            <van-button block round type="danger" @click="deleteDebt"
              >删除</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button block round @click="showDebtPopup = false"
            >取消</van-button
          >
          <van-button block round type="primary" @click="handleAddDebt"
            >确定</van-button
          >
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showDebtTypePicker" position="bottom">
      <van-picker
        title="选择负债类型"
        :columns="debtTypeColumns"
        @confirm="onDebtTypeConfirm"
        @cancel="showDebtTypePicker = false"
      />
    </van-popup>

    <!-- 加载状态 -->
    <div v-if="pageLoading" class="page-loading">
      <van-loading type="spinner" size="48px">加载中...</van-loading>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import {
  showToast,
  showSuccessToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from "vant";
import { useRoute, useRouter } from "vue-router";
import { getAssetRegister, updateAssetRegister } from "@/utils/api/asset";

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const pageLoading = ref(true);
const recordId = ref(null);

// ========== 汇率限制 0~999.9999 ==========
const exchangeRateList = ref([
  { currency: "HKD", value: "" },
  { currency: "USD", value: "" },
  { currency: "GBP", value: "" },
  { currency: "EUR", value: "" },
]);

const exchangeRates = computed(() => {
  const obj = {};
  exchangeRateList.value.forEach(item => {
    obj[item.currency] = item.value;
  });
  return obj;
});

const hasValidRate = (currency) => {
  if (!currency) return false;
  const rate = exchangeRates.value[currency];
  return rate !== null && rate !== undefined && rate !== "";
};

const getRate = (currency) => {
  if (!hasValidRate(currency)) return null;
  return Number(exchangeRates.value[currency]);
};

const convertToCNY = (amount, currency) => {
  if (!hasValidRate(currency)) return Number(amount) || 0;
  const rate = getRate(currency);
  return Math.round(((Number(amount) * rate) / 100) * 100) / 100;
};

const onRateChange = () => {};

const deleteRate = (currency) => {
  if (currency === "HKD") {
    showToast("HKD不可删除");
    return;
  }
  const isUsed = offshoreList.value.some(item => item.currency === currency && item.amount);
  if (isUsed) {
    showToast("该汇率已被使用，无法删除");
    return;
  }
  const index = exchangeRateList.value.findIndex(r => r.currency === currency);
  if (index >= 0) {
    exchangeRateList.value.splice(index, 1);
  }
};

const showAddRatePopup = ref(false);
const newCustomRate = ref({ code: "", value: "" });

const formatCurrencyCode = (value) => {
  return value.replace(/[^a-zA-Z]/g, '').toUpperCase();
};

const formatRate = (val) => {
  if (!val) return '';
  let num = val.replace(/[^\d.]/g, '');
  num = num.replace(/\.{2,}/g, '.');
  num = num.replace('.', '#').replace(/\./g, '').replace('#', '.');
  num = num.replace(/^(\d+)(\.\d{0,4})?.*$/, '$1$2');
  if (parseFloat(num) > 999.9999) {
    num = '999.9999';
  }
  return num;
};

const addCustomRate = () => {
  if (!newCustomRate.value.code || !newCustomRate.value.value) {
    showToast("请输入币种代码和汇率");
    return;
  }
  const code = newCustomRate.value.code.toUpperCase();
  const existingIndex = exchangeRateList.value.findIndex(r => r.currency === code);
  if (existingIndex >= 0) {
    exchangeRateList.value[existingIndex].value = newCustomRate.value.value;
  } else {
    exchangeRateList.value.push({
      currency: code,
      value: newCustomRate.value.value
    });
  }
  showAddRatePopup.value = false;
  newCustomRate.value = { code: "", value: "" };
};

// ========== 余额（编辑+删除） ==========
const balanceList = ref([]);
const showBalancePopup = ref(false);
const showBalanceTypePicker = ref(false);
const balanceForm = ref({ type: "", customName: "", amount: "", remark: "" });
const editBalanceId = ref(null);

const balanceTypes = [
  { text: "微信余额", value: "wechat" },
  { text: "支付宝余额", value: "alipay" },
  { text: "银行活期", value: "bank" },
  { text: "理财", value: "wealth" },
  { text: "基金", value: "fund" },
  { text: "股票", value: "stock" },
  { text: "现金", value: "cash" },
  { text: "其他", value: "other" },
];
const balanceTypeColumns = balanceTypes;
const getBalanceName = (key) =>
  balanceTypes.find((t) => t.value === key)?.text || key;
const balanceTotal = computed(() =>
  balanceList.value.reduce((s, i) => s + Number(i.amount || 0), 0)
);

const openAddPopup = (type) => {
  if (type === "balance") {
    editBalanceId.value = null;
    balanceForm.value = { type: "", customName: "", amount: "", remark: "" };
    showBalancePopup.value = true;
  } else if (type === "offshore") {
    openOffshorePopup();
  } else if (type === "debt") {
    openDebtPopup();
  }
};
const editBalance = (id) => {
  const item = balanceList.value.find((i) => i.id === id);
  if (!item) return;
  editBalanceId.value = id;
  balanceForm.value = { ...item };
  showBalancePopup.value = true;
};
const deleteBalance = () => {
  if (editBalanceId.value) {
    balanceList.value = balanceList.value.filter((i) => i.id !== editBalanceId.value);
  }
  showBalancePopup.value = false;
};
const handleAddBalance = () => {
  if (!balanceForm.value.amount) return showToast("请输入金额");
  const data = { ...balanceForm.value, amount: Number(balanceForm.value.amount) };
  if (editBalanceId.value) {
    const idx = balanceList.value.findIndex((i) => i.id === editBalanceId.value);
    if (idx !== -1) balanceList.value[idx] = { ...data, id: editBalanceId.value };
    showBalancePopup.value = false;
    return;
  }
  balanceList.value.push({ ...data, id: `bl_${Date.now()}` });
  showBalancePopup.value = false;
};
const onBalanceTypeConfirm = ({ selectedOptions }) => {
  const idx = balanceTypes.findIndex(
    (t) => t.text === selectedOptions[0]
  );
  balanceForm.value.type = balanceTypes[idx].value;
  showBalanceTypePicker.value = false;
};

// ========== 境外资产（编辑+删除） ==========
const offshoreList = ref([]);
const showOffshorePopup = ref(false);
const showOffshoreTypePicker = ref(false);
const offshoreForm = ref({ type: "", customName: "", amount: "", currency: "" });
const editOffshoreId = ref(null);

const offshoreTypes = computed(() => {
  const baseType = { text: "其他港币", value: "OtherHKD", currency: "HKD" };
  const additionalTypes = exchangeRateList.value
    .filter(rate => rate.value && rate.currency !== "HKD")
    .map(rate => ({
      text: `其他${rate.currency}`,
      value: `Other${rate.currency}`,
      currency: rate.currency
    }));
  return [baseType, ...additionalTypes];
});

const offshoreTypeColumns = computed(() => offshoreTypes.value);

const getOffshoreName = (key) => {
  if (key.startsWith("Other")) return `其他${key.replace("Other", "")}`;
  return offshoreTypes.value.find((t) => t.value === key)?.text || key;
};
const offshoreTotal = computed(() =>
  offshoreList.value.reduce((s, i) => s + convertToCNY(i.amount, i.currency), 0)
);

const openOffshorePopup = () => {
  editOffshoreId.value = null;
  offshoreForm.value = { type: "", customName: "", amount: "", currency: "" };
  showOffshorePopup.value = true;
};
const editOffshore = (id) => {
  const item = offshoreList.value.find((i) => i.id === id);
  if (!item) return;
  editOffshoreId.value = id;
  offshoreForm.value = { ...item };
  showOffshorePopup.value = true;
};
const deleteOffshore = () => {
  if (editOffshoreId.value) {
    offshoreList.value = offshoreList.value.filter((i) => i.id !== editOffshoreId.value);
  }
  showOffshorePopup.value = false;
};
const handleAddOffshore = async () => {
  if (!offshoreForm.value.amount) return showToast("请输入金额");
  const data = { ...offshoreForm.value, amount: Number(offshoreForm.value.amount) };
  if (editOffshoreId.value) {
    const idx = offshoreList.value.findIndex((i) => i.id === editOffshoreId.value);
    if (idx !== -1) offshoreList.value[idx] = { ...data, id: editOffshoreId.value };
    showOffshorePopup.value = false;
    return;
  }
  // 新增时检查同类型是否已存在
  const exists = offshoreList.value.some((i) => i.type === data.type && !i.customName);
  if (exists) {
    try {
      await showConfirmDialog({
        title: "提示",
        message: `已存在「${getOffshoreName(data.type)}」类型，是否继续添加？`,
      });
    } catch {
      return;
    }
  }
  offshoreList.value.push({ ...data, id: `of_${Date.now()}` });
  showOffshorePopup.value = false;
};
const onOffshoreTypeConfirm = ({ selectedOptions }) => {
  const item = selectedOptions[0];
  offshoreForm.value.type = item.value;
  offshoreForm.value.currency = item.currency || item.value.replace("Other", "");
  showOffshoreTypePicker.value = false;
};

// ========== 负债（编辑+删除） ==========
const debtList = ref([]);
const showDebtPopup = ref(false);
const showDebtTypePicker = ref(false);
const debtForm = ref({ type: "", customName: "", amount: "", remark: "" });
const editDebtId = ref(null);

const debtTypes = [
  { text: "信用卡", value: "CreditCard" },
  { text: "花呗", value: "Huabei" },
  { text: "借呗", value: "Jiebei" },
  { text: "京东白条", value: "JD" },
  { text: "美团月付", value: "Meituan" },
  { text: "其他", value: "Other" },
];
const debtTypeColumns = debtTypes;
const getDebtName = (key) =>
  debtTypes.find((t) => t.value === key)?.text || key;
const debtTotal = computed(() =>
  debtList.value.reduce((s, i) => s + Number(i.amount || 0), 0)
);

const openDebtPopup = () => {
  editDebtId.value = null;
  debtForm.value = { type: "", customName: "", amount: "", remark: "" };
  showDebtPopup.value = true;
};
const editDebt = (id) => {
  const item = debtList.value.find((i) => i.id === id);
  if (!item) return;
  editDebtId.value = id;
  debtForm.value = { ...item };
  showDebtPopup.value = true;
};
const deleteDebt = () => {
  if (editDebtId.value) {
    debtList.value = debtList.value.filter((i) => i.id !== editDebtId.value);
  }
  showDebtPopup.value = false;
};
const handleAddDebt = () => {
  if (!debtForm.value.amount) return showToast("请输入金额");
  const data = { ...debtForm.value, amount: Number(debtForm.value.amount) };
  if (editDebtId.value) {
    const idx = debtList.value.findIndex((i) => i.id === editDebtId.value);
    if (idx !== -1) debtList.value[idx] = { ...data, id: editDebtId.value };
    showDebtPopup.value = false;
    return;
  }
  debtList.value.push({ ...data, id: `db_${Date.now()}` });
  showDebtPopup.value = false;
};
const onDebtTypeConfirm = ({ selectedOptions }) => {
  const idx = debtTypes.findIndex(
    (t) => t.text === selectedOptions[0]
  );
  debtForm.value.type = debtTypes[idx].value;
  showDebtTypePicker.value = false;
};

// ========== 计算 ==========
const finalBalance = computed(() =>
  Math.round((balanceTotal.value + offshoreTotal.value - debtTotal.value) * 100) / 100
);

// ========== 格式化 ==========
const formatAmount = (val) => {
  const num = Number(val) || 0;
  return num.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

const formatAmountInput = (val) => {
  if (!val) return '';
  let num = val.replace(/[^\d.]/g, '');
  num = num.replace(/\.{2,}/g, '.');
  num = num.replace('.', '#').replace(/\./g, '').replace('#', '.');
  num = num.replace(/^(\d+)(\.\d{0,3})?.*$/, '$1$2');
  if (parseFloat(num) > 999999999.999) {
    num = '999999999.999';
  }
  return num;
};

// ========== 提交 ==========
const handleSubmit = async () => {
  const totalAsset = balanceTotal.value + offshoreTotal.value;
  const debt = debtTotal.value;

  if (totalAsset === 0 && debt === 0)
    return showToast("至少填写一项资产或负债");

  try {
    await showConfirmDialog({
      title: "确认提交",
      message: "确定要更新这条资产登记记录吗？",
      confirmButtonColor: "#07c160",
    });
  } catch {
    return;
  }

  loading.value = true;
  showLoadingToast({ message: "保存中...", forbidClick: true });

  try {
    const asset_details = {
      balance: balanceList.value,
      offshore: offshoreList.value,
      debt: debtList.value,
      exchangeRates: exchangeRates.value,
    };

    const payload = {
      total_asset: Math.round(totalAsset * 100) / 100,
      credit_debt: Math.round(debt * 100) / 100,
      total_balance: Math.round(finalBalance.value * 100) / 100,
      asset_details: asset_details,
      remark: "手动资产登记",
    };

    await updateAssetRegister(recordId.value, payload);

    closeToast();
    showSuccessToast({ message: "保存成功", onClose: () => router.back() });
  } catch (e) {
    closeToast();
    showToast(e.message || "保存失败");
  } finally {
    loading.value = false;
  }
};

// ========== 初始化 - 从 API 读取数据 ==========
const initFromApi = async () => {
  recordId.value = route.query.id;
  if (!recordId.value) {
    showToast("缺少记录ID");
    router.back();
    return;
  }

  try {
    const res = await getAssetRegister(recordId.value);
    const data = res.data || res;

    if (data.asset_details) {
      if (data.asset_details.balance) {
        balanceList.value = Array.isArray(data.asset_details.balance)
          ? data.asset_details.balance
          : Object.entries(data.asset_details.balance).map(([key, val]) => ({ ...val, type: key, id: val.id || `bl_${key}` }));
      }
      if (data.asset_details.offshore) {
        offshoreList.value = Array.isArray(data.asset_details.offshore)
          ? data.asset_details.offshore
          : Object.entries(data.asset_details.offshore).map(([key, val]) => ({ ...val, type: key, id: val.id || `of_${key}` }));
      }
      if (data.asset_details.debt) {
        debtList.value = Array.isArray(data.asset_details.debt)
          ? data.asset_details.debt
          : Object.entries(data.asset_details.debt).map(([key, val]) => ({ ...val, type: key, id: val.id || `db_${key}` }));
      }
      if (data.asset_details.exchangeRates) {
        Object.entries(data.asset_details.exchangeRates).forEach(([key, val]) => {
          const rateItem = exchangeRateList.value.find(r => r.currency === key);
          if (rateItem) {
            rateItem.value = String(val);
          }
        });
      }
    }
  } catch (e) {
    console.error("加载资产数据失败", e);
    showToast("加载数据失败");
  } finally {
    pageLoading.value = false;
  }
};

onMounted(() => {
  initFromApi();
});
</script>

<style scoped>
.page-assets-register {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 100px;
}

.balance-card {
  background: linear-gradient(135deg, #07c160 0%, #10b981 100%);
  color: white;
  padding: 24px 20px;
  margin-bottom: 12px;
}

.balance-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.balance-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.balance-value .currency {
  font-size: 20px;
  font-weight: 600;
}

.balance-value .amount {
  font-size: 32px;
  font-weight: 700;
}

.asset-section {
  background: white;
  margin-bottom: 12px;
  padding: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.add-icon {
  font-size: 20px;
  color: #07c160;
  cursor: pointer;
}

.section-content {
  padding: 16px;
  min-height: 70px;
}

.empty-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
  padding: 20px 0;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.asset-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-name {
  font-size: 15px;
  color: #1f2937;
  font-weight: 500;
}

.item-remark {
  font-size: 12px;
  color: #9ca3af;
}

.item-currency {
  font-size: 12px;
  color: #6b7280;
}

.item-currency .convert {
  color: #07c160;
  margin-left: 4px;
}

.item-amount {
  font-size: 15px;
  font-weight: 600;
  color: #07c160;
}

.item-amount.danger {
  color: #ef4444;
}

.zero-warning {
  font-size: 12px;
  color: #ef4444;
  margin-top: 4px;
}

.section-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
  font-size: 14px;
  color: #6b7280;
  margin-top: 12px;
}

.total-value {
  font-weight: 600;
  color: #07c160;
}

.total-value.danger {
  color: #ef4444;
}

.submit-section {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: white;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.exchange-rate-section {
  margin-bottom: 16px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.rate-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 12px;
}

.rate-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.rate-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-item span {
  width: 40px;
  font-size: 13px;
  color: #374151;
}

.rate-delete {
  color: #999;
  cursor: pointer;
  font-size: 14px;
}

.rate-delete:active {
  color: #ee0a24;
}

.add-rate {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  background: #f5f5f5;
  border-radius: 4px;
  cursor: pointer;
  color: #666;
}

.add-rate:active {
  background: #eee;
}

.add-popup {
  padding: 20px;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}

.popup-header .van-icon {
  font-size: 20px;
  color: #9ca3af;
}

.popup-form {
  gap: 10px;
  display: flex;
  flex-direction: column;
}

.popup-footer {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.popup-footer .van-button {
  flex: 1;
}

.delete-row {
  margin-top: 12px;
}

.custom-currency-popup {
  padding: 20px;
  padding-bottom: 30px;
}

.custom-currency-popup .popup-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}

.custom-currency-popup .popup-header {
  margin-bottom: 20px;
}

.popup-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.popup-actions .van-button {
  flex: 1;
}

.page-loading {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.9);
  z-index: 999;
}
</style>
