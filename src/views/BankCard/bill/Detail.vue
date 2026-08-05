<template>
  <div class="page-bill-detail">


    <van-skeleton title :row="10" v-if="loading" />

    <div class="bill-content" v-if="!loading && billData.id">
      <!-- 卡片信息 -->
      <div class="card-info-section">
        <div class="section-title">卡片信息</div>
        <van-cell-group inset>
          <app-cell title="卡片别名" :value="billData.card_alias || '-'" />
          <app-cell title="卡号后4位" :value="`**** ${billData.card_last4}`" />
          <app-cell title="信用额度" :value="`¥${formatMoney(billData.credit_limit)}`" />
          <app-cell title="可用额度" :value="`¥${formatMoney(billData.avail_limit)}`" />
          <app-cell title="已用额度" :value="`¥${formatMoney(billData.used_limit)}`" />
        </van-cell-group>
      </div>

      <!-- 账单周期 -->
      <div class="period-section">
        <div class="section-title">账单周期</div>
        <van-cell-group inset>
          <app-cell title="账单开始" :value="formatDate(billData.bill_start_date)" />
          <app-cell title="账单结束" :value="formatDate(billData.bill_end_date)" />
        </van-cell-group>
      </div>

      <!-- 账单金额 -->
      <div class="amount-section">
        <div class="section-title">账单金额</div>
        <van-cell-group inset>
          <app-cell title="本期账单" value-class="amount-highlight" :value="`¥${formatMoney(billData.bill_amount)}`" />
          <app-cell title="最低还款" :value="`¥${formatMoney(billData.min_repay)}`" />
          <app-cell title="已还金额" :value="`¥${formatMoney(billData.repaid)}`" />
          <app-cell title="待还金额" value-class="amount-warning" :value="`¥${formatMoney(billData.need_repay)}`" />
        </van-cell-group>
      </div>

      <!-- 临时额度和积分 -->
      <div class="extra-section" v-if="billData.temp_limit || billData.points">
        <div class="section-title">附加信息</div>
        <van-cell-group inset>
          <app-cell title="临时额度" v-if="billData.temp_limit" :value="`¥${formatMoney(billData.temp_limit)}`" />
          <app-cell title="积分" v-if="billData.points" :value="billData.points" />
          <app-cell title="积分到期" v-if="billData.points_expire" :value="formatDate(billData.points_expire)" />
        </van-cell-group>
      </div>

      <!-- 还款状态 -->
      <div class="status-section">
        <div class="section-title">还款状态</div>
        <van-cell-group inset>
          <app-cell title="还款状态" :value="billData.repay_status || '未还'" />
          <app-cell title="是否逾期">
            <template #value>
              <app-tag :type="isOverdue ? 'danger' : 'success'">
                {{ isOverdue ? `逾期 ${displayOverdueDays} 天` : '正常' }}
              </app-tag>
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <!-- 操作按钮 -->
      <div class="action-section">
        <app-button plain block round type="primary" @click="goToLedger">
          查看流水明细
        </app-button>
        <app-button type="primary" block round @click="goToRepay" v-if="!isPaidOff">
          添加还款记录
        </app-button>
        <app-button plain block round type="danger" @click="handleDelete" v-if="!isPaidOff">
          删除账单
        </app-button>
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
import { ref, computed, onMounted } from "vue";
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from "vant";
import { useRouter, useRoute } from "vue-router";
import { getBillDetail, deleteBill } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const billData = ref({});

// 实时计算逾期状态：已还清或待还金额为0时，不应显示逾期
const isOverdue = computed(() => {
  const bill = billData.value;
  // 已还清或待还为0，强制不逾期
  if (!bill.need_repay || Number(bill.need_repay) <= 0) return false;
  if (bill.repay_status === "已还清") return false;
  return !!bill.is_overdue;
});

const displayOverdueDays = computed(() => {
  if (!isOverdue.value) return 0;
  return billData.value.overdue_days || 0;
});

// 是否已还清：还清后隐藏 添加还款记录 和 删除账单 按钮
const isPaidOff = computed(() => {
  const bill = billData.value;
  if (bill.repay_status === "已还清") return true;
  if (bill.need_repay !== null && bill.need_repay !== undefined && Number(bill.need_repay) <= 0) return true;
  return false;
});

// 加载账单详情
const loadBillDetail = async () => {
  const id = route.query.id;
  if (!id) {
    showToast("缺少账单ID");
    router.back();
    return;
  }

  loading.value = true;
  try {
    const res = await getBillDetail(id);
    billData.value = res.data || res || {};
  } catch (error) {
    showToast(error.message || "加载失败");
  } finally {
    loading.value = false;
  }
};

// 格式化金额
import { formatMoney } from "@/utils/money";

// 格式化日期
const formatDate = (date) => {
  if (!date) return "-";
  return date.split(" ")[0];
};

// 返回
const onClickLeft = () => {
  router.back();
};

// 查看流水明细
const goToLedger = () => {
  router.push(`/card/bill/ledger?id=${billData.value.id}`);
};

// 添加还款记录
const goToRepay = () => {
  router.push(`/card/repay/add?billId=${billData.value.id}`);
};

// 删除账单
const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: "删除确认",
      message: "确定要删除这条账单吗？",
      confirmButtonColor: "var(--van-danger-color, #ee0a24)",
    });

    showLoadingToast({ message: "删除中...", forbidClick: true });
    await deleteBill(billData.value.id);

    closeToast();
    showToast({ message: "删除成功", onClose: () => router.back() });
  } catch (error) {
    if (error !== "cancel") {
      showToast(error.message || "删除失败");
    }
  }
};

onMounted(() => {
  loadBillDetail();
});
</script>

<style scoped>
.page-bill-detail {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 40px;
}

.page-header {
  background: var(--theme-bg-secondary);
}

.bill-content {
  padding: 12px 0;
}

.section-title {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  padding: 16px 16px 8px;
}

.action-section {
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.amount-highlight {
  font-weight: 600;
  color: var(--theme-text-primary);
}

.amount-warning {
  font-weight: 600;
  color: var(--van-danger-color, #ee0a24);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
