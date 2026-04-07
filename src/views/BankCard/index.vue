<template>
  <div class="page-bank-card-manage">
    <!-- Tab 切换 -->
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="借记卡" name="debit" />
      <van-tab title="信用卡" name="credit" />
    </van-tabs>

    <!-- 子页面内容 -->
    <router-view />
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
watch(activeTab, (val) => {
  router.push(val === "debit" ? "/card/debit" : "/card/credit");
});
</script>

<style scoped>
.page-bank-card-manage {
  height: calc(100vh - 50px);
  background: #f7f8fa;
}
</style>
