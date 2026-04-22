<template>
  <div class="page-flow-detail">
    <!-- 加载状态 -->
    <van-loading v-if="loading" class="loading-center" vertical
      >加载中...</van-loading
    >

    <!-- 详情内容 -->
    <div v-else-if="detail" class="detail-content">
      <!-- 金额卡片 -->
      <div class="amount-card">
        <div class="amount-label">
          {{ detail.direction === 1 ? "收入" : "支出" }}
          <span v-if="showExchange" class="currency-tag">
            {{ getCurrencySymbol(detail.currency) }} {{ detail.amount }}
          </span>
        </div>
        <div
          class="amount-value"
          :class="detail.direction === 1 ? 'income' : 'expense'"
        >
          {{ detail.direction === 1 ? "+" : "-" }}¥{{
            formatAmount(detail.amount)
          }}
        </div>
        <div class="exchange-info" v-if="showExchange">
          约 {{ getCurrencySymbol(detail.currency) }}{{ detail.amount }} = ¥{{
            formatAmount(detail.amount)
          }}
        </div>
      </div>

      <!-- 信息列表 -->
      <van-cell-group inset class="info-group">
        <van-cell title="分类">
          <template #value>
            <span>{{ getCategoryText(detail) }}</span>
          </template>
        </van-cell>
        <van-cell title="币种">
          <template #value>
            <span>{{ detail.currency || "CNY" }}</span>
          </template>
        </van-cell>
        <van-cell
          v-if="detail.currency && detail.currency !== 'CNY'"
          title="汇率"
        >
          <template #value>
            <span>{{ detail.exchange_rate || "-" }}</span>
          </template>
        </van-cell>
        <van-cell title="时间">
          <template #value>
            <span>{{ formatDateTime(detail.create_time) }}</span>
          </template>
        </van-cell>
        <van-cell title="交易方式">
          <template #value>
            <span>{{ detail.pay_method || "-" }}</span>
          </template>
        </van-cell>
        <van-cell title="关联卡片">
          <template #value>
            <div class="card-cell">
              <van-image
                v-if="getCardBankIcon(detail.card_id)"
                width="18"
                height="18"
                :src="getFullUrl(getCardBankIcon(detail.card_id))"
                fit="contain"
                class="card-cell-icon"
              />
              <span>{{ getCardText(detail.card_id) }}</span>
            </div>
          </template>
        </van-cell>
        <van-cell title="交易日期">
          <template #value>
            <span>{{ detail.trans_date || "-" }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 备注 -->
      <van-cell-group inset class="info-group">
        <van-cell title="备注">
          <template #value>
            <span class="remark-text">{{ detail.remark || "暂无备注" }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 操作按钮 -->
      <div class="action-btns">
        <van-button
          v-if="getReverseType(detail) && getReverseType(detail) !== 'credit-repay'"
          type="warning"
          round
          @click="handleReverse"
        >
          {{ getReverseBtnText(getReverseType(detail)) }}
        </van-button>
        <van-button
          v-if="getReverseType(detail) !== 'credit-repay'"
          type="primary"
          block
          round
          @click="openRemarkPopup"
        >
          修改备注
        </van-button>
      </div>

      <!-- 还款撤销提示 -->
      <div class="repay-hint" v-if="getReverseType(detail) === 'credit-repay'">
        <van-icon name="info-o" />
        <span>本收支不计支出。如需撤销还款，请到还款记录编辑</span>
      </div>

      <!-- 备注编辑弹窗 -->
      <van-popup v-model:show="showRemarkPopup" position="bottom" round>
        <div class="remark-popup">
          <div class="popup-header">
            <span>修改备注</span>
            <van-icon name="cross" @click="showRemarkPopup = false" />
          </div>
          <div class="popup-tip">请输入新的备注</div>
          <van-field
            v-model="editRemark"
            type="textarea"
            rows="3"
            placeholder="请输入备注"
            maxlength="200"
            show-word-limit
          />
          <div class="popup-footer">
            <van-button size="small" @click="showRemarkPopup = false"
              >取消</van-button
            >
            <van-button
              type="primary"
              size="small"
              :loading="remarkLoading"
              @click="handleSaveRemark"
              >保存</van-button
            >
          </div>
        </div>
      </van-popup>
    </div>

    <!-- 空状态 -->
    <van-empty v-else description="未找到相关记录" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showToast, showConfirmDialog } from "vant";
import dayjs from "dayjs";
import zhCn from "dayjs/locale/zh-cn";
import {
  getAccountDetail,
  updateAccountRemark,
  reverseDebit,
  reverseCreditExpense,
} from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";

dayjs.locale(zhCn);

const BASE_URL = ENV.FILE_BASE_URL;

const router = useRouter();
const route = useRoute();

// 状态
const loading = ref(true);
const detail = ref(null);
const cardList = ref([]);
const bankList = ref([]);
const showRemarkPopup = ref(false);
const editRemark = ref("");
const remarkLoading = ref(false);

// 格式化金额（根据币种和汇率计算）
const formatAmount = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  const num = Number(amount);
  if (!detail.value?.currency || detail.value.currency === "CNY") {
    return num.toFixed(2);
  }
  // 外币：金额 * 汇率 / 100
  const rate = Number(detail.value.exchange_rate) || 0;
  return ((num * rate) / 100).toFixed(2);
};

