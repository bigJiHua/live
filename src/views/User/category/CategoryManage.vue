<template>
  <div class="page-category-manage">
    <van-tabs v-model:active="activeType" sticky @change="onTypeChange">
      <van-tab title="支出" name="expense" />
      <van-tab title="收入" name="income" />
      <van-tab title="资产" name="asset" />
      <van-tab title="固定资产" name="fixed" />
    </van-tabs>

    <div class="category-content">
      <div class="action-bar">
        <span class="count">共 {{ currentCategories.length }} 个分类</span>
        <van-button size="small" type="primary" icon="plus" @click="showAddDialog = true">
          新增
        </van-button>
      </div>

      <div class="category-grid" v-if="currentCategories.length > 0">
        <div v-for="item in currentCategories" :key="item.id" class="category-card">
          <!-- 编辑 + 删除 按钮 右上角 -->
          <div class="card-actions">
            <van-icon name="edit" size="16" @click="handleEdit(item)" />
            <van-icon name="delete" size="16" @click="handleDelete(item)" />
          </div>

          <div class="card-left">
            <div class="card-icon" v-if="item.icon_url || item.iconUrl">
              <van-image width="36" height="36" :src="getFullUrl(item.icon_url || item.iconUrl)" fit="cover" />
            </div>
            <div class="card-icon default" v-else>
              <img src="@/assets/icon/cate.svg" alt="" width="22" height="22" />
            </div>
          </div>

          <div class="card-content">
            <div class="category-name">{{ item.name }}</div>
            <div v-if="item.remark" class="category-remark">{{ item.remark }}</div>
          </div>
        </div>
      </div>

      <van-empty v-else-if="!loading" description="暂无分类，点击上方按钮添加" />
    </div>

    <van-overlay :show="loading">
      <div class="flex-center">
        <van-loading />
      </div>
    </van-overlay>

    <!-- 新增/编辑弹窗 -->
    <van-popup
      v-model:show="showAddDialog"
      position="bottom"
      round
      :style="{ maxHeight: '70vh' }"
    >
      <div class="dialog-container">
        <div class="dialog-header">
          <span class="dialog-title">{{ editingItem ? '编辑分类' : '新增分类' }}</span>
        </div>

        <div class="dialog-form">
          <van-field
            v-model="formData.name"
            label="名称"
            placeholder="请输入分类名称"
            :maxlength="20"
            show-word-limit
          />

          <!-- 图标选择 -->
          <div class="icon-select-wrap">
            <div class="icon-label">选择图标</div>
            <div class="icon-preview" @click="showIconPicker = true">
              <van-image
                v-if="formData.iconUrl"
                width="48"
                height="48"
                :src="getFullUrl(formData.iconUrl)"
                fit="cover"
                round
              />
              <div v-else class="icon-placeholder">
                <van-icon name="plus" size="24" />
              </div>
            </div>
            <div class="icon-tip" v-if="formData.iconUrl">点击更换图标</div>
          </div>

          <van-field
            v-model="formData.remark"
            label="备注"
            placeholder="选填"
            :maxlength="50"
            show-word-limit
          />
        </div>

        <div class="dialog-actions">
          <van-button plain size="large" round @click="showAddDialog = false">取消</van-button>
          <van-button type="primary" size="large" round @click="handleConfirm">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 图标选择弹窗 -->
    <van-popup
      v-model:show="showIconPicker"
      position="bottom"
      round
      :style="{ height: '70%' }"
    >
      <div class="icon-picker">
        <div class="picker-header">
          <span class="picker-title">选择分类图标</span>
          <van-icon name="cross" @click="showIconPicker = false" />
        </div>

        <!-- 上传新图标 -->
        <div class="upload-section">
          <van-uploader
            v-model="uploadQueue"
            :max-count="1"
            :after-read="handleAfterRead"
            :before-read="beforeUpload"
            preview-size="60px"
          >
            <div class="upload-trigger">
              <van-icon name="plus" size="20" />
              <span>上传图标</span>
            </div>
          </van-uploader>
        </div>

        <!-- 图标列表 -->
        <van-loading v-if="iconLoading" size="24px" class="icon-loading" />
        <div v-else class="icon-grid-wrapper">
          <div class="icon-grid">
            <div
              v-for="item in iconList"
              :key="item.id"
              class="icon-item"
              :class="{ active: formData.iconUrl === (item.file_path || item.url) }"
              @click="selectIcon(item)"
            >
              <van-image width="48" height="48" :src="getThumbUrl(item)" fit="cover" />
              <van-icon
                v-if="formData.iconUrl === (item.file_path || item.url)"
                name="success"
                class="check-icon"
              />
            </div>
          </div>
        </div>

        <van-empty v-if="!iconLoading && iconList.length === 0" description="暂无图标，请上传" />
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { showToast, showConfirmDialog } from "vant";
import { categoryApi } from "@/utils/api/category";
import { uploadApi, BusType } from "@/utils/api/upload";
import ENV from "@/utils/env";

