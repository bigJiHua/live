<template>
  <div class="page-quick-add" @click="hideKeyboard">
    <!-- 步骤条（顶部 sticky） -->
    <div class="qsa-bar">
      <button
        class="qsa-nav-btn"
        :disabled="!canPrev"
        :class="{ disabled: !canPrev }"
        @click="onPrev"
      >
        <van-icon name="arrow-left" />
      </button>
      <div class="qsa-steps">
        <div
          v-for="(label, idx) in stepLabels"
          :key="idx"
          class="qsa-step"
          :class="{
            active: step === idx + 1,
            done: step > idx + 1,
          }"
          @click="onStepClick(idx + 1)"
        >
          <span class="qsa-num">{{ idx + 1 }}</span>
          <span class="qsa-lbl">{{ label }}</span>
        </div>
      </div>
      <button
        class="qsa-nav-btn"
        :disabled="!canNext"
        :class="{ disabled: !canNext }"
        @click="onNext"
      >
        <van-icon name="arrow" />
      </button>
    </div>

    <!-- Step 1: 方向 -->
    <transition name="slide-fade" mode="out-in">
      <div v-if="step === 1" class="qsa-panel" :key="1">
        <h2 class="qsa-title">这一笔是？</h2>
        <div class="grid-1">
          <button class="big-btn out" @click="selectDirection(0)">
            <van-icon name="arrow-up" class="big-icon" />
            <span class="big-label">支出</span>
            <span class="big-sub">花钱出去</span>
          </button>
          <button class="big-btn in" @click="selectDirection(1)">
            <van-icon name="arrow-down" class="big-icon" />
            <span class="big-label">收入</span>
            <span class="big-sub">钱到账了</span>
          </button>
          <button class="big-btn tf" @click="selectDirection('transfer')">
            <van-icon name="exchange" class="big-icon" />
            <span class="big-label">转账</span>
            <span class="big-sub">A卡 → B卡</span>
          </button>
        </div>
      </div>

    <!-- Step 2: 分类（转账场景下显示三种子类型） -->
      <div v-else-if="step === 2" class="qsa-panel" :key="2">
        <h2 class="qsa-title">{{ isTransfer ? '哪种转账？' : '哪个分类？' }}</h2>
        <!-- 转账：对外 / 自转 / 提现 -->
        <div v-if="isTransfer" class="grid-1">
          <button
            class="big-btn tf-mode external"
            :class="{ active: transferMode === 'external' }"
            @click="selectTransferMode('external')"
          >
            <van-icon name="send-gift-o" class="big-icon" />
            <span class="big-label">对外转账</span>
            <span class="big-sub">转给他人 · 单笔支出</span>
          </button>
          <button
            class="big-btn tf-mode self"
            :class="{ active: transferMode === 'self' }"
            @click="selectTransferMode('self')"
          >
            <van-icon name="exchange" class="big-icon" />
            <span class="big-label">自转</span>
            <span class="big-sub">我的卡之间互转</span>
          </button>
          <button
            class="big-btn tf-mode withdraw"
            :class="{ active: transferMode === 'withdraw' }"
            @click="selectTransferMode('withdraw')"
          >
            <van-icon name="cash-back-record-o" class="big-icon" />
            <span class="big-label">提现</span>
            <span class="big-sub">余额 → 银行卡</span>
          </button>
        </div>
        <!-- 普通收支：分类列表 -->
        <div v-else class="grid-3">
          <button
            v-for="cat in categoryOptions"
            :key="cat.id"
            class="big-btn cat"
            :class="{ active: form.categoryId === cat.id }"
            @click="selectCategory(cat)"
          >
            <span class="big-label">{{ cat.name }}</span>
          </button>
        </div>
        <div v-if="!isTransfer && categoryOptions.length === 0" class="qsa-empty">该方向暂无可用分类</div>
      </div>

    <!-- Step 3: 卡片（根据方向 + 转账模式动态布局） -->
      <div v-else-if="step === 3" class="qsa-panel" :key="3">
        <h2 class="qsa-title">
          <template v-if="isTransfer && transferMode === 'self'">从哪张卡转出 / 转入？</template>
          <template v-else-if="isTransfer && transferMode === 'withdraw'">提现到哪张卡？</template>
          <template v-else-if="isTransfer && transferMode === 'external'">从哪张卡转出？</template>
          <template v-else>从哪出 / 进？</template>
        </h2>

        <!-- ===== 自转模式：转出 + 转入 双列（银行卡 ↔ 银行卡） ===== -->
        <div v-if="isTransfer && transferMode === 'self'" class="self-transfer-grid">
          <div class="transfer-side">
            <div class="side-label">
              <van-icon name="arrow-up" />
              <span>转出</span>
            </div>
            <div class="side-cards">
              <button
                v-for="card in selfOutCards"
                :key="card.id"
                class="card-item"
                :class="{ active: form.outCardId === card.id, disabled: form.inCardId === card.id }"
                :disabled="form.inCardId === card.id"
                @click="selectSelfOut(card.id, getCardDisplayText(card), getCardTypeText(card.card_type))"
              >
                <van-image
                  v-if="getBankIcon(card) && !bankIconErrorMap.has(card.id)"
                  :src="getFullUrl(getBankIcon(card))"
                  width="26"
                  height="26"
                  fit="contain"
                  class="card-bank-icon"
                  @error="onBankIconError(card.id)"
                >
                  <template #error>
                    <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="16" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                  </template>
                </van-image>
                <div v-else class="card-bank-icon card-bank-fallback">
                  <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="16" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                </div>
                <div class="card-info">
                  <div class="card-name">
                    <span class="card-last4">{{ getCardLast4(card) }}</span>
                    <span class="card-type-text">{{ getCardTypeText(card.card_type) }}</span>
                  </div>
                  <div class="card-bank-name-line">{{ getBankName(card.bank_id || card.bankId) }}</div>
                </div>
              </button>
              <div v-if="selfOutCards.length === 0" class="qsa-empty">暂无可选转出卡</div>
            </div>
          </div>

          <div class="transfer-arrow-col">
            <van-icon name="arrow" />
          </div>

          <div class="transfer-side">
            <div class="side-label in">
              <van-icon name="arrow-down" />
              <span>转入</span>
            </div>
            <div class="side-cards">
              <button
                v-for="card in selfInCards"
                :key="card.id"
                class="card-item"
                :class="{ active: form.inCardId === card.id, disabled: form.outCardId === card.id }"
                :disabled="form.outCardId === card.id"
                @click="selectSelfIn(card.id, getCardDisplayText(card), getCardTypeText(card.card_type))"
              >
                <van-image
                  v-if="getBankIcon(card) && !bankIconErrorMap.has(card.id)"
                  :src="getFullUrl(getBankIcon(card))"
                  width="26"
                  height="26"
                  fit="contain"
                  class="card-bank-icon"
                  @error="onBankIconError(card.id)"
                >
                  <template #error>
                    <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="16" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                  </template>
                </van-image>
                <div v-else class="card-bank-icon card-bank-fallback">
                  <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="16" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                </div>
                <div class="card-info">
                  <div class="card-name">
                    <span class="card-last4">{{ getCardLast4(card) }}</span>
                    <span class="card-type-text">{{ getCardTypeText(card.card_type) }}</span>
                  </div>
                  <div class="card-bank-name-line">{{ getBankName(card.bank_id || card.bankId) }}</div>
                </div>
              </button>
              <div v-if="selfInCards.length === 0" class="qsa-empty">暂无可选转入卡</div>
            </div>
          </div>
        </div>

        <!-- ===== 提现模式：顶部固定余额（已选），下方到账卡列表 ===== -->
        <div v-else-if="isTransfer && transferMode === 'withdraw'">
          <div class="withdraw-out">
            <div class="side-label">
              <van-icon name="arrow-up" />
              <span>从</span>
            </div>
            <div class="withdraw-out-card">
              <van-icon name="balance-o" class="big-icon" style="color:#1989fa" />
              <span class="big-label">余额</span>
            </div>
          </div>
          <div class="withdraw-down">
            <van-icon name="arrow-down" />
          </div>
          <div class="qsa-divider">
            <span>到账借记卡</span>
          </div>
          <div class="card-search">
            <van-search
              v-model="cardSearchKey"
              placeholder="搜索银行名/尾号"
              shape="round"
              background="transparent"
              clearable
            />
          </div>
          <div class="card-list">
            <button
              v-for="card in withdrawInCards"
              :key="card.id"
              class="card-item"
              :class="{ active: form.inCardId === card.id }"
              @click="selectSelfIn(card.id, getCardDisplayText(card), getCardTypeText(card.card_type))"
            >
              <van-image
                v-if="getBankIcon(card) && !bankIconErrorMap.has(card.id)"
                :src="getFullUrl(getBankIcon(card))"
                width="28"
                height="28"
                fit="contain"
                class="card-bank-icon"
                @error="onBankIconError(card.id)"
              >
                <template #error>
                  <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="18" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                </template>
              </van-image>
              <div v-else class="card-bank-icon card-bank-fallback">
                <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="18" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
              </div>
              <div class="card-info">
                <div class="card-name">
                  <span class="card-last4">{{ getCardLast4(card) }}</span>
                  <span class="card-type-text">{{ getCardTypeText(card.card_type) }}</span>
                </div>
                <div class="card-bank-name-line">{{ getBankName(card.bank_id || card.bankId) }}</div>
              </div>
              <van-icon
                v-if="form.inCardId === card.id"
                name="success"
                color="#1989fa"
                size="18"
                class="card-check"
              />
            </button>
            <div v-if="withdrawInCards.length === 0" class="qsa-empty">暂无借记卡</div>
          </div>
        </div>

        <!-- ===== 对外转账 / 普通收支：原有卡片选择 ===== -->
        <div v-else>
          <!-- 现金 + 余额固定顶部（普通收支才显示，对外转账不能是现金） -->
          <div v-if="!isTransfer || transferMode !== 'external'" class="grid-2">
            <button
              class="big-btn fixed cash"
              :class="{ active: form.cardId === 'xxxx' }"
              @click="selectVirtualCard('xxxx', '现金', '现金')"
            >
              <van-icon name="cash-back-record-o" class="big-icon fixed-icon" style="color:#ff976a" />
              <span class="big-label">现金</span>
            </button>
            <button
              class="big-btn fixed balance"
              :class="{ active: form.cardId === 'yyyy' }"
              @click="selectVirtualCard('yyyy', '余额', '余额')"
            >
              <van-icon name="balance-o" class="big-icon fixed-icon" style="color:#1989fa" />
              <span class="big-label">余额</span>
            </button>
          </div>

          <div v-if="realCards.length > 0">
            <div class="qsa-divider">
              <span>{{ isTransfer && transferMode === 'external' ? '转出卡片' : '我的卡片' }}</span>
            </div>

            <div v-if="!isIncome && !(isTransfer && transferMode === 'external')" class="card-type-tabs">
              <span
                class="card-type-tab"
                :class="{ active: cardTab === 'credit' }"
                @click="cardTab = 'credit'"
              >信用卡</span>
              <span
                class="card-type-tab"
                :class="{ active: cardTab === 'debit' }"
                @click="cardTab = 'debit'"
              >借记卡</span>
            </div>

            <div class="card-search">
              <van-search
                v-model="cardSearchKey"
                placeholder="搜索银行卡名/尾号"
                shape="round"
                background="transparent"
                clearable
              />
            </div>

            <div class="card-list">
              <button
                v-for="card in displayedRealCards"
                :key="card.id"
                class="card-item"
                :class="{ active: form.cardId === card.id }"
                @click="selectRealCard(card)"
              >
                <van-image
                  v-if="getBankIcon(card) && !bankIconErrorMap.has(card.id)"
                  :src="getFullUrl(getBankIcon(card))"
                  width="28"
                  height="28"
                  fit="contain"
                  class="card-bank-icon"
                  @error="onBankIconError(card.id)"
                >
                  <template #error>
                    <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="18" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                  </template>
                </van-image>
                <div v-else class="card-bank-icon card-bank-fallback">
                  <van-icon :name="card.card_type === 'credit' ? 'credit-pay' : 'debit-pay'" size="18" :color="card.card_type === 'credit' ? '#ee0a24' : '#1989fa'" />
                </div>
                <div class="card-info">
                  <div class="card-name">
                    <span class="card-last4">{{ getCardLast4(card) }}</span>
                    <span class="card-type-text">{{ getCardTypeText(card.card_type) }}</span>
                  </div>
                  <div class="card-bank-name-line">{{ getBankName(card.bank_id || card.bankId) }}</div>
                </div>
                <van-icon
                  v-if="form.cardId === card.id"
                  name="success"
                  color="#1989fa"
                  size="18"
                  class="card-check"
                />
              </button>
              <div v-if="displayedRealCards.length === 0" class="qsa-empty">该类型暂无卡片</div>
            </div>
          </div>

          <div v-if="realCards.length === 0" class="qsa-empty">暂无真实银行卡</div>
        </div>

        <!-- Step 3 底部：下一步按钮（自转/提现/对外转账不自动跳，需手动点） -->
        <button
          v-if="isTransfer"
          class="next-big-btn"
          :disabled="!canNext"
          @click="onNext"
        >
          <span>下一步</span>
          <van-icon name="arrow" />
        </button>
      </div>

    <!-- Step 4: 金额 -->
      <div v-else-if="step === 4" class="qsa-panel amount-panel" :key="4">
        <h2 class="qsa-title">多少金额？</h2>
        <div class="currency-selector" @click.stop="showCurrencyPicker = true">
          <span class="currency-name">{{ selectedCurrency.label }}</span>
          <van-icon name="arrow-down" />
        </div>
        <div class="amount-display" @click.stop="showKeyboard = true">
          <span class="amount-symbol">{{ selectedCurrency.symbol }}</span>
          <span class="amount-num">{{ form.amount || '0.00' }}</span>
        </div>
        <div
          class="exchange-tip"
          v-if="selectedCurrency.code !== 'CNY' && form.amount"
        >
          约 ¥ {{ formatMoney(exchangedAmount) }}
        </div>
        <div v-if="selectedCurrency.code !== 'CNY'" class="rate-field">
          <span class="rate-label">汇率</span>
          <input
            v-model="exchangeRate"
            class="rate-input"
            type="text"
            inputmode="decimal"
            placeholder="如: 684.5125"
            @focus="showKeyboard = false"
          />
          <span class="rate-unit">/100</span>
        </div>
        <div class="amount-tip">点击金额区域唤起键盘</div>

        <transition name="fade-up">
          <button
            v-if="!showKeyboard"
            class="next-big-btn"
            :disabled="!canNext"
            @click="onNext"
          >
            <span>下一步</span>
            <van-icon name="arrow" />
          </button>
        </transition>
      </div>

    <!-- Step 5: 备注 + 确认 -->
      <div v-else-if="step === 5" class="qsa-panel" :key="5">
        <h2 class="qsa-title">{{ remarkTitle }}</h2>

        <!-- 对外转账：必填收款人 -->
        <div v-if="isTransfer && transferMode === 'external'" class="payee-field">
          <span class="payee-label">转给</span>
          <input
            v-model="form.payee"
            class="payee-input"
            type="text"
            maxlength="30"
            placeholder="请输入收款人（必填）"
            @focus="hideKeyboard"
          />
        </div>

        <app-field
          v-else
          v-model="form.remark"
          :placeholder="remarkPlaceholder"
          maxlength="50"
          show-word-limit
          clearable
        />

        <div class="summary">
          <div class="summary-row">
            <span class="summary-label">方向</span>
            <span class="summary-val">{{ directionLabel }}</span>
          </div>
          <div class="summary-row" v-if="!isTransfer">
            <span class="summary-label">分类</span>
            <span class="summary-val">{{ form.categoryName }}</span>
          </div>
          <template v-if="isTransfer && transferMode === 'self'">
            <div class="summary-row">
              <span class="summary-label">转出账户</span>
              <span class="summary-val">{{ form.outCardLabel }}</span>
            </div>
            <div class="summary-row">
              <span class="summary-label">转入账户</span>
              <span class="summary-val">{{ form.inCardLabel }}</span>
            </div>
          </template>
          <template v-else-if="isTransfer && transferMode === 'withdraw'">
            <div class="summary-row">
              <span class="summary-label">转出账户</span>
              <span class="summary-val">余额</span>
            </div>
            <div class="summary-row">
              <span class="summary-label">到账账户</span>
              <span class="summary-val">{{ form.inCardLabel }}</span>
            </div>
          </template>
          <template v-else>
            <div class="summary-row">
              <span class="summary-label">{{ isTransfer ? '转出账户' : '账户' }}</span>
              <span class="summary-val">{{ form.cardLabel }}</span>
            </div>
          </template>
          <div class="summary-row summary-amount-row">
            <span class="summary-label">金额</span>
            <span class="summary-val summary-amount">{{ selectedCurrency.symbol }} {{ form.amount || '0.00' }}</span>
          </div>
        </div>
        <div class="qsa-actions">
          <app-button
            type="primary"
            block
            round
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="onSubmit"
          >
            {{ isTransfer && transferMode === 'withdraw' ? '确认提现' : isTransfer && transferMode === 'self' ? '确认自转' : '确认登记' }}
          </app-button>
        </div>
      </div>
    </transition>

    <van-number-keyboard
      v-model="form.amount"
      :show="showKeyboard"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      :maxlength="12"
      @blur="showKeyboard = false"
    />
    <app-popup v-model:show="showCurrencyPicker" position="bottom" round teleport="body">
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
      />
    </app-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { showToast, showSuccessToast, showFailToast } from "vant";
