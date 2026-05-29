<template>
  <div class="page-bill-list">

    <!-- 卡片选择 -->
    <div class="filter-section">
      <van-cell-group inset>
        <van-field
          v-model="selectedCardName"
          label="选择卡片"
          placeholder="全部卡片"
          is-link
          readonly
          @click="showCardPicker = true"
        />
        <van-field
          v-model="monthText"
          label="代还月份"
          placeholder="请选择月份"
          is-link
          readonly
          @click="showMonthPicker = true"
        />
      </van-cell-group>
    </div>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        v-model="selectedValues"
        title="选择月份"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <!-- 账单列表 -->
    <div class="bill-list" v-if="billList.length > 0">
      <div
        v-for="item in billList"
        :key="item.id"
        class="bill-card"
        @click="goToDetail(item)"
      >
        <div class="bill-header">
          <div class="bill-info">
            <div class="bill-name-row">
              <img 
                v-if="getCardBankIcon(item)" 
                :src="getFullUrl(getCardBankIcon(item))" 
                class="bank-icon"
              />
              <span class="bill-card-name">{{ getCardDisplayName(item) }}</span>
              <span class="bill-card-last4" v-if="item.card_last4">**** {{ item.card_last4 }}</span>
              <van-button size="mini" plain type="primary" class="detail-btn" @click.stop="goToLedger(item)">明细</van-button>
            </div>
            <div class="bill-fee-info">
              <span>年费 ¥{{ formatMoney(item.annual_fee) }}</span>
              <span class="fee-divider">|</span>
              <span>{{ getFeeFreeRuleText(item.fee_free_rule) }}</span>
            </div>
          </div>
          <div class="bill-status-col">
            <van-tag :type="getStatusType(item)" :class="{ 'tag-normal': getStatusType(item) === '' }">
              {{ getStatusText(item) }}
            </van-tag>
            <div class="status-extra" v-if="getStatusExtra(item)">
              {{ getStatusExtra(item) }}
            </div>
          </div>
        </div>

        <div class="bill-body">
          <div class="bill-limit-small">
            <div class="limit-row">
              <span>额度</span>
              <span>¥{{ formatMoney(item.credit_limit) }}</span>
            </div>
            <div class="limit-row">
              <span>可用</span>
              <span>¥{{ formatMoney(item.avail_limit) }}</span>
            </div>
          </div>
          <div class="bill-amount-right">
            <div class="amount-col">
              <div class="repay-label">{{ getBillMonthText(item) }}</div>
              <div class="repay-value" :class="{ overdue: Number(item.used_limit) > 0 }">
                ¥{{ formatMoney(item.used_limit) }}
              </div>
            </div>
            <div class="amount-col">
              <div class="repay-label">待还</div>
              <div class="repay-value danger">
                ¥{{ formatMoney(item.need_repay) }}
              </div>
            </div>
          </div>
        </div>

        <div class="bill-footer">
          <div class="bill-day-info">
            <div class="limit-row">
              <span>本月账单日</span>
              <span style="color: red;">{{ item.bill_day }}</span>号
            </div>
            <div class="limit-row">
              <span>次月还款日</span>
              <span style="color: red;">{{ item.repay_day }}</span>号
            </div>
          </div>
          <div class="bill-actions">
            <van-button
              size="small"
              plain
              round
              @click.stop="refreshBill(item, $event)"
            >
              刷新账单
            </van-button>
            <van-button
              size="small"
              type="danger"
              round
              @click.stop="goToRepay(item)"
            >
              立即还款
            </van-button>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && billList.length === 0" description="暂无账单记录" />

    <!-- 添加账单按钮 -->
    <div class="add-btn-wrap" v-if="canAddBill">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加账单</span>
      </button>
    </div>

    <van-overlay :show="loading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>

    <!-- 卡片选择器 -->
    <van-popup v-model:show="showCardPicker" position="bottom">
      <van-picker
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { showToast } from "vant";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { getBillList, getCardList, rebuildBill } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";

const BASE_URL = ENV.FILE_BASE_URL;

const router = useRouter();

const billList = ref([]);
const cardList = ref([]);
const bankList = ref([]);
const loading = ref(false);
const selectedCardId = ref(null);
const selectedCardName = ref("");
const showCardPicker = ref(false);

// 月份选择（Vant 4 写法）
const now = dayjs();
const currentYear = ref(now.year());
const currentMonth = ref(now.month() + 1);
const showMonthPicker = ref(false);
// Vant 4 必须绑定 v-model 数组来控制选中项
const selectedValues = ref([`${now.year()}年`, `${now.month() + 1}月`]);
const monthText = computed(() => `${currentYear.value}年${currentMonth.value}月`);