// 获取币种符号
const getCurrencySymbol = (code) => {
  const symbols = {
    CNY: "¥",
    USD: "$",
    EUR: "€",
    HKD: "HK$",
    JPY: "¥",
    GBP: "£",
    KRW: "₩",
    TWD: "NT$",
  };
  return symbols[code] || code || "¥";
};

// 是否显示汇率换算
const showExchange = computed(() => {
  return detail.value?.currency && detail.value.currency !== "CNY";
});

// 格式化日期时间（处理时间戳）
const formatDateTime = (timestamp) => {
  if (!timestamp) return "-";
  const d = dayjs(Number(timestamp));
  if (!d.isValid()) return "-";
  return d.format("YYYY-MM-DD HH:mm:ss");
};

// 获取完整 URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 根据 bank_id 获取银行信息
const getBankInfo = (bankId) => {
  return bankList.value.find((b) => b.id === bankId) || null;
};

// 获取卡片显示文本
const getCardText = (cardId) => {
  if (!cardId) return "-";
  if (cardId === "xxxx") return "现金";
  if (cardId === "yyyy") return "余额";

  const card = cardList.value.find((c) => c.id === cardId);
  const bankId = card?.bank_id || card?.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  const bankName = bank?.name || detail.value?.card_alias || card?.alias || card?.bank_name || "";
  const last4 = detail.value?.card_last4 || card?.card_last4 || card?.last4_no || card?.last4No || "";

  if (bankName && last4) return `${bankName} ****${last4}`;
  if (bankName) return bankName;
  if (last4) return `****${last4}`;
  return cardId;
};

// 获取卡片银行图标
const getCardBankIcon = (cardId) => {
  if (!cardId || cardId === "xxxx" || cardId === "yyyy") return "";
  const card = cardList.value.find((c) => c.id === cardId);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  const bank = bankId ? getBankInfo(bankId) : null;
  return bank?.icon_url || bank?.iconUrl || "";
};

// 获取分类显示文本
const getCategoryText = (item) => {
  if (item.category_id === "CATEGORY_REPAY") {
    return "信用卡还款";
  }
  return item.category_name || "未知分类";
};

// 加载卡片列表和银行分类
const loadCardList = async () => {
  try {
    const [cardRes, bankRes] = await Promise.all([
      getCardList(),
      categoryApi.list("bank"),
    ]);
    cardList.value = cardRes.data || cardRes || [];
    bankList.value = bankRes.data || bankRes || [];
  } catch (e) {
    cardList.value = [];
    bankList.value = [];
  }
};