const BASE_URL = ENV.FILE_BASE_URL;

const apiGetCategories = async (type) => {
  const res = await categoryApi.list(type);
  return res.data || [];
};
const apiCreateCategory = async (data) => {
  const res = await categoryApi.create(data);
  return res.data;
};
const apiUpdateCategory = async (id, data) => {
  await categoryApi.update(id, data);
};
const apiDeleteCategory = async (id) => {
  await categoryApi.delete(id);
};

const typeMap = { expense: "支出", income: "收入", asset: "资产", fixed: "固定资产" };
const allCategories = ref({ expense: [], income: [], asset: [], fixed: [] });
const activeType = ref("expense");
const loading = ref(false);
const currentCategories = computed(() => allCategories.value[activeType.value] || []);

const showAddDialog = ref(false);
const editingItem = ref(null);
const formData = ref({ name: "", remark: "", iconUrl: "" });

// 图标选择
const showIconPicker = ref(false);
const iconList = ref([]);
const iconLoading = ref(false);
const uploadQueue = ref([]);

// 获取完整URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 获取缩略图URL
const getThumbUrl = (item) => {
  const path = item.thumbnail || item.file_path || item.url || "";
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 加载图标列表
const loadIconList = async () => {
  iconLoading.value = true;
  try {
    const res = await uploadApi.list({ busType: BusType.OTHER, busId: "", limit: 100, offset: 0 });
    iconList.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    iconList.value = [];
  } finally {
    iconLoading.value = false;
  }
};

watch(showIconPicker, (val) => {
  if (val) loadIconList();
});

// 上传前校验
const beforeUpload = (file) => {
  const isImage = file.type.startsWith("image/");
  if (!isImage) { showToast("只能上传图片文件"); return false; }
  if (file.size / 1024 / 1024 >= 2) { showToast("图片大小不能超过 2MB"); return false; }
  return true;
};

// 上传图标
const handleAfterRead = async (file) => {
  file.status = "uploading";
  file.message = "上传中...";
  try {
    const res = await uploadApi.multiple([file.file], BusType.OTHER, "", "", []);
    if (res.data && res.data.length > 0) {
      iconList.value.unshift(res.data[0]);
      formData.value.iconUrl = res.data[0].file_path || res.data[0].url;
      file.status = "done";
      file.message = "上传成功";
      showToast("上传成功");
    }
  } catch (e) {
    file.status = "failed";
    file.message = "上传失败";
    showToast("上传失败");
  }
  uploadQueue.value = [];
};

// 选择图标
const selectIcon = (item) => {
  formData.value.iconUrl = item.file_path || item.url;
  showIconPicker.value = false;
};

const loadCategories = async (type) => {
  loading.value = true;
  try {
    const data = await apiGetCategories(type);
    allCategories.value[type] = data;
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

const onTypeChange = () => {
  if (allCategories.value[activeType.value].length === 0) {
    loadCategories(activeType.value);
  }
};

const handleEdit = (item) => {
  editingItem.value = item;
  formData.value = { name: item.name, remark: item.remark || "", iconUrl: item.icon_url || item.iconUrl || "" };
  showAddDialog.value = true;
};

const handleConfirm = async () => {
  if (!formData.value.name.trim()) return showToast("请输入名称");
  try {
    if (editingItem.value) {
      const updateData = {
        name: formData.value.name.trim(),
        remark: formData.value.remark.trim() || null,
        iconUrl: formData.value.iconUrl.trim() || null,
      };
      await apiUpdateCategory(editingItem.value.id, updateData);
      const idx = allCategories.value[activeType.value].findIndex(c => c.id === editingItem.value.id);
      if (idx !== -1) allCategories.value[activeType.value][idx] = { ...editingItem.value, ...updateData, icon_url: updateData.iconUrl };
      showToast("更新成功");
    } else {
      const newItem = await apiCreateCategory({
        type: activeType.value,
        name: formData.value.name.trim(),
        remark: formData.value.remark.trim() || null,
        iconUrl: formData.value.iconUrl.trim() || null,
      });
      allCategories.value[activeType.value].push(newItem);
      showToast("创建成功");
    }
  } catch (e) {
    showToast(editingItem.value ? "更新失败" : "创建失败");
  }
  editingItem.value = null;
  formData.value = { name: "", remark: "", iconUrl: "" };
  showAddDialog.value = false;
};

const handleDelete = async (item) => {
  try {
    await showConfirmDialog({ title: "确认删除", message: `删除「${item.name}」？` });
    await apiDeleteCategory(item.id);
    allCategories.value[activeType.value] = allCategories.value[activeType.value].filter(c => c.id !== item.id);
    showToast("已删除");
  } catch (e) {}
};

onMounted(() => loadCategories("expense"));
</script>

<style scoped>
.page-category-manage {
  min-height: 100vh;
  background: #f7f8fa;
}
.category-content {
  padding: 16px;
}
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.count {
  font-size: 13px;
  color: #969799;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.category-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}
.card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 10px;
  color: #999;
}
.card-left { flex-shrink: 0; }
.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-icon.default {
  width: 36px;
  height: 36px;
  background: #f2f2f2;
  color: #999;
}
.card-content { flex: 1; min-width: 0; }
.category-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.category-remark {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}
.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
.dialog-form {
  padding: 16px;
}

/* 弹窗样式 */
.dialog-container {
  padding: 20px;
}
.dialog-header {
  text-align: center;
  margin-bottom: 20px;
}
.dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}
.dialog-form {
  background: #f7f8fa;
  border-radius: 12px;
  padding: 8px 0;
}
.dialog-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
.dialog-actions .van-button {
  flex: 1;
}

/* 图标选择 */
.icon-select-wrap {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #ebedf0;
}
.icon-label {
  font-size: 14px;
  color: #646566;
  flex-shrink: 0;
}
.icon-preview {
  margin-left: auto;
  cursor: pointer;
}
.icon-placeholder {
  width: 48px;
  height: 48px;
  background: #f7f8fa;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #969799;
}
.icon-tip {
  font-size: 12px;
  color: #969799;
  margin-left: 8px;
}

/* 图标选择弹窗 */
.icon-picker {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  box-sizing: border-box;
}
.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.upload-section {
  padding-bottom: 16px;
  border-bottom: 1px solid #ebedf0;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.upload-trigger {
  width: 60px;
  height: 60px;
  background: #f7f8fa;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #969799;
  font-size: 11px;
}
.icon-loading {
  text-align: center;
  padding: 20px;
  flex-shrink: 0;
}
.icon-grid-wrapper {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.icon-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 4px 4px 16px;
}
.icon-item {
  position: relative;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  transition: all 0.2s;
}
.icon-item:active {
  transform: scale(0.95);
}
.icon-item.active {
  border-color: #1989fa;
}
.icon-item .check-icon {
  position: absolute;
  top: 2px;
  right: 2px;
  background: #1989fa;
  border-radius: 50%;
  padding: 2px;
  color: #fff;
  font-size: 10px;
}
</style>