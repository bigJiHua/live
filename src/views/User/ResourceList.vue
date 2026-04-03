<template>
  <div class="page-resource-list">
    <div class="action-bar">
      <van-search
        v-model="searchKey"
        placeholder="搜索图片说明/标签"
        shape="round"
        background="transparent"
        @search="handleSearch"
        @clear="handleSearchClear"
      />
      <van-button size="small" type="primary" @click="showUpload = true">
        <van-icon name="plus" /> 上传
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
          <van-checkbox :name="item.id" class="resource-checkbox" />

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
            <div v-if="item.remark" class="resource-remark">
              {{ item.remark }}
            </div>
            <div
              v-if="Array.isArray(item.tags) && item.tags.length"
              class="resource-tags"
            >
              <van-tag
                v-for="tag in item.tags"
                :key="tag"
                size="small"
                type="primary"
                >{{ tag }}</van-tag
              >
            </div>
            <!-- 选中时显示编辑按钮 -->
            <div v-if="selectedList.includes(item.id) && !undeletableIds.includes(item.id)" class="edit-button">
              <van-button
                size="mini"
                type="primary"
                icon="edit"
                @click.stop="openEditDialog(item)"
              />
            </div>
            <!-- 不可删除标签 -->
            <div v-if="undeletableIds.includes(item.id)" class="undeletable-tag">
              <van-tag type="warning" size="small">使用中</van-tag>
            </div>
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
      :style="{ height: '90%' }"
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

        <!-- 图片说明 -->
        <div class="upload-field">
          <van-field
            v-model="uploadRemark"
            label="图片说明"
            placeholder="可选，描述这张图片"
            maxlength="30"
            show-word-limit
            clearable
          />
        </div>

        <!-- 标签输入 -->
        <div class="upload-field">
          <van-field
            v-model="tagInput"
            label="标签"
            placeholder="输入标签后回车添加"
            maxlength="10"
            @keyup.enter="addTag"
            clearable
          >
            <template #button>
              <van-button size="small" type="primary" @click="addTag"
                >添加</van-button
              >
            </template>
          </van-field>
          <div v-if="uploadTags.length > 0" class="tag-list">
            <van-tag
              v-for="(tag, idx) in uploadTags"
              :key="idx"
              size="medium"
              closeable
              @close="removeTag(idx)"
            >
              {{ tag }}
            </van-tag>
          </div>
        </div>

        <div class="upload-tips">支持多选，单文件最大 10MB</div>

        <van-button
          type="primary"
          block
          :loading="uploading"
          :disabled="uploadFileList.length === 0"
          @click="handleSubmit"
        >
          提交上传
        </van-button>
      </div>
    </van-popup>

    <!-- 编辑弹框 -->
    <van-dialog
      v-model:show="showEditDialog"
      title="编辑图片信息"
      show-cancel-button
      @confirm="handleEditConfirm"
    >
      <div class="edit-form">
        <van-field
          v-model="editRemark"
          label="图片说明"
          placeholder="描述这张图片"
          maxlength="30"
          show-word-limit
          clearable
        />
        <van-field
          v-model="editTagInput"
          label="标签"
          placeholder="输入标签后点击添加"
          maxlength="10"
        >
          <template #button>
            <van-button size="small" type="primary" @click="addEditTag"
              >添加</van-button
            >
          </template>
        </van-field>
        <div v-if="editTags.length > 0" class="edit-tags">
          <van-tag
            v-for="(tag, idx) in editTags"
            :key="idx"
            size="medium"
            closeable
            @close="removeEditTag(idx)"
          >
            {{ tag }}
          </van-tag>
        </div>
      </div>
    </van-dialog>

    <van-image-preview
      v-model:show="showPreview"
      :images="previewImages"
      :start-position="previewIndex"
      @change="onPreviewChange"
      closeable
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRoute } from "vue-router";
import { showConfirmDialog, showToast } from "vant";
import { uploadApi, BusType } from "@/utils/api/upload";
import imageCompression from "browser-image-compression";

const route = useRoute();

// --- 常量配置 ---
const BASE_URL = "http://192.168.0.103:3001/api/public";

// --- 响应式状态 ---
const loading = ref(false);
const resourceList = ref([]);
const selectedList = ref([]);
const showUpload = ref(false);
const showPreview = ref(false);
const searchKey = ref("");
const uploading = ref(false);
const previewIndex = ref(0);
const uploadFileList = ref([]);
const undeletableIds = ref([]); // 不可删除的文件ID列表

// 上传附加信息
const uploadRemark = ref("");
const tagInput = ref("");
const uploadTags = ref([]);

// 编辑相关
const showEditDialog = ref(false);
const editTargetId = ref("");
const editRemark = ref("");
const editTagInput = ref("");
const editTags = ref([]);

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
  resourceList.value.map((item) => getFullUrl(item.url || item.file_path))
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
 * 搜索数据
 */
const loadSearchResult = async () => {
  if (!searchKey.value.trim()) {
    loadResourceList();
    return;
  }
  loading.value = true;
  try {
    const res = await uploadApi.search({
      type: currentType.value,
      key: searchKey.value.trim(),
      limit: 50,
    });
    resourceList.value = res.data || [];
  } catch (err) {
    showToast("搜索失败");
  } finally {
    loading.value = false;
  }
};

/**
 * 执行搜索
 */
const handleSearch = () => {
  // 限制关键词长度 10 个字符
  if (searchKey.value.length > 10) {
    searchKey.value = searchKey.value.slice(0, 10);
  }
  loadSearchResult();
};

