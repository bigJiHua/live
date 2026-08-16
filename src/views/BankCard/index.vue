<template>
  <div class="page-bank-card-manage">
    <!-- 统一顶栏：标题 + 分段切换 + 预览开关 -->
    <header class="page-header card-manage-header" :class="{ scrolled: listScrolled }">
      <div class="header-row">
        <h1 class="title">{{ activeTab === "credit" ? "信用卡" : "借记卡" }}</h1>
        <!-- 分段切换：借记卡 / 信用卡（排序模式下隐藏） -->
        <div class="segment-switch" role="tablist" v-if="!sortMode">
          <button
            class="segment-item"
            :class="{ active: activeTab === 'debit' }"
            @click="onTabChange('debit')"
          >
            借记卡
          </button>
          <button
            class="segment-item"
            :class="{ active: activeTab === 'credit' }"
            @click="onTabChange('credit')"
          >
            信用卡
          </button>
          <span class="segment-thumb" :class="activeTab" />
        </div>
      </div>

      <div class="header-row">
        <span class="subtitle">{{ sortMode ? "长按拖动卡片排序" : "管理你的银行卡" }}</span>
        <div class="header-actions">
          <!-- 排序勾选（圆点点亮/暗） -->
          <label class="preview-toggle" :class="{ on: sortMode }" @click="sortMode = !sortMode">
            <span class="preview-label">排序</span>
            <span class="dot" />
          </label>
          <!-- 预览勾选（排序模式下隐藏） -->
          <label class="preview-toggle" v-if="!sortMode" :class="{ on: previewMode }" @click="previewMode = !previewMode">
            <span class="preview-label">预览</span>
            <span class="dot" />
          </label>
        </div>
      </div>
    </header>

    <!-- 路由视图 -->
    <router-view v-slot="{ Component }">
      <transition name="card-tab-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
import { ref, watch, provide } from "vue";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();

// 根据路由初始化 tab
const activeTab = ref(route.path.includes("credit") ? "credit" : "debit");

// 预览模式（分享录屏时隐藏真实卡号）：提升到壳层，跨子页共享
const previewMode = ref(false);
provide("cardPreviewMode", previewMode);

// 排序模式：开启后子页切换为简洁 list 并支持拖拽排序
const sortMode = ref(false);
provide("cardSortMode", sortMode);

// 列表滚动收起状态：子页监听 .page-card-list 内部滚动后写入此 ref，
// 壳层 Header 与子页「添加卡片」按钮共享同一状态。
// 注意：滚动容器是 .page-card-list（非 window），不能在壳层监听 window。
const listScrolled = ref(false);
provide("cardListScrolled", listScrolled);

// 监听 tab 切换，导航到对应路由
const onTabChange = (name) => {
  router.push(name === "debit" ? "/card/debit" : "/card/credit");
};

// 监听路由变化，同步 tab 状态
watch(() => route.path, (path) => {
  activeTab.value = path.includes("credit") ? "credit" : "debit";
});
</script>

<style scoped>
.page-bank-card-manage {
  height: calc(100vh - 50px);
  background: var(--theme-bg-secondary);
  display: flex;
  flex-direction: column;
}

.card-manage-header {
  margin: 10px 20px 0;
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--theme-bg-secondary);
  overflow: hidden;
  max-height: 200px;
  transition: opacity 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    max-height 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    margin 0.32s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: max-height, opacity;
}
.card-manage-header.scrolled {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  padding-top: 0;
  padding-bottom: 0;
  pointer-events: none;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  margin: 0;
}

/* 分段切换控件 */
.segment-switch {
  position: relative;
  display: inline-flex;
  padding: 4px;
  background: var(--theme-bg-primary, #fff);
  border-radius: 22px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.segment-item {
  position: relative;
  z-index: 2;
  border: none;
  background: transparent;
  padding: 7px 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 18px;
  transition: color 0.28s ease;
}
.segment-item.active {
  color: #fff;
}
/* 滑动高亮块 */
.segment-thumb {
  position: absolute;
  z-index: 1;
  top: 4px;
  bottom: 4px;
  width: calc(50% - 4px);
  border-radius: 18px;
  background: var(--theme-primary);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}
.segment-thumb.debit {
  transform: translateX(0);
}
.segment-thumb.credit {
  transform: translateX(100%);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 164px;
}

.preview-toggle {
  flex: 1;
  min-width: 0;
  justify-content: center;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--theme-bg-primary, #fff);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  user-select: none;
}
.preview-label {
  font-size: 13px;
  color: var(--theme-text-secondary);
  font-weight: 500;
  transition: color 0.2s ease;
}
.preview-toggle.on .preview-label {
  color: var(--theme-primary);
  font-weight: 600;
}
.dot {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid #dcdee0;
  background: transparent;
  transition: all 0.2s ease;
}
.preview-toggle.on .dot {
  border-color: var(--theme-primary);
  background: var(--theme-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--theme-primary) 20%, transparent);
}

/* 借记卡/信用卡切换过渡：淡入 + 轻微上滑 */
.card-tab-fade-enter-active,
.card-tab-fade-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}
.card-tab-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.card-tab-fade-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
</style>
