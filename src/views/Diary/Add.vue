<template>
  <div class="page-diary-add">
    <van-nav-bar>
      <template #left>
        <van-button size="small" plain type="default" round @click="handleCancel">
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
        <div
          v-for="item in selectedImages"
          :key="item.id"
          class="preview-item"
        >
          <van-image fit="cover" :src="getFullUrl(item.file_path)" />
          <van-icon name="cross" class="remove-icon" @click="removeSelected(item.id)" />
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
            <div
              v-for="item in imageList"
              :key="item.id"
              class="image-item"
            >
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
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from "vant";
import { uploadApi, BusType } from "@/utils/api/upload";
import { getFullLocation } from "@/utils/geolocation";
import { momentApi } from "@/utils/api/moment";

const router = useRouter();

import ENV from '@/utils/env'
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

// 位置信息
const locationText = ref("点击获取位置");
const locationData = ref(null); // 完整位置对象 { name, lat, lng }
const hasLocated = ref(false);

// 已选择的图片（最终确认的）
const selectedImages = ref([]);

// 图片选择弹窗内
const imageList = ref([]);       // 全部图片列表
const localSelected = ref([]);   // 弹窗内临时选中
const uploadQueue = ref([]);     // 上传队列
const loading = ref(false);

const moodActions = [
  { name: "开心" },
  { name: "平静" },
  { name: "难过" },
  { name: "累" },
  { name: "兴奋" },
  { name: "郁闷" },
];

// 心情选择
const onMoodSelect = (item) => {
  mood.value = item.name;
  showMood.value = false;
};

// 获取当前位置
const locationLoading = ref(false);

const handleGetLocation = async () => {
  if (locationLoading.value) return;

  locationLoading.value = true;
  try {
    // 获取完整位置（含经纬度）
    const loc = await getFullLocation();
    locationText.value = loc.name;
    locationData.value = loc;
    hasLocated.value = true;
  } catch (err) {
    console.error("获取位置失败:", err);
    showToast("获取位置失败，请检查定位权限");
  } finally {
    locationLoading.value = false;
  }
};

// 加载图片列表
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
    console.error("加载图片列表失败:", err);
  } finally {
    loading.value = false;
  }
};

// 获取缩略图路径（列表展示用）
const getThumbUrl = (item) => {
  const path = item.thumbnail || item.file_path;
  return getFullUrl(path);
};

// 弹窗打开时加载列表
watch(showImagePicker, (val) => {
  if (val) {
    loadImageList();
    localSelected.value = [...selectedImages.value.map((i) => i.id)];
  }
});

// 上传前校验
const beforeUpload = (file) => {
  const isImage = file.type.startsWith("image/");
  if (!isImage) {
    showToast("只能上传图片文件");
    return false;
  }
  const isLt5M = file.size / 1024 / 1024 < 5;
  if (!isLt5M) {
    showToast("图片大小不能超过 5MB");
    return false;
  }
  return true;
};

// 上传图片
const handleAfterRead = async (file) => {
  const files = Array.isArray(file) ? file : [file];

  for (const f of files) {
    f.status = "uploading";
    f.message = "上传中...";

    try {
      const res = await uploadApi.single(f.file, BusType.POST);
      if (res.data) {
        // 上传成功，添加到列表并自动选中
        // 统一 file_path 字段：url -> file_path，确保预览能正常显示
        const newItem = {
          ...res.data,
          file_path: res.data.url || res.data.file_path,
          uploading: false,
        };
        imageList.value.unshift(newItem);
        // 自动选中刚上传的图片
        if (!localSelected.value.includes(newItem.id) && localSelected.value.length < MAX_SELECT) {
          localSelected.value.push(newItem.id);
        }
        f.status = "done";
        f.message = "上传成功";
      } else {
        f.status = "failed";
        f.message = "上传失败";
      }
    } catch (err) {
      f.status = "failed";
      f.message = "上传失败";
      console.error("上传失败:", err);
    }
  }

  uploadQueue.value = [];
};

// 点击图片（未选中则选中，已选中则取消）
const MAX_SELECT = 9;

const handleImageClick = (item) => {
  const id = item.id;
  const index = localSelected.value.indexOf(id);
  if (index > -1) {
    // 取消选中
    localSelected.value.splice(index, 1);
  } else {
    // 选中，最多9张
    if (localSelected.value.length >= MAX_SELECT) {
      showToast(`最多只能选择 ${MAX_SELECT} 张图片`);
      return;
    }
    localSelected.value.push(id);
  }
};

// 确认选择
const confirmSelection = () => {
  selectedImages.value = imageList.value
    .filter((item) => localSelected.value.includes(item.id))
    .map((item) => ({
      ...item,
      // 统一 file_path 字段：优先用 file_path，否则 fallback 到 url
      file_path: item.file_path || item.url,
    }));
  showImagePicker.value = false;
};

// 移除已选图片
const removeSelected = (id) => {
  const index = selectedImages.value.findIndex((i) => i.id === id);
  if (index > -1) {
    selectedImages.value.splice(index, 1);
  }
};

// 发布
const handleSave = async () => {
  if (!content.value.trim()) {
    showToast("请输入内容");
    return;
  }

  try {
    await showConfirmDialog({
      title: "确认发布",
      message: `将发布动态${selectedImages.value.length > 0 ? `，附带 ${selectedImages.value.length} 张图片` : ""}`,
    });

    saving.value = true;
    showLoadingToast({ message: "发布中...", forbidClick: true });

    // 构建图片列表（使用 url 字段）
    const images = selectedImages.value.map((item) => ({
      url: item.file_path || item.url || "",
    }));

    // 构建位置对象
    const location = locationData.value || null;

    // 调用 API
    await momentApi.create({
      content: content.value,
      images,
      mood: mood.value,
      location,
      visibleType: 0,
    });

    closeToast();
    showToast("发布成功");
    setTimeout(() => router.replace("/diary"), 1000);
  } catch (err) {
    closeToast();
    if (err !== "cancel") {
      console.error("发布失败:", err);
      showToast("发布失败，请重试");
    }
  } finally {
    saving.value = false;
  }
};

// 取消
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
  height: 50vh!important;
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