import dayjs from "dayjs";
import { categoryApi } from "@/utils/api/category";
import { getCardList } from "@/utils/api/card";
import { createDebitAccount, createCreditAccount } from "@/utils/api/account";
import { useFlowSyncStore } from "@/stores/flowSync";
import ENV from "@/utils/env";

defineOptions({ name: "FinanceQuickAdd" });

const router = useRouter();
const flowSync = useFlowSyncStore();
const BASE_URL = ENV.FILE_BASE_URL;

// 步骤控制
const totalSteps = 5;
const step = ref(1);
const stepLabels = ["方向", "分类", "账户", "金额", "确认"];

const canPrev = computed(() => step.value > 1);
const canNext = computed(() => {
  if (step.value >= totalSteps) return false;
  if (step.value === 1) return form.value.direction !== null;
  if (step.value === 2) {
    if (isTransfer.value) return !!transferMode.value;
    return !!form.value.categoryId;
  }
  if (step.value === 3) {
    if (isTransfer.value && transferMode.value === 'self') {
      return !!form.value.outCardId && !!form.value.inCardId && form.value.outCardId !== form.value.inCardId;
    }
    if (isTransfer.value && transferMode.value === 'withdraw') {
      return !!form.value.inCardId && form.value.inCardId !== 'xxxx' && form.value.inCardId !== 'yyyy';
    }
    if (isTransfer.value && transferMode.value === 'external') {
      // 对外转账：必须是真实银行卡（不能是现金/余额）
      return !!form.value.cardId && form.value.cardId !== 'xxxx' && form.value.cardId !== 'yyyy';
    }
    return !!form.value.cardId;
  }
  if (step.value === 4) return isAmountValid.value;
  return false;
});

