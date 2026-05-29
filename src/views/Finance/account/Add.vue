﻿<template>
  <div class="page-finance-add" @click="showKeyboard = false">
    <!-- 顶部金额卡片 -->
    <div class="amount-card" @click.stop="showKeyboard = true">
      <!-- 币种选择 -->
      <div class="currency-selector" @click.stop="showCurrencyPicker = true">
        <span class="currency-name">{{ selectedCurrency.label }}</span>
        <van-icon name="arrow-down" />
      </div>
      <div class="label">
        请输入{{ isExpense ? "支出" : isIncome ? "收入" : "转账" }}金额
      </div>
      <div class="value">
        <span class="currency-symbol">{{ selectedCurrency.symbol }}</span>
        <span>{{ displayAmount || "0.00" }}</span>
      </div>
      <!-- 汇率换算提示（非人民币时显示） -->
      <div
        class="exchange-tip"
        v-if="selectedCurrency.code !== 'CNY' && displayAmount"
      >
        约 ¥ {{ formatMoney(exchangedAmount) }}
      </div>
    </div>

    <!-- 支出/收入切换 + 信息填写区域 -->
    <div class="info-section">
      <!-- 支出/收入/转账切换 -->
      <van-tabs v-model:active="type" animated swipeable @change="onTypeChange">
        <van-tab title="支出" name="expense" />
        <van-tab title="收入" name="income" />
        <van-tab title="转账" name="transfer" />
      </van-tabs>

      <!-- 信息填写表单项 -->
      <div class="form-items">
        <!-- ===== 常规收支 ===== -->
        <template v-if="!isTransfer">
          <!-- 分类选择 -->
          <van-cell title="分类" is-link @click="showCategoryPicker = true">
            <template #value>
              <span :class="{ placeholder: !selectedCategory }">
                {{ selectedCategory?.name || "请选择分类" }}
              </span>
            </template>
          </van-cell>

          <!-- 支付方式 -->
          <van-field
            v-model="selectedPayMethod"
            :label="isExpense ? '支出方式' : '收入方式'"
            placeholder="请输入或选择支付方式"
            @click-right-icon="showPayMethodPicker = true"
          >
            <template #right-icon>
              <van-icon name="arrow-down" @click="showPayMethodPicker = true" />
            </template>
          </van-field>

          <!-- 关联卡片 -->
          <van-cell
            v-if="showCardCell"
            :title="selectedPayMethod"
            is-link
            @click="showCardPicker = true"
          >
            <template #value>
              <span :class="{ placeholder: !selectedCard?.id }">
                {{ getCardDisplayText(selectedCard) }}
              </span>
            </template>
          </van-cell>

          <!-- 日期选择 -->
          <van-cell title="日期" is-link @click="showDatePicker = true">
            <template #value>
              <span>{{ formatDate(selectedDate) }}</span>
            </template>
          </van-cell>

          <!-- 汇率输入（非人民币时显示） -->
          <van-field
            v-if="selectedCurrency.code !== 'CNY'"
            v-model="exchangeRate"
            label="汇率"
            type="number"
            placeholder="如: 684.5125"
            @focus="showKeyboard = false"
          >
            <template #button>
              <span style="color: #969799">/100</span>
            </template>
          </van-field>

          <!-- 备注 -->
          <van-field
            v-model="remark"
            :label="isExpense ? '备注' : '说明'"
            placeholder="选填"
            clearable
          />
        </template>

        <!-- ===== 转账 ===== -->
        <template v-if="isTransfer">
          <!-- 转账模式切换 -->
          <div class="transfer-toggle">
            <span
              :class="['toggle-btn', { active: transferMode === 'external' }]"
              @click="transferMode = 'external'"
              >对外</span
            >
            <span
              :class="['toggle-btn', { active: transferMode === 'self' }]"
              @click="transferMode = 'self'"
              >自转</span
            >
            <span
              :class="['toggle-btn', { active: transferMode === 'withdraw' }]"
              @click="transferMode = 'withdraw'"
              >提现</span
            >
          </div>

          <!-- 分类（固定为转账） -->
          <van-cell title="分类" value="转账" />

          <!-- 对外模式 -->
          <template v-if="transferMode === 'external'">
            <!-- 支出方式 -->
            <van-field
              v-model="selectedPayMethod"
              label="支出方式"
              placeholder="请输入或选择支付方式"
              @click-right-icon="showPayMethodPicker = true"
            >
              <template #right-icon>
                <van-icon
                  name="arrow-down"
                  @click="showPayMethodPicker = true"
                />
              </template>
            </van-field>

            <!-- 关联卡片 -->
            <van-cell
              v-if="showCardCell"
              :title="selectedPayMethod"
              is-link
              @click="showCardPicker = true"
            >
              <template #value>
                <span :class="{ placeholder: !selectedCard?.id }">
                  {{ getCardDisplayText(selectedCard) }}
                </span>
              </template>
            </van-cell>

            <!-- 日期 -->
            <van-cell title="日期" is-link @click="showDatePicker = true">
              <template #value>
                <span>{{ formatDate(selectedDate) }}</span>
              </template>
            </van-cell>

            <!-- 备注（必填，提示收款人） -->
            <van-field
              v-model="remark"
              label="备注"
              placeholder="收款人：xxx"
            />
          </template>

          <!-- 自转模式 -->
          <template v-if="transferMode === 'self'">
            <!-- 转出账户 -->
            <van-cell title="转出账户" is-link @click="showCardPicker = true">
              <template #value>
                <span :class="{ placeholder: !selectedCard?.id }">
                  {{ getCardDisplayText(selectedCard) || "选择转出卡片" }}
                </span>
              </template>
            </van-cell>

            <!-- 转入账户 -->
            <van-cell
              title="转入账户"
              is-link
              @click="showIncomeCardPicker = true"
            >
              <template #value>
                <span :class="{ placeholder: !selectedIncomeCard?.id }">
                  {{ getCardDisplayText(selectedIncomeCard) || "选择转入卡片" }}
                </span>
              </template>
            </van-cell>

            <!-- 日期 -->
            <van-cell title="日期" is-link @click="showDatePicker = true">
              <template #value>
                <span>{{ formatDate(selectedDate) }}</span>
              </template>
            </van-cell>

            <!-- 备注 -->
            <van-field
              v-model="remark"
              label="备注"
              placeholder="选填"
              clearable
            />
          </template>

          <!-- 提现模式 -->
          <template v-if="transferMode === 'withdraw'">
            <!-- 转出账户（固定为余额） -->
            <van-cell title="转出账户" value="余额" />

            <!-- 提现到卡 -->
            <van-cell
              title="提现到卡"
              is-link
              @click="showIncomeCardPicker = true"
            >
              <template #value>
                <span :class="{ placeholder: !selectedIncomeCard?.id }">
                  {{ getCardDisplayText(selectedIncomeCard) || "选择到账卡片" }}
                </span>
              </template>
            </van-cell>

            <!-- 日期 -->
            <van-cell title="日期" is-link @click="showDatePicker = true">
              <template #value>
                <span>{{ formatDate(selectedDate) }}</span>
              </template>
            </van-cell>

            <!-- 备注 -->
            <van-field
              v-model="remark"
              label="备注"
              placeholder="选填"
              clearable
            />
          </template>
        </template>
      </div>
    </div>

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model="displayAmount"
      :show="showKeyboard"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      maxlength="12"
      @blur="showKeyboard = false"
    />

    <!-- 提交按钮 -->
    <div class="submit-wrap">
      <van-button
        type="primary"
        block
        round
        :disabled="!canSubmit"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{
          isExpense
            ? "记一笔支出"
            : isIncome
            ? "记一笔收入"
            : transferMode === "external"
            ? "登记转账支出"
            : transferMode === "withdraw"
            ? "确认提现"
            : "登记自转账"
        }}
      </van-button>
    </div>

    <!-- 分类选择弹框 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <!-- 支付方式选择弹框 -->
    <van-popup v-model:show="showPayMethodPicker" position="bottom" round>
      <van-picker
        title="选择支付方式"
        :columns="payMethodColumns"
        @confirm="onPayMethodConfirm"
        @cancel="showPayMethodPicker = false"
      />
    </van-popup>

    <!-- 卡片选择弹框 -->
    <van-popup v-model:show="showCardPicker" position="bottom" round>
      <div class="card-picker-popup">
        <div class="popup-header">
          <span>选择关联卡片</span>
          <van-icon name="cross" @click="showCardPicker = false" />
        </div>
        <van-search
          v-model="cardSearchKey"
          placeholder="搜索银行卡"
          show-action
          @cancel="cardSearchKey = ''"
        />
        <div class="card-list">
          <div
            v-for="card in filteredCardColumns"
            :key="card.value"
            class="card-item"
            @click="onCardSelect(card)"
          >
            <span class="card-text">{{ card.text }}</span>
            <van-icon
              v-if="card.value === selectedCardId"
              name="success"
              color="#07c160"
            />
          </div>
          <div v-if="filteredCardColumns.length === 0" class="empty-tip">
            未找到匹配的银行卡
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 收入方式选择弹框 -->
    <van-popup v-model:show="showIncomePayMethodPicker" position="bottom" round>
      <van-picker
        title="选择收入方式"
        :columns="incomePayMethodColumns"
        @confirm="onIncomePayMethodConfirm"
        @cancel="showIncomePayMethodPicker = false"
      />
    </van-popup>

    <!-- 转入卡片选择弹框 -->
    <van-popup v-model:show="showIncomeCardPicker" position="bottom" round>
      <div class="card-picker-popup">
        <div class="popup-header">
          <span>选择转入卡片</span>
          <van-icon name="cross" @click="showIncomeCardPicker = false" />
        </div>
        <van-search
          v-model="incomeCardSearchKey"
          placeholder="搜索银行卡"
          show-action
          @cancel="incomeCardSearchKey = ''"
        />
        <div class="card-list">
          <div
            v-for="card in filteredIncomeCardColumns"
            :key="card.value"
            class="card-item"
            @click="onIncomeCardSelect(card)"
          >
            <span class="card-text">{{ card.text }}</span>
            <van-icon
              v-if="card.value === selectedIncomeCardId"
              name="success"
              color="#07c160"
            />
          </div>
          <div v-if="filteredIncomeCardColumns.length === 0" class="empty-tip">
            未找到匹配的银行卡
          </div>
        </div>
      </div>
    </van-popup>

    <!-- 币种选择弹框 -->
    <van-popup v-model:show="showCurrencyPicker" position="bottom" round>
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
      />
    </van-popup>

    <!-- 日期日历弹框 -->
    <van-calendar
      v-model:show="showDatePicker"
      :default-date="selectedDate"
      :min-date="minDate"
      :max-date="maxDate"
      @confirm="onDateConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { showToast, showSuccessToast, showFailToast } from "vant";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { categoryApi } from "@/utils/api/category";