// 月份选择器列
const pickerColumns = computed(() => {
  const currentYearVal = dayjs().year();
  const years = [];
  for (let i = currentYearVal - 10; i <= currentYearVal + 10; i++) {
    years.push({ text: `${i}年`, value: `${i}年` });
  }
  const months = Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: `${i + 1}月`,
  }));
  return [years, months];
});

// 月份确认
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText);
  selectedValues.value = [yearText, monthText];
  showMonthPicker.value = false;
  loadBillList();
};

// 卡片选择列
const cardColumns = computed(() => {
  const cols = [{ text: "全部卡片", value: null }];
  cardList.value.forEach(card => {
    cols.push({
      text: `${card.alias || card.bank_name || '卡片'} **** ${card.last4_no || card.last4No || '****'}`,
      value: card.id
    });
  });
  return cols;
});

// 是否可以添加账单（需要先有信用卡）
const canAddBill = computed(() => {
  return cardList.value.some(card => (card.card_type || card.cardType) === 'credit');
});

// 加载账单列表
const loadBillList = async () => {
  loading.value = true;
  try {
    const params = {};
    if (selectedCardId.value) {
      params.cardId = selectedCardId.value;
    }
    // 添加账单月份筛选
    const billMonth = `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`;
    params.billMonth = billMonth;
    const res = await getBillList(params);
    let list = res.data || res || [];
    // 排序：有欠款（need_repay > 0）按账单日升序排前面，无欠款的放底部
    list.sort((a, b) => {
      const aHasDebt = Number(a.need_repay) > 0
      const bHasDebt = Number(b.need_repay) > 0
      // 有欠款的优先
      if (aHasDebt !== bHasDebt) return aHasDebt ? -1 : 1
      // 都有欠款或都无欠款时，按账单日排序
      const aDate = dayjs(`${a.bill_month}-${String(a.bill_day).padStart(2, '0')}`)
      const bDate = dayjs(`${b.bill_month}-${String(b.bill_day).padStart(2, '0')}`)
      return aDate.valueOf() - bDate.valueOf()
    })
    billList.value = list
  } catch (error) {
    showToast(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList({ cardType: 'credit' });
    cardList.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    bankList.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 卡片选择确认
const onCardConfirm = ({ selectedOptions }) => {
  selectedCardId.value = selectedOptions[0].value;
  selectedCardName.value = selectedOptions[0].text;
  showCardPicker.value = false;
  loadBillList();
};

// 计算账单状态（核心逻辑）
// 基于 bill_day（账单日）和 repay_day（次月还款日）做本地计算
const getBillStatus = (item) => {
  if (!item) return { type: '', text: '正常', extra: '' }

  const now = dayjs()
  const billMonth = item.bill_month
  const billDayNum = Number(item.bill_day)
  const repayDayNum = Number(item.repay_day)

  // 账单日：bill_month 月的 bill_day 号
  const billDate = dayjs(`${billMonth}-${String(billDayNum).padStart(2, '0')}`)
  if (!billDate.isValid()) return { type: '', text: '正常', extra: '' }

  // 还款日：账单日所在月的**次月**的 repay_day 号
  const repayDate = billDate.add(1, 'month').date(repayDayNum)

  const needRepay = Number(item.need_repay) || 0
  const daysToBill = billDate.diff(now, 'day')
  const daysToRepay = repayDate.diff(now, 'day')
  const daysAfterBill = now.diff(billDate, 'day')

  // ① 已逾期：超过还款日且仍有欠款
  if (needRepay > 0 && now.isAfter(repayDate, 'day')) {
    const overdueDays = now.diff(repayDate, 'day')
    return { type: 'danger', text: `已逾期${overdueDays}天`, extra: '' }
  }

  // ② 已过账单日（已出账）
  if (!now.isBefore(billDate, 'day')) {
    // 无欠款或已还清
    if (needRepay === 0 || item.repay_status === '已还清') {
      const extra = daysAfterBill > 0 ? `已出账${daysAfterBill}天` : ''
      return { type: 'success', text: '已还清', extra }
    }
    // 有欠款且在还款日前 → 待还款
    const extra = daysToRepay >= 0 ? `${daysToRepay}天后还款日` : ''
    return { type: 'warning', text: '待还款', extra }
  }

  // ③ 未到账单日（未出账）
  if (needRepay > 0) {
    const extra = `出账${daysToBill}天·还款${daysToRepay}天`
    return { type: 'default', text: '未出账', extra }
  }

  // ④ 默认：正常
  return { type: '', text: '正常', extra: '' }
}

// 获取状态类型（供模板使用）
const getStatusType = (item) => getBillStatus(item).type

// 获取状态文本（供模板使用）
const getStatusText = (item) => getBillStatus(item).text

// 获取附加信息（如"XX天后出账"等，供模板使用）
const getStatusExtra = (item) => getBillStatus(item).extra

// 格式化金额
const formatMoney = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

// 格式化日期 - 只显示天数
const formatDay = (date) => {
  if (!date) return "--";
  // 格式如 2026-03-06，提取最后5位中的天数部分
  const match = date.match(/-(\d{2})$/);
  return match ? match[1] : "--";
};

// 根据 bank_id 获取银行信息
const getBankInfo = (bankId) => {
  return bankList.value.find((b) => b.id === bankId) || null;
};

// 获取完整 URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 获取卡片关联的银行图标
const getCardBankIcon = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  if (!bankId) return "";
  const bank = getBankInfo(bankId);
  return bank?.icon || bank?.image || "";
};

// 获取卡片显示名称
const getCardDisplayName = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  const bankId = card?.bank_id || card?.bankId;
  const bank = getBankInfo(bankId);
  const bankName = bank?.name || "";
  if (item.card_alias) return item.card_alias;
  if (bankName) return bankName;
  return '信用卡';
};

// 获取免年费规则文本
const getFeeFreeRuleText = (rule) => {
  if (!rule) return '无免年费规则';
  // 如果是纯数字，显示 "X笔消费免"
  if (/^\d+$/.test(String(rule))) {
    return `${rule}笔消费免`;
  }
  // 否则直接显示原有值
  return rule;
};

// 获取账单月份文本（根据 bill_start_date 判断）
const getBillMonthText = (item) => {
  if (!item.bill_start_date) return '账单';
  const d = dayjs(item.bill_start_date);
  if (!d.isValid()) return '账单';
  return `${d.month() + 1}月账单`;
};

// 跳转到详情
const goToDetail = (item) => {
  router.push(`/card/bill/detail?id=${item.id}`);
};

// 跳转到流水明细
const goToLedger = (item) => {
  router.push(`/card/bill/ledger?id=${item.id}`);
};

// 跳转到还款
const goToRepay = (item) => {
  // 携带账单ID、卡ID等信息
  const params = new URLSearchParams({
    billId: item.id,
    cardId: item.card_id,
    last4: item.card_last4 || '',
    alias: item.card_alias || '',
    billDay: formatDay(item.bill_start_date),
    repayDay: formatDay(item.bill_end_date)
  });
  router.push(`/card/repay/add?${params.toString()}`);
};

// 跳转到添加
const goToAdd = () => {
  router.push("/card/bill/add");
};

// 刷新账单
const refreshBill = async (item, event) => {
  event.stopPropagation();
  try {
    const res = await rebuildBill(item.card_id);
    const msg = res.message || "账单已刷新";
    showToast(msg);
    loadBillList();
  } catch (error) {
    showToast(error.message || "刷新失败");
  }
};

// 返回
const onClickLeft = () => {
  router.back();
};

onMounted(() => {
  loadCardList();
  loadBankList();
  loadBillList();
});
</script>

<style scoped>
.page-bill-list {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.page-header {
  background: #fff;
}

.filter-section {
  padding: 12px 0;
}

.bill-list {
  padding: 0 16px;
}

.bill-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.bill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.bill-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.bill-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bank-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
}

.bill-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.bill-card-last4 {
  font-size: 12px;
  color: #999;
}

.detail-btn {
  margin-left: 6px;
  flex-shrink: 0;
}

.bill-fee-info {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.fee-divider {
  color: #ddd;
}

.bill-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 8px;
}

.bill-repay {
  flex: 1;
  text-align: center;
}

.repay-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.repay-value {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.repay-value.overdue {
  color: #ee0a24;
}

.repay-value.danger {
  color: #ee0a24;
}

.bill-limit-small {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding-right: 12px;
  border-right: 1px solid #f0f0f0;
}

.limit-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: #999;
}

.limit-row span:last-child {
  color: #666;
}

.bill-amount-right {
  flex: 1;
  display: flex;
  justify-content: space-around;
  align-items: center;
}

.amount-col {
  text-align: center;
}

.tag-normal {
  background: #1989fa !important;
  color: #fff !important;
}

.bill-status-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.status-extra {
  font-size: 10px;
  color: #999;
  white-space: nowrap;
}

.bill-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.bill-day-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bill-actions {
  display: flex;
  gap: 8px;
}

.bill-limit {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
}

.add-btn-wrap {
  position: fixed;
  bottom: 30px;
  left: 20px;
  right: 20px;
  z-index: 100;
}

.glass-add-btn {
  width: 100%;
  height: 56px;
  background: #1989fa;
  color: #fff;
  border: none;
  border-radius: 28px;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 24px rgba(25, 137, 250, 0.3);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