const onPrev = () => { if (canPrev.value) step.value -= 1; };
const onNext = () => { if (canNext.value) step.value += 1; };
const onStepClick = (target) => { if (target < step.value) step.value = target; };

// 表单数据
const form = ref({
  direction: null,
  categoryId: null,
  categoryName: "",
  cardId: null,
  cardLabel: "",
  cardType: "",
  outCardId: null,
  outCardLabel: "",
  outCardType: "",
  inCardId: null,
  inCardLabel: "",
  inCardType: "",
  payee: "",
  amount: "",
  remark: "",
  transDate: dayjs().format("YYYY-MM-DD"),
  currency: "CNY",
  exchangeRate: 1,
});
const showKeyboard = ref(false);
const submitting = ref(false);

// 转账模式
const transferMode = ref(null);

// 币种配置（与 Add.vue 一致）
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
const exchangedAmount = computed(() => {
  if (!form.value.amount || selectedCurrency.value.code === "CNY") return 0;
  const foreignAmount = Number(form.value.amount);
  const rate = Number(exchangeRate.value) || selectedCurrency.value.rate;
  return (foreignAmount * rate) / 100;
});
const currencyColumns = currencyOptions.map((c) => ({
  text: `${c.label} ${c.symbol}`,
  value: c.code,
}));
const onCurrencyConfirm = ({ selectedOptions }) => {
  const code = selectedOptions[0].value;
  selectedCurrency.value =
    currencyOptions.find((c) => c.code === code) || currencyOptions[0];
  showCurrencyPicker.value = false;
};
import { formatMoney } from "@/utils/money";