import { getCardList } from "@/utils/api/card";
import { createAccount } from "@/utils/api/account";
import ENV from "@/utils/env";

// ============================================
// 常量配置
// ============================================

// 支出支付方式选项
const expensePayMethods = [
  "现金",
  "余额",
  "微信支付",
  "支付宝",
  "借记卡",
  "信用卡",
];

// 收入支付方式选项（不含信用卡）
const incomePayMethods = ["现金", "余额", "微信支付", "支付宝", "借记卡"];

// 自转支付方式选项（仅借记卡和余额）
const selfTransferPayMethods = ["借记卡", "余额"];

// ============================================
// 组件逻辑
// ============================================

const router = useRouter();

// 基础状态
const displayAmount = ref("");
const showKeyboard = ref(false);
const submitting = ref(false);

// 类型状态
const type = ref("expense");
const isExpense = computed(() => type.value === "expense");
const isIncome = computed(() => type.value === "income");
const isTransfer = computed(() => type.value === "transfer");
const direction = computed(() => (type.value === "expense" ? 0 : 1));

// 分类状态
const categories = ref([]);
const selectedCategory = ref(null);
const showCategoryPicker = ref(false);

// 支付方式状态
const selectedPayMethod = ref("");
const showPayMethodPicker = ref(false);

// 卡片状态
const cardList = ref([]);
const bankList = ref([]);
const selectedCard = ref(null);
const showCardPicker = ref(false);
const cardSearchKey = ref("");
const selectedCardId = ref("");

