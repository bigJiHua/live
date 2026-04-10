<template>
  <div class="page-repay-detail">
    <van-skeleton title :row="8" v-if="loading" />

    <div class="repay-content" v-if="!loading && repayData.id">
      <!-- 还款信息 -->
      <div class="info-section">
        <div class="amount-display">
          <div class="amount-label">还款金额</div>
          <div class="amount-value">
            ¥{{ formatMoney(repayData.repay_amount) }}
          </div>
        </div>
      </div>

      <!-- 关联信息 -->
      <div class="info-section">
        <div class="section-title">关联信息</div>
        <van-cell-group inset>
          <van-cell title="卡号" :value="`**** ${repayData.card_last4}`" />
          <van-cell
            title="关联账单"
            :value="repayData.bill_id ? `是（${repayData.bill_id}）` : '否'"
          />
          <van-cell title="账单流水号" :value="repayData.id" />
          <van-cell
            v-if="repayData.bill_amount"
            title="账单金额"
            :value="`¥${formatMoney(repayData.bill_amount)}`"
          />
          <van-cell
            v-if="repayData.bill_need_repay"
            title="账单待还"
            :value="`¥${formatMoney(repayData.bill_need_repay)}`"
          />
          <van-cell
            title="是否溢缴款"
            :value="repayData.bill_need_repay < repayData.repay_amount  ? `是（+¥${formatMoney(repayData.repay_amount)}）` : '否'"
          />
        </van-cell-group>
      </div>

      <!-- 还款详情 -->
      <div class="info-section">
        <div class="section-title">还款详情</div>
        <van-cell-group inset>
          <van-cell
            title="所属账单周期"
            :value="repayData.bill_month || '-'"
          />
          <van-cell
            title="还款方式"
            :value="repayData.repay_method || '转账'"
          />
          <van-cell
            title="还款时间"
            :value="formatDateTime(repayData.create_time)"
          />
          <van-cell
            title="更新时间"
            :value="formatDateTime(repayData.update_time)"
          />
        </van-cell-group>
      </div>

      <!-- 备注 -->
      <div class="info-section" v-if="repayData.remark">
        <div class="section-title">备注</div>
        <van-cell-group inset>
          <van-cell>
            <div class="remark-content">{{ repayData.remark }}</div>
          </van-cell>
        </van-cell-group>
      </div>
    </div>

    <van-overlay :show="loading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { showToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { getRepayDetail } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const repayData = ref({});

// 加载还款详情
const loadRepayDetail = async () => {
  const id = route.query.id;
  if (!id) {
    showToast("缺少还款记录ID");
    router.back();
    return;
  }

  loading.value = true;
  try {
    const res = await getRepayDetail(id);
    repayData.value = res.data || res || {};
  } catch (error) {
    showToast(error.message || "加载失败");
  } finally {
    loading.value = false;
  }
};

// 格式化金额
const formatMoney = (amount) => {
  if (amount === null || amount === undefined) return "0.00";
  return Number(amount)
    .toFixed(2)
    .replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};
import dayjs from "dayjs";
// 格式化日期时间
const formatDateTime = (date) => {
  if (!date) return "-";
  const newDate = Number(date)
  return dayjs(newDate).format("YYYY-MM-DD HH:mm:ss");
};

// 返回
const onClickLeft = () => {
  router.back();
};

onMounted(() => {
  loadRepayDetail();
});
</script>

<style scoped>
.page-repay-detail {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.page-header {
  background: #fff;
}

.repay-content {
  padding: 12px 0;
}

.info-section {
  margin-bottom: 12px;
}

.amount-display {
  background: linear-gradient(135deg, #07c160 0%, #1a1a1a 150%);
  color: #fff;
  text-align: center;
  padding: 32px 16px;
}

.amount-label {
  font-size: 14px;
  opacity: 0.8;
  margin-bottom: 8px;
}

.amount-value {
  font-size: 36px;
  font-weight: 600;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 16px 16px 8px;
}

.remark-content {
  color: #666;
  line-height: 1.5;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