// 数据
const allCategories = ref({ expense: [], income: [] });
const allCards = ref([]);
const bankList = ref([]);

// 根据卡片类型选择借记卡/信用卡接口
// cardId 为 xxxx(现金)/yyyy(余额) 走 debit；其余查 allCards 判断 card_type
const createAccount = async (data) => {
  const cardId = data.cardId;
  const card = cardId ? allCards.value.find(c => c.id === cardId) : null;
  const isCredit = card && card.card_type === 'credit';
  return isCredit ? createCreditAccount(data) : createDebitAccount(data);
};

onMounted(async () => {
  try {
    const [expenseCats, incomeCats, cardRes, bankRes] = await Promise.all([
      categoryApi.list("expense").catch(() => []),
      categoryApi.list("income").catch(() => []),
      getCardList().catch(() => []),
      categoryApi.list("bank").catch(() => []),
    ]);
    allCategories.value = {
      expense: expenseCats.data || expenseCats || [],
      income: incomeCats.data || incomeCats || [],
    };
    allCards.value = cardRes.data || cardRes || [];
    bankList.value = bankRes.data || bankRes || [];
  } catch (e) {
    console.error("加载分类/卡片失败", e);
  }
});

// 工具函数
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};
const getBankName = (bankId) => {
  if (!bankId) return "";
  return bankList.value.find((b) => b.id === bankId)?.name || "";
};
const getBankIcon = (card) => {
  const bankId = card.bank_id || card.bankId;
  if (!bankId) return "";
  return bankList.value.find((b) => b.id === bankId)?.icon_url || "";
};
const getCardTypeText = (cardType) => {
  const map = { credit: "信用卡", debit: "借记卡", virtual_cash: "现金", virtual_balance: "余额" };
  return map[cardType] || cardType || "卡片";
};
const getCardDisplayText = (card) => {
  if (!card) return "";
  const typeText = getCardTypeText(card.card_type);
  if (card.card_type === "virtual_cash" || card.card_type === "virtual_balance") return typeText;
  const bankName = getBankName(card.bank_id || card.bankId);
  const cardNo = card.last4_no || card.last4No || card.card_last4 || "****";
  return bankName ? `${bankName} ${cardNo}` : `${typeText} ${cardNo}`;
};
const getCardLast4 = (card) => {
  if (!card) return "****";
  return card.last4_no || card.last4No || card.card_last4 || "****";
};
// 找一张真实的"转账"分类（用于转账模式）
const transferCategory = computed(() => {
  return allCategories.value.expense.find((c) => c.name === "转账") || null;
});

// Step 1: 方向
const selectDirection = (dir) => {
  form.value.direction = dir;
  form.value.categoryId = null;
  form.value.categoryName = "";
  form.value.cardId = null;
  form.value.cardLabel = "";
  form.value.cardType = "";
  form.value.outCardId = null;
  form.value.outCardLabel = "";
  form.value.outCardType = "";
  form.value.inCardId = null;
  form.value.inCardLabel = "";
  form.value.inCardType = "";
  form.value.payee = "";
  form.value.remark = "";
  transferMode.value = null;
  selectedCurrency.value = currencyOptions[0];
  exchangeRate.value = "";
  cardTab.value = "debit";
  cardSearchKey.value = "";
  step.value = 2;
};
const isTransfer = computed(() => form.value.direction === "transfer");
const isIncome = computed(() => form.value.direction === 1);
const directionLabel = computed(() => {
  if (form.value.direction === 0) return "支出";
  if (form.value.direction === 1) return "收入";
  if (form.value.direction === "transfer") return "转账";
  return "-";
});