// 日期状态
const minDate = new Date(2020, 0, 1);
const maxDate = new Date();
const selectedDate = ref(new Date());
const showDatePicker = ref(false);

// 备注
const remark = ref("");

// 转账状态
const transferMode = ref("external");

// 收入方式状态（自转模式下使用）
const incomePayMethod = ref("");
const showIncomePayMethodPicker = ref(false);

// 转入卡片状态
const selectedIncomeCard = ref(null);
const showIncomeCardPicker = ref(false);
const incomeCardSearchKey = ref("");
const selectedIncomeCardId = ref("");

// 币种状态
const currencyOptions = [
  { code: "CNY", label: "人民币", symbol: "¥", rate: 1 },
  { code: "USD", label: "美元", symbol: "$", rate: 6.8451 },
  { code: "EUR", label: "欧元", symbol: "€", rate: 7.5 },
  { code: "HKD", label: "港币", symbol: "HK$", rate: 0.88 },
  { code: "JPY", label: "日元", symbol: "¥", rate: 0.045 },
  { code: "GBP", label: "英镑", symbol: "£", rate: 8.7 },
  { code: "KRW", label: "韩元", symbol: "₩", rate: 0.005 },
  { code: "TWD", label: "台币", symbol: "NT$", rate: 0.22 },
];
const selectedCurrency = ref(currencyOptions[0]);
const showCurrencyPicker = ref(false);
const exchangeRate = ref("");

