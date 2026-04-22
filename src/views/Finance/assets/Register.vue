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
          <div class="rate-item">
            <span>USD</span>
            <van-field
              v-model.number="exchangeRates.USD"
              type="number"
              placeholder="0.0000"
              :max="999.9999"
              :min="0"
              step="0.0001"
            />
          </div>
          <div class="rate-item">
            <span>HKD</span>
            <van-field
              v-model.number="exchangeRates.HKD"
              type="number"
              placeholder="0.0000"
              :max="999.9999"
              :min="0"
              step="0.0001"
            />
          </div>
          <div class="rate-item">
            <span>GBP</span>
            <van-field
              v-model.number="exchangeRates.GBP"
              type="number"
              placeholder="0.0000"
              :max="999.9999"
              :min="0"
              step="0.0001"
            />
          </div>
          <div class="rate-item">
            <span>EUR</span>
            <van-field
              v-model.number="exchangeRates.EUR"
              type="number"
              placeholder="0.0000"
              :max="999.9999"
              :min="0"
              step="0.0001"
            />
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
          </div>
        </div>
      </div>
      <div class="section-total">
        <span>境外资产折合（CNY）</span>
        <span class="total-value">¥{{ formatAmount(offshoreTotal) }}</span>
      </div>
    </div>

    <!-- 信用卡欠款 -->
    <div class="asset-section debt-section">
      <div class="section-header">
        <span class="section-title">信用卡 / 负债</span>
        <van-icon name="plus" class="add-icon" @click="openAddPopup('debt')" />
      </div>
      <div class="section-content">
        <div v-if="debtList.length === 0" class="empty-tip">
          暂无负债，点击 + 添加
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
            <div class="item-amount danger">
              ¥{{ formatAmount(item.amount) }}
            </div>
          </div>
        </div>
      </div>
      <div class="section-total">
        <span>负债合计</span>
        <span class="total-value danger">¥{{ formatAmount(debtTotal) }}</span>
      </div>
    </div>

    <!-- 提交 -->
    <div class="submit-wrap">
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
            placeholder="请输入金额"
            :max="999999999.999"
            :min="0"
            step="0.001"
          />
          <van-field
            v-model="balanceForm.remark"
            label="备注"
            placeholder="选填"
          />

          <div v-if="editBalanceId" class="delete-row">
            <van-button type="danger" size="small" block @click="deleteBalance"
              >删除此项</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button size="small" @click="showBalancePopup = false"
            >取消</van-button
          >
          <van-button type="primary" size="small" @click="handleAddBalance"
            >保存</van-button
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
            :max="999999999.999"
            :min="0"
            step="0.001"
          />
          <van-field
            v-model="offshoreForm.currency"
            label="币种"
            disabled
            placeholder="自动带出"
          />

          <div v-if="editOffshoreId" class="delete-row">
            <van-button type="danger" size="small" block @click="deleteOffshore"
              >删除此项</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button size="small" @click="showOffshorePopup = false"
            >取消</van-button
          >
          <van-button type="primary" size="small" @click="handleAddOffshore"
            >保存</van-button
          >
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
            :max="999999999.999"
            :min="0"
            step="0.001"
          />
          <van-field
            v-model="debtForm.remark"
            label="备注"
            placeholder="选填"
          />

          <div v-if="editDebtId" class="delete-row">
            <van-button type="danger" size="small" block @click="deleteDebt"
              >删除此项</van-button
            >
          </div>
        </div>
        <div class="popup-footer">
          <van-button size="small" @click="showDebtPopup = false"
            >取消</van-button
          >
          <van-button type="primary" size="small" @click="handleAddDebt"
            >保存</van-button
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
import { useRoute } from "vue-router";
import { createAssetRegister, updateAssetRegister } from "@/utils/api/asset";

const route = useRoute();

const loading = ref(false);

// ========== 汇率限制 0~999.9999 ==========
const exchangeRates = ref({ USD: "", HKD: "", GBP: "", EUR: "" });

// 判断是否有有效汇率
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
const balanceTypeColumns = balanceTypes.map((t) => ({
  text: t.text,
  value: t.value,
}));
const getBalanceName = (key) =>
  balanceTypes.find((t) => t.value === key)?.text || key;
const balanceTotal = computed(() =>
  balanceList.value.reduce((s, i) => s + Number(i.amount || 0), 0)
);

