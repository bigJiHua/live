<template>
  <div class="page-structure">
    <div class="balance-content">
      <!-- 总资产卡片 -->
      <div class="total-card" :class="{ 'no-balance': totalBalance === 0 }">
        <div class="total-label">系统内计总资产</div>
        <div class="total-amount">
          <span class="currency">¥</span>
          <span class="amount-num" @click="toggleBalance">
            {{ showAmount ? formatMoney(totalBalance) : '******' }}
          </span>
        </div>
        <!-- 余额为0时的提示 -->
        <div class="zero-tip" v-if="totalBalance === 0" @click="goToFinance">
          <van-icon name="info-o" />
          <span>余额为零，去记账获取收支计划</span>
          <van-icon name="arrow" />
        </div>
      </div>

      <!-- 虚拟账户 -->
      <div class="section-title">虚拟账户</div>
      <div class="account-list">
        <div
          v-for="account in virtualAccounts"
          :key="account.card_id"
          class="account-item"
        >
          <div class="account-left">
            <div class="account-icon" :style="{ background: getAccountColor(account) }">
              <van-icon :name="getAccountIcon(account)" size="20" color="#fff" />
            </div>
            <div class="account-info">
              <div class="account-name">{{ account.alias }}</div>
              <div class="account-type">{{ getAccountTypeLabel(account) }}</div>
            </div>
          </div>
          <div class="account-right">
            <div class="account-balance" :class="{ 'is-zero': Number(account.balance) === 0, 'is-negative': Number(account.balance) < 0 }">
              {{ showAmount ? '¥' + formatMoney(account.balance) : '******' }}
            </div>
          </div>
        </div>
      </div>

      <!-- 银行卡 -->
      <div class="section-title" v-if="bankCards.length > 0">银行卡</div>
      <div class="bank-search" v-if="bankCards.length > 0">
        <div class="bank-search-row">
          <van-search
            v-model="bankSearchKey"
            placeholder="搜索银行卡"
            @clear="bankSearchKey = ''"
          />
          <button class="preview-btn" type="button" @click="goToPreview">
            <van-icon :name="showFlowStats ? 'exchange' : 'eye-o'" />
            <span>{{ showFlowStats ? '余额' : '动账' }}</span>
          </button>
        </div>
      </div>
      <div class="account-list" v-if="bankCards.length > 0">
        <div
          v-for="account in filteredBankCards"
          :key="account.card_id"
          class="account-item"
          @click="goToCardFlow(account)"
        >
          <div class="account-left">
            <!-- 银行 logo：加载失败统一毛玻璃首字兜底 -->
            <div class="account-icon">
              <BankIcon
                :src="getFullUrl(getCardBankInfo(account.card_id).bankIcon)"
                :name="getCardBankInfo(account.card_id).bankName || account.card_alias || '卡'"
                :size="28"
                rounded="10"
              />
            </div>
            <div class="account-info">
              <div class="account-name">
                {{ getCardBankInfo(account.card_id).bankName || account.card_alias || '银行卡' }}
              </div>
              <div class="account-type">
                <span v-if="account.card_last4 || getCardBankInfo(account.card_id).cardLast4">****{{ account.card_last4 || getCardBankInfo(account.card_id).cardLast4 }}</span>
                <span v-else>借记卡</span>
              </div>
            </div>
          </div>
          <div class="account-right">
            <template v-if="showFlowStats">
              <div
                class="flow-stats"
                :class="{ 'is-empty': isCardIdle(account.card_id) }"
              >
                <template v-if="isCardIdle(account.card_id)">
                  <span class="flow-empty">近6个月无动账</span>
                </template>
                <template v-else>
                  <div class="flow-line">消费 {{ getFlowExpense(account.card_id) }} 笔</div>
                  <div class="flow-line">收入 {{ getFlowIncome(account.card_id) }} 笔</div>
                </template>
              </div>
            </template>
            <template v-else>
              <div class="account-balance" :class="{ 'is-zero': Number(account.balance) === 0, 'is-negative': Number(account.balance) < 0 }">
                {{ showAmount ? '¥' + formatMoney(account.balance) : '******' }}
              </div>
            </template>
            <van-icon name="arrow" color="#c8c9cc" class="arrow-icon" />
          </div>
        </div>
      </div>

      <van-empty v-if="accountList.length === 0 && !loading" description="暂无账户数据" />

      <!-- 查看提示 -->
      <div class="view-tip" v-if="accountList.length > 0">
        <van-icon name="eye-o" />
        <span>余额由收支计划自动计算，仅供参考</span>
      </div>
    </div>

    <van-icon v-show="showBackTop" name="back-top" class="back-top" @click="scrollToTop" />

    <van-overlay :show="loading">
      <div class="flex-center">
        <van-loading />
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { getBalanceList, getCardsFlowStats } from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";
import BankIcon from "@/components/BankIcon.vue";