// 汇率换算
const exchangedAmount = computed(() => {
  if (!displayAmount.value || selectedCurrency.value.code === "CNY") return 0;
  const foreignAmount = Number(displayAmount.value);
  const rate = Number(exchangeRate.value) || selectedCurrency.value.rate;
  return (foreignAmount * rate) / 100;
});

// 加载分类数据
const loadCategories = async () => {
  try {
    const res = await categoryApi.list(
      isTransfer.value ? "expense" : type.value
    );
    categories.value = res.data || res || [];
    if (isTransfer.value) {
      const transferCat = categories.value.find((c) => c.name === "转账");
      if (transferCat) selectedCategory.value = transferCat;
    }
  } catch (e) {
    showToast("加载分类失败");
  }
};

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList();
    cardList.value = res.data || res || [];
  } catch (e) {
    cardList.value = [];
  }
};

// 加载银行分类
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    bankList.value = res.data || res || [];
  } catch (e) {
    bankList.value = [];
  }
};

// 根据 bank_id 获取银行名称
const getBankName = (bankId) => {
  const bank = bankList.value.find((b) => b.id === bankId);
  return bank?.name || "";
};

// 计算属性
const categoryColumns = computed(() =>
  categories.value.map((c) => ({
    text: c.name,
    value: c.id,
    iconUrl: c.icon_url || "",
    ...c,
  }))
);

// 支付方式选项（根据类型动态过滤）
const payMethodColumns = computed(() => {
  let options;
  if (isTransfer.value && transferMode.value === "self") {
    options = selfTransferPayMethods;
  } else if (isTransfer.value && transferMode.value === "withdraw") {
    // 提现模式不需要选支付方式，返回空列表
    return [];
  } else {
    options = isExpense.value ? expensePayMethods : incomePayMethods;
  }
  return options.map((m) => ({ text: m, value: m }));
});

const incomePayMethodColumns = computed(() => {
  const options =
    isTransfer.value && transferMode.value === "self"
      ? selfTransferPayMethods
      : incomePayMethods;
  return options.map((m) => ({ text: m, value: m }));
});

const currencyColumns = currencyOptions.map((c) => ({
  text: `${c.label} ${c.symbol}`,
  value: c.code,
}));

