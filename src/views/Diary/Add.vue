<template>
  <div class="page-diary-add">
    <van-nav-bar>
      <template #left>
        <van-button
          size="small"
          plain
          type="default"
          round
          @click="handleCancel"
        >
          取消
        </van-button>
      </template>
      <template #right>
        <van-button size="small" type="primary" round @click="handleSave">
          发布
        </van-button>
      </template>
    </van-nav-bar>

    <div class="editor-container">
      <van-field
        v-model="content"
        rows="10"
        autosize
        type="textarea"
        placeholder="这一刻的想法..."
        :border="false"
        class="diary-input"
      />

      <!-- 已选择图片预览 -->
      <div v-if="selectedImages.length > 0" class="selected-preview">
        <div v-for="item in selectedImages" :key="item.id" class="preview-item">
          <van-image fit="cover" :src="getFullUrl(item.file_path)" />
          <van-icon
            name="cross"
            class="remove-icon"
            @click="removeSelected(item.id)"
          />
        </div>
      </div>

      <van-cell-group inset class="meta-cells">
        <van-cell
          title="今日心情"
          is-link
          :value="mood"
          @click="showMood = true"
          icon="smile-o"
        />
        <van-cell
          title="添加图片"
          is-link
          value=""
          icon="photograph"
          @click="showImagePicker = true"
        />
        <van-cell
          title="当前位置"
          is-link
          :value="locationText"
          icon="location-o"
          @click="handleGetLocation"
        />
      </van-cell-group>
    </div>

    <!-- 心情选择 -->
    <van-action-sheet
      v-model:show="showMood"
      :actions="moodActions"
      @select="onMoodSelect"
    />

    <!-- 位置选择弹窗 -->
    <van-action-sheet
      v-model:show="showLocationPicker"
      title="选择位置"
      :actions="locationActions"
      @select="onLocationSelect"
    />

    <!-- 图片选择弹窗 -->
    <van-popup
      v-model:show="showImagePicker"
      position="bottom"
      round
      :style="{ height: '70%' }"
    >
      <div class="image-picker">
        <div class="picker-header">
          <span class="picker-title">选择图片</span>
          <span v-if="localSelected.length > 0" class="selected-count">
            已选 {{ localSelected.length }} 张
          </span>
          <van-button size="small" type="primary" @click="confirmSelection">
            确认
          </van-button>
        </div>

        <!-- 上传区域 -->
        <div class="upload-section">
          <van-uploader
            v-model="uploadQueue"
            multiple
            :max-count="9"
            :after-read="handleAfterRead"
            :before-read="beforeUpload"
            preview-size="80px"
          >
            <div class="upload-trigger">
              <van-icon name="plus" size="24" />
              <span>上传图片</span>
            </div>
          </van-uploader>
        </div>

        <!-- 图片列表 -->
        <van-checkbox-group v-model="localSelected" class="image-list-wrapper">
          <div class="image-grid">
            <div v-for="item in imageList" :key="item.id" class="image-item">
              <van-image
                fit="cover"
                :src="getThumbUrl(item)"
                @click="handleImageClick(item)"
              />
              <van-checkbox
                :name="item.id"
                class="image-checkbox"
                @click.stop
              />
              <!-- 上传中标记 -->
              <div v-if="item.uploading" class="uploading-mask">
                <van-loading type="spinner" color="#fff" />
              </div>
            </div>
          </div>
        </van-checkbox-group>

        <!-- 空状态 -->
        <van-empty
          v-if="!loading && imageList.length === 0"
          description="暂无图片，请上传"
          image="search"
        />

        <!-- 加载中 -->
        <van-loading v-if="loading" class="loading" />
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  showToast,
  showConfirmDialog,
  showLoadingToast,
  closeToast,
} from "vant";
import { uploadApi, BusType } from "@/utils/api/upload";
import { getFullLocation, getLocationOptions } from "@/utils/geolocation";
import { momentApi } from "@/utils/api/moment";
import imageCompression from "browser-image-compression";

const router = useRouter();

import ENV from "@/utils/env";
const BASE_URL = ENV.FILE_BASE_URL;
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  return BASE_URL + path;
};

const content = ref("");
const mood = ref("开心");
const showMood = ref(false);
const showImagePicker = ref(false);
const saving = ref(false);

// ================== 📍 位置 ==================
const locationText = ref("点击获取位置");
const locationData = ref(null);
const hasLocated = ref(false);

const showLocationPicker = ref(false);
const locationActions = ref([]);

// 👉 点击只弹选择（不请求）
const handleGetLocation = () => {
  locationActions.value = [
    {
      name: "精确定位",
      subname: "GPS（需要授权）",
      type: "gps",
    },
    {
      name: "大致位置",
      subname: "IP 定位",
      type: "ip",
    },
  ];

  showLocationPicker.value = true;
};

// 👉 用户选择后才执行
const onLocationSelect = async (item) => {
  showLocationPicker.value = false;

  // ===== GPS =====
  if (item.type === "gps") {
    try {
      showToast("正在获取精确位置...");

      const loc = await getFullLocation();

      locationText.value = loc.name;
      locationData.value = loc;
      hasLocated.value = true;
    } catch (err) {
      console.error(err);
      showToast("定位失败或未授权");
    }
  }

  // ===== IP =====
  if (item.type === "ip") {
    try {
      showToast("正在获取大致位置...");

      const res = await getLocationOptions();
      const ip = res.ip;

      if (!ip || ip.name === "未知") {
        showToast("IP定位失败");
        return;
      }

      locationText.value = ip.name;
      locationData.value = {
        name: ip.name,
        lat: ip.lat,
        lng: ip.lng,
      };
      hasLocated.value = true;
    } catch (err) {
      console.error(err);
      showToast("IP定位失败");
    }
  }
};