/**
 * 清除搜索
 */
const handleSearchClear = () => {
  loadResourceList();
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
 * 上传处理 - 只添加到列表，不立即上传
 */
const handleAfterRead = () => {
  // 文件已自动添加到 uploadFileList，无需额外处理
};

/**
 * 判断是否为 SVG 格式
 */
const isSvg = (file) => {
  return file.type === "image/svg+xml" || file.name.toLowerCase().endsWith(".svg");
};

/**
 * 文件大小限制（字节）
 */
const MAX_SIZE = 10 * 1024 * 1024; // 10MB

/**
 * 压缩单个文件
 */
const compressFile = async (file) => {
  // SVG 不压缩，只做大小限制
  if (isSvg(file)) {
    if (file.size > MAX_SIZE) {
      showToast(`${file.name} 超过10MB限制`);
      throw new Error("文件过大");
    }
    return file;
  }

  try {
    const options = {
      maxSizeMB: 1, // 最大 1MB
      maxWidthOrHeight: 1920, // 最大尺寸
      useWebWorker: true, // 使用 Web Worker
    };
    return await imageCompression(file, options);
  } catch (e) {
    // 压缩失败则返回原文件
    console.warn("图片压缩失败，使用原文件:", e);
    return file;
  }
};

/**
 * 提交上传
 */
const handleSubmit = async () => {
  if (uploadFileList.value.length === 0) return;

  uploading.value = true;
  try {
    // 显示压缩进度
    showToast("正在压缩图片...");

    // 压缩所有文件
    const compressedFiles = [];
    for (const item of uploadFileList.value) {
      const compressed = await compressFile(item.file);
      compressedFiles.push(compressed);
    }

    const remark = uploadRemark.value.trim();
    const tags = [...uploadTags.value]; // 确保是纯数组
    await uploadApi.multiple(compressedFiles, currentType.value, "", remark, tags);
    showToast("上传成功");
    // 清空上传信息
    uploadRemark.value = "";
    uploadTags.value = [];
    tagInput.value = "";
    uploadFileList.value = [];
    showUpload.value = false;
    loadResourceList();
  } catch (e) {
    if (e.message !== "文件过大") {
      showToast("上传失败");
    }
  } finally {
    uploading.value = false;
  }
};

/**
 * 添加标签
 */
const addTag = () => {
  const tag = tagInput.value.trim();
  if (tag && !uploadTags.value.includes(tag)) {
    uploadTags.value.push(tag);
    tagInput.value = "";
  }
};

/**
 * 删除标签
 */
const removeTag = (index) => {
  uploadTags.value.splice(index, 1);
};

/**
 * 打开编辑弹框
 */
const openEditDialog = (item) => {
  editTargetId.value = item.id;
  editRemark.value = item.remark || "";
  editTags.value = item.tags ? [...item.tags] : [];
  editTagInput.value = "";
  showEditDialog.value = true;
};

/**
 * 添加编辑标签
 */
const addEditTag = () => {
  const tag = editTagInput.value.trim();
  if (tag && !editTags.value.includes(tag)) {
    editTags.value.push(tag);
    editTagInput.value = "";
  }
};

/**
 * 删除编辑标签
 */
const removeEditTag = (index) => {
  editTags.value.splice(index, 1);
};

/**
 * 确认编辑
 */
const handleEditConfirm = async () => {
  try {
    await uploadApi.update(editTargetId.value, {
      remark: editRemark.value,
      tags: editTags.value,
    });
    showToast("更新成功");
    loadResourceList();
  } catch (e) {
    showToast("更新失败");
  }
};

/**
 * 批量删除
 */
const handleBatchDelete = async () => {
  try {
    await showConfirmDialog({
      title: "警告",
      message: `确定删除这 ${selectedList.value.length} 项吗？`,
    });
    await uploadApi.batchDelete(selectedList.value);
    showToast("批量删除成功");
    selectedList.value = [];
    loadResourceList();
  } catch (e) {
    // 处理删除失败（文件正在被使用）
    if (e?.response?.status === 400 && e?.response?.data?.referencedIds) {
      const referencedIds = e.response.data.referencedIds;
      // 将不可删除的文件ID存入列表
      undeletableIds.value = referencedIds;
      // 从选中列表中移除已成功删除的
      selectedList.value = selectedList.value.filter(
        (id) => !referencedIds.includes(id)
      );
      showToast(e.response.data.message || "部分文件无法删除");
    } else {
      showToast("删除失败");
    }
  }
};

// --- 工具函数 ---
const formatName = (name) =>
  name ? (name.length > 12 ? name.slice(0, 12) + "..." : name) : "未知文件";

const formatSize = (size) => {
  const bytes = parseInt(size);
  if (isNaN(bytes) || bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
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
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 99;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  gap: 8px;
}

.action-bar :deep(.van-search) {
  flex: 1;
  padding: 0;
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
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.03);
}

.resource-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 4px;
  padding: 2px;
}

.edit-button {
  position: absolute;
  top: 5px;
  right: 8px;
  z-index: 10;
}

.undeletable-tag {
  position: absolute;
  top: 5px;
  right: 8px;
  z-index: 10;
}

.resource-image {
  width: 100%;
  height: 160px;
  display: block;
}

.resource-info {
  padding: 8px 10px;
  position: relative;
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

.resource-remark {
  font-size: 12px;
  color: #666;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

/* 编辑表单 */
.edit-form {
  padding: 16px;
}

.edit-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
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

.upload-field {
  margin-top: 12px;
}

.upload-tips {
  margin: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px;
}
</style>
