<template>
  <div class="page-diary-add">
    <van-nav-bar>
      <template #left>
        <van-button size="small" plain type="default" round @click="handleCancel">
          取消
        </van-button>
      </template>
      <template #title>
        <div class="mode-toggle">
          <span
            :class="['mode-btn', { active: mode === 'quick' }]"
            @click="switchMode('quick')"
          >快速</span>
          <span
            :class="['mode-btn', { active: mode === 'precision' }]"
            @click="switchMode('precision')"
          >精准</span>
        </div>
      </template>
      <template #right>
        <van-button size="small" type="primary" round @click="handleSave">
          发布
        </van-button>
      </template>
    </van-nav-bar>

    <!-- ============= 快速模式 ============= -->
    <div v-show="mode === 'quick'" class="editor-container">
      <van-field
        v-model="content"
        rows="10"
        autosize
        type="textarea"
        placeholder="这一刻的想法..."
        :border="false"
        class="diary-input"
      />

      <div v-if="selectedImages.length > 0" class="selected-preview">
        <div v-for="item in selectedImages" :key="item.id" class="preview-item">
          <van-image fit="cover" :src="getFullUrl(item.file_path)" />
          <van-icon name="cross" class="remove-icon" @click="removeSelected(item.id)" />
        </div>
      </div>
    </div>

    <!-- ============= 精准模式 ============= -->
    <div v-if="mode === 'precision'" class="editor-container wangeditor-container">
      <Toolbar
        :editor="editorRef"
        :defaultConfig="toolbarConfig"
        class="wangeditor-toolbar"
      />
      <Editor
        v-model="htmlContent"
        :defaultConfig="editorConfig"
        class="wangeditor-editor"
        style="height: 400px; overflow-y: hidden;"
        @onCreated="handleCreated"
      />
    </div>

    <!-- ============= 底部元信息（两种模式共用） ============= -->
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

    <!-- 心情选择 -->
    <van-action-sheet
      v-model:show="showMood"
      :actions="moodActions"
      @select="onMoodSelect"
    />

    <!-- 位置选择 -->
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

        <van-checkbox-group v-model="localSelected" class="image-list-wrapper">
          <div class="image-grid">
            <div
              v-for="item in imageList"
              :key="item.id"
              :class="['image-item', { checked: localSelected.includes(item.id) }]"
            >
              <van-image fit="cover" :src="getThumbUrl(item)" @click="handleImageClick(item)" />
              <van-checkbox :name="item.id" class="image-checkbox" @click.stop />
              <div v-if="item.uploading" class="uploading-mask">
                <van-loading type="spinner" color="#fff" />
              </div>
            </div>
          </div>
        </van-checkbox-group>

        <van-empty v-if="!loading && imageList.length === 0" description="暂无图片，请上传" image="search" />
        <van-loading v-if="loading" class="loading" />
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, watch, computed, shallowRef, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from "vant";
import { uploadApi, BusType } from "@/utils/api/upload";
import { getBrowserLocation, getAmapLocation, getIpLocation } from "@/utils/geo";
import { momentApi } from "@/utils/api/moment";
import imageCompression from "browser-image-compression";

import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import ENV from "@/utils/env";

const router = useRouter();
const BASE_URL = ENV.FILE_BASE_URL;
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  return BASE_URL + path;
};

// ================== 模式切换 ==================
const mode = ref("quick");
const content = ref("");
const htmlContent = ref("");

const escapeHtml = (str) => {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
};

const switchMode = (newMode) => {
  // 精准 → 快速：迁移纯文本到 textarea，并清理 editor 引用
  if (mode.value === 'precision' && newMode === 'quick') {
    if (editorRef.value) {
      const text = editorRef.value.getText().trim();
      if (text && !content.value.trim()) {
        content.value = text;
      }
    }
    // v-if 销毁 Editor 后 editor-for-vue 会自毁，此处防悬空引用
    editorRef.value = null;
  }
  // 快速 → 精准：迁移纯文本到富文本（需转义防 XSS）
  if (mode.value === 'quick' && newMode === 'precision' && content.value.trim()) {
    htmlContent.value = htmlContent.value || `<p>${escapeHtml(content.value)}</p>`;
  }
  mode.value = newMode;
};

// ================== 😊 心情 ==================
const mood = ref("开心");
const showMood = ref(false);

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

// ================== 📍 位置 ==================
const locationText = ref("点击获取位置");
const locationData = ref(null);
const showLocationPicker = ref(false);
const locationActions = ref([]);

const handleGetLocation = () => {
  locationActions.value = [
    { name: "精确定位", subname: "GPS（需要授权）", type: "gps" },
    { name: "高德定位", subname: "GPS + 高德地址解析", type: "amap" },
    { name: "大致位置", subname: "IP 定位", type: "ip" },
  ];
  showLocationPicker.value = true;
};