// 加载详情
const loadDetail = async () => {
  loading.value = true;
  try {
    const res = await getAccountDetail(route.params.id);
    detail.value = res.data || res;
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 打开备注编辑弹窗
const openRemarkPopup = () => {
  editRemark.value = detail.value?.remark || "";
  showRemarkPopup.value = true;
};

// 保存备注
const handleSaveRemark = async () => {
  try {
    remarkLoading.value = true;
    await updateAccountRemark(route.params.id, editRemark.value);
    detail.value.remark = editRemark.value;
    showRemarkPopup.value = false;
    showToast("保存成功");
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    remarkLoading.value = false;
  }
};

// ========== 冲正相关 ==========

/**
 * 判断冲正类型
 */
const getReverseType = (item) => {
  if (!item) return null;
  const accountType = item.account_type;
  const categoryId = item.category_id;
  const payType = item.pay_type;

  // 还款撤销 → 提示用户去还款记录操作
  if (payType === "还款" && categoryId === "CATEGORY_REPAY") {
    return "credit-repay";
  }

  // 借记卡相关（包括 debit 和 virtual_balance）
  if (accountType === "debit" || accountType === "virtual_balance") {
    return "debit";
  }

  // 信用卡消费
  if (accountType === "credit") {
    return "credit-expense";
  }

  return null;
};

/**
 * 获取冲正按钮文本
 */
const getReverseBtnText = (type) => {
  const map = {
    debit: "借记卡冲正",
    "credit-expense": "消费冲正",
    "credit-repay": "还款撤销",
  };
  return map[type] || "";
};

/**
 * 执行冲正
 */
const handleReverse = async () => {
  const type = getReverseType(detail.value);
  if (!type) return;

  // 还款撤销提示用户去还款记录操作
  if (type === "credit-repay") {
    showToast("请前往还款记录进行撤销");
    return;
  }

  try {
    await showConfirmDialog({
      title: "确认冲正",
      message: `确定要执行${getReverseBtnText(type)}吗？`,
    });

    let res;
    switch (type) {
      case "debit":
        res = await reverseDebit(detail.value.id);
        break;
      case "credit-expense":
        res = await reverseCreditExpense(detail.value.id);
        break;
    }
    showToast(res.message || "冲正成功");
    return router.push("/finance/flow");
  } catch (e) {
    if (e !== "cancel") {
      // 错误已由拦截器处理
    }
  }
};

// 初始化
onMounted(() => {
  loadCardList();
  loadDetail();
});
</script>

<style scoped>
.page-flow-detail {
  min-height: 100vh;
  background: #f7f8fa;
}

.loading-center {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.amount-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  text-align: center;
  color: #fff;
}

.amount-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.currency-tag {
  margin-left: 8px;
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  font-size: 12px;
}

.exchange-info {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.7;
}

.amount-value {
  font-size: 36px;
  font-weight: bold;
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.amount-value.income {
  color: #07c160;
}

.amount-value.expense {
  color: #fff;
}

.info-group {
  margin: 16px;
  border-radius: 12px;
  overflow: hidden;
}

.remark-text {
  color: #646566;
  word-break: break-all;
}

.card-cell {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.card-cell-icon {
  border-radius: 3px;
  flex-shrink: 0;
}

.notice-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 16px;
  padding: 12px 16px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 13px;
  color: #fa8c16;
}

.notice-banner .van-icon {
  font-size: 16px;
}

.action-btns {
  padding: 20px 16px;
  display: flex;
  gap: 12px;
}

.action-btns .van-button {
  flex: 1;
}

.remark-popup {
  padding: 16px;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
}

.popup-tip {
  color: #969799;
  font-size: 14px;
  margin-bottom: 12px;
}

.popup-header .van-icon {
  font-size: 20px;
  color: #969799;
}

.popup-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.repay-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 16px;
  margin: 16px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 13px;
  color: #fa8c16;
}

.repay-hint .van-icon {
  font-size: 16px;
}
</style>
