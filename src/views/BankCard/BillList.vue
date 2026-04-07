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
      </van-cell-group>
    </div>

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
            <span class="bill-card-name">{{ item.card_alias || '未知卡片' }}</span>
            <span class="bill-card-no">**** {{ item.card_last4 }}</span>
          </div>
          <van-tag :type="getStatusType(item)">{{ getStatusText(item) }}</van-tag>
        </div>

        <div class="bill-body">
          <div class="bill-amount">
            <div class="amount-label">账单金额</div>
            <div class="amount-value">¥{{ formatMoney(item.bill_amount) }}</div>
          </div>
          <div class="bill-repay">
            <div class="repay-label">待还金额</div>
            <div class="repay-value" :class="{ overdue: item.need_repay > 0 }">
              ¥{{ formatMoney(item.need_repay) }}
            </div>
          </div>
        </div>

        <div class="bill-footer">
          <div class="bill-period">
            {{ formatDate(item.bill_start_date) }} ~ {{ formatDate(item.bill_end_date) }}
          </div>
          <div class="bill-limit">
            <span>额度: ¥{{ formatMoney(item.credit_limit) }}</span>
            <span>可用: ¥{{ formatMoney(item.avail_limit) }}</span>
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
import { getBillList, getCardList } from "@/utils/api/card";

const router = useRouter();

const billList = ref([]);
const cardList = ref([]);
const loading = ref(false);
const selectedCardId = ref(null);
const selectedCardName = ref("");
const showCardPicker = ref(false);

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
    const res = await getBillList(params);
    billList.value = res.data || res || [];
  } catch (error) {
    showToast(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList();
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
  loadBillList();
};

// 获取状态类型
const getStatusType = (item) => {
  if (item.is_overdue || item.overdue_days > 0) return "danger";
  if (item.repay_status === "已还清") return "success";
  if (item.need_repay > 0) return "warning";
  return "default";
};

// 获取状态文本
const getStatusText = (item) => {
  if (item.is_overdue || item.overdue_days > 0) return "已逾期";
  if (item.repay_status === "已还清") return "已还清";
  if (item.need_repay > 0) return "待还款";
  return "正常";
};

// 格式化金额
const formatMoney = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

// 格式化日期
const formatDate = (date) => {
  if (!date) return "--";
  return date.split(" ")[0];
};

// 跳转到详情
const goToDetail = (item) => {
  router.push(`/card/bill/detail?id=${item.id}`);
};

// 跳转到添加
const goToAdd = () => {
  router.push("/card/bill/add");
};

// 返回
const onClickLeft = () => {
  router.back();
};

onMounted(() => {
  loadCardList();
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
}

.bill-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.bill-card-no {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.bill-body {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.amount-label,
.repay-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.amount-value {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.repay-value {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  text-align: right;
}

.repay-value.overdue {
  color: #ee0a24;
}

.bill-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.bill-period {
  font-size: 12px;
  color: #999;
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