const onLocationSelect = async (item) => {
  showLocationPicker.value = false;

  if (item.type === "gps") {
    showToast("正在获取精确位置...");
    const loc = await getBrowserLocation();
    if (!loc.success) {
      showToast(loc.error || "定位失败");
      return;
    }
    locationText.value = `📍 ${loc.address}`;
    locationData.value = { name: loc.address, lat: loc.lat, lng: loc.lng, type: loc.type };
  }

  if (item.type === "amap") {
    showToast("正在获取高德定位...");
    const loc = await getAmapLocation();
    if (!loc.success) {
      showToast(loc.error || "高德定位失败");
      return;
    }
    locationText.value = `📍 ${loc.address}`;
    locationData.value = { name: loc.address, lat: loc.lat, lng: loc.lng, type: loc.type };
  }

  if (item.type === "ip") {
    showToast("正在获取大致位置...");
    const loc = await getIpLocation();
    if (!loc.success) {
      showToast(loc.error || "IP定位失败");
      return;
    }
    const label = loc.type === "ip" ? "🌐 " : "📍 ";
    locationText.value = label + loc.address;
    locationData.value = { name: loc.address, lat: loc.lat, lng: loc.lng, type: loc.type };
  }
};

// ==================  图片 ==================
const selectedImages = ref([]);
const showImagePicker = ref(false);
const imageList = ref([]);
const localSelected = ref([]);
const uploadQueue = ref([]);
const loading = ref(false);

const loadImageList = async (force = false) => {
  // 缓存：非强制刷新且有数据时跳过
  if (!force && imageList.value.length > 0) return;
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
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    if (!f.type.startsWith("image/")) {
      showToast("只能上传图片")
      return false
    }
    if (f.size / 1024 / 1024 > MAX_FILE_SIZE) {
      showToast(`不能超过 ${MAX_FILE_SIZE}MB`)
      return false
    }
  }
  return true
}

const uploadSingleFile = async (fileItem) => {
  try {
    const res = await uploadApi.single(fileItem.file, BusType.POST);
    if (res.data) {
      const newItem = {
        ...res.data,
        file_path: res.data.url || res.data.file_path,
      };
      imageList.value.unshift(newItem);
      localSelected.value.unshift(newItem.id);
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
  // 并发上传，提升多图速度
  await Promise.all(files.map((f) => compressAndUpload(f)));
  // 延迟清空，避免 van-uploader 预览消失
  setTimeout(() => { uploadQueue.value = []; }, 300);
};

// ================== 🧠 选择 ==================
const MAX_SELECT_QUICK = 9;
const MAX_SELECT_PRECISION = 20;

const MAX_SELECT = computed(() =>
  mode.value === 'precision' ? MAX_SELECT_PRECISION : MAX_SELECT_QUICK
);

const handleImageClick = (item) => {
  const i = localSelected.value.indexOf(item.id);
  if (i > -1) {
    localSelected.value.splice(i, 1);
  } else {
    if (localSelected.value.length >= MAX_SELECT.value) {
      showToast(`最多${MAX_SELECT.value}张`);
      return;
    }
    localSelected.value.push(item.id);
  }
};

const confirmSelection = () => {
  const selected = imageList.value.filter((i) =>
    localSelected.value.includes(i.id)
  );

  if (mode.value === 'quick') {
    selectedImages.value = selected;
  } else {
    const editor = editorRef.value;
    if (editor) {
      // WangEditor v5 无 insertImg API，用 dangerouslyInsertHtml 插入图片
      const imgsHtml = selected
        .map((item) => {
          const url = getFullUrl(item.file_path);
          // 校验 URL 安全性：必须以 BASE_URL 或 https:// 开头
          if (!url.startsWith(BASE_URL) && !url.startsWith('https://')) return '';
          return `<img src="${url}" style="max-width:100%;border-radius:8px;margin:4px 0"/>`;
        })
        .filter(Boolean)
        .join('');
      if (imgsHtml) {
        editor.focus();
        editor.dangerouslyInsertHtml(imgsHtml);
      }
    }
  }

  showImagePicker.value = false;
};

const removeSelected = (id) => {
  selectedImages.value = selectedImages.value.filter((i) => i.id !== id);
};

// ================== 📝 WangEditor ==================
const editorRef = shallowRef()
const toolbarConfig = {
  toolbarKeys: [
    'bold',
    'italic',
    'underline',
    'through',
    'fontSize',
    'lineHeight',
    '|',
    'bulletedList',
    'numberedList',
    'clearStyle',
    'emotion',
    '|',
    {
      key: 'group-more-style',
      title: '更多',
      menuKeys: [
        'header1', 'header2', 'header3',
        'blockquote',
        'codeBlock',
        'divider',
        'todo',
        'undo', 'redo',
      ]
    }
  ]
}
const editorConfig = {
  placeholder: '这一刻的想法...',
  readOnly: false,
  autoFocus: false,
  scroll: true,
}

const handleCreated = (editor) => {
  editorRef.value = editor
}

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    try { editor.destroy() } catch {}
  }
})