// Step 2: 转账模式选择 / 普通分类
const selectTransferMode = (mode) => {
  transferMode.value = mode;
  // 同步分类为"转账"（与 Add.vue 一致）
  if (transferCategory.value) {
    form.value.categoryId = transferCategory.value.id;
    form.value.categoryName = transferCategory.value.name;
  }
  // 重置转账相关字段
  form.value.cardId = null;
  form.value.cardLabel = "";
  form.value.cardType = "";
  form.value.outCardId = null;
  form.value.outCardLabel = "";
  form.value.inCardId = null;
  form.value.inCardLabel = "";
  step.value = 3;
};

const categoryOptions = computed(() => {
  if (isTransfer.value) return [];
  const cats = form.value.direction === 0 ? allCategories.value.expense : allCategories.value.income;
  return cats;
});
const selectCategory = (cat) => {
  form.value.categoryId = cat.id;
  form.value.categoryName = cat.name;
  step.value = 3;
};

// Step 3: 卡片
const realCards = computed(() =>
  allCards.value.filter((c) => c.card_type !== "virtual_cash" && c.card_type !== "virtual_balance")
);
const cardTab = ref("debit");
const cardSearchKey = ref("");
const bankIconErrorMap = ref(new Set());
const onBankIconError = (cardId) => {
  if (!bankIconErrorMap.value.has(cardId)) {
    bankIconErrorMap.value.add(cardId);
    bankIconErrorMap.value = new Set(bankIconErrorMap.value);
  }
};
// 普通支出/收入/对外转账 列表（按 tab + 搜索过滤）
const displayedRealCards = computed(() => {
  // 对外转账：只显示借记卡
  if (isTransfer.value && transferMode.value === 'external') {
    const key = cardSearchKey.value.trim().toLowerCase();
    let list = realCards.value.filter((c) => c.card_type === "debit");
    if (!key) return list;
    return list.filter((c) => {
      const bank = getBankName(c.bank_id || c.bankId).toLowerCase();
      return bank.includes(key) || getCardLast4(c).includes(key);
    });
  }
  // 收入：只显示借记卡
  let list = isIncome.value
    ? realCards.value.filter((c) => c.card_type === "debit")
    : realCards.value.filter((c) => c.card_type === cardTab.value);
  const key = cardSearchKey.value.trim().toLowerCase();
  if (!key) return list;
  return list.filter((c) => {
    const bank = getBankName(c.bank_id || c.bankId).toLowerCase();
    const last4 = getCardLast4(c);
    const type = getCardTypeText(c.card_type).toLowerCase();
    return bank.includes(key) || last4.includes(key) || type.includes(key);
  });
});
// 自转：转出侧（借记卡 + 现金 + 余额），排除已选为转入的卡
const selfOutCards = computed(() => {
  const key = cardSearchKey.value.trim().toLowerCase();
  let list = realCards.value.filter((c) => c.card_type === "debit");
  if (key) {
    list = list.filter((c) => {
      const bank = getBankName(c.bank_id || c.bankId).toLowerCase();
      return bank.includes(key) || getCardLast4(c).includes(key);
    });
  }
  return list;
});
// 自转：转入侧（仅借记卡），排除已选为转出的卡
const selfInCards = computed(() => {
  const key = cardSearchKey.value.trim().toLowerCase();
  let list = realCards.value.filter((c) => c.card_type === "debit");
  if (key) {
    list = list.filter((c) => {
      const bank = getBankName(c.bank_id || c.bankId).toLowerCase();
      return bank.includes(key) || getCardLast4(c).includes(key);
    });
  }
  return list;
});
// 提现：到账卡（仅借记卡）
const withdrawInCards = computed(() => {
  const key = cardSearchKey.value.trim().toLowerCase();
  let list = realCards.value.filter((c) => c.card_type === "debit");
  if (key) {
    list = list.filter((c) => {
      const bank = getBankName(c.bank_id || c.bankId).toLowerCase();
      return bank.includes(key) || getCardLast4(c).includes(key);
    });
  }
  return list;
});

const selectVirtualCard = (value, label, type) => {
  form.value.cardId = value;
  form.value.cardLabel = label;
  form.value.cardType = type;
  step.value = 4;
};
const selectRealCard = (card) => {
  form.value.cardId = card.id;
  form.value.cardLabel = getCardDisplayText(card);
  form.value.cardType = getCardTypeText(card.card_type);
  step.value = 4;
};
const selectSelfOut = (id, label, type) => {
  form.value.outCardId = id;
  form.value.outCardLabel = label;
  form.value.outCardType = type;
};
const selectSelfIn = (id, label, type) => {
  form.value.inCardId = id;
  form.value.inCardLabel = label;
  form.value.inCardType = type;
  // 提现模式：选完卡自动进入下一步
  if (isTransfer.value && transferMode.value === 'withdraw') {
    setTimeout(() => { if (canNext.value) step.value = 4; }, 80);
  }
};

