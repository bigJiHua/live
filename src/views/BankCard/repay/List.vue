<template>
  <div class="page-repay-list">

    <!-- 筛选 -->
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
          label="账单月份"
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
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <!-- 还款记录列表 -->
    <div class="repay-list" v-if="repayList.length > 0">
      <div
        v-for="item in repayList"
        :key="item.id"
        class="repay-card"
        @click="goToDetail(item)"
      >
        <div class="repay-header">
          <div class="repay-info">
            <span class="repay-card-name">{{ getCardDisplayName(item) }}</span>
            <span class="repay-date">本次记录还款日期 {{ formatDate(item.repay_time) }}</span>
          </div>
          <van-tag type="success">已还款</van-tag>
        </div>

        <div class="repay-body">
          <div class="bill-info-col">
            <div class="info-row">
              <span class="info-label">账单金额</span>
              <span class="info-value">¥{{ formatMoney(item.bill_amount) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">已还金额</span>
              <span class="info-value">¥{{ formatMoney(item.repay_amount) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">待还金额</span>
              <span class="info-value danger">¥{{ formatMoney(item.bill_need_repay) }}</span>
            </div>
          </div>
          <div class="repay-amount-col">
            <div class="repay-label">本次还款</div>
            <div class="repay-value">
              ¥{{ formatMoney(item.repay_amount) }}
            </div>
          </div>
        </div>

        <div class="repay-footer">
          <div class="repay-method">
            <van-icon name="card" size="14" />
            <span>{{ formatRepayMethod(item.repay_method) }}</span>
          </div>
          <div class="repay-actions">
            <van-button
              size="small"
              round
              plain
              @click.stop="goToRepay(item)"
            >
              再次还款
            </van-button>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && repayList.length === 0" description="暂无还款记录" />

    <!-- 添加还款按钮 -->
    <div class="add-btn-wrap">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加还款记录</span>
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
import { getRepayList, getCardList } from "@/utils/api/card";

const router = useRouter();

const repayList = ref([]);
const cardList = ref([]);
const loading = ref(false);
const selectedCardId = ref(null);
const selectedCardName = ref("");
const showCardPicker = ref(false);

// 月份选择（Vant 4 写法）
const now = dayjs();
const currentYear = ref(now.year());
const currentMonth = ref(now.month() + 1);
const showMonthPicker = ref(false);
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
const onMonthConfirm = ({ selectedOptions }) => {
  currentYear.value = parseInt(selectedOptions[0].text);
  currentMonth.value = parseInt(selectedOptions[1].text);
  selectedValues.value = [selectedOptions[0].text, selectedOptions[1].text];
  showMonthPicker.value = false;
  loadRepayList();
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

// 加载还款记录
const loadRepayList = async () => {
  loading.value = true;
  try {
    const params = {};
    if (selectedCardId.value) {
      params.cardId = selectedCardId.value;
    }
    // 添加账单月份筛选
    const billMonth = `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`;
    params.billMonth = billMonth;
    const res = await getRepayList(params);
    repayList.value = res.data || res || [];
  } catch (error) {
    showToast(error.message || "加载失败");
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

// 卡片选择确认
const onCardConfirm = ({ selectedOptions }) => {
  selectedCardId.value = selectedOptions[0].value;
  selectedCardName.value = selectedOptions[0].text;
  showCardPicker.value = false;
  loadRepayList();
};

// 获取卡片显示名称
const getCardDisplayName = (item) => {
  if (item.card_alias) return item.card_alias;
  if (item.card_last4) return `信用卡 ****${item.card_last4}`;
  return '信用卡';
};

// 格式化金额
const formatMoney = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

// 格式化日期 - 显示完整日期
const formatDate = (date) => {
  if (!date) return "--";
  const d = dayjs(date);
  if (!d.isValid()) return "--";
  return `${d.month() + 1}月${d.date()}日`;
};

// 格式化还款方式
const formatRepayMethod = (method) => {
  const map = {
    cash: '现金还款',
    balance: '余额还款',
    bank_card: '借记卡还款',
    card: '卡还款',
  };
  return map[method] || method || '还款';
};

// 跳转到详情
const goToDetail = (item) => {
  router.push(`/card/repay/detail?id=${item.id}`);
};

// 跳转到再次还款
const goToRepay = (item) => {
  const params = new URLSearchParams({
    cardId: item.card_id,
    billId: item.bill_id || '',
    last4: item.card_last4 || '',
    alias: item.card_alias || '',
  });
  router.push(`/card/repay/add?${params.toString()}`);
};

// 跳转到添加
const goToAdd = () => {
  router.push("/card/repay/add");
};

onMounted(() => {
  loadCardList();
  loadRepayList();
});
</script>

<style scoped>
.page-repay-list {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.filter-section {
  padding: 12px 0;
}

.repay-list {
  padding: 0 16px;
}

.repay-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.repay-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.repay-info {
  display: flex;
  flex-direction: column;
}

.repay-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.repay-date {
  font-size: 13px;
  color: #07c160;
  font-weight: 500;
  margin-top: 2px;
}

.repay-body {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  margin-bottom: 12px;
}

.bill-info-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 13px;
  color: #999;
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.info-value.danger {
  color: #ee0a24;
}

.repay-amount-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 8px 16px;
  background: linear-gradient(135deg, #07c160 0%, #10b981 100%);
  border-radius: 8px;
  min-width: 100px;
}

.repay-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-bottom: 4px;
}

.repay-value {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.repay-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.repay-method {
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 4px;
}

.repay-actions {
  display: flex;
  gap: 8px;
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
  background: #07c160;
  color: #fff;
  border: none;
  border-radius: 28px;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 24px rgba(7, 193, 96, 0.3);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
