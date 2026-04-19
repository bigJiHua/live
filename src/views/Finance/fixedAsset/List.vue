<template>
  <div class="page-fixed-asset">
    <!-- 顶部统计 -->
    <div class="stats-card">
      <div class="stat-item">
        <span class="stat-label">资产总数</span>
        <span class="stat-value">{{ assetList.length }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">总购买价</span>
        <span class="stat-value">¥{{ formatAmount(totalBuyPrice) }}</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">当前总价值</span>
        <span class="stat-value primary"
          >¥{{ formatAmount(totalCurrentValue) }}</span
        >
      </div>
    </div>

    <!-- Tab 筛选 -->
    <div class="filter-tabs">
      <van-tabs v-model:active="activeTab">
        <van-tab title="全部" name="all" />
        <van-tab title="使用中" name="using" />
        <van-tab title="折旧完毕" name="finished" />
        <van-tab title="已归档" name="archived" />
      </van-tabs>
    </div>

    <!-- 资产列表 -->
    <div class="asset-list" v-if="displayList.length > 0">
      <div
        v-for="item in displayList"
        :key="item.id"
        class="asset-card"
        @click="showDetail(item)"
      >
        <div class="card-image" v-if="item.img_url">
          <img :src="getImageUrl(item.img_url)" :alt="item.info" />
        </div>
        <div class="card-image placeholder" v-else>
          <van-icon name="photo-o" />
        </div>
        <div class="card-content">
          <div class="card-header">
            <span class="card-title">{{ item.info }}</span>
            <van-tag :type="getStatusType(item.status)" size="small">
              {{ getStatusText(item.status) }}
            </van-tag>
          </div>
          <div class="card-tag" v-if="item.tag">{{ item.tag }}</div>
          <div class="card-info">
            <div class="info-row">
              <span class="label">购买价</span>
              <span class="value">¥{{ formatAmount(item.buy_price) }}</span>
            </div>
            <div class="info-row">
              <span class="label">当前价值</span>
              <span class="value primary"
                >¥{{ formatAmount(item.now_val || item.buy_price) }}</span
              >
            </div>
          </div>
          <div class="card-footer">
            <div
              style="display: flex; justify-content: space-between; width: 100%"
            >
              <span class="deprec-info" v-if="item.month_deprec > 0">
                月折旧 ¥{{ formatAmount(item.month_deprec) }}
              </span>
              <span class="deprec-info" v-if="item.total_deprec > 0">
                累计折旧 ¥{{ formatAmount(item.total_deprec) }}
              </span>
            </div>
            <div
              style="display: flex; justify-content: space-between; width: 100%"
            >
              <span class="usage-info">已使用：{{ formatUsedTime(item) }}</span>
              <span class="cost-info"
                >每日成本 ¥{{ formatDailyCost(item) }}</span
              >
              <van-tag
                v-if="item.deprec_finished === 1"
                type="success"
                size="small"
                plain
              >
                折旧完毕
              </van-tag>
            </div>
          </div>
        </div>
        <van-icon name="arrow" class="card-arrow" />
      </div>
    </div>

    <van-empty
      v-if="!loading && displayList.length === 0"
      description="暂无固定资产"
    >
      <template #image>
        <van-icon name="albums-o" size="60" />
      </template>
    </van-empty>

    <van-loading v-if="loading" class="loading-center" vertical
      >加载中...</van-loading
    >

    <!-- 底部按钮 -->
    <div class="bottom-actions">
      <van-button type="primary" round block @click="openAddForm">
        <van-icon name="plus" /> 登记固定资产
      </van-button>
      <van-button type="default" round block @click="goRecycleBin">
        <van-icon name="delete-o" /> 回收站
      </van-button>
    </div>

    <!-- 表单弹窗 -->
    <AssetForm
      v-model="showAddForm"
      :asset="editingAsset"
      @success="loadList"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import dayjs from "dayjs";
import { getFixedAssetList } from "@/utils/api/fixedAsset";
import AssetForm from "./components/AssetForm.vue";

const router = useRouter();

// 状态
const loading = ref(false);
const assetList = ref([]);
const activeTab = ref("all");
const showAddForm = ref(false);
const editingAsset = ref(null);

// 筛选后的列表
const displayList = computed(() => {
  if (activeTab.value === "all") {
    return assetList.value;
  } else if (activeTab.value === "using") {
    return assetList.value.filter((item) => item.status === "using");
  } else if (activeTab.value === "finished") {
    return assetList.value.filter((item) => item.deprec_finished === 1);
  } else if (activeTab.value === "archived") {
    return assetList.value.filter((item) =>
      ["scrapped", "sold", "lost"].includes(item.status)
    );
  }
  return assetList.value;
});

// 统计
const totalBuyPrice = computed(() => {
  return assetList.value.reduce(
    (sum, item) => sum + parseFloat(item.buy_price) || 0,
    0
  );
});

const totalCurrentValue = computed(() => {
  return assetList.value.reduce((sum, item) => {
    return sum + parseFloat(item.now_val || item.buy_price) || 0;
  }, 0);
});

const formatAmount = (amount) => {
  if (!amount && amount !== 0) return "0.00";
  return (parseFloat(amount) || 0).toFixed(2);
};

const getStatusText = (status) => {
  const map = {
    using: "使用中",
    scrapped: "已报废",
    sold: "已出售",
    lost: "已遗失",
  };
  return map[status] || status;
};

const getStatusType = (status) => {
  const map = {
    using: "success",
    scrapped: "danger",
    sold: "primary",
    lost: "warning",
  };
  return map[status] || "default";
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

// 每日成本 = 购买价格 / (使用年限 * 365)
const formatDailyCost = (item) => {
  const buyPrice = parseFloat(item.buy_price) || 0;
  const useYears = parseFloat(item.use_years) || 1;
  const dailyCost = buyPrice / (useYears * 365);
  return dailyCost.toFixed(2);
};

const getImageUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const BASE_URL = import.meta.env.VITE_FILE_BASE_URL || "";
  return BASE_URL + path;
};

// 加载列表
const loadList = async () => {
  loading.value = true;
  try {
    const res = await getFixedAssetList();
    assetList.value = res.data || [];
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 显示详情
const showDetail = (item) => {
  router.push(`/finance/fixed-asset/detail/${item.id}`);
};

// 打开新增表单
const openAddForm = () => {
  editingAsset.value = null;
  showAddForm.value = true;
};

// 跳转回收站
const goRecycleBin = () => {
  router.push("/finance/fixed-asset/recycle");
};

onMounted(() => {
  loadList();
});
</script>

<style scoped>
.page-fixed-asset {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.stats-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #1989fa, #1976d2);
  margin: 12px 16px;
  border-radius: 12px;
  padding: 16px;
  color: #fff;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  opacity: 0.85;
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
}

.stat-value.primary {
  color: #ffd700;
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: rgba(255, 255, 255, 0.3);
}

.filter-tabs {
  margin: 0 16px;
  background: #fff;
  border-radius: 8px;
}

.asset-list {
  padding: 0 16px;
  margin-top: 12px;
}

.asset-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
  cursor: pointer;
}

.card-image {
  width: 70px;
  height: 70px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-image.placeholder {
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #969799;
  font-size: 24px;
}

.card-content {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-tag {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}

.card-info {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.info-row .label {
  font-size: 12px;
  color: #969799;
}

.info-row .value {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
}

.info-row .value.primary {
  color: #ee0a24;
}

.card-footer {
  margin-top: 6px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.deprec-info {
  font-size: 11px;
  color: #969799;
}

.usage-info {
  font-size: 11px;
  color: #1989fa;
}

.cost-info {
  font-size: 11px;
  color: #ee0a24;
}

.card-arrow {
  color: #969799;
  font-size: 14px;
  flex-shrink: 0;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.bottom-actions {
  position: fixed;
  bottom: 20px;
  left: 16px;
  right: 16px;
  display: flex;
  gap: 12px;
}
</style>