const BASE_URL = ENV.FILE_BASE_URL;

const router = useRouter();
const showAmount = ref(true);
const loading = ref(false);
const showBackTop = ref(false);

const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
const onWindowScroll = () => { showBackTop.value = window.scrollY > 400 }
const accountList = ref([]);
const bankList = ref([]);
const cardList = ref([]);
const bankSearchKey = ref('');
const bankIconError = ref({});

// 获取完整 URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 虚拟账户配置
const virtualConfig = {
  xxxx: { label: "现金", icon: "coupon-o", color: "#07c160" },
  yyyy: { label: "余额", icon: "wechat", color: "#07c160" },
};

// 判断是否是虚拟账户
const isVirtualAccount = (cardId) => Object.keys(virtualConfig).includes(cardId);

const getAccountIcon = (account) => {
  if (isVirtualAccount(account.card_id)) {
    return virtualConfig[account.card_id]?.icon || "wallet-o";
  }
  return "card";
};

const getAccountColor = (account) => {
  if (isVirtualAccount(account.card_id)) {
    return virtualConfig[account.card_id]?.color || "var(--theme-primary)";
  }
  return "var(--theme-primary)";
};

const getAccountTypeLabel = (account) => {
  if (isVirtualAccount(account.card_id)) {
    return virtualConfig[account.card_id]?.label || "虚拟账户";
  }
  const info = getCardBankInfo(account.card_id);
  return info.bankName || "借记卡";
};

// 根据 bank_id 获取银行信息
const getBankInfo = (bankId) => {
  const bank = bankList.value.find((b) => b.id === bankId);
  return bank || null;
};

// 通过 card_id 关联卡片 → 银行分类，获取银行名 + icon + 尾号
const getCardBankInfo = (cardId) => {
  const card = cardList.value.find((c) => c.id === cardId || c.card_id === cardId);
  if (!card) return {};
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  return {
    bankName: bank?.name || "",
    bankIcon: bank?.icon_url || bank?.iconUrl || "",
    cardLast4: card.card_last4 || card.last4 || "",
  };
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

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList({ cardType: "debit" });
    cardList.value = res.data || res || [];
  } catch (e) {
    cardList.value = [];
  }
};

const virtualAccounts = computed(() =>
  accountList.value.filter(acc => isVirtualAccount(acc.card_id))
);

const bankCards = computed(() =>
  accountList.value.filter(acc => !isVirtualAccount(acc.card_id))
);

const filteredBankCards = computed(() => {
  const key = bankSearchKey.value.toLowerCase().trim()
  if (!key) return bankCards.value
  return bankCards.value.filter(acc => {
    const bankInfo = getCardBankInfo(acc.card_id)
    const bankName = bankInfo.bankName || ''
    const cardAlias = acc.card_alias || ''
    const cardNo = acc.card_last4 || bankInfo.cardLast4 || ''
    return bankName.toLowerCase().includes(key) ||
           cardAlias.toLowerCase().includes(key) ||
           cardNo.toLowerCase().includes(key)
  })
});

const totalBalance = computed(() =>
  accountList.value.reduce((sum, acc) => sum + Number(acc.balance), 0)
);

const toggleBalance = () => {
  showAmount.value = !showAmount.value;
};

