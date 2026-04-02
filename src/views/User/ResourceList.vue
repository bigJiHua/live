<template>
  <div class="page-resource-list">
    <div class="action-bar">
      <van-button size="small" type="primary" @click="showUpload = true">
        <van-icon name="plus" /> 上传文件
      </van-button>
      <van-button
        v-if="selectedList.length > 0"
        size="small"
        type="danger"
        plain
        @click="handleBatchDelete"
      >
        删除 ({{ selectedList.length }})
      </van-button>
    </div>

    <van-checkbox-group v-model="selectedList" shape="square">
      <div class="resource-grid">
        <div
          v-for="(item, index) in resourceList"
          :key="item.id"
          class="resource-item"
        >
          <van-checkbox
            :name="item.id"
            class="resource-checkbox"
          />
          
          <van-image
            fit="cover"
            :src="getThumbUrl(item)"
            class="resource-image"
            @click="handlePreview(index)"
            lazy-load
          >
            <template #loading>
              <van-loading type="spinner" size="20" />
            </template>
            <template #error>加载失败</template>
          </van-image>

          <div class="resource-info">
            <span class="resource-name">{{ formatName(item.file_name) }}</span>
            <span class="resource-size">{{ formatSize(item.file_size) }}</span>
          </div>
        </div>
      </div>
    </van-checkbox-group>

    <van-empty
      v-if="!loading && resourceList.length === 0"
      description="暂无资源"
      image="search"
    />

    <van-overlay :show="loading" z-index="100">
      <div class="flex-center">
        <van-loading vertical>加载中...</van-loading>
      </div>
    </van-overlay>

    <van-popup
      v-model:show="showUpload"
      position="bottom"
      round
      :style="{ height: '60%' }"
    >
      <div class="upload-popup">
        <div class="popup-header">
          <span>上传到 {{ currentTypeName }}</span>
          <van-icon name="cross" @click="showUpload = false" />
        </div>

        <div class="upload-content">
          <van-uploader
            v-model="uploadFileList"
            :after-read="handleAfterRead"
            :max-count="10"
            multiple
            accept="image/*"
          >
            <div class="upload-trigger">
              <van-icon name="photograph" size="40" color="#969799" />
              <span>点击或拖拽上传</span>
            </div>
          </van-uploader>
        </div>
        
        <div class="upload-tips">支持多选，单文件最大 10MB</div>
      </div>
    </van-popup>

    <van-image-preview
      v-model:show="showPreview"
      :images="previewImages"
      :start-position="previewIndex"
      @change="onPreviewChange"
      closeable
    >
      <template #cover>
        <div class="preview-footer" v-if="currentPreviewItem">
          <div class="file-detail">
            <p>{{ currentPreviewItem.file_name }}</p>
            <small>{{ formatSize(currentPreviewItem.file_size) }}</small>
          </div>
          <van-button size="small" type="danger" icon="delete" @click="handleDelete(currentPreviewItem)">
            删除
          </van-button>
        </div>
      </template>
    </van-image-preview>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRoute } from "vue-router";
import { showConfirmDialog, showToast } from "vant";
import { uploadApi, BusType } from "@/utils/api/upload";

const route = useRoute();

// --- 常量配置 ---
const BASE_URL = "http://192.168.0.103:3001/api/public";

// --- 响应式状态 ---
const loading = ref(false);
const resourceList = ref([]);
const selectedList = ref([]);
const showUpload = ref(false);
const showPreview = ref(false);
const previewIndex = ref(0);
const uploadFileList = ref([]);

// --- 计算属性 ---
const currentType = computed(() => route.query.type || BusType.OTHER);

const currentTypeName = computed(() => {
  const map = {
    [BusType.AVATAR]: "头像",
    [BusType.POST]: "动态",
    [BusType.PRODUCT]: "商品",
  };
  return map[currentType.value] || "资源";
});

// 构建所有预览图片的完整 URL 数组（使用 url 字段）
const previewImages = computed(() => 
  resourceList.value.map(item => getFullUrl(item.url || item.file_path))
);

// 获取当前正在预览的对象
const currentPreviewItem = computed(() => 
  resourceList.value[previewIndex.value] || null
);

// --- 核心方法 ---

/**
 * URL 拼接核心逻辑
 */
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  // 确保 BASE_URL 末尾无斜杠，path 开头有斜杠
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

/**
 * 获取缩略图路径（列表展示用）
 */
const getThumbUrl = (item) => {
  const path = item.thumbnail || item.file_path;
  return getFullUrl(path);
};

/**
 * 加载数据
 */
const loadResourceList = async () => {
  loading.value = true;
  try {
    const res = await uploadApi.list({
      busType: currentType.value,
      limit: 100,
    });
    resourceList.value = res.data || [];
  } catch (err) {
    showToast("列表加载失败");
  } finally {
    loading.value = false;
  }
};

/**
 * 处理预览显示
 */
const handlePreview = (index) => {
  previewIndex.value = index;
  // nextTick 确保预览数组已经计算完成
  nextTick(() => {
    showPreview.value = true;
  });
};

const onPreviewChange = (index) => {
  previewIndex.value = index;
};

/**
 * 上传处理
 */
const handleAfterRead = async (file) => {
  const files = Array.isArray(file) ? file : [file];
  
  for (const f of files) {
    f.status = 'uploading';
    f.message = '上传中...';
    try {
      await uploadApi.single(f.file, currentType.value);
      f.status = 'done';
    } catch (e) {
      f.status = 'failed';
      f.message = '失败';
    }
  }
  
  showToast("处理完成");
  loadResourceList();
};

/**
 * 删除单个
 */
const handleDelete = async (item) => {
  try {
    await showConfirmDialog({ title: '确认删除？' });
    await uploadApi.delete(item.id);
    showToast("已删除");
    showPreview.value = false; // 关闭预览
    loadResourceList();
  } catch (e) {}
};

/**
 * 批量删除
 */
const handleBatchDelete = async () => {
  try {
    await showConfirmDialog({ title: '警告', message: `确定删除这 ${selectedList.value.length} 项吗？` });
    await uploadApi.batchDelete(selectedList.value);
    showToast("批量删除成功");
    selectedList.value = [];
    loadResourceList();
  } catch (e) {}
};

// --- 工具函数 ---
const formatName = (name) => name ? (name.length > 12 ? name.slice(0, 12) + '...' : name) : '未知文件';

const formatSize = (size) => {
  const bytes = parseInt(size);
  if (isNaN(bytes) || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

onMounted(() => {
  loadResourceList();
});
</script>

<style scoped>
.page-resource-list {
  min-height: 100vh;
  background: #f7f8fa;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 99;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px;
}

.resource-item {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 2px 4px rgba(0,0,0,0.03);
}

.resource-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  background: rgba(255,255,255,0.8);
  border-radius: 4px;
  padding: 2px;
}

.resource-image {
  width: 100%;
  height: 160px;
  display: block;
}

.resource-info {
  padding: 8px 10px;
}

.resource-name {
  display: block;
  font-size: 13px;
  color: #323233;
  margin-bottom: 2px;
}

.resource-size {
  font-size: 11px;
  color: #969799;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

/* 预览脚部定制 */
.preview-footer {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px 16px;
  background: linear-gradient(to top, rgba(0,0,0,0.8), transparent);
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fff;
}

.file-detail p {
  margin: 0;
  font-size: 14px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-popup {
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  font-weight: bold;
}

.upload-trigger {
  border: 2px dashed #dcdee0;
  border-radius: 8px;
  width: 100%;
  height: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #969799;
}
</style>