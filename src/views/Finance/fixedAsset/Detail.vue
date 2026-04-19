<template>
  <div class="page-asset-detail">
    <div class="detail-content" v-if="asset">
      <!-- 图片 -->
      <div class="detail-image" v-if="asset.img_url">
        <img :src="getImageUrl(asset.img_url)" :alt="asset.info" />
      </div>

      <!-- 基本信息 -->
      <van-cell-group inset title="基本信息">
        <van-cell title="资产名称" :value="asset.info" />
        <van-cell title="品类" :value="asset.tag || '-'" />
        <van-cell
          title="购买价格"
          :value="'¥' + formatAmount(asset.buy_price)"
        />
        <van-cell title="购买日期" :value="asset.buy_date" />
      </van-cell-group>

      <!-- 折旧信息 -->
      <van-cell-group inset title="折旧信息">
        <van-cell title="预计使用年限" :value="asset.use_years + '年'" />
        <van-cell title="残值率" :value="asset.residual_rate + '%'" />
        <van-cell
          title="残值"
          :value="'¥' + formatAmount(asset.residual_val)"
        />
        <van-cell
          title="二手市场价"
          :value="
            asset.secondhand_price
              ? '¥' + formatAmount(asset.secondhand_price)
              : '-'
          "
        />
        <van-divider />
        <van-cell
          title="月折旧"
          :value="'¥' + formatAmount(asset.month_deprec)"
        />
        <van-cell
          title="累计折旧"
          :value="'¥' + formatAmount(asset.total_deprec)"
        />
        <van-cell
          title="当前账面价值"
          value-class="primary-value"
          :value="'¥' + formatAmount(asset.now_val)"
        />
        <van-cell title="已使用时长" :value="formatUsedTime(asset)" />
        <van-cell title="上次折旧日期" :value="asset.last_deprec_date || '-'" />
        <van-cell title="折旧状态">
          <template #value>
            <van-tag
              :type="asset.deprec_finished === 1 ? 'success' : 'primary'"
              size="small"
            >
              {{ asset.deprec_finished === 1 ? "已折旧完毕" : "未折旧完" }}
            </van-tag>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 状态信息 -->
      <van-cell-group inset title="状态信息">
        <van-cell title="当前状态">
          <template #value>
            <van-tag :type="getStatusType(asset.status)">{{
              getStatusText(asset.status)
            }}</van-tag>
          </template>
        </van-cell>
        <van-cell
          title="结束日期"
          v-if="asset.scrap_date"
          :value="asset.scrap_date"
        />
      </van-cell-group>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <van-button type="primary" plain block @click="handleEdit"
          >编辑</van-button
        >
        <van-button type="warning" plain block @click="handleChangeStatus"
          >变更状态</van-button
        >
        <van-button type="danger" plain block @click="handleDelete"
          >删除</van-button
        >
      </div>
    </div>

    <van-loading v-if="loading" class="loading-center" vertical
      >加载中...</van-loading
    >

    <!-- 状态变更弹窗 -->
    <van-popup v-model:show="showStatusPicker" position="bottom" round>
      <van-picker
        title="选择状态"
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showConfirmDialog, showToast, showSuccessToast } from "vant";
import dayjs from "dayjs";
import {
  getFixedAsset,
  changeAssetStatus,
  deleteFixedAsset,
} from "@/utils/api/fixedAsset";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const asset = ref(null);
const showStatusPicker = ref(false);

const statusColumns = [
  { text: "使用中", value: "using" },
  { text: "已报废", value: "scrapped" },
  { text: "已出售", value: "sold" },
  { text: "已遗失", value: "lost" },
];

const statusMap = {
  using: "使用中",
  scrapped: "已报废",
  sold: "已出售",
  lost: "已遗失",
};

const getStatusText = (status) => statusMap[status] || status;

const getStatusType = (status) => {
  const map = {
    using: "success",
    scrapped: "danger",
    sold: "primary",
    lost: "warning",
  };
  return map[status] || "default";
};

const formatAmount = (amount) => {
  if (!amount && amount !== 0) return "0.00";
  return (parseFloat(amount) || 0).toFixed(2);
};

const formatUsedTime = (item) => {
  if (!item.buy_date) return "-";
  const endDate =
    ["scrapped", "sold", "lost"].includes(item.status) && item.scrap_date
      ? item.scrap_date
      : dayjs().format("YYYY-MM-DD");
  const usage = calculateUsage(item.buy_date, endDate);
  return formatUsageTime(usage);
};

const calculateUsage = (startDate, endDate) => {
  if (!startDate || !endDate) return null;
  const start = new Date(startDate);
  const end = new Date(endDate);
  if (end < start) return null;

  let years = end.getFullYear() - start.getFullYear();
  let months = end.getMonth() - start.getMonth();
  let days = end.getDate() - start.getDate();

  if (days < 0) {
    months--;
    const lastMonth = new Date(end.getFullYear(), end.getMonth(), 0);
    days += lastMonth.getDate();
  }
  if (months < 0) {
    years--;
    months += 12;
  }
  return { years, months, days };
};

const formatUsageTime = (usage) => {
  if (!usage) return "-";
  const parts = [];
  if (usage.years > 0) parts.push(usage.years + "年");
  if (usage.months > 0) parts.push(usage.months + "个月");
  if (usage.days > 0) parts.push(usage.days + "天");
  return parts.join("") || "0天";
};

const getImageUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const BASE_URL = import.meta.env.VITE_FILE_BASE_URL || "";
  return BASE_URL + path;
};

const loadDetail = async () => {
  loading.value = true;
  try {
    const res = await getFixedAsset(route.params.id);
    asset.value = res.data;
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

const handleEdit = () => {
  router.push(`/finance/fixed-asset/edit/${route.params.id}`);
};

const handleChangeStatus = () => {
  showStatusPicker.value = true;
};

const onStatusConfirm = async ({ selectedOptions }) => {
  const newStatus = selectedOptions[0].value;
  showStatusPicker.value = false;

  try {
    await changeAssetStatus(route.params.id, newStatus);
    showSuccessToast("状态变更成功");
    loadDetail();
  } catch (e) {
    showToast(e.message || "变更失败");
  }
};

const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: "确认删除",
      message: "确定要删除这个固定资产吗？",
      confirmButtonColor: "#ee0a24",
    });
    await deleteFixedAsset(route.params.id);
    showSuccessToast("删除成功");
    router.replace("/finance/fixed-asset");
  } catch (e) {
    if (e !== "cancel") {
      showToast(e.message || "删除失败");
    }
  }
};

onMounted(() => {
  loadDetail();
});
</script>

<style scoped>
.page-asset-detail {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.detail-image {
  width: 100%;
  height: 250px;
  background: #fff;
}

.detail-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.primary-value {
  color: #ee0a24 !important;
  font-weight: 600;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  margin-top: 16px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}
</style>
