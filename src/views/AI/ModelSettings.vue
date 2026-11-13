<template>
  <div class="model-settings-page">
    <AiSettingsDialog as-page :show="true" />
  </div>
</template>

<script setup>
import AiSettingsDialog from '@/components/ai/AiSettingsDialog.vue';
</script>

<style scoped>
.model-settings-page{height:100vh;height:100dvh;overflow:hidden;background:var(--theme-bg-primary);color:var(--theme-text-primary)}
</style>