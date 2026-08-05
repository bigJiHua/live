<template>
  <div class="page-bank-card-manage">
    <!-- Tab 切换 -->
    <van-tabs v-model:active="activeTab" @change="onTabChange" class="center-tabs">
      <van-tab title="借记卡" name="debit" />
      <van-tab title="信用卡" name="credit" />
    </van-tabs>
    
    <!-- 路由视图 -->
    <router-view v-slot="{ Component }">
      <transition name="card-tab-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";

const router = useRouter();
const route = useRoute();

// 根据路由初始化 tab
const activeTab = ref(route.path.includes("credit") ? "credit" : "debit");

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

.page-bank-card-manage :deep(.van-tabs__nav) {
  justify-content: center;
}

.page-bank-card-manage :deep(.van-tabs__line) {
  background-color: var(--theme-primary);
}

.page-bank-card-manage :deep(.van-tabs__content) {
  flex: 1;
  overflow-y: auto;
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
