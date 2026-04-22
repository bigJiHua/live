<template>
  <div class="page-structure">
    <div class="page-header">
      <div class="header-title">账户余额</div>
      <div class="header-sub">查看各账户余额</div>
    </div>

    <div class="balance-content">
      <!-- 总资产卡片 -->
      <div class="total-card" :class="{ 'no-balance': totalBalance === 0 }">
        <div class="total-label">总资产</div>
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
      <div class="account-list" v-if="bankCards.length > 0">
        <div
          v-for="account in bankCards"
          :key="account.card_id"
          class="account-item"
        >
          <div class="account-left">
            <div class="account-icon bank-icon" v-if="!getCardBankInfo(account.card_id).bankIcon">
              <van-icon name="card" size="20" color="#fff" />
            </div>
            <div class="account-icon bank-icon-img" v-else>
              <van-image width="28" height="28" :src="getFullUrl(getCardBankInfo(account.card_id).bankIcon)" fit="contain" />
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
            <div class="account-balance" :class="{ 'is-zero': Number(account.balance) === 0, 'is-negative': Number(account.balance) < 0 }">
              {{ showAmount ? '¥' + formatMoney(account.balance) : '******' }}
            </div>
          </div>
        </div>
      </div>

      <van-empty v-if="accountList.length === 0 && !loading" description="暂无账户数据" />

      <!-- 查看提示 -->
      <div class="view-tip" v-if="accountList.length > 0">
        <van-icon name="eye-o" />
        <span>余额由收支计划自动计算，仅供查看</span>
      </div>
    </div>

    <van-overlay :show="loading">
      <div class="flex-center">
        <van-loading />
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { getBalanceList } from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";

const BASE_URL = ENV.FILE_BASE_URL;

const router = useRouter();
const showAmount = ref(true);
const loading = ref(false);
const accountList = ref([]);
const bankList = ref([]);
const cardList = ref([]);

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
    return virtualConfig[account.card_id]?.color || "#1989fa";
  }
  return "#7232dd";
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
  await Promise.all([loadBankList(), loadCardList()]);
  loadData();
});
</script>

<style scoped>
.page-structure {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 24px 20px;
  color: #fff;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}

.header-sub {
  font-size: 13px;
  opacity: 0.8;
}

.balance-content {
  padding: 16px;
}

.total-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  text-align: center;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.total-label {
  font-size: 14px;
  color: #969799;
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
  color: #323233;
  font-weight: 500;
}

.amount-num {
  font-size: 32px;
  font-weight: bold;
  color: #323233;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.zero-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  padding: 10px 16px;
  background: #fff7e6;
  border-radius: 20px;
  font-size: 13px;
  color: #fa8c16;
  cursor: pointer;
}

.zero-tip:active {
  background: #ffecc7;
}

.section-title {
  font-size: 13px;
  color: #969799;
  margin: 16px 0 8px;
  padding-left: 4px;
}

.account-list {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 8px;
}

.account-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f2f2f2;
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.bank-icon-img {
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.account-info .account-name {
  font-size: 15px;
  font-weight: 500;
  color: #323233;
  margin-bottom: 4px;
}

.account-info .account-type {
  font-size: 12px;
  color: #969799;
}

.account-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-balance {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.account-balance.is-zero {
  color: #969799;
}

.account-balance.is-negative {
  color: #ee0a24;
}

.view-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 24px;
  font-size: 12px;
  color: #c8c9cc;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