const formatMoney = (val) => {
  const num = Number(val) || 0;
  return num.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

const goToFinance = () => {
  router.push("/finance");
};

const goToCardFlow = (account) => {
  router.push(`/finance/report/card-flow?cardId=${account.card_id}`);
};

// 消费预览状态：点击后拉取所有银行卡近6个月支出/收入笔数，并就地切换余额展示为笔数
const showFlowStats = ref(false);
const flowStatsMap = ref({}); // card_id -> { expenseCount, incomeCount }

const getFlowExpense = (cardId) => flowStatsMap.value[cardId]?.expenseCount ?? 0;
const getFlowIncome = (cardId) => flowStatsMap.value[cardId]?.incomeCount ?? 0;
// 该卡近6个月无任何支出/收入 → 视为无动账
const isCardIdle = (cardId) =>
  getFlowExpense(cardId) === 0 && getFlowIncome(cardId) === 0;

// 消费预览：切换余额/笔数视图，进入预览态时批量拉取所有银行卡近6个月收支笔数
const goToPreview = async () => {
  // 已处于预览态则切回余额展示
  if (showFlowStats.value) {
    showFlowStats.value = false;
    return;
  }
  try {
    const res = await getCardsFlowStats({ months: 6 });
    const list = res.data?.list || [];
    const map = {};
    list.forEach((item) => {
      map[item.cardId] = {
        expenseCount: item.expenseCount || 0,
        incomeCount: item.incomeCount || 0,
      };
    });
    // 严格按 card_id 匹配，保证每张卡颗粒度精确
    flowStatsMap.value = map;
    showFlowStats.value = true;
  } catch (e) {
    console.error("获取消费预览失败", e);
    showToast("获取失败");
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res = await getBalanceList();
    accountList.value = res.data || [];
  } catch (e) {
    console.error("加载失败", e);
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  window.addEventListener('scroll', onWindowScroll, { passive: true });
  await Promise.all([loadBankList(), loadCardList()]);
  loadData();
});
onUnmounted(() => {
  window.removeEventListener('scroll', onWindowScroll);
});
</script>

<style scoped>
.page-structure {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 20px;
}

.balance-content {
  padding: 16px;
}

.total-card {
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  padding: 24px;
  text-align: center;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.total-label {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  margin-bottom: 8px;
}

.total-amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
}

.currency {
  font-size: 20px;
  color: var(--theme-text-primary);
  font-weight: 500;
}

.amount-num {
  font-size: 32px;
  font-weight: bold;
  color: var(--theme-text-primary);
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.zero-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  padding: 10px 16px;
  background: var(--van-orange-bg);
  border-radius: 20px;
  font-size: 13px;
  color: var(--van-orange);
  cursor: pointer;
}

.zero-tip:active {
  background: rgba(255, 151, 106, 0.2);
}

.section-title {
  font-size: 13px;
  color: var(--theme-text-tertiary);
  margin: 16px 0 8px;
  padding-left: 4px;
}

.account-list {
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 8px;
}

.account-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--theme-border);
  cursor: pointer;
}

.account-item:active {
  background: var(--theme-bg-primary);
}

.account-item:last-child {
  border-bottom: none;
}

.account-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bank-icon {
  background: linear-gradient(135deg, var(--theme-primary) 0%, var(--theme-primary-grad) 100%);
}

/* 银行名首字占位（logo 缺失/加载失败时） */
.bank-mock {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.bank-icon-img {
  background: var(--theme-bg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.account-info .account-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--theme-text-primary);
  margin-bottom: 4px;
}

.account-info .account-type {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.account-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-balance {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.account-balance.is-zero {
  color: var(--theme-text-tertiary);
}

.account-balance.is-negative {
  color: var(--theme-danger-color);
}

/* 消费预览：近6个月支出/收入笔数 */
.flow-stats {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  text-align: right;
  font-size: 13px;
  line-height: 1.5;
  color: var(--theme-text-secondary);
}

.flow-line {
  white-space: nowrap;
}

.flow-stats.is-empty .flow-empty {
  color: var(--theme-danger-color);
  font-weight: 500;
}

.view-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 24px;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.arrow-icon {
  flex-shrink: 0;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

.bank-search {
  padding: 0 16px;
  background: var(--theme-bg-secondary);
}

.bank-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bank-search-row :deep(.van-search) {
  flex: 1;
  min-width: 0;
}

.preview-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  padding: 7px 14px;
  border: none;
  border-radius: 999px;
  background: var(--theme-primary);
  color: #fff;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.preview-btn:active {
  opacity: 0.85;
}

/* 搜索框适配主题 */
.bank-search :deep(.van-search) {
  background: var(--theme-bg-secondary);
  padding: 8px 0;
}
.bank-search :deep(.van-search__content) {
  background: var(--theme-bg-tertiary);
  border-radius: 999px;
}
.bank-search :deep(.van-field__control) {
  color: var(--theme-text-primary);
}
.bank-search :deep(.van-field__control::placeholder) {
  color: var(--theme-text-placeholder);
}
.bank-search :deep(.van-search__action) {
  color: var(--theme-primary);
}

/* 返回顶部 */
.back-top {
  position: fixed;
  right: 16px;
  bottom: 60px;
  width: 40px;
  height: 40px;
  background: var(--theme-bg-secondary);
  border-radius: 50%;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--theme-primary);
  z-index: 999;
}
</style>