// Step 4: 金额
const isAmountValid = computed(() => {
  const v = Number(form.value.amount);
  return v > 0 && Number.isFinite(v);
});
const hideKeyboard = () => { showKeyboard.value = false; };
watch(
  () => step.value,
  (v) => {
    if (v === 4) {
      setTimeout(() => { showKeyboard.value = true; }, 50);
    } else {
      showKeyboard.value = false;
    }
  }
);
watch(
  () => form.value.amount,
  (val) => {
    if (!val && val !== 0) return;
    let v = String(val).replace(/[^\d.]/g, "");
    const parts = v.split(".");
    if (parts.length > 2) {
      v = parts[0] + "." + parts.slice(1).join("");
      form.value.amount = v;
      return;
    }
    if (parts[1] && parts[1].length > 2) {
      form.value.amount = parts[0] + "." + parts[1].slice(0, 2);
      return;
    }
    if (parts[0].length > 10) {
      form.value.amount =
        parts[0].slice(0, 10) + (parts[1] ? "." + parts[1] : "");
      return;
    }
    if (v !== val) form.value.amount = v;
  }
);
watch(exchangeRate, (val) => {
  if (!val && val !== 0) return;
  let v = String(val).replace(/[^\d.]/g, "");
  const parts = v.split(".");
  if (parts.length > 2) {
    v = parts[0] + "." + parts.slice(1).join("");
    exchangeRate.value = v;
    return;
  }
  if (parts[0].length > 3) {
    exchangeRate.value =
      parts[0].slice(0, 3) + (parts[1] ? "." + parts[1] : "");
    return;
  }
  if (parts[1] && parts[1].length > 4) {
    exchangeRate.value = parts[0] + "." + parts[1].slice(0, 4);
    return;
  }
  if (v !== val) exchangeRate.value = v;
});

// Step 5: 备注
const remarkTitle = computed(() => {
  if (isTransfer.value && transferMode.value === 'external') return "转给谁？";
  if (isTransfer.value && transferMode.value === 'self') return "补充信息（可选）";
  if (isTransfer.value && transferMode.value === 'withdraw') return "补充信息（可选）";
  return "补充信息（可选）";
});
const remarkPlaceholder = computed(() => "备注（不填直接提交）");
const canSubmit = computed(() => {
  if (!isAmountValid.value) return false;
  if (isTransfer.value) {
    if (transferMode.value === 'self') {
      return !!form.value.outCardId && !!form.value.inCardId && form.value.outCardId !== form.value.inCardId;
    }
    if (transferMode.value === 'withdraw') {
      return !!form.value.inCardId && form.value.inCardId !== 'xxxx' && form.value.inCardId !== 'yyyy';
    }
    if (transferMode.value === 'external') {
      return !!form.value.cardId && form.value.cardId !== 'xxxx' && form.value.cardId !== 'yyyy' && !!form.value.payee.trim();
    }
  }
  return form.value.direction !== null && form.value.categoryId && form.value.cardId;
});

// 提交（与 Add.vue 行为一致）
const onSubmit = async () => {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    const currency = selectedCurrency.value.code;
    let rate = 1;
    if (currency !== "CNY") {
      const r = Number(exchangeRate.value) || selectedCurrency.value.rate;
      rate = Math.round(r * 100000) / 100000;
    }
    const basePayload = {
      amount: Number(form.value.amount),
      currency,
      exchangeRate: rate,
      transDate: form.value.transDate,
    };
    const buildCardId = (method, fallbackId) => {
      if (method === "现金") return "xxxx";
      if (method === "余额") return "yyyy";
      return fallbackId || "";
    };

    if (isTransfer.value) {
      if (transferMode.value === 'external') {
        // 对外转账：单笔支出
        await createAccount({
          ...basePayload,
          direction: 0,
          categoryId: form.value.categoryId,
          payType: "转账",
          payMethod: form.value.cardType,
          cardId: buildCardId(form.value.cardType, form.value.cardId),
          remark: form.value.payee.trim(),
        });
        showSuccessToast("登记成功");
      } else if (transferMode.value === 'self') {
        // 自转：两笔（转出 + 转入）共用 transferGroupId
        const tgId = crypto.randomUUID ? crypto.randomUUID() : `tg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
        const remarkText = form.value.remark.trim();
        await createAccount({
          ...basePayload,
          direction: 0,
          categoryId: form.value.categoryId,
          payType: "转账",
          payMethod: form.value.outCardType,
          cardId: buildCardId(form.value.outCardType, form.value.outCardId),
          remark: remarkText ? `转出 - ${remarkText}` : "转出",
          transferGroupId: tgId,
        });
        await createAccount({
          ...basePayload,
          direction: 1,
          categoryId: form.value.categoryId,
          payType: "转账",
          payMethod: form.value.inCardType,
          cardId: buildCardId(form.value.inCardType, form.value.inCardId),
          remark: remarkText ? `转入 - ${remarkText}` : "转入",
          transferGroupId: tgId,
        });
        showSuccessToast("自转登记成功");
      } else if (transferMode.value === 'withdraw') {
        // 提现：两笔（余额 → 借记卡）共用 transferGroupId
        const tgId = crypto.randomUUID ? crypto.randomUUID() : `tg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
        const remarkText = form.value.remark.trim() || "提现";
        await createAccount({
          ...basePayload,
          direction: 0,
          categoryId: form.value.categoryId,
          payType: "转账",
          payMethod: "余额",
          cardId: "yyyy",
          remark: remarkText,
          transferGroupId: tgId,
        });
        await createAccount({
          ...basePayload,
          direction: 1,
          categoryId: form.value.categoryId,
          payType: "转账",
          payMethod: form.value.inCardType,
          cardId: form.value.inCardId,
          remark: remarkText,
          transferGroupId: tgId,
        });
        showSuccessToast("提现登记成功");
      }
    } else {
      // 普通收支
      await createAccount({
        ...basePayload,
        direction: form.value.direction,
        categoryId: form.value.categoryId,
        payType: form.value.categoryName,
        payMethod: form.value.cardType,
        cardId: buildCardId(form.value.cardType, form.value.cardId),
        remark: form.value.remark.trim() || "",
      });
      showSuccessToast("登记成功");
    }
    flowSync.markListRefresh();
    router.back();
  } catch (e) {
    showFailToast(e.message || "提交失败");
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.page-quick-add {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 40px;
}

/* 步骤条（顶部 sticky） */
.qsa-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--theme-bg-secondary);
  border: 1px solid var(--theme-border);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}