const openBalancePopup = () => {
  editBalanceId.value = null;
  balanceForm.value = { type: "", customName: "", amount: "", remark: "" };
  showBalancePopup.value = true;
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
  showToast("已删除");
};
const onBalanceTypeConfirm = ({ selectedOptions }) => {
  balanceForm.value.type = selectedOptions[0].value;
  showBalanceTypePicker.value = false;
};
const handleAddBalance = async () => {
  if (!balanceForm.value.amount) return showToast("请输入金额");
  const data = { ...balanceForm.value, amount: Number(balanceForm.value.amount) };
  if (editBalanceId.value) {
    const idx = balanceList.value.findIndex((i) => i.id === editBalanceId.value);
    if (idx !== -1) balanceList.value[idx] = { ...data, id: editBalanceId.value };
    showBalancePopup.value = false;
    return;
  }
  // 新增时检查同类型是否已存在
  const duplicate = balanceList.value.find((i) => i.type === data.type);
  if (duplicate) {
    try {
      await showConfirmDialog({
        title: "确认添加",
        message: `已存在「${getBalanceName(data.type)}」类型，是否继续添加？`,
      });
    } catch {
      return; // 用户取消
    }
  }
  balanceList.value.push({ ...data, id: `bl_${Date.now()}` });
  showBalancePopup.value = false;
};

// ========== 境外资产（编辑+删除） ==========
const offshoreList = ref([]);
const showOffshorePopup = ref(false);
const showOffshoreTypePicker = ref(false);
const offshoreForm = ref({
  type: "",
  customName: "",
  amount: "",
  currency: "",
});
const editOffshoreId = ref(null);

const offshoreTypes = [
  { text: "工商银行(港)", value: "ICBCA", currency: "HKD" },
  { text: "中国银行(港)", value: "BOCA", currency: "HKD" },
  { text: "汇丰银行(港)", value: "HSBC", currency: "HKD" },
  { text: "Wise", value: "Wise", currency: "USD" },
  { text: "IBKR", value: "IBKR", currency: "USD" },
  { text: "盈透证券", value: "Schwab", currency: "USD" },
  { text: "其他港币", value: "OtherHKD", currency: "HKD" },
  { text: "其他美元", value: "OtherUSD", currency: "USD" },
  { text: "其他欧元", value: "OtherEUR", currency: "EUR" },
  { text: "其他", value: "Other", currency: "" },
];
const offshoreTypeColumns = offshoreTypes.map((t) => ({
  text: `${t.text} (${t.currency})`,
  value: t.value,
}));
const getOffshoreName = (key) =>
  offshoreTypes.find((t) => t.value === key)?.text || key;
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
  showToast("已删除");
};
const onOffshoreTypeConfirm = ({ selectedOptions }) => {
  const info = offshoreTypes.find((t) => t.value === selectedOptions[0].value);
  offshoreForm.value.type = info.value;
  offshoreForm.value.currency = info.currency;
  showOffshoreTypePicker.value = false;
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
  const duplicate = offshoreList.value.find((i) => i.type === data.type);
  if (duplicate) {
    try {
      await showConfirmDialog({
        title: "确认添加",
        message: `已存在「${getOffshoreName(data.type)}」类型，是否继续添加？`,
      });
    } catch {
      return; // 用户取消
    }
  }
  offshoreList.value.push({ ...data, id: `of_${Date.now()}` });
  showOffshorePopup.value = false;
};

// ========== 负债（编辑+删除） ==========
const debtList = ref([]);
const showDebtPopup = ref(false);
const showDebtTypePicker = ref(false);
const debtForm = ref({ type: "", customName: "", amount: "", remark: "" });
const editDebtId = ref(null);

const debtTypes = [
  { text: "工商银行信用卡", value: "ICBC" },
  { text: "农业银行信用卡", value: "ABC" },
  { text: "建设银行信用卡", value: "CCB" },
  { text: "中国银行信用卡", value: "BOC" },
  { text: "招商银行信用卡", value: "CMBC" },
  { text: "浦发信用卡", value: "SPDB" },
  { text: "花呗", value: "Huabei" },
  { text: "借呗", value: "Jiebei" },
  { text: "京东白条", value: "JD" },
  { text: "美团月付", value: "Meituan" },
  { text: "其他", value: "Other" },
];
const debtTypeColumns = debtTypes.map((t) => ({
  text: t.text,
  value: t.value,
}));
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
  showToast("已删除");
};
const onDebtTypeConfirm = ({ selectedOptions }) => {
  debtForm.value.type = selectedOptions[0].value;
  showDebtTypePicker.value = false;
};
const handleAddDebt = async () => {
  if (!debtForm.value.amount) return showToast("请输入金额");
  const data = { ...debtForm.value, amount: Number(debtForm.value.amount) };
  if (editDebtId.value) {
    const idx = debtList.value.findIndex((i) => i.id === editDebtId.value);
    if (idx !== -1) debtList.value[idx] = { ...data, id: editDebtId.value };
    showDebtPopup.value = false;
    return;
  }
  // 新增时检查同类型是否已存在
  const duplicate = debtList.value.find((i) => i.type === data.type);
  if (duplicate) {
    try {
      await showConfirmDialog({
        title: "确认添加",
        message: `已存在「${getDebtName(data.type)}」类型，是否继续添加？`,
      });
    } catch {
      return; // 用户取消
    }
  }
  debtList.value.push({ ...data, id: `db_${Date.now()}` });
  showDebtPopup.value = false;
};

