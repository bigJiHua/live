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
      </van-cell-group>
    </div>

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
            <span class="repay-card-name">{{ item.card_alias || '未知卡片' }}</span>
            <span class="repay-card-no">**** {{ item.card_last4 }}</span>
          </div>
          <div class="repay-amount">
            -¥{{ formatMoney(item.repay_amount) }}
          </div>
        </div>

        <div class="repay-body">
          <div class="repay-method">
            <van-icon name="alipay" v-if="item.repay_method === '支付宝'" />
            <van-icon name="wechat-pay" v-else-if="item.repay_method === '微信'" />
            <van-icon name="credit-card" v-else />
            {{ item.repay_method || '转账' }}
          </div>
          <div class="repay-time">{{ formatDate(item.repay_time) }}</div>
        </div>

        <div class="repay-footer" v-if="item.remark">
          {{ item.remark }}
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
import { getRepayList, getCardList } from "@/utils/api/card";

const router = useRouter();

const repayList = ref([]);
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

// 加载还款记录
const loadRepayList = async () => {
  loading.value = true;
  try {
    const params = {};
    if (selectedCardId.value) {
      params.cardId = selectedCardId.value;
    }
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

// 格式化金额
const formatMoney = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

// 格式化日期
const formatDate = (date) => {
  if (!date) return "--";
  return date.replace("T", " ").split(".")[0];
};

// 跳转到详情
const goToDetail = (item) => {
  router.push(`/card/repay/detail?id=${item.id}`);
};

// 跳转到添加
const goToAdd = () => {
  router.push("/card/repay/add");
};

// 返回
const onClickLeft = () => {
  router.back();
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

.page-header {
  background: #fff;
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
  align-items: flex-start;
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

.repay-card-no {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.repay-amount {
  font-size: 20px;
  font-weight: 600;
  color: #07c160;
}

.repay-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}

.repay-method {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #666;
}

.repay-time {
  font-size: 12px;
  color: #999;
}

.repay-footer {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #f5f5f5;
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