.qsa-nav-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--theme-bg-tertiary);
  border-radius: 50%;
  color: var(--theme-text-primary);
  font-size: 16px;
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
}
.qsa-nav-btn:active { transform: scale(0.92); }
.qsa-nav-btn.disabled {
  background: var(--theme-bg-primary);
  color: var(--theme-text-tertiary);
  cursor: not-allowed;
}
.qsa-steps {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  overflow-x: auto;
}
.qsa-step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
  cursor: pointer;
  padding: 4px 0;
  border-radius: 6px;
  transition: color 0.2s;
}
.qsa-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--theme-bg-tertiary);
  color: var(--theme-text-tertiary);
  font-size: 12px;
  font-weight: 600;
  transition: all 0.2s;
}
.qsa-lbl {
  font-size: 10px;
  color: var(--theme-text-tertiary);
  white-space: nowrap;
  transition: color 0.2s;
}
.qsa-step.done .qsa-num {
  background: rgba(var(--theme-primary-rgb), 0.15);
  color: var(--theme-primary);
}
.qsa-step.done .qsa-lbl { color: var(--theme-primary); }
.qsa-step.active .qsa-num {
  background: var(--theme-primary);
  color: #fff;
  box-shadow: 0 0 0 3px rgba(var(--theme-primary-rgb), 0.15);
}
.qsa-step.active .qsa-lbl { color: var(--theme-primary); font-weight: 600; }

/* 步骤面板 */
.qsa-panel { padding: 24px 16px; }
.qsa-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--theme-text-primary);
  margin: 0 0 20px;
  text-align: center;
}
.qsa-empty {
  text-align: center;
  color: var(--theme-text-tertiary);
  font-size: 13px;
  margin-top: 24px;
}

/* 大块按钮 */
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.grid-1 {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.big-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 96px;
  padding: 16px 8px;
  border: 1px solid var(--theme-border);
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  outline: none;
}
.grid-1 .big-btn {
  min-height: 120px;
  padding: 24px 12px;
}
.grid-1 .big-icon { font-size: 44px; margin-bottom: 10px; }
.grid-1 .big-label { font-size: 20px; }
.grid-1 .big-sub { font-size: 13px; margin-top: 4px; }
.big-btn:active { transform: scale(0.97); }
.big-btn.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.1); }

.big-icon { font-size: 28px; margin-bottom: 6px; }
.big-label { font-size: 16px; font-weight: 600; color: var(--theme-text-primary); }
.big-sub { font-size: 11px; color: var(--theme-text-tertiary); margin-top: 3px; }

/* 方向按钮配色 */
.big-btn.out { border-color: rgba(238, 10, 36, 0.3); }
.big-btn.out .big-icon { color: var(--theme-danger); }
.big-btn.out:active { background: rgba(238, 10, 36, 0.08); }
.big-btn.out.active { border-color: var(--theme-danger); background: rgba(238, 10, 36, 0.08); }

.big-btn.in { border-color: rgba(7, 193, 96, 0.3); }
.big-btn.in .big-icon { color: var(--van-green); }
.big-btn.in:active { background: rgba(7, 193, 96, 0.08); }
.big-btn.in.active { border-color: var(--van-green); background: rgba(7, 193, 96, 0.08); }

.big-btn.tf { border-color: rgba(var(--theme-primary-rgb), 0.3); }
.big-btn.tf .big-icon { color: var(--theme-primary); }
.big-btn.tf:active { background: rgba(var(--theme-primary-rgb), 0.1); }
.big-btn.tf.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.1); }

.big-btn.cat .big-icon { font-size: 26px; }
.grid-3 .big-btn.cat {
  min-height: 96px;
  padding: 12px 6px;
}
.grid-3 .big-btn.cat .big-label { font-size: 15px; }

/* 转账模式按钮 */
.big-btn.tf-mode.external { border-color: rgba(var(--theme-primary-rgb), 0.3); }
.big-btn.tf-mode.external .big-icon { color: var(--theme-primary); }
.big-btn.tf-mode.external.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.1); }
.big-btn.tf-mode.self { border-color: rgba(7, 193, 96, 0.3); }
.big-btn.tf-mode.self .big-icon { color: var(--van-green); }
.big-btn.tf-mode.self.active { border-color: var(--van-green); background: rgba(7, 193, 96, 0.08); }
.big-btn.tf-mode.withdraw { border-color: rgba(255, 151, 106, 0.3); }
.big-btn.tf-mode.withdraw .big-icon { color: var(--van-orange); }
.big-btn.tf-mode.withdraw.active { border-color: var(--van-orange); background: rgba(255, 151, 106, 0.12); }

/* 现金/余额固定顶部 */
.big-btn.fixed { min-height: 48px; padding: 6px 6px; }
.big-btn.fixed .fixed-icon { font-size: 20px; margin-bottom: 2px; }
.big-btn.fixed .big-label { font-size: 13px; }
.big-btn.fixed .big-sub { display: none; }
.big-btn.cash { border-color: rgba(255, 151, 106, 0.3); }
.big-btn.cash.active { border-color: var(--van-orange); background: rgba(255, 151, 106, 0.12); }
.big-btn.balance { border-color: rgba(var(--theme-primary-rgb), 0.3); }
.big-btn.balance.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.1); }

.qsa-divider {
  display: flex;
  align-items: center;
  margin: 20px 0 12px;
  color: var(--theme-text-tertiary);
  font-size: 12px;
}
.qsa-divider::before,
.qsa-divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--theme-border);
  margin: 0 8px;
}

/* 真实卡片：左右两列 */
.card-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.card-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  min-height: 48px;
  padding: 6px 8px;
  border: 1px solid var(--theme-border);
  background: var(--theme-bg-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  outline: none;
  text-align: left;
  gap: 6px;
  overflow: hidden;
}
.card-item:active { transform: scale(0.99); }
.card-item.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.08); }
.card-item.disabled {
  opacity: 0.4;
  cursor: not-allowed;
  background: var(--theme-bg-primary);
}

/* 卡片搜索 */
.card-search { margin: 0 0 10px; }
.card-search :deep(.van-search) { padding: 6px 0; }
.card-search :deep(.van-search__content) {
  background: var(--theme-bg-tertiary);
  border: 1px solid var(--theme-border);
}

