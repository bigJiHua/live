<template>
  <div class="page-flow-detail">
    <!-- 加载状态 -->
    <van-loading v-if="loading" class="loading-center" vertical
      >加载中...</van-loading
    >

    <!-- 详情内容 -->
    <div v-else-if="detail" class="detail-content">
      <!-- 金额卡片 -->
      <div class="amount-card" :class="{ 'is-expense': detail.direction !== 1 }">
        <div class="amount-label">
          {{ detail.direction === 1 ? "收入" : "支出" }}
          <span class="currency-tag">{{ detail.currency || "CNY" }}</span>
        </div>
        <div
          class="amount-value"
          :class="detail.direction === 1 ? 'income' : 'expense'"
        >
          {{ detail.direction === 1 ? "+" : "-" }}{{ getCurrencySymbol(detail.currency) }}{{
            formatOriginalAmount(detail.amount)
          }}
        </div>
        <div class="exchange-info" v-if="showExchange">
          ≈ ¥{{ formatCnyAmount(detail.amount) }}（汇率 {{ detail.exchange_rate }}）
        </div>
      </div>

      <!-- 信息列表 -->
      <van-cell-group inset class="info-group">
        <van-cell title="分类" is-link @click="openCategoryPopup">
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
            <div style="display: flex; align-items: center; justify-content: flex-end;">
              <van-image
              v-if="getCardBankIcon(detail.card_id)"
              width="18"
              height="18"
              :src="getFullUrl(getCardBankIcon(detail.card_id))"
              fit="contain"
              style="vertical-align: middle; margin-right: 5px;"
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
            <div class="remark-cell">
              <span
                class="remark-text"
                :class="{ collapsed: !remarkExpanded && shouldTruncateRemark }"
                >{{ detail.remark || "暂无备注" }}</span
              >
              <span
                v-if="shouldTruncateRemark"
                class="remark-toggle"
                @click="remarkExpanded = !remarkExpanded"
                >{{ remarkExpanded ? "收起" : "展开" }}</span
              >
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 操作按钮 -->
      <div class="action-btns">
        <van-button
          v-if="getReverseType(detail) && getReverseType(detail) !== 'credit-repay' && getReverseType(detail) !== 'transfer-legacy'"
          :style="{ borderRadius: '5px', border: '2px dashed #f97316', background: 'transparent', color: '#f97316' }"
          round
          @click="handleReverse"
        >
          {{ getReverseBtnText(getReverseType(detail), detail) }}
        </van-button>
        <van-button
          v-if="getReverseType(detail) !== 'credit-repay'"
          :style="{ borderRadius: '5px', border: '2px dashed #3b82f6', background: 'transparent', color: '#3b82f6' }"
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

      <!-- 旧转账记录提示 -->
      <div class="repay-hint" v-if="getReverseType(detail) === 'transfer-legacy'">
        <van-icon name="info-o" />
        <span>此为旧版转账记录，暂不支持自动冲正。如需撤销请手动创建相反方向的收支记录</span>
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

      <!-- 分类编辑弹窗 -->
      <van-popup v-model:show="showCategoryPopup" position="bottom" round>
        <div class="category-popup">
          <div class="popup-header">
            <span>{{ categoryPopupTitle }}</span>
            <van-icon name="cross" @click="showCategoryPopup = false" />
          </div>
          <div class="popup-tip">请选择新的{{ categoryDirectionText }}</div>
          <div class="category-list">
            <div
              v-for="cat in categoryList"
              :key="cat.id"
              class="category-item"
              :class="{ active: selectedCategoryId === cat.id }"
              @click="selectCategory(cat)"
            >
              <span class="category-name">{{ cat.name }}</span>
              <van-icon v-if="selectedCategoryId === cat.id" name="success" color="#07c160" />
            </div>
          </div>
          <div class="popup-footer">
            <van-button size="small" @click="showCategoryPopup = false">取消</van-button>
            <van-button
              type="primary"
              size="small"
              :loading="categoryLoading"
              @click="handleSaveCategory"
            >保存</van-button>
          </div>
        </div>
      </van-popup>
    </div>

    <!-- 空状态 -->
    <van-empty v-else description="未找到相关记录" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showToast, showConfirmDialog } from "vant";
import dayjs from "dayjs";
import zhCn from "dayjs/locale/zh-cn";
import {
  getAccountDetail,
  updateAccount,
  updateAccountRemark,
  reverseDebit,
  reverseCreditExpense,
  reverseTransfer,
} from "@/utils/api/account";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";
import { useFlowSyncStore } from "@/stores/flowSync";
defineOptions({ name: "FinanceFlowDetail" });

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
const showCategoryPopup = ref(false);
const categoryList = ref([]);
const selectedCategoryId = ref(null);
const categoryLoading = ref(false);
const remarkExpanded = ref(false);

const shouldTruncateRemark = computed(
  () => (detail.value?.remark || "").length > 30
);

const flowSync = useFlowSyncStore()

// 格式化原币金额（不做换算）
const formatOriginalAmount = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount).toFixed(2);
};