const cardColumns = computed(() => {
  const method = selectedPayMethod.value;

  // 借记卡：只显示真实借记卡
  if (method === "借记卡") {
    return cardList.value
      .filter((c) => c.card_type === "debit")
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c,
      }));
  }

  // 信用卡：只显示真实信用卡
  if (method === "信用卡") {
    return cardList.value
      .filter((c) => c.card_type === "credit")
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c,
      }));
  }

  // 微信/支付宝：显示借记卡 + 余额
  if (method === "微信支付" || method === "支付宝") {
    return cardList.value
      .filter(
        (c) => c.card_type === "debit" || c.card_type === "virtual_balance"
      )
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c,
      }));
  }

  // 其他（现金等）：返回空
  return [];
});

const filteredCardColumns = computed(() => {
  const key = cardSearchKey.value.toLowerCase().trim();
  if (!key) return cardColumns.value;
  return cardColumns.value.filter(
    (card) =>
      card.text.toLowerCase().includes(key) ||
      card.card_no?.includes(key) ||
      card.bank_name?.toLowerCase().includes(key)
  );
});

const incomeCardColumns = computed(() => {
  const method = incomePayMethod.value;
  // 自转模式下，排除已选择的转出卡
  const excludeId = isTransfer.value && transferMode.value === 'self' ? selectedCard.value?.id : null;
  const filterCards = (cards) =>
    cards.filter((c) => !excludeId || c.id !== excludeId)

  if (method === "借记卡") {
    return filterCards(cardList.value.filter((c) => c.card_type === "debit"))
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c,
      }));
  }
  if (method === "余额") {
    return filterCards(cardList.value.filter((c) => c.card_type === "virtual_balance"))
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c,
      }));
  }
  if (method === "微信支付" || method === "支付宝") {
    return filterCards(
      cardList.value.filter(
        (c) => c.card_type === "debit" || c.card_type === "virtual_balance"
      )
    ).map((c) => ({
      text: getCardDisplayText(c),
      value: c.id,
      ...c,
    }));
  }
  return [];
});

const filteredIncomeCardColumns = computed(() => {
  const key = incomeCardSearchKey.value.toLowerCase().trim();
  if (!key) return incomeCardColumns.value;
  return incomeCardColumns.value.filter(
    (card) =>
      card.text.toLowerCase().includes(key) ||
      card.card_no?.includes(key) ||
      card.bank_name?.toLowerCase().includes(key)
  );
});

// 是否显示关联卡片（借记卡/信用卡/微信支付/支付宝时显示）
const showCardCell = computed(() => {
  const method = selectedPayMethod.value;
  return method && !["现金", "余额"].includes(method);
});

// 自转模式下是否显示转入卡片
const showIncomeCardCell = computed(() => {
  const method = incomePayMethod.value;
  return method && !["现金", "余额"].includes(method);
});

// 获取卡片显示文本
const getCardDisplayText = (card) => {
  if (!card) return "请选择";
  const typeText = getCardTypeText(card.card_type);
  // 虚拟卡片（现金、余额）只显示名称
  if (
    card.card_type === "virtual_cash" ||
    card.card_type === "virtual_balance"
  ) {
    return typeText;
  }
  // 优先显示银行名 + 尾号，如"招商银行 6666"
  const bankName = getBankName(card.bank_id || card.bankId);
  const cardNo = card.last4_no || card.last4No || card.card_last4 || "****";
  if (bankName) {
    return `${bankName} ${cardNo}`;
  }
  // 没有银行名时退化为类型 + 尾号
  return `${typeText} ${cardNo}`;
};

// 获取卡片类型文本
const getCardTypeText = (cardType) => {
  const map = {
    credit: "信用卡",
    debit: "借记卡",
    virtual_cash: "现金",
    virtual_balance: "余额",
  };
  return map[cardType] || cardType || "卡片";
};