// ================== 😊 心情 ==================
const moodActions = [
  { name: "开心" },
  { name: "平静" },
  { name: "难过" },
  { name: "累" },
  { name: "兴奋" },
  { name: "郁闷" },
];

const onMoodSelect = (item) => {
  mood.value = item.name;
  showMood.value = false;
};

// ================== 🖼 图片 ==================
const selectedImages = ref([]);
const imageList = ref([]);
const localSelected = ref([]);
const uploadQueue = ref([]);
const loading = ref(false);

const loadImageList = async () => {
  loading.value = true;
  try {
    const res = await uploadApi.list({
      busType: BusType.POST,
      busId: "",
      limit: 100,
      offset: 0,
    });
    imageList.value = Array.isArray(res.data) ? res.data : [];
  } catch (err) {
    console.error("加载图片失败:", err);
  } finally {
    loading.value = false;
  }
};

const getThumbUrl = (item) => {
  const path = item.thumbnail || item.file_path;
  return getFullUrl(path);
};

watch(showImagePicker, (val) => {
  if (val) {
    loadImageList();
    localSelected.value = selectedImages.value.map((i) => i.id);
  }
});

// ================== 📤 上传 ==================
const MAX_FILE_SIZE = 35;

const beforeUpload = (file) => {
  if (!file.type.startsWith("image/")) {
    showToast("只能上传图片");
    return false;
  }
  if (file.size / 1024 / 1024 > MAX_FILE_SIZE) {
    showToast(`不能超过 ${MAX_FILE_SIZE}MB`);
    return false;
  }
  return true;
};

const uploadSingleFile = async (fileItem) => {
  try {
    const res = await uploadApi.single(fileItem.file, BusType.POST);
    if (res.data) {
      const newItem = {
        ...res.data,
        file_path: res.data.url || res.data.file_path,
      };
      imageList.value.unshift(newItem);
      fileItem.status = "done";
      return newItem;
    }
  } catch {
    fileItem.status = "failed";
  }
};

const compressAndUpload = async (fileItem) => {
  const file = fileItem.file;

  if (file.size / 1024 / 1024 < 5) {
    return uploadSingleFile(fileItem);
  }

  fileItem.status = "uploading";
  fileItem.message = "压缩中...";

  try {
    const compressed = await imageCompression(file, {
      maxSizeMB: 4.9,
      maxWidthOrHeight: 4096,
      useWebWorker: true,
    });

    fileItem.file = compressed;
    return uploadSingleFile(fileItem);
  } catch {
    fileItem.status = "failed";
  }
};

const handleAfterRead = async (file) => {
  const files = Array.isArray(file) ? file : [file];
  for (const f of files) {
    await compressAndUpload(f);
  }
  uploadQueue.value = [];
};

// ================== 🧠 选择 ==================
const MAX_SELECT = 9;

const handleImageClick = (item) => {
  const i = localSelected.value.indexOf(item.id);
  if (i > -1) {
    localSelected.value.splice(i, 1);
  } else {
    if (localSelected.value.length >= MAX_SELECT) {
      showToast("最多9张");
      return;
    }
    localSelected.value.push(item.id);
  }
};

const confirmSelection = () => {
  selectedImages.value = imageList.value.filter((i) =>
    localSelected.value.includes(i.id)
  );
  showImagePicker.value = false;
};

const removeSelected = (id) => {
  selectedImages.value = selectedImages.value.filter((i) => i.id !== id);
};

// ================== 🚀 发布 ==================
const handleSave = async () => {
  if (!content.value.trim()) {
    showToast("请输入内容");
    return;
  }

  try {
    await showConfirmDialog({ title: "确认发布" });

    showLoadingToast({ message: "发布中..." });

    await momentApi.create({
      content: content.value,
      images: selectedImages.value.map((i) => ({ url: i.file_path })),
      mood: mood.value,
      location: locationData.value,
      visibleType: 0,
    });

    closeToast();
    showToast("成功");
    router.replace("/diary");
  } catch (err) {
    closeToast();
    if (err !== "cancel") showToast("失败");
  }
};

const handleCancel = () => {
  router.back();
};
</script>

<style scoped>
.page-diary-add {
  min-height: 100vh;
  background: #f7f8fa;
}

.editor-container {
  background: #fff;
}

.diary-input {
  font-size: 16px;
  padding: 16px;
}

/* 已选图片预览 */
.selected-preview {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  flex-wrap: wrap;
}

.preview-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
}

.preview-item .van-image {
  width: 100%;
  height: 100%;
}

.remove-icon {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 50%;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.meta-cells {
  margin-top: 12px;
}

/* 图片选择弹窗 */
.image-picker {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.picker-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.picker-title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}

.selected-count {
  font-size: 13px;
  color: #07c160;
}

/* 上传区域 */
.upload-section {
  padding: 12px 0;
}

.upload-trigger {
  width: 80px;
  height: 80px;
  background: #f7f8fa;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #969799;
}

.upload-trigger span {
  font-size: 12px;
}
/* 图片展示区域 */
:deep(.van-checkbox-group) {
  height: 50vh !important;
  overflow-y: scroll;
}

/* 图片网格 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 12px 0;
}

.image-list-wrapper {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.image-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}

.image-item:has(.van-checkbox--checked) {
  border-color: #07c160;
}

.image-item .van-image {
  width: 100%;
  height: 100%;
}

.image-checkbox {
  position: absolute;
  top: 4px;
  right: 4px;
}

/* 上传中遮罩 */
.uploading-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading {
  text-align: center;
  padding: 20px;
}
</style>