// 格式化 CNY 换算金额（外币时使用）
const formatCnyAmount = (amount) => {
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

  if (bankName && last4) return `${bankName} ${last4}`;
  if (bankName) return bankName;
  if (last4) return ` ${last4}`;
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
    const data = res.data || res;
    if (!data || !data.id) {
      // 流水不存在或已被删除 → 跳回列表页
      showToast("记录不存在或已被删除");
      router.replace("/finance/flow");
      return;
    }
    detail.value = data;
  } catch (e) {
    // 后端返回 404 或其他错误 → 跳回列表页
    router.replace("/finance/flow");
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
    flowSync.recordChange(route.params.id, { remark: editRemark.value })
    showRemarkPopup.value = false;
    showToast("保存成功");
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    remarkLoading.value = false;
  }
};

// 加载分类列表
const loadCategoryList = async (direction) => {
  try {
    const type = direction === 1 ? 'income' : 'expense'
    const res = await categoryApi.list(type);
    categoryList.value = res.data || res || [];
  } catch (e) {
    categoryList.value = [];
  }
};

const categoryDirectionText = computed(() => {
  return detail.value?.direction === 1 ? '收入分类' : '支出分类'
})

const categoryPopupTitle = computed(() => {
  return `选择${categoryDirectionText.value}`
})

// 打开分类编辑弹窗
const openCategoryPopup = () => {
  selectedCategoryId.value = detail.value.category_id;
  loadCategoryList(detail.value.direction);
  showCategoryPopup.value = true;
};

// 选择分类
const selectCategory = (cat) => {
  selectedCategoryId.value = cat.id;
};

// 保存分类
const handleSaveCategory = async () => {
  if (!selectedCategoryId.value) {
    showToast("请选择分类");
    return;
  }
  if (selectedCategoryId.value === detail.value.category_id) {
    showCategoryPopup.value = false;
    return;
  }
  try {
    await showConfirmDialog({
      title: '确认修改',
      message: `确定将分类从「${getCategoryText(detail.value)}」改为「${categoryList.value.find(c => c.id === selectedCategoryId.value)?.name || ''}」吗？`,
    });
  } catch {
    return;
  }
  try {
    categoryLoading.value = true;
    await updateAccount(route.params.id, { categoryId: selectedCategoryId.value });
    const selected = categoryList.value.find(c => c.id === selectedCategoryId.value);
    detail.value.category_id = selectedCategoryId.value;
    detail.value.category_name = selected?.name || "未知分类";
    flowSync.recordChange(route.params.id, { category_name: selected?.name || '未知分类', category_id: selectedCategoryId.value })
    showCategoryPopup.value = false;
    showToast("分类已更新");
  } catch (e) {
    // 错误已由拦截器处理
  } finally {
    categoryLoading.value = false;
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

  // 转账冲正（仅自转和提现）
  // 转账记录的 pay_type 固定为 "转账"，有 transfer_group_id 的才是成对记录
  if (payType === "转账" && item.transfer_group_id) {
    return "transfer";
  }

  // 旧版转账记录（无 transfer_group_id）→ 不显示冲正按钮，仅提示
  if (payType === "转账" && !item.transfer_group_id) {
    return "transfer-legacy";
  }

  // 借记卡相关（包括 debit、virtual_balance、virtual_cash）
  if (accountType === "debit" || accountType === "virtual_balance" || accountType === "virtual_cash") {
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
const getReverseBtnText = (type, item) => {
  if (type === "debit" && item) {
    const acct = item.account_type
    if (acct === "virtual_balance") return "余额冲正"
    if (acct === "virtual_cash") return "现金冲正"
    return "借记卡冲正"
  }
  const map = {
    "credit-expense": "消费冲正",
    "credit-repay": "还款撤销",
    transfer: "转账冲正",
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
      case "transfer":
        res = await reverseTransfer(detail.value.id);
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

// 路由 id 变化时重新加载（处理同组件复用场景）
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    loadDetail();
  }
});

// 初始化
onMounted(() => {
  loadCardList();
  loadDetail();
});
</script>

<style scoped>
.page-flow-detail {
  background: #f7f8fa;
}

.loading-center {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.amount-card {
  background: linear-gradient(135deg, #79818f 0%, #052356 100%);
  padding: 40px 20px;
  text-align: center;
  color: #fff;
}

.amount-card.is-expense {
  background: linear-gradient(135deg, #79818f 0%, #052356 100%);
}

.amount-label {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.currency-tag {
  margin-left: 8px;
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  font-size: 12px;
}

.exchange-info {
  margin-top: 8px;
  font-size: 12px;
  opacity: 0.85;
}

.amount-value {
  font-size: 36px;
  font-weight: bold;
  font-family: "DIN Alternate", -apple-system, sans-serif;
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
  white-space: pre-wrap;
  text-align: right;
}

.remark-text.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.remark-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 70%;
}

.remark-toggle {
  color: #1989fa;
  font-size: 12px;
  margin-top: 4px;
  cursor: pointer;
}

.card-cell {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 5px;
  flex-wrap: nowrap;
  text-align: left;
}

.card-cell-icon {
  border-radius: 3px;
  flex-shrink: 0;
  align-self: center;
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
  justify-content: center;
}

.action-btns .van-button {
  flex: 0 0 auto;
  width: 150px;
  padding: 0 10px;
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

.category-popup {
  padding: 16px;
  max-height: 60vh;
  overflow-y: auto;
}

.category-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 12px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.category-item:active {
  background: #f7f8fa;
}

.category-item.active {
  border-color: #07c160;
  background: #f0fff5;
}

.category-item .van-icon-success {
  position: absolute;
  top: 4px;
  right: 4px;
  font-size: 14px;
}

.category-name {
  font-size: 12px;
  color: #323233;
  text-align: center;
  line-height: 1.3;
}
</style>