const canSubmit = computed(() => {
  const hasAmount = displayAmount.value && Number(displayAmount.value) > 0;
  if (isTransfer.value) {
    if (transferMode.value === "external") {
      const needCard = showCardCell.value;
      const hasCard = !!selectedCard.value?.id;
      return hasAmount && !!selectedPayMethod.value && (!needCard || hasCard);
    }
    if (transferMode.value === "withdraw") {
      // 提现：支出方固定余额，只需要选到账卡
      return hasAmount && !!selectedIncomeCard.value?.id;
    }
    if (transferMode.value === "self") {
      const needExpCard = showCardCell.value;
      const hasExpCard = !!selectedCard.value?.id;
      const needIncCard = showIncomeCardCell.value;
      const hasIncCard = !!selectedIncomeCard.value?.id;
      // 两张卡不能相同
      const sameCard =
        selectedCard.value?.id &&
        selectedIncomeCard.value?.id &&
        selectedCard.value.id === selectedIncomeCard.value.id;
      return (
        hasAmount &&
        !!selectedPayMethod.value &&
        (!needExpCard || hasExpCard) &&
        !!incomePayMethod.value &&
        (!needIncCard || hasIncCard) &&
        !sameCard
      );
    }
    return false;
  }
  const needCard = showCardCell.value;
  const hasCard = !!selectedCard.value?.id;
  return (
    hasAmount &&
    !!selectedCategory.value?.id &&
    !!selectedPayMethod.value &&
    (!needCard || hasCard)
  );
});

// 格式化日期
const formatDate = (date) => {
  return dayjs(date).format("YYYY-MM-DD");
};

// 格式化金额
const formatMoney = (val) => {
  const num = Number(val) || 0;
  return num.toFixed(2);
};

// 币种选择
const onCurrencyConfirm = ({ selectedOptions }) => {
  const code = selectedOptions[0].value;
  selectedCurrency.value =
    currencyOptions.find((c) => c.code === code) || currencyOptions[0];
  showCurrencyPicker.value = false;
};

// 类型切换
const onTypeChange = () => {
  selectedCategory.value = null;
  if (isTransfer.value) {
    selectedPayMethod.value = "借记卡";
    selectedCard.value = null;
    incomePayMethod.value = "借记卡";
    selectedIncomeCard.value = null;
    remark.value = "";
    loadCategories();
    return;
  }
  if (!isExpense.value && selectedPayMethod.value === "信用卡") {
    selectedPayMethod.value = "";
    selectedCard.value = null;
  }
  loadCategories();
};

// 自转/对外切换时，自转固定为借记卡
watch(transferMode, (mode) => {
  if (mode === "self") {
    selectedPayMethod.value = "借记卡";
    incomePayMethod.value = "借记卡";
    selectedCard.value = null;
    selectedIncomeCard.value = null;
  } else if (mode === "withdraw") {
    selectedPayMethod.value = "余额";
    incomePayMethod.value = "借记卡";
    selectedCard.value = null;
    selectedIncomeCard.value = null;
    incomeCardSearchKey.value = "";
  }
});

// 分类选择
const onCategoryConfirm = ({ selectedOptions }) => {
  selectedCategory.value = selectedOptions[0];
  showCategoryPicker.value = false;
};

// 支付方式选择
const onPayMethodConfirm = ({ selectedOptions }) => {
  const method = selectedOptions[0].value;
  selectedPayMethod.value = method;
  // 现金/余额不需要卡片，清空选择；其他方式也清空，等待用户选择
  selectedCard.value = null;
  showPayMethodPicker.value = false;
};

// 卡片选择
const onCardConfirm = ({ selectedOptions }) => {
  selectedCard.value = selectedOptions[0];
  showCardPicker.value = false;
};

const onCardSelect = (card) => {
  selectedCardId.value = card.value;
  selectedCard.value = card;
  cardSearchKey.value = "";
  showCardPicker.value = false;
};

// 收入方式选择
const onIncomePayMethodConfirm = ({ selectedOptions }) => {
  const method = selectedOptions[0].value;
  incomePayMethod.value = method;
  selectedIncomeCard.value = null;
  showIncomePayMethodPicker.value = false;
};

// 转入卡片选择
const onIncomeCardSelect = (card) => {
  selectedIncomeCardId.value = card.value;
  selectedIncomeCard.value = card;
  incomeCardSearchKey.value = "";
  showIncomeCardPicker.value = false;
};

// 日期选择
const onDateConfirm = (date) => {
  selectedDate.value = date;
  showDatePicker.value = false;
};

