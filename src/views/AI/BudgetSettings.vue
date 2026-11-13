<template>
  <main class="budget-page">
    <header class="budget-page-head">
      <h2 id="ai-budget-page-title">额度与超时</h2>
      <button type="button" aria-label="返回" @click="router.back()"><van-icon name="arrow-left" /></button>
    </header>
    <div class="budget-page-body"><AiBudgetSettings active standalone /></div>
  </main>
</template>
<script setup>
import { useRouter } from 'vue-router';
import AiBudgetSettings from '@/components/ai/AiBudgetSettings.vue';
const router = useRouter();
</script>
<style scoped>
.budget-page{height:100vh;height:100dvh;display:flex;flex-direction:column;background:var(--theme-bg-primary);color:var(--theme-text-primary)}
.budget-page-head{flex-shrink:0;display:flex;align-items:center;gap:12px;width:100%;max-width:560px;box-sizing:border-box;margin:0 auto;padding:calc(env(safe-area-inset-top,0px) + 14px) 16px 12px;border-bottom:1px solid var(--theme-border)}
.budget-page-head h2{margin:0;font-size:17px;flex:1;order:1}.budget-page-head button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:17px;cursor:pointer}
.budget-page-body{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain;box-sizing:border-box;width:100%;max-width:560px;margin:0 auto;padding:18px 16px max(28px,env(safe-area-inset-bottom))}
.budget-page-head button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
@media(max-width:600px){.budget-page-head{padding:calc(env(safe-area-inset-top,0px) + 12px) 14px 10px}.budget-page-head h2{font-size:15px}.budget-page-body{padding:16px 14px max(24px,env(safe-area-inset-bottom))}}
</style>
