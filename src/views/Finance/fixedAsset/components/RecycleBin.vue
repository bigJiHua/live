<template>
  <div class="recycle-bin">
    <div class="stats-bar">
      <span class="count">{{ list.length }} 个已删除资产</span>
    </div>
    <!-- 资产列表 -->
    <div class="asset-list" v-if="list.length > 0">
      <div v-for="item in list" :key="item.id" class="asset-card">
        <div class="card-image" v-if="item.img_url">
          <img :src="getImageUrl(item.img_url)" :alt="item.info" />
        </div>
        <div class="card-image placeholder" v-else>
          <van-icon name="photo-o" />
        </div>
        <div class="card-content">
          <div class="card-title">{{ item.info }}</div>
          <div class="card-tag" v-if="item.tag">{{ item.tag }}</div>
          <div class="card-info">
            <span>购买价 ¥{{ formatAmount(item.buy_price) }}</span>
          </div>
        </div>
        <div class="card-actions">
          <van-button
            size="small"
            type="primary"
            plain
            @click="handleRestore(item)"
          >
            恢复
          </van-button>
          <van-button
            size="small"
            type="danger"
            plain
            @click="handlePermanentDelete(item)"
          >
            永久删除
          </van-button>
        </div>
      </div>
    </div>
    <div class="empty-container" v-if="!loading && list.length === 0">
      <van-empty description="回收站为空" class="empty-content" />
    </div>

    <van-loading v-if="loading" class="loading-center" vertical
      >加载中...</van-loading
    >
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { showConfirmDialog, showToast, showSuccessToast } from "vant";
import {
  getRecycleBinList,
  restoreFixedAsset,
  permanentlyDeleteFixedAsset,
} from "@/utils/api/fixedAsset";

const emit = defineEmits(["restore", "delete"]);

const loading = ref(false);
const list = ref([]);

const loadList = async () => {
  loading.value = true;
  try {
    const res = await getRecycleBinList();
    list.value = res.data || [];
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

const formatAmount = (amount) => {
  if (!amount && amount !== 0) return "0.00";
  return (parseFloat(amount) || 0).toFixed(2);
};

const getImageUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const BASE_URL = import.meta.env.VITE_FILE_BASE_URL || "";
  return BASE_URL + path;
};

const handleRestore = async (item) => {
  try {
    await showConfirmDialog({
      title: "确认恢复",
      message: "确定要恢复这个资产吗？",
    });
    await restoreFixedAsset(item.id);
    showSuccessToast("恢复成功");
    emit("restore");
    loadList();
  } catch (e) {
    if (e !== "cancel") {
      showToast(e.message || "恢复失败");
    }
  }
};

const handlePermanentDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: "警告",
      titleClass: "danger-title",
      message: "永久删除后数据无法恢复，确定要删除吗？",
      confirmButtonColor: "#ee0a24",
    });
    await permanentlyDeleteFixedAsset(item.id);
    showSuccessToast("删除成功");
    emit("delete");
    loadList();
  } catch (e) {
    if (e !== "cancel") {
      showToast(e.message || "删除失败");
    }
  }
};

defineExpose({ loadList });

onMounted(() => {
  loadList();
});
</script>

<style scoped>
.recycle-bin {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 12px 16px;
}

.stats-bar {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  text-align: center;
  color: #969799;
  font-size: 14px;
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.asset-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  gap: 12px;
}

.card-image {
  width: 60px;
  height: 60px;
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
  font-size: 20px;
}

.card-content {
  flex: 1;
  min-width: 0;
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
  margin-top: 2px;
}

.card-info {
  font-size: 12px;
  color: #646566;
  margin-top: 4px;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}

:deep(.danger-title) {
  color: #ee0a24;
}

/* 新增：修复回收站为空时图标与文字对齐 */
.empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

.empty-icon {
  color: #c8c9cc; /* 与空状态文字颜色统一，更协调 */
  margin-bottom: 16px; /* 图标与文字间距，避免拥挤 */
}

/* 微调空状态文字样式，确保与图标对齐协调 */
:deep(.van-empty__description) {
  color: #969799;
  font-size: 14px;
}
</style>