// 监听金额输入，decimal(12,2)：总共12位，小数2位，整数10位
watch(displayAmount, (val) => {
  val = val.replace(/[^\d.]/g, "");
  const parts = val.split(".");
  if (parts.length > 2) {
    displayAmount.value = parts[0] + "." + parts.slice(1).join("");
    return;
  }
  if (parts[1] && parts[1].length > 2) {
    displayAmount.value = parts[0] + "." + parts[1].slice(0, 2);
    return;
  }
  if (parts[0].length > 10) {
    displayAmount.value =
      parts[0].slice(0, 10) + (parts[1] ? "." + parts[1] : "");
    return;
  }
  displayAmount.value = val;
});

// 监听汇率输入，限制前3后4位精度（999.9999）
watch(exchangeRate, (val) => {
  val = val.replace(/[^\d.]/g, "");
  const parts = val.split(".");
  if (parts.length > 2) {
    exchangeRate.value = parts[0] + "." + parts.slice(1).join("");
    return;
  }
  // 整数部分最多3位
  if (parts[0].length > 3) {
    exchangeRate.value =
      parts[0].slice(0, 3) + (parts[1] ? "." + parts[1] : "");
    return;
  }
  // 小数部分最多4位
  if (parts[1] && parts[1].length > 4) {
    exchangeRate.value = parts[0] + "." + parts[1].slice(0, 4);
    return;
  }
  exchangeRate.value = val;
});

// 提交
const handleSubmit = async () => {
  if (!canSubmit.value) {
    if (isTransfer.value) {
      if (transferMode.value === "external") {
        if (showCardCell.value && !selectedCard.value?.id) {
          showFailToast("请选择关联卡片");
        } else {
          showFailToast("请填写完整信息");
        }
      } else if (transferMode.value === "withdraw") {
        if (!selectedIncomeCard.value?.id) {
          showFailToast("请选择到账卡片");
        } else {
          showFailToast("请填写完整信息");
        }
      } else {
        if (showCardCell.value && !selectedCard.value?.id) {
          showFailToast("请选择转出卡片");
        } else if (showIncomeCardCell.value && !selectedIncomeCard.value?.id) {
          showFailToast("请选择转入卡片");
        } else if (
          selectedCard.value?.id &&
          selectedIncomeCard.value?.id &&
          selectedCard.value.id === selectedIncomeCard.value.id
        ) {
          showFailToast("转出和转入不能是同一张卡");
        } else {
          showFailToast("请填写完整信息");
        }
      }
    } else {
      if (showCardCell.value && !selectedCard.value?.id) {
        showFailToast("请选择关联卡片");
      } else {
        showFailToast("请填写完整信息");
      }
    }
    return;
  }

  submitting.value = true;
  try {
    const amount = Number(displayAmount.value);
    const transDate = formatDate(selectedDate.value);
    const currency = selectedCurrency.value.code;
    let exchangeRate = 1;
    if (currency !== "CNY") {
      const rate = Number(exchangeRate.value) || selectedCurrency.value.rate;
      exchangeRate = Math.round(rate * 100000) / 100000;
    }

    const buildCardId = (method, card) => {
      if (method === "现金") return "xxxx";
      if (method === "余额") return "yyyy";
      return card?.id || "";
    };

    if (isTransfer.value) {
      if (transferMode.value === "external") {
        // 对外转账 → 单笔支出
        await createAccount({
          direction: 0,
          categoryId: selectedCategory.value?.id,
          payType: "转账",
          payMethod: selectedPayMethod.value,
          amount,
          currency,
          transDate,
          cardId: buildCardId(selectedPayMethod.value, selectedCard.value),
          exchangeRate,
          remark: remark.value.trim() || "",
        });
      } else if (transferMode.value === "withdraw") {
        // 提现 → 两笔：余额支出 + 银行卡收入
        const remarkText = remark.value.trim();
        const transferGroupId = crypto.randomUUID ? crypto.randomUUID() : `tg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;

        // 1. 支出（余额提现）
        //    如果余额不足，后端返回非200，await 抛出异常，下面的收入请求不会执行
        await createAccount({
          direction: 0,
          categoryId: selectedCategory.value?.id,
          payType: "转账",
          payMethod: "余额",
          amount,
          currency,
          transDate,
          cardId: "yyyy",
          exchangeRate,
          remark: remarkText || "提现",
          transferGroupId,
        });

        // 2. 收入（到账银行卡）→ 后端自动写入 account_transfer
        await createAccount({
          direction: 1,
          categoryId: selectedCategory.value?.id,
          payType: "转账",
          payMethod: "借记卡",
          amount,
          currency,
          transDate,
          cardId: selectedIncomeCard.value?.id,
          exchangeRate,
          remark: remarkText || "提现",
          transferGroupId,
        });
      } else {
        // 自转 → 两笔：支出 + 收入
        const remarkText = remark.value.trim();
        const transferGroupId = crypto.randomUUID ? crypto.randomUUID() : `tg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;

        // 1. 支出（转出）
        await createAccount({
          direction: 0,
          categoryId: selectedCategory.value?.id,
          payType: "转账",
          payMethod: selectedPayMethod.value,
          amount,
          currency,
          transDate,
          cardId: buildCardId(selectedPayMethod.value, selectedCard.value),
          exchangeRate,
          remark: remarkText ? `转出 - ${remarkText}` : "转出",
          transferGroupId,
        });

        // 2. 收入（转入）→ 后端自动写入 account_transfer
        await createAccount({
          direction: 1,
          categoryId: selectedCategory.value?.id,
          payType: "转账",
          payMethod: incomePayMethod.value,
          amount,
          currency,
          transDate,
          cardId: buildCardId(incomePayMethod.value, selectedIncomeCard.value),
          exchangeRate,
          remark: remarkText ? `转入 - ${remarkText}` : "转入",
          transferGroupId,
        });
      }
    } else {
      // 常规收支
      let cardId = buildCardId(selectedPayMethod.value, selectedCard.value);

      const data = {
        direction: direction.value,
        categoryId: selectedCategory.value?.id,
        payType: selectedCategory.value?.name,
        payMethod: selectedPayMethod.value,
        amount,
        currency,
        transDate,
        cardId,
        exchangeRate,
        remark: remark.value.trim() || "",
      };

      await createAccount(data);
    }

    showSuccessToast("提交成功");
    router.back();
  } catch (e) {
    showFailToast(e.message || "提交失败");
  } finally {
    submitting.value = false;
  }
};

