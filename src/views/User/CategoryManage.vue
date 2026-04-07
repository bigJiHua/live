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
            <div class="card-icon" v-if="item.icon_url">
              <van-image width="36" height="36" :src="item.icon_url" fit="cover" />
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

    <van-dialog
      v-model:show="showAddDialog"
      :title="editingItem ? '编辑分类' : '新增分类'"
      show-cancel-button
      @confirm="handleConfirm"
    >
      <div class="dialog-form">
        <van-field
          v-model="formData.name"
          label="名称"
          placeholder="请输入分类名称"
          :maxlength="20"
          show-word-limit
        />
        <van-field
          v-model="formData.iconUrl"
          label="图标URL"
          placeholder="选填"
        />
        <van-field
          v-model="formData.remark"
          label="备注"
          placeholder="选填"
          :maxlength="50"
          show-word-limit
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { showToast, showConfirmDialog } from "vant";
import { categoryApi } from "@/utils/api/category";

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
  formData.value = { name: item.name, remark: item.remark || "", iconUrl: item.iconUrl || "" };
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
      if (idx !== -1) allCategories.value[activeType.value][idx] = { ...editingItem.value, ...updateData };
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
</style>