// ================== 🚀 发布 ==================
const submitting = ref(false);

const isEmptyHtml = (html) => {
  if (!html) return true;
  // 用 DOM 解析，避免正则遗漏 <br> <hr> 等标签
  const div = document.createElement('div');
  div.innerHTML = html;
  return !div.textContent.trim() && !div.querySelector('img');
};

const extractImagesFromHtml = (html) => {
  if (!html) return [];
  const div = document.createElement('div');
  div.innerHTML = html;
  const imgs = div.querySelectorAll('img');
  return Array.from(imgs).map((img) => {
    const src = img.getAttribute('src') || '';
    // 还原为相对路径，与快速模式保持一致
    const filePath = src.startsWith(BASE_URL) ? src.slice(BASE_URL.length) : src;
    return { url: filePath };
  });
};

const handleSave = async () => {
  if (submitting.value) return;
  
  let finalContent = '';
  let images = [];

  if (mode.value === 'quick') {
    if (!content.value.trim()) {
      showToast("请输入内容");
      return;
    }
    finalContent = content.value;
    images = selectedImages.value.map((i) => ({ url: i.file_path }));
  } else {
    const editor = editorRef.value;
    if (!editor) return showToast("编辑器未就绪");
    // 检测是否有实际文本内容
    const hasText = !!editor.getText()?.trim();
    // 检测 HTML 中是否内嵌了图片
    const hasImg = /<img\s[^>]*>/i.test(htmlContent.value);
    if (!hasText && !hasImg) {
      showToast("请输入内容");
      return;
    }
    // 归一化空 HTML：仅空段落且无图片时清空
    if (isEmptyHtml(htmlContent.value)) {
      htmlContent.value = '';
    }
    finalContent = htmlContent.value;
    // 从 HTML 中提取图片 URL，写入 images 字段，确保后端图片关联不丢失
    images = extractImagesFromHtml(finalContent);
  }

  submitting.value = true;
  try {
    await showConfirmDialog({ title: "确认发布" });
    showLoadingToast({ message: "发布中...", duration: 0 });

    await momentApi.create({
      content: finalContent,
      images,
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
  } finally {
    submitting.value = false;
  }
};

const handleCancel = () => {
  router.back();
};
</script>

<style scoped>
.page-diary-add {
  min-height: 100dvh;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
}

/* ---------- 模式切换 ---------- */
.mode-toggle {
  display: flex;
  align-items: center;
  background: #f0f0f0;
  border-radius: 16px;
  padding: 2px;
  font-size: 13px;
  line-height: 1;
}

.mode-btn {
  padding: 4px 14px;
  border-radius: 14px;
  color: #666;
  transition: all 0.2s;
  cursor: pointer;
  user-select: none;
}

.mode-btn.active {
  background: #fff;
  color: #333;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

/* ---------- 快速模式 ---------- */
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

/* ---------- 精准模式（WangEditor） ---------- */
.wangeditor-container {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.wangeditor-toolbar {
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  position: relative;
}

.wangeditor-editor {
  flex: 1;
  height: 400px;
}

.wangeditor-editor :deep(.w-e-text-container) {
  height: 400px !important;
  border: none !important;
}

/* 背景提示文字 — 行高、字号与滚动容器严格同步 */
.wangeditor-editor :deep(.w-e-text-placeholder) {
  top: 16px !important;
  left: 16px !important;
  font-size: 16px !important;
  line-height: 1.6 !important;
  color: #c8c9cc;
}

/* 滚动容器 — 文本基础样式 */
.wangeditor-editor :deep(.w-e-text-container .w-e-scroll) {
  padding: 16px;
  font-size: 16px;
  line-height: 1.6;
  -webkit-overflow-scrolling: touch;
}

/* 段落边距清零 — 防止首行 p 自带 margin 将光标推离占位文字 */
.wangeditor-editor :deep(.w-e-text-container [contenteditable="true"] p),
.wangeditor-editor :deep(.w-e-text-container [contenteditable="true"] .w-e-textarea-2),
.wangeditor-editor :deep(.w-e-text-container [data-slate-editor]) {
  margin: 0 !important;
  padding: 0 !important;
  line-height: 1.6 !important;
}

.wangeditor-editor :deep(.w-e-bar) {
  background: #fff;
}

.wangeditor-editor :deep(.w-e-bar-item button) {
  padding: 0 6px;
}

/* ---------- 底部元信息 ---------- */
.meta-cells {
  margin-top: 12px;
  flex-shrink: 0;
}

/* ---------- 图片选择弹窗 ---------- */
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

:deep(.van-checkbox-group) {
  height: 50vh !important;
  overflow-y: scroll;
}

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

.image-item.checked {
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
}
</style>