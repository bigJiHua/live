<template>
  <div class="page-category-manage">
    <div class="category-content">
      <div class="action-bar">
        <span class="count">共 {{ categories.length }} 个银行分类</span>
        <van-button size="small" type="primary" icon="plus" @click="handleAdd">
          新增
        </van-button>
      </div>

      <div class="category-grid" v-if="categories.length > 0">
        <div v-for="item in categories" :key="item.id" class="category-card">
          <div class="card-actions">
            <van-icon name="edit" size="16" @click="handleEdit(item)" />
            <van-icon name="delete" size="16" @click="handleDelete(item)" />
          </div>

          <div class="card-left">
            <div class="card-icon" v-if="item.icon_url || item.iconUrl">
              <van-image
                width="36"
                height="36"
                :src="getFullUrl(item.icon_url || item.iconUrl)"
                fit="cover"
              />
            </div>
            <div class="card-icon default" v-else>
              <van-icon name="card" size="22" />
            </div>
          </div>

          <div class="card-content">
            <div class="category-name">{{ item.name }}</div>
            <div v-if="item.remark" class="category-remark">
              {{ item.remark }}
            </div>
          </div>
        </div>
      </div>

      <van-empty
        v-else-if="!loading"
        description="暂无银行分类，点击上方按钮添加"
      >
        <template #icon>
          <van-icon name="card" size="48" color="#c8c9cc" />
        </template>
      </van-empty>
    </div>

    <van-overlay :show="loading">
      <div class="flex-center">
        <van-loading />
      </div>
    </van-overlay>

    <!-- 新增/编辑弹窗 -->
    <van-popup
      v-model:show="showDialog"
      position="bottom"
      round
      :style="{ maxHeight: '70vh' }"
    >
      <div class="dialog-container">
        <div class="dialog-header">
          <span class="dialog-title">{{
            editingItem ? "编辑银行" : "新增银行"
          }}</span>
        </div>

        <div class="dialog-form">
          <van-field
            v-model="formData.name"
            label="银行名称"
            placeholder="请输入银行名称"
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
          <van-button plain size="large" round @click="showDialog = false"
            >取消</van-button
          >
          <van-button type="primary" size="large" round @click="handleConfirm">
            保存
          </van-button>
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
          <span class="picker-title">选择银行图标</span>
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
        <div v-else class="icon-grid">
          <div
            v-for="item in iconList"
            :key="item.id"
            class="icon-item"
            :class="{
              active: formData.iconUrl === (item.file_path || item.url),
            }"
            @click="selectIcon(item)"
          >
            <van-image
              width="48"
              height="48"
              :src="getThumbUrl(item)"
              fit="cover"
            />
            <van-icon
              v-if="formData.iconUrl === (item.file_path || item.url)"
              name="success"
              class="check-icon"
            />
          </div>
        </div>

        <van-empty
          v-if="!iconLoading && iconList.length === 0"
          description="暂无图标，请上传"
        />
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { showToast, showConfirmDialog } from "vant";
import { categoryApi } from "@/utils/api/category";
import { uploadApi, BusType } from "@/utils/api/upload";

const categories = ref([]);
const loading = ref(false);
const showDialog = ref(false);
const editingItem = ref(null);
const formData = ref({ name: "", remark: "", iconUrl: "" });

// 图标选择
const showIconPicker = ref(false);
const iconList = ref([]);
const iconLoading = ref(false);
const uploadQueue = ref([]);

// BASE_URL
const BASE_URL = "http://192.168.0.103:3001/api/public";

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

// 加载银行分类
const loadCategories = async () => {
  loading.value = true;
  try {
    const res = await categoryApi.list("bank");
    categories.value = res.data || res || [];
  } catch (e) {
    categories.value = [
      { id: "1", name: "招商银行", remark: "招商银行银行卡", icon_url: "" },
      { id: "2", name: "建设银行", remark: "建设银行银行卡", icon_url: "" },
      { id: "3", name: "工商银行", remark: "工商银行银行卡", icon_url: "" },
    ];
  } finally {
    loading.value = false;
  }
};

// 加载图标列表
const loadIconList = async () => {
  iconLoading.value = true;
  try {
    const res = await uploadApi.list({
      busType: BusType.BANK,
      busId: "",
      limit: 100,
      offset: 0,
    });
    iconList.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    iconList.value = [];
  } finally {
    iconLoading.value = false;
  }
};

// 监听图标选择弹窗
watch(showIconPicker, (val) => {
  if (val) {
    loadIconList();
  }
});

// 上传前校验
const beforeUpload = (file) => {
  const isImage = file.type.startsWith("image/");
  if (!isImage) {
    showToast("只能上传图片文件");
    return false;
  }
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isLt2M) {
    showToast("图片大小不能超过 2MB");
    return false;
  }
  return true;
};

// 上传图标
const handleAfterRead = async (file) => {
  file.status = "uploading";
  file.message = "上传中...";

  try {
    const res = await uploadApi.multiple([file.file], BusType.BANK, "", "", []);
    if (res.data && res.data.length > 0) {
      iconList.value.unshift(res.data[0]);
      // 自动选中刚上传的图标
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

// 新增
const handleAdd = () => {
  editingItem.value = null;
  formData.value = { name: "", remark: "", iconUrl: "" };
  showDialog.value = true;
};

// 编辑
const handleEdit = (item) => {
  editingItem.value = item;
  formData.value = {
    name: item.name,
    remark: item.remark || "",
    iconUrl: item.icon_url || item.iconUrl || "",
  };
  showDialog.value = true;
};

// 确认保存
const handleConfirm = async () => {
  if (!formData.value.name.trim()) return showToast("请输入银行名称");

  try {
    if (editingItem.value) {
      await categoryApi.update(editingItem.value.id, {
        name: formData.value.name.trim(),
        remark: formData.value.remark.trim() || null,
        iconUrl: formData.value.iconUrl.trim() || null,
      });
      const idx = categories.value.findIndex(
        (c) => c.id === editingItem.value.id
      );
      if (idx !== -1) {
        categories.value[idx] = {
          ...categories.value[idx],
          name: formData.value.name.trim(),
          remark: formData.value.remark.trim(),
          icon_url: formData.value.iconUrl.trim(),
        };
      }
      showToast("更新成功");
    } else {
      const newItem = await categoryApi.create({
        type: "bank",
        name: formData.value.name.trim(),
        remark: formData.value.remark.trim() || null,
        iconUrl: formData.value.iconUrl.trim() || null,
      });
      categories.value.push(newItem.data || newItem);
      showToast("创建成功");
    }
  } catch (e) {
    showToast(editingItem.value ? "更新失败" : "创建失败");
  }

  editingItem.value = null;
  formData.value = { name: "", remark: "", iconUrl: "" };
  showDialog.value = false;
};

// 删除
const handleDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: "确认删除",
      message: `删除「${item.name}」？`,
    });
    await categoryApi.delete(item.id);
    categories.value = categories.value.filter((c) => c.id !== item.id);
    showToast("已删除");
  } catch (e) {}
};

onMounted(() => loadCategories());
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
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
.card-left {
  flex-shrink: 0;
}
.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-icon.default {
  width: 36px;
  height: 36px;
  background: #f2f2f2;
  border-radius: 8px;
  color: #999;
}
.card-content {
  flex: 1;
  min-width: 0;
}
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
}
.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
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
}
.icon-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  overflow-y: auto;
  flex: 1;
  padding-bottom: 16px;
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