// 初始化
onMounted(async () => {
  await loadBankList();
  loadCategories();
  loadCardList();
});
</script>

<style scoped>
.page-finance-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.amount-card {
  margin: 0;
  padding: 40px 20px 20px;
  background: #fff;
  text-align: center;
  border-bottom: 1px solid #eee;
}

.currency-selector {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: #f7f8fa;
  border-radius: 16px;
  font-size: 13px;
  color: #323233;
  margin-bottom: 12px;
  cursor: pointer;
}

.currency-symbol {
  font-size: 32px;
  margin-right: 2px;
}

.exchange-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #969799;
}

.label {
  color: #999;
  font-size: 14px;
}

.value {
  font-size: 48px;
  font-weight: bold;
  color: #333;
  margin-top: 15px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.placeholder {
  color: #999;
}

.info-section {
  background: #fff;
  margin-top: 12px;
}

.form-items {
  padding-bottom: 10px;
}

.submit-wrap {
  padding: 30px 20px;
  background: #f7f8fa;
}

.card-picker-popup {
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #eee;
  font-size: 16px;
  font-weight: 600;
}

.card-list {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
}

.card-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}

.card-item:active {
  background: #f7f8fa;
}

.card-text {
  font-size: 14px;
  color: #323233;
}

.empty-tip {
  text-align: center;
  padding: 30px;
  color: #969799;
  font-size: 14px;
}

.transfer-toggle {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.pay-method-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #f5f6fa;
  border-radius: 12px;
  font-size: 12px;
  color: #323233;
  white-space: nowrap;
  max-width: 120px;
  overflow: hidden;
  cursor: pointer;
}

.toggle-btn {
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 14px;
  background: #f5f6fa;
  color: #969799;
  cursor: pointer;
}

.toggle-btn.active {
  background: #1989fa;
  color: #fff;
}

.section-divider {
  font-size: 12px;
  color: #969799;
  padding: 10px 16px 4px;
  background: #f7f8fa;
}
</style>