// ========== 通用 ==========
const openAddPopup = (type) => {
  if (type === "balance") openBalancePopup();
  if (type === "offshore") openOffshorePopup();
  if (type === "debt") openDebtPopup();
};

const finalBalance = computed(
  () => balanceTotal.value + offshoreTotal.value - debtTotal.value
);
const formatAmount = (amount) =>
  (Number(amount) || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

// ========== 提交 ==========
const handleSubmit = async () => {
  const totalAsset = balanceTotal.value + offshoreTotal.value;
  const debt = debtTotal.value;
  const net = finalBalance.value;

  if (totalAsset === 0 && debt === 0)
    return showToast("至少填写一项资产或负债");

  // 确认框
  const isEdit = !!route.query.id;
  try {
    await showConfirmDialog({
      title: "确认提交",
      message: isEdit ? "确定要更新这条资产登记记录吗？" : "确定要提交这条资产登记记录吗？",
      confirmButtonColor: "#07c160",
    });
  } catch {
    return; // 用户取消
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
      total_balance: Math.round(net * 100) / 100,
      asset_details: asset_details,
      remark: "手动资产登记",
    };

    // 有 id 则更新，无 id 则创建
    const recordId = route.query.id;
    if (recordId) {
      await updateAssetRegister(recordId, payload);
    } else {
      await createAssetRegister(payload);
    }

    closeToast();
    showSuccessToast({ message: "保存成功", onClose: () => history.back() });
  } catch (e) {
    closeToast();
    showToast(e.message || "保存失败");
  } finally {
    loading.value = false;
  }
};

// ========== 初始化 - 接收从列表页传来的数据 ==========
const initFromQuery = () => {
  const { data, rates } = route.query;

  if (data) {
    try {
      const assetDetails = JSON.parse(data);
      if (assetDetails.balance) {
        // 兼容旧的对象格式和新的数组格式
        const bal = assetDetails.balance;
        balanceList.value = Array.isArray(bal)
          ? bal
          : Object.entries(bal).map(([key, val]) => ({ ...val, id: val.id || `bl_${key}` }));
      }
      if (assetDetails.offshore) {
        const off = assetDetails.offshore;
        offshoreList.value = Array.isArray(off)
          ? off
          : Object.entries(off).map(([key, val]) => ({ ...val, id: val.id || `of_${key}` }));
      }
      if (assetDetails.debt) {
        const dbt = assetDetails.debt;
        debtList.value = Array.isArray(dbt)
          ? dbt
          : Object.entries(dbt).map(([key, val]) => ({ ...val, id: val.id || `db_${key}` }));
      }
    } catch (e) {
      console.error("解析资产数据失败", e);
    }
  }

  if (rates) {
    try {
      const parsedRates = JSON.parse(rates);
      Object.entries(parsedRates).forEach(([key, val]) => {
        exchangeRates.value[key] = String(val);
      });
    } catch (e) {
      console.error("解析汇率数据失败", e);
    }
  }
};

onMounted(() => {
  initFromQuery();
});
</script>

<style scoped>
.page-assets-register {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 100px;
}

.balance-card {
  background: linear-gradient(135deg, #4a6cf7 0%, #7c4dff 100%);
  color: #fff;
  text-align: center;
  padding: 36px 20px;
  margin: 16px;
  border-radius: 20px;
  box-shadow: 0 8px 25px rgba(74, 108, 247, 0.25);
}
.balance-label {
  font-size: 15px;
  opacity: 0.92;
  margin-bottom: 8px;
}
.balance-value {
  display: flex;
  justify-content: center;
  align-items: baseline;
}
.currency {
  font-size: 22px;
  margin-right: 6px;
}
.amount {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 1px;
}

.asset-section {
  background: #fff;
  margin: 16px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f3f4f6;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.add-icon {
  font-size: 19px;
  color: #4a6cf7;
}

.exchange-rate-section {
  padding: 12px 16px;
  background: #f9fafb;
}
.rate-title {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
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

.section-content {
  padding: 16px;
  min-height: 70px;
}
.empty-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 20px 0;
}
.item-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.asset-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}
.item-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.item-name {
  font-size: 15px;
  color: #1f2937;
  font-weight: 500;
}
.item-currency {
  font-size: 12px;
  color: #6b7280;
}
.convert {
  color: #4a6cf7;
  margin-left: 4px;
  font-weight: 500;
}
.item-remark {
  font-size: 12px;
  color: #9ca3af;
}
.item-amount {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.item-amount.danger {
  color: #f43f5e;
}

.section-total {
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
  background: #f9fafb;
  font-size: 14px;
  color: #4b5563;
  font-weight: 500;
}
.total-value {
  font-weight: 700;
  color: #1f2937;
}
.total-value.danger {
  color: #f43f5e;
}

.submit-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16px;
  background: #fff;
  box-shadow: 0 -2px 15px rgba(0, 0, 0, 0.05);
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
.delete-row {
  margin-top: 12px;
}
.popup-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
</style>