/* 卡片类型 tab */
.card-type-tabs {
  display: flex;
  gap: 8px;
  padding: 4px 4px 10px;
}
.card-type-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 14px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-tertiary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.card-type-tab.active {
  background: var(--theme-primary);
  color: #fff;
  font-weight: 600;
}

.card-bank-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 5px;
}
.card-bank-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: var(--theme-bg-primary);
  border-radius: 5px;
}

.card-info { flex: 1 1 0; min-width: 0; overflow: hidden; }
.card-name {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  overflow: hidden;
  line-height: 1.2;
}
.card-last4 {
  font-size: 13px;
  font-weight: 700;
  color: var(--theme-text-primary);
  letter-spacing: 0.5px;
  font-family: "DIN Alternate", "SF Mono", monospace;
  flex-shrink: 0;
}
.card-type-text {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  line-height: 1.2;
}
.card-bank-name-line {
  font-size: 10px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-check { flex-shrink: 0; margin-left: 2px; }

/* ===== 自转模式双列 ===== */
.self-transfer-grid {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 6px;
  align-items: stretch;
}
.transfer-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.side-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-danger);
}
.side-label.in { color: var(--van-green); justify-content: flex-end; }
.side-label .van-icon { font-size: 14px; }
.side-fixed {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.mini-btn {
  min-height: 36px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--theme-border);
  background: var(--theme-bg-secondary);
  border-radius: 6px;
  cursor: pointer;
  outline: none;
  font-family: inherit;
  color: var(--theme-text-primary);
  transition: all 0.2s;
}
.mini-btn.cash { border-color: rgba(255, 151, 106, 0.3); }
.mini-btn.cash.active { border-color: var(--van-orange); background: rgba(255, 151, 106, 0.12); color: var(--van-orange); }
.mini-btn.balance { border-color: rgba(var(--theme-primary-rgb), 0.3); }
.mini-btn.balance.active { border-color: var(--theme-primary); background: rgba(var(--theme-primary-rgb), 0.1); color: var(--theme-primary); }
.side-cards {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 400px;
  overflow-y: auto;
}
.transfer-arrow-col {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-primary);
  font-size: 18px;
}

/* ===== 提现模式 ===== */
.withdraw-out {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.withdraw-out .side-label {
  color: var(--theme-danger);
}
.withdraw-out-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--theme-bg-secondary);
  border: 1.5px solid rgba(var(--theme-primary-rgb), 0.3);
  border-radius: 10px;
  font-weight: 600;
  color: var(--theme-primary);
}
.withdraw-down {
  text-align: center;
  color: var(--theme-primary);
  font-size: 18px;
  margin: 4px 0;
}

/* 金额面板 */
.amount-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.currency-selector {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: var(--theme-bg-secondary);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  font-size: 13px;
  color: var(--theme-text-primary);
  margin-bottom: 12px;
  cursor: pointer;
}
.amount-display {
  display: flex;
  align-items: baseline;
  justify-content: center;
  margin: 32px 0 12px;
  padding: 28px 0;
  width: 100%;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  cursor: pointer;
}
.amount-symbol {
  font-size: 28px;
  color: var(--theme-text-tertiary);
  margin-right: 4px;
}
.amount-num {
  font-size: 56px;
  font-weight: 700;
  color: var(--theme-text-primary);
  font-family: "DIN Alternate", "SF Mono", monospace;
  letter-spacing: 1px;
}
.amount-tip {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 8px;
}
.exchange-tip {
  font-size: 13px;
  color: var(--theme-primary);
  margin-top: 4px;
}
.rate-field {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 14px;
  padding: 10px 14px;
  background: var(--theme-bg-secondary);
  border-radius: 10px;
  gap: 8px;
}
.rate-label {
  font-size: 14px;
  color: var(--theme-text-secondary);
  flex-shrink: 0;
}
.rate-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--theme-text-primary);
  background: transparent;
  min-width: 0;
  font-family: inherit;
}
.rate-input::placeholder { color: var(--theme-text-tertiary); }
.rate-unit {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--theme-text-tertiary);
}

/* 巨大下一步按钮 */
.next-big-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 80px;
  margin-top: 24px;
  padding: 20px 16px;
  font-family: inherit;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--theme-primary) 0%, var(--theme-primary-grad) 100%);
  border: none;
  border-radius: 14px;
  cursor: pointer;
  outline: none;
  box-shadow: 0 6px 16px rgba(var(--theme-primary-rgb), 0.25);
  transition: all 0.2s ease;
}
.next-big-btn:active { transform: scale(0.98); }
.next-big-btn:disabled {
  background: var(--theme-bg-tertiary);
  box-shadow: none;
  color: var(--theme-text-tertiary);
  cursor: not-allowed;
}
.next-big-btn .van-icon {
  font-size: 22px;
  font-weight: 700;
}

/* 对外转账收款人 */
.payee-field {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 14px 16px;
  background: var(--theme-bg-secondary);
  border-radius: 10px;
  gap: 8px;
  margin-bottom: 12px;
}
.payee-label {
  font-size: 15px;
  color: var(--theme-text-primary);
  font-weight: 600;
  flex-shrink: 0;
}
.payee-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--theme-text-primary);
  background: transparent;
  min-width: 0;
  font-family: inherit;
}
.payee-input::placeholder { color: var(--theme-text-tertiary); }

/* 摘要 */
.summary {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 16px;
  margin-top: 16px;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
}
.summary-row + .summary-row { border-top: 1px solid var(--theme-border); }
.summary-label { color: var(--theme-text-tertiary); }
.summary-val { color: var(--theme-text-primary); font-weight: 500; }
.summary-amount-row { margin-top: 4px; padding-top: 12px; }
.summary-amount {
  font-size: 24px;
  font-weight: 700;
  color: #ee0a24;
  font-family: "DIN Alternate", "SF Mono", monospace;
}

.qsa-actions { margin-top: 20px; }

/* 步骤切换动画 */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s ease;
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
.fade-up-enter-active,
.fade-up-leave-active {
  transition: all 0.25s ease;
}
.fade-up-enter-from,
.fade-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
