<template>
  <div class="recycle-bin">
    <div class="recycle-bin__tip">
      回收站里的资产不参与统计与折旧，恢复后回到资产列表；彻底删除无法找回。
    </div>

    <van-loading v-if="loading" class="recycle-bin__loading" vertical>加载中...</van-loading>

    <van-empty v-else-if="assets.length === 0" description="回收站是空的" />

    <div v-else class="recycle-bin__list">
      <div v-for="item in assets" :key="item.id" class="recycle-card">
        <div class="recycle-card__image">
          <img v-if="item.img_url" :src="getImageUrl(item.img_url)" :alt="item.info" />
          <van-icon v-else name="photo-o" />
        </div>
        <div class="recycle-card__main">
          <div class="recycle-card__title">{{ item.info }}</div>
          <div class="recycle-card__meta">{{ item.tag || "未分类" }} · ¥{{ formatAmount(item.buy_price) }}</div>
          <div class="recycle-card__meta">购买日期 {{ item.buy_date || "-" }}</div>
        </div>
        <div class="recycle-card__actions">
          <app-button type="primary" plain size="small" @click="restore(item)">恢复</app-button>
          <app-button type="danger" plain size="small" @click="permanentDelete(item)">彻底删除</app-button>
        </div>
      </div>
    </div>

    <div v-if="!loading && assets.length > 0" class="recycle-bin__count">共 {{ assets.length }} 项</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog } from 'vant'
import { showSuccessToast } from '@/utils/toast'
import { apiErrorToast } from '@/utils/request/helpers'
import {
  getRecycleBinList,
  restoreFixedAsset,
  permanentlyDeleteFixedAsset,
} from '@/utils/api/fixedAsset'

const assets = ref([])
const loading = ref(false)
const busyId = ref('')

const formatAmount = amount => {
  if (amount === null || amount === undefined || amount === '') return '0.00'
  return (parseFloat(amount) || 0).toFixed(2)
}

const getImageUrl = path => {
  if (!path) return ''
  if (/^https?:/.test(path)) return path
  const base = import.meta.env.VITE_FILE_BASE_URL || ''
  return base + path
}

async function loadList() {
  loading.value = true
  try {
    const res = await getRecycleBinList()
    assets.value = res.data || []
  } catch (exception) {
    assets.value = []
    apiErrorToast(exception, '加载回收站失败')
  } finally {
    loading.value = false
  }
}

async function restore(item) {
  if (busyId.value) return
  busyId.value = item.id
  try {
    await restoreFixedAsset(item.id)
    showSuccessToast('已恢复')
    await loadList()
  } catch (exception) {
    apiErrorToast(exception, '恢复失败')
  } finally {
    busyId.value = ''
  }
}

async function permanentDelete(item) {
  if (busyId.value) return
  try {
    await showConfirmDialog({
      title: '彻底删除',
      message: `「${item.info}」将被永久删除，无法恢复，确定继续？`,
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消',
      confirmButtonColor: '#ee0a24',
    })
  } catch {
    return
  }
  busyId.value = item.id
  try {
    await permanentlyDeleteFixedAsset(item.id)
    showSuccessToast('已彻底删除')
    await loadList()
  } catch (exception) {
    apiErrorToast(exception, '删除失败')
  } finally {
    busyId.value = ''
  }
}

defineExpose({ loadList })
</script>

<style scoped>
.recycle-bin {
  padding: 12px 16px 24px;
}

.recycle-bin__tip {
  padding: 10px 12px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: var(--theme-bg-secondary);
  color: var(--theme-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.recycle-bin__loading {
  padding: 40px 0;
}

.recycle-bin__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recycle-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--theme-bg-secondary);
}

.recycle-card__image {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--theme-bg-primary);
  color: var(--theme-text-tertiary);
  font-size: 22px;
}

.recycle-card__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recycle-card__main {
  flex: 1;
  min-width: 0;
}

.recycle-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recycle-card__meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.recycle-card__actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.recycle-bin__count {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}
</style>